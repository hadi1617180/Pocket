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

let fbApp, auth, db;
try {
  fbApp = initializeApp(FIREBASE_CONFIG);
  auth = getAuth(fbApp);
  db = getFirestore(fbApp, FIRESTORE_DATABASE_ID);
} catch (err) {
  console.warn("Firebase init fallback:", err);
}

const STORAGE_KEY = "pocket_ios_state_v2";

const WALLET_ICONS = {
  CASH: "assets/wallet_icon_cash.png",
  BANK: "assets/wallet_icon_bank.png",
  BKASH: "assets/wallet_icon_bkash.png",
  NAGAD: "assets/wallet_icon_nagad.png",
  ROCKET: "assets/wallet_icon_rocket.png",
  BRAC: "assets/wallet_icon_brac.png",
  MTB: "assets/wallet_icon_mtb.png",
  CARD: "assets/wallet_icon_card.png",
  RAPID_PASS: "assets/wallet_icon_rapid_pass.png",
  SAVINGS: "assets/wallet_icon_savings.png"
};

const DEFAULT_CATEGORIES = [
  { id: 1, name: "Food & Dining", type: "EXPENSE", iconName: "RESTAURANT", colorHex: "#F15A24", sortOrder: 1, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 2, name: "Bazar & Groceries", type: "EXPENSE", iconName: "CART", colorHex: "#22C55E", sortOrder: 2, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 3, name: "Transport", type: "EXPENSE", iconName: "CAR", colorHex: "#3B82F6", sortOrder: 3, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 4, name: "Bills & Utilities", type: "EXPENSE", iconName: "BOLT", colorHex: "#F59E0B", sortOrder: 4, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 5, name: "Shopping", type: "EXPENSE", iconName: "BAG", colorHex: "#EC4899", sortOrder: 5, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 6, name: "Service Charge / Fee", type: "EXPENSE", iconName: "FEE", colorHex: "#94A3B8", sortOrder: 6, isArchived: false, isSystemFee: true, monthlyBudget: 0 },
  { id: 7, name: "Salary", type: "INCOME", iconName: "WORK", colorHex: "#10B981", sortOrder: 7, isArchived: false, isSystemFee: false, monthlyBudget: 0 },
  { id: 8, name: "Business / Freelance", type: "INCOME", iconName: "TREND", colorHex: "#06B6D4", sortOrder: 8, isArchived: false, isSystemFee: false, monthlyBudget: 0 }
];

function createInitialState() {
  return {
    hasCompletedOnboarding: false,
    user: null,
    settings: {
      displayName: "Pocket User",
      email: "",
      photoUrl: "",
      currencyCode: "BDT",
      currencySymbol: "৳",
      themeMode: "DARK",
      accentColorHex: "#F15A24",
      masterMonthlyBudget: 0,
      hideBalancesPrivacy: false,
      warnOnNegativeBalance: true,
      pinCode: "",
      lastBackgroundTimestamp: 0
    },
    wallets: [],
    categories: [...DEFAULT_CATEGORIES],
    transactions: [],
    transfers: [],
    loans: [],
    loanActivities: [],
    savingsGoals: [],
    recurringTransactions: [],
    ui: {
      activeTab: "HOME",
      reportCurrency: "BDT",
      historyFilterType: "ALL",
      historySearch: "",
      historyStartDate: "",
      historyEndDate: "",
      historyMonthFilter: "",
      loanGroupMode: "LIST",
      loanSearch: "",
      selectedWalletId: null,
      walletSearch: "",
      isLocked: false
    }
  };
}

let state = loadLocalState();

function loadLocalState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createInitialState();
    const parsed = JSON.parse(raw);
    return {
      ...createInitialState(),
      ...parsed,
      settings: { ...createInitialState().settings, ...(parsed.settings || {}) },
      ui: { ...createInitialState().ui, isLocked: Boolean(parsed.settings?.pinCode) }
    };
  } catch (e) {
    return createInitialState();
  }
}

function saveLocalState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    console.warn("Save state error:", e);
  }
}

// 30-second PIN Lock Grace Period when switching apps (e.g. copying MFS SMS)
document.addEventListener("visibilitychange", () => {
  if (!state.settings.pinCode) return;
  if (document.visibilityState === "hidden") {
    state.settings.lastBackgroundTimestamp = Date.now();
    saveLocalState();
  } else if (document.visibilityState === "visible") {
    const elapsed = Date.now() - (state.settings.lastBackgroundTimestamp || 0);
    if (elapsed > 30000) {
      state.ui.isLocked = true;
      render();
    }
  }
});

function applyThemeAndAccent() {
  const accent = state.settings.accentColorHex || "#F15A24";
  document.documentElement.style.setProperty("--accent", accent);
  document.documentElement.style.setProperty("--accent-soft", accent + "28");

  const mode = state.settings.themeMode || "DARK";
  const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
  const isLight = mode === "LIGHT" || (mode === "SYSTEM" && prefersLight);
  document.body.classList.toggle("theme-light", isLight);
}

