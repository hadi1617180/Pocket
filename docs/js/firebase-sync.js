// firebase-sync.js — Real-time Firebase Auth & Cloud Firestore Sync strictly matching PocketCloudSyncRepository.kt & firestore.rules
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  deleteDoc,
  collection,
  getDocs,
  runTransaction,
  onSnapshot,
  writeBatch,
  serverTimestamp
} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';
import { BackupManager, SubscriptionManager } from './accounting.js';

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAIy498LT2vooT327SThshw8VU5D_pOYxg",
  authDomain: "gen-lang-client-0285964029.firebaseapp.com",
  projectId: "gen-lang-client-0285964029",
  storageBucket: "gen-lang-client-0285964029.firebasestorage.app",
  messagingSenderId: "30179850125",
  appId: "1:30179850125:android:fcf248906c9e43807f6fc4"
};

const FIRESTORE_DATABASE_ID = "ai-studio-android-pocket-90c40f1c-a604-4528-b70a-bb3c8a09d67f";
export const DEVELOPER_ADMIN_EMAIL = "hadi1617180@gmail.com";

let app = null;
let auth = null;
let db = null;
let activeProfileUnsub = null;

try {
  app = initializeApp(FIREBASE_CONFIG);
  auth = getAuth(app);
  db = getFirestore(app, FIRESTORE_DATABASE_ID);
} catch (err) {
  console.warn("Firebase initialization warning:", err);
}

