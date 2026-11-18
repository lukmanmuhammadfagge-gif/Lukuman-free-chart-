# LUKUMAN FREE CHAT — Firebase Configuration Guide

**Package ID:** `com.lukuman.freechat`  
**Firebase Project ID:** `gen-lang-client-0536418440`  
**Firestore Named Database ID:** `ai-studio-android-lukumanf-76649950-d474-4e03-a2bd-b1f4a5219501`  
**Founder & Developer:** Lukuman Muhammad Abubakar Fagge

## 1. Provisioned Services
- **Cloud Firestore (Enterprise Edition)** using the named database ID in `app/src/main/res/values/firebase_applet_config.xml`.
- **Firebase Authentication** with **Google Sign-In** (via Jetpack Credential Manager).
- **Security Rules** defined in `firestore.rules` and verified against the local Firebase Emulator Suite (`firestore.test.js` and `ChatRepositoryRuleTest.kt`).

## 2. Manual Console Steps for Optional Extended Providers
To activate additional Firebase services in your Firebase Console (`https://console.firebase.google.com/project/gen-lang-client-0536418440`):

1. **Phone Auth (SMS OTP for +234 and International Numbers):**
   - Go to **Authentication -> Sign-in method -> Phone** and enable it.
   - Ensure **Play Integrity API** (Google Play) and/or **reCAPTCHA** are enabled in Google Cloud Console for `com.lukuman.freechat` with your release/debug SHA-1 and SHA-256 fingerprints registered.
   - Until verified via `PhoneAuthProvider`, phone numbers remain strictly marked **Unverified** in both Firestore security rules and the UI.

2. **Firebase Storage (Images, Documents, Voice Notes, Avatars):**
   - Go to **Storage** in Firebase Console and click **Get started** for bucket `gen-lang-client-0536418440.firebasestorage.app`.
   - Deploy `storage.rules` (`firebase deploy --only storage`).

3. **Firebase Cloud Messaging (Push Notifications):**
   - Go to **Cloud Messaging** in Firebase Console (Android `FreeChatMessagingService` is already registered in `AndroidManifest.xml` and syncs FCM tokens to `/users/{uid}`).

4. **Firebase App Check:**
   - Debug builds automatically install `DebugAppCheckProviderFactory`. Register your debug token in **App Check -> Apps** and configure **Play Integrity** for release builds.

## 3. Security Hygiene
- **Never** embed Firebase Admin SDK credentials, service account JSON files, or private keys in the Android app.
- All Firestore collections enforce authenticated access, owner-only private PII isolation (`/users/{uid}`), participant-scoped conversations, and admin/member-scoped groups.
