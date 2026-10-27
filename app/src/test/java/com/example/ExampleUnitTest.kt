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
}
