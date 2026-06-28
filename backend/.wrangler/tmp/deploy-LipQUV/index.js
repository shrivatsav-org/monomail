var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/index.ts
var index_default = {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/register") {
      return await handleRegister(request, env);
    }
    if (request.method === "POST" && (url.pathname === "/webhook/gmail" || url.pathname === "/webhook")) {
      return await handleGmailWebhook(request, env);
    }
    if (url.pathname === "/webhook/outlook") {
      return await handleOutlookWebhook(request, env);
    }
    return new Response("Monomail Push Backend is running.", { status: 200 });
  }
};
async function handleRegister(request, env) {
  try {
    const data = await request.json();
    if (!data.accountId || !data.email || !data.fcmToken || !data.provider) {
      return new Response(JSON.stringify({ error: "Missing required fields" }), { status: 400 });
    }
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
    if (data.provider === "gmail" && data.accessToken) {
      const watchResp = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/watch", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${data.accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          topicName: env.PUBSUB_TOPIC,
          labelIds: ["INBOX"]
        })
      });
      if (!watchResp.ok) {
        const errorText = await watchResp.text();
        console.error("Gmail watch API failed:", errorText);
        return new Response(JSON.stringify({ error: "Gmail watch API failed", details: errorText }), { status: 500 });
      }
    } else if (data.provider === "outlook" && data.accessToken) {
      const notificationUrl = `${env.WORKER_BASE_URL.replace(/\/$/, "")}/webhook/outlook`;
      const expirationDateTime = new Date(Date.now() + 3 * 24 * 60 * 60 * 1e3).toISOString();
      const subResp = await fetch("https://graph.microsoft.com/v1.0/subscriptions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${data.accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          changeType: "created",
          notificationUrl,
          resource: "me/mailFolders('Inbox')/messages",
          expirationDateTime,
          clientState: data.accountId
          // Pass accountId in clientState to identify incoming webhook
        })
      });
      if (!subResp.ok) {
        const errorText = await subResp.text();
        console.error("Microsoft Graph subscription failed:", errorText);
        return new Response(JSON.stringify({ error: "Microsoft Graph subscription failed", details: errorText }), { status: 500 });
      }
    }
    return new Response(JSON.stringify({ success: true }), { status: 200, headers: { "Content-Type": "application/json" } });
  } catch (err) {
    console.error("handleRegister error:", err);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
}
__name(handleRegister, "handleRegister");
async function handleGmailWebhook(request, env) {
  try {
    const body = await request.json();
    if (!body.message || !body.message.data) {
      return new Response("Invalid Pub/Sub message", { status: 400 });
    }
    const decodedData = atob(body.message.data);
    const payload = JSON.parse(decodedData);
    const emailAddress = payload.emailAddress;
    if (!emailAddress) {
      return new Response("No email address in payload", { status: 400 });
    }
    const storedStr = await env.FCM_TOKENS.get(`email:${emailAddress}`);
    if (!storedStr) {
      console.warn(`No FCM token mapping found for email: ${emailAddress}`);
      return new Response("No token mapping found", { status: 200 });
    }
    const stored = JSON.parse(storedStr);
    await sendFcmMessage(env, stored.fcmToken, stored.accountId, "gmail");
    return new Response("OK", { status: 200 });
  } catch (err) {
    console.error("handleGmailWebhook error:", err);
    return new Response(err.message, { status: 500 });
  }
}
__name(handleGmailWebhook, "handleGmailWebhook");
async function handleOutlookWebhook(request, env) {
  const url = new URL(request.url);
  const validationToken = url.searchParams.get("validationToken");
  if (validationToken) {
    return new Response(validationToken, { status: 200, headers: { "Content-Type": "text/plain" } });
  }
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }
  try {
    const body = await request.json();
    if (body && body.value && Array.isArray(body.value)) {
      for (const notification of body.value) {
        const accountId = notification.clientState;
        if (!accountId) continue;
        const storedStr = await env.FCM_TOKENS.get(`account:${accountId}`);
        if (!storedStr) continue;
        const stored = JSON.parse(storedStr);
        await sendFcmMessage(env, stored.fcmToken, stored.accountId, "outlook");
      }
    }
    return new Response("OK", { status: 200 });
  } catch (err) {
    console.error("handleOutlookWebhook error:", err);
    return new Response(err.message, { status: 500 });
  }
}
__name(handleOutlookWebhook, "handleOutlookWebhook");
async function sendFcmMessage(env, fcmToken, accountId, provider) {
  if (!env.GCP_SERVICE_ACCOUNT_KEY) {
    console.error("GCP_SERVICE_ACCOUNT_KEY secret is not set.");
    return;
  }
  try {
    const serviceAccount = JSON.parse(env.GCP_SERVICE_ACCOUNT_KEY);
    const accessToken = await getGoogleOAuthAccessToken(serviceAccount);
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id || env.GCP_PROJECT_ID}/messages:send`;
    const fcmResp = await fetch(fcmUrl, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${accessToken}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: {
            accountId,
            provider,
            syncRequired: "true",
            timestamp: Date.now().toString()
          }
        }
      })
    });
    if (!fcmResp.ok) {
      const errText = await fcmResp.text();
      console.error("FCM send failed:", errText);
    }
  } catch (err) {
    console.error("sendFcmMessage error:", err);
  }
}
__name(sendFcmMessage, "sendFcmMessage");
async function getGoogleOAuthAccessToken(serviceAccount) {
  const header = {
    alg: "RS256",
    typ: "JWT",
    kid: serviceAccount.private_key_id
  };
  const iat = Math.floor(Date.now() / 1e3);
  const exp = iat + 3600;
  const claimset = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp,
    iat
  };
  const encodedHeader = urlSafeBase64Encode(JSON.stringify(header));
  const encodedClaimset = urlSafeBase64Encode(JSON.stringify(claimset));
  const toSign = `${encodedHeader}.${encodedClaimset}`;
  const privateKey = importPrivateKey(serviceAccount.private_key);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    privateKey,
    { name: "RSASSA-PKCS1-v1_5", hash: { name: "SHA-256" } },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(toSign)
  );
  const encodedSignature = urlSafeBase64Encode(signature);
  const jwt = `${toSign}.${encodedSignature}`;
  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`
  });
  const tokenData = await tokenResp.json();
  return tokenData.access_token;
}
__name(getGoogleOAuthAccessToken, "getGoogleOAuthAccessToken");
function urlSafeBase64Encode(data) {
  let base64 = "";
  if (typeof data === "string") {
    base64 = btoa(data);
  } else {
    const bytes = new Uint8Array(data);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    base64 = btoa(binary);
  }
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
__name(urlSafeBase64Encode, "urlSafeBase64Encode");
function importPrivateKey(pem) {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
__name(importPrivateKey, "importPrivateKey");
export {
  index_default as default
};
//# sourceMappingURL=index.js.map
