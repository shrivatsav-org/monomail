export interface Env {
  FCM_TOKENS: KVNamespace;
  DB: D1Database;
  GCP_PROJECT_ID: string;
  PUBSUB_TOPIC: string;
  WORKER_BASE_URL: string;
  GCP_SERVICE_ACCOUNT_KEY: string; // Secret containing JSON string of GCP service account key
  ADMIN_KEY: string; // Secret containing admin API key
}

interface RegisterRequest {
  accountId: string;
  email: string;
  fcmToken: string;
  accessToken: string;
  provider: 'gmail' | 'outlook';
}
const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

function corsResponse(response: Response): Response {
  const newResponse = new Response(response.body, response);
  for (const [key, value] of Object.entries(CORS_HEADERS)) {
    newResponse.headers.set(key, value);
  }
  return newResponse;
}


export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (request.method === 'POST' && url.pathname === '/register') {
      return await handleRegister(request, env);
    }

    if (request.method === 'POST' && (url.pathname === '/webhook/gmail' || url.pathname === '/webhook')) {
      return await handleGmailWebhook(request, env);
    }

    if (url.pathname === '/webhook/outlook') {
      return await handleOutlookWebhook(request, env);
    }

    // License management endpoints
    if (request.method === 'POST' && url.pathname === '/license/validate') {
      return corsResponse(await handleLicenseValidate(request, env));
    }
    if (request.method === 'POST' && url.pathname === '/license/generate') {
      return corsResponse(await handleLicenseGenerate(request, env));
    }
    if (request.method === 'GET' && url.pathname === '/license/list') {
      return corsResponse(await handleLicenseList(request, env));
    }
    if (request.method === 'POST' && url.pathname === '/license/revoke') {
      return corsResponse(await handleLicenseRevoke(request, env));
    }
    if (request.method === 'GET' && url.pathname === '/license/schema') {
      return corsResponse(await handleLicenseSchema(request, env));
    }

    return new Response('Monomail Push Backend is running.', { status: 200 });
  }
};

