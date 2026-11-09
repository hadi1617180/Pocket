# Savings Goal Sub-Wallet Support, Cash Wallet Display Rule & Systemic Bug Fixes

This update brings full `<Wallet>-Personal`, `<Wallet>-Fund`, and `Borrow for Personal` support to Savings Goals (enabled by default), updates the **Cash** wallet card on the Wallets home screen to display **Cash Personal** as its main headline amount with `Cash-Total`, `Cash-Fund`, and `Borrowed Due` in the subtitle, and resolves remaining sub-wallet and validation loopholes across the app.

## User Review & Critical Decisions

> [!IMPORTANT]
> Both design preferences were confirmed during clarification and are incorporated into this plan:

- **Confirmed Decision 1 (Savings Goal Wallet & Sub-Wallet Selector)**: When adding to or withdrawing from a Savings Goal, the **Transfer from/to Wallet** option is **enabled by default**, immediately showing the wallet chips alongside the `<Wallet>-Personal` and `<Wallet>-Fund` sub-wallet selector cards (plus `Official Fund Expense` vs `Borrow for Personal` when depositing from Fund).
- **Confirmed Decision 2 (Cash Wallet Display Rule)**: On the Wallets home screen, the **Cash** wallet displays **Cash Personal** (`personalBalance`) as its main big amount on the right, and—when Cash has an active Fund balance or Borrowed Due—displays `Cash-Total`, `Cash-Fund`, and `Borrowed Due` in its 2-column subtitle. All other wallets continue to show Total as the main big amount and `<Wallet>-Personal`, `<Wallet>-Fund`, and `Borrowed Due` in the subtitle.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. Upgrades the **Savings Goal Add / Withdraw Dialog** so users can seamlessly fund goals from (or withdraw into) `<Wallet>-Personal` or `<Wallet>-Fund`, including borrowing from Fund (`FUND_BORROW`) or spending Official Fund (`FUND_EXPENSE`).
  2. Customizes the **Cash** wallet card on the Wallets home screen so its main headline figure reflects spendable **Cash Personal**, while `Cash-Total`, `Cash-Fund`, and `Borrowed Due` appear in the tabular subtitle whenever Cash has an active Fund or Borrowed Due balance.
  3. Closes remaining sub-wallet and accounting loopholes in Savings Goal withdrawals, Recurring Bill payments, and Balance Reconciliation.
