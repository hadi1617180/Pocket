# Pocket — Two-Stage Startup & Google Account Lifecycle Plan

> [!IMPORTANT]
> **User Review & Critical Decisions**
> - **Two-Page Startup Onboarding**: Startup is split into **Page 1 (Welcome & Authentication)** featuring the centered large Pocket branding and two entry actions, and **Page 2 (Profile & Wallet Setup)** with the top Google sign-in card removed.
> - **Offline-to-Cloud Wipe Confirmation**: Signing in with Google from Settings while in offline mode displays a confirmation warning dialog before permanently deleting local device data; existing Google accounts restore directly to Home, while brand-new Google accounts proceed to **Startup Page 2** to configure starter wallets.
> - **Full Device Cache Reset on Sign-Out**: Signing out of a Google account from Settings wipes cached local financial records and returns immediately to **Startup Page 1**.

---

## 1. Overview & Core Concept

Pocket's onboarding and account lifecycle will transition to a clear, two-stage startup architecture that cleanly separates **authentication choice** from **initial wallet and profile configuration**, while enforcing strict isolation between offline local usage and Google Cloud accounts:

- **Startup Page 1 (Welcome & Identity Gate)**: Presents a bold, centered hero layout with the Pocket logo, app title, and `"App developed by Abdullahil Hadi"` credit, paired with two primary actions: **Sign in with Google** and **Continue without logging in**.
- **Startup Page 2 (Profile, Wallets & Existing Loans Setup)**: Retains the streamlined setup experience (profile name/photo, starter wallets with opening balances, expandable wallet list, existing loans, and bottom **Get started** button) without the redundant Google sign-in card at the top.
- **Deterministic Account Switching in Settings**: Eliminates data-merging ambiguity by warning offline users before wiping local data upon Google Sign-In, routing brand-new cloud accounts to Startup Page 2 for wallet setup, and clearing cached device data back to Startup Page 1 whenever a signed-in user signs out.

---

## 2. User Experience & Visual Design

### Visual Layout & Component Hierarchy

- **Startup Page 1 (Centered Hero Welcome)**
  - **Hero Branding Block**: Vertically centered in the upper-middle viewport featuring the custom Pocket adaptive/accent logo (`88.dp`), large display title **Pocket**, and prominent subtitle **"App developed by Abdullahil Hadi"**.
  - **Dual Action Stack**: Positioned directly beneath the hero branding with generous spacing and minimum `48.dp` touch targets:
    1. **Sign in with Google** (Primary filled button with Google Cloud icon and loading indicator during credential resolution & cloud lookup).
    2. **Continue without logging in** (Secondary outlined button that advances directly to Startup Page 2).
- **Startup Page 2 (Wallet & Profile Setup)**
  - **Top Header**: Compact Pocket logo + title + developer credit (with a back action to return to Startup Page 1 if the user wants to switch entry modes before completing setup).
  - **Setup Sections**: Editable user name & profile photo row (pre-filled from Google profile if signed in on Page 1), starter wallet checklist with inline opening balances (`BDT ৳`), expandable "Show more wallets" & "Existing loan" sections, and the pinned bottom **Get started** button.
  - **Clean Top Layout**: The previous top "Sign in with Google" card is completely removed from Page 2.

### End-to-End Navigation & Account Flows

| User Action | Account / Data Condition | Resulting System Behavior & Destination |
| :--- | :--- | :--- |
| **Page 1 → "Sign in with Google"** | Google account has existing cloud data | Restores cloud wallets, transactions, loans, and settings into local cache and navigates directly to **Home (Wallets Overview)**. |
| **Page 1 → "Sign in with Google"** | Brand-new Google account (no cloud data yet) | Pre-populates user name & photo from Google account and advances to **Startup Page 2** to set up starter wallets; pushes to cloud on **Get started**. |
| **Page 1 → "Continue without logging in"** | Offline session | Advances directly to **Startup Page 2** to set up profile and starter wallets locally. |
| **Settings → "Sign in with Google"** (while offline) | Offline local data present | Shows a confirmation warning dialog stating all local data will be permanently deleted. On confirm: wipes local data, completes Google Sign-In, and goes to **Home** (if cloud data exists) or **Startup Page 2** (if brand-new Google account). |
| **Settings → "Sign Out"** (while signed in) | Active Google Cloud session | Signs out of Firebase Auth & Credential Manager, clears all cached device data, and returns immediately to **Startup Page 1**. |

---

## 3. Key Product Decisions & Trade-Offs

- **Zero Local-to-Cloud Merging**:
  - *Decision*: Local offline data is never merged into a Google account when signing in later from Settings.
  - *Rationale*: Prevents duplicate wallets, double-counted opening balances, and accidental contamination of an existing cloud ledger with temporary offline test entries.