export const PocketCloudSync = {
  currentUser: null,

  observeAuth(onUserChanged) {
    if (!auth) {
      onUserChanged(null);
      return () => {};
    }
    return onAuthStateChanged(auth, (user) => {
      this.currentUser = user
        ? {
            uid: user.uid,
            email: user.email || '',
            displayName: user.displayName || '',
            photoUrl: user.photoURL || ''
          }
        : null;
      onUserChanged(this.currentUser);
    });
  },

  isDeveloperAdmin(user = this.currentUser) {
    return Boolean(user?.email && user.email.trim().toLowerCase() === DEVELOPER_ADMIN_EMAIL);
  },

  async signInWithGoogle() {
    if (!auth) throw new Error("Firebase Auth is not initialized.");
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    const cred = await signInWithPopup(auth, provider);
    const u = cred.user;
    this.currentUser = {
      uid: u.uid,
      email: u.email || '',
      displayName: u.displayName || '',
      photoUrl: u.photoURL || ''
    };
    return this.currentUser;
  },

  async signOutUser() {
    this.stopRealtimeListeners();
    if (auth) {
      await signOut(auth);
    }
    this.currentUser = null;
  },

  stopRealtimeListeners() {
    if (activeProfileUnsub) {
      activeProfileUnsub();
      activeProfileUnsub = null;
    }
  },

  startRealtimeListeners(uid, onRemoteUpdate) {
    this.stopRealtimeListeners();
    if (!db || !uid) return;
    const userRef = doc(db, 'users', uid);
    activeProfileUnsub = onSnapshot(
      userRef,
      async (snap) => {
        if (!snap.exists()) return;
        try {
          const snapshot = await this.pullSnapshot(uid);
          if (snapshot && snapshot.payload) {
            onRemoteUpdate(snapshot);
          }
        } catch (err) {
          console.warn("Snapshot pull error during realtime update:", err);
        }
      },
      (err) => console.warn("Realtime profile listener warning:", err)
    );
  },

  async pullSnapshot(uid) {
    if (!db || !uid) return null;
    const userRef = doc(db, 'users', uid);
    const userSnap = await getDoc(userRef);
    if (!userSnap.exists()) return null;
    const uData = userSnap.data();

    const [walletsSnap, catsSnap, txsSnap, loansSnap, loanActsSnap, recsSnap, goalsSnap, adjsSnap, transSnap] = await Promise.all([
      getDocs(collection(db, 'users', uid, 'wallets')),
      getDocs(collection(db, 'users', uid, 'categories')),
      getDocs(collection(db, 'users', uid, 'transactions')),
      getDocs(collection(db, 'users', uid, 'loans')),
      getDocs(collection(db, 'users', uid, 'loan_activities')),
      getDocs(collection(db, 'users', uid, 'recurring_transactions')),
      getDocs(collection(db, 'users', uid, 'savings_goals')),
      getDocs(collection(db, 'users', uid, 'balance_adjustments')),
      getDocs(collection(db, 'users', uid, 'transfers'))
    ]);

    const wallets = walletsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        name: data.name,
        type: data.type || 'BANK',
        accountHint: data.accountHint || '',
        openingBalance: Number(data.openingBalance || 0),
        openingDate: Number(data.openingDate || 0),
        currency: data.currency || 'BDT',
        iconName: data.iconName || 'BANK',
        colorHex: data.colorHex || '#2E9E5B',
        sortOrder: Number(data.sortOrder || 0),
        isArchived: Boolean(data.isArchived),
        includeInTotalBalance: Boolean(data.includeInTotalBalance ?? true),
        creditLimit: Number(data.creditLimit || 0),
        statementDay: Number(data.statementDay || 0),
        paymentDueDay: Number(data.paymentDueDay || 0)
      };
    });

    const categories = catsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        name: data.name,
        type: data.type || 'EXPENSE',
        iconName: data.iconName || 'CATEGORY',
        colorHex: data.colorHex || '#F15A24',
        sortOrder: Number(data.sortOrder || 0),
        isArchived: Boolean(data.isArchived),
        isSystemFee: Boolean(data.isSystemFee),
        monthlyBudget: Number(data.monthlyBudget || 0)
      };
    });

    const transactions = txsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        type: data.type || 'EXPENSE',
        amount: Number(data.amount || 0),
        date: Number(data.date || Date.now()),
        walletId: Number(data.walletId || 1),
        categoryId: Number(data.categoryId || 1),
        note: data.note || '',
        reference: data.reference || '',
        tags: data.tags || '',
        attachmentUri: data.attachmentUri || '',
        transferId: data.transferId ? Number(data.transferId) : null,
        parentTransactionId: data.parentTransactionId ? Number(data.parentTransactionId) : null,
        loanActivityId: data.loanActivityId ? Number(data.loanActivityId) : null
      };
    });

    const loans = loansSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        personName: data.personName,
        direction: data.direction || 'LENT',
        principalAmount: Number(data.principalAmount || 0),
        initialWalletId: (data.initialWalletId !== undefined && data.initialWalletId !== null) ? Number(data.initialWalletId) : 0,
        startDate: Number(data.startDate || Date.now()),
        dueDate: data.dueDate ? Number(data.dueDate) : null,
        interestRate: Number(data.interestRate || 0),
        interestType: data.interestType || 'NONE',
        note: data.note || '',
        reference: data.reference || '',
        isArchived: Boolean(data.isArchived),
        attachmentUri: data.attachmentUri || ''
      };
    });

    const loanActivities = loanActsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        loanId: Number(data.loanId),
        activityType: data.activityType || 'REPAYMENT',
        principalPortion: Number(data.principalPortion || 0),
        interestPortion: Number(data.interestPortion || 0),
        walletId: (data.walletId !== undefined && data.walletId !== null) ? Number(data.walletId) : 0,
        date: Number(data.date || Date.now()),
        note: data.note || ''
      };
    });

    const recurringTransactions = recsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        title: data.title,
        type: data.type || 'EXPENSE',
        amountMode: data.amountMode || 'FIXED',
        amount: Number(data.amount || 0),
        categoryId: Number(data.categoryId || 1),
        defaultWalletId: Number(data.defaultWalletId || 1),
        frequency: data.frequency || 'MONTHLY',
        dayOfMonth: Number(data.dayOfMonth || 1),
        customIntervalDays: Number(data.customIntervalDays || 30),
        nextDueDate: Number(data.nextDueDate || Date.now()),
        lastPaidDate: data.lastPaidDate ? Number(data.lastPaidDate) : null,
        note: data.note || '',
        isActive: Boolean(data.isActive ?? true),
        reminderEnabled: Boolean(data.reminderEnabled ?? true)
      };
    });

    const savingsGoals = goalsSnap.docs.map(d => {
      const data = d.data();
      return {
        id: Number(data.entityId || d.id),
        title: data.title,
        targetAmount: Number(data.targetAmount || 0),
        savedAmount: Number(data.savedAmount || 0),
        iconName: data.iconName || 'SAVINGS',
        colorHex: data.colorHex || '#2E9E5B',
        deadlineMillis: data.deadlineMillis ? Number(data.deadlineMillis) : null,
        targetDate: data.targetDate ? Number(data.targetDate) : null,
        note: data.note || '',
        goalCreatedAt: Number(data.goalCreatedAt || Date.now())
      };
    });

    const adjustments = adjsSnap.docs.map(d => {
      const data = d.data();
      const diff = Number(data.difference ?? data.deltaAmount ?? 0);
      return {
        id: Number(data.entityId || d.id),
        walletId: Number(data.walletId || 1),
        actualBalance: Number(data.actualBalance ?? data.targetBalance ?? 0),
        calculatedBalanceBefore: Number(data.calculatedBalanceBefore ?? data.previousCalculatedBalance ?? 0),
        difference: diff,
        deltaAmount: diff,
        date: Number(data.date || Date.now()),
        reason: data.reason || 'Reconciliation',
        note: data.note || '',
        isReversed: Boolean(data.isReversed)
      };
    });

    const transfers = transSnap.docs.map(d => {
      const data = d.data();
      const pAmt = Number(data.principalAmount ?? data.amount ?? 0);
      const dAmt = Number(data.destinationAmount || pAmt);
      return {
        id: Number(data.entityId || d.id),
        sourceWalletId: Number(data.sourceWalletId || 1),
        destinationWalletId: Number(data.destinationWalletId || 1),
        principalAmount: pAmt,
        amount: pAmt,
        destinationAmount: dAmt,
        conversionRate: Number(data.conversionRate || 1),
        feeMode: data.feeMode || 'NONE',
        fixedFeeInput: Number(data.fixedFeeInput || 0),
        percentageInput: Number(data.percentageInput || 0),
        feeAmount: Number(data.feeAmount || 0),
        date: Number(data.date || Date.now()),
        note: data.note || ''
      };
    });

    const anyWalletHasFund = wallets.some(w => /\[VFUND:1:/i.test(String(w.accountHint || '')) || String(w.type || '').toUpperCase() === 'FUND');
    const fundFeatureEnabled = Boolean(uData.fundFeatureEnabled ?? anyWalletHasFund);
    const hasCompletedOnboarding = Boolean(uData.hasCompletedOnboarding || wallets.length > 0 || transactions.length > 0);

    return {
      backupUpdatedAtMillis: Date.now(),
      profile: {
        uid,
        email: uData.email || '',
        displayName: uData.displayName || '',
        photoUrl: uData.photoUrl || '',
        currencyCode: uData.currencyCode || 'BDT',
        currencySymbol: uData.currencySymbol || '৳',
        themeMode: uData.themeMode || 'DARK',
        accentColorHex: uData.accentColorHex || '#F15A24',
        boxColorHex: uData.boxColorHex || '',
        boxOpacity: Number(uData.boxOpacity ?? 0.50),
        appLanguage: uData.appLanguage || 'EN',
        fundFeatureEnabled,
        subscriptionTier: uData.subscriptionTier || 'FREE',
        subscriptionExpiresAt: uData.subscriptionExpiresAt ? Number(uData.subscriptionExpiresAt) : null,
        activationCode: uData.activationCode || '',
        hasCompletedOnboarding,
        syncVersion: Number(uData.syncVersion || 1)
      },
      payload: {
        formatVersion: 'pocket-backup-v1',
        exportedAtMillis: Date.now(),
        wallets: wallets.length > 0 ? wallets : BackupManager.defaultWallets(),
        categories: categories.length > 0 ? categories : BackupManager.defaultCategories(),
        transactions,
        transfers,
        loans,
        loanActivities,
        loanPayments: loanActivities,
        adjustments,
        recurringTransactions,
        savingsGoals,
        settings: {
          userName: uData.displayName || '',
          profilePhotoUri: uData.photoUrl || '',
          currencyCode: uData.currencyCode || 'BDT',
          currencySymbol: uData.currencySymbol || '৳',
          themeMode: uData.themeMode || 'DARK',
          defaultWalletId: uData.defaultWalletId || 1,
          accentColorHex: uData.accentColorHex || '#F15A24',
          boxColorHex: uData.boxColorHex || '',
          boxOpacity: Number(uData.boxOpacity ?? 0.50),
          appLanguage: uData.appLanguage || 'EN',
          fundFeatureEnabled,
          masterMonthlyBudget: Number(uData.masterMonthlyBudget || 0),
          hideBalancesPrivacy: Boolean(uData.hideBalancesPrivacy),
          subscriptionTier: uData.subscriptionTier || 'FREE',
          subscriptionExpiresAt: uData.subscriptionExpiresAt ? Number(uData.subscriptionExpiresAt) : null,
          activationCode: uData.activationCode || '',
          hasCompletedOnboarding,
          onboardingStep: hasCompletedOnboarding ? 'COMPLETED' : 'PAGE_1_WELCOME'
        }
      }
    };
  },

  async pushSnapshot(state) {
    const user = this.currentUser;
    if (!db || !user?.uid) throw new Error("Not signed in with Google.");
    const uid = user.uid;
    const userRef = doc(db, 'users', uid);
    const existingSnap = await getDoc(userRef);

    const safeName = String(state.settings.userName || user.displayName || 'Pocket User').trim().slice(0, 100);
    const profileData = {
      userId: uid,
      displayName: safeName,
      email: String(user.email || '').slice(0, 200),
      photoUrl: String(state.settings.profilePhotoUri || user.photoUrl || '').slice(0, 1000),
      currencyCode: 'BDT',
      currencySymbol: '৳',
      themeMode: (state.settings.themeMode === 'LIGHT') ? 'LIGHT' : 'DARK',
      defaultWalletId: Number(state.settings.defaultWalletId) || 1,
      warnOnNegativeBalance: Boolean(state.settings.warnOnNegativeBalance ?? true),
      loanDeadlineNotificationEnabled: Boolean(state.settings.loanDeadlineNotificationEnabled ?? true),
      dailyExpenseReminderEnabled: Boolean(state.settings.dailyExpenseReminderEnabled ?? true),
      dailyReminderHour: Number(state.settings.dailyReminderHour ?? 20),
      dailyReminderMinute: Number(state.settings.dailyReminderMinute ?? 0),
      backgroundOpacity: Number(state.settings.backgroundOpacity ?? 0.85),
      accentColorHex: state.settings.accentColorHex || '#F15A24',
      boxColorHex: state.settings.boxColorHex || '',
      boxOpacity: Number(state.settings.boxOpacity ?? 0.50),
      appLanguage: (state.settings.appLanguage || 'EN').toUpperCase() === 'BN' ? 'BN' : 'EN',
      fundFeatureEnabled: Boolean(state.settings.fundFeatureEnabled),
      dateFormatPattern: 'dd MMM yyyy',
      masterMonthlyBudget: Number(state.settings.masterMonthlyBudget || 0),
      hideBalancesPrivacy: Boolean(state.settings.hideBalancesPrivacy),
      subscriptionTier: state.settings.subscriptionTier || 'FREE',
      subscriptionExpiresAt: state.settings.subscriptionExpiresAt ? Number(state.settings.subscriptionExpiresAt) : null,
      activationCode: state.settings.activationCode || state.settings.activatedCode || '',
      hasCompletedOnboarding: Boolean(state.settings.hasCompletedOnboarding),
      syncVersion: (Number(state.settings.syncVersion) || 1) + 1,
      updatedAt: serverTimestamp()
    };

    if (!existingSnap.exists()) {
      profileData.createdAt = serverTimestamp();
      await setDoc(userRef, profileData);
    } else {
      await setDoc(userRef, profileData, { merge: true });
    }

    const batch = writeBatch(db);

    // 1. Wallets
    for (const w of (state.wallets || [])) {
      const ref = doc(db, 'users', uid, 'wallets', String(w.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(w.id),
        name: String(w.name || 'Wallet').slice(0, 100),
        type: String(w.type || 'BANK').slice(0, 40),
        accountHint: String(w.accountHint || '').slice(0, 200),
        openingBalance: Number(w.openingBalance || 0),
        openingDate: Number(w.openingDate || 0),
        currency: String(w.currency || 'BDT').slice(0, 10),
        iconName: String(w.iconName || 'BANK').slice(0, 100),
        colorHex: String(w.colorHex || '#2E9E5B').slice(0, 20),
        sortOrder: Number(w.sortOrder || 0),
        isArchived: Boolean(w.isArchived),
        includeInTotalBalance: Boolean(w.includeInTotalBalance ?? true),
        creditLimit: Number(w.creditLimit || 0),
        statementDay: Number(w.statementDay || 0),
        paymentDueDay: Number(w.paymentDueDay || 0),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 2. Categories
    for (const c of (state.categories || [])) {
      const ref = doc(db, 'users', uid, 'categories', String(c.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(c.id),
        name: String(c.name || 'Category').slice(0, 100),
        type: (c.type === 'INCOME') ? 'INCOME' : 'EXPENSE',
        iconName: String(c.iconName || 'CATEGORY').slice(0, 100),
        colorHex: String(c.colorHex || '#F15A24').slice(0, 20),
        sortOrder: Number(c.sortOrder || 0),
        isArchived: Boolean(c.isArchived),
        isSystemFee: Boolean(c.isSystemFee),
        monthlyBudget: Number(c.monthlyBudget || 0),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 3. Transactions
    const recentTx = (state.transactions || []).slice(-120);
    for (const t of recentTx) {
      const ref = doc(db, 'users', uid, 'transactions', String(t.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(t.id),
        type: (t.type === 'INCOME') ? 'INCOME' : (t.type === 'EXPENSE_REVERSAL' ? 'EXPENSE_REVERSAL' : 'EXPENSE'),
        amount: Number(t.amount || 0),
        date: Number(t.date || Date.now()),
        walletId: Number(t.walletId || 1),
        categoryId: Number(t.categoryId || 1),
        note: String(t.note || '').slice(0, 1000),
        reference: String(t.reference || '').slice(0, 200),
        tags: String(t.tags || '').slice(0, 500),
        transferId: t.transferId ? Number(t.transferId) : null,
        parentTransactionId: t.parentTransactionId ? Number(t.parentTransactionId) : null,
        loanActivityId: t.loanActivityId ? Number(t.loanActivityId) : null,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 4. Transfers (including Return to Fund, Personal Borrow, Official Fund In/Out)
    const recentTr = (state.transfers || []).slice(-120);
    for (const tr of recentTr) {
      const ref = doc(db, 'users', uid, 'transfers', String(tr.id));
      const pAmt = Number(tr.principalAmount ?? tr.amount ?? 0);
      const dAmt = Number(tr.destinationAmount || pAmt);
      batch.set(ref, {
        userId: uid,
        entityId: Number(tr.id),
        sourceWalletId: Number(tr.sourceWalletId || 1),
        destinationWalletId: Number(tr.destinationWalletId || 1),
        principalAmount: pAmt,
        destinationAmount: dAmt,
        conversionRate: Number(tr.conversionRate || 1),
        feeMode: String(tr.feeMode || (Number(tr.feeAmount) > 0 ? 'MANUAL' : 'NONE')).slice(0, 40),
        fixedFeeInput: Number(tr.fixedFeeInput ?? tr.feeAmount ?? 0),
        percentageInput: Number(tr.percentageInput || 0),
        feeAmount: Number(tr.feeAmount || 0),
        date: Number(tr.date || Date.now()),
        note: String(tr.note || '').slice(0, 1000),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 5. Loans
    for (const l of (state.loans || [])) {
      const ref = doc(db, 'users', uid, 'loans', String(l.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(l.id),
        personName: String(l.personName || 'Person').slice(0, 150),
        direction: (l.direction === 'BORROWED') ? 'BORROWED' : 'LENT',
        principalAmount: Number(l.principalAmount || 0),
        initialWalletId: Number(l.initialWalletId ?? 0),
        startDate: Number(l.startDate || Date.now()),
        dueDate: l.dueDate ? Number(l.dueDate) : null,
        interestRate: Number(l.interestRate || 0),
        interestType: String(l.interestType || 'NONE').slice(0, 40),
        note: String(l.note || '').slice(0, 1000),
        reference: String(l.reference || '').slice(0, 200),
        isArchived: Boolean(l.isArchived),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 6. Loan Activities (Disbursements & Repayments)
    const acts = (state.loanPayments || state.loanActivities || []).slice(-120);
    for (const act of acts) {
      const ref = doc(db, 'users', uid, 'loan_activities', String(act.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(act.id),
        loanId: Number(act.loanId),
        activityType: (act.activityType === 'DISBURSEMENT' || act.activityType === 'ADJUSTMENT') ? act.activityType : 'REPAYMENT',
        principalPortion: Number(act.principalPortion || 0),
        interestPortion: Number(act.interestPortion || 0),
        walletId: Number(act.walletId ?? 0),
        date: Number(act.date || Date.now()),
        note: String(act.note || '').slice(0, 1000),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 7. Balance Adjustments
    const adjs = (state.adjustments || []).slice(-80);
    for (const adj of adjs) {
      const ref = doc(db, 'users', uid, 'balance_adjustments', String(adj.id));
      const diff = Number(adj.difference ?? adj.deltaAmount ?? 0);
      batch.set(ref, {
        userId: uid,
        entityId: Number(adj.id),
        walletId: Number(adj.walletId || 1),
        actualBalance: Number(adj.actualBalance ?? adj.targetBalance ?? 0),
        calculatedBalanceBefore: Number(adj.calculatedBalanceBefore ?? adj.previousCalculatedBalance ?? 0),
        difference: diff,
        date: Number(adj.date || Date.now()),
        reason: String(adj.reason || 'Reconciliation').slice(0, 100),
        note: String(adj.note || '').slice(0, 1000),
        isReversed: Boolean(adj.isReversed),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 8. Recurring Transactions
    for (const r of (state.recurringTransactions || [])) {
      const ref = doc(db, 'users', uid, 'recurring_transactions', String(r.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(r.id),
        title: String(r.title || 'Recurring').slice(0, 150),
        type: (r.type === 'INCOME') ? 'INCOME' : 'EXPENSE',
        amountMode: (r.amountMode === 'MANUAL') ? 'MANUAL' : 'FIXED',
        amount: Number(r.amount || 0),
        categoryId: Number(r.categoryId || 1),
        defaultWalletId: Number(r.defaultWalletId || 1),
        frequency: String(r.frequency || 'MONTHLY').slice(0, 40),
        dayOfMonth: Number(r.dayOfMonth || 1),
        customIntervalDays: Number(r.customIntervalDays || 30),
        nextDueDate: Number(r.nextDueDate || Date.now()),
        lastPaidDate: r.lastPaidDate ? Number(r.lastPaidDate) : null,
        note: String(r.note || '').slice(0, 1000),
        isActive: Boolean(r.isActive ?? true),
        reminderEnabled: Boolean(r.reminderEnabled ?? true),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // 9. Savings Goals
    for (const g of (state.savingsGoals || [])) {
      const ref = doc(db, 'users', uid, 'savings_goals', String(g.id));
      batch.set(ref, {
        userId: uid,
        entityId: Number(g.id),
        title: String(g.title || 'Goal').slice(0, 150),
        targetAmount: Number(g.targetAmount || 0),
        savedAmount: Number(g.savedAmount || 0),
        iconName: String(g.iconName || 'SAVINGS').slice(0, 100),
        colorHex: String(g.colorHex || '#2E9E5B').slice(0, 20),
        deadlineMillis: g.deadlineMillis ? Number(g.deadlineMillis) : null,
        targetDate: g.targetDate ? Number(g.targetDate) : null,
        note: String(g.note || '').slice(0, 1000),
        goalCreatedAt: Number(g.goalCreatedAt || Date.now()),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      }, { merge: true });
    }

    // Reconcile deletions / Undo across cloud collections
    try {
      const [cloudTxs, cloudTrs, cloudLoans, cloudActs, cloudAdjs, cloudRecs, cloudGoals] = await Promise.all([
        getDocs(collection(db, 'users', uid, 'transactions')),
        getDocs(collection(db, 'users', uid, 'transfers')),
        getDocs(collection(db, 'users', uid, 'loans')),
        getDocs(collection(db, 'users', uid, 'loan_activities')),
        getDocs(collection(db, 'users', uid, 'balance_adjustments')),
        getDocs(collection(db, 'users', uid, 'recurring_transactions')),
        getDocs(collection(db, 'users', uid, 'savings_goals'))
      ]);
      const localTxIds = new Set((state.transactions || []).map(x => String(x.id)));
      for (const d of cloudTxs.docs) { if (!localTxIds.has(d.id)) batch.delete(d.ref); }

      const localTrIds = new Set((state.transfers || []).map(x => String(x.id)));
      for (const d of cloudTrs.docs) { if (!localTrIds.has(d.id)) batch.delete(d.ref); }

      const localLoanIds = new Set((state.loans || []).map(x => String(x.id)));
      for (const d of cloudLoans.docs) { if (!localLoanIds.has(d.id)) batch.delete(d.ref); }

      const localActIds = new Set((state.loanPayments || state.loanActivities || []).map(x => String(x.id)));
      for (const d of cloudActs.docs) { if (!localActIds.has(d.id)) batch.delete(d.ref); }

      const localAdjIds = new Set((state.adjustments || []).map(x => String(x.id)));
      for (const d of cloudAdjs.docs) { if (!localAdjIds.has(d.id)) batch.delete(d.ref); }

      const localRecIds = new Set((state.recurringTransactions || []).map(x => String(x.id)));
      for (const d of cloudRecs.docs) { if (!localRecIds.has(d.id)) batch.delete(d.ref); }

      const localGoalIds = new Set((state.savingsGoals || []).map(x => String(x.id)));
      for (const d of cloudGoals.docs) { if (!localGoalIds.has(d.id)) batch.delete(d.ref); }
    } catch (e) {
      console.warn("Cloud cleanup check warning:", e);
    }

    await batch.commit();
    return Date.now();
  },

  async redeemActivationCode(rawCode) {
    const normalized = String(rawCode || '').trim().toUpperCase();
    if (!normalized) throw new Error("Please enter an activation code.");

    // 1. Check Firestore single-use cloud pool if signed in
    if (db && this.currentUser?.uid) {
      try {
        const codeRef = doc(db, 'activation_codes', normalized);
        const uid = this.currentUser.uid;
        const email = this.currentUser.email || '';
        const tier = await runTransaction(db, async (tx) => {
          const snap = await tx.get(codeRef);
          if (!snap.exists()) return null;
          const data = snap.data();
          const isClaimed = Boolean(data.isClaimed);
          const claimedByUid = String(data.claimedByUid || '');
          const tierStr = String(data.tier || '').toUpperCase();
          if (isClaimed && claimedByUid && claimedByUid !== uid) {
            throw new Error("This activation code has already been claimed by another account.");
          }
          tx.update(codeRef, {
            isClaimed: true,
            claimedByUid: uid,
            claimedByEmail: email,
            claimedAt: serverTimestamp()
          });
          return tierStr === 'LIFETIME' ? 'LIFETIME' : 'THREE_DAYS';
        });
        if (tier) return tier;
      } catch (e) {
        if (e.message && e.message.includes('already been claimed')) throw e;
      }
    }

    // 2. Fallback to offline SHA-256 hash bank in assets/activation_codes.json
    const offlineTier = await SubscriptionManager.verifyAndDecodeOfflineCode(normalized);
    if (offlineTier) return offlineTier;

    throw new Error("Invalid or expired activation code.");
  },

  async submitFeedbackReport(subjectOrObj, detailsArg = '', userNameArg = '') {
    if (!db || !this.currentUser?.uid) throw new Error("Please sign in to submit a bug report.");
    const subject = typeof subjectOrObj === 'object' ? subjectOrObj.subject : subjectOrObj;
    const details = typeof subjectOrObj === 'object' ? subjectOrObj.details : detailsArg;
    const colRef = collection(db, 'feedback_reports');
    await setDoc(doc(colRef), {
      uid: this.currentUser.uid,
      userEmail: String(this.currentUser.email || '').slice(0, 200),
      userName: String(userNameArg || this.currentUser.displayName || 'Pocket User').slice(0, 120),
      subject: String(subject || 'Feedback').slice(0, 300),
      details: String(details || '').slice(0, 5000),
      platform: 'Web',
      createdAt: serverTimestamp()
    });
  },

  async fetchAllCloudActivationCodes() {
    if (!db || !this.isDeveloperAdmin()) return [];
    try {
      const snap = await getDocs(collection(db, 'activation_codes'));
      return snap.docs.map(d => {
        const data = d.data();
        return {
          code: d.id,
          tier: data.tier || 'LIFETIME',
          status: data.isClaimed ? 'CLAIMED' : 'AVAILABLE',
          usedByEmail: data.claimedByEmail || ''
        };
      });
    } catch (_) {
      return [];
    }
  },

  async generateNewCloudActivationCode(tier = 'LIFETIME') {
    if (!db || !this.isDeveloperAdmin()) return null;
    const prefix = tier === 'LIFETIME' ? 'PKLT' : 'PK3D';
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const seg = () => Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    const code = `${prefix}-${seg()}-${seg()}`;
    await setDoc(doc(db, 'activation_codes', code), {
      code,
      tier,
      isClaimed: false,
      claimedByUid: '',
      claimedByEmail: '',
      createdAt: serverTimestamp()
    });
    return code;
  }
};