function formatMoney(amount, currencyCode) {
  if (state.settings.hideBalancesPrivacy) {
    const sym = currencySymbolFor(currencyCode || state.settings.currencyCode);
    return `${sym} ••••`;
  }
  const num = Number(amount || 0);
  const sym = currencySymbolFor(currencyCode || state.settings.currencyCode);
  return `${sym}${num.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

function currencySymbolFor(code) {
  const map = { BDT: "৳", USD: "$", EUR: "€", GBP: "£", INR: "₹" };
  return map[code] || state.settings.currencySymbol || "৳";
}

function getWalletBalance(walletId) {
  const w = state.wallets.find((x) => Number(x.id) === Number(walletId));
  if (!w) return 0;
  let bal = Number(w.openingBalance || 0);
  for (const tx of state.transactions) {
    if (Number(tx.walletId) === Number(walletId)) {
      if (tx.type === "INCOME" || tx.type === "EXPENSE_REVERSAL") bal += Number(tx.amount || 0);
      else if (tx.type === "EXPENSE") bal -= Number(tx.amount || 0);
    }
  }
  for (const tr of state.transfers) {
    if (Number(tr.sourceWalletId) === Number(walletId)) {
      bal -= Number(tr.principalAmount || 0);
    }
    if (Number(tr.destinationWalletId) === Number(walletId)) {
      bal += Number(tr.destinationAmount || tr.principalAmount || 0);
    }
  }
  return bal;
}

// Inline Math Evaluator (+, -, *, /)
function evalMathExpression(expr) {
  if (typeof expr !== "string") return Number(expr) || 0;
  const cleaned = expr.replace(/[^0-9.+\-*/]/g, "");
  if (!cleaned) return 0;
  try {
    const tokens = cleaned.match(/(\d+\.?\d*|\.\d+|[+\-*/])/g);
    if (!tokens) return 0;
    let total = parseFloat(tokens[0]) || 0;
    for (let i = 1; i < tokens.length; i += 2) {
      const op = tokens[i];
      const next = parseFloat(tokens[i + 1]);
      if (isNaN(next)) break;
      if (op === "+") total += next;
      else if (op === "-") total -= next;
      else if (op === "*") total *= next;
      else if (op === "/") total = next !== 0 ? total / next : total;
    }
    return Math.round(total * 100) / 100;
  } catch {
    return parseFloat(cleaned) || 0;
  }
}

// Insert operator and guarantee cursor stays AFTER the symbol
window.insertMathSymbol = function (inputId, symbol) {
  const input = document.getElementById(inputId);
  if (!input) return;
  input.focus();
  const start = input.selectionStart ?? input.value.length;
  const end = input.selectionEnd ?? input.value.length;
  const val = input.value;
  input.value = val.slice(0, start) + symbol + val.slice(end);
  const newPos = start + symbol.length;
  requestAnimationFrame(() => {
    input.focus();
    input.setSelectionRange(newPos, newPos);
  });
};

// Cloud Sync with Android Firestore Database
async function pullAllFromFirestore(user) {
  if (!db || !user) return;
  const uid = user.uid;
  try {
    const profSnap = await getDoc(doc(db, "users", uid));
    if (profSnap.exists()) {
      const d = profSnap.data();
      state.settings = {
        ...state.settings,
        displayName: d.displayName || user.displayName || "Pocket User",
        email: d.email || user.email || "",
        photoUrl: d.photoUrl || user.photoURL || "",
        currencyCode: d.currencyCode || "BDT",
        currencySymbol: d.currencySymbol || "৳",
        themeMode: d.themeMode || "DARK",
        accentColorHex: d.accentColorHex || "#F15A24",
        masterMonthlyBudget: Number(d.masterMonthlyBudget || 0),
        hideBalancesPrivacy: Boolean(d.hideBalancesPrivacy),
        warnOnNegativeBalance: d.warnOnNegativeBalance !== false
      };
      state.hasCompletedOnboarding = true;
    }

    const fetchSub = async (subName) => {
      const snap = await getDocs(collection(db, "users", uid, subName));
      return snap.docs.map((docSnap) => {
        const data = docSnap.data();
        return { ...data, id: Number(data.entityId || docSnap.id) };
      });
    };

    const [wallets, categories, transactions, transfers, loans, loanActivities, savingsGoals, recurring] =
      await Promise.all([
        fetchSub("wallets"),
        fetchSub("categories"),
        fetchSub("transactions"),
        fetchSub("transfers"),
        fetchSub("loans"),
        fetchSub("loan_activities"),
        fetchSub("savings_goals"),
        fetchSub("recurring_transactions")
      ]);

    state.wallets = wallets.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
    if (categories.length > 0) state.categories = categories;
    state.transactions = transactions.sort((a, b) => (b.date || 0) - (a.date || 0));
    state.transfers = transfers.sort((a, b) => (b.date || 0) - (a.date || 0));
    state.loans = loans.sort((a, b) => (b.startDate || 0) - (a.startDate || 0));
    state.loanActivities = loanActivities;
    state.savingsGoals = savingsGoals;
    state.recurringTransactions = recurring;

    saveLocalState();
    applyThemeAndAccent();
    render();
  } catch (err) {
    console.warn("Firestore pull warning:", err);
  }
}

async function syncEntityToCloud(subName, entityId, payload) {
  if (!db || !state.user) return;
  const uid = state.user.uid;
  const docRef = doc(db, "users", uid, subName, String(entityId));
  try {
    const existing = await getDoc(docRef);
    const createdAt = existing.exists() && existing.data().createdAt ? existing.data().createdAt : serverTimestamp();
    await setDoc(docRef, {
      ...payload,
      userId: uid,
      entityId: Number(entityId),
      createdAt,
      updatedAt: serverTimestamp()
    });
  } catch (err) {
    console.warn(`Cloud sync (${subName}) warning:`, err);
  }
}

async function syncProfileToCloud() {
  if (!db || !state.user) return;
  const uid = state.user.uid;
  const docRef = doc(db, "users", uid);
  try {
    const existing = await getDoc(docRef);
    const createdAt = existing.exists() && existing.data().createdAt ? existing.data().createdAt : serverTimestamp();
    await setDoc(docRef, {
      userId: uid,
      displayName: (state.settings.displayName || "Pocket User").slice(0, 100),
      email: (state.settings.email || "").slice(0, 200),
      photoUrl: (state.settings.photoUrl || "").slice(0, 1000),
      currencyCode: state.settings.currencyCode || "BDT",
      currencySymbol: state.settings.currencySymbol || "৳",
      themeMode: state.settings.themeMode || "DARK",
      warnOnNegativeBalance: Boolean(state.settings.warnOnNegativeBalance),
      loanDeadlineNotificationEnabled: true,
      dailyExpenseReminderEnabled: true,
      dailyReminderHour: 20,
      dailyReminderMinute: 0,
      backgroundOpacity: 0.85,
      accentColorHex: state.settings.accentColorHex || "#F15A24",
      masterMonthlyBudget: Number(state.settings.masterMonthlyBudget || 0),
      hideBalancesPrivacy: Boolean(state.settings.hideBalancesPrivacy),
      hasCompletedOnboarding: true,
      syncVersion: 1,
      createdAt,
      updatedAt: serverTimestamp()
    });
  } catch (err) {
    console.warn("Profile cloud sync warning:", err);
  }
}

// Auth Listener
if (auth) {
  getRedirectResult(auth).catch(() => {});
  onAuthStateChanged(auth, async (user) => {
    if (user) {
      state.user = {
        uid: user.uid,
        displayName: user.displayName || "Pocket User",
        email: user.email || "",
        photoURL: user.photoURL || ""
      };
      state.settings.displayName = state.user.displayName;
      state.settings.email = state.user.email;
      state.settings.photoUrl = state.user.photoURL;
      await pullAllFromFirestore(user);
    } else {
      state.user = null;
    }
    saveLocalState();
    render();
  });
}

window.signInWithGoogleAccount = async function () {
  if (!auth) {
    alert("Firebase Auth is not initialized.");
    return;
  }
  const hasOfflineData =
    !state.user &&
    (state.wallets.length > 0 || state.transactions.length > 0 || state.loans.length > 0);
  if (hasOfflineData) {
    const confirmed = confirm(
      "Warning: Signing in with your Google Account will replace all local offline data on this device forever. Continue?"
    );
    if (!confirmed) return;
  }
  const provider = new GoogleAuthProvider();
  try {
    await signInWithPopup(auth, provider);
  } catch (e) {
    await signInWithRedirect(auth, provider);
  }
};

window.signOutGoogleAccount = async function () {
  if (!auth) return;
  await signOut(auth);
  state.user = null;
  saveLocalState();
  render();
};

// Startup Onboarding Completion (only creates user-selected wallets, never forces MTB/Brac/Card/Savings)
window.completeStartupSetup = async function () {
  const cashChecked = document.getElementById("onboard-cash")?.checked;
  const bkashChecked = document.getElementById("onboard-bkash")?.checked;
  const nagadChecked = document.getElementById("onboard-nagad")?.checked;
  const bankChecked = document.getElementById("onboard-bank")?.checked;

  const chosen = [];
  const now = Date.now();
  if (cashChecked) {
    chosen.push({ id: now + 1, name: "Cash", type: "CASH", accountHint: "", openingBalance: 0, openingDate: now, currency: "BDT", iconName: "CASH", colorHex: "#22C55E", sortOrder: 1, isArchived: false, includeInTotalBalance: true });
  }
  if (bkashChecked) {
    chosen.push({ id: now + 2, name: "bKash", type: "MFS", accountHint: "", openingBalance: 0, openingDate: now, currency: "BDT", iconName: "BKASH", colorHex: "#E2136E", sortOrder: 2, isArchived: false, includeInTotalBalance: true });
  }
  if (nagadChecked) {
    chosen.push({ id: now + 3, name: "Nagad", type: "MFS", accountHint: "", openingBalance: 0, openingDate: now, currency: "BDT", iconName: "NAGAD", colorHex: "#F15A24", sortOrder: 3, isArchived: false, includeInTotalBalance: true });
  }
  if (bankChecked) {
    chosen.push({ id: now + 4, name: "Bank Account", type: "BANK", accountHint: "", openingBalance: 0, openingDate: now, currency: "BDT", iconName: "BANK", colorHex: "#3B82F6", sortOrder: 4, isArchived: false, includeInTotalBalance: true });
  }

  state.wallets = chosen;
  state.hasCompletedOnboarding = true;
  saveLocalState();
  for (const w of chosen) {
    await syncEntityToCloud("wallets", w.id, w);
  }
  await syncProfileToCloud();
  render();
};

// Image File -> Compressed Base64 Data URI
function readCompressedImage(file, maxSize = 600, quality = 0.72) {
  return new Promise((resolve) => {
    if (!file) return resolve("");
    const reader = new FileReader();
    reader.onload = (ev) => {
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      img.onerror = () => resolve("");
      img.src = ev.target.result;
    };
    reader.readAsDataURL(file);
  });
}

// Offline MFS / Bank SMS Paste Parser (100% Offline, No SMS Permission Needed)
window.parsePastedSms = function () {
  const raw = document.getElementById("sms-paste-input")?.value || "";
  if (!raw.trim()) return;
  const amountMatch = raw.match(/(?:Tk|BDT|৳)\s*([0-9,]+(?:\.[0-9]{1,2})?)/i) || raw.match(/([0-9,]+(?:\.[0-9]{2}))/);
  const feeMatch = raw.match(/(?:Fee|Charge)\s*(?:Tk|BDT|৳)?\s*([0-9,]+(?:\.[0-9]{1,2})?)/i);
  const trxMatch = raw.match(/(?:TrxID|TxnId|Ref)\s*[:#]?\s*([A-Z0-9]+)/i);

  const isIncome = /received|cash\s*in|credited|deposit/i.test(raw);
  const amount = amountMatch ? parseFloat(amountMatch[1].replace(/,/g, "")) : 0;
  const fee = feeMatch ? parseFloat(feeMatch[1].replace(/,/g, "")) : 0;
  const trxId = trxMatch ? trxMatch[1] : "";

  const amtInput = document.getElementById("tx-amount");
  const feeInput = document.getElementById("tx-fee");
  const noteInput = document.getElementById("tx-note");
  const typeSelect = document.getElementById("tx-type");
  if (amtInput && amount > 0) amtInput.value = String(amount);
  if (feeInput && fee > 0) feeInput.value = String(fee);
  if (typeSelect) typeSelect.value = isIncome ? "INCOME" : "EXPENSE";
  if (noteInput) noteInput.value = trxId ? `SMS (${trxId}): ${raw.slice(0, 60)}` : raw.slice(0, 80);
};

// Modals Management
let activeModalHtml = "";
window.closeModal = function () {
  activeModalHtml = "";
  renderModalContainer();
};

function openModal(html) {
  activeModalHtml = html;
  renderModalContainer();
}

function renderModalContainer() {
  const container = document.getElementById("modal-root");
  if (!container) return;
  if (!activeModalHtml) {
    container.innerHTML = "";
    return;
  }
  container.innerHTML = `
    <div class="modal-backdrop" onclick="if(event.target===this) closeModal()">
      <div class="modal-card">
        <button class="modal-close" onclick="closeModal()" aria-label="Close">✕</button>
        ${activeModalHtml}
      </div>
    </div>
  `;
}

// Developer Popup ("App developed by Abdullahil Hadi")
window.openDeveloperModal = function () {
  openModal(`
    <div style="text-align:center; padding-top:6px;">
      <img src="assets/developer_hadi_photo.jpg" alt="Abdullahil Hadi" class="dev-photo-frame" />
      <h3 style="margin:0 0 4px; font-size:19px;">Abdullahil Hadi</h3>
      <p style="margin:0 0 16px; font-size:13px; color:var(--text-muted);">App Developer & Creator of Pocket</p>
    </div>
    <a class="contact-link-row" href="tel:+8801991617180">
      <span class="contact-icon">📞</span>
      <div>
        <div class="label-sm">Phone</div>
        <div>+8801991617180</div>
      </div>
    </a>
    <a class="contact-link-row" href="https://wa.me/8801991617180" target="_blank" rel="noopener">
      <span class="contact-icon">💬</span>
      <div>
        <div class="label-sm">WhatsApp</div>
        <div>+8801991617180</div>
      </div>
    </a>
    <a class="contact-link-row" href="mailto:hadi280994@gmail.com">
      <span class="contact-icon">✉️</span>
      <div>
        <div class="label-sm">Email</div>
        <div>hadi280994@gmail.com</div>
      </div>
    </a>
    <a class="contact-link-row" href="http://fb.me/hadi280994" target="_blank" rel="noopener">
      <span class="contact-icon">🌐</span>
      <div>
        <div class="label-sm">Facebook</div>
        <div>Abdullahil Hadi</div>
      </div>
    </a>
  `);
};

// Fullscreen Attachment Viewer
window.viewAttachmentFull = function (uri) {
  if (!uri) return;
  openModal(`
    <h3 style="margin-top:0;">Attached Slip / Receipt</h3>
    <img src="${uri}" alt="Attachment" style="width:100%; border-radius:14px; max-height:70vh; object-fit:contain;" />
  `);
};

// Add / Edit Wallet Modal (with Credit Card Limit, Statement Date, Payment Due Date)
window.openAddWalletModal = function () {
  openModal(`
    <h3 style="margin-top:0;">Add Wallet or Card</h3>
    <div class="form-group">
      <label>Wallet Name</label>
      <input id="w-name" class="input" placeholder="e.g. Cash, bKash, City AMEX" />
    </div>
    <div class="grid-2">
      <div class="form-group">
        <label>Type & Icon</label>
        <select id="w-icon" class="input" onchange="document.getElementById('card-extra-fields').style.display = this.value==='CARD' ? 'block' : 'none'">
          <option value="CASH">Cash</option>
          <option value="BKASH">bKash</option>
          <option value="NAGAD">Nagad</option>
          <option value="ROCKET">Rocket</option>
          <option value="BANK">Bank Account</option>
          <option value="BRAC">BRAC Bank</option>
          <option value="MTB">MTB</option>
          <option value="CARD">Credit Card</option>
          <option value="RAPID_PASS">Rapid Pass</option>
          <option value="SAVINGS">Savings</option>
        </select>
      </div>
      <div class="form-group">
        <label>Currency</label>
        <select id="w-currency" class="input">
          <option value="BDT">BDT (৳)</option>
          <option value="USD">USD ($)</option>
          <option value="EUR">EUR (€)</option>
          <option value="GBP">GBP (£)</option>
        </select>
      </div>
    </div>
    <div class="form-group">
      <label>Opening Balance</label>
      <input id="w-opening" class="input" inputmode="decimal" placeholder="0" value="0" />
      <div class="calc-toolbar">
        <button type="button" class="calc-key" onclick="insertMathSymbol('w-opening','+')">+</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('w-opening','-')">−</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('w-opening','*')">×</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('w-opening','/')">÷</button>
      </div>
    </div>
    <div id="card-extra-fields" style="display:none; padding:10px; background:var(--surface-2); border-radius:12px; margin-bottom:12px;">
      <div class="form-group">
        <label>Credit Limit (Optional)</label>
        <input id="w-credit-limit" class="input" type="number" placeholder="e.g. 100000" />
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Statement Day (1-31)</label>
          <input id="w-stmt-day" class="input" type="number" min="1" max="31" placeholder="15" />
        </div>
        <div class="form-group">
          <label>Payment Due Day (1-31)</label>
          <input id="w-due-day" class="input" type="number" min="1" max="31" placeholder="5" />
        </div>
      </div>
    </div>
    <button class="btn" onclick="saveNewWallet()">Save Wallet</button>
  `);
};

window.saveNewWallet = async function () {
  const name = (document.getElementById("w-name")?.value || "").trim();
  if (!name) return alert("Please enter a wallet name");
  const iconName = document.getElementById("w-icon")?.value || "CASH";
  const currency = document.getElementById("w-currency")?.value || "BDT";
  const openingBalance = evalMathExpression(document.getElementById("w-opening")?.value || "0");
  const creditLimit = Number(document.getElementById("w-credit-limit")?.value || 0);
  const statementDay = Number(document.getElementById("w-stmt-day")?.value || 0);
  const paymentDueDay = Number(document.getElementById("w-due-day")?.value || 0);

  const now = Date.now();
  const wallet = {
    id: now,
    name,
    type: iconName === "CARD" ? "CARD" : iconName,
    accountHint: "",
    openingBalance,
    openingDate: now,
    currency,
    iconName,
    colorHex: state.settings.accentColorHex || "#F15A24",
    sortOrder: state.wallets.length + 1,
    isArchived: false,
    includeInTotalBalance: true,
    creditLimit,
    statementDay,
    paymentDueDay
  };
  state.wallets.push(wallet);
  saveLocalState();
  closeModal();
  render();
  await syncEntityToCloud("wallets", wallet.id, wallet);
};

// Add Expense / Income / Transfer Modal (with DatePicker, Merchant Autocomplete, Split Transaction, Linked Fee, Photo)
window.openAddTransactionModal = function () {
  if (state.wallets.length === 0) {
    alert("Please create at least one wallet first.");
    openAddWalletModal();
    return;
  }
  const todayIso = new Date().toISOString().slice(0, 10);
  const recentMerchants = Array.from(
    new Set(state.transactions.map((t) => (t.note || "").trim()).filter(Boolean))
  ).slice(0, 6);

  openModal(`
    <h3 style="margin-top:0;">Log Transaction / Transfer</h3>
    <div class="form-group">
      <label>Entry Mode</label>
      <select id="tx-type" class="input" onchange="toggleTxModeFields()">
        <option value="EXPENSE">Expense</option>
        <option value="INCOME">Income</option>
        <option value="TRANSFER">Exchange / Wallet Transfer</option>
      </select>
    </div>
    <div class="grid-2">
      <div class="form-group">
        <label>Date (Tap to pick past date)</label>
        <input id="tx-date" type="date" class="input" value="${todayIso}" />
      </div>
      <div class="form-group">
        <label>Wallet</label>
        <select id="tx-wallet" class="input">
          ${state.wallets.map((w) => `<option value="${w.id}">${w.name} (${w.currency})</option>`).join("")}
        </select>
      </div>
    </div>
    <div id="tx-dest-wallet-group" class="form-group" style="display:none;">
      <label>Destination Wallet</label>
      <select id="tx-dest-wallet" class="input">
        ${state.wallets.map((w) => `<option value="${w.id}">${w.name} (${w.currency})</option>`).join("")}
      </select>
    </div>
    <div class="form-group">
      <label>Amount (Supports + − × ÷)</label>
      <input id="tx-amount" class="input" inputmode="text" placeholder="e.g. 250+120" />
      <div class="calc-toolbar">
        <button type="button" class="calc-key" onclick="insertMathSymbol('tx-amount','+')">+</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('tx-amount','-')">−</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('tx-amount','*')">×</button>
        <button type="button" class="calc-key" onclick="insertMathSymbol('tx-amount','/')">÷</button>
      </div>
    </div>
    <div class="grid-2" id="tx-cat-fee-row">
      <div class="form-group">
        <label>Category</label>
        <select id="tx-category" class="input">
          ${state.categories.map((c) => `<option value="${c.id}">${c.name}</option>`).join("")}
        </select>
      </div>
      <div class="form-group">
        <label>Service Charge / Fee</label>
        <input id="tx-fee" class="input" type="number" placeholder="0" value="0" />
      </div>
    </div>
    <div class="form-group">
      <label style="display:flex; align-items:center; justify-content:space-between;">
        <span>Split into 2nd Category (Optional)</span>
        <input type="checkbox" id="tx-split-toggle" onchange="document.getElementById('tx-split-box').style.display=this.checked?'grid':'none'" />
      </label>
      <div id="tx-split-box" class="grid-2" style="display:none; margin-top:6px;">
        <select id="tx-split-category" class="input">
          ${state.categories.map((c) => `<option value="${c.id}">${c.name}</option>`).join("")}
        </select>
        <input id="tx-split-amount" class="input" type="number" placeholder="2nd Cat Amount" />
      </div>
    </div>
    <div class="form-group">
      <label>Merchant / Note</label>
      <input id="tx-note" class="input" placeholder="e.g. Shwapno, Daraz, Pathao" />
      ${
        recentMerchants.length > 0
          ? `<div class="chip-row" style="margin-top:6px;">
              ${recentMerchants
                .map(
                  (m) =>
                    `<button type="button" class="chip" onclick="selectMerchantChip('${m.replace(/'/g, "\\'")}')">${m}</button>`
                )
                .join("")}
            </div>`
          : ""
      }
    </div>
    <div class="form-group">
      <label>Attach Receipt / Bill Photo (Optional)</label>
      <input id="tx-photo" type="file" accept="image/*" class="input" />
    </div>
    <details style="margin-bottom:12px;">
      <summary style="font-size:12px; color:var(--accent); cursor:pointer; font-weight:600;">📋 Paste MFS / Bank SMS (100% Offline Parser)</summary>
      <div style="margin-top:8px;">
        <textarea id="sms-paste-input" class="input" rows="2" placeholder="Paste bKash/Nagad/Bank SMS here..."></textarea>
        <button type="button" class="btn secondary" style="margin-top:6px; padding:8px;" onclick="parsePastedSms()">Auto-Fill from SMS</button>
      </div>
    </details>
    <button class="btn" onclick="saveTransactionEntry()">Save Entry</button>
  `);
};

