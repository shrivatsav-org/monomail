export interface Env {
  FCM_TOKENS: KVNamespace;
  PUSH_QUEUE?: Queue<PushJob>;
  GCP_PROJECT_ID: string;
  PUBSUB_TOPIC: string;
  PUBSUB_AUDIENCE: string;
  PUBSUB_SERVICE_ACCOUNT: string;
  PUBSUB_SUBSCRIPTION: string;
  WORKER_BASE_URL: string;
  GCP_SERVICE_ACCOUNT_KEY: string;
  INSTALLATION_TTL_SECONDS?: string;
}

type Provider = 'gmail' | 'outlook';

interface RegisterRequest {
  accountId: string;
  email: string;
  fcmToken: string;
  installationId: string;
  provider: Provider;
}

interface UnregisterRequest {
  accountId: string;
  email: string;
  installationId: string;
  provider: Provider;
}

interface VerifiedIdentity {
  provider: Provider;
  subject: string;
  email: string;
}

interface InstallationRecord {
  version: 1;
  identityId: string;
  accountId: string;
  email: string;
  fcmToken: string;
  installationId: string;
  provider: Provider;
  expiresAt: number;
}

interface IdentityRecord {
  version: 1;
  identityId: string;
  subject: string;
  email: string;
  provider: Provider;
  installationIds: string[];
  expiresAt: number;
  gmailWatch?: { expiration: number; historyId: string };
  outlookSubscriptionId?: string;
}

interface OutlookSubscription {
  version: 1;
  subscriptionId: string;
  identityId: string;
  clientState: string;
  expiresAt: number;
}

interface PushJob {
  identityId: string;
  installationId: string;
  accountId: string;
  fcmToken: string;
  provider: Provider;
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id?: string;
}

interface GoogleJwk extends JsonWebKey {
  kid?: string;
  alg?: string;
}

interface GoogleJwkSet {
  keys: GoogleJwk[];
}

class HttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

class FcmError extends Error {
  readonly invalidToken: boolean;

  constructor(message: string, invalidToken: boolean) {
    super(message);
    this.invalidToken = invalidToken;
  }
}

const JSON_HEADERS = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
const DEFAULT_INSTALLATION_TTL = 30 * 24 * 60 * 60;
const MAX_BODY_BYTES = 16 * 1024;
const GRAPH_SUBSCRIPTION_LIFETIME_MS = 60 * 60 * 1000 * 60;
const GOOGLE_JWKS_URL = 'https://www.googleapis.com/oauth2/v3/certs';
let googleJwksCache: { expiresAt: number; value: GoogleJwkSet } | undefined;

const worker = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    try {
      validateKv(env.FCM_TOKENS);
      const url = new URL(request.url);
      if (request.method === 'POST' && url.pathname === '/register') {
        return await handleRegister(request, env);
      }
      if (request.method === 'POST' && url.pathname === '/unregister') {
        return await handleUnregister(request, env);
      }
      if (request.method === 'POST' && (url.pathname === '/webhook/gmail' || url.pathname === '/webhook')) {
        return await handleGmailWebhook(request, env, ctx);
      }
      if (url.pathname === '/webhook/outlook') {
        return await handleOutlookWebhook(request, env, ctx);
      }
      return new Response('Not found', { status: 404 });
    } catch (error: unknown) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      console.error('Unhandled request error:', error instanceof Error ? error.message : error);
      return json({ error: 'Internal server error' }, 500);
    }
  },

  async queue(batch: MessageBatch<PushJob>, env: Env): Promise<void> {
    validateKv(env.FCM_TOKENS);
    for (const message of batch.messages) {
      try {
        await deliverPush(env, message.body);
        message.ack();
      } catch (error: unknown) {
        console.error('Queued FCM delivery failed:', error instanceof Error ? error.message : error);
        message.retry();
      }
    }
  }
};

export default worker;