- **Pre-Wipe Warning Dialog in Settings**:
  - *Decision*: Offline users tapping **Sign in with Google** in Settings must explicitly confirm a warning dialog before local data is deleted, and if they cancel during the Google credential prompt or confirmation step, their offline data remains untouched.
  - *Rationale*: Protects users from accidental taps while keeping the account-based storage model strict and predictable.
- **Smart Routing for New vs. Returning Google Accounts**:
  - *Decision*: Signing in with a brand-new Google account (whether from Startup Page 1 or after wiping offline data in Settings) routes the user to **Startup Page 2** (`hasCompletedOnboarding = false`, `onboardingStep = PAGE_2`) rather than dropping them into an empty Home screen with zero wallets.
  - *Rationale*: Ensures every new cloud user gets the guided wallet and opening-balance setup experience.

---

## 4. Technical Architecture & Data Strategy

### Onboarding & Auth State Machine

```
                      ┌─────────────────────────────────────────┐
                      │            STARTUP PAGE 1               │
                      │   Logo · "Pocket" · Abdullahil Hadi     │
                      │  [Sign in with Google]  [Continue...]   │
                      └───────────┬─────────────────────┬───────┘
                                  │                     │
                    Sign in with  │                     │ Continue without
                       Google     ▼                     │   logging in
                      ┌───────────────────────┐         │
                      │ Check Firestore Cloud │         │
                      │   Snapshot for UID    │         │
                      └───┬───────────────┬───┘         │
        Has Cloud Data    │               │ New Account │
               ┌──────────┘               └──────┬──────┘
               ▼                                 ▼
┌──────────────────────────────┐  ┌─────────────────────────────────────────┐
│     HOME / WALLETS SCREEN    │  │             STARTUP PAGE 2              │
│                              │◄─┤  Profile · Starter Wallets · Loans      │
│ • Signed-In: Sign Out clears │  │  (No Google Sign-In Card at Top)        │
│   device cache ──▶ Page 1    │  │  [Get started] ──▶ Saves & Opens Home   │
│ • Offline: Sign In warns &   │  └─────────────────────────────────────────┘
│   wipes local data ──▶ Cloud │                 ▲
│   Home or Page 2 (if new)    │─────────────────┘
└──────────────────────────────┘   (If offline user signs in with brand-new
                                    Google account after confirming wipe)
```

### State & Persistence Synchronization

1. **Two-Step Onboarding State Tracking**:
   - Track the active startup stage (`PAGE_1_WELCOME` vs. `PAGE_2_SETUP`) in `PocketUiState` alongside `settings.hasCompletedOnboarding`.
   - Whenever `settings.hasCompletedOnboarding` is `false`, `MainActivity` renders the two-stage startup flow starting on **Page 1** unless transitioned to **Page 2** via "Continue without logging in" or via a newly authenticated Google account that has no prior cloud setup.
2. **Cloud Lookup & New-Account Detection**:
   - When `onGoogleUserSignedIn` runs (from Startup Page 1 or from Settings after offline wipe confirmation), query `PocketCloudSyncRepository.pullCloudPayloadIfPresent(uid)`.
   - **Existing Account** (`remoteSnapshot != null && (remoteSnapshot.profile.hasCompletedOnboarding || remoteSnapshot.payload.wallets.isNotEmpty())`): Atomically replace local database contents with the cloud snapshot, mark `hasCompletedOnboarding = true`, start real-time cloud profile observation, and navigate to `PocketScreen.Wallets`.
   - **New Account** (`remoteSnapshot == null` or empty un-onboarded profile): Clear local financial tables, retain default categories, populate `userName` and `profilePhotoUri` from the Google account, set `hasCompletedOnboarding = false`, and set the startup stage to **Startup Page 2** so the user can pick their starter wallets. When they tap **Get started**, `completeOnboarding` saves their chosen wallets and immediately pushes the initial snapshot to Firestore.
3. **Settings Sign-Out & Offline Sign-In Confirmation**:
   - **Offline Sign-In Warning**: In Settings, when `googleAccountEmail` is null, tapping **Sign in with Google** first opens a Material 3 confirmation dialog explaining that all local offline data will be permanently deleted upon signing in. Only after confirming does the Google credential flow launch and replace local data.
   - **Sign-Out Device Wipe**: Calling `signOutGoogle` cancels active Firestore sync jobs, signs out of Firebase Auth and Credential Manager, atomically clears all local Room tables (`wallets`, `transactions`, `transfers`, `loans`, `loanActivities`, `adjustments`, `recurringTransactions`, `savingsGoals`, and resets `AppSettingsEntity` with `hasCompletedOnboarding = false`), cancels scheduled local notifications, refreshes widgets, and resets the startup flow to **Startup Page 1**.
