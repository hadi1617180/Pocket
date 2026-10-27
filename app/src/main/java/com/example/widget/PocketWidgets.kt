package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AccountingEngine
import com.example.data.PocketDatabase
import com.example.data.WalletBalanceSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class WidgetSnapshotData(
    val currencySymbol: String,
    val totalActiveBalance: Double,
    val activeWalletSummaries: List<WalletBalanceSummary>,
    val allWalletSummaries: List<WalletBalanceSummary>,
    val hideBalancesPrivacy: Boolean = false,
    val accentColorHex: String = "#F97316"
)

object PocketWidgetUpdater {
    const val EXTRA_WIDGET_DESTINATION = "extra_widget_destination"
    const val EXTRA_WIDGET_WALLET_ID = "extra_widget_wallet_id"

    const val DEST_HOME = "HOME"
    const val DEST_ADD_INCOME = "ADD_INCOME"
    const val DEST_ADD_EXPENSE = "ADD_EXPENSE"
    const val DEST_WALLET_DETAIL = "WALLET_DETAIL"

    private fun parseAccentColorInt(hex: String): Int {
        return runCatching {
            val cleaned = if (hex.startsWith("#")) hex else "#$hex"
            android.graphics.Color.parseColor(cleaned)
        }.getOrDefault(android.graphics.Color.parseColor("#F97316"))
    }

    private fun formatWidgetMoney(
        amount: Double,
        symbol: String,
        hideBalancesPrivacy: Boolean,
        includeDecimals: Boolean = true
    ): String {
        return if (hideBalancesPrivacy) {
            "$symbol ••••"
        } else {
            AccountingEngine.formatMoney(amount, symbol, includeDecimals)
        }
    }

    /**
     * Queries the single-source-of-truth Room database and AccountingEngine to compute
     * up-to-date balances for all widgets.
     */
    suspend fun loadSnapshot(context: Context): WidgetSnapshotData {
        val dao = PocketDatabase.getDatabase(context).pocketDao()
        val wallets = dao.getAllWallets()
        val transactions = dao.getAllTransactions()
        val transfers = dao.getAllTransfers()
        val loans = dao.getAllLoans()
        val loanActivities = dao.getAllLoanActivities()
        val adjustments = dao.getAllBalanceAdjustments()
        val settings = dao.getSettings()
        val symbol = settings?.currencySymbol ?: "৳"
        val hidePrivacy = settings?.hideBalancesPrivacy ?: false
        val accentHex = settings?.accentColorHex ?: "#F97316"

        val summaries = wallets.map { w ->
            AccountingEngine.calculateWalletSummary(
                wallet = w,
                transactions = transactions,
                transfers = transfers,
                loans = loans,
                loanActivities = loanActivities,
                adjustments = adjustments
            )
        }
        val active = summaries.filter { !it.wallet.isArchived }
        val total = AccountingEngine.roundCurrency(
            active.filter {
                it.wallet.includeInTotalBalance &&
                    (it.wallet.currency.isBlank() || it.wallet.currency.equals("BDT", ignoreCase = true))
            }.sumOf { it.currentBalance }
        )

        return WidgetSnapshotData(
            currencySymbol = symbol,
            totalActiveBalance = total,
            activeWalletSummaries = active,
            allWalletSummaries = summaries,
            hideBalancesPrivacy = hidePrivacy,
            accentColorHex = accentHex
        )
    }

    fun refreshAllWidgets(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val snapshot = loadSnapshot(appContext)
                val manager = AppWidgetManager.getInstance(appContext)

                val totalIds = manager.getAppWidgetIds(
                    ComponentName(appContext, TotalBalanceWidgetProvider::class.java)
                )
                totalIds.forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val views = buildTotalBalanceRemoteViews(appContext, id, options, snapshot)
                    manager.updateAppWidget(id, views)
                }

                val overviewIds = manager.getAppWidgetIds(
                    ComponentName(appContext, WalletOverviewWidgetProvider::class.java)
                )
                overviewIds.forEach { id ->
                    val views = buildWalletOverviewRemoteViews(appContext, id, snapshot)
                    manager.updateAppWidget(id, views)
                }

                val singleIds = manager.getAppWidgetIds(
                    ComponentName(appContext, SingleWalletWidgetProvider::class.java)
                )
                singleIds.forEach { id ->
                    val views = buildSingleWalletRemoteViews(appContext, id, snapshot)
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }

