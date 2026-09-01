import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'bun:test';
import worker from '../src/index.ts';

class MemoryKv {
  values = new Map();
  puts = [];

  async get(key, type) {
    const value = this.values.get(key);
    if (value === undefined) return null;
    return type === 'json' ? JSON.parse(value) : value;
  }

  async put(key, value, options) {
    this.values.set(key, value);
    this.puts.push({ key, value, options });
  }

  async delete(key) {
    this.values.delete(key);
  }

  async list({ prefix = '' }) {
    return {
      keys: [...this.values.keys()].filter((key) => key.startsWith(prefix)).map((name) => ({ name })),
      list_complete: true,
      cacheStatus: null
    };
  }
}

const originalFetch = globalThis.fetch;
const context = { waitUntil() {}, passThroughOnException() {}, props: {} };

beforeEach(() => {
  globalThis.fetch = originalFetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
});

function env(kv = new MemoryKv()) {
  return {
    FCM_TOKENS: kv,
    GCP_PROJECT_ID: 'monomail-500604',
    PUBSUB_TOPIC: 'projects/monomail-500604/topics/monomail-push',
    PUBSUB_AUDIENCE: 'https://push.example.com/webhook/gmail',
    PUBSUB_SERVICE_ACCOUNT: 'pubsub-push@monomail-500604.iam.gserviceaccount.com',
    PUBSUB_SUBSCRIPTION: 'projects/monomail-500604/subscriptions/worker',
    WORKER_BASE_URL: 'https://push.example.com',
    GCP_SERVICE_ACCOUNT_KEY: '',
    INSTALLATION_TTL_SECONDS: '3600'
  };
}

