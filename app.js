import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  onAuthStateChanged,
  signOut
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import {
  getFirestore,
  doc,
  setDoc,
  getDoc,
  deleteDoc,
  collection,
  getDocs,
  onSnapshot,
  serverTimestamp
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js';

// Exact Firebase configuration from Android app/google-services.json & firebase_applet_config.xml
const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAIy498LT2vooT327SThshw8VU5D_pOYxg",
  authDomain: "gen-lang-client-0285964029.firebaseapp.com",
  projectId: "gen-lang-client-0285964029",
  storageBucket: "gen-lang-client-0285964029.firebasestorage.app",
  messagingSenderId: "30179850125",
  appId: "1:30179850125:android:fcf248906c9e43807f6fc4"
};
const FIRESTORE_DATABASE_ID = "ai-studio-android-pocket-90c40f1c-a604-4528-b70a-bb3c8a09d67f";

let fbApp = null, auth = null, db = null, profileUnsubscribe = null;
try {
  fbApp = initializeApp(FIREBASE_CONFIG);
  auth = getAuth(fbApp);
  db = getFirestore(fbApp, FIRESTORE_DATABASE_ID);
} catch (err) {
  console.warn("Firebase initialization fallback:", err);
}

// Resolve asset paths whether served from root (/) or (/ios-pocket/)
const IN_SUBFOLDER = window.location.pathname.includes("/ios-pocket");
export function assetUrl(fileName) {
  return IN_SUBFOLDER ? `assets/${fileName}` : `ios-pocket/assets/${fileName}`;
}

const STORAGE_KEY = "pocket_web_android_sync_v3";

const STARTER_WALLETS = [
  { name: "Cash", type: "CASH", accountHint: "", iconName: "CASH", colorHex: "#2E9E5B", includeInTotalBalance: true, sortOrder: 1 },
  { name: "Bank", type: "BANK", accountHint: "", iconName: "BANK", colorHex: "#2563EB", includeInTotalBalance: true, sortOrder: 2 },
  { name: "MTB", type: "BANK", accountHint: "Mutual Trust Bank", iconName: "MTB", colorHex: "#16A34A", includeInTotalBalance: true, sortOrder: 3 },
  { name: "Brac", type: "BANK", accountHint: "BRAC Bank", iconName: "BRAC", colorHex: "#1D4ED8", includeInTotalBalance: true, sortOrder: 4 },
  { name: "Bkash", type: "MOBILE_BANKING", accountHint: "", iconName: "BKASH", colorHex: "#E2136E", includeInTotalBalance: true, sortOrder: 5 },
  { name: "Nagad", type: "MOBILE_BANKING", accountHint: "", iconName: "NAGAD", colorHex: "#F15A24", includeInTotalBalance: true, sortOrder: 6 },
  { name: "Rocket", type: "MOBILE_BANKING", accountHint: "", iconName: "ROCKET", colorHex: "#7C3AED", includeInTotalBalance: true, sortOrder: 7 },
  { name: "Rapid Pass", type: "OTHER", accountHint: "Transit Card", iconName: "RAPID_PASS", colorHex: "#0EA5E9", includeInTotalBalance: true, sortOrder: 8 },
  { name: "Card", type: "CARD", accountHint: "", iconName: "CARD", colorHex: "#6366F1", includeInTotalBalance: true, sortOrder: 9 },
  { name: "Savings", type: "SAVINGS", accountHint: "", iconName: "SAVINGS", colorHex: "#D97706", includeInTotalBalance: false, sortOrder: 10 }
];

const WALLET_ICON_FILES = {
  CASH: "wallet_icon_cash.png",
  BANK: "wallet_icon_bank.png",
  MTB: "wallet_icon_mtb.png",
  BRAC: "wallet_icon_brac.png",
  BKASH: "wallet_icon_bkash.png",
  NAGAD: "wallet_icon_nagad.png",
  ROCKET: "wallet_icon_rocket.png",
  RAPID_PASS: "wallet_icon_rapid_pass.png",
  CARD: "wallet_icon_card.png",
  SAVINGS: "wallet_icon_savings.png"
};

export function resolveWalletLogoUrl(iconName, walletName = "") {
  if (iconName && (iconName.startsWith("data:image/") || iconName.startsWith("http"))) {
    return iconName;
  }
  const key = (iconName || "").toUpperCase();
  if (WALLET_ICON_FILES[key]) return assetUrl(WALLET_ICON_FILES[key]);
  const lower = (walletName || "").trim().toLowerCase();
  if (lower.includes("bkash")) return assetUrl(WALLET_ICON_FILES.BKASH);
  if (lower.includes("nagad")) return assetUrl(WALLET_ICON_FILES.NAGAD);
  if (lower.includes("rocket")) return assetUrl(WALLET_ICON_FILES.ROCKET);
  if (lower.includes("brac")) return assetUrl(WALLET_ICON_FILES.BRAC);
  if (lower.includes("mtb")) return assetUrl(WALLET_ICON_FILES.MTB);
  if (lower.includes("rapid")) return assetUrl(WALLET_ICON_FILES.RAPID_PASS);
  if (lower.includes("card")) return assetUrl(WALLET_ICON_FILES.CARD);
  if (lower.includes("saving")) return assetUrl(WALLET_ICON_FILES.SAVINGS);
  if (lower.includes("bank")) return assetUrl(WALLET_ICON_FILES.BANK);
  return assetUrl(WALLET_ICON_FILES.CASH);
}

export function defaultCategories() {
  return [
    // Income (5)
    { id: 1, name: "Salary", type: "INCOME", iconName: "WORK", colorHex: "#15803D", sortOrder: 1, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 2, name: "Freelance", type: "INCOME", iconName: "LAPTOP", colorHex: "#15803D", sortOrder: 2, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 3, name: "Gift", type: "INCOME", iconName: "GIFT", colorHex: "#15803D", sortOrder: 3, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 4, name: "Client payment", type: "INCOME", iconName: "PAYMENTS", colorHex: "#15803D", sortOrder: 4, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 5, name: "Others", type: "INCOME", iconName: "CATEGORY", colorHex: "#15803D", sortOrder: 5, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    // Expense (14)
    { id: 6, name: "Bills", type: "EXPENSE", iconName: "RECEIPT", colorHex: "#F15A24", sortOrder: 1, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 7, name: "Shopping", type: "EXPENSE", iconName: "SHOPPING_BAG", colorHex: "#F15A24", sortOrder: 2, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 8, name: "Transport", type: "EXPENSE", iconName: "DIRECTIONS_BUS", colorHex: "#F15A24", sortOrder: 3, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 9, name: "Food & Dining", type: "EXPENSE", iconName: "RESTAURANT", colorHex: "#F15A24", sortOrder: 4, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 10, name: "Bazar", type: "EXPENSE", iconName: "SHOPPING_CART", colorHex: "#F15A24", sortOrder: 5, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 11, name: "Medicine", type: "EXPENSE", iconName: "MEDICATION", colorHex: "#F15A24", sortOrder: 6, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 12, name: "Entertainment", type: "EXPENSE", iconName: "MOVIE", colorHex: "#F15A24", sortOrder: 7, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 13, name: "Donation", type: "EXPENSE", iconName: "VOLUNTEER", colorHex: "#F15A24", sortOrder: 8, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 14, name: "Service Charge", type: "EXPENSE", iconName: "BUILD", colorHex: "#F15A24", sortOrder: 9, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 15, name: "Health", type: "EXPENSE", iconName: "HEALTH", colorHex: "#F15A24", sortOrder: 10, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 16, name: "Education", type: "EXPENSE", iconName: "SCHOOL", colorHex: "#F15A24", sortOrder: 11, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 17, name: "Mobile Recharge", type: "EXPENSE", iconName: "PHONE_ANDROID", colorHex: "#F15A24", sortOrder: 12, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 18, name: "Others", type: "EXPENSE", iconName: "MORE_HORIZ", colorHex: "#F15A24", sortOrder: 13, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
    { id: 19, name: "Fees & Charges", type: "EXPENSE", iconName: "PERCENT", colorHex: "#F15A24", sortOrder: 14, isArchived: false, isSystemFee: true, monthlyBudget: 0 }
  ];
}

function createDefaultSettings() {
  return {
    id: 1,
    hasCompletedOnboarding: false,
    userName: "",
    profilePhotoUri: "",
    currencyCode: "BDT",
    currencySymbol: "৳",
    defaultWalletId: null,
    warnOnNegativeBalance: true,
    themeMode: "DARK",
    accentColorHex: "#F15A24",
    customBackgroundUri: "",
    backgroundOpacity: 0.85,
    masterMonthlyBudget: 0,
    hideBalancesPrivacy: false,
    appLockEnabled: false,
    appLockPin: "",
    loanDeadlineNotificationEnabled: true,
    dailyExpenseReminderEnabled: true,
    dailyReminderHour: 20,
    dailyReminderMinute: 0,
    syncVersion: 0
  };
}

function createInitialState() {
  const now = new Date();
  return {
    onboardingStage: "PAGE_1_WELCOME", // PAGE_1_WELCOME | PAGE_2_SETUP
    settings: createDefaultSettings(),
    wallets: [],
    categories: defaultCategories(),
    transactions: [],
    transfers: [],
    loans: [],
    loanActivities: [],
    adjustments: [],
    recurringTransactions: [],
    savingsGoals: [],
    cloudAuth: {
      uid: null,
      email: "",
      displayName: "",
      photoUrl: "",
      isSyncing: false,
      lastSyncStatus: "Offline mode (not signed in)",
      pendingConflict: null
    },
    ui: {
      activeBottomTab: "BALANCES", // BALANCES | RECENT_TRANSACTIONS | REPORTS | LOANS
      currentScreen: "WALLETS",    // WALLETS | WALLET_DETAIL | HISTORY | REPORTS | LOANS | SETTINGS | RECURRING | CATEGORIES | ADJUSTMENT | BACKUP
      selectedWalletId: null,
      selectedYear: now.getFullYear(),
      selectedMonth: now.getMonth(),
      selectedReportCurrency: "BDT",
      isFabExpanded: false,
      isAppUnlocked: true,
      lastBackgroundMillis: 0,
      toastMessage: null,
      historyFilterType: "ALL",
      historyFilterWalletId: null,
      historySearch: "",
      loanViewMode: "ALL", // ALL | PERSON
      loanFilter: "ACTIVE", // ACTIVE | SETTLED | ALL
      modal: null, // Active modal descriptor
      page2State: {
        showMoreWallets: false,
        selectedMap: {},
        balanceMap: {},
        customWallets: [],
        starterLoans: [],
        loanPerson: "",
        loanAmount: "",
        loanDirection: "LENT",
        loanDueDate: ""
      }
    }
  };
}

let state = loadStateFromStorage();

function loadStateFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createInitialState();
    const parsed = JSON.parse(raw);
    const base = createInitialState();
    const mergedSettings = { ...base.settings, ...(parsed.settings || {}) };
    return {
      ...base,
      onboardingStage: parsed.onboardingStage || "PAGE_1_WELCOME",
      settings: mergedSettings,
      wallets: Array.isArray(parsed.wallets) ? parsed.wallets : [],
      categories: Array.isArray(parsed.categories) && parsed.categories.length > 0 ? parsed.categories : defaultCategories(),
      transactions: Array.isArray(parsed.transactions) ? parsed.transactions : [],
      transfers: Array.isArray(parsed.transfers) ? parsed.transfers : [],
      loans: Array.isArray(parsed.loans) ? parsed.loans : [],
      loanActivities: Array.isArray(parsed.loanActivities) ? parsed.loanActivities : [],
      adjustments: Array.isArray(parsed.adjustments) ? parsed.adjustments : [],
      recurringTransactions: Array.isArray(parsed.recurringTransactions) ? parsed.recurringTransactions : [],
      savingsGoals: Array.isArray(parsed.savingsGoals) ? parsed.savingsGoals : [],
      cloudAuth: { ...base.cloudAuth, ...(parsed.cloudAuth || {}), isSyncing: false, pendingConflict: null },
      ui: {
        ...base.ui,
        isAppUnlocked: !(mergedSettings.appLockEnabled && mergedSettings.appLockPin)
      }
    };
  } catch (err) {
    return createInitialState();
  }
}

function saveStateToStorage() {
  try {
    const serializable = {
      onboardingStage: state.onboardingStage,
      settings: state.settings,
      wallets: state.wallets,
      categories: state.categories,
      transactions: state.transactions,
      transfers: state.transfers,
      loans: state.loans,
      loanActivities: state.loanActivities,
      adjustments: state.adjustments,
      recurringTransactions: state.recurringTransactions,
      savingsGoals: state.savingsGoals,
      cloudAuth: {
        uid: state.cloudAuth.uid,
        email: state.cloudAuth.email,
        displayName: state.cloudAuth.displayName,
        photoUrl: state.cloudAuth.photoUrl,
        lastSyncStatus: state.cloudAuth.lastSyncStatus
      }
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(serializable));
  } catch (err) {
    console.warn("Storage quota warning:", err);
  }
}

let toastTimeout = null;
function showToast(msg) {
  state.ui.toastMessage = msg;
  render();
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    state.ui.toastMessage = null;
    render();
  }, 3600);
}

// 30-second App Lock PIN grace period when switching browser tabs
document.addEventListener("visibilitychange", () => {
  if (!state.settings.appLockEnabled || !state.settings.appLockPin) return;
  if (document.visibilityState === "hidden") {
    state.ui.lastBackgroundMillis = Date.now();
  } else if (document.visibilityState === "visible") {
    const elapsed = Date.now() - (state.ui.lastBackgroundMillis || 0);
    if (elapsed > 30000) {
      state.ui.isAppUnlocked = false;
      render();
    }
  }
});

// ============================================================================
// Accounting Engine (1:1 Match with Android AccountingEngine.kt)
// ============================================================================
export function roundCurrency(val) {
  const num = Number(val || 0);
  if (!Number.isFinite(num)) return 0;
  return Math.round(num * 100) / 100;
}

export function currencySymbolForCode(code) {
  const c = (code || "BDT").trim().toUpperCase();
  const map = { BDT: "৳", USD: "$", EUR: "€", GBP: "£", INR: "₹", SAR: "﷼", AED: "د.إ", CAD: "C$", AUD: "A$" };
  return map[c] || c || "৳";
}

export function formatMoney(amount, symbol = "৳", includeDecimals = true, showSign = false) {
  const rounded = roundCurrency(amount);
  const absVal = Math.abs(rounded);
  const hasCents = Math.abs(absVal - Math.round(absVal)) > 0.004;
  const formatted = absVal.toLocaleString("en-IN", {
    minimumFractionDigits: includeDecimals && hasCents ? 2 : 0,
    maximumFractionDigits: includeDecimals ? 2 : 0
  });
  const sign = rounded < -0.004 ? "−" : (showSign && rounded > 0.004 ? "+" : "");
  return `${sign}${symbol} ${formatted}`;
}

export function calculateWalletSummary(wallet) {
  const wId = Number(wallet.id);
  let totalIncome = 0;
  let totalExpenses = 0;
  let totalReversals = 0;
  for (const tx of state.transactions) {
    if (Number(tx.walletId) !== wId) continue;
    const amt = Number(tx.amount || 0);
    if (tx.type === "INCOME") totalIncome += amt;
    else if (tx.type === "EXPENSE") totalExpenses += amt;
    else if (tx.type === "EXPENSE_REVERSAL") totalReversals += amt;
  }

  let transfersIn = 0;
  let transfersOut = 0;
  for (const tr of state.transfers) {
    if (Number(tr.sourceWalletId) === wId) {
      transfersOut += Number(tr.principalAmount || 0);
    }
    if (Number(tr.destinationWalletId) === wId) {
      const destAmt = Number(tr.destinationAmount || 0) > 0 ? Number(tr.destinationAmount) : Number(tr.principalAmount || 0);
      transfersIn += destAmt;
    }
  }

  const loanMap = new Map(state.loans.map((l) => [Number(l.id), l]));
  let loanInflows = 0;
  let loanOutflows = 0;
  for (const act of state.loanActivities) {
    if (Number(act.walletId) !== wId) continue;
    const loan = loanMap.get(Number(act.loanId));
    if (!loan) continue;
    const totalAmt = Number(act.principalPortion || 0) + Number(act.interestPortion || 0);
    if (loan.direction === "LENT") {
      if (act.activityType === "DISBURSEMENT") loanOutflows += totalAmt;
      else if (act.activityType === "REPAYMENT") loanInflows += totalAmt;
    } else {
      if (act.activityType === "DISBURSEMENT") loanInflows += totalAmt;
      else if (act.activityType === "REPAYMENT") loanOutflows += totalAmt;
    }
  }

  let adjustmentsNet = 0;
  for (const adj of state.adjustments) {
    if (Number(adj.walletId) === wId && !adj.isReversed) {
      adjustmentsNet += Number(adj.difference || 0);
    }
  }

  const currentBalance = roundCurrency(
    Number(wallet.openingBalance || 0) +
    totalIncome +
    totalReversals -
    totalExpenses +
    transfersIn -
    transfersOut +
    loanInflows -
    loanOutflows +
    adjustmentsNet
  );

  return {
    wallet,
    currentBalance,
    totalIncome: roundCurrency(totalIncome),
    totalExpenses: roundCurrency(totalExpenses - totalReversals),
    transfersIn: roundCurrency(transfersIn),
    transfersOut: roundCurrency(transfersOut),
    loanInflows: roundCurrency(loanInflows),
    loanOutflows: roundCurrency(loanOutflows),
    adjustmentsNet: roundCurrency(adjustmentsNet)
  };
}

export function calculateLoanSummaries() {
  const walletMap = new Map(state.wallets.map((w) => [Number(w.id), w]));
  return state.loans
    .slice()
    .sort((a, b) => Number(b.startDate || 0) - Number(a.startDate || 0))
    .map((loan) => {
      const acts = state.loanActivities
        .filter((a) => Number(a.loanId) === Number(loan.id))
        .sort((a, b) => Number(b.date || 0) - Number(a.date || 0));
      let totalDisbursed = 0;
      let principalRepaid = 0;
      let interestPaid = 0;
      let adjustmentsDelta = 0;
      for (const act of acts) {
        if (act.activityType === "DISBURSEMENT") totalDisbursed += Number(act.principalPortion || 0);
        else if (act.activityType === "REPAYMENT") {
          principalRepaid += Number(act.principalPortion || 0);
          interestPaid += Number(act.interestPortion || 0);
        } else if (act.activityType === "ADJUSTMENT") {
          adjustmentsDelta += Number(act.principalPortion || 0);
        }
      }
      const basePrincipal = totalDisbursed > 0 ? totalDisbursed : Number(loan.principalAmount || 0);
      const effectivePrincipal = roundCurrency(basePrincipal + adjustmentsDelta);
      const outstandingPrincipal = roundCurrency(Math.max(0, effectivePrincipal - principalRepaid));
      const status = outstandingPrincipal <= 0.005 ? "SETTLED" : (principalRepaid > 0 ? "PARTIALLY_PAID" : "ACTIVE");
      const w = walletMap.get(Number(loan.initialWalletId));
      return {
        loan,
        walletName: w ? w.name : "Cash",
        effectivePrincipal,
        principalRepaid: roundCurrency(principalRepaid),
        interestPaid: roundCurrency(interestPaid),
        outstandingPrincipal,
        status,
        activities: acts
      };
    });
}