window.toggleTxModeFields = function () {
  const mode = document.getElementById("tx-type")?.value;
  const destGroup = document.getElementById("tx-dest-wallet-group");
  if (destGroup) destGroup.style.display = mode === "TRANSFER" ? "block" : "none";
};

window.selectMerchantChip = function (merchant) {
  const noteInput = document.getElementById("tx-note");
  if (noteInput) noteInput.value = merchant;
  const lastTx = state.transactions.find((t) => (t.note || "").toLowerCase() === merchant.toLowerCase());
  if (lastTx && document.getElementById("tx-category")) {
    document.getElementById("tx-category").value = String(lastTx.categoryId);
  }
};

window.saveTransactionEntry = async function () {
  const type = document.getElementById("tx-type")?.value || "EXPENSE";
  const walletId = Number(document.getElementById("tx-wallet")?.value);
  const amount = evalMathExpression(document.getElementById("tx-amount")?.value || "0");
  const fee = evalMathExpression(document.getElementById("tx-fee")?.value || "0");
  const categoryId = Number(document.getElementById("tx-category")?.value || 1);
  const note = (document.getElementById("tx-note")?.value || "").trim();
  const dateStr = document.getElementById("tx-date")?.value;
  const dateMs = dateStr ? new Date(dateStr + "T12:00:00").getTime() : Date.now();
  const photoFile = document.getElementById("tx-photo")?.files?.[0];
  const attachmentUri = await readCompressedImage(photoFile);

  if (amount <= 0) return alert("Please enter a valid amount greater than 0.");

  const now = Date.now();
  if (type === "TRANSFER") {
    const destWalletId = Number(document.getElementById("tx-dest-wallet")?.value);
    if (destWalletId === walletId) return alert("Source and destination wallets must be different.");
    const tr = {
      id: now,
      sourceWalletId: walletId,
      destinationWalletId: destWalletId,
      principalAmount: amount,
      destinationAmount: amount,
      conversionRate: 1,
      feeMode: fee > 0 ? "FIXED" : "NONE",
      fixedFeeInput: fee,
      percentageInput: 0,
      feeAmount: fee,
      date: dateMs,
      note
    };
    state.transfers.unshift(tr);
    if (fee > 0) {
      const feeCat = state.categories.find((c) => c.isSystemFee) || state.categories[0];
      const feeTx = {
        id: now + 1,
        type: "EXPENSE",
        amount: fee,
        date: dateMs,
        walletId,
        categoryId: feeCat.id,
        note: `Transfer Service Charge (${note || "Transfer"})`,
        reference: "",
        tags: "fee",
        transferId: tr.id
      };
      state.transactions.unshift(feeTx);
      await syncEntityToCloud("transactions", feeTx.id, feeTx);
    }
    saveLocalState();
    closeModal();
    render();
    await syncEntityToCloud("transfers", tr.id, tr);
    return;
  }

  const splitChecked = document.getElementById("tx-split-toggle")?.checked;
  const splitCatId = Number(document.getElementById("tx-split-category")?.value || categoryId);
  const splitAmt = Number(document.getElementById("tx-split-amount")?.value || 0);

  const mainAmount = splitChecked && splitAmt > 0 && splitAmt < amount ? amount - splitAmt : amount;
  const parentTx = {
    id: now,
    type,
    amount: mainAmount,
    date: dateMs,
    walletId,
    categoryId,
    note,
    reference: "",
    tags: "",
    attachmentUri
  };
  state.transactions.unshift(parentTx);
  await syncEntityToCloud("transactions", parentTx.id, parentTx);

  if (splitChecked && splitAmt > 0 && splitAmt < amount) {
    const splitTx = {
      id: now + 1,
      type,
      amount: splitAmt,
      date: dateMs,
      walletId,
      categoryId: splitCatId,
      note: `${note} (Split)`,
      reference: "",
      tags: "split",
      parentTransactionId: parentTx.id,
      attachmentUri
    };
    state.transactions.unshift(splitTx);
    await syncEntityToCloud("transactions", splitTx.id, splitTx);
  }

  // Linked Service-Charge Transaction (automatically linked via parentTransactionId)
  if (fee > 0) {
    const feeCat = state.categories.find((c) => c.isSystemFee) || state.categories[0];
    const feeTx = {
      id: now + 2,
      type: "EXPENSE",
      amount: fee,
      date: dateMs,
      walletId,
      categoryId: feeCat.id,
      note: `Service Charge: ${note || "Transaction"}`,
      reference: "",
      tags: "fee",
      parentTransactionId: parentTx.id
    };
    state.transactions.unshift(feeTx);
    await syncEntityToCloud("transactions", feeTx.id, feeTx);
  }

  saveLocalState();
  closeModal();
  render();
};

