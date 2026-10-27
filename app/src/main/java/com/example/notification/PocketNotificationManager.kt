package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AccountingEngine
import com.example.data.LoanCalculatedSummary
import com.example.data.LoanDirection
import com.example.data.PocketDatabase
import com.example.data.PocketRepository
import com.example.data.RecurringAmountMode
import com.example.data.RecurringTransactionEntity
import com.example.data.TransactionType
import com.example.widget.PocketWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Manages Pocket's local notifications and WorkManager background schedules:
 * 1. Persistent Loan Deadline Reminders (1–3 days before a Lent/Borrowed loan deadline, and while due/overdue).
 *    Notifications use FLAG_ONGOING_EVENT | FLAG_NO_CLEAR so they cannot be dismissed until the loan is
 *    received (Lent) or paid (Borrowed) in full.
 * 2. Daily Evening Expense Reminder (scheduled for 8:00 PM every evening via WorkManager) to log daily expenses.
 * 3. Recurring Bill & Subscription Reminders ("Ask to Confirm") with "Pay" and "Remind Tomorrow" actions.
 */
object PocketNotificationManager {

    const val CHANNEL_LOAN_DEADLINES = "pocket_loan_deadlines_channel"
    const val CHANNEL_DAILY_EXPENSE = "pocket_daily_expense_channel"
    const val CHANNEL_RECURRING_BILLS = "pocket_recurring_bills_channel"

    const val WORK_PERIODIC_LOAN_CHECK = "pocket_periodic_loan_deadline_check"
    const val WORK_PERIODIC_DAILY_EXPENSE = "pocket_periodic_daily_expense_reminder"

    const val ACTION_REMIND_TOMORROW = "com.example.notification.ACTION_REMIND_TOMORROW"
    const val EXTRA_RECURRING_ID = "extra_recurring_id"

