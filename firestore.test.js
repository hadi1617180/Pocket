const { test, describe, before, after, beforeEach } = require('node:test');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp
} = require('@firebase/firestore');
const { readFileSync } = require('node:fs');

const PROJECT_ID = 'gen-lang-client-0285964029';
const DATABASE_ID = 'ai-studio-android-pocket-90c40f1c-a604-4528-b70a-bb3c8a09d67f';

let testEnv;

describe('Pocket Firestore Security Rules', () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: readFileSync('firestore.rules', 'utf8'),
        host: '127.0.0.1',
        port: 8085
      }
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  test('denies unauthenticated access to user profile and wallets', async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
    await assertFails(getDoc(doc(unauthedDb, 'users/alice')));
    await assertFails(
      setDoc(doc(unauthedDb, 'users/alice'), {
        userId: 'alice',
        displayName: 'Alice',
        currencyCode: 'BDT',
        currencySymbol: '৳',
        hasCompletedOnboarding: true,
        syncVersion: 1,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
    await assertFails(getDoc(doc(unauthedDb, 'users/alice/wallets/w_1')));
  });

  test('allows owner to create, read, and update valid UserProfile and denies cross-user access', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore(DATABASE_ID);
    const bobDb = testEnv.authenticatedContext('bob').firestore(DATABASE_ID);

    const profileRef = doc(aliceDb, 'users/alice');
    await assertSucceeds(
      setDoc(profileRef, {
        userId: 'alice',
        displayName: 'Alice Rahman',
        email: 'alice@example.com',
        photoUrl: 'https://lh3.googleusercontent.com/a/alice',
        currencyCode: 'BDT',
        currencySymbol: '৳',
        themeMode: 'DARK',
        defaultWalletId: 1,
        warnOnNegativeBalance: true,
        loanDeadlineNotificationEnabled: true,
        dailyExpenseReminderEnabled: true,
        dailyReminderHour: 20,
        dailyReminderMinute: 0,
        hasCompletedOnboarding: true,
        syncVersion: 1,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    await assertSucceeds(getDoc(profileRef));
    await assertFails(getDoc(doc(bobDb, 'users/alice')));

    // Owner can update displayName and syncVersion
    await assertSucceeds(
      updateDoc(profileRef, {
        displayName: 'Alice Updated',
        syncVersion: 2,
        updatedAt: serverTimestamp()
      })
    );

    // Owner cannot mutate userId
    await assertFails(
      updateDoc(profileRef, {
        userId: 'bob',
        updatedAt: serverTimestamp()
      })
    );
  });

  test('validates schema and ownership for wallets, categories, transactions, transfers, loans, activities, and adjustments', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore(DATABASE_ID);
    const bobDb = testEnv.authenticatedContext('bob').firestore(DATABASE_ID);

    // 1. Wallet
    const walletRef = doc(aliceDb, 'users/alice/wallets/1');
    await assertSucceeds(
      setDoc(walletRef, {
        userId: 'alice',
        entityId: 1,
        name: 'Cash',
        type: 'CASH',
        accountHint: '',
        openingBalance: 1500.0,
        openingDate: 1700000000000,
        currency: 'BDT',
        iconName: 'CASH',
        colorHex: '#2E9E5B',
        sortOrder: 1,
        isArchived: false,
        includeInTotalBalance: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
    await assertFails(getDoc(doc(bobDb, 'users/alice/wallets/1')));

    // Rejects invalid wallet (empty name)
    await assertFails(
      setDoc(doc(aliceDb, 'users/alice/wallets/2'), {
        userId: 'alice',
        entityId: 2,
        name: '',
        type: 'CASH',
        openingBalance: 0,
        openingDate: 1700000000000,
        currency: 'BDT',
        iconName: 'CASH',
        colorHex: '#2E9E5B',
        sortOrder: 2,
        isArchived: false,
        includeInTotalBalance: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    // 2. Category
    await assertSucceeds(
      setDoc(doc(aliceDb, 'users/alice/categories/1'), {
        userId: 'alice',
        entityId: 1,
        name: 'Salary',
        type: 'INCOME',
        iconName: 'WORK',
        colorHex: '#4ADE80',
        sortOrder: 1,
        isArchived: false,
        isSystemFee: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    // 3. Transaction
    await assertSucceeds(
      setDoc(doc(aliceDb, 'users/alice/transactions/10'), {
        userId: 'alice',
        entityId: 10,
        type: 'INCOME',
        amount: 25000.0,
        date: 1700000000000,
        walletId: 1,
        categoryId: 1,
        note: 'October Salary',
        reference: 'SAL-10',
        tags: 'work',
        transferId: null,
        loanActivityId: null,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    // 4. Transfer
    await assertSucceeds(
      setDoc(doc(aliceDb, 'users/alice/transfers/5'), {
        userId: 'alice',
        entityId: 5,
        sourceWalletId: 1,
        destinationWalletId: 2,
        principalAmount: 500.0,
        feeMode: 'NONE',
        fixedFeeInput: 0.0,
        percentageInput: 0.0,
        feeAmount: 0.0,
        date: 1700000000000,
        note: 'Top up',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    // 5. Loan & LoanActivity
    await assertSucceeds(
      setDoc(doc(aliceDb, 'users/alice/loans/3'), {
        userId: 'alice',
        entityId: 3,
        personName: 'Rahim',
        direction: 'LENT',
        principalAmount: 2000.0,
        initialWalletId: 1,
        startDate: 1700000000000,
        dueDate: null,
        interestRate: 0.0,
        interestType: 'NONE',
        note: 'Friendly loan',
        reference: '',
        isArchived: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    await assertSucceeds(
      setDoc(doc(aliceDb, 'users/alice/loan_activities/4'), {
        userId: 'alice',
        entityId: 4,
        loanId: 3,
        activityType: 'DISBURSEMENT',
        principalPortion: 2000.0,
        interestPortion: 0.0,
        walletId: 1,
        date: 1700000000000,
        note: 'Disbursed',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    // 6. Balance Adjustment
    const adjRef = doc(aliceDb, 'users/alice/balance_adjustments/7');
    await assertSucceeds(
      setDoc(adjRef, {
        userId: 'alice',
        entityId: 7,
        walletId: 1,
        actualBalance: 1600.0,
        calculatedBalanceBefore: 1500.0,
        difference: 100.0,
        date: 1700000000000,
        reason: 'Wallet count reconciliation',
        note: '',
        isReversed: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );

    await assertSucceeds(deleteDoc(adjRef));
  });
});
