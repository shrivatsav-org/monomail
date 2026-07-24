# Privacy Policy

**Effective Date:** June 14, 2026

## Introduction
Welcome to Monomail. Monomail is an open-source student project designed to provide a distilled and minimalistic email client experience. This Privacy Policy explains how we handle, use, and protect your data when you use the Monomail application.

## 1. Scope of Application
Monomail uses the Google OAuth 2.0 API to access your Gmail account. The specific scope requested is:
`https://www.googleapis.com/auth/gmail.modify`

This scope is necessary for the core functionality of the application, which includes:
- Reading your emails to display them in the app.
- Organizing emails (e.g., archiving, deleting, marking as read).
- Sending emails on your behalf.

## 2. Data Storage, Processing, and Protection
**Monomail operates entirely locally on your device and employs strict data protection mechanisms for sensitive data.** 
- **No Third-Party Servers:** We do not own, operate, or use any third-party servers to process, store, or transmit your email data. 
- **Data Protection for Sensitive Data:** All sensitive data, including OAuth 2.0 access tokens, refresh tokens, and cached email content, is encrypted at rest. We utilize the **Android Keystore System** and **Encrypted DataStore/SharedPreferences** to ensure that your sensitive authentication credentials and personal emails are cryptographically protected from unauthorized access on your device.
- **Direct Communication:** The app communicates directly and securely (via HTTPS) between your device and Google's Gmail API servers.

## 3. Data Sharing and Disclosure
Because Monomail does not collect or transmit your data to any external servers (other than Google's own API), we do not and cannot share, sell, or disclose your personal information or email data to any third parties.

## 4. Open Source and Student Project Status
Monomail is an open-source project created by a student for educational purposes. The source code is publicly available for review, ensuring complete transparency regarding how your data is handled.

## 5. Your Consent
By using Monomail, you consent to the direct processing of your Gmail data on your local device as described in this Privacy Policy.

## 6. Changes to this Policy
We may update this Privacy Policy from time to time. Any changes will be reflected in the repository and within the app.

## 7. Contact
If you have any questions about this Privacy Policy, please refer to the project's public repository.
