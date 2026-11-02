// app.js — Complete 1:1 Web UI & State Engine for Pocket
import {
  AccountingEngine,
  SubscriptionManager,
  BackupManager,
  DEFAULT_STARTER_WALLETS,
  DEFAULT_CATEGORIES,
  DEFAULT_SETTINGS,
  evaluateMathExpression,
  parseFinancialSms
} from './accounting.js';
import { PocketLocalizer } from './localizer.js';
import { PocketCloudSync } from './firebase-sync.js';

const STORAGE_KEY = 'pocket_web_ledger_v1';

// Official PNG Wallet Icons in /docs/assets/ matching PocketComponents.kt
const WALLET_PNG_MAP = {
  CASH: './assets/wallet_icon_cash.png',
  BANK: './assets/wallet_icon_bank.png',
  MTB: './assets/wallet_icon_mtb.png',
  BRAC: './assets/wallet_icon_brac.png',
  BKASH: './assets/wallet_icon_bkash.png',
  NAGAD: './assets/wallet_icon_nagad.png',
  ROCKET: './assets/wallet_icon_rocket.png',
  RAPID_PASS: './assets/wallet_icon_rapid_pass.png',
  CARD: './assets/wallet_icon_card.png',
  SAVINGS: './assets/wallet_icon_savings.png'
};

// Material Symbol mapping for Categories & Actions
const MATERIAL_ICON_MAP = {
  RESTAURANT: 'restaurant',
  SHOPPING_BASKET: 'shopping_basket',
  DIRECTIONS_CAR: 'directions_car',
  HOME: 'home',
  BOLT: 'bolt',
  WIFI: 'wifi',
  SHOPPING_BAG: 'shopping_bag',
  MEDICAL_SERVICES: 'medical_services',
  SCHOOL: 'school',
  FAMILY_RESTROOM: 'family_restroom',
  MOVIE: 'movie',
  FLIGHT: 'flight',
  VOLUNTEER_ACTIVISM: 'volunteer_activism',
  RECEIPT: 'receipt_long',
  CATEGORY: 'category',
  PAYMENTS: 'payments',
  WORK: 'work',
  TRENDING_UP: 'trending_up',
  CARD_GIFTCARD: 'card_giftcard',
  ATTACH_MONEY: 'attach_money',
  SWAP_HORIZ: 'swap_horiz',
  HANDSHAKE: 'handshake',
  TUNE: 'tune',
  SAVINGS: 'savings'
};

const nowDate = new Date();

export const AppState = {
  // Persistent Ledger Collections
  settings: { ...DEFAULT_SETTINGS },
  wallets: JSON.parse(JSON.stringify(DEFAULT_STARTER_WALLETS)),
  categories: JSON.parse(JSON.stringify(DEFAULT_CATEGORIES)),
  transactions: [],
  transactionSplits: [],
  transfers: [],
  loans: [],
  loanPayments: [],
  adjustments: [],
  recurringTransactions: [],
  savingsGoals: [],

  // Transient UI State
  currentScreen: 'BALANCES', // BALANCES, HISTORY, REPORTS, LOANS, SETTINGS, WALLET_DETAIL, TX_FORM, TRANSFER_FORM, ADJUST_FORM, CATEGORIES, RECURRING, STATEMENT, BACKUP
  screenStack: [],
  selectedYear: nowDate.getFullYear(),
  selectedMonth: nowDate.getMonth(), // 0-indexed
  isAppUnlocked: false,
  isFabExpanded: false,
  selectedWalletDetailId: null,
  editingTx: null,
  txFormInitialType: 'EXPENSE',
  txFormInitialWalletId: null,
  adjustInitialWalletId: null,

  // History Filters
  historySearch: '',
  historyTypeFilter: 'ALL',
  historyWalletFilter: null,
  historyBatchIds: new Set(),

  // Loans UI
  loanFilter: 'ACTIVE', // ACTIVE, OVERDUE, SETTLED, ALL
  loanKhataView: false,
  expandedKhataPerson: null,

  // Onboarding UI State
  onboardingShowAllWallets: false,
  onboardingSelectedIds: new Set(), // initially all unchecked, matching Android
  onboardingBalances: {},
  onboardingLoans: [{ direction: 'LENT', personName: '', amount: '', loanDateMs: Date.now(), dueDateMs: null }],

  // Cloud Sync State
  googleUser: null,
  isCloudSyncing: false,
  lastCloudSyncStatus: '',
  lastAppliedCloudMs: 0
};

function escHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function t(str) {
  return PocketLocalizer.t(str);
}

function hexToRgbTuple(hex, fallback = [241, 90, 36]) {
  const clean = String(hex || '').replace('#', '').trim();
  if (clean.length === 6) {
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    if (!Number.isNaN(r) && !Number.isNaN(g) && !Number.isNaN(b)) {
      return [r, g, b];
    }
  }
  return fallback;
}

// Updates CSS variables and the SVG <feColorMatrix> filter so wallet PNG icons match the exact active accent color
export function applyThemeAndAccentVariables() {
  const s = AppState.settings;
  PocketLocalizer.setLanguage(s.appLanguage || 'EN');

  const isLight = String(s.themeMode || 'DARK').toUpperCase() === 'LIGHT';
  document.body.classList.toggle('theme-light', isLight);

  const accentHex = s.accentColorHex || '#F15A24';
  const [ar, ag, ab] = hexToRgbTuple(accentHex, [241, 90, 36]);
  const root = document.documentElement;
  root.style.setProperty('--accent-color', accentHex);
  root.style.setProperty('--accent-rgb', `${ar}, ${ag}, ${ab}`);

  const boxOpacity = Math.min(1, Math.max(0.15, Number(s.boxOpacity) || 0.50));
  root.style.setProperty('--box-opacity', String(boxOpacity));
  root.style.setProperty('--elevated-opacity', String(Math.min(1, boxOpacity + 0.08)));

  const customBoxHex = String(s.boxColorHex || '').trim();
  if (customBoxHex) {
    const [br, bg, bb] = hexToRgbTuple(customBoxHex, isLight ? [255, 255, 255] : [20, 20, 22]);
    root.style.setProperty('--box-rgb', `${br}, ${bg}, ${bb}`);
    root.style.setProperty('--elevated-rgb', `${Math.min(255, br + 12)}, ${Math.min(255, bg + 12)}, ${Math.min(255, bb + 14)}`);
  } else {
    root.style.setProperty('--box-rgb', isLight ? '255, 255, 255' : '20, 20, 22');
    root.style.setProperty('--elevated-rgb', isLight ? '244, 244, 245' : '31, 31, 35');
  }

  // Update SVG <feColorMatrix> for official wallet PNG recoloring matching PocketComponents.kt
  const rf = (ar / 255) * 1.12;
  const gf = (ag / 255) * 1.12;
  const bf = (ab / 255) * 1.12;
  const matrixEl = document.getElementById('pocket-wallet-tint-matrix');
  if (matrixEl) {
    const m = [
      (0.2126 * rf).toFixed(4), (0.7152 * rf).toFixed(4), (0.0722 * rf).toFixed(4), 0, (0.04 * rf).toFixed(4),
      (0.2126 * gf).toFixed(4), (0.7152 * gf).toFixed(4), (0.0722 * gf).toFixed(4), 0, (0.04 * gf).toFixed(4),
      (0.2126 * bf).toFixed(4), (0.7152 * bf).toFixed(4), (0.0722 * bf).toFixed(4), 0, (0.04 * bf).toFixed(4),
      0, 0, 0, 1, 0
    ].join(' ');
    matrixEl.setAttribute('values', m);
  }

  // Wallpaper layer
  const wpEl = document.getElementById('custom-wallpaper-layer');
  if (wpEl) {
    if (s.customBackgroundUri) {
      wpEl.style.backgroundImage = `url("${s.customBackgroundUri}")`;
      wpEl.style.opacity = String(Math.min(1, Math.max(0, Number(s.backgroundOpacity) ?? 0.25)));
    } else {
      wpEl.style.backgroundImage = 'none';
      wpEl.style.opacity = '0';
    }
  }
}

export function renderIconBadge(iconName, colorHex, sizePx = 42, walletNameHint = '') {
  const upperIcon = String(iconName || '').toUpperCase();
  const upperHint = String(walletNameHint || '').toUpperCase();
  let pngSrc = WALLET_PNG_MAP[upperIcon] || null;
  if (!pngSrc) {
    if (upperHint.includes('BKASH')) pngSrc = WALLET_PNG_MAP.BKASH;
    else if (upperHint.includes('NAGAD')) pngSrc = WALLET_PNG_MAP.NAGAD;
    else if (upperHint.includes('ROCKET')) pngSrc = WALLET_PNG_MAP.ROCKET;
    else if (upperHint.includes('MTB')) pngSrc = WALLET_PNG_MAP.MTB;
    else if (upperHint.includes('BRAC')) pngSrc = WALLET_PNG_MAP.BRAC;
    else if (upperHint.includes('RAPID')) pngSrc = WALLET_PNG_MAP.RAPID_PASS;
    else if (upperHint.includes('CASH')) pngSrc = WALLET_PNG_MAP.CASH;
    else if (upperHint.includes('BANK')) pngSrc = WALLET_PNG_MAP.BANK;
    else if (upperHint.includes('CARD')) pngSrc = WALLET_PNG_MAP.CARD;
    else if (upperHint.includes('SAVINGS')) pngSrc = WALLET_PNG_MAP.SAVINGS;
  }

  const accent = AppState.settings.accentColorHex || '#F15A24';
  const badgeColor = pngSrc ? accent : (colorHex || accent);
  const [r, g, b] = hexToRgbTuple(badgeColor, [241, 90, 36]);

  if (pngSrc) {
    return `
      <div class="icon-badge-circle" style="width:${sizePx}px;height:${sizePx}px;background:rgba(${r},${g},${b},0.16);border:1px solid rgba(${r},${g},${b},0.34);">
        <img src="${pngSrc}" alt="${escHtml(walletNameHint || iconName)}" class="wallet-png-tinted" />
      </div>
    `;
  }

  const matName = MATERIAL_ICON_MAP[upperIcon] || 'account_balance_wallet';
  const fontSize = Math.round(sizePx * 0.5);
  return `
    <div class="icon-badge-circle" style="width:${sizePx}px;height:${sizePx}px;background:rgba(${r},${g},${b},0.18);border:1px solid rgba(${r},${g},${b},0.35);color:${badgeColor};">
      <span class="material-symbols-rounded" style="font-size:${fontSize}px;">${matName}</span>
    </div>
  `;
}

export function showSnackbar(message) {
  const el = document.getElementById('pocket-snackbar');
  if (!el) return;
  el.textContent = t(message);
  el.classList.add('visible');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.remove('visible'), 3200);
}

export function saveStateToLocal(skipCloudPush = false) {
  try {
    const payload = BackupManager.createBackupPayload(AppState);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch (e) {
    console.warn('Local storage save warning:', e);
  }
  if (!skipCloudPush && PocketCloudSync.currentUser && AppState.settings.hasCompletedOnboarding) {
    clearTimeout(saveStateToLocal._cloudDebounce);
    saveStateToLocal._cloudDebounce = setTimeout(async () => {
      try {
        AppState.isCloudSyncing = true;
        const ts = await PocketCloudSync.pushSnapshot(AppState);
        AppState.lastAppliedCloudMs = ts;
        AppState.isCloudSyncing = false;
        AppState.lastCloudSyncStatus = 'Synced just now';
      } catch (err) {
        AppState.isCloudSyncing = false;
        console.warn('Auto cloud push warning:', err);
      }
    }, 900);
  }
}

export function loadStateFromLocal() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const res = BackupManager.validateAndParseBackup(raw);
    if (res.isValid && res.payload) {
      applyBackupPayloadToState(res.payload, true);
    }
  } catch (e) {
    console.warn('Local storage load warning:', e);
  }
}

export function applyBackupPayloadToState(p, skipCloudPush = true) {
  AppState.settings = { ...DEFAULT_SETTINGS, ...(p.settings || {}) };
  AppState.wallets = Array.isArray(p.wallets) && p.wallets.length > 0 ? p.wallets : JSON.parse(JSON.stringify(DEFAULT_STARTER_WALLETS));
  AppState.categories = Array.isArray(p.categories) && p.categories.length > 0 ? p.categories : JSON.parse(JSON.stringify(DEFAULT_CATEGORIES));
  AppState.transactions = p.transactions || [];
  AppState.transactionSplits = p.transactionSplits || [];
  AppState.transfers = p.transfers || [];
  AppState.loans = p.loans || [];
  AppState.loanPayments = p.loanPayments || [];
  AppState.adjustments = p.adjustments || [];
  AppState.recurringTransactions = p.recurringTransactions || [];
  AppState.savingsGoals = p.savingsGoals || [];
  if (!AppState.settings.appLockEnabled || !AppState.settings.appLockPin) {
    AppState.isAppUnlocked = true;
  }
  saveStateToLocal(skipCloudPush);
  applyThemeAndAccentVariables();
  renderApp();
}

export function computeDerivedData() {
  const activeWallets = AppState.wallets
    .filter(w => !w.isArchived)
    .slice()
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0));

  const walletSummaries = activeWallets.map(w =>
    AccountingEngine.calculateWalletSummary(
      w,
      AppState.transactions,
      AppState.transfers,
      AppState.loans,
      AppState.loanPayments,
      AppState.adjustments
    )
  );

  let totalAvailableBalance = 0;
  const foreignCurrencyTotals = {};

  for (const ws of walletSummaries) {
    if (!ws.wallet.includeInTotalBalance) continue;
    const curr = String(ws.wallet.currency || 'BDT').toUpperCase();
    if (curr === 'BDT') {
      totalAvailableBalance += ws.currentBalance;
    } else {
      foreignCurrencyTotals[curr] = AccountingEngine.roundCurrency((foreignCurrencyTotals[curr] || 0) + ws.currentBalance);
    }
  }
  totalAvailableBalance = AccountingEngine.roundCurrency(totalAvailableBalance);

  const loanSummaries = AccountingEngine.calculateLoanSummaries(
    AppState.loans,
    AppState.loanPayments,
    AppState.wallets
  );

  const khataSummaries = AccountingEngine.calculatePersonKhataSummaries(loanSummaries, AppState.wallets);

  const unifiedLedger = AccountingEngine.buildUnifiedLedger(
    AppState.wallets,
    AppState.categories,
    AppState.transactions,
    AppState.transfers,
    AppState.loans,
    AppState.loanPayments,
    AppState.adjustments
  );

  const monthlyReport = AccountingEngine.calculateMonthlyReport(
    AppState.selectedYear,
    AppState.selectedMonth,
    AppState.categories,
    AppState.transactions,
    AppState.transfers,
    AppState.loans,
    AppState.loanPayments,
    AppState.transactionSplits
  );

  const isSubscriptionActive = SubscriptionManager.isSubscriptionActive(
    AppState.settings.subscriptionTier,
    AppState.settings.subscriptionExpiresAt
  );
  const activeTier = SubscriptionManager.resolveActiveTier(
    AppState.settings.subscriptionTier,
    AppState.settings.subscriptionExpiresAt
  );

  return {
    walletSummaries,
    totalAvailableBalance,
    foreignCurrencyTotals,
    loanSummaries,
    khataSummaries,
    unifiedLedger,
    monthlyReport,
    isSubscriptionActive,
    activeTier
  };
}

