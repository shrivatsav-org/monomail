# Monomail Push Notification Backend (Cloudflare Worker)

This lightweight Cloudflare Worker bridges push notifications from **Google Cloud Pub/Sub (Gmail)** and **Microsoft Graph Webhooks (Outlook)** to **Firebase Cloud Messaging (FCM)** on your Android device.

## Features
- **Gmail Push**: Leverages Gmail API `watch` to receive real-time updates via Google Cloud Pub/Sub.
- **Outlook Push**: Creates Microsoft Graph Webhook subscriptions for real-time inbox updates.
- **FCM Relay**: Relays data messages to your device via the FCM HTTP v1 API using WebCrypto JWT signing.
- **Serverless**: Zero maintenance, runs entirely on Cloudflare Workers edge network with Cloudflare KV storage.

---

## Setup Instructions for Self-Hosters

### 1. Pre-requisites
- A **Cloudflare** account.
- A **Google Cloud Platform (GCP)** project with Pub/Sub enabled and Firebase Cloud Messaging set up.
- A **Microsoft Azure App Registration** (if supporting Outlook).

### 2. Configure Cloudflare KV
1. Install Wrangler CLI: `npm install -g wrangler`
2. Login to Cloudflare: `wrangler login`
3. Create a KV namespace for storing FCM tokens:
   ```bash
   wrangler kv:namespace create FCM_TOKENS
   ```
4. Copy the output binding configuration into `wrangler.toml`.

### 3. Configure Gmail Pub/Sub
1. In your GCP Console, navigate to **Pub/Sub** > **Topics** and create a topic (e.g., `monomail-push`).
2. Give the Gmail API permission to publish to your topic by adding `gmail-api-push@system.gserviceaccount.com` as a Pub/Sub Publisher.
3. Create a **Subscription** for your topic:
   - Select **Push** delivery method.
   - Set the Endpoint URL to your Cloudflare Worker URL: `https://monomail-push.<your-subdomain>.workers.dev/webhook/gmail`
4. Update `GCP_PROJECT_ID` and `PUBSUB_TOPIC` in `wrangler.toml`.

### 4. Configure Firebase Cloud Messaging
1. In your GCP/Firebase Console, go to **Project Settings** > **Service Accounts**.
2. Generate a new private key JSON file.
3. Set this JSON file content as a secret in your Cloudflare Worker:
   ```bash
   wrangler secret put GCP_SERVICE_ACCOUNT_KEY
   ```
   *(Paste the entire JSON string when prompted).*

### 5. Configure Outlook Webhooks
1. In `wrangler.toml`, ensure `WORKER_BASE_URL` matches your deployed Cloudflare Worker base URL (e.g., `https://monomail-push.<your-subdomain>.workers.dev`).

### 6. Deploy the Worker
Run the following command to deploy your worker to Cloudflare's global network:
```bash
npm run deploy
```

---

## Configuring the Android App

To point the Monomail Android application to your self-hosted backend, add or update the `PUSH_BACKEND_URL` property in your `secrets.properties` file at the root of the Android project:

```properties
PUSH_BACKEND_URL=https://monomail-push.<your-subdomain>.workers.dev
```

The app will automatically register your device with your custom backend upon building the `playstore` release variant.