    fun createMainActivityPendingIntent(
        context: Context,
        requestCode: Int,
        destination: String,
        walletId: Long? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WIDGET_DESTINATION, destination)
            if (walletId != null) {
                putExtra(EXTRA_WIDGET_WALLET_ID, walletId)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun buildTotalBalanceRemoteViews(
        context: Context,
        appWidgetId: Int,
        options: Bundle?,
        snapshot: WidgetSnapshotData
    ): RemoteViews {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 200
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 110
        val isCompact = minWidth < 170 || minHeight < 88

        val formattedBalance = formatWidgetMoney(
            snapshot.totalActiveBalance,
            snapshot.currencySymbol,
            snapshot.hideBalancesPrivacy
        )
        val accentInt = parseAccentColorInt(snapshot.accentColorHex)

        return if (isCompact) {
            RemoteViews(context.packageName, R.layout.widget_total_balance_compact).apply {
                setTextViewText(R.id.widget_total_amount, formattedBalance)
                setTextColor(R.id.widget_compact_add_expense, accentInt)
                setOnClickPendingIntent(
                    R.id.widget_total_balance_click_area,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 1, DEST_HOME)
                )
                setOnClickPendingIntent(
                    R.id.widget_compact_add_income,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 2, DEST_ADD_INCOME)
                )
                setOnClickPendingIntent(
                    R.id.widget_compact_add_expense,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 3, DEST_ADD_EXPENSE)
                )
            }
        } else {
            RemoteViews(context.packageName, R.layout.widget_total_balance_large).apply {
                setTextViewText(R.id.widget_total_amount, formattedBalance)
                setTextViewText(
                    R.id.widget_active_wallets_count,
                    "${snapshot.activeWalletSummaries.size} Wallets"
                )
                setTextColor(R.id.widget_active_wallets_count, accentInt)
                setTextColor(R.id.widget_btn_add_expense, accentInt)
                setOnClickPendingIntent(
                    R.id.widget_total_balance_click_area,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 1, DEST_HOME)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_add_income,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 2, DEST_ADD_INCOME)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_add_expense,
                    createMainActivityPendingIntent(context, appWidgetId * 10 + 3, DEST_ADD_EXPENSE)
                )
            }
        }
    }

    fun buildWalletOverviewRemoteViews(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshotData
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_wallet_overview)
        val configuredIds = WidgetPreferences.getOverviewWalletIds(context, appWidgetId)
        val accentInt = parseAccentColorInt(snapshot.accentColorHex)

        val selectedSummaries = if (configuredIds.isNotEmpty()) {
            snapshot.allWalletSummaries.filter { it.wallet.id in configuredIds && !it.wallet.isArchived }
                .ifEmpty { snapshot.activeWalletSummaries }
        } else {
            snapshot.activeWalletSummaries
        }.take(5)

        val shownTotal = AccountingEngine.roundCurrency(
            selectedSummaries
                .filter { it.wallet.currency.isBlank() || it.wallet.currency.equals("BDT", ignoreCase = true) }
                .sumOf { it.currentBalance }
        )
        views.setTextViewText(
            R.id.widget_overview_total,
            formatWidgetMoney(shownTotal, snapshot.currencySymbol, snapshot.hideBalancesPrivacy, includeDecimals = false)
        )
        views.setTextColor(R.id.widget_overview_total, accentInt)
        views.setOnClickPendingIntent(
            R.id.widget_overview_header,
            createMainActivityPendingIntent(context, appWidgetId * 100, DEST_HOME)
        )

        val rowIds = listOf(
            Triple(R.id.widget_wallet_row_1, R.id.widget_wallet_name_1, R.id.widget_wallet_bal_1),
            Triple(R.id.widget_wallet_row_2, R.id.widget_wallet_name_2, R.id.widget_wallet_bal_2),
            Triple(R.id.widget_wallet_row_3, R.id.widget_wallet_name_3, R.id.widget_wallet_bal_3),
            Triple(R.id.widget_wallet_row_4, R.id.widget_wallet_name_4, R.id.widget_wallet_bal_4),
            Triple(R.id.widget_wallet_row_5, R.id.widget_wallet_name_5, R.id.widget_wallet_bal_5)
        )

        if (selectedSummaries.isEmpty()) {
            views.setViewVisibility(R.id.widget_overview_empty, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_overview_empty,
                createMainActivityPendingIntent(context, appWidgetId * 100 + 9, DEST_HOME)
            )
            rowIds.forEach { (rowId, _, _) -> views.setViewVisibility(rowId, View.GONE) }
        } else {
            views.setViewVisibility(R.id.widget_overview_empty, View.GONE)
            rowIds.forEachIndexed { index, (rowId, nameId, balId) ->
                val summary = selectedSummaries.getOrNull(index)
                if (summary != null) {
                    val wSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(nameId, summary.wallet.name)
                    views.setTextViewText(
                        balId,
                        formatWidgetMoney(summary.currentBalance, wSym, snapshot.hideBalancesPrivacy, includeDecimals = false)
                    )
                    views.setOnClickPendingIntent(
                        rowId,
                        createMainActivityPendingIntent(
                            context = context,
                            requestCode = appWidgetId * 100 + index + 1,
                            destination = DEST_WALLET_DETAIL,
                            walletId = summary.wallet.id
                        )
                    )
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }
        }
        return views
    }

    fun buildSingleWalletRemoteViews(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshotData
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_single_wallet)
        val configuredId = WidgetPreferences.getSingleWalletId(context, appWidgetId)
        val accentInt = parseAccentColorInt(snapshot.accentColorHex)

        val targetSummary = if (configuredId != null) {
            snapshot.allWalletSummaries.firstOrNull { it.wallet.id == configuredId }
        } else {
            snapshot.activeWalletSummaries.firstOrNull()
        }

        if (targetSummary != null) {
            val w = targetSummary.wallet
            val wSym = AccountingEngine.currencySymbolForCode(w.currency)
            val badgeText = if (w.isArchived) "${w.type} · ARCHIVED" else w.type
            views.setTextViewText(R.id.widget_single_wallet_badge, badgeText)
            views.setTextColor(R.id.widget_single_wallet_badge, accentInt)
            views.setTextViewText(R.id.widget_single_wallet_name, w.name)
            views.setTextViewText(
                R.id.widget_single_wallet_balance,
                formatWidgetMoney(targetSummary.currentBalance, wSym, snapshot.hideBalancesPrivacy)
            )
            views.setOnClickPendingIntent(
                R.id.widget_single_wallet_root,
                createMainActivityPendingIntent(
                    context = context,
                    requestCode = appWidgetId * 1000 + w.id.toInt(),
                    destination = DEST_WALLET_DETAIL,
                    walletId = w.id
                )
            )
        } else {
            views.setTextViewText(R.id.widget_single_wallet_badge, "POCKET")
            views.setTextColor(R.id.widget_single_wallet_badge, accentInt)
            views.setTextViewText(R.id.widget_single_wallet_name, "No wallet found")
            views.setTextViewText(R.id.widget_single_wallet_balance, "Tap to open")
            views.setOnClickPendingIntent(
                R.id.widget_single_wallet_root,
                createMainActivityPendingIntent(context, appWidgetId * 1000, DEST_HOME)
            )
        }
        return views
    }
}

class TotalBalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = PocketWidgetUpdater.loadSnapshot(context)
                appWidgetIds.forEach { id ->
                    val options = appWidgetManager.getAppWidgetOptions(id)
                    val views = PocketWidgetUpdater.buildTotalBalanceRemoteViews(
                        context,
                        id,
                        options,
                        snapshot
                    )
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = PocketWidgetUpdater.loadSnapshot(context)
                val views = PocketWidgetUpdater.buildTotalBalanceRemoteViews(
                    context,
                    appWidgetId,
                    newOptions,
                    snapshot
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class WalletOverviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = PocketWidgetUpdater.loadSnapshot(context)
                appWidgetIds.forEach { id ->
                    val views = PocketWidgetUpdater.buildWalletOverviewRemoteViews(context, id, snapshot)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPreferences.deleteWidgetConfig(context, it) }
    }
}

class SingleWalletWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = PocketWidgetUpdater.loadSnapshot(context)
                appWidgetIds.forEach { id ->
                    val views = PocketWidgetUpdater.buildSingleWalletRemoteViews(context, id, snapshot)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPreferences.deleteWidgetConfig(context, it) }
    }
}
