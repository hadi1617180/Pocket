package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun `transfer fee calculation modes reconcile accurately`() {
        // ৳1,000 with 1.5% charge -> ৳15.00
        val pctFee = AccountingEngine.calculateTransferFee(
            principalAmount = 1000.0,
            feeMode = TransferFeeMode.PERCENTAGE,
            fixedFeeInput = 0.0,
            percentageInput = 1.5
        )
        assertEquals(15.0, pctFee, 0.001)

        // Fixed ৳10 + 1.85% on ৳2,000 -> 10 + 37 = ৳47.00
        val comboFee = AccountingEngine.calculateTransferFee(
            principalAmount = 2000.0,
            feeMode = TransferFeeMode.FIXED_PLUS_PERCENTAGE,
            fixedFeeInput = 10.0,
            percentageInput = 1.85
        )
        assertEquals(47.0, comboFee, 0.001)
    }

    @Test
    fun `wallet balances transfers loans and monthly statements reconcile without distorting income or expenses`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val oct10 = cal.timeInMillis

        val bkash = WalletEntity(id = 1, name = "bKash", openingBalance = 8200.0, openingDate = oct10 - 86400000L)
        val cash = WalletEntity(id = 2, name = "Cash", openingBalance = 3500.0, openingDate = oct10 - 86400000L)
        val wallets = listOf(bkash, cash)

        val salaryCat = CategoryEntity(id = 1, name = "Salary", type = "INCOME")
        val grocCat = CategoryEntity(id = 2, name = "Groceries", type = "EXPENSE")
        val feeCat = CategoryEntity(id = 3, name = "Fees", type = "EXPENSE", isSystemFee = true)
        val categories = listOf(salaryCat, grocCat, feeCat)

        // Transfer ৳1,000 from bKash to Cash with ৳15 fee
        val transfer = TransferEntity(
            id = 10,
            sourceWalletId = 1,
            destinationWalletId = 2,
            principalAmount = 1000.0,
            feeMode = TransferFeeMode.PERCENTAGE.name,
            percentageInput = 1.5,
            feeAmount = 15.0,
            date = oct10
        )
        val feeExpenseTx = TransactionEntity(
            id = 100,
            type = TransactionType.EXPENSE.name,
            amount = 15.0,
            date = oct10,
            walletId = 1,
            categoryId = 3,
            note = "Transfer fee",
            transferId = 10
        )
        val groceryExpenseTx = TransactionEntity(
            id = 101,
            type = TransactionType.EXPENSE.name,
            amount = 500.0,
            date = oct10,
            walletId = 2,
            categoryId = 2,
            note = "Groceries"
        )

        // Lend ৳500 from Cash to Friend A, then receive ৳200 principal back into Cash
        val loan = LoanEntity(
            id = 50,
            personName = "Friend A",
            direction = LoanDirection.LENT.name,
            principalAmount = 500.0,
            initialWalletId = 2,
            startDate = oct10
        )
        val loanDisburse = LoanActivityEntity(
            id = 501,
            loanId = 50,
            activityType = LoanActivityType.DISBURSEMENT.name,
            principalPortion = 500.0,
            walletId = 2,
            date = oct10
        )
        val loanRepay = LoanActivityEntity(
            id = 502,
            loanId = 50,
            activityType = LoanActivityType.REPAYMENT.name,
            principalPortion = 200.0,
            walletId = 2,
            date = oct10 + 1000L
        )

        val txs = listOf(feeExpenseTx, groceryExpenseTx)
        val transfers = listOf(transfer)
        val loans = listOf(loan)
        val loanActs = listOf(loanDisburse, loanRepay)

        // 1. Verify bKash balance: 8,200 - 1,000 (transfer) - 15 (fee) = 7,185.00
        val bkashSummary = AccountingEngine.calculateWalletSummary(
            wallet = bkash,
            transactions = txs,
            transfers = transfers,
            loans = loans,
            loanActivities = loanActs,
            adjustments = emptyList()
        )
        assertEquals(7185.0, bkashSummary.currentBalance, 0.001)

        // 2. Verify Cash balance: 3,500 + 1,000 (transfer in) - 500 (groceries) - 500 (lent) + 200 (repaid) = 3,700.00
        val cashSummary = AccountingEngine.calculateWalletSummary(
            wallet = cash,
            transactions = txs,
            transfers = transfers,
            loans = loans,
            loanActivities = loanActs,
            adjustments = emptyList()
        )
        assertEquals(3700.0, cashSummary.currentBalance, 0.001)

        // 3. Verify Monthly Report excludes transfer principal (1,000) and loan principal (500 / 200),
        //    counting ONLY Groceries (500) + Transfer Fee (15) = 515.00 as Expenses and 0.00 as Income
        val report = AccountingEngine.calculateMonthlyReport(
            year = 2026,
            month = Calendar.OCTOBER,
            transactions = txs,
            categories = categories,
            loans = loans,
            loanActivities = loanActs,
            wallets = wallets
        )
        assertEquals(0.0, report.totalIncome, 0.001)
        assertEquals(515.0, report.totalExpenses, 0.001)
        assertEquals(300.0, report.loanReceivableOutstanding, 0.001)

        // 4. Verify Monthly Statement Opening + Movements = Closing
        val statement = AccountingEngine.generateMonthlyStatement(
            year = 2026,
            month = Calendar.OCTOBER,
            walletFilterId = null,
            wallets = wallets,
            categories = categories,
            transactions = txs,
            transfers = transfers,
            loans = loans,
            loanActivities = loanActs,
            adjustments = emptyList()
        )
        assertEquals(11700.0, statement.openingBalance, 0.001)
        assertEquals(7185.0 + 3700.0, statement.closingBalance, 0.001)
    }

    @Test
    fun `backup validation catches invalid referential integrity`() {
        val badPayload = PocketBackupPayload(
            wallets = listOf(WalletEntity(id = 1, name = "Cash")),
            categories = listOf(CategoryEntity(id = 1, name = "Food", type = "EXPENSE")),
            transactions = listOf(
                TransactionEntity(
                    id = 1,
                    type = "EXPENSE",
                    amount = 100.0,
                    date = 1000L,
                    walletId = 999, // non-existent wallet!
                    categoryId = 1
                )
            ),
            transfers = emptyList(),
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList(),
            settings = AppSettingsEntity()
        )
        val json = BackupManager.exportToJson(badPayload)
        val result = BackupManager.validateAndParseBackup(json)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("missing wallet") == true)
    }

    @Test
    fun `subscription manager enforces free tier limits and unlocks on trial and lifetime`() {
        val now = 1_700_000_000_000L
        val trialExp = now + 24 * 60 * 60 * 1000L
        val expiredExp = now - 1000L

        assertFalse(SubscriptionManager.isSubscriptionActive(SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.isSubscriptionActive(SubscriptionTier.TRIAL_3_DAYS.name, trialExp, now))
        assertFalse(SubscriptionManager.isSubscriptionActive(SubscriptionTier.TRIAL_3_DAYS.name, expiredExp, now))
        assertTrue(SubscriptionManager.isSubscriptionActive(SubscriptionTier.LIFETIME.name, null, now))

        // Daily transaction limit: max 10 on Free
        assertTrue(SubscriptionManager.canAddTransactionToday(9, SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddTransactionToday(10, SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddTransactionToday(10, SubscriptionTier.TRIAL_3_DAYS.name, trialExp, now))
        assertTrue(SubscriptionManager.canAddTransactionToday(10, SubscriptionTier.LIFETIME.name, null, now))

        // Wallets: up to 3 default starter wallets and 1 custom wallet on Free
        assertTrue(SubscriptionManager.canAddDefaultWallet(2, SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddDefaultWallet(3, SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddDefaultWallet(3, SubscriptionTier.LIFETIME.name, null, now))
        assertTrue(SubscriptionManager.canAddCustomWallet(0, SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddCustomWallet(1, SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddCustomWallet(1, SubscriptionTier.LIFETIME.name, null, now))

        // Savings goals & Recurring bills: max 3 each on Free
        assertTrue(SubscriptionManager.canAddSavingsGoal(2, SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddSavingsGoal(3, SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddSavingsGoal(3, SubscriptionTier.TRIAL_3_DAYS.name, trialExp, now))
        assertTrue(SubscriptionManager.canAddRecurringBill(2, SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddRecurringBill(3, SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddRecurringBill(3, SubscriptionTier.LIFETIME.name, null, now))

        // Loan persons: max 3 unique people on Free
        val threePeople = setOf("Rahim", "Karim", "Salam")
        assertTrue(SubscriptionManager.canAddLoanPerson(threePeople, "Rahim", SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canAddLoanPerson(threePeople, "Jamal", SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canAddLoanPerson(threePeople, "Jamal", SubscriptionTier.TRIAL_3_DAYS.name, trialExp, now))

        // Accent color (Orange #F15A24 and Teal #14B8A6 free) & App Lock (Local PIN free, Biometrics paid)
        assertTrue(SubscriptionManager.isAccentColorAllowed("#F15A24", SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.isAccentColorAllowed("#14B8A6", SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.isAccentColorAllowed("#3B82F6", SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.isAccentColorAllowed("#3B82F6", SubscriptionTier.LIFETIME.name, null, now))
        assertTrue(SubscriptionManager.canUseAppLock(SubscriptionTier.FREE.name, null, now))
        assertFalse(SubscriptionManager.canUseBiometricLock(SubscriptionTier.FREE.name, null, now))
        assertTrue(SubscriptionManager.canUseBiometricLock(SubscriptionTier.LIFETIME.name, null, now))
    }

    @Test
    fun `fund wallet tracks personal borrow from expenses and transfers and recovers via return to fund`() {
        val now = 1_700_000_000_000L
        val officeFund = WalletEntity(id = 10, name = "Office Fund", type = WalletType.FUND.name, openingBalance = 400000.0)
        val bank = WalletEntity(id = 11, name = "Bank", type = WalletType.BANK.name, openingBalance = 50000.0)

        val officialExpense = TransactionEntity(
            id = 1,
            type = TransactionType.EXPENSE.name,
            amount = 20000.0,
            date = now,
            walletId = 10,
            categoryId = 1,
            note = "Office setup"
        )
        val personalBorrowExpense = TransactionEntity(
            id = 2,
            type = TransactionType.EXPENSE.name,
            amount = 30000.0,
            date = now + 1000L,
            walletId = 10,
            categoryId = 1,
            note = "Personal shopping",
            tags = "FUND_BORROW"
        )
        val returnTransfer = TransferEntity(
            id = 1,
            sourceWalletId = 11,
            destinationWalletId = 10,
            principalAmount = 10000.0,
            date = now + 2000L,
            note = "[FUND_RETURN] Returned personal borrow to Office Fund"
        )

        val summaryBeforeReturn = AccountingEngine.calculateWalletSummary(
            wallet = officeFund,
            transactions = listOf(officialExpense, personalBorrowExpense),
            transfers = emptyList(),
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(350000.0, summaryBeforeReturn.currentBalance, 0.001)
        assertEquals(30000.0, summaryBeforeReturn.fundBorrowedDue, 0.001)

        val summaryAfterPartialReturn = AccountingEngine.calculateWalletSummary(
            wallet = officeFund,
            transactions = listOf(officialExpense, personalBorrowExpense),
            transfers = listOf(returnTransfer),
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(360000.0, summaryAfterPartialReturn.currentBalance, 0.001)
        assertEquals(20000.0, summaryAfterPartialReturn.fundBorrowedDue, 0.001)
        assertEquals(30000.0, summaryAfterPartialReturn.fundBorrowedTotal, 0.001)
        assertEquals(10000.0, summaryAfterPartialReturn.fundReturnedTotal, 0.001)
        assertNotNull(bank)
    }

    @Test
    fun `per-wallet virtual fund accurately tracks 4 lakh cash investment, 50k bank fund transfer, 20k office expense, and 30k personal borrow`() {
        val now = 1_700_000_000_000L

        // User starts with Cash wallet (with Virtual Fund enabled, opening 0) and Bank wallet (with Virtual Fund enabled, opening 0)
        val cashWallet = WalletEntity(id = 1, name = "Cash", type = WalletType.CASH.name, openingBalance = 0.0)
            .withVirtualFund(enabled = true, openingFund = 0.0)
        val bankWallet = WalletEntity(id = 2, name = "Bank", type = WalletType.BANK.name, openingBalance = 0.0)
            .withVirtualFund(enabled = true, openingFund = 0.0)

        // 1. Receive 4,00,000 Tk Investment in Cash as Add to Fund (FUND_IN)
        val investmentIncome = TransactionEntity(
            id = 10,
            type = TransactionType.INCOME.name,
            amount = 400000.0,
            date = now,
            walletId = 1,
            categoryId = 1,
            note = "Investment capital in Cash",
            tags = "FUND_IN"
        )

        // 2. Deposit 50,000 Tk from Cash to Bank as a Fund Transfer ([FUND_TRANSFER])
        val depositToBank = TransferEntity(
            id = 20,
            sourceWalletId = 1,
            destinationWalletId = 2,
            principalAmount = 50000.0,
            date = now + 1000L,
            note = "[FUND_TRANSFER] Deposited 50k of fund into Bank"
        )

        // 3. Spend 20,000 Tk from Cash on official office work (FUND_EXPENSE)
        val officeExpense = TransactionEntity(
            id = 30,
            type = TransactionType.EXPENSE.name,
            amount = 20000.0,
            date = now + 2000L,
            walletId = 1,
            categoryId = 1,
            note = "Office work expense",
            tags = "FUND_EXPENSE"
        )

        // 4. Spend 30,000 Tk from Cash for personal use to be returned later (FUND_BORROW)
        val personalBorrow = TransactionEntity(
            id = 40,
            type = TransactionType.EXPENSE.name,
            amount = 30000.0,
            date = now + 3000L,
            walletId = 1,
            categoryId = 1,
            note = "Personal use (borrow from fund)",
            tags = "FUND_BORROW"
        )

        val txs = listOf(investmentIncome, officeExpense, personalBorrow)
        val transfers = listOf(depositToBank)

        val cashSummary = AccountingEngine.calculateWalletSummary(
            wallet = cashWallet,
            transactions = txs,
            transfers = transfers,
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        val bankSummary = AccountingEngine.calculateWalletSummary(
            wallet = bankWallet,
            transactions = txs,
            transfers = transfers,
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )

        // Verify Cash: Physical = 3,00,000, Fund = 3,00,000, Official Spent = 20,000, Borrowed Due = 30,000
        assertEquals(300000.0, cashSummary.currentBalance, 0.001)
        assertEquals(300000.0, cashSummary.fundBalance, 0.001)
        assertEquals(0.0, cashSummary.personalBalance, 0.001)
        assertEquals(20000.0, cashSummary.fundOfficialSpent, 0.001)
        assertEquals(30000.0, cashSummary.fundBorrowedDue, 0.001)

        // Verify Bank: Physical = 50,000, Fund = 50,000
        assertEquals(50000.0, bankSummary.currentBalance, 0.001)
        assertEquals(50000.0, bankSummary.fundBalance, 0.001)
        assertEquals(0.0, bankSummary.personalBalance, 0.001)

        // Verify Combined Auto-Summed Fund: 3,00,000 + 50,000 = 3,50,000 (equals Total Physical Balance without double-counting!)
        val combinedFundBalance = cashSummary.fundBalance + bankSummary.fundBalance
        val totalPhysicalBalance = cashSummary.currentBalance + bankSummary.currentBalance
        assertEquals(350000.0, combinedFundBalance, 0.001)
        assertEquals(350000.0, totalPhysicalBalance, 0.001)

        // 5. Now user receives 40,000 Tk personal salary into Bank, and returns the 30,000 Tk borrowed due from Bank into Cash Fund
        val personalSalaryInBank = TransactionEntity(
            id = 50,
            type = TransactionType.INCOME.name,
            amount = 40000.0,
            date = now + 4000L,
            walletId = 2,
            categoryId = 1,
            note = "Personal Salary"
        )
        val returnFromBankToCashFund = TransferEntity(
            id = 60,
            sourceWalletId = 2,
            destinationWalletId = 1,
            principalAmount = 30000.0,
            date = now + 5000L,
            note = "[FUND_RETURN] Returned 30k personal borrow to Cash Fund"
        )

        val txsAfterReturn = txs + personalSalaryInBank
        val transfersAfterReturn = transfers + returnFromBankToCashFund

        val cashAfterReturn = AccountingEngine.calculateWalletSummary(
            wallet = cashWallet,
            transactions = txsAfterReturn,
            transfers = transfersAfterReturn,
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        val bankAfterReturn = AccountingEngine.calculateWalletSummary(
            wallet = bankWallet,
            transactions = txsAfterReturn,
            transfers = transfersAfterReturn,
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )

        // Cash Fund is now 3,30,000, Borrowed Due is 0!
        assertEquals(330000.0, cashAfterReturn.currentBalance, 0.001)
        assertEquals(330000.0, cashAfterReturn.fundBalance, 0.001)
        assertEquals(0.0, cashAfterReturn.fundBorrowedDue, 0.001)

        // Bank physical is 50,000 + 40,000 - 30,000 = 60,000 (Fund: 50,000, Personal: 10,000)
        assertEquals(60000.0, bankAfterReturn.currentBalance, 0.001)
        assertEquals(50000.0, bankAfterReturn.fundBalance, 0.001)
        assertEquals(10000.0, bankAfterReturn.personalBalance, 0.001)

        // Combined Fund is now 3,30,000 + 50,000 = 3,80,000 (4,00,000 capital - 20,000 official expense)!
        assertEquals(380000.0, cashAfterReturn.fundBalance + bankAfterReturn.fundBalance, 0.001)
    }

    @Test
    fun `solution 2 dedicated Fund wallet keeps pocket Cash separate and tracks Bank fund transfers without double counting`() {
        val now = 1_700_000_000_000L

        // Pocket Cash has 2,000 Tk, Bank has 10,000 Tk personal, and dedicated Fund wallet has 4,00,000 Tk cash fund
        val pocketCash = WalletEntity(id = 1, name = "Cash", type = WalletType.CASH.name, openingBalance = 2000.0)
        val bankWallet = WalletEntity(id = 2, name = "Bank", type = WalletType.BANK.name, openingBalance = 10000.0)
        val fundWallet = WalletEntity(id = 3, name = "Fund", type = WalletType.FUND.name, openingBalance = 400000.0)

        // 1. Transfer 50,000 Tk from Fund (Cash) to Bank (Keep as Fund)
        val transferToBank = TransferEntity(
            id = 10,
            sourceWalletId = 3,
            destinationWalletId = 2,
            principalAmount = 50000.0,
            date = now + 1000L,
            note = "[FUND_TRANSFER] Fund transfer from Fund to Bank"
        )

        // 2. Spend 20,000 Tk for office work from Bank (FUND_EXPENSE) and 30,000 Tk Personal Borrow from Fund
        val officeFromBank = TransactionEntity(
            id = 20,
            type = TransactionType.EXPENSE.name,
            amount = 20000.0,
            date = now + 2000L,
            walletId = 2,
            categoryId = 1,
            note = "Office equipment from Bank",
            tags = "FUND_EXPENSE"
        )
        val borrowFromFund = TransactionEntity(
            id = 21,
            type = TransactionType.EXPENSE.name,
            amount = 30000.0,
            date = now + 3000L,
            walletId = 3,
            categoryId = 1,
            note = "Personal borrow from Fund",
            tags = "FUND_BORROW"
        )

        val txs = listOf(officeFromBank, borrowFromFund)
        val transfers = listOf(transferToBank)

        val cashSum = AccountingEngine.calculateWalletSummary(pocketCash, txs, transfers, emptyList(), emptyList(), emptyList())
        val bankSum = AccountingEngine.calculateWalletSummary(bankWallet, txs, transfers, emptyList(), emptyList(), emptyList())
        val fundSum = AccountingEngine.calculateWalletSummary(fundWallet, txs, transfers, emptyList(), emptyList(), emptyList())

        // Pocket Cash is untouched at 2,000 Tk!
        assertEquals(2000.0, cashSum.currentBalance, 0.001)
        assertFalse(cashSum.hasVirtualFund)

        // Bank has 10,000 personal + 50,000 fund in - 20,000 fund expense = 40,000 total (30,000 Fund + 10,000 Personal)
        assertEquals(40000.0, bankSum.currentBalance, 0.001)
        assertEquals(30000.0, bankSum.fundBalance, 0.001)
        assertEquals(10000.0, bankSum.personalBalance, 0.001)

        // Fund (Cash) has 4,00,000 - 50,000 (to Bank) - 30,000 (borrow) = 3,20,000 Cash in Fund
        assertEquals(320000.0, fundSum.currentBalance, 0.001)
        assertEquals(320000.0, fundSum.fundBalance, 0.001)

        // Total Fund Remaining across Fund (3,20,000) + Bank (30,000) = 3,50,000!
        val totalFundRemaining = fundSum.fundBalance + bankSum.fundBalance
        assertEquals(350000.0, totalFundRemaining, 0.001)

        // Total Available Balance across all wallets = 2,000 (Cash) + 40,000 (Bank) + 3,20,000 (Fund) = 3,62,000 (zero double counting!)
        val totalAvailableBalance = cashSum.currentBalance + bankSum.currentBalance + fundSum.currentBalance
        assertEquals(362000.0, totalAvailableBalance, 0.001)
    }

    @Test
    fun `fund cycle reset archives old official spent and borrowed stats while keeping personal and fund balances exact`() {
        val t0 = 1_700_000_000_000L
        val mtb = WalletEntity(id = 1, name = "MTB", type = WalletType.BANK.name, openingBalance = 10000.0)
            .withVirtualFund(enabled = true, openingFund = 0.0, resetAtMillis = 0L)

        val fundIn1 = TransactionEntity(
            id = 1,
            type = TransactionType.INCOME.name,
            amount = 50000.0,
            date = t0 + 1000L,
            walletId = 1,
            categoryId = 1,
            tags = "FUND_IN"
        )
        val officialExp1 = TransactionEntity(
            id = 2,
            type = TransactionType.EXPENSE.name,
            amount = 20000.0,
            date = t0 + 2000L,
            walletId = 1,
            categoryId = 1,
            tags = "FUND_EXPENSE"
        )
        val txsBeforeReset = listOf(fundIn1, officialExp1)

        val summaryBefore = AccountingEngine.calculateWalletSummary(
            wallet = mtb,
            transactions = txsBeforeReset,
            transfers = emptyList(),
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        assertEquals(40000.0, summaryBefore.currentBalance, 0.001) // 10k personal + 30k fund
        assertEquals(10000.0, summaryBefore.personalBalance, 0.001)
        assertEquals(30000.0, summaryBefore.fundBalance, 0.001)
        assertEquals(20000.0, summaryBefore.fundOfficialSpent, 0.001)

        // Now reset cycle stats at t0 + 5000L while keeping current fund balance (30,000) intact:
        val resetTime = t0 + 5000L
        val mtbAfterCycleReset = mtb.withVirtualFund(
            enabled = true,
            openingFund = summaryBefore.fundBalance,
            resetAtMillis = resetTime
        )
        assertEquals(resetTime, mtbAfterCycleReset.fundResetAtMillis)

        val summaryAfterReset = AccountingEngine.calculateWalletSummary(
            wallet = mtbAfterCycleReset,
            transactions = txsBeforeReset,
            transfers = emptyList(),
            loans = emptyList(),
            loanActivities = emptyList(),
            adjustments = emptyList()
        )
        // Balances remain 100% unchanged, while Official Spent & Borrowed Due reset to 0 for the new cycle!
        assertEquals(40000.0, summaryAfterReset.currentBalance, 0.001)
        assertEquals(10000.0, summaryAfterReset.personalBalance, 0.001)
        assertEquals(30000.0, summaryAfterReset.fundBalance, 0.001)
        assertEquals(0.0, summaryAfterReset.fundOfficialSpent, 0.001)
        assertEquals(0.0, summaryAfterReset.fundBorrowedDue, 0.001)
    }
}