async function handleRegister(request: Request, env: Env): Promise<Response> {
  try {
    const data: RegisterRequest = await request.json();
    if (!data.accountId || !data.email || !data.fcmToken || !data.provider) {
      return new Response(JSON.stringify({ error: 'Missing required fields' }), { status: 400 });
    }

    // Save mapping in KV. We store the mapping keyed by email or accountId depending on webhook lookup needs.
    // For Gmail, Pub/Sub sends emailAddress. For Outlook, we can embed accountId in clientState or lookup by subscriptionId.
    await env.FCM_TOKENS.put(`email:${data.email}`, JSON.stringify({
      accountId: data.accountId,
      fcmToken: data.fcmToken,
      provider: data.provider
    }));
    await env.FCM_TOKENS.put(`account:${data.accountId}`, JSON.stringify({
      accountId: data.accountId,
      fcmToken: data.fcmToken,
      provider: data.provider
    }));

    if (data.provider === 'gmail' && data.accessToken) {
      // Call Gmail API watch endpoint
      const watchResp = await fetch('https://gmail.googleapis.com/gmail/v1/users/me/watch', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${data.accessToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          topicName: env.PUBSUB_TOPIC,
          labelIds: ['INBOX']
        })
      });

      if (!watchResp.ok) {
        const errorText = await watchResp.text();
        console.error('Gmail watch API failed:', errorText);
        return new Response(JSON.stringify({ error: 'Gmail watch API failed', details: errorText }), { status: 500 });
      }
    } else if (data.provider === 'outlook' && data.accessToken) {
      // Call Microsoft Graph API to create subscription
      const notificationUrl = `${env.WORKER_BASE_URL.replace(/\/$/, '')}/webhook/outlook`;
      const expirationDateTime = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(); // 3 days max for messages

      const subResp = await fetch('https://graph.microsoft.com/v1.0/subscriptions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${data.accessToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          changeType: 'created',
          notificationUrl: notificationUrl,
          resource: 'me/mailFolders(\'Inbox\')/messages',
          expirationDateTime: expirationDateTime,
          clientState: data.accountId // Pass accountId in clientState to identify incoming webhook
        })
      });

      if (!subResp.ok) {
        const errorText = await subResp.text();
        console.error('Microsoft Graph subscription failed:', errorText);
        return new Response(JSON.stringify({ error: 'Microsoft Graph subscription failed', details: errorText }), { status: 500 });
      }
    }

    return new Response(JSON.stringify({ success: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  } catch (err: any) {
    console.error('handleRegister error:', err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
}

async function handleGmailWebhook(request: Request, env: Env): Promise<Response> {
  try {
    const body: any = await request.json();
    if (!body.message || !body.message.data) {
      return new Response('Invalid Pub/Sub message', { status: 400 });
    }

    // Decode base64 data
    const decodedData = atob(body.message.data);
    const payload = JSON.parse(decodedData);
    const emailAddress = payload.emailAddress;

    if (!emailAddress) {
      return new Response('No email address in payload', { status: 400 });
    }

    // Lookup FCM token in KV
    const storedStr = await env.FCM_TOKENS.get(`email:${emailAddress}`);
    if (!storedStr) {
      console.warn(`No FCM token mapping found for email: ${emailAddress}`);
      return new Response('No token mapping found', { status: 200 }); // Return 200 to acknowledge Pub/Sub
    }

    const stored = JSON.parse(storedStr);
    await sendFcmMessage(env, stored.fcmToken, stored.accountId, 'gmail');

    return new Response('OK', { status: 200 });
  } catch (err: any) {
    console.error('handleGmailWebhook error:', err);
    return new Response(err.message, { status: 500 });
  }
}

async function handleOutlookWebhook(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);

  // Handle Microsoft Graph validation token verification
  const validationToken = url.searchParams.get('validationToken');
  if (validationToken) {
    return new Response(validationToken, { status: 200, headers: { 'Content-Type': 'text/plain' } });
  }

  if (request.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 });
  }

  try {
    const body: any = await request.json();
    if (body && body.value && Array.isArray(body.value)) {
      for (const notification of body.value) {
        const accountId = notification.clientState;
        if (!accountId) continue;

        const storedStr = await env.FCM_TOKENS.get(`account:${accountId}`);
        if (!storedStr) continue;

        const stored = JSON.parse(storedStr);
        await sendFcmMessage(env, stored.fcmToken, stored.accountId, 'outlook');
      }
    }

    return new Response('OK', { status: 200 });
  } catch (err: any) {
    console.error('handleOutlookWebhook error:', err);
    return new Response(err.message, { status: 500 });
  }
}

async function sendFcmMessage(env: Env, fcmToken: string, accountId: string, provider: string) {
  if (!env.GCP_SERVICE_ACCOUNT_KEY) {
    console.error('GCP_SERVICE_ACCOUNT_KEY secret is not set.');
    return;
  }

  try {
    const serviceAccount = JSON.parse(env.GCP_SERVICE_ACCOUNT_KEY);
    const accessToken = await getGoogleOAuthAccessToken(serviceAccount);

    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id || env.GCP_PROJECT_ID}/messages:send`;
    const fcmResp = await fetch(fcmUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          android: {
            priority: 'high'
          },
          data: {
            accountId: accountId,
            provider: provider,
            syncRequired: 'true',
            timestamp: Date.now().toString()
          }
        }
      })
    });

    if (!fcmResp.ok) {
      const errText = await fcmResp.text();
      console.error('FCM send failed:', errText);
    }
  } catch (err) {
    console.error('sendFcmMessage error:', err);
  }
}

// Helper to generate Google OAuth Access Token via JWT for Service Account
async function getGoogleOAuthAccessToken(serviceAccount: any): Promise<string> {
  const header = {
    alg: 'RS256',
    typ: 'JWT',
    kid: serviceAccount.private_key_id
  };

  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600;
  const claimset = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: exp,
    iat: iat
  };

  const encodedHeader = urlSafeBase64Encode(JSON.stringify(header));
  const encodedClaimset = urlSafeBase64Encode(JSON.stringify(claimset));
  const toSign = `${encodedHeader}.${encodedClaimset}`;

  const privateKey = importPrivateKey(serviceAccount.private_key);
  const key = await crypto.subtle.importKey(
    'pkcs8',
    privateKey,
    { name: 'RSASSA-PKCS1-v1_5', hash: { name: 'SHA-256' } },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(toSign)
  );

  const encodedSignature = urlSafeBase64Encode(signature);
  const jwt = `${toSign}.${encodedSignature}`;

  const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`
  });

  const tokenData: any = await tokenResp.json();
  return tokenData.access_token;
}