async function handleRegister(request: Request, env: Env): Promise<Response> {
  const body = parseRegisterRequest(await readJson(request));
  const accessToken = bearerToken(request);
  const identity = await verifyProviderIdentity(body.provider, accessToken);
  assertRequestedIdentity(body.email, identity);

  const clientIp = request.headers.get('CF-Connecting-IP') ?? 'unknown';
  if (!(await checkRateLimit(env.FCM_TOKENS, `register:${clientIp}`, 20, 60))) {
    throw new HttpError(429, 'Rate limit exceeded');
  }

  const ttl = installationTtl(env);
  const now = Date.now();
  const identityId = await sha256(`${identity.provider}:${identity.subject}`);
  const existing = await getIdentity(env.FCM_TOKENS, identityId);
  if (existing && (existing.provider !== identity.provider || existing.subject !== identity.subject)) {
    throw new HttpError(409, 'Identity binding conflict');
  }

  const identityRecord: IdentityRecord = {
    version: 1,
    identityId,
    subject: identity.subject,
    email: identity.email,
    provider: identity.provider,
    installationIds: unique([...(existing?.installationIds ?? []), body.installationId]),
    expiresAt: now + ttl * 1000,
    gmailWatch: existing?.gmailWatch,
    outlookSubscriptionId: existing?.outlookSubscriptionId
  };
  const installation: InstallationRecord = {
    version: 1,
    identityId,
    accountId: body.accountId,
    email: identity.email,
    fcmToken: body.fcmToken,
    installationId: body.installationId,
    provider: body.provider,
    expiresAt: identityRecord.expiresAt
  };

  const writes: Promise<void>[] = [
    env.FCM_TOKENS.put(identityKey(identityId), JSON.stringify(identityRecord), { expirationTtl: ttl }),
    env.FCM_TOKENS.put(installationKey(identityId, body.installationId), JSON.stringify(installation), { expirationTtl: ttl })
  ];
  if (body.provider === 'gmail') writes.push(env.FCM_TOKENS.put(emailKey(identity.email), identityId, { expirationTtl: ttl }));
  await Promise.all(writes);

  if (body.provider === 'gmail') {
    const watch = await createGmailWatch(env, accessToken);
    identityRecord.gmailWatch = watch;
    await env.FCM_TOKENS.put(identityKey(identityId), JSON.stringify(identityRecord), { expirationTtl: ttl });
  } else {
    identityRecord.outlookSubscriptionId = await ensureOutlookSubscription(env, accessToken, identityRecord, existing);
    await env.FCM_TOKENS.put(identityKey(identityId), JSON.stringify(identityRecord), { expirationTtl: ttl });
  }

  return json({ success: true, expiresAt: identityRecord.expiresAt }, 200);
}

async function handleUnregister(request: Request, env: Env): Promise<Response> {
  const body = parseUnregisterRequest(await readJson(request));
  const accessToken = bearerToken(request);
  const identity = await verifyProviderIdentity(body.provider, accessToken);
  assertRequestedIdentity(body.email, identity);
  const identityId = await sha256(`${identity.provider}:${identity.subject}`);
  const record = await getInstallation(env.FCM_TOKENS, identityId, body.installationId);
  if (!record || record.accountId !== body.accountId || record.provider !== body.provider) {
    throw new HttpError(404, 'Installation is not registered for this account');
  }

  const identityRecord = await getIdentity(env.FCM_TOKENS, identityId);
  const remaining = (await listInstallationIds(env.FCM_TOKENS, identityId)).filter((id) => id !== body.installationId);
  if (remaining.length === 0 && identityRecord) {
    if (identityRecord.provider === 'gmail') {
      await stopGmailWatch(accessToken);
    } else if (identityRecord.outlookSubscriptionId) {
      await deleteOutlookSubscription(accessToken, identityRecord.outlookSubscriptionId);
      await env.FCM_TOKENS.delete(subscriptionKey(identityRecord.outlookSubscriptionId));
    }
  }

  await env.FCM_TOKENS.delete(installationKey(identityId, body.installationId));
  if (!identityRecord || remaining.length === 0) {
    const deletions = [env.FCM_TOKENS.delete(identityKey(identityId))];
    if (identity.provider === 'gmail') deletions.push(env.FCM_TOKENS.delete(emailKey(identity.email)));
    await Promise.all(deletions);
  } else {
    identityRecord.installationIds = remaining;
    const ttl = ttlFromExpiry(identityRecord.expiresAt);
    await env.FCM_TOKENS.put(identityKey(identityId), JSON.stringify(identityRecord), { expirationTtl: ttl });
  }
  return json({ success: true }, 200);
}

