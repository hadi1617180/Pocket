// accounting.js — 1:1 Web port of AccountingEngine.kt, SubscriptionManager, BackupManager.kt, and PocketRepository defaults
import { PocketLocalizer } from './localizer.js';

export const CURRENCY_SYMBOLS = {
  BDT: '৳',
  USD: '$',
  EUR: '€',
  GBP: '£',
  INR: '₹',
  SAR: '﷼',
  AED: 'د.إ',
  MYR: 'RM'
};

export const DEFAULT_STARTER_WALLETS = [
  { id: 1, name: 'Cash', type: 'CASH', openingBalance: 0, includeInTotalBalance: true, colorHex: '#2E9E5B', iconName: 'CASH', sortOrder: 1, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 2, name: 'Bank', type: 'BANK', openingBalance: 0, includeInTotalBalance: true, colorHex: '#2563EB', iconName: 'BANK', sortOrder: 2, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 3, name: 'MTB', type: 'BANK', openingBalance: 0, includeInTotalBalance: true, colorHex: '#006A4E', iconName: 'MTB', sortOrder: 3, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 4, name: 'Brac', type: 'BANK', openingBalance: 0, includeInTotalBalance: true, colorHex: '#0072BC', iconName: 'BRAC', sortOrder: 4, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 5, name: 'Bkash', type: 'MFS', openingBalance: 0, includeInTotalBalance: true, colorHex: '#E2136E', iconName: 'BKASH', sortOrder: 5, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 6, name: 'Nagad', type: 'MFS', openingBalance: 0, includeInTotalBalance: true, colorHex: '#F37021', iconName: 'NAGAD', sortOrder: 6, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 7, name: 'Rocket', type: 'MFS', openingBalance: 0, includeInTotalBalance: true, colorHex: '#8C3494', iconName: 'ROCKET', sortOrder: 7, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 8, name: 'Rapid Pass', type: 'TRANSIT', openingBalance: 0, includeInTotalBalance: true, colorHex: '#1E3A8A', iconName: 'RAPID_PASS', sortOrder: 8, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 9, name: 'Card', type: 'CARD', openingBalance: 0, includeInTotalBalance: true, colorHex: '#DC2626', iconName: 'CARD', sortOrder: 9, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 },
  { id: 10, name: 'Savings', type: 'SAVINGS', openingBalance: 0, includeInTotalBalance: false, colorHex: '#F15A24', iconName: 'SAVINGS', sortOrder: 10, isArchived: false, currency: 'BDT', creditLimit: 0, statementDay: 0, paymentDueDay: 0 }
];

export const DEFAULT_CATEGORIES = [
  { id: 1, name: 'Food & Dining', type: 'EXPENSE', colorHex: '#EF4444', iconName: 'RESTAURANT', monthlyBudget: 0, isArchived: false },
  { id: 2, name: 'Groceries & Bazar', type: 'EXPENSE', colorHex: '#10B981', iconName: 'SHOPPING_BASKET', monthlyBudget: 0, isArchived: false },
  { id: 3, name: 'Transport & Fuel', type: 'EXPENSE', colorHex: '#F59E0B', iconName: 'DIRECTIONS_CAR', monthlyBudget: 0, isArchived: false },
  { id: 4, name: 'Rent & Housing', type: 'EXPENSE', colorHex: '#6366F1', iconName: 'HOME', monthlyBudget: 0, isArchived: false },
  { id: 5, name: 'Utilities & Bills', type: 'EXPENSE', colorHex: '#3B82F6', iconName: 'BOLT', monthlyBudget: 0, isArchived: false },
  { id: 6, name: 'Mobile & Internet', type: 'EXPENSE', colorHex: '#06B6D4', iconName: 'WIFI', monthlyBudget: 0, isArchived: false },
  { id: 7, name: 'Shopping & Clothes', type: 'EXPENSE', colorHex: '#EC4899', iconName: 'SHOPPING_BAG', monthlyBudget: 0, isArchived: false },
  { id: 8, name: 'Healthcare & Medicine', type: 'EXPENSE', colorHex: '#14B8A6', iconName: 'MEDICAL_SERVICES', monthlyBudget: 0, isArchived: false },
  { id: 9, name: 'Education & Books', type: 'EXPENSE', colorHex: '#8B5CF6', iconName: 'SCHOOL', monthlyBudget: 0, isArchived: false },
  { id: 10, name: 'Family & Personal', type: 'EXPENSE', colorHex: '#F97316', iconName: 'FAMILY_RESTROOM', monthlyBudget: 0, isArchived: false },
  { id: 11, name: 'Entertainment', type: 'EXPENSE', colorHex: '#A855F7', iconName: 'MOVIE', monthlyBudget: 0, isArchived: false },
  { id: 12, name: 'Travel & Trips', type: 'EXPENSE', colorHex: '#0EA5E9', iconName: 'FLIGHT', monthlyBudget: 0, isArchived: false },
  { id: 13, name: 'Charity & Zakat', type: 'EXPENSE', colorHex: '#22C55E', iconName: 'VOLUNTEER_ACTIVISM', monthlyBudget: 0, isArchived: false },
  { id: 14, name: 'Fees & Charges', type: 'EXPENSE', colorHex: '#94A3B8', iconName: 'RECEIPT', monthlyBudget: 0, isArchived: false },
  { id: 15, name: 'Other Expense', type: 'EXPENSE', colorHex: '#64748B', iconName: 'CATEGORY', monthlyBudget: 0, isArchived: false },
  { id: 16, name: 'Salary & Wages', type: 'INCOME', colorHex: '#22C55E', iconName: 'PAYMENTS', monthlyBudget: 0, isArchived: false },
  { id: 17, name: 'Freelance & Business', type: 'INCOME', colorHex: '#10B981', iconName: 'WORK', monthlyBudget: 0, isArchived: false },
  { id: 18, name: 'Investment & Profit', type: 'INCOME', colorHex: '#06B6D4', iconName: 'TRENDING_UP', monthlyBudget: 0, isArchived: false },
  { id: 19, name: 'Gifts & Bonus', type: 'INCOME', colorHex: '#F59E0B', iconName: 'CARD_GIFTCARD', monthlyBudget: 0, isArchived: false },
  { id: 20, name: 'Other Income', type: 'INCOME', colorHex: '#84CC16', iconName: 'ATTACH_MONEY', monthlyBudget: 0, isArchived: false }
];

