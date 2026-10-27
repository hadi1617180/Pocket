package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.AccountingEngine
import com.example.data.WalletBalanceSummary
import com.example.ui.components.IconBadgeCircle
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.PocketTheme
import com.example.ui.theme.TabularMoneyMedium
import kotlinx.coroutines.launch

class WalletWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val providerClassName = manager.getAppWidgetInfo(appWidgetId)?.provider?.className ?: ""
        val isSingleWalletMode = providerClassName.contains("SingleWalletWidgetProvider")

        setContent {
            val scope = rememberCoroutineScope()
            var snapshot by remember { mutableStateOf<WidgetSnapshotData?>(null) }

            LaunchedEffect(Unit) {
                snapshot = PocketWidgetUpdater.loadSnapshot(this@WalletWidgetConfigureActivity)
            }

            PocketTheme {
                val data = snapshot
                if (data == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    WidgetConfigDialogContent(
                        isSingleWalletMode = isSingleWalletMode,
                        summaries = data.activeWalletSummaries,
                        currencySymbol = data.currencySymbol,
                        onConfirmSingle = { selectedId ->
                            WidgetPreferences.saveSingleWalletId(
                                this@WalletWidgetConfigureActivity,
                                appWidgetId,
                                selectedId
                            )
                            scope.launch {
                                val updatedSnapshot = PocketWidgetUpdater.loadSnapshot(this@WalletWidgetConfigureActivity)
                                val views = PocketWidgetUpdater.buildSingleWalletRemoteViews(
                                    this@WalletWidgetConfigureActivity,
                                    appWidgetId,
                                    updatedSnapshot
                                )
                                manager.updateAppWidget(appWidgetId, views)
                                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(Activity.RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        onConfirmOverview = { selectedIds ->
                            WidgetPreferences.saveOverviewWalletIds(
                                this@WalletWidgetConfigureActivity,
                                appWidgetId,
                                selectedIds
                            )
                            scope.launch {
                                val updatedSnapshot = PocketWidgetUpdater.loadSnapshot(this@WalletWidgetConfigureActivity)
                                val views = PocketWidgetUpdater.buildWalletOverviewRemoteViews(
                                    this@WalletWidgetConfigureActivity,
                                    appWidgetId,
                                    updatedSnapshot
                                )
                                manager.updateAppWidget(appWidgetId, views)
                                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(Activity.RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigDialogContent(
    isSingleWalletMode: Boolean,
    summaries: List<WalletBalanceSummary>,
    currencySymbol: String,
    onConfirmSingle: (Long) -> Unit,
    onConfirmOverview: (Set<Long>) -> Unit,
    onCancel: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    var selectedSingleId by remember(summaries) {
        mutableStateOf(summaries.firstOrNull()?.wallet?.id ?: 0L)
    }
    val selectedMultiSet = remember(summaries) {
        mutableStateMapOf<Long, Boolean>().apply {
            summaries.take(5).forEach { put(it.wallet.id, true) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isSingleWalletMode) "Select Wallet for Widget" else "Choose Wallets for Overview Widget",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(summaries, key = { it.wallet.id }) { s ->
                    val w = s.wallet
                    val isSelected = if (isSingleWalletMode) {
                        selectedSingleId == w.id
                    } else {
                        selectedMultiSet[w.id] == true
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (isSingleWalletMode) {
                                    selectedSingleId = w.id
                                } else {
                                    selectedMultiSet[w.id] = !(selectedMultiSet[w.id] ?: false)
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) pocketColors.surfaceSelected else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSingleWalletMode) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedSingleId = w.id }
                                )
                            } else {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { selectedMultiSet[w.id] = it }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconBadgeCircle(iconName = w.iconName, colorHex = w.colorHex, size = 38.dp, iconSize = 18.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = w.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = AccountingEngine.formatMoney(s.currentBalance, currencySymbol, includeDecimals = false),
                                style = TabularMoneyMedium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (isSingleWalletMode) {
                            if (selectedSingleId > 0L) onConfirmSingle(selectedSingleId)
                        } else {
                            val chosen = selectedMultiSet.filterValues { it }.keys
                            onConfirmOverview(chosen)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Save Widget")
                }
            }
        }
    }
}
