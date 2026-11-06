# Deep Analysis Bug Fixes & Loans (Khata) Minimalistic UI Plan

## 1. Bug & Loophole Fixes

### A. Wallets Screen — Current Month Budget & Report Sync (`WalletsAndAdjustmentScreens.kt`)
- **Current-Month Remaining Days Fix:** In `WalletsOverviewScreen`, update the `Monthly Budget` bar calculation (`remainingDays` and `safePerDay`) to always use `calNow.getActualMaximum(Calendar.DAY_OF_MONTH)` and `calNow.get(Calendar.DAY_OF_MONTH)` instead of referencing `uiState.selectedYear` and `uiState.selectedMonth`. This ensures switching months in the `Report` tab never distorts the `Safe: ৳X/day (Y d left)` calculation on the `Wallets` screen.
- **Current-Month Report Navigation Sync:** When tapping the `Monthly Budget` progress bar on `WalletsOverviewScreen`, call `viewModel.resetToCurrentMonth()` before navigating to `PocketScreen.Reports` so the report always opens on the current calendar month matching the budget bar.

### B. Wallet Detail Screen — Personal vs. Fund Balance & Adjustment Edit (`WalletsAndAdjustmentScreens.kt`)
- **`isFundEnabled` Guard:** Guard all Fund breakdown pills and actions in `WalletDetailScreen` with `uiState.isFundEnabled` so no Fund text appears when the Fund feature is disabled in Settings.
- **Personal Balance Headline & Fund Pill:** When `uiState.isFundEnabled` is active and a regular wallet (`!wallet.isFundWallet`) holds virtual Fund money (`summary.hasVirtualFund || kotlin.math.abs(summary.fundBalance) > 0.005 || summary.fundBorrowedDue > 0.005`):
  - Display `summary.personalBalance` as the primary headline balance (labeled `"Personal Balance"`).
  - Below it, display a clean elevated pill showing `Fund: ৳X • Total: ৳Y` (plus `Borrowed Due: ৳Z` when applicable) and a compact `Return to Fund` button when `effectiveDue > 0.005`.
- **Direct Adjustment Editing:** When selecting an adjustment entry (`LedgerEntryKind.ADJUSTMENT`) from `Running Balance History` and tapping `Edit`, open `OneTapAdjustBalanceDialog` (`showOneTapAdjustDialog = true`) instead of the full `WalletFormDialog`.

---

## 2. Minimalistic UI Redesign — Loans (Khata) Screen (`CategoriesLoansSettingsScreens.kt`)

### A. Single-Row View Mode & Direction Filter Bar
- Combine the view mode chips (`By Person (Khata)` / `Individual Loans`) and direction filter chips (`All` / `Lent` / `Borrowed`) into a single compact horizontal `LazyRow` with a subtle vertical divider between the two groups, removing an entire vertical row above the search field.

### B. Cleaner Collapsed Khata Person Cards
- Move the `Share Khata Statement` and `PDF` export buttons inside the `if (isExpanded)` section of each `khata_person_card_${group.personKey}` card.
- Collapsed Khata cards will only display the person's avatar badge, name, loan count / status subtitle, net amount, and expand chevron—making the Khata list clean and scannable at a glance.

---

## 3. Verification
- Run `compile_applet` to verify all Kotlin types, imports, and Compose test tags compile cleanly without errors.
