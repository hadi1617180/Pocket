# Pocket Web App 1:1 Parity Upgrade (Features, Icons, Accounting & Cloud Sync)

Upgrade the Pocket Web App (`/docs`) so every feature, vector icon, sub-wallet accounting formula, Undo system, global Floating Action Button, and real-time Firestore Cloud Sync matches the Android application 1:1.

---

## User Review & Confirmed Decisions

> [!IMPORTANT]
> **Confirmed Choices from Phase 1:**
> - **Full 9-Collection Real-Time Cloud Sync**: The Web app will sync all 9 Firestore subcollections (`wallets`, `categories`, `transactions`, `transfers`, `loans`, `loan_activities`, `balance_adjustments`, `recurring_transactions`, `savings_goals`) plus `fundFeatureEnabled` and `fundResetAtMillis` so actions, Fund Returns, and Undo operations sync seamlessly between Android and Web.
> - **1:1 UI with Platform-Clean Settings**: The Web app will mirror the Android UI 1:1 while keeping Android-only OS hardware settings (Home Screen Widget setup) hidden on Web.

---

## 1. Overview & Gap Analysis Discovered in Web App

A deep audit comparing the Android app against the Web app uncovered 7 areas where the Web version fell behind recent Android updates:

1. **Vector Wallet Icons & Transparent Tinted App Logo**:
   - **Android**: Uses crisp monochromatic vector icons (`ic_wallet_vec_cash`, `bank`, `mtb`, `brac`, `bkash`, `nagad`, `rocket`, `rapid_pass`, `card`, `savings`) and a transparent circular Pocket logo dynamically tinted to the user's active accent color.
   - **Web Gap**: Still used raster PNG filter matrices and a square drop-shadow around the logo.
   - **Fix**: Embed all 10 exact vector SVG paths from Android's `ic_wallet_vec_*.xml` drawables and tint the transparent Pocket logo with the active accent color.

2. **Top Header Undo Button & Persistent Undo Engine**:
   - **Android**: Features an `Undo` icon button placed directly before the `Settings` gear icon in the top-right header of the Wallets screen, backed by an in-memory Undo stack + database fallback that reverts the latest transaction, transfer, loan entry, or balance adjustment and shows `"Undone: ..."` in the bottom Snackbar.
   - **Web Gap**: Missing both the Undo header button, the Settings gear button in the top-right header, and the Undo engine.
   - **Fix**: Add the exact Undo icon button and Settings gear button to the Wallets header and port the full Undo stack + database fallback engine to Web.

3. **Global Floating Action Button (`+`) Across All 4 Main Tabs & `180px` Bottom Padding**:
   - **Android**: Shows the expandable `+` FAB (`Transfer`, `Income`, `Expense`) at the exact same bottom-right position across all 4 main navigation dock tabs (`Wallets`, `History`, `Reports`, `Loans`) with `180px` bottom scroll padding.
   - **Web Gap**: Only showed the FAB on the first tab (`Balances`), used `Balances` instead of `Wallets` as the tab label, and only had `110px` bottom padding.
   - **Fix**: Render the global FAB in the exact same position across all 4 main tabs (`Wallets`, `History`, `Reports`, `Loans`) with `Transfer`, `Income`, and `Expense` quick actions and `180px` bottom padding on every page.

4. **History Date Grouping & Multi-Line Transfer Route Subtitles**:
   - **Android**: Groups `History` entries under daily date headers (`09 Nov 2026`) with daily net totals and omits the redundant date from each entry's subtitle. In ungrouped lists (Wallet Detail / Fund Activity History), when a route contains `→` (e.g., `Cash-Personal → Cash-Fund`), the date (`09 Nov 2026`) is placed on the **next line** so neither the route nor the date is truncated.
   - **Web Gap**: Rendered a flat ungrouped list in History with the date repeated on every line and single-line truncated routes.
   - **Fix**: Group History entries by date header, remove the redundant date from History item subtitles, and place the date on the next line below `→` transfer routes in Wallet/Fund detail histories.

