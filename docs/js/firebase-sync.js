// firebase-sync.js — Real-time Firebase Auth & Cloud Firestore Sync matching PocketCloudSyncRepository.kt
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
  onSnapshot
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
let activeBackupUnsub = null;

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
    if (activeBackupUnsub) {
      activeBackupUnsub();
      activeBackupUnsub = null;
    }
  },

  startRealtimeListeners(uid, onRemoteSnapshot) {
    this.stopRealtimeListeners();
    if (!db || !uid) return;
    const backupRef = doc(db, 'users', uid, 'backups', 'latest');
    activeBackupUnsub = onSnapshot(
      backupRef,
      (snap) => {
        if (!snap.exists()) return;
        const data = snap.data();
        if (data && data.backupJson) {
          const parsed = BackupManager.validateAndParseBackup(data.backupJson);
          if (parsed.isValid && parsed.payload) {
            onRemoteSnapshot({
              updatedAtMillis: Number(data.updatedAtMillis) || 0,
              payload: parsed.payload
            });
          }
        }
      },
      (err) => console.warn("Realtime backup listener warning:", err)
    );
  },

  async pullSnapshot(uid) {
    if (!db || !uid) return null;
    const profileRef = doc(db, 'users', uid, 'profile', 'main');
    const backupRef = doc(db, 'users', uid, 'backups', 'latest');
    const [profileSnap, backupSnap] = await Promise.all([getDoc(profileRef), getDoc(backupRef)]);
    if (!profileSnap.exists() && !backupSnap.exists()) return null;

    const pData = profileSnap.exists() ? profileSnap.data() : {};
    const bData = backupSnap.exists() ? backupSnap.data() : {};
    const parsed = bData.backupJson ? BackupManager.validateAndParseBackup(bData.backupJson) : null;

    return {
      profile: {
        uid,
        email: pData.email || '',
        displayName: pData.displayName || '',
        photoUrl: pData.photoUrl || '',
        currencySymbol: pData.currencySymbol || '৳',
        themeMode: pData.themeMode || 'DARK',
        accentColorHex: pData.accentColorHex || '#F15A24',
        hasCompletedOnboarding: Boolean(pData.hasCompletedOnboarding),
        updatedAtMillis: Number(pData.updatedAtMillis) || 0
      },
      payload: parsed?.payload || null,
      backupUpdatedAtMillis: Number(bData.updatedAtMillis) || 0
    };
  },

  async pushSnapshot(state) {
    const user = this.currentUser;
    if (!db || !user?.uid) throw new Error("Not signed in with Google.");
    const now = Date.now();
    const payload = BackupManager.createBackupPayload(state);
    payload.exportedAtMillis = now;
    const backupJson = JSON.stringify(payload);

    const profileRef = doc(db, 'users', user.uid, 'profile', 'main');
    const backupRef = doc(db, 'users', user.uid, 'backups', 'latest');

    const profileMap = {
      uid: user.uid,
      email: user.email || '',
      displayName: state.settings.userName || user.displayName || 'Pocket User',
      photoUrl: user.photoUrl || '',
      currencySymbol: state.settings.currencySymbol || '৳',
      themeMode: state.settings.themeMode || 'DARK',
      accentColorHex: state.settings.accentColorHex || '#F15A24',
      hasCompletedOnboarding: Boolean(state.settings.hasCompletedOnboarding),
      updatedAtMillis: now
    };

    const backupMap = {
      uid: user.uid,
      schemaVersion: 1,
      format: 'pocket-backup-v1',
      backupJson,
      walletCount: state.wallets.length,
      transactionCount: state.transactions.length,
      loanCount: state.loans.length,
      updatedAtMillis: now
    };

    await Promise.all([
      setDoc(profileRef, profileMap),
      setDoc(backupRef, backupMap)
    ]);
    return now;
  },

  async deleteAllCloudDataForCurrentUser() {
    const user = this.currentUser;
    if (!db || !user?.uid) return;
    this.stopRealtimeListeners();
    await Promise.allSettled([
      deleteDoc(doc(db, 'users', user.uid, 'profile', 'main')),
      deleteDoc(doc(db, 'users', user.uid, 'backups', 'latest'))
    ]);
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
          const status = String(data.status || 'UNUSED').toUpperCase();
          const usedByUid = String(data.usedByUid || '');
          const tierStr = String(data.tier || '').toUpperCase();
          if (status === 'USED' && usedByUid && usedByUid !== uid) {
            throw new Error("This activation code has already been used by another account.");
          }
          tx.set(codeRef, {
            code: normalized,
            tier: tierStr,
            status: 'USED',
            usedByUid: uid,
            usedByEmail: email,
            usedAtMillis: Date.now(),
            createdAtMillis: data.createdAtMillis || Date.now()
          });
          return tierStr === 'LIFETIME' ? 'LIFETIME' : 'THREE_DAYS';
        });
        if (tier) return tier;
      } catch (e) {
        if (e.message && e.message.includes('already been used')) throw e;
      }
    }

    // 2. Fallback to offline SHA-256 hash bank in assets/activation_codes.json
    const offlineTier = await SubscriptionManager.verifyAndDecodeOfflineCode(normalized);
    if (offlineTier) return offlineTier;

    throw new Error("Invalid or unrecognized activation code.");
  },

  async submitFeedbackReport(subject, details, userName) {
    if (!db || !this.currentUser?.uid) {
      throw new Error("Please sign in with Google to submit feedback.");
    }
    const now = Date.now();
    const docId = `${now}_${this.currentUser.uid.slice(0, 8)}`;
    await setDoc(doc(db, 'feedback_reports', docId), {
      id: docId,
      uid: this.currentUser.uid,
      userEmail: this.currentUser.email || '',
      userName: userName || this.currentUser.displayName || 'Pocket User',
      subject: subject.trim(),
      details: details.trim(),
      createdAtMillis: now,
      appVersion: 'Web 1.0',
      platform: 'Web (GitHub Pages)'
    });
  },

  async fetchAdminFeedbackReports() {
    if (!db || !this.isDeveloperAdmin()) return [];
    const snap = await getDocs(collection(db, 'feedback_reports'));
    const items = [];
    snap.forEach(d => items.push({ id: d.id, ...d.data() }));
    return items.sort((a, b) => (Number(b.createdAtMillis) || 0) - (Number(a.createdAtMillis) || 0));
  },

  async deleteAdminFeedbackReport(id) {
    if (!db || !this.isDeveloperAdmin()) return;
    await deleteDoc(doc(db, 'feedback_reports', id));
  },

  async fetchAllCloudActivationCodes() {
    if (!db || !this.isDeveloperAdmin()) return [];
    const snap = await getDocs(collection(db, 'activation_codes'));
    const list = [];
    snap.forEach(d => list.push({ code: d.id, ...d.data() }));
    return list.sort((a, b) => String(a.code).localeCompare(String(b.code)));
  },

  async generateNewCloudActivationCode(tier) {
    if (!db || !this.isDeveloperAdmin()) throw new Error("Admin access required.");
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const rand4 = () => Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    const prefix = tier === 'LIFETIME' ? 'PKLT' : 'PK3D';
    const newCode = `${prefix}-${rand4()}-${rand4()}`;
    const now = Date.now();
    const record = {
      code: newCode,
      tier,
      status: 'UNUSED',
      usedByUid: '',
      usedByEmail: '',
      usedAtMillis: 0,
      createdAtMillis: now
    };
    await setDoc(doc(db, 'activation_codes', newCode), record);
    return record;
  },

  async releaseOrDeleteCloudActivationCode(code, deletePermanently = false) {
    if (!db || !this.isDeveloperAdmin()) return;
    const ref = doc(db, 'activation_codes', code);
    if (deletePermanently) {
      await deleteDoc(ref);
    } else {
      const snap = await getDoc(ref);
      if (snap.exists()) {
        const d = snap.data();
        await setDoc(ref, {
          ...d,
          status: 'UNUSED',
          usedByUid: '',
          usedByEmail: '',
          usedAtMillis: 0
        });
      }
    }
  }
};
