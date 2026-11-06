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
    version = 16,
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

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN subscriptionTier TEXT NOT NULL DEFAULT 'FREE'") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN subscriptionExpiresAt INTEGER") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN activationCode TEXT NOT NULL DEFAULT ''") }
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN appLanguage TEXT NOT NULL DEFAULT 'EN'") }
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN boxColorHex TEXT NOT NULL DEFAULT ''") }
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN boxOpacity REAL NOT NULL DEFAULT 0.50") }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `loans_temp` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `personName` TEXT NOT NULL,
                            `direction` TEXT NOT NULL,
                            `principalAmount` REAL NOT NULL,
                            `initialWalletId` INTEGER,
                            `startDate` INTEGER NOT NULL,
                            `dueDate` INTEGER,
                            `interestRate` REAL NOT NULL,
                            `interestType` TEXT NOT NULL,
                            `note` TEXT NOT NULL,
                            `reference` TEXT NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `attachmentUri` TEXT NOT NULL,
                            FOREIGN KEY(`initialWalletId`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO `loans_temp` SELECT id, personName, direction, principalAmount, initialWalletId, startDate, dueDate, interestRate, interestType, note, reference, isArchived, attachmentUri FROM `loans`")
                    db.execSQL("DROP TABLE `loans`")
                    db.execSQL("ALTER TABLE `loans_temp` RENAME TO `loans`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_initialWalletId` ON `loans` (`initialWalletId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_startDate` ON `loans` (`startDate`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `loan_activities_temp` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `loanId` INTEGER NOT NULL,
                            `activityType` TEXT NOT NULL,
                            `principalPortion` REAL NOT NULL,
                            `interestPortion` REAL NOT NULL,
                            `walletId` INTEGER,
                            `date` INTEGER NOT NULL,
                            `note` TEXT NOT NULL,
                            FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`walletId`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO `loan_activities_temp` SELECT id, loanId, activityType, principalPortion, interestPortion, walletId, date, note FROM `loan_activities`")
                    db.execSQL("DROP TABLE `loan_activities`")
                    db.execSQL("ALTER TABLE `loan_activities_temp` RENAME TO `loan_activities`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_activities_loanId` ON `loan_activities` (`loanId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_activities_walletId` ON `loan_activities` (`walletId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_activities_date` ON `loan_activities` (`date`)")
                }
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching { db.execSQL("ALTER TABLE app_settings ADD COLUMN fundFeatureEnabled INTEGER NOT NULL DEFAULT 0") }
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
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.setForeignKeyConstraintsEnabled(true)
                            runCatching {
                                db.execSQL("UPDATE app_settings SET boxColorHex = '#2A2A2A' WHERE boxColorHex = '' OR UPPER(boxColorHex) = '#141416'")
                                db.execSQL("UPDATE app_settings SET backgroundOpacity = 0.20 WHERE ABS(backgroundOpacity - 0.85) < 0.01")
                            }
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