5. **Virtual Fund, Sub-Wallet Tabs (`All` / `Personal` / `Fund`), Date in Return to Fund & Cycle Reset**:
   - **Android**:
     - Settings includes the **Enable Fund Feature** toggle.
     - Wallet Detail includes the 3-tab sub-wallet switcher (`All (Total)`, `Wallet-Personal`, `Wallet-Fund`), collapsible `Formula Breakdown` card, `AdjustPersonal`/`AdjustFund`, and `Clear Fund to 0` cycle reset (`fundResetAtMillis`).
     - `Return to Fund` dialog includes a **Date selector** at the top, supports same-wallet `Cash-Personal → Cash-Fund` returns, and displays `Cash-Personal → Cash-Fund` in Main, Fund, and Wallet histories.
   - **Web Gap**: Missing the `Enable Fund Feature` toggle in Settings, missing the 3-tab sub-wallet view and formula breakdown in Wallet Detail, missing `fundResetAtMillis` support, and missing the Date field in `Return to Fund`.
   - **Fix**: Upgrade Web's `AccountingEngine` and Wallet/Fund Detail screens with the 3-tab sub-wallet switcher, formula breakdown, `Clear Fund to 0` cycle reset, Date picker in `Return to Fund`, and same-wallet `Personal → Fund` transfer support.

6. **Loans & Khata Sub-Wallet Support (`[FUND_LOAN]` / `[FUND_LOAN_BORROW]`)**:
   - **Android**: Allows recording loans and repayments from/to either `Personal` or `Fund` sub-wallets, and isolates official Fund loan interest from Personal monthly reports.
   - **Web Gap**: Only supported basic wallet selection on loans without Fund sub-wallet tags or disbursement activity tracking.
   - **Fix**: Port full `DISBURSEMENT` / `REPAYMENT` loan activity tracking and Fund loan sub-wallet options to Web.

7. **Full 9-Collection Real-Time Firestore Cloud Sync**:
   - **Android**: Syncs all 9 subcollections (`wallets`, `categories`, `transactions`, `transfers`, `loans`, `loan_activities`, `balance_adjustments`, `recurring_transactions`, `savings_goals`) with deletion reconciliation and `fundFeatureEnabled`.
   - **Web Gap**: `pushSnapshot` in `firebase-sync.js` omitted `transfers`, `balance_adjustments`, `recurring_transactions`, and `savings_goals` when pushing to Firestore, and did not delete removed transactions/transfers/wallets on sync.
   - **Fix**: Upgrade `firebase-sync.js` so `pushSnapshot` and `pullSnapshot` sync and reconcile all 9 subcollections and `fundFeatureEnabled` 1:1 with Android's `PocketCloudSyncRepository`.

---

## 2. User Experience & Visual Design

- **Top Header Bar (`Wallets` Tab)**:
  - Left: Accent-tinted circular transparent Pocket logo + `Pocket` title + `Free` badge (when on Free tier).
  - Right: Plain `Undo` icon button (`undo`) placed immediately before the `Settings` icon button (`settings`).
- **Hero Summary Card (`Wallets` Tab)**:
  - Displays `Total Personal Balance` (when Fund is enabled) or `Total Wallet Balance`, privacy eye toggle, Month selector pill, right-aligned circular User Profile Avatar + Name, and the 2x2 metric grid (`Income`, `Expenses`, `Fund Balance`, `Borrowed Due` when Fund is active).
- **Wallet Detail 3-Tab Sub-Wallet Switcher**:
  - Segmented pill bar at the top of any Fund-enabled wallet: `All (Total)` | `Wallet-Personal` | `Wallet-Fund`.
  - Expandable `Formula Breakdown` showing exact inflows/outflows for the selected sub-wallet.
  - Running Balance History with search bar and multi-line date below `→` transfer routes.

---

## 3. Technical Architecture & Parity Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Pocket Web App (/docs PWA)                           │
├──────────────────────┬───────────────────────────┬───────────────────────┤
│   UI & SVG Icons     │   1:1 Accounting Engine   │  Firestore Cloud Sync │
│  • 10 Vector Wallet  │  • Sub-Wallet Deltas      │  • All 9 Collections  │
│    SVGs (ic_wallet)  │    (Total/Personal/Fund)  │    (Wallets, Cats,    │
│  • Header Undo +     │  • Same-Wallet Transfers  │     Txs, Transfers,   │
│    Settings Icons    │    (Personal → Fund)      │     Loans, Acts,      │
│  • Global 4-Tab FAB  │  • Fund Cycle Reset       │     Adjustments,      │
│    + 180px Padding   │    (fundResetAtMillis)    │     Recurring, Goals) │
│  • Grouped History   │  • Undo Stack + DB        │  • Orphan Deletion    │
│    (No Date in Sub)  │    Fallback Engine        │    Reconciliation     │
└──────────────────────┴───────────────────────────┴───────────────────────┘
```