export function buildUnifiedLedger() {
  const walletMap = new Map(state.wallets.map((w) => [Number(w.id), w]));
  const catMap = new Map(state.categories.map((c) => [Number(c.id), c]));
  const loanMap = new Map(state.loans.map((l) => [Number(l.id), l]));
  const entries = [];

  for (const tx of state.transactions) {
    const w = walletMap.get(Number(tx.walletId));
    const c = catMap.get(Number(tx.categoryId));
    const isIncome = tx.type === "INCOME" || tx.type === "EXPENSE_REVERSAL";
    entries.push({
      uid: `TX_${tx.id}`,
      rawId: Number(tx.id),
      kind: tx.type,
      title: tx.note ? tx.note : (c ? c.name : tx.type),
      categoryId: Number(tx.categoryId),
      categoryName: c ? c.name : "General",
      walletId: Number(tx.walletId),
      walletName: w ? w.name : "Wallet",
      secondaryWalletName: null,
      currencyCode: w ? (w.currency || "BDT") : "BDT",
      amount: Number(tx.amount || 0),
      signedAmount: isIncome ? Number(tx.amount || 0) : -Number(tx.amount || 0),
      feeAmount: 0,
      date: Number(tx.date || 0),
      note: tx.note || "",
      reference: tx.reference || "",
      tags: tx.tags || "",
      attachmentUri: tx.attachmentUri || "",
      transferId: tx.transferId || null
    });
  }

  for (const tr of state.transfers) {
    const sw = walletMap.get(Number(tr.sourceWalletId));
    const dw = walletMap.get(Number(tr.destinationWalletId));
    entries.push({
      uid: `TR_${tr.id}`,
      rawId: Number(tr.id),
      kind: "TRANSFER",
      title: tr.note ? tr.note : `Transfer: ${sw ? sw.name : "Wallet"} → ${dw ? dw.name : "Wallet"}`,
      categoryId: null,
      categoryName: "Wallet Transfer",
      walletId: Number(tr.sourceWalletId),
      secondaryWalletId: Number(tr.destinationWalletId),
      walletName: sw ? sw.name : "Source",
      secondaryWalletName: dw ? dw.name : "Destination",
      currencyCode: sw ? (sw.currency || "BDT") : "BDT",
      amount: Number(tr.principalAmount || 0),
      signedAmount: -Number(tr.principalAmount || 0),
      feeAmount: Number(tr.feeAmount || 0),
      date: Number(tr.date || 0),
      note: tr.note || "",
      reference: "",
      tags: ""
    });
  }

  for (const act of state.loanActivities) {
    const loan = loanMap.get(Number(act.loanId));
    if (!loan) continue;
    const w = walletMap.get(Number(act.walletId));
    const amt = Number(act.principalPortion || 0) + Number(act.interestPortion || 0);
    const isInflow =
      (loan.direction === "LENT" && act.activityType === "REPAYMENT") ||
      (loan.direction === "BORROWED" && act.activityType === "DISBURSEMENT");
    const actionLabel =
      act.activityType === "DISBURSEMENT"
        ? (loan.direction === "LENT" ? `Lent to ${loan.personName}` : `Borrowed from ${loan.personName}`)
        : (loan.direction === "LENT" ? `Received from ${loan.personName}` : `Paid to ${loan.personName}`);
    entries.push({
      uid: `LA_${act.id}`,
      rawId: Number(act.id),
      loanId: Number(loan.id),
      kind: "LOAN",
      title: act.note ? `${actionLabel} • ${act.note}` : actionLabel,
      categoryId: null,
      categoryName: loan.direction === "LENT" ? "Loan Lent" : "Loan Borrowed",
      walletId: Number(act.walletId),
      walletName: w ? w.name : "Wallet",
      secondaryWalletName: null,
      currencyCode: w ? (w.currency || "BDT") : "BDT",
      amount: amt,
      signedAmount: isInflow ? amt : -amt,
      feeAmount: 0,
      date: Number(act.date || 0),
      note: act.note || "",
      reference: "",
      tags: ""
    });
  }

  for (const adj of state.adjustments) {
    if (adj.isReversed) continue;
    const w = walletMap.get(Number(adj.walletId));
    const diff = Number(adj.difference || 0);
    entries.push({
      uid: `ADJ_${adj.id}`,
      rawId: Number(adj.id),
      kind: "ADJUSTMENT",
      title: adj.reason || "Balance Reconciliation",
      categoryId: null,
      categoryName: "Reconciliation",
      walletId: Number(adj.walletId),
      walletName: w ? w.name : "Wallet",
      secondaryWalletName: null,
      currencyCode: w ? (w.currency || "BDT") : "BDT",
      amount: Math.abs(diff),
      signedAmount: diff,
      feeAmount: 0,
      date: Number(adj.date || 0),
      note: adj.note || "",
      reference: "",
      tags: ""
    });
  }

  return entries.sort((a, b) => b.date - a.date);
}

export function calculateMonthlyReport(year, month, currencyCode = "BDT") {
  const start = new Date(year, month, 1, 0, 0, 0, 0).getTime();
  const end = new Date(year, month + 1, 0, 23, 59, 59, 999).getTime();
  const targetCurr = (currencyCode || "BDT").toUpperCase();
  const walletMap = new Map(state.wallets.map((w) => [Number(w.id), w]));
  const catMap = new Map(state.categories.map((c) => [Number(c.id), c]));

  let totalIncome = 0;
  let totalExpenses = 0;
  const catTotals = new Map();

  for (const tx of state.transactions) {
    const d = Number(tx.date || 0);
    if (d < start || d > end) continue;
    const w = walletMap.get(Number(tx.walletId));
    const wCurr = (w?.currency || "BDT").toUpperCase();
    if (wCurr !== targetCurr) continue;

    const amt = Number(tx.amount || 0);
    if (tx.type === "INCOME") {
      totalIncome += amt;
    } else if (tx.type === "EXPENSE") {
      totalExpenses += amt;
      const cId = Number(tx.categoryId);
      catTotals.set(cId, (catTotals.get(cId) || 0) + amt);
    } else if (tx.type === "EXPENSE_REVERSAL") {
      totalExpenses -= amt;
      const cId = Number(tx.categoryId);
      catTotals.set(cId, (catTotals.get(cId) || 0) - amt);
    }
  }

  totalIncome = roundCurrency(Math.max(0, totalIncome));
  totalExpenses = roundCurrency(Math.max(0, totalExpenses));

  const categoryBreakdown = [];
  for (const [cId, rawTotal] of catTotals.entries()) {
    if (rawTotal <= 0.005) continue;
    const cat = catMap.get(cId);
    const total = roundCurrency(rawTotal);
    const pct = totalExpenses > 0 ? roundCurrency((total / totalExpenses) * 100) : 0;
    categoryBreakdown.push({
      categoryId: cId,
      categoryName: cat ? cat.name : "Other",
      colorHex: cat ? cat.colorHex : "#F15A24",
      monthlyBudget: Number(cat?.monthlyBudget || 0),
      amount: total,
      percentage: pct
    });
  }
  categoryBreakdown.sort((a, b) => b.amount - a.amount);

  return {
    year,
    month,
    currencyCode: targetCurr,
    totalIncome,
    totalExpenses,
    netSavings: roundCurrency(totalIncome - totalExpenses),
    categoryBreakdown
  };
}

// ============================================================================
// Firestore Cloud Sync (Strict Compatibility with Android & firestore.rules)
// ============================================================================
function toSafeInt(val, fallback = 1) {
  const n = Math.trunc(Number(val));
  return Number.isFinite(n) && n >= 1 ? n : fallback;
}

function toSafeTimestampMillis(val) {
  const n = Math.trunc(Number(val));
  return Number.isFinite(n) && n >= 0 ? n : Date.now();
}

function buildFirestoreDocMaps(uid) {
  const s = state.settings;
  const syncVersion = Math.max(Date.now(), toSafeInt(s.syncVersion, 1) + 1);
  s.syncVersion = syncVersion;

  const resolvedPhoto =
    s.profilePhotoUri &&
    (s.profilePhotoUri.startsWith("http://") ||
      s.profilePhotoUri.startsWith("https://") ||
      s.profilePhotoUri.startsWith("data:image/"))
      ? s.profilePhotoUri.slice(0, 250000)
      : (state.cloudAuth.photoUrl || "").slice(0, 250000);

  const profileMap = {
    userId: uid,
    displayName: (s.userName || state.cloudAuth.displayName || "Pocket User").trim().slice(0, 100) || "Pocket User",
    email: (state.cloudAuth.email || "").trim().slice(0, 200),
    photoUrl: resolvedPhoto,
    currencyCode: "BDT",
    currencySymbol: "৳",
    themeMode: ["DARK", "LIGHT", "SYSTEM"].includes(s.themeMode) ? s.themeMode : "DARK",
    defaultWalletId: s.defaultWalletId ? toSafeInt(s.defaultWalletId) : null,
    warnOnNegativeBalance: Boolean(s.warnOnNegativeBalance),
    loanDeadlineNotificationEnabled: Boolean(s.loanDeadlineNotificationEnabled),
    dailyExpenseReminderEnabled: Boolean(s.dailyExpenseReminderEnabled),
    dailyReminderHour: Math.min(23, Math.max(0, Math.trunc(Number(s.dailyReminderHour ?? 20)))),
    dailyReminderMinute: Math.min(59, Math.max(0, Math.trunc(Number(s.dailyReminderMinute ?? 0)))),
    backgroundOpacity: Math.min(1, Math.max(0, Number(s.backgroundOpacity ?? 0.85))),
    accentColorHex: (s.accentColorHex || "#F15A24").slice(0, 20),
    masterMonthlyBudget: Math.max(0, Number(s.masterMonthlyBudget || 0)),
    hideBalancesPrivacy: Boolean(s.hideBalancesPrivacy),
    hasCompletedOnboarding: Boolean(s.hasCompletedOnboarding),
    syncVersion
  };

  return { profileMap };
}

async function syncSubcollectionToFirestore(uid, colName, items, mapBuilder) {
  const colRef = collection(db, "users", uid, colName);
  const existingSnap = await getDocs(colRef);
  const existingMap = new Map();
  existingSnap.docs.forEach((d) => existingMap.set(d.id, d.data()));

  const incomingIds = new Set(items.map((item) => String(toSafeInt(item.id))));
  for (const [docId] of existingMap.entries()) {
    if (!incomingIds.has(docId)) {
      await deleteDoc(doc(db, "users", uid, colName, docId));
    }
  }

  for (const item of items) {
    const entityId = toSafeInt(item.id);
    const docId = String(entityId);
    const prev = existingMap.get(docId);
    const createdAt = prev && prev.createdAt ? prev.createdAt : serverTimestamp();
    const docData = mapBuilder(item, entityId, createdAt);
    await setDoc(doc(db, "users", uid, colName, docId), docData);
  }
}

