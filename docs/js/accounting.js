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
  fundFeatureEnabled: false,
  subscriptionTier: 'FREE',
  subscriptionActivatedAt: null,
  subscriptionExpiresAt: null,
  activatedCode: ''
};

export const VIRTUAL_FUND_WALLET_ID = -999;
export const VFUND_HINT_REGEX = /\[VFUND:(1|0):(-?[0-9]+(?:\.[0-9]+)?)(?::([0-9]+))?]/;

export function isDedicatedFundWallet(wallet) {
  return String(wallet?.type || '').toUpperCase() === 'FUND' ||
    String(wallet?.name || '').trim().toLowerCase() === 'fund';
}

export function parseWalletVirtualFund(wallet) {
  const hint = String(wallet?.accountHint || '');
  const m = hint.match(VFUND_HINT_REGEX);
  const isFundW = isDedicatedFundWallet(wallet);
  const hasVirtualFund = isFundW ? true : (m ? m[1] === '1' : Boolean(wallet?.hasVirtualFund));
  const openingFundBalance = isFundW
    ? Math.max(0, parseFloat(wallet?.openingBalance) || 0)
    : (m
      ? (parseFloat(m[2]) || 0)
      : (wallet?.openingFundBalance !== undefined ? (parseFloat(wallet.openingFundBalance) || 0) : 0));
  const fundResetAtMillis = m && m[3]
    ? (parseInt(m[3], 10) || 0)
    : (Number(wallet?.fundResetAtMillis) || 0);
  const cleanAccountHint = hint.replace(VFUND_HINT_REGEX, '').trim();
  return { hasVirtualFund, openingFundBalance, fundResetAtMillis, cleanAccountHint, isFundWallet: isFundW };
}