function jsonRequest(path, body, token = undefined) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return new Request(`https://push.example.com${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  });
}

function registration(installationId, overrides = {}) {
  return {
    accountId: 'gmail_owner@example.com',
    email: 'owner@example.com',
    fcmToken: `valid-fcm-token-${installationId}`,
    installationId,
    provider: 'gmail',
    ...overrides
  };
}

async function digest(value) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return base64Url(bytes);
}

function base64Url(value) {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : new Uint8Array(value);
  return Buffer.from(bytes).toString('base64url');
}

test('provider ownership creates multiple expiring installations and unregisters conditionally', async () => {
  const kv = new MemoryKv();
  const testEnv = env(kv);
  let stopCalls = 0;
  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    if (url.includes('/v1/userinfo')) {
      assert.match(init.headers.Authorization, /^Bearer google-access-token-/);
      return Response.json({ sub: 'google-subject-1', email: 'owner@example.com', email_verified: true });
    }
    if (url.endsWith('/watch')) {
      assert.equal(JSON.parse(init.body).topicName, testEnv.PUBSUB_TOPIC);
      return Response.json({ expiration: String(Date.now() + 6 * 24 * 60 * 60 * 1000), historyId: '12345' });
    }
    if (url.endsWith('/stop')) {
      stopCalls += 1;
      return new Response(null, { status: 204 });
    }
    throw new Error(`unexpected fetch ${url}`);
  };

  const firstId = 'installation_device_one';
  const secondId = 'installation_device_two';
  assert.equal((await worker.fetch(jsonRequest('/register', registration(firstId), 'google-access-token-one'), testEnv, context)).status, 200);
  assert.equal((await worker.fetch(jsonRequest('/register', registration(secondId), 'google-access-token-two'), testEnv, context)).status, 200);

  const identityId = await digest('gmail:google-subject-1');
  const identity = JSON.parse(kv.values.get(`identity:${identityId}`));
  assert.deepEqual(identity.installationIds.sort(), [firstId, secondId].sort());
  assert.equal(identity.gmailWatch.historyId, '12345');
  assert.ok(identity.gmailWatch.expiration > Date.now());
  assert.equal(JSON.stringify([...kv.values.values()]).includes('google-access-token'), false);
  assert.ok(kv.puts.filter((put) => put.key.startsWith('installation:')).every((put) => put.options.expirationTtl === 3600));

  const wrongAccount = registration(firstId, { accountId: 'another-local-account' });
  delete wrongAccount.fcmToken;
  assert.equal((await worker.fetch(jsonRequest('/unregister', wrongAccount, 'google-access-token-one'), testEnv, context)).status, 404);
  assert.ok(kv.values.has(`installation:${identityId}:${firstId}`));

  const firstUnregister = registration(firstId);
  delete firstUnregister.fcmToken;
  assert.equal((await worker.fetch(jsonRequest('/unregister', firstUnregister, 'google-access-token-one'), testEnv, context)).status, 200);
  assert.equal(stopCalls, 0);
  assert.ok(kv.values.has(`installation:${identityId}:${secondId}`));

  const secondUnregister = registration(secondId);
  delete secondUnregister.fcmToken;
  assert.equal((await worker.fetch(jsonRequest('/unregister', secondUnregister, 'google-access-token-two'), testEnv, context)).status, 200);
  assert.equal(stopCalls, 1);
  assert.equal(kv.values.has(`identity:${identityId}`), false);
});

test('registration rejects an email not owned by the provider and strict malformed input', async () => {
  const kv = new MemoryKv();
  globalThis.fetch = async () => Response.json({ sub: 'subject', email: 'actual@example.com', email_verified: true });

  const mismatch = await worker.fetch(
    jsonRequest('/register', registration('installation_mismatch'), 'google-access-token-mismatch'), env(kv), context
  );
  assert.equal(mismatch.status, 403);
  assert.equal([...kv.values.keys()].some((key) => key.startsWith('identity:')), false);

  const extraField = registration('installation_malformed', { accessToken: 'must-not-be-in-body' });
  const malformed = await worker.fetch(jsonRequest('/register', extraField, 'google-access-token-malformed'), env(kv), context);
  assert.equal(malformed.status, 400);
});

test('Outlook uses random subscription clientState and validates subscription metadata before fan-out', async () => {
  const kv = new MemoryKv();
  const jobs = [];
  const testEnv = {
    ...env(kv),
    PUSH_QUEUE: {
      async sendBatch(messages) {
        jobs.push(...messages.map((message) => message.body));
      }
    }
  };
  let graphRequest;
  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    if (url.includes('/v1.0/me?')) {
      return Response.json({ id: 'microsoft-object-id', mail: null, userPrincipalName: 'owner@example.com' });
    }
    if (url.endsWith('/v1.0/subscriptions')) {
      graphRequest = JSON.parse(init.body);
      return Response.json({ id: 'graph-subscription-id', expirationDateTime: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString() });
    }
    throw new Error(`unexpected fetch ${url}`);
  };

  const body = registration('outlook_installation_one', {
    accountId: 'outlook_owner@example.com',
    provider: 'outlook',
    fcmToken: 'valid-fcm-token-for-outlook-device'
  });
  const response = await worker.fetch(jsonRequest('/register', body, 'microsoft-access-token-value'), testEnv, context);
  assert.equal(response.status, 200);
  assert.match(graphRequest.notificationUrl, /\/webhook\/outlook$/);
  assert.match(graphRequest.clientState, /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(graphRequest.clientState, body.accountId);

  const handshake = await worker.fetch(
    new Request('https://push.example.com/webhook/outlook?validationToken=graph%20challenge', { method: 'POST' }), testEnv, context
  );
  assert.equal(handshake.status, 200);
  assert.equal(await handshake.text(), 'graph challenge');

  const wrongState = await worker.fetch(jsonRequest('/webhook/outlook', {
    value: [{ subscriptionId: 'graph-subscription-id', clientState: 'wrong-client-state-value' }]
  }), testEnv, context);
  assert.equal(wrongState.status, 202);
  assert.equal(jobs.length, 0);

  const valid = await worker.fetch(jsonRequest('/webhook/outlook', {
    value: [{ subscriptionId: 'graph-subscription-id', clientState: graphRequest.clientState }]
  }), testEnv, context);
  assert.equal(valid.status, 202);
  assert.equal(jobs.length, 1);
  assert.equal(jobs[0].installationId, 'outlook_installation_one');
});

test('Gmail webhook fails closed and accepts only a valid Google OIDC JWT', async () => {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify']
  );
  const publicJwk = await crypto.subtle.exportKey('jwk', keyPair.publicKey);
  publicJwk.kid = 'test-google-key';
  publicJwk.alg = 'RS256';
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: publicJwk.kid }));
  const claims = base64Url(JSON.stringify({
    iss: 'https://accounts.google.com',
    aud: 'https://push.example.com/webhook/gmail',
    email: 'pubsub-push@monomail-500604.iam.gserviceaccount.com',
    email_verified: true,
    iat: now,
    exp: now + 1800
  }));
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', keyPair.privateKey, new TextEncoder().encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${base64Url(signature)}`;

  const kv = new MemoryKv();
  const identityId = await digest('gmail:google-subject-webhook');
  kv.values.set('email:owner@example.com', identityId);
  kv.values.set(`identity:${identityId}`, JSON.stringify({
    version: 1,
    identityId,
    subject: 'google-subject-webhook',
    email: 'owner@example.com',
    provider: 'gmail',
    installationIds: ['gmail_webhook_device'],
    expiresAt: Date.now() + 60 * 60 * 1000
  }));
  kv.values.set(`installation:${identityId}:gmail_webhook_device`, JSON.stringify({
    version: 1,
    identityId,
    accountId: 'gmail_owner@example.com',
    email: 'owner@example.com',
    fcmToken: 'valid-fcm-token-for-gmail-webhook',
    installationId: 'gmail_webhook_device',
    provider: 'gmail',
    expiresAt: Date.now() + 60 * 60 * 1000
  }));
  const jobs = [];
  const testEnv = {
    ...env(kv),
    PUSH_QUEUE: { async sendBatch(messages) { jobs.push(...messages.map((message) => message.body)); } }
  };
  globalThis.fetch = async (input) => {
    if (String(input) === 'https://www.googleapis.com/oauth2/v3/certs') {
      return Response.json({ keys: [publicJwk] }, { headers: { 'Cache-Control': 'max-age=3600' } });
    }
    throw new Error(`unexpected fetch ${input}`);
  };
  const webhookBody = {
    message: { data: Buffer.from(JSON.stringify({ emailAddress: 'owner@example.com', historyId: '999' })).toString('base64') },
    subscription: testEnv.PUBSUB_SUBSCRIPTION
  };

  const missingAuth = await worker.fetch(jsonRequest('/webhook/gmail', webhookBody), testEnv, context);
  assert.equal(missingAuth.status, 401);
  const invalidAuth = await worker.fetch(jsonRequest('/webhook/gmail', webhookBody, 'not-a-valid-google-token'), testEnv, context);
  assert.equal(invalidAuth.status, 401);
  const validAuth = await worker.fetch(jsonRequest('/webhook/gmail', webhookBody, jwt), testEnv, context);
  assert.equal(validAuth.status, 200);
  assert.equal(jobs.length, 1);

  const unconfigured = { ...testEnv, PUBSUB_AUDIENCE: '' };
  const failClosed = await worker.fetch(jsonRequest('/webhook/gmail', webhookBody, jwt), unconfigured, context);
  assert.equal(failClosed.status, 503);
});

