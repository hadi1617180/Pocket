package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class WalletType(val label: String, val subtitle: String) {
    CASH("Cash", "Physical money"),
    MFS("MFS", "Mobile financial account"),
    BANK("Bank", "Savings / current"),
    TRANSIT("Transit Card", "Transit balance"),
    SAVINGS("Savings", "Dedicated savings"),
    CARD("Card", "Debit / credit card"),
    CUSTOM("Custom", "Custom wallet")
}

enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    EXPENSE_REVERSAL("Refund (Expense Reversal)")
}

enum class TransferFeeMode(val label: String) {
    NONE("No charge"),
    MANUAL("Enter charge manually"),
    PERCENTAGE("Calculate as a percentage"),
    FIXED_PLUS_PERCENTAGE("Fixed charge + percentage")
}

enum class LoanDirection(val label: String, val subtitle: String) {
    LENT("Money Lent", "Someone owes you (Receivable)"),
    BORROWED("Money Borrowed", "You owe someone (Payable)")
}

enum class LoanActivityType(val label: String) {
    DISBURSEMENT("Initial Principal"),
    REPAYMENT("Repayment"),
    ADJUSTMENT("Principal Adjustment")
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = WalletType.CASH.name,
    val accountHint: String = "",
    val openingBalance: Double = 0.0,
    val openingDate: Long = System.currentTimeMillis(),
    val currency: String = "BDT",
    val iconName: String = "WALLET",
    val colorHex: String = "#14532D",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val includeInTotalBalance: Boolean = true,
    val creditLimit: Double = 0.0,
    val statementDay: Int = 0,
    val paymentDueDay: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "INCOME" or "EXPENSE"
    val iconName: String = "CATEGORY",
    val colorHex: String = "#15803D",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isSystemFee: Boolean = false,
    val monthlyBudget: Double = 0.0
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("walletId"),
        Index("categoryId"),
        Index("date"),
        Index("transferId"),
        Index("loanActivityId"),
        Index("parentTransactionId")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME, EXPENSE, EXPENSE_REVERSAL
    val amount: Double,
    val date: Long,
    val walletId: Long,
    val categoryId: Long,
    val note: String = "",
    val reference: String = "",
    val tags: String = "",
    val transferId: Long? = null,
    val loanActivityId: Long? = null,
    val parentTransactionId: Long? = null,
    val attachmentUri: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceWalletId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationWalletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sourceWalletId"),
        Index("destinationWalletId"),
        Index("date")
    ]
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceWalletId: Long,
    val destinationWalletId: Long,
    val principalAmount: Double,
    val destinationAmount: Double = 0.0, // If > 0.0, credited amount in destination wallet's currency
    val conversionRate: Double = 1.0, // Conversion rate from source currency to destination currency
    val feeMode: String = TransferFeeMode.NONE.name,
    val fixedFeeInput: Double = 0.0,
    val percentageInput: Double = 0.0,
    val feeAmount: Double = 0.0,
    val date: Long,
    val note: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["initialWalletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("initialWalletId"), Index("startDate")]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val direction: String, // LENT or BORROWED
    val principalAmount: Double,
    val initialWalletId: Long,
    val startDate: Long,
    val dueDate: Long? = null,
    val interestRate: Double = 0.0,
    val interestType: String = "NONE", // NONE, SIMPLE, FLAT
    val note: String = "",
    val reference: String = "",
    val isArchived: Boolean = false,
    val attachmentUri: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "loan_activities",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("loanId"), Index("walletId"), Index("date")]
)
data class LoanActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val activityType: String, // DISBURSEMENT, REPAYMENT, ADJUSTMENT
    val principalPortion: Double,
    val interestPortion: Double = 0.0,
    val walletId: Long,
    val date: Long,
    val note: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("walletId"), Index("date")]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walletId: Long,
    val actualBalance: Double,
    val calculatedBalanceBefore: Double,
    val difference: Double,
    val date: Long,
    val reason: String,
    val note: String = "",
    val isReversed: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "",
    val profilePhotoUri: String = "",
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val defaultWalletId: Long? = null,
    val warnOnNegativeBalance: Boolean = true,
    val themeMode: String = "DARK", // SYSTEM, LIGHT, DARK
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val biometricUnlockEnabled: Boolean = false,
    val customBackgroundUri: String = "",
    val backgroundOpacity: Float = 0.85f,
    val loanDeadlineNotificationEnabled: Boolean = true,
    val dailyExpenseReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val dateFormatPattern: String = "dd MMM yyyy",
    val masterMonthlyBudget: Double = 0.0,
    val hideBalancesPrivacy: Boolean = false,
    val accentColorHex: String = "#F15A24"
)

enum class RecurringAmountMode {
    FIXED,
    MANUAL
}

enum class RecurringFrequency {
    MONTHLY,
    WEEKLY,
    QUARTERLY,
    YEARLY,
    CUSTOM_DAYS
}

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultWalletId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("defaultWalletId"), Index("nextDueDate")]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = "EXPENSE", // EXPENSE or INCOME
    val amountMode: String = "FIXED", // FIXED or MANUAL
    val amount: Double = 0.0, // Fixed subscription amount if FIXED, or last paid amount if MANUAL
    val categoryId: Long,
    val defaultWalletId: Long? = null, // Optional suggested wallet; user always picks wallet on Pay
    val frequency: String = "MONTHLY", // MONTHLY, WEEKLY, QUARTERLY, YEARLY, CUSTOM_DAYS
    val dayOfMonth: Int = 1, // 1..31
    val customIntervalDays: Int = 30,
    val nextDueDate: Long,
    val lastPaidDate: Long? = null,
    val note: String = "",
    val isActive: Boolean = true,
    val reminderEnabled: Boolean = true
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val iconName: String = "SAVINGS",
    val colorHex: String = "#10B981",
    val deadlineMillis: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    @get:androidx.room.Ignore
    val name: String
        get() = title
}



