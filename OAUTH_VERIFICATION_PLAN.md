# OAuth Verification Action Plan

Based on the feedback from the Google Third-Party Data Safety Team, here is the plan to resolve the issues and get Monomail verified.

## 1. Privacy Policy Update
**Issue:** The privacy policy lacks specific data protection mechanisms for sensitive data.
**Action:** Update `PRIVACY_POLICY.md` (and wherever it is hosted) to explicitly detail how sensitive data is protected.
* Add a dedicated **Data Protection & Security** section.
* Explicitly state that OAuth tokens and cached emails are encrypted at rest using Android's EncryptedSharedPreferences/DataStore and the Android Keystore system.
* Re-publish the privacy policy so the URL reflects these changes.

## 2. Cloud Console Scope Discrepancy Alignment
**Issue:** The codebase requests `gmail.modify`, but the Cloud Console configuration likely includes `gmail.modify`, `gmail.readonly`, and `gmail.send`, creating a discrepancy.
**Action:**
* Go to the **Google Cloud Console** > **APIs & Services** > **OAuth consent screen** > **Edit App**.
* Navigate to the **Scopes** step.
* **Remove** `https://www.googleapis.com/auth/gmail.readonly` and `https://www.googleapis.com/auth/gmail.send`.
* Keep **only** `https://www.googleapis.com/auth/gmail.modify` (which implicitly covers reading, sending, and modifying labels). This will ensure a strict string match with the active codebase request.

## 3. Scope Justification (For the Email Reply)
**Issue:** The previous justification didn't sufficiently explain why `gmail.modify` is needed over narrower scopes.
**Action:** Provide a clear justification bridging the gap between backend ops and UX:
> "Monomail is a full-featured email client. We require `https://www.googleapis.com/auth/gmail.modify` because users can archive, trash, and mark emails as read within the app. Narrower permissions like `gmail.readonly` and `gmail.send` only permit reading and sending, and would break the app's ability to organize the user's inbox (which requires modifying labels). `gmail.modify` is the narrowest scope that allows for reading, sending, and managing email labels."

## 4. Test Credentials & Demo Video
**Action:**
* Create a fresh test Gmail account (e.g., `monomail.test@gmail.com`) with a password.
* **Crucial:** Turn off 2-Step Verification and remove any phone number/recovery email requirements for this test account so Google reviewers aren't blocked.
* Record a **new demonstration video**:
    1. Show the app launching and initiating the Google Sign-in process.
    2. Clearly show the client ID in the URL bar during the OAuth flow.
    3. Show the OAuth Consent screen displaying the requested scope (`gmail.modify`).
    4. Log in with the test account and demonstrate the core features (reading an email, sending an email, and archiving/deleting an email to prove `modify` is needed).
* Upload the video to YouTube (Unlisted) or Google Drive (Anyone with link can view).

## 5. Email Reply Template
Once the above is done, reply to the Google team with the following template:

```text
Hello Third-Party Data Safety Team,

Thank you for your review. We have addressed the items as requested:

1. Privacy Policy Requirement:
We have updated our Privacy Policy to explicitly detail our data protection mechanisms for sensitive data (using Android Keystore and Encrypted DataStore). You can view the updated policy here: [Link to Privacy Policy]

2. Scope Discrepancy & Justification:
We have audited our codebase and Cloud Console. Our app only requires `https://www.googleapis.com/auth/gmail.modify`. We have removed `gmail.readonly` and `gmail.send` from our Cloud Console configuration to ensure a strict string match with our codebase. 
Justification for `gmail.modify`: Monomail is an email client that allows users to organize their inbox. We need `gmail.modify` because users can archive, delete, and mark emails as read (which requires modifying labels). Narrower scopes like `gmail.readonly` and `gmail.send` do not allow label modification.

3. Demonstration Video:
Here is a new video demonstrating the OAuth flow (showing the client ID), the requested scope, and the app's functionality proving the need for `gmail.modify`: [Link to Video]

4. Test Credentials:
Email: [Test Email Address]
Password: [Test Password]
Navigation steps: 
1. Open the app and tap "Sign in with Google".
2. Enter the test credentials provided above.
3. Grant the requested permissions.
4. You will be routed to the inbox where you can test reading, sending, and archiving emails.
Note: All authentication blockers (2FA, phone verification) have been disabled for this account.

Please let us know if you need anything else.

Best regards,
[Your Name/Team]
```