export const DEFAULT_SETTINGS = {
  id: 1,
  currencySymbol: '৳',
  defaultWalletId: 1,
  warnOnNegativeBalance: true,
  hasCompletedOnboarding: false,
  onboardingStep: 'PAGE_1_WELCOME',
  themeMode: 'DARK',
  accentColorHex: '#F15A24',
  appLockEnabled: false,
  appLockPin: '',
  biometricUnlockEnabled: false,
  masterMonthlyBudget: 0.0,
  userName: '',
  profilePhotoUri: '',
  customBackgroundUri: '',
  backgroundOpacity: 0.25,
  boxColorHex: '',
  boxOpacity: 0.50,
  loanDeadlineNotificationEnabled: true,
  dailyExpenseReminderEnabled: false,
  dailyReminderHour: 21,
  dailyReminderMinute: 0,
  hideBalancesPrivacy: false,
  autoSmsListenerEnabled: false,
  appLanguage: 'EN',
  subscriptionTier: 'FREE',
  subscriptionActivatedAt: null,
  subscriptionExpiresAt: null,
  activatedCode: ''
};

export const AccountingEngine = {
  roundCurrency(value) {
    const n = Number(value) || 0;
    return Math.round((n + Number.EPSILON) * 100) / 100;
  },

  currencySymbolForCode(code) {
    const clean = String(code || 'BDT').trim().toUpperCase();
    return CURRENCY_SYMBOLS[clean] || '৳';
  },

  formatNumberWithCommas(num, decimals) {
    const fixed = Math.abs(num).toFixed(decimals);
    const [intPart, decPart] = fixed.split('.');
    const withCommas = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return decPart !== undefined ? `${withCommas}.${decPart}` : withCommas;
  },

  formatAmount(amount, symbol = '৳', includeDecimals = true) {
    const rounded = this.roundCurrency(amount);
    const absVal = Math.abs(rounded);
    const hasFraction = Math.abs(absVal - Math.round(absVal)) > 0.004;
    const decimals = includeDecimals && hasFraction ? 2 : 0;
    const formatted = this.formatNumberWithCommas(absVal, decimals);
    const sign = rounded < -0.004 ? '−' : '';
    return PocketLocalizer.toBanglaDigits(`${sign}${symbol}${formatted}`);
  },

  formatMoney(amount, symbol = '৳', includeDecimals = true) {
    const rounded = this.roundCurrency(amount);
    const absVal = Math.abs(rounded);
    const hasFraction = Math.abs(absVal - Math.round(absVal)) > 0.004;
    const decimals = includeDecimals && hasFraction ? 2 : 0;
    const formatted = this.formatNumberWithCommas(absVal, decimals);
    const sign = rounded < -0.004 ? '−' : '';
    return PocketLocalizer.toBanglaDigits(`${sign}${symbol} ${formatted}`);
  },

  calculateWalletSummary(wallet, transactions, transfers, loans, loanPayments, adjustments) {
    const wId = Number(wallet.id);
    let incomeTotal = 0;
    let expenseTotal = 0;
    let expenseReversalTotal = 0;

    for (const tx of transactions) {
      if (Number(tx.walletId) !== wId) continue;
      const amt = Number(tx.amount) || 0;
      if (tx.type === 'INCOME') incomeTotal += amt;
      else if (tx.type === 'EXPENSE') expenseTotal += amt;
      else if (tx.type === 'EXPENSE_REVERSAL') expenseReversalTotal += amt;
    }

    let transfersIn = 0;
    let transfersOut = 0;
    let transferFees = 0;
    for (const tr of transfers) {
      if (Number(tr.destinationWalletId) === wId) {
        const destAmt = tr.destinationAmount !== null && tr.destinationAmount !== undefined
          ? Number(tr.destinationAmount)
          : Number(tr.amount) || 0;
        transfersIn += destAmt;
      }
      if (Number(tr.sourceWalletId) === wId) {
        transfersOut += Number(tr.amount) || 0;
        transferFees += Number(tr.feeAmount) || 0;
      }
    }

    let loansBorrowedIn = 0;
    let loansLentOut = 0;
    for (const loan of loans) {
      if (Number(loan.initialWalletId) !== wId) continue;
      const p = Number(loan.principalAmount) || 0;
      if (loan.direction === 'BORROWED') loansBorrowedIn += p;
      else if (loan.direction === 'LENT') loansLentOut += p;
    }

    const loanMap = new Map(loans.map(l => [Number(l.id), l]));
    let loanRepaymentsReceivedIn = 0;
    let loanRepaymentsPaidOut = 0;
    for (const lp of loanPayments) {
      if (Number(lp.walletId) !== wId) continue;
      const parentLoan = loanMap.get(Number(lp.loanId));
      if (!parentLoan) continue;
      const totalPayment = (Number(lp.principalPortion) || 0) + (Number(lp.interestPortion) || 0);
      if (parentLoan.direction === 'LENT') loanRepaymentsReceivedIn += totalPayment;
      else if (parentLoan.direction === 'BORROWED') loanRepaymentsPaidOut += totalPayment;
    }

    let manualAdjustmentsNet = 0;
    for (const adj of adjustments) {
      if (Number(adj.walletId) === wId) {
        manualAdjustmentsNet += Number(adj.deltaAmount) || 0;
      }
    }

    const opening = Number(wallet.openingBalance) || 0;
    const rawBalance =
      opening +
      incomeTotal -
      expenseTotal +
      expenseReversalTotal +
      transfersIn -
      transfersOut -
      transferFees +
      loansBorrowedIn -
      loansLentOut +
      loanRepaymentsReceivedIn -
      loanRepaymentsPaidOut +
      manualAdjustmentsNet;

    return {
      wallet,
      openingBalance: this.roundCurrency(opening),
      incomeTotal: this.roundCurrency(incomeTotal),
      expenseTotal: this.roundCurrency(expenseTotal),
      expenseReversalTotal: this.roundCurrency(expenseReversalTotal),
      transfersIn: this.roundCurrency(transfersIn),
      transfersOut: this.roundCurrency(transfersOut),
      transferFees: this.roundCurrency(transferFees),
      loansBorrowedIn: this.roundCurrency(loansBorrowedIn),
      loansLentOut: this.roundCurrency(loansLentOut),
      loanRepaymentsReceivedIn: this.roundCurrency(loanRepaymentsReceivedIn),
      loanRepaymentsPaidOut: this.roundCurrency(loanRepaymentsPaidOut),
      manualAdjustmentsNet: this.roundCurrency(manualAdjustmentsNet),
      currentBalance: this.roundCurrency(rawBalance)
    };
  },

  calculateLoanSummaries(loans, loanPayments, wallets, nowMillis = Date.now()) {
    const walletMap = new Map(wallets.map(w => [Number(w.id), w]));
    const paymentsByLoan = new Map();
    for (const lp of loanPayments) {
      const lid = Number(lp.loanId);
      if (!paymentsByLoan.has(lid)) paymentsByLoan.set(lid, []);
      paymentsByLoan.get(lid).push(lp);
    }

    return loans
      .slice()
      .sort((a, b) => (Number(b.startDate) || 0) - (Number(a.startDate) || 0))
      .map(loan => {
        const payments = (paymentsByLoan.get(Number(loan.id)) || [])
          .slice()
          .sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
        const principalRepaid = this.roundCurrency(
          payments.reduce((sum, p) => sum + (Number(p.principalPortion) || 0), 0)
        );
        const interestTotal = this.roundCurrency(
          payments.reduce((sum, p) => sum + (Number(p.interestPortion) || 0), 0)
        );
        const orig = this.roundCurrency(loan.principalAmount);
        const rawOutstanding = this.roundCurrency(orig - principalRepaid);
        const outstandingPrincipal = Math.max(0, rawOutstanding);
        const isSettled = outstandingPrincipal <= 0.004;
        const isOverdue = !isSettled && loan.dueDate && Number(loan.dueDate) < nowMillis;
        const status = isSettled ? 'SETTLED' : isOverdue ? 'OVERDUE' : 'ACTIVE';
        const w = walletMap.get(Number(loan.initialWalletId));
        return {
          loan,
          walletName: w ? w.name : 'Wallet',
          originalPrincipal: orig,
          principalRepaid,
          interestPaidOrReceived: interestTotal,
          outstandingPrincipal,
          status,
          payments
        };
      });
  },

  calculatePersonKhataSummaries(loanSummaries, wallets = []) {
    const walletMap = new Map(wallets.map(w => [Number(w.id), w]));
    const groups = new Map();
    for (const s of loanSummaries) {
      const key = String(s.loan.personName || 'Person').trim().toLowerCase();
      if (!groups.has(key)) {
        groups.set(key, {
          personName: String(s.loan.personName || 'Person').trim(),
          loans: []
        });
      }
      groups.get(key).loans.push(s);
    }

    const result = [];
    for (const g of groups.values()) {
      let totalLentOut = 0;
      let totalBorrowedIn = 0;
      let outstandingReceivable = 0;
      let outstandingPayable = 0;
      for (const ls of g.loans) {
        if (ls.loan.direction === 'LENT') {
          totalLentOut += ls.originalPrincipal;
          outstandingReceivable += ls.outstandingPrincipal;
        } else {
          totalBorrowedIn += ls.originalPrincipal;
          outstandingPayable += ls.outstandingPrincipal;
        }
      }
      const firstWallet = walletMap.get(Number(g.loans[0]?.loan?.initialWalletId));
      const currencySymbol = this.currencySymbolForCode(firstWallet?.currency || 'BDT');
      result.push({
        personName: g.personName,
        loans: g.loans,
        totalLentOut: this.roundCurrency(totalLentOut),
        totalBorrowedIn: this.roundCurrency(totalBorrowedIn),
        outstandingReceivable: this.roundCurrency(outstandingReceivable),
        outstandingPayable: this.roundCurrency(outstandingPayable),
        netReceivableMinusPayable: this.roundCurrency(outstandingReceivable - outstandingPayable),
        currencySymbol
      });
    }
    return result.sort((a, b) => Math.abs(b.netReceivableMinusPayable) - Math.abs(a.netReceivableMinusPayable));
  },

  buildUnifiedLedger(wallets, categories, transactions, transfers, loans, loanPayments, adjustments) {
    const walletMap = new Map(wallets.map(w => [Number(w.id), w]));
    const catMap = new Map(categories.map(c => [Number(c.id), c]));
    const loanMap = new Map(loans.map(l => [Number(l.id), l]));
    const entries = [];

    for (const tx of transactions) {
      const w = walletMap.get(Number(tx.walletId));
      const cat = catMap.get(Number(tx.categoryId));
      const isIncome = tx.type === 'INCOME';
      const isReversal = tx.type === 'EXPENSE_REVERSAL';
      entries.push({
        uniqueKey: `TX_${tx.id}`,
        entityId: tx.id,
        kind: isIncome ? 'INCOME' : isReversal ? 'EXPENSE_REVERSAL' : 'EXPENSE',
        date: Number(tx.date) || Date.now(),
        title: cat ? cat.name : (isIncome ? 'Income' : 'Expense'),
        subtitle: `${w ? w.name : 'Wallet'}${tx.note ? ' • ' + tx.note : ''}`,
        walletId: Number(tx.walletId),
        secondaryWalletId: null,
        categoryId: tx.categoryId ? Number(tx.categoryId) : null,
        signedAmountText: `${isIncome || isReversal ? '+' : '−'}${this.formatAmount(tx.amount, this.currencySymbolForCode(w?.currency))}`,
        rawAmount: this.roundCurrency(tx.amount),
        isPositiveFlow: isIncome || isReversal,
        badgeLabel: isIncome ? 'INCOME' : isReversal ? 'REFUND' : 'EXPENSE',
        reference: tx.reference || '',
        tags: tx.tags || '',
        attachmentUri: tx.attachmentUri || '',
        iconName: cat?.iconName || (isIncome ? 'PAYMENTS' : 'RECEIPT'),
        colorHex: cat?.colorHex || (isIncome ? '#22C55E' : '#EF4444'),
        rawEntity: tx
      });
    }

    for (const tr of transfers) {
      const srcW = walletMap.get(Number(tr.sourceWalletId));
      const dstW = walletMap.get(Number(tr.destinationWalletId));
      entries.push({
        uniqueKey: `TR_${tr.id}`,
        entityId: tr.id,
        kind: 'TRANSFER',
        date: Number(tr.date) || Date.now(),
        title: `${srcW ? srcW.name : 'Wallet'} → ${dstW ? dstW.name : 'Wallet'}`,
        subtitle: tr.note ? tr.note : (tr.feeAmount > 0 ? `Fee: ${this.formatAmount(tr.feeAmount, this.currencySymbolForCode(srcW?.currency))}` : 'Wallet Transfer'),
        walletId: Number(tr.sourceWalletId),
        secondaryWalletId: Number(tr.destinationWalletId),
        categoryId: null,
        signedAmountText: this.formatAmount(tr.amount, this.currencySymbolForCode(srcW?.currency)),
        rawAmount: this.roundCurrency(tr.amount),
        isPositiveFlow: false,
        badgeLabel: 'TRANSFER',
        reference: '',
        tags: '',
        attachmentUri: '',
        iconName: 'SWAP_HORIZ',
        colorHex: '#3B82F6',
        rawEntity: tr
      });
    }

    for (const loan of loans) {
      const w = walletMap.get(Number(loan.initialWalletId));
      const isLent = loan.direction === 'LENT';
      entries.push({
        uniqueKey: `LOAN_${loan.id}`,
        entityId: loan.id,
        kind: isLent ? 'LOAN_LENT' : 'LOAN_BORROWED',
        date: Number(loan.startDate) || Date.now(),
        title: `${isLent ? 'Lent to' : 'Borrowed from'} ${loan.personName}`,
        subtitle: `${w ? w.name : 'Wallet'}${loan.note ? ' • ' + loan.note : ''}`,
        walletId: Number(loan.initialWalletId),
        secondaryWalletId: null,
        categoryId: null,
        signedAmountText: `${isLent ? '−' : '+'}${this.formatAmount(loan.principalAmount, this.currencySymbolForCode(w?.currency))}`,
        rawAmount: this.roundCurrency(loan.principalAmount),
        isPositiveFlow: !isLent,
        badgeLabel: isLent ? 'LOAN OUT' : 'LOAN IN',
        reference: '',
        tags: '',
        attachmentUri: loan.attachmentUri || '',
        iconName: 'HANDSHAKE',
        colorHex: '#FF9838',
        rawEntity: loan
      });
    }

    for (const lp of loanPayments) {
      const parentLoan = loanMap.get(Number(lp.loanId));
      if (!parentLoan) continue;
      const w = walletMap.get(Number(lp.walletId));
      const isLent = parentLoan.direction === 'LENT';
      const totalAmt = this.roundCurrency((Number(lp.principalPortion) || 0) + (Number(lp.interestPortion) || 0));
      entries.push({
        uniqueKey: `LP_${lp.id}`,
        entityId: lp.id,
        kind: 'LOAN_REPAYMENT',
        date: Number(lp.date) || Date.now(),
        title: `${isLent ? 'Repayment from' : 'Repayment to'} ${parentLoan.personName}`,
        subtitle: `${w ? w.name : 'Wallet'}${lp.note ? ' • ' + lp.note : ''}`,
        walletId: Number(lp.walletId),
        secondaryWalletId: null,
        categoryId: null,
        signedAmountText: `${isLent ? '+' : '−'}${this.formatAmount(totalAmt, this.currencySymbolForCode(w?.currency))}`,
        rawAmount: totalAmt,
        isPositiveFlow: isLent,
        badgeLabel: 'REPAYMENT',
        reference: '',
        tags: '',
        attachmentUri: '',
        iconName: 'HANDSHAKE',
        colorHex: isLent ? '#22C55E' : '#FF9838',
        rawEntity: lp
      });
    }

    for (const adj of adjustments) {
      const w = walletMap.get(Number(adj.walletId));
      const delta = Number(adj.deltaAmount) || 0;
      entries.push({
        uniqueKey: `ADJ_${adj.id}`,
        entityId: adj.id,
        kind: 'ADJUSTMENT',
        date: Number(adj.date) || Date.now(),
        title: `Balance Adjustment (${w ? w.name : 'Wallet'})`,
        subtitle: adj.reason || 'Reconciliation',
        walletId: Number(adj.walletId),
        secondaryWalletId: null,
        categoryId: null,
        signedAmountText: `${delta >= 0 ? '+' : '−'}${this.formatAmount(Math.abs(delta), this.currencySymbolForCode(w?.currency))}`,
        rawAmount: this.roundCurrency(Math.abs(delta)),
        isPositiveFlow: delta >= 0,
        badgeLabel: 'ADJUSTMENT',
        reference: '',
        tags: '',
        attachmentUri: '',
        iconName: 'TUNE',
        colorHex: '#A855F7',
        rawEntity: adj
      });
    }

    return entries.sort((a, b) => b.date - a.date);
  },

  calculateMonthlyReport(year, month, categories, transactions, transfers, loans, loanPayments, splits = []) {
    const startMs = new Date(year, month, 1, 0, 0, 0, 0).getTime();
    const endMs = new Date(year, month + 1, 0, 23, 59, 59, 999).getTime();

    const monthTxs = transactions.filter(t => t.date >= startMs && t.date <= endMs);
    const monthTransfers = transfers.filter(t => t.date >= startMs && t.date <= endMs);
    const monthLoanPayments = loanPayments.filter(lp => lp.date >= startMs && lp.date <= endMs);
    const loanMap = new Map(loans.map(l => [Number(l.id), l]));

    let grossIncome = 0;
    let grossExpense = 0;
    let expenseReversals = 0;

    for (const tx of monthTxs) {
      const amt = Number(tx.amount) || 0;
      if (tx.type === 'INCOME') grossIncome += amt;
      else if (tx.type === 'EXPENSE') grossExpense += amt;
      else if (tx.type === 'EXPENSE_REVERSAL') expenseReversals += amt;
    }

    let transferFees = 0;
    for (const tr of monthTransfers) {
      transferFees += Number(tr.feeAmount) || 0;
    }

    let loanInterestEarned = 0;
    let loanInterestPaid = 0;
    for (const lp of monthLoanPayments) {
      const interest = Number(lp.interestPortion) || 0;
      if (interest <= 0) continue;
      const parent = loanMap.get(Number(lp.loanId));
      if (!parent) continue;
      if (parent.direction === 'LENT') loanInterestEarned += interest;
      else loanInterestPaid += interest;
    }

    const totalIncome = this.roundCurrency(grossIncome + loanInterestEarned);
    const totalExpenses = this.roundCurrency(Math.max(0, grossExpense - expenseReversals) + transferFees + loanInterestPaid);
    const netSavings = this.roundCurrency(totalIncome - totalExpenses);
    const savingsRatePercent = totalIncome > 0 ? this.roundCurrency((netSavings / totalIncome) * 100) : 0;

    // Category Breakdown
    const catMap = new Map(categories.map(c => [Number(c.id), c]));
    const splitsByTx = new Map();
    for (const sp of splits) {
      const tid = Number(sp.transactionId);
      if (!splitsByTx.has(tid)) splitsByTx.set(tid, []);
      splitsByTx.get(tid).push(sp);
    }

    const expByCat = new Map();
    const incByCat = new Map();

    for (const tx of monthTxs) {
      const txSplits = splitsByTx.get(Number(tx.id));
      if (txSplits && txSplits.length > 0 && tx.type === 'EXPENSE') {
        for (const sp of txSplits) {
          const cid = Number(sp.categoryId) || 0;
          const cur = expByCat.get(cid) || { amount: 0, count: 0 };
          cur.amount += Number(sp.amount) || 0;
          cur.count += 1;
          expByCat.set(cid, cur);
        }
      } else if (tx.type === 'EXPENSE') {
        const cid = Number(tx.categoryId) || 0;
        const cur = expByCat.get(cid) || { amount: 0, count: 0 };
        cur.amount += Number(tx.amount) || 0;
        cur.count += 1;
        expByCat.set(cid, cur);
      } else if (tx.type === 'INCOME') {
        const cid = Number(tx.categoryId) || 0;
        const cur = incByCat.get(cid) || { amount: 0, count: 0 };
        cur.amount += Number(tx.amount) || 0;
        cur.count += 1;
        incByCat.set(cid, cur);
      }
    }

    const expenseBreakdown = Array.from(expByCat.entries())
      .map(([cid, data]) => {
        const cat = catMap.get(cid);
        const amount = this.roundCurrency(data.amount);
        return {
          categoryId: cid,
          categoryName: cat ? cat.name : 'Uncategorized',
          colorHex: cat ? cat.colorHex : '#F15A24',
          iconName: cat ? cat.iconName : 'CATEGORY',
          amount,
          percentage: totalExpenses > 0 ? this.roundCurrency((amount / totalExpenses) * 100) : 0,
          monthlyBudget: Number(cat?.monthlyBudget) || 0,
          transactionCount: data.count
        };
      })
      .sort((a, b) => b.amount - a.amount);

    const incomeBreakdown = Array.from(incByCat.entries())
      .map(([cid, data]) => {
        const cat = catMap.get(cid);
        const amount = this.roundCurrency(data.amount);
        return {
          categoryId: cid,
          categoryName: cat ? cat.name : 'Other Income',
          colorHex: cat ? cat.colorHex : '#22C55E',
          iconName: cat ? cat.iconName : 'PAYMENTS',
          amount,
          percentage: totalIncome > 0 ? this.roundCurrency((amount / totalIncome) * 100) : 0,
          monthlyBudget: 0,
          transactionCount: data.count
        };
      })
      .sort((a, b) => b.amount - a.amount);

    // Daily trend for current month
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const dailyExpenses = new Array(daysInMonth).fill(0);
    for (const tx of monthTxs) {
      if (tx.type === 'EXPENSE') {
        const d = new Date(tx.date).getDate();
        if (d >= 1 && d <= daysInMonth) {
          dailyExpenses[d - 1] = this.roundCurrency(dailyExpenses[d - 1] + (Number(tx.amount) || 0));
        }
      }
    }

    return {
      year,
      month,
      totalIncome,
      totalExpenses,
      transferFees: this.roundCurrency(transferFees),
      loanInterestEarned: this.roundCurrency(loanInterestEarned),
      loanInterestPaid: this.roundCurrency(loanInterestPaid),
      netSavings,
      savingsRatePercent,
      expenseBreakdown,
      incomeBreakdown,
      dailyExpenses,
      transactionCount: monthTxs.length + monthTransfers.length + monthLoanPayments.length
    };
  },

  buildWalletRunningBalanceLedger(wallet, unifiedLedger) {
    const wId = Number(wallet.id);
    const relevant = unifiedLedger
      .filter(e => Number(e.walletId) === wId || Number(e.secondaryWalletId) === wId)
      .slice()
      .sort((a, b) => a.date - b.date);

    let running = Number(wallet.openingBalance) || 0;
    const rows = [];
    for (const entry of relevant) {
      let delta = 0;
      if (entry.kind === 'INCOME' || entry.kind === 'EXPENSE_REVERSAL') {
        delta = entry.rawAmount;
      } else if (entry.kind === 'EXPENSE') {
        delta = -entry.rawAmount;
      } else if (entry.kind === 'TRANSFER') {
        const tr = entry.rawEntity;
        if (Number(tr.sourceWalletId) === wId) {
          delta = -(Number(tr.amount) || 0) - (Number(tr.feeAmount) || 0);
        } else if (Number(tr.destinationWalletId) === wId) {
          delta = tr.destinationAmount !== null && tr.destinationAmount !== undefined
            ? Number(tr.destinationAmount)
            : Number(tr.amount) || 0;
        }
      } else if (entry.kind === 'LOAN_LENT') {
        delta = -entry.rawAmount;
      } else if (entry.kind === 'LOAN_BORROWED') {
        delta = entry.rawAmount;
      } else if (entry.kind === 'LOAN_REPAYMENT') {
        delta = entry.isPositiveFlow ? entry.rawAmount : -entry.rawAmount;
      } else if (entry.kind === 'ADJUSTMENT') {
        delta = Number(entry.rawEntity.deltaAmount) || 0;
      }
      running = this.roundCurrency(running + delta);
      rows.push({
        entry,
        deltaAmount: this.roundCurrency(delta),
        runningBalanceAfter: running
      });
    }
    return rows.reverse();
  }
};

