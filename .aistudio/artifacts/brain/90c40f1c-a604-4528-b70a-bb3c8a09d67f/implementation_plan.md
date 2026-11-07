# Deep Audit Bug Fixes & Minimalistic UI Polish Plan

## 1. Accounting & Data-Layer Loopholes Found (To Be Fixed)

### A. Monthly Reports & Home Summary Mixing Fund with Personal (`AccountingEngine.kt` & `ReportsAndStatementScreens.kt`)
- **Issue**: Currently, `calculateMonthlyReport` includes `FUND_IN` (official Fund deposits) and `FUND_EXPENSE` (official Fund expenses) inside the user's Personal Monthly Income, Expense, Net Cashflow, and Monthly Budget progress.
- **Fix**:
  - Default the Home Hero Card's monthly Income/Expense pills and the Personal Monthly Budget tracker to **Personal-only** (`PERSONAL` income/expenses + `FUND_BORROW` expenses, which are personal expenses paid by borrowing from Fund).
  - Add a clean 3-chip filter toggle (`Personal` • `Fund` • `Combined`) at the top of the **Reports** screen so the user can inspect Personal cashflow, Official Fund cashflow (`FUND_IN` vs `FUND_EXPENSE`), or Combined totals independently.

### B. Wallet Migration Deleting Same-Wallet Sub-Wallet Transfers (`PocketDao.kt`)
- **Issue**: `deleteSelfTransfersAfterMigration()` deletes all transfers where `sourceWalletId == destinationWalletId` across the database. Because `<Wallet>-Personal ↔ <Wallet>-Fund` transfers (`[FUND_BORROW]` / `[FUND_RETURN]`) legitimately share the same `walletId`, migrating any wallet would accidentally delete internal sub-wallet transfers.
- **Fix**: Exclude `[FUND_BORROW]`, `[FUND_RETURN]`, and `[FUND_TRANSFER]` notes from `deleteSelfTransfersAfterMigration()` and `deleteSelfTransferFeesAfterMigration()`.

### C. Service Fees & Transfer Fees Sub-Wallet Tag Propagation (`PocketRepository.kt` & `PocketDao.kt`)
- **Issue**:
  1. In `saveTransactionWithOptionalFee`, editing an existing transaction with a child service fee did not update `tags` on the child fee transaction, and an `INCOME` with `FUND_IN` that had a fee created an `EXPENSE` child with `FUND_IN` instead of `FUND_EXPENSE`.
  2. In `saveTransferAtomically`, a transfer with `[FUND_BORROW]` that included a fee tagged the fee as `FUND_EXPENSE` instead of `FUND_BORROW`.
- **Fix**: Properly map child fee tags (`FUND_EXPENSE` for official Fund transactions/transfers, `FUND_BORROW` for borrowed-from-Fund transactions/transfers) on both insert and update.

### D. Cross-Wallet Fund Borrow & Return Reconciliation (`AccountingEngine.kt` & `PocketUiState`)
- **Issue**: If a user borrows from `MTB-Fund` (`৳500`) and later repays that borrowed amount into `Bkash-Fund` (`[FUND_RETURN] ৳500`), the global `totalFundBorrowedDue` becomes `৳0`, but `MTB`'s individual `fundBorrowedDue` still showed `৳500` because the return happened in `Bkash`. Also, `totalFundBalance` summed across wallets without checking currency (`BDT`).
- **Fix**: Filter global Fund totals to primary currency (`BDT`) and reconcile individual wallet `fundBorrowedDue` against global excess returns so paid-off borrows never leave stale warnings on individual cards.

### E. `CombinedFundDetailScreen` & `WalletDetailScreen` Sub-Wallet Completeness (`WalletsAndAdjustmentScreens.kt`)
- **Issue**:
  1. `CombinedFundDetailScreen` missed `[FUND_LOAN]` entries in its filter and didn't support editing `LOAN_PRINCIPAL` or transfer-fee rows directly.
  2. In `WalletDetailScreen`, switching to `<Wallet>-Personal` or `<Wallet>-Fund` still showed the combined wallet formula breakdown at the top instead of the selected sub-wallet's breakdown, and hid the running balance on sub-wallet tabs.
- **Fix**:
  - Include `[FUND_LOAN]` entries in `CombinedFundDetailScreen` and support full edit actions for transfers and loans.
  - Show sub-wallet-specific formula breakdown and accurate sub-wallet running balances when viewing `<Wallet>-Personal` or `<Wallet>-Fund` tabs.

---

## 2. Minimalistic UI Enhancements Included
- **Collapsible Formula Breakdown in Wallet Detail**: Keep the top balance card clean by making the multi-line formula breakdown expandable/collapsible with a single tap so the transaction list is immediately visible without scrolling.
- **Sub-Wallet Filter Chips in Transaction History**: Add quick `All` / `Personal` / `Fund` filter chips in the History screen so users can filter personal vs fund entries anytime.
- **Clean Single-Line Borrowed-from-Fund Banner**: Streamline the Borrowed-from-Fund indicator on the Wallets screen into a compact single-line pill with a quick `Repay` action.
