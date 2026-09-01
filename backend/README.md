# Monomail Push Backend

This Cloudflare Worker verifies Gmail or Outlook account ownership, manages provider subscriptions, and fans provider notifications out to every registered Android installation through FCM. Provider OAuth access tokens are used only for the current request and are never persisted.

## Protocol

`POST /register` and `POST /unregister` require `Authorization: Bearer <provider-access-token>` and `Content-Type: application/json`. There is no application-wide API key.

Registration body:

```json
{
  "accountId": "local-account-id",
  "email": "owner@example.com",
  "fcmToken": "firebase-registration-token",
  "installationId": "stable-random-installation-id",
  "provider": "gmail"
}
```

Unregistration body:

```json
{
  "accountId": "local-account-id",
  "email": "owner@example.com",
  "installationId": "stable-random-installation-id",
  "provider": "gmail"
}
```

The Worker validates Gmail tokens with Google userinfo and Outlook tokens with Microsoft Graph `/me`. The verified provider subject and email are bound to expiring, per-installation KV records. Unregistration removes only the matching installation and only stops/deletes the provider subscription when the last installation is removed.

Gmail watch creation happens after the installation mapping is stored. Watch `expiration` and `historyId` are retained. Outlook subscriptions are created and renewed with a random 256-bit `clientState`; callback notifications must match both the stored subscription ID and client state.

## Cloudflare

Create a KV namespace and set its ID in `wrangler.toml`:

```bash
npx wrangler kv namespace create FCM_TOKENS
```

Configure these Worker variables:

- `GCP_PROJECT_ID`: Firebase/GCP project ID.
- `PUBSUB_TOPIC`: full Gmail watch topic, such as `projects/project-id/topics/monomail-push`.
- `PUBSUB_AUDIENCE`: exact audience configured on the authenticated Pub/Sub push subscription, normally the Gmail webhook URL.
- `PUBSUB_SERVICE_ACCOUNT`: service account whose Google-signed OIDC token authenticates Pub/Sub pushes.
- `PUBSUB_SUBSCRIPTION`: full subscription name expected in the Pub/Sub message envelope.
- `WORKER_BASE_URL`: public HTTPS Worker base URL used for Graph callbacks.
- `INSTALLATION_TTL_SECONDS`: registration TTL, from 3600 through 7776000 seconds; 2592000 is the default.

Set the Firebase service account JSON as a Worker secret:

```bash
npx wrangler secret put GCP_SERVICE_ACCOUNT_KEY
```

### Durable FCM Delivery

A Cloudflare Queue is optional but recommended. Without one, the webhook sends synchronously and returns `503` for retryable FCM failures so Gmail/Graph can retry. Invalid/unregistered FCM tokens are removed in either mode.

Create the queue:

```bash
npx wrangler queues create monomail-push-delivery
```

Add bindings to `wrangler.toml`:

```toml
[[queues.producers]]
binding = "PUSH_QUEUE"
queue = "monomail-push-delivery"

[[queues.consumers]]
queue = "monomail-push-delivery"
max_batch_size = 10
max_retries = 5
```

## Gmail Pub/Sub

1. Create `PUBSUB_TOPIC` and grant `gmail-api-push@system.gserviceaccount.com` Pub/Sub Publisher on it.
2. Create a dedicated push-auth service account matching `PUBSUB_SERVICE_ACCOUNT`.
3. Allow the Pub/Sub service agent to mint OIDC tokens for that account by granting `roles/iam.serviceAccountTokenCreator` on it.
4. Create the push subscription with endpoint `https://your-worker.example/webhook/gmail`, OIDC authentication enabled, and the exact `PUBSUB_AUDIENCE` value.

Example:

```bash
gcloud pubsub subscriptions create monomail-push-worker \
  --topic=monomail-push \
  --push-endpoint=https://your-worker.example/webhook/gmail \
  --push-auth-service-account=pubsub-push@project-id.iam.gserviceaccount.com \
  --push-auth-token-audience=https://your-worker.example/webhook/gmail
```

The Worker validates the Google JWT signature against Google's JWKS and checks `alg`, issuer, exact audience, service-account email, `email_verified`, issue time, expiry, and the configured subscription name. Missing authentication configuration fails closed.

The Android Google token must include `gmail.modify` and `userinfo.email`. The app's daily renewal worker refreshes registration before Gmail's watch expires.

## Microsoft Graph

The Android Microsoft access token must allow reading mail and creating subscriptions for `/me/mailFolders('Inbox')/messages`. Registration creates a roughly 60-hour subscription. Daily Android renewal calls registration again; the Worker retains a healthy subscription, patches one nearing expiry, or recreates one no longer present. Last-installation unregistration deletes it using the client-provided token.

Graph's validation request to `POST /webhook/outlook?validationToken=...` is returned as plain text. Normal notifications do not trigger FCM unless stored `subscriptionId` and cryptographically random `clientState` metadata both match and remain unexpired.

## Android

Set only the backend URL in root `secrets.properties`:

```properties
PUSH_BACKEND_URL=https://your-worker.example
```

The Play Store flavor creates a stable random installation ID in app-private preferences. OAuth tokens are sent over HTTPS in the authorization header for registration and unregistration, but are not placed in request JSON or backend storage.

## Verification

```bash
npm test
npm run typecheck
npm run deploy
```