// Delete Transaction (also deletes linked service-charge fee transaction)
window.deleteTransactionWithLinkedFee = async function (txId) {
  const linkedChildren = state.transactions.filter((t) => Number(t.parentTransactionId) === Number(txId));
  state.transactions = state.transactions.filter(
    (t) => Number(t.id) !== Number(txId) && Number(t.parentTransactionId) !== Number(txId)
  );
  saveLocalState();
  closeModal();
  render();
  if (db && state.user) {
    try {
      await deleteDoc(doc(db, "users", state.user.uid, "transactions", String(txId)));
      for (const child of linkedChildren) {
        await deleteDoc(doc(db, "users", state.user.uid, "transactions", String(child.id)));
      }
    } catch (e) {}
  }
};

window.openTransactionDetailModal = function (txId) {
  const tx = state.transactions.find((t) => Number(t.id) === Number(txId));
  if (!tx) return;
  const wallet = state.wallets.find((w) => Number(w.id) === Number(tx.walletId));
  const cat = state.categories.find((c) => Number(c.id) === Number(tx.categoryId));
  const linkedFee = state.transactions.find((t) => Number(t.parentTransactionId) === Number(tx.id));

  openModal(`
    <h3 style="margin-top:0;">Transaction Details</h3>
    <div class="card" style="background:var(--surface-2);">
      <div class="row-between">
        <span class="label-sm">${tx.type}</span>
        <span class="badge">${new Date(tx.date).toLocaleDateString()}</span>
      </div>
      <div class="hero-balance" style="margin:8px 0;">${formatMoney(tx.amount, wallet?.currency)}</div>
      <div style="font-size:14px; font-weight:600;">${tx.note || cat?.name || "Entry"}</div>
      <div class="wallet-sub">Wallet: ${wallet?.name || "Wallet"} • Category: ${cat?.name || "General"}</div>
      ${
        linkedFee
          ? `<div style="margin-top:8px; font-size:12px; color:var(--amber);">Linked Service Fee: ${formatMoney(linkedFee.amount, wallet?.currency)}</div>`
          : ""
      }
    </div>
    ${
      tx.attachmentUri
        ? `<div style="margin-bottom:12px;">
            <div class="label-sm" style="margin-bottom:6px;">Attached Receipt (Tap to enlarge)</div>
            <img src="${tx.attachmentUri}" onclick="viewAttachmentFull('${tx.attachmentUri}')" style="width:100%; max-height:200px; object-fit:cover; border-radius:12px; cursor:pointer;" />
          </div>`
        : ""
    }
    <button class="btn danger" onclick="deleteTransactionWithLinkedFee(${tx.id})">Delete Transaction ${linkedFee ? "& Linked Fee" : ""}</button>
  `);
};

