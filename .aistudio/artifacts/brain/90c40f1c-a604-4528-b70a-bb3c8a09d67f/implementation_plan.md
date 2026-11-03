# Web Google Sign-In Permission Fix & Code Protection Strategy

Resolve the Firestore "Missing or insufficient permissions" error during Google Sign-In on the Web application by unifying the Web sync engine with Android's Firestore subcollection architecture, and provide a comprehensive code & business model protection strategy.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following confirmed decisions will be executed:

- **Confirmed Decision 1 (Firestore Schema Alignment)**: Rewrite `docs/js/firebase-sync.js` to match Android's `PocketCloudSyncRepository.kt` document hierarchy (`users/{userId}` for profile, and subcollections `wallets`, `categories`, `transactions`, `transfers`, `loans`, `loan_activities`, `balance_adjustments`, `recurring_transactions`, `savings_goals`). Remove invalid references to `/users/{uid}/profile/main` and `/users/{uid}/backups/latest`.
- **Confirmed Decision 2 (Security Rules Audit & Deployment)**: Ensure `firestore.rules` allows all profile update fields (`boxColorHex`, `boxOpacity`, `appLanguage`, `dateFormatPattern`) and deploy via `deploy_firebase`.
- **Confirmed Decision 3 (Code Protection & Anti-Piracy Architecture)**: Provide a 3-pillar protection system:
  1. Making the GitHub repository **Private** (or hosting the Web app via a clean frontend-only repository/branch).
  2. Enabling **R8 / ProGuard bytecode obfuscation and resource shrinking** in `app/build.gradle.kts` for release builds.
  3. Enforcing **Server-side Zero-Trust Firestore Security Rules** for Activation Codes and Cloud Sync.

---

## 1. Overview & Core Concept

- **Problem 1 (Web Sign-In Permission Error)**: When users click "Sign in with Google" on `hadi1617180.github.io/Pocket/`, Google Authentication succeeds, but the subsequent Firestore snapshot fetch fails with *"Missing or insufficient permissions"* because `firebase-sync.js` requested non-existent paths (`/profile/main` and `/backups/latest`) disallowed by security rules.
- **Problem 2 (Source Code & APK Security)**: With the repository public, any user could view the source code, offline activation code hashes, or decompile the APK to bypass Free/Paid limitations.
- **Solution**: Unify Firestore data operations to the verified Android schema, deploy updated security rules, and activate R8 code obfuscation while guiding the user on repository privacy settings.

---

## 2. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│               Google Sign-In Success                   │
│             auth.currentUser (Google UID)              │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            Firestore Security Rules Contract           │
│  /users/{userId}  (UserProfileDoc)                     │
│  /users/{userId}/wallets/{walletId}                    │
│  /users/{userId}/categories/{categoryId}              │
│  /users/{userId}/transactions/{txId}                   │
│  /users/{userId}/loans/{loanId}                        │
└───────────────────────────┬────────────────────────────┘
                            │
        ┌───────────────────┴────────────────────┐
        ▼                                        ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│       Android Client          │ │          Web Client           │
│ (PocketCloudSyncRepository.kt)│ │      (firebase-sync.js)       │
│                               │ │                               │
│ - Reads doc("users/$uid")     │ │ - Reads doc(db, 'users', uid) │
│ - Reads subcollections        │ │ - Reads subcollections        │
│ - Direct 1:1 real-time sync   │ │ - Direct 1:1 real-time sync   │
└───────────────────────────────┘ └───────────────────────────────┘
```

---

## 3. Implementation Steps

### Step 1: Update `firestore.rules`
- Add missing keys to the `affectedKeys().hasOnly([...])` whitelist on `/users/{userId}` update rules:
  `'boxColorHex', 'boxOpacity', 'appLanguage', 'dateFormatPattern'`
- Verify rules and deploy to live Firebase using `deploy_firebase`.

### Step 2: Refactor `docs/js/firebase-sync.js`
- **User Profile**: Read and write directly at `/users/{userId}` with envelope fields (`userId`, `displayName`, `email`, `photoUrl`, `currencyCode`, `currencySymbol`, `themeMode`, `boxColorHex`, `boxOpacity`, `appLanguage`, `syncVersion`, `createdAt`, `updatedAt`).
- **Subcollections**: Fetch and save individual entities under `/users/{userId}/wallets`, `categories`, `transactions`, `transfers`, `loans`, `loan_activities`, `balance_adjustments`, `recurring_transactions`, `savings_goals`.
- **Live Sync**: Listen to real-time updates on `/users/{userId}` and subcollections so Android and Web stay in sync seamlessly without permission denials.

### Step 3: APK Obfuscation & Security Hardening
- Configure `app/build.gradle.kts`:
  - Enable `isMinifyEnabled = true` and `isShrinkResources = true` in the `release` build type with standard `proguard-android-optimize.txt` and `proguard-rules.pro`.
- Add ProGuard rules for Kotlin Coroutines, Firebase, and kotlinx.serialization.

### Step 4: Step-by-Step Security Guide for the User
- Provide clear Bengali instructions on making the GitHub repo Private while keeping GitHub Pages active, and how R8 ProGuard protects the Android APK against tampering.