test('synchronous FCM failures are retriable and invalid tokens are removed', async () => {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify']
  );
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)).toString('base64');
  const serviceAccount = JSON.stringify({
    client_email: 'firebase-admin@monomail-500604.iam.gserviceaccount.com',
    private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8}\n-----END PRIVATE KEY-----`,
    project_id: 'monomail-500604'
  });
  const kv = new MemoryKv();
  const identityId = await digest('outlook:fcm-test-user');
  const expiresAt = Date.now() + 60 * 60 * 1000;
  kv.values.set(`identity:${identityId}`, JSON.stringify({
    version: 1, identityId, subject: 'fcm-test-user', email: 'owner@example.com', provider: 'outlook',
    installationIds: ['fcm_failure_device'], expiresAt, outlookSubscriptionId: 'fcm-test-subscription'
  }));
  kv.values.set(`installation:${identityId}:fcm_failure_device`, JSON.stringify({
    version: 1, identityId, accountId: 'outlook_owner@example.com', email: 'owner@example.com',
    fcmToken: 'valid-fcm-token-that-will-fail', installationId: 'fcm_failure_device', provider: 'outlook', expiresAt
  }));
  kv.values.set('subscription:fcm-test-subscription', JSON.stringify({
    version: 1, subscriptionId: 'fcm-test-subscription', identityId, clientState: 'cryptographic-client-state', expiresAt
  }));
  const testEnv = { ...env(kv), GCP_SERVICE_ACCOUNT_KEY: serviceAccount };
  let fcmStatus = 500;
  globalThis.fetch = async (input) => {
    const url = String(input);
    if (url === 'https://oauth2.googleapis.com/token') return Response.json({ access_token: 'firebase-oauth-access-token-value' });
    if (url.includes('fcm.googleapis.com')) {
      if (fcmStatus === 404) {
        return Response.json({ error: { details: [{ errorCode: 'UNREGISTERED' }] } }, { status: 404 });
      }
      return Response.json({ error: { status: 'UNAVAILABLE' } }, { status: 500 });
    }
    throw new Error(`unexpected fetch ${url}`);
  };
  const notification = { value: [{ subscriptionId: 'fcm-test-subscription', clientState: 'cryptographic-client-state' }] };

  const retry = await worker.fetch(jsonRequest('/webhook/outlook', notification), testEnv, context);
  assert.equal(retry.status, 503);
  assert.ok(kv.values.has(`installation:${identityId}:fcm_failure_device`));

  fcmStatus = 404;
  const invalid = await worker.fetch(jsonRequest('/webhook/outlook', notification), testEnv, context);
  assert.equal(invalid.status, 202);
  assert.equal(kv.values.has(`installation:${identityId}:fcm_failure_device`), false);
});
