package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

data class StarterWalletOption(
    val name: String,
    val type: WalletType,
    val accountHint: String,
    val iconName: String,
    val colorHex: String,
    val defaultBalance: Double = 0.0,
    val selectedByDefault: Boolean = true,
    val includeInTotalBalance: Boolean = true
)

data class StarterLoanInput(
    val personName: String,
    val direction: LoanDirection,
    val principalAmount: Double,
    val walletName: String = "",
    val dueDate: Long? = null
)

class PocketRepository(private val dao: PocketDao) {

    val walletsFlow: Flow<List<WalletEntity>> = dao.observeWallets()
    val categoriesFlow: Flow<List<CategoryEntity>> = dao.observeCategories()
    val transactionsFlow: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val transfersFlow: Flow<List<TransferEntity>> = dao.observeTransfers()
    val loansFlow: Flow<List<LoanEntity>> = dao.observeLoans()
    val loanActivitiesFlow: Flow<List<LoanActivityEntity>> = dao.observeLoanActivities()
    val adjustmentsFlow: Flow<List<BalanceAdjustmentEntity>> = dao.observeBalanceAdjustments()
    val recurringTransactionsFlow: Flow<List<RecurringTransactionEntity>> = dao.observeRecurringTransactions()
    val savingsGoalsFlow: Flow<List<SavingsGoalEntity>> = dao.observeSavingsGoals()
    val settingsFlow: Flow<AppSettingsEntity?> = dao.observeSettings()