export async function pushLocalPayloadToCloud() {
  const uid = state.cloudAuth.uid;
  if (!db || !uid) return;
  state.cloudAuth.isSyncing = true;
  state.cloudAuth.lastSyncStatus = "Syncing with Google Cloud...";
  render();

  try {
    const { profileMap } = buildFirestoreDocMaps(uid);
    const userRef = doc(db, "users", uid);
    const existingProfile = await getDoc(userRef);
    const profileCreatedAt =
      existingProfile.exists() && existingProfile.data().createdAt
        ? existingProfile.data().createdAt
        : serverTimestamp();

    await setDoc(userRef, {
      ...profileMap,
      createdAt: profileCreatedAt,
      updatedAt: serverTimestamp()
    });

    await syncSubcollectionToFirestore(uid, "wallets", state.wallets, (w, entityId, createdAt) => ({
      userId: uid,
      entityId,
      name: (w.name || "Wallet").trim().slice(0, 100) || "Wallet",
      type: (w.type || "CASH").slice(0, 40),
      accountHint: (w.accountHint || "").slice(0, 200),
      openingBalance: Number(w.openingBalance || 0),
      openingDate: toSafeTimestampMillis(w.openingDate || 0),
      currency: (w.currency || "BDT").slice(0, 10),
      iconName: (w.iconName || "CASH").slice(0, 250000),
      colorHex: (w.colorHex || "#2E9E5B").slice(0, 20),
      sortOrder: Math.trunc(Number(w.sortOrder || 0)),
      isArchived: Boolean(w.isArchived),
      includeInTotalBalance: w.includeInTotalBalance !== false,
      creditLimit: Math.max(0, Number(w.creditLimit || 0)),
      statementDay: Math.min(31, Math.max(0, Math.trunc(Number(w.statementDay || 0)))),
      paymentDueDay: Math.min(31, Math.max(0, Math.trunc(Number(w.paymentDueDay || 0)))),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "categories", state.categories, (c, entityId, createdAt) => ({
      userId: uid,
      entityId,
      name: (c.name || "Category").trim().slice(0, 100) || "Category",
      type: c.type === "INCOME" ? "INCOME" : "EXPENSE",
      iconName: (c.iconName || "CATEGORY").slice(0, 100),
      colorHex: (c.colorHex || "#F15A24").slice(0, 20),
      sortOrder: Math.trunc(Number(c.sortOrder || 0)),
      isArchived: Boolean(c.isArchived),
      isSystemFee: Boolean(c.isSystemFee),
      monthlyBudget: Math.max(0, Number(c.monthlyBudget || 0)),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "transactions", state.transactions, (t, entityId, createdAt) => ({
      userId: uid,
      entityId,
      type: ["INCOME", "EXPENSE", "EXPENSE_REVERSAL"].includes(t.type) ? t.type : "EXPENSE",
      amount: Math.max(0, Number(t.amount || 0)),
      date: toSafeTimestampMillis(t.date),
      walletId: toSafeInt(t.walletId),
      categoryId: toSafeInt(t.categoryId),
      note: (t.note || "").slice(0, 1000),
      reference: (t.reference || "").slice(0, 200),
      tags: (t.tags || "").slice(0, 500),
      transferId: t.transferId ? toSafeInt(t.transferId) : null,
      loanActivityId: t.loanActivityId ? toSafeInt(t.loanActivityId) : null,
      parentTransactionId: t.parentTransactionId ? toSafeInt(t.parentTransactionId) : null,
      attachmentUri: (t.attachmentUri || "").slice(0, 250000),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "transfers", state.transfers, (tr, entityId, createdAt) => ({
      userId: uid,
      entityId,
      sourceWalletId: toSafeInt(tr.sourceWalletId),
      destinationWalletId: toSafeInt(tr.destinationWalletId),
      principalAmount: Math.max(0, Number(tr.principalAmount || 0)),
      destinationAmount: Math.max(0, Number(tr.destinationAmount || 0)),
      conversionRate: Math.max(0, Number(tr.conversionRate || 1)),
      feeMode: (tr.feeMode || "NONE").slice(0, 50),
      fixedFeeInput: Math.max(0, Number(tr.fixedFeeInput || 0)),
      percentageInput: Math.max(0, Number(tr.percentageInput || 0)),
      feeAmount: Math.max(0, Number(tr.feeAmount || 0)),
      date: toSafeTimestampMillis(tr.date),
      note: (tr.note || "").slice(0, 1000),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "loans", state.loans, (l, entityId, createdAt) => ({
      userId: uid,
      entityId,
      personName: (l.personName || "Contact").trim().slice(0, 150) || "Contact",
      direction: l.direction === "BORROWED" ? "BORROWED" : "LENT",
      principalAmount: Math.max(0, Number(l.principalAmount || 0)),
      initialWalletId: toSafeInt(l.initialWalletId),
      startDate: toSafeTimestampMillis(l.startDate),
      dueDate: l.dueDate ? toSafeTimestampMillis(l.dueDate) : null,
      interestRate: Math.max(0, Number(l.interestRate || 0)),
      interestType: (l.interestType || "NONE").slice(0, 40),
      note: (l.note || "").slice(0, 1000),
      reference: (l.reference || "").slice(0, 200),
      isArchived: Boolean(l.isArchived),
      attachmentUri: (l.attachmentUri || "").slice(0, 250000),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "loan_activities", state.loanActivities, (a, entityId, createdAt) => ({
      userId: uid,
      entityId,
      loanId: toSafeInt(a.loanId),
      activityType: ["DISBURSEMENT", "REPAYMENT", "ADJUSTMENT"].includes(a.activityType) ? a.activityType : "REPAYMENT",
      principalPortion: Math.max(0, Number(a.principalPortion || 0)),
      interestPortion: Math.max(0, Number(a.interestPortion || 0)),
      walletId: toSafeInt(a.walletId),
      date: toSafeTimestampMillis(a.date),
      note: (a.note || "").slice(0, 1000),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "balance_adjustments", state.adjustments, (adj, entityId, createdAt) => ({
      userId: uid,
      entityId,
      walletId: toSafeInt(adj.walletId),
      actualBalance: Number(adj.actualBalance || 0),
      calculatedBalanceBefore: Number(adj.calculatedBalanceBefore || 0),
      difference: Number(adj.difference || 0),
      date: toSafeTimestampMillis(adj.date),
      reason: (adj.reason || "Manual balance adjustment").slice(0, 300),
      note: (adj.note || "").slice(0, 1000),
      isReversed: Boolean(adj.isReversed),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "recurring_transactions", state.recurringTransactions, (rec, entityId, createdAt) => ({
      userId: uid,
      entityId,
      title: (rec.title || "Recurring").trim().slice(0, 150) || "Recurring",
      type: rec.type === "INCOME" ? "INCOME" : "EXPENSE",
      amountMode: rec.amountMode === "MANUAL" ? "MANUAL" : "FIXED",
      amount: Math.max(0, Number(rec.amount || 0)),
      categoryId: toSafeInt(rec.categoryId),
      defaultWalletId: rec.defaultWalletId ? toSafeInt(rec.defaultWalletId) : null,
      frequency: (rec.frequency || "MONTHLY").slice(0, 40),
      dayOfMonth: Math.min(31, Math.max(1, Math.trunc(Number(rec.dayOfMonth || 1)))),
      customIntervalDays: Math.min(365, Math.max(1, Math.trunc(Number(rec.customIntervalDays || 30)))),
      nextDueDate: toSafeTimestampMillis(rec.nextDueDate),
      lastPaidDate: rec.lastPaidDate ? toSafeTimestampMillis(rec.lastPaidDate) : null,
      note: (rec.note || "").slice(0, 1000),
      isActive: rec.isActive !== false,
      reminderEnabled: rec.reminderEnabled !== false,
      createdAt,
      updatedAt: serverTimestamp()
    }));

    await syncSubcollectionToFirestore(uid, "savings_goals", state.savingsGoals, (g, entityId, createdAt) => ({
      userId: uid,
      entityId,
      title: (g.name || g.title || "Goal").trim().slice(0, 150) || "Goal",
      targetAmount: Math.max(0, Number(g.targetAmount || 0)),
      savedAmount: Math.max(0, Number(g.savedAmount || 0)),
      iconName: (g.iconName || "SAVINGS").slice(0, 100),
      colorHex: (g.colorHex || "#F15A24").slice(0, 20),
      deadlineMillis: g.targetDate ? toSafeTimestampMillis(g.targetDate) : null,
      targetDate: g.targetDate ? toSafeTimestampMillis(g.targetDate) : null,
      note: (g.note || "").slice(0, 1000),
      goalCreatedAt: toSafeTimestampMillis(g.createdAt || Date.now()),
      createdAt,
      updatedAt: serverTimestamp()
    }));

    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = `Synced (${state.wallets.length} wallets, ${state.transactions.length} transactions)`;
    saveStateToStorage();
    render();
  } catch (err) {
    console.warn("Cloud push error:", err);
    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = `Sync pending (${err.message || "check connection"})`;
    saveStateToStorage();
    render();
  }
}

let pushDebounceTimer = null;
function scheduleCloudPush() {
  saveStateToStorage();
  if (!state.cloudAuth.uid || !state.settings.hasCompletedOnboarding) return;
  if (pushDebounceTimer) clearTimeout(pushDebounceTimer);
  pushDebounceTimer = setTimeout(() => {
    pushLocalPayloadToCloud();
  }, 900);
}

async function pullCloudSnapshot(uid) {
  if (!db || !uid) return null;
  const profSnap = await getDoc(doc(db, "users", uid));
  if (!profSnap.exists()) return null;
  const profile = profSnap.data();

  const readSub = async (subName) => {
    const snap = await getDocs(collection(db, "users", uid, subName));
    return snap.docs.map((d) => {
      const data = d.data();
      return {
        ...data,
        id: Number(data.entityId || d.id),
        name: data.name || data.title || ""
      };
    });
  };

  const [
    wallets,
    categories,
    transactions,
    transfers,
    loans,
    loanActivities,
    adjustments,
    recurringTransactions,
    savingsGoals
  ] = await Promise.all([
    readSub("wallets"),
    readSub("categories"),
    readSub("transactions"),
    readSub("transfers"),
    readSub("loans"),
    readSub("loan_activities"),
    readSub("balance_adjustments"),
    readSub("recurring_transactions"),
    readSub("savings_goals")
  ]);

  return {
    profile,
    payload: {
      wallets: wallets.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)),
      categories: categories.length > 0 ? categories.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)) : defaultCategories(),
      transactions: transactions.sort((a, b) => (b.date || 0) - (a.date || 0)),
      transfers: transfers.sort((a, b) => (b.date || 0) - (a.date || 0)),
      loans: loans.sort((a, b) => (b.startDate || 0) - (a.startDate || 0)),
      loanActivities,
      adjustments,
      recurringTransactions,
      savingsGoals
    }
  };
}

function applyCloudSnapshotToState(snapshot, googleUser = {}) {
  const p = snapshot.profile || {};
  const payload = snapshot.payload;
  state.wallets = payload.wallets;
  state.categories = payload.categories;
  state.transactions = payload.transactions;
  state.transfers = payload.transfers;
  state.loans = payload.loans;
  state.loanActivities = payload.loanActivities;
  state.adjustments = payload.adjustments;
  state.recurringTransactions = payload.recurringTransactions;
  state.savingsGoals = payload.savingsGoals;

  state.settings = {
    ...state.settings,
    hasCompletedOnboarding: true,
    userName: p.displayName || googleUser.displayName || state.settings.userName || "Pocket User",
    profilePhotoUri: p.photoUrl || googleUser.photoUrl || state.settings.profilePhotoUri || "",
    currencyCode: "BDT",
    currencySymbol: "৳",
    defaultWalletId: p.defaultWalletId || (payload.wallets[0]?.id ?? null),
    warnOnNegativeBalance: p.warnOnNegativeBalance !== false,
    themeMode: p.themeMode || state.settings.themeMode || "DARK",
    accentColorHex: p.accentColorHex || state.settings.accentColorHex || "#F15A24",
    backgroundOpacity: typeof p.backgroundOpacity === "number" ? p.backgroundOpacity : 0.85,
    masterMonthlyBudget: Number(p.masterMonthlyBudget || 0),
    hideBalancesPrivacy: Boolean(p.hideBalancesPrivacy),
    syncVersion: Number(p.syncVersion || 1)
  };
  state.onboardingStage = "PAGE_1_WELCOME";
  state.ui.currentScreen = "WALLETS";
  state.ui.activeBottomTab = "BALANCES";
  saveStateToStorage();
  applyThemeToDom();
  render();
}

function startObservingRemoteProfile(uid) {
  if (!db || !uid) return;
  if (profileUnsubscribe) profileUnsubscribe();
  profileUnsubscribe = onSnapshot(doc(db, "users", uid), async (snap) => {
    if (!snap.exists()) return;
    const data = snap.data();
    const remoteVer = Number(data.syncVersion || 0);
    if (remoteVer > Number(state.settings.syncVersion || 0) && !state.cloudAuth.isSyncing) {
      const remoteSnap = await pullCloudSnapshot(uid);
      if (remoteSnap && (remoteSnap.profile.hasCompletedOnboarding || remoteSnap.payload.wallets.length > 0)) {
        applyCloudSnapshotToState(remoteSnap, state.cloudAuth);
        state.cloudAuth.lastSyncStatus = "Synced from another device";
        render();
      }
    }
  });
}

// ============================================================================
// Google Sign-In, Conflict Resolution & Sign-Out Flows
// ============================================================================
export async function triggerGoogleSignIn() {
  if (!auth) {
    showToast("Firebase Auth is unavailable in this environment.");
    return;
  }
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  state.cloudAuth.isSyncing = true;
  state.cloudAuth.lastSyncStatus = "Connecting to Google Cloud...";
  render();

  try {
    const cred = await signInWithPopup(auth, provider);
    if (cred && cred.user) {
      await handleGoogleUserSignedIn(cred.user, false);
    }
  } catch (err) {
    if (err && (err.code === "auth/popup-blocked" || err.code === "auth/operation-not-supported-in-this-environment")) {
      await signInWithRedirect(auth, provider);
      return;
    }
    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = "Sign-in cancelled";
    render();
  }
}

async function handleGoogleUserSignedIn(user, isSilentStartupRestore = false) {
  const uid = user.uid;
  const googleName = (user.displayName || "").trim();
  const googleEmail = (user.email || "").trim();
  const googlePhoto = (user.photoURL || "").trim();

  state.cloudAuth = {
    ...state.cloudAuth,
    uid,
    email: googleEmail,
    displayName: googleName,
    photoUrl: googlePhoto,
    isSyncing: true,
    lastSyncStatus: "Checking Google Cloud backup..."
  };
  render();

  try {
    const remoteSnapshot = await pullCloudSnapshot(uid);
    const hasExistingCloudData =
      remoteSnapshot !== null &&
      (Boolean(remoteSnapshot.profile.hasCompletedOnboarding) ||
        remoteSnapshot.payload.wallets.length > 0 ||
        remoteSnapshot.payload.transactions.length > 0);

    // 1. Offline user signing in from Settings -> Show Confirmation Dialog before wiping local data!
    if (!isSilentStartupRestore && state.settings.hasCompletedOnboarding) {
      const targetSnapshot = hasExistingCloudData
        ? remoteSnapshot
        : {
            profile: {
              userId: uid,
              displayName: googleName,
              email: googleEmail,
              photoUrl: googlePhoto,
              themeMode: state.settings.themeMode,
              accentColorHex: state.settings.accentColorHex,
              hasCompletedOnboarding: false,
              syncVersion: 0
            },
            payload: {
              wallets: [],
              categories: defaultCategories(),
              transactions: [],
              transfers: [],
              loans: [],
              loanActivities: [],
              adjustments: [],
              recurringTransactions: [],
              savingsGoals: []
            }
          };

      state.cloudAuth.isSyncing = false;
      state.cloudAuth.pendingConflict = targetSnapshot;
      state.cloudAuth.lastSyncStatus = "Confirm switching to Google Account";
      render();
      return;
    }

    // 2. Existing Google Account with cloud data -> Restore & open Home Page
    if (hasExistingCloudData) {
      applyCloudSnapshotToState(remoteSnapshot, { displayName: googleName, photoUrl: googlePhoto });
      state.cloudAuth.isSyncing = false;
      state.cloudAuth.pendingConflict = null;
      state.cloudAuth.lastSyncStatus = `Synced (${remoteSnapshot.payload.wallets.length} wallets, ${remoteSnapshot.payload.transactions.length} transactions)`;
      startObservingRemoteProfile(uid);
      if (!isSilentStartupRestore) {
        showToast(`Restored account data for ${googleName || googleEmail} from Google Cloud.`);
      }
      render();
      return;
    }

    // 3. Silent startup restore when already onboarded locally
    if (isSilentStartupRestore && state.settings.hasCompletedOnboarding) {
      await pushLocalPayloadToCloud();
      startObservingRemoteProfile(uid);
      return;
    }

    // 4. Brand-new Google Account on Startup Page 1 -> Wipe local data & go to Startup Page 2
    wipeAllLocalData(true, googleName, googlePhoto);
    state.onboardingStage = "PAGE_2_SETUP";
    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = `Signed in as ${googleName || googleEmail}`;
    startObservingRemoteProfile(uid);
    saveStateToStorage();
    render();
  } catch (err) {
    console.warn("Google Sign-In Cloud check error:", err);
    if (!state.settings.hasCompletedOnboarding) {
      state.settings.userName = state.settings.userName || googleName;
      state.settings.profilePhotoUri = state.settings.profilePhotoUri || googlePhoto;
      state.onboardingStage = "PAGE_2_SETUP";
      state.cloudAuth.isSyncing = false;
      state.cloudAuth.lastSyncStatus = "Signed in (cloud sync pending)";
      saveStateToStorage();
      render();
    } else if (!isSilentStartupRestore) {
      await signOut(auth).catch(() => {});
      state.cloudAuth = createInitialState().cloudAuth;
      showToast("Could not connect to Google Cloud. Local data kept intact.");
      render();
    } else {
      state.cloudAuth.isSyncing = false;
      render();
    }
  }
}

export async function resolveCloudConflict(choice) {
  const conflict = state.cloudAuth.pendingConflict;
  const uid = state.cloudAuth.uid;
  if (!conflict || !uid) return;

  const hasExistingCloudData =
    Boolean(conflict.profile.hasCompletedOnboarding) ||
    conflict.payload.wallets.length > 0 ||
    conflict.payload.transactions.length > 0;

  state.cloudAuth.pendingConflict = null;
  state.cloudAuth.isSyncing = true;
  render();

  if (choice === "KEEP_LOCAL" && !hasExistingCloudData) {
    state.settings.userName = state.settings.userName || state.cloudAuth.displayName || "Pocket User";
    state.settings.profilePhotoUri = state.settings.profilePhotoUri || state.cloudAuth.photoUrl || "";
    saveStateToStorage();
    await pushLocalPayloadToCloud();
    startObservingRemoteProfile(uid);
    showToast(`Local data backed up to ${state.cloudAuth.email || "Google Cloud"}.`);
    return;
  }

  if (hasExistingCloudData) {
    applyCloudSnapshotToState(conflict, state.cloudAuth);
    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = `Synced (${conflict.payload.wallets.length} wallets, ${conflict.payload.transactions.length} transactions)`;
    startObservingRemoteProfile(uid);
    showToast(`Local data deleted. Loaded Google Cloud backup (${conflict.payload.wallets.length} wallets).`);
    render();
  } else {
    // Brand-new Google account -> Wipe local data and go to Startup Page 2
    wipeAllLocalData(true, state.cloudAuth.displayName, state.cloudAuth.photoUrl);
    state.onboardingStage = "PAGE_2_SETUP";
    state.cloudAuth.isSyncing = false;
    state.cloudAuth.lastSyncStatus = `Signed in as ${state.cloudAuth.displayName || state.cloudAuth.email}`;
    startObservingRemoteProfile(uid);
    saveStateToStorage();
    showToast("Local data deleted. Set up wallets for your Google Account.");
    render();
  }
}

export async function dismissCloudConflict() {
  if (profileUnsubscribe) profileUnsubscribe();
  if (auth) await signOut(auth).catch(() => {});
  state.cloudAuth = createInitialState().cloudAuth;
  saveStateToStorage();
  showToast("Sign-in cancelled. Staying offline with current local data.");
  render();
}

export async function signOutGoogleAccountFromApp() {
  if (profileUnsubscribe) profileUnsubscribe();
  if (pushDebounceTimer) {
    clearTimeout(pushDebounceTimer);
    pushDebounceTimer = null;
    if (state.cloudAuth.uid && state.settings.hasCompletedOnboarding) {
      await pushLocalPayloadToCloud().catch(() => {});
    }
  }
  if (auth) await signOut(auth).catch(() => {});
  wipeAllLocalData(true, "", "");
  state.cloudAuth = createInitialState().cloudAuth;
  state.onboardingStage = "PAGE_1_WELCOME";
  state.ui.currentScreen = "WALLETS";
  state.ui.activeBottomTab = "BALANCES";
  saveStateToStorage();
  showToast("Signed out of Google Account. Cached device data cleared.");
  render();
}

function wipeAllLocalData(resetOnboardingToFalse = true, userName = "", profilePhotoUri = "") {
  const keepTheme = state.settings.themeMode || "DARK";
  const keepAccent = state.settings.accentColorHex || "#F15A24";
  state.wallets = [];
  state.categories = defaultCategories();
  state.transactions = [];
  state.transfers = [];
  state.loans = [];
  state.loanActivities = [];
  state.adjustments = [];
  state.recurringTransactions = [];
  state.savingsGoals = [];
  state.settings = {
    ...createDefaultSettings(),
    hasCompletedOnboarding: !resetOnboardingToFalse,
    userName: userName || "",
    profilePhotoUri: profilePhotoUri || "",
    themeMode: keepTheme,
    accentColorHex: keepAccent
  };
  state.ui.page2State = createInitialState().ui.page2State;
}

// ============================================================================
// Theme & Carbon-Fiber / Custom Wallpaper Application
// ============================================================================
function applyThemeToDom() {
  const accent = state.settings.accentColorHex || "#F15A24";
  document.documentElement.style.setProperty("--accent", accent);
  document.documentElement.style.setProperty("--accent-soft", `${accent}33`);
  document.documentElement.style.setProperty("--bg-opacity", String(state.settings.backgroundOpacity ?? 0.85));

  const mode = (state.settings.themeMode || "DARK").toUpperCase();
  const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
  const isLight = mode === "LIGHT" || (mode === "SYSTEM" && prefersLight);
  document.body.classList.toggle("theme-light", isLight);
}

// ============================================================================
// Inline Math Calculator Helper
// ============================================================================
export function evaluateMathInput(expr) {
  if (typeof expr === "number") return roundCurrency(expr);
  const str = String(expr || "").replace(/,/g, "").trim();
  if (!str) return 0;
  const cleaned = str.replace(/[^0-9.+\-*/]/g, "");
  if (!cleaned) return 0;
  try {
    const tokens = cleaned.match(/(\d+\.?\d*|\.\d+|[+\-*/])/g);
    if (!tokens || tokens.length === 0) return 0;
    let total = parseFloat(tokens[0]) || 0;
    for (let i = 1; i < tokens.length; i += 2) {
      const op = tokens[i];
      const next = parseFloat(tokens[i + 1]);
      if (Number.isNaN(next)) break;
      if (op === "+") total += next;
      else if (op === "-") total -= next;
      else if (op === "*") total *= next;
      else if (op === "/" && next !== 0) total /= next;
    }
    return roundCurrency(Math.max(0, total));
  } catch {
    return roundCurrency(parseFloat(cleaned) || 0);
  }
}

window.insertMathOperator = function (inputId, op) {
  const el = document.getElementById(inputId);
  if (!el) return;
  if (op === "=") {
    el.value = String(evaluateMathInput(el.value) || "");
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.focus();
    return;
  }
  const start = el.selectionStart ?? el.value.length;
  const end = el.selectionEnd ?? el.value.length;
  el.value = el.value.slice(0, start) + op + el.value.slice(end);
  const pos = start + op.length;
  el.dispatchEvent(new Event("input", { bubbles: true }));
  requestAnimationFrame(() => {
    el.focus();
    try { el.setSelectionRange(pos, pos); } catch {}
  });
};

// ============================================================================
// UI Rendering Engine (1:1 Android Jetpack Compose Screens)
// ============================================================================
function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatDateShort(millis) {
  if (!millis) return "";
  const d = new Date(Number(millis));
  return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}

function formatDateInputValue(millis) {
  const d = millis ? new Date(Number(millis)) : new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function renderPocketLogo(sizePx = 44) {
  const logoUrl = assetUrl("ic_pocket_logo.png");
  return `<span class="pocket-logo-tinted" style="width:${sizePx}px;height:${sizePx}px;-webkit-mask-image:url('${logoUrl}');mask-image:url('${logoUrl}');"></span>`;
}

function renderStartupScreen() {
  const isSignedIn = Boolean(state.cloudAuth.uid || state.cloudAuth.email);
  if (state.onboardingStage === "PAGE_1_WELCOME") {
    return `
      <div class="startup-page1" data-testid="startup_page1">
        ${renderPocketLogo(96)}
        <h1 class="startup-app-title" data-testid="startup_page1_app_name">Pocket</h1>
        <div class="startup-dev-title" data-testid="startup_page1_subtitle_hadi">App developed by Abdullahil Hadi</div>

        <button class="btn btn-primary btn-full" style="min-height:52px;" onclick="window.pocketActions.signInGoogle()" ${state.cloudAuth.isSyncing ? "disabled" : ""} data-testid="google_sign_in_primary_button">
          <span>G</span>
          <span>${state.cloudAuth.isSyncing ? "Connecting to Google..." : "Sign in with Google"}</span>
        </button>

        <div style="height:12px;"></div>

        <button class="btn btn-outline-accent btn-full" style="min-height:52px;" onclick="window.pocketActions.continueOffline()" ${state.cloudAuth.isSyncing ? "disabled" : ""} data-testid="startup_page1_continue_offline_button">
          Continue without logging in
        </button>
      </div>
    `;
  }

  // Startup Page 2: Wallet & Profile Setup (Sign in with Google card removed from top)
  const p2 = state.ui.page2State;
  const primaryNames = new Set(["cash", "bank", "bkash"]);
  const visibleStarters = p2.showMoreWallets
    ? STARTER_WALLETS
    : STARTER_WALLETS.filter((w) => primaryNames.has(w.name.toLowerCase()));

  const displayName = state.settings.userName || state.cloudAuth.displayName || "";
  const photoUri = state.settings.profilePhotoUri || state.cloudAuth.photoUrl || "";

  return `
    <div class="app-shell" style="padding-bottom:40px;" data-testid="startup_page2">
      <div class="row-between">
        <div class="row-center">
          ${renderPocketLogo(44)}
          <div>
            <div style="font-size:24px;font-weight:800;line-height:1.1;">Pocket</div>
            <div style="font-size:12px;font-weight:700;color:var(--accent);">App developed by Abdullahil Hadi</div>
          </div>
        </div>
        ${
          isSignedIn
            ? `<button class="btn btn-outline btn-sm" onclick="window.pocketActions.backToStartupPage1()">Switch account</button>`
            : `<button class="btn btn-outline btn-sm" onclick="window.pocketActions.backToStartupPage1()">← Back</button>`
        }
      </div>

      ${
        isSignedIn
          ? `<div class="elevated-box row-between" style="border-color:var(--accent);">
               <div style="font-size:12px;font-weight:700;color:var(--accent);">✓ Signed in as ${escapeHtml(state.cloudAuth.email || state.cloudAuth.displayName)}</div>
             </div>`
          : ""
      }

      <!-- 1. Your Profile -->
      <div class="glass-card">
        <div style="font-size:15px;font-weight:800;margin-bottom:10px;">Your Profile</div>
        <div class="row-center" style="gap:14px;">
          <label class="profile-avatar" style="cursor:pointer;" title="Upload Profile Photo">
            ${
              photoUri
                ? `<img src="${escapeHtml(photoUri)}" alt="Profile" style="width:100%;height:100%;object-fit:cover;" />`
                : `<span>${escapeHtml((displayName || "P").charAt(0).toUpperCase())}</span>`
            }
            <input type="file" accept="image/*" style="display:none;" onchange="window.pocketActions.uploadProfilePhoto(event)" />
          </label>
          <div style="flex:1;">
            <input
              type="text"
              class="input-field"
              placeholder="Your Name (e.g. Abdullahil Hadi)"
              value="${escapeHtml(displayName)}"
              oninput="window.pocketActions.updatePage2Name(this.value)"
            />
            <div style="font-size:11px;color:var(--text-muted);margin-top:4px;">Tap circular avatar to choose a profile photo</div>
          </div>
        </div>
      </div>

      <!-- 2. Choose Starter Wallets -->
      <div class="glass-card">
        <div class="row-between" style="margin-bottom:10px;">
          <div>
            <div style="font-size:15px;font-weight:800;">Select Your Wallets</div>
            <div style="font-size:12px;color:var(--text-secondary);">Check the wallets you use and enter today's balance (৳)</div>
          </div>
        </div>

        <div style="display:flex;flex-direction:column;gap:8px;">
          ${visibleStarters
            .map((w) => {
              const checked = Boolean(p2.selectedMap[w.name]);
              const balVal = p2.balanceMap[w.name] ?? "";
              return `
                <div class="elevated-box" style="border-color:${checked ? "var(--accent)" : "var(--border)"};background:${checked ? "var(--surface-selected)" : "var(--surface-elevated)"};">
                  <div class="row-between" onclick="window.pocketActions.toggleStarterWallet('${w.name}')" style="cursor:pointer;">
                    <div class="row-center">
                      <input type="checkbox" ${checked ? "checked" : ""} onclick="event.stopPropagation();window.pocketActions.toggleStarterWallet('${w.name}')" style="width:18px;height:18px;accent-color:var(--accent);" />
                      <div class="wallet-logo-circle" style="width:36px;height:36px;">
                        <img src="${resolveWalletLogoUrl(w.iconName, w.name)}" alt="${escapeHtml(w.name)}" />
                      </div>
                      <div>
                        <div style="font-weight:700;font-size:14px;">${escapeHtml(w.name)}</div>
                        ${!w.includeInTotalBalance ? `<div style="font-size:11px;color:var(--text-muted);">Excluded from main balance</div>` : ""}
                      </div>
                    </div>
                    ${
                      checked
                        ? `<input
                             type="number"
                             step="any"
                             class="input-field money-sm"
                             style="width:130px;min-height:38px;padding:6px 10px;text-align:right;"
                             placeholder="0"
                             value="${escapeHtml(balVal)}"
                             onclick="event.stopPropagation()"
                             oninput="window.pocketActions.updateStarterWalletBalance('${w.name}', this.value)"
                           />`
                        : `<span style="font-size:12px;color:var(--text-muted);">Tap to enable</span>`
                    }
                  </div>
                </div>
              `;
            })
            .join("")}
        </div>

        <button class="btn btn-outline btn-sm btn-full" style="margin-top:10px;" onclick="window.pocketActions.toggleShowMoreStarterWallets()">
          ${p2.showMoreWallets ? "Show fewer wallets" : "Show 7 more wallets (MTB, Brac, Nagad, Rocket, Rapid Pass, Card, Savings)"}
        </button>
      </div>

      <!-- 3. Existing Loans (Optional) -->
      <div class="glass-card">
        <div style="font-size:15px;font-weight:800;margin-bottom:4px;">Existing Loans (Optional)</div>
        <div style="font-size:12px;color:var(--text-secondary);margin-bottom:10px;">Track money already lent or borrowed before today without changing your current wallet balances</div>

        <div class="grid-2" style="margin-bottom:8px;">
          <button class="btn btn-sm ${p2.loanDirection === "LENT" ? "btn-primary" : "btn-outline"}" onclick="window.pocketActions.setPage2LoanDir('LENT')">I Lent (Will Get)</button>
          <button class="btn btn-sm ${p2.loanDirection === "BORROWED" ? "btn-primary" : "btn-outline"}" onclick="window.pocketActions.setPage2LoanDir('BORROWED')">I Borrowed (Owe)</button>
        </div>

        <div class="grid-2" style="margin-bottom:8px;">
          <input type="text" class="input-field" placeholder="Person Name" value="${escapeHtml(p2.loanPerson)}" oninput="window.pocketActions.setPage2LoanField('loanPerson', this.value)" />
          <input type="number" step="any" class="input-field" placeholder="Amount (৳)" value="${escapeHtml(p2.loanAmount)}" oninput="window.pocketActions.setPage2LoanField('loanAmount', this.value)" />
        </div>

        <div class="row-between">
          <input type="date" class="input-field" style="flex:1;" value="${escapeHtml(p2.loanDueDate)}" onchange="window.pocketActions.setPage2LoanField('loanDueDate', this.value)" />
          <button class="btn btn-outline-accent btn-sm" style="min-height:48px;" onclick="window.pocketActions.addStarterLoan()">+ Add Loan</button>
        </div>

        ${
          p2.starterLoans.length > 0
            ? `<div style="margin-top:10px;display:flex;flex-direction:column;gap:6px;">
                 ${p2.starterLoans
                   .map(
                     (sl, idx) => `
                       <div class="elevated-box row-between">
                         <div>
                           <span style="font-weight:700;">${escapeHtml(sl.personName)}</span>
                           <span class="${sl.direction === "LENT" ? "text-income" : "text-expense"}" style="font-size:12px;margin-left:6px;">
                             (${sl.direction === "LENT" ? "Lent" : "Borrowed"} ${formatMoney(sl.principalAmount)})
                           </span>
                         </div>
                         <button class="btn btn-outline btn-sm" onclick="window.pocketActions.removeStarterLoan(${idx})">✕</button>
                       </div>
                     `
                   )
                   .join("")}
               </div>`
            : ""
        }
      </div>

      <button class="btn btn-primary btn-full" style="min-height:52px;font-size:16px;" onclick="window.pocketActions.completeOnboarding()" data-testid="get_started_button">
        Get started
      </button>
    </div>
  `;
}

const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function renderBalancesHomeScreen() {
  const hide = state.settings.hideBalancesPrivacy;
  const activeWallets = state.wallets
    .filter((w) => !w.isArchived)
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const summaries = activeWallets.map((w) => calculateWalletSummary(w));

  const totalBdt = roundCurrency(
    summaries
      .filter((s) => s.wallet.includeInTotalBalance !== false && (!s.wallet.currency || s.wallet.currency.toUpperCase() === "BDT"))
      .reduce((acc, s) => acc + s.currentBalance, 0)
  );

  const foreignMap = new Map();
  for (const s of summaries) {
    const curr = (s.wallet.currency || "BDT").toUpperCase();
    if (s.wallet.includeInTotalBalance !== false && curr !== "BDT") {
      foreignMap.set(curr, roundCurrency((foreignMap.get(curr) || 0) + s.currentBalance));
    }
  }

  const report = calculateMonthlyReport(state.ui.selectedYear, state.ui.selectedMonth, "BDT");
  const userName = state.settings.userName || state.cloudAuth.displayName || "Pocket User";
  const userPhoto = state.settings.profilePhotoUri || state.cloudAuth.photoUrl || "";

  const masterBudget = Number(state.settings.masterMonthlyBudget || 0);
  const spent = report.totalExpenses;
  const budgetPct = masterBudget > 0 ? Math.min(100, Math.round((spent / masterBudget) * 100)) : 0;
  const now = new Date();
  const maxDays = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const remainingDays = Math.max(1, maxDays - now.getDate() + 1);
  const safePerDay = masterBudget > spent ? roundCurrency((masterBudget - spent) / remainingDays) : 0;

  const endOfToday = new Date();
  endOfToday.setHours(23, 59, 59, 999);
  const dueRecurring = state.recurringTransactions.filter(
    (r) => r.isActive !== false && r.reminderEnabled !== false && Number(r.nextDueDate || 0) <= endOfToday.getTime()
  );

  return `
    <!-- Top Header: Logo + "Pocket" + Month Selector Pill -->
    <div class="home-header">
      <div class="brand-click" onclick="window.pocketActions.openScreen('SETTINGS')" data-testid="home_top_left_app_icon">
        ${renderPocketLogo(42)}
        <span class="brand-name">Pocket</span>
      </div>
      <div class="month-pill">
        <button onclick="window.pocketActions.shiftMonth(-1)" title="Previous Month">‹</button>
        <span>${MONTH_NAMES[state.ui.selectedMonth]} ${state.ui.selectedYear}</span>
        <button onclick="window.pocketActions.shiftMonth(1)" title="Next Month">›</button>
      </div>
    </div>

    <!-- 1st Big Box: Total Wallet Balance + Profile Badge + Green Income & Red Expenses -->
    <div class="glass-card-lg">
      <div class="row-between" style="align-items:flex-start;">
        <div style="flex:1;">
          <div class="row-center" style="gap:6px;">
            <span style="font-size:13px;font-weight:700;color:var(--text-secondary);cursor:pointer;" onclick="window.pocketActions.openScreen('ADJUSTMENT')">Total wallet balance</span>
            <button class="btn btn-outline btn-sm" style="min-height:28px;padding:0 8px;border:none;" onclick="window.pocketActions.togglePrivacy()" title="Toggle Balance Privacy">
              ${hide ? "🙈" : "👁"}
            </button>
          </div>
          <div class="money-lg" style="margin-top:4px;cursor:pointer;" onclick="window.pocketActions.openScreen('ADJUSTMENT')" data-testid="wallets_total_balance_text">
            ${hide ? "৳ •••••" : formatMoney(totalBdt, "৳")}
          </div>
          ${Array.from(foreignMap.entries())
            .map(
              ([code, bal]) =>
                `<div class="money-sm text-secondary" style="margin-top:2px;">${
                  hide ? `${currencySymbolForCode(code)} •••• ${code}` : `${formatMoney(bal, currencySymbolForCode(code))} ${code}`
                }</div>`
            )
            .join("")}
        </div>

        <div class="profile-badge" onclick="window.pocketActions.openScreen('SETTINGS')" data-testid="balances_profile_badge">
          <div class="profile-avatar">
            ${
              userPhoto
                ? `<img src="${escapeHtml(userPhoto)}" alt="Avatar" style="width:100%;height:100%;object-fit:cover;" />`
                : `<span>${escapeHtml(userName.charAt(0).toUpperCase())}</span>`
            }
          </div>
          <span class="profile-name-label">${escapeHtml(userName)}</span>
        </div>
      </div>

      <div style="height:16px;"></div>

      <!-- Income (Green) & Expenses (Red) directly as text without inner boxes -->
      <div class="grid-2" style="gap:24px;">
        <div style="cursor:pointer;" onclick="window.pocketActions.openHistoryWithFilter('INCOME')">
          <div class="text-income" style="font-size:12px;font-weight:700;">↗ Income</div>
          <div class="money-md text-income" style="margin-top:2px;">
            ${hide ? "৳ ••••" : formatMoney(report.totalIncome, "৳", false)}
          </div>
        </div>
        <div style="cursor:pointer;" onclick="window.pocketActions.openHistoryWithFilter('EXPENSE')">
          <div class="text-expense" style="font-size:12px;font-weight:700;">↘ Expenses</div>
          <div class="money-md text-expense" style="margin-top:2px;">
            ${hide ? "৳ ••••" : formatMoney(report.totalExpenses, "৳", false)}
          </div>
        </div>
      </div>

      ${
        masterBudget > 0
          ? `<div style="margin-top:14px;cursor:pointer;" onclick="window.pocketActions.switchBottomTab('REPORTS')">
               <div class="row-between" style="font-size:11px;font-weight:700;margin-bottom:5px;">
                 <span class="text-secondary">Monthly Budget (${budgetPct}%)</span>
                 <span class="${spent > masterBudget ? "text-expense" : "text-accent"}">
                   ${
                     hide
                       ? "৳ •••• / day"
                       : spent > masterBudget
                       ? `Over budget by ${formatMoney(spent - masterBudget, "৳", false)}`
                       : `Safe: ${formatMoney(safePerDay, "৳", false)}/day (${remainingDays} d left)`
                   }
                 </span>
               </div>
               <div class="progress-track">
                 <div class="progress-fill" style="width:${budgetPct}%;background:${spent > masterBudget ? "var(--expense)" : "var(--accent)"};"></div>
               </div>
             </div>`
          : ""
      }
    </div>

    <!-- Due Recurring Bills Banner -->
    ${
      dueRecurring.length > 0
        ? `<div class="glass-card" style="border-color:var(--accent);">
             <div class="row-between" style="margin-bottom:10px;">
               <div style="font-weight:800;font-size:14px;">🔁 Recurring Due (${dueRecurring.length})</div>
               <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('RECURRING')">Manage All</button>
             </div>
             <div style="display:flex;flex-direction:column;gap:8px;">
               ${dueRecurring
                 .map(
                   (r) => `
                     <div class="elevated-box">
                       <div class="row-between" style="margin-bottom:8px;">
                         <div>
                           <div style="font-weight:700;font-size:14px;">${escapeHtml(r.title)}</div>
                           <div style="font-size:11px;color:var(--text-secondary);">Due ${formatDateShort(r.nextDueDate)}</div>
                         </div>
                         <div class="money-md ${r.type === "INCOME" ? "text-income" : "text-expense"}">
                           ${r.amountMode === "FIXED" && r.amount > 0 ? formatMoney(r.amount) : "Manual"}
                         </div>
                       </div>
                       <div class="grid-2">
                         <button class="btn btn-outline btn-sm" onclick="window.pocketActions.snoozeRecurring(${r.id})">Remind Tomorrow</button>
                         <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openRecurringPayModal(${r.id})">${r.type === "INCOME" ? "Receive" : "Pay"}</button>
                       </div>
                     </div>
                   `
                 )
                 .join("")}
             </div>
           </div>`
        : ""
    }

    <!-- Available Wallets List -->
    <div style="display:flex;flex-direction:column;gap:12px;">
      ${summaries
        .map((s, idx) => {
          const w = s.wallet;
          const wCurr = (w.currency || "BDT").toUpperCase();
          const wSym = currencySymbolForCode(wCurr);
          return `
            <div class="wallet-card" onclick="window.pocketActions.openWalletDetail(${w.id})" data-testid="wallet_overview_item_${escapeHtml(w.name)}">
              <div class="row-center" style="flex:1;min-width:0;">
                <div class="wallet-logo-circle">
                  <img src="${resolveWalletLogoUrl(w.iconName, w.name)}" alt="${escapeHtml(w.name)}" />
                </div>
                <div style="min-width:0;flex:1;">
                  <div class="row-center" style="gap:6px;">
                    <span style="font-size:16px;font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(w.name)}</span>
                    ${wCurr !== "BDT" ? `<span class="currency-badge">${escapeHtml(wCurr)}</span>` : ""}
                  </div>
                  ${!w.includeInTotalBalance ? `<div style="font-size:11px;color:var(--text-muted);">Not in total balance</div>` : ""}
                  ${w.accountHint ? `<div style="font-size:11px;color:var(--text-secondary);">${escapeHtml(w.accountHint)}</div>` : ""}
                </div>
              </div>
              <div class="row-center" style="gap:8px;">
                <div class="money-md ${s.currentBalance < 0 ? "text-expense" : ""}">
                  ${hide ? `${wSym} ••••` : formatMoney(s.currentBalance, wSym)}
                </div>
                <div style="display:flex;flex-direction:column;gap:2px;" onclick="event.stopPropagation()">
                  ${idx > 0 ? `<button class="btn btn-outline btn-sm" style="min-height:22px;padding:0 5px;font-size:10px;" onclick="window.pocketActions.moveWallet(${w.id}, -1)" title="Move Up">▲</button>` : ""}
                  ${idx < summaries.length - 1 ? `<button class="btn btn-outline btn-sm" style="min-height:22px;padding:0 5px;font-size:10px;" onclick="window.pocketActions.moveWallet(${w.id}, 1)" title="Move Down">▼</button>` : ""}
                </div>
              </div>
            </div>
          `;
        })
        .join("")}

      <!-- + New Wallet Card -->
      <div class="add-wallet-card" onclick="window.pocketActions.openWalletModal(null)" data-testid="add_wallet_button">
        <span>＋</span>
        <span>New Wallet</span>
      </div>
    </div>

    <!-- Savings Goals (Virtual Pots) Section -->
    <div class="glass-card" data-testid="savings_goals_section_card">
      <div class="row-between" style="margin-bottom:10px;">
        <div style="font-size:16px;font-weight:800;">🐷 Savings Goals</div>
        <button class="btn btn-outline-accent btn-sm" onclick="window.pocketActions.openSavingsGoalModal(null)">+ Add Goal</button>
      </div>
      ${
        state.savingsGoals.length === 0
          ? `<div style="font-size:12px;color:var(--text-secondary);">No virtual pots yet. Tap + Add Goal for Emergency Fund, New Phone, or Tour.</div>`
          : `<div style="display:flex;flex-direction:column;gap:10px;">
               ${state.savingsGoals
                 .map((g) => {
                   const pct = g.targetAmount > 0 ? Math.min(100, Math.round((Number(g.savedAmount || 0) / Number(g.targetAmount)) * 100)) : 0;
                   return `
                     <div class="elevated-box" style="cursor:pointer;" onclick="window.pocketActions.openSavingsGoalModal(${g.id})">
                       <div class="row-between" style="margin-bottom:6px;">
                         <span style="font-weight:700;font-size:14px;">${escapeHtml(g.name || g.title)}</span>
                         <span class="money-sm text-accent">
                           ${hide ? "৳ •••• / ৳ ••••" : `${formatMoney(g.savedAmount, "৳", false)} / ${formatMoney(g.targetAmount, "৳", false)}`}
                         </span>
                       </div>
                       <div class="progress-track" style="margin-bottom:8px;">
                         <div class="progress-fill" style="width:${pct}%;"></div>
                       </div>
                       <div class="row-between" onclick="event.stopPropagation()">
                         <span style="font-size:11px;color:var(--text-muted);">${pct}% saved</span>
                         <div class="row-center" style="gap:6px;">
                           <button class="btn btn-outline btn-sm" style="min-height:28px;padding:0 10px;font-size:11px;" onclick="window.pocketActions.adjustSavingsGoal(${g.id}, false)">− Withdraw</button>
                           <button class="btn btn-primary btn-sm" style="min-height:28px;padding:0 10px;font-size:11px;" onclick="window.pocketActions.adjustSavingsGoal(${g.id}, true)">+ Add</button>
                         </div>
                       </div>
                     </div>
                   `;
                 })
                 .join("")}
             </div>`
      }
    </div>
  `;
}

function renderWalletDetailScreen() {
  const wallet = state.wallets.find((w) => Number(w.id) === Number(state.ui.selectedWalletId));
  if (!wallet) {
    state.ui.currentScreen = "WALLETS";
    return renderBalancesHomeScreen();
  }
  const summary = calculateWalletSummary(wallet);
  const sym = currencySymbolForCode(wallet.currency || "BDT");
  const ledger = buildUnifiedLedger().filter(
    (e) => Number(e.walletId) === Number(wallet.id) || Number(e.secondaryWalletId) === Number(wallet.id)
  );

  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('WALLETS')">← Back</button>
      <div style="font-size:18px;font-weight:800;">${escapeHtml(wallet.name)}</div>
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openWalletModal(${wallet.id})">Edit</button>
    </div>

    <div class="glass-card-lg">
      <div class="row-between">
        <div class="row-center">
          <div class="wallet-logo-circle" style="width:52px;height:52px;">
            <img src="${resolveWalletLogoUrl(wallet.iconName, wallet.name)}" alt="${escapeHtml(wallet.name)}" />
          </div>
          <div>
            <div style="font-size:12px;color:var(--text-secondary);">Current Balance (${escapeHtml(wallet.currency || "BDT")})</div>
            <div class="money-lg">${formatMoney(summary.currentBalance, sym)}</div>
          </div>
        </div>
        <button class="btn btn-outline-accent btn-sm" onclick="window.pocketActions.openScreen('ADJUSTMENT', ${wallet.id})">Reconcile</button>
      </div>

      <div class="grid-2" style="margin-top:16px;">
        <div class="elevated-box">
          <div style="font-size:11px;color:var(--text-secondary);">Opening Balance</div>
          <div class="money-sm" style="margin-top:2px;">${formatMoney(wallet.openingBalance, sym)}</div>
        </div>
        <div class="elevated-box">
          <div style="font-size:11px;color:var(--text-secondary);">Net Transfers / Loans</div>
          <div class="money-sm" style="margin-top:2px;">${formatMoney(summary.transfersIn - summary.transfersOut + summary.loanInflows - summary.loanOutflows, sym, true, true)}</div>
        </div>
      </div>

      <div class="grid-2" style="margin-top:10px;">
        <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openTransactionModal('EXPENSE', null, ${wallet.id})">+ Record Expense / Income</button>
        <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openTransferModal(null, ${wallet.id})">⇄ Transfer</button>
      </div>
    </div>

    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:10px;">Wallet Ledger (${ledger.length})</div>
      ${
        ledger.length === 0
          ? `<div style="font-size:13px;color:var(--text-secondary);">No transactions in this wallet yet.</div>`
          : `<div style="display:flex;flex-direction:column;gap:8px;">
               ${ledger.map((e) => renderLedgerRow(e)).join("")}
             </div>`
      }
    </div>
  `;
}

function renderLedgerRow(entry) {
  const sym = currencySymbolForCode(entry.currencyCode || "BDT");
  const colorClass =
    entry.kind === "INCOME" || entry.kind === "EXPENSE_REVERSAL"
      ? "text-income"
      : entry.kind === "EXPENSE"
      ? "text-expense"
      : entry.kind === "TRANSFER"
      ? "text-transfer"
      : "text-loan";

  return `
    <div class="elevated-box row-between" style="cursor:pointer;" onclick="window.pocketActions.inspectLedgerEntry('${entry.uid}')">
      <div style="min-width:0;flex:1;">
        <div style="font-weight:700;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(entry.title)}</div>
        <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
          ${escapeHtml(entry.categoryName)} • ${escapeHtml(entry.walletName)}${entry.secondaryWalletName ? ` → ${escapeHtml(entry.secondaryWalletName)}` : ""} • ${formatDateShort(entry.date)}
        </div>
      </div>
      <div class="money-md ${colorClass}">
        ${formatMoney(entry.signedAmount, sym, true, true)}
      </div>
    </div>
  `;
}

function renderHistoryScreen() {
  const allEntries = buildUnifiedLedger();
  const q = (state.ui.historySearch || "").trim().toLowerCase();
  const typeFilter = state.ui.historyFilterType || "ALL";
  const walletFilter = state.ui.historyFilterWalletId ? Number(state.ui.historyFilterWalletId) : null;

  const filtered = allEntries.filter((e) => {
    if (typeFilter !== "ALL" && e.kind !== typeFilter) return false;
    if (walletFilter && Number(e.walletId) !== walletFilter && Number(e.secondaryWalletId) !== walletFilter) return false;
    if (q) {
      const hay = `${e.title} ${e.categoryName} ${e.walletName} ${e.note} ${e.reference} ${e.tags}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  return `
    <div class="row-between">
      <div style="font-size:22px;font-weight:800;">Transaction History</div>
      <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openTransactionModal('EXPENSE')">+ Add Record</button>
    </div>

    <div class="glass-card" style="padding:12px;">
      <input
        type="text"
        class="input-field"
        placeholder="Search note, category, wallet, TrxID, tag..."
        value="${escapeHtml(state.ui.historySearch)}"
        oninput="window.pocketActions.setHistorySearch(this.value)"
      />
      <div class="chip-scroll" style="margin-top:10px;">
        ${["ALL", "INCOME", "EXPENSE", "TRANSFER", "LOAN", "ADJUSTMENT"]
          .map(
            (k) => `
              <button class="filter-chip ${typeFilter === k ? "active" : ""}" onclick="window.pocketActions.setHistoryTypeFilter('${k}')">
                ${k === "ALL" ? "All" : k.charAt(0) + k.slice(1).toLowerCase()}
              </button>
            `
          )
          .join("")}
      </div>
    </div>

    <div style="display:flex;flex-direction:column;gap:8px;">
      ${
        filtered.length === 0
          ? `<div class="glass-card" style="text-align:center;color:var(--text-secondary);">No matching records found.</div>`
          : filtered.map((e) => renderLedgerRow(e)).join("")
      }
    </div>
  `;
}

function renderReportsScreen() {
  const currencies = ["BDT", ...new Set(state.wallets.map((w) => (w.currency || "BDT").toUpperCase()).filter((c) => c !== "BDT"))];
  const activeCurr = currencies.includes(state.ui.selectedReportCurrency) ? state.ui.selectedReportCurrency : "BDT";
  const sym = currencySymbolForCode(activeCurr);
  const report = calculateMonthlyReport(state.ui.selectedYear, state.ui.selectedMonth, activeCurr);

  return `
    <div class="row-between">
      <div style="font-size:22px;font-weight:800;">Monthly Report</div>
      <div class="month-pill">
        <button onclick="window.pocketActions.shiftMonth(-1)">‹</button>
        <span>${MONTH_NAMES[state.ui.selectedMonth]} ${state.ui.selectedYear}</span>
        <button onclick="window.pocketActions.shiftMonth(1)">›</button>
      </div>
    </div>

    ${
      currencies.length > 1
        ? `<div class="chip-scroll">
             ${currencies
               .map(
                 (c) => `
                   <button class="filter-chip ${activeCurr === c ? "active" : ""}" onclick="window.pocketActions.setReportCurrency('${c}')">${c}</button>
                 `
               )
               .join("")}
           </div>`
        : ""
    }

    <div class="glass-card-lg">
      <div class="grid-2" style="gap:14px;">
        <div class="elevated-box">
          <div style="font-size:12px;color:var(--text-secondary);">Total Income</div>
          <div class="money-md text-income" style="margin-top:4px;">${formatMoney(report.totalIncome, sym)}</div>
        </div>
        <div class="elevated-box">
          <div style="font-size:12px;color:var(--text-secondary);">Total Expenses</div>
          <div class="money-md text-expense" style="margin-top:4px;">${formatMoney(report.totalExpenses, sym)}</div>
        </div>
      </div>
      <div class="elevated-box row-between" style="margin-top:10px;">
        <span style="font-size:13px;font-weight:700;">Net Cashflow</span>
        <span class="money-md ${report.netSavings >= 0 ? "text-income" : "text-expense"}">${formatMoney(report.netSavings, sym, true, true)}</span>
      </div>
      <div class="grid-2" style="margin-top:12px;">
        <button class="btn btn-outline btn-sm" onclick="window.pocketActions.exportMonthlyCsv()">⬇ Export CSV</button>
        <button class="btn btn-outline-accent btn-sm" onclick="window.print()">🖨 Print / Save PDF</button>
      </div>
    </div>

    <div class="glass-card">
      <div style="font-size:16px;font-weight:800;margin-bottom:12px;">Spending by Category</div>
      ${
        report.categoryBreakdown.length === 0
          ? `<div style="font-size:13px;color:var(--text-secondary);">No expenses recorded in ${MONTH_NAMES[state.ui.selectedMonth]} ${state.ui.selectedYear}.</div>`
          : `<div style="display:flex;flex-direction:column;gap:12px;">
               ${report.categoryBreakdown
                 .map(
                   (item) => `
                     <div>
                       <div class="row-between" style="margin-bottom:4px;">
                         <span style="font-weight:700;font-size:14px;">${escapeHtml(item.categoryName)} (${item.percentage}%)</span>
                         <span class="money-sm">${formatMoney(item.amount, sym)}</span>
                       </div>
                       <div class="progress-track">
                         <div class="progress-fill" style="width:${Math.min(100, item.percentage)}%;background:${escapeHtml(item.colorHex)};"></div>
                       </div>
                     </div>
                   `
                 )
                 .join("")}
             </div>`
      }
    </div>
  `;
}

function renderLoansScreen() {
  const summaries = calculateLoanSummaries();
  const totalLentOut = roundCurrency(
    summaries.filter((s) => s.loan.direction === "LENT").reduce((acc, s) => acc + s.outstandingPrincipal, 0)
  );
  const totalBorrowedOut = roundCurrency(
    summaries.filter((s) => s.loan.direction === "BORROWED").reduce((acc, s) => acc + s.outstandingPrincipal, 0)
  );

  const filter = state.ui.loanFilter || "ACTIVE";
  const filtered = summaries.filter((s) => {
    if (filter === "ACTIVE") return s.status !== "SETTLED";
    if (filter === "SETTLED") return s.status === "SETTLED";
    return true;
  });

  return `
    <div class="row-between">
      <div style="font-size:22px;font-weight:800;">Loans & Khata</div>
      <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openLoanModal()">+ New Loan</button>
    </div>

    <div class="grid-2">
      <div class="glass-card">
        <div style="font-size:12px;color:var(--text-secondary);">You Will Get (Lent)</div>
        <div class="money-md text-income" style="margin-top:4px;">${formatMoney(totalLentOut, "৳")}</div>
      </div>
      <div class="glass-card">
        <div style="font-size:12px;color:var(--text-secondary);">You Owe (Borrowed)</div>
        <div class="money-md text-expense" style="margin-top:4px;">${formatMoney(totalBorrowedOut, "৳")}</div>
      </div>
    </div>

    <div class="chip-scroll">
      ${["ACTIVE", "SETTLED", "ALL"]
        .map(
          (f) => `
            <button class="filter-chip ${filter === f ? "active" : ""}" onclick="window.pocketActions.setLoanFilter('${f}')">
              ${f === "ACTIVE" ? "Active Loans" : f === "SETTLED" ? "Settled" : "All Loans"}
            </button>
          `
        )
        .join("")}
    </div>

    <div style="display:flex;flex-direction:column;gap:10px;">
      ${
        filtered.length === 0
          ? `<div class="glass-card" style="text-align:center;color:var(--text-secondary);">No loans in this view.</div>`
          : filtered
              .map(
                (s) => `
                  <div class="glass-card">
                    <div class="row-between">
                      <div>
                        <div style="font-size:16px;font-weight:800;">${escapeHtml(s.loan.personName)}</div>
                        <div style="font-size:12px;color:var(--text-secondary);">
                          ${s.loan.direction === "LENT" ? "Lent" : "Borrowed"} • Principal ${formatMoney(s.effectivePrincipal)}
                          ${s.loan.dueDate ? ` • Due ${formatDateShort(s.loan.dueDate)}` : ""}
                        </div>
                      </div>
                      <div style="text-align:right;">
                        <div class="money-md ${s.loan.direction === "LENT" ? "text-income" : "text-expense"}">
                          ${formatMoney(s.outstandingPrincipal)}
                        </div>
                        <div style="font-size:11px;color:var(--text-muted);">${s.status}</div>
                      </div>
                    </div>
                    <div class="row-between" style="margin-top:12px;">
                      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.deleteLoan(${s.loan.id})">Delete</button>
                      ${
                        s.status !== "SETTLED"
                          ? `<button class="btn btn-primary btn-sm" onclick="window.pocketActions.openLoanRepaymentModal(${s.loan.id})">
                               ${s.loan.direction === "LENT" ? "Receive Payment" : "Pay Installment"}
                             </button>`
                          : `<span style="font-size:12px;font-weight:700;color:var(--income);">✓ Settled</span>`
                      }
                    </div>
                  </div>
                `
              )
              .join("")
      }
    </div>
  `;
}

function renderSettingsScreen() {
  const isSignedIn = Boolean(state.cloudAuth.uid || state.cloudAuth.email);
  const s = state.settings;
  const userName = s.userName || state.cloudAuth.displayName || "Pocket User";
  const userPhoto = s.profilePhotoUri || state.cloudAuth.photoUrl || "";
  const accentPresets = ["#F15A24", "#E11D48", "#2563EB", "#16A34A", "#7C3AED", "#D97706", "#0D9488"];

  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('WALLETS')">← Back</button>
      <div style="font-size:20px;font-weight:800;">Settings</div>
      <div style="width:60px;"></div>
    </div>

    <!-- Profile Card -->
    <div class="glass-card">
      <div class="row-center" style="gap:14px;">
        <label class="profile-avatar" style="cursor:pointer;" title="Change Profile Photo">
          ${
            userPhoto
              ? `<img src="${escapeHtml(userPhoto)}" alt="Profile" style="width:100%;height:100%;object-fit:cover;" />`
              : `<span>${escapeHtml(userName.charAt(0).toUpperCase())}</span>`
          }
          <input type="file" accept="image/*" style="display:none;" onchange="window.pocketActions.uploadProfilePhoto(event)" />
        </label>
        <div style="flex:1;">
          <input
            type="text"
            class="input-field"
            value="${escapeHtml(userName)}"
            placeholder="Your Name"
            onchange="window.pocketActions.updateUserName(this.value)"
          />
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px;">App developed by Abdullahil Hadi</div>
        </div>
      </div>
    </div>

    <!-- Google Account Cloud Sync Card -->
    <div class="glass-card" data-testid="settings_google_sync_card">
      <div style="font-size:15px;font-weight:800;margin-bottom:6px;">☁ Google Account Cloud Sync</div>
      ${
        isSignedIn
          ? `<div style="font-size:13px;color:var(--text-secondary);margin-bottom:10px;">
               Signed in as <strong>${escapeHtml(state.cloudAuth.email || state.cloudAuth.displayName)}</strong><br/>
               <span style="font-size:12px;color:var(--accent);">${escapeHtml(state.cloudAuth.lastSyncStatus)}</span>
             </div>
             <div class="grid-2">
               <button class="btn btn-primary btn-sm" onclick="window.pocketActions.syncNow()" ${state.cloudAuth.isSyncing ? "disabled" : ""}>
                 ${state.cloudAuth.isSyncing ? "Syncing..." : "Sync Now"}
               </button>
               <button class="btn btn-danger btn-sm" onclick="window.pocketActions.confirmSignOutModal()">Sign Out</button>
             </div>`
          : `<div style="font-size:13px;color:var(--text-secondary);margin-bottom:10px;">
               Currently using Pocket offline. Sign in with Google to switch to your cloud backup.
             </div>
             <button class="btn btn-primary btn-full" onclick="window.pocketActions.signInGoogle()">
               Sign in with Google
             </button>`
      }
    </div>

    <!-- Financial Modules Navigation -->
    <div class="glass-card" style="display:flex;flex-direction:column;gap:8px;">
      <div style="font-size:15px;font-weight:800;margin-bottom:4px;">Financial Modules & Tools</div>
      <button class="btn btn-outline btn-full" style="justify-content:space-between;" onclick="window.pocketActions.openScreen('RECURRING')">
        <span>🔁 Recurring Bills & Subscriptions</span><span>›</span>
      </button>
      <button class="btn btn-outline btn-full" style="justify-content:space-between;" onclick="window.pocketActions.openScreen('CATEGORIES')">
        <span>🏷 Categories & Monthly Budgets</span><span>›</span>
      </button>
      <button class="btn btn-outline btn-full" style="justify-content:space-between;" onclick="window.pocketActions.openScreen('ADJUSTMENT')">
        <span>⚖ Reconcile Wallet Balance</span><span>›</span>
      </button>
      <button class="btn btn-outline btn-full" style="justify-content:space-between;" onclick="window.pocketActions.openScreen('BACKUP')">
        <span>💾 JSON Backup & Restore</span><span>›</span>
      </button>
    </div>

    <!-- Appearance & Personalization -->
    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:10px;">Appearance & Wallpaper</div>
      <div class="grid-2" style="margin-bottom:12px;">
        <button class="btn btn-sm ${s.themeMode === "DARK" ? "btn-primary" : "btn-outline"}" onclick="window.pocketActions.setThemeMode('DARK')">🌙 Dark Carbon</button>
        <button class="btn btn-sm ${s.themeMode === "LIGHT" ? "btn-primary" : "btn-outline"}" onclick="window.pocketActions.setThemeMode('LIGHT')">☀ Light Mode</button>
      </div>

      <div class="field-label" style="margin-bottom:6px;">Accent Color</div>
      <div class="row-center" style="gap:8px;flex-wrap:wrap;margin-bottom:12px;">
        ${accentPresets
          .map(
            (hex) => `
              <button
                onclick="window.pocketActions.setAccentColor('${hex}')"
                style="width:32px;height:32px;border-radius:50%;background:${hex};border:${s.accentColorHex.toUpperCase() === hex ? "3px solid var(--text)" : "1px solid var(--border)"};cursor:pointer;"
              ></button>
            `
          )
          .join("")}
      </div>

      <div class="field-group">
        <div class="row-between">
          <span class="field-label">Background Weave / Wallpaper Opacity</span>
          <span class="money-sm">${Math.round((s.backgroundOpacity ?? 0.85) * 100)}%</span>
        </div>
        <input
          type="range"
          min="0"
          max="100"
          value="${Math.round((s.backgroundOpacity ?? 0.85) * 100)}"
          oninput="window.pocketActions.setBackgroundOpacity(this.value)"
          style="width:100%;accent-color:var(--accent);"
        />
      </div>

      <div class="row-between" style="margin-top:8px;">
        <label class="btn btn-outline btn-sm" style="cursor:pointer;">
          🖼 Custom Wallpaper
          <input type="file" accept="image/*" style="display:none;" onchange="window.pocketActions.uploadWallpaper(event)" />
        </label>
        ${
          s.customBackgroundUri
            ? `<button class="btn btn-outline btn-sm" onclick="window.pocketActions.clearWallpaper()">Reset Wallpaper</button>`
            : ""
        }
      </div>
    </div>

    <!-- Budget, Security & Danger Zone -->
    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:10px;">Budget & Security</div>
      <div class="field-group">
        <label class="field-label">Master Monthly Spending Budget (৳)</label>
        <input
          type="number"
          step="any"
          class="input-field"
          value="${escapeHtml(s.masterMonthlyBudget || "")}"
          placeholder="0 (Disabled)"
          onchange="window.pocketActions.setMasterBudget(this.value)"
        />
      </div>
      <div class="field-group">
        <label class="field-label">4-Digit App Lock PIN (Optional)</label>
        <input
          type="password"
          maxlength="8"
          class="input-field"
          value="${escapeHtml(s.appLockPin || "")}"
          placeholder="Leave empty to disable PIN lock"
          onchange="window.pocketActions.setAppLockPin(this.value)"
        />
      </div>
      <button class="btn btn-danger btn-sm btn-full" style="margin-top:8px;" onclick="window.pocketActions.resetAllNumericalData()">
        Reset All Numerical Data to 0
      </button>
    </div>
  `;
}

function renderRecurringScreen() {
  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('SETTINGS')">← Back</button>
      <div style="font-size:20px;font-weight:800;">Recurring Bills</div>
      <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openRecurringFormModal()">+ Add</button>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;">
      ${
        state.recurringTransactions.length === 0
          ? `<div class="glass-card" style="text-align:center;color:var(--text-secondary);">No recurring rules yet. Tap + Add to schedule monthly bills or salary.</div>`
          : state.recurringTransactions
              .map(
                (r) => `
                  <div class="glass-card row-between">
                    <div>
                      <div style="font-weight:700;font-size:15px;">${escapeHtml(r.title)}</div>
                      <div style="font-size:12px;color:var(--text-secondary);">
                        ${r.frequency} • Next due ${formatDateShort(r.nextDueDate)}
                      </div>
                    </div>
                    <div class="row-center" style="gap:8px;">
                      <span class="money-md ${r.type === "INCOME" ? "text-income" : "text-expense"}">
                        ${r.amountMode === "FIXED" ? formatMoney(r.amount) : "Manual"}
                      </span>
                      <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openRecurringPayModal(${r.id})">
                        ${r.type === "INCOME" ? "Receive" : "Pay"}
                      </button>
                      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.deleteRecurring(${r.id})">✕</button>
                    </div>
                  </div>
                `
              )
              .join("")
      }
    </div>
  `;
}

function renderCategoriesScreen() {
  const incomes = state.categories.filter((c) => c.type === "INCOME").sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const expenses = state.categories.filter((c) => c.type === "EXPENSE").sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));

  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('SETTINGS')">← Back</button>
      <div style="font-size:20px;font-weight:800;">Categories</div>
      <button class="btn btn-primary btn-sm" onclick="window.pocketActions.openCategoryModal()">+ New</button>
    </div>

    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:10px;" class="text-income">Income Categories (${incomes.length})</div>
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${incomes
          .map(
            (c) => `
              <div class="elevated-box row-between">
                <span style="font-weight:700;">${escapeHtml(c.name)}</span>
                <button class="btn btn-outline btn-sm" onclick="window.pocketActions.deleteCategory(${c.id})">Delete</button>
              </div>
            `
          )
          .join("")}
      </div>
    </div>

    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:10px;" class="text-expense">Expense Categories (${expenses.length})</div>
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${expenses
          .map(
            (c) => `
              <div class="elevated-box row-between">
                <div>
                  <div style="font-weight:700;">${escapeHtml(c.name)}</div>
                  ${c.monthlyBudget > 0 ? `<div style="font-size:11px;color:var(--text-secondary);">Budget: ${formatMoney(c.monthlyBudget)}/mo</div>` : ""}
                </div>
                ${!c.isSystemFee ? `<button class="btn btn-outline btn-sm" onclick="window.pocketActions.deleteCategory(${c.id})">Delete</button>` : `<span style="font-size:11px;color:var(--text-muted);">System</span>`}
              </div>
            `
          )
          .join("")}
      </div>
    </div>
  `;
}

function renderAdjustmentScreen() {
  const activeWallets = state.wallets.filter((w) => !w.isArchived);
  const selectedId = state.ui.selectedWalletId || activeWallets[0]?.id || 0;
  const targetWallet = activeWallets.find((w) => Number(w.id) === Number(selectedId));
  const summary = targetWallet ? calculateWalletSummary(targetWallet) : null;

  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('WALLETS')">← Back</button>
      <div style="font-size:20px;font-weight:800;">Reconcile Balance</div>
      <div style="width:60px;"></div>
    </div>

    <div class="glass-card">
      <div class="field-group">
        <label class="field-label">Select Wallet</label>
        <select class="select-field" id="adj_wallet_id" onchange="window.pocketActions.selectAdjustmentWallet(this.value)">
          ${activeWallets
            .map(
              (w) => `<option value="${w.id}" ${Number(w.id) === Number(selectedId) ? "selected" : ""}>${escapeHtml(w.name)} (${escapeHtml(w.currency || "BDT")})</option>`
            )
            .join("")}
        </select>
      </div>
      ${
        summary
          ? `<div class="elevated-box row-between" style="margin-bottom:12px;">
               <span style="font-size:13px;color:var(--text-secondary);">Calculated App Balance</span>
               <span class="money-md">${formatMoney(summary.currentBalance, currencySymbolForCode(targetWallet.currency))}</span>
             </div>`
          : ""
      }
      <div class="field-group">
        <label class="field-label">Actual Real-World Balance Today</label>
        <input type="number" step="any" id="adj_actual_input" class="input-field" placeholder="Enter actual balance" value="${summary ? summary.currentBalance : 0}" />
      </div>
      <div class="field-group">
        <label class="field-label">Reason / Note</label>
        <input type="text" id="adj_reason_input" class="input-field" placeholder="e.g. Cash count reconciliation" value="Balance reconciliation" />
      </div>
      <button class="btn btn-primary btn-full" onclick="window.pocketActions.submitBalanceAdjustment()">Save Reconciliation</button>
    </div>
  `;
}

function renderBackupScreen() {
  return `
    <div class="row-between">
      <button class="btn btn-outline btn-sm" onclick="window.pocketActions.openScreen('SETTINGS')">← Back</button>
      <div style="font-size:20px;font-weight:800;">Backup & Restore</div>
      <div style="width:60px;"></div>
    </div>

    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:6px;">Export Pocket Backup (JSON)</div>
      <div style="font-size:12px;color:var(--text-secondary);margin-bottom:12px;">
        Downloads a complete JSON backup compatible with both Android Pocket and Web Pocket.
      </div>
      <button class="btn btn-primary btn-full" onclick="window.pocketActions.downloadJsonBackup()">⬇ Download JSON Backup</button>
    </div>

    <div class="glass-card">
      <div style="font-size:15px;font-weight:800;margin-bottom:6px;">Restore from JSON Backup</div>
      <div style="font-size:12px;color:var(--text-secondary);margin-bottom:12px;">
        Select a previously exported <code>.json</code> backup file to restore all wallets, transactions, loans, and settings.
      </div>
      <label class="btn btn-outline-accent btn-full" style="cursor:pointer;">
        📂 Choose Backup JSON File
        <input type="file" accept=".json,application/json" style="display:none;" onchange="window.pocketActions.restoreJsonBackup(event)" />
      </label>
    </div>
  `;
}

// ============================================================================
// Modals (Cloud Conflict, Sign-Out, Transaction, Transfer, Wallet, Loan, etc.)
// ============================================================================
function renderActiveModal() {
  // 1. Highest priority: Cloud Sync Conflict Dialog ("Local Data Will Be Gone Forever")
  if (state.cloudAuth.pendingConflict) {
    const snap = state.cloudAuth.pendingConflict;
    const hasExisting =
      Boolean(snap.profile.hasCompletedOnboarding) ||
      snap.payload.wallets.length > 0 ||
      snap.payload.transactions.length > 0;
    const email = state.cloudAuth.email || "your Google account";

    return `
      <div class="modal-scrim">
        <div class="modal-card" data-testid="cloud_conflict_dialog">
          <div style="font-size:20px;font-weight:800;color:var(--expense);margin-bottom:10px;">⚠ Local Data Will Be Gone Forever</div>
          <p style="font-size:14px;color:var(--text-secondary);line-height:1.45;margin:0 0 12px;">
            You have been using Pocket without logging in. Signing in to <strong>${escapeHtml(email)}</strong> will permanently delete all local offline data on this device — local data will NOT be merged and will be gone forever.
          </p>
          <p style="font-size:13px;font-weight:700;color:var(--accent);margin:0 0 16px;">
            ${
              hasExisting
                ? `Your Google account backup (${snap.payload.wallets.length} wallets, ${snap.payload.transactions.length} transactions) will be loaded instead.`
                : `This Google account has no saved cloud data yet, so you will proceed to wallet setup after deleting local data.`
            }
          </p>
          <div style="display:flex;flex-direction:column;gap:10px;">
            <button class="btn btn-danger btn-full" onclick="window.pocketActions.resolveConflict('USE_CLOUD')" data-testid="cloud_conflict_use_cloud_button">
              🗑 Wipe Local Data & Continue
            </button>
            ${
              !hasExisting
                ? `<button class="btn btn-outline-accent btn-full" onclick="window.pocketActions.resolveConflict('KEEP_LOCAL')" data-testid="cloud_conflict_keep_local_button">
                     ☁ Keep & Backup Local Data to Google
                   </button>`
                : ""
            }
            <button class="btn btn-outline btn-full" onclick="window.pocketActions.cancelConflict()">
              Cancel (Keep Local Data)
            </button>
          </div>
        </div>
      </div>
    `;
  }

  const m = state.ui.modal;
  if (!m) return "";

  if (m.type === "CONFIRM_SIGN_OUT") {
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div style="font-size:19px;font-weight:800;margin-bottom:8px;">Sign Out of Google Account?</div>
          <p style="font-size:14px;color:var(--text-secondary);line-height:1.45;">
            Your wallets, transactions, and loans are safely backed up in Google Cloud. Signing out will clear cached data from this device and return you to Startup Page 1.
          </p>
          <div class="grid-2" style="margin-top:16px;">
            <button class="btn btn-outline" onclick="window.pocketActions.closeModal()">Cancel</button>
            <button class="btn btn-danger" onclick="window.pocketActions.confirmSignOutNow()">Sign Out</button>
          </div>
        </div>
      </div>
    `;
  }

  if (m.type === "TRANSACTION") {
    const activeWallets = state.wallets.filter((w) => !w.isArchived);
    const txType = m.txType || "EXPENSE";
    const cats = state.categories.filter((c) => (txType === "INCOME" ? c.type === "INCOME" : c.type === "EXPENSE") && !c.isArchived);
    const defaultWId = m.walletId || state.settings.defaultWalletId || activeWallets[0]?.id || 1;
    const defaultCatId = m.categoryId || cats[0]?.id || 1;

    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:19px;font-weight:800;">${m.editId ? "Edit Record" : "New Record"}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>

          <div class="grid-2" style="margin-bottom:12px;">
            <button class="btn btn-sm ${txType === "EXPENSE" ? "btn-danger" : "btn-outline"}" onclick="window.pocketActions.switchTxModalType('EXPENSE')">− Expense</button>
            <button class="btn btn-sm ${txType === "INCOME" ? "btn-primary" : "btn-outline"}" onclick="window.pocketActions.switchTxModalType('INCOME')">+ Income</button>
          </div>

          <div class="field-group">
            <label class="field-label">Amount (Supports inline math: e.g. 250+80*2)</label>
            <input type="text" id="tx_amount_input" class="input-field money-md" placeholder="0" value="${escapeHtml(m.amount ?? "")}" />
            <div class="math-bar">
              <button type="button" class="math-btn" onclick="window.insertMathOperator('tx_amount_input','+')">+</button>
              <button type="button" class="math-btn" onclick="window.insertMathOperator('tx_amount_input','-')">−</button>
              <button type="button" class="math-btn" onclick="window.insertMathOperator('tx_amount_input','*')">×</button>
              <button type="button" class="math-btn" onclick="window.insertMathOperator('tx_amount_input','/')">÷</button>
              <button type="button" class="math-btn" onclick="window.insertMathOperator('tx_amount_input','=')">=</button>
            </div>
          </div>

          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Wallet</label>
              <select id="tx_wallet_input" class="select-field">
                ${activeWallets.map((w) => `<option value="${w.id}" ${Number(w.id) === Number(defaultWId) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Category</label>
              <select id="tx_category_input" class="select-field">
                ${cats.map((c) => `<option value="${c.id}" ${Number(c.id) === Number(defaultCatId) ? "selected" : ""}>${escapeHtml(c.name)}</option>`).join("")}
              </select>
            </div>
          </div>

          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Date</label>
              <input type="date" id="tx_date_input" class="input-field" value="${formatDateInputValue(m.date)}" />
            </div>
            <div class="field-group">
              <label class="field-label">Reference / TrxID</label>
              <input type="text" id="tx_ref_input" class="input-field" placeholder="Optional" value="${escapeHtml(m.reference || "")}" />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">Note</label>
            <input type="text" id="tx_note_input" class="input-field" placeholder="What was this for?" value="${escapeHtml(m.note || "")}" />
          </div>

          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveTransactionFromModal()">Save Record</button>
        </div>
      </div>
    `;
  }

  if (m.type === "TRANSFER") {
    const activeWallets = state.wallets.filter((w) => !w.isArchived);
    const srcId = m.sourceWalletId || activeWallets[0]?.id || 1;
    const dstId = m.destWalletId || activeWallets[1]?.id || activeWallets[0]?.id || 1;

    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:19px;font-weight:800;">Wallet Transfer</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>

          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">From Wallet</label>
              <select id="tr_src_wallet" class="select-field">
                ${activeWallets.map((w) => `<option value="${w.id}" ${Number(w.id) === Number(srcId) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">To Wallet</label>
              <select id="tr_dst_wallet" class="select-field">
                ${activeWallets.map((w) => `<option value="${w.id}" ${Number(w.id) === Number(dstId) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}
              </select>
            </div>
          </div>

          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Transfer Amount</label>
              <input type="number" step="any" id="tr_amount" class="input-field" placeholder="0" value="${escapeHtml(m.amount || "")}" />
            </div>
            <div class="field-group">
              <label class="field-label">Transfer Fee (Optional)</label>
              <input type="number" step="any" id="tr_fee" class="input-field" placeholder="0" value="${escapeHtml(m.fee || "")}" />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">Note</label>
            <input type="text" id="tr_note" class="input-field" placeholder="e.g. Cash out / Send money" value="${escapeHtml(m.note || "")}" />
          </div>

          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveTransferFromModal()">Complete Transfer</button>
        </div>
      </div>
    `;
  }

  if (m.type === "WALLET") {
    const existing = m.walletId ? state.wallets.find((w) => Number(w.id) === Number(m.walletId)) : null;
    const name = existing ? existing.name : "";
    const type = existing ? existing.type : "CASH";
    const currency = existing ? (existing.currency || "BDT") : "BDT";
    const bal = existing ? existing.openingBalance : "";
    const includeTotal = existing ? existing.includeInTotalBalance !== false : true;

    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:19px;font-weight:800;">${existing ? "Edit Wallet" : "New Wallet"}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>

          <div class="field-group">
            <label class="field-label">Wallet Name</label>
            <input type="text" id="wal_name" class="input-field" placeholder="e.g. Bkash Personal, City Bank" value="${escapeHtml(name)}" />
          </div>

          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Type / Icon</label>
              <select id="wal_type" class="select-field">
                ${["CASH", "BANK", "MTB", "BRAC", "BKASH", "NAGAD", "ROCKET", "RAPID_PASS", "CARD", "SAVINGS"]
                  .map((t) => `<option value="${t}" ${(existing?.iconName || type) === t ? "selected" : ""}>${t}</option>`)
                  .join("")}
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Currency</label>
              <select id="wal_currency" class="select-field">
                ${["BDT", "USD", "EUR", "GBP", "INR"].map((c) => `<option value="${c}" ${currency === c ? "selected" : ""}>${c}</option>`).join("")}
              </select>
            </div>
          </div>

          <div class="field-group">
            <label class="field-label">Opening Balance</label>
            <input type="number" step="any" id="wal_opening" class="input-field" placeholder="0" value="${escapeHtml(bal)}" />
          </div>

          <label class="row-center" style="margin-bottom:14px;cursor:pointer;">
            <input type="checkbox" id="wal_include_total" ${includeTotal ? "checked" : ""} style="width:18px;height:18px;accent-color:var(--accent);" />
            <span style="font-size:13px;font-weight:600;">Include in Total Wallet Balance</span>
          </label>

          <div class="row-between">
            ${
              existing
                ? `<button class="btn btn-danger btn-sm" onclick="window.pocketActions.deleteWallet(${existing.id})">Delete</button>`
                : `<div></div>`
            }
            <button class="btn btn-primary" onclick="window.pocketActions.saveWalletFromModal(${existing ? existing.id : "null"})">Save Wallet</button>
          </div>
        </div>
      </div>
    `;
  }

  if (m.type === "LOAN") {
    const activeWallets = state.wallets.filter((w) => !w.isArchived);
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:19px;font-weight:800;">New Loan Record</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Direction</label>
            <select id="loan_dir" class="select-field">
              <option value="LENT">I Lent Money (Will Receive Back)</option>
              <option value="BORROWED">I Borrowed Money (Need to Pay)</option>
            </select>
          </div>
          <div class="field-group">
            <label class="field-label">Person Name</label>
            <input type="text" id="loan_person" class="input-field" placeholder="e.g. Tanvir" />
          </div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Principal Amount (৳)</label>
              <input type="number" step="any" id="loan_amount" class="input-field" placeholder="0" />
            </div>
            <div class="field-group">
              <label class="field-label">Wallet</label>
              <select id="loan_wallet" class="select-field">
                ${activeWallets.map((w) => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join("")}
              </select>
            </div>
          </div>
          <div class="field-group">
            <label class="field-label">Deadline Date (Optional)</label>
            <input type="date" id="loan_due" class="input-field" />
          </div>
          <label class="row-center" style="margin-bottom:14px;cursor:pointer;">
            <input type="checkbox" id="loan_disburse_now" checked style="width:18px;height:18px;accent-color:var(--accent);" />
            <span style="font-size:13px;">Update selected wallet balance today (uncheck if this is an older existing loan)</span>
          </label>
          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveLoanFromModal()">Save Loan</button>
        </div>
      </div>
    `;
  }

  if (m.type === "LOAN_REPAY") {
    const summary = calculateLoanSummaries().find((s) => Number(s.loan.id) === Number(m.loanId));
    if (!summary) return "";
    const activeWallets = state.wallets.filter((w) => !w.isArchived);
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:18px;font-weight:800;">${summary.loan.direction === "LENT" ? "Receive from" : "Pay"} ${escapeHtml(summary.loan.personName)}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Amount (Remaining: ${formatMoney(summary.outstandingPrincipal)})</label>
            <input type="number" step="any" id="repay_amount" class="input-field" value="${summary.outstandingPrincipal}" />
          </div>
          <div class="field-group">
            <label class="field-label">Wallet</label>
            <select id="repay_wallet" class="select-field">
              ${activeWallets.map((w) => `<option value="${w.id}" ${Number(w.id) === Number(summary.loan.initialWalletId) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}
            </select>
          </div>
          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveLoanRepaymentFromModal(${summary.loan.id})">Confirm Settlement</button>
        </div>
      </div>
    `;
  }

  if (m.type === "SAVINGS_GOAL") {
    const existing = m.goalId ? state.savingsGoals.find((g) => Number(g.id) === Number(m.goalId)) : null;
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:18px;font-weight:800;">${existing ? "Edit Savings Goal" : "New Savings Goal"}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Goal Title</label>
            <input type="text" id="goal_title" class="input-field" placeholder="e.g. Emergency Fund, MacBook" value="${escapeHtml(existing?.name || existing?.title || "")}" />
          </div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Target Amount (৳)</label>
              <input type="number" step="any" id="goal_target" class="input-field" placeholder="50000" value="${escapeHtml(existing?.targetAmount || "")}" />
            </div>
            <div class="field-group">
              <label class="field-label">Saved So Far (৳)</label>
              <input type="number" step="any" id="goal_saved" class="input-field" placeholder="0" value="${escapeHtml(existing?.savedAmount || 0)}" />
            </div>
          </div>
          <div class="row-between">
            ${existing ? `<button class="btn btn-danger btn-sm" onclick="window.pocketActions.deleteSavingsGoal(${existing.id})">Delete</button>` : `<div></div>`}
            <button class="btn btn-primary" onclick="window.pocketActions.saveSavingsGoalFromModal(${existing ? existing.id : "null"})">Save Goal</button>
          </div>
        </div>
      </div>
    `;
  }

  if (m.type === "RECURRING_FORM") {
    const cats = state.categories.filter((c) => !c.isArchived);
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:18px;font-weight:800;">New Recurring Rule</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Title</label>
            <input type="text" id="rec_title" class="input-field" placeholder="e.g. Home Internet, Netflix, House Rent" />
          </div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Type</label>
              <select id="rec_type" class="select-field">
                <option value="EXPENSE">Expense</option>
                <option value="INCOME">Income</option>
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Amount (৳)</label>
              <input type="number" step="any" id="rec_amount" class="input-field" placeholder="1200" />
            </div>
          </div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Category</label>
              <select id="rec_cat" class="select-field">
                ${cats.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("")}
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Next Due Date</label>
              <input type="date" id="rec_due" class="input-field" value="${formatDateInputValue(Date.now())}" />
            </div>
          </div>
          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveRecurringFromModal()">Save Recurring Rule</button>
        </div>
      </div>
    `;
  }

  if (m.type === "RECURRING_PAY") {
    const rec = state.recurringTransactions.find((r) => Number(r.id) === Number(m.recurringId));
    if (!rec) return "";
    const activeWallets = state.wallets.filter((w) => !w.isArchived);
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:18px;font-weight:800;">Confirm: ${escapeHtml(rec.title)}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Amount (৳)</label>
            <input type="number" step="any" id="rec_pay_amt" class="input-field" value="${rec.amount || ""}" />
          </div>
          <div class="field-group">
            <label class="field-label">Choose Wallet</label>
            <select id="rec_pay_wal" class="select-field">
              ${activeWallets.map((w) => `<option value="${w.id}" ${Number(w.id) === Number(rec.defaultWalletId) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}
            </select>
          </div>
          <button class="btn btn-primary btn-full" onclick="window.pocketActions.confirmRecurringPayment(${rec.id})">Log & Advance Next Due Date</button>
        </div>
      </div>
    `;
  }

  if (m.type === "CATEGORY_FORM") {
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:12px;">
            <div style="font-size:18px;font-weight:800;">New Category</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="field-group">
            <label class="field-label">Category Name</label>
            <input type="text" id="cat_name" class="input-field" placeholder="e.g. Gym, Gadgets" />
          </div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Type</label>
              <select id="cat_type" class="select-field">
                <option value="EXPENSE">Expense</option>
                <option value="INCOME">Income</option>
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Monthly Budget (Optional)</label>
              <input type="number" step="any" id="cat_budget" class="input-field" placeholder="0" />
            </div>
          </div>
          <button class="btn btn-primary btn-full" onclick="window.pocketActions.saveCategoryFromModal()">Save Category</button>
        </div>
      </div>
    `;
  }

  if (m.type === "INSPECT_ENTRY") {
    const entry = buildUnifiedLedger().find((e) => e.uid === m.uid);
    if (!entry) return "";
    const sym = currencySymbolForCode(entry.currencyCode || "BDT");
    return `
      <div class="modal-scrim" onclick="window.pocketActions.closeModal()">
        <div class="modal-card" onclick="event.stopPropagation()">
          <div class="row-between" style="margin-bottom:10px;">
            <div style="font-size:18px;font-weight:800;">${escapeHtml(entry.title)}</div>
            <button class="btn btn-outline btn-sm" onclick="window.pocketActions.closeModal()">✕</button>
          </div>
          <div class="money-lg" style="margin-bottom:10px;">${formatMoney(entry.signedAmount, sym, true, true)}</div>
          <div style="font-size:13px;color:var(--text-secondary);display:flex;flex-direction:column;gap:4px;margin-bottom:16px;">
            <div><strong>Category:</strong> ${escapeHtml(entry.categoryName)}</div>
            <div><strong>Wallet:</strong> ${escapeHtml(entry.walletName)}${entry.secondaryWalletName ? ` → ${escapeHtml(entry.secondaryWalletName)}` : ""}</div>
            <div><strong>Date:</strong> ${formatDateShort(entry.date)}</div>
            ${entry.reference ? `<div><strong>Reference:</strong> ${escapeHtml(entry.reference)}</div>` : ""}
          </div>
          <div class="row-between">
            <button class="btn btn-danger btn-sm" onclick="window.pocketActions.deleteLedgerEntry('${entry.uid}')">Delete</button>
            ${
              entry.kind === "INCOME" || entry.kind === "EXPENSE"
                ? `<div class="row-center" style="gap:8px;">
                     <button class="btn btn-outline btn-sm" onclick="window.pocketActions.duplicateTx(${entry.rawId})">Duplicate</button>
                     <button class="btn btn-primary btn-sm" onclick="window.pocketActions.editTx(${entry.rawId})">Edit</button>
                   </div>`
                : ""
            }
          </div>
        </div>
      </div>
    `;
  }

  return "";
}

function renderAppLockScreen() {
  return `
    <div class="startup-page1">
      <div class="glass-card-lg" style="width:100%;max-width:380px;">
        ${renderPocketLogo(56)}
        <h2 style="margin:12px 0 4px;">Pocket Locked</h2>
        <p style="font-size:12px;color:var(--text-secondary);margin:0 0 16px;">Enter your PIN to continue</p>
        <input type="password" id="unlock_pin_input" class="input-field" placeholder="PIN" style="text-align:center;font-size:20px;letter-spacing:4px;margin-bottom:12px;" />
        <button class="btn btn-primary btn-full" onclick="window.pocketActions.unlockWithPin()">Unlock</button>
      </div>
    </div>
  `;
}

export function render() {
  applyThemeToDom();
  const root = document.getElementById("app");
  if (!root) return;

  const bgWallpaperHtml = state.settings.customBackgroundUri
    ? `<div class="carbon-bg-layer"></div><div class="custom-wallpaper-layer" style="background-image:url('${escapeHtml(state.settings.customBackgroundUri)}');"></div>`
    : `<div class="carbon-bg-layer"></div><div class="carbon-weave-layer"></div>`;

  if (!state.settings.hasCompletedOnboarding) {
    root.innerHTML = `
      ${bgWallpaperHtml}
      ${renderStartupScreen()}
      ${renderActiveModal()}
      ${state.ui.toastMessage ? `<div class="snackbar-toast">${escapeHtml(state.ui.toastMessage)}</div>` : ""}
    `;
    return;
  }

  if (!state.ui.isAppUnlocked) {
    root.innerHTML = `
      ${bgWallpaperHtml}
      ${renderAppLockScreen()}
      ${state.ui.toastMessage ? `<div class="snackbar-toast">${escapeHtml(state.ui.toastMessage)}</div>` : ""}
    `;
    return;
  }

  let screenHtml = "";
  const scr = state.ui.currentScreen;
  if (scr === "WALLET_DETAIL") screenHtml = renderWalletDetailScreen();
  else if (scr === "HISTORY") screenHtml = renderHistoryScreen();
  else if (scr === "REPORTS") screenHtml = renderReportsScreen();
  else if (scr === "LOANS") screenHtml = renderLoansScreen();
  else if (scr === "SETTINGS") screenHtml = renderSettingsScreen();
  else if (scr === "RECURRING") screenHtml = renderRecurringScreen();
  else if (scr === "CATEGORIES") screenHtml = renderCategoriesScreen();
  else if (scr === "ADJUSTMENT") screenHtml = renderAdjustmentScreen();
  else if (scr === "BACKUP") screenHtml = renderBackupScreen();
  else screenHtml = renderBalancesHomeScreen();

  const isTopLevel = ["WALLETS", "HISTORY", "REPORTS", "LOANS"].includes(scr);
  const hasBlur = Boolean(state.ui.isFabExpanded || state.ui.modal || state.cloudAuth.pendingConflict);

  root.innerHTML = `
    ${bgWallpaperHtml}
    <div class="app-shell ${hasBlur ? "blurred" : ""}">
      ${screenHtml}
    </div>

    ${
      scr === "WALLETS"
        ? `
          <div class="fab-container">
            ${
              state.ui.isFabExpanded
                ? `<div class="fab-menu">
                     <button class="fab-action-pill" style="background:var(--income-bg);color:var(--income);" onclick="window.pocketActions.openTransactionModal('INCOME')">＋ Income</button>
                     <button class="fab-action-pill" style="background:var(--expense-bg);color:var(--expense);" onclick="window.pocketActions.openTransactionModal('EXPENSE')">－ Expense</button>
                     <button class="fab-action-pill" style="background:var(--transfer-bg);color:var(--transfer);" onclick="window.pocketActions.openTransferModal()">⇄ Transfer</button>
                     <button class="fab-action-pill" style="background:var(--loan-bg);color:var(--loan);" onclick="window.pocketActions.openLoanModal()">🤝 Loans</button>
                     <button class="fab-action-pill" style="background:var(--overlay-surface);color:var(--accent);" onclick="window.pocketActions.openScreen('RECURRING')">🔁 Recurring</button>
                   </div>`
                : ""
            }
            <button class="fab-main ${state.ui.isFabExpanded ? "open" : ""}" onclick="window.pocketActions.toggleFab()" data-testid="home_fab_record">＋</button>
          </div>
        `
        : ""
    }

    ${
      isTopLevel
        ? `
          <div class="liquid-bottom-nav-wrap">
            <nav class="liquid-bottom-nav">
              <button class="nav-tab-btn ${state.ui.activeBottomTab === "BALANCES" ? "active" : ""}" onclick="window.pocketActions.switchBottomTab('BALANCES')">
                <span style="font-size:17px;">👛</span>
                <span>Balances</span>
              </button>
              <button class="nav-tab-btn ${state.ui.activeBottomTab === "RECENT_TRANSACTIONS" ? "active" : ""}" onclick="window.pocketActions.switchBottomTab('RECENT_TRANSACTIONS')">
                <span style="font-size:17px;">🧾</span>
                <span>History</span>
              </button>
              <button class="nav-tab-btn ${state.ui.activeBottomTab === "REPORTS" ? "active" : ""}" onclick="window.pocketActions.switchBottomTab('REPORTS')">
                <span style="font-size:17px;">📊</span>
                <span>Report</span>
              </button>
              <button class="nav-tab-btn ${state.ui.activeBottomTab === "LOANS" ? "active" : ""}" onclick="window.pocketActions.switchBottomTab('LOANS')">
                <span style="font-size:17px;">🤝</span>
                <span>Loans</span>
              </button>
            </nav>
          </div>
        `
        : ""
    }

    ${renderActiveModal()}
    ${state.ui.toastMessage ? `<div class="snackbar-toast">${escapeHtml(state.ui.toastMessage)}</div>` : ""}
  `;
}

// ============================================================================
// Interactive User Actions
// ============================================================================
function nextId(list) {
  return list.reduce((max, item) => Math.max(max, Number(item.id || 0)), 0) + 1;
}

window.pocketActions = {
  signInGoogle: () => triggerGoogleSignIn(),

  continueOffline: () => {
    state.onboardingStage = "PAGE_2_SETUP";
    saveStateToStorage();
    render();
  },

  backToStartupPage1: async () => {
    if (state.cloudAuth.uid || state.cloudAuth.email) {
      if (auth) await signOut(auth).catch(() => {});
      state.cloudAuth = createInitialState().cloudAuth;
    }
    state.onboardingStage = "PAGE_1_WELCOME";
    saveStateToStorage();
    render();
  },

  updatePage2Name: (val) => {
    const clean = val.length > 0 ? val.charAt(0).toUpperCase() + val.slice(1) : "";
    state.settings.userName = clean;
  },

  toggleStarterWallet: (name) => {
    const p2 = state.ui.page2State;
    p2.selectedMap[name] = !p2.selectedMap[name];
    render();
  },

  updateStarterWalletBalance: (name, val) => {
    state.ui.page2State.balanceMap[name] = val;
  },

  toggleShowMoreStarterWallets: () => {
    state.ui.page2State.showMoreWallets = !state.ui.page2State.showMoreWallets;
    render();
  },

  setPage2LoanDir: (dir) => {
    state.ui.page2State.loanDirection = dir;
    render();
  },

  setPage2LoanField: (field, val) => {
    state.ui.page2State[field] = val;
  },

  addStarterLoan: () => {
    const p2 = state.ui.page2State;
    const person = (p2.loanPerson || "").trim();
    const amt = roundCurrency(p2.loanAmount);
    if (!person || amt <= 0) {
      showToast("Enter person name and loan amount.");
      return;
    }
    p2.starterLoans.push({
      personName: person.charAt(0).toUpperCase() + person.slice(1),
      direction: p2.loanDirection,
      principalAmount: amt,
      dueDate: p2.loanDueDate ? new Date(p2.loanDueDate).getTime() : null
    });
    p2.loanPerson = "";
    p2.loanAmount = "";
    p2.loanDueDate = "";
    render();
  },

  removeStarterLoan: (idx) => {
    state.ui.page2State.starterLoans.splice(idx, 1);
    render();
  },

  completeOnboarding: async () => {
    const p2 = state.ui.page2State;
    const chosen = STARTER_WALLETS.filter((w) => p2.selectedMap[w.name]);
    const now = Date.now();
    const createdWallets = [];

    if (chosen.length === 0) {
      // Default to Cash wallet if none selected
      const cashOpt = STARTER_WALLETS[0];
      createdWallets.push({
        id: 1,
        name: cashOpt.name,
        type: cashOpt.type,
        accountHint: "",
        openingBalance: 0,
        openingDate: now,
        currency: "BDT",
        iconName: cashOpt.iconName,
        colorHex: cashOpt.colorHex,
        sortOrder: 1,
        isArchived: false,
        includeInTotalBalance: true
      });
    } else {
      chosen.forEach((opt, idx) => {
        createdWallets.push({
          id: idx + 1,
          name: opt.name,
          type: opt.type,
          accountHint: opt.accountHint || "",
          openingBalance: roundCurrency(p2.balanceMap[opt.name] || 0),
          openingDate: now,
          currency: "BDT",
          iconName: opt.iconName,
          colorHex: opt.colorHex,
          sortOrder: opt.sortOrder || idx + 1,
          isArchived: false,
          includeInTotalBalance: opt.includeInTotalBalance
        });
      });
    }

    state.wallets = createdWallets;
    state.categories = defaultCategories();
    const fallbackWalletId = createdWallets[0].id;

    // Insert starter existing loans WITHOUT fake today-disbursement so opening wallet balances stay exact
    state.loans = p2.starterLoans.map((sl, i) => ({
      id: i + 1,
      personName: sl.personName,
      direction: sl.direction,
      principalAmount: sl.principalAmount,
      initialWalletId: fallbackWalletId,
      startDate: now,
      dueDate: sl.dueDate,
      interestRate: 0,
      interestType: "NONE",
      note: "Existing loan",
      reference: "",
      isArchived: false
    }));
    state.loanActivities = [];

    state.settings.hasCompletedOnboarding = true;
    state.settings.defaultWalletId = fallbackWalletId;
    state.ui.currentScreen = "WALLETS";
    state.ui.activeBottomTab = "BALANCES";
    saveStateToStorage();
    render();

    if (state.cloudAuth.uid) {
      await pushLocalPayloadToCloud();
    }
  },

  switchBottomTab: (tab) => {
    state.ui.activeBottomTab = tab;
    state.ui.isFabExpanded = false;
    if (tab === "BALANCES") state.ui.currentScreen = "WALLETS";
    else if (tab === "RECENT_TRANSACTIONS") state.ui.currentScreen = "HISTORY";
    else if (tab === "REPORTS") state.ui.currentScreen = "REPORTS";
    else if (tab === "LOANS") state.ui.currentScreen = "LOANS";
    render();
  },

  openScreen: (screen, walletId = null) => {
    state.ui.isFabExpanded = false;
    if (walletId !== null) state.ui.selectedWalletId = Number(walletId);
    state.ui.currentScreen = screen;
    render();
  },

  openWalletDetail: (walletId) => {
    state.ui.selectedWalletId = Number(walletId);
    state.ui.currentScreen = "WALLET_DETAIL";
    render();
  },

  shiftMonth: (delta) => {
    let m = state.ui.selectedMonth + delta;
    let y = state.ui.selectedYear;
    if (m < 0) { m = 11; y -= 1; }
    else if (m > 11) { m = 0; y += 1; }
    state.ui.selectedMonth = m;
    state.ui.selectedYear = y;
    render();
  },

  togglePrivacy: () => {
    state.settings.hideBalancesPrivacy = !state.settings.hideBalancesPrivacy;
    scheduleCloudPush();
    render();
  },

  toggleFab: () => {
    state.ui.isFabExpanded = !state.ui.isFabExpanded;
    render();
  },

  closeModal: () => {
    state.ui.modal = null;
    render();
  },

  openTransactionModal: (txType = "EXPENSE", editId = null, walletId = null) => {
    state.ui.isFabExpanded = false;
    if (editId) {
      const existing = state.transactions.find((t) => Number(t.id) === Number(editId));
      if (existing) {
        state.ui.modal = {
          type: "TRANSACTION",
          editId: existing.id,
          txType: existing.type,
          amount: existing.amount,
          walletId: existing.walletId,
          categoryId: existing.categoryId,
          date: existing.date,
          note: existing.note,
          reference: existing.reference
        };
        render();
        return;
      }
    }
    state.ui.modal = {
      type: "TRANSACTION",
      editId: null,
      txType,
      amount: "",
      walletId: walletId || state.settings.defaultWalletId || state.wallets[0]?.id,
      categoryId: null,
      date: Date.now(),
      note: "",
      reference: ""
    };
    render();
  },

  switchTxModalType: (txType) => {
    if (!state.ui.modal) return;
    state.ui.modal.txType = txType;
    state.ui.modal.amount = document.getElementById("tx_amount_input")?.value || state.ui.modal.amount;
    state.ui.modal.note = document.getElementById("tx_note_input")?.value || state.ui.modal.note;
    render();
  },

  saveTransactionFromModal: () => {
    const m = state.ui.modal;
    if (!m) return;
    const amt = evaluateMathInput(document.getElementById("tx_amount_input")?.value);
    if (amt <= 0) {
      showToast("Enter a valid amount greater than 0.");
      return;
    }
    const walletId = Number(document.getElementById("tx_wallet_input")?.value || state.wallets[0]?.id || 1);
    const categoryId = Number(document.getElementById("tx_category_input")?.value || state.categories[0]?.id || 1);
    const dateVal = document.getElementById("tx_date_input")?.value;
    const dateMillis = dateVal ? new Date(dateVal).getTime() : Date.now();
    const note = (document.getElementById("tx_note_input")?.value || "").trim();
    const reference = (document.getElementById("tx_ref_input")?.value || "").trim();

    if (m.editId) {
      const idx = state.transactions.findIndex((t) => Number(t.id) === Number(m.editId));
      if (idx !== -1) {
        state.transactions[idx] = {
          ...state.transactions[idx],
          type: m.txType,
          amount: amt,
          walletId,
          categoryId,
          date: dateMillis,
          note,
          reference
        };
      }
    } else {
      state.transactions.unshift({
        id: nextId(state.transactions),
        type: m.txType,
        amount: amt,
        walletId,
        categoryId,
        date: dateMillis,
        note,
        reference,
        tags: "",
        transferId: null,
        loanActivityId: null,
        parentTransactionId: null,
        attachmentUri: ""
      });
    }

    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Record saved.");
    render();
  },

  openTransferModal: (editId = null, sourceWalletId = null) => {
    state.ui.isFabExpanded = false;
    state.ui.modal = {
      type: "TRANSFER",
      sourceWalletId: sourceWalletId || state.wallets[0]?.id,
      destWalletId: state.wallets[1]?.id || state.wallets[0]?.id,
      amount: "",
      fee: "",
      note: ""
    };
    render();
  },

  saveTransferFromModal: () => {
    const srcId = Number(document.getElementById("tr_src_wallet")?.value);
    const dstId = Number(document.getElementById("tr_dst_wallet")?.value);
    const amt = roundCurrency(document.getElementById("tr_amount")?.value);
    const fee = roundCurrency(document.getElementById("tr_fee")?.value);
    const note = (document.getElementById("tr_note")?.value || "").trim();

    if (!srcId || !dstId || srcId === dstId) {
      showToast("Choose two different wallets for a transfer.");
      return;
    }
    if (amt <= 0) {
      showToast("Enter a transfer amount greater than 0.");
      return;
    }

    const trId = nextId(state.transfers);
    const now = Date.now();
    state.transfers.unshift({
      id: trId,
      sourceWalletId: srcId,
      destinationWalletId: dstId,
      principalAmount: amt,
      destinationAmount: amt,
      conversionRate: 1,
      feeMode: fee > 0 ? "FIXED" : "NONE",
      fixedFeeInput: fee,
      percentageInput: 0,
      feeAmount: fee,
      date: now,
      note
    });

    if (fee > 0) {
      const feeCat = state.categories.find((c) => c.isSystemFee) || state.categories.find((c) => c.type === "EXPENSE") || { id: 19 };
      state.transactions.unshift({
        id: nextId(state.transactions),
        type: "EXPENSE",
        amount: fee,
        walletId: srcId,
        categoryId: feeCat.id,
        date: now,
        note: `Transfer fee${note ? `: ${note}` : ""}`,
        reference: "",
        tags: "",
        transferId: trId,
        loanActivityId: null,
        parentTransactionId: null,
        attachmentUri: ""
      });
    }

    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Transfer completed.");
    render();
  },

  openWalletModal: (walletId = null) => {
    state.ui.modal = { type: "WALLET", walletId };
    render();
  },

  saveWalletFromModal: (existingId = null) => {
    const rawName = (document.getElementById("wal_name")?.value || "").trim();
    const type = document.getElementById("wal_type")?.value || "CASH";
    const currency = document.getElementById("wal_currency")?.value || "BDT";
    const opening = roundCurrency(document.getElementById("wal_opening")?.value || 0);
    const includeTotal = Boolean(document.getElementById("wal_include_total")?.checked);

    if (!rawName) {
      showToast("Enter a wallet name.");
      return;
    }
    const name = rawName.charAt(0).toUpperCase() + rawName.slice(1);

    if (existingId) {
      const idx = state.wallets.findIndex((w) => Number(w.id) === Number(existingId));
      if (idx !== -1) {
        state.wallets[idx] = {
          ...state.wallets[idx],
          name,
          type,
          iconName: type,
          currency,
          openingBalance: opening,
          includeInTotalBalance: includeTotal
        };
      }
    } else {
      state.wallets.push({
        id: nextId(state.wallets),
        name,
        type,
        accountHint: "",
        openingBalance: opening,
        openingDate: Date.now(),
        currency,
        iconName: type,
        colorHex: "#F15A24",
        sortOrder: state.wallets.length + 1,
        isArchived: false,
        includeInTotalBalance: includeTotal
      });
    }

    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Wallet saved.");
    render();
  },

  deleteWallet: (walletId) => {
    if (state.wallets.length <= 1) {
      showToast("Keep at least one active wallet.");
      return;
    }
    const wId = Number(walletId);
    const fallback = state.wallets.find((w) => Number(w.id) !== wId);
    if (fallback) {
      const toId = Number(fallback.id);
      state.transactions.forEach((t) => { if (Number(t.walletId) === wId) t.walletId = toId; });
      state.transfers.forEach((tr) => {
        if (Number(tr.sourceWalletId) === wId) tr.sourceWalletId = toId;
        if (Number(tr.destinationWalletId) === wId) tr.destinationWalletId = toId;
      });
      // Remove self-transfers and their fees
      const selfTrIds = new Set(state.transfers.filter((tr) => Number(tr.sourceWalletId) === Number(tr.destinationWalletId)).map((tr) => Number(tr.id)));
      state.transactions = state.transactions.filter((t) => !selfTrIds.has(Number(t.transferId)));
      state.transfers = state.transfers.filter((tr) => !selfTrIds.has(Number(tr.id)));
      state.loans.forEach((l) => { if (Number(l.initialWalletId) === wId) l.initialWalletId = toId; });
      state.loanActivities.forEach((a) => { if (Number(a.walletId) === wId) a.walletId = toId; });
      state.adjustments.forEach((adj) => { if (Number(adj.walletId) === wId) adj.walletId = toId; });
      if (Number(state.settings.defaultWalletId) === wId) state.settings.defaultWalletId = toId;
    }
    state.wallets = state.wallets.filter((w) => Number(w.id) !== wId);
    state.ui.modal = null;
    state.ui.currentScreen = "WALLETS";
    scheduleCloudPush();
    showToast("Wallet deleted.");
    render();
  },

  moveWallet: (walletId, delta) => {
    const sorted = state.wallets.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
    const idx = sorted.findIndex((w) => Number(w.id) === Number(walletId));
    const targetIdx = idx + delta;
    if (idx < 0 || targetIdx < 0 || targetIdx >= sorted.length) return;
    const tmp = sorted[idx];
    sorted[idx] = sorted[targetIdx];
    sorted[targetIdx] = tmp;
    sorted.forEach((w, i) => { w.sortOrder = i + 1; });
    state.wallets = sorted;
    scheduleCloudPush();
    render();
  },

  openLoanModal: () => {
    state.ui.isFabExpanded = false;
    state.ui.modal = { type: "LOAN" };
    render();
  },

  saveLoanFromModal: () => {
    const dir = document.getElementById("loan_dir")?.value || "LENT";
    const person = (document.getElementById("loan_person")?.value || "").trim();
    const amt = roundCurrency(document.getElementById("loan_amount")?.value);
    const walletId = Number(document.getElementById("loan_wallet")?.value || state.wallets[0]?.id || 1);
    const dueVal = document.getElementById("loan_due")?.value;
    const disburseNow = Boolean(document.getElementById("loan_disburse_now")?.checked);

    if (!person || amt <= 0) {
      showToast("Enter person name and loan amount.");
      return;
    }
    const loanId = nextId(state.loans);
    const now = Date.now();
    state.loans.unshift({
      id: loanId,
      personName: person.charAt(0).toUpperCase() + person.slice(1),
      direction: dir,
      principalAmount: amt,
      initialWalletId: walletId,
      startDate: now,
      dueDate: dueVal ? new Date(dueVal).getTime() : null,
      interestRate: 0,
      interestType: "NONE",
      note: "",
      reference: "",
      isArchived: false
    });

    if (disburseNow) {
      state.loanActivities.push({
        id: nextId(state.loanActivities),
        loanId,
        activityType: "DISBURSEMENT",
        principalPortion: amt,
        interestPortion: 0,
        walletId,
        date: now,
        note: "Initial loan disbursement"
      });
    }

    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Loan saved.");
    render();
  },

  openLoanRepaymentModal: (loanId) => {
    state.ui.modal = { type: "LOAN_REPAY", loanId };
    render();
  },

  saveLoanRepaymentFromModal: (loanId) => {
    const amt = roundCurrency(document.getElementById("repay_amount")?.value);
    const walletId = Number(document.getElementById("repay_wallet")?.value || state.wallets[0]?.id || 1);
    if (amt <= 0) {
      showToast("Enter a valid repayment amount.");
      return;
    }
    state.loanActivities.push({
      id: nextId(state.loanActivities),
      loanId: Number(loanId),
      activityType: "REPAYMENT",
      principalPortion: amt,
      interestPortion: 0,
      walletId,
      date: Date.now(),
      note: "Loan settlement"
    });
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Loan repayment recorded.");
    render();
  },

  deleteLoan: (loanId) => {
    const id = Number(loanId);
    state.loans = state.loans.filter((l) => Number(l.id) !== id);
    state.loanActivities = state.loanActivities.filter((a) => Number(a.loanId) !== id);
    scheduleCloudPush();
    showToast("Loan deleted.");
    render();
  },

  setLoanFilter: (f) => {
    state.ui.loanFilter = f;
    render();
  },

  openSavingsGoalModal: (goalId = null) => {
    state.ui.modal = { type: "SAVINGS_GOAL", goalId };
    render();
  },

  saveSavingsGoalFromModal: (existingId = null) => {
    const title = (document.getElementById("goal_title")?.value || "").trim();
    const target = roundCurrency(document.getElementById("goal_target")?.value);
    const saved = roundCurrency(document.getElementById("goal_saved")?.value);
    if (!title || target <= 0) {
      showToast("Enter goal title and target amount.");
      return;
    }
    if (existingId) {
      const idx = state.savingsGoals.findIndex((g) => Number(g.id) === Number(existingId));
      if (idx !== -1) {
        state.savingsGoals[idx] = { ...state.savingsGoals[idx], name: title, title, targetAmount: target, savedAmount: saved };
      }
    } else {
      state.savingsGoals.push({
        id: nextId(state.savingsGoals),
        name: title,
        title,
        targetAmount: target,
        savedAmount: saved,
        iconName: "SAVINGS",
        colorHex: "#F15A24",
        createdAt: Date.now()
      });
    }
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Savings goal saved.");
    render();
  },

  adjustSavingsGoal: (goalId, isAdd) => {
    const g = state.savingsGoals.find((x) => Number(x.id) === Number(goalId));
    if (!g) return;
    const raw = window.prompt(`${isAdd ? "Add to" : "Withdraw from"} "${g.name || g.title}" (৳):`, "500");
    if (raw === null) return;
    const amt = roundCurrency(raw);
    if (amt <= 0) return;
    g.savedAmount = roundCurrency(Math.max(0, Number(g.savedAmount || 0) + (isAdd ? amt : -amt)));
    scheduleCloudPush();
    render();
  },

  deleteSavingsGoal: (goalId) => {
    state.savingsGoals = state.savingsGoals.filter((g) => Number(g.id) !== Number(goalId));
    state.ui.modal = null;
    scheduleCloudPush();
    render();
  },

  openRecurringFormModal: () => {
    state.ui.modal = { type: "RECURRING_FORM" };
    render();
  },

  saveRecurringFromModal: () => {
    const title = (document.getElementById("rec_title")?.value || "").trim();
    const type = document.getElementById("rec_type")?.value || "EXPENSE";
    const amt = roundCurrency(document.getElementById("rec_amount")?.value);
    const catId = Number(document.getElementById("rec_cat")?.value || state.categories[0]?.id || 1);
    const dueVal = document.getElementById("rec_due")?.value;
    if (!title) {
      showToast("Enter a title for the recurring rule.");
      return;
    }
    const nextDue = dueVal ? new Date(dueVal).getTime() : Date.now();
    state.recurringTransactions.push({
      id: nextId(state.recurringTransactions),
      title,
      type,
      amountMode: amt > 0 ? "FIXED" : "MANUAL",
      amount: amt,
      categoryId: catId,
      defaultWalletId: state.wallets[0]?.id || null,
      frequency: "MONTHLY",
      dayOfMonth: new Date(nextDue).getDate(),
      customIntervalDays: 30,
      nextDueDate: nextDue,
      lastPaidDate: null,
      note: "",
      isActive: true,
      reminderEnabled: true
    });
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Recurring rule added.");
    render();
  },

  openRecurringPayModal: (recurringId) => {
    state.ui.modal = { type: "RECURRING_PAY", recurringId };
    render();
  },

  confirmRecurringPayment: (recurringId) => {
    const rec = state.recurringTransactions.find((r) => Number(r.id) === Number(recurringId));
    if (!rec) return;
    const amt = roundCurrency(document.getElementById("rec_pay_amt")?.value || rec.amount);
    const walId = Number(document.getElementById("rec_pay_wal")?.value || state.wallets[0]?.id || 1);
    if (amt <= 0) {
      showToast("Enter an amount greater than 0.");
      return;
    }
    const now = Date.now();
    state.transactions.unshift({
      id: nextId(state.transactions),
      type: rec.type,
      amount: amt,
      walletId: walId,
      categoryId: rec.categoryId,
      date: now,
      note: rec.title,
      reference: "",
      tags: "recurring",
      transferId: null,
      loanActivityId: null,
      parentTransactionId: null,
      attachmentUri: ""
    });
    const nextDate = new Date(Number(rec.nextDueDate || now));
    nextDate.setMonth(nextDate.getMonth() + 1);
    rec.lastPaidDate = now;
    rec.nextDueDate = nextDate.getTime();
    state.ui.modal = null;
    scheduleCloudPush();
    showToast(`Logged ${rec.title}.`);
    render();
  },

  snoozeRecurring: (recurringId) => {
    const rec = state.recurringTransactions.find((r) => Number(r.id) === Number(recurringId));
    if (!rec) return;
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(9, 0, 0, 0);
    rec.nextDueDate = tomorrow.getTime();
    scheduleCloudPush();
    showToast("Snoozed until tomorrow.");
    render();
  },

  deleteRecurring: (id) => {
    state.recurringTransactions = state.recurringTransactions.filter((r) => Number(r.id) !== Number(id));
    scheduleCloudPush();
    render();
  },

  openCategoryModal: () => {
    state.ui.modal = { type: "CATEGORY_FORM" };
    render();
  },

  saveCategoryFromModal: () => {
    const name = (document.getElementById("cat_name")?.value || "").trim();
    const type = document.getElementById("cat_type")?.value || "EXPENSE";
    const budget = roundCurrency(document.getElementById("cat_budget")?.value || 0);
    if (!name) {
      showToast("Enter category name.");
      return;
    }
    state.categories.push({
      id: nextId(state.categories),
      name: name.charAt(0).toUpperCase() + name.slice(1),
      type,
      iconName: "CATEGORY",
      colorHex: type === "INCOME" ? "#15803D" : "#F15A24",
      sortOrder: state.categories.length + 1,
      isArchived: false,
      isSystemFee: false,
      monthlyBudget: budget
    });
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Category added.");
    render();
  },

  deleteCategory: (catId) => {
    const id = Number(catId);
    const fallback = state.categories.find((c) => Number(c.id) !== id);
    if (fallback) {
      state.transactions.forEach((t) => { if (Number(t.categoryId) === id) t.categoryId = fallback.id; });
      state.recurringTransactions.forEach((r) => { if (Number(r.categoryId) === id) r.categoryId = fallback.id; });
    }
    state.categories = state.categories.filter((c) => Number(c.id) !== id);
    scheduleCloudPush();
    render();
  },

  selectAdjustmentWallet: (val) => {
    state.ui.selectedWalletId = Number(val);
    render();
  },

  submitBalanceAdjustment: () => {
    const walId = Number(document.getElementById("adj_wallet_id")?.value || state.ui.selectedWalletId || state.wallets[0]?.id);
    const wallet = state.wallets.find((w) => Number(w.id) === walId);
    if (!wallet) return;
    const summary = calculateWalletSummary(wallet);
    const actual = roundCurrency(document.getElementById("adj_actual_input")?.value);
    const reason = (document.getElementById("adj_reason_input")?.value || "Balance reconciliation").trim();
    const diff = roundCurrency(actual - summary.currentBalance);
    if (Math.abs(diff) < 0.005) {
      showToast("Actual balance already matches calculated balance.");
      return;
    }
    state.adjustments.push({
      id: nextId(state.adjustments),
      walletId: walId,
      actualBalance: actual,
      calculatedBalanceBefore: summary.currentBalance,
      difference: diff,
      date: Date.now(),
      reason,
      note: "",
      isReversed: false
    });
    state.ui.currentScreen = "WALLETS";
    scheduleCloudPush();
    showToast("Wallet balance reconciled.");
    render();
  },

  inspectLedgerEntry: (uid) => {
    state.ui.modal = { type: "INSPECT_ENTRY", uid };
    render();
  },

  editTx: (txId) => {
    window.pocketActions.openTransactionModal("EXPENSE", txId);
  },

  duplicateTx: (txId) => {
    const orig = state.transactions.find((t) => Number(t.id) === Number(txId));
    if (!orig) return;
    state.transactions.unshift({
      ...orig,
      id: nextId(state.transactions),
      date: Date.now(),
      transferId: null,
      loanActivityId: null,
      parentTransactionId: null
    });
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Transaction duplicated.");
    render();
  },

  deleteLedgerEntry: (uid) => {
    if (uid.startsWith("TX_")) {
      const id = Number(uid.replace("TX_", ""));
      const tx = state.transactions.find((t) => Number(t.id) === id);
      if (tx?.transferId) {
        const trId = Number(tx.transferId);
        state.transfers = state.transfers.filter((tr) => Number(tr.id) !== trId);
        state.transactions = state.transactions.filter((t) => Number(t.transferId) !== trId);
      } else {
        state.transactions = state.transactions.filter((t) => Number(t.id) !== id);
      }
    } else if (uid.startsWith("TR_")) {
      const id = Number(uid.replace("TR_", ""));
      state.transfers = state.transfers.filter((tr) => Number(tr.id) !== id);
      state.transactions = state.transactions.filter((t) => Number(t.transferId) !== id);
    } else if (uid.startsWith("LA_")) {
      const id = Number(uid.replace("LA_", ""));
      state.loanActivities = state.loanActivities.filter((a) => Number(a.id) !== id);
    } else if (uid.startsWith("ADJ_")) {
      const id = Number(uid.replace("ADJ_", ""));
      state.adjustments = state.adjustments.filter((a) => Number(a.id) !== id);
    }
    state.ui.modal = null;
    scheduleCloudPush();
    showToast("Record deleted.");
    render();
  },

  openHistoryWithFilter: (type) => {
    state.ui.historyFilterType = type;
    state.ui.activeBottomTab = "RECENT_TRANSACTIONS";
    state.ui.currentScreen = "HISTORY";
    render();
  },

  setHistorySearch: (val) => {
    state.ui.historySearch = val;
    render();
  },

  setHistoryTypeFilter: (type) => {
    state.ui.historyFilterType = type;
    render();
  },

  setReportCurrency: (curr) => {
    state.ui.selectedReportCurrency = curr;
    render();
  },

  exportMonthlyCsv: () => {
    const entries = buildUnifiedLedger();
    const rows = [["Date", "Type", "Category", "Wallet", "Amount", "Note", "Reference"]];
    for (const e of entries) {
      rows.push([
        formatDateShort(e.date),
        e.kind,
        e.categoryName,
        e.walletName,
        String(e.signedAmount),
        `"${String(e.note || "").replace(/"/g, '""')}"`,
        `"${String(e.reference || "").replace(/"/g, '""')}"`
      ]);
    }
    const csv = rows.map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `Pocket_Statement_${state.ui.selectedYear}_${state.ui.selectedMonth + 1}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  },

  updateUserName: (val) => {
    state.settings.userName = (val || "").trim();
    scheduleCloudPush();
    showToast("Profile name updated.");
  },

  uploadProfilePhoto: (ev) => {
    const file = ev.target?.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      state.settings.profilePhotoUri = String(reader.result || "");
      scheduleCloudPush();
      render();
    };
    reader.readAsDataURL(file);
  },

  uploadWallpaper: (ev) => {
    const file = ev.target?.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      state.settings.customBackgroundUri = String(reader.result || "");
      saveStateToStorage();
      render();
    };
    reader.readAsDataURL(file);
  },

  clearWallpaper: () => {
    state.settings.customBackgroundUri = "";
    saveStateToStorage();
    render();
  },

  setThemeMode: (mode) => {
    state.settings.themeMode = mode;
    scheduleCloudPush();
    render();
  },

  setAccentColor: (hex) => {
    state.settings.accentColorHex = hex;
    scheduleCloudPush();
    render();
  },

  setBackgroundOpacity: (pctVal) => {
    const op = Math.min(1, Math.max(0, Number(pctVal) / 100));
    state.settings.backgroundOpacity = op;
    document.documentElement.style.setProperty("--bg-opacity", String(op));
    scheduleCloudPush();
  },

  setMasterBudget: (val) => {
    state.settings.masterMonthlyBudget = Math.max(0, roundCurrency(val));
    scheduleCloudPush();
    showToast("Master monthly budget updated.");
    render();
  },

  setAppLockPin: (val) => {
    const clean = (val || "").trim();
    state.settings.appLockPin = clean;
    state.settings.appLockEnabled = clean.length > 0;
    saveStateToStorage();
    showToast(clean ? "App Lock PIN enabled." : "App Lock PIN disabled.");
  },

  unlockWithPin: () => {
    const entered = document.getElementById("unlock_pin_input")?.value || "";
    if (entered === state.settings.appLockPin) {
      state.ui.isAppUnlocked = true;
      render();
    } else {
      showToast("Incorrect PIN.");
    }
  },

  syncNow: () => pushLocalPayloadToCloud(),

  confirmSignOutModal: () => {
    state.ui.modal = { type: "CONFIRM_SIGN_OUT" };
    render();
  },

  confirmSignOutNow: () => {
    state.ui.modal = null;
    signOutGoogleAccountFromApp();
  },

  resolveConflict: (choice) => resolveCloudConflict(choice),

  cancelConflict: () => dismissCloudConflict(),

  resetAllNumericalData: () => {
    if (!window.confirm("Reset all income, expenses, loans, transfers, and wallet balances to 0?")) return;
    state.transactions = [];
    state.transfers = [];
    state.loans = [];
    state.loanActivities = [];
    state.adjustments = [];
    state.wallets.forEach((w) => { w.openingBalance = 0; });
    scheduleCloudPush();
    showToast("All numerical balances and records reset to 0.");
    render();
  },

  downloadJsonBackup: () => {
    const payload = {
      formatVersion: "pocket-backup-v1",
      exportedAtMillis: Date.now(),
      wallets: state.wallets,
      categories: state.categories,
      transactions: state.transactions,
      transfers: state.transfers,
      loans: state.loans,
      loanActivities: state.loanActivities,
      adjustments: state.adjustments,
      recurringTransactions: state.recurringTransactions,
      savingsGoals: state.savingsGoals,
      settings: state.settings
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `pocket_backup_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  },

  restoreJsonBackup: (ev) => {
    const file = ev.target?.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result || "{}"));
        if (!Array.isArray(parsed.wallets)) throw new Error("Invalid Pocket backup file.");
        state.wallets = parsed.wallets;
        state.categories = Array.isArray(parsed.categories) && parsed.categories.length > 0 ? parsed.categories : defaultCategories();
        state.transactions = Array.isArray(parsed.transactions) ? parsed.transactions : [];
        state.transfers = Array.isArray(parsed.transfers) ? parsed.transfers : [];
        state.loans = Array.isArray(parsed.loans) ? parsed.loans : [];
        state.loanActivities = Array.isArray(parsed.loanActivities) ? parsed.loanActivities : [];
        state.adjustments = Array.isArray(parsed.adjustments) ? parsed.adjustments : [];
        state.recurringTransactions = Array.isArray(parsed.recurringTransactions) ? parsed.recurringTransactions : [];
        state.savingsGoals = Array.isArray(parsed.savingsGoals) ? parsed.savingsGoals : [];
        state.settings = { ...state.settings, ...(parsed.settings || {}), hasCompletedOnboarding: true };
        state.ui.currentScreen = "WALLETS";
        scheduleCloudPush();
        showToast(`Restored ${state.wallets.length} wallets & ${state.transactions.length} transactions.`);
        render();
      } catch (err) {
        showToast("Could not restore backup: " + (err.message || "invalid JSON"));
      }
    };
    reader.readAsText(file);
  }
};

// ============================================================================
// Initial Boot & Firebase Redirect / Session Listener
// ============================================================================
render();

if (auth) {
  getRedirectResult(auth)
    .then((cred) => {
      if (cred && cred.user) {
        handleGoogleUserSignedIn(cred.user, false);
      }
    })
    .catch(() => {});

  onAuthStateChanged(auth, (user) => {
    if (user && !state.cloudAuth.uid) {
      handleGoogleUserSignedIn(user, true);
    }
  });
}