- **Target Audience / Persona**: Pocket users managing personal cash alongside official/project funds and savings targets.
- **Key Value**: Consistent sub-wallet awareness across every money movement dialog and immediate visibility of spendable personal cash on the home screen.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Adding / Withdrawing Money in Savings Goals**:
     - Tapping **+ Add** or **− Withdraw** on any Savings Goal opens the Liquid Glass dialog with **Transfer from/to Wallet** switched **ON by default**.
     - Users pick a wallet chip (showing each wallet's live balance) and immediately see the 2-column `<Wallet>-Personal` and `<Wallet>-Fund` selector cards.
     - When adding to a goal (`+ Add`) with `<Wallet>-Fund` selected, users can choose between **Official Fund Expense** (deducts from `<Wallet>-Fund`) and **Borrow for Personal** (deducts from `<Wallet>-Fund` and adds to `Borrowed Due`).
     - When withdrawing from a goal (`− Withdraw`), the dialog validates that the withdrawal amount does not exceed the goal's currently saved balance (`savedAmount`).
  2. **Cash Wallet Card on Wallets Home Screen**:
     - When the Fund feature is active, the **Cash** wallet card shows `Cash Personal` as its primary large right-aligned amount.
     - When Cash has an active Fund balance or Borrowed Due, the 2-column subtitle under Cash displays:
       - `Cash-Total` → Total Cash balance (`currentBalance`)
       - `Cash-Fund` → Fund Cash balance (`fundBalance`)
       - `Borrowed Due` → Outstanding borrowed due (when `> 0`)
     - All other wallets (`Bkash`, `Nagad`, `Bank`, etc.) keep their existing layout: Total balance as the main big amount, and `<Wallet>-Personal`, `<Wallet>-Fund`, and `Borrowed Due` in the subtitle.
- **Visual Identity & Theme**:
  - Preserves the iOS 27 Liquid Glass aesthetic, specular borders, tabular numerals (`tnum`), and dynamic accent coloring.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Enabling Sub-Wallet Selection Across All Remaining Financial Dialogs**
  - *Chosen Approach*: Bring the same `SubWalletSelectorCards` (`<Wallet>-Personal` / `<Wallet>-Fund` + `Official Fund` / `Borrow for Personal`) to **Savings Goal Add/Withdraw**, **Recurring Bill Pay Confirmation**, and **Reconcile Balance**.
  - *Why*: Previously, paying a recurring bill or funding a savings goal only allowed selecting the parent wallet, forcing all recurring payments and savings deposits into Personal even when paid from Fund.
- **Decision 2: Preventing Over-Withdrawal Loophole on Savings Goals**
  - *Chosen Approach*: Cap and validate Savings Goal withdrawals so a user cannot withdraw more than `goal.savedAmount` and accidentally mint extra wallet income.
  - *Why*: Previously, withdrawing ৳1,000 from a goal that only had ৳500 saved clamped the goal to ৳0 but still credited ৳1,000 of income to the destination wallet.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Wallets Home Screen                              │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Wallet Card Renderer                                               │  │
│  │  • If wallet == "Cash" & Fund Enabled:                             │  │
│  │      Main Big Amount  ──► summary.personalBalance                  │  │
│  │      Subtitle Rows    ──► Cash-Total | Cash-Fund | Borrowed Due    │  │
│  │  • All Other Wallets:                                              │  │
│  │      Main Big Amount  ──► summary.currentBalance (Total)           │  │
│  │      Subtitle Rows    ──► <W>-Personal | <W>-Fund | Borrowed Due   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                 Savings Goal & Recurring Bill Dialogs                    │
│  ┌──────────────────────────────┐    ┌────────────────────────────────┐  │
│  │ SavingsGoalFundDialog        │    │ RecurringPayConfirmDialog      │  │
│  │ • transferFromOrToWallet=true│    │ • SubWalletSelectorCards       │  │
│  │ • SubWalletSelectorCards     │    │ • Official Fund vs Borrow      │  │
│  │ • Official Fund vs Borrow    │    │ • Balance warning per bucket   │  │
│  │ • Over-withdrawal guard      │    └────────────────────────────────┘  │
│  └──────────────┬───────────────┘                     │                  │
└─────────────────┼─────────────────────────────────────┼──────────────────┘
                  ▼                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    PocketViewModel & AccountingEngine                    │
│  • Auto-enables virtual fund (`withVirtualFund(enabled = true)`) when    │
│    a Fund or Borrow tag (`FUND_EXPENSE`, `FUND_BORROW`, `FUND_IN`) is    │
│    used in Savings Goals or Recurring Bill payments.                     │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - `SavingsGoalFundDialog`:
    - Defaults `transferFromOrToWallet = true` and selects the user's default/first active wallet.
    - Integrates `SubWalletSelectorCards` and `Official Fund Expense` / `Borrow for Personal` chips when `uiState.isFundEnabled` is true.
    - Displays a negative-balance warning banner if deducting from the chosen sub-wallet (`Personal` or `Fund`) will make that sub-wallet negative.
    - Prevents withdrawing more than `goal.savedAmount`.
  - `PocketViewModel.addOrWithdrawSavingsGoal`:
    - Accepts `fundMode: String = "PERSONAL"` (`"PERSONAL"`, `"FUND_EXPENSE"`, `"FUND_BORROW"`, `"FUND_IN"`).
    - Automatically activates `hasVirtualFund` on the target wallet if a Fund mode is chosen, and attaches the corresponding tag (`FUND_EXPENSE`, `FUND_BORROW`, or `FUND_IN`) alongside `SavingsGoal` on the generated transaction.
  - `RecurringPayConfirmDialog` & `PocketViewModel.confirmRecurringPayment`:
    - Adds `SubWalletSelectorCards` and `Official Fund Expense` / `Borrow for Personal` selection when `uiState.isFundEnabled` is active, passing the chosen `fundMode` into `confirmRecurringPayment`.
  - `BalanceAdjustmentScreen`:
    - When `uiState.isFundEnabled` is active and a non-Fund wallet is selected, allows reconciling either the `Total`, `<Wallet>-Personal`, or `<Wallet>-Fund` balance directly.
