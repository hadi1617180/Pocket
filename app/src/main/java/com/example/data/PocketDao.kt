package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PocketDao {

    // --- Wallets ---
    @Query("SELECT * FROM wallets ORDER BY sortOrder ASC, id ASC")
    fun observeWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllWallets(): List<WalletEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<WalletEntity>)

    @Update
    suspend fun updateWallet(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun deleteWalletById(walletId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId")
    suspend fun countTransactionsForWallet(walletId: Long): Int

    @Query("SELECT COUNT(*) FROM transfers WHERE sourceWalletId = :walletId OR destinationWalletId = :walletId")
    suspend fun countTransfersForWallet(walletId: Long): Int

    @Query("SELECT COUNT(*) FROM loan_activities WHERE walletId = :walletId")
    suspend fun countLoanActivitiesForWallet(walletId: Long): Int

    @Query("SELECT COUNT(*) FROM balance_adjustments WHERE walletId = :walletId")
    suspend fun countAdjustmentsForWallet(walletId: Long): Int

    @Transaction
    suspend fun migrateAndDeleteWallet(fromWalletId: Long, toWalletId: Long) {
        reassignWalletTransactions(fromWalletId, toWalletId)
        reassignWalletTransfersSource(fromWalletId, toWalletId)
        reassignWalletTransfersDest(fromWalletId, toWalletId)
        reassignWalletLoansInitial(fromWalletId, toWalletId)
        reassignWalletLoanActivities(fromWalletId, toWalletId)
        reassignWalletAdjustments(fromWalletId, toWalletId)
        reassignWalletRecurringDefault(fromWalletId, toWalletId)
        deleteSelfTransfersAfterMigration()
        deleteWalletById(fromWalletId)
    }

    @Query("UPDATE transactions SET walletId = :toWalletId WHERE walletId = :fromWalletId")
    suspend fun reassignWalletTransactions(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE transfers SET sourceWalletId = :toWalletId WHERE sourceWalletId = :fromWalletId")
    suspend fun reassignWalletTransfersSource(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE transfers SET destinationWalletId = :toWalletId WHERE destinationWalletId = :fromWalletId")
    suspend fun reassignWalletTransfersDest(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE loans SET initialWalletId = :toWalletId WHERE initialWalletId = :fromWalletId")
    suspend fun reassignWalletLoansInitial(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE loan_activities SET walletId = :toWalletId WHERE walletId = :fromWalletId")
    suspend fun reassignWalletLoanActivities(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE balance_adjustments SET walletId = :toWalletId WHERE walletId = :fromWalletId")
    suspend fun reassignWalletAdjustments(fromWalletId: Long, toWalletId: Long)

    @Query("UPDATE recurring_transactions SET defaultWalletId = :toWalletId WHERE defaultWalletId = :fromWalletId")
    suspend fun reassignWalletRecurringDefault(fromWalletId: Long, toWalletId: Long)

    @Query("DELETE FROM transfers WHERE sourceWalletId = destinationWalletId")
    suspend fun deleteSelfTransfersAfterMigration()

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsForCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :categoryId")
    suspend fun countRecurringForCategory(categoryId: Long): Int

    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategoryTransactions(oldCategoryId: Long, newCategoryId: Long)

    @Query("UPDATE recurring_transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategoryRecurring(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Transaction
    suspend fun reassignAndDeleteCategory(oldCategoryId: Long, newCategoryId: Long) {
        reassignCategoryTransactions(oldCategoryId, newCategoryId)
        reassignCategoryRecurring(oldCategoryId, newCategoryId)
        deleteCategoryById(oldCategoryId)
    }

    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE parentTransactionId = :parentId ORDER BY id ASC")
    suspend fun getChildTransactions(parentId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE parentTransactionId = :parentId")
    suspend fun deleteChildTransactions(parentId: Long)

    @Query("DELETE FROM transactions WHERE transferId = :transferId")
    suspend fun deleteTransactionsByTransferId(transferId: Long)

    @Query("DELETE FROM transactions WHERE loanActivityId = :loanActivityId")
    suspend fun deleteTransactionsByLoanActivityId(loanActivityId: Long)

    // --- Transfers ---
    @Query("SELECT * FROM transfers ORDER BY date DESC, id DESC")
    fun observeTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers ORDER BY date DESC, id DESC")
    suspend fun getAllTransfers(): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfers(transfers: List<TransferEntity>)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE id = :transferId")
    suspend fun deleteTransferById(transferId: Long)

    @Transaction
    suspend fun saveTransferAtomically(
        transfer: TransferEntity,
        feeCategoryId: Long,
        sourceWalletName: String,
        destWalletName: String
    ): Long {
        val savedTransferId = if (transfer.id == 0L) {
            insertTransfer(transfer)
        } else {
            updateTransfer(transfer)
            deleteTransactionsByTransferId(transfer.id)
            transfer.id
        }
        if (transfer.feeAmount > 0.0) {
            val feeNote = if (transfer.note.isNotBlank()) {
                "Transfer fee ($sourceWalletName → $destWalletName): ${transfer.note}"
            } else {
                "Transfer fee ($sourceWalletName → $destWalletName)"
            }
            insertTransaction(
                TransactionEntity(
                    type = TransactionType.EXPENSE.name,
                    amount = transfer.feeAmount,
                    date = transfer.date,
                    walletId = transfer.sourceWalletId,
                    categoryId = feeCategoryId,
                    note = feeNote,
                    reference = "TR-$savedTransferId",
                    tags = "transfer-fee",
                    transferId = savedTransferId
                )
            )
        }
        return savedTransferId
    }

    @Transaction
    suspend fun deleteTransferAtomically(transferId: Long) {
        deleteTransactionsByTransferId(transferId)
        deleteTransferById(transferId)
    }

    // --- Loans & Loan Activities ---
    @Query("SELECT * FROM loans ORDER BY startDate DESC, id DESC")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY startDate DESC, id DESC")
    suspend fun getAllLoans(): List<LoanEntity>

    @Query("SELECT * FROM loan_activities ORDER BY date DESC, id DESC")
    fun observeLoanActivities(): Flow<List<LoanActivityEntity>>

    @Query("SELECT * FROM loan_activities ORDER BY date DESC, id DESC")
    suspend fun getAllLoanActivities(): List<LoanActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoans(loans: List<LoanEntity>)

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :loanId")
    suspend fun deleteLoanById(loanId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanActivity(activity: LoanActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanActivities(activities: List<LoanActivityEntity>)

    @Query("DELETE FROM loan_activities WHERE id = :activityId")
    suspend fun deleteLoanActivityById(activityId: Long)

    @Query("SELECT * FROM loan_activities WHERE loanId = :loanId")
    suspend fun getActivitiesForLoan(loanId: Long): List<LoanActivityEntity>

    @Transaction
    suspend fun createLoanAtomically(loan: LoanEntity): Long {
        val loanId = insertLoan(loan)
        insertLoanActivity(
            LoanActivityEntity(
                loanId = loanId,
                activityType = LoanActivityType.DISBURSEMENT.name,
                principalPortion = loan.principalAmount,
                interestPortion = 0.0,
                walletId = loan.initialWalletId,
                date = loan.startDate,
                note = if (loan.direction == LoanDirection.LENT.name) {
                    "Initial loan lent to ${loan.personName}"
                } else {
                    "Initial loan borrowed from ${loan.personName}"
                }
            )
        )
        return loanId
    }

    @Transaction
    suspend fun recordLoanRepaymentAtomically(
        loan: LoanEntity,
        activity: LoanActivityEntity,
        interestCategoryId: Long
    ): Long {
        val activityId = if (activity.id == 0L) {
            insertLoanActivity(activity)
        } else {
            deleteTransactionsByLoanActivityId(activity.id)
            insertLoanActivity(activity)
        }
        if (activity.interestPortion > 0.0) {
            val isLent = loan.direction == LoanDirection.LENT.name
            insertTransaction(
                TransactionEntity(
                    type = if (isLent) TransactionType.INCOME.name else TransactionType.EXPENSE.name,
                    amount = activity.interestPortion,
                    date = activity.date,
                    walletId = activity.walletId,
                    categoryId = interestCategoryId,
                    note = if (isLent) {
                        "Loan interest received from ${loan.personName}"
                    } else {
                        "Loan interest paid to ${loan.personName}"
                    },
                    reference = "LOAN-${loan.id}",
                    tags = "loan-interest",
                    loanActivityId = activityId
                )
            )
        }
        return activityId
    }

    @Transaction
    suspend fun deleteLoanActivityAtomically(activityId: Long) {
        deleteTransactionsByLoanActivityId(activityId)
        deleteLoanActivityById(activityId)
    }

    @Transaction
    suspend fun deleteLoanAtomically(loanId: Long) {
        val activities = getActivitiesForLoan(loanId)
        activities.forEach { act ->
            deleteTransactionsByLoanActivityId(act.id)
        }
        deleteLoanById(loanId)
    }

    // --- Balance Adjustments ---
    @Query("SELECT * FROM balance_adjustments ORDER BY date DESC, id DESC")
    fun observeBalanceAdjustments(): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments ORDER BY date DESC, id DESC")
    suspend fun getAllBalanceAdjustments(): List<BalanceAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalanceAdjustment(adjustment: BalanceAdjustmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalanceAdjustments(adjustments: List<BalanceAdjustmentEntity>)

    @Update
    suspend fun updateBalanceAdjustment(adjustment: BalanceAdjustmentEntity)

    @Query("DELETE FROM balance_adjustments WHERE id = :adjustmentId")
    suspend fun deleteBalanceAdjustmentById(adjustmentId: Long)

    // --- Recurring Transactions ---
    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, nextDueDate ASC, id ASC")
    fun observeRecurringTransactions(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, nextDueDate ASC, id ASC")
    suspend fun getAllRecurringTransactions(): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id LIMIT 1")
    suspend fun getRecurringTransactionById(id: Long): RecurringTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTransaction(item: RecurringTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTransactions(items: List<RecurringTransactionEntity>)

    @Update
    suspend fun updateRecurringTransaction(item: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteRecurringTransactionById(id: Long)

    // --- Savings Goals ---
    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC, id ASC")
    fun observeSavingsGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC, id ASC")
    suspend fun getAllSavingsGoals(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoals(goals: List<SavingsGoalEntity>)

    @Update
    suspend fun updateSavingsGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteSavingsGoalById(id: Long)

    @Query("DELETE FROM savings_goals")
    suspend fun clearAllSavingsGoals()

    // --- Bulk Transaction Actions ---
    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE id IN (:transactionIds)")
    suspend fun updateTransactionsCategoryByIds(transactionIds: List<Long>, newCategoryId: Long)

    // --- Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)

    // --- Full Database Clear & Restore (Atomic) ---
    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()

    @Query("DELETE FROM loan_activities")
    suspend fun clearAllLoanActivities()

    @Query("DELETE FROM loans")
    suspend fun clearAllLoans()

    @Query("DELETE FROM balance_adjustments")
    suspend fun clearAllAdjustments()

    @Query("DELETE FROM recurring_transactions")
    suspend fun clearAllRecurringTransactions()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM wallets")
    suspend fun clearAllWallets()

    @Transaction
    suspend fun replaceAllDataAtomically(
        wallets: List<WalletEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        transfers: List<TransferEntity>,
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        adjustments: List<BalanceAdjustmentEntity>,
        settings: AppSettingsEntity,
        recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
        savingsGoals: List<SavingsGoalEntity> = emptyList()
    ) {
        clearAllTransactions()
        clearAllTransfers()
        clearAllLoanActivities()
        clearAllLoans()
        clearAllAdjustments()
        clearAllRecurringTransactions()
        clearAllSavingsGoals()
        clearAllCategories()
        clearAllWallets()

        insertWallets(wallets)
        insertCategories(categories)
        insertTransfers(transfers)
        insertLoans(loans)
        insertLoanActivities(loanActivities)
        insertTransactions(transactions)
        insertBalanceAdjustments(adjustments)
        insertRecurringTransactions(recurringTransactions)
        insertSavingsGoals(savingsGoals)
        saveSettings(settings.copy(id = 1, hasCompletedOnboarding = true))
    }
}