// Loans / Khata Modals & WhatsApp Statement Sharing
window.openAddLoanModal = function () {
  if (state.wallets.length === 0) return openAddWalletModal();
  openModal(`
    <h3 style="margin-top:0;">New Loan / Khata Entry</h3>
    <div class="form-group">
      <label>Person / Contact Name</label>
      <input id="loan-person" class="input" placeholder="e.g. Rahim, Karim" />
    </div>
    <div class="grid-2">
      <div class="form-group">
        <label>Direction</label>
        <select id="loan-dir" class="input">
          <option value="LENT">I Lent (Receivable)</option>
          <option value="BORROWED">I Borrowed (Payable)</option>
        </select>
      </div>
      <div class="form-group">
        <label>Wallet</label>
        <select id="loan-wallet" class="input">
          ${state.wallets.map((w) => `<option value="${w.id}">${w.name}</option>`).join("")}
        </select>
      </div>
    </div>
    <div class="form-group">
      <label>Principal Amount</label>
      <input id="loan-amount" class="input" inputmode="decimal" placeholder="0" />
    </div>
    <div class="form-group">
      <label>Note / Reference</label>
      <input id="loan-note" class="input" placeholder="Optional note" />
    </div>
    <div class="form-group">
      <label>Attach Slip / Screenshot (Optional)</label>
      <input id="loan-photo" type="file" accept="image/*" class="input" />
    </div>
    <button class="btn" onclick="saveNewLoan()">Save Khata Entry</button>
  `);
};

window.saveNewLoan = async function () {
  const personName = (document.getElementById("loan-person")?.value || "").trim();
  const direction = document.getElementById("loan-dir")?.value || "LENT";
  const initialWalletId = Number(document.getElementById("loan-wallet")?.value || state.wallets[0]?.id || 1);
  const principalAmount = evalMathExpression(document.getElementById("loan-amount")?.value || "0");
  const note = (document.getElementById("loan-note")?.value || "").trim();
  const photoFile = document.getElementById("loan-photo")?.files?.[0];
  const attachmentUri = await readCompressedImage(photoFile);

  if (!personName || principalAmount <= 0) return alert("Enter person name and valid amount.");
  const now = Date.now();
  const loan = {
    id: now,
    personName,
    direction,
    principalAmount,
    initialWalletId,
    startDate: now,
    interestRate: 0,
    interestType: "NONE",
    note,
    reference: "",
    isArchived: false,
    attachmentUri
  };
  state.loans.unshift(loan);
  saveLocalState();
  closeModal();
  render();
  await syncEntityToCloud("loans", loan.id, loan);
};

window.shareKhataStatementWhatsApp = function (personName) {
  const personLoans = state.loans.filter((l) => l.personName.toLowerCase() === personName.toLowerCase());
  let lent = 0, borrowed = 0;
  const lines = [`*📒 Pocket Khata Statement — ${personName}*`];
  for (const l of personLoans) {
    const dt = new Date(l.startDate).toLocaleDateString();
    if (l.direction === "LENT") lent += Number(l.principalAmount || 0);
    else borrowed += Number(l.principalAmount || 0);
    lines.push(`• ${dt}: ${l.direction === "LENT" ? "Lent" : "Borrowed"} ৳${l.principalAmount} ${l.note ? `(${l.note})` : ""}`);
  }
  const net = lent - borrowed;
  lines.push(`\n*Total Lent:* ৳${lent}`);
  lines.push(`*Total Borrowed:* ৳${borrowed}`);
  lines.push(`*Net Status:* ${net >= 0 ? `Receivable ৳${net}` : `Payable ৳${Math.abs(net)}`}`);
  const url = `https://wa.me/?text=${encodeURIComponent(lines.join("\n"))}`;
  window.open(url, "_blank");
};

// Savings Goal Modal with Optional "Transfer from/to Wallet" Toggle
window.openSavingsGoalModal = function () {
  openModal(`
    <h3 style="margin-top:0;">Add Savings Goal</h3>
    <div class="form-group">
      <label>Goal Title</label>
      <input id="sg-title" class="input" placeholder="e.g. Emergency Fund, iPhone, Tour" />
    </div>
    <div class="grid-2">
      <div class="form-group">
        <label>Target Amount</label>
        <input id="sg-target" class="input" type="number" placeholder="50000" />
      </div>
      <div class="form-group">
        <label>Initial Deposit</label>
        <input id="sg-saved" class="input" type="number" placeholder="0" value="0" />
      </div>
    </div>
    <div class="form-group">
      <label style="display:flex; align-items:center; justify-content:space-between;">
        <span>Transfer from Wallet now?</span>
        <input type="checkbox" id="sg-wallet-toggle" />
      </label>
      <select id="sg-wallet-id" class="input" style="margin-top:6px;">
        ${state.wallets.map((w) => `<option value="${w.id}">${w.name}</option>`).join("")}
      </select>
    </div>
    <button class="btn" onclick="saveSavingsGoal()">Save Goal</button>
  `);
};

window.saveSavingsGoal = async function () {
  const title = (document.getElementById("sg-title")?.value || "").trim();
  const targetAmount = Number(document.getElementById("sg-target")?.value || 0);
  const savedAmount = Number(document.getElementById("sg-saved")?.value || 0);
  const deductFromWallet = document.getElementById("sg-wallet-toggle")?.checked;
  const walletId = Number(document.getElementById("sg-wallet-id")?.value || state.wallets[0]?.id || 1);

  if (!title || targetAmount <= 0) return alert("Enter goal title and target amount.");
  const now = Date.now();
  const goal = {
    id: now,
    title,
    targetAmount,
    savedAmount,
    iconName: "SAVINGS",
    colorHex: state.settings.accentColorHex || "#F15A24"
  };
  state.savingsGoals.push(goal);
  if (deductFromWallet && savedAmount > 0 && state.wallets.length > 0) {
    const tx = {
      id: now + 1,
      type: "EXPENSE",
      amount: savedAmount,
      date: now,
      walletId,
      categoryId: state.categories[0].id,
      note: `Savings Goal Transfer: ${title}`,
      reference: "",
      tags: "savings"
    };
    state.transactions.unshift(tx);
    await syncEntityToCloud("transactions", tx.id, tx);
  }
  saveLocalState();
  closeModal();
  render();
  await syncEntityToCloud("savings_goals", goal.id, goal);
};

// Category Budget Limit Setter on Report Tab
window.promptCategoryBudget = async function (catId) {
  const cat = state.categories.find((c) => Number(c.id) === Number(catId));
  if (!cat) return;
  const val = prompt(`Set Monthly Limit for "${cat.name}" (0 for no limit):`, String(cat.monthlyBudget || 0));
  if (val === null) return;
  cat.monthlyBudget = Math.max(0, Number(val) || 0);
  saveLocalState();
  render();
  await syncEntityToCloud("categories", cat.id, cat);
};

