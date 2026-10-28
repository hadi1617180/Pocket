# Clean Rebuild & Dual-Path Deployment Plan for Pocket

A complete rebuild and synchronization plan to ensure Pocket delivers the exact same modern, high-fidelity UI and complete feature set across both the Android app and the Web app, with instant dual-path deployment on GitHub Pages (serving identically at both the repository root and `/ios-pocket/`).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during clarification:
> - **URL Deployment Target**: Both repository root (`/`) and subfolder (`/ios-pocket/`) will host the exact same modern application identically on GitHub Pages.
> - **Rebuild Scope**: Clean rebuild keeping both the native Android app (Kotlin / Jetpack Compose) and the Web app (ES Modules, CSS3 Glassmorphism, PWA, Firebase) in 100% feature and visual parity.

- **Confirmed Decision 1**: Dual-path deployment — both `https://hadi1617180.github.io/Pocket/` and `https://hadi1617180.github.io/Pocket/ios-pocket/` will serve the full modern app without relying on cross-directory relative path imports.
- **Confirmed Decision 2**: Clean structure synchronization — purge legacy prototype remnants, bump Service Worker cache versions (`pocket-web-v6`), and ensure `.nojekyll` and cache busters are in place so GitHub Pages updates immediately.

---

## 1. Overview & Core Concept

- **What It Does**: Pocket is a personal finance, multi-wallet ledger, and Khata (debt/credit loan tracker) application with real-time Firebase cloud synchronization, Google authentication, monthly statements, recurring expense alerts, privacy controls, and tactile haptic feedback.
- **Target Audience / Persona**: Individuals and small business owners managing multiple payment methods (Cash, Banks, bKash, Nagad, Rocket, Transit cards, Savings) and personal/customer loans in BDT or foreign currencies.
- **Key Value**: A single unified database and visual identity whether accessed on an Android device or via any modern desktop/mobile web browser.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **First Launch & Google Authentication**:
   - Clean startup screen showcasing the Pocket brand logo, punchy headline, and one-tap "Sign in with Google" button.
   - Immediate transition to the Home screen upon successful authentication without getting stuck in a sync loop.
2. **Dashboard & Balances**:
   - Top bar: Circular brand logo + "Pocket" title + interactive Month/Year selector pill (`‹ Current Month ›`).
   - Primary Glassmorphic Card: Total wallet balance with one-tap privacy toggle (`👁` / `🙈`), profile avatar badge, and direct text figures for green monthly Income (`↗`) and red monthly Expenses (`↘`).
   - Wallet Carousel / List: Multi-currency cards with transaction statements, add wallet modal, and quick transfer options.
3. **Khata (Loans & Debts)**:
   - "You will get" (green) and "You will give" (red) net summary pills.
   - Contact-based khata cards displaying outstanding balance, transaction logs, and quick settlement dialogs.
4. **Settings & Account Management**:
   - User profile preview with Google account info.
   - Prominent **Delete Account** action with a safety confirmation dialog (completely removes Firestore user records and restarts as a new account).
   - High-intensity haptic vibration toggle, currency preferences, master budget goals, and developer credit card.

### Visual Identity & Theme

- **Aesthetic Direction**: Premium dark glassmorphic interface with deep black base (`#000000`), frosted surface cards (`rgba(20, 20, 22, 0.55)`), vibrant orange brand accent (`#F15A24`), emerald green for income (`#4ADE80`), and coral red for expenses (`#FF6B6B`).
- **Typography & Hierarchy**: Inter for clean numeric figures paired with JetBrains Mono for monetary precision and ledger calculations.
- **Motion & Feedback**: Punchy high-intensity tactile haptic vibrations on buttons, bottom tab switches, and transaction submissions; fluid liquid indicator on the bottom navigation bar.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Path Standalone Web Architecture**:
  - *Chosen Approach*: Maintain full, self-contained web builds at both root (`/`) and `/ios-pocket/` with local asset resolution (`assets/` and `ios-pocket/assets/`).
  - *Why*: Eliminates 404 errors caused by relative path traversal (`../app.js`) regardless of whether GitHub Pages is configured to serve the repo root or `/ios-pocket/`.
- **Decision 2: Service Worker Cache Invalidation (`v6`)**:
  - *Chosen Approach*: Bump service worker cache names to `pocket-web-v6`, add `?v=6` query params to stylesheets/scripts, and register an explicit `reg.update()` call on load.
  - *Why*: Solves browser caching where Chrome serves the stale October 27 prototype.
- **Decision 3: Synchronized Android & Web Codebase**:
  - *Chosen Approach*: Maintain native Android Jetpack Compose code with Room offline persistence and Cloud Firestore sync alongside the static PWA web app.
  - *Why*: Delivers an installable APK for Android users while providing browser access on any desktop or iOS device.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    User Presentation Layer                     │
├───────────────────────────────┬─────────────────────────────────┤
│       Android Native App      │             Web App             │
│   (Jetpack Compose + M3)     │  (ES6 Modules + Glassmorphic UI)│
│    - MainActivity / ViewModels│  - Dual Entry: / & /ios-pocket/ │
│    - Liquid Bottom Navigation │  - Service Worker (v6 Cache)    │
└───────────────┬───────────────┴────────────────┬────────────────┘
                │                                │
                ▼                                ▼
┌───────────────────────────────┬─────────────────────────────────┐
│   Offline Local Persistence   │    Cloud Persistence & Auth     │
│    - Android: Room Database   │    - Firebase Authentication    │
│    - Web: LocalStorage Cache  │    - Firestore Database Sync    │
└───────────────────────────────┴─────────────────────────────────┘
```

### Data Model & Entities

- **Wallets**: `id`, `name`, `type` (Cash, Bank, Mobile Banking, Savings, Card), `balance`, `currency`, `iconName`, `colorHex`, `includeInTotalBalance`.
- **Transactions**: `id`, `walletId`, `type` (INCOME, EXPENSE, TRANSFER), `amount`, `category`, `date`, `note`, `transferToWalletId`.
- **Khata Loans**: `id`, `personName`, `phone`, `type` (GIVE, GET), `amount`, `dueDate`, `isSettled`.
- **User Settings**: `userName`, `profilePhotoUri`, `currency`, `masterMonthlyBudget`, `hapticStrength`, `appLockPin`.

---

## 5. Execution Steps

1. **Clean Web App Duplication & Independence**:
   - Ensure `/index.html`, `/app.js`, `/styles.css`, and `/sw.js` at root are mirrored in `/ios-pocket/` with self-contained relative asset links.
   - Verify `.nojekyll` exists at root to ensure GitHub Pages does not ignore underscore files or assets.
2. **Service Worker & Cache Buster Polish**:
   - Ensure all references to scripts and stylesheets include `?v=6` and Service Worker automatically purges legacy caches.
3. **Android Build & Test Suite Verification**:
   - Verify Android project compiles cleanly via `compile_applet`.
   - Run unit and Robolectric tests (`gradle :app:testDebugUnitTest`) to ensure zero regressions in Google Sign-In, onboarding, or database sync.
4. **GitHub Pages Deployment Guidance**:
   - Provide clear, simple steps for pushing the unified codebase to GitHub so the live GitHub Pages link immediately reflects the modern design.
