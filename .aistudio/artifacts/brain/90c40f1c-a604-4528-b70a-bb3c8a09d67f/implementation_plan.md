# Pocket Web App 100% Parity Alignment

Ensure 100% visual and functional parity between the Pocket Android app and the Web version (`/docs`), specifically enforcing Free-tier wallet limits on Startup Page 2, adding the missing Box Color rainbow color wheel swatch in Settings, and synchronizing subscription limits and UI behaviors.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following requirements and user choices are confirmed for execution:

- **Confirmed Decision 1 (Startup Page 2 Free-Tier Limit)**: Free-tier users can select a maximum of 3 default starter wallets on Startup Page 2 (`FREE_DEFAULT_WALLETS_LIMIT = 3`). Selecting a 4th wallet will display the upgrade dialog/snackbar prompt: *"Free tier allows choosing any 3 default wallets. Upgrade to Pocket Premium for unlimited wallets."*
- **Confirmed Decision 2 (Startup Page 2 Initial Selection)**: Starter wallets on Startup Page 2 will start **all unchecked initially** (identical to the Android app), with balance input fields hidden until a wallet checkbox is ticked.
- **Confirmed Decision 3 (Settings Box Color Wheel)**: Add the interactive `conic-gradient` rainbow color wheel swatch to the Box Color row in Settings. For Free-tier users, it displays a lock icon and prompts the premium upgrade message; for Pro users, it opens an interactive color picker / custom hex input.
- **Confirmed Decision 4 (Subscription Limits Synchronization)**: Align all Free-tier limits in `docs/js/accounting.js` and `docs/js/app.js` with Android `AccountingEngine.kt`:
  - 10 transactions / day limit (`FREE_DAILY_TRANSACTIONS_LIMIT = 10`)
  - 3 default wallets limit (`FREE_DEFAULT_WALLETS_LIMIT = 3`)
  - 0 custom wallets for Free tier (`FREE_CUSTOM_WALLETS_LIMIT = 0`)
  - 2 distinct loan persons limit (`FREE_LOAN_PERSONS_LIMIT = 2`)
  - 2 recurring bills limit (`FREE_RECURRING_BILLS_LIMIT = 2`)
  - 1 custom category limit (`FREE_CUSTOM_CATEGORIES_LIMIT = 1`)

---

## 1. Overview & Core Concept

- **What It Does**: Resolves subtle discrepancies between the Android app and the Web version, ensuring both platforms behave identically under Free and Pro tiers, provide matching customization tools (Color Wheels for both Accent and Box colors), and present the exact same onboarding experience.
- **Target Audience**: Pocket users accessing their personal finance ledger seamlessly across Android devices and Web browsers via GitHub Pages (`hadi1617180.github.io/Pocket/`).
- **Key Value**: A consistent, trustworthy cross-platform experience where business logic, design tokens, and user tier limits never diverge.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Startup Page 2 Onboarding Flow**:
   - User signs in with Google or proceeds offline.
   - Profile avatar and Name input appear at the top.
   - **Starter Wallets List**: All wallets start unchecked.
   - User checks up to 3 wallets: clicking a wallet reveals the balance input with a clean placeholder.
   - User clicks a 4th wallet on Free tier: The checkbox is blocked, and an Upgrade Prompt/Snackbar appears explaining the 3-wallet Free limit.
   - User can uncheck a wallet and check a different one freely.

2. **Settings Box Color Wheel Flow**:
   - In Settings under "Background wallpaper, Opacity, Box Color & Box Opacity", the **Box Color** row displays:
     - 5 preset swatches: Carbon (`#141416`), Slate (`#1E293B`), Midnight (`#172554`), Emerald (`#14532D`), Burgundy (`#31102F`).
     - **Rainbow Color Wheel Swatch**: A circular swatch styled with `conic-gradient(red, yellow, lime, aqua, blue, magenta, red)`.
     - Free user clicks color wheel: Upgrade dialog opens stating *"Custom box color picker is a Pocket Premium feature. Carbon, Slate, and Box Opacity are free."*
     - Pro user clicks color wheel: Opens the HTML5 color picker or hex input, instantly updating the CSS variable `--box-rgb` and `--elevated-rgb` with glassmorphic transparency.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Aligning SubscriptionManager Constants Between Kotlin and JS**:
  - *Chosen Approach*: Update `docs/js/accounting.js` `SubscriptionManager` constants to strictly mirror `app/src/main/java/com/example/data/AccountingEngine.kt`.
  - *Why*: Prevents confusion where a Free-tier user could perform actions on the Web that are blocked in the Android app (e.g., creating custom wallets or exceeding daily transactions).
- **Decision 2: Startup Page 2 Wallet State Initialization**:
  - *Chosen Approach*: Set `onboardingSelectedIds` to `new Set()` by default instead of `Set([1, 2, 5])`, and require user intent to choose their desired wallets up to 3.
  - *Why*: Exactly reflects the Android app's `PocketRepository.defaultStarterWallets()` onboarding state.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                      User Action                       │
│    (Startup Page 2 Checkbox  /  Settings Box Wheel)    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                   PocketApp State                      │
│        AppState.onboardingSelectedIds (Set)            │
│        SubscriptionManager.isPro()                     │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
   [If free & count >= 3]         [If allowed]
              │                            │
              ▼                            ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│   Show Upgrade Dialog     │ │ Toggle State & Update    │
│   (3-Wallet Limit Msg)    │ │ CSS Tokens / UI Re-render│
└───────────────────────────┘ └──────────────────────────┘
```

### Component & File Mapping

1. **`docs/js/accounting.js`**:
   - Update `SubscriptionManager` constants (`FREE_DAILY_TRANSACTIONS_LIMIT = 10`, `FREE_DEFAULT_WALLETS_LIMIT = 3`, `FREE_CUSTOM_WALLETS_LIMIT = 0`, `FREE_LOAN_PERSONS_LIMIT = 2`, `FREE_RECURRING_BILLS_LIMIT = 2`, `FREE_CUSTOM_CATEGORIES_LIMIT = 1`).
   - Add `isStarterWallet(walletName)` helper to identify starter vs custom wallets.
2. **`docs/js/app.js`**:
   - Change `onboardingSelectedIds` initial state to empty `new Set()`.
   - Update `toggleOnboardingWallet(wId)` to check `!SubscriptionManager.isPro() && AppState.onboardingSelectedIds.size >= 3` and trigger `openUpgradeDialog()`.
   - Add subtitle indicator on Startup Page 2: *"Free tier: Choose any 3 default wallets (X/3)"*.
   - In `renderSettingsScreen()`, append the `RainbowColorWheelSwatch` `<label>` with `<input type="color">` and lock state to the Box Color row.
   - Enforce daily transaction limit (`10/day`) in `submitTxForm()` for Free tier.