async function handleGmailWebhook(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  validateGmailWebhookConfig(env);
  await verifyGooglePubSubJwt(bearerToken(request), env);
  const body = await readJson(request);
  if (!isRecord(body) || !isRecord(body.message) || typeof body.message.data !== 'string' ||
      typeof body.subscription !== 'string' || body.subscription !== env.PUBSUB_SUBSCRIPTION) {
    throw new HttpError(400, 'Invalid Pub/Sub message');
  }

  let payload: unknown;
  try {
    payload = JSON.parse(new TextDecoder().decode(base64Decode(body.message.data)));
  } catch {
    throw new HttpError(400, 'Invalid Pub/Sub message data');
  }
  if (!isRecord(payload) || !validEmail(payload.emailAddress) ||
      (typeof payload.historyId !== 'string' && typeof payload.historyId !== 'number')) {
    throw new HttpError(400, 'Invalid Gmail notification');
  }

  const identityId = await env.FCM_TOKENS.get(emailKey(payload.emailAddress.toLowerCase()));
  if (!identityId) return new Response('OK', { status: 200 });
  const identity = await getIdentity(env.FCM_TOKENS, identityId);
  if (!identity || identity.provider !== 'gmail' || identity.email !== payload.emailAddress.toLowerCase()) {
    return new Response('OK', { status: 200 });
  }
  await dispatchPush(env, ctx, identity);
  return new Response('OK', { status: 200 });
}

async function handleOutlookWebhook(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const url = new URL(request.url);
  const validationToken = url.searchParams.get('validationToken');
  if (validationToken !== null) {
    if (request.method !== 'POST' || validationToken.length < 1 || validationToken.length > 1024) {
      throw new HttpError(400, 'Invalid validation token');
    }
    return new Response(validationToken, { status: 200, headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
  }
  if (request.method !== 'POST') throw new HttpError(405, 'Method not allowed');

  const body = await readJson(request);
  if (!isRecord(body) || !Array.isArray(body.value) || body.value.length > 100) {
    throw new HttpError(400, 'Invalid Outlook notification');
  }

  const identities = new Map<string, IdentityRecord>();
  for (const value of body.value) {
    if (!isRecord(value) || !validOpaqueId(value.subscriptionId, 256) || !validOpaqueId(value.clientState, 256)) continue;
    const metadata = await getSubscription(env.FCM_TOKENS, value.subscriptionId);
    if (!metadata || metadata.clientState !== value.clientState || metadata.expiresAt <= Date.now()) continue;
    const identity = await getIdentity(env.FCM_TOKENS, metadata.identityId);
    if (!identity || identity.provider !== 'outlook' || identity.outlookSubscriptionId !== metadata.subscriptionId) continue;
    identities.set(identity.identityId, identity);
  }
  for (const identity of identities.values()) await dispatchPush(env, ctx, identity);
  return new Response('Accepted', { status: 202 });
}

async function dispatchPush(env: Env, _ctx: ExecutionContext, identity: IdentityRecord): Promise<void> {
  const installationIds = await listInstallationIds(env.FCM_TOKENS, identity.identityId);
  const installations = (await Promise.all(
    installationIds.map((id) => getInstallation(env.FCM_TOKENS, identity.identityId, id))
  )).filter((record): record is InstallationRecord => record !== null && record.expiresAt > Date.now());
  const jobs = installations.map((record): PushJob => ({
    identityId: record.identityId,
    installationId: record.installationId,
    accountId: record.accountId,
    fcmToken: record.fcmToken,
    provider: record.provider
  }));
  if (jobs.length === 0) return;

  if (env.PUSH_QUEUE) {
    await env.PUSH_QUEUE.sendBatch(jobs.map((body) => ({ body })));
    return;
  }
  try {
    await Promise.all(jobs.map((job) => deliverPush(env, job)));
  } catch (error: unknown) {
    console.error('Synchronous FCM delivery failed:', error instanceof Error ? error.message : error);
    throw new HttpError(503, 'Push delivery failed; retry notification');
  }
}

async function deliverPush(env: Env, job: PushJob): Promise<void> {
  try {
    await sendFcmMessage(env, job);
  } catch (error: unknown) {
    if (error instanceof FcmError && error.invalidToken) {
      await removeInvalidInstallation(env.FCM_TOKENS, job);
      return;
    }
    throw error;
  }
}

async function sendFcmMessage(env: Env, job: PushJob): Promise<void> {
  const serviceAccount = parseServiceAccount(env.GCP_SERVICE_ACCOUNT_KEY);
  const projectId = serviceAccount.project_id ?? env.GCP_PROJECT_ID;
  if (!validProjectId(projectId)) throw new Error('GCP project ID is not configured correctly');
  const accessToken = await getGoogleOAuthAccessToken(serviceAccount);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: { token: job.fcmToken, data: { accountId: job.accountId, provider: job.provider, type: 'new_email' } }
    })
  });
  if (response.ok) return;
  const responseText = await response.text();
  let invalidToken = false;
  try {
    const parsed: unknown = JSON.parse(responseText);
    invalidToken ||= isRecord(parsed) && isRecord(parsed.error) && Array.isArray(parsed.error.details) &&
      parsed.error.details.some((detail) => isRecord(detail) && detail.errorCode === 'UNREGISTERED');
  } catch {
    // Non-JSON failures are retryable.
  }
  throw new FcmError(`FCM returned ${response.status}`, invalidToken);
}