export function navigateTo(screen) {
  AppState.isFabExpanded = false;
  if (AppState.currentScreen !== screen) {
    AppState.screenStack.push(AppState.currentScreen);
    AppState.currentScreen = screen;
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
  renderApp();
}

export function navigateBack() {
  AppState.isFabExpanded = false;
  if (AppState.screenStack.length > 0) {
    AppState.currentScreen = AppState.screenStack.pop();
  } else {
    AppState.currentScreen = 'BALANCES';
  }
  renderApp();
}

export function openModal(htmlContent) {
  const backdrop = document.getElementById('modal-backdrop');
  const shell = document.getElementById('app-shell');
  if (!backdrop) return;
  backdrop.innerHTML = `<div class="pocket-dialog" onclick="event.stopPropagation()">${htmlContent}</div>`;
  backdrop.classList.add('open');
  if (shell) shell.classList.add('blurred-12');
}

export function closeModal() {
  const backdrop = document.getElementById('modal-backdrop');
  const shell = document.getElementById('app-shell');
  if (backdrop) {
    backdrop.classList.remove('open');
    backdrop.innerHTML = '';
  }
  if (shell && !AppState.isFabExpanded) {
    shell.classList.remove('blurred-12');
  }
}

// ============================================================================
// MAIN RENDER ENTRY
// ============================================================================
export function renderApp() {
  applyThemeAndAccentVariables();
  const shell = document.getElementById('app-shell');
  const navWrap = document.getElementById('bottom-nav-container');
  const fabWrap = document.getElementById('fab-container');
  if (!shell) return;

  // 1. Check App Lock PIN
  if (AppState.settings.appLockEnabled && AppState.settings.appLockPin && !AppState.isAppUnlocked) {
    navWrap.innerHTML = '';
    fabWrap.innerHTML = '';
    shell.innerHTML = renderAppLockScreen();
    return;
  }

  // 2. Check Onboarding
  if (!AppState.settings.hasCompletedOnboarding) {
    navWrap.innerHTML = '';
    fabWrap.innerHTML = '';
    shell.innerHTML = renderOnboardingScreen();
    return;
  }

  const derived = computeDerivedData();

  // 3. Render Active Screen
  let contentHtml = '';
  switch (AppState.currentScreen) {
    case 'BALANCES':
      contentHtml = renderBalancesScreen(derived);
      break;
    case 'HISTORY':
      contentHtml = renderHistoryScreen(derived);
      break;
    case 'REPORTS':
      contentHtml = renderReportsScreen(derived);
      break;
    case 'LOANS':
      contentHtml = renderLoansScreen(derived);
      break;
    case 'SETTINGS':
      contentHtml = renderSettingsScreen(derived);
      break;
    case 'WALLET_DETAIL':
      contentHtml = renderWalletDetailScreen(derived);
      break;
    case 'TX_FORM':
      contentHtml = renderTransactionFormScreen(derived);
      break;
    case 'TRANSFER_FORM':
      contentHtml = renderTransferFormScreen(derived);
      break;
    case 'ADJUST_FORM':
      contentHtml = renderAdjustBalanceScreen(derived);
      break;
    case 'CATEGORIES':
      contentHtml = renderCategoriesScreen(derived);
      break;
    case 'RECURRING':
      contentHtml = renderRecurringScreen(derived);
      break;
    case 'BACKUP':
      contentHtml = renderBackupScreen(derived);
      break;
    default:
      contentHtml = renderBalancesScreen(derived);
  }

  shell.innerHTML = contentHtml;
  shell.classList.toggle('blurred-12', AppState.isFabExpanded || document.getElementById('modal-backdrop')?.classList.contains('open'));

  // 4. Bottom Navigation Bar (visible on top-level tabs)
  const topTabs = ['BALANCES', 'HISTORY', 'REPORTS', 'LOANS'];
  if (topTabs.includes(AppState.currentScreen)) {
    const idx = topTabs.indexOf(AppState.currentScreen);
    navWrap.innerHTML = `
      <div class="liquid-bottom-nav">
        <div class="liquid-pill-indicator" style="transform: translateX(${idx * 100}%);"></div>
        <div class="liquid-nav-item ${idx === 0 ? 'active' : ''}" onclick="window.PocketApp.switchMainTab('BALANCES')">
          <span class="material-symbols-rounded">account_balance_wallet</span>
          <span style="font-size:11px;">${t('Balances')}</span>
        </div>
        <div class="liquid-nav-item ${idx === 1 ? 'active' : ''}" onclick="window.PocketApp.switchMainTab('HISTORY')">
          <span class="material-symbols-rounded">receipt_long</span>
          <span style="font-size:11px;">${t('History')}</span>
        </div>
        <div class="liquid-nav-item ${idx === 2 ? 'active' : ''}" onclick="window.PocketApp.switchMainTab('REPORTS')">
          <span class="material-symbols-rounded">bar_chart</span>
          <span style="font-size:11px;">${t('Report')}</span>
        </div>
        <div class="liquid-nav-item ${idx === 3 ? 'active' : ''}" onclick="window.PocketApp.switchMainTab('LOANS')">
          <span class="material-symbols-rounded">handshake</span>
          <span style="font-size:11px;">${t('Loans')}</span>
        </div>
      </div>
    `;
  } else {
    navWrap.innerHTML = '';
  }

  // 5. Speed-Dial FAB on Balances Screen
  if (AppState.currentScreen === 'BALANCES') {
    fabWrap.innerHTML = `
      <div class="pocket-fab-container">
        ${AppState.isFabExpanded ? `
          <div class="pocket-fab-item" onclick="window.PocketApp.openNewTxForm('EXPENSE')">
            <span class="material-symbols-rounded" style="color:var(--expense-text);">add_circle</span>
            <span>${t('New Transaction')}</span>
          </div>
          <div class="pocket-fab-item" onclick="window.PocketApp.openTransferForm()">
            <span class="material-symbols-rounded" style="color:var(--transfer-text);">swap_horiz</span>
            <span>${t('Wallet Transfer')}</span>
          </div>
          <div class="pocket-fab-item" onclick="window.PocketApp.openAdjustForm()">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">tune</span>
            <span>${t('Adjust Balance')}</span>
          </div>
          <div class="pocket-fab-item" onclick="window.PocketApp.openWalletDialog()">
            <span class="material-symbols-rounded" style="color:var(--income-text);">account_balance_wallet</span>
            <span>${t('New Wallet')}</span>
          </div>
        ` : ''}
        <button class="pocket-fab-main ${AppState.isFabExpanded ? 'expanded' : ''}" onclick="window.PocketApp.toggleFab()" aria-label="Quick Actions">
          <span class="material-symbols-rounded" style="font-size:28px;">add</span>
        </button>
      </div>
    `;
  } else {
    fabWrap.innerHTML = '';
  }
}

// ============================================================================
// ONBOARDING SCREENS (PAGE 1 WELCOME & PAGE 2 SETUP)
// ============================================================================
function renderOnboardingScreen() {
  const step = AppState.settings.onboardingStep || 'PAGE_1_WELCOME';
  const isBangla = (AppState.settings.appLanguage || 'EN').toUpperCase() === 'BN';

  if (step === 'PAGE_1_WELCOME') {
    return `
      <div style="display:flex;flex-direction:column;justify-content:space-between;min-height:100dvh;padding:24px;">
        <div style="display:flex;justify-content:flex-end;">
          <div class="pocket-card-elevated" style="display:inline-flex;padding:3px;border-radius:999px;">
            <button onclick="window.PocketApp.setLanguage('EN')" style="border:none;padding:6px 14px;border-radius:999px;font-weight:700;font-size:12px;cursor:pointer;background:${!isBangla ? 'var(--accent-color)' : 'transparent'};color:${!isBangla ? '#FFF' : 'var(--text-secondary)'};">EN</button>
            <button onclick="window.PocketApp.setLanguage('BN')" style="border:none;padding:6px 14px;border-radius:999px;font-weight:700;font-size:12px;cursor:pointer;background:${isBangla ? 'var(--accent-color)' : 'transparent'};color:${isBangla ? '#FFF' : 'var(--text-secondary)'};">বাংলা</button>
          </div>
        </div>

        <div style="display:flex;flex-direction:column;align-items:center;text-align:center;margin:auto 0;">
          <img src="./assets/ic_pocket_logo.png" alt="Pocket Logo" style="width:112px;height:112px;border-radius:26px;box-shadow:0 16px 40px rgba(0,0,0,0.5);" />
          <h1 style="margin:20px 0 6px;font-size:2.2rem;font-weight:800;">Pocket</h1>
          <p style="margin:0 0 32px;color:var(--text-muted);font-size:0.95rem;">${t('Personal Finance, Wallets & Loan Khata')}</p>

          <div style="width:100%;max-width:380px;display:flex;flex-direction:column;gap:14px;">
            <button class="pocket-btn-primary" style="width:100%;min-height:54px;border-radius:16px;" onclick="window.PocketApp.onboardingGoogleSignIn()">
              <span class="material-symbols-rounded">cloud_Queue</span>
              <span>${t('Sign in with Google')}</span>
            </button>
            <button class="pocket-btn-outlined" style="width:100%;min-height:54px;border-radius:16px;" onclick="window.PocketApp.onboardingContinueOffline()">
              <span>${t('Continue without logging in')}</span>
            </button>
          </div>
        </div>
        <div style="height:16px;"></div>
      </div>
    `;
  }

  // PAGE 2 SETUP
  const primaryNames = new Set(['cash', 'bank', 'bkash']);
  const primaryWallets = AppState.wallets.filter(w => primaryNames.has(w.name.toLowerCase()));
  const extraWallets = AppState.wallets.filter(w => !primaryNames.has(w.name.toLowerCase()));
  const visibleWallets = AppState.onboardingShowAllWallets ? [...primaryWallets, ...extraWallets] : primaryWallets;
  const photoUri = AppState.settings.profilePhotoUri || AppState.googleUser?.photoUrl || '';

  return `
    <div style="padding:18px 16px 48px;display:flex;flex-direction:column;gap:16px;">
      <!-- Top Back & Switch Account -->
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 12px;border-radius:12px;" onclick="window.PocketApp.onboardingBackToPage1()">
          <span class="material-symbols-rounded" style="font-size:18px;">arrow_back</span>
          <span>${t('Back')}</span>
        </button>
        ${AppState.googleUser ? `
          <span style="font-size:0.8rem;color:var(--accent-color);font-weight:600;">${escHtml(AppState.googleUser.email)}</span>
        ` : ''}
      </div>

      <!-- Profile Avatar + Name -->
      <div class="pocket-card" style="padding:16px;display:flex;align-items:center;gap:14px;">
        <div onclick="window.PocketApp.triggerProfilePhotoUpload()" style="width:62px;height:62px;border-radius:50%;border:2px solid var(--accent-color);background:var(--surface-elevated);display:flex;align-items:center;justify-content:center;cursor:pointer;overflow:hidden;flex-shrink:0;">
          ${photoUri
            ? `<img src="${escHtml(photoUri)}" style="width:100%;height:100%;object-fit:cover;" />`
            : `<span class="material-symbols-rounded" style="font-size:28px;color:var(--accent-color);">add_a_photo</span>`
          }
        </div>
        <div style="flex:1;">
          <label class="pocket-input-label">${t('Your Name')}</label>
          <input type="text" class="pocket-input" placeholder="${t('Enter your name')}" value="${escHtml(AppState.settings.userName || AppState.googleUser?.displayName || '')}" oninput="window.PocketApp.setOnboardingName(this.value)" />
        </div>
      </div>

      <!-- Starter Wallets Selection -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div>
          <h3 style="margin:0;font-size:1.05rem;font-weight:700;">${t('Choose Wallets & Current Balances')}</h3>
          ${!SubscriptionManager.isSubscriptionActive(AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt) ? `
            <div style="font-size:0.8rem;color:var(--text-secondary);margin-top:2px;">
              ${t('Free tier: Choose any 3 default wallets')} (${AppState.onboardingSelectedIds.size}/3)
            </div>
          ` : ''}
        </div>
        ${visibleWallets.map(w => {
          const sel = AppState.onboardingSelectedIds.has(w.id);
          const balVal = AppState.onboardingBalances[w.id] ?? '';
          return `
            <div class="${sel ? 'pocket-card-selected' : 'pocket-card-elevated'}" style="padding:12px;transition:all 0.16s ease;">
              <div style="display:flex;align-items:center;gap:10px;cursor:pointer;" onclick="window.PocketApp.toggleOnboardingWallet(${w.id})">
                <input type="checkbox" ${sel ? 'checked' : ''} style="width:18px;height:18px;accent-color:var(--accent-color);pointer-events:none;" />
                ${renderIconBadge(w.iconName, w.colorHex, 36, w.name)}
                <span style="font-weight:700;flex:1;">${escHtml(w.name)}</span>
              </div>
              ${sel ? `
                <div style="margin-top:10px;">
                  <input type="number" step="any" class="pocket-input" placeholder="${t('Current Balance')} (৳ 0)" value="${escHtml(balVal)}" oninput="window.PocketApp.setOnboardingWalletBalance(${w.id}, this.value)" />
                </div>
              ` : ''}
            </div>
          `;
        }).join('')}

        <button class="pocket-btn-outlined" style="width:100%;" onclick="window.PocketApp.toggleOnboardingMoreWallets()">
          <span class="material-symbols-rounded">${AppState.onboardingShowAllWallets ? 'expand_less' : 'expand_more'}</span>
          <span>${AppState.onboardingShowAllWallets ? t('Show less wallets') : t(`Show more wallets (${extraWallets.length} more)`)}</span>
        </button>
      </div>

      <!-- Existing Loan Section -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <h3 style="margin:0;font-size:1.05rem;font-weight:700;">${t('Existing loan')}</h3>
        ${AppState.onboardingLoans.map((ln, idx) => `
          <div class="pocket-card-elevated" style="padding:12px;display:flex;flex-direction:column;gap:10px;">
            <div style="display:flex;gap:8px;align-items:center;">
              <button class="pocket-chip ${ln.direction === 'LENT' ? 'selected' : ''}" style="flex:1;justify-content:center;" onclick="window.PocketApp.updateOnboardingLoan(${idx}, 'direction', 'LENT')">${t('I gave')}</button>
              <button class="pocket-chip ${ln.direction === 'BORROWED' ? 'selected' : ''}" style="flex:1;justify-content:center;" onclick="window.PocketApp.updateOnboardingLoan(${idx}, 'direction', 'BORROWED')">${t('I took')}</button>
              ${AppState.onboardingLoans.length > 1 ? `
                <button class="pocket-btn-outlined" style="min-height:36px;padding:4px 8px;" onclick="window.PocketApp.removeOnboardingLoan(${idx})">
                  <span class="material-symbols-rounded" style="font-size:18px;">close</span>
                </button>
              ` : ''}
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
              <input type="text" class="pocket-input" placeholder="${t('Person name')}" value="${escHtml(ln.personName)}" oninput="window.PocketApp.updateOnboardingLoan(${idx}, 'personName', this.value)" />
              <input type="number" step="any" class="pocket-input" placeholder="${t('Amount')} (৳)" value="${escHtml(ln.amount)}" oninput="window.PocketApp.updateOnboardingLoan(${idx}, 'amount', this.value)" />
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
              <div>
                <label class="pocket-input-label">${ln.direction === 'LENT' ? t('Date of Giving Loan') : t('Date of Taking Loan')}</label>
                <input type="date" class="pocket-input" value="${new Date(ln.loanDateMs || Date.now()).toISOString().slice(0, 10)}" onchange="window.PocketApp.updateOnboardingLoanDate(${idx}, 'loanDateMs', this.value)" />
              </div>
              <div>
                <label class="pocket-input-label">${t('Due Date')} (${t('Optional')})</label>
                <input type="date" class="pocket-input" value="${ln.dueDateMs ? new Date(ln.dueDateMs).toISOString().slice(0, 10) : ''}" onchange="window.PocketApp.updateOnboardingLoanDate(${idx}, 'dueDateMs', this.value)" />
              </div>
            </div>
          </div>
        `).join('')}

        <button class="pocket-btn-outlined" style="width:100%;" onclick="window.PocketApp.addOnboardingLoanRow()">
          <span class="material-symbols-rounded">add</span>
          <span>${t('Add More Loan')}</span>
        </button>
      </div>

      <button class="pocket-btn-primary" style="width:100%;min-height:54px;border-radius:16px;font-size:1rem;" onclick="window.PocketApp.finishOnboarding()">
        <span>${t('Get started')}</span>
      </button>
    </div>
  `;
}

// ============================================================================
// APP LOCK PIN SCREEN
// ============================================================================
function renderAppLockScreen() {
  return `
    <div style="min-height:100dvh;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px;text-align:center;">
      <img src="./assets/ic_pocket_logo.png" alt="Pocket" style="width:84px;height:84px;border-radius:20px;margin-bottom:16px;" />
      <h2 style="margin:0 0 6px;">${t('Pocket is Locked')}</h2>
      <p style="margin:0 0 20px;color:var(--text-muted);font-size:0.9rem;">${t('Enter your App PIN to unlock')}</p>
      <div class="pocket-card" style="padding:20px;width:100%;max-width:340px;display:flex;flex-direction:column;gap:14px;">
        <input id="unlock-pin-input" type="password" inputmode="numeric" maxlength="6" class="pocket-input" style="text-align:center;font-size:1.5rem;letter-spacing:8px;" placeholder="••••" onkeydown="if(event.key==='Enter') window.PocketApp.submitUnlockPin()" />
        <button class="pocket-btn-primary" style="width:100%;" onclick="window.PocketApp.submitUnlockPin()">
          <span class="material-symbols-rounded">lock_open</span>
          <span>Unlock</span>
        </button>
      </div>
    </div>
  `;
}

// ============================================================================
// TAB 1: BALANCES / WALLETS OVERVIEW SCREEN
// ============================================================================
function renderBalancesScreen(derived) {
  const s = AppState.settings;
  const hide = s.hideBalancesPrivacy;
  const symbol = '৳';
  const monthLabel = new Date(AppState.selectedYear, AppState.selectedMonth, 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  const profileName = s.userName || AppState.googleUser?.displayName || '';
  const profilePhoto = s.profilePhotoUri || AppState.googleUser?.photoUrl || '';

  // Master Budget Progress
  const masterBudget = Number(s.masterMonthlyBudget) || 0;
  const spent = derived.monthlyReport.totalExpenses;
  const budgetPct = masterBudget > 0 ? Math.min(100, Math.round((spent / masterBudget) * 100)) : 0;
  const daysInMonth = new Date(AppState.selectedYear, AppState.selectedMonth + 1, 0).getDate();
  const isCurrMonth = new Date().getFullYear() === AppState.selectedYear && new Date().getMonth() === AppState.selectedMonth;
  const remDays = Math.max(1, daysInMonth - (isCurrMonth ? new Date().getDate() : 1) + 1);
  const safePerDay = AccountingEngine.roundCurrency(Math.max(0, masterBudget - spent) / remDays);

  // Due Recurring Bills
  const nowMs = Date.now();
  const dueRecurring = AppState.recurringTransactions.filter(r => r.isActive !== false && Number(r.nextDueDate) <= nowMs);

  return `
    <div style="padding:12px 16px 110px;display:flex;flex-direction:column;gap:14px;">
      <!-- Top Header: App Logo + Pocket Name (opens Settings) + Free Badge + Month Selector -->
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;cursor:pointer;" onclick="window.PocketApp.navigateTo('SETTINGS')">
          <img src="./assets/ic_pocket_logo.png" alt="Pocket" style="width:44px;height:44px;border-radius:12px;" />
          <span style="font-size:1.55rem;font-weight:800;">Pocket</span>
          ${!derived.isSubscriptionActive ? `
            <span onclick="event.stopPropagation();window.PocketApp.openUpgradeDialog();" style="padding:3px 8px;border-radius:8px;font-size:11px;font-weight:700;background:rgba(var(--accent-rgb),0.2);border:1px solid rgba(var(--accent-rgb),0.5);color:var(--accent-color);">Free</span>
          ` : ''}
        </div>

        <!-- Month Selector Pill -->
        <div class="pocket-card-elevated" style="display:inline-flex;align-items:center;padding:4px 8px;border-radius:999px;gap:4px;">
          <button onclick="window.PocketApp.changeMonth(-1)" style="background:none;border:none;color:var(--text-primary);cursor:pointer;display:flex;align-items:center;padding:4px;">
            <span class="material-symbols-rounded" style="font-size:18px;">chevron_left</span>
          </button>
          <span style="font-size:0.82rem;font-weight:700;min-width:74px;text-align:center;">${PocketLocalizer.toBanglaDigits(monthLabel)}</span>
          <button onclick="window.PocketApp.changeMonth(1)" style="background:none;border:none;color:var(--text-primary);cursor:pointer;display:flex;align-items:center;padding:4px;">
            <span class="material-symbols-rounded" style="font-size:18px;">chevron_right</span>
          </button>
        </div>
      </div>

      <!-- 1st Big Hero Box: Total Wallet Balance + Profile Badge + Green Income & Red Expenses -->
      <div class="pocket-card-hero" style="padding:20px;">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;">
          <div style="flex:1;">
            <div style="display:flex;align-items:center;gap:6px;">
              <span style="font-size:0.86rem;font-weight:600;color:var(--text-secondary);cursor:pointer;" onclick="window.PocketApp.navigateTo('SETTINGS')">${t('Total wallet balance')}</span>
              <button onclick="window.PocketApp.togglePrivacy()" style="background:none;border:none;color:var(--text-secondary);cursor:pointer;display:flex;align-items:center;padding:4px;" aria-label="Toggle Balance Privacy">
                <span class="material-symbols-rounded" style="font-size:18px;">${hide ? 'visibility_off' : 'visibility'}</span>
              </button>
            </div>
            <div class="tabular-money-lg" style="margin-top:4px;cursor:pointer;" onclick="window.PocketApp.navigateTo('SETTINGS')">
              ${hide ? `${symbol} •••••` : AccountingEngine.formatMoney(derived.totalAvailableBalance, symbol)}
            </div>
            ${Object.entries(derived.foreignCurrencyTotals).map(([code, bal]) => {
              const sym = AccountingEngine.currencySymbolForCode(code);
              return `<div class="tabular-money-sm" style="color:var(--text-secondary);margin-top:2px;">${hide ? `${sym} •••• ${code}` : `${AccountingEngine.formatMoney(bal, sym)} ${code}`}</div>`;
            }).join('')}
          </div>

          <!-- Right-aligned User Profile Badge -->
          <div onclick="window.PocketApp.navigateTo('SETTINGS')" style="display:flex;flex-direction:column;align-items:center;cursor:pointer;gap:4px;">
            <div style="width:58px;height:58px;border-radius:50%;border:2px solid var(--accent-color);background:var(--surface-elevated);display:flex;align-items:center;justify-content:center;overflow:hidden;">
              ${profilePhoto
                ? `<img src="${escHtml(profilePhoto)}" style="width:100%;height:100%;object-fit:cover;" />`
                : `<span class="material-symbols-rounded" style="font-size:28px;color:var(--accent-color);">person</span>`
              }
            </div>
            ${profileName ? `<span style="font-size:0.74rem;font-weight:600;color:var(--text-secondary);max-width:86px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escHtml(profileName)}</span>` : ''}
          </div>
        </div>

        <!-- Income & Expenses Row (Green & Red text directly without inner boxes) -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-top:16px;">
          <div style="cursor:pointer;padding:4px 0;" onclick="window.PocketApp.openHistoryWithType('INCOME')">
            <div style="display:flex;align-items:center;gap:4px;color:var(--income-text);font-size:0.8rem;font-weight:600;">
              <span class="material-symbols-rounded" style="font-size:16px;">trending_up</span>
              <span>${t('Income')}</span>
            </div>
            <div class="tabular-money-md" style="color:var(--income-text);margin-top:2px;">
              ${hide ? `${symbol} ••••` : AccountingEngine.formatMoney(derived.monthlyReport.totalIncome, symbol, false)}
            </div>
          </div>

          <div style="cursor:pointer;padding:4px 0;" onclick="window.PocketApp.openHistoryWithType('EXPENSE')">
            <div style="display:flex;align-items:center;gap:4px;color:var(--expense-text);font-size:0.8rem;font-weight:600;">
              <span class="material-symbols-rounded" style="font-size:16px;">trending_down</span>
              <span>${t('Expenses')}</span>
            </div>
            <div class="tabular-money-md" style="color:var(--expense-text);margin-top:2px;">
              ${hide ? `${symbol} ••••` : AccountingEngine.formatMoney(derived.monthlyReport.totalExpenses, symbol, false)}
            </div>
          </div>
        </div>

        ${masterBudget > 0 ? `
          <div style="margin-top:14px;cursor:pointer;" onclick="window.PocketApp.switchMainTab('REPORTS')">
            <div style="display:flex;justify-content:space-between;font-size:0.75rem;font-weight:600;margin-bottom:6px;">
              <span style="color:var(--text-secondary);">${t(`Monthly Budget (${budgetPct}%)`)}</span>
              <span style="color:${spent > masterBudget ? 'var(--expense-text)' : 'var(--accent-color)'};">
                ${hide
                  ? `${symbol} •••• / day`
                  : spent > masterBudget
                    ? `Over budget by ${AccountingEngine.formatMoney(spent - masterBudget, symbol, false)}`
                    : `Safe: ${AccountingEngine.formatMoney(safePerDay, symbol, false)}/day (${remDays}d left)`
                }
              </span>
            </div>
            <div style="width:100%;height:6px;border-radius:3px;background:var(--surface-elevated);overflow:hidden;">
              <div style="width:${budgetPct}%;height:100%;background:${spent > masterBudget ? 'var(--expense-text)' : 'var(--accent-color)'};"></div>
            </div>
          </div>
        ` : ''}
      </div>

      <!-- Due Today / Overdue Recurring Bills Banner -->
      ${dueRecurring.length > 0 ? `
        <div class="pocket-card" style="padding:16px;border-color:rgba(var(--accent-rgb),0.65);display:flex;flex-direction:column;gap:12px;">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <div style="display:flex;align-items:center;gap:8px;font-weight:700;">
              <span class="material-symbols-rounded" style="color:var(--accent-color);font-size:20px;">event_repeat</span>
              <span>${t(`Recurring Due (${dueRecurring.length})`)}</span>
            </div>
            <button onclick="window.PocketApp.navigateTo('RECURRING')" style="background:none;border:none;color:var(--accent-color);font-weight:700;cursor:pointer;">${t('Manage All')}</button>
          </div>
          ${dueRecurring.map(rec => {
            const isIncome = rec.type === 'INCOME';
            const isFixed = rec.amountMode !== 'MANUAL';
            return `
              <div class="pocket-card-elevated" style="padding:12px;display:flex;flex-direction:column;gap:10px;">
                <div style="display:flex;justify-content:space-between;align-items:center;">
                  <div>
                    <div style="font-weight:700;">${escHtml(rec.title)}</div>
                    <div style="font-size:0.76rem;color:var(--text-muted);">${isFixed ? 'Fixed subscription' : 'Manual amount input'}</div>
                  </div>
                  <div class="tabular-money-md" style="color:${isIncome ? 'var(--income-text)' : 'var(--expense-text)'};">
                    ${isFixed && rec.amount > 0 ? (hide ? '৳ ••••' : AccountingEngine.formatMoney(rec.amount, '৳')) : t('Manual')}
                  </div>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">
                  <button class="pocket-btn-outlined" style="min-height:42px;" onclick="window.PocketApp.snoozeRecurring(${rec.id})">${t('Remind Tomorrow')}</button>
                  <button class="pocket-btn-primary" style="min-height:42px;" onclick="window.PocketApp.payRecurringNow(${rec.id})">${isIncome ? t('Receive') : t('Pay')}</button>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      ` : ''}

      <!-- Active Wallets List -->
      <div style="display:flex;flex-direction:column;gap:12px;">
        ${derived.walletSummaries.map((ws, idx) => {
          const w = ws.wallet;
          const curr = String(w.currency || 'BDT').toUpperCase();
          const wSym = AccountingEngine.currencySymbolForCode(curr);
          return `
            <div class="pocket-card pocket-clickable" style="padding:14px 16px;display:flex;align-items:center;gap:14px;" onclick="window.PocketApp.openWalletDetail(${w.id})">
              ${renderIconBadge(w.iconName, w.colorHex, 46, w.name)}
              <div style="flex:1;min-width:0;">
                <div style="display:flex;align-items:center;gap:6px;">
                  <span style="font-size:1rem;font-weight:700;">${escHtml(t(w.name))}</span>
                  ${curr !== 'BDT' ? `<span style="padding:2px 6px;border-radius:6px;font-size:10px;font-weight:700;background:rgba(var(--accent-rgb),0.15);color:var(--accent-color);">${curr}</span>` : ''}
                </div>
                ${!w.includeInTotalBalance ? `<div style="font-size:0.74rem;color:var(--text-muted);">${t('Not in total balance')}</div>` : ''}
                ${w.type === 'CARD' && w.creditLimit > 0 ? `
                  <div style="font-size:0.73rem;color:var(--text-secondary);margin-top:2px;">
                    Limit: ${hide ? `${wSym} ••••` : AccountingEngine.formatMoney(w.creditLimit, wSym, false)}
                  </div>
                ` : ''}
              </div>
              <div style="text-align:right;display:flex;align-items:center;gap:8px;">
                <div>
                  <div class="tabular-money-md" style="color:${ws.currentBalance < 0 ? 'var(--expense-text)' : 'var(--text-primary)'};">
                    ${hide ? `${wSym} ••••` : AccountingEngine.formatMoney(ws.currentBalance, wSym)}
                  </div>
                </div>
                <div style="display:flex;flex-direction:column;gap:2px;" onclick="event.stopPropagation()">
                  ${idx > 0 ? `<button onclick="window.PocketApp.moveWalletOrder(${w.id}, -1)" style="background:none;border:none;color:var(--text-muted);cursor:pointer;padding:0;line-height:1;" title="Move Up"><span class="material-symbols-rounded" style="font-size:16px;">keyboard_arrow_up</span></button>` : ''}
                  ${idx < derived.walletSummaries.length - 1 ? `<button onclick="window.PocketApp.moveWalletOrder(${w.id}, 1)" style="background:none;border:none;color:var(--text-muted);cursor:pointer;padding:0;line-height:1;" title="Move Down"><span class="material-symbols-rounded" style="font-size:16px;">keyboard_arrow_down</span></button>` : ''}
                </div>
              </div>
            </div>
          `;
        }).join('')}
      </div>

      <!-- Savings Goals Section (if any exist or button to add) -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">savings</span>
            <span style="font-weight:700;font-size:0.95rem;">${t('Savings Goals')}</span>
          </div>
          <button class="pocket-chip" onclick="window.PocketApp.openSavingsGoalDialog()">
            <span class="material-symbols-rounded" style="font-size:16px;">add</span>
            <span>${t('New')}</span>
          </button>
        </div>
        ${AppState.savingsGoals.length === 0 ? `
          <div style="font-size:0.82rem;color:var(--text-muted);">${t('Track dedicated targets like Emergency Fund, Gadget, or Hajj/Trip.')}</div>
        ` : AppState.savingsGoals.map(g => {
          const pct = g.targetAmount > 0 ? Math.min(100, Math.round(((g.savedAmount || 0) / g.targetAmount) * 100)) : 0;
          return `
            <div class="pocket-card-elevated" style="padding:12px;display:flex;flex-direction:column;gap:8px;">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <span style="font-weight:700;">${escHtml(g.title)}</span>
                <span class="tabular-money-sm" style="color:var(--accent-color);">${hide ? '৳ ••••' : `${AccountingEngine.formatMoney(g.savedAmount || 0, '৳', false)} / ${AccountingEngine.formatMoney(g.targetAmount, '৳', false)}`} (${pct}%)</span>
              </div>
              <div style="width:100%;height:6px;border-radius:3px;background:rgba(255,255,255,0.08);overflow:hidden;">
                <div style="width:${pct}%;height:100%;background:var(--income-text);"></div>
              </div>
              <div style="display:flex;justify-content:flex-end;gap:8px;">
                <button class="pocket-chip" onclick="window.PocketApp.fundSavingsGoal(${g.id}, true)">+ ${t('Add Savings')}</button>
                <button class="pocket-chip" onclick="window.PocketApp.deleteSavingsGoal(${g.id})" style="color:var(--expense-text);">${t('Delete')}</button>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

// ============================================================================
// WALLET DETAIL & RUNNING BALANCE RECONCILIATION SCREEN
// ============================================================================
function renderWalletDetailScreen(derived) {
  const wId = Number(AppState.selectedWalletDetailId);
  const ws = derived.walletSummaries.find(s => s.wallet.id === wId) || derived.walletSummaries[0];
  if (!ws) return renderBalancesScreen(derived);
  const w = ws.wallet;
  const hide = AppState.settings.hideBalancesPrivacy;
  const sym = AccountingEngine.currencySymbolForCode(w.currency);
  const runningRows = AccountingEngine.buildWalletRunningBalanceLedger(w, derived.unifiedLedger);

  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <!-- Top Bar -->
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
            <span class="material-symbols-rounded">arrow_back</span>
          </button>
          <h2 style="margin:0;font-size:1.2rem;font-weight:800;">${escHtml(t(w.name))}</h2>
        </div>
        <div style="display:flex;gap:8px;">
          <button class="pocket-chip" onclick="window.PocketApp.openAdjustForm(${w.id})">
            <span class="material-symbols-rounded" style="font-size:16px;">tune</span>
            <span>${t('Adjust Balance')}</span>
          </button>
          <button class="pocket-chip" onclick="window.PocketApp.openWalletDialog(${w.id})">
            <span class="material-symbols-rounded" style="font-size:16px;">edit</span>
            <span>${t('Edit')}</span>
          </button>
        </div>
      </div>

      <!-- Summary Hero -->
      <div class="pocket-card-hero" style="padding:20px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:14px;">
          ${renderIconBadge(w.iconName, w.colorHex, 52, w.name)}
          <div>
            <div style="font-size:0.8rem;color:var(--text-secondary);">${t('Current Balance')}</div>
            <div class="tabular-money-lg">${hide ? `${sym} •••••` : AccountingEngine.formatMoney(ws.currentBalance, sym)}</div>
          </div>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;padding-top:10px;border-top:1px solid var(--divider-subtle);font-size:0.78rem;">
          <div>
            <div style="color:var(--text-muted);">${t('Opening Balance')}</div>
            <div class="tabular-money-sm">${hide ? `${sym} ••••` : AccountingEngine.formatMoney(ws.openingBalance, sym)}</div>
          </div>
          <div>
            <div style="color:var(--income-text);">+ Inflows</div>
            <div class="tabular-money-sm" style="color:var(--income-text);">${hide ? `${sym} ••••` : AccountingEngine.formatMoney(ws.incomeTotal + ws.transfersIn + ws.loansBorrowedIn + ws.loanRepaymentsReceivedIn, sym)}</div>
          </div>
          <div>
            <div style="color:var(--expense-text);">− Outflows</div>
            <div class="tabular-money-sm" style="color:var(--expense-text);">${hide ? `${sym} ••••` : AccountingEngine.formatMoney(ws.expenseTotal + ws.transfersOut + ws.transferFees + ws.loansLentOut + ws.loanRepaymentsPaidOut, sym)}</div>
          </div>
        </div>
      </div>

      <!-- Running Balance Statement -->
      <h3 style="margin:4px 0 0;font-size:1rem;">Wallet Ledger (${runningRows.length})</h3>
      ${runningRows.length === 0 ? `
        <div class="pocket-card" style="padding:28px;text-align:center;color:var(--text-muted);">
          ${t('No transactions yet')}
        </div>
      ` : runningRows.map(r => {
        const dStr = new Date(r.entry.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        return `
          <div class="pocket-card" style="padding:12px 14px;display:flex;justify-content:space-between;align-items:center;gap:10px;">
            <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1;">
              ${renderIconBadge(r.entry.iconName, r.entry.colorHex, 38)}
              <div style="min-width:0;">
                <div style="font-weight:700;font-size:0.92rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escHtml(t(r.entry.title))}</div>
                <div style="font-size:0.75rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${dStr} • ${escHtml(r.entry.subtitle)}</div>
              </div>
            </div>
            <div style="text-align:right;flex-shrink:0;">
              <div class="tabular-money-sm" style="color:${r.deltaAmount >= 0 ? 'var(--income-text)' : 'var(--expense-text)'};">
                ${r.deltaAmount >= 0 ? '+' : '−'}${hide ? `${sym} ••••` : AccountingEngine.formatAmount(Math.abs(r.deltaAmount), sym)}
              </div>
              <div class="tabular-money-sm" style="font-size:0.72rem;color:var(--text-muted);">
                Bal: ${hide ? `${sym} ••••` : AccountingEngine.formatAmount(r.runningBalanceAfter, sym)}
              </div>
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

// ============================================================================
// ADD / EDIT TRANSACTION FORM SCREEN
// ============================================================================
function renderTransactionFormScreen(derived) {
  const editing = AppState.editingTx;
  const type = AppState.txFormInitialType || (editing?.type || 'EXPENSE');
  const activeWallets = derived.walletSummaries.map(s => s.wallet);
  const selectedWalletId = AppState.txFormInitialWalletId || editing?.walletId || AppState.settings.defaultWalletId || activeWallets[0]?.id || 1;
  const filteredCats = AppState.categories.filter(c => !c.isArchived && (type === 'INCOME' ? c.type === 'INCOME' : c.type === 'EXPENSE'));
  const selectedCatId = AppState._txFormCatId || editing?.categoryId || filteredCats[0]?.id || 1;
  const amountVal = AppState._txFormAmount !== undefined ? AppState._txFormAmount : (editing ? String(editing.amount) : '');
  const feeVal = AppState._txFormFee !== undefined ? AppState._txFormFee : '';
  const noteVal = AppState._txFormNote !== undefined ? AppState._txFormNote : (editing?.note || '');
  const refVal = AppState._txFormRef !== undefined ? AppState._txFormRef : (editing?.reference || '');
  const dateMs = AppState._txFormDateMs || editing?.date || Date.now();
  const attachmentUri = AppState._txFormAttachment !== undefined ? AppState._txFormAttachment : (editing?.attachmentUri || '');
  const evaluated = evaluateMathExpression(amountVal);

  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <!-- Top Bar with Paste SMS & CSV Import -->
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
            <span class="material-symbols-rounded">arrow_back</span>
          </button>
          <h2 style="margin:0;font-size:1.15rem;font-weight:800;">${editing ? t('Edit Record') : t('New Record')}</h2>
        </div>
        <div style="display:flex;gap:8px;">
          <button class="pocket-chip" onclick="window.PocketApp.openSmsPasteDialog()">
            <span class="material-symbols-rounded" style="font-size:16px;color:var(--accent-color);">content_paste</span>
            <span style="color:var(--accent-color);">${t('Paste SMS')}</span>
          </button>
          <button class="pocket-chip" onclick="window.PocketApp.openCsvBankImportDialog()">
            <span class="material-symbols-rounded" style="font-size:16px;color:var(--accent-color);">upload_file</span>
            <span style="color:var(--accent-color);">CSV</span>
          </button>
        </div>
      </div>

      <!-- Type Switcher -->
      <div class="pocket-card" style="padding:6px;display:grid;grid-template-columns:1fr 1fr;gap:6px;">
        <button onclick="window.PocketApp.setTxFormType('EXPENSE')" style="border:none;min-height:42px;border-radius:12px;font-weight:700;cursor:pointer;background:${type === 'EXPENSE' ? 'var(--expense-text)' : 'transparent'};color:${type === 'EXPENSE' ? '#FFF' : 'var(--text-secondary)'};">
          ${t('− Expense')}
        </button>
        <button onclick="window.PocketApp.setTxFormType('INCOME')" style="border:none;min-height:42px;border-radius:12px;font-weight:700;cursor:pointer;background:${type === 'INCOME' ? 'var(--income-text)' : 'transparent'};color:${type === 'INCOME' ? '#000' : 'var(--text-secondary)'};">
          ${t('+ Income')}
        </button>
      </div>

      <!-- Amount + MiniCalculatorBar -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <label class="pocket-input-label">${t('Amount (Supports inline math: e.g. 250+80*2)')}</label>
        <input id="tx-amount-input" type="text" class="pocket-input tabular-money-md" placeholder="0" value="${escHtml(amountVal)}" oninput="window.PocketApp.updateTxAmountInput(this.value)" />
        <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
          ${['+', '-', '×', '÷', '='].map(op => `
            <button type="button" class="pocket-chip" style="min-width:42px;justify-content:center;font-weight:800;" onclick="window.PocketApp.appendMathOperator('${op}')">${op}</button>
          `).join('')}
          ${evaluated !== null ? `<span class="tabular-money-sm" style="margin-left:auto;color:var(--accent-color);">= ${AccountingEngine.formatMoney(evaluated, '৳')}</span>` : ''}
        </div>

        ${type === 'EXPENSE' && !editing ? `
          <div style="margin-top:4px;">
            <label class="pocket-input-label">${t('Fee / Charge (Optional)')}</label>
            <div style="display:flex;gap:8px;align-items:center;">
              <input id="tx-fee-input" type="number" step="any" class="pocket-input" placeholder="0" value="${escHtml(feeVal)}" oninput="window.PocketApp.setTxDraftField('_txFormFee', this.value)" style="flex:1;" />
              <button type="button" class="pocket-chip" onclick="window.PocketApp.applyQuickFeePercent(1.85)">1.85% bKash</button>
              <button type="button" class="pocket-chip" onclick="window.PocketApp.applyQuickFeePercent(1.4)">1.4% Nagad</button>
            </div>
          </div>
        ` : ''}
      </div>

      <!-- Wallet Selector -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <label class="pocket-input-label">${t('Wallet')}</label>
        <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;padding-bottom:2px;">
          ${derived.walletSummaries.map(ws => {
            const isSel = ws.wallet.id === selectedWalletId;
            const wSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency);
            return `
              <div class="${isSel ? 'pocket-card-selected' : 'pocket-card-elevated'}" style="padding:8px 12px;display:flex;align-items:center;gap:8px;cursor:pointer;flex-shrink:0;" onclick="window.PocketApp.selectTxWallet(${ws.wallet.id})">
                ${renderIconBadge(ws.wallet.iconName, ws.wallet.colorHex, 28, ws.wallet.name)}
                <div>
                  <div style="font-size:0.82rem;font-weight:700;">${escHtml(t(ws.wallet.name))}</div>
                  <div class="tabular-money-sm" style="font-size:0.72rem;color:var(--text-muted);">${AccountingEngine.formatMoney(ws.currentBalance, wSym)}</div>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Category Selector -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <label class="pocket-input-label">${t('Category')}</label>
          <button type="button" class="pocket-chip" onclick="window.PocketApp.navigateTo('CATEGORIES')">+ ${t('Category')}</button>
        </div>
        <div style="display:flex;flex-wrap:wrap;gap:8px;">
          ${filteredCats.map(cat => {
            const isSel = cat.id === selectedCatId;
            return `
              <button type="button" class="pocket-chip ${isSel ? 'selected' : ''}" onclick="window.PocketApp.selectTxCategory(${cat.id})">
                <span>${escHtml(t(cat.name))}</span>
              </button>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Date, Note, Reference & Photo Attachment -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Date')}</label>
          <input type="date" class="pocket-input" value="${new Date(dateMs).toISOString().slice(0, 10)}" onchange="window.PocketApp.setTxDate(this.value)" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Reason / Note')}</label>
          <input type="text" class="pocket-input" placeholder="${t('What was this for?')}" value="${escHtml(noteVal)}" oninput="window.PocketApp.setTxDraftField('_txFormNote', this.value)" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Reference / TrxID')} (${t('Optional')})</label>
          <input type="text" class="pocket-input" placeholder="e.g. TrxID / Invoice #" value="${escHtml(refVal)}" oninput="window.PocketApp.setTxDraftField('_txFormRef', this.value)" />
        </div>

        <!-- Receipt Photo Attachment -->
        <div class="pocket-card-elevated" style="padding:12px;display:flex;justify-content:space-between;align-items:center;">
          <div style="display:flex;align-items:center;gap:10px;">
            ${attachmentUri
              ? `<img src="${escHtml(attachmentUri)}" style="width:44px;height:44px;border-radius:8px;object-fit:cover;border:1px solid var(--accent-color);" />`
              : `<span class="material-symbols-rounded" style="color:var(--accent-color);">add_a_photo</span>`
            }
            <div>
              <div style="font-size:0.85rem;font-weight:600;">${attachmentUri ? t('Photo Attached') : t('Receipt / Bill Photo (Optional)')}</div>
              <div style="font-size:0.74rem;color:var(--text-muted);">${t('Warranty slip, bill, or MFS screenshot')}</div>
            </div>
          </div>
          <div style="display:flex;gap:6px;">
            ${attachmentUri ? `<button type="button" class="pocket-chip" style="color:var(--expense-text);" onclick="window.PocketApp.setTxAttachment('')">${t('Remove')}</button>` : ''}
            <button type="button" class="pocket-chip" onclick="window.PocketApp.triggerTxPhotoUpload()">${attachmentUri ? t('Change') : t('Attach')}</button>
          </div>
        </div>
      </div>

      <div style="display:flex;gap:10px;">
        ${editing ? `
          <button type="button" class="pocket-btn-danger" style="flex:1;" onclick="window.PocketApp.deleteTransaction(${editing.id})">
            <span class="material-symbols-rounded">delete</span>
            <span>${t('Delete')}</span>
          </button>
        ` : ''}
        <button type="button" class="pocket-btn-primary" style="flex:2;" onclick="window.PocketApp.saveTransactionForm()">
          <span class="material-symbols-rounded">check</span>
          <span>${t('Save')}</span>
        </button>
      </div>
    </div>
  `;
}

// ============================================================================
// TRANSFER FORM & ADJUST BALANCE SCREENS
// ============================================================================
function renderTransferFormScreen(derived) {
  const wallets = derived.walletSummaries;
  const srcId = AppState._trSrcId || wallets[0]?.wallet?.id || 1;
  const dstId = AppState._trDstId || wallets[1]?.wallet?.id || wallets[0]?.wallet?.id || 2;
  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;align-items:center;gap:10px;">
        <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
          <span class="material-symbols-rounded">arrow_back</span>
        </button>
        <h2 style="margin:0;font-size:1.15rem;font-weight:800;">${t('Wallet Transfer')}</h2>
      </div>

      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <label class="pocket-input-label">${t('From Wallet')}</label>
        <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;">
          ${wallets.map(ws => `
            <button type="button" class="pocket-chip ${ws.wallet.id === srcId ? 'selected' : ''}" onclick="window.PocketApp.setTransferWallet('src', ${ws.wallet.id})">
              ${escHtml(t(ws.wallet.name))} (${AccountingEngine.formatMoney(ws.currentBalance, '৳')})
            </button>
          `).join('')}
        </div>

        <label class="pocket-input-label" style="margin-top:6px;">${t('To Wallet')}</label>
        <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;">
          ${wallets.map(ws => `
            <button type="button" class="pocket-chip ${ws.wallet.id === dstId ? 'selected' : ''}" onclick="window.PocketApp.setTransferWallet('dst', ${ws.wallet.id})">
              ${escHtml(t(ws.wallet.name))} (${AccountingEngine.formatMoney(ws.currentBalance, '৳')})
            </button>
          `).join('')}
        </div>

        <div class="pocket-input-group" style="margin-top:6px;">
          <label class="pocket-input-label">${t('Transfer Amount')} (৳)</label>
          <input id="transfer-amount-input" type="number" step="any" class="pocket-input" placeholder="0" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Transfer Fee (Optional)')} (৳)</label>
          <input id="transfer-fee-input" type="number" step="any" class="pocket-input" placeholder="0" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Note')}</label>
          <input id="transfer-note-input" type="text" class="pocket-input" placeholder="e.g. Cash Out / Send Money" />
        </div>

        <button class="pocket-btn-primary" style="width:100%;margin-top:6px;" onclick="window.PocketApp.submitTransferForm(${srcId}, ${dstId})">
          <span class="material-symbols-rounded">swap_horiz</span>
          <span>${t('Complete Transfer')}</span>
        </button>
      </div>
    </div>
  `;
}

function renderAdjustBalanceScreen(derived) {
  const wallets = derived.walletSummaries;
  const targetId = AppState.adjustInitialWalletId || wallets[0]?.wallet?.id || 1;
  const currentSummary = wallets.find(w => w.wallet.id === targetId) || wallets[0];
  const sym = AccountingEngine.currencySymbolForCode(currentSummary?.wallet?.currency);

  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;align-items:center;gap:10px;">
        <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
          <span class="material-symbols-rounded">arrow_back</span>
        </button>
        <h2 style="margin:0;font-size:1.15rem;font-weight:800;">${t('Adjust Current Balance')}</h2>
      </div>

      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <label class="pocket-input-label">${t('Select Wallet')}</label>
        <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;">
          ${wallets.map(ws => `
            <button type="button" class="pocket-chip ${ws.wallet.id === targetId ? 'selected' : ''}" onclick="window.PocketApp.selectAdjustWallet(${ws.wallet.id})">
              ${escHtml(t(ws.wallet.name))}
            </button>
          `).join('')}
        </div>

        <div class="pocket-card-elevated" style="padding:12px;display:flex;justify-content:space-between;align-items:center;">
          <span style="font-size:0.84rem;color:var(--text-secondary);">${t('Calculated App Balance')}</span>
          <span class="tabular-money-md">${AccountingEngine.formatMoney(currentSummary?.currentBalance || 0, sym)}</span>
        </div>

        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Actual Real-World Balance Today')} (${sym})</label>
          <input id="adjust-actual-input" type="number" step="any" class="pocket-input tabular-money-md" placeholder="0" value="${currentSummary?.currentBalance || 0}" />
        </div>

        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Reason for balance adjustment')}</label>
          <input id="adjust-reason-input" type="text" class="pocket-input" placeholder="${t('Reconciliation')}" value="Reconciliation" />
        </div>

        <button class="pocket-btn-primary" style="width:100%;" onclick="window.PocketApp.submitAdjustBalance(${targetId})">
          <span class="material-symbols-rounded">check</span>
          <span>${t('Save Reconciliation')}</span>
        </button>
      </div>
    </div>
  `;
}

// ============================================================================
// TAB 2: TRANSACTION HISTORY SCREEN
// ============================================================================
function renderHistoryScreen(derived) {
  const q = AppState.historySearch.trim().toLowerCase();
  const tf = AppState.historyTypeFilter;
  const wf = AppState.historyWalletFilter;
  const hide = AppState.settings.hideBalancesPrivacy;

  const filtered = derived.unifiedLedger.filter(item => {
    if (tf !== 'ALL') {
      if (tf === 'LOAN' && !item.kind.startsWith('LOAN')) return false;
      else if (tf !== 'LOAN' && item.kind !== tf) return false;
    }
    if (wf && item.walletId !== wf && item.secondaryWalletId !== wf) return false;
    if (q) {
      const hay = `${item.title} ${item.subtitle} ${item.reference} ${item.tags} ${item.rawAmount}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  return `
    <div style="padding:12px 16px 110px;display:flex;flex-direction:column;gap:12px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <h2 style="margin:0;font-size:1.35rem;font-weight:800;">${t('History')}</h2>
        <button class="pocket-chip" onclick="window.PocketApp.openNewTxForm('EXPENSE')">
          <span class="material-symbols-rounded" style="font-size:16px;">add</span>
          <span>${t('New Record')}</span>
        </button>
      </div>

      <!-- Search Input -->
      <input type="text" class="pocket-input" placeholder="${t('Search')} note, category, TrxID, amount..." value="${escHtml(AppState.historySearch)}" oninput="window.PocketApp.setHistorySearch(this.value)" />

      <!-- Type Filters -->
      <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;">
        ${[
          ['ALL', 'All'],
          ['EXPENSE', 'Expense'],
          ['INCOME', 'Income'],
          ['TRANSFER', 'Transfer'],
          ['LOAN', 'Loans'],
          ['ADJUSTMENT', 'Adjustments']
        ].map(([code, label]) => `
          <button class="pocket-chip ${tf === code ? 'selected' : ''}" onclick="window.PocketApp.setHistoryTypeFilter('${code}')">${t(label)}</button>
        `).join('')}
      </div>

      <!-- Wallet Filters -->
      <div class="no-scrollbar" style="display:flex;gap:6px;overflow-x:auto;">
        <button class="pocket-chip ${!wf ? 'selected' : ''}" onclick="window.PocketApp.setHistoryWalletFilter(null)">${t('All')} Wallets</button>
        ${derived.walletSummaries.map(ws => `
          <button class="pocket-chip ${wf === ws.wallet.id ? 'selected' : ''}" onclick="window.PocketApp.setHistoryWalletFilter(${ws.wallet.id})">${escHtml(t(ws.wallet.name))}</button>
        `).join('')}
      </div>

      <!-- Ledger Items -->
      ${filtered.length === 0 ? `
        <div class="pocket-card" style="padding:32px;text-align:center;color:var(--text-muted);">
          <span class="material-symbols-rounded" style="font-size:40px;color:var(--accent-color);">receipt_long</span>
          <div style="margin-top:8px;font-weight:700;">${t('No matching transactions')}</div>
        </div>
      ` : filtered.map(item => {
        const dStr = new Date(item.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        const canEditTx = item.kind === 'EXPENSE' || item.kind === 'INCOME' || item.kind === 'EXPENSE_REVERSAL';
        const colorVar = item.kind === 'INCOME' || item.kind === 'EXPENSE_REVERSAL'
          ? 'var(--income-text)'
          : item.kind === 'EXPENSE'
            ? 'var(--expense-text)'
            : item.kind === 'TRANSFER'
              ? 'var(--transfer-text)'
              : 'var(--loan-text)';
        return `
          <div class="pocket-card ${canEditTx ? 'pocket-clickable' : ''}" style="padding:13px 15px;display:flex;justify-content:space-between;align-items:center;gap:12px;" ${canEditTx ? `onclick="window.PocketApp.editTransaction(${item.entityId})"` : ''}>
            <div style="display:flex;align-items:center;gap:12px;min-width:0;flex:1;">
              ${renderIconBadge(item.iconName, item.colorHex, 40)}
              <div style="min-width:0;flex:1;">
                <div style="display:flex;align-items:center;gap:6px;">
                  <span style="font-weight:700;font-size:0.94rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escHtml(t(item.title))}</span>
                  ${item.attachmentUri ? `<span class="material-symbols-rounded" style="font-size:15px;color:var(--accent-color);">photo_camera</span>` : ''}
                </div>
                <div style="font-size:0.76rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                  ${PocketLocalizer.toBanglaDigits(dStr)} • ${escHtml(item.subtitle)}
                </div>
                ${item.reference ? `<div style="font-size:0.7rem;color:var(--accent-color);">Ref: ${escHtml(item.reference)}</div>` : ''}
              </div>
            </div>
            <div style="text-align:right;flex-shrink:0;">
              <div class="tabular-money-md" style="color:${colorVar};">
                ${hide ? '৳ ••••' : item.signedAmountText}
              </div>
              <div style="font-size:0.68rem;color:var(--text-muted);font-weight:700;">${t(item.badgeLabel)}</div>
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

// ============================================================================
// TAB 3: REPORTS & ANALYTICS SCREEN
// ============================================================================
function renderReportsScreen(derived) {
  const rep = derived.monthlyReport;
  const hide = AppState.settings.hideBalancesPrivacy;
  const sym = '৳';
  const monthLabel = new Date(AppState.selectedYear, AppState.selectedMonth, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  // Last 6 months bar chart data
  const sixMonths = [];
  for (let i = 5; i >= 0; i--) {
    const d = new Date(AppState.selectedYear, AppState.selectedMonth - i, 1);
    const r = AccountingEngine.calculateMonthlyReport(
      d.getFullYear(),
      d.getMonth(),
      AppState.categories,
      AppState.transactions,
      AppState.transfers,
      AppState.loans,
      AppState.loanPayments,
      AppState.transactionSplits
    );
    sixMonths.push({
      year: d.getFullYear(),
      month: d.getMonth(),
      label: d.toLocaleDateString('en-US', { month: 'short' }),
      income: r.totalIncome,
      expense: r.totalExpenses
    });
  }
  const maxBar = Math.max(1, ...sixMonths.flatMap(m => [m.income, m.expense]));

  return `
    <div style="padding:12px 16px 110px;display:flex;flex-direction:column;gap:14px;">
      <!-- Top Header + Month Selector + Export PDF -->
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <h2 style="margin:0;font-size:1.35rem;font-weight:800;">${t('Report')}</h2>
        <div style="display:flex;gap:8px;align-items:center;">
          <button class="pocket-chip" onclick="window.print()">
            <span class="material-symbols-rounded" style="font-size:16px;color:var(--accent-color);">picture_as_pdf</span>
            <span>${t('Export PDF')}</span>
          </button>
        </div>
      </div>

      <!-- Month Selector Row -->
      <div class="pocket-card" style="padding:10px 14px;display:flex;justify-content:space-between;align-items:center;">
        <button class="pocket-chip" onclick="window.PocketApp.changeMonth(-1)">
          <span class="material-symbols-rounded" style="font-size:18px;">chevron_left</span>
        </button>
        <span style="font-weight:800;font-size:1rem;">${PocketLocalizer.toBanglaDigits(monthLabel)}</span>
        <button class="pocket-chip" onclick="window.PocketApp.changeMonth(1)">
          <span class="material-symbols-rounded" style="font-size:18px;">chevron_right</span>
        </button>
      </div>

      <!-- KPI Summary Cards -->
      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;">
        <div class="pocket-card" style="padding:14px;">
          <div style="font-size:0.74rem;color:var(--income-text);font-weight:700;">${t('Total Income')}</div>
          <div class="tabular-money-md" style="color:var(--income-text);margin-top:4px;">${hide ? '৳ ••••' : AccountingEngine.formatMoney(rep.totalIncome, sym, false)}</div>
        </div>
        <div class="pocket-card" style="padding:14px;">
          <div style="font-size:0.74rem;color:var(--expense-text);font-weight:700;">${t('Total Expenses')}</div>
          <div class="tabular-money-md" style="color:var(--expense-text);margin-top:4px;">${hide ? '৳ ••••' : AccountingEngine.formatMoney(rep.totalExpenses, sym, false)}</div>
        </div>
        <div class="pocket-card" style="padding:14px;">
          <div style="font-size:0.74rem;color:var(--text-secondary);font-weight:700;">${t('Net Savings')}</div>
          <div class="tabular-money-md" style="color:${rep.netSavings >= 0 ? 'var(--income-text)' : 'var(--expense-text)'};margin-top:4px;">${hide ? '৳ ••••' : AccountingEngine.formatMoney(rep.netSavings, sym, false)}</div>
        </div>
      </div>

      <!-- 6-Month Interactive Bar Chart -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-weight:700;font-size:0.95rem;">6-Month Cashflow Comparison</span>
          <span style="font-size:0.75rem;color:var(--text-muted);">Savings Rate: ${PocketLocalizer.toBanglaDigits(rep.savingsRatePercent)}%</span>
        </div>
        <div style="display:grid;grid-template-columns:repeat(6, 1fr);gap:8px;height:130px;align-items:end;padding-top:12px;">
          ${sixMonths.map(m => {
            const incH = Math.max(4, Math.round((m.income / maxBar) * 96));
            const expH = Math.max(4, Math.round((m.expense / maxBar) * 96));
            const isSel = m.year === AppState.selectedYear && m.month === AppState.selectedMonth;
            return `
              <div onclick="window.PocketApp.selectYearMonth(${m.year}, ${m.month})" style="display:flex;flex-direction:column;align-items:center;gap:6px;cursor:pointer;">
                <div style="display:flex;align-items:flex-end;gap:4px;height:100px;">
                  <div style="width:10px;height:${incH}px;background:var(--income-text);border-radius:4px 4px 0 0;" title="Income: ${m.income}"></div>
                  <div style="width:10px;height:${expH}px;background:var(--expense-text);border-radius:4px 4px 0 0;" title="Expense: ${m.expense}"></div>
                </div>
                <span style="font-size:0.72rem;font-weight:${isSel ? '800' : '500'};color:${isSel ? 'var(--accent-color)' : 'var(--text-muted)'};">${m.label}</span>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Master Monthly Budget Editor Card -->
      <div class="pocket-card" style="padding:16px;display:flex;justify-content:space-between;align-items:center;">
        <div>
          <div style="font-weight:700;font-size:0.94rem;">${t('Master Monthly Budget')}</div>
          <div style="font-size:0.78rem;color:var(--text-muted);">
            ${AppState.settings.masterMonthlyBudget > 0 ? AccountingEngine.formatMoney(AppState.settings.masterMonthlyBudget, sym, false) : t('0 (Disabled)')}
          </div>
        </div>
        <button class="pocket-chip" onclick="window.PocketApp.openMasterBudgetDialog()">
          <span class="material-symbols-rounded" style="font-size:16px;">edit</span>
          <span>${t('Set Budget')}</span>
        </button>
      </div>

      <!-- Expense Breakdown by Category -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <h3 style="margin:0;font-size:1rem;font-weight:700;">${t('Expense Breakdown by Category')}</h3>
        ${rep.expenseBreakdown.length === 0 ? `
          <div style="font-size:0.84rem;color:var(--text-muted);padding:12px 0;">${t('No transactions yet')}</div>
        ` : rep.expenseBreakdown.map(item => `
          <div style="display:flex;flex-direction:column;gap:6px;">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <div style="display:flex;align-items:center;gap:10px;">
                ${renderIconBadge(item.iconName, item.colorHex, 32)}
                <div>
                  <div style="font-weight:700;font-size:0.9rem;">${escHtml(t(item.categoryName))}</div>
                  <div style="font-size:0.72rem;color:var(--text-muted);">${PocketLocalizer.toBanglaDigits(item.percentage)}% • ${PocketLocalizer.toBanglaDigits(item.transactionCount)} txns</div>
                </div>
              </div>
              <div class="tabular-money-sm">${hide ? '৳ ••••' : AccountingEngine.formatMoney(item.amount, sym)}</div>
            </div>
            <div style="width:100%;height:6px;border-radius:3px;background:var(--surface-elevated);overflow:hidden;">
              <div style="width:${Math.min(100, item.percentage)}%;height:100%;background:${item.colorHex};"></div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>
  `;
}

// ============================================================================
// TAB 4: LOANS & PERSON KHATA SCREEN
// ============================================================================
function renderLoansScreen(derived) {
  const hide = AppState.settings.hideBalancesPrivacy;
  const sym = '৳';
  const activeLoans = derived.loanSummaries.filter(s => s.status !== 'SETTLED');
  const totalLentOut = AccountingEngine.roundCurrency(
    activeLoans.filter(s => s.loan.direction === 'LENT').reduce((sum, s) => sum + s.outstandingPrincipal, 0)
  );
  const totalBorrowedIn = AccountingEngine.roundCurrency(
    activeLoans.filter(s => s.loan.direction === 'BORROWED').reduce((sum, s) => sum + s.outstandingPrincipal, 0)
  );

  const displayedLoans = derived.loanSummaries.filter(s => {
    if (AppState.loanFilter === 'ACTIVE') return s.status !== 'SETTLED';
    if (AppState.loanFilter === 'OVERDUE') return s.status === 'OVERDUE';
    if (AppState.loanFilter === 'SETTLED') return s.status === 'SETTLED';
    return true;
  });

  return `
    <div style="padding:12px 16px 110px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <h2 style="margin:0;font-size:1.35rem;font-weight:800;">${t('Loans')}</h2>
        <button class="pocket-btn-primary" style="min-height:40px;padding:8px 14px;border-radius:12px;" onclick="window.PocketApp.openCreateLoanDialog()">
          <span class="material-symbols-rounded" style="font-size:18px;">add</span>
          <span>${t('New Loan')}</span>
        </button>
      </div>

      <!-- Summary Row: You Will Get (Lent) vs You Owe (Borrowed) -->
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
        <div class="pocket-card" style="padding:16px;">
          <div style="font-size:0.78rem;color:var(--income-text);font-weight:700;">${t('You Will Get (Lent)')}</div>
          <div class="tabular-money-md" style="color:var(--income-text);margin-top:4px;">
            ${hide ? '৳ ••••' : AccountingEngine.formatMoney(totalLentOut, sym)}
          </div>
        </div>
        <div class="pocket-card" style="padding:16px;">
          <div style="font-size:0.78rem;color:var(--loan-text);font-weight:700;">${t('You Owe (Borrowed)')}</div>
          <div class="tabular-money-md" style="color:var(--loan-text);margin-top:4px;">
            ${hide ? '৳ ••••' : AccountingEngine.formatMoney(totalBorrowedIn, sym)}
          </div>
        </div>
      </div>

      <!-- Filter & Person Khata Toggle -->
      <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap;">
        <div style="display:flex;gap:6px;">
          ${['ACTIVE', 'OVERDUE', 'SETTLED', 'ALL'].map(f => `
            <button class="pocket-chip ${AppState.loanFilter === f ? 'selected' : ''}" onclick="window.PocketApp.setLoanFilter('${f}')">${t(f.charAt(0) + f.slice(1).toLowerCase())}</button>
          `).join('')}
        </div>
        <button class="pocket-chip ${AppState.loanKhataView ? 'selected' : ''}" onclick="window.PocketApp.toggleLoanKhataView()">
          <span class="material-symbols-rounded" style="font-size:16px;">menu_book</span>
          <span>${t('Person Khata')}</span>
        </button>
      </div>

      ${displayedLoans.length === 0 ? `
        <div class="pocket-card" style="padding:32px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px;">
          <span class="material-symbols-rounded" style="font-size:42px;color:var(--accent-color);">handshake</span>
          <div style="font-weight:700;">${t('No loans recorded')}</div>
          <button class="pocket-btn-primary" onclick="window.PocketApp.openCreateLoanDialog()">${t('Create First Loan')}</button>
        </div>
      ` : AppState.loanKhataView ? (
        derived.khataSummaries.map(khata => {
          const net = khata.netReceivableMinusPayable;
          const kSym = khata.currencySymbol || sym;
          const headline = Math.abs(net) < 0.005
            ? `${khata.personName} is settled`
            : net > 0
              ? `${khata.personName} owes you net ${AccountingEngine.formatMoney(net, kSym, false)}`
              : `You owe ${khata.personName} net ${AccountingEngine.formatMoney(Math.abs(net), kSym, false)}`;
          return `
            <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <div>
                  <div style="font-size:1.05rem;font-weight:700;">${escHtml(khata.personName)}</div>
                  <div style="font-size:0.86rem;font-weight:600;color:${net >= 0 ? 'var(--income-text)' : 'var(--loan-text)'};">${escHtml(headline)}</div>
                </div>
                <button class="pocket-chip" onclick="window.PocketApp.shareKhataText('${escHtml(khata.personName)}')">
                  <span class="material-symbols-rounded" style="font-size:16px;">share</span>
                  <span>${t('Share')}</span>
                </button>
              </div>
            </div>
          `;
        }).join('')
      ) : (
        displayedLoans.map(s => {
          const isLent = s.loan.direction === 'LENT';
          const w = AppState.wallets.find(x => x.id === s.loan.initialWalletId);
          const lSym = AccountingEngine.currencySymbolForCode(w?.currency);
          const dueStr = s.loan.dueDate ? new Date(s.loan.dueDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'Tap to set deadline date';
          return `
            <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <div>
                  <div style="font-size:1.05rem;font-weight:700;">${escHtml(s.loan.personName)}</div>
                  <div style="font-size:0.78rem;color:var(--text-muted);">
                    ${isLent ? t('Lent') : t('Borrowed')} • Original ${AccountingEngine.formatMoney(s.originalPrincipal, lSym, false)} • ${escHtml(s.walletName)}
                  </div>
                </div>
                <span style="padding:4px 8px;border-radius:8px;font-size:0.72rem;font-weight:700;background:${s.status === 'SETTLED' ? 'var(--income-container)' : s.status === 'OVERDUE' ? 'var(--expense-container)' : 'var(--loan-container)'};color:${s.status === 'SETTLED' ? 'var(--income-text)' : s.status === 'OVERDUE' ? 'var(--expense-text)' : 'var(--loan-text)'};">
                  ${s.status}
                </span>
              </div>

              <!-- Deadline Box -->
              <div class="pocket-card-elevated pocket-clickable" style="padding:10px 12px;display:flex;justify-content:space-between;align-items:center;border-color:${s.status === 'OVERDUE' ? 'var(--expense-text)' : 'rgba(var(--accent-rgb),0.45)'};" onclick="window.PocketApp.openLoanDeadlineDialog(${s.loan.id})">
                <div style="display:flex;align-items:center;gap:8px;">
                  <span class="material-symbols-rounded" style="font-size:18px;color:var(--accent-color);">event</span>
                  <div>
                    <div style="font-size:0.72rem;color:var(--text-muted);">${isLent ? 'Deadline to receive (partial or full)' : 'Deadline to pay (partial or full)'}</div>
                    <div style="font-size:0.84rem;font-weight:700;">${dueStr}</div>
                  </div>
                </div>
                <span class="material-symbols-rounded" style="font-size:18px;color:var(--accent-color);">edit_calendar</span>
              </div>

              <div style="display:flex;justify-content:space-between;align-items:center;">
                <div>
                  <div class="tabular-money-md" style="color:${isLent ? 'var(--income-text)' : 'var(--loan-text)'};">
                    Outstanding: ${hide ? `${lSym} ••••` : AccountingEngine.formatMoney(s.outstandingPrincipal, lSym)}
                  </div>
                  ${s.principalRepaid > 0 ? `<div style="font-size:0.75rem;color:var(--text-muted);">Repaid so far: ${AccountingEngine.formatMoney(s.principalRepaid, lSym)}</div>` : ''}
                </div>
                <button class="pocket-btn-outlined" style="min-height:42px;padding:6px 14px;" onclick="window.PocketApp.openLoanRepaymentDialog(${s.loan.id})">
                  ${s.status === 'SETTLED' ? t('Ledger') : t('Repay / View')}
                </button>
              </div>
            </div>
          `;
        }).join('')
      )}
    </div>
  `;
}

// ============================================================================
// SETTINGS SCREEN (100% Matching CategoriesLoansSettingsScreens.kt)
// ============================================================================
function renderSettingsScreen(derived) {
  const s = AppState.settings;
  const isPremium = derived.isSubscriptionActive;
  const tier = derived.activeTier;
  const isDark = String(s.themeMode || 'DARK').toUpperCase() !== 'LIGHT';
  const isBangla = String(s.appLanguage || 'EN').toUpperCase() === 'BN';
  const profilePhoto = s.profilePhotoUri || AppState.googleUser?.photoUrl || '';
  const isAdmin = PocketCloudSync.isDeveloperAdmin();

  const accentPresets = [
    ['#F15A24', 'Orange'],
    ['#14B8A6', 'Teal'],
    ['#3B82F6', 'Blue'],
    ['#8B5CF6', 'Violet'],
    ['#F43F5E', 'Rose']
  ];

  const boxPresets = [
    ['#141416', 'Carbon'],
    ['#1E293B', 'Slate'],
    ['#172554', 'Midnight'],
    ['#14532D', 'Emerald'],
    ['#31102F', 'Burgundy']
  ];

  return `
    <div style="padding:12px 16px 88px;display:flex;flex-direction:column;gap:12px;">
      <!-- Top Bar -->
      <div style="display:flex;align-items:center;gap:10px;">
        <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
          <span class="material-symbols-rounded">arrow_back</span>
        </button>
        <h2 style="margin:0;font-size:1.3rem;font-weight:800;">${t('Settings')}</h2>
      </div>

      <!-- i. Profile Photo & Name -->
      <div class="pocket-card" style="padding:16px;display:flex;align-items:center;gap:14px;">
        <div onclick="window.PocketApp.triggerProfilePhotoUpload()" style="width:56px;height:56px;border-radius:50%;border:2px solid var(--accent-color);background:var(--surface-elevated);display:flex;align-items:center;justify-content:center;cursor:pointer;overflow:hidden;flex-shrink:0;">
          ${profilePhoto
            ? `<img src="${escHtml(profilePhoto)}" style="width:100%;height:100%;object-fit:cover;" />`
            : `<span class="material-symbols-rounded" style="font-size:26px;color:var(--accent-color);">person</span>`
          }
        </div>
        <div style="flex:1;">
          <label class="pocket-input-label">${t('Your Name')}</label>
          <input type="text" class="pocket-input" value="${escHtml(s.userName || AppState.googleUser?.displayName || '')}" oninput="window.PocketApp.updateUserName(this.value)" />
        </div>
      </div>

      <!-- ii. Free / Premium Subscription Banner -->
      <div class="pocket-card pocket-clickable" onclick="window.PocketApp.openUpgradeDialog()" style="padding:16px;background:${isPremium ? 'rgba(16,185,129,0.12)' : 'rgba(241,90,36,0.12)'};border-color:${isPremium ? 'rgba(16,185,129,0.4)' : 'rgba(241,90,36,0.4)'};display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:12px;">
          <div style="width:40px;height:40px;border-radius:50%;background:${isPremium ? 'rgba(16,185,129,0.2)' : 'rgba(241,90,36,0.2)'};display:flex;align-items:center;justify-content:center;">
            <span class="material-symbols-rounded" style="color:${isPremium ? '#10B981' : '#F15A24'};">${isPremium ? 'workspace_premium' : 'lock'}</span>
          </div>
          <div>
            <div style="display:flex;align-items:center;gap:6px;">
              <span style="font-weight:800;color:${isPremium ? '#10B981' : '#F15A24'};">
                ${isPremium ? (tier === 'LIFETIME' ? t('Lifetime Premium') : t('3 Days Premium')) : t('Free Tier Plan')}
              </span>
              <span style="padding:2px 5px;border-radius:4px;font-size:10px;font-weight:900;background:${isPremium ? '#10B981' : '#F15A24'};color:#FFF;">
                ${isPremium ? 'PREMIUM' : 'FREE'}
              </span>
            </div>
            <div style="font-size:0.78rem;color:var(--text-muted);">
              ${isPremium ? t('All features permanently unlocked') : t('Tap to upgrade to Pocket Premium')}
            </div>
          </div>
        </div>
        <span class="material-symbols-rounded" style="color:var(--text-muted);">chevron_right</span>
      </div>

      <!-- Google Account Cloud Sync Card -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--accent-color);">cloud_sync</span>
          <div>
            <div style="font-weight:700;">${t('Google Account Cloud Sync')}</div>
            <div style="font-size:0.78rem;color:var(--text-muted);">
              ${AppState.googleUser ? `${escHtml(AppState.googleUser.email)} ${AppState.lastCloudSyncStatus ? '• ' + AppState.lastCloudSyncStatus : ''}` : t('Offline mode (not signed in)')}
            </div>
          </div>
        </div>
        ${AppState.googleUser ? `
          <div style="display:flex;gap:8px;flex-wrap:wrap;">
            <button class="pocket-btn-primary" style="flex:1;min-height:42px;" onclick="window.PocketApp.syncCloudNow()">
              <span class="material-symbols-rounded" style="font-size:18px;">sync</span>
              <span>${AppState.isCloudSyncing ? t('Syncing...') : t('Sync Now')}</span>
            </button>
            <button class="pocket-btn-outlined" style="min-height:42px;" onclick="window.PocketApp.signOutGoogle()">
              <span>${t('Sign Out')}</span>
            </button>
          </div>
        ` : `
          <button class="pocket-btn-primary" style="width:100%;" onclick="window.PocketApp.signInGoogleFromSettings()">
            <span class="material-symbols-rounded">login</span>
            <span>${t('Sign in with Google')}</span>
          </button>
        `}
      </div>

      <!-- iii. Financial Modules (Home Widget excluded per user instruction) -->
      <div style="font-weight:700;font-size:1rem;margin-top:4px;">${t('Financial Modules')}</div>

      <div class="pocket-card pocket-clickable" style="padding:16px;display:flex;align-items:center;gap:14px;" onclick="window.PocketApp.navigateTo('CATEGORIES')">
        <span class="material-symbols-rounded" style="color:var(--accent-color);">category</span>
        <span style="flex:1;font-weight:700;">${t('Categories')}</span>
        <span class="material-symbols-rounded" style="color:var(--text-muted);">chevron_right</span>
      </div>

      <div class="pocket-card pocket-clickable" style="padding:16px;display:flex;align-items:center;gap:14px;" onclick="window.PocketApp.navigateTo('RECURRING')">
        <span class="material-symbols-rounded" style="color:var(--accent-color);">event_repeat</span>
        <span style="flex:1;font-weight:700;">${t('Recurring Bills')}</span>
        <span class="material-symbols-rounded" style="color:var(--text-muted);">chevron_right</span>
      </div>

      <div class="pocket-card pocket-clickable" style="padding:16px;display:flex;align-items:center;gap:14px;" onclick="window.PocketApp.switchMainTab('REPORTS')">
        <span class="material-symbols-rounded" style="color:var(--accent-color);">receipt_long</span>
        <span style="flex:1;font-weight:700;">${t('Monthly Statement')}</span>
        <span class="material-symbols-rounded" style="color:var(--text-muted);">chevron_right</span>
      </div>

      <div class="pocket-card pocket-clickable" style="padding:16px;display:flex;align-items:center;gap:14px;" onclick="window.PocketApp.navigateTo('BACKUP')">
        <span class="material-symbols-rounded" style="color:var(--accent-color);">backup</span>
        <span style="flex:1;font-weight:700;">${t('Backup')}</span>
        <span class="material-symbols-rounded" style="color:var(--text-muted);">chevron_right</span>
      </div>

      <!-- iv. Preferences -->
      <div style="font-weight:700;font-size:1rem;margin-top:6px;">${t('Preferences')}</div>

      <!-- 1. App theme -->
      <div class="pocket-card" style="padding:16px;display:flex;justify-content:space-between;align-items:center;">
        <span style="font-weight:700;">${t('App theme')}</span>
        <div class="pocket-card-elevated" style="display:inline-flex;padding:4px;border-radius:999px;gap:4px;">
          <button onclick="window.PocketApp.setThemeMode('DARK')" style="border:none;padding:8px 14px;border-radius:999px;font-weight:700;cursor:pointer;background:${isDark ? 'var(--accent-color)' : 'transparent'};color:${isDark ? '#FFF' : 'var(--text-secondary)'};">${t('Dark')}</button>
          <button onclick="window.PocketApp.setThemeMode('LIGHT')" style="border:none;padding:8px 14px;border-radius:999px;font-weight:700;cursor:pointer;background:${!isDark ? 'var(--accent-color)' : 'transparent'};color:${!isDark ? '#FFF' : 'var(--text-secondary)'};">${t('Light')}</button>
        </div>
      </div>

      <!-- 2. Accent color -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--accent-color);">palette</span>
          <span style="font-weight:700;">${t('Accent color')}</span>
        </div>
        <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
          ${accentPresets.map(([hex, label]) => {
            const sel = String(s.accentColorHex || '#F15A24').toUpperCase() === hex;
            const allowed = SubscriptionManager.isAccentColorAllowed(hex, s.subscriptionTier, s.subscriptionExpiresAt);
            return `
              <div onclick="window.PocketApp.selectAccentColor('${hex}')" style="width:38px;height:38px;border-radius:50%;background:${hex};border:${sel ? '2.5px solid var(--text-primary)' : '1px solid var(--card-border)'};display:flex;align-items:center;justify-content:center;cursor:pointer;" title="${label}">
                ${!allowed ? `<span class="material-symbols-rounded" style="font-size:15px;color:#FFF;">lock</span>` : sel ? `<span class="material-symbols-rounded" style="font-size:18px;color:#FFF;">check</span>` : ''}
              </div>
            `;
          }).join('')}
          <label style="width:38px;height:38px;border-radius:50%;background:conic-gradient(red, yellow, lime, aqua, blue, magenta, red);border:1.5px solid var(--card-border);display:flex;align-items:center;justify-content:center;cursor:pointer;" title="Custom Accent Color">
            <input type="color" value="${escHtml(s.accentColorHex || '#F15A24')}" style="opacity:0;width:0;height:0;" onchange="window.PocketApp.selectCustomAccentColor(this.value)" />
            ${!isPremium ? `<span class="material-symbols-rounded" style="font-size:14px;color:#FFF;background:rgba(0,0,0,0.45);border-radius:50%;padding:4px;">lock</span>` : ''}
          </label>
        </div>
      </div>

      <!-- 3. Background wallpaper, Opacity, Box Color & Box Opacity -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--accent-color);">wallpaper</span>
          <span style="font-weight:700;">${t('Background wallpaper & Opacity')}</span>
        </div>
        <div style="display:flex;gap:8px;">
          <button class="pocket-btn-primary" style="flex:1;" onclick="window.PocketApp.triggerWallpaperUpload()">
            <span class="material-symbols-rounded" style="font-size:18px;">upload</span>
            <span>${s.customBackgroundUri ? t('Change Wallpaper') : t('Upload Wallpaper')}</span>
          </button>
          ${s.customBackgroundUri ? `
            <button class="pocket-btn-outlined" onclick="window.PocketApp.removeWallpaper()">${t('Remove')}</button>
          ` : ''}
        </div>

        <div>
          <div style="display:flex;justify-content:space-between;font-size:0.82rem;font-weight:600;margin-bottom:4px;">
            <span>${t('Wallpaper Opacity')}</span>
            <span style="color:var(--accent-color);">${Math.round((Number(s.backgroundOpacity) || 0.25) * 100)}%</span>
          </div>
          <input type="range" min="0" max="1" step="0.02" value="${Number(s.backgroundOpacity) || 0.25}" class="pocket-slider" oninput="window.PocketApp.setWallpaperOpacity(this.value)" />
        </div>

        <hr style="border:none;border-top:1px solid var(--divider-subtle);margin:4px 0;" />

        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-weight:700;font-size:0.88rem;">${t('Box Color')}</span>
          ${s.boxColorHex ? `<button onclick="window.PocketApp.selectBoxColor('')" style="background:none;border:none;color:var(--accent-color);font-weight:700;cursor:pointer;">${t('Reset')}</button>` : ''}
        </div>
        <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
          ${boxPresets.map(([hex, label]) => {
            const eff = s.boxColorHex || (isDark ? '#141416' : '#FFFFFF');
            const sel = eff.toUpperCase() === hex;
            const allowed = SubscriptionManager.isBoxColorAllowed(hex, s.subscriptionTier, s.subscriptionExpiresAt);
            return `
              <div onclick="window.PocketApp.selectBoxColor('${hex}')" style="width:38px;height:38px;border-radius:50%;background:${hex};border:${sel ? '2.5px solid var(--accent-color)' : '1px solid var(--card-border)'};display:flex;align-items:center;justify-content:center;cursor:pointer;" title="${label}">
                ${!allowed ? `<span class="material-symbols-rounded" style="font-size:15px;color:#FFF;">lock</span>` : sel ? `<span class="material-symbols-rounded" style="font-size:18px;color:#FFF;">check</span>` : ''}
              </div>
            `;
          }).join('')}
          ${(() => {
            const isCustomBox = Boolean(s.boxColorHex && !boxPresets.some(([h]) => h.toUpperCase() === String(s.boxColorHex).toUpperCase()));
            return `
              <label style="width:38px;height:38px;border-radius:50%;background:conic-gradient(red, yellow, lime, aqua, blue, magenta, red);border:${isCustomBox ? '2.5px solid var(--accent-color)' : '1.5px solid var(--card-border)'};display:flex;align-items:center;justify-content:center;cursor:pointer;position:relative;" title="${t('Custom Box Color')}" onclick="if(!${isPremium}) { event.preventDefault(); window.PocketApp.openUpgradeDialog(t('Custom box color picker is a Pocket Premium feature. Carbon, Slate, and Box Opacity are free.')); }">
                <input type="color" value="${escHtml(s.boxColorHex || (isDark ? '#141416' : '#FFFFFF'))}" style="opacity:0;width:0;height:0;position:absolute;pointer-events:${isPremium ? 'auto' : 'none'};" onchange="window.PocketApp.selectCustomBoxColor(this.value)" />
                ${!isPremium ? `<span class="material-symbols-rounded" style="font-size:14px;color:#FFF;background:rgba(0,0,0,0.45);border-radius:50%;padding:4px;">lock</span>` : isCustomBox ? `<span class="material-symbols-rounded" style="font-size:18px;color:#FFF;background:rgba(0,0,0,0.45);border-radius:50%;padding:2px;">check</span>` : ''}
              </label>
            `;
          })()}
        </div>

        <div>
          <div style="display:flex;justify-content:space-between;font-size:0.82rem;font-weight:600;margin-bottom:4px;">
            <span>${t('Box Opacity')}</span>
            <span style="color:var(--accent-color);">${Math.round((Number(s.boxOpacity) || 0.50) * 100)}%</span>
          </div>
          <input type="range" min="0.15" max="1" step="0.02" value="${Number(s.boxOpacity) || 0.50}" class="pocket-slider" oninput="window.PocketApp.setBoxOpacity(this.value)" />
        </div>
      </div>

      <!-- 4. Language -->
      <div class="pocket-card" style="padding:16px;display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--accent-color);">language</span>
          <span style="font-weight:700;">${t('Language')}</span>
        </div>
        <div class="pocket-card-elevated" style="display:inline-flex;padding:4px;border-radius:999px;gap:4px;">
          <button onclick="window.PocketApp.setLanguage('EN')" style="border:none;padding:8px 14px;border-radius:999px;font-weight:700;cursor:pointer;background:${!isBangla ? 'var(--accent-color)' : 'transparent'};color:${!isBangla ? '#FFF' : 'var(--text-secondary)'};">English</button>
          <button onclick="window.PocketApp.setLanguage('BN')" style="border:none;padding:8px 14px;border-radius:999px;font-weight:700;cursor:pointer;background:${isBangla ? 'var(--accent-color)' : 'transparent'};color:${isBangla ? '#FFF' : 'var(--text-secondary)'};">বাংলা</button>
        </div>
      </div>

      <!-- 5. Default Wallet & Negative-balance warnings -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <span style="font-weight:700;">${t('Default wallet for new Entries')}</span>
        <div class="no-scrollbar" style="display:flex;gap:8px;overflow-x:auto;">
          ${derived.walletSummaries.map(ws => `
            <button class="pocket-chip ${s.defaultWalletId === ws.wallet.id ? 'selected' : ''}" onclick="window.PocketApp.setDefaultWallet(${ws.wallet.id})">
              ${escHtml(t(ws.wallet.name))}
            </button>
          `).join('')}
        </div>
        <hr style="border:none;border-top:1px solid var(--divider-subtle);margin:2px 0;" />
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span style="font-weight:700;">${t('Negative-balance warnings')}</span>
          <label class="pocket-switch">
            <input type="checkbox" ${s.warnOnNegativeBalance ? 'checked' : ''} onchange="window.PocketApp.toggleNegativeWarning(this.checked)" />
            <span class="pocket-switch-slider"></span>
          </label>
        </div>
      </div>

      <!-- v. Security -->
      <div style="font-weight:700;font-size:1rem;margin-top:4px;">${t('Security')}</div>
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <div style="display:flex;align-items:center;gap:10px;">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">lock</span>
            <span style="font-weight:700;">${t('App Lock (PIN)')}</span>
          </div>
          <label class="pocket-switch">
            <input type="checkbox" ${s.appLockEnabled ? 'checked' : ''} onchange="window.PocketApp.toggleAppLockPin(this.checked)" />
            <span class="pocket-switch-slider"></span>
          </label>
        </div>
      </div>

      <!-- vii. Reset all Data to zero -->
      <button class="pocket-btn-danger" style="width:100%;min-height:52px;border-radius:16px;margin-top:4px;" onclick="window.PocketApp.confirmResetAllToZero()">
        <span class="material-symbols-rounded">delete_forever</span>
        <span>${t('Reset all Data to zero')}</span>
      </button>

      <!-- viii. Feedback & Bugs Report -->
      ${AppState.googleUser ? `
        <button class="pocket-btn-outlined" style="width:100%;min-height:52px;border-radius:16px;" onclick="window.PocketApp.openFeedbackDialog()">
          <span class="material-symbols-rounded" style="color:var(--accent-color);">bug_report</span>
          <span>${t('Feedback & Bugs Report')}</span>
        </button>
      ` : ''}

      <!-- Admin: Manage Activation Codes (Only visible to hadi1617180@gmail.com) -->
      ${isAdmin ? `
        <button class="pocket-btn-primary" style="width:100%;min-height:52px;border-radius:16px;background:#10B981;" onclick="window.PocketApp.openAdminCodeManagerDialog()">
          <span class="material-symbols-rounded">admin_panel_settings</span>
          <span>${t('Admin: Manage Activation Codes')}</span>
        </button>
      ` : ''}

      <!-- ix. App developed by Abdullahil Hadi -->
      <button class="pocket-btn-outlined" style="width:100%;min-height:52px;border-radius:16px;border-color:rgba(var(--accent-rgb),0.65);color:var(--accent-color);" onclick="window.PocketApp.openDeveloperInfoDialog()">
        <span class="material-symbols-rounded" style="color:var(--accent-color);">person</span>
        <span>${t('App developed by Abdullahil Hadi')}</span>
      </button>

      <!-- x. 4-line Centered Acknowledgement for Mahadia Afruj -->
      <div style="text-align:center;padding:8px 8px 4px;display:flex;flex-direction:column;gap:3px;">
        <div style="font-size:0.78rem;font-weight:700;color:var(--text-secondary);">${t('Acknowledgement')}</div>
        <div style="font-size:0.78rem;color:var(--text-secondary);">${t('Special Thanks to my beloved wife')}</div>
        <div style="font-size:1.05rem;font-weight:800;color:var(--text-primary);">${t('💖 Mahadia Afruj 💖')}</div>
        <div style="font-size:0.78rem;color:var(--text-secondary);">${t('for inspiring core architecture, her love and endless support.')}</div>
      </div>
    </div>
  `;
}

// ============================================================================
// CATEGORIES, RECURRING BILLS & BACKUP SUB-SCREENS
// ============================================================================
function renderCategoriesScreen() {
  const expCats = AppState.categories.filter(c => !c.isArchived && c.type === 'EXPENSE');
  const incCats = AppState.categories.filter(c => !c.isArchived && c.type === 'INCOME');
  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
            <span class="material-symbols-rounded">arrow_back</span>
          </button>
          <h2 style="margin:0;font-size:1.2rem;font-weight:800;">${t('Categories & Budgets')}</h2>
        </div>
        <button class="pocket-btn-primary" style="min-height:40px;padding:6px 14px;" onclick="window.PocketApp.openCategoryDialog()">
          <span class="material-symbols-rounded" style="font-size:18px;">add</span>
          <span>${t('New Category')}</span>
        </button>
      </div>

      <h3 style="margin:4px 0 0;font-size:0.95rem;">${t('Expense Categories')} (${expCats.length})</h3>
      ${expCats.map(c => `
        <div class="pocket-card" style="padding:12px 14px;display:flex;justify-content:space-between;align-items:center;">
          <div style="display:flex;align-items:center;gap:12px;">
            ${renderIconBadge(c.iconName, c.colorHex, 38)}
            <div>
              <div style="font-weight:700;">${escHtml(t(c.name))}</div>
              ${c.monthlyBudget > 0 ? `<div style="font-size:0.74rem;color:var(--accent-color);">Budget: ${AccountingEngine.formatMoney(c.monthlyBudget, '৳', false)}/mo</div>` : ''}
            </div>
          </div>
          <div style="display:flex;gap:6px;">
            <button class="pocket-chip" onclick="window.PocketApp.openCategoryDialog(${c.id})">${t('Edit')}</button>
            <button class="pocket-chip" style="color:var(--expense-text);" onclick="window.PocketApp.archiveCategory(${c.id})">${t('Delete')}</button>
          </div>
        </div>
      `).join('')}

      <h3 style="margin:8px 0 0;font-size:0.95rem;">${t('Income Categories')} (${incCats.length})</h3>
      ${incCats.map(c => `
        <div class="pocket-card" style="padding:12px 14px;display:flex;justify-content:space-between;align-items:center;">
          <div style="display:flex;align-items:center;gap:12px;">
            ${renderIconBadge(c.iconName, c.colorHex, 38)}
            <div style="font-weight:700;">${escHtml(t(c.name))}</div>
          </div>
          <div style="display:flex;gap:6px;">
            <button class="pocket-chip" onclick="window.PocketApp.openCategoryDialog(${c.id})">${t('Edit')}</button>
            <button class="pocket-chip" style="color:var(--expense-text);" onclick="window.PocketApp.archiveCategory(${c.id})">${t('Delete')}</button>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderRecurringScreen() {
  const list = AppState.recurringTransactions;
  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div style="display:flex;align-items:center;gap:10px;">
          <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
            <span class="material-symbols-rounded">arrow_back</span>
          </button>
          <h2 style="margin:0;font-size:1.2rem;font-weight:800;">${t('Recurring Bills & Income')}</h2>
        </div>
        <button class="pocket-btn-primary" style="min-height:40px;padding:6px 14px;" onclick="window.PocketApp.openRecurringDialog()">
          <span class="material-symbols-rounded" style="font-size:18px;">add</span>
          <span>${t('New')}</span>
        </button>
      </div>

      ${list.length === 0 ? `
        <div class="pocket-card" style="padding:28px;text-align:center;">
          <span class="material-symbols-rounded" style="font-size:40px;color:var(--accent-color);">event_repeat</span>
          <div style="font-weight:700;margin:8px 0;">No Recurring Bills or Subscriptions Yet</div>
          <button class="pocket-btn-primary" onclick="window.PocketApp.openRecurringDialog()">Add Recurring Transaction</button>
        </div>
      ` : list.map(item => {
        const dStr = new Date(item.nextDueDate || Date.now()).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        return `
          <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <div>
                <div style="font-weight:700;font-size:1rem;">${escHtml(item.title)}</div>
                <div style="font-size:0.78rem;color:var(--text-muted);">${item.frequency || 'MONTHLY'} • Next: ${dStr}</div>
              </div>
              <div class="tabular-money-md" style="color:${item.type === 'INCOME' ? 'var(--income-text)' : 'var(--expense-text)'};">
                ${item.amount > 0 ? AccountingEngine.formatMoney(item.amount, '৳') : t('Manual')}
              </div>
            </div>
            <div style="display:flex;gap:8px;">
              <button class="pocket-btn-primary" style="flex:1;min-height:40px;" onclick="window.PocketApp.payRecurringNow(${item.id})">${item.type === 'INCOME' ? t('Receive') : t('Pay')}</button>
              <button class="pocket-btn-outlined" style="flex:1;min-height:40px;" onclick="window.PocketApp.snoozeRecurring(${item.id})">${t('Remind Tomorrow')}</button>
              <button class="pocket-btn-outlined" style="min-height:40px;color:var(--expense-text);" onclick="window.PocketApp.deleteRecurring(${item.id})">${t('Delete')}</button>
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

function renderBackupScreen(derived) {
  return `
    <div style="padding:12px 16px 48px;display:flex;flex-direction:column;gap:14px;">
      <div style="display:flex;align-items:center;gap:10px;">
        <button class="pocket-btn-outlined" style="min-height:40px;padding:6px 10px;" onclick="window.PocketApp.navigateBack()">
          <span class="material-symbols-rounded">arrow_back</span>
        </button>
        <h2 style="margin:0;font-size:1.2rem;font-weight:800;">${t('Backup, Restore & CSV')}</h2>
      </div>

      <!-- Export JSON Backup -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <div style="font-weight:700;">Export Complete Backup (pocket-backup-v1.json)</div>
        <div style="font-size:0.8rem;color:var(--text-muted);">100% compatible with the Pocket Android app.</div>
        <div style="display:flex;gap:10px;">
          <button class="pocket-btn-primary" style="flex:1;" onclick="window.PocketApp.downloadBackupJson()">
            <span class="material-symbols-rounded">download</span>
            <span>Save JSON</span>
          </button>
          <button class="pocket-btn-outlined" style="flex:1;" onclick="window.PocketApp.previewBackupJson()">
            <span>Preview JSON</span>
          </button>
        </div>
      </div>

      <!-- Import & Restore Backup -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <div style="font-weight:700;">Import & Restore Backup</div>
        <div style="display:flex;gap:10px;">
          <button class="pocket-btn-outlined" style="flex:1;" onclick="window.PocketApp.triggerBackupFileImport()">Select Backup File</button>
          <button class="pocket-btn-primary" style="flex:1;" onclick="window.PocketApp.restoreFromJsonTextarea()">Restore JSON</button>
        </div>
        <textarea id="backup-json-textarea" rows="5" class="pocket-input" placeholder="Paste pocket-backup-v1.json content here..."></textarea>
      </div>

      <!-- Export CSV -->
      <div class="pocket-card" style="padding:16px;display:flex;flex-direction:column;gap:10px;">
        <div style="font-weight:700;">Export CSV for Spreadsheets</div>
        <button class="pocket-btn-outlined" style="width:100%;" onclick="window.PocketApp.downloadLedgerCsv()">
          <span class="material-symbols-rounded">table_view</span>
          <span>Export CSV File (${derived.unifiedLedger.length} rows)</span>
        </button>
      </div>
    </div>
  `;
}

// ============================================================================
// WINDOW GLOBAL ACTIONS & DIALOG CONTROLLERS
// ============================================================================
window.PocketApp = {
  navigateTo,
  navigateBack,
  closeModal,

  switchMainTab(tab) {
    AppState.screenStack = [];
    AppState.currentScreen = tab;
    AppState.isFabExpanded = false;
    window.scrollTo({ top: 0, behavior: 'smooth' });
    renderApp();
  },

  toggleFab() {
    AppState.isFabExpanded = !AppState.isFabExpanded;
    renderApp();
  },

  changeMonth(delta) {
    let m = AppState.selectedMonth + delta;
    let y = AppState.selectedYear;
    if (m < 0) { m = 11; y -= 1; }
    else if (m > 11) { m = 0; y += 1; }
    AppState.selectedMonth = m;
    AppState.selectedYear = y;
    renderApp();
  },

  selectYearMonth(year, month) {
    AppState.selectedYear = year;
    AppState.selectedMonth = month;
    renderApp();
  },

  togglePrivacy() {
    AppState.settings.hideBalancesPrivacy = !AppState.settings.hideBalancesPrivacy;
    saveStateToLocal();
    renderApp();
  },

  setLanguage(code) {
    AppState.settings.appLanguage = code;
    PocketLocalizer.setLanguage(code);
    saveStateToLocal();
    renderApp();
  },

  setThemeMode(mode) {
    AppState.settings.themeMode = mode;
    saveStateToLocal();
    renderApp();
  },

  selectAccentColor(hex) {
    const allowed = SubscriptionManager.isAccentColorAllowed(hex, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt);
    if (!allowed) {
      this.openUpgradeDialog("Orange and Teal are free. Upgrade to Pocket Premium to unlock all prebuilt colors and the custom color wheel.");
      return;
    }
    AppState.settings.accentColorHex = hex;
    saveStateToLocal();
    renderApp();
  },

  selectCustomAccentColor(hex) {
    if (!SubscriptionManager.isSubscriptionActive(AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
      this.openUpgradeDialog("Custom color picker is a Pocket Premium feature.");
      return;
    }
    AppState.settings.accentColorHex = hex;
    saveStateToLocal();
    renderApp();
  },

  selectBoxColor(hex) {
    const allowed = SubscriptionManager.isBoxColorAllowed(hex, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt);
    if (!allowed) {
      this.openUpgradeDialog(t("Carbon and Slate box colors are free. Upgrade to Pocket Premium to unlock Midnight, Emerald, Burgundy, and custom box colors."));
      return;
    }
    AppState.settings.boxColorHex = hex;
    saveStateToLocal();
    applyThemeAndAccentVariables();
    renderApp();
  },

  selectCustomBoxColor(hex) {
    const isPro = SubscriptionManager.isSubscriptionActive(AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt);
    if (!isPro) {
      this.openUpgradeDialog(t("Custom box color picker is a Pocket Premium feature. Carbon, Slate, and Box Opacity are free."));
      return;
    }
    AppState.settings.boxColorHex = hex;
    saveStateToLocal();
    applyThemeAndAccentVariables();
    renderApp();
  },

  setBoxOpacity(val) {
    AppState.settings.boxOpacity = parseFloat(val);
    applyThemeAndAccentVariables();
    saveStateToLocal();
  },

  setWallpaperOpacity(val) {
    AppState.settings.backgroundOpacity = parseFloat(val);
    applyThemeAndAccentVariables();
    saveStateToLocal();
  },

  removeWallpaper() {
    AppState.settings.customBackgroundUri = '';
    saveStateToLocal();
    renderApp();
  },

  updateUserName(val) {
    AppState.settings.userName = val.trim();
    saveStateToLocal();
  },

  setDefaultWallet(wId) {
    AppState.settings.defaultWalletId = wId;
    saveStateToLocal();
    renderApp();
  },

  toggleNegativeWarning(checked) {
    AppState.settings.warnOnNegativeBalance = checked;
    saveStateToLocal();
    renderApp();
  },

  // Onboarding handlers
  async onboardingGoogleSignIn() {
    try {
      const user = await PocketCloudSync.signInWithGoogle();
      AppState.googleUser = user;
      const snap = await PocketCloudSync.pullSnapshot(user.uid);
      if (snap && snap.payload && (snap.profile.hasCompletedOnboarding || snap.payload.wallets.length > 0)) {
        applyBackupPayloadToState(snap.payload, true);
        AppState.settings.hasCompletedOnboarding = true;
        saveStateToLocal(true);
        PocketCloudSync.startRealtimeListeners(user.uid, (remote) => {
          if (remote.updatedAtMillis > AppState.lastAppliedCloudMs) {
            AppState.lastAppliedCloudMs = remote.updatedAtMillis;
            applyBackupPayloadToState(remote.payload, true);
          }
        });
        showSnackbar(`Signed in as ${user.email}. Cloud backup loaded!`);
        renderApp();
      } else {
        AppState.settings.userName = user.displayName || '';
        AppState.settings.profilePhotoUri = user.photoUrl || '';
        AppState.settings.onboardingStep = 'PAGE_2_SETUP';
        saveStateToLocal(true);
        renderApp();
      }
    } catch (e) {
      showSnackbar(e.message || 'Google Sign-In cancelled or failed.');
    }
  },

  onboardingContinueOffline() {
    AppState.settings.onboardingStep = 'PAGE_2_SETUP';
    saveStateToLocal(true);
    renderApp();
  },

  onboardingBackToPage1() {
    AppState.settings.onboardingStep = 'PAGE_1_WELCOME';
    saveStateToLocal(true);
    renderApp();
  },

  setOnboardingName(val) {
    AppState.settings.userName = val;
  },

  toggleOnboardingWallet(wId) {
    const isPro = SubscriptionManager.isSubscriptionActive(AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt);
    if (AppState.onboardingSelectedIds.has(wId)) {
      AppState.onboardingSelectedIds.delete(wId);
    } else {
      if (!isPro && AppState.onboardingSelectedIds.size >= SubscriptionManager.FREE_DEFAULT_WALLETS_LIMIT) {
        this.openUpgradeDialog(t("Free tier allows choosing any 3 default wallets. Upgrade to Pocket Premium for unlimited wallets."));
        return;
      }
      AppState.onboardingSelectedIds.add(wId);
    }
    renderApp();
  },

  setOnboardingWalletBalance(wId, val) {
    AppState.onboardingBalances[wId] = val;
  },

  toggleOnboardingMoreWallets() {
    AppState.onboardingShowAllWallets = !AppState.onboardingShowAllWallets;
    renderApp();
  },

  updateOnboardingLoan(idx, field, val) {
    if (AppState.onboardingLoans[idx]) {
      AppState.onboardingLoans[idx][field] = val;
      if (field === 'direction') renderApp();
    }
  },

  updateOnboardingLoanDate(idx, field, dateStr) {
    if (AppState.onboardingLoans[idx]) {
      AppState.onboardingLoans[idx][field] = dateStr ? new Date(dateStr).getTime() : null;
    }
  },

  addOnboardingLoanRow() {
    AppState.onboardingLoans.push({ direction: 'LENT', personName: '', amount: '', loanDateMs: Date.now(), dueDateMs: null });
    renderApp();
  },

  removeOnboardingLoan(idx) {
    AppState.onboardingLoans.splice(idx, 1);
    renderApp();
  },

  finishOnboarding() {
    const selectedIds = AppState.onboardingSelectedIds.size > 0
      ? AppState.onboardingSelectedIds
      : new Set([1]); // Default to Cash wallet if none checked
    AppState.wallets = AppState.wallets.map(w => ({
      ...w,
      isArchived: !selectedIds.has(w.id),
      openingBalance: selectedIds.has(w.id) ? AccountingEngine.roundCurrency(parseFloat(AppState.onboardingBalances[w.id]) || 0) : 0
    }));
    const firstActive = AppState.wallets.find(w => !w.isArchived) || AppState.wallets[0];
    AppState.settings.defaultWalletId = firstActive.id;

    // Save any initial loans
    for (const ln of AppState.onboardingLoans) {
      const amt = parseFloat(ln.amount) || 0;
      if (ln.personName.trim() && amt > 0) {
        AppState.loans.push({
          id: Date.now() + Math.floor(Math.random() * 1000),
          personName: ln.personName.trim(),
          direction: ln.direction,
          principalAmount: AccountingEngine.roundCurrency(amt),
          initialWalletId: firstActive.id,
          startDate: ln.loanDateMs || Date.now(),
          dueDate: ln.dueDateMs || null,
          note: 'Initial Loan',
          attachmentUri: ''
        });
      }
    }

    AppState.settings.hasCompletedOnboarding = true;
    AppState.settings.onboardingStep = 'COMPLETED';
    AppState.currentScreen = 'BALANCES';
    saveStateToLocal(false);
    renderApp();
  },

  // Wallets & Transactions
  openWalletDetail(wId) {
    AppState.selectedWalletDetailId = wId;
    navigateTo('WALLET_DETAIL');
  },

  moveWalletOrder(wId, dir) {
    const active = AppState.wallets.filter(w => !w.isArchived).sort((a, b) => a.sortOrder - b.sortOrder);
    const idx = active.findIndex(w => w.id === wId);
    const targetIdx = idx + dir;
    if (idx < 0 || targetIdx < 0 || targetIdx >= active.length) return;
    const temp = active[idx].sortOrder;
    active[idx].sortOrder = active[targetIdx].sortOrder;
    active[targetIdx].sortOrder = temp;
    saveStateToLocal();
    renderApp();
  },

  openNewTxForm(type = 'EXPENSE', walletId = null) {
    AppState.editingTx = null;
    AppState.txFormInitialType = type;
    AppState.txFormInitialWalletId = walletId || AppState.settings.defaultWalletId;
    AppState._txFormAmount = '';
    AppState._txFormFee = '';
    AppState._txFormNote = '';
    AppState._txFormRef = '';
    AppState._txFormDateMs = Date.now();
    AppState._txFormAttachment = '';
    AppState._txFormCatId = null;
    navigateTo('TX_FORM');
  },

  editTransaction(txId) {
    const tx = AppState.transactions.find(t => t.id === txId);
    if (!tx) return;
    AppState.editingTx = tx;
    AppState.txFormInitialType = tx.type === 'INCOME' ? 'INCOME' : 'EXPENSE';
    AppState.txFormInitialWalletId = tx.walletId;
    AppState._txFormAmount = String(tx.amount);
    AppState._txFormFee = '';
    AppState._txFormNote = tx.note || '';
    AppState._txFormRef = tx.reference || '';
    AppState._txFormDateMs = tx.date;
    AppState._txFormAttachment = tx.attachmentUri || '';
    AppState._txFormCatId = tx.categoryId;
    navigateTo('TX_FORM');
  },

  setTxFormType(type) {
    AppState.txFormInitialType = type;
    AppState._txFormCatId = null;
    renderApp();
  },

  updateTxAmountInput(val) {
    AppState._txFormAmount = val;
  },

  appendMathOperator(op) {
    const cur = String(AppState._txFormAmount || '').trim();
    if (op === '=') {
      const ev = evaluateMathExpression(cur);
      if (ev !== null) AppState._txFormAmount = String(ev);
    } else {
      AppState._txFormAmount = `${cur}${op}`;
    }
    renderApp();
    const input = document.getElementById('tx-amount-input');
    if (input) {
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
    }
  },

  applyQuickFeePercent(pct) {
    const amt = evaluateMathExpression(AppState._txFormAmount) || parseFloat(AppState._txFormAmount) || 0;
    if (amt > 0) {
      AppState._txFormFee = String(AccountingEngine.roundCurrency((amt * pct) / 100));
      renderApp();
    }
  },

  selectTxWallet(wId) {
    AppState.txFormInitialWalletId = wId;
    renderApp();
  },

  selectTxCategory(cId) {
    AppState._txFormCatId = cId;
    renderApp();
  },

  setTxDraftField(field, val) {
    AppState[field] = val;
  },

  setTxDate(dateStr) {
    if (dateStr) AppState._txFormDateMs = new Date(dateStr).getTime();
  },

  setTxAttachment(uri) {
    AppState._txFormAttachment = uri;
    renderApp();
  },

  saveTransactionForm() {
    const amt = evaluateMathExpression(AppState._txFormAmount) ?? parseFloat(AppState._txFormAmount) ?? 0;
    if (amt <= 0) {
      showSnackbar('Please enter an amount greater than 0.');
      return;
    }

    // Check Free tier daily limit on new record
    if (!AppState.editingTx) {
      const startOfDay = new Date();
      startOfDay.setHours(0, 0, 0, 0);
      const todayCount = AppState.transactions.filter(t => t.date >= startOfDay.getTime()).length;
      if (!SubscriptionManager.canAddTransactionToday(todayCount, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
        this.openUpgradeDialog(t('Free tier allows up to 10 transactions per day. Upgrade to Pocket Premium for unlimited daily entries.'));
        return;
      }
    }

    const type = AppState.txFormInitialType || 'EXPENSE';
    const walletId = AppState.txFormInitialWalletId || AppState.settings.defaultWalletId || 1;
    const filteredCats = AppState.categories.filter(c => !c.isArchived && c.type === type);
    const categoryId = AppState._txFormCatId || filteredCats[0]?.id || 1;

    if (AppState.editingTx) {
      const idx = AppState.transactions.findIndex(t => t.id === AppState.editingTx.id);
      if (idx >= 0) {
        AppState.transactions[idx] = {
          ...AppState.transactions[idx],
          type,
          amount: AccountingEngine.roundCurrency(amt),
          walletId,
          categoryId,
          date: AppState._txFormDateMs || Date.now(),
          note: (AppState._txFormNote || '').trim(),
          reference: (AppState._txFormRef || '').trim(),
          attachmentUri: AppState._txFormAttachment || ''
        };
      }
      showSnackbar('Transaction updated.');
    } else {
      const newId = Date.now();
      AppState.transactions.push({
        id: newId,
        type,
        amount: AccountingEngine.roundCurrency(amt),
        walletId,
        categoryId,
        date: AppState._txFormDateMs || Date.now(),
        note: (AppState._txFormNote || '').trim(),
        reference: (AppState._txFormRef || '').trim(),
        tags: '',
        attachmentUri: AppState._txFormAttachment || ''
      });

      const fee = parseFloat(AppState._txFormFee) || 0;
      if (type === 'EXPENSE' && fee > 0) {
        const feeCat = AppState.categories.find(c => c.name.toLowerCase().includes('fee')) || filteredCats[0];
        AppState.transactions.push({
          id: newId + 1,
          type: 'EXPENSE',
          amount: AccountingEngine.roundCurrency(fee),
          walletId,
          categoryId: feeCat ? feeCat.id : categoryId,
          date: AppState._txFormDateMs || Date.now(),
          note: `Fee for ${(AppState._txFormNote || 'transaction').trim()}`,
          reference: (AppState._txFormRef || '').trim(),
          tags: '',
          attachmentUri: ''
        });
      }
      showSnackbar('Transaction saved.');
    }

    saveStateToLocal();
    navigateBack();
  },

  deleteTransaction(txId) {
    AppState.transactions = AppState.transactions.filter(t => t.id !== txId);
    saveStateToLocal();
    showSnackbar('Transaction deleted.');
    navigateBack();
  },

  // SMS Paste Dialog
  openSmsPasteDialog() {
    openModal(`
      <h3 style="margin:0 0 8px;">${t('Paste MFS / Bank SMS')}</h3>
      <p style="margin:0 0 12px;font-size:0.82rem;color:var(--text-muted);">Paste a bKash, Nagad, Rocket, or Bank SMS below. Pocket extracts amount, fee, TrxID, and wallet 100% offline.</p>
      <textarea id="sms-paste-textarea" rows="4" class="pocket-input" placeholder="e.g. Cash Out Tk 2,000.00 successful. Fee Tk 37.00. TrxID ABC123XYZ"></textarea>
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.confirmParseSms()">${t('Auto-Fill')}</button>
      </div>
    `);
  },

  confirmParseSms() {
    const raw = document.getElementById('sms-paste-textarea')?.value || '';
    const parsed = parseFinancialSms(raw, AppState.wallets);
    if (!parsed) {
      showSnackbar('Could not detect a valid amount in this SMS.');
      return;
    }
    AppState.txFormInitialType = parsed.inferredType;
    AppState._txFormAmount = String(parsed.amount);
    AppState._txFormFee = parsed.feeAmount > 0 ? String(parsed.feeAmount) : '';
    AppState._txFormRef = parsed.reference || '';
    AppState._txFormNote = parsed.summaryNote || '';
    if (parsed.matchedWalletId) AppState.txFormInitialWalletId = parsed.matchedWalletId;
    closeModal();
    renderApp();
    showSnackbar('SMS auto-filled!');
  },

  // CSV Bank Statement Import Dialog
  openCsvBankImportDialog() {
    openModal(`
      <h3 style="margin:0 0 8px;">Import Bank / MFS CSV</h3>
      <p style="margin:0 0 12px;font-size:0.82rem;color:var(--text-muted);">Paste CSV rows (Date,Description,Amount) or pick a .CSV file.</p>
      <textarea id="csv-import-textarea" rows="5" class="pocket-input" placeholder="Date,Description,Amount&#10;2026-10-25,Shwapno Grocery,-1250&#10;2026-10-26,Salary Credit,45000"></textarea>
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.confirmCsvImport()">Import Rows</button>
      </div>
    `);
  },

  confirmCsvImport() {
    const raw = document.getElementById('csv-import-textarea')?.value || '';
    const lines = raw.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    if (lines.length < 2) {
      showSnackbar('Please provide header and data rows.');
      return;
    }
    const walletId = AppState.txFormInitialWalletId || AppState.settings.defaultWalletId || 1;
    let imported = 0;
    for (let i = 1; i < lines.length; i++) {
      const cols = lines[i].split(',').map(c => c.replace(/^"|"$/g, '').trim());
      if (cols.length < 2) continue;
      const amtRaw = parseFloat(cols[cols.length - 1].replace(/[^0-9.-]/g, ''));
      if (!amtRaw || Number.isNaN(amtRaw)) continue;
      const isIncome = amtRaw > 0 && lines.some(l => l.includes('-'));
      AppState.transactions.push({
        id: Date.now() + i,
        type: isIncome ? 'INCOME' : 'EXPENSE',
        amount: AccountingEngine.roundCurrency(Math.abs(amtRaw)),
        walletId,
        categoryId: isIncome ? 16 : 1,
        date: Date.now(),
        note: cols[1] || 'CSV Import',
        reference: '',
        tags: '',
        attachmentUri: ''
      });
      imported++;
    }
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar(`Imported ${imported} transactions!`);
  },

  // Transfer & Adjustment
  openTransferForm() {
    navigateTo('TRANSFER_FORM');
  },

  setTransferWallet(which, wId) {
    if (which === 'src') AppState._trSrcId = wId;
    else AppState._trDstId = wId;
    renderApp();
  },

  submitTransferForm(srcId, dstId) {
    if (srcId === dstId) {
      showSnackbar('Source and destination wallets must be different.');
      return;
    }
    const amt = parseFloat(document.getElementById('transfer-amount-input')?.value) || 0;
    const fee = parseFloat(document.getElementById('transfer-fee-input')?.value) || 0;
    const note = (document.getElementById('transfer-note-input')?.value || '').trim();
    if (amt <= 0) {
      showSnackbar('Enter a transfer amount greater than 0.');
      return;
    }
    AppState.transfers.push({
      id: Date.now(),
      sourceWalletId: srcId,
      destinationWalletId: dstId,
      amount: AccountingEngine.roundCurrency(amt),
      feeAmount: AccountingEngine.roundCurrency(fee),
      date: Date.now(),
      note
    });
    saveStateToLocal();
    showSnackbar('Transfer recorded.');
    navigateBack();
  },

  openAdjustForm(wId = null) {
    AppState.adjustInitialWalletId = wId || AppState.settings.defaultWalletId || 1;
    navigateTo('ADJUST_FORM');
  },

  selectAdjustWallet(wId) {
    AppState.adjustInitialWalletId = wId;
    renderApp();
  },

  submitAdjustBalance(wId) {
    const derived = computeDerivedData();
    const ws = derived.walletSummaries.find(s => s.wallet.id === wId);
    if (!ws) return;
    const actual = parseFloat(document.getElementById('adjust-actual-input')?.value);
    if (Number.isNaN(actual)) {
      showSnackbar('Please enter a valid actual balance.');
      return;
    }
    const delta = AccountingEngine.roundCurrency(actual - ws.currentBalance);
    if (Math.abs(delta) < 0.005) {
      showSnackbar('Balance is already equal to actual amount.');
      navigateBack();
      return;
    }
    const reason = (document.getElementById('adjust-reason-input')?.value || 'Reconciliation').trim();
    AppState.adjustments.push({
      id: Date.now(),
      walletId: wId,
      previousCalculatedBalance: ws.currentBalance,
      targetBalance: AccountingEngine.roundCurrency(actual),
      deltaAmount: delta,
      date: Date.now(),
      reason
    });
    saveStateToLocal();
    showSnackbar('Wallet balance reconciled.');
    navigateBack();
  },

  // Create / Edit Wallet Dialog
  openWalletDialog(editId = null) {
    AppState.isFabExpanded = false;
    const existing = editId ? AppState.wallets.find(w => w.id === editId) : null;
    if (!existing) {
      const activeWallets = AppState.wallets.filter(w => !w.isArchived);
      const isPro = SubscriptionManager.isSubscriptionActive(AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt);
      if (!isPro && activeWallets.length >= SubscriptionManager.FREE_DEFAULT_WALLETS_LIMIT) {
        this.openUpgradeDialog(t('Free tier allows up to 3 active wallets. Upgrade to Pocket Premium for unlimited wallets.'));
        return;
      }
    }
    const icons = ['CASH', 'BANK', 'MTB', 'BRAC', 'BKASH', 'NAGAD', 'ROCKET', 'RAPID_PASS', 'CARD', 'SAVINGS'];
    openModal(`
      <h3 style="margin:0 0 12px;">${existing ? t('Edit Wallet') : t('New Wallet')}</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Wallet Name')}</label>
          <input id="dlg-wallet-name" type="text" class="pocket-input" value="${escHtml(existing?.name || '')}" placeholder="e.g. City Bank / bKash Personal" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Opening Balance')}</label>
          <input id="dlg-wallet-opening" type="number" step="any" class="pocket-input" value="${existing?.openingBalance ?? 0}" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">Currency</label>
          <select id="dlg-wallet-currency" class="pocket-input">
            ${['BDT', 'USD', 'EUR', 'GBP', 'INR', 'SAR', 'AED', 'MYR'].map(c => `<option value="${c}" ${(existing?.currency || 'BDT') === c ? 'selected' : ''}>${c} (${AccountingEngine.currencySymbolForCode(c)})</option>`).join('')}
          </select>
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Wallet Icon')}</label>
          <select id="dlg-wallet-icon" class="pocket-input">
            ${icons.map(ic => `<option value="${ic}" ${(existing?.iconName || 'BANK') === ic ? 'selected' : ''}>${ic}</option>`).join('')}
          </select>
        </div>
        <label style="display:flex;align-items:center;gap:8px;font-size:0.86rem;margin-top:4px;">
          <input id="dlg-wallet-include" type="checkbox" ${(existing?.includeInTotalBalance ?? true) ? 'checked' : ''} style="width:18px;height:18px;accent-color:var(--accent-color);" />
          <span>${t('Include in Total Balance')}</span>
        </label>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px;">
          ${existing ? `<button class="pocket-btn-danger" style="margin-right:auto;" onclick="window.PocketApp.archiveWallet(${existing.id})">${t('Delete')}</button>` : ''}
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.saveWalletDialog(${existing ? existing.id : 'null'})">${t('Save Wallet')}</button>
        </div>
      </div>
    `);
  },

  saveWalletDialog(editId) {
    const name = (document.getElementById('dlg-wallet-name')?.value || '').trim();
    const opening = parseFloat(document.getElementById('dlg-wallet-opening')?.value) || 0;
    const currency = document.getElementById('dlg-wallet-currency')?.value || 'BDT';
    const iconName = document.getElementById('dlg-wallet-icon')?.value || 'BANK';
    const include = Boolean(document.getElementById('dlg-wallet-include')?.checked);
    if (!name) {
      showSnackbar('Please enter a wallet name.');
      return;
    }
    if (editId) {
      const idx = AppState.wallets.findIndex(w => w.id === editId);
      if (idx >= 0) {
        AppState.wallets[idx] = {
          ...AppState.wallets[idx],
          name,
          openingBalance: AccountingEngine.roundCurrency(opening),
          currency,
          iconName,
          includeInTotalBalance: include
        };
      }
    } else {
      const activeWallets = AppState.wallets.filter(w => !w.isArchived);
      if (!SubscriptionManager.canAddWallet(activeWallets, name, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
        if (!SubscriptionManager.isStarterWallet(name)) {
          this.openUpgradeDialog(t('Custom wallets are a Pocket Premium feature. Free tier allows choosing up to 3 default wallets.'));
        } else {
          this.openUpgradeDialog(t('Free tier allows up to 3 active wallets. Upgrade to Pocket Premium for unlimited wallets.'));
        }
        return;
      }
      AppState.wallets.push({
        id: Date.now(),
        name,
        type: iconName === 'CARD' ? 'CARD' : 'BANK',
        openingBalance: AccountingEngine.roundCurrency(opening),
        includeInTotalBalance: include,
        colorHex: AppState.settings.accentColorHex || '#F15A24',
        iconName,
        sortOrder: AppState.wallets.length + 1,
        isArchived: false,
        currency,
        creditLimit: 0,
        statementDay: 0,
        paymentDueDay: 0
      });
    }
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('Wallet saved.');
  },

  archiveWallet(wId) {
    const idx = AppState.wallets.findIndex(w => w.id === wId);
    if (idx >= 0) AppState.wallets[idx].isArchived = true;
    saveStateToLocal();
    closeModal();
    AppState.currentScreen = 'BALANCES';
    renderApp();
    showSnackbar('Wallet archived.');
  },

  // History Filters
  openHistoryWithType(type) {
    AppState.historyTypeFilter = type;
    this.switchMainTab('HISTORY');
  },

  setHistorySearch(val) {
    AppState.historySearch = val;
    renderApp();
  },

  setHistoryTypeFilter(tf) {
    AppState.historyTypeFilter = tf;
    renderApp();
  },

  setHistoryWalletFilter(wf) {
    AppState.historyWalletFilter = wf;
    renderApp();
  },

  // Loans & Khata
  setLoanFilter(f) {
    AppState.loanFilter = f;
    renderApp();
  },

  toggleLoanKhataView() {
    AppState.loanKhataView = !AppState.loanKhataView;
    renderApp();
  },

  openCreateLoanDialog() {
    if (!SubscriptionManager.canAddLoan(AppState.loans.length, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
      this.openUpgradeDialog('Free tier allows up to 2 loan records. Upgrade to Pocket Premium for unlimited loans & person khatas.');
      return;
    }
    const todayIso = new Date().toISOString().slice(0, 10);
    const activeWallets = AppState.wallets.filter(w => !w.isArchived);
    openModal(`
      <h3 style="margin:0 0 12px;">${t('Create Loan Record')}</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <div class="pocket-input-group">
          <label class="pocket-input-label">Direction</label>
          <select id="dlg-loan-dir" class="pocket-input">
            <option value="LENT">${t('Lent (Receivable)')}</option>
            <option value="BORROWED">${t('Borrowed (Payable)')}</option>
          </select>
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Person or Institution Name')}</label>
          <input id="dlg-loan-person" type="text" class="pocket-input" placeholder="e.g. Rahim / Karim" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Principal Amount')} (৳)</label>
          <input id="dlg-loan-amount" type="number" step="any" class="pocket-input" placeholder="0" />
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">Wallet</label>
          <select id="dlg-loan-wallet" class="pocket-input">
            ${activeWallets.map(w => `<option value="${w.id}">${escHtml(w.name)}</option>`).join('')}
          </select>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
          <div class="pocket-input-group">
            <label class="pocket-input-label">${t('Date of Giving Loan')}</label>
            <input id="dlg-loan-start" type="date" class="pocket-input" value="${todayIso}" />
          </div>
          <div class="pocket-input-group">
            <label class="pocket-input-label">${t('Due Date')} (${t('Optional')})</label>
            <input id="dlg-loan-due" type="date" class="pocket-input" />
          </div>
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Note')}</label>
          <input id="dlg-loan-note" type="text" class="pocket-input" placeholder="Optional note" />
        </div>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px;">
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.saveCreateLoanDialog()">${t('Save Loan')}</button>
        </div>
      </div>
    `);
  },

  saveCreateLoanDialog() {
    const dir = document.getElementById('dlg-loan-dir')?.value || 'LENT';
    const person = (document.getElementById('dlg-loan-person')?.value || '').trim();
    const amt = parseFloat(document.getElementById('dlg-loan-amount')?.value) || 0;
    const walletId = Number(document.getElementById('dlg-loan-wallet')?.value) || 1;
    const startStr = document.getElementById('dlg-loan-start')?.value;
    const dueStr = document.getElementById('dlg-loan-due')?.value;
    const note = (document.getElementById('dlg-loan-note')?.value || '').trim();
    if (!person || amt <= 0) {
      showSnackbar('Please enter person name and principal amount.');
      return;
    }
    const existingPersons = Array.from(new Set(AppState.loans.map(l => String(l.personName || '').trim().toLowerCase()))).filter(Boolean);
    if (!SubscriptionManager.canAddLoanPerson(existingPersons, person, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
      this.openUpgradeDialog(t('Free tier allows loans for up to 2 persons. Upgrade to Pocket Premium for unlimited loan records.'));
      return;
    }
    AppState.loans.push({
      id: Date.now(),
      personName: person,
      direction: dir,
      principalAmount: AccountingEngine.roundCurrency(amt),
      initialWalletId: walletId,
      startDate: startStr ? new Date(startStr).getTime() : Date.now(),
      dueDate: dueStr ? new Date(dueStr).getTime() : null,
      note,
      attachmentUri: ''
    });
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('Loan record created.');
  },

  openLoanDeadlineDialog(loanId) {
    const loan = AppState.loans.find(l => l.id === loanId);
    if (!loan) return;
    const curIso = loan.dueDate ? new Date(loan.dueDate).toISOString().slice(0, 10) : '';
    openModal(`
      <h3 style="margin:0 0 10px;">Set Deadline Date — ${escHtml(loan.personName)}</h3>
      <input id="dlg-deadline-date" type="date" class="pocket-input" value="${curIso}" />
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.saveLoanDeadline(${loan.id}, null)">${t('Clear')}</button>
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.saveLoanDeadline(${loan.id}, document.getElementById('dlg-deadline-date').value)">Save Deadline</button>
      </div>
    `);
  },

  saveLoanDeadline(loanId, dateStr) {
    const loan = AppState.loans.find(l => l.id === loanId);
    if (loan) {
      loan.dueDate = dateStr ? new Date(dateStr).getTime() : null;
      saveStateToLocal();
    }
    closeModal();
    renderApp();
  },

  openLoanRepaymentDialog(loanId) {
    const derived = computeDerivedData();
    const s = derived.loanSummaries.find(x => x.loan.id === loanId);
    if (!s) return;
    const activeWallets = AppState.wallets.filter(w => !w.isArchived);
    openModal(`
      <h3 style="margin:0 0 8px;">${escHtml(s.loan.personName)} · Loan Ledger</h3>
      <div style="font-size:0.84rem;color:var(--text-secondary);margin-bottom:10px;">
        Original: ${AccountingEngine.formatMoney(s.originalPrincipal, '৳')} • Outstanding: <strong>${AccountingEngine.formatMoney(s.outstandingPrincipal, '৳')}</strong>
      </div>
      ${s.status !== 'SETTLED' ? `
        <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:12px;">
          <label class="pocket-input-label">Principal Repayment Amount (৳)</label>
          <input id="dlg-repay-principal" type="number" step="any" class="pocket-input" value="${s.outstandingPrincipal}" />
          <label class="pocket-input-label">Interest / Profit Portion (Optional, ৳)</label>
          <input id="dlg-repay-interest" type="number" step="any" class="pocket-input" placeholder="0" />
          <label class="pocket-input-label">Wallet</label>
          <select id="dlg-repay-wallet" class="pocket-input">
            ${activeWallets.map(w => `<option value="${w.id}" ${w.id === s.loan.initialWalletId ? 'selected' : ''}>${escHtml(w.name)}</option>`).join('')}
          </select>
          <button class="pocket-btn-primary" style="margin-top:4px;" onclick="window.PocketApp.submitLoanRepayment(${s.loan.id})">${t('Record Repayment')}</button>
        </div>
      ` : ''}
      <div style="display:flex;justify-content:space-between;margin-top:8px;">
        <button class="pocket-btn-danger" onclick="window.PocketApp.deleteLoan(${s.loan.id})">${t('Delete')} Loan</button>
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Close')}</button>
      </div>
    `);
  },

  submitLoanRepayment(loanId) {
    const p = parseFloat(document.getElementById('dlg-repay-principal')?.value) || 0;
    const interest = parseFloat(document.getElementById('dlg-repay-interest')?.value) || 0;
    const walletId = Number(document.getElementById('dlg-repay-wallet')?.value) || 1;
    if (p <= 0 && interest <= 0) {
      showSnackbar('Please enter a repayment amount.');
      return;
    }
    AppState.loanPayments.push({
      id: Date.now(),
      loanId,
      walletId,
      principalPortion: AccountingEngine.roundCurrency(p),
      interestPortion: AccountingEngine.roundCurrency(interest),
      date: Date.now(),
      note: 'Loan Repayment'
    });
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('Loan repayment recorded.');
  },

  deleteLoan(loanId) {
    AppState.loans = AppState.loans.filter(l => l.id !== loanId);
    AppState.loanPayments = AppState.loanPayments.filter(lp => lp.loanId !== loanId);
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('Loan deleted.');
  },

  shareKhataText(personName) {
    const derived = computeDerivedData();
    const khata = derived.khataSummaries.find(k => k.personName === personName);
    if (!khata) return;
    const text = BackupManager.formatPersonKhataStatementText(khata, khata.currencySymbol);
    if (navigator.share) {
      navigator.share({ title: `Loan Khata - ${personName}`, text }).catch(() => {});
    } else {
      navigator.clipboard?.writeText(text);
      showSnackbar('Khata statement copied to clipboard!');
    }
  },

  // Developer Info Dialog (100% Matching DeveloperInfoDialog in PocketSmartToolsAndDevComponents.kt)
  openDeveloperInfoDialog() {
    openModal(`
      <div style="position:relative;display:flex;flex-direction:column;align-items:center;gap:12px;padding-top:6px;">
        <button onclick="window.PocketApp.closeModal()" style="position:absolute;top:0;right:0;background:none;border:none;color:var(--accent-color);cursor:pointer;">
          <span class="material-symbols-rounded">close</span>
        </button>
        <img src="./assets/developer_hadi_photo.jpg" alt="Abdullahil Hadi" style="width:132px;height:132px;border-radius:50%;object-fit:cover;border:3px solid var(--accent-color);" />
        <div style="text-align:center;">
          <div style="font-size:1.3rem;font-weight:800;">Abdullahil Hadi</div>
          <div style="font-size:0.84rem;font-weight:600;color:var(--accent-color);">App Developer</div>
        </div>
        <div style="width:100%;display:flex;flex-direction:column;gap:10px;margin-top:4px;">
          <a href="tel:+8801991617180" class="pocket-card-elevated" style="padding:11px 14px;display:flex;align-items:center;gap:12px;text-decoration:none;color:var(--text-primary);">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">call</span>
            <div style="flex:1;">
              <div style="font-size:0.72rem;color:var(--text-muted);">Phone</div>
              <div style="font-weight:700;">+8801991617180</div>
            </div>
            <span class="material-symbols-rounded" style="color:var(--accent-color);">chevron_right</span>
          </a>
          <a href="https://api.whatsapp.com/send?phone=8801991617180" target="_blank" rel="noopener" class="pocket-card-elevated" style="padding:11px 14px;display:flex;align-items:center;gap:12px;text-decoration:none;color:var(--text-primary);">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">chat</span>
            <div style="flex:1;">
              <div style="font-size:0.72rem;color:var(--text-muted);">WhatsApp</div>
              <div style="font-weight:700;">+8801991617180</div>
            </div>
            <span class="material-symbols-rounded" style="color:var(--accent-color);">chevron_right</span>
          </a>
          <a href="mailto:hadi280994@gmail.com" class="pocket-card-elevated" style="padding:11px 14px;display:flex;align-items:center;gap:12px;text-decoration:none;color:var(--text-primary);">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">mail</span>
            <div style="flex:1;">
              <div style="font-size:0.72rem;color:var(--text-muted);">Email</div>
              <div style="font-weight:700;">hadi280994@gmail.com</div>
            </div>
            <span class="material-symbols-rounded" style="color:var(--accent-color);">chevron_right</span>
          </a>
          <a href="https://www.facebook.com/hadi280994" target="_blank" rel="noopener" class="pocket-card-elevated" style="padding:11px 14px;display:flex;align-items:center;gap:12px;text-decoration:none;color:var(--text-primary);">
            <span class="material-symbols-rounded" style="color:var(--accent-color);">public</span>
            <div style="flex:1;">
              <div style="font-size:0.72rem;color:var(--text-muted);">Facebook</div>
              <div style="font-weight:700;">Abdullahil Hadi</div>
            </div>
            <span class="material-symbols-rounded" style="color:var(--accent-color);">chevron_right</span>
          </a>
        </div>
      </div>
    `);
  },

  // Upgrade / Activation Code Dialog
  openUpgradeDialog(reasonText = '') {
    openModal(`
      <div style="display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="material-symbols-rounded" style="color:var(--accent-color);font-size:28px;">workspace_premium</span>
          <h3 style="margin:0;">${t('Upgrade to Pocket Premium')}</h3>
        </div>
        ${reasonText ? `<div class="pocket-card-elevated" style="padding:10px 12px;font-size:0.83rem;color:var(--accent-color);">${escHtml(reasonText)}</div>` : ''}
        <div style="font-size:0.84rem;color:var(--text-secondary);line-height:1.45;">
          Unlock unlimited wallets, unlimited daily transactions, unlimited loans & categories, custom accent/box color wheels, and biometric security.
        </div>
        <div class="pocket-input-group">
          <label class="pocket-input-label">${t('Enter Activation Code')}</label>
          <input id="dlg-activation-code" type="text" class="pocket-input" placeholder="PKLT-XXXX-XXXX or PK3D-XXXX-XXXX" style="text-transform:uppercase;" />
        </div>
        <div style="display:flex;flex-direction:column;gap:8px;margin-top:4px;">
          <button class="pocket-btn-primary" onclick="window.PocketApp.redeemCodeFromDialog()">Activate Premium</button>
          <a href="https://api.whatsapp.com/send?phone=8801991617180&text=Hello%20Abdullahil%20Hadi%2C%20I%20want%20to%20buy%20a%20Pocket%20Premium%20Activation%20Code." target="_blank" rel="noopener" class="pocket-btn-outlined" style="text-decoration:none;text-align:center;">
            <span>${t('Contact on WhatsApp to Buy')}</span>
          </a>
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Close')}</button>
        </div>
      </div>
    `);
  },

  async redeemCodeFromDialog() {
    const raw = document.getElementById('dlg-activation-code')?.value || '';
    try {
      const tier = await PocketCloudSync.redeemActivationCode(raw);
      const now = Date.now();
      AppState.settings.subscriptionTier = tier;
      AppState.settings.subscriptionActivatedAt = now;
      AppState.settings.subscriptionExpiresAt = tier === 'THREE_DAYS' ? now + SubscriptionManager.THREE_DAYS_MILLIS : null;
      AppState.settings.activatedCode = raw.trim().toUpperCase();
      saveStateToLocal();
      closeModal();
      renderApp();
      showSnackbar(tier === 'LIFETIME' ? 'Lifetime Premium Activated!' : '3 Days Premium Activated!');
    } catch (e) {
      showSnackbar(e.message || 'Invalid activation code.');
    }
  },

  // Master Budget Dialog
  openMasterBudgetDialog() {
    openModal(`
      <h3 style="margin:0 0 10px;">${t('Master Monthly Budget')}</h3>
      <input id="dlg-master-budget" type="number" step="any" class="pocket-input" value="${AppState.settings.masterMonthlyBudget || 0}" placeholder="0" />
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.saveMasterBudget()">${t('Save')}</button>
      </div>
    `);
  },

  saveMasterBudget() {
    const val = parseFloat(document.getElementById('dlg-master-budget')?.value) || 0;
    AppState.settings.masterMonthlyBudget = AccountingEngine.roundCurrency(Math.max(0, val));
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('Monthly budget updated.');
  },

  // Savings Goals
  openSavingsGoalDialog() {
    openModal(`
      <h3 style="margin:0 0 12px;">${t('New Savings Goal')}</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <input id="dlg-goal-title" type="text" class="pocket-input" placeholder="${t('Goal Title')} (e.g. Emergency Fund)" />
        <input id="dlg-goal-target" type="number" step="any" class="pocket-input" placeholder="${t('Target Amount (৳)')}" />
        <input id="dlg-goal-saved" type="number" step="any" class="pocket-input" placeholder="${t('Saved So Far (৳)')}" value="0" />
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px;">
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.saveSavingsGoal()">${t('Save Goal')}</button>
        </div>
      </div>
    `);
  },

  saveSavingsGoal() {
    const title = (document.getElementById('dlg-goal-title')?.value || '').trim();
    const target = parseFloat(document.getElementById('dlg-goal-target')?.value) || 0;
    const saved = parseFloat(document.getElementById('dlg-goal-saved')?.value) || 0;
    if (!title || target <= 0) {
      showSnackbar('Please enter goal title and target amount.');
      return;
    }
    AppState.savingsGoals.push({
      id: Date.now(),
      title,
      targetAmount: AccountingEngine.roundCurrency(target),
      savedAmount: AccountingEngine.roundCurrency(saved),
      colorHex: '#10B981',
      iconName: 'SAVINGS'
    });
    saveStateToLocal();
    closeModal();
    renderApp();
  },

  fundSavingsGoal(goalId) {
    const g = AppState.savingsGoals.find(x => x.id === goalId);
    if (!g) return;
    openModal(`
      <h3 style="margin:0 0 10px;">${t('Add Savings')} — ${escHtml(g.title)}</h3>
      <input id="dlg-goal-add-amt" type="number" step="any" class="pocket-input" placeholder="Amount (৳)" />
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.confirmFundSavingsGoal(${g.id})">${t('Save')}</button>
      </div>
    `);
  },

  confirmFundSavingsGoal(goalId) {
    const g = AppState.savingsGoals.find(x => x.id === goalId);
    const amt = parseFloat(document.getElementById('dlg-goal-add-amt')?.value) || 0;
    if (g && amt > 0) {
      g.savedAmount = AccountingEngine.roundCurrency((g.savedAmount || 0) + amt);
      saveStateToLocal();
    }
    closeModal();
    renderApp();
  },

  deleteSavingsGoal(goalId) {
    AppState.savingsGoals = AppState.savingsGoals.filter(g => g.id !== goalId);
    saveStateToLocal();
    renderApp();
  },

  // Categories & Recurring
  openCategoryDialog(editId = null) {
    const existing = editId ? AppState.categories.find(c => c.id === editId) : null;
    openModal(`
      <h3 style="margin:0 0 12px;">${existing ? t('Edit Category') : t('New Category')}</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <input id="dlg-cat-name" type="text" class="pocket-input" value="${escHtml(existing?.name || '')}" placeholder="${t('Category Name')}" />
        <select id="dlg-cat-type" class="pocket-input">
          <option value="EXPENSE" ${(existing?.type || 'EXPENSE') === 'EXPENSE' ? 'selected' : ''}>${t('Expense')}</option>
          <option value="INCOME" ${existing?.type === 'INCOME' ? 'selected' : ''}>${t('Income')}</option>
        </select>
        <input id="dlg-cat-budget" type="number" step="any" class="pocket-input" value="${existing?.monthlyBudget || 0}" placeholder="${t('Monthly Budget (Optional)')}" />
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px;">
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.saveCategoryDialog(${existing ? existing.id : 'null'})">${t('Save Category')}</button>
        </div>
      </div>
    `);
  },

  saveCategoryDialog(editId) {
    const name = (document.getElementById('dlg-cat-name')?.value || '').trim();
    const type = document.getElementById('dlg-cat-type')?.value || 'EXPENSE';
    const budget = parseFloat(document.getElementById('dlg-cat-budget')?.value) || 0;
    if (!name) return;
    if (editId) {
      const idx = AppState.categories.findIndex(c => c.id === editId);
      if (idx >= 0) {
        AppState.categories[idx] = { ...AppState.categories[idx], name, type, monthlyBudget: AccountingEngine.roundCurrency(budget) };
      }
    } else {
      const customCount = AppState.categories.filter(c => c.isDefault === false || c.id > 1000).length;
      if (!SubscriptionManager.canAddCategory(type, customCount, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
        this.openUpgradeDialog(t('Free tier allows 1 custom category. Upgrade to Pocket Premium for unlimited custom categories.'));
        return;
      }
      AppState.categories.push({
        id: Date.now(),
        name,
        type,
        colorHex: type === 'INCOME' ? '#22C55E' : '#F15A24',
        iconName: 'CATEGORY',
        monthlyBudget: AccountingEngine.roundCurrency(budget),
        isArchived: false
      });
    }
    saveStateToLocal();
    closeModal();
    renderApp();
  },

  archiveCategory(cId) {
    const c = AppState.categories.find(x => x.id === cId);
    if (c) c.isArchived = true;
    saveStateToLocal();
    renderApp();
  },

  openRecurringDialog() {
    if (!SubscriptionManager.canAddRecurringBill(AppState.recurringTransactions.length, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
      this.openUpgradeDialog('Free tier allows up to 2 recurring bills. Upgrade to Pocket Premium for unlimited recurring bills.');
      return;
    }
    openModal(`
      <h3 style="margin:0 0 12px;">New Recurring Schedule</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <input id="dlg-rec-title" type="text" class="pocket-input" placeholder="Title (e.g. WiFi, Netflix, House Rent)" />
        <select id="dlg-rec-type" class="pocket-input">
          <option value="EXPENSE">Expense / Bill</option>
          <option value="INCOME">Income</option>
        </select>
        <input id="dlg-rec-amount" type="number" step="any" class="pocket-input" placeholder="Amount (৳)" />
        <select id="dlg-rec-freq" class="pocket-input">
          <option value="MONTHLY">Monthly</option>
          <option value="WEEKLY">Weekly</option>
          <option value="YEARLY">Yearly</option>
        </select>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px;">
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.saveRecurringDialog()">${t('Save')}</button>
        </div>
      </div>
    `);
  },

  saveRecurringDialog() {
    const title = (document.getElementById('dlg-rec-title')?.value || '').trim();
    const type = document.getElementById('dlg-rec-type')?.value || 'EXPENSE';
    const amount = parseFloat(document.getElementById('dlg-rec-amount')?.value) || 0;
    const frequency = document.getElementById('dlg-rec-freq')?.value || 'MONTHLY';
    if (!title) return;
    const activeCount = AppState.recurringTransactions.filter(r => r.isActive !== false).length;
    if (!SubscriptionManager.canAddRecurringBill(activeCount, AppState.settings.subscriptionTier, AppState.settings.subscriptionExpiresAt)) {
      this.openUpgradeDialog(t('Free tier allows up to 2 recurring bills. Upgrade to Pocket Premium for unlimited recurring bills.'));
      return;
    }
    AppState.recurringTransactions.push({
      id: Date.now(),
      title,
      type,
      amountMode: amount > 0 ? 'FIXED' : 'MANUAL',
      amount: AccountingEngine.roundCurrency(amount),
      categoryId: type === 'INCOME' ? 16 : 5,
      defaultWalletId: AppState.settings.defaultWalletId || 1,
      frequency,
      dayOfMonth: new Date().getDate(),
      customIntervalDays: 30,
      nextDueDate: Date.now(),
      isActive: true
    });
    saveStateToLocal();
    closeModal();
    renderApp();
  },

  payRecurringNow(recId) {
    const rec = AppState.recurringTransactions.find(r => r.id === recId);
    if (!rec) return;
    const amt = rec.amount > 0 ? rec.amount : parseFloat(prompt(`Enter amount for ${rec.title}:`) || '0');
    if (!amt || amt <= 0) return;
    AppState.transactions.push({
      id: Date.now(),
      type: rec.type || 'EXPENSE',
      amount: AccountingEngine.roundCurrency(amt),
      walletId: rec.defaultWalletId || AppState.settings.defaultWalletId || 1,
      categoryId: rec.categoryId || 5,
      date: Date.now(),
      note: rec.title,
      reference: '',
      tags: 'Recurring',
      attachmentUri: ''
    });
    rec.nextDueDate = Date.now() + 30 * 24 * 3600 * 1000;
    saveStateToLocal();
    renderApp();
    showSnackbar(`${rec.title} logged & next due date advanced!`);
  },

  snoozeRecurring(recId) {
    const rec = AppState.recurringTransactions.find(r => r.id === recId);
    if (rec) {
      rec.nextDueDate = Date.now() + 24 * 3600 * 1000;
      saveStateToLocal();
      renderApp();
      showSnackbar('Snoozed until tomorrow.');
    }
  },

  deleteRecurring(recId) {
    AppState.recurringTransactions = AppState.recurringTransactions.filter(r => r.id !== recId);
    saveStateToLocal();
    renderApp();
  },

  // Security PIN Lock
  toggleAppLockPin(enabled) {
    if (!enabled) {
      AppState.settings.appLockEnabled = false;
      AppState.settings.appLockPin = '';
      saveStateToLocal();
      renderApp();
      return;
    }
    openModal(`
      <h3 style="margin:0 0 10px;">Set Device-Local App PIN</h3>
      <input id="dlg-new-pin" type="password" inputmode="numeric" maxlength="6" class="pocket-input" placeholder="4-6 digit PIN" />
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:14px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal();window.PocketApp.renderApp();">${t('Cancel')}</button>
        <button class="pocket-btn-primary" onclick="window.PocketApp.saveNewAppPin()">Enable Lock</button>
      </div>
    `);
  },

  saveNewAppPin() {
    const pin = (document.getElementById('dlg-new-pin')?.value || '').replace(/[^0-9]/g, '');
    if (pin.length < 4) {
      showSnackbar('PIN must be at least 4 digits.');
      return;
    }
    AppState.settings.appLockEnabled = true;
    AppState.settings.appLockPin = pin;
    AppState.isAppUnlocked = true;
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('App PIN lock enabled.');
  },

  submitUnlockPin() {
    const entered = document.getElementById('unlock-pin-input')?.value || '';
    if (entered === AppState.settings.appLockPin) {
      AppState.isAppUnlocked = true;
      renderApp();
    } else {
      showSnackbar('Incorrect PIN.');
    }
  },

  // Reset all data to zero
  confirmResetAllToZero() {
    openModal(`
      <h3 style="margin:0 0 8px;color:var(--error-color);">Reset All Data to Zero?</h3>
      <p style="margin:0 0 14px;font-size:0.86rem;color:var(--text-secondary);">
        Are you sure you want to reset all financial data? All income, expenses, loans, transfers, balance adjustments, and wallet balances will be set to 0. Your wallets and categories will be kept.
      </p>
      <div style="display:flex;justify-content:flex-end;gap:8px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
        <button class="pocket-btn-danger" onclick="window.PocketApp.executeResetAllToZero()">Yes, Reset to Zero</button>
      </div>
    `);
  },

  executeResetAllToZero() {
    AppState.transactions = [];
    AppState.transactionSplits = [];
    AppState.transfers = [];
    AppState.loans = [];
    AppState.loanPayments = [];
    AppState.adjustments = [];
    AppState.savingsGoals = [];
    AppState.wallets = AppState.wallets.map(w => ({ ...w, openingBalance: 0 }));
    saveStateToLocal();
    closeModal();
    renderApp();
    showSnackbar('All financial data reset to 0.');
  },

  // Google Cloud Sync Actions from Settings
  async signInGoogleFromSettings() {
    try {
      const user = await PocketCloudSync.signInWithGoogle();
      AppState.googleUser = user;
      const snap = await PocketCloudSync.pullSnapshot(user.uid);
      if (snap && snap.payload && (snap.payload.transactions.length > 0 || snap.payload.wallets.length > 0)) {
        applyBackupPayloadToState(snap.payload, true);
        showSnackbar('Loaded cloud backup from Google account.');
      } else {
        await PocketCloudSync.pushSnapshot(AppState);
        showSnackbar('Local data backed up to Google account!');
      }
      PocketCloudSync.startRealtimeListeners(user.uid, (remote) => {
        if (remote.updatedAtMillis > AppState.lastAppliedCloudMs) {
          AppState.lastAppliedCloudMs = remote.updatedAtMillis;
          applyBackupPayloadToState(remote.payload, true);
        }
      });
      renderApp();
    } catch (e) {
      showSnackbar(e.message || 'Sign-in cancelled.');
    }
  },

  async syncCloudNow() {
    if (!AppState.googleUser) return;
    try {
      AppState.isCloudSyncing = true;
      renderApp();
      const ts = await PocketCloudSync.pushSnapshot(AppState);
      AppState.lastAppliedCloudMs = ts;
      AppState.isCloudSyncing = false;
      AppState.lastCloudSyncStatus = 'Synced just now';
      renderApp();
      showSnackbar('Cloud sync completed!');
    } catch (e) {
      AppState.isCloudSyncing = false;
      renderApp();
      showSnackbar(e.message || 'Sync failed.');
    }
  },

  async signOutGoogle() {
    await PocketCloudSync.signOutUser();
    AppState.googleUser = null;
    renderApp();
    showSnackbar('Signed out of Google.');
  },

  // Feedback & Admin Dialogs
  openFeedbackDialog() {
    openModal(`
      <h3 style="margin:0 0 10px;">${t('Feedback & Bugs Report')}</h3>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <input id="dlg-fb-subject" type="text" class="pocket-input" placeholder="${t('Subject')}" />
        <textarea id="dlg-fb-details" rows="4" class="pocket-input" placeholder="${t('Details')}"></textarea>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:6px;">
          <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">${t('Cancel')}</button>
          <button class="pocket-btn-primary" onclick="window.PocketApp.submitFeedbackFromDialog()">${t('Submit')}</button>
        </div>
      </div>
    `);
  },

  async submitFeedbackFromDialog() {
    const subj = (document.getElementById('dlg-fb-subject')?.value || '').trim();
    const det = (document.getElementById('dlg-fb-details')?.value || '').trim();
    if (!subj && !det) {
      showSnackbar(t('Please enter a subject or details.'));
      return;
    }
    try {
      await PocketCloudSync.submitFeedbackReport(subj || 'Pocket Web Feedback', det, AppState.settings.userName);
      closeModal();
      showSnackbar(t('Thank you! Your feedback has been sent.'));
    } catch (e) {
      showSnackbar(e.message || 'Could not send feedback.');
    }
  },

  async openAdminCodeManagerDialog() {
    const codes = await PocketCloudSync.fetchAllCloudActivationCodes();
    openModal(`
      <h3 style="margin:0 0 10px;">Admin: Activation Codes (${codes.length})</h3>
      <div style="display:flex;gap:8px;margin-bottom:10px;">
        <button class="pocket-btn-primary" style="flex:1;min-height:40px;" onclick="window.PocketApp.adminGenerateCode('LIFETIME')">+ Lifetime Code</button>
        <button class="pocket-btn-outlined" style="flex:1;min-height:40px;" onclick="window.PocketApp.adminGenerateCode('THREE_DAYS')">+ 3-Day Code</button>
      </div>
      <div style="max-height:280px;overflow-y:auto;display:flex;flex-direction:column;gap:6px;">
        ${codes.map(c => `
          <div class="pocket-card-elevated" style="padding:8px 10px;display:flex;justify-content:space-between;align-items:center;font-size:0.8rem;">
            <div>
              <div style="font-weight:800;font-family:monospace;">${escHtml(c.code)}</div>
              <div style="color:var(--text-muted);">${c.tier} • ${c.status} ${c.usedByEmail ? '(' + escHtml(c.usedByEmail) + ')' : ''}</div>
            </div>
            <button class="pocket-chip" onclick="navigator.clipboard.writeText('${escHtml(c.code)}');window.PocketApp.showSnackbar('Copied!')">Copy</button>
          </div>
        `).join('')}
      </div>
      <div style="display:flex;justify-content:flex-end;margin-top:12px;">
        <button class="pocket-btn-outlined" onclick="window.PocketApp.closeModal()">Close</button>
      </div>
    `);
  },

  async adminGenerateCode(tier) {
    await PocketCloudSync.generateNewCloudActivationCode(tier);
    await this.openAdminCodeManagerDialog();
  },

  // Backup & File Upload Helpers
  downloadBackupJson() {
    const json = JSON.stringify(BackupManager.createBackupPayload(AppState), null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'pocket-backup-v1.json';
    a.click();
    URL.revokeObjectURL(url);
    showSnackbar('Backup file downloaded.');
  },

  previewBackupJson() {
    const ta = document.getElementById('backup-json-textarea');
    if (ta) {
      ta.value = JSON.stringify(BackupManager.createBackupPayload(AppState), null, 2);
      showSnackbar('Generated backup JSON preview.');
    }
  },

  triggerBackupFileImport() {
    const input = document.getElementById('hidden-file-input');
    if (!input) return;
    input.accept = '.json,application/json,text/plain';
    input.onchange = (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        const ta = document.getElementById('backup-json-textarea');
        if (ta) ta.value = String(reader.result || '');
      };
      reader.readAsText(file);
    };
    input.click();
  },

  restoreFromJsonTextarea() {
    const raw = document.getElementById('backup-json-textarea')?.value || '';
    const res = BackupManager.validateAndParseBackup(raw);
    if (!res.isValid || !res.payload) {
      showSnackbar(res.errorMessage || 'Invalid backup JSON.');
      return;
    }
    applyBackupPayloadToState(res.payload, false);
    showSnackbar('Backup restored successfully!');
  },

  downloadLedgerCsv() {
    const derived = computeDerivedData();
    const csv = BackupManager.exportLedgerToCsv(derived.unifiedLedger);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'pocket-transactions.csv';
    a.click();
    URL.revokeObjectURL(url);
    showSnackbar('CSV exported.');
  },

  triggerProfilePhotoUpload() {
    this._pickAndCompressImage(320, (dataUri) => {
      AppState.settings.profilePhotoUri = dataUri;
      saveStateToLocal();
      renderApp();
    });
  },

  triggerWallpaperUpload() {
    this._pickAndCompressImage(960, (dataUri) => {
      AppState.settings.customBackgroundUri = dataUri;
      saveStateToLocal();
      renderApp();
    });
  },

  triggerTxPhotoUpload() {
    this._pickAndCompressImage(680, (dataUri) => {
      AppState._txFormAttachment = dataUri;
      renderApp();
    });
  },

  _pickAndCompressImage(maxDim, callback) {
    const input = document.getElementById('hidden-file-input');
    if (!input) return;
    input.accept = 'image/*';
    input.onchange = (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {
        const img = new Image();
        img.onload = () => {
          const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.round(img.width * scale);
          canvas.height = Math.round(img.height * scale);
          const ctx = canvas.getContext('2d');
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
          callback(canvas.toDataURL('image/jpeg', 0.82));
        };
        img.src = ev.target.result;
      };
      reader.readAsDataURL(file);
    };
    input.click();
  },

  showSnackbar,
  renderApp
};

// ============================================================================
// BOOTSTRAP
// ============================================================================
window.addEventListener('DOMContentLoaded', () => {
  loadStateFromLocal();
  renderApp();

  PocketCloudSync.observeAuth(async (user) => {
    AppState.googleUser = user;
    if (user) {
      try {
        const snap = await PocketCloudSync.pullSnapshot(user.uid);
        if (snap && snap.payload && snap.backupUpdatedAtMillis > AppState.lastAppliedCloudMs) {
          AppState.lastAppliedCloudMs = snap.backupUpdatedAtMillis;
          applyBackupPayloadToState(snap.payload, true);
        }
        PocketCloudSync.startRealtimeListeners(user.uid, (remote) => {
          if (remote.updatedAtMillis > AppState.lastAppliedCloudMs) {
            AppState.lastAppliedCloudMs = remote.updatedAtMillis;
            applyBackupPayloadToState(remote.payload, true);
          }
        });
      } catch (e) {
        console.warn('Initial cloud pull warning:', e);
      }
    }
    renderApp();
  });
});
