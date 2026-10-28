package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.widget.PocketWidgetUpdater
import com.example.widget.WidgetPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and Created By Hadi tagline from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val tagline = context.getString(R.string.app_tagline)
        assertEquals("Pocket", appName)
        assertEquals("Created By Hadi", tagline)
    }

    @Test
    fun `widget preferences persist overview and single wallet selections`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WidgetPreferences.saveOverviewWalletIds(context, appWidgetId = 101, walletIds = setOf(1L, 3L, 5L))
        assertEquals(setOf(1L, 3L, 5L), WidgetPreferences.getOverviewWalletIds(context, 101))

        WidgetPreferences.saveSingleWalletId(context, appWidgetId = 202, walletId = 4L)
        assertEquals(4L, WidgetPreferences.getSingleWalletId(context, 202))

        WidgetPreferences.deleteWidgetConfig(context, 101)
        WidgetPreferences.deleteWidgetConfig(context, 202)
    }

    @Test
    fun `starter wallets include Brac and Savings in exact 1 to 10 order and default categories match spec`() {
        val starters = PocketRepository.defaultStarterWallets()
        assertEquals(
            listOf(
                "Cash",
                "Bank",
                "MTB",
                "Brac",
                "Bkash",
                "Nagad",
                "Rocket",
                "Rapid Pass",
                "Card",
                "Savings"
            ),
            starters.map { it.name }
        )
        assertTrue(starters.all { !it.selectedByDefault })
        assertTrue(starters.all { it.defaultBalance == 1000.0 })
        assertEquals("Savings", starters.last().name)
        assertEquals(false, starters.last().includeInTotalBalance)
        assertTrue(starters.dropLast(1).all { it.includeInTotalBalance })

        // Verify all 10 wallets resolve to dedicated circular logo drawables
        starters.forEach { opt ->
            val resId = com.example.ui.components.resolveWalletDrawableRes(opt.iconName, opt.name)
            assertNotNull("Expected drawable for wallet ${opt.name}", resId)
        }

        val categories = PocketRepository.defaultCategories()
        val incomes = categories.filter { it.type == "INCOME" }
        val expenses = categories.filter { it.type == "EXPENSE" }

        assertEquals(
            listOf("Salary", "Freelance", "Gift", "Client payment", "Others"),
            incomes.map { it.name }
        )
        assertTrue(incomes.all { it.colorHex == PocketRepository.DEFAULT_INCOME_COLOR_HEX })
        // Verify Client payment and all income categories resolve to normal Material vector icons (not wallet drawables)
        incomes.forEach { cat ->
            val walletRes = com.example.ui.components.resolveWalletDrawableRes(cat.iconName, "")
            org.junit.Assert.assertNull("Income category ${cat.name} must not resolve to a wallet logo", walletRes)
        }
        org.junit.Assert.assertNull(
            "Interest category must not resolve to a wallet logo",
            com.example.ui.components.resolveWalletDrawableRes("TRENDING_UP", "")
        )

        assertEquals(
            listOf(
                "Bills",
                "Shopping",
                "Transport",
                "Food & Dining",
                "Bazar",
                "Medicine",
                "Entertainment",
                "Donation",
                "Service Charge",
                "Health",
                "Education",
                "Mobile Recharge",
                "Others",
                "Fees & Charges"
            ),
            expenses.map { it.name }
        )
        assertTrue(expenses.all { it.colorHex == PocketRepository.DEFAULT_EXPENSE_COLOR_HEX })
        assertTrue(expenses.first { it.name == "Fees & Charges" }.isSystemFee)
        assertEquals(4, com.example.ui.BottomNavTab.entries.size)
        assertEquals("Loans", com.example.ui.BottomNavTab.LOANS.label)

        // Verify first-letter auto-capitalization helper
        assertEquals("Hadi", com.example.ui.components.capitalizeFirstLetter("hadi"))
        assertEquals("Brac bank", com.example.ui.components.capitalizeFirstLetter("brac bank"))
        assertEquals("", com.example.ui.components.capitalizeFirstLetter(""))
    }

    @Test
    fun `widget snapshot and RemoteViews match Pocket accounting engine balances`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PocketDatabase.getDatabase(context)
        val repo = PocketRepository(db.pocketDao())
        repo.completeOnboarding(
            currencyCode = "BDT",
            currencySymbol = "৳",
            userName = "Hadi",
            profilePhotoUri = "content://example/profile.png",
            selectedWallets = PocketRepository.defaultStarterWallets()
                .take(4)
                .map { it to it.defaultBalance },
            loadSampleTransactions = false,
            starterLoans = listOf(
                StarterLoanInput("Tanvir", LoanDirection.LENT, 1200.0, dueDate = 1790000000000L)
            )
        )

        val snapshot = PocketWidgetUpdater.loadSnapshot(context)
        assertTrue("Active wallets should be populated", snapshot.activeWalletSummaries.isNotEmpty())

        // Verify Total Balance Widget RemoteViews (compact and large)
        val compactOptions = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
        }
        val compactRv = PocketWidgetUpdater.buildTotalBalanceRemoteViews(
            context = context,
            appWidgetId = 11,
            options = compactOptions,
            snapshot = snapshot
        )
        val compactView = compactRv.apply(context, FrameLayout(context))
        val compactBalanceText = compactView.findViewById<TextView>(R.id.widget_total_amount)
        assertNotNull(compactBalanceText)
        assertEquals(
            AccountingEngine.formatMoney(snapshot.totalActiveBalance, snapshot.currencySymbol),
            compactBalanceText.text.toString()
        )

        val largeOptions = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        }
        val largeRv = PocketWidgetUpdater.buildTotalBalanceRemoteViews(
            context = context,
            appWidgetId = 12,
            options = largeOptions,
            snapshot = snapshot
        )
        val largeView = largeRv.apply(context, FrameLayout(context))
        val largeBalanceText = largeView.findViewById<TextView>(R.id.widget_total_amount)
        assertEquals(
            AccountingEngine.formatMoney(snapshot.totalActiveBalance, snapshot.currencySymbol),
            largeBalanceText.text.toString()
        )

        // Verify Wallet Overview Widget RemoteViews with selected wallets
        val selectedWallets = snapshot.activeWalletSummaries.take(3).map { it.wallet.id }.toSet()
        WidgetPreferences.saveOverviewWalletIds(context, 21, selectedWallets)
        val overviewRv = PocketWidgetUpdater.buildWalletOverviewRemoteViews(
            context = context,
            appWidgetId = 21,
            snapshot = snapshot
        )
        val overviewView = overviewRv.apply(context, FrameLayout(context))
        val row1Name = overviewView.findViewById<TextView>(R.id.widget_wallet_name_1)
        val row1Balance = overviewView.findViewById<TextView>(R.id.widget_wallet_bal_1)
        assertEquals(snapshot.activeWalletSummaries[0].wallet.name, row1Name.text.toString())
        assertEquals(
            AccountingEngine.formatMoney(snapshot.activeWalletSummaries[0].currentBalance, snapshot.currencySymbol, includeDecimals = false),
            row1Balance.text.toString()
        )

        // Verify Single Wallet Widget RemoteViews (valid wallet)
        val targetWallet = snapshot.activeWalletSummaries.first()
        WidgetPreferences.saveSingleWalletId(context, 31, targetWallet.wallet.id)
        val singleRv = PocketWidgetUpdater.buildSingleWalletRemoteViews(
            context = context,
            appWidgetId = 31,
            snapshot = snapshot
        )
        val singleView = singleRv.apply(context, FrameLayout(context))
        val singleName = singleView.findViewById<TextView>(R.id.widget_single_wallet_name)
        val singleBalance = singleView.findViewById<TextView>(R.id.widget_single_wallet_balance)
        assertEquals(targetWallet.wallet.name, singleName.text.toString())
        assertEquals(
            AccountingEngine.formatMoney(targetWallet.currentBalance, snapshot.currencySymbol),
            singleBalance.text.toString()
        )
    }

    @Test
    fun `reset all financial data sets income expenses loans transfers and wallet balances to zero`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PocketDatabase.getDatabase(context)
        val dao = db.pocketDao()
        val repo = PocketRepository(dao)

        repo.completeOnboarding(
            currencyCode = "BDT",
            currencySymbol = "৳",
            userName = "Hadi",
            profilePhotoUri = "",
            selectedWallets = PocketRepository.defaultStarterWallets().take(4).map { it to 5000.0 },
            loadSampleTransactions = true,
            starterLoans = listOf(
                StarterLoanInput("Tanvir", LoanDirection.LENT, 1500.0, dueDate = System.currentTimeMillis() + 86400000L)
            )
        )

        // Verify non-zero state before reset
        assertTrue(dao.getAllTransactions().isNotEmpty())
        assertTrue(dao.getAllLoans().isNotEmpty())
        val walletsBefore = dao.getAllWallets()
        assertTrue(walletsBefore.any { it.openingBalance > 0.0 })

        // Perform full numerical reset to zero
        repo.resetAllFinancialDataToZero()

        // Verify all numerical records are cleared and wallet balances are 0.0 while wallets/categories remain
        assertTrue(dao.getAllTransactions().isEmpty())
        assertTrue(dao.getAllTransfers().isEmpty())
        assertTrue(dao.getAllLoans().isEmpty())
        assertTrue(dao.getAllLoanActivities().isEmpty())
        assertTrue(dao.getAllBalanceAdjustments().isEmpty())

        val walletsAfter = dao.getAllWallets()
        assertEquals(walletsBefore.size, walletsAfter.size)
        assertTrue(walletsAfter.all { it.openingBalance == 0.0 })

        val summariesAfter = walletsAfter.map { w ->
            AccountingEngine.calculateWalletSummary(
                wallet = w,
                transactions = dao.getAllTransactions(),
                transfers = dao.getAllTransfers(),
                loans = dao.getAllLoans(),
                loanActivities = dao.getAllLoanActivities(),
                adjustments = dao.getAllBalanceAdjustments()
            )
        }
        assertTrue(summariesAfter.all { it.currentBalance == 0.0 })
        assertEquals(0.0, summariesAfter.filter { it.wallet.includeInTotalBalance }.sumOf { it.currentBalance }, 0.001)
    }

    @Test
    fun `notification settings and daily log reminder time formatting persist in app settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PocketDatabase.getDatabase(context)
        val dao = db.pocketDao()
        val repo = PocketRepository(dao)

        repo.updateSettings(com.example.data.AppSettingsEntity())

        val current = dao.getSettings()!!
        assertEquals(true, current.loanDeadlineNotificationEnabled)
        assertEquals(true, current.dailyExpenseReminderEnabled)
        assertEquals(20, current.dailyReminderHour)
        assertEquals(0, current.dailyReminderMinute)
        assertEquals("08:00 PM", com.example.notification.PocketNotificationManager.formatReminderTime(20, 0))
        assertEquals("09:30 AM", com.example.notification.PocketNotificationManager.formatReminderTime(9, 30))

        repo.updateSettings(
            current.copy(
                loanDeadlineNotificationEnabled = false,
                dailyExpenseReminderEnabled = true,
                dailyReminderHour = 21,
                dailyReminderMinute = 15
            )
        )
        val updated = dao.getSettings()!!
        assertEquals(false, updated.loanDeadlineNotificationEnabled)
        assertEquals(21, updated.dailyReminderHour)
        assertEquals(15, updated.dailyReminderMinute)
        assertEquals("09:15 PM", com.example.notification.PocketNotificationManager.formatReminderTime(updated.dailyReminderHour, updated.dailyReminderMinute))
    }

    @Test
    fun `loan deadline notification stays ongoing 1 to 3 days before deadline and clears when settled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PocketDatabase.getDatabase(context)
        val dao = db.pocketDao()
        val repo = PocketRepository(dao)

        val now = System.currentTimeMillis()
        val twoDaysFromNow = now + 2L * 24L * 60L * 60L * 1000L
        val fiveDaysFromNow = now + 5L * 24L * 60L * 60L * 1000L

        val wallets = dao.getAllWallets()
        val cashWalletId = wallets.firstOrNull()?.id ?: 1L

        val loanId = repo.createLoan(
            com.example.data.LoanEntity(
                personName = "Rahim",
                direction = LoanDirection.LENT.name,
                principalAmount = 3000.0,
                initialWalletId = cashWalletId,
                startDate = now,
                dueDate = twoDaysFromNow,
                note = "Urgent loan"
            )
        )

        val allLoans = dao.getAllLoans()
        val allActivities = dao.getAllLoanActivities()
        val summaries = AccountingEngine.calculateLoanSummaries(allLoans, allActivities, wallets)
        val rahimSummary = summaries.first { it.loan.id == loanId }

        assertTrue(
            "Loan due in 2 days must trigger persistent reminder",
            com.example.notification.PocketNotificationManager.shouldShowPersistentLoanReminder(rahimSummary, now)
        )
        org.junit.Assert.assertFalse(
            "Loan due in 5 days must not trigger 1-3 day reminder yet",
            com.example.notification.PocketNotificationManager.shouldShowPersistentLoanReminder(
                rahimSummary.copy(loan = rahimSummary.loan.copy(dueDate = fiveDaysFromNow)),
                now
            )
        )

        // Sync notifications and verify non-dismissible ongoing flags
        com.example.notification.PocketNotificationManager.syncLoanNotifications(context)
        val builtNotification = com.example.notification.PocketNotificationManager.buildOngoingLoanNotification(
            context = context,
            summary = rahimSummary,
            currencySymbol = "৳",
            nowMillis = now
        )
        assertTrue(
            "Loan notification must have FLAG_ONGOING_EVENT",
            (builtNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        )
        assertTrue(
            "Loan notification must have FLAG_NO_CLEAR",
            (builtNotification.flags and android.app.Notification.FLAG_NO_CLEAR) != 0
        )

        // Settle the loan (mark as received/paid) and verify reminder condition becomes false
        repo.recordLoanRepayment(
            loan = rahimSummary.loan,
            activity = com.example.data.LoanActivityEntity(
                loanId = loanId,
                activityType = com.example.data.LoanActivityType.REPAYMENT.name,
                principalPortion = 3000.0,
                interestPortion = 0.0,
                walletId = cashWalletId,
                date = now,
                note = "Full repayment"
            )
        )
        val settledSummaries = AccountingEngine.calculateLoanSummaries(dao.getAllLoans(), dao.getAllLoanActivities(), wallets)
        val settledRahim = settledSummaries.first { it.loan.id == loanId }
        org.junit.Assert.assertFalse(
            "Settled loan must immediately stop showing persistent reminder",
            com.example.notification.PocketNotificationManager.shouldShowPersistentLoanReminder(settledRahim, now)
        )
    }

    @Test
    fun `local profile photo picker intents preserve MediaStore URI and EXIF rotation corrects rotated images`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intents = MainActivity.buildLocalImagePickerIntents(context)
        assertTrue("Expected candidate local storage intents", intents.isNotEmpty())

        // First intent must preserve BOTH external MediaStore URI and image/* MIME type
        val first = intents.first()
        assertEquals(android.content.Intent.ACTION_PICK, first.action)
        assertEquals(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, first.data)
        assertEquals("image/*", first.type)

        // Verify EXIF 90-degree rotation correction produces an upright bitmap
        val tempFile = java.io.File(context.cacheDir, "exif_test_portrait.jpg")
        try {
            // Create a landscape 120x60 bitmap that has EXIF ORIENTATION_ROTATE_90 (should become 60x120 upright)
            val rawBmp = android.graphics.Bitmap.createBitmap(120, 60, android.graphics.Bitmap.Config.ARGB_8888)
            tempFile.outputStream().use { out ->
                rawBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            val exif = android.media.ExifInterface(tempFile.absolutePath)
            exif.setAttribute(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            exif.saveAttributes()

            val corrected = com.example.ui.components.decodeExifCorrectedBitmap(
                context = context,
                pathOrUri = android.net.Uri.fromFile(tempFile).toString(),
                maxDimension = 512
            )
            assertNotNull("Expected EXIF-corrected bitmap", corrected)
            assertEquals("Width and height should swap after 90-deg EXIF rotation", 60, corrected!!.width)
            assertEquals("Width and height should swap after 90-deg EXIF rotation", 120, corrected.height)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `numeric input helpers strip leading zeros and start empty for zero values and allowBackup is disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appInfo = context.applicationInfo
        org.junit.Assert.assertEquals(
            "allowBackup must be false so fresh USB installs do not restore old databases",
            0,
            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP
        )

        assertEquals("", com.example.ui.components.formatNumericFieldInitialValue(null))
        assertEquals("", com.example.ui.components.formatNumericFieldInitialValue(0.0))
        assertEquals("1500", com.example.ui.components.formatNumericFieldInitialValue(1500.0))
        assertEquals("1500.75", com.example.ui.components.formatNumericFieldInitialValue(1500.75))

        assertEquals("5", com.example.ui.components.sanitizeNumericInput("05"))
        assertEquals("500", com.example.ui.components.sanitizeNumericInput("0500"))
        assertEquals("0.5", com.example.ui.components.sanitizeNumericInput("0.5"))
        assertEquals("0.5", com.example.ui.components.sanitizeNumericInput(".5"))
        assertEquals("-250", com.example.ui.components.sanitizeNumericInput("-0250", allowNegative = true))
    }

    @Test
    fun `custom wallpaper crops to screen aspect ratio and persists in settings`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = PocketDatabase.getDatabase(context)
            val repo = PocketRepository(db.pocketDao())

            // 1. Verify cropBitmapToAspectRatio correctly produces an image matching target aspect ratio
            val sampleBitmap = android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888)
            val targetAspect = 9f / 16f // 0.5625 (standard mobile screen aspect ratio)
            val cropped = com.example.ui.components.cropBitmapToAspectRatio(
                source = sampleBitmap,
                targetAspectRatio = targetAspect,
                zoom = 1f,
                offsetX = 0f,
                offsetY = 0f,
                viewportWidthPx = 450f,
                viewportHeightPx = 800f,
                maxTargetWidthPx = 720
            )
            assertNotNull("Expected cropped wallpaper bitmap", cropped)
            val croppedAspect = cropped.width.toFloat() / cropped.height.toFloat()
            assertEquals(targetAspect, croppedAspect, 0.05f)

            // 2. Save cropped wallpaper to internal storage
            val savedPath = com.example.ui.components.saveBitmapToInternalStorage(
                context = context,
                bitmap = cropped,
                subDir = "wallpapers",
                prefix = "wallpaper_test",
                compressAsJpeg = true
            )
            assertNotNull("Expected saved wallpaper file path", savedPath)
            assertTrue("Saved wallpaper file must exist on disk", java.io.File(savedPath!!).exists())

            // 3. Persist wallpaper path in repository settings
            repo.updateCustomBackgroundUri(savedPath)
            val updatedSettings = db.pocketDao().getSettings()
            assertNotNull("Expected updated app settings", updatedSettings)
            assertEquals(savedPath, updatedSettings?.customBackgroundUri)

            // 4. Clean up test file
            java.io.File(savedPath).delete()
        }
    }

    @Test
    fun `recurring transactions support Fixed Subscription and Manual Input with Pay wallet picker and Remind Tomorrow`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = PocketDatabase.getDatabase(context)
            val dao = db.pocketDao()
            val repo = PocketRepository(dao)

            repo.seedShowcaseData()
            val wallets = dao.getAllWallets()
            val bkashWallet = wallets.first { it.name.equals("Bkash", ignoreCase = true) }
            val mtbWallet = wallets.first { it.name.equals("MTB", ignoreCase = true) }
            val billsCat = dao.getAllCategories().first { it.type == "EXPENSE" && it.name.contains("Bills", ignoreCase = true) }

            // Give MTB 5000 BDT opening balance so user can pick MTB when Bkash doesn't have enough cash
            dao.updateWallet(mtbWallet.copy(openingBalance = 5000.0))

            val initialTransactionsCount = dao.getAllTransactions().size
            val now = System.currentTimeMillis()

            // 1. Create a FIXED subscription (e.g., WiFi Bill 1200 BDT, default Bkash) due today
            val fixedRecurringId = repo.saveRecurringTransaction(
                RecurringTransactionEntity(
                    title = "Home WiFi Subscription",
                    type = TransactionType.EXPENSE.name,
                    amountMode = RecurringAmountMode.FIXED.name,
                    amount = 1200.0,
                    categoryId = billsCat.id,
                    defaultWalletId = bkashWallet.id,
                    frequency = RecurringFrequency.MONTHLY.name,
                    dayOfMonth = 15,
                    nextDueDate = now,
                    note = "Monthly fiber internet",
                    isActive = true
                )
            )
            assertTrue("Expected valid recurring ID", fixedRecurringId > 0L)

            // 2. Create a MANUAL input recurring bill (e.g., DESCO Electricity) due today
            val manualRecurringId = repo.saveRecurringTransaction(
                RecurringTransactionEntity(
                    title = "DESCO Electricity Bill",
                    type = TransactionType.EXPENSE.name,
                    amountMode = RecurringAmountMode.MANUAL.name,
                    amount = 0.0,
                    categoryId = billsCat.id,
                    defaultWalletId = bkashWallet.id,
                    frequency = RecurringFrequency.MONTHLY.name,
                    dayOfMonth = 10,
                    nextDueDate = now,
                    note = "Variable monthly utility",
                    isActive = true
                )
            )
            assertTrue("Expected valid manual recurring ID", manualRecurringId > 0L)

            // Verify NO transaction was auto-logged before user confirmation
            assertEquals(initialTransactionsCount, dao.getAllTransactions().size)

            // 3. Verify notification is due and has "Pay" and "Remind Tomorrow" actions
            val fixedItem = dao.getRecurringTransactionById(fixedRecurringId)!!
            assertTrue(
                "Due today recurring item should trigger notification",
                com.example.notification.PocketNotificationManager.shouldShowRecurringNotification(fixedItem, now)
            )
            val notification = com.example.notification.PocketNotificationManager.buildRecurringBillNotification(
                context = context,
                item = fixedItem,
                currencySymbol = "৳",
                nowMillis = now
            )
            assertEquals("Expected 2 notification actions (Pay and Remind Tomorrow)", 2, notification.actions.size)
            assertEquals("Pay", notification.actions[0].title.toString())
            assertEquals("Remind Tomorrow", notification.actions[1].title.toString())

            // 4. Press "Remind Tomorrow" on the manual bill -> snoozes to tomorrow and hides today's notification
            val snoozed = repo.snoozeRecurringToTomorrow(manualRecurringId, now)
            assertNotNull(snoozed)
            assertFalse(
                "Snoozed recurring bill must not show notification today",
                com.example.notification.PocketNotificationManager.shouldShowRecurringNotification(snoozed!!, now)
            )
            assertEquals(
                "Snoozing must not log any transaction",
                initialTransactionsCount,
                dao.getAllTransactions().size
            )

            // 5. Press "Pay" on the WiFi bill and pick MTB wallet instead of default Bkash
            val mtbSummaryBefore = AccountingEngine.calculateWalletSummary(
                wallet = dao.getAllWallets().first { it.id == mtbWallet.id },
                transactions = dao.getAllTransactions(),
                transfers = dao.getAllTransfers(),
                loans = dao.getAllLoans(),
                loanActivities = dao.getAllLoanActivities(),
                adjustments = dao.getAllBalanceAdjustments()
            )
            val bkashSummaryBefore = AccountingEngine.calculateWalletSummary(
                wallet = dao.getAllWallets().first { it.id == bkashWallet.id },
                transactions = dao.getAllTransactions(),
                transfers = dao.getAllTransfers(),
                loans = dao.getAllLoans(),
                loanActivities = dao.getAllLoanActivities(),
                adjustments = dao.getAllBalanceAdjustments()
            )

            val loggedTxId = repo.confirmAndPayRecurringTransaction(
                recurringId = fixedRecurringId,
                chosenWalletId = mtbWallet.id,
                finalAmount = 1200.0,
                note = "Paid WiFi via MTB",
                paidAtMillis = now
            )
            assertTrue("Expected logged transaction ID after picking wallet and confirming", loggedTxId > 0L)

            val mtbSummaryAfter = AccountingEngine.calculateWalletSummary(
                wallet = dao.getAllWallets().first { it.id == mtbWallet.id },
                transactions = dao.getAllTransactions(),
                transfers = dao.getAllTransfers(),
                loans = dao.getAllLoans(),
                loanActivities = dao.getAllLoanActivities(),
                adjustments = dao.getAllBalanceAdjustments()
            )
            val bkashSummaryAfter = AccountingEngine.calculateWalletSummary(
                wallet = dao.getAllWallets().first { it.id == bkashWallet.id },
                transactions = dao.getAllTransactions(),
                transfers = dao.getAllTransfers(),
                loans = dao.getAllLoans(),
                loanActivities = dao.getAllLoanActivities(),
                adjustments = dao.getAllBalanceAdjustments()
            )

            // Verify MTB was deducted by 1200 BDT and Bkash was untouched
            assertEquals(mtbSummaryBefore.currentBalance - 1200.0, mtbSummaryAfter.currentBalance, 0.01)
            assertEquals(bkashSummaryBefore.currentBalance, bkashSummaryAfter.currentBalance, 0.01)

            // Verify nextDueDate advanced to next cycle so it is no longer due today
            val updatedFixed = dao.getRecurringTransactionById(fixedRecurringId)!!
            assertFalse(
                "Paid recurring bill should advance to next cycle and not show notification today",
                com.example.notification.PocketNotificationManager.shouldShowRecurringNotification(updatedFixed, now)
            )
        }
    }

    @Test
    fun `per-wallet currency isolates USD and EUR from BDT total balance and supports cross-currency transfer conversion and fees`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = PocketDatabase.getDatabase(context)
            val dao = db.pocketDao()
            val repo = PocketRepository(dao)

            repo.seedShowcaseData()
            repo.resetAllFinancialDataToZero()
            val bdtWallet = dao.getAllWallets().first { it.name.equals("Cash", ignoreCase = true) }
            dao.updateWallet(bdtWallet.copy(openingBalance = 10000.0, currency = "BDT", includeInTotalBalance = true))

            // Create a USD wallet ($200) and a EUR wallet (€150)
            val usdWalletId = dao.insertWallet(
                WalletEntity(
                    name = "Payoneer USD",
                    type = WalletType.BANK.name,
                    openingBalance = 200.0,
                    currency = "USD",
                    iconName = "CARD",
                    colorHex = "#2563EB",
                    includeInTotalBalance = true
                )
            )
            val eurWalletId = dao.insertWallet(
                WalletEntity(
                    name = "Wise EUR",
                    type = WalletType.BANK.name,
                    openingBalance = 150.0,
                    currency = "EUR",
                    iconName = "BANK",
                    colorHex = "#059669",
                    includeInTotalBalance = true
                )
            )

            // Transfer $50 USD from Payoneer USD to Cash BDT at conversion rate 122 BDT/USD (= 6100 BDT) with $2 USD service fee
            repo.saveTransfer(
                transfer = TransferEntity(
                    sourceWalletId = usdWalletId,
                    destinationWalletId = bdtWallet.id,
                    principalAmount = 50.0,
                    destinationAmount = 6100.0,
                    conversionRate = 122.0,
                    feeMode = TransferFeeMode.MANUAL.name,
                    fixedFeeInput = 2.0,
                    feeAmount = 2.0,
                    date = System.currentTimeMillis(),
                    note = "USD to BDT withdrawal"
                ),
                sourceWalletName = "Payoneer USD",
                destWalletName = "Cash"
            )

            val allWallets = dao.getAllWallets()
            val summaries = allWallets.map { w ->
                AccountingEngine.calculateWalletSummary(
                    wallet = w,
                    transactions = dao.getAllTransactions(),
                    transfers = dao.getAllTransfers(),
                    loans = dao.getAllLoans(),
                    loanActivities = dao.getAllLoanActivities(),
                    adjustments = dao.getAllBalanceAdjustments()
                )
            }

            val cashSummary = summaries.first { it.wallet.id == bdtWallet.id }
            val usdSummary = summaries.first { it.wallet.id == usdWalletId }
            val eurSummary = summaries.first { it.wallet.id == eurWalletId }

            // Cash BDT received +6100 BDT (10000 + 6100 = 16100 BDT)
            assertEquals(16100.0, cashSummary.currentBalance, 0.01)
            // Payoneer USD sent $50 + $2 fee = $52 deducted (200 - 52 = $148 USD)
            assertEquals(148.0, usdSummary.currentBalance, 0.01)
            // Wise EUR remains €150 EUR
            assertEquals(150.0, eurSummary.currentBalance, 0.01)

            // BDT total balance must ONLY include BDT wallets (not $148 or €150)
            val bdtTotal = summaries
                .filter { it.wallet.includeInTotalBalance && (it.wallet.currency.isBlank() || it.wallet.currency.equals("BDT", ignoreCase = true)) }
                .sumOf { it.currentBalance }
            assertEquals(16100.0, bdtTotal, 0.01)

            // Foreign currency totals must list USD ($148) and EUR (€150) separately
            val foreignTotals = summaries
                .filter { it.wallet.includeInTotalBalance && !it.wallet.currency.equals("BDT", ignoreCase = true) }
                .groupBy { it.wallet.currency.uppercase() }
                .mapValues { (_, list) -> AccountingEngine.roundCurrency(list.sumOf { it.currentBalance }) }
            assertEquals(148.0, foreignTotals["USD"] ?: 0.0, 0.01)
            assertEquals(150.0, foreignTotals["EUR"] ?: 0.0, 0.01)

            // Currency symbols
            assertEquals("৳", AccountingEngine.currencySymbolForCode("BDT"))
            assertEquals("$", AccountingEngine.currencySymbolForCode("USD"))
            assertEquals("€", AccountingEngine.currencySymbolForCode("EUR"))
        }
    }

    @Test
    fun `wallpaper opacity slider and offline-to-cloud backup merge preserve all records`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = PocketDatabase.getDatabase(context)
            val dao = db.pocketDao()
            val repo = PocketRepository(dao)

            repo.seedShowcaseData()

            // 1. Update background opacity via repository
            repo.updateBackgroundOpacity(0.42f)
            val settingsAfter = dao.getSettings()!!
            assertEquals(0.42f, settingsAfter.backgroundOpacity, 0.001f)

            // 2. Test merging offline local payload with an existing cloud backup payload
            val localPayload = repo.buildBackupPayload()
            val remotePayload = PocketBackupPayload(
                wallets = localPayload.wallets + WalletEntity(
                    id = 99L,
                    name = "Cloud USD Account",
                    type = WalletType.BANK.name,
                    openingBalance = 500.0,
                    currency = "USD",
                    iconName = "BANK",
                    colorHex = "#2563EB"
                ),
                categories = localPayload.categories,
                transactions = listOf(
                    TransactionEntity(
                        id = 500L,
                        type = TransactionType.INCOME.name,
                        amount = 25000.0,
                        date = 1710000000000L,
                        walletId = localPayload.wallets.first().id,
                        categoryId = localPayload.categories.first().id,
                        note = "Cloud Salary Entry"
                    )
                ),
                transfers = emptyList(),
                loans = emptyList(),
                loanActivities = emptyList(),
                adjustments = emptyList(),
                recurringTransactions = emptyList(),
                settings = settingsAfter
            )

            // Also add an offline-only transaction locally
            val offlineTx = TransactionEntity(
                id = 501L,
                type = TransactionType.EXPENSE.name,
                amount = 650.0,
                date = 1710050000000L,
                walletId = localPayload.wallets.first().id,
                categoryId = localPayload.categories.first().id,
                note = "Offline Grocery Entry"
            )
            val localWithOfflineTx = localPayload.copy(transactions = localPayload.transactions + offlineTx)

            val merged = repo.mergeBackupPayloads(local = localWithOfflineTx, remote = remotePayload)
            assertTrue("Merged wallets must contain Cloud USD Account", merged.wallets.any { it.name == "Cloud USD Account" })
            assertTrue("Merged transactions must contain Cloud Salary Entry", merged.transactions.any { it.note == "Cloud Salary Entry" })
            assertTrue("Merged transactions must contain Offline Grocery Entry", merged.transactions.any { it.note == "Offline Grocery Entry" })
        }
    }

    @Test
    fun `wipeAllLocalData clears all cached device data and resets onboarding to false for Startup Page 1 or Page 2`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.pocketDao()
            val repo = PocketRepository(dao)

            // Complete onboarding with wallets, transactions, and loans
            repo.completeOnboarding(
                currencyCode = "BDT",
                currencySymbol = "৳",
                userName = "Abdullahil Hadi",
                profilePhotoUri = "https://example.com/photo.png",
                selectedWallets = PocketRepository.defaultStarterWallets().take(3).map { it to 2500.0 },
                loadSampleTransactions = true,
                starterLoans = listOf(
                    StarterLoanInput("Rahim", LoanDirection.LENT, 800.0, dueDate = null)
                )
            )

            assertTrue(dao.getSettings()?.hasCompletedOnboarding == true)
            assertTrue(dao.getAllWallets().isNotEmpty())
            assertTrue(dao.getAllTransactions().isNotEmpty())
            assertTrue(dao.getAllLoans().isNotEmpty())

            // Simulate signing out or wiping local data when switching to a brand-new Google account
            repo.wipeAllLocalData(
                resetOnboardingToFalse = true,
                userName = "",
                profilePhotoUri = ""
            )

            val settingsAfterWipe = dao.getSettings()
            assertNotNull(settingsAfterWipe)
            assertFalse("Onboarding must be reset to false so user returns to Startup flow", settingsAfterWipe!!.hasCompletedOnboarding)
            assertEquals("Default accent color must match standard preset #F15A24", "#F15A24", settingsAfterWipe.accentColorHex)
            assertTrue("All local wallets must be wiped", dao.getAllWallets().isEmpty())
            assertTrue("All local transactions must be wiped", dao.getAllTransactions().isEmpty())
            assertTrue("All local transfers must be wiped", dao.getAllTransfers().isEmpty())
            assertTrue("All local loans must be wiped", dao.getAllLoans().isEmpty())
            assertTrue("Default categories must still be seeded", dao.getAllCategories().isNotEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `starter existing loans preserve wallet opening balance and FK checks and migration clean up properly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.pocketDao()
            val repo = PocketRepository(dao)

            val starterWallets = PocketRepository.defaultStarterWallets().take(2).map { it to 1000.0 }
            repo.completeOnboarding(
                currencyCode = "BDT",
                currencySymbol = "৳",
                userName = "Hadi",
                profilePhotoUri = "",
                selectedWallets = starterWallets,
                loadSampleTransactions = false,
                starterLoans = listOf(
                    StarterLoanInput("Karim", LoanDirection.LENT, 1500.0, dueDate = null)
                )
            )

            val wallets = dao.getAllWallets()
            val loans = dao.getAllLoans()
            val activities = dao.getAllLoanActivities()
            assertEquals(2, wallets.size)
            assertEquals(1, loans.size)
            assertTrue("Existing starter loan should not create fake today disbursement activity", activities.isEmpty())

            val cashWallet = wallets.first()
            val cashSummary = AccountingEngine.calculateWalletSummary(
                wallet = cashWallet,
                transactions = emptyList(),
                transfers = emptyList(),
                loans = loans,
                loanActivities = activities,
                adjustments = emptyList()
            )
            assertEquals("Cash wallet opening balance must remain 1000.0", 1000.0, cashSummary.currentBalance, 0.001)

            val loanSummaries = AccountingEngine.calculateLoanSummaries(loans, activities, wallets)
            assertEquals(1500.0, loanSummaries.first().outstandingPrincipal, 0.001)

            // Verify canDirectlyDeleteWallet detects loans.initialWalletId FK
            assertFalse("Wallet referenced by loans.initialWalletId must require migration", repo.canDirectlyDeleteWallet(cashWallet.id))

            // Verify countTransactionsForCategory includes recurring_transactions
            val cat = dao.getAllCategories().first()
            assertEquals(0, repo.countTransactionsForCategory(cat.id))
            val recId = repo.saveRecurringTransaction(
                RecurringTransactionEntity(
                    title = "Internet Bill",
                    type = TransactionType.EXPENSE.name,
                    amount = 1200.0,
                    categoryId = cat.id,
                    defaultWalletId = cashWallet.id,
                    frequency = RecurringFrequency.MONTHLY.name,
                    nextDueDate = System.currentTimeMillis(),
                    reminderEnabled = false
                )
            )
            assertEquals(1, repo.countTransactionsForCategory(cat.id))

            val savedRec = dao.getAllRecurringTransactions().first { it.id == recId }
            assertFalse(
                "Recurring notification must respect reminderEnabled = false",
                com.example.notification.PocketNotificationManager.shouldShowRecurringNotification(savedRec)
            )

            // Verify migrateAndDeleteWallet cleans up self-transfer fees and updates defaultWalletId
            val secondWallet = wallets[1]
            repo.saveTransfer(
                transfer = TransferEntity(
                    sourceWalletId = cashWallet.id,
                    destinationWalletId = secondWallet.id,
                    principalAmount = 200.0,
                    feeAmount = 10.0,
                    date = System.currentTimeMillis(),
                    note = "Test transfer with fee"
                ),
                sourceWalletName = cashWallet.name,
                destWalletName = secondWallet.name
            )
            assertEquals(1, dao.getAllTransfers().size)
            assertEquals(1, dao.getAllTransactions().size) // fee transaction

            repo.deleteOrMigrateWallet(cashWallet.id, secondWallet.id)
            assertTrue("Self-transfers after migration must be removed", dao.getAllTransfers().isEmpty())
            assertTrue("Orphan self-transfer fee transactions must be removed", dao.getAllTransactions().isEmpty())
            assertEquals(secondWallet.id, dao.getSettings()?.defaultWalletId)
        } finally {
            db.close()
        }
    }
}