async function removeInvalidInstallation(kv: KVNamespace, job: PushJob): Promise<void> {
  const current = await getInstallation(kv, job.identityId, job.installationId);
  if (!current || current.fcmToken !== job.fcmToken) return;
  await kv.delete(installationKey(job.identityId, job.installationId));
  const identity = await getIdentity(kv, job.identityId);
  if (!identity) return;
  identity.installationIds = (await listInstallationIds(kv, job.identityId)).filter((id) => id !== job.installationId);
  if (identity.installationIds.length === 0) {
    const deletions = [kv.delete(identityKey(job.identityId))];
    if (identity.provider === 'gmail') deletions.push(kv.delete(emailKey(identity.email)));
    await Promise.all(deletions);
  } else {
    await kv.put(identityKey(job.identityId), JSON.stringify(identity), { expirationTtl: ttlFromExpiry(identity.expiresAt) });
  }
}

async function verifyProviderIdentity(provider: Provider, token: string): Promise<VerifiedIdentity> {
  if (provider === 'gmail') {
    const response = await fetch('https://openidconnect.googleapis.com/v1/userinfo', {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (response.status === 401 || response.status === 403) throw new HttpError(401, 'Provider token is invalid or expired');
    if (!response.ok) throw new HttpError(502, 'Google identity verification failed');
    const value: unknown = await response.json();
    if (!isRecord(value) || !validOpaqueId(value.sub, 255) || !validEmail(value.email) || value.email_verified !== true) {
      throw new HttpError(401, 'Google account identity is not verified');
    }
    return { provider, subject: value.sub, email: value.email.toLowerCase() };
  }

  const response = await fetch('https://graph.microsoft.com/v1.0/me?$select=id,mail,userPrincipalName', {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (response.status === 401 || response.status === 403) throw new HttpError(401, 'Provider token is invalid or expired');
  if (!response.ok) throw new HttpError(502, 'Microsoft identity verification failed');
  const value: unknown = await response.json();
  if (!isRecord(value) || !validOpaqueId(value.id, 255)) throw new HttpError(401, 'Microsoft account identity is not verified');
  const emails = [value.userPrincipalName, value.mail].filter((candidate): candidate is string => validEmail(candidate));
  if (emails.length === 0) throw new HttpError(401, 'Microsoft account has no verified email address');
  return { provider, subject: value.id, email: emails[0].toLowerCase() };
}

function assertRequestedIdentity(email: string, identity: VerifiedIdentity): void {
  if (email.toLowerCase() !== identity.email) throw new HttpError(403, 'Provider account does not own the requested email');
}

async function createGmailWatch(env: Env, accessToken: string): Promise<{ expiration: number; historyId: string }> {
  if (!/^projects\/[a-z][a-z0-9-]{4,61}[a-z0-9]\/topics\/[A-Za-z][A-Za-z0-9._~-]{2,254}$/.test(env.PUBSUB_TOPIC ?? '')) {
    throw new HttpError(503, 'PUBSUB_TOPIC is not configured correctly');
  }
  const response = await fetch('https://gmail.googleapis.com/gmail/v1/users/me/watch', {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicName: env.PUBSUB_TOPIC, labelIds: ['INBOX'] })
  });
  if (!response.ok) throw new HttpError(response.status === 401 || response.status === 403 ? 401 : 502, 'Gmail watch registration failed');
  const value: unknown = await response.json();
  const expiration = isRecord(value) && typeof value.expiration === 'string' ? Number(value.expiration) : NaN;
  if (!isRecord(value) || !Number.isSafeInteger(expiration) || expiration <= Date.now() || !validOpaqueId(value.historyId, 64)) {
    throw new HttpError(502, 'Gmail watch returned invalid metadata');
  }
  return { expiration, historyId: value.historyId };
}

async function stopGmailWatch(accessToken: string): Promise<void> {
  const response = await fetch('https://gmail.googleapis.com/gmail/v1/users/me/stop', {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: '{}'
  });
  if (!response.ok && response.status !== 404) throw new HttpError(502, 'Gmail watch deletion failed');
}

async function ensureOutlookSubscription(
  env: Env,
  accessToken: string,
  identity: IdentityRecord,
  existing: IdentityRecord | null
): Promise<string> {
  const baseUrl = parseWorkerBaseUrl(env.WORKER_BASE_URL);
  const currentId = existing?.outlookSubscriptionId;
  if (currentId) {
    const metadata = await getSubscription(env.FCM_TOKENS, currentId);
    if (metadata && metadata.expiresAt > Date.now() + 24 * 60 * 60 * 1000) return currentId;
    if (metadata) {
      const expirationDateTime = new Date(Date.now() + GRAPH_SUBSCRIPTION_LIFETIME_MS).toISOString();
      const response = await fetch(`https://graph.microsoft.com/v1.0/subscriptions/${encodeURIComponent(currentId)}`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expirationDateTime })
      });
      if (response.ok) {
        const value: unknown = await response.json();
        const expiresAt = graphExpiration(value);
        metadata.expiresAt = expiresAt;
        await env.FCM_TOKENS.put(subscriptionKey(currentId), JSON.stringify(metadata), { expirationTtl: ttlFromExpiry(expiresAt) });
        return currentId;
      }
      if (response.status !== 404) throw new HttpError(502, 'Outlook subscription renewal failed');
      await env.FCM_TOKENS.delete(subscriptionKey(currentId));
    }
  }

  const clientState = randomToken(32);
  const response = await fetch('https://graph.microsoft.com/v1.0/subscriptions', {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      changeType: 'created',
      notificationUrl: `${baseUrl}/webhook/outlook`,
      resource: "/me/mailFolders('Inbox')/messages",
      expirationDateTime: new Date(Date.now() + GRAPH_SUBSCRIPTION_LIFETIME_MS).toISOString(),
      clientState,
      latestSupportedTlsVersion: 'v1_2'
    })
  });
  if (!response.ok) throw new HttpError(response.status === 401 || response.status === 403 ? 401 : 502, 'Outlook subscription creation failed');
  const value: unknown = await response.json();
  if (!isRecord(value) || !validOpaqueId(value.id, 256)) throw new HttpError(502, 'Outlook subscription returned invalid metadata');
  const expiresAt = graphExpiration(value);
  const metadata: OutlookSubscription = {
    version: 1,
    subscriptionId: value.id,
    identityId: identity.identityId,
    clientState,
    expiresAt
  };
  await env.FCM_TOKENS.put(subscriptionKey(value.id), JSON.stringify(metadata), { expirationTtl: ttlFromExpiry(expiresAt) });
  return value.id;
}