export const SubscriptionManager = {
  FREE_MAX_WALLETS: 4,
  FREE_MAX_DAILY_TRANSACTIONS: 5,
  FREE_MAX_TOTAL_LOANS: 2,
  FREE_MAX_EXPENSE_CATEGORIES: 7,
  FREE_MAX_INCOME_CATEGORIES: 4,
  FREE_MAX_RECURRING_BILLS: 2,
  FREE_ALLOWED_ACCENT_HEXES: new Set(['#F15A24', '#14B8A6']),
  FREE_ALLOWED_BOX_HEXES: new Set(['', '#141416', '#1E293B', '#FFFFFF']),
  THREE_DAYS_MILLIS: 3 * 24 * 60 * 60 * 1000,

  resolveActiveTier(tierString, expiresAtMillis, nowMillis = Date.now()) {
    const t = String(tierString || 'FREE').toUpperCase();
    if (t === 'LIFETIME') return 'LIFETIME';
    if (t === 'THREE_DAYS') {
      if (expiresAtMillis && Number(expiresAtMillis) > nowMillis) return 'THREE_DAYS';
      return 'FREE';
    }
    return 'FREE';
  },

  isSubscriptionActive(tierString, expiresAtMillis, nowMillis = Date.now()) {
    return this.resolveActiveTier(tierString, expiresAtMillis, nowMillis) !== 'FREE';
  },

  canAddWallet(currentActiveWalletCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return currentActiveWalletCount < this.FREE_MAX_WALLETS;
  },

  canAddTransactionToday(todayCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return todayCount < this.FREE_MAX_DAILY_TRANSACTIONS;
  },

  canAddLoan(totalLoanCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return totalLoanCount < this.FREE_MAX_TOTAL_LOANS;
  },

  canAddCategory(categoryType, currentActiveTypeCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    if (String(categoryType).toUpperCase() === 'INCOME') {
      return currentActiveTypeCount < this.FREE_MAX_INCOME_CATEGORIES;
    }
    return currentActiveTypeCount < this.FREE_MAX_EXPENSE_CATEGORIES;
  },

  canAddRecurringBill(currentRecurringCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return currentRecurringCount < this.FREE_MAX_RECURRING_BILLS;
  },

  isAccentColorAllowed(hex, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return this.FREE_ALLOWED_ACCENT_HEXES.has(String(hex || '').trim().toUpperCase());
  },

  isBoxColorAllowed(hex, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return this.FREE_ALLOWED_BOX_HEXES.has(String(hex || '').trim().toUpperCase());
  },

  async sha256Hex(input) {
    const encoder = new TextEncoder();
    const data = encoder.encode(input);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(hashBuffer))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
  },

  async verifyAndDecodeOfflineCode(rawCode) {
    const normalized = String(rawCode || '').trim().toUpperCase();
    if (!normalized) return null;
    try {
      const resp = await fetch('./assets/activation_codes.json');
      if (!resp.ok) return null;
      const bank = await resp.json();
      const digest = await this.sha256Hex(`POCKET_SALT_2026:${normalized}`);
      if (Array.isArray(bank.lifetimeHashes) && bank.lifetimeHashes.includes(digest)) {
        return 'LIFETIME';
      }
      if (Array.isArray(bank.threeDaysHashes) && bank.threeDaysHashes.includes(digest)) {
        return 'THREE_DAYS';
      }
    } catch (_) {}
    return null;
  }
};

