package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        LoanEntity::class,
        LoanActivityEntity::class,
        BalanceAdjustmentEntity::class,
        AppSettingsEntity::class,
        RecurringTransactionEntity::class,
        SavingsGoalEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun pocketDao(): PocketDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN userName TEXT NOT NULL DEFAULT ''") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN profilePhotoUri TEXT NOT NULL DEFAULT ''") }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE wallets ADD COLUMN includeInTotalBalance INTEGER NOT NULL DEFAULT 1") }
                runCatching { db.execSQL("UPDATE wallets SET includeInTotalBalance = 0 WHERE LOWER(name) = 'savings'") }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN biometricUnlockEnabled INTEGER NOT NULL DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN customBackgroundUri TEXT NOT NULL DEFAULT ''") }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN loanDeadlineNotificationEnabled INTEGER NOT NULL DEFAULT 1") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN dailyExpenseReminderEnabled INTEGER NOT NULL DEFAULT 1") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN dailyReminderHour INTEGER NOT NULL DEFAULT 20") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN dailyReminderMinute INTEGER NOT NULL DEFAULT 0") }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `amountMode` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `categoryId` INTEGER NOT NULL,
                            `defaultWalletId` INTEGER,
                            `frequency` TEXT NOT NULL,
                            `dayOfMonth` INTEGER NOT NULL,
                            `nextDueDate` INTEGER NOT NULL,
                            `lastPaidDate` INTEGER,
                            `note` TEXT NOT NULL,
                            `isActive` INTEGER NOT NULL,
                            `reminderEnabled` INTEGER NOT NULL,
                            FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                            FOREIGN KEY(`defaultWalletId`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_categoryId` ON `recurring_transactions` (`categoryId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_defaultWalletId` ON `recurring_transactions` (`defaultWalletId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_nextDueDate` ON `recurring_transactions` (`nextDueDate`)")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE transfers ADD COLUMN destinationAmount REAL NOT NULL DEFAULT 0.0") }
                runCatching { db.execSQL("ALTER TABLE transfers ADD COLUMN conversionRate REAL NOT NULL DEFAULT 1.0") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN backgroundOpacity REAL NOT NULL DEFAULT 0.85") }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN masterMonthlyBudget REAL NOT NULL DEFAULT 0.0") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN hideBalancesPrivacy INTEGER NOT NULL DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN customIntervalDays INTEGER NOT NULL DEFAULT 30") }
                runCatching {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `savings_goals` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `targetAmount` REAL NOT NULL,
                            `savedAmount` REAL NOT NULL,
                            `iconName` TEXT NOT NULL,
                            `colorHex` TEXT NOT NULL,
                            `deadlineMillis` INTEGER,
                            `note` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN accentColorHex TEXT NOT NULL DEFAULT '#F15A24'") }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudget REAL NOT NULL DEFAULT 0.0") }
                runCatching { db.execSQL("ALTER TABLE transactions ADD COLUMN parentTransactionId INTEGER") }
                runCatching { db.execSQL("ALTER TABLE transactions ADD COLUMN attachmentUri TEXT NOT NULL DEFAULT ''") }
                runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_parentTransactionId` ON `transactions` (`parentTransactionId`)") }
                runCatching { db.execSQL("ALTER TABLE loans ADD COLUMN attachmentUri TEXT NOT NULL DEFAULT ''") }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE wallets ADD COLUMN creditLimit REAL NOT NULL DEFAULT 0.0") }
                runCatching { db.execSQL("ALTER TABLE wallets ADD COLUMN statementDay INTEGER NOT NULL DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE wallets ADD COLUMN paymentDueDay INTEGER NOT NULL DEFAULT 0") }
            }
        }

        @Volatile
        private var INSTANCE: PocketDatabase? = null

        fun getDatabase(context: Context): PocketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketDatabase::class.java,
                    "pocket_finance.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.setForeignKeyConstraintsEnabled(true)
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
