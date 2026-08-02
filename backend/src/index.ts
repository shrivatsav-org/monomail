export interface Env {
  FCM_TOKENS: KVNamespace;
  GCP_PROJECT_ID: string;
  PUBSUB_TOPIC: string;
  WORKER_BASE_URL: string;
  GCP_SERVICE_ACCOUNT_KEY: string; // Secret: JSON string of GCP service account key
  PUSH_API_KEY: string;            // Secret: shared key required on /register and /unregister
  PUBSUB_VERIFY_TOKEN: string;     // Secret: token embedded in Pub/Sub push URL for verification
}

interface RegisterRequest {
  accountId: string;
  email: string;
  fcmToken: string;
  provider: 'gmail' | 'outlook';
}

interface UnregisterRequest {
  accountId: string;
  email: string;
}

interface StoredToken {
  accountId: string;
  fcmToken: string;
  provider: string;
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id?: string;
}

function isRegisterRequest(v: unknown): v is RegisterRequest {
  return (
    typeof v === 'object' && v !== null &&
    typeof (v as Record<string, unknown>).accountId === 'string' &&
    typeof (v as Record<string, unknown>).email === 'string' &&
    typeof (v as Record<string, unknown>).fcmToken === 'string' &&
    typeof (v as Record<string, unknown>).provider === 'string'
  );
}

function isUnregisterRequest(v: unknown): v is UnregisterRequest {
  return (
    typeof v === 'object' && v !== null &&
    typeof (v as Record<string, unknown>).accountId === 'string' &&
    typeof (v as Record<string, unknown>).email === 'string'
  );
}

function isStoredToken(v: unknown): v is StoredToken {
  return (
    typeof v === 'object' && v !== null &&
    typeof (v as Record<string, unknown>).accountId === 'string' &&
    typeof (v as Record<string, unknown>).fcmToken === 'string'
  );
}

function isServiceAccount(v: unknown): v is ServiceAccount {
  return (
    typeof v === 'object' && v !== null &&
    typeof (v as Record<string, unknown>).client_email === 'string' &&
    typeof (v as Record<string, unknown>).private_key === 'string'
  );
}

/** Return true when the caller supplied the correct shared API key. */
function verifyApiKey(request: Request, env: Env): boolean {
  const key = request.headers.get('X-Api-Key') ?? '';
  return env.PUSH_API_KEY.length > 0 && key === env.PUSH_API_KEY;
}

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === 'POST' && url.pathname === '/register') {
      return handleRegister(request, env);
    }
    if (request.method === 'POST' && url.pathname === '/unregister') {
      return handleUnregister(request, env);
    }
    if (request.method === 'POST' && (url.pathname === '/webhook/gmail' || url.pathname === '/webhook')) {
      return handleGmailWebhook(request, env, url);
    }
    if (url.pathname === '/webhook/outlook') {
      return handleOutlookWebhook(request, env);
    }

    return new Response('Not found', { status: 404 });
  }
};

async function handleRegister(request: Request, env: Env): Promise<Response> {
  if (!verifyApiKey(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  }
  try {
    const body: unknown = await request.json();
    if (!isRegisterRequest(body)) {
      return new Response(JSON.stringify({ error: 'Missing or invalid fields' }), { status: 400 });
    }

    const record = JSON.stringify({ accountId: body.accountId, fcmToken: body.fcmToken, provider: body.provider } satisfies StoredToken);
    await env.FCM_TOKENS.put(`email:${body.email}`, record);
    await env.FCM_TOKENS.put(`account:${body.accountId}`, record);

    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Unknown error';
    console.error('handleRegister error:', msg);
    return new Response(JSON.stringify({ error: msg }), { status: 500 });
  }
}

async function handleUnregister(request: Request, env: Env): Promise<Response> {
  if (!verifyApiKey(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 });
  }
  try {
    const body: unknown = await request.json();
    if (!isUnregisterRequest(body)) {
      return new Response(JSON.stringify({ error: 'Missing required fields' }), { status: 400 });
    }
    await env.FCM_TOKENS.delete(`email:${body.email}`);
    await env.FCM_TOKENS.delete(`account:${body.accountId}`);
    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Unknown error';
    console.error('handleUnregister error:', msg);
    return new Response(JSON.stringify({ error: msg }), { status: 500 });
  }
}