    const val NOTIFICATION_ID_DAILY_EXPENSE = 89001
    private const val NOTIFICATION_ID_LOAN_BASE = 90000
    private const val NOTIFICATION_ID_RECURRING_BASE = 95000
    private const val NOTIFICATION_ID_CARD_BASE = 98000
    private const val PREFS_NAME = "pocket_notification_prefs"
    private const val KEY_ACTIVE_LOAN_NOTIFICATION_IDS = "active_loan_notification_ids"
    private const val KEY_ACTIVE_RECURRING_NOTIFICATION_IDS = "active_recurring_notification_ids"
    private const val KEY_ACTIVE_CARD_NOTIFICATION_IDS = "active_card_notification_ids"

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    const val THREE_DAYS_MILLIS = 3L * DAY_MILLIS

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val loanChannel = NotificationChannel(
                CHANNEL_LOAN_DEADLINES,
                "Loan Deadline Persistent Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pinned reminders 1–3 days before Lent/Borrowed loan deadlines that stay active until settled."
                enableVibration(true)
            }

            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY_EXPENSE,
                "Daily Evening Expense Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Evening reminder to log your daily expenses and income in Pocket."
            }

            val recurringChannel = NotificationChannel(
                CHANNEL_RECURRING_BILLS,
                "Recurring Bills & Subscriptions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for due recurring bills and subscriptions with Pay and Remind Tomorrow options."
                enableVibration(true)
            }

            manager.createNotificationChannel(loanChannel)
            manager.createNotificationChannel(dailyChannel)
            manager.createNotificationChannel(recurringChannel)
        }
    }

    fun notificationIdForLoan(loanId: Long): Int {
        return NOTIFICATION_ID_LOAN_BASE + (loanId % 50000L).toInt()
    }

    fun notificationIdForRecurring(recurringId: Long): Int {
        return NOTIFICATION_ID_RECURRING_BASE + (recurringId % 50000L).toInt()
    }

    fun notificationIdForCard(walletId: Long): Int {
        return NOTIFICATION_ID_CARD_BASE + (walletId % 10000L).toInt()
    }

    /**
     * Computes the number of days from today until the next occurrence of [dayOfMonth] (1..31).
     */
    fun daysUntilDayOfMonth(
        dayOfMonth: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (dayOfMonth !in 1..31) return Int.MAX_VALUE
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = (todayCal.clone() as Calendar).apply {
            val maxDayThisMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDayThisMonth))
        }
        if (targetCal.timeInMillis < todayCal.timeInMillis) {
            targetCal.add(Calendar.MONTH, 1)
            val maxDayNextMonth = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            targetCal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDayNextMonth))
        }
        val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
        return (diffMillis / DAY_MILLIS).toInt().coerceAtLeast(0)
    }

    fun endOfDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /**
     * Checks whether a recurring rule is due today or overdue and should trigger a confirmation notification.
     */
    fun shouldShowRecurringNotification(
        item: RecurringTransactionEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!item.isActive) return false
        return item.nextDueDate <= endOfDayMillis(nowMillis)
    }

    /**
     * Builds the "Ask to Confirm" notification for a due recurring transaction with two actions:
     * - "Pay": Opens the app and prompts the user to pick a wallet and confirm/input the amount.
     * - "Remind Tomorrow": Snoozes `nextDueDate` to tomorrow and dismisses the notification.
     */
    fun buildRecurringBillNotification(
        context: Context,
        item: RecurringTransactionEntity,
        currencySymbol: String = "৳",
        nowMillis: Long = System.currentTimeMillis()
    ): Notification {
        ensureChannelsCreated(context)
        val isFixed = item.amountMode == RecurringAmountMode.FIXED.name
        val isIncome = item.type == TransactionType.INCOME.name
        val dueFormatted = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(item.nextDueDate))

        val title = if (isIncome) {
            "Recurring Income Due: ${item.title}"
        } else {
            "Recurring Bill Due: ${item.title}"
        }

        val amountDesc = if (isFixed && item.amount > 0.0) {
            "${AccountingEngine.formatMoney(item.amount, currencySymbol)} (Fixed subscription)"
        } else {
            "Manual amount input"
        }

        val body = "$amountDesc • Due $dueFormatted. Tap Pay to choose a wallet & log, or Remind Tomorrow."

        val payIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION, "RECURRING_PAY")
            putExtra(EXTRA_RECURRING_ID, item.id)
        }
        val payPendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForRecurring(item.id),
            payIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remindIntent = Intent(context, RecurringNotificationActionReceiver::class.java).apply {
            action = ACTION_REMIND_TOMORROW
            putExtra(EXTRA_RECURRING_ID, item.id)
        }
        val remindPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationIdForRecurring(item.id) + 25000,
            remindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val payActionLabel = if (isIncome) "Receive / Log" else "Pay"

        return NotificationCompat.Builder(context, CHANNEL_RECURRING_BILLS)
            .setSmallIcon(R.drawable.ic_pocket_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(payPendingIntent)
            .addAction(
                R.drawable.ic_pocket_logo,
                payActionLabel,
                payPendingIntent
            )
            .addAction(
                R.drawable.ic_pocket_logo,
                "Remind Tomorrow",
                remindPendingIntent
            )
            .build()
    }

    /**
     * Synchronizes all due recurring bill notifications with the current database state.
     */
    suspend fun syncRecurringNotifications(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)

        val db = PocketDatabase.getDatabase(appCtx)
        val dao = db.pocketDao()
        val settings = dao.getSettings()
        val symbol = settings?.currencySymbol ?: "৳"
        val allRecurring = dao.getAllRecurringTransactions()

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyNotifiedIds = prefs.getStringSet(KEY_ACTIVE_RECURRING_NOTIFICATION_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

        val notificationManager = NotificationManagerCompat.from(appCtx)
        val currentlyActiveIds = mutableSetOf<Long>()

        allRecurring.forEach { item ->
            if (shouldShowRecurringNotification(item, nowMillis)) {
                currentlyActiveIds.add(item.id)
                if (hasNotificationPermission(appCtx)) {
                    val notification = buildRecurringBillNotification(
                        context = appCtx,
                        item = item,
                        currencySymbol = symbol,
                        nowMillis = nowMillis
                    )
                    runCatching {
                        notificationManager.notify(notificationIdForRecurring(item.id), notification)
                    }
                }
            } else {
                runCatching {
                    notificationManager.cancel(notificationIdForRecurring(item.id))
                }
            }
        }

        (previouslyNotifiedIds - currentlyActiveIds).forEach { removedId ->
            runCatching {
                notificationManager.cancel(notificationIdForRecurring(removedId))
            }
        }

        prefs.edit()
            .putStringSet(
                KEY_ACTIVE_RECURRING_NOTIFICATION_IDS,
                currentlyActiveIds.map { it.toString() }.toSet()
            )
            .apply()

        return currentlyActiveIds.size
    }

    /**
     * Checks whether a loan qualifies for a persistent deadline reminder:
     * - Must not be settled (`status != LoanStatus.SETTLED` and `remainingBalance > 0.005`)
     * - Must have a non-null `dueDate`
     * - Deadline must be within the next 3 days (1–3 days before deadline, due today, or overdue)
     */
    fun shouldShowPersistentLoanReminder(
        summary: LoanCalculatedSummary,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (summary.status == "SETTLED" || summary.outstandingPrincipal <= 0.005) {
            return false
        }
        val dueDate = summary.loan.dueDate ?: return false
        val timeRemaining = dueDate - nowMillis
        return timeRemaining <= THREE_DAYS_MILLIS
    }

    fun cancelAllLoanNotifications(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyNotifiedLoanIds = prefs.getStringSet(KEY_ACTIVE_LOAN_NOTIFICATION_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        val notificationManager = NotificationManagerCompat.from(appCtx)
        previouslyNotifiedLoanIds.forEach { loanId ->
            runCatching {
                notificationManager.cancel(notificationIdForLoan(loanId))
            }
        }
        prefs.edit().remove(KEY_ACTIVE_LOAN_NOTIFICATION_IDS).apply()
    }

    /**
     * Synchronizes all ongoing loan deadline notifications with the current database state.
     * - If `settings.loanDeadlineNotificationEnabled == false`, cancels all active loan deadline notifications.
     * - Posts/updates ongoing non-dismissible notifications for active loans due within 1–3 days (or overdue).
     * - Immediately cancels notifications for loans that have been received/paid (settled), deleted, or moved >3 days out.
     */
    suspend fun syncLoanNotifications(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)

        val db = PocketDatabase.getDatabase(appCtx)
        val dao = db.pocketDao()
        val settings = dao.getSettings()
        if (settings != null && !settings.loanDeadlineNotificationEnabled) {
            cancelAllLoanNotifications(appCtx)
            return 0
        }

        val loans = dao.getAllLoans()
        val activities = dao.getAllLoanActivities()
        val wallets = dao.getAllWallets()
        val symbol = settings?.currencySymbol ?: "৳"

        val summaries = AccountingEngine.calculateLoanSummaries(
            loans = loans,
            loanActivities = activities,
            wallets = wallets
        )

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyNotifiedLoanIds = prefs.getStringSet(KEY_ACTIVE_LOAN_NOTIFICATION_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

        val notificationManager = NotificationManagerCompat.from(appCtx)
        val currentlyActiveLoanIds = mutableSetOf<Long>()

        summaries.forEach { summary ->
            val loanId = summary.loan.id
            if (shouldShowPersistentLoanReminder(summary, nowMillis)) {
                currentlyActiveLoanIds.add(loanId)
                if (hasNotificationPermission(appCtx)) {
                    val notification = buildOngoingLoanNotification(
                        context = appCtx,
                        summary = summary,
                        currencySymbol = symbol,
                        nowMillis = nowMillis
                    )
                    runCatching {
                        notificationManager.notify(notificationIdForLoan(loanId), notification)
                    }
                }
            } else {
                // Loan is settled (received/paid) or no longer due within 3 days -> remove notification immediately
                runCatching {
                    notificationManager.cancel(notificationIdForLoan(loanId))
                }
            }
        }

        // Also cancel notifications for any loans that were deleted
        (previouslyNotifiedLoanIds - currentlyActiveLoanIds).forEach { removedLoanId ->
            runCatching {
                notificationManager.cancel(notificationIdForLoan(removedLoanId))
            }
        }

        prefs.edit()
            .putStringSet(
                KEY_ACTIVE_LOAN_NOTIFICATION_IDS,
                currentlyActiveLoanIds.map { it.toString() }.toSet()
            )
            .apply()

        return currentlyActiveLoanIds.size
    }

    /**
     * Builds a persistent, non-dismissible (`FLAG_ONGOING_EVENT | FLAG_NO_CLEAR`) notification for a Lent/Borrowed loan.
     */
    fun buildOngoingLoanNotification(
        context: Context,
        summary: LoanCalculatedSummary,
        currencySymbol: String = "৳",
        nowMillis: Long = System.currentTimeMillis()
    ): Notification {
        ensureChannelsCreated(context)
        val loan = summary.loan
        val isLent = loan.direction == LoanDirection.LENT.name
        val dueDate = loan.dueDate ?: nowMillis
        val diffMillis = dueDate - nowMillis
        val daysLeft = ceil(diffMillis.toDouble() / DAY_MILLIS.toDouble()).toInt()
        val dueFormatted = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(dueDate))
        val amountFormatted = AccountingEngine.formatMoney(summary.outstandingPrincipal, currencySymbol)

        val actionVerb = if (isLent) "Receive from" else "Pay"
        val settlementCondition = if (isLent) "received" else "paid"

        val title = when {
            daysLeft < 0 -> "Overdue Loan: $actionVerb ${loan.personName}"
            daysLeft == 0 -> "Loan Due Today: $actionVerb ${loan.personName}"
            daysLeft == 1 -> "Loan Due Tomorrow: $actionVerb ${loan.personName}"
            else -> "Loan Due in $daysLeft Days: $actionVerb ${loan.personName}"
        }

        val body = "$amountFormatted remaining (Deadline: $dueFormatted). Pinned until $settlementCondition in Pocket."

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION, "LOANS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForLoan(loan.id),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOAN_DEADLINES)
            .setSmallIcon(R.drawable.ic_pocket_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        notification.flags = notification.flags or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR

        return notification
    }

    /**
     * Formats a 24-hour `(hour, minute)` pair into a 12-hour label such as `"08:00 PM"`.
     */
    fun formatReminderTime(hour: Int, minute: Int): String {
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val amPm = if (h24 >= 12) "PM" else "AM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.ENGLISH, "%02d:%02d %s", h12, m, amPm)
    }

    /**
     * Posts the daily reminder encouraging the user to log today's income and expenses.
     */
    fun postDailyExpenseReminder(context: Context): Boolean {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)
        if (!hasNotificationPermission(appCtx)) return false

        val launchIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION, "ADD_EXPENSE")
        }
        val pendingIntent = PendingIntent.getActivity(
            appCtx,
            NOTIFICATION_ID_DAILY_EXPENSE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Daily Check-In: Log Today's Entries"
        val body = "Take a moment to record today's income and expenses so your Pocket wallet balances stay accurate."

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_DAILY_EXPENSE)
            .setSmallIcon(R.drawable.ic_pocket_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appCtx).notify(NOTIFICATION_ID_DAILY_EXPENSE, notification)
            true
        }.getOrDefault(false)
    }

    /**
     * Calculates the delay in milliseconds from now until the next occurrence of `(hour:minute)` local time.
     */
    fun calculateDelayUntilNextReminderMillis(
        hour: Int = 20,
        minute: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (targetCal.timeInMillis - nowCal.timeInMillis).coerceAtLeast(60_000L)
    }

    /**
     * Reschedules or cancels the daily log entry reminder WorkManager task according to user settings.
     */
    fun rescheduleDailyLogReminder(
        context: Context,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)
        runCatching {
            val workManager = WorkManager.getInstance(appCtx)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_PERIODIC_DAILY_EXPENSE)
                NotificationManagerCompat.from(appCtx).cancel(NOTIFICATION_ID_DAILY_EXPENSE)
            } else {
                val delay = calculateDelayUntilNextReminderMillis(hour = hour, minute = minute)
                val dailyExpenseRequest = PeriodicWorkRequestBuilder<DailyExpenseReminderWorker>(
                    24, TimeUnit.HOURS
                )
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_PERIODIC_DAILY_EXPENSE,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    dailyExpenseRequest
                )
            }
        }
    }

    /**
     * Schedules periodic WorkManager tasks using the saved settings:
     * 1. Periodic 6-hour loan & recurring bill check + immediate sync.
     * 2. Periodic 24-hour daily log entry reminder aligned to the user's chosen time (default 8:00 PM).
     */
    fun scheduleAllReminders(
        context: Context,
        loanDeadlineEnabled: Boolean = true,
        dailyReminderEnabled: Boolean = true,
        dailyHour: Int = 20,
        dailyMinute: Int = 0
    ) {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)

        runCatching {
            val workManager = WorkManager.getInstance(appCtx)

            val loanCheckRequest = PeriodicWorkRequestBuilder<LoanDeadlineReminderWorker>(
                6, TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_PERIODIC_LOAN_CHECK,
                ExistingPeriodicWorkPolicy.KEEP,
                loanCheckRequest
            )
            workManager.enqueue(OneTimeWorkRequestBuilder<LoanDeadlineReminderWorker>().build())

            if (!loanDeadlineEnabled) {
                cancelAllLoanNotifications(appCtx)
            }

            if (dailyReminderEnabled) {
                val initialDelay = calculateDelayUntilNextReminderMillis(hour = dailyHour, minute = dailyMinute)
                val dailyExpenseRequest = PeriodicWorkRequestBuilder<DailyExpenseReminderWorker>(
                    24, TimeUnit.HOURS
                )
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_PERIODIC_DAILY_EXPENSE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    dailyExpenseRequest
                )
            } else {
                workManager.cancelUniqueWork(WORK_PERIODIC_DAILY_EXPENSE)
            }
        }
    }
    /**
     * Synchronizes early credit card payment due date notifications (up to 5 days before paymentDueDay)
     * for active CARD wallets that have `paymentDueDay` configured.
     */
    suspend fun syncCreditCardNotifications(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val appCtx = context.applicationContext
        ensureChannelsCreated(appCtx)
        val db = PocketDatabase.getDatabase(appCtx)
        val dao = db.pocketDao()
        val wallets = dao.getAllWallets()
        val transactions = dao.getAllTransactions()
        val transfers = dao.getAllTransfers()
        val loans = dao.getAllLoans()
        val loanActivities = dao.getAllLoanActivities()
        val adjustments = dao.getAllBalanceAdjustments()
        val summariesByWalletId = wallets.associate { wallet ->
            wallet.id to AccountingEngine.calculateWalletSummary(
                wallet = wallet,
                transactions = transactions,
                transfers = transfers,
                loans = loans,
                loanActivities = loanActivities,
                adjustments = adjustments
            )
        }

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevCardIds = prefs.getStringSet(KEY_ACTIVE_CARD_NOTIFICATION_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

        val notificationManager = NotificationManagerCompat.from(appCtx)
        val activeCardIds = mutableSetOf<Long>()
        val canPost = hasNotificationPermission(appCtx)

        wallets.filter { !it.isArchived && it.type.equals("CARD", ignoreCase = true) && it.paymentDueDay in 1..31 }
            .forEach { card ->
                val daysLeft = daysUntilDayOfMonth(card.paymentDueDay, nowMillis)
                val balance = summariesByWalletId[card.id]?.currentBalance ?: card.openingBalance
                val usedCredit = if (balance < 0.0) kotlin.math.abs(balance) else balance
                // Notify early (0..5 days before paymentDueDay) when card has a due date configured
                if (daysLeft in 0..5) {
                    activeCardIds.add(card.id)
                    if (canPost) {
                        val symbol = AccountingEngine.currencySymbolForCode(card.currency)
                        val dueLabel = when (daysLeft) {
                            0 -> "Due TODAY (Day ${card.paymentDueDay})"
                            1 -> "Due TOMORROW (Day ${card.paymentDueDay})"
                            else -> "Due in $daysLeft days (Day ${card.paymentDueDay})"
                        }
                        val utilPart = if (card.creditLimit > 0.0) {
                            val pct = ((usedCredit / card.creditLimit) * 100.0).coerceIn(0.0, 999.0)
                            " • Utilization: ${String.format(Locale.ENGLISH, "%.0f%%", pct)} of ${AccountingEngine.formatMoney(card.creditLimit, symbol)}"
                        } else ""
                        val title = "Credit Card Bill Reminder: ${card.name}"
                        val body = "$dueLabel • Current Balance: ${AccountingEngine.formatMoney(balance, symbol)}$utilPart"

                        val intent = Intent(appCtx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION, PocketWidgetUpdater.DEST_WALLET_DETAIL)
                            putExtra(PocketWidgetUpdater.EXTRA_WIDGET_WALLET_ID, card.id)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            appCtx,
                            notificationIdForCard(card.id),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val notification = NotificationCompat.Builder(appCtx, CHANNEL_RECURRING_BILLS)
                            .setSmallIcon(R.drawable.ic_pocket_logo)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build()
                        runCatching {
                            notificationManager.notify(notificationIdForCard(card.id), notification)
                        }
                    }
                }
            }

        (prevCardIds - activeCardIds).forEach { staleId ->
            runCatching { notificationManager.cancel(notificationIdForCard(staleId)) }
        }
        prefs.edit()
            .putStringSet(KEY_ACTIVE_CARD_NOTIFICATION_IDS, activeCardIds.map { it.toString() }.toSet())
            .apply()

        return activeCardIds.size
    }
}

class RecurringNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PocketNotificationManager.ACTION_REMIND_TOMORROW) return
        val recurringId = intent.getLongExtra(PocketNotificationManager.EXTRA_RECURRING_ID, 0L)
        if (recurringId <= 0L) return

        val appCtx = context.applicationContext
        runCatching {
            NotificationManagerCompat.from(appCtx).cancel(
                PocketNotificationManager.notificationIdForRecurring(recurringId)
            )
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = PocketDatabase.getDatabase(appCtx).pocketDao()
                val repo = PocketRepository(dao)
                repo.snoozeRecurringToTomorrow(recurringId)
                PocketNotificationManager.syncRecurringNotifications(appCtx)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}

class LoanDeadlineReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            PocketNotificationManager.syncLoanNotifications(applicationContext)
            PocketNotificationManager.syncRecurringNotifications(applicationContext)
            PocketNotificationManager.syncCreditCardNotifications(applicationContext)
            Result.success()
        }.getOrElse { Result.success() }
    }
}

class DailyExpenseReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val settings = PocketDatabase.getDatabase(applicationContext).pocketDao().getSettings()
            if (settings == null || settings.dailyExpenseReminderEnabled) {
                PocketNotificationManager.postDailyExpenseReminder(applicationContext)
            }
            if (settings == null || settings.loanDeadlineNotificationEnabled) {
                PocketNotificationManager.syncLoanNotifications(applicationContext)
            }
            PocketNotificationManager.syncRecurringNotifications(applicationContext)
            PocketNotificationManager.syncCreditCardNotifications(applicationContext)
            Result.success()
        }.getOrElse { Result.success() }
    }
}
