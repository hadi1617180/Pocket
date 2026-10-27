package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.*
import com.example.ui.PocketScreen
import com.example.ui.PocketUiState
import com.example.ui.PocketViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.TabularMoneyLarge
import com.example.ui.theme.TabularMoneyMedium
import com.example.ui.theme.TabularMoneySmall
import com.example.notification.PocketNotificationManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WalletsOverviewScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    var editingWallet by remember { mutableStateOf<WalletEntity?>(null) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var quickAdjustWalletSummary by remember { mutableStateOf<WalletBalanceSummary?>(null) }
    var showSavingsGoalDialog by remember { mutableStateOf(false) }
    var editingSavingsGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var fundTargetGoal by remember { mutableStateOf<Pair<SavingsGoalEntity, Boolean>?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }
    val symbol = "৳"
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val density = LocalDensity.current
    val report = uiState.monthlyReport
    val displayedSummaries = uiState.activeWalletSummaries
    val hideBalances = uiState.settings.hideBalancesPrivacy

    // Smooth manual hand-drag reordering state
    var draggedWalletId by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightsPx = remember { mutableStateMapOf<Long, Int>() }
    val localOrderedSummaries = remember { mutableStateListOf<WalletBalanceSummary>() }
    val spacingPx = with(density) { 14.dp.toPx() }

    LaunchedEffect(displayedSummaries, draggedWalletId) {
        if (draggedWalletId == null) {
            localOrderedSummaries.clear()
            localOrderedSummaries.addAll(displayedSummaries)
        }
    }

    val isOverlayActive = isFabExpanded || showWalletDialog || showSavingsGoalDialog || fundTargetGoal != null || quickAdjustWalletSummary != null
    val overviewBlur by animateDpAsState(
        targetValue = if (isOverlayActive) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wallets_overview_blur"
    )

    val fabRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_icon_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blur(overviewBlur)
                .padding(horizontal = 16.dp)
                .testTag("balances_lazy_column"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Top-left Pocket App Logo + "Pocket" app name (tapping opens Settings) + Month & Year button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.navigateTo(PocketScreen.Settings) }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PocketBurgundyLogo(
                            size = 44.dp,
                            onClick = { viewModel.navigateTo(PocketScreen.Settings) },
                            modifier = Modifier.testTag("home_top_left_app_icon")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pocket",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    MonthSelectorPill(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        onPreviousMonth = { viewModel.selectPreviousMonth() },
                        onNextMonth = { viewModel.selectNextMonth() }
                    )
                }
            }

            // 1st Big Box: Total Wallet Balance + Income & Expenses in Green & Red text (no inner boxes)
            // + Uploaded Profile Picture in circular frame and User Name below on the right alignment blank space
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Total wallet balance",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.navigateTo(PocketScreen.BalanceAdjustment())
                                            }
                                            .testTag("total_balance_reconcile_trigger")
                                    )
                                    IconButton(
                                        onClick = {
                                            performPocketHaptic(view)
                                            viewModel.toggleBalancePrivacy()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("balance_privacy_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (hideBalances) "Show balances" else "Hide balances",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (hideBalances) {
                                    Text(
                                        text = "$symbol •••••",
                                        style = TabularMoneyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("wallets_total_balance_text")
                                    )
                                } else {
                                    AnimatedBalanceText(
                                        amount = uiState.totalAvailableBalance,
                                        currencySymbol = symbol,
                                        style = TabularMoneyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.navigateTo(PocketScreen.BalanceAdjustment())
                                            }
                                            .testTag("wallets_total_balance_text")
                                    )
                                }
                                if (uiState.foreignCurrencyTotals.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.testTag("wallets_foreign_currency_totals_column")
                                    ) {
                                        uiState.foreignCurrencyTotals.forEach { (currCode, currBalance) ->
                                            val currSym = AccountingEngine.currencySymbolForCode(currCode)
                                            Text(
                                                text = if (hideBalances) "$currSym •••• $currCode" else "${AccountingEngine.formatMoney(currBalance, currSym)} $currCode",
                                                style = TabularMoneySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("wallets_foreign_total_$currCode")
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Right alignment blank space: Circular framed Profile Picture + User Name below
                            UserProfileBadge(
                                userName = uiState.settings.userName.ifBlank { uiState.googleAccountName ?: "" },
                                profilePhotoUri = uiState.settings.profilePhotoUri.ifBlank { uiState.googleAccountPhotoUrl ?: "" },
                                avatarSize = 58.dp,
                                showNameBelow = true,
                                onClick = { viewModel.navigateTo(PocketScreen.Settings) },
                                modifier = Modifier.testTag("balances_profile_badge")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Income and Expenses directly as Green and Red text (without the 2 inner boxes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Income text (Green)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.navigateTo(
                                            PocketScreen.TransactionHistory(initialTypeFilter = LedgerEntryKind.INCOME)
                                        )
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("home_income_summary_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = pocketColors.incomeText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Income",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pocketColors.incomeText
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (hideBalances) {
                                    Text(
                                        text = "$symbol ••••",
                                        style = TabularMoneyMedium,
                                        color = pocketColors.incomeText
                                    )
                                } else {
                                    AnimatedBalanceText(
                                        amount = report?.totalIncome ?: 0.0,
                                        currencySymbol = symbol,
                                        includeDecimals = false,
                                        style = TabularMoneyMedium,
                                        color = pocketColors.incomeText
                                    )
                                }
                            }

                            // Expenses text (Red)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.navigateTo(
                                            PocketScreen.TransactionHistory(initialTypeFilter = LedgerEntryKind.EXPENSE)
                                        )
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("home_expense_summary_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = pocketColors.expenseText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Expenses",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pocketColors.expenseText
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (hideBalances) {
                                    Text(
                                        text = "$symbol ••••",
                                        style = TabularMoneyMedium,
                                        color = pocketColors.expenseText
                                    )
                                } else {
                                    AnimatedBalanceText(
                                        amount = report?.totalExpenses ?: 0.0,
                                        currencySymbol = symbol,
                                        includeDecimals = false,
                                        style = TabularMoneyMedium,
                                        color = pocketColors.expenseText
                                    )
                                }
                            }
                        }

                        val masterBudget = uiState.settings.masterMonthlyBudget
                        if (masterBudget > 0.0) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val spent = report?.totalExpenses ?: 0.0
                            val progress = (spent / masterBudget).toFloat().coerceIn(0f, 1f)
                            val remainingBudget = (masterBudget - spent).coerceAtLeast(0.0)
                            val calNow = java.util.Calendar.getInstance()
                            val maxDays = calNow.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                            val currentDay = calNow.get(java.util.Calendar.DAY_OF_MONTH)
                            val remainingDays = (maxDays - currentDay + 1).coerceAtLeast(1)
                            val safePerDay = AccountingEngine.roundCurrency(remainingBudget / remainingDays)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.navigateTo(PocketScreen.Reports) }
                                    .padding(top = 4.dp)
                                    .testTag("home_master_budget_bar"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Monthly Budget (${(progress * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (hideBalances) "$symbol •••• / day"
                                        else if (spent > masterBudget) "Over budget by ${AccountingEngine.formatMoney(spent - masterBudget, symbol, includeDecimals = false)}"
                                        else "Safe: ${AccountingEngine.formatMoney(safePerDay, symbol, includeDecimals = false)}/day ($remainingDays d left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (spent > masterBudget) pocketColors.expenseText else MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (spent > masterBudget) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                    trackColor = pocketColors.surfaceElevated
                                )
                            }
                        }
                    }
                }
            }

            // Due Today / Overdue Recurring Bills ("Ask to Confirm": Pay or Remind Tomorrow)
            val dueRecurringItems = uiState.recurringTransactions.filter {
                PocketNotificationManager.shouldShowRecurringNotification(it)
            }
            if (dueRecurringItems.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_due_recurring_banner"),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EventRepeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Recurring Due (${dueRecurringItems.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.navigateTo(PocketScreen.RecurringManagement) },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text("Manage All")
                                }
                            }

                            dueRecurringItems.forEach { rec ->
                                val isFixed = rec.amountMode == RecurringAmountMode.FIXED.name
                                val isIncome = rec.type == TransactionType.INCOME.name
                                val dueFormatted = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(rec.nextDueDate))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = rec.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = if (isFixed) {
                                                        "Fixed subscription • Due $dueFormatted"
                                                    } else {
                                                        "Manual amount input • Due $dueFormatted"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (isFixed && rec.amount > 0.0) {
                                                    AccountingEngine.formatMoney(rec.amount, symbol)
                                                } else {
                                                    "Manual"
                                                },
                                                style = TabularMoneyMedium,
                                                color = if (isIncome) pocketColors.incomeText else pocketColors.expenseText
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.snoozeRecurringToTomorrow(rec.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("recurring_remind_tomorrow_${rec.id}"),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Remind Tomorrow")
                                            }
                                            Button(
                                                onClick = { viewModel.openRecurringPayDialog(rec.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("recurring_pay_button_${rec.id}"),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(if (isIncome) "Receive" else "Pay")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Available Wallets list: Tap once opens Wallet Statement (with Edit & Adjust inside); Long-Press holds & drags smoothly up/down by hand
            items(localOrderedSummaries, key = { it.wallet.id }) { summary ->
                val w = summary.wallet
                val isDragged = draggedWalletId == w.id
                val walletCurrency = w.currency.ifBlank { "BDT" }.uppercase()
                val walletSymbol = AccountingEngine.currencySymbolForCode(walletCurrency)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 10f else 0f)
                        .then(if (!isDragged) Modifier.animateItem() else Modifier)
                        .onSizeChanged { itemHeightsPx[w.id] = it.height }
                        .graphicsLayer {
                            translationY = if (isDragged) draggedOffsetY else 0f
                            scaleX = if (isDragged) 1.03f else 1f
                            scaleY = if (isDragged) 1.03f else 1f
                            shadowElevation = if (isDragged) 20f else 0f
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (draggedWalletId == null) {
                                viewModel.navigateTo(PocketScreen.WalletDetail(w.id))
                            }
                        }
                        .pointerInput(w.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    performPocketHaptic(view, isLongPress = true)
                                    draggedWalletId = w.id
                                    draggedOffsetY = 0f
                                },
                                onDragEnd = {
                                    val orderedIds = localOrderedSummaries.map { it.wallet.id }
                                    draggedWalletId = null
                                    draggedOffsetY = 0f
                                    viewModel.reorderWalletsByIds(orderedIds)
                                },
                                onDragCancel = {
                                    draggedWalletId = null
                                    draggedOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggedOffsetY += dragAmount.y
                                    val currentIndex = localOrderedSummaries.indexOfFirst { it.wallet.id == w.id }
                                    if (currentIndex != -1) {
                                        val cardStep = (itemHeightsPx[w.id] ?: 160).toFloat() + spacingPx
                                        val threshold = cardStep * 0.55f
                                        if (draggedOffsetY > threshold && currentIndex < localOrderedSummaries.lastIndex) {
                                            val moved = localOrderedSummaries.removeAt(currentIndex)
                                            localOrderedSummaries.add(currentIndex + 1, moved)
                                            draggedOffsetY -= cardStep
                                            performPocketHaptic(view)
                                        } else if (draggedOffsetY < -threshold && currentIndex > 0) {
                                            val moved = localOrderedSummaries.removeAt(currentIndex)
                                            localOrderedSummaries.add(currentIndex - 1, moved)
                                            draggedOffsetY += cardStep
                                            performPocketHaptic(view)
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("wallet_overview_item_${w.name}"),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDragged) pocketColors.surfaceSelected else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isDragged) 1.5.dp else 1.dp,
                        if (isDragged) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadgeCircle(
                            iconName = w.iconName,
                            colorHex = w.colorHex,
                            size = 46.dp,
                            iconSize = 22.dp,
                            walletNameHint = w.name
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = w.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (walletCurrency != "BDT") {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = walletCurrency,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (!w.includeInTotalBalance) {
                                Text(
                                    text = "Not in total balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pocketColors.mutedText
                                )
                            }
                            if (w.type.equals("CARD", ignoreCase = true) && (w.creditLimit > 0.0 || w.paymentDueDay in 1..31 || w.statementDay in 1..31)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val usedCredit = kotlin.math.abs(summary.currentBalance)
                                val utilPct = if (w.creditLimit > 0.0) {
                                    ((usedCredit / w.creditLimit) * 100.0).coerceIn(0.0, 999.0)
                                } else 0.0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (w.paymentDueDay in 1..31) {
                                        val daysLeft = PocketNotificationManager.daysUntilDayOfMonth(w.paymentDueDay)
                                        val isSoon = daysLeft <= 5
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSoon) pocketColors.expenseText.copy(alpha = 0.14f) else pocketColors.surfaceElevated,
                                            border = BorderStroke(
                                                0.5.dp,
                                                if (isSoon) pocketColors.expenseText else pocketColors.cardBorder
                                            )
                                        ) {
                                            Text(
                                                text = when (daysLeft) {
                                                    0 -> "Bill Due Today (Day ${w.paymentDueDay})"
                                                    1 -> "Bill Due Tomorrow"
                                                    else -> "Due Day ${w.paymentDueDay} (${daysLeft}d)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSoon) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (w.statementDay in 1..31) {
                                        Text(
                                            text = "Stmt: Day ${w.statementDay}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = pocketColors.mutedText
                                        )
                                    }
                                }
                                if (w.creditLimit > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val barProgress = (usedCredit / w.creditLimit).toFloat().coerceIn(0f, 1f)
                                    Text(
                                        text = if (hideBalances) "Utilization: ${String.format(Locale.ENGLISH, "%.0f%%", utilPct)}"
                                        else "Utilization: ${String.format(Locale.ENGLISH, "%.0f%%", utilPct)} of ${AccountingEngine.formatMoney(w.creditLimit, walletSymbol, includeDecimals = false)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (utilPct >= 80.0) pocketColors.expenseText else pocketColors.mutedText
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { barProgress },
                                        modifier = Modifier
                                            .fillMaxWidth(0.88f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = if (utilPct >= 80.0) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                        trackColor = pocketColors.surfaceElevated
                                    )
                                }
                            }
                        }
                        if (hideBalances) {
                            Text(
                                text = "$walletSymbol ••••",
                                style = TabularMoneyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            AnimatedBalanceText(
                                amount = summary.currentBalance,
                                currencySymbol = walletSymbol,
                                style = TabularMoneyMedium,
                                color = if (summary.currentBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Bottom "+ New Wallet" option after all available wallets
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            editingWallet = null
                            showWalletDialog = true
                        }
                        .testTag("add_wallet_button"),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Savings Goals (Virtual Pots / Wishlists) Section
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_goals_section_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Savings Goals",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = {
                                    editingSavingsGoal = null
                                    showSavingsGoalDialog = true
                                },
                                modifier = Modifier.testTag("add_savings_goal_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Goal")
                            }
                        }

                        if (uiState.savingsGoals.isEmpty()) {
                            Text(
                                text = "No virtual pots yet. Tap + Add Goal for Emergency Fund, New Phone, or Tour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.savingsGoals.forEach { goal ->
                                val progress = if (goal.targetAmount > 0.0) {
                                    (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                                } else 0f
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingSavingsGoal = goal
                                            showSavingsGoalDialog = true
                                        }
                                        .testTag("savings_goal_item_${goal.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = goal.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = if (hideBalances) "$symbol •••• / $symbol ••••"
                                                else "${AccountingEngine.formatMoney(goal.savedAmount, symbol, includeDecimals = false)} / ${AccountingEngine.formatMoney(goal.targetAmount, symbol, includeDecimals = false)}",
                                                style = TabularMoneySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${(progress * 100).toInt()}% saved",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = pocketColors.mutedText
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { fundTargetGoal = goal to false },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text("− Withdraw", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Button(
                                                    onClick = { fundTargetGoal = goal to true },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text("+ Add", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtle lighter scrim over 12.dp blurred background when upward FAB speed-dial or dialog is open
        AnimatedVisibility(
            visible = isFabExpanded || showWalletDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isFabExpanded) isFabExpanded = false
                    }
            )
        }

        // Upward-expanding Liquid FAB Menu + FloatingActionButton (+) at bottom right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = isFabExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = 300f
                    )
                ) + scaleIn(
                    initialScale = 0.75f,
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = 300f
                    )
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidFabActionItem(
                        icon = Icons.Default.AddCircleOutline,
                        label = "Income",
                        accentColor = pocketColors.incomeText,
                        containerColor = pocketColors.overlayIncomeContainer,
                        testTag = "quick_action_income",
                        onClick = {
                            isFabExpanded = false
                            viewModel.navigateTo(PocketScreen.TransactionForm(initialType = TransactionType.INCOME))
                        }
                    )
                    LiquidFabActionItem(
                        icon = Icons.Default.RemoveCircleOutline,
                        label = "Expense",
                        accentColor = pocketColors.expenseText,
                        containerColor = pocketColors.overlayExpenseContainer,
                        testTag = "quick_action_expense",
                        onClick = {
                            isFabExpanded = false
                            viewModel.navigateTo(PocketScreen.TransactionForm(initialType = TransactionType.EXPENSE))
                        }
                    )
                    LiquidFabActionItem(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        label = "Transfer",
                        accentColor = pocketColors.transferText,
                        containerColor = pocketColors.overlayTransferContainer,
                        testTag = "quick_action_transfer",
                        onClick = {
                            isFabExpanded = false
                            viewModel.navigateTo(PocketScreen.TransferForm())
                        }
                    )
                    LiquidFabActionItem(
                        icon = Icons.Default.Handshake,
                        label = "Loans",
                        accentColor = pocketColors.loanText,
                        containerColor = pocketColors.overlayLoanContainer,
                        testTag = "quick_action_loans",
                        onClick = {
                            isFabExpanded = false
                            viewModel.navigateTo(PocketScreen.LoansManagement)
                        }
                    )
                    LiquidFabActionItem(
                        icon = Icons.Default.EventRepeat,
                        label = "Recurring",
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = pocketColors.overlaySurface,
                        testTag = "quick_action_recurring",
                        onClick = {
                            isFabExpanded = false
                            viewModel.navigateTo(PocketScreen.RecurringManagement)
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = { isFabExpanded = !isFabExpanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .testTag("home_fab_record")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Record",
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(fabRotation)
                )
            }
        }
    }

    if (showWalletDialog) {
        val currentEditingBal = editingWallet?.let { w ->
            uiState.walletSummaries.firstOrNull { it.wallet.id == w.id }?.currentBalance
        }
        WalletFormDialog(
            existingWallet = editingWallet,
            allWallets = uiState.wallets,
            currencyCode = "BDT",
            currencySymbol = symbol,
            currentBalance = currentEditingBal,
            viewModel = viewModel,
            onDismiss = { showWalletDialog = false }
        )
    }

    quickAdjustWalletSummary?.let { targetSummary ->
        OneTapAdjustBalanceDialog(
            summary = targetSummary,
            viewModel = viewModel,
            onDismiss = { quickAdjustWalletSummary = null }
        )
    }

    if (showSavingsGoalDialog) {
        SavingsGoalFormDialog(
            existingGoal = editingSavingsGoal,
            currencySymbol = symbol,
            onSave = { goal ->
                viewModel.saveSavingsGoal(goal)
                showSavingsGoalDialog = false
            },
            onDelete = editingSavingsGoal?.let { g ->
                {
                    viewModel.deleteSavingsGoal(g.id)
                    showSavingsGoalDialog = false
                }
            },
            onDismiss = { showSavingsGoalDialog = false }
        )
    }

    fundTargetGoal?.let { (goal, isAdd) ->
        SavingsGoalFundDialog(
            goal = goal,
            isAdd = isAdd,
            wallets = uiState.wallets.filter { !it.isArchived },
            currencySymbol = symbol,
            onConfirm = { amount, transferWalletId ->
                viewModel.addOrWithdrawSavingsGoal(
                    goal = goal,
                    delta = if (isAdd) amount else -amount,
                    transferWalletId = transferWalletId
                )
                fundTargetGoal = null
            },
            onDismiss = { fundTargetGoal = null }
        )
    }
}

@Composable
private fun LiquidFabActionItem(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    containerColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, accentColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
fun WalletDetailScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    walletId: Long
) {
    val summary = uiState.walletSummaries.firstOrNull { it.wallet.id == walletId }
    val pocketColors = LocalPocketColors.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showOneTapAdjustDialog by remember { mutableStateOf(false) }
    var selectedEntryForModal by remember { mutableStateOf<UnifiedLedgerEntry?>(null) }
    var statementSearchQuery by remember(walletId) { mutableStateOf("") }

    if (summary == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            PocketTopBar(title = "Wallet Not Found", onBack = { viewModel.navigateBack() })
        }
        return
    }

    val wallet = summary.wallet
    val symbol = AccountingEngine.currencySymbolForCode(wallet.currency)
    val walletRunningLedger = remember(wallet, uiState.unifiedLedger, uiState.transfers) {
        AccountingEngine.buildWalletRunningBalanceLedger(
            wallet = wallet,
            unifiedLedger = uiState.unifiedLedger,
            transfers = uiState.transfers
        )
    }
    val filteredWalletRunningLedger = remember(walletRunningLedger, statementSearchQuery) {
        val q = statementSearchQuery.trim().lowercase(Locale.ENGLISH)
        if (q.isBlank()) {
            walletRunningLedger
        } else {
            walletRunningLedger.filter { (entry, _) ->
                entry.title.lowercase(Locale.ENGLISH).contains(q) ||
                    entry.categoryName.orEmpty().lowercase(Locale.ENGLISH).contains(q) ||
                    entry.reference.lowercase(Locale.ENGLISH).contains(q) ||
                    entry.tags.lowercase(Locale.ENGLISH).contains(q) ||
                    entry.secondaryWalletName.orEmpty().lowercase(Locale.ENGLISH).contains(q) ||
                    entry.signedAmount.toString().contains(q)
            }
        }
    }
    val detailBlur by animateDpAsState(
        targetValue = if (showEditDialog || showOneTapAdjustDialog || selectedEntryForModal != null) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wallet_detail_blur"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(detailBlur)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = wallet.name,
            onBack = { viewModel.navigateBack() },
            actions = {
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wallet_detail_edit_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit wallet & adjust balance")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Balance & Formula Reconciliation Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadgeCircle(
                                    iconName = wallet.iconName,
                                    colorHex = wallet.colorHex,
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                    walletNameHint = wallet.name
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Current Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("wallet_detail_one_tap_adjust_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit / Adjust", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        AnimatedBalanceText(
                            amount = summary.currentBalance,
                            currencySymbol = symbol,
                            style = TabularMoneyLarge,
                            color = if (summary.currentBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                        )
                        if (wallet.type.equals("CARD", ignoreCase = true) && (wallet.creditLimit > 0.0 || wallet.paymentDueDay in 1..31 || wallet.statementDay in 1..31)) {
                            val usedCredit = kotlin.math.abs(summary.currentBalance)
                            val utilPct = if (wallet.creditLimit > 0.0) {
                                ((usedCredit / wallet.creditLimit) * 100.0).coerceIn(0.0, 999.0)
                            } else 0.0
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pocketColors.surfaceElevated,
                                border = BorderStroke(1.dp, pocketColors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Credit Card Billing & Limit",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (wallet.paymentDueDay in 1..31) {
                                            val daysLeft = PocketNotificationManager.daysUntilDayOfMonth(wallet.paymentDueDay)
                                            Text(
                                                text = if (daysLeft == 0) "Due Today (Day ${wallet.paymentDueDay})"
                                                else "Due Day ${wallet.paymentDueDay} (in ${daysLeft}d)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (daysLeft <= 5) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (wallet.statementDay in 1..31) {
                                        Text(
                                            text = "Statement Date: Day ${wallet.statementDay} of every month",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (wallet.creditLimit > 0.0) {
                                        val availableCredit = (wallet.creditLimit - usedCredit).coerceAtLeast(0.0)
                                        Text(
                                            text = "Credit Limit: ${AccountingEngine.formatMoney(wallet.creditLimit, symbol)} • Available: ${AccountingEngine.formatMoney(availableCredit, symbol)} (${String.format(Locale.ENGLISH, "%.1f%%", utilPct)} used)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        LinearProgressIndicator(
                                            progress = { (usedCredit / wallet.creditLimit).toFloat().coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (utilPct >= 80.0) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surface
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        BalanceBreakdownRow("Opening balance", summary.openingBalance, symbol)
                        BalanceBreakdownRow("+ Income received", summary.totalIncome, symbol, color = pocketColors.incomeText)
                        if (summary.expenseReversals > 0.0) {
                            BalanceBreakdownRow("+ Refund reversals", summary.expenseReversals, symbol, color = pocketColors.incomeText)
                        }
                        BalanceBreakdownRow("− Expenses (incl. fees)", -summary.grossExpenses, symbol, color = pocketColors.expenseText)
                        BalanceBreakdownRow("+ Transfers in", summary.transferIn, symbol, color = pocketColors.transferText)
                        BalanceBreakdownRow("− Transfers out", -summary.transferOut, symbol, color = pocketColors.transferText)
                        if (summary.loanPrincipalIn > 0.0 || summary.loanPrincipalOut > 0.0) {
                            BalanceBreakdownRow("+ Loan principal in", summary.loanPrincipalIn, symbol)
                            BalanceBreakdownRow("− Loan principal out", -summary.loanPrincipalOut, symbol)
                        }
                        if (summary.adjustmentsNet != 0.0) {
                            BalanceBreakdownRow("± Balance adjustments", summary.adjustmentsNet, symbol)
                        }
                    }
                }
            }

            // Quick Actions for this Wallet
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                viewModel.navigateTo(
                                    PocketScreen.TransactionForm(
                                        initialType = TransactionType.INCOME,
                                        preselectedWalletId = wallet.id
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Income")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = {
                                viewModel.navigateTo(
                                    PocketScreen.TransactionForm(
                                        initialType = TransactionType.EXPENSE,
                                        preselectedWalletId = wallet.id
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Expense")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                viewModel.navigateTo(PocketScreen.TransferForm(preselectedSourceWalletId = wallet.id))
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transfer")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showOneTapAdjustDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adjust Balance")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                viewModel.navigateTo(PocketScreen.MonthlyStatement(initialWalletId = wallet.id))
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Statement")
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (statementSearchQuery.isBlank()) {
                            "Running Balance History (${walletRunningLedger.size})"
                        } else {
                            "Running Balance History (${filteredWalletRunningLedger.size} of ${walletRunningLedger.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = statementSearchQuery,
                        onValueChange = { statementSearchQuery = it },
                        placeholder = { Text("Search note, category, TrxID, or amount...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search wallet statement"
                            )
                        },
                        trailingIcon = {
                            if (statementSearchQuery.isNotBlank()) {
                                IconButton(onClick = { statementSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_statement_search_input")
                    )
                }
            }

            if (filteredWalletRunningLedger.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = if (statementSearchQuery.isBlank()) "No wallet movements yet" else "No matching statement entries"
                    )
                }
            } else {
                items(filteredWalletRunningLedger, key = { it.first.uniqueKey }) { (entry, runningBalAfter) ->
                    LedgerEntryRowCard(
                        entry = entry,
                        currencySymbol = symbol,
                        dateFormatPattern = uiState.settings.dateFormatPattern,
                        runningBalanceAfter = runningBalAfter,
                        onClick = { selectedEntryForModal = entry }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        WalletFormDialog(
            existingWallet = wallet,
            allWallets = uiState.wallets,
            currencyCode = "BDT",
            currencySymbol = symbol,
            currentBalance = summary.currentBalance,
            viewModel = viewModel,
            onDismiss = { showEditDialog = false },
            onDeleted = {
                showEditDialog = false
                viewModel.navigateBack()
            }
        )
    }

    if (showOneTapAdjustDialog) {
        OneTapAdjustBalanceDialog(
            summary = summary,
            viewModel = viewModel,
            onDismiss = { showOneTapAdjustDialog = false }
        )
    }

    selectedEntryForModal?.let { entry ->
        LedgerEntryActionDialog(
            entry = entry,
            currencySymbol = symbol,
            onDismiss = { selectedEntryForModal = null },
            onEdit = {
                selectedEntryForModal = null
                when (entry.kind) {
                    LedgerEntryKind.INCOME -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.INCOME, entry.rawId)
                    )
                    LedgerEntryKind.EXPENSE, LedgerEntryKind.EXPENSE_REVERSAL -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.EXPENSE, entry.rawId)
                    )
                    LedgerEntryKind.TRANSFER -> viewModel.navigateTo(
                        PocketScreen.TransferForm(editTransferId = entry.rawId)
                    )
                    LedgerEntryKind.LOAN_PRINCIPAL -> viewModel.navigateTo(PocketScreen.LoansManagement)
                    LedgerEntryKind.ADJUSTMENT -> viewModel.navigateTo(
                        PocketScreen.BalanceAdjustment(preselectedWalletId = wallet.id)
                    )
                }
            },
            onDelete = {
                viewModel.deleteLedgerEntry(entry)
                selectedEntryForModal = null
            }
        )
    }
}

@Composable
fun OneTapAdjustBalanceDialog(
    summary: WalletBalanceSummary,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val wallet = summary.wallet
    val symbol = AccountingEngine.currencySymbolForCode(wallet.currency)
    val calculatedBalance = summary.currentBalance
    var actualBalanceText by remember(summary) {
        mutableStateOf(formatNumericFieldInitialValue(calculatedBalance))
    }
    var noteText by remember { mutableStateOf("") }
    val evaluatedActual = remember(actualBalanceText) {
        evaluateMathExpression(actualBalanceText) ?: actualBalanceText.toDoubleOrNull()
    }
    val diff = if (evaluatedActual != null) {
        AccountingEngine.roundCurrency(evaluatedActual - calculatedBalance)
    } else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text("Adjust ${wallet.name} Balance", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "App Balance: ${AccountingEngine.formatMoney(calculatedBalance, symbol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MathAmountTextField(
                    value = actualBalanceText,
                    onValueChange = { actualBalanceText = sanitizeMathExpressionInput(it) },
                    label = { Text("Actual Balance in Hand ($symbol)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(actualBalanceText) { actualBalanceText = it }
                        .testTag("one_tap_adjust_balance_input")
                )
                MiniCalculatorBar(
                    expressionText = actualBalanceText,
                    currencySymbol = symbol,
                    onExpressionChange = { actualBalanceText = it }
                )
                if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                    Text(
                        text = "Difference: ${AccountingEngine.formatMoney(diff, symbol, showSign = true)}",
                        style = TabularMoneySmall,
                        color = if (diff >= 0) pocketColors.incomeText else pocketColors.expenseText
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = capitalizeFirstLetter(it) },
                    label = { Text("Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                        performPocketHaptic(view)
                        viewModel.createBalanceAdjustment(
                            walletId = wallet.id,
                            actualBalance = evaluatedActual,
                            calculatedBalanceBefore = calculatedBalance,
                            date = System.currentTimeMillis(),
                            reason = "Quick Adjust",
                            note = noteText.trim().ifBlank { "One-tap balance reconciliation" },
                            navigateBackOnSave = false
                        )
                        onDismiss()
                    }
                },
                enabled = evaluatedActual != null && kotlin.math.abs(diff) > 0.004,
                modifier = Modifier.testTag("confirm_one_tap_adjust_button")
            ) {
                Text("Save Balance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SavingsGoalFormDialog(
    existingGoal: SavingsGoalEntity?,
    currencySymbol: String,
    onSave: (SavingsGoalEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    var name by remember(existingGoal) { mutableStateOf(existingGoal?.name ?: "") }
    var targetText by remember(existingGoal) {
        mutableStateOf(formatNumericFieldInitialValue(existingGoal?.targetAmount))
    }
    var savedText by remember(existingGoal) {
        mutableStateOf(formatNumericFieldInitialValue(existingGoal?.savedAmount))
    }
    val targetVal = evaluateMathExpression(targetText) ?: targetText.toDoubleOrNull() ?: 0.0
    val savedVal = evaluateMathExpression(savedText) ?: savedText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = { Text(if (existingGoal != null) "Edit Savings Goal" else "New Savings Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = capitalizeFirstLetter(it) },
                    label = { Text("Goal Name (e.g., Emergency Fund, New Phone)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("savings_goal_name_input")
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = sanitizeMathExpressionInput(it) },
                    label = { Text("Target Amount ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(targetText) { targetText = it }
                        .testTag("savings_goal_target_input")
                )
                OutlinedTextField(
                    value = savedText,
                    onValueChange = { savedText = sanitizeMathExpressionInput(it) },
                    label = { Text("Currently Saved ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(savedText) { savedText = it }
                        .testTag("savings_goal_saved_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && targetVal > 0.0) {
                        onSave(
                            SavingsGoalEntity(
                                id = existingGoal?.id ?: 0L,
                                title = name.trim(),
                                targetAmount = AccountingEngine.roundCurrency(targetVal),
                                savedAmount = AccountingEngine.roundCurrency(savedVal.coerceAtLeast(0.0))
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && targetVal > 0.0,
                modifier = Modifier.testTag("save_savings_goal_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun SavingsGoalFundDialog(
    goal: SavingsGoalEntity,
    isAdd: Boolean,
    wallets: List<WalletEntity>,
    currencySymbol: String,
    onConfirm: (Double, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    var amountText by remember { mutableStateOf("") }
    var transferFromOrToWallet by remember { mutableStateOf(false) }
    var selectedWalletId by remember(wallets) { mutableStateOf(wallets.firstOrNull()?.id) }
    val amountVal = evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text(if (isAdd) "Add to ${goal.name}" else "Withdraw from ${goal.name}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MathAmountTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeMathExpressionInput(it) },
                    label = { Text("Amount ($currencySymbol)") },
                    modifier = Modifier.fillMaxWidth()
                )
                MiniCalculatorBar(
                    expressionText = amountText,
                    currencySymbol = currencySymbol,
                    onExpressionChange = { amountText = it }
                )
                if (wallets.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pocketColors.surfaceElevated,
                        border = BorderStroke(1.dp, pocketColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { transferFromOrToWallet = !transferFromOrToWallet },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isAdd) "Transfer from Wallet" else "Transfer to Wallet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (transferFromOrToWallet) {
                                            if (isAdd) "Also deducts this amount from the selected wallet"
                                            else "Also credits this amount into the selected wallet"
                                        } else {
                                            "Virtual goal progress only (no wallet balance change)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = transferFromOrToWallet,
                                    onCheckedChange = { transferFromOrToWallet = it },
                                    modifier = Modifier.testTag("savings_goal_wallet_transfer_switch")
                                )
                            }
                            if (transferFromOrToWallet) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(wallets, key = { it.id }) { w ->
                                        FilterChip(
                                            selected = selectedWalletId == w.id,
                                            onClick = { selectedWalletId = w.id },
                                            label = { Text(w.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amountVal > 0.0) {
                        onConfirm(amountVal, if (transferFromOrToWallet) selectedWalletId else null)
                    }
                },
                enabled = amountVal > 0.0 && (!transferFromOrToWallet || selectedWalletId != null)
            ) {
                Text(if (isAdd) "Add Funds" else "Withdraw")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BalanceBreakdownRow(
    label: String,
    amount: Double,
    symbol: String,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = AccountingEngine.formatMoney(amount, symbol, showSign = true),
            style = TabularMoneySmall,
            color = color
        )
    }
}

@Composable
fun WalletFormDialog(
    existingWallet: WalletEntity?,
    allWallets: List<WalletEntity>,
    currencyCode: String,
    currencySymbol: String,
    currentBalance: Double? = null,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pocketColors = LocalPocketColors.current
    var name by remember(existingWallet) { mutableStateOf(existingWallet?.name ?: "") }
    var selectedType by remember(existingWallet) {
        mutableStateOf(
            existingWallet?.type?.let { runCatching { WalletType.valueOf(it) }.getOrNull() } ?: WalletType.CASH
        )
    }
    var openingBalanceText by remember(existingWallet) {
        mutableStateOf(formatNumericFieldInitialValue(existingWallet?.openingBalance))
    }
    var actualBalanceText by remember(existingWallet, currentBalance) {
        mutableStateOf(if (currentBalance != null) formatNumericFieldInitialValue(currentBalance) else "")
    }
    val evaluatedActualBalance = remember(actualBalanceText) {
        evaluateMathExpression(actualBalanceText) ?: actualBalanceText.toDoubleOrNull()
    }
    val initialCurrencyUpper = existingWallet?.currency?.trim()?.uppercase()?.ifBlank { "BDT" } ?: "BDT"
    val presetCurrencies = listOf("BDT", "USD", "EUR")
    var selectedCurrencyOption by remember(existingWallet) {
        mutableStateOf(if (initialCurrencyUpper in presetCurrencies) initialCurrencyUpper else "OTHERS")
    }
    var customCurrencyCode by remember(existingWallet) {
        mutableStateOf(if (initialCurrencyUpper !in presetCurrencies) initialCurrencyUpper else "")
    }
    val resolvedCurrencyCode = remember(selectedCurrencyOption, customCurrencyCode) {
        if (selectedCurrencyOption == "OTHERS") {
            customCurrencyCode.trim().uppercase().ifBlank { "GBP" }
        } else {
            selectedCurrencyOption
        }
    }
    val activeCurrencySymbol = remember(resolvedCurrencyCode) {
        AccountingEngine.currencySymbolForCode(resolvedCurrencyCode)
    }
    var selectedIcon by remember(existingWallet) { mutableStateOf(existingWallet?.iconName ?: "CASH") }
    var selectedColor by remember(existingWallet) { mutableStateOf(existingWallet?.colorHex ?: "#F15A24") }
    var includeInTotalBalance by remember(existingWallet) {
        mutableStateOf(
            existingWallet?.includeInTotalBalance
                ?: !(existingWallet?.name?.equals("Savings", ignoreCase = true) == true)
        )
    }
    var showSafeDeleteStep by remember { mutableStateOf(false) }
    var canDirectlyDelete by remember { mutableStateOf(true) }
    val otherWallets = remember(allWallets, existingWallet) {
        allWallets.filter { it.id != existingWallet?.id }
    }
    var migrateToWalletId by remember(otherWallets) {
        mutableStateOf(otherWallets.firstOrNull()?.id)
    }

    var creditLimitText by remember(existingWallet) {
        mutableStateOf(
            if ((existingWallet?.creditLimit ?: 0.0) > 0.0) {
                formatNumericFieldInitialValue(existingWallet?.creditLimit)
            } else ""
        )
    }
    var statementDayText by remember(existingWallet) {
        mutableStateOf(
            if ((existingWallet?.statementDay ?: 0) in 1..31) existingWallet!!.statementDay.toString() else ""
        )
    }
    var paymentDueDayText by remember(existingWallet) {
        mutableStateOf(
            if ((existingWallet?.paymentDueDay ?: 0) in 1..31) existingWallet!!.paymentDueDay.toString() else ""
        )
    }

    // Zero-permission local storage image picker for custom wallet icon (encoded as portable Base64 data URI)
    val customImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveUprightImageFromUri(
                context = context,
                uri = uri,
                subDir = "wallet_icons",
                prefix = "wallet_icon",
                maxDimension = 256,
                compressAsJpeg = true,
                encodeAsDataUri = true
            )?.let { savedPath ->
                selectedIcon = "CUSTOM_URI:$savedPath"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (existingWallet != null) "Edit Wallet" else "New Wallet")
                if (existingWallet != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            viewModel.navigateTo(PocketScreen.WalletDetail(existingWallet.id))
                        }
                    ) {
                        Text("Ledger")
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = capitalizeFirstLetter(it) },
                    label = { Text("Wallet Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_name_input")
                )
                Text("Type", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WalletType.entries) { wt ->
                        FilterChip(
                            selected = selectedType == wt,
                            onClick = { selectedType = wt },
                            label = { Text(wt.label) }
                        )
                    }
                }
                Text("Currency", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("wallet_currency_picker_row")
                ) {
                    items(listOf("BDT" to "BDT (৳)", "USD" to "USD ($)", "EUR" to "EUR (€)", "OTHERS" to "Others")) { (code, label) ->
                        FilterChip(
                            selected = selectedCurrencyOption == code,
                            onClick = { selectedCurrencyOption = code },
                            label = { Text(label) },
                            modifier = Modifier.testTag("wallet_currency_chip_$code")
                        )
                    }
                }
                if (selectedCurrencyOption == "OTHERS") {
                    OutlinedTextField(
                        value = customCurrencyCode,
                        onValueChange = { raw ->
                            customCurrencyCode = raw.filter { it.isLetterOrDigit() }.take(5).uppercase()
                        },
                        label = { Text("Custom Currency Code (e.g., GBP, CAD, AED)") },
                        placeholder = { Text("GBP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_custom_currency_input")
                    )
                }
                if (existingWallet != null && currentBalance != null) {
                    val adjDiff = if (evaluatedActualBalance != null) {
                        AccountingEngine.roundCurrency(evaluatedActualBalance - currentBalance)
                    } else 0.0
                    MathAmountTextField(
                        value = actualBalanceText,
                        onValueChange = { actualBalanceText = sanitizeMathExpressionInput(it) },
                        label = { Text("Adjust Balance ($activeCurrencySymbol $resolvedCurrencyCode)") },
                        placeholder = { Text("0") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(actualBalanceText) { actualBalanceText = it }
                            .testTag("wallet_adjust_balance_input")
                    )
                    MiniCalculatorBar(
                        expressionText = actualBalanceText,
                        currencySymbol = activeCurrencySymbol,
                        onExpressionChange = { actualBalanceText = it }
                    )
                    if (evaluatedActualBalance != null && kotlin.math.abs(adjDiff) > 0.004) {
                        Text(
                            text = "Reconciliation difference: ${AccountingEngine.formatMoney(adjDiff, activeCurrencySymbol, showSign = true)}",
                            style = TabularMoneySmall,
                            color = if (adjDiff >= 0) pocketColors.incomeText else pocketColors.expenseText
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = openingBalanceText,
                        onValueChange = { openingBalanceText = sanitizeNumericInput(it, allowNegative = true) },
                        label = { Text("Opening Balance ($activeCurrencySymbol $resolvedCurrencyCode)") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(openingBalanceText) { openingBalanceText = it }
                            .testTag("wallet_opening_balance_input")
                    )
                }

                if (selectedType == WalletType.CARD) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = sanitizeNumericInput(it, allowNegative = false) },
                        label = { Text("Credit Limit ($activeCurrencySymbol, Optional)") },
                        placeholder = { Text("e.g. 100000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_card_credit_limit_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = statementDayText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(2)
                                val num = digits.toIntOrNull()
                                statementDayText = if (num != null && num > 31) "31" else digits
                            },
                            label = { Text("Statement Day (1-31)") },
                            placeholder = { Text("e.g. 15") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wallet_card_statement_day_input")
                        )
                        OutlinedTextField(
                            value = paymentDueDayText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(2)
                                val num = digits.toIntOrNull()
                                paymentDueDayText = if (num != null && num > 31) "31" else digits
                            },
                            label = { Text("Payment Due Day (1-31)") },
                            placeholder = { Text("e.g. 5") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wallet_card_due_day_input")
                        )
                    }
                }

                // Wallet Icon / Brand Logo Picker + Custom Image Attachment from Local Storage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wallet Icon", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { customImageLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("attach_custom_wallet_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attach Image", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (selectedIcon.startsWith("CUSTOM_URI:")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconBadgeCircle(
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                            size = 42.dp,
                            walletNameHint = name
                        )
                        Text(
                            text = "Custom image attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("wallet_icon_picker_row")
                ) {
                    items(WalletIconPickerOptions, key = { it.key }) { option ->
                        val isChosen = selectedIcon == option.key
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedIcon = option.key
                                    selectedColor = option.defaultColorHex
                                    if (name.isBlank()) {
                                        name = option.label
                                    }
                                    if (existingWallet == null && option.key == "SAVINGS") {
                                        selectedType = WalletType.SAVINGS
                                        includeInTotalBalance = false
                                    }
                                }
                                .testTag("wallet_icon_option_${option.key}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChosen) pocketColors.surfaceSelected else pocketColors.surfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isChosen) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconBadgeCircle(
                                    iconName = option.key,
                                    colorHex = option.defaultColorHex,
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                    walletNameHint = option.label
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Option to include or exclude this wallet's balance in the Total Balance
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pocketColors.surfaceElevated,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeInTotalBalance = !includeInTotalBalance }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add to Total Balance",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!includeInTotalBalance) {
                                    "Excluded from total wallet balance"
                                } else if (resolvedCurrencyCode == "BDT") {
                                    "Included in primary BDT total wallet balance"
                                } else {
                                    "Shown on a separate $resolvedCurrencyCode line under BDT total balance"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeInTotalBalance,
                            onCheckedChange = { includeInTotalBalance = it },
                            modifier = Modifier.testTag("wallet_include_in_total_switch")
                        )
                    }
                }

                if (existingWallet != null && showSafeDeleteStep) {
                    HorizontalDivider()
                    if (canDirectlyDelete) {
                        Text(
                            text = "Delete this empty wallet permanently?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Migrate existing transactions before deleting:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(otherWallets, key = { it.id }) { target ->
                                FilterChip(
                                    selected = migrateToWalletId == target.id,
                                    onClick = { migrateToWalletId = target.id },
                                    label = { Text(target.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showSafeDeleteStep && existingWallet != null) {
                Button(
                    onClick = {
                        if (canDirectlyDelete || migrateToWalletId != null) {
                            viewModel.deleteOrMigrateWallet(
                                walletId = existingWallet.id,
                                migrateToWalletId = if (canDirectlyDelete) null else migrateToWalletId,
                                onDone = {
                                    onDismiss()
                                    onDeleted()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = canDirectlyDelete || migrateToWalletId != null
                ) {
                    Text(if (canDirectlyDelete) "Delete Wallet" else "Migrate & Delete")
                }
            } else {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val openBal = openingBalanceText.toDoubleOrNull() ?: (existingWallet?.openingBalance ?: 0.0)
                            val parsedCreditLimit = if (selectedType == WalletType.CARD) {
                                (creditLimitText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                            } else 0.0
                            val parsedStatementDay = if (selectedType == WalletType.CARD) {
                                (statementDayText.toIntOrNull() ?: 0).coerceIn(0, 31)
                            } else 0
                            val parsedDueDay = if (selectedType == WalletType.CARD) {
                                (paymentDueDayText.toIntOrNull() ?: 0).coerceIn(0, 31)
                            } else 0
                            viewModel.saveWallet(
                                WalletEntity(
                                    id = existingWallet?.id ?: 0L,
                                    name = name.trim(),
                                    type = selectedType.name,
                                    accountHint = "",
                                    openingBalance = AccountingEngine.roundCurrency(openBal),
                                    openingDate = existingWallet?.openingDate ?: System.currentTimeMillis(),
                                    currency = resolvedCurrencyCode,
                                    iconName = selectedIcon,
                                    colorHex = selectedColor,
                                    sortOrder = existingWallet?.sortOrder ?: 0,
                                    isArchived = false,
                                    includeInTotalBalance = includeInTotalBalance,
                                    creditLimit = AccountingEngine.roundCurrency(parsedCreditLimit),
                                    statementDay = parsedStatementDay,
                                    paymentDueDay = parsedDueDay
                                )
                            ) {
                                if (existingWallet != null && currentBalance != null && evaluatedActualBalance != null) {
                                    val diff = AccountingEngine.roundCurrency(evaluatedActualBalance - currentBalance)
                                    if (kotlin.math.abs(diff) > 0.004) {
                                        viewModel.createBalanceAdjustment(
                                            walletId = existingWallet.id,
                                            actualBalance = evaluatedActualBalance,
                                            calculatedBalanceBefore = currentBalance,
                                            date = System.currentTimeMillis(),
                                            reason = "Edit Wallet Adjust",
                                            note = "Adjusted balance in Edit Wallet",
                                            navigateBackOnSave = false
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_wallet_dialog_button")
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existingWallet != null && !showSafeDeleteStep) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                canDirectlyDelete = viewModel.canDirectlyDeleteWallet(existingWallet.id)
                                showSafeDeleteStep = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun BalanceAdjustmentScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    preselectedWalletId: Long?
) {
    val wallets = uiState.activeWalletSummaries
    var selectedWalletId by remember(preselectedWalletId, wallets) {
        mutableStateOf(preselectedWalletId ?: wallets.firstOrNull()?.wallet?.id ?: 0L)
    }
    val selectedSummary = wallets.firstOrNull { it.wallet.id == selectedWalletId }
    var actualBalanceText by remember(selectedSummary) {
        mutableStateOf(formatNumericFieldInitialValue(selectedSummary?.currentBalance))
    }
    val reasons = listOf("Correction", "Missing transaction", "Initial setup", "Other")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var noteText by remember { mutableStateOf("") }

    val symbol = AccountingEngine.currencySymbolForCode(selectedSummary?.wallet?.currency ?: "BDT")
    val pocketColors = LocalPocketColors.current
    val calculatedBalance = selectedSummary?.currentBalance ?: 0.0
    val actualBalance = actualBalanceText.toDoubleOrNull()
    val difference = if (actualBalance != null) {
        AccountingEngine.roundCurrency(actualBalance - calculatedBalance)
    } else 0.0

    val walletMap = remember(uiState.wallets) { uiState.wallets.associateBy { it.id } }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = pocketColors.overlaySurface,
        border = BorderStroke(1.dp, pocketColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            PocketTopBar(
                title = "Reconcile Balance",
                onBack = { viewModel.navigateBack() }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Wallet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.wallet.id }) { summary ->
                            val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                            FilterChip(
                                selected = summary.wallet.id == selectedWalletId,
                                onClick = {
                                    selectedWalletId = summary.wallet.id
                                    actualBalanceText = formatNumericFieldInitialValue(summary.currentBalance)
                                },
                                label = {
                                    Text("${summary.wallet.name} (${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)})")
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = actualBalanceText,
                        onValueChange = { actualBalanceText = sanitizeNumericInput(it, allowNegative = true) },
                        label = { Text("Actual Balance ($symbol)") },
                        placeholder = { Text("0") },
                        textStyle = TabularMoneyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(actualBalanceText) { actualBalanceText = it }
                            .testTag("adjustment_actual_balance_input")
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = pocketColors.overlayCard,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Calculated balance:", style = MaterialTheme.typography.bodyMedium)
                                Text(AccountingEngine.formatMoney(calculatedBalance, symbol), style = TabularMoneySmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Actual balance:", style = MaterialTheme.typography.bodyMedium)
                                Text(AccountingEngine.formatMoney(actualBalance ?: calculatedBalance, symbol), style = TabularMoneySmall)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Difference:", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = AccountingEngine.formatMoney(difference, symbol, showSign = true),
                                    style = TabularMoneyMedium,
                                    color = when {
                                        difference > 0.004 -> pocketColors.incomeText
                                        difference < -0.004 -> pocketColors.expenseText
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Reason", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reasons) { r ->
                            FilterChip(
                                selected = selectedReason == r,
                                onClick = { selectedReason = r },
                                label = { Text(r) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Note (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = {
                            if (selectedWalletId != 0L && actualBalance != null && kotlin.math.abs(difference) > 0.004) {
                                viewModel.createBalanceAdjustment(
                                    walletId = selectedWalletId,
                                    actualBalance = actualBalance,
                                    calculatedBalanceBefore = calculatedBalance,
                                    date = System.currentTimeMillis(),
                                    reason = selectedReason,
                                    note = noteText.trim(),
                                    navigateBackOnSave = false
                                )
                                noteText = ""
                            }
                        },
                        enabled = selectedWalletId != 0L && actualBalance != null && kotlin.math.abs(difference) > 0.004,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_balance_adjustment_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save Adjustment")
                    }
                }

                if (uiState.adjustments.isNotEmpty()) {
                    item {
                        Text("Adjustment History", style = MaterialTheme.typography.titleMedium)
                    }
                    items(uiState.adjustments, key = { it.id }) { adj ->
                        val wName = walletMap[adj.walletId]?.name ?: "Wallet"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = pocketColors.overlayCard,
                            border = BorderStroke(1.dp, pocketColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$wName · ${adj.reason}${if (adj.isReversed) " (Reversed)" else ""}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (adj.note.isNotBlank()) {
                                        Text(adj.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = AccountingEngine.formatMoney(adj.difference, symbol, showSign = true),
                                        style = TabularMoneySmall,
                                        color = if (adj.isReversed) MaterialTheme.colorScheme.onSurfaceVariant
                                        else if (adj.difference >= 0) pocketColors.incomeText else pocketColors.expenseText
                                    )
                                    TextButton(onClick = { viewModel.toggleReverseAdjustment(adj) }) {
                                        Text(if (adj.isReversed) "Restore" else "Reverse")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