function urlSafeBase64Encode(data: string | ArrayBuffer): string {
  let base64 = '';
  if (typeof data === 'string') {
    base64 = btoa(data);
  } else {
    const bytes = new Uint8Array(data);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    base64 = btoa(binary);
  }
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function importPrivateKey(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s+/g, '');
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
// ── License Management ──────────────────────────────────────────────────────

function generateKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  const segments = [4, 4, 4, 4];
  return 'MONO-' + segments.map(len => {
    let seg = '';
    for (let i = 0; i < len; i++) {
      seg += chars[Math.floor(Math.random() * chars.length)];
    }
    return seg;
  }).join('-');
}

function verifyAdmin(request: Request, env: Env): boolean {
  const auth = request.headers.get('Authorization');
  return auth === `Bearer ${env.ADMIN_KEY}`;
}

async function handleLicenseValidate(request: Request, env: Env): Promise<Response> {
  try {
    const { key } = await request.json() as { key?: string };
    if (!key) {
      return new Response(JSON.stringify({ error: 'Missing key' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const row = await env.DB.prepare('SELECT * FROM licenses WHERE key = ?').bind(key).first();
    if (!row) {
      return new Response(JSON.stringify({ valid: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    if (row.status !== 'active') {
      return new Response(JSON.stringify({ valid: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    if (row.expiresAt && row.expiresAt < Date.now()) {
      return new Response(JSON.stringify({ valid: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    return new Response(JSON.stringify({
      valid: true,
      email: row.email,
      plan: row.plan,
      expiresAt: row.expiresAt,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    console.error('handleLicenseValidate error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

async function handleLicenseGenerate(request: Request, env: Env): Promise<Response> {
  if (!verifyAdmin(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    const { email, plan, expiresAt } = await request.json() as {
      email?: string;
      plan?: string;
      expiresAt?: number;
    };
    if (!email) {
      return new Response(JSON.stringify({ error: 'Missing email' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const key = generateKey();
    await env.DB.prepare(
      'INSERT INTO licenses (key, email, plan, status, expiresAt, createdAt) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(key, email, plan ?? 'premium', 'active', expiresAt ?? null, Date.now()).run();

    return new Response(JSON.stringify({ key }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    console.error('handleLicenseGenerate error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

async function handleLicenseList(request: Request, env: Env): Promise<Response> {
  if (!verifyAdmin(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    const { results } = await env.DB.prepare('SELECT * FROM licenses').all();
    return new Response(JSON.stringify(results), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    console.error('handleLicenseList error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

async function handleLicenseRevoke(request: Request, env: Env): Promise<Response> {
  if (!verifyAdmin(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    const { key } = await request.json() as { key?: string };
    if (!key) {
      return new Response(JSON.stringify({ error: 'Missing key' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const result = await env.DB.prepare(
      "UPDATE licenses SET status = 'revoked', revokedAt = ? WHERE key = ? AND status = 'active'"
    ).bind(Date.now(), key).run();

    const revoked = result.meta.changes > 0;
    return new Response(JSON.stringify({ revoked }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    console.error('handleLicenseRevoke error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

async function handleLicenseSchema(request: Request, env: Env): Promise<Response> {
  if (!verifyAdmin(request, env)) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    await env.DB.exec(`
      CREATE TABLE IF NOT EXISTS licenses (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT UNIQUE NOT NULL,
        email TEXT NOT NULL,
        plan TEXT DEFAULT 'premium',
        status TEXT DEFAULT 'active',
        expiresAt INTEGER,
        createdAt INTEGER NOT NULL,
        revokedAt INTEGER
      );
    `);
    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (err: any) {
    console.error('handleLicenseSchema error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