async function deleteOutlookSubscription(accessToken: string, subscriptionId: string): Promise<void> {
  const response = await fetch(`https://graph.microsoft.com/v1.0/subscriptions/${encodeURIComponent(subscriptionId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (!response.ok && response.status !== 404) throw new HttpError(502, 'Outlook subscription deletion failed');
}

function graphExpiration(value: unknown): number {
  if (!isRecord(value) || typeof value.expirationDateTime !== 'string') throw new HttpError(502, 'Outlook subscription expiration is missing');
  const expiresAt = Date.parse(value.expirationDateTime);
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) throw new HttpError(502, 'Outlook subscription expiration is invalid');
  return expiresAt;
}

async function verifyGooglePubSubJwt(jwt: string, env: Env): Promise<void> {
  const parts = jwt.split('.');
  if (parts.length !== 3 || parts.some((part) => part.length === 0 || part.length > 8192)) throw new HttpError(401, 'Invalid Pub/Sub authentication');
  let header: unknown;
  let claims: unknown;
  try {
    header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
  } catch {
    throw new HttpError(401, 'Invalid Pub/Sub authentication');
  }
  if (!isRecord(header) || header.alg !== 'RS256' || !validOpaqueId(header.kid, 256) || !isRecord(claims)) {
    throw new HttpError(401, 'Invalid Pub/Sub authentication');
  }
  const now = Math.floor(Date.now() / 1000);
  const issuer = claims.iss;
  if ((issuer !== 'https://accounts.google.com' && issuer !== 'accounts.google.com') ||
      claims.aud !== env.PUBSUB_AUDIENCE || claims.email !== env.PUBSUB_SERVICE_ACCOUNT || claims.email_verified !== true ||
      typeof claims.exp !== 'number' || claims.exp <= now || claims.exp > now + 3700 ||
      typeof claims.iat !== 'number' || claims.iat > now + 60 || claims.iat < now - 3700) {
    throw new HttpError(401, 'Invalid Pub/Sub authentication');
  }
  const jwks = await getGoogleJwks();
  const jwk = jwks.keys.find((key) => key.kid === header.kid && key.kty === 'RSA' && (!key.alg || key.alg === 'RS256'));
  if (!jwk) throw new HttpError(401, 'Invalid Pub/Sub authentication');
  try {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const verified = await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5', key, base64UrlDecode(parts[2]).buffer, new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    if (!verified) throw new Error('invalid signature');
  } catch {
    throw new HttpError(401, 'Invalid Pub/Sub authentication');
  }
}

async function getGoogleJwks(): Promise<GoogleJwkSet> {
  if (googleJwksCache && googleJwksCache.expiresAt > Date.now()) return googleJwksCache.value;
  const response = await fetch(GOOGLE_JWKS_URL);
  if (!response.ok) throw new HttpError(503, 'Pub/Sub authentication keys unavailable');
  const value: unknown = await response.json();
  if (!isRecord(value) || !Array.isArray(value.keys) || value.keys.length === 0) throw new HttpError(503, 'Pub/Sub authentication keys invalid');
  const keys = value.keys.filter((key): key is GoogleJwk => isRecord(key) && typeof key.kty === 'string');
  if (keys.length === 0) throw new HttpError(503, 'Pub/Sub authentication keys invalid');
  googleJwksCache = { expiresAt: Date.now() + cacheMaxAge(response.headers.get('Cache-Control')) * 1000, value: { keys } };
  return googleJwksCache.value;
}

async function getGoogleOAuthAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const signingInput = `${base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${base64UrlEncode(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  }))}`;
  const key = await crypto.subtle.importKey(
    'pkcs8', importPrivateKey(serviceAccount.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']
  );
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput));
  const assertion = `${signingInput}.${base64UrlEncode(signature)}`;
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion })
  });
  if (!response.ok) throw new Error(`Google OAuth token exchange returned ${response.status}`);
  const value: unknown = await response.json();
  if (!isRecord(value) || typeof value.access_token !== 'string' || value.access_token.length < 20) {
    throw new Error('Google OAuth token exchange returned an invalid token');
  }
  return value.access_token;
}

function parseRegisterRequest(value: unknown): RegisterRequest {
  if (!hasOnlyKeys(value, ['accountId', 'email', 'fcmToken', 'installationId', 'provider']) ||
      !validAccountId(value.accountId) || !validEmail(value.email) || !validFcmToken(value.fcmToken) ||
      !validInstallationId(value.installationId) || !validProvider(value.provider)) {
    throw new HttpError(400, 'Invalid registration request');
  }
  return value as unknown as RegisterRequest;
}

function parseUnregisterRequest(value: unknown): UnregisterRequest {
  if (!hasOnlyKeys(value, ['accountId', 'email', 'installationId', 'provider']) ||
      !validAccountId(value.accountId) || !validEmail(value.email) ||
      !validInstallationId(value.installationId) || !validProvider(value.provider)) {
    throw new HttpError(400, 'Invalid unregistration request');
  }
  return value as unknown as UnregisterRequest;
}

async function readJson(request: Request): Promise<unknown> {
  const contentType = request.headers.get('Content-Type') ?? '';
  if (!/^application\/json(?:\s*;|$)/i.test(contentType)) throw new HttpError(415, 'Content-Type must be application/json');
  const contentLength = Number(request.headers.get('Content-Length') ?? '0');
  if (!Number.isFinite(contentLength) || contentLength > MAX_BODY_BYTES) throw new HttpError(413, 'Request body too large');
  const text = await request.text();
  if (text.length === 0 || new TextEncoder().encode(text).length > MAX_BODY_BYTES) throw new HttpError(400, 'Invalid JSON body');
  try {
    return JSON.parse(text);
  } catch {
    throw new HttpError(400, 'Invalid JSON body');
  }
}

function bearerToken(request: Request): string {
  const header = request.headers.get('Authorization') ?? '';
  const match = /^Bearer ([!-~]{20,8192})$/.exec(header);
  if (!match) throw new HttpError(401, 'Bearer authentication required');
  return match[1];
}

async function checkRateLimit(kv: KVNamespace, key: string, limit: number, windowSec: number): Promise<boolean> {
  const rlKey = `rl:${key}`;
  const current = Number.parseInt((await kv.get(rlKey)) ?? '0', 10);
  if (Number.isFinite(current) && current >= limit) return false;
  await kv.put(rlKey, String((Number.isFinite(current) ? current : 0) + 1), { expirationTtl: windowSec });
  return true;
}

async function getIdentity(kv: KVNamespace, identityId: string): Promise<IdentityRecord | null> {
  const value = await kv.get(identityKey(identityId), 'json');
  return isIdentityRecord(value) ? value : null;
}

async function getInstallation(kv: KVNamespace, identityId: string, installationId: string): Promise<InstallationRecord | null> {
  const value = await kv.get(installationKey(identityId, installationId), 'json');
  return isInstallationRecord(value) ? value : null;
}

async function getSubscription(kv: KVNamespace, id: string): Promise<OutlookSubscription | null> {
  const value = await kv.get(subscriptionKey(id), 'json');
  return isSubscription(value) ? value : null;
}

async function listInstallationIds(kv: KVNamespace, identityId: string): Promise<string[]> {
  const prefix = `installation:${identityId}:`;
  let cursor: string | undefined;
  const ids: string[] = [];
  do {
    const page = await kv.list({ prefix, cursor, limit: 1000 });
    for (const key of page.keys) {
      const id = key.name.slice(prefix.length);
      if (validInstallationId(id)) ids.push(id);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
  return unique(ids);
}

function isIdentityRecord(value: unknown): value is IdentityRecord {
  return isRecord(value) && value.version === 1 && validOpaqueId(value.identityId, 64) && validOpaqueId(value.subject, 255) &&
    validEmail(value.email) && validProvider(value.provider) && Array.isArray(value.installationIds) &&
    value.installationIds.length <= 100 && value.installationIds.every(validInstallationId) && typeof value.expiresAt === 'number';
}

function isInstallationRecord(value: unknown): value is InstallationRecord {
  return isRecord(value) && value.version === 1 && validOpaqueId(value.identityId, 64) && validAccountId(value.accountId) &&
    validEmail(value.email) && validFcmToken(value.fcmToken) && validInstallationId(value.installationId) &&
    validProvider(value.provider) && typeof value.expiresAt === 'number';
}

function isSubscription(value: unknown): value is OutlookSubscription {
  return isRecord(value) && value.version === 1 && validOpaqueId(value.subscriptionId, 256) &&
    validOpaqueId(value.identityId, 64) && validOpaqueId(value.clientState, 256) && typeof value.expiresAt === 'number';
}

function parseServiceAccount(raw: string): ServiceAccount {
  if (!raw || raw.length > 64 * 1024) throw new Error('GCP_SERVICE_ACCOUNT_KEY is not configured');
  let value: unknown;
  try { value = JSON.parse(raw); } catch { throw new Error('GCP_SERVICE_ACCOUNT_KEY is malformed'); }
  if (!isRecord(value) || !validEmail(value.client_email) || typeof value.private_key !== 'string' ||
      !value.private_key.includes('-----BEGIN PRIVATE KEY-----') ||
      (value.project_id !== undefined && !validProjectId(value.project_id))) {
    throw new Error('GCP_SERVICE_ACCOUNT_KEY is malformed');
  }
  return value as unknown as ServiceAccount;
}

function validateKv(value: unknown): asserts value is KVNamespace {
  if (!value || typeof (value as KVNamespace).get !== 'function' || typeof (value as KVNamespace).put !== 'function' ||
      typeof (value as KVNamespace).delete !== 'function' || typeof (value as KVNamespace).list !== 'function') {
    throw new HttpError(503, 'FCM_TOKENS binding is missing');
  }
}

function validateGmailWebhookConfig(env: Env): void {
  if (!validEmail(env.PUBSUB_SERVICE_ACCOUNT) || !validOpaqueId(env.PUBSUB_SUBSCRIPTION, 512)) {
    throw new HttpError(503, 'Pub/Sub authentication is not configured');
  }
  try {
    const audience = new URL(env.PUBSUB_AUDIENCE);
    if (audience.protocol !== 'https:') throw new Error();
  } catch {
    throw new HttpError(503, 'Pub/Sub audience is not configured correctly');
  }
}

function parseWorkerBaseUrl(raw: string): string {
  try {
    const url = new URL(raw);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error();
    return url.href.replace(/\/$/, '');
  } catch {
    throw new HttpError(503, 'WORKER_BASE_URL is not configured correctly');
  }
}

function installationTtl(env: Env): number {
  const value = env.INSTALLATION_TTL_SECONDS === undefined ? DEFAULT_INSTALLATION_TTL : Number(env.INSTALLATION_TTL_SECONDS);
  if (!Number.isInteger(value) || value < 3600 || value > 90 * 24 * 60 * 60) {
    throw new HttpError(503, 'INSTALLATION_TTL_SECONDS is invalid');
  }
  return value;
}

function ttlFromExpiry(expiresAt: number): number {
  return Math.max(60, Math.ceil((expiresAt - Date.now()) / 1000));
}

function identityKey(id: string): string { return `identity:${id}`; }
function installationKey(identityId: string, installationId: string): string { return `installation:${identityId}:${installationId}`; }
function subscriptionKey(id: string): string { return `subscription:${id}`; }
function emailKey(email: string): string { return `email:${email.toLowerCase()}`; }

function json(value: unknown, status: number): Response {
  return new Response(JSON.stringify(value), { status, headers: JSON_HEADERS });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasOnlyKeys(value: unknown, keys: string[]): value is Record<string, unknown> {
  return isRecord(value) && Object.keys(value).length === keys.length && keys.every((key) => key in value);
}

function validProvider(value: unknown): value is Provider { return value === 'gmail' || value === 'outlook'; }
function validEmail(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 320 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}
function validAccountId(value: unknown): value is string { return validOpaqueId(value, 256); }
function validInstallationId(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{16,128}$/.test(value);
}
function validFcmToken(value: unknown): value is string {
  return typeof value === 'string' && value.length >= 20 && value.length <= 4096 && /^[A-Za-z0-9_:\-.]+$/.test(value);
}
function validOpaqueId(value: unknown, max: number): value is string {
  return typeof value === 'string' && value.length >= 1 && value.length <= max && !/[\u0000-\u001f\u007f]/.test(value);
}
function validProjectId(value: unknown): value is string {
  return typeof value === 'string' && /^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(value);
}

function unique(values: string[]): string[] {
  return [...new Set(values)].slice(-100);
}

function randomToken(bytes: number): string {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return base64UrlEncode(value.buffer);
}

async function sha256(value: string): Promise<string> {
  return base64UrlEncode(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
}

function cacheMaxAge(value: string | null): number {
  const match = /(?:^|,)\s*max-age=(\d+)/i.exec(value ?? '');
  return match ? Math.min(Math.max(Number(match[1]), 60), 24 * 60 * 60) : 60 * 60;
}

function base64Decode(value: string): Uint8Array<ArrayBuffer> {
  if (value.length > MAX_BODY_BYTES * 2 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) throw new Error('invalid base64');
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('invalid base64url');
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  return base64Decode(normalized);
}

function base64UrlEncode(value: string | ArrayBuffer): string {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : new Uint8Array(value);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function importPrivateKey(pem: string): ArrayBuffer {
  const contents = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  return base64Decode(contents).buffer;
}