export function withWalletVirtualFund(wallet, enabled, openingFund = 0, resetAtMillis = null) {
  const prev = parseWalletVirtualFund(wallet);
  const roundedFund = Math.round((Number(openingFund) || 0) * 100) / 100;
  const resolvedReset = resetAtMillis !== null && resetAtMillis !== undefined
    ? (Number(resetAtMillis) || 0)
    : prev.fundResetAtMillis;
  const tag = resolvedReset > 0
    ? `[VFUND:${enabled ? '1' : '0'}:${roundedFund}:${resolvedReset}]`
    : `[VFUND:${enabled ? '1' : '0'}:${roundedFund}]`;
  const nextHint = prev.cleanAccountHint ? `${prev.cleanAccountHint} ${tag}` : tag;
  return {
    ...wallet,
    accountHint: nextHint,
    hasVirtualFund: Boolean(enabled),
    openingFundBalance: enabled ? roundedFund : 0,
    fundResetAtMillis: resolvedReset
  };
}

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

  calculateWalletSummary(wallet, transactions, transfers, loans, loanPayments, adjustments, fundFeatureEnabled = true) {
    const wId = Number(wallet.id);
    const vfMeta = parseWalletVirtualFund(wallet);
    const isLegacyFundWallet = vfMeta.isFundWallet;
    const fundCycleStartMillis = Number(vfMeta.fundResetAtMillis) || 0;

    let incomeTotal = 0;
    let expenseTotal = 0;
    let expenseReversalTotal = 0;
    let fundIncomeIn = 0;
    let fundOfficialSpentRaw = 0;
    let fundBorrowedOut = 0;
    let fundReturnedIn = 0;
    let fundTransferIn = 0;
    let fundTransferOut = 0;

    for (const tx of transactions) {
      if (Number(tx.walletId) !== wId) continue;
      const amt = Number(tx.amount) || 0;
      const txDate = Number(tx.date) || 0;
      const inCurrentFundCycle = txDate > fundCycleStartMillis;
      const tagsUpper = String(tx.tags || '').toUpperCase();
      if (tx.type === 'INCOME') {
        incomeTotal += amt;
        if (inCurrentFundCycle && (tagsUpper.includes('FUND_IN') || (isLegacyFundWallet && !tagsUpper.includes('PERSONAL')))) {
          fundIncomeIn += amt;
        }
      } else if (tx.type === 'EXPENSE') {
        expenseTotal += amt;
        if (inCurrentFundCycle) {
          if (tagsUpper.includes('FUND_BORROW')) {
            fundBorrowedOut += amt;
          } else if (tagsUpper.includes('FUND_EXPENSE') || (isLegacyFundWallet && !tagsUpper.includes('PERSONAL'))) {
            fundOfficialSpentRaw += amt;
          }
        }
      } else if (tx.type === 'EXPENSE_REVERSAL') {
        expenseReversalTotal += amt;
        if (inCurrentFundCycle) {
          if (tagsUpper.includes('FUND_BORROW')) {
            fundBorrowedOut = Math.max(0, fundBorrowedOut - amt);
          } else if (tagsUpper.includes('FUND_EXPENSE') || (isLegacyFundWallet && !tagsUpper.includes('PERSONAL'))) {
            fundOfficialSpentRaw = Math.max(0, fundOfficialSpentRaw - amt);
          }
        }
      }
    }

    let transfersIn = 0;
    let transfersOut = 0;
    let transferFees = 0;
    for (const tr of transfers) {
      const noteUpper = String(tr.note || '').toUpperCase();
      const trDate = Number(tr.date) || 0;
      const inCurrentFundCycle = trDate > fundCycleStartMillis;
      const isSameWalletInternal = Number(tr.sourceWalletId) === wId && Number(tr.destinationWalletId) === wId;
      const srcAmt = Number(tr.principalAmount ?? tr.amount) || 0;
      const destAmt = tr.destinationAmount !== null && tr.destinationAmount !== undefined && Number(tr.destinationAmount) > 0
        ? Number(tr.destinationAmount)
        : srcAmt;
      const trFee = Number(tr.feeAmount) || 0;

      if (isSameWalletInternal) {
        if (inCurrentFundCycle) {
          if (noteUpper.includes('[FUND_RETURN]')) {
            fundReturnedIn += destAmt;
          } else if (noteUpper.includes('[FUND_TOPUP]')) {
            fundTransferIn += destAmt;
          } else if (noteUpper.includes('[FUND_BORROW]')) {
            fundBorrowedOut += srcAmt;
          } else if (noteUpper.includes('[FUND_SETTLE]')) {
            fundTransferOut += srcAmt;
          }
        }
        continue;
      }

      if (Number(tr.destinationWalletId) === wId) {
        transfersIn += destAmt;
        if (inCurrentFundCycle) {
          if (noteUpper.includes('[FUND_RETURN]')) {
            fundReturnedIn += destAmt;
          } else if (noteUpper.includes('[FUND_TOPUP]') || noteUpper.includes('[FUND_TRANSFER]') || isLegacyFundWallet) {
            fundTransferIn += destAmt;
          }
        }
      }
      if (Number(tr.sourceWalletId) === wId) {
        transfersOut += srcAmt;
        transferFees += trFee;
        if (inCurrentFundCycle) {
          if (noteUpper.includes('[FUND_BORROW]')) {
            fundBorrowedOut += srcAmt;
          } else if (noteUpper.includes('[FUND_SETTLE]') || noteUpper.includes('[FUND_TRANSFER]') || isLegacyFundWallet) {
            fundTransferOut += srcAmt;
          }
        }
      }
    }

    const loanMap = new Map(loans.map(l => [Number(l.id), l]));
    const loansWithDisbursementActivity = new Set();
    let loansBorrowedIn = 0;
    let loansLentOut = 0;
    let loanRepaymentsReceivedIn = 0;
    let loanRepaymentsPaidOut = 0;
    let fundLoanIn = 0;
    let fundLoanOut = 0;

    for (const act of loanPayments) {
      const actType = act.activityType || 'REPAYMENT';
      if (actType === 'DISBURSEMENT') {
        loansWithDisbursementActivity.add(Number(act.loanId));
      }
      if (Number(act.walletId) !== wId) continue;
      const parentLoan = loanMap.get(Number(act.loanId));
      if (!parentLoan) continue;
      const isLent = parentLoan.direction === 'LENT';
      const pPortion = Number(act.principalPortion) || 0;
      const actDate = Number(act.date) || 0;
      const inCurrentFundCycle = actDate > fundCycleStartMillis;
      const actNoteUpper = String(act.note || '').toUpperCase();
      const loanNoteUpper = String(parentLoan.note || '').toUpperCase();
      const isFundLoanBorrowAct = fundFeatureEnabled && inCurrentFundCycle && (
        actNoteUpper.includes('[FUND_LOAN_BORROW]') ||
        (actType === 'DISBURSEMENT' && loanNoteUpper.includes('[FUND_LOAN_BORROW]'))
      );
      const isFundLoanAct = fundFeatureEnabled && inCurrentFundCycle && !isFundLoanBorrowAct && (
        actNoteUpper.includes('[FUND_LOAN]') ||
        (actType === 'DISBURSEMENT' && loanNoteUpper.includes('[FUND_LOAN]'))
      );

      if (actType === 'DISBURSEMENT' || actType === 'ADJUSTMENT') {
        if (isLent) {
          loansLentOut += pPortion;
          if (isFundLoanBorrowAct) fundBorrowedOut += pPortion;
          else if (isFundLoanAct) fundLoanOut += pPortion;
        } else {
          loansBorrowedIn += pPortion;
          if (isFundLoanAct) fundLoanIn += pPortion;
        }
      } else if (actType === 'REPAYMENT') {
        if (isLent) {
          loanRepaymentsReceivedIn += pPortion;
          if (isFundLoanBorrowAct || (isFundLoanAct && loanNoteUpper.includes('[FUND_LOAN_BORROW]'))) {
            fundReturnedIn += pPortion;
          } else if (isFundLoanAct) {
            fundLoanIn += pPortion;
          }
        } else {
          loanRepaymentsPaidOut += pPortion;
          if (isFundLoanBorrowAct) fundBorrowedOut += pPortion;
          else if (isFundLoanAct) fundLoanOut += pPortion;
        }
      }
    }

    // Fallback for loans created without an explicit DISBURSEMENT activity row
    for (const loan of loans) {
      if (loansWithDisbursementActivity.has(Number(loan.id))) continue;
      if (Number(loan.initialWalletId) !== wId) continue;
      const p = Number(loan.principalAmount) || 0;
      const loanDate = Number(loan.startDate) || 0;
      const inCurrentFundCycle = loanDate > fundCycleStartMillis;
      const loanNoteUpper = String(loan.note || '').toUpperCase();
      const isFundLoanBorrow = fundFeatureEnabled && inCurrentFundCycle && loanNoteUpper.includes('[FUND_LOAN_BORROW]');
      const isFundLoan = fundFeatureEnabled && inCurrentFundCycle && !isFundLoanBorrow && loanNoteUpper.includes('[FUND_LOAN]');
      if (loan.direction === 'BORROWED') {
        loansBorrowedIn += p;
        if (isFundLoan) fundLoanIn += p;
      } else if (loan.direction === 'LENT') {
        loansLentOut += p;
        if (isFundLoanBorrow) fundBorrowedOut += p;
        else if (isFundLoan) fundLoanOut += p;
      }
    }

    let manualAdjustmentsNet = 0;
    let fundAdjustmentsNet = 0;
    for (const adj of adjustments) {
      if (adj.isReversed) continue;
      if (Number(adj.walletId) === wId) {
        const diff = (adj.difference !== undefined ? Number(adj.difference) : Number(adj.deltaAmount)) || 0;
        manualAdjustmentsNet += diff;
        const adjTextUpper = `${adj.reason || ''} ${adj.note || ''}`.toUpperCase();
        const inCurrentFundCycle = (Number(adj.date) || 0) > fundCycleStartMillis;
        if (inCurrentFundCycle && adjTextUpper.includes('[FUND_ADJ]')) {
          fundAdjustmentsNet += diff;
        }
      }
    }

    const opening = Number(wallet.openingBalance) || 0;
    // Note: if transfer fees were already recorded as separate EXPENSE transactions with transferId, don't double subtract
    const txTransferFees = transactions
      .filter(tx => Number(tx.walletId) === wId && tx.type === 'EXPENSE' && tx.transferId)
      .reduce((s, tx) => s + (Number(tx.amount) || 0), 0);
    const unrecordedTransferFees = Math.max(0, transferFees - txTransferFees);

    const rawBalance =
      opening +
      incomeTotal -
      expenseTotal +
      expenseReversalTotal +
      transfersIn -
      transfersOut -
      unrecordedTransferFees +
      loansBorrowedIn -
      loansLentOut +
      loanRepaymentsReceivedIn -
      loanRepaymentsPaidOut +
      manualAdjustmentsNet;

    const currentBalance = this.roundCurrency(rawBalance);
    const hasFundActivity =
      isLegacyFundWallet ||
      Math.abs(vfMeta.openingFundBalance) > 0.005 ||
      fundIncomeIn > 0.005 ||
      Math.abs(fundOfficialSpentRaw) > 0.005 ||
      fundBorrowedOut > 0.005 ||
      fundReturnedIn > 0.005 ||
      fundTransferIn > 0.005 ||
      fundTransferOut > 0.005 ||
      fundLoanIn > 0.005 ||
      fundLoanOut > 0.005 ||
      Math.abs(fundAdjustmentsNet) > 0.005;
    const hasVirtualFund = Boolean(fundFeatureEnabled && (vfMeta.hasVirtualFund || hasFundActivity));
    const openingFund = hasVirtualFund ? this.roundCurrency(vfMeta.openingFundBalance) : 0;
    const fundOfficialSpent = this.roundCurrency(Math.max(0, fundOfficialSpentRaw));
    const fundBorrowedDue = hasVirtualFund ? this.roundCurrency(Math.max(0, fundBorrowedOut - fundReturnedIn)) : 0;
    const rawFundBalance = !fundFeatureEnabled
      ? 0
      : isLegacyFundWallet
        ? currentBalance
        : (hasVirtualFund
          ? (openingFund + fundIncomeIn + fundTransferIn + fundReturnedIn + fundLoanIn - fundOfficialSpent - fundBorrowedOut - fundTransferOut - fundLoanOut + fundAdjustmentsNet)
          : 0);
    const fundBalance = hasVirtualFund ? this.roundCurrency(rawFundBalance) : 0;
    const personalBalance = hasVirtualFund ? this.roundCurrency(currentBalance - fundBalance) : currentBalance;

    return {
      wallet: {
        ...wallet,
        hasVirtualFund,
        openingFundBalance: openingFund,
        fundResetAtMillis: fundCycleStartMillis
      },
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
      currentBalance,
      hasVirtualFund,
      openingFundBalance: openingFund,
      fundResetAtMillis: fundCycleStartMillis,
      fundIncomeIn: this.roundCurrency(fundIncomeIn),
      fundTransferIn: this.roundCurrency(fundTransferIn),
      fundTransferOut: this.roundCurrency(fundTransferOut),
      fundOfficialSpent,
      fundBalance,
      personalBalance,
      fundBorrowedDue,
      fundBorrowedTotal: this.roundCurrency(fundBorrowedOut),
      fundReturnedTotal: this.roundCurrency(fundReturnedIn),
      fundLoanIn: this.roundCurrency(fundLoanIn),
      fundLoanOut: this.roundCurrency(fundLoanOut)
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
        const payments = (paymentsByLoan.get(Number(loan.id)) || []).slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
        let totalDisbursed = 0;
        let totalAdditions = 0;
        let principalRepaid = 0;
        let interestTotal = 0;

        for (const p of payments) {
          const type = p.activityType || 'REPAYMENT';
          const pPortion = Number(p.principalPortion) || 0;
          const iPortion = Number(p.interestPortion) || 0;
          if (type === 'DISBURSEMENT') {
            totalDisbursed += pPortion;
          } else if (type === 'ADJUSTMENT') {
            totalAdditions += pPortion;
          } else {
            principalRepaid += pPortion;
            interestTotal += iPortion;
          }
        }
        principalRepaid = this.roundCurrency(principalRepaid);
        interestTotal = this.roundCurrency(interestTotal);

        const basePrincipal = totalDisbursed > 0 ? totalDisbursed : (Number(loan.principalAmount) || 0);
        const orig = this.roundCurrency(basePrincipal);
        const rawOutstanding = this.roundCurrency(orig + totalAdditions - principalRepaid);
        const outstandingPrincipal = Math.max(0, rawOutstanding);
        const isSettled = outstandingPrincipal <= 0.005;
        const isOverdue = !isSettled && loan.dueDate && Number(loan.dueDate) < nowMillis;
        const status = isSettled ? 'SETTLED' : isOverdue ? 'OVERDUE' : principalRepaid > 0.005 ? 'PARTIAL' : 'ACTIVE';
        const initialWId = Number(loan.initialWalletId) || 0;
        const w = initialWId > 0 ? walletMap.get(initialWId) : null;
        const baseWName = initialWId <= 0 ? 'No wallet' : (w ? w.name : 'No wallet');
        const noteUpper = String(loan.note || '').toUpperCase();
        const isFundLoanNote = noteUpper.includes('[FUND_LOAN]') || noteUpper.includes('[FUND_LOAN_BORROW]');
        const walletName = (isFundLoanNote && baseWName !== 'No wallet') ? `${baseWName}-Fund` : baseWName;
        return {
          loan,
          walletName,
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

  buildUnifiedLedger(wallets, categories, transactions, transfers, loans, loanPayments, adjustments, fundFeatureEnabled = true) {
    const walletMap = new Map(wallets.map(w => [Number(w.id), w]));
    const catMap = new Map(categories.map(c => [Number(c.id), c]));
    const loanMap = new Map(loans.map(l => [Number(l.id), l]));
    const entries = [];

    for (const tx of transactions) {
      const w = walletMap.get(Number(tx.walletId));
      if (!fundFeatureEnabled && w && isDedicatedFundWallet(w)) continue;
      const walletName = w ? w.name : 'Wallet';
      const vfMeta = parseWalletVirtualFund(w);
      const cat = catMap.get(Number(tx.categoryId));
      const catName = cat ? cat.name : (tx.type === 'INCOME' ? 'Income' : 'Uncategorized');
      const isIncome = tx.type === 'INCOME';
      const isReversal = tx.type === 'EXPENSE_REVERSAL';
      const tagsUpper = String(tx.tags || '').toUpperCase();
      const isFundBorrow = fundFeatureEnabled && tagsUpper.includes('FUND_BORROW');
      const isFundExpense = fundFeatureEnabled && tagsUpper.includes('FUND_EXPENSE');
      const isFundIn = fundFeatureEnabled && tagsUpper.includes('FUND_IN');
      const isFundSubWalletTx = isFundBorrow || isFundExpense || isFundIn;

      const subWalletLabel = isFundSubWalletTx
        ? `${walletName}-Fund`
        : (fundFeatureEnabled && vfMeta.hasVirtualFund ? `${walletName}-Personal` : walletName);

      const badgeLabel = tx.transferId
        ? 'Transfer Fee'
        : tx.parentTransactionId
          ? 'Service Fee'
          : tx.loanActivityId
            ? 'Loan Interest'
            : isReversal
              ? 'Refund Reversal'
              : isFundBorrow
                ? 'Personal Borrow'
                : isFundExpense
                  ? 'Fund Expense'
                  : isFundIn
                    ? 'Added to Fund'
                    : '';

      const signedAmt = isIncome || isReversal ? Number(tx.amount) || 0 : -(Number(tx.amount) || 0);
      entries.push({
        uniqueKey: `TX_${tx.id}`,
        entityId: Number(tx.id),
        kind: isIncome ? 'INCOME' : isReversal ? 'EXPENSE_REVERSAL' : 'EXPENSE',
        date: Number(tx.date) || Date.now(),
        title: (tx.note && String(tx.note).trim()) ? String(tx.note).trim() : catName,
        subtitle: `${catName} · ${subWalletLabel}`,
        routeText: subWalletLabel,
        walletId: Number(tx.walletId),
        walletName,
        secondaryWalletId: null,
        secondaryWalletName: null,
        categoryId: tx.categoryId ? Number(tx.categoryId) : null,
        categoryName: catName,
        signedAmount: this.roundCurrency(signedAmt),
        signedAmountText: `${isIncome || isReversal ? '+' : '−'}${this.formatMoney(Math.abs(tx.amount), this.currencySymbolForCode(w?.currency))}`,
        rawAmount: this.roundCurrency(tx.amount),
        isPositiveFlow: isIncome || isReversal,
        badgeLabel,
        note: tx.note || '',
        reference: tx.reference || '',
        tags: tx.tags || '',
        attachmentUri: tx.attachmentUri || '',
        iconName: cat?.iconName || (isIncome ? 'TRENDING_UP' : 'RECEIPT'),
        colorHex: cat?.colorHex || (isIncome ? '#15803D' : '#B91C1C'),
        rawEntity: tx
      });
    }

    for (const tr of transfers) {
      const srcW = walletMap.get(Number(tr.sourceWalletId));
      const dstW = walletMap.get(Number(tr.destinationWalletId));
      const srcName = srcW ? srcW.name : 'Source';
      const dstName = dstW ? dstW.name : 'Destination';
      const noteUpper = String(tr.note || '').toUpperCase();
      const isFundTr = noteUpper.includes('[FUND_TRANSFER]');
      const isBorrowTr = noteUpper.includes('[FUND_BORROW]');
      const isSettleTr = noteUpper.includes('[FUND_SETTLE]');
      const isReturnTr = noteUpper.includes('[FUND_RETURN]');
      const isTopupTr = noteUpper.includes('[FUND_TOPUP]');
      const hasExplicitFundTag = isFundTr || isBorrowTr || isSettleTr || isReturnTr || isTopupTr;
      const isSameW = Number(tr.sourceWalletId) === Number(tr.destinationWalletId);
      if (!fundFeatureEnabled && !hasExplicitFundTag && (isSameW || isDedicatedFundWallet(srcW) || isDedicatedFundWallet(dstW))) {
        continue;
      }

      const cleanNote = String(tr.note || '')
        .replace(/\[FUND_TRANSFER\]/gi, '')
        .replace(/\[FUND_BORROW\]/gi, '')
        .replace(/\[FUND_SETTLE\]/gi, '')
        .replace(/\[FUND_RETURN\]/gi, '')
        .replace(/\[FUND_TOPUP\]/gi, '')
        .trim();

      const srcVf = parseWalletVirtualFund(srcW);
      const dstVf = parseWalletVirtualFund(dstW);
      const routeText = (isReturnTr || isTopupTr)
        ? `${srcName}-Personal → ${dstName}-Fund`
        : (isBorrowTr || isSettleTr)
          ? `${srcName}-Fund → ${dstName}-Personal`
          : isFundTr
            ? `${srcName}-Fund → ${dstName}-Fund`
            : (fundFeatureEnabled && (srcVf.hasVirtualFund || dstVf.hasVirtualFund))
              ? `${srcName}-Personal → ${dstName}-Personal`
              : `${srcName} → ${dstName}`;

      const transferSubtitle = isReturnTr
        ? `Return to Fund · ${routeText}`
        : isTopupTr
          ? `Official Fund In · ${routeText}`
          : isBorrowTr
            ? `Personal Borrow · ${routeText}`
            : isSettleTr
              ? `Official Fund Out · ${routeText}`
              : isFundTr
                ? `Fund Transfer · ${routeText}`
                : `Transfer · ${routeText}`;

      const badgeLabel = isFundTr
        ? 'Fund Transfer'
        : isBorrowTr
          ? 'Personal Borrow'
          : isSettleTr
            ? 'Official Fund Out'
            : isReturnTr
              ? 'Returned to Fund'
              : isTopupTr
                ? 'Official Fund In'
                : (Number(tr.feeAmount) > 0 ? `Fee: ${this.formatMoney(tr.feeAmount, this.currencySymbolForCode(srcW?.currency))}` : 'Transfer');

      const trAmt = Number(tr.principalAmount ?? tr.amount) || 0;
      const trTags = isReturnTr
        ? 'FUND_RETURN'
        : isTopupTr
          ? 'FUND_TOPUP'
          : isBorrowTr
            ? 'FUND_BORROW'
            : isSettleTr
              ? 'FUND_SETTLE'
              : isFundTr
                ? 'FUND_TRANSFER'
                : '';

      const iconName = isReturnTr
        ? 'KEYBOARD_RETURN'
        : isTopupTr
          ? 'ADD_CIRCLE'
          : isBorrowTr
            ? 'PAYMENTS'
            : 'SWAP_HORIZ';

      const colorHex = isReturnTr
        ? '#15803D'
        : isTopupTr
          ? '#0D9488'
          : isBorrowTr
            ? '#DC2626'
            : '#1D4ED8';

      entries.push({
        uniqueKey: `TR_${tr.id}`,
        entityId: Number(tr.id),
        kind: 'TRANSFER',
        date: Number(tr.date) || Date.now(),
        title: cleanNote ? cleanNote : routeText,
        subtitle: transferSubtitle,
        routeText,
        walletId: Number(tr.sourceWalletId),
        walletName: srcName,
        secondaryWalletId: Number(tr.destinationWalletId),
        secondaryWalletName: dstName,
        categoryId: null,
        categoryName: 'Transfer',
        signedAmount: this.roundCurrency(trAmt),
        signedAmountText: `⇄ ${this.formatMoney(trAmt, this.currencySymbolForCode(srcW?.currency))}`,
        rawAmount: this.roundCurrency(trAmt),
        feeAmount: this.roundCurrency(tr.feeAmount || 0),
        isPositiveFlow: false,
        badgeLabel,
        note: tr.note || '',
        reference: '',
        tags: trTags,
        attachmentUri: '',
        iconName,
        colorHex,
        rawEntity: tr
      });
    }

    const loansWithDisbursementActivity = new Set();
    for (const lp of loanPayments) {
      const actType = lp.activityType || 'REPAYMENT';
      if (actType === 'DISBURSEMENT') {
        loansWithDisbursementActivity.add(Number(lp.loanId));
      }
      const parentLoan = loanMap.get(Number(lp.loanId));
      if (!parentLoan) continue;
      const w = walletMap.get(Number(lp.walletId));
      const walletName = w ? w.name : 'No wallet';
      const vfMeta = parseWalletVirtualFund(w);
      const isLent = parentLoan.direction === 'LENT';
      const actNoteUpper = String(lp.note || '').toUpperCase();
      const loanNoteUpper = String(parentLoan.note || '').toUpperCase();
      const isFundLoanBorrowAct = fundFeatureEnabled && (
        actNoteUpper.includes('[FUND_LOAN_BORROW]') ||
        (actType === 'DISBURSEMENT' && loanNoteUpper.includes('[FUND_LOAN_BORROW]'))
      );
      const isFundLoanAct = fundFeatureEnabled && !isFundLoanBorrowAct && (
        actNoteUpper.includes('[FUND_LOAN]') ||
        (actType === 'DISBURSEMENT' && loanNoteUpper.includes('[FUND_LOAN]'))
      );
      const cleanActNote = String(lp.note || '')
        .replace(/\[FUND_LOAN_BORROW\]/gi, '')
        .replace(/\[FUND_LOAN\]/gi, '')
        .trim();

      const isInflow = (actType === 'DISBURSEMENT' || actType === 'ADJUSTMENT') ? !isLent : isLent;
      const pPortion = this.roundCurrency(Number(lp.principalPortion) || 0);
      const signedAmt = isInflow ? pPortion : -pPortion;
      const typeLabel = actType === 'DISBURSEMENT'
        ? (isLent ? `Lent to ${parentLoan.personName}` : `Borrowed from ${parentLoan.personName}`)
        : actType === 'REPAYMENT'
          ? (isLent ? `Repayment from ${parentLoan.personName}` : `Repaid to ${parentLoan.personName}`)
          : `Loan adjustment (${parentLoan.personName})`;
      const subWalletName = walletName === 'No wallet'
        ? walletName
        : (isFundLoanAct || isFundLoanBorrowAct)
          ? `${walletName}-Fund`
          : (fundFeatureEnabled && vfMeta.hasVirtualFund ? `${walletName}-Personal` : walletName);

      const badgeLabel = isFundLoanBorrowAct
        ? 'Personal Borrow'
        : isFundLoanAct
          ? (isLent ? 'Fund Loan (Receivable)' : 'Fund Loan (Payable)')
          : (isLent ? 'Loan (Receivable)' : 'Loan (Payable)');

      entries.push({
        uniqueKey: `LA_${lp.id}`,
        entityId: Number(lp.id),
        kind: actType === 'REPAYMENT' ? 'LOAN_REPAYMENT' : (isLent ? 'LOAN_LENT' : 'LOAN_BORROWED'),
        date: Number(lp.date) || Date.now(),
        title: cleanActNote || typeLabel,
        subtitle: `${typeLabel} · ${subWalletName}`,
        routeText: subWalletName,
        walletId: Number(lp.walletId) || 0,
        walletName,
        secondaryWalletId: null,
        categoryId: null,
        categoryName: 'Loan Principal',
        signedAmount: signedAmt,
        signedAmountText: `${isInflow ? '+' : '−'}${this.formatMoney(pPortion, this.currencySymbolForCode(w?.currency))}`,
        rawAmount: pPortion,
        isPositiveFlow: isInflow,
        badgeLabel,
        note: lp.note || '',
        reference: '',
        tags: isFundLoanBorrowAct ? 'FUND_LOAN_BORROW' : isFundLoanAct ? 'FUND_LOAN' : '',
        attachmentUri: parentLoan.attachmentUri || '',
        iconName: 'HANDSHAKE',
        colorHex: '#B45309',
        rawEntity: lp
      });
    }

    // Fallback for loans created without an explicit DISBURSEMENT activity row
    for (const loan of loans) {
      if (loansWithDisbursementActivity.has(Number(loan.id))) continue;
      const w = walletMap.get(Number(loan.initialWalletId));
      const walletName = w ? w.name : 'No wallet';
      const vfMeta = parseWalletVirtualFund(w);
      const isLent = loan.direction === 'LENT';
      const loanNoteUpper = String(loan.note || '').toUpperCase();
      const isFundLoanBorrow = fundFeatureEnabled && loanNoteUpper.includes('[FUND_LOAN_BORROW]');
      const isFundLoan = fundFeatureEnabled && !isFundLoanBorrow && loanNoteUpper.includes('[FUND_LOAN]');
      const cleanLoanNote = String(loan.note || '')
        .replace(/\[FUND_LOAN_BORROW\]/gi, '')
        .replace(/\[FUND_LOAN\]/gi, '')
        .trim();
      const typeLabel = isLent ? `Lent to ${loan.personName}` : `Borrowed from ${loan.personName}`;
      const subWalletName = walletName === 'No wallet'
        ? walletName
        : (isFundLoan || isFundLoanBorrow)
          ? `${walletName}-Fund`
          : (fundFeatureEnabled && vfMeta.hasVirtualFund ? `${walletName}-Personal` : walletName);
      const pAmt = this.roundCurrency(loan.principalAmount);
      const signedAmt = isLent ? -pAmt : pAmt;
      const badgeLabel = isFundLoanBorrow
        ? 'Personal Borrow'
        : isFundLoan
          ? (isLent ? 'Fund Loan (Receivable)' : 'Fund Loan (Payable)')
          : (isLent ? 'Loan (Receivable)' : 'Loan (Payable)');

      entries.push({
        uniqueKey: `LOAN_${loan.id}`,
        entityId: Number(loan.id),
        kind: isLent ? 'LOAN_LENT' : 'LOAN_BORROWED',
        date: Number(loan.startDate) || Date.now(),
        title: cleanLoanNote || typeLabel,
        subtitle: `${typeLabel} · ${subWalletName}`,
        routeText: subWalletName,
        walletId: Number(loan.initialWalletId) || 0,
        walletName,
        secondaryWalletId: null,
        categoryId: null,
        categoryName: 'Loan Principal',
        signedAmount: signedAmt,
        signedAmountText: `${isLent ? '−' : '+'}${this.formatMoney(pAmt, this.currencySymbolForCode(w?.currency))}`,
        rawAmount: pAmt,
        isPositiveFlow: !isLent,
        badgeLabel,
        note: loan.note || '',
        reference: '',
        tags: isFundLoanBorrow ? 'FUND_LOAN_BORROW' : isFundLoan ? 'FUND_LOAN' : '',
        attachmentUri: loan.attachmentUri || '',
        iconName: 'HANDSHAKE',
        colorHex: '#B45309',
        rawEntity: loan
      });
    }

    for (const adj of adjustments) {
      if (adj.isReversed) continue;
      const w = walletMap.get(Number(adj.walletId));
      const walletName = w ? w.name : 'Wallet';
      const delta = adj.difference !== undefined ? Number(adj.difference) : (Number(adj.deltaAmount) || 0);
      const adjNote = String(adj.note || '').trim();
      const adjReason = String(adj.reason || 'Reconciliation').trim();
      entries.push({
        uniqueKey: `ADJ_${adj.id}`,
        entityId: Number(adj.id),
        kind: 'ADJUSTMENT',
        date: Number(adj.date) || Date.now(),
        title: adjNote || `Balance Adjustment (${adjReason})`,
        subtitle: `${adjReason} · ${walletName}`,
        routeText: walletName,
        walletId: Number(adj.walletId),
        walletName,
        secondaryWalletId: null,
        categoryId: null,
        categoryName: 'Reconciliation',
        signedAmount: this.roundCurrency(delta),
        signedAmountText: `${delta >= 0 ? '+' : '−'}${this.formatMoney(Math.abs(delta), this.currencySymbolForCode(w?.currency))}`,
        rawAmount: this.roundCurrency(Math.abs(delta)),
        isPositiveFlow: delta >= 0,
        badgeLabel: 'Adjustment',
        note: adj.note || adjReason,
        reference: '',
        tags: '',
        attachmentUri: '',
        iconName: 'TUNE',
        colorHex: '#6D28D9',
        rawEntity: adj
      });
    }

    return entries.sort((a, b) => (b.date - a.date) || ((b.entityId || 0) - (a.entityId || 0)));
  },

  calculateMonthlyReport(year, month, categories, transactions, transfers, loans, loanPayments, splits = [], wallets = [], subWalletFilter = 'PERSONAL') {
    if (typeof wallets === 'string') {
      subWalletFilter = wallets;
      wallets = [];
    }
    const startMs = new Date(year, month, 1, 0, 0, 0, 0).getTime();
    const endMs = new Date(year, month + 1, 0, 23, 59, 59, 999).getTime();

    const fundWalletIds = new Set((Array.isArray(wallets) ? wallets : []).filter(w => isDedicatedFundWallet(w)).map(w => Number(w.id)));
    const normFilter = String(subWalletFilter || 'PERSONAL').trim().toUpperCase();

    const filteredTransactions = transactions.filter(tx => {
      const isDedicatedFund = fundWalletIds.has(Number(tx.walletId));
      const tagsUpper = String(tx.tags || '').toUpperCase();
      const isFundIn = tagsUpper.includes('FUND_IN') || (isDedicatedFund && tx.type === 'INCOME' && !tagsUpper.includes('PERSONAL'));
      const isFundExpense = tagsUpper.includes('FUND_EXPENSE') || tagsUpper.includes('FUND_BORROW') ||
        (isDedicatedFund && (tx.type === 'EXPENSE' || tx.type === 'EXPENSE_REVERSAL') && !tagsUpper.includes('PERSONAL'));
      if (normFilter === 'FUND') return isFundIn || isFundExpense;
      if (normFilter === 'PERSONAL') return !isFundIn && !isFundExpense;
      return true;
    });

    const monthTxs = filteredTransactions.filter(t => t.date >= startMs && t.date <= endMs);
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

    // Only add transfer fees not already recorded as separate EXPENSE rows
    const recordedFeeTransferIds = new Set(monthTxs.filter(tx => tx.transferId).map(tx => Number(tx.transferId)));
    let transferFees = 0;
    for (const tr of monthTransfers) {
      if (!recordedFeeTransferIds.has(Number(tr.id))) {
        transferFees += Number(tr.feeAmount) || 0;
      }
    }

    const recordedInterestActIds = new Set(monthTxs.filter(tx => tx.loanActivityId).map(tx => Number(tx.loanActivityId)));
    let loanInterestEarned = 0;
    let loanInterestPaid = 0;
    for (const lp of monthLoanPayments) {
      if (lp.activityType && lp.activityType !== 'REPAYMENT') continue;
      if (recordedInterestActIds.has(Number(lp.id))) continue;
      const interest = Number(lp.interestPortion) || 0;
      if (interest <= 0) continue;
      const parent = loanMap.get(Number(lp.loanId));
      if (!parent) continue;
      const isOfficialFundLoan = String(parent.note || '').toUpperCase().includes('[FUND_LOAN]');
      if (normFilter === 'PERSONAL' && isOfficialFundLoan) continue;
      if (normFilter === 'FUND' && !isOfficialFundLoan) continue;
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
      } else if (tx.type === 'EXPENSE_REVERSAL') {
        const cid = Number(tx.categoryId) || 0;
        const cur = expByCat.get(cid) || { amount: 0, count: 0 };
        cur.amount -= Number(tx.amount) || 0;
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
      .filter(([, data]) => data.amount > 0.005)
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
      .filter(([, data]) => data.amount > 0.005)
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

  buildWalletRunningBalanceLedger(wallet, unifiedLedger, subWalletMode = 0) {
    const mode = subWalletMode === 'PERSONAL' ? 1 : subWalletMode === 'FUND' ? 2 : (Number(subWalletMode) || 0);
    const wId = Number(wallet.id);
    const vfMeta = parseWalletVirtualFund(wallet);
    const fundResetAtMillis = Number(vfMeta.fundResetAtMillis) || 0;
    const openingFund = vfMeta.isFundWallet
      ? Math.max(0, Number(wallet.openingBalance) || 0)
      : (vfMeta.hasVirtualFund ? (Number(vfMeta.openingFundBalance) || 0) : 0);

    // Filter entries belonging to this wallet & sub-wallet mode
    const relevant = unifiedLedger
      .filter(entry => {
        if (Number(entry.walletId) !== wId && Number(entry.secondaryWalletId) !== wId) return false;
        const tagsUpper = String(entry.tags || '').toUpperCase();
        const noteUpper = String(entry.note || '').toUpperCase();
        const isFundTx = tagsUpper.includes('FUND_IN') || tagsUpper.includes('FUND_EXPENSE') || tagsUpper.includes('FUND_BORROW');
        const isFundLoan = noteUpper.includes('[FUND_LOAN]') || noteUpper.includes('[FUND_LOAN_BORROW]') || tagsUpper.includes('FUND_LOAN');
        if (mode === 1) {
          // Personal sub-wallet
          if (entry.kind === 'TRANSFER') {
            if (noteUpper.includes('[FUND_TRANSFER]')) return false;
            const isBorrowOrSettle = noteUpper.includes('[FUND_BORROW]') || noteUpper.includes('[FUND_SETTLE]');
            const isReturnOrTopup = noteUpper.includes('[FUND_RETURN]') || noteUpper.includes('[FUND_TOPUP]');
            if (Number(entry.walletId) === wId && Number(entry.secondaryWalletId) === wId) {
              return isBorrowOrSettle || isReturnOrTopup;
            }
            if (Number(entry.walletId) === wId) return !isBorrowOrSettle;
            if (Number(entry.secondaryWalletId) === wId) return !isReturnOrTopup;
            return true;
          }
          if (entry.kind.startsWith('LOAN')) return !isFundLoan;
          if (entry.kind === 'ADJUSTMENT') return !noteUpper.includes('-FUND') && !noteUpper.includes('[FUND_ADJ]');
          return !isFundTx;
        }
        if (mode === 2) {
          // Fund sub-wallet
          if (Number(entry.date) <= fundResetAtMillis) return false;
          if (entry.kind === 'TRANSFER') {
            const isFundTr = noteUpper.includes('[FUND_TRANSFER]');
            const isBorrowOrSettle = noteUpper.includes('[FUND_BORROW]') || noteUpper.includes('[FUND_SETTLE]');
            const isReturnOrTopup = noteUpper.includes('[FUND_RETURN]') || noteUpper.includes('[FUND_TOPUP]');
            if (Number(entry.walletId) === wId && Number(entry.secondaryWalletId) === wId) {
              return isBorrowOrSettle || isReturnOrTopup;
            }
            if (Number(entry.walletId) === wId) return isFundTr || isBorrowOrSettle;
            if (Number(entry.secondaryWalletId) === wId) return isFundTr || isReturnOrTopup;
            return false;
          }
          if (entry.kind.startsWith('LOAN')) return isFundLoan;
          if (entry.kind === 'ADJUSTMENT') return noteUpper.includes('-FUND') || noteUpper.includes('[FUND_ADJ]');
          return isFundTx;
        }
        return true;
      })
      .slice()
      .sort((a, b) => (a.date - b.date) || ((a.entityId || 0) - (b.entityId || 0)));

    let running = mode === 1
      ? this.roundCurrency((Number(wallet.openingBalance) || 0) - openingFund)
      : mode === 2
        ? this.roundCurrency(openingFund)
        : this.roundCurrency(Number(wallet.openingBalance) || 0);

    const rows = [];
    for (const entry of relevant) {
      let delta = 0;
      const noteUpper = String(entry.note || '').toUpperCase();
      const tagsUpper = String(entry.tags || '').toUpperCase();
      if (mode === 1) {
        if (entry.kind === 'TRANSFER') {
          const tr = entry.rawEntity;
          const srcAmt = Number(tr.principalAmount ?? tr.amount) || 0;
          const dstAmt = tr.destinationAmount !== null && tr.destinationAmount !== undefined && Number(tr.destinationAmount) > 0
            ? Number(tr.destinationAmount)
            : srcAmt;
          const isBorrowOrSettle = noteUpper.includes('[FUND_BORROW]') || noteUpper.includes('[FUND_SETTLE]');
          const isReturnOrTopup = noteUpper.includes('[FUND_RETURN]') || noteUpper.includes('[FUND_TOPUP]');
          if (Number(tr.sourceWalletId) === wId && Number(tr.destinationWalletId) === wId) {
            delta = isBorrowOrSettle ? dstAmt : isReturnOrTopup ? -srcAmt : 0;
          } else if (Number(tr.sourceWalletId) === wId) {
            delta = isBorrowOrSettle ? 0 : -srcAmt;
          } else if (Number(tr.destinationWalletId) === wId) {
            delta = isReturnOrTopup ? 0 : dstAmt;
          }
        } else {
          delta = Number(entry.signedAmount) || 0;
        }
      } else if (mode === 2) {
        if (entry.kind === 'TRANSFER') {
          const tr = entry.rawEntity;
          const srcAmt = Number(tr.principalAmount ?? tr.amount) || 0;
          const dstAmt = tr.destinationAmount !== null && tr.destinationAmount !== undefined && Number(tr.destinationAmount) > 0
            ? Number(tr.destinationAmount)
            : srcAmt;
          const isFundTr = noteUpper.includes('[FUND_TRANSFER]');
          const isBorrowOrSettle = noteUpper.includes('[FUND_BORROW]') || noteUpper.includes('[FUND_SETTLE]');
          const isReturnOrTopup = noteUpper.includes('[FUND_RETURN]') || noteUpper.includes('[FUND_TOPUP]');
          if (Number(tr.sourceWalletId) === wId && Number(tr.destinationWalletId) === wId) {
            delta = isBorrowOrSettle ? -srcAmt : isReturnOrTopup ? dstAmt : 0;
          } else if (Number(tr.sourceWalletId) === wId) {
            delta = (isFundTr || isBorrowOrSettle) ? -srcAmt : 0;
          } else if (Number(tr.destinationWalletId) === wId) {
            delta = (isFundTr || isReturnOrTopup) ? dstAmt : 0;
          }
        } else if (entry.kind === 'ADJUSTMENT') {
          delta = noteUpper.includes('[FUND_ADJ]') ? (Number(entry.signedAmount) || 0) : 0;
        } else if (entry.kind === 'EXPENSE' && tagsUpper.includes('FUND_BORROW')) {
          delta = -Math.abs(Number(entry.rawAmount) || 0);
        } else {
          delta = Number(entry.signedAmount) || 0;
        }
      } else {
        if (entry.kind === 'TRANSFER') {
          const tr = entry.rawEntity;
          const srcAmt = Number(tr.principalAmount ?? tr.amount) || 0;
          const dstAmt = tr.destinationAmount !== null && tr.destinationAmount !== undefined && Number(tr.destinationAmount) > 0
            ? Number(tr.destinationAmount)
            : srcAmt;
          if (Number(tr.sourceWalletId) === wId && Number(tr.destinationWalletId) === wId) {
            delta = 0;
          } else if (Number(tr.sourceWalletId) === wId) {
            delta = -srcAmt;
          } else if (Number(tr.destinationWalletId) === wId) {
            delta = dstAmt;
          }
        } else {
          delta = Number(entry.signedAmount) || 0;
        }
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
  FREE_DAILY_TRANSACTIONS_LIMIT: 10,
  FREE_DEFAULT_WALLETS_LIMIT: 3,
  FREE_CUSTOM_WALLETS_LIMIT: 0,
  FREE_LOAN_PERSONS_LIMIT: 2,
  FREE_SAVINGS_GOALS_LIMIT: 2,
  FREE_RECURRING_BILLS_LIMIT: 2,
  FREE_CUSTOM_CATEGORIES_LIMIT: 1,

  STARTER_WALLET_NAMES: new Set([
    'cash', 'bank', 'mtb', 'brac', 'bkash', 'nagad', 'rocket', 'rapid pass', 'card', 'savings',
    'ক্যাশ', 'ব্যাংক', 'এমটিবি', 'ব্র্যাক', 'বিকাশ', 'নগদ', 'রকেট', 'র‍্যাপিড পাস', 'কার্ড', 'সেভিংস'
  ]),

  FREE_ALLOWED_ACCENT_HEXES: new Set(['#F15A24', '#14B8A6']),
  FREE_ALLOWED_BOX_HEXES: new Set(['', '#141416', '#1E293B', '#101726', '#FFFFFF']),
  THREE_DAYS_MILLIS: 3 * 24 * 60 * 60 * 1000,

  resolveActiveTier(tierString, expiresAtMillis, nowMillis = Date.now()) {
    const t = String(tierString || 'FREE').toUpperCase();
    if (t === 'LIFETIME') return 'LIFETIME';
    if (t === 'THREE_DAYS' || t === 'TRIAL_3_DAYS') {
      if (expiresAtMillis && Number(expiresAtMillis) > nowMillis) return t;
      return 'FREE';
    }
    return 'FREE';
  },

  isSubscriptionActive(tierString, expiresAtMillis, nowMillis = Date.now()) {
    return this.resolveActiveTier(tierString, expiresAtMillis, nowMillis) !== 'FREE';
  },

  isStarterWallet(walletName) {
    return this.STARTER_WALLET_NAMES.has(String(walletName || '').trim().toLowerCase());
  },

  canAddWallet(existingWallets = [], newWalletName = '', tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    const isStarter = this.isStarterWallet(newWalletName);
    if (isStarter) {
      return existingWallets.length < this.FREE_DEFAULT_WALLETS_LIMIT;
    }
    // Custom wallet
    const customCount = existingWallets.filter(w => !this.isStarterWallet(w.name)).length;
    return customCount < this.FREE_CUSTOM_WALLETS_LIMIT;
  },

  canAddTransactionToday(todayCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return todayCount < this.FREE_DAILY_TRANSACTIONS_LIMIT;
  },

  canAddLoanPerson(existingDistinctPersons = [], targetPersonName = '', tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    const key = String(targetPersonName || '').trim().toLowerCase();
    const alreadyExists = existingDistinctPersons.some(p => String(p || '').trim().toLowerCase() === key);
    if (alreadyExists) return true;
    return existingDistinctPersons.length < this.FREE_LOAN_PERSONS_LIMIT;
  },

  canAddCategory(categoryType, currentCustomCategoriesCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return currentCustomCategoriesCount < this.FREE_CUSTOM_CATEGORIES_LIMIT;
  },

  canAddRecurringBill(currentRecurringCount, tierString, expiresAtMillis) {
    if (this.isSubscriptionActive(tierString, expiresAtMillis)) return true;
    return currentRecurringCount < this.FREE_RECURRING_BILLS_LIMIT;
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
  defaultWallets() {
    return JSON.parse(JSON.stringify(DEFAULT_STARTER_WALLETS));
  },

  defaultCategories() {
    return JSON.parse(JSON.stringify(DEFAULT_CATEGORIES));
  },

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