    companion object {
        const val DEFAULT_INCOME_COLOR_HEX = "#4ADE80"
        const val DEFAULT_EXPENSE_COLOR_HEX = "#F87171"

        fun defaultStarterWallets(): List<StarterWalletOption> = listOf(
            StarterWalletOption("Cash", WalletType.CASH, "", "CASH", "#2E9E5B", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Bank", WalletType.BANK, "", "BANK", "#2563EB", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("MTB", WalletType.BANK, "Mutual Trust Bank", "MTB", "#006A4E", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Brac", WalletType.BANK, "BRAC Bank", "BRAC", "#0072BC", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Bkash", WalletType.MFS, "", "BKASH", "#E2136E", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Nagad", WalletType.MFS, "", "NAGAD", "#F37021", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Rocket", WalletType.MFS, "", "ROCKET", "#8C3494", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Rapid Pass", WalletType.TRANSIT, "", "RAPID_PASS", "#1E3A8A", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Card", WalletType.CARD, "", "CARD", "#DC2626", 1000.0, false, includeInTotalBalance = true),
            StarterWalletOption("Savings", WalletType.SAVINGS, "", "SAVINGS", "#F15A24", 1000.0, false, includeInTotalBalance = false)
        )

        fun canonicalWalletSortOrder(walletName: String): Int? {
            return when (walletName.trim().lowercase()) {
                "cash" -> 1
                "bank", "bank account" -> 2
                "mtb", "mutual trust bank" -> 3
                "brac", "brac bank" -> 4
                "bkash" -> 5
                "nagad" -> 6
                "rocket" -> 7
                "rapid pass", "metro rapid pass" -> 8
                "card" -> 9
                "savings" -> 10
                else -> null
            }
        }

        fun defaultCategories(): List<CategoryEntity> = listOf(
            // Default Income Categories (all green icons)
            CategoryEntity(name = "Salary", type = "INCOME", iconName = "WORK", colorHex = DEFAULT_INCOME_COLOR_HEX, sortOrder = 1),
            CategoryEntity(name = "Freelance", type = "INCOME", iconName = "LAPTOP", colorHex = DEFAULT_INCOME_COLOR_HEX, sortOrder = 2),
            CategoryEntity(name = "Gift", type = "INCOME", iconName = "CARD_GIFTCARD", colorHex = DEFAULT_INCOME_COLOR_HEX, sortOrder = 3),
            CategoryEntity(name = "Client payment", type = "INCOME", iconName = "PAYMENTS", colorHex = DEFAULT_INCOME_COLOR_HEX, sortOrder = 4),
            CategoryEntity(name = "Others", type = "INCOME", iconName = "MORE_HORIZ", colorHex = DEFAULT_INCOME_COLOR_HEX, sortOrder = 5),

            // Default Expense Categories (all red icons)
            CategoryEntity(name = "Bills", type = "EXPENSE", iconName = "BOLT", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 1),
            CategoryEntity(name = "Shopping", type = "EXPENSE", iconName = "SHOPPING_BAG", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 2),
            CategoryEntity(name = "Transport", type = "EXPENSE", iconName = "DIRECTIONS_BUS", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 3),
            CategoryEntity(name = "Food & Dining", type = "EXPENSE", iconName = "RESTAURANT", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 4),
            CategoryEntity(name = "Bazar", type = "EXPENSE", iconName = "SHOPPING_BASKET", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 5),
            CategoryEntity(name = "Medicine", type = "EXPENSE", iconName = "MEDICATION", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 6),
            CategoryEntity(name = "Entertainment", type = "EXPENSE", iconName = "MOVIE", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 7),
            CategoryEntity(name = "Donation", type = "EXPENSE", iconName = "VOLUNTEER_ACTIVISM", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 8),
            CategoryEntity(name = "Service Charge", type = "EXPENSE", iconName = "BUILD", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 9),
            CategoryEntity(name = "Health", type = "EXPENSE", iconName = "MEDICAL_SERVICES", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 10),
            CategoryEntity(name = "Education", type = "EXPENSE", iconName = "SCHOOL", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 11),
            CategoryEntity(name = "Mobile Recharge", type = "EXPENSE", iconName = "PHONE_ANDROID", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 12),
            CategoryEntity(name = "Others", type = "EXPENSE", iconName = "MORE_HORIZ", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 13),
            CategoryEntity(name = "Fees & Charges", type = "EXPENSE", iconName = "RECEIPT", colorHex = DEFAULT_EXPENSE_COLOR_HEX, sortOrder = 14, isSystemFee = true)
        )

        /**
         * Calculates the next cycle's due date from `fromMillis` based on `frequency`, `dayOfMonth`, and `customIntervalDays`.
         */
        fun calculateNextRecurringDueDate(
            fromMillis: Long,
            frequency: String,
            dayOfMonth: Int = 1,
            customIntervalDays: Int = 30
        ): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            when (frequency.uppercase()) {
                RecurringFrequency.WEEKLY.name -> {
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                }
                RecurringFrequency.QUARTERLY.name -> {
                    cal.add(Calendar.MONTH, 3)
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDay))
                }
                RecurringFrequency.YEARLY.name -> {
                    cal.add(Calendar.YEAR, 1)
                }
                RecurringFrequency.CUSTOM_DAYS.name -> {
                    cal.add(Calendar.DAY_OF_YEAR, customIntervalDays.coerceIn(1, 3650))
                }
                else -> {
                    cal.add(Calendar.MONTH, 1)
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDay))
                }
            }
            return cal.timeInMillis
        }

        /**
         * Calculates a snoozed reminder timestamp for tomorrow morning (10:00 AM next day, or +24h).
         */
        fun calculateTomorrowReminderMillis(nowMillis: Long = System.currentTimeMillis()): Long {
            return Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    suspend fun ensureDefaultCategoriesExist(): List<CategoryEntity> {
        // Migrate existing wallets to canonical names, icons, and exact 1..10 sort order
        val existingWallets = dao.getAllWallets()
        if (existingWallets.isNotEmpty()) {
            val currentAfterRename = existingWallets.toMutableList()
            val maxSort = currentAfterRename.maxOfOrNull { it.sortOrder } ?: currentAfterRename.size
            var nextSort = maxSort + 10

            // Normalize names, icons, and canonical 1..10 sortOrder across existing wallets only
            dao.getAllWallets().forEach { w ->
                val lower = w.name.trim().lowercase()
                val canonicalName = when (lower) {
                    "cash" -> "Cash"
                    "bank", "bank account" -> "Bank"
                    "mtb", "mutual trust bank" -> "MTB"
                    "brac", "brac bank" -> "Brac"
                    "bkash" -> "Bkash"
                    "nagad" -> "Nagad"
                    "rocket" -> "Rocket"
                    "rapid pass", "metro rapid pass" -> "Rapid Pass"
                    "card" -> "Card"
                    "savings" -> "Savings"
                    else -> w.name
                }
                val canonicalIcon = when {
                    w.iconName.startsWith("CUSTOM_URI:") -> w.iconName
                    canonicalName == "Cash" -> "CASH"
                    canonicalName == "Bank" -> "BANK"
                    canonicalName == "MTB" -> "MTB"
                    canonicalName == "Brac" -> "BRAC"
                    canonicalName == "Bkash" -> "BKASH"
                    canonicalName == "Nagad" -> "NAGAD"
                    canonicalName == "Rocket" -> "ROCKET"
                    canonicalName == "Rapid Pass" -> "RAPID_PASS"
                    canonicalName == "Card" -> "CARD"
                    canonicalName == "Savings" -> "SAVINGS"
                    else -> w.iconName
                }
                val preservedOrder = if (w.sortOrder > 0) {
                    w.sortOrder
                } else {
                    canonicalWalletSortOrder(canonicalName) ?: nextSort++
                }
                val updated = w.copy(
                    name = canonicalName,
                    iconName = canonicalIcon,
                    sortOrder = preservedOrder,
                    isArchived = false,
                    type = if (canonicalName == "Savings") WalletType.SAVINGS.name else w.type
                )
                if (updated != w) {
                    dao.updateWallet(updated)
                }
            }
        }

        // Enforce BDT currency on existing settings
        val currentSettings = dao.getSettings()
        if (currentSettings != null && (currentSettings.currencyCode != "BDT" || currentSettings.currencySymbol != "৳")) {
            dao.saveSettings(currentSettings.copy(currencyCode = "BDT", currencySymbol = "৳"))
        }

        val existing = dao.getAllCategories()
        val defaults = defaultCategories()
        if (existing.isEmpty()) {
            dao.insertCategories(defaults)
            return dao.getAllCategories()
        }

        // Synchronize legacy category names & ensure all required default categories (plus Interest if present) have normal green/red vector icons
        val renameMap = mapOf(
            "INCOME" to mapOf(
                "Other Income" to "Others",
                "Business" to "Client payment"
            ),
            "EXPENSE" to mapOf(
                "Bills & Utilities" to "Bills",
                "Groceries" to "Bazar",
                "Other Expense" to "Others"
            )
        )
        existing.forEach { cat ->
            val mappedName = renameMap[cat.type]?.get(cat.name) ?: cat.name
            val matchingDefault = defaults.firstOrNull {
                it.type == cat.type && it.name.equals(mappedName, ignoreCase = true)
            }
            val targetColor = if (cat.type == "INCOME") DEFAULT_INCOME_COLOR_HEX else DEFAULT_EXPENSE_COLOR_HEX
            val normalizedIcon = when {
                matchingDefault != null -> matchingDefault.iconName
                cat.type == "INCOME" && mappedName.equals("Interest", ignoreCase = true) -> "TRENDING_UP"
                cat.type == "INCOME" && mappedName.equals("Client payment", ignoreCase = true) -> "PAYMENTS"
                cat.iconName.uppercase() == "SAVINGS" || cat.iconName.uppercase() == "CASH" -> "MONETIZATION_ON"
                else -> cat.iconName
            }
            val updated = cat.copy(
                name = matchingDefault?.name ?: mappedName,
                iconName = normalizedIcon,
                colorHex = targetColor,
                sortOrder = matchingDefault?.sortOrder ?: cat.sortOrder,
                isArchived = false,
                isSystemFee = matchingDefault?.isSystemFee ?: cat.isSystemFee
            )
            if (updated != cat) {
                dao.updateCategory(updated)
            }
        }

        val afterUpdate = dao.getAllCategories()
        defaults.forEach { defCat ->
            if (afterUpdate.none { it.type == defCat.type && it.name.equals(defCat.name, ignoreCase = true) }) {
                dao.insertCategory(defCat)
            }
        }

        return dao.getAllCategories()
    }

    suspend fun getOrCreateFeeCategoryId(): Long {
        val categories = ensureDefaultCategoriesExist()
        val feeCat = categories.firstOrNull { it.isSystemFee && it.type == "EXPENSE" }
            ?: categories.firstOrNull { it.name.contains("Fee", ignoreCase = true) && it.type == "EXPENSE" }
        if (feeCat != null) return feeCat.id
        return dao.insertCategory(
            CategoryEntity(
                name = "Fees & Charges",
                type = "EXPENSE",
                iconName = "RECEIPT",
                colorHex = DEFAULT_EXPENSE_COLOR_HEX,
                sortOrder = 99,
                isSystemFee = true
            )
        )
    }

    suspend fun getOrCreateLoanInterestCategoryId(isIncome: Boolean): Long {
        val categories = ensureDefaultCategoriesExist()
        val targetType = if (isIncome) "INCOME" else "EXPENSE"
        val existing = categories.firstOrNull {
            it.type == targetType && (it.name.contains("Interest", ignoreCase = true) || it.name.contains("Others", ignoreCase = true) || it.isSystemFee)
        } ?: categories.firstOrNull { it.type == targetType }
        if (existing != null) return existing.id
        return dao.insertCategory(
            CategoryEntity(
                name = if (isIncome) "Others" else "Fees & Charges",
                type = targetType,
                iconName = if (isIncome) "MORE_HORIZ" else "RECEIPT",
                colorHex = if (isIncome) DEFAULT_INCOME_COLOR_HEX else DEFAULT_EXPENSE_COLOR_HEX,
                sortOrder = 90
            )
        )
    }

    suspend fun completeOnboarding(
        currencyCode: String,
        currencySymbol: String,
        selectedWallets: List<Pair<StarterWalletOption, Double>>,
        loadSampleTransactions: Boolean,
        starterLoans: List<StarterLoanInput> = emptyList(),
        userName: String = "",
        profilePhotoUri: String = ""
    ) {
        val fixedCode = "BDT"
        val fixedSymbol = "৳"
        ensureDefaultCategoriesExist()
        val now = System.currentTimeMillis()
        val startOfMonth = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        if (loadSampleTransactions) {
            seedShowcaseData(fixedCode, fixedSymbol)
            val currentSettings = dao.getSettings()
            if (currentSettings != null && (userName.isNotBlank() || profilePhotoUri.isNotBlank())) {
                dao.saveSettings(
                    currentSettings.copy(
                        userName = userName.trim(),
                        profilePhotoUri = profilePhotoUri
                    )
                )
            }
            val allWallets = dao.getAllWallets()
            val defaultWId = allWallets.firstOrNull()?.id ?: 0L
            if (defaultWId != 0L) {
                starterLoans.forEach { sl ->
                    if (sl.personName.isNotBlank() && sl.principalAmount > 0.0) {
                        val targetWId = allWallets.firstOrNull {
                            it.name.equals(sl.walletName, ignoreCase = true)
                        }?.id ?: defaultWId
                        dao.insertLoan(
                            LoanEntity(
                                personName = sl.personName.trim(),
                                direction = sl.direction.name,
                                principalAmount = AccountingEngine.roundCurrency(sl.principalAmount),
                                initialWalletId = targetWId,
                                startDate = now,
                                dueDate = sl.dueDate,
                                note = "Existing loan added during setup"
                            )
                        )
                    }
                }
            }
            return
        }

        dao.clearAllTransactions()
        dao.clearAllTransfers()
        dao.clearAllLoanActivities()
        dao.clearAllLoans()
        dao.clearAllAdjustments()
        dao.clearAllRecurringTransactions()
        dao.clearAllSavingsGoals()
        dao.clearAllWallets()
        ensureDefaultCategoriesExist()

        val createdWallets = mutableListOf<Pair<String, Long>>()
        selectedWallets.forEachIndexed { index, (opt, openingBal) ->
            val id = dao.insertWallet(
                WalletEntity(
                    name = opt.name,
                    type = opt.type.name,
                    accountHint = opt.accountHint,
                    openingBalance = openingBal,
                    openingDate = startOfMonth,
                    currency = fixedCode,
                    iconName = opt.iconName,
                    colorHex = opt.colorHex,
                    sortOrder = canonicalWalletSortOrder(opt.name) ?: (100 + index + 1),
                    includeInTotalBalance = opt.includeInTotalBalance
                )
            )
            createdWallets.add(opt.name to id)
        }

        if (starterLoans.isNotEmpty() && createdWallets.isEmpty()) {
            val cashId = dao.insertWallet(
                WalletEntity(
                    name = "Cash",
                    type = WalletType.CASH.name,
                    openingBalance = 0.0,
                    openingDate = startOfMonth,
                    currency = fixedCode,
                    iconName = "CASH",
                    colorHex = "#2E9E5B",
                    sortOrder = 1,
                    includeInTotalBalance = true
                )
            )
            createdWallets.add("Cash" to cashId)
        }

        val fallbackWalletId = createdWallets.firstOrNull()?.second ?: 0L
        if (fallbackWalletId != 0L) {
            starterLoans.forEach { sl ->
                if (sl.personName.isNotBlank() && sl.principalAmount > 0.0) {
                    val targetWId = createdWallets.firstOrNull {
                        it.first.equals(sl.walletName, ignoreCase = true)
                    }?.second ?: fallbackWalletId
                    dao.insertLoan(
                        LoanEntity(
                            personName = sl.personName.trim(),
                            direction = sl.direction.name,
                            principalAmount = AccountingEngine.roundCurrency(sl.principalAmount),
                            initialWalletId = targetWId,
                            startDate = now,
                            dueDate = sl.dueDate,
                            note = "Existing loan"
                        )
                    )
                }
            }
        }

        val currentSettings = dao.getSettings() ?: AppSettingsEntity()
        dao.saveSettings(
            currentSettings.copy(
                id = 1,
                hasCompletedOnboarding = true,
                userName = userName.trim().ifBlank { currentSettings.userName },
                profilePhotoUri = profilePhotoUri.ifBlank { currentSettings.profilePhotoUri },
                currencyCode = fixedCode,
                currencySymbol = fixedSymbol,
                defaultWalletId = createdWallets.firstOrNull()?.second
            )
        )
    }

    suspend fun seedShowcaseData(
        currencyCode: String = "BDT",
        currencySymbol: String = "৳"
    ) {
        val fixedCode = "BDT"
        val fixedSymbol = "৳"
        dao.clearAllTransactions()
        dao.clearAllTransfers()
        dao.clearAllLoanActivities()
        dao.clearAllLoans()
        dao.clearAllAdjustments()
        dao.clearAllRecurringTransactions()
        dao.clearAllCategories()
        dao.clearAllWallets()

        dao.insertCategories(defaultCategories())
        val categories = dao.getAllCategories()
        fun catId(namePart: String, type: String): Long =
            categories.firstOrNull { it.type == type && it.name.contains(namePart, ignoreCase = true) }?.id
                ?: categories.first { it.type == type }.id

        val now = System.currentTimeMillis()
        fun daysAgo(days: Int, hour: Int = 12): Long {
            return Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -days)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 15)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val openingDate = daysAgo(25, 8)

        val cashId = dao.insertWallet(
            WalletEntity(
                name = "Cash",
                type = WalletType.CASH.name,
                accountHint = "",
                openingBalance = 2350.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "CASH",
                colorHex = "#2E9E5B",
                sortOrder = 1
            )
        )
        val bankId = dao.insertWallet(
            WalletEntity(
                name = "Bank",
                type = WalletType.BANK.name,
                accountHint = "",
                openingBalance = 2050.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "BANK",
                colorHex = "#2563EB",
                sortOrder = 2
            )
        )
        dao.insertWallet(
            WalletEntity(
                name = "MTB",
                type = WalletType.BANK.name,
                accountHint = "Mutual Trust Bank",
                openingBalance = 0.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "MTB",
                colorHex = "#006A4E",
                sortOrder = 3
            )
        )
        dao.insertWallet(
            WalletEntity(
                name = "Brac",
                type = WalletType.BANK.name,
                accountHint = "BRAC Bank",
                openingBalance = 0.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "BRAC",
                colorHex = "#0072BC",
                sortOrder = 4
            )
        )
        val bkashId = dao.insertWallet(
            WalletEntity(
                name = "Bkash",
                type = WalletType.MFS.name,
                accountHint = "",
                openingBalance = 2715.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "BKASH",
                colorHex = "#E2136E",
                sortOrder = 5
            )
        )
        val nagadId = dao.insertWallet(
            WalletEntity(
                name = "Nagad",
                type = WalletType.MFS.name,
                accountHint = "",
                openingBalance = 1485.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "NAGAD",
                colorHex = "#F37021",
                sortOrder = 6
            )
        )
        dao.insertWallet(
            WalletEntity(
                name = "Rocket",
                type = WalletType.MFS.name,
                accountHint = "",
                openingBalance = 0.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "ROCKET",
                colorHex = "#8C3494",
                sortOrder = 7
            )
        )
        val metroId = dao.insertWallet(
            WalletEntity(
                name = "Rapid Pass",
                type = WalletType.TRANSIT.name,
                accountHint = "",
                openingBalance = 650.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "RAPID_PASS",
                colorHex = "#1E3A8A",
                sortOrder = 8
            )
        )
        dao.insertWallet(
            WalletEntity(
                name = "Card",
                type = WalletType.CARD.name,
                accountHint = "",
                openingBalance = 0.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "CARD",
                colorHex = "#DC2626",
                sortOrder = 9
            )
        )
        dao.insertWallet(
            WalletEntity(
                name = "Savings",
                type = WalletType.SAVINGS.name,
                accountHint = "",
                openingBalance = 0.0,
                openingDate = openingDate,
                currency = fixedCode,
                iconName = "SAVINGS",
                colorHex = "#F15A24",
                sortOrder = 10,
                includeInTotalBalance = false
            )
        )

        // Income Transactions (৳32,000 total)
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.INCOME.name,
                amount = 30000.0,
                date = daysAgo(0, 10),
                walletId = bankId,
                categoryId = catId("Salary", "INCOME"),
                note = "Monthly Salary",
                reference = "OCT-SAL",
                tags = "work, salary"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.INCOME.name,
                amount = 2000.0,
                date = daysAgo(5, 16),
                walletId = bkashId,
                categoryId = catId("Freelance", "INCOME"),
                note = "Design consultation payout",
                reference = "INV-104",
                tags = "freelance"
            )
        )

        // Expense Transactions (৳12,385 + ৳15 transfer fee = ৳12,400 total)
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 850.0,
                date = daysAgo(0, 13),
                walletId = cashId,
                categoryId = catId("Groceries", "EXPENSE"),
                note = "Groceries",
                tags = "home, food"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 3150.0,
                date = daysAgo(2, 19),
                walletId = bankId,
                categoryId = catId("Food", "EXPENSE"),
                note = "Monthly pantry & dining",
                tags = "family, food"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 2000.0,
                date = daysAgo(3, 9),
                walletId = cashId,
                categoryId = catId("Transport", "EXPENSE"),
                note = "City commute & ride share",
                tags = "commute"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 500.0,
                date = daysAgo(4, 9),
                walletId = metroId,
                categoryId = catId("Transport", "EXPENSE"),
                note = "MRT Line-6 Metro trips",
                tags = "metro, transit"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 2000.0,
                date = daysAgo(6, 11),
                walletId = bkashId,
                categoryId = catId("Bills", "EXPENSE"),
                note = "Electricity & fiber internet",
                reference = "DESCO-OCT",
                tags = "bills, home"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 1500.0,
                date = daysAgo(7, 17),
                walletId = bkashId,
                categoryId = catId("Shopping", "EXPENSE"),
                note = "Essentials & stationery",
                tags = "shopping"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 1400.0,
                date = daysAgo(9, 15),
                walletId = bankId,
                categoryId = catId("Health", "EXPENSE"),
                note = "Pharmacy & checkup",
                tags = "health"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amount = 985.0,
                date = daysAgo(11, 20),
                walletId = nagadId,
                categoryId = catId("Entertainment", "EXPENSE"),
                note = "Books & weekend outing",
                tags = "leisure"
            )
        )

        val feeCatId = getOrCreateFeeCategoryId()
        dao.saveTransferAtomically(
            transfer = TransferEntity(
                sourceWalletId = bkashId,
                destinationWalletId = cashId,
                principalAmount = 1000.0,
                feeMode = TransferFeeMode.PERCENTAGE.name,
                fixedFeeInput = 0.0,
                percentageInput = 1.5,
                feeAmount = 15.0,
                date = daysAgo(4, 14),
                note = "bKash Cash Out"
            ),
            feeCategoryId = feeCatId,
            sourceWalletName = "bKash",
            destWalletName = "Cash"
        )
        dao.saveTransferAtomically(
            transfer = TransferEntity(
                sourceWalletId = bankId,
                destinationWalletId = metroId,
                principalAmount = 500.0,
                feeMode = TransferFeeMode.NONE.name,
                fixedFeeInput = 0.0,
                percentageInput = 0.0,
                feeAmount = 0.0,
                date = daysAgo(8, 10),
                note = "Rapid Pass top-up"
            ),
            feeCategoryId = feeCatId,
            sourceWalletName = "Bank",
            destWalletName = "Rapid Pass"
        )
        dao.saveTransferAtomically(
            transfer = TransferEntity(
                sourceWalletId = bankId,
                destinationWalletId = bkashId,
                principalAmount = 8000.0,
                feeMode = TransferFeeMode.NONE.name,
                feeAmount = 0.0,
                date = daysAgo(12, 11),
                note = "Bank to bKash Add Money"
            ),
            feeCategoryId = feeCatId,
            sourceWalletName = "Bank",
            destWalletName = "bKash"
        )
        dao.saveTransferAtomically(
            transfer = TransferEntity(
                sourceWalletId = bankId,
                destinationWalletId = cashId,
                principalAmount = 3000.0,
                feeMode = TransferFeeMode.NONE.name,
                feeAmount = 0.0,
                date = daysAgo(10, 14),
                note = "ATM Cash Withdrawal"
            ),
            feeCategoryId = feeCatId,
            sourceWalletName = "Bank",
            destWalletName = "Cash"
        )
        dao.saveTransferAtomically(
            transfer = TransferEntity(
                sourceWalletId = bankId,
                destinationWalletId = nagadId,
                principalAmount = 2000.0,
                feeMode = TransferFeeMode.NONE.name,
                feeAmount = 0.0,
                date = daysAgo(12, 15),
                note = "Bank to Nagad Add Money"
            ),
            feeCategoryId = feeCatId,
            sourceWalletName = "Bank",
            destWalletName = "Nagad"
        )

        val loan1Id = dao.createLoanAtomically(
            LoanEntity(
                personName = "Friend A (Rahim)",
                direction = LoanDirection.LENT.name,
                principalAmount = 10000.0,
                initialWalletId = bankId,
                startDate = daysAgo(14, 12),
                dueDate = daysAgo(-15, 12),
                note = "Emergency medical support"
            )
        )
        dao.insertLoanActivity(
            LoanActivityEntity(
                loanId = loan1Id,
                activityType = LoanActivityType.REPAYMENT.name,
                principalPortion = 4000.0,
                interestPortion = 0.0,
                walletId = bankId,
                date = daysAgo(3, 15),
                note = "First installment received"
            )
        )
        dao.createLoanAtomically(
            LoanEntity(
                personName = "Cousin Nabil",
                direction = LoanDirection.LENT.name,
                principalAmount = 2000.0,
                initialWalletId = bankId,
                startDate = daysAgo(10, 12),
                dueDate = daysAgo(-20, 12),
                note = "Semester book advance"
            )
        )
        val loan3Id = dao.createLoanAtomically(
            LoanEntity(
                personName = "Person B (Karim)",
                direction = LoanDirection.BORROWED.name,
                principalAmount = 10000.0,
                initialWalletId = bankId,
                startDate = daysAgo(18, 12),
                dueDate = daysAgo(-10, 12),
                note = "Laptop purchase bridge loan"
            )
        )
        dao.insertLoanActivity(
            LoanActivityEntity(
                loanId = loan3Id,
                activityType = LoanActivityType.REPAYMENT.name,
                principalPortion = 6000.0,
                interestPortion = 0.0,
                walletId = bankId,
                date = daysAgo(6, 16),
                note = "Partial principal repayment"
            )
        )

        dao.saveSettings(
            AppSettingsEntity(
                id = 1,
                hasCompletedOnboarding = true,
                currencyCode = fixedCode,
                currencySymbol = fixedSymbol,
                defaultWalletId = cashId,
                warnOnNegativeBalance = true,
                themeMode = "DARK"
            )
        )
    }

    // --- Wallet Operations ---
    suspend fun saveWallet(wallet: WalletEntity): Long {
        return if (wallet.id == 0L) {
            val count = dao.getAllWallets().size
            dao.insertWallet(wallet.copy(sortOrder = count + 1))
        } else {
            dao.updateWallet(wallet)
            wallet.id
        }
    }

    suspend fun reorderWallet(walletId: Long, moveUp: Boolean) {
        moveWalletOrder(walletId, if (moveUp) "UP" else "DOWN")
    }

    suspend fun moveWalletOrder(walletId: Long, action: String) {
        val wallets = dao.getAllWallets()
            .sortedWith(compareBy<WalletEntity> { it.sortOrder }.thenBy { it.id })
            .toMutableList()
        val idx = wallets.indexOfFirst { it.id == walletId }
        if (idx == -1) return
        val item = wallets.removeAt(idx)
        val targetIdx = when (action.uppercase()) {
            "TOP" -> 0
            "UP" -> (idx - 1).coerceAtLeast(0)
            "DOWN" -> (idx + 1).coerceAtMost(wallets.size)
            "BOTTOM" -> wallets.size
            else -> idx
        }
        wallets.add(targetIdx, item)
        wallets.forEachIndexed { index, w ->
            dao.updateWallet(w.copy(sortOrder = index + 1))
        }
    }

    suspend fun reorderWalletsByIds(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        val allWallets = dao.getAllWallets().associateBy { it.id }
        var nextOrder = 1
        orderedIds.forEach { id ->
            val w = allWallets[id]
            if (w != null) {
                if (w.sortOrder != nextOrder) {
                    dao.updateWallet(w.copy(sortOrder = nextOrder))
                }
                nextOrder++
            }
        }
        allWallets.values.filter { it.id !in orderedIds }.forEach { w ->
            if (w.sortOrder != nextOrder) {
                dao.updateWallet(w.copy(sortOrder = nextOrder))
            }
            nextOrder++
        }
    }

    suspend fun canDirectlyDeleteWallet(walletId: Long): Boolean {
        val txCount = dao.countTransactionsForWallet(walletId)
        val trCount = dao.countTransfersForWallet(walletId)
        val loanCount = dao.countLoansForWallet(walletId)
        val laCount = dao.countLoanActivitiesForWallet(walletId)
        val adjCount = dao.countAdjustmentsForWallet(walletId)
        return (txCount + trCount + loanCount + laCount + adjCount) == 0
    }

    suspend fun deleteOrMigrateWallet(walletId: Long, migrateToWalletId: Long?) {
        if (migrateToWalletId != null && migrateToWalletId != walletId) {
            dao.migrateAndDeleteWallet(walletId, migrateToWalletId)
        } else {
            dao.deleteWalletById(walletId)
        }
    }

    // --- Category Operations ---
    suspend fun saveCategory(category: CategoryEntity): Long {
        return if (category.id == 0L) {
            val count = dao.getAllCategories().count { it.type == category.type }
            dao.insertCategory(category.copy(sortOrder = count + 1))
        } else {
            dao.updateCategory(category)
            category.id
        }
    }

    suspend fun reorderCategory(categoryId: Long, moveUp: Boolean) {
        val all = dao.getAllCategories()
        val target = all.firstOrNull { it.id == categoryId } ?: return
        val sameType = all.filter { it.type == target.type }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
            .toMutableList()
        val idx = sameType.indexOfFirst { it.id == categoryId }
        if (idx == -1) return
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in sameType.indices) return
        val tmp = sameType[idx]
        sameType[idx] = sameType[swapIdx]
        sameType[swapIdx] = tmp
        sameType.forEachIndexed { index, cat ->
            dao.updateCategory(cat.copy(sortOrder = index + 1))
        }
    }

    suspend fun countTransactionsForCategory(categoryId: Long): Int {
        return dao.countTransactionsForCategory(categoryId) + dao.countRecurringForCategory(categoryId)
    }

    suspend fun deleteOrReassignCategory(categoryId: Long, reassignToCategoryId: Long?) {
        if (reassignToCategoryId != null && reassignToCategoryId != categoryId) {
            dao.reassignAndDeleteCategory(categoryId, reassignToCategoryId)
        } else {
            dao.deleteCategoryById(categoryId)
        }
    }

    // --- Transactions ---
    suspend fun saveTransaction(transaction: TransactionEntity): Long {
        return if (transaction.id == 0L) {
            dao.insertTransaction(transaction)
        } else {
            dao.updateTransaction(transaction)
            transaction.id
        }
    }

    suspend fun getChildTransactions(parentId: Long): List<TransactionEntity> {
        return dao.getChildTransactions(parentId)
    }

    suspend fun saveTransactionWithOptionalFee(
        transaction: TransactionEntity,
        serviceFeeAmount: Double = 0.0
    ): Long {
        val parentId = saveTransaction(transaction)
        val existingChildren = dao.getChildTransactions(parentId)
        val roundedFee = AccountingEngine.roundCurrency(serviceFeeAmount.coerceAtLeast(0.0))
        if (roundedFee > 0.005) {
            val feeCatId = getOrCreateFeeCategoryId()
            val feeNote = if (transaction.note.isNotBlank()) {
                "Service charge: ${transaction.note}"
            } else {
                "Service charge / payment fee"
            }
            val firstChild = existingChildren.firstOrNull()
            if (firstChild != null) {
                dao.updateTransaction(
                    firstChild.copy(
                        type = TransactionType.EXPENSE.name,
                        amount = roundedFee,
                        date = transaction.date,
                        walletId = transaction.walletId,
                        categoryId = feeCatId,
                        note = feeNote,
                        reference = transaction.reference,
                        parentTransactionId = parentId
                    )
                )
                existingChildren.drop(1).forEach { extra ->
                    dao.deleteTransactionById(extra.id)
                }
            } else {
                dao.insertTransaction(
                    TransactionEntity(
                        type = TransactionType.EXPENSE.name,
                        amount = roundedFee,
                        date = transaction.date,
                        walletId = transaction.walletId,
                        categoryId = feeCatId,
                        note = feeNote,
                        reference = transaction.reference,
                        tags = transaction.tags,
                        parentTransactionId = parentId
                    )
                )
            }
        } else if (existingChildren.isNotEmpty()) {
            dao.deleteChildTransactions(parentId)
        }
        return parentId
    }

    suspend fun deleteTransaction(transactionId: Long) {
        dao.deleteChildTransactions(transactionId)
        dao.deleteTransactionById(transactionId)
    }

    suspend fun bulkUpdateTransactionsCategory(transactionIds: List<Long>, newCategoryId: Long) {
        if (transactionIds.isEmpty()) return
        dao.updateTransactionsCategoryByIds(transactionIds, newCategoryId)
    }

    suspend fun bulkDeleteLedgerEntries(entries: List<UnifiedLedgerEntry>) {
        val allTxs = dao.getAllTransactions()
        val allAdjs = dao.getAllBalanceAdjustments()
        entries.forEach { entry ->
            when (entry.kind) {
                LedgerEntryKind.INCOME,
                LedgerEntryKind.EXPENSE,
                LedgerEntryKind.EXPENSE_REVERSAL -> {
                    val tx = allTxs.firstOrNull { it.id == entry.rawId }
                    if (tx?.transferId != null) {
                        dao.deleteTransferAtomically(tx.transferId)
                    } else if (tx?.loanActivityId != null) {
                        dao.deleteLoanActivityAtomically(tx.loanActivityId)
                    } else {
                        dao.deleteChildTransactions(entry.rawId)
                        dao.deleteTransactionById(entry.rawId)
                    }
                }
                LedgerEntryKind.TRANSFER -> dao.deleteTransferAtomically(entry.rawId)
                LedgerEntryKind.LOAN_PRINCIPAL -> dao.deleteLoanActivityAtomically(entry.rawId)
                LedgerEntryKind.ADJUSTMENT -> {
                    val adj = allAdjs.firstOrNull { it.id == entry.rawId }
                    if (adj != null && !adj.isReversed) {
                        dao.updateBalanceAdjustment(adj.copy(isReversed = true))
                    }
                }
            }
        }
    }

    // --- Savings Goals (Virtual Pots / Wishlists) ---
    suspend fun saveSavingsGoal(goal: SavingsGoalEntity): Long {
        return if (goal.id == 0L) {
            dao.insertSavingsGoal(goal)
        } else {
            dao.updateSavingsGoal(goal)
            goal.id
        }
    }

    suspend fun deleteSavingsGoal(id: Long) {
        dao.deleteSavingsGoalById(id)
    }

    // --- Transfers ---
    suspend fun saveTransfer(
        transfer: TransferEntity,
        sourceWalletName: String,
        destWalletName: String
    ): Long {
        val feeCatId = getOrCreateFeeCategoryId()
        return dao.saveTransferAtomically(transfer, feeCatId, sourceWalletName, destWalletName)
    }

    suspend fun deleteTransfer(transferId: Long) {
        dao.deleteTransferAtomically(transferId)
    }

    // --- Loans ---
    suspend fun createLoan(loan: LoanEntity): Long {
        return dao.createLoanAtomically(loan)
    }

    suspend fun updateLoanMetadata(loan: LoanEntity) {
        dao.updateLoan(loan)
    }

    suspend fun recordLoanRepayment(loan: LoanEntity, activity: LoanActivityEntity): Long {
        val isLent = loan.direction == LoanDirection.LENT.name
        val interestCatId = getOrCreateLoanInterestCategoryId(isIncome = isLent)
        return dao.recordLoanRepaymentAtomically(loan, activity, interestCatId)
    }

    suspend fun deleteLoanActivity(activityId: Long) {
        dao.deleteLoanActivityAtomically(activityId)
    }

    suspend fun deleteLoan(loanId: Long) {
        dao.deleteLoanAtomically(loanId)
    }

    // --- Balance Adjustments ---
    suspend fun createBalanceAdjustment(adjustment: BalanceAdjustmentEntity): Long {
        return dao.insertBalanceAdjustment(adjustment)
    }

    suspend fun reverseBalanceAdjustment(adjustment: BalanceAdjustmentEntity) {
        dao.updateBalanceAdjustment(adjustment.copy(isReversed = !adjustment.isReversed))
    }

    // --- Recurring Transactions (Option A: Ask to Confirm) ---
    suspend fun saveRecurringTransaction(item: RecurringTransactionEntity): Long {
        return if (item.id == 0L) {
            dao.insertRecurringTransaction(item)
        } else {
            dao.updateRecurringTransaction(item)
            item.id
        }
    }

    suspend fun deleteRecurringTransaction(id: Long) {
        dao.deleteRecurringTransactionById(id)
    }

    suspend fun snoozeRecurringToTomorrow(
        recurringId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): RecurringTransactionEntity? {
        val existing = dao.getRecurringTransactionById(recurringId) ?: return null
        val tomorrowMillis = calculateTomorrowReminderMillis(nowMillis)
        val updated = existing.copy(nextDueDate = tomorrowMillis)
        dao.updateRecurringTransaction(updated)
        return updated
    }

    /**
     * Logs the actual transaction ONLY after the user picks `chosenWalletId` and confirms `finalAmount`,
     * and advances the recurring rule's `nextDueDate` to the next cycle.
     */
    suspend fun confirmAndPayRecurringTransaction(
        recurringId: Long,
        chosenWalletId: Long,
        finalAmount: Double,
        note: String = "",
        serviceFeeAmount: Double = 0.0,
        paidAtMillis: Long = System.currentTimeMillis()
    ): Long {
        val recurring = dao.getRecurringTransactionById(recurringId) ?: return 0L
        val cleanAmount = AccountingEngine.roundCurrency(finalAmount)
        if (cleanAmount <= 0.0) return 0L

        val resolvedNote = note.trim().ifBlank {
            if (recurring.note.isNotBlank()) "${recurring.title} — ${recurring.note}" else recurring.title
        }

        val txId = dao.insertTransaction(
            TransactionEntity(
                type = recurring.type,
                amount = cleanAmount,
                date = paidAtMillis,
                walletId = chosenWalletId,
                categoryId = recurring.categoryId,
                note = resolvedNote,
                reference = "REC-${recurring.id}",
                tags = "recurring"
            )
        )

        val cleanFee = AccountingEngine.roundCurrency(serviceFeeAmount)
        if (cleanFee > 0.0) {
            val feeCatId = getOrCreateFeeCategoryId()
            dao.insertTransaction(
                TransactionEntity(
                    type = TransactionType.EXPENSE.name,
                    amount = cleanFee,
                    date = paidAtMillis,
                    walletId = chosenWalletId,
                    categoryId = feeCatId,
                    note = "Service charge: ${recurring.title}",
                    reference = "FEE-REC-${recurring.id}",
                    tags = "fee, recurring"
                )
            )
        }

        val anchorMillis = maxOf(recurring.nextDueDate, paidAtMillis)
        val nextDue = calculateNextRecurringDueDate(
            fromMillis = anchorMillis,
            frequency = recurring.frequency,
            dayOfMonth = recurring.dayOfMonth,
            customIntervalDays = recurring.customIntervalDays
        )

        dao.updateRecurringTransaction(
            recurring.copy(
                amount = cleanAmount,
                defaultWalletId = chosenWalletId,
                nextDueDate = nextDue,
                lastPaidDate = paidAtMillis
            )
        )

        return txId
    }

    // --- Settings ---
    suspend fun updateSettings(settings: AppSettingsEntity) {
        dao.saveSettings(settings.copy(id = 1))
    }

    suspend fun updateProfilePhotoUri(profilePhotoUri: String) {
        val current = dao.getSettings() ?: AppSettingsEntity(id = 1, hasCompletedOnboarding = true)
        dao.saveSettings(current.copy(id = 1, profilePhotoUri = profilePhotoUri))
    }

    suspend fun updateCustomBackgroundUri(customBackgroundUri: String) {
        val current = dao.getSettings() ?: AppSettingsEntity(id = 1, hasCompletedOnboarding = true)
        dao.saveSettings(current.copy(id = 1, customBackgroundUri = customBackgroundUri))
    }

    suspend fun updateBackgroundOpacity(opacity: Float) {
        val current = dao.getSettings() ?: AppSettingsEntity(id = 1, hasCompletedOnboarding = true)
        dao.saveSettings(current.copy(id = 1, backgroundOpacity = opacity.coerceIn(0.0f, 1.0f)))
    }

    /**
     * Resets all numerical financial data to zero:
     * - Clears all transactions (income, expense, reversals, fee/interest entries)
     * - Clears all transfers
     * - Clears all loan activities and loans
     * - Clears all balance adjustments
     * - Clears all recurring transactions
     * - Sets every wallet's openingBalance to 0.0 while keeping wallet names, icons, and categories intact.
     */
    suspend fun resetAllFinancialDataToZero() {
        dao.clearAllTransactions()
        dao.clearAllTransfers()
        dao.clearAllLoanActivities()
        dao.clearAllLoans()
        dao.clearAllAdjustments()
        dao.clearAllRecurringTransactions()
        dao.clearAllSavingsGoals()
        val existingWallets = dao.getAllWallets()
        existingWallets.forEach { wallet ->
            dao.updateWallet(wallet.copy(openingBalance = 0.0))
        }
    }

    /**
     * Completely deletes all local wallets, transactions, transfers, loans, loan activities,
     * balance adjustments, recurring rules, and savings goals from the device, restores
     * default categories, and resets AppSettingsEntity (including hasCompletedOnboarding).
     */
    suspend fun wipeAllLocalData(
        resetOnboardingToFalse: Boolean = true,
        userName: String = "",
        profilePhotoUri: String = "",
        themeMode: String = "DARK",
        accentColorHex: String = "#F15A24"
    ) {
        val cleanSettings = AppSettingsEntity(
            id = 1,
            currencyCode = "BDT",
            currencySymbol = "৳",
            themeMode = themeMode.ifBlank { "DARK" },
            accentColorHex = accentColorHex.ifBlank { "#F15A24" },
            hasCompletedOnboarding = !resetOnboardingToFalse,
            userName = userName.trim(),
            profilePhotoUri = profilePhotoUri.trim()
        )
        dao.clearAllDeviceDataAndSaveSettings(
            categories = defaultCategories(),
            settings = cleanSettings
        )
    }

    // --- Backup & Restore ---
    suspend fun buildBackupPayload(): PocketBackupPayload {
        return PocketBackupPayload(
            formatVersion = "pocket-backup-v1",
            exportedAtMillis = System.currentTimeMillis(),
            wallets = dao.getAllWallets(),
            categories = dao.getAllCategories(),
            transactions = dao.getAllTransactions(),
            transfers = dao.getAllTransfers(),
            loans = dao.getAllLoans(),
            loanActivities = dao.getAllLoanActivities(),
            adjustments = dao.getAllBalanceAdjustments(),
            recurringTransactions = dao.getAllRecurringTransactions(),
            savingsGoals = dao.getAllSavingsGoals(),
            settings = dao.getSettings() ?: AppSettingsEntity(hasCompletedOnboarding = true)
        )
    }

    suspend fun restoreBackupPayload(payload: PocketBackupPayload) {
        dao.replaceAllDataAtomically(
            wallets = payload.wallets,
            categories = payload.categories,
            transactions = payload.transactions,
            transfers = payload.transfers,
            loans = payload.loans,
            loanActivities = payload.loanActivities,
            adjustments = payload.adjustments,
            recurringTransactions = payload.recurringTransactions,
            savingsGoals = payload.savingsGoals,
            settings = payload.settings
        )
    }

    /**
     * Merges a user's offline local phone data (`local`) with an existing Google Cloud snapshot (`remote`),
     * preserving custom wallets, transactions, transfers, loans, adjustments, and recurring rules from both.
     */
    fun mergeBackupPayloads(
        local: PocketBackupPayload,
        remote: PocketBackupPayload
    ): PocketBackupPayload {
        // 1. Merge Categories (match by case-insensitive name + type)
        val mergedCategories = remote.categories.ifEmpty { defaultCategories() }.toMutableList()
        var nextCatId = (mergedCategories.maxOfOrNull { it.id } ?: 0L) + 1L
        val categoryIdMap = mutableMapOf<Long, Long>()
        for (localCat in local.categories) {
            val match = mergedCategories.firstOrNull {
                it.type.equals(localCat.type, ignoreCase = true) &&
                    it.name.trim().equals(localCat.name.trim(), ignoreCase = true)
            }
            if (match != null) {
                categoryIdMap[localCat.id] = match.id
            } else {
                val newId = nextCatId++
                mergedCategories.add(localCat.copy(id = newId))
                categoryIdMap[localCat.id] = newId
            }
        }

        // 2. Merge Wallets (match by case-insensitive name + currency)
        val mergedWallets = remote.wallets.toMutableList()
        var nextWalletId = (mergedWallets.maxOfOrNull { it.id } ?: 0L) + 1L
        val walletIdMap = mutableMapOf<Long, Long>()
        for (localW in local.wallets) {
            val localCurrency = localW.currency.trim().ifBlank { "BDT" }.uppercase()
            val matchIndex = mergedWallets.indexOfFirst {
                it.name.trim().equals(localW.name.trim(), ignoreCase = true) &&
                    it.currency.trim().ifBlank { "BDT" }.uppercase() == localCurrency
            }
            if (matchIndex >= 0) {
                val existing = mergedWallets[matchIndex]
                walletIdMap[localW.id] = existing.id
                mergedWallets[matchIndex] = existing.copy(
                    openingBalance = if (existing.openingBalance == 0.0 && localW.openingBalance != 0.0) localW.openingBalance else existing.openingBalance,
                    creditLimit = if (existing.creditLimit <= 0.0 && localW.creditLimit > 0.0) localW.creditLimit else existing.creditLimit,
                    statementDay = if (existing.statementDay <= 0 && localW.statementDay > 0) localW.statementDay else existing.statementDay,
                    paymentDueDay = if (existing.paymentDueDay <= 0 && localW.paymentDueDay > 0) localW.paymentDueDay else existing.paymentDueDay
                )
            } else {
                val newId = nextWalletId++
                mergedWallets.add(localW.copy(id = newId, sortOrder = mergedWallets.size + 1))
                walletIdMap[localW.id] = newId
            }
        }

        val fallbackWalletId = mergedWallets.firstOrNull()?.id ?: 1L
        val fallbackCategoryId = mergedCategories.firstOrNull()?.id ?: 1L

        // 3. Merge Loans
        val mergedLoans = remote.loans.toMutableList()
        var nextLoanId = (mergedLoans.maxOfOrNull { it.id } ?: 0L) + 1L
        val loanIdMap = mutableMapOf<Long, Long>()
        for (localLoan in local.loans) {
            val mappedWalletId = walletIdMap[localLoan.initialWalletId] ?: fallbackWalletId
            val match = mergedLoans.firstOrNull {
                it.personName.trim().equals(localLoan.personName.trim(), ignoreCase = true) &&
                    it.direction == localLoan.direction &&
                    it.principalAmount == localLoan.principalAmount &&
                    it.startDate == localLoan.startDate
            }
            if (match != null) {
                loanIdMap[localLoan.id] = match.id
            } else {
                val newId = nextLoanId++
                mergedLoans.add(localLoan.copy(id = newId, initialWalletId = mappedWalletId))
                loanIdMap[localLoan.id] = newId
            }
        }

        // 4. Merge Loan Activities
        val mergedLoanActivities = remote.loanActivities.toMutableList()
        var nextLaId = (mergedLoanActivities.maxOfOrNull { it.id } ?: 0L) + 1L
        val loanActivityIdMap = mutableMapOf<Long, Long>()
        for (localLa in local.loanActivities) {
            val mappedLoanId = loanIdMap[localLa.loanId] ?: continue
            val mappedWalletId = walletIdMap[localLa.walletId] ?: fallbackWalletId
            val match = mergedLoanActivities.firstOrNull {
                it.loanId == mappedLoanId &&
                    it.activityType == localLa.activityType &&
                    it.principalPortion == localLa.principalPortion &&
                    it.date == localLa.date
            }
            if (match != null) {
                loanActivityIdMap[localLa.id] = match.id
            } else {
                val newId = nextLaId++
                mergedLoanActivities.add(
                    localLa.copy(id = newId, loanId = mappedLoanId, walletId = mappedWalletId)
                )
                loanActivityIdMap[localLa.id] = newId
            }
        }

        // 5. Merge Transfers
        val mergedTransfers = remote.transfers.toMutableList()
        var nextTrId = (mergedTransfers.maxOfOrNull { it.id } ?: 0L) + 1L
        val transferIdMap = mutableMapOf<Long, Long>()
        for (localTr in local.transfers) {
            val mappedSrc = walletIdMap[localTr.sourceWalletId] ?: fallbackWalletId
            val mappedDst = walletIdMap[localTr.destinationWalletId] ?: fallbackWalletId
            if (mappedSrc == mappedDst) continue
            val match = mergedTransfers.firstOrNull {
                it.sourceWalletId == mappedSrc &&
                    it.destinationWalletId == mappedDst &&
                    it.principalAmount == localTr.principalAmount &&
                    it.date == localTr.date
            }
            if (match != null) {
                transferIdMap[localTr.id] = match.id
            } else {
                val newId = nextTrId++
                mergedTransfers.add(
                    localTr.copy(
                        id = newId,
                        sourceWalletId = mappedSrc,
                        destinationWalletId = mappedDst
                    )
                )
                transferIdMap[localTr.id] = newId
            }
        }

        // 6. Merge Transactions (parents before linked child fee transactions so parentTransactionId remaps cleanly)
        val mergedTransactions = remote.transactions.toMutableList()
        var nextTxId = (mergedTransactions.maxOfOrNull { it.id } ?: 0L) + 1L
        val transactionIdMap = mutableMapOf<Long, Long>()
        for (localTx in local.transactions.sortedBy { if (it.parentTransactionId == null) 0 else 1 }) {
            val mappedWalletId = walletIdMap[localTx.walletId] ?: fallbackWalletId
            val mappedCategoryId = categoryIdMap[localTx.categoryId] ?: fallbackCategoryId
            val mappedTransferId = localTx.transferId?.let { transferIdMap[it] }
            val mappedLoanActivityId = localTx.loanActivityId?.let { loanActivityIdMap[it] }
            val mappedParentTxId = localTx.parentTransactionId?.let { transactionIdMap[it] ?: it }
            val existingMatch = mergedTransactions.firstOrNull {
                it.type == localTx.type &&
                    it.amount == localTx.amount &&
                    it.date == localTx.date &&
                    it.walletId == mappedWalletId &&
                    it.categoryId == mappedCategoryId &&
                    it.note == localTx.note
            }
            if (existingMatch != null) {
                transactionIdMap[localTx.id] = existingMatch.id
            } else {
                val newId = nextTxId++
                transactionIdMap[localTx.id] = newId
                mergedTransactions.add(
                    localTx.copy(
                        id = newId,
                        walletId = mappedWalletId,
                        categoryId = mappedCategoryId,
                        transferId = mappedTransferId,
                        loanActivityId = mappedLoanActivityId,
                        parentTransactionId = mappedParentTxId
                    )
                )
            }
        }

        // 7. Merge Balance Adjustments
        val mergedAdjustments = remote.adjustments.toMutableList()
        var nextAdjId = (mergedAdjustments.maxOfOrNull { it.id } ?: 0L) + 1L
        for (localAdj in local.adjustments) {
            val mappedWalletId = walletIdMap[localAdj.walletId] ?: fallbackWalletId
            val isDuplicate = mergedAdjustments.any {
                it.walletId == mappedWalletId &&
                    it.actualBalance == localAdj.actualBalance &&
                    it.date == localAdj.date
            }
            if (!isDuplicate) {
                mergedAdjustments.add(localAdj.copy(id = nextAdjId++, walletId = mappedWalletId))
            }
        }

        // 8. Merge Recurring Transactions
        val mergedRecurring = remote.recurringTransactions.toMutableList()
        var nextRecId = (mergedRecurring.maxOfOrNull { it.id } ?: 0L) + 1L
        for (localRec in local.recurringTransactions) {
            val mappedCatId = categoryIdMap[localRec.categoryId] ?: fallbackCategoryId
            val mappedDefWalletId = localRec.defaultWalletId?.let { walletIdMap[it] }
            val isDuplicate = mergedRecurring.any {
                it.title.trim().equals(localRec.title.trim(), ignoreCase = true) &&
                    it.type == localRec.type &&
                    it.frequency == localRec.frequency
            }
            if (!isDuplicate) {
                mergedRecurring.add(
                    localRec.copy(
                        id = nextRecId++,
                        categoryId = mappedCatId,
                        defaultWalletId = mappedDefWalletId
                    )
                )
            }
        }

        // 9. Merge Savings Goals
        val mergedSavingsGoals = remote.savingsGoals.toMutableList()
        var nextGoalId = (mergedSavingsGoals.maxOfOrNull { it.id } ?: 0L) + 1L
        for (localGoal in local.savingsGoals) {
            val isDuplicate = mergedSavingsGoals.any {
                it.name.trim().equals(localGoal.name.trim(), ignoreCase = true)
            }
            if (!isDuplicate) {
                mergedSavingsGoals.add(localGoal.copy(id = nextGoalId++))
            }
        }

        val mergedSettings = remote.settings.copy(
            id = 1,
            hasCompletedOnboarding = true,
            userName = local.settings.userName.ifBlank { remote.settings.userName },
            profilePhotoUri = local.settings.profilePhotoUri.ifBlank { remote.settings.profilePhotoUri },
            customBackgroundUri = local.settings.customBackgroundUri.ifBlank { remote.settings.customBackgroundUri },
            backgroundOpacity = local.settings.backgroundOpacity,
            accentColorHex = if (!local.settings.accentColorHex.equals("#FF5A1F", ignoreCase = true) && local.settings.accentColorHex.isNotBlank()) {
                local.settings.accentColorHex
            } else {
                remote.settings.accentColorHex
            },
            masterMonthlyBudget = if (local.settings.masterMonthlyBudget > 0.0) local.settings.masterMonthlyBudget else remote.settings.masterMonthlyBudget,
            hideBalancesPrivacy = local.settings.hideBalancesPrivacy
        )

        return PocketBackupPayload(
            formatVersion = "pocket-backup-v1",
            exportedAtMillis = System.currentTimeMillis(),
            wallets = mergedWallets,
            categories = mergedCategories,
            transactions = mergedTransactions,
            transfers = mergedTransfers,
            loans = mergedLoans,
            loanActivities = mergedLoanActivities,
            adjustments = mergedAdjustments,
            recurringTransactions = mergedRecurring,
            savingsGoals = mergedSavingsGoals,
            settings = mergedSettings
        )
    }
}
