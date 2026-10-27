package com.example.widget

import android.content.Context

object WidgetPreferences {
    private const val PREFS_NAME = "pocket_widget_prefs"
    private const val KEY_DEFAULT_OVERVIEW_WALLETS = "default_overview_wallet_ids"
    private const val KEY_DEFAULT_SINGLE_WALLET = "default_single_wallet_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveOverviewWalletIds(context: Context, appWidgetId: Int, walletIds: Set<Long>) {
        val serialized = walletIds.joinToString(",")
        prefs(context).edit()
            .putString("overview_wallets_$appWidgetId", serialized)
            .putString(KEY_DEFAULT_OVERVIEW_WALLETS, serialized)
            .apply()
    }

    fun setDefaultOverviewWalletIds(context: Context, walletIds: Set<Long>) {
        val serialized = walletIds.joinToString(",")
        prefs(context).edit()
            .putString(KEY_DEFAULT_OVERVIEW_WALLETS, serialized)
            .apply()
    }

    fun getOverviewWalletIds(context: Context, appWidgetId: Int? = null): Set<Long> {
        val p = prefs(context)
        val raw = if (appWidgetId != null && p.contains("overview_wallets_$appWidgetId")) {
            p.getString("overview_wallets_$appWidgetId", null)
        } else {
            p.getString(KEY_DEFAULT_OVERVIEW_WALLETS, null)
        }
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    fun saveSingleWalletId(context: Context, appWidgetId: Int, walletId: Long) {
        prefs(context).edit()
            .putLong("single_wallet_$appWidgetId", walletId)
            .putLong(KEY_DEFAULT_SINGLE_WALLET, walletId)
            .apply()
    }

    fun setDefaultSingleWalletId(context: Context, walletId: Long) {
        prefs(context).edit()
            .putLong(KEY_DEFAULT_SINGLE_WALLET, walletId)
            .apply()
    }

    fun getSingleWalletId(context: Context, appWidgetId: Int? = null): Long? {
        val p = prefs(context)
        val id = if (appWidgetId != null && p.contains("single_wallet_$appWidgetId")) {
            p.getLong("single_wallet_$appWidgetId", -1L)
        } else {
            p.getLong(KEY_DEFAULT_SINGLE_WALLET, -1L)
        }
        return if (id > 0L) id else null
    }

    fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("overview_wallets_$appWidgetId")
            .remove("single_wallet_$appWidgetId")
            .apply()
    }
}