async function handleGmailWebhook(request: Request, env: Env, url: URL): Promise<Response> {
  // Verify token embedded in the Pub/Sub push subscription URL
  // Configure the subscription endpoint as: <WORKER_URL>/webhook/gmail?token=<PUBSUB_VERIFY_TOKEN>
  const token = url.searchParams.get('token') ?? '';
  if (env.PUBSUB_VERIFY_TOKEN.length > 0 && token !== env.PUBSUB_VERIFY_TOKEN) {
    console.warn('Gmail webhook: invalid verify token');
    return new Response('Forbidden', { status: 403 });
  }

  try {
    const body: unknown = await request.json();
    if (
      typeof body !== 'object' || body === null ||
      !('message' in body) || typeof (body as Record<string, unknown>).message !== 'object'
    ) {
      return new Response('Invalid Pub/Sub message', { status: 400 });
    }

    const message = (body as Record<string, unknown>).message as Record<string, unknown>;
    if (typeof message.data !== 'string') {
      return new Response('Invalid Pub/Sub message', { status: 400 });
    }

    const decodedData = atob(message.data);
    const payload: unknown = JSON.parse(decodedData);
    const emailAddress =
      typeof payload === 'object' && payload !== null && typeof (payload as Record<string, unknown>).emailAddress === 'string'
        ? (payload as Record<string, unknown>).emailAddress as string
        : null;

    if (!emailAddress) {
      return new Response('No email address in payload', { status: 400 });
    }

    const storedStr = await env.FCM_TOKENS.get(`email:${emailAddress}`);
    if (!storedStr) {
      console.warn(`No FCM token mapping found for email: ${emailAddress}`);
      return new Response('No token mapping found', { status: 200 });
    }

    const stored: unknown = JSON.parse(storedStr);
    if (!isStoredToken(stored)) {
      return new Response('Corrupt token record', { status: 500 });
    }

    await sendFcmMessage(env, stored.fcmToken, stored.accountId, 'gmail');
    return new Response('OK', { status: 200 });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Unknown error';
    console.error('handleGmailWebhook error:', msg);
    return new Response(msg, { status: 500 });
  }
}

async function handleOutlookWebhook(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);

  const validationToken = url.searchParams.get('validationToken');
  if (validationToken) {
    return new Response(validationToken, { status: 200, headers: { 'Content-Type': 'text/plain' } });
  }

  if (request.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 });
  }

  try {
    const body: unknown = await request.json();
    if (typeof body === 'object' && body !== null && 'value' in body && Array.isArray((body as Record<string, unknown>).value)) {
      for (const notification of (body as Record<string, unknown[]>).value) {
        if (typeof notification !== 'object' || notification === null) continue;
        const accountId = (notification as Record<string, unknown>).clientState;
        if (typeof accountId !== 'string' || !accountId) continue;

        const storedStr = await env.FCM_TOKENS.get(`account:${accountId}`);
        if (!storedStr) continue;

        const stored: unknown = JSON.parse(storedStr);
        if (!isStoredToken(stored)) continue;

        await sendFcmMessage(env, stored.fcmToken, stored.accountId, 'outlook');
      }
    }
    return new Response('OK', { status: 200 });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Unknown error';
    console.error('handleOutlookWebhook error:', msg);
    return new Response(msg, { status: 500 });
  }
}

async function sendFcmMessage(env: Env, fcmToken: string, accountId: string, provider: string): Promise<void> {
  if (!env.GCP_SERVICE_ACCOUNT_KEY) {
    console.error('GCP_SERVICE_ACCOUNT_KEY not set');
    return;
  }
  try {
    const serviceAccount: unknown = JSON.parse(env.GCP_SERVICE_ACCOUNT_KEY);
    if (!isServiceAccount(serviceAccount)) {
      console.error('GCP_SERVICE_ACCOUNT_KEY is malformed');
      return;
    }
    const accessToken = await getGoogleOAuthAccessToken(serviceAccount);
    const projectId = serviceAccount.project_id ?? env.GCP_PROJECT_ID;
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    const fcmResp = await fetch(fcmUrl, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: { accountId, provider, type: 'new_email' }
        }
      })
    });

    if (!fcmResp.ok) {
      console.error('FCM send failed:', await fcmResp.text());
    } else {
      console.log(`FCM message sent for account: ${accountId}`);
    }
  } catch (err: unknown) {
    console.error('sendFcmMessage error:', err instanceof Error ? err.message : err);
  }
}

async function getGoogleOAuthAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  };

  const encodedHeader = urlSafeBase64Encode(JSON.stringify(header));
  const encodedPayload = urlSafeBase64Encode(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const privateKeyDer = importPrivateKey(serviceAccount.private_key);
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    privateKeyDer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', cryptoKey, new TextEncoder().encode(signingInput));
  const jwt = `${signingInput}.${urlSafeBase64Encode(signature)}`;

  const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenData: unknown = await tokenResp.json();
  if (typeof tokenData !== 'object' || tokenData === null || typeof (tokenData as Record<string, unknown>).access_token !== 'string') {
    throw new Error('Failed to obtain Google OAuth token');
  }
  return (tokenData as Record<string, string>).access_token;
}

function urlSafeBase64Encode(data: string | ArrayBuffer): string {
  let base64: string;
  if (typeof data === 'string') {
    base64 = btoa(unescape(encodeURIComponent(data)));
  } else {
    base64 = btoa(String.fromCharCode(...new Uint8Array(data)));
  }
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

function importPrivateKey(pem: string): ArrayBuffer {
  const pemContents = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '');
  const binaryString = atob(pemContents);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}