// JSON Backup & Restore (No Passphrase Encryption)
window.exportJsonBackup = function () {
  const blob = new Blob([JSON.stringify(state, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `pocket_backup_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
};

window.importJsonBackup = function (event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const imported = JSON.parse(e.target.result);
      state = { ...createInitialState(), ...imported, hasCompletedOnboarding: true };
      saveLocalState();
      applyThemeAndAccent();
      render();
      alert("Backup restored successfully!");
    } catch {
      alert("Invalid backup JSON file.");
    }
  };
  reader.readAsText(file);
};

// CSV Import from Bank / MFS Statements
window.importCsvStatement = function (event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (state.wallets.length === 0) return alert("Create a wallet first before importing CSV.");
  const reader = new FileReader();
  reader.onload = async (e) => {
    const lines = String(e.target.result || "").split(/\r?\n/).filter((l) => l.trim());
    if (lines.length < 2) return alert("CSV file is empty.");
    let importedCount = 0;
    const defaultWalletId = state.wallets[0].id;
    const defaultCatId = state.categories[0].id;

    for (let i = 1; i < lines.length; i++) {
      const cols = lines[i].split(",").map((c) => c.replace(/^"|"$/g, "").trim());
      if (cols.length < 2) continue;
      const dateParsed = Date.parse(cols[0]) || Date.now();
      const note = cols[1] || "CSV Imported Row";
      const rawAmt = parseFloat((cols[2] || cols[1] || "0").replace(/[^0-9.-]/g, ""));
      if (!rawAmt || isNaN(rawAmt)) continue;
      const tx = {
        id: Date.now() + i,
        type: rawAmt < 0 ? "EXPENSE" : "EXPENSE",
        amount: Math.abs(rawAmt),
        date: dateParsed,
        walletId: defaultWalletId,
        categoryId: defaultCatId,
        note,
        reference: "CSV",
        tags: "csv"
      };
      state.transactions.unshift(tx);
      importedCount++;
    }
    saveLocalState();
    render();
    alert(`Imported ${importedCount} transactions from CSV!`);
  };
  reader.readAsText(file);
};

// Navigation & UI Actions
window.setTab = function (tab) {
  state.ui.activeTab = tab;
  state.ui.selectedWalletId = null;
  render();
};

window.drillDownCategoryMonth = function (catId) {
  const currentMonth = new Date().toISOString().slice(0, 7);
  state.ui.historyMonthFilter = currentMonth;
  state.ui.activeTab = "HISTORY";
  render();
};

// Main Render Loop
function render() {
  applyThemeAndAccent();
  const root = document.getElementById("app");
  if (!root) return;

  // PIN Lock Screen
  if (state.ui.isLocked && state.settings.pinCode) {
    root.innerHTML = `
      <div class="startup-screen" style="justify-content:center; gap:18px;">
        <div>
          <span class="pocket-logo-tinted" style="width:54px; height:54px;"></span>
          <h2 style="margin:10px 0 4px;">Pocket Locked</h2>
          <p class="wallet-sub">Enter your 4-digit PIN to unlock</p>
        </div>
        <div style="max-width:240px; margin:0 auto; width:100%;">
          <input id="pin-unlock-input" type="password" inputmode="numeric" maxlength="4" class="input" style="text-align:center; font-size:22px; letter-spacing:8px;" placeholder="••••" />
          <button class="btn" style="margin-top:12px;" onclick="if(document.getElementById('pin-unlock-input').value===state.settings.pinCode){state.ui.isLocked=false;state.settings.lastBackgroundTimestamp=Date.now();render();}else{alert('Incorrect PIN');}">Unlock</button>
        </div>
      </div>
    `;
    return;
  }

  // Startup / Onboarding Page (Text: "App developed by Abdullahil Hadi", No "Skip setup" at bottom)
  if (!state.hasCompletedOnboarding) {
    root.innerHTML = `
      <div class="startup-screen">
        <div style="padding-top:20px;">
          <span class="pocket-logo-tinted" style="width:64px; height:64px;"></span>
          <h1 style="margin:10px 0 4px; font-size:32px; font-weight:800;">Pocket</h1>
          <div style="font-size:14px; color:var(--accent); font-weight:700;">App developed by Abdullahil Hadi</div>
          <p class="wallet-sub" style="margin-top:8px;">Smart Wallets, Khata & Cloud Sync for iPhone & Android</p>
        </div>

        <div class="card" style="text-align:left;">
          <div class="label-sm" style="margin-bottom:8px;">1. Google Cloud Account (Recommended)</div>
          <button class="btn secondary" onclick="signInWithGoogleAccount()">
            <span>🔐 Continue with Google Account</span>
          </button>
          <div style="margin:16px 0 8px;" class="label-sm">2. Choose Initial Wallets to Create</div>
          <label class="row-between" style="padding:8px 0;"><span style="font-size:14px;">💵 Cash Wallet</span><input type="checkbox" id="onboard-cash" checked /></label>
          <label class="row-between" style="padding:8px 0;"><span style="font-size:14px;">📱 bKash</span><input type="checkbox" id="onboard-bkash" checked /></label>
          <label class="row-between" style="padding:8px 0;"><span style="font-size:14px;">🟠 Nagad</span><input type="checkbox" id="onboard-nagad" /></label>
          <label class="row-between" style="padding:8px 0;"><span style="font-size:14px;">🏦 Bank Account</span><input type="checkbox" id="onboard-bank" /></label>
        </div>

        <div>
          <button class="btn" onclick="completeStartupSetup()">Get started</button>
        </div>
      </div>
    `;
    return;
  }

  let bodyHtml = "";
  if (state.ui.activeTab === "HOME") bodyHtml = renderHomeTab();
  else if (state.ui.activeTab === "HISTORY") bodyHtml = renderHistoryTab();
  else if (state.ui.activeTab === "LOANS") bodyHtml = renderLoansTab();
  else if (state.ui.activeTab === "REPORTS") bodyHtml = renderReportsTab();
  else if (state.ui.activeTab === "SETTINGS") bodyHtml = renderSettingsTab();

  root.innerHTML = `
    <div class="app-shell">
      <header class="top-header">
        <div class="brand-row">
          <span class="pocket-logo-tinted" aria-hidden="true"></span>
          <span class="brand-title">Pocket</span>
        </div>
        <div class="header-actions">
          <span class="sync-pill">
            <span class="sync-dot" style="background:${state.user ? "var(--green)" : "var(--amber)"}"></span>
            ${state.user ? "Cloud Synced" : "Offline"}
          </span>
          <button class="icon-btn" onclick="state.settings.hideBalancesPrivacy=!state.settings.hideBalancesPrivacy;saveLocalState();syncProfileToCloud();render();" title="Toggle Privacy">
            ${state.settings.hideBalancesPrivacy ? "🙈" : "👁️"}
          </button>
        </div>
      </header>

      <main class="main-content">
        ${bodyHtml}
      </main>

      <nav class="bottom-nav">
        <button class="nav-item ${state.ui.activeTab === "HOME" ? "active" : ""}" onclick="setTab('HOME')">
          <span>👛</span><span>Wallets</span>
        </button>
        <button class="nav-item ${state.ui.activeTab === "HISTORY" ? "active" : ""}" onclick="setTab('HISTORY')">
          <span>📜</span><span>History</span>
        </button>
        <button class="fab-add" onclick="openAddTransactionModal()" aria-label="Add Transaction">+</button>
        <button class="nav-item ${state.ui.activeTab === "LOANS" ? "active" : ""}" onclick="setTab('LOANS')">
          <span>📒</span><span>Khata</span>
        </button>
        <button class="nav-item ${state.ui.activeTab === "REPORTS" ? "active" : ""}" onclick="setTab('REPORTS')">
          <span>📊</span><span>Report</span>
        </button>
        <button class="nav-item ${state.ui.activeTab === "SETTINGS" ? "active" : ""}" onclick="setTab('SETTINGS')">
          <span>⚙️</span><span>Settings</span>
        </button>
      </nav>
    </div>
    <div id="modal-root"></div>
  `;
  renderModalContainer();
}

function renderHomeTab() {
  // If drilling into a specific Wallet Statement
  if (state.ui.selectedWalletId) {
    const w = state.wallets.find((x) => Number(x.id) === Number(state.ui.selectedWalletId));
    if (w) {
      const bal = getWalletBalance(w.id);
      const q = (state.ui.walletSearch || "").toLowerCase();
      const txs = state.transactions.filter(
        (t) =>
          Number(t.walletId) === Number(w.id) &&
          (!q || (t.note || "").toLowerCase().includes(q) || String(t.amount).includes(q))
      );
      const usedCredit = w.type === "CARD" && bal < 0 ? Math.abs(bal) : 0;
      const utilPct = w.creditLimit > 0 ? Math.min(100, Math.round((usedCredit / w.creditLimit) * 100)) : 0;

      return `
        <button class="icon-btn" style="margin-bottom:12px;" onclick="state.ui.selectedWalletId=null;render();">← Back to Wallets</button>
        <div class="hero-card">
          <div class="row-between">
            <span class="label-sm">${w.name} (${w.currency})</span>
            <span class="badge">${w.type}</span>
          </div>
          <div class="hero-balance">${formatMoney(bal, w.currency)}</div>
          ${
            w.creditLimit > 0
              ? `<div style="font-size:12px; color:var(--text-muted);">
                  Credit Limit: ${formatMoney(w.creditLimit, w.currency)} • Utilization: ${utilPct}%
                  ${w.paymentDueDay ? `• Due Day: ${w.paymentDueDay}` : ""}
                </div>
                <div class="progress-bar"><div class="progress-fill" style="width:${utilPct}%"></div></div>`
              : ""
          }
        </div>
        <div class="form-group">
          <input class="input" placeholder="🔍 Search inside ${w.name} statement..." value="${state.ui.walletSearch || ""}" oninput="state.ui.walletSearch=this.value;render();" />
        </div>
        <div class="card">
          <div class="section-title" style="margin-top:0;">Wallet Statement (${txs.length})</div>
          ${
            txs.length === 0
              ? `<div class="wallet-sub">No matching transactions in this wallet.</div>`
              : txs
                  .map(
                    (t) => `
                <div class="tx-row" onclick="openTransactionDetailModal(${t.id})">
                  <div>
                    <div style="font-weight:600; font-size:14px;">
                      ${t.note || "Transaction"} ${t.attachmentUri ? "📷" : ""}
                    </div>
                    <div class="wallet-sub">${new Date(t.date).toLocaleDateString()}</div>
                  </div>
                  <div class="val ${t.type === "INCOME" ? "green" : "red"}">
                    ${t.type === "INCOME" ? "+" : "−"}${formatMoney(t.amount, w.currency)}
                  </div>
                </div>`
                  )
                  .join("")
          }
        </div>
      `;
    }
  }

  const totalBdt = state.wallets
    .filter((w) => !w.isArchived && w.includeInTotalBalance !== false && (w.currency || "BDT") === "BDT")
    .reduce((sum, w) => sum + getWalletBalance(w.id), 0);

  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const monthIncome = state.transactions
    .filter((t) => t.type === "INCOME" && t.date >= monthStart)
    .reduce((s, t) => s + Number(t.amount || 0), 0);
  const monthExpense = state.transactions
    .filter((t) => t.type === "EXPENSE" && t.date >= monthStart)
    .reduce((s, t) => s + Number(t.amount || 0), 0);

  return `
    <div class="hero-card">
      <div class="label-sm">Total Available Balance</div>
      <div class="hero-balance">${formatMoney(totalBdt, "BDT")}</div>
      <div class="grid-2">
        <div class="stat-box">
          <div class="label-sm">This Month Income</div>
          <div class="val green">+${formatMoney(monthIncome, "BDT")}</div>
        </div>
        <div class="stat-box">
          <div class="label-sm">This Month Expense</div>
          <div class="val red">−${formatMoney(monthExpense, "BDT")}</div>
        </div>
      </div>
    </div>

    <div class="section-title">
      <span>My Wallets (${state.wallets.length})</span>
      <button class="icon-btn accent" onclick="openAddWalletModal()">+ Add Wallet</button>
    </div>

    <div class="wallet-list">
      ${
        state.wallets.length === 0
          ? `<div class="card" style="text-align:center; color:var(--text-muted);">No wallets yet. Tap "+ Add Wallet" to create one.</div>`
          : state.wallets
              .map((w) => {
                const bal = getWalletBalance(w.id);
                const iconSrc = WALLET_ICONS[w.iconName] || WALLET_ICONS.CASH;
                return `
                  <div class="wallet-item" onclick="state.ui.selectedWalletId=${w.id};render();">
                    <div class="wallet-left">
                      <img src="${iconSrc}" class="wallet-icon" alt="${w.name}" />
                      <div>
                        <div class="wallet-name">${w.name}</div>
                        <div class="wallet-sub">${w.type} • ${w.currency || "BDT"} ${w.paymentDueDay ? `• Due Day ${w.paymentDueDay}` : ""}</div>
                      </div>
                    </div>
                    <div style="text-align:right;">
                      <div style="font-weight:800; font-size:15px;">${formatMoney(bal, w.currency)}</div>
                      <div class="wallet-sub">View Statement →</div>
                    </div>
                  </div>
                `;
              })
              .join("")
      }
    </div>
  `;
}

// History Tab (Minimal Filter Options, No "Income, Expenses, Net cash flow" summary box, Custom Date Range)
function renderHistoryTab() {
  const q = (state.ui.historySearch || "").toLowerCase();
  const typeFilter = state.ui.historyFilterType || "ALL";
  const startMs = state.ui.historyStartDate ? new Date(state.ui.historyStartDate + "T00:00:00").getTime() : 0;
  const endMs = state.ui.historyEndDate ? new Date(state.ui.historyEndDate + "T23:59:59").getTime() : Infinity;

  const filtered = state.transactions.filter((t) => {
    if (typeFilter !== "ALL" && t.type !== typeFilter) return false;
    if (t.date < startMs || t.date > endMs) return false;
    if (state.ui.historyMonthFilter) {
      const ym = new Date(t.date).toISOString().slice(0, 7);
      if (ym !== state.ui.historyMonthFilter) return false;
    }
    if (q) {
      const cat = state.categories.find((c) => Number(c.id) === Number(t.categoryId));
      const hay = `${t.note || ""} ${cat?.name || ""} ${t.amount}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  return `
    <div class="chip-row">
      <button class="chip ${typeFilter === "ALL" ? "active" : ""}" onclick="state.ui.historyFilterType='ALL';render();">All</button>
      <button class="chip ${typeFilter === "EXPENSE" ? "active" : ""}" onclick="state.ui.historyFilterType='EXPENSE';render();">Expenses</button>
      <button class="chip ${typeFilter === "INCOME" ? "active" : ""}" onclick="state.ui.historyFilterType='INCOME';render();">Income</button>
      ${
        state.ui.historyMonthFilter
          ? `<button class="chip active" onclick="state.ui.historyMonthFilter='';render();">Month: ${state.ui.historyMonthFilter} ✕</button>`
          : ""
      }
    </div>

    <div class="card" style="padding:12px;">
      <input class="input" placeholder="🔍 Search merchant, category, or amount..." value="${state.ui.historySearch || ""}" oninput="state.ui.historySearch=this.value;render();" style="margin-bottom:8px;" />
      <div class="grid-2">
        <div>
          <label class="label-sm">Start Date</label>
          <input type="date" class="input" value="${state.ui.historyStartDate || ""}" onchange="state.ui.historyStartDate=this.value;render();" />
        </div>
        <div>
          <label class="label-sm">End Date</label>
          <input type="date" class="input" value="${state.ui.historyEndDate || ""}" onchange="state.ui.historyEndDate=this.value;render();" />
        </div>
      </div>
    </div>

    <div class="card">
      ${
        filtered.length === 0
          ? `<div class="wallet-sub" style="text-align:center; padding:18px 0;">No transactions match your filter.</div>`
          : filtered
              .map((t) => {
                const cat = state.categories.find((c) => Number(c.id) === Number(t.categoryId));
                const w = state.wallets.find((x) => Number(x.id) === Number(t.walletId));
                return `
                  <div class="tx-row" onclick="openTransactionDetailModal(${t.id})">
                    <div>
                      <div style="font-weight:700; font-size:14px;">
                        ${t.note || cat?.name || "Transaction"}
                        ${t.attachmentUri ? `<span title="Receipt Attached">📷</span>` : ""}
                      </div>
                      <div class="wallet-sub">${cat?.name || "General"} • ${w?.name || "Wallet"} • ${new Date(t.date).toLocaleDateString()}</div>
                    </div>
                    <div class="val ${t.type === "INCOME" ? "green" : "red"}">
                      ${t.type === "INCOME" ? "+" : "−"}${formatMoney(t.amount, w?.currency)}
                    </div>
                  </div>
                `;
              })
              .join("")
      }
    </div>
  `;
}

// Loans / Khata Tab (Person-wise grouping, Search bar, Attached slip viewer, 1-tap Share Khata Statement on WhatsApp)
function renderLoansTab() {
  const q = (state.ui.loanSearch || "").toLowerCase();
  const loans = state.loans.filter(
    (l) => !q || l.personName.toLowerCase().includes(q) || (l.note || "").toLowerCase().includes(q)
  );

  const groupedByPerson = {};
  for (const l of loans) {
    const key = l.personName.trim();
    if (!groupedByPerson[key]) groupedByPerson[key] = [];
    groupedByPerson[key].push(l);
  }

  return `
    <div class="section-title" style="margin-top:0;">
      <span>Loans & Khata (${loans.length})</span>
      <button class="icon-btn accent" onclick="openAddLoanModal()">+ New Khata</button>
    </div>

    <div class="row-between" style="margin-bottom:10px;">
      <div class="chip-row" style="margin-bottom:0;">
        <button class="chip ${state.ui.loanGroupMode === "LIST" ? "active" : ""}" onclick="state.ui.loanGroupMode='LIST';render();">All Entries</button>
        <button class="chip ${state.ui.loanGroupMode === "PERSON" ? "active" : ""}" onclick="state.ui.loanGroupMode='PERSON';render();">👥 Person-wise Grouping</button>
      </div>
    </div>

    <div class="form-group">
      <input class="input" placeholder="🔍 Search person or loan note..." value="${state.ui.loanSearch || ""}" oninput="state.ui.loanSearch=this.value;render();" />
    </div>

    ${
      state.ui.loanGroupMode === "PERSON"
        ? Object.keys(groupedByPerson)
            .map((person) => {
              const items = groupedByPerson[person];
              const lent = items.filter((x) => x.direction === "LENT").reduce((s, x) => s + Number(x.principalAmount || 0), 0);
              const borrowed = items.filter((x) => x.direction === "BORROWED").reduce((s, x) => s + Number(x.principalAmount || 0), 0);
              return `
                <div class="card">
                  <div class="row-between">
                    <div>
                      <div style="font-weight:800; font-size:16px;">👤 ${person}</div>
                      <div class="wallet-sub">Lent: ${formatMoney(lent)} • Borrowed: ${formatMoney(borrowed)}</div>
                    </div>
                    <button class="icon-btn accent" onclick="shareKhataStatementWhatsApp('${person.replace(/'/g, "\\'")}')">💬 Share Statement</button>
                  </div>
                </div>
              `;
            })
            .join("")
        : loans
            .map(
              (l) => `
              <div class="card">
                <div class="row-between">
                  <div>
                    <div style="font-weight:700; font-size:15px;">
                      ${l.personName} ${l.attachmentUri ? "📷" : ""}
                    </div>
                    <div class="wallet-sub">${l.direction === "LENT" ? "I Lent" : "I Borrowed"} • ${new Date(l.startDate).toLocaleDateString()}</div>
                    ${l.note ? `<div class="wallet-sub">${l.note}</div>` : ""}
                  </div>
                  <div style="text-align:right;">
                    <div class="val ${l.direction === "LENT" ? "green" : "red"}">${formatMoney(l.principalAmount)}</div>
                    <button class="icon-btn" style="margin-top:6px; height:30px; font-size:11px;" onclick="shareKhataStatementWhatsApp('${l.personName.replace(/'/g, "\\'")}')">💬 WhatsApp</button>
                  </div>
                </div>
                ${
                  l.attachmentUri
                    ? `<img src="${l.attachmentUri}" onclick="viewAttachmentFull('${l.attachmentUri}')" style="margin-top:10px; width:100%; max-height:130px; object-fit:cover; border-radius:10px; cursor:pointer;" />`
                    : ""
                }
              </div>
            `
            )
            .join("")
    }
  `;
}

// Reports Tab (Currency Pill Selector, Category Limits directly on category cards, Savings Goals)
function renderReportsTab() {
  const currencies = Array.from(new Set(["BDT", ...state.wallets.map((w) => w.currency || "BDT")]));
  const activeCur = state.ui.reportCurrency || "BDT";
  const walletIdsInCur = new Set(
    state.wallets.filter((w) => (w.currency || "BDT") === activeCur).map((w) => Number(w.id))
  );

  const expenseCats = state.categories.filter((c) => c.type === "EXPENSE");

  return `
    ${
      currencies.length > 1
        ? `<div class="chip-row">
            ${currencies
              .map(
                (cur) =>
                  `<button class="chip ${activeCur === cur ? "active" : ""}" onclick="state.ui.reportCurrency='${cur}';render();">${cur}</button>`
              )
              .join("")}
          </div>`
        : ""
    }

    <div class="section-title" style="margin-top:0;">
      <span>Category Spending & Limits (${activeCur})</span>
    </div>

    ${expenseCats
      .map((cat) => {
        const spent = state.transactions
          .filter(
            (t) =>
              t.type === "EXPENSE" &&
              Number(t.categoryId) === Number(cat.id) &&
              (walletIdsInCur.size === 0 || walletIdsInCur.has(Number(t.walletId)))
          )
          .reduce((s, t) => s + Number(t.amount || 0), 0);
        const limit = Number(cat.monthlyBudget || 0);
        const pct = limit > 0 ? Math.min(100, Math.round((spent / limit) * 100)) : 0;
        const isOver = limit > 0 && spent > limit;

        return `
          <div class="card" style="padding:13px;">
            <div class="row-between">
              <div onclick="drillDownCategoryMonth(${cat.id})" style="cursor:pointer;">
                <div style="font-weight:700; font-size:14px;">${cat.name}</div>
                <div class="wallet-sub">Spent: ${formatMoney(spent, activeCur)} ${limit > 0 ? `/ Limit: ${formatMoney(limit, activeCur)}` : ""}</div>
              </div>
              <button class="icon-btn" onclick="promptCategoryBudget(${cat.id})">🎯 Limit</button>
            </div>
            ${
              limit > 0
                ? `<div class="progress-bar">
                    <div class="progress-fill" style="width:${pct}%; background:${isOver ? "var(--red)" : "var(--accent)"}"></div>
                  </div>`
                : ""
            }
          </div>
        `;
      })
      .join("")}

    <div class="section-title">
      <span>Savings Goals (${state.savingsGoals.length})</span>
      <button class="icon-btn accent" onclick="openSavingsGoalModal()">+ Add Goal</button>
    </div>

    ${
      state.savingsGoals.length === 0
        ? `<div class="card wallet-sub">No savings goals yet. Tap "+ Add Goal" to start saving.</div>`
        : state.savingsGoals
            .map((g) => {
              const pct = g.targetAmount > 0 ? Math.min(100, Math.round((g.savedAmount / g.targetAmount) * 100)) : 0;
              return `
                <div class="card">
                  <div class="row-between">
                    <div style="font-weight:700;">${g.title}</div>
                    <span class="badge">${pct}%</span>
                  </div>
                  <div class="wallet-sub" style="margin-top:4px;">${formatMoney(g.savedAmount)} of ${formatMoney(g.targetAmount)}</div>
                  <div class="progress-bar"><div class="progress-fill" style="width:${pct}%"></div></div>
                </div>
              `;
            })
            .join("")
    }
  `;
}

// Settings Tab (Google Account, Theme, Accent Color, Backup/CSV, PIN Lock, and "App developed by Abdullahil Hadi" at bottom)
function renderSettingsTab() {
  const accents = ["#F15A24", "#22C55E", "#3B82F6", "#A855F7", "#EC4899", "#F59E0B", "#14B8A6"];
  return `
    <div class="card">
      <div class="label-sm" style="margin-bottom:8px;">Google Cloud Account</div>
      ${
        state.user
          ? `<div class="row-between">
              <div>
                <div style="font-weight:700;">${state.user.displayName}</div>
                <div class="wallet-sub">${state.user.email}</div>
              </div>
              <button class="icon-btn" onclick="signOutGoogleAccount()">Sign Out</button>
            </div>`
          : `<button class="btn" onclick="signInWithGoogleAccount()">🔐 Sign in with Google Account</button>
             <div class="wallet-sub" style="margin-top:6px;">Signing in replaces local data with your Google Account cloud data.</div>`
      }
    </div>

    <div class="card">
      <div class="label-sm" style="margin-bottom:8px;">Appearance & Accent Color</div>
      <div class="grid-3" style="margin-bottom:12px;">
        ${["DARK", "LIGHT", "SYSTEM"]
          .map(
            (m) =>
              `<button class="icon-btn ${state.settings.themeMode === m ? "accent" : ""}" onclick="state.settings.themeMode='${m}';saveLocalState();syncProfileToCloud();render();">${m}</button>`
          )
          .join("")}
      </div>
      <div style="display:flex; gap:10px; flex-wrap:wrap;">
        ${accents
          .map(
            (hex) => `
            <button onclick="state.settings.accentColorHex='${hex}';saveLocalState();syncProfileToCloud();render();" style="width:34px; height:34px; border-radius:50%; background:${hex}; border:${state.settings.accentColorHex === hex ? "3px solid var(--text)" : "none"}; cursor:pointer;"></button>
          `
          )
          .join("")}
      </div>
    </div>

    <div class="card">
      <div class="label-sm" style="margin-bottom:8px;">Security (PIN Lock with 30s Grace Period)</div>
      <div class="row-between">
        <span style="font-size:14px;">4-Digit App PIN</span>
        <button class="icon-btn" onclick="const p=prompt('Enter 4-digit PIN (leave blank to disable):', state.settings.pinCode||''); if(p!==null){state.settings.pinCode=p.trim();saveLocalState();render();}">
          ${state.settings.pinCode ? "Change / Remove PIN" : "Set PIN"}
        </button>
      </div>
    </div>

    <div class="card">
      <div class="label-sm" style="margin-bottom:8px;">Backup, Restore & Bank CSV Import</div>
      <div class="grid-2" style="margin-bottom:10px;">
        <button class="icon-btn" onclick="exportJsonBackup()">⬇️ Export .json</button>
        <label class="icon-btn" style="cursor:pointer;">
          ⬆️ Restore .json
          <input type="file" accept=".json,application/json" style="display:none;" onchange="importJsonBackup(event)" />
        </label>
      </div>
      <label class="icon-btn" style="width:100%; cursor:pointer;">
        📄 Import Bank / MFS Statement (.csv)
        <input type="file" accept=".csv,text/csv" style="display:none;" onchange="importCsvStatement(event)" />
      </label>
    </div>

    <button class="btn secondary" style="margin-top:16px; border-color:var(--accent); color:var(--accent);" onclick="openDeveloperModal()">
      👨‍💻 App developed by Abdullahil Hadi
    </button>
  `;
}

// Initial boot
applyThemeAndAccent();
render();