export function evaluateMathExpression(expr) {
  if (!expr) return null;
  const normalized = PocketLocalizer.normalizeToAsciiDigits(String(expr))
    .replace(/,/g, '')
    .replace(/×/g, '*')
    .replace(/÷/g, '/')
    .replace(/−/g, '-')
    .trim();
  if (!normalized) return null;
  if (!/^[0-9+\-*/.() ]+$/.test(normalized)) return null;
  try {
    const cleaned = normalized.replace(/[+\-*/. ]+$/, '');
    if (!cleaned) return null;
    // Safe arithmetic evaluation using Function after strict regex whitelist
    const val = Function(`"use strict"; return (${cleaned});`)();
    if (typeof val === 'number' && Number.isFinite(val)) {
      return AccountingEngine.roundCurrency(val);
    }
  } catch (_) {}
  return null;
}

export function parseFinancialSms(rawSms, wallets = []) {
  const text = PocketLocalizer.normalizeToAsciiDigits(String(rawSms || '').trim());
  if (!text) return null;
  const lower = text.toLowerCase();

  let providerHint = '';
  if (lower.includes('bkash')) providerHint = 'Bkash';
  else if (lower.includes('nagad')) providerHint = 'Nagad';
  else if (lower.includes('rocket') || lower.includes('dbbl')) providerHint = 'Rocket';
  else if (lower.includes('upay')) providerHint = 'Upay';
  else if (lower.includes('mtb') || lower.includes('mutual trust')) providerHint = 'MTB';
  else if (lower.includes('brac') || lower.includes('astha')) providerHint = 'Brac';
  else if (lower.includes('card') || lower.includes('visa') || lower.includes('mastercard')) providerHint = 'Card';
  else if (lower.includes('ac ') || lower.includes('a/c') || lower.includes('account') || lower.includes('bank')) providerHint = 'Bank';

  const matchedWallet = providerHint
    ? wallets.find(w => w.name.toLowerCase().includes(providerHint.toLowerCase()) || w.iconName.toLowerCase().includes(providerHint.toLowerCase()))
    : null;

  const feeMatch = text.match(/(?:fee|charge|vat|comm\.?)\s*(?:amount)?\s*[:\-]?\s*(?:tk\.?|bdt|\$|usd)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i);
  const extractedFee = feeMatch ? parseFloat(feeMatch[1].replace(/,/g, '')) || 0 : 0;

  const withoutBalance = text.replace(/(?:balance|bal|avl\.?\s*bal|current\s*balance)\s*[:\-]?\s*(?:tk\.?|bdt|\$|usd)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?/gi, ' ');
  const withoutFee = feeMatch ? withoutBalance.replace(feeMatch[0], ' ') : withoutBalance;

  const amtMatch = withoutFee.match(/(?:tk\.?|bdt|taka|usd|\$|€|£|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:tk\.?|bdt|taka)/i);
  let principalAmount = null;
  if (amtMatch) {
    const rawNum = (amtMatch[1] || amtMatch[2] || '').replace(/,/g, '');
    principalAmount = parseFloat(rawNum);
  } else {
    const fallback = withoutFee.match(/\b([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b/g);
    if (fallback) {
      for (const candidate of fallback) {
        const clean = candidate.replace(/,/g, '');
        if (clean.length === 11 && clean.startsWith('01')) continue;
        const n = parseFloat(clean);
        if (n > 0) {
          principalAmount = n;
          break;
        }
      }
    }
  }
  if (!principalAmount || principalAmount <= 0) return null;

  const trxMatch = text.match(/(?:trxid|txnid|txn\s*id|trx\s*id|trans\s*id|ref(?:erence)?\s*(?:no|id)?)\s*[:#\-]?\s*([A-Za-z0-9]{5,24})/i);
  const reference = trxMatch ? trxMatch[1].trim() : '';

  const isIncome =
    lower.includes('you have received') ||
    lower.includes('cash in') ||
    lower.includes('received tk') ||
    lower.includes('received bdt') ||
    lower.includes('money received') ||
    lower.includes('credited') ||
    lower.includes('deposited') ||
    lower.includes('refund');

  let actionLabel = 'SMS Entry';
  if (lower.includes('cash out')) actionLabel = 'Cash Out';
  else if (lower.includes('cash in')) actionLabel = 'Cash In';
  else if (lower.includes('send money')) actionLabel = 'Send Money';
  else if (lower.includes('payment')) actionLabel = 'Merchant Payment';
  else if (lower.includes('pay bill') || lower.includes('bill pay')) actionLabel = 'Bill Pay';
  else if (lower.includes('mobile recharge') || lower.includes('airtime')) actionLabel = 'Mobile Recharge';
  else if (lower.includes('received') || lower.includes('credited')) actionLabel = 'Received';

  return {
    amount: AccountingEngine.roundCurrency(principalAmount),
    feeAmount: AccountingEngine.roundCurrency(extractedFee),
    reference,
    inferredType: isIncome ? 'INCOME' : 'EXPENSE',
    matchedWalletId: matchedWallet ? matchedWallet.id : null,
    providerName: providerHint,
    summaryNote: `${providerHint ? providerHint + ' ' : ''}${actionLabel}`.trim()
  };
}

export const BackupManager = {
  createBackupPayload(state) {
    return {
      schemaVersion: 1,
      format: 'pocket-backup-v1',
      exportedAtMillis: Date.now(),
      settings: state.settings,
      wallets: state.wallets,
      categories: state.categories,
      transactions: state.transactions,
      transactionSplits: state.transactionSplits || [],
      transfers: state.transfers,
      loans: state.loans,
      loanPayments: state.loanPayments,
      adjustments: state.adjustments,
      recurringTransactions: state.recurringTransactions || [],
      savingsGoals: state.savingsGoals || []
    };
  },

  validateAndParseBackup(rawJson) {
    try {
      const parsed = JSON.parse(String(rawJson || '').trim());
      if (!parsed || typeof parsed !== 'object') {
        return { isValid: false, errorMessage: 'Invalid JSON structure.' };
      }
      if (!Array.isArray(parsed.wallets) || !Array.isArray(parsed.categories) || !Array.isArray(parsed.transactions)) {
        return { isValid: false, errorMessage: 'Missing required Pocket backup collections (wallets, categories, transactions).' };
      }
      return {
        isValid: true,
        payload: {
          schemaVersion: parsed.schemaVersion || 1,
          format: parsed.format || 'pocket-backup-v1',
          exportedAtMillis: parsed.exportedAtMillis || Date.now(),
          settings: { ...DEFAULT_SETTINGS, ...(parsed.settings || {}) },
          wallets: parsed.wallets || [],
          categories: parsed.categories || [],
          transactions: parsed.transactions || [],
          transactionSplits: parsed.transactionSplits || [],
          transfers: parsed.transfers || [],
          loans: parsed.loans || [],
          loanPayments: parsed.loanPayments || [],
          adjustments: parsed.adjustments || [],
          recurringTransactions: parsed.recurringTransactions || [],
          savingsGoals: parsed.savingsGoals || []
        }
      };
    } catch (e) {
      return { isValid: false, errorMessage: `JSON parse error: ${e.message}` };
    }
  },

  exportLedgerToCsv(unifiedLedger) {
    const header = 'Date,Kind,Title,Details,Badge,SignedAmount,Amount,Reference,Tags';
    const rows = unifiedLedger.map(item => {
      const d = new Date(item.date).toISOString().replace('T', ' ').slice(0, 16);
      const esc = (v) => `"${String(v || '').replace(/"/g, '""')}"`;
      return [
        esc(d),
        esc(item.kind),
        esc(item.title),
        esc(item.subtitle),
        esc(item.badgeLabel),
        esc(item.signedAmountText),
        item.rawAmount,
        esc(item.reference),
        esc(item.tags)
      ].join(',');
    });
    return [header, ...rows].join('\n');
  },

  formatPersonKhataStatementText(khata, symbol = '৳') {
    const lines = [];
    lines.push(`📒 POCKET LOAN KHATA STATEMENT`);
    lines.push(`Person: ${khata.personName}`);
    lines.push(`Date: ${new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })}`);
    lines.push(`----------------------------------------`);
    lines.push(`Total Lent (Receivable): ${AccountingEngine.formatMoney(khata.outstandingReceivable, symbol)}`);
    lines.push(`Total Borrowed (Payable): ${AccountingEngine.formatMoney(khata.outstandingPayable, symbol)}`);
    const net = khata.netReceivableMinusPayable;
    if (Math.abs(net) < 0.005) {
      lines.push(`Net Position: SETTLED (${symbol} 0)`);
    } else if (net > 0) {
      lines.push(`Net Position: ${khata.personName} owes you ${AccountingEngine.formatMoney(net, symbol)}`);
    } else {
      lines.push(`Net Position: You owe ${khata.personName} ${AccountingEngine.formatMoney(Math.abs(net), symbol)}`);
    }
    lines.push(`----------------------------------------`);
    khata.loans.forEach((s, idx) => {
      const dStr = new Date(s.loan.startDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
      lines.push(`${idx + 1}. [${s.loan.direction}] ${AccountingEngine.formatMoney(s.originalPrincipal, symbol)} on ${dStr} (${s.walletName}) — Outstanding: ${AccountingEngine.formatMoney(s.outstandingPrincipal, symbol)} [${s.status}]`);
    });
    return lines.join('\n');
  }
};
