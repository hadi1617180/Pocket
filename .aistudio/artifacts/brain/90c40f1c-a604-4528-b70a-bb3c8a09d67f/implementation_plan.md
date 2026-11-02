# Pocket Deep Audit: Multi-Currency Accuracy, Tier Enforcement & UI/UX Polish

This plan addresses the newly identified multi-currency inaccuracies across Loans and Recurring schedules, closes Free-tier limit bypasses in transaction creation and recurring bill confirmations, completes Balance Privacy Mode masking in Wallet Detail views, and refines form and dialog UX across the application.

### User Review & Critical Decisions

- **Multi-Currency Accuracy in Loans, Khata & Recurring Schedules**: Confirmed that all loan cards, repayment dialogs, due-bill banners, recurring management cards, and savings goal transfer notifications will dynamically resolve the currency symbol of their linked wallet (`AccountingEngine.currencySymbolForCode`) rather than falling back to a hardcoded `৳` or global currency symbol.
- **Free-Tier Subscription & Daily Limit Enforcement**: Confirmed that creating a recurring schedule via the Transaction Form toggle will enforce the Free-tier recurring bill limit (`FREE_RECURRING_BILLS_LIMIT = 2`), and confirming a recurring payment will validate the Free-tier daily transaction limit (`FREE_DAILY_TRANSACTIONS_LIMIT = 5`).
- **Complete Balance Privacy Mode Masking**: Confirmed that enabling Balance Privacy Mode (`hideBalancesPrivacy`) will also mask Credit Card limit/available figures and per-row historical running balances on the Wallet Detail screen.
- **Archived Category Preservation & Dialog Polish**: Confirmed that editing an existing transaction will preserve its assigned category even if that category was subsequently archived, modal dialogs in Wallet Detail and Settings will include a consistent dark scrim overlay over blurred backgrounds, and the Recurring Schedule dialog will support inline math expressions.

---

### 1. Overview & Core Concept

While core wallet ledgers, statements, and reports already honor per-wallet currency symbols and privacy toggles, secondary financial flows—specifically Loans/Khata tracking, Recurring Bill schedules, and Savings Goal wallet transfers—still rely on the global currency symbol or unmasked balance fields. Furthermore, inline convenience flows (such as creating a recurring bill directly from the Transaction Form or one-tap confirming a due recurring bill) bypass Free-tier quota checks. This update unifies currency resolution, quota enforcement, privacy masking, and modal visual treatments across every remaining screen.

---

### 2. User Experience & Visual Design

- **Wallet-Aware Currency Formatting**:
  - **Loans & Khata**: Each loan card, activity history row, and repayment dialog displays amounts using the currency symbol of the wallet associated with the loan (`initialWalletId` or the activity's `walletId`), falling back to the primary currency symbol only when no wallet is linked.
  - **Recurring Bills & Banners**: Due recurring bill alerts on the Wallets Overview banner, recurring schedule cards, and the Add/Edit Recurring Schedule dialog dynamically display the symbol of the selected default wallet (`defaultWalletId`).
  - **Savings Goal Transfers**: Status feedback when contributing to or withdrawing from a savings goal formats the amount with the selected wallet's currency symbol.
- **Consistent Privacy Masking**:
  - When **Hide Balances (Privacy Mode)** is active, the **Credit Card Limit & Cycle** card masks both `Credit Limit` and `Available` amounts (`$symbol ••••`), and individual ledger rows in the **Running Balance History** suppress their `Bal: ...` trailing pill so historical balances cannot be inferred.
- **Seamless Form & Modal Interactions**:
  - **Archived Category Retention**: Opening an older transaction for editing whose category was later archived keeps that archived category visible and selected in the chip selector so users can update notes or amounts without accidentally reassigning the category.
  - **Inline Math in Recurring Dialogs**: The Add/Edit Recurring Schedule amount field includes the interactive `MiniCalculatorBar` (`+`, `-`, `×`, `÷`, `=`) and automatic math expression evaluation.
  - **Unified Modal Scrims**: Opening dialogs on the Wallet Detail or Settings screens pairs the 14dp background blur with a `28%` black translucent scrim overlay for optimal contrast and visual hierarchy.

---

### 3. Key Product Decisions & Trade-Offs

- **Preserving Archived Categories Only for Matching Transactions**: Archived categories remain hidden when creating new transactions to keep the selector clean, but are dynamically included if the transaction currently being edited references that archived category ID.
- **UI-Level + ViewModel-Level Guardrails for Recurring Toggles**: In the Transaction Form, if a Free-tier user has already reached the 2-recurring-bill limit, toggling "Make this a Recurring Bill / Subscription" immediately prompts the Pro Upgrade Paywall rather than waiting until form submission, while the ViewModel also enforces the check as a defensive safeguard.
- **Suppressing Running Balance Pills in Privacy Mode**: Rather than rendering repetitive masked text on every single transaction row in the Wallet Detail ledger, passing `null` for `runningBalanceAfter` when `hideBalancesPrivacy` is active cleanly hides the running balance badge while keeping individual transaction amounts visible when inspecting a specific wallet.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer (UI)                          │
│                                                                          │
│  ┌────────────────────────┐   ┌────────────────────────┐                 │
│  │  Wallet Detail & Home  │   │  Loans & Recurring UI  │                 │
│  │  • Credit Limit Mask   │   │  • Wallet Currency Sym │                 │
│  │  • Running Bal Privacy │   │  • Inline Math Calc    │                 │
│  │  • Due Banner Currency │   │  • Modal Scrim Overlay │                 │
│  └───────────┬────────────┘   └───────────┬────────────┘                 │
│              │                            │                              │
│              ▼                            ▼                              │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                      Transaction Form Screen                       │  │
│  │  • Retains existingTx.categoryId even when category.isArchived     │  │
│  │  • Guards "Make Recurring" toggle with SubscriptionManager check   │  │
│  └───────────────────────────────────┬────────────────────────────────┘  │
└──────────────────────────────────────┼───────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      Domain & State Layer (ViewModel)                    │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                          PocketViewModel                           │  │
│  │  • saveTransaction(): Validates canAddRecurringBill() before       │  │
│  │    inserting RecurringTransactionEntity                            │  │
│  │  • confirmRecurringPayment(): Validates canAddTransactionToday()   │  │
│  │    before recording payment transaction                            │  │
│  │  • addOrWithdrawSavingsGoal(): Uses wallet currency symbol         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Currency Symbol Resolution**: Lookup helpers resolve `wallets.find { it.id == walletId }?.let { AccountingEngine.currencySymbolForCode(it.currency) } ?: uiState.settings.currencySymbol` across Loan cards, Loan Repayment dialogs, Due Recurring banners, and Recurring Schedule forms.
- **Subscription Quota Validation**:
  - `TransactionFormScreen` checks `SubscriptionManager.canAddRecurringBill(uiState.recurringTransactions.size, uiState.settings.subscriptionTier, uiState.settings.subscriptionExpiryDate)` when enabling `makeRecurring`.
  - `PocketViewModel.saveTransaction` only creates the companion `RecurringTransactionEntity` if `SubscriptionManager.canAddRecurringBill(...)` passes.
  - `PocketViewModel.confirmRecurringPayment` verifies `SubscriptionManager.canAddTransactionToday(todayCount, settings.subscriptionTier, settings.subscriptionExpiryDate)` before inserting the ledger transaction.
