package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showFundBreakdownPopover by remember { mutableStateOf(false) }
    var isFundHeroExpanded by rememberSaveable { mutableStateOf(false) }
    val symbol = "৳"
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val density = LocalDensity.current
    val report = uiState.currentMonthReport ?: uiState.monthlyReport
    val displayedSummaries = uiState.activeWalletSummaries
    val hideBalances = uiState.settings.hideBalancesPrivacy
    val listState = rememberLazyListState()
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    var hasTriggeredPullHaptic by remember { mutableStateOf(false) }
    var isManualPullSync by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCloudSyncing) {
        if (!uiState.isCloudSyncing) {
            isManualPullSync = false
        }
    }
    val pullTriggerThresholdPx = with(density) { 80.dp.toPx() }
    val maxPullOffsetPx = with(density) { 150.dp.toPx() }
    val syncingHoldOffsetPx = with(density) { 54.dp.toPx() }
    val pullNestedScrollConnection = remember(uiState.isCloudSyncing, pullTriggerThresholdPx, maxPullOffsetPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (pullDistancePx > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullDistancePx)
                    val nextPull = (pullDistancePx + consumedY).coerceAtLeast(0f)
                    if (nextPull < pullTriggerThresholdPx) {
                        hasTriggeredPullHaptic = false
                    }
                    pullDistancePx = nextPull
                    return androidx.compose.ui.geometry.Offset(0f, consumedY)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    !uiState.isCloudSyncing
                ) {
                    val resistance = (1f - (pullDistancePx / maxPullOffsetPx).coerceIn(0f, 0.65f)) * 0.68f
                    val nextPull = (pullDistancePx + available.y * resistance).coerceIn(0f, maxPullOffsetPx)
                    if (nextPull >= pullTriggerThresholdPx && !hasTriggeredPullHaptic) {
                        hasTriggeredPullHaptic = true
                        performPocketHaptic(view, hapticType = PocketHapticType.SYNC_THRESHOLD)
                    } else if (nextPull < pullTriggerThresholdPx) {
                        hasTriggeredPullHaptic = false
                    }
                    pullDistancePx = nextPull
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val triggered = pullDistancePx >= pullTriggerThresholdPx && !uiState.isCloudSyncing
                pullDistancePx = 0f
                hasTriggeredPullHaptic = false
                if (triggered) {
                    isManualPullSync = true
                    performPocketHaptic(view, hapticType = PocketHapticType.SUCCESS_MULTI_PULSE)
                    viewModel.syncCloudNow()
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                if (pullDistancePx != 0f) {
                    pullDistancePx = 0f
                    hasTriggeredPullHaptic = false
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    val targetPullOffsetPx = when {
        pullDistancePx > 0f -> pullDistancePx
        isManualPullSync && uiState.isCloudSyncing -> syncingHoldOffsetPx
        else -> 0f
    }
    val animatedPullOffsetPx by animateFloatAsState(
        targetValue = targetPullOffsetPx,
        animationSpec = if (pullDistancePx > 0f) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "wallets_page_pull_offset"
    )

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

    val isOverlayActive = isFabExpanded || showFundBreakdownPopover || showWalletDialog || showSavingsGoalDialog || fundTargetGoal != null || quickAdjustWalletSummary != null
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
            .nestedScroll(pullNestedScrollConnection)
    ) {
        if (animatedPullOffsetPx > 4f || (isManualPullSync && uiState.isCloudSyncing)) {
            val readyToSync = pullDistancePx >= pullTriggerThresholdPx
            val pillProgress = (animatedPullOffsetPx / pullTriggerThresholdPx).coerceIn(0f, 1f)
            val pillSlideOffsetPx = (animatedPullOffsetPx * 0.45f - with(density) { 18.dp.toPx() })
                .coerceAtMost(with(density) { 14.dp.toPx() })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = pillSlideOffsetPx
                        alpha = if (uiState.isCloudSyncing) 1f else (pillProgress * 1.35f).coerceIn(0f, 1f)
                        val scale = if (uiState.isCloudSyncing) 1f else (0.85f + 0.15f * pillProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = pocketColors.surfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (readyToSync || uiState.isCloudSyncing) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            pocketColors.cardBorder
                        }
                    ),
                    modifier = Modifier.testTag("wallets_pull_sync_indicator")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isCloudSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Syncing with Google Cloud...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (readyToSync) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        rotationZ = pillProgress * 180f
                                    }
                            )
                            Text(
                                text = if (readyToSync) "Release to sync with Cloud" else "Pull down to sync",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (readyToSync) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = animatedPullOffsetPx
                }
                .blur(overviewBlur)
                .padding(horizontal = 16.dp)
                .testTag("balances_lazy_column"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Header: Ultra-clean Top-left Pocket App Logo + "Pocket" app name + Top-right circular glass Settings icon button
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
                            size = 34.dp,
                            onClick = { viewModel.navigateTo(PocketScreen.Settings) },
                            modifier = Modifier.testTag("home_top_left_app_icon")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pocket",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!uiState.isSubscriptionActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clickable { viewModel.showUpgradeDialog() }
                                    .testTag("home_tier_badge")
                            ) {
                                Text(
                                    text = " Free ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = uiState.isCloudSyncing && !isManualPullSync,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "bg_sync_spin")
                            val syncRotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 1000, easing = LinearEasing)
                                ),
                                label = "bg_sync_rotation_deg"
                            )
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Background Cloud Syncing",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(syncRotation)
                                    .testTag("home_background_sync_icon")
                            )
                        }

                        Surface(
                            onClick = {
                                performPocketHaptic(view)
                                viewModel.navigateTo(PocketScreen.Settings)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush()),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("wallets_top_right_settings_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1st Big Box: Locked-Height iOS 27 Liquid Glass Horizontal Swipe Pager (Slide 0: Personal Wallet Balance, Slide 1: Fund Wallet Balance)
            item {
                val isFundEnabled = uiState.isFundEnabled
                val heroVirtualPageCount = if (isFundEnabled) 10000 else 1
                val heroInitialPage = if (isFundEnabled) 5000 else 0
                val heroPagerState = rememberPagerState(initialPage = heroInitialPage, pageCount = { heroVirtualPageCount })
                LaunchedEffect(isFundEnabled) {
                    if (isFundEnabled && heroPagerState.currentPage < 100) {
                        heroPagerState.scrollToPage(5000)
                    }
                }
                val pagerCoroutineScope = rememberCoroutineScope()
                val fundWalletsList = remember(uiState.activeWalletSummaries) {
                    uiState.activeWalletSummaries.filter {
                        !it.wallet.isFundWallet && kotlin.math.abs(it.fundBalance) > 0.005
                    }
                }

                val fundArrowRotation by animateFloatAsState(
                    targetValue = if (isFundHeroExpanded) 180f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
                    label = "fund_arrow_rot"
                )

                val compactHeroBaseHeight = 175.dp
                val activeCurrentSlide = if (isFundEnabled) heroPagerState.currentPage % 2 else 0
                val heroPagerHeight by animateDpAsState(
                    targetValue = if (isFundHeroExpanded && activeCurrentSlide == 1) {
                        (compactHeroBaseHeight + (fundWalletsList.size * 28).dp + 16.dp).coerceAtMost(360.dp)
                    } else {
                        compactHeroBaseHeight
                    },
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                    label = "hero_pager_dynamic_height"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val heroBackdropTone = if (pocketColors.isDark) Color(0x85121218) else Color(0x8AFFFFFF)
                        // Dynamic high-blur liquid glass backdrop layer behind Hero card
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(26.dp))
                                .blur(20.dp)
                                .background(heroBackdropTone)
                        )

                        val heroSurfaceColor = remember(uiState.settings.boxColorHex, uiState.settings.boxOpacity, pocketColors.isDark) {
                            computeDockAndHeroSurfaceColor(uiState.settings.boxColorHex, uiState.settings.boxOpacity, pocketColors.isDark)
                        }

                        HorizontalPager(
                            state = heroPagerState,
                            userScrollEnabled = isFundEnabled,
                            pageSpacing = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroPagerHeight)
                                .testTag("home_hero_balance_pager")
                        ) { page ->
                        val activeSlide = if (isFundEnabled) page % 2 else 0
                        if (activeSlide == 0) {
                            // Slide 0: Personal Wallet Balance Hero (Accent Bold Amount + 2-Column Subtitle + 68dp Avatar + Liquid Specular Border)
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(26.dp),
                                color = heroSurfaceColor,
                                border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush(isHighlighted = true))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = if (uiState.isFundEnabled) {
                                                    "Personal Wallet Balance"
                                                } else {
                                                    "Total Wallet Balance"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("total_balance_reconcile_trigger")
                                            )
                                            val heroMainBalance = if (uiState.isFundEnabled) {
                                                uiState.totalPersonalBalance
                                            } else {
                                                uiState.totalAvailableBalance
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (hideBalances) {
                                                    Text(
                                                        text = "$symbol •••••",
                                                        style = TabularMoneyLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.testTag("wallets_total_balance_text")
                                                    )
                                                } else {
                                                    AnimatedBalanceText(
                                                        amount = heroMainBalance,
                                                        currencySymbol = symbol,
                                                        style = TabularMoneyLarge,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.testTag("wallets_total_balance_text")
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        performPocketHaptic(view)
                                                        viewModel.toggleBalancePrivacy()
                                                    },
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .testTag("balance_privacy_toggle_button")
                                                ) {
                                                    Icon(
                                                        imageVector = if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = if (hideBalances) "Show balances" else "Hide balances",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // 2-Column Tabular Subtitle: Total label on left, ৳ amount on right
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "Total",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (uiState.foreignCurrencyTotals.isNotEmpty()) {
                                                        uiState.foreignCurrencyTotals.take(2).forEach { (currCode, _) ->
                                                            Text(
                                                                text = currCode,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = if (hideBalances) "$symbol ••••" else AccountingEngine.formatMoney(uiState.totalAvailableBalance, symbol),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.testTag("wallets_top_fund_total_pill")
                                                    )
                                                    if (uiState.foreignCurrencyTotals.isNotEmpty()) {
                                                        uiState.foreignCurrencyTotals.take(2).forEach { (currCode, currBal) ->
                                                            val currSym = AccountingEngine.currencySymbolForCode(currCode)
                                                            Text(
                                                                text = if (hideBalances) "$currSym ••••" else "${AccountingEngine.formatMoney(currBal, currSym, includeDecimals = false)}",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.testTag("wallets_foreign_total_$currCode")
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        UserProfileBadge(
                                            userName = uiState.settings.userName.ifBlank { uiState.googleAccountName ?: "" },
                                            profilePhotoUri = uiState.settings.profilePhotoUri.ifBlank { uiState.googleAccountPhotoUrl ?: "" },
                                            avatarSize = 68.dp,
                                            showNameBelow = true,
                                            onClick = { viewModel.navigateTo(PocketScreen.Settings) },
                                            modifier = Modifier.testTag("balances_profile_badge")
                                        )
                                    }

                                    // Single-Line Income & Expense Mini-Ticker (+ optional compact budget pill)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.navigateTo(
                                                            PocketScreen.TransactionHistory(initialTypeFilter = LedgerEntryKind.INCOME)
                                                        )
                                                    }
                                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                                                    .testTag("home_income_summary_card"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "↑",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pocketColors.incomeText
                                                )
                                                if (hideBalances) {
                                                    Text(
                                                        text = "$symbol ••••",
                                                        style = TabularMoneySmall,
                                                        color = pocketColors.incomeText
                                                    )
                                                } else {
                                                    AnimatedBalanceText(
                                                        amount = report?.totalIncome ?: 0.0,
                                                        currencySymbol = symbol,
                                                        includeDecimals = false,
                                                        style = TabularMoneySmall,
                                                        color = pocketColors.incomeText
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            )

                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.navigateTo(
                                                            PocketScreen.TransactionHistory(initialTypeFilter = LedgerEntryKind.EXPENSE)
                                                        )
                                                    }
                                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                                                    .testTag("home_expense_summary_card"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "↓",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pocketColors.expenseText
                                                )
                                                if (hideBalances) {
                                                    Text(
                                                        text = "$symbol ••••",
                                                        style = TabularMoneySmall,
                                                        color = pocketColors.expenseText
                                                    )
                                                } else {
                                                    AnimatedBalanceText(
                                                        amount = report?.totalExpenses ?: 0.0,
                                                        currencySymbol = symbol,
                                                        includeDecimals = false,
                                                        style = TabularMoneySmall,
                                                        color = pocketColors.expenseText
                                                    )
                                                }
                                            }
                                        }

                                        val masterBudget = uiState.settings.masterMonthlyBudget
                                        if (masterBudget > 0.0) {
                                            val spent = report?.totalExpenses ?: 0.0
                                            val progress = (spent / masterBudget).toFloat().coerceIn(0f, 1f)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = BorderStroke(0.5.dp, pocketLiquidGlassBorderBrush()),
                                                modifier = Modifier
                                                    .clickable {
                                                        viewModel.resetToCurrentMonth()
                                                        viewModel.navigateTo(PocketScreen.Reports)
                                                    }
                                                    .testTag("home_master_budget_bar")
                                            ) {
                                                Text(
                                                    text = "Budget ${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (spent > masterBudget) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Slide 1: Fund Wallet Balance Hero (Accent Bold Amount + 2-Column Total/Due Subtitle + In-Place Breakdown Toggle)
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(26.dp))
                                    .testTag("auto_summed_fund_wallet_card"),
                                shape = RoundedCornerShape(26.dp),
                                color = heroSurfaceColor,
                                border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush(isHighlighted = true))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Fund Wallet Balance",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (hideBalances) {
                                                    Text(
                                                        text = "$symbol •••••",
                                                        style = TabularMoneyLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (uiState.totalFundBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.testTag("wallets_fund_hero_balance_text")
                                                    )
                                                } else {
                                                    AnimatedBalanceText(
                                                        amount = uiState.totalFundBalance,
                                                        currencySymbol = symbol,
                                                        style = TabularMoneyLarge,
                                                        color = if (uiState.totalFundBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.testTag("wallets_fund_hero_balance_text")
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        performPocketHaptic(view)
                                                        viewModel.toggleBalancePrivacy()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = if (hideBalances) "Show balances" else "Hide balances",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // 2-Column Tabular Subtitle: Total and Borrow Due
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "Total",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "Borrow Due",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (uiState.totalFundBorrowedDue > 0.005) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = if (hideBalances) "$symbol ••••" else AccountingEngine.formatMoney(uiState.totalAvailableBalance, symbol),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = if (hideBalances) "$symbol ••••" else AccountingEngine.formatMoney(uiState.totalFundBorrowedDue, symbol, includeDecimals = false),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = if (uiState.totalFundBorrowedDue > 0.005) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (uiState.totalFundBorrowedDue > 0.005) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        UserProfileBadge(
                                            userName = uiState.settings.userName.ifBlank { uiState.googleAccountName ?: "" },
                                            profilePhotoUri = uiState.settings.profilePhotoUri.ifBlank { uiState.googleAccountPhotoUrl ?: "" },
                                            avatarSize = 68.dp,
                                            showNameBelow = true,
                                            onClick = { viewModel.navigateTo(PocketScreen.Settings) }
                                        )
                                    }

                                    // In-place <Wallet>-Fund breakdown rows when expanded
                                    AnimatedVisibility(
                                        visible = isFundHeroExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (fundWalletsList.isEmpty()) {
                                                Text(
                                                    text = "No active fund balances",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            } else {
                                                fundWalletsList.forEach { item ->
                                                    val itemCurr = item.wallet.currency.ifBlank { "BDT" }
                                                    val itemSym = AccountingEngine.currencySymbolForCode(itemCurr)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${item.wallet.name}-Fund",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (hideBalances) "$itemSym ••••" else AccountingEngine.formatMoney(item.fundBalance, itemSym, includeDecimals = false),
                                                            style = TabularMoneySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (item.fundBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Bottom middle subtle down arrow (v) rotating to up arrow (^) on expand
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(
                                            onClick = {
                                                performPocketHaptic(view)
                                                isFundHeroExpanded = !isFundHeroExpanded
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("fund_hero_expand_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isFundHeroExpanded) "Hide fund breakdown" else "Show fund breakdown",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .graphicsLayer { rotationZ = fundArrowRotation }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // Minimal iOS-style Pager Indicator Dots below Hero Card
                    if (isFundEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("home_hero_pager_indicator")
                        ) {
                            repeat(2) { pageIdx ->
                                val isCurrentPage = activeCurrentSlide == pageIdx
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isCurrentPage) 18.dp else 6.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label = "hero_pager_dot_$pageIdx"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrentPage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .clickable {
                                            pagerCoroutineScope.launch {
                                                val diff = pageIdx - activeCurrentSlide
                                                heroPagerState.animateScrollToPage(
                                                    page = heroPagerState.currentPage + diff,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                        .testTag("hero_pager_dot_$pageIdx")
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
                                val dueFormatted = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(rec.nextDueDate))
                                val recWalletSymbol = rec.defaultWalletId
                                    ?.let { wId -> uiState.wallets.firstOrNull { it.id == wId }?.currency }
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { AccountingEngine.currencySymbolForCode(it) }
                                    ?: symbol
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
                                                    if (hideBalances) "$recWalletSymbol ••••"
                                                    else AccountingEngine.formatMoney(rec.amount, recWalletSymbol)
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
                        if (isDragged) 1.5.dp else 0.5.dp,
                        pocketLiquidGlassBorderBrush(isHighlighted = isDragged)
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
                                if (w.isFundWallet) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = "Fund",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
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
                            val isCashWallet = w.name.trim().equals("Cash", ignoreCase = true)
                            if (uiState.isFundEnabled && !w.isFundWallet && (kotlin.math.abs(summary.fundBalance) > 0.005 || summary.fundBorrowedDue > 0.005)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val subWalletRows = buildList {
                                    if (isCashWallet) {
                                        add(
                                            Triple(
                                                "${w.name}-Total",
                                                if (hideBalances) "$walletSymbol ••••" else AccountingEngine.formatMoney(summary.currentBalance, walletSymbol, includeDecimals = false),
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    } else {
                                        add(
                                            Triple(
                                                "${w.name}-Personal",
                                                if (hideBalances) "$walletSymbol ••••" else AccountingEngine.formatMoney(summary.personalBalance, walletSymbol, includeDecimals = false),
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    add(
                                        Triple(
                                            "${w.name}-Fund",
                                            if (hideBalances) "$walletSymbol ••••" else AccountingEngine.formatMoney(summary.fundBalance, walletSymbol, includeDecimals = false),
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    if (summary.fundBorrowedDue > 0.005) {
                                        add(
                                            Triple(
                                                "Borrowed Due",
                                                if (hideBalances) "$walletSymbol ••••" else AccountingEngine.formatMoney(summary.fundBorrowedDue, walletSymbol, includeDecimals = false),
                                                pocketColors.expenseText
                                            )
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        subWalletRows.forEach { (subLabel, _, _) ->
                                            Text(
                                                text = subLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        subWalletRows.forEach { (_, subAmountText, amountColor) ->
                                            Text(
                                                text = subAmountText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                color = amountColor,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else if (uiState.isFundEnabled && w.isFundWallet) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (hideBalances) {
                                        "Official Spent: $walletSymbol ••••"
                                    } else {
                                        "Official Spent: ${AccountingEngine.formatMoney(summary.fundOfficialSpent, walletSymbol, includeDecimals = false)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (w.type.equals("CARD", ignoreCase = true) && (w.creditLimit > 0.0 || w.paymentDueDay in 1..31 || w.statementDay in 1..31)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val usedCredit = (-summary.currentBalance).coerceAtLeast(0.0)
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
                        // Cash wallet shows Cash Personal as main big amount when Fund is enabled; all other wallets show Total
                        val isCashCard = w.name.trim().equals("Cash", ignoreCase = true)
                        val displayedCardBalance = if (uiState.isFundEnabled && isCashCard && !w.isFundWallet) {
                            summary.personalBalance
                        } else {
                            summary.currentBalance
                        }
                        if (hideBalances) {
                            Text(
                                text = "$walletSymbol ••••",
                                style = TabularMoneyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            AnimatedBalanceText(
                                amount = displayedCardBalance,
                                currencySymbol = walletSymbol,
                                style = TabularMoneyMedium,
                                color = if (displayedCardBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Bottom "+ New Wallet" option after all available wallets
            item {
                val activeNonFundWallets = uiState.wallets.filter { !it.isArchived && !it.isFundWallet }
                val defaultWalletsCount = activeNonFundWallets.count { SubscriptionManager.isStarterWalletName(it.name) }
                val customWalletsCount = SubscriptionManager.countCustomWallets(activeNonFundWallets)
                val canAddWallet = SubscriptionManager.canAddDefaultWallet(
                    defaultWalletsCount,
                    uiState.settings.subscriptionTier,
                    uiState.settings.subscriptionExpiresAt
                ) || SubscriptionManager.canAddCustomWallet(
                    customWalletsCount,
                    uiState.settings.subscriptionTier,
                    uiState.settings.subscriptionExpiresAt
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (!canAddWallet) {
                                viewModel.showUpgradeDialog("Free tier allows up to 3 default wallets and 1 custom wallet. Upgrade to Pocket Premium for unlimited wallets.")
                            } else {
                                editingWallet = null
                                showWalletDialog = true
                            }
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
                            imageVector = if (canAddWallet) Icons.Default.Add else Icons.Default.Lock,
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
                val canAddGoal = SubscriptionManager.canAddSavingsGoal(uiState.savingsGoals.size, uiState.settings.subscriptionTier, uiState.settings.subscriptionExpiresAt)
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
                            .padding(
                                horizontal = 16.dp,
                                vertical = if (uiState.savingsGoals.isEmpty()) 8.dp else 16.dp
                            ),
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
                            FilledTonalButton(
                                onClick = {
                                    if (!canAddGoal) {
                                        viewModel.showUpgradeDialog("Free tier allows up to 3 savings targets. Upgrade to Pocket Premium for unlimited savings targets.")
                                    } else {
                                        editingSavingsGoal = null
                                        showSavingsGoalDialog = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_savings_goal_button")
                            ) {
                                Icon(if (canAddGoal) Icons.Default.Add else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Goal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (uiState.savingsGoals.isNotEmpty()) {
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

            // Generous bottom scroll clearance so Savings Goals and Add Goal scroll fully above the FAB and dock
            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // Subtle lighter scrim over 12.dp blurred background when upward FAB speed-dial, Fund breakdown popover, or dialog is open
        AnimatedVisibility(
            visible = isFabExpanded || showFundBreakdownPopover || showWalletDialog || showSavingsGoalDialog || fundTargetGoal != null || quickAdjustWalletSummary != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isFabExpanded) isFabExpanded = false
                        if (showFundBreakdownPopover) showFundBreakdownPopover = false
                    }
            )
        }

        // Floating iOS 27 Liquid Glass Popover for <Wallet>-Fund Breakdown
        AnimatedVisibility(
            visible = showFundBreakdownPopover,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 210.dp, start = 20.dp, end = 20.dp)
        ) {
            val fundWalletsList = uiState.activeWalletSummaries.filter {
                !it.wallet.isFundWallet && kotlin.math.abs(it.fundBalance) > 0.005
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = pocketColors.overlaySurface,
                border = BorderStroke(0.8.dp, pocketLiquidGlassBorderBrush(isHighlighted = true)),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fund_breakdown_glass_popover")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fund Wallet Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showFundBreakdownPopover = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close breakdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (fundWalletsList.isEmpty()) {
                        Text(
                            text = "No active <Wallet>-Fund balances yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            fundWalletsList.forEach { ws ->
                                val wsSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showFundBreakdownPopover = false
                                            viewModel.navigateTo(PocketScreen.WalletDetail(ws.wallet.id))
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ws.wallet.name}-Fund",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (hideBalances) "$wsSym ••••" else AccountingEngine.formatMoney(ws.fundBalance, wsSym),
                                        style = TabularMoneySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = pocketColors.cardBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showFundBreakdownPopover = false
                                viewModel.navigateTo(PocketScreen.WalletDetail(VIRTUAL_FUND_WALLET_ID))
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("fund_popover_open_statement_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open Combined Fund Statement",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Upward-expanding Liquid FAB Menu + FloatingActionButton (+) at bottom right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 104.dp),
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
                            viewModel.navigateTo(PocketScreen.LoansManagement(openCreateDialog = true))
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
            uiState = uiState,
            currencySymbol = symbol,
            onConfirm = { amount, transferWalletId, fundMode ->
                viewModel.addOrWithdrawSavingsGoal(
                    goal = goal,
                    delta = if (isAdd) amount else -amount,
                    transferWalletId = transferWalletId,
                    fundMode = fundMode
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
    if (walletId == VIRTUAL_FUND_WALLET_ID) {
        CombinedFundDetailScreen(uiState = uiState, viewModel = viewModel)
        return
    }
    val summary = uiState.walletSummaries.firstOrNull { it.wallet.id == walletId }
    val pocketColors = LocalPocketColors.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showOneTapAdjustDialog by remember { mutableStateOf(false) }
    var showReturnToFundDialog by remember { mutableStateOf(false) }
    var selectedEntryForModal by remember { mutableStateOf<UnifiedLedgerEntry?>(null) }
    var statementSearchQuery by remember(walletId) { mutableStateOf("") }

    var selectedSubWalletTab by remember(walletId) { mutableStateOf(0) } // 0 = All, 1 = Personal, 2 = Fund
    var showSubWalletAdjustDialog by remember { mutableStateOf<Boolean?>(null) } // true = Fund, false = Personal
    var showClearFundDialog by remember { mutableStateOf(false) }
    var showFormulaBreakdown by remember(walletId) { mutableStateOf(false) }

    if (summary == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            PocketTopBar(title = "Wallet Not Found", onBack = { viewModel.navigateBack() })
        }
        return
    }

    val wallet = summary.wallet
    val symbol = AccountingEngine.currencySymbolForCode(wallet.currency)
    val walletRunningLedger = remember(wallet, uiState.unifiedLedger, uiState.transfers, selectedSubWalletTab, uiState.isFundEnabled) {
        AccountingEngine.buildWalletRunningBalanceLedger(
            wallet = wallet,
            unifiedLedger = uiState.unifiedLedger,
            transfers = uiState.transfers,
            subWalletMode = if (uiState.isFundEnabled && !wallet.isFundWallet) selectedSubWalletTab else 0
        )
    }
    val subWalletFilteredLedger = remember(walletRunningLedger, selectedSubWalletTab, uiState.isFundEnabled, wallet.id) {
        if (!uiState.isFundEnabled || selectedSubWalletTab == 0) {
            walletRunningLedger
        } else {
            walletRunningLedger.filter { (entry, _) ->
                val isFundEntry = when (entry.kind) {
                    LedgerEntryKind.INCOME, LedgerEntryKind.EXPENSE, LedgerEntryKind.EXPENSE_REVERSAL ->
                        entry.tags.contains("FUND_IN", ignoreCase = true) ||
                            entry.tags.contains("FUND_EXPENSE", ignoreCase = true) ||
                            entry.tags.contains("FUND_BORROW", ignoreCase = true)
                    LedgerEntryKind.TRANSFER -> {
                        val isFundTransfer = entry.note.contains("[FUND_TRANSFER]", ignoreCase = true)
                        val isFundBorrow = entry.note.contains("[FUND_BORROW]", ignoreCase = true)
                        val isFundSettle = entry.note.contains("[FUND_SETTLE]", ignoreCase = true)
                        val isFundReturn = entry.note.contains("[FUND_RETURN]", ignoreCase = true)
                        val isFundTopup = entry.note.contains("[FUND_TOPUP]", ignoreCase = true)
                        when {
                            isFundTransfer -> true
                            isFundBorrow || isFundSettle -> entry.walletId == wallet.id // source is Fund, dest is Personal
                            isFundReturn || isFundTopup -> entry.secondaryWalletId == wallet.id // source is Personal, dest is Fund
                            else -> false
                        }
                    }
                    LedgerEntryKind.LOAN_PRINCIPAL ->
                        entry.note.contains("[FUND_LOAN]", ignoreCase = true) ||
                            entry.note.contains("[FUND_LOAN_BORROW]", ignoreCase = true)
                    LedgerEntryKind.ADJUSTMENT -> entry.note.contains("-Fund", ignoreCase = true)
                }
                val isFundBorrowExpense = ((entry.kind == LedgerEntryKind.EXPENSE || entry.kind == LedgerEntryKind.EXPENSE_REVERSAL) &&
                    entry.tags.contains("FUND_BORROW", ignoreCase = true)) ||
                    (entry.kind == LedgerEntryKind.LOAN_PRINCIPAL && entry.note.contains("[FUND_LOAN_BORROW]", ignoreCase = true))
                val isSameWalletDualTransfer = entry.kind == LedgerEntryKind.TRANSFER &&
                    entry.walletId == wallet.id && entry.secondaryWalletId == wallet.id &&
                    (entry.note.contains("[FUND_BORROW]", ignoreCase = true) ||
                        entry.note.contains("[FUND_SETTLE]", ignoreCase = true) ||
                        entry.note.contains("[FUND_RETURN]", ignoreCase = true) ||
                        entry.note.contains("[FUND_TOPUP]", ignoreCase = true))
                when (selectedSubWalletTab) {
                    2 -> isFundEntry || isSameWalletDualTransfer
                    1 -> !isFundEntry || isSameWalletDualTransfer || isFundBorrowExpense
                    else -> true
                }
            }
        }
    }
    val filteredWalletRunningLedger = remember(subWalletFilteredLedger, statementSearchQuery) {
        val rawQ = statementSearchQuery.trim().lowercase(Locale.ENGLISH)
        val normQ = PocketLocalizer.normalizeDigits(rawQ)
        if (rawQ.isBlank()) {
            subWalletFilteredLedger
        } else {
            subWalletFilteredLedger.filter { (entry, _) ->
                val catName = entry.categoryName.orEmpty()
                val secWallet = entry.secondaryWalletName.orEmpty()
                entry.title.lowercase(Locale.ENGLISH).contains(rawQ) ||
                    catName.lowercase(Locale.ENGLISH).contains(rawQ) ||
                    PocketLocalizer.translate(catName).lowercase(Locale.ENGLISH).contains(rawQ) ||
                    entry.reference.lowercase(Locale.ENGLISH).contains(rawQ) ||
                    entry.tags.lowercase(Locale.ENGLISH).contains(rawQ) ||
                    secWallet.lowercase(Locale.ENGLISH).contains(rawQ) ||
                    PocketLocalizer.translate(secWallet).lowercase(Locale.ENGLISH).contains(rawQ) ||
                    kotlin.math.abs(entry.signedAmount).toString().contains(normQ)
            }
        }
    }
    val anyDetailDialogOpen = showEditDialog || showOneTapAdjustDialog || showSubWalletAdjustDialog != null || showClearFundDialog || showReturnToFundDialog || selectedEntryForModal != null
    val detailBlur by animateDpAsState(
        targetValue = if (anyDetailDialogOpen) 14.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "wallet_detail_blur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(detailBlur)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = wallet.name,
            onBack = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Balance & Sub-Wallet Selector Card
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val showSubWallets = uiState.isFundEnabled && !wallet.isFundWallet
                        if (showSubWallets) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0 to "All (Total)",
                                    1 to "${wallet.name}-Personal",
                                    2 to "${wallet.name}-Fund"
                                ).forEach { (tabIdx, tabTitle) ->
                                    val isTabSelected = selectedSubWalletTab == tabIdx
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedSubWalletTab = tabIdx }
                                            .testTag("wallet_detail_subwallet_tab_$tabIdx"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isTabSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else pocketColors.surfaceElevated,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isTabSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                        )
                                    ) {
                                        Text(
                                            text = tabTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isTabSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val headlineBalance = when {
                            !showSubWallets -> summary.currentBalance
                            selectedSubWalletTab == 1 -> summary.personalBalance
                            selectedSubWalletTab == 2 -> summary.fundBalance
                            else -> summary.currentBalance
                        }
                        val headlineLabel = when {
                            !showSubWallets -> "Current Balance"
                            selectedSubWalletTab == 1 -> "${wallet.name}-Personal Balance"
                            selectedSubWalletTab == 2 -> "${wallet.name}-Fund Balance"
                            else -> "Total Balance (${wallet.name})"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconBadgeCircle(
                                    iconName = if (selectedSubWalletTab == 2) "FUND" else wallet.iconName,
                                    colorHex = if (selectedSubWalletTab == 2) "#0EA5E9" else wallet.colorHex,
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                    walletNameHint = wallet.name
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = headlineLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    when {
                                        showSubWallets && selectedSubWalletTab == 1 -> showSubWalletAdjustDialog = false
                                        showSubWallets && selectedSubWalletTab == 2 -> showSubWalletAdjustDialog = true
                                        else -> showEditDialog = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("wallet_detail_one_tap_adjust_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        showSubWallets && selectedSubWalletTab == 1 -> "Adjust Personal"
                                        showSubWallets && selectedSubWalletTab == 2 -> "Adjust Fund"
                                        else -> "Edit / Adjust"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        val isBalanceHidden = uiState.settings.hideBalancesPrivacy
                        if (isBalanceHidden) {
                            Text(
                                text = "$symbol ••••",
                                style = TabularMoneyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            AnimatedBalanceText(
                                amount = headlineBalance,
                                currencySymbol = symbol,
                                style = TabularMoneyLarge,
                                color = if (headlineBalance < 0) pocketColors.expenseText else if (selectedSubWalletTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (showSubWallets) {
                            val effectiveDue = maxOf(summary.fundBorrowedDue, uiState.totalFundBorrowedDue)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pocketColors.surfaceElevated,
                                border = BorderStroke(1.dp, pocketColors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = if (isBalanceHidden) {
                                                    "${wallet.name}-Personal: $symbol •••• • ${wallet.name}-Fund: $symbol ••••"
                                                } else {
                                                    "${wallet.name}-Personal: ${AccountingEngine.formatMoney(summary.personalBalance, symbol, includeDecimals = false)} • ${wallet.name}-Fund: ${AccountingEngine.formatMoney(summary.fundBalance, symbol, includeDecimals = false)}"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (selectedSubWalletTab == 2 || summary.fundOfficialSpent > 0.005 || summary.fundBorrowedDue > 0.005) {
                                                Text(
                                                    text = if (isBalanceHidden) {
                                                        "Official Spent: $symbol •••• • Borrowed Due: $symbol ••••"
                                                    } else {
                                                        "Official Spent: ${AccountingEngine.formatMoney(summary.fundOfficialSpent, symbol, includeDecimals = false)} • Borrowed Due: ${AccountingEngine.formatMoney(summary.fundBorrowedDue, symbol, includeDecimals = false)}"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (summary.fundBorrowedDue > 0.005) pocketColors.expenseText else pocketColors.mutedText
                                                )
                                            }
                                        }
                                        if (effectiveDue > 0.005 || selectedSubWalletTab == 2) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { showReturnToFundDialog = true },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .testTag("return_to_fund_button")
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Return to Fund", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    if (selectedSubWalletTab == 2 && (kotlin.math.abs(summary.fundBalance) > 0.005 || summary.fundOfficialSpent > 0.005 || summary.fundBorrowedDue > 0.005)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showSubWalletAdjustDialog = true },
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Edit ${wallet.name}-Fund", style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (kotlin.math.abs(summary.fundBalance) > 0.005) {
                                                OutlinedButton(
                                                    onClick = { showClearFundDialog = true },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp)
                                                        .testTag("clear_fund_subwallet_button"),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = pocketColors.expenseText)
                                                ) {
                                                    Text("Clear Fund to 0", style = MaterialTheme.typography.labelSmall)
                                                }
                                            } else if (summary.fundOfficialSpent > 0.005 || summary.fundBorrowedDue > 0.005) {
                                                OutlinedButton(
                                                    onClick = { viewModel.resetSettledFundCycleStats(wallet.id) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp)
                                                        .testTag("reset_fund_cycle_stats_button"),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Reset Cycle Stats", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (wallet.type.equals("CARD", ignoreCase = true) && (wallet.creditLimit > 0.0 || wallet.paymentDueDay in 1..31 || wallet.statementDay in 1..31)) {
                            val usedCredit = if (summary.currentBalance < 0.0) kotlin.math.abs(summary.currentBalance) else 0.0
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
                                        val limitDisplay = if (isBalanceHidden) "$symbol ••••" else AccountingEngine.formatMoney(wallet.creditLimit, symbol)
                                        val availDisplay = if (isBalanceHidden) "$symbol ••••" else AccountingEngine.formatMoney(availableCredit, symbol)
                                        Text(
                                            text = "Credit Limit: $limitDisplay • Available: $availDisplay (${String.format(Locale.ENGLISH, "%.1f%%", utilPct)} used)",
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFormulaBreakdown = !showFormulaBreakdown }
                                .padding(vertical = 4.dp)
                                .testTag("wallet_detail_toggle_breakdown_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    showSubWallets && selectedSubWalletTab == 1 -> "${wallet.name}-Personal Formula Breakdown"
                                    showSubWallets && selectedSubWalletTab == 2 -> "${wallet.name}-Fund Formula Breakdown"
                                    else -> "Balance Formula Breakdown"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showFormulaBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showFormulaBreakdown) "Hide breakdown" else "Show breakdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(visible = showFormulaBreakdown) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (showSubWallets && selectedSubWalletTab == 2) {
                                    BalanceBreakdownRow("Opening Fund balance", summary.openingFundBalance, symbol, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Fund income received", summary.fundIncomeIn, symbol, color = pocketColors.incomeText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Fund transfers in", summary.fundTransferIn, symbol, color = pocketColors.transferText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Borrowed returned in", summary.fundReturnedTotal, symbol, color = pocketColors.incomeText, hideBalance = isBalanceHidden)
                                    if (summary.fundLoanIn > 0.0 || summary.fundLoanOut > 0.0) {
                                        BalanceBreakdownRow("+ Fund loan principal in", summary.fundLoanIn, symbol, hideBalance = isBalanceHidden)
                                        BalanceBreakdownRow("− Fund loan principal out", -summary.fundLoanOut, symbol, hideBalance = isBalanceHidden)
                                    }
                                    BalanceBreakdownRow("− Official Fund expenses", -summary.fundOfficialSpent, symbol, color = pocketColors.expenseText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("− Personal borrowed out", -summary.fundBorrowedTotal, symbol, color = pocketColors.expenseText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("− Fund transfers out", -summary.fundTransferOut, symbol, color = pocketColors.transferText, hideBalance = isBalanceHidden)
                                } else if (showSubWallets && selectedSubWalletTab == 1) {
                                    val personalOpening = AccountingEngine.roundCurrency(summary.openingBalance - summary.openingFundBalance)
                                    val personalIncome = AccountingEngine.roundCurrency(summary.totalIncome - summary.fundIncomeIn)
                                    val personalLoanIn = AccountingEngine.roundCurrency(summary.loanPrincipalIn - summary.fundLoanIn)
                                    val personalLoanOut = AccountingEngine.roundCurrency(summary.loanPrincipalOut - summary.fundLoanOut)
                                    BalanceBreakdownRow("Opening Personal balance", personalOpening, symbol, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Personal income received", personalIncome, symbol, color = pocketColors.incomeText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("− Personal expenses & net transfers", AccountingEngine.roundCurrency(summary.personalBalance - personalOpening - personalIncome - personalLoanIn + personalLoanOut - summary.adjustmentsNet), symbol, color = pocketColors.expenseText, hideBalance = isBalanceHidden)
                                    if (personalLoanIn > 0.0 || personalLoanOut > 0.0) {
                                        BalanceBreakdownRow("+ Personal loan principal in", personalLoanIn, symbol, hideBalance = isBalanceHidden)
                                        BalanceBreakdownRow("− Personal loan principal out", -personalLoanOut, symbol, hideBalance = isBalanceHidden)
                                    }
                                    if (summary.adjustmentsNet != 0.0) {
                                        BalanceBreakdownRow("± Balance adjustments", summary.adjustmentsNet, symbol, hideBalance = isBalanceHidden)
                                    }
                                } else {
                                    BalanceBreakdownRow("Opening balance", summary.openingBalance, symbol, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Income received", summary.totalIncome, symbol, color = pocketColors.incomeText, hideBalance = isBalanceHidden)
                                    if (summary.expenseReversals > 0.0) {
                                        BalanceBreakdownRow("+ Refund reversals", summary.expenseReversals, symbol, color = pocketColors.incomeText, hideBalance = isBalanceHidden)
                                    }
                                    BalanceBreakdownRow("− Expenses (incl. fees)", -summary.grossExpenses, symbol, color = pocketColors.expenseText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("+ Transfers in", summary.transferIn, symbol, color = pocketColors.transferText, hideBalance = isBalanceHidden)
                                    BalanceBreakdownRow("− Transfers out", -summary.transferOut, symbol, color = pocketColors.transferText, hideBalance = isBalanceHidden)
                                    if (summary.loanPrincipalIn > 0.0 || summary.loanPrincipalOut > 0.0) {
                                        BalanceBreakdownRow("+ Loan principal in", summary.loanPrincipalIn, symbol, hideBalance = isBalanceHidden)
                                        BalanceBreakdownRow("− Loan principal out", -summary.loanPrincipalOut, symbol, hideBalance = isBalanceHidden)
                                    }
                                    if (summary.adjustmentsNet != 0.0) {
                                        BalanceBreakdownRow("± Balance adjustments", summary.adjustmentsNet, symbol, hideBalance = isBalanceHidden)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (statementSearchQuery.isBlank()) {
                            "Running Balance History (${subWalletFilteredLedger.size})"
                        } else {
                            "Running Balance History (${filteredWalletRunningLedger.size} of ${subWalletFilteredLedger.size})"
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
                val isBalanceHidden = uiState.settings.hideBalancesPrivacy
                items(filteredWalletRunningLedger, key = { it.first.uniqueKey }) { (entry, runningBalAfter) ->
                    LedgerEntryRowCard(
                        entry = entry,
                        currencySymbol = symbol,
                        runningBalanceAfter = if (isBalanceHidden) null else runningBalAfter,
                        onClick = { selectedEntryForModal = entry }
                    )
                }
            }
        }
    }
    if (anyDetailDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
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

    showSubWalletAdjustDialog?.let { isFundSub ->
        SubWalletAdjustDialog(
            summary = summary,
            isFundSubWallet = isFundSub,
            viewModel = viewModel,
            onDismiss = { showSubWalletAdjustDialog = null }
        )
    }

    if (showClearFundDialog) {
        ClearFundSubWalletDialog(
            summary = summary,
            viewModel = viewModel,
            onDismiss = { showClearFundDialog = false }
        )
    }

    if (showReturnToFundDialog) {
        ReturnToFundDialog(
            defaultDestFundWallet = summary,
            fundWallets = uiState.fundEnabledSummaries.ifEmpty { listOf(summary) },
            allActiveWallets = uiState.activeWalletSummaries,
            totalBorrowedDue = maxOf(summary.fundBorrowedDue, uiState.totalFundBorrowedDue),
            viewModel = viewModel,
            onDismiss = { showReturnToFundDialog = false }
        )
    }

    selectedEntryForModal?.let { entry ->
        LedgerEntryActionDialog(
            entry = entry,
            currencySymbol = symbol,
            onDismiss = { selectedEntryForModal = null },
            onEdit = {
                selectedEntryForModal = null
                val rawTx = if (entry.kind == LedgerEntryKind.INCOME || entry.kind == LedgerEntryKind.EXPENSE || entry.kind == LedgerEntryKind.EXPENSE_REVERSAL) {
                    uiState.transactions.firstOrNull { it.id == entry.rawId }
                } else null
                when {
                    rawTx?.transferId != null -> viewModel.navigateTo(
                        PocketScreen.TransferForm(editTransferId = rawTx.transferId)
                    )
                    rawTx?.parentTransactionId != null -> {
                        val parentTx = uiState.transactions.firstOrNull { it.id == rawTx.parentTransactionId }
                        val parentType = if (parentTx?.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE
                        viewModel.navigateTo(
                            PocketScreen.TransactionForm(parentType, rawTx.parentTransactionId)
                        )
                    }
                    entry.kind == LedgerEntryKind.INCOME -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.INCOME, entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.EXPENSE || entry.kind == LedgerEntryKind.EXPENSE_REVERSAL -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.EXPENSE, entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.TRANSFER -> viewModel.navigateTo(
                        PocketScreen.TransferForm(editTransferId = entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.LOAN_PRINCIPAL -> viewModel.navigateTo(PocketScreen.LoansManagement())
                    entry.kind == LedgerEntryKind.ADJUSTMENT -> {
                        if (selectedSubWalletTab == 1) {
                            showSubWalletAdjustDialog = false
                        } else if (selectedSubWalletTab == 2) {
                            showSubWalletAdjustDialog = true
                        } else {
                            showOneTapAdjustDialog = true
                        }
                    }
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
fun SubWalletSelectorCards(
    walletSummary: WalletBalanceSummary?,
    selectedIsFund: Boolean,
    onSelectSubWallet: (Boolean) -> Unit,
    testTagPrefix: String = "form",
    hideBalances: Boolean = false
) {
    if (walletSummary == null) return
    val pocketColors = LocalPocketColors.current
    val walletName = walletSummary.wallet.name
    val symbol = AccountingEngine.currencySymbolForCode(walletSummary.wallet.currency)
    val personalAmt = walletSummary.personalBalance
    val fundAmt = walletSummary.fundBalance

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pocketColors.surfaceElevated.copy(alpha = 0.55f),
        border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_subwallet_selector_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                false to "$walletName-Personal",
                true to "$walletName-Fund"
            ).forEach { (isFundCard, cardTitle) ->
                val isSelected = selectedIsFund == isFundCard
                val cardAmount = if (isFundCard) fundAmt else personalAmt
                val accentColor = if (isFundCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSubWallet(isFundCard) }
                        .testTag("${testTagPrefix}_subwallet_${if (isFundCard) "fund" else "personal"}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    border = if (isSelected) {
                        BorderStroke(0.8.dp, pocketLiquidGlassBorderBrush(isHighlighted = true))
                    } else {
                        null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = cardTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (hideBalances) "$symbol ••••" else AccountingEngine.formatMoney(cardAmount, symbol, includeDecimals = false),
                                style = TabularMoneySmall,
                                color = if (cardAmount < 0) pocketColors.expenseText else if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClearFundSubWalletDialog(
    summary: WalletBalanceSummary,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val wallet = summary.wallet
    val symbol = AccountingEngine.currencySymbolForCode(wallet.currency)
    val fundBalanceFormatted = AccountingEngine.formatMoney(summary.fundBalance, symbol, includeDecimals = false)
    var deductFromTotalWallet by remember { mutableStateOf(true) }
    var resetCycleStats by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clear ${wallet.name}-Fund ($fundBalanceFormatted)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "How should the remaining $fundBalanceFormatted in ${wallet.name}-Fund be handled?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                listOf(
                    true to Pair(
                        "Deduct from ${wallet.name} (Settled / Returned)",
                        "Fund money left the wallet or was returned to authority. ${wallet.name}-Personal stays unchanged."
                    ),
                    false to Pair(
                        "Move to ${wallet.name}-Personal",
                        "Keep total ${wallet.name} balance unchanged and shift $fundBalanceFormatted into ${wallet.name}-Personal."
                    )
                ).forEach { (isDeduct, labels) ->
                    val isSelected = deductFromTotalWallet == isDeduct
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { deductFromTotalWallet = isDeduct }
                            .testTag("clear_fund_option_${if (isDeduct) "deduct" else "personal"}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else pocketColors.surfaceElevated,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { deductFromTotalWallet = isDeduct },
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = labels.first,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = labels.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { resetCycleStats = !resetCycleStats }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = resetCycleStats,
                        onCheckedChange = { resetCycleStats = it }
                    )
                    Text(
                        text = "Also reset Official Spent & Borrowed stats for a new Fund cycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.clearWalletFundSubWallet(
                        walletId = wallet.id,
                        deductFromTotalWallet = deductFromTotalWallet,
                        resetCycleStats = resetCycleStats
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = pocketColors.expenseText),
                modifier = Modifier.testTag("confirm_clear_fund_subwallet_button")
            ) {
                Text("Clear Fund to 0")
            }
        },
        dismissButton = null
    )
}

@Composable
fun SubWalletAdjustDialog(
    summary: WalletBalanceSummary,
    isFundSubWallet: Boolean,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val wallet = summary.wallet
    val symbol = AccountingEngine.currencySymbolForCode(wallet.currency)
    val subWalletTitle = if (isFundSubWallet) "${wallet.name}-Fund" else "${wallet.name}-Personal"
    val calculatedBalance = if (isFundSubWallet) summary.fundBalance else summary.personalBalance
    var actualBalanceText by remember(summary, isFundSubWallet) {
        mutableStateOf(formatNumericFieldInitialValue(calculatedBalance))
    }
    var selectedReconcileReason by remember(summary, isFundSubWallet) { mutableStateOf("Missing") }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adjust $subWalletTitle", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_subwallet_adjust_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current $subWalletTitle Balance: ${AccountingEngine.formatMoney(calculatedBalance, symbol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MathAmountTextField(
                    value = actualBalanceText,
                    onValueChange = { actualBalanceText = sanitizeMathExpressionInput(it) },
                    label = { Text("Actual $subWalletTitle Balance ($symbol)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(actualBalanceText) { actualBalanceText = it }
                        .testTag("subwallet_adjust_balance_input")
                )
                MiniCalculatorBar(
                    expressionText = actualBalanceText,
                    currencySymbol = symbol,
                    onExpressionChange = { actualBalanceText = it }
                )
                Text(
                    text = "Reconciliation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Missing", "No source", "Other").forEach { option ->
                        FilterChip(
                            selected = selectedReconcileReason == option,
                            onClick = { selectedReconcileReason = option },
                            label = { Text(option) }
                        )
                    }
                }
                if (selectedReconcileReason == "Other") {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                    Text(
                        text = "Difference: ${AccountingEngine.formatMoney(diff, symbol, showSign = true)}",
                        style = TabularMoneySmall,
                        color = if (diff >= 0) pocketColors.incomeText else pocketColors.expenseText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                        performPocketHaptic(view)
                        val resolvedReason = if (selectedReconcileReason == "Other") {
                            noteText.trim().ifBlank { "Other" }
                        } else {
                            selectedReconcileReason
                        }
                        viewModel.adjustSubWalletBalance(
                            walletId = wallet.id,
                            isFundSubWallet = isFundSubWallet,
                            actualSubBalance = evaluatedActual,
                            calculatedSubBalanceBefore = calculatedBalance,
                            date = System.currentTimeMillis(),
                            reason = resolvedReason,
                            note = if (selectedReconcileReason == "Other" && noteText.isNotBlank()) {
                                "$subWalletTitle: ${noteText.trim()}"
                            } else {
                                "$subWalletTitle Reconciliation ($resolvedReason)"
                            }
                        )
                        onDismiss()
                    }
                },
                enabled = evaluatedActual != null && kotlin.math.abs(diff) > 0.004,
                modifier = Modifier.testTag("confirm_subwallet_adjust_button")
            ) {
                Text("Save $subWalletTitle")
            }
        },
        dismissButton = null
    )
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
    var selectedReconcileReason by remember(summary) { mutableStateOf("Missing") }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adjust ${wallet.name} Balance", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_one_tap_adjust_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                    label = { Text("Adjust Current Balance ($symbol)") },
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
                Text(
                    text = "Reconciliation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Missing", "No source", "Other").forEach { option ->
                        FilterChip(
                            selected = selectedReconcileReason == option,
                            onClick = { selectedReconcileReason = option },
                            label = { Text(option) }
                        )
                    }
                }
                if (selectedReconcileReason == "Other") {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Reconciliation Note") },
                        placeholder = { Text("Reason for balance adjustment") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                    Text(
                        text = "Difference: ${AccountingEngine.formatMoney(diff, symbol, showSign = true)}",
                        style = TabularMoneySmall,
                        color = if (diff >= 0) pocketColors.incomeText else pocketColors.expenseText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (evaluatedActual != null && kotlin.math.abs(diff) > 0.004) {
                        performPocketHaptic(view)
                        val resolvedReason = if (selectedReconcileReason == "Other") {
                            noteText.trim().ifBlank { "Other" }
                        } else {
                            selectedReconcileReason
                        }
                        viewModel.createBalanceAdjustment(
                            walletId = wallet.id,
                            actualBalance = evaluatedActual,
                            calculatedBalanceBefore = calculatedBalance,
                            date = System.currentTimeMillis(),
                            reason = resolvedReason,
                            note = if (selectedReconcileReason == "Other" && noteText.isNotBlank()) {
                                noteText.trim()
                            } else {
                                "Reconciliation: $resolvedReason"
                            },
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
        dismissButton = null
    )
}

@Composable
fun CombinedFundDetailScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val pocketColors = LocalPocketColors.current
    val symbol = uiState.settings.currencySymbol
    val isBalanceHidden = uiState.settings.hideBalancesPrivacy
    var showReturnToFundDialog by remember { mutableStateOf(false) }
    var selectedEntryForModal by remember { mutableStateOf<UnifiedLedgerEntry?>(null) }

    val fundWallets = uiState.fundEnabledSummaries
    val fundLedgerEntries = remember(uiState.unifiedLedger) {
        uiState.unifiedLedger.filter { entry ->
            entry.tags.contains("FUND_IN", ignoreCase = true) ||
                entry.tags.contains("FUND_EXPENSE", ignoreCase = true) ||
                entry.tags.contains("FUND_BORROW", ignoreCase = true) ||
                entry.note.contains("[FUND_TRANSFER]", ignoreCase = true) ||
                entry.note.contains("[FUND_BORROW]", ignoreCase = true) ||
                entry.note.contains("[FUND_SETTLE]", ignoreCase = true) ||
                entry.note.contains("[FUND_RETURN]", ignoreCase = true) ||
                entry.note.contains("[FUND_TOPUP]", ignoreCase = true) ||
                entry.note.contains("[FUND_LOAN]", ignoreCase = true) ||
                entry.note.contains("[FUND_LOAN_BORROW]", ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            PocketTopBar(
                title = "Fund",
                onBack = { viewModel.navigateBack() }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Card: Combined Fund Balance, Official Spent, Personal Borrowed Due, & Return to Fund
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadgeCircle(
                                        iconName = "FUND",
                                        colorHex = "#0EA5E9",
                                        size = 38.dp,
                                        iconSize = 18.dp,
                                        walletNameHint = "Fund"
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Available Combined Fund",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Auto-summed across ${fundWallets.size} wallet(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = pocketColors.mutedText
                                        )
                                    }
                                }
                                Button(
                                    onClick = { showReturnToFundDialog = true },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("return_to_fund_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Return to Fund", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (isBalanceHidden) {
                                Text(
                                    text = "$symbol ••••",
                                    style = TabularMoneyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                AnimatedBalanceText(
                                    amount = uiState.totalFundBalance,
                                    currencySymbol = symbol,
                                    style = TabularMoneyLarge,
                                    color = if (uiState.totalFundBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Official Spent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isBalanceHidden) "$symbol ••••"
                                            else AccountingEngine.formatMoney(uiState.totalFundOfficialSpent, symbol, includeDecimals = false),
                                            style = TabularMoneyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (uiState.totalFundBorrowedDue > 0.005) pocketColors.expenseText.copy(alpha = 0.5f) else pocketColors.cardBorder
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Personal Borrowed Due",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (uiState.totalFundBorrowedDue > 0.005) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isBalanceHidden) "$symbol ••••"
                                            else AccountingEngine.formatMoney(uiState.totalFundBorrowedDue, symbol, includeDecimals = false),
                                            style = TabularMoneyMedium,
                                            color = if (uiState.totalFundBorrowedDue > 0.005) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Quick actions for Fund
                            val defaultFundWalletId = fundWallets.firstOrNull()?.wallet?.id
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.navigateTo(
                                            PocketScreen.TransactionForm(
                                                initialType = TransactionType.EXPENSE,
                                                preselectedWalletId = defaultFundWalletId
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Fund Expense", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.navigateTo(
                                            PocketScreen.TransferForm(
                                                preselectedSourceWalletId = defaultFundWalletId
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Transfer Fund", style = MaterialTheme.typography.labelSmall)
                                }
                                if (uiState.totalFundOfficialSpent > 0.005 || uiState.totalFundBorrowedDue > 0.005) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetSettledFundCycleStats(null) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("combined_fund_reset_stats_button")
                                    ) {
                                        Text("Reset Stats", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Per-Wallet Fund Breakdown
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Sub-Wallet Fund Breakdown (Tap any wallet to edit/clear its Fund)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            fundWallets.forEach { ws ->
                                val wsSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.navigateTo(PocketScreen.WalletDetail(ws.wallet.id))
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${ws.wallet.name}-Fund",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = if (isBalanceHidden) {
                                                    "Total ${ws.wallet.name}: $wsSym •••• • ${ws.wallet.name}-Personal: $wsSym ••••"
                                                } else {
                                                    "Total ${ws.wallet.name}: ${AccountingEngine.formatMoney(ws.currentBalance, wsSym, includeDecimals = false)} • ${ws.wallet.name}-Personal: ${AccountingEngine.formatMoney(ws.personalBalance, wsSym, includeDecimals = false)}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = if (isBalanceHidden) "$wsSym ••••"
                                            else AccountingEngine.formatMoney(ws.fundBalance, wsSym, includeDecimals = false),
                                            style = TabularMoneyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Fund Activity History
                item {
                    Text(
                        text = "Fund Activity History (${fundLedgerEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (fundLedgerEntries.isEmpty()) {
                    item {
                        EmptyStateBox(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "No Fund transactions recorded yet"
                        )
                    }
                } else {
                    items(fundLedgerEntries, key = { it.uniqueKey }) { entry ->
                        LedgerEntryRowCard(
                            entry = entry,
                            currencySymbol = symbol,
                            runningBalanceAfter = null,
                            onClick = { selectedEntryForModal = entry }
                        )
                    }
                }
            }
        }
    }

    if (showReturnToFundDialog) {
        ReturnToFundDialog(
            defaultDestFundWallet = fundWallets.firstOrNull { it.fundBorrowedDue > 0.005 } ?: fundWallets.firstOrNull(),
            fundWallets = fundWallets,
            allActiveWallets = uiState.activeWalletSummaries,
            totalBorrowedDue = uiState.totalFundBorrowedDue,
            viewModel = viewModel,
            onDismiss = { showReturnToFundDialog = false }
        )
    }

    selectedEntryForModal?.let { entry ->
        LedgerEntryActionDialog(
            entry = entry,
            currencySymbol = symbol,
            onDismiss = { selectedEntryForModal = null },
            onEdit = {
                selectedEntryForModal = null
                val rawTx = if (entry.kind == LedgerEntryKind.INCOME || entry.kind == LedgerEntryKind.EXPENSE || entry.kind == LedgerEntryKind.EXPENSE_REVERSAL) {
                    uiState.transactions.firstOrNull { it.id == entry.rawId }
                } else null
                when {
                    rawTx?.transferId != null -> viewModel.navigateTo(
                        PocketScreen.TransferForm(editTransferId = rawTx.transferId)
                    )
                    rawTx?.parentTransactionId != null -> {
                        val parentTx = uiState.transactions.firstOrNull { it.id == rawTx.parentTransactionId }
                        val parentType = if (parentTx?.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE
                        viewModel.navigateTo(
                            PocketScreen.TransactionForm(parentType, rawTx.parentTransactionId)
                        )
                    }
                    entry.kind == LedgerEntryKind.INCOME -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.INCOME, entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.EXPENSE || entry.kind == LedgerEntryKind.EXPENSE_REVERSAL -> viewModel.navigateTo(
                        PocketScreen.TransactionForm(TransactionType.EXPENSE, entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.TRANSFER -> viewModel.navigateTo(
                        PocketScreen.TransferForm(editTransferId = entry.rawId)
                    )
                    entry.kind == LedgerEntryKind.LOAN_PRINCIPAL -> viewModel.navigateTo(PocketScreen.LoansManagement())
                    else -> {}
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
fun ReturnToFundDialog(
    defaultDestFundWallet: WalletBalanceSummary?,
    fundWallets: List<WalletBalanceSummary>,
    allActiveWallets: List<WalletBalanceSummary>,
    totalBorrowedDue: Double,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    var destFundWalletId by remember(defaultDestFundWallet, fundWallets) {
        mutableStateOf(
            defaultDestFundWallet?.wallet?.id
                ?: fundWallets.firstOrNull()?.wallet?.id
                ?: allActiveWallets.firstOrNull()?.wallet?.id
                ?: 0L
        )
    }
    var sourceWalletId by remember(allActiveWallets, destFundWalletId) {
        val destWalletObj = allActiveWallets.firstOrNull { it.wallet.id == destFundWalletId }
        val defaultSrc = when {
            destWalletObj != null && !destWalletObj.wallet.isFundWallet && destWalletObj.personalBalance > 0.005 -> destWalletObj.wallet.id
            else -> allActiveWallets.firstOrNull { !it.wallet.isFundWallet && it.wallet.id != destFundWalletId }?.wallet?.id
                ?: allActiveWallets.firstOrNull { !it.wallet.isFundWallet }?.wallet?.id
                ?: allActiveWallets.firstOrNull()?.wallet?.id
                ?: 0L
        }
        mutableStateOf(defaultSrc)
    }
    val destSummary = allActiveWallets.firstOrNull { it.wallet.id == destFundWalletId } ?: defaultDestFundWallet
    val symbol = AccountingEngine.currencySymbolForCode(destSummary?.wallet?.currency ?: "BDT")
    var amountText by remember(totalBorrowedDue) {
        mutableStateOf(
            if (totalBorrowedDue > 0.005) formatNumericFieldInitialValue(totalBorrowedDue) else ""
        )
    }
    var noteText by remember { mutableStateOf("") }
    val amountVal = evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
    val selectedSource = allActiveWallets.firstOrNull { it.wallet.id == sourceWalletId }
    val targetFundChoices = if (fundWallets.isNotEmpty()) fundWallets else allActiveWallets

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Return to Fund", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_return_to_fund_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Personal Borrowed Due: ${AccountingEngine.formatMoney(totalBorrowedDue, symbol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (totalBorrowedDue > 0.005) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "From Wallet (Personal Money)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allActiveWallets, key = { "src_${it.wallet.id}" }) { ws ->
                        val wsSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                        val availDisplay = if (ws.hasVirtualFund) ws.personalBalance else ws.currentBalance
                        FilterChip(
                            selected = sourceWalletId == ws.wallet.id,
                            onClick = { sourceWalletId = ws.wallet.id },
                            label = {
                                Text("${ws.wallet.name} (${AccountingEngine.formatMoney(availDisplay, wsSym, includeDecimals = false)})")
                            },
                            modifier = Modifier.testTag("return_to_fund_source_${ws.wallet.name}")
                        )
                    }
                }
                if (targetFundChoices.size > 1) {
                    Text(
                        text = "Into Wallet's Fund",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(targetFundChoices, key = { "dst_${it.wallet.id}" }) { ws ->
                            val wsSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                            FilterChip(
                                selected = destFundWalletId == ws.wallet.id,
                                onClick = { destFundWalletId = ws.wallet.id },
                                label = {
                                    Text("${ws.wallet.name} Fund (${AccountingEngine.formatMoney(ws.fundBalance, wsSym, includeDecimals = false)})")
                                },
                                modifier = Modifier.testTag("return_to_fund_dest_${ws.wallet.name}")
                            )
                        }
                    }
                }
                if (totalBorrowedDue > 0.005) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                amountText = formatNumericFieldInitialValue(
                                    AccountingEngine.roundCurrency(totalBorrowedDue / 2.0)
                                )
                            },
                            label = { Text("50% Return") }
                        )
                        AssistChip(
                            onClick = {
                                amountText = formatNumericFieldInitialValue(
                                    AccountingEngine.roundCurrency(totalBorrowedDue)
                                )
                            },
                            label = { Text("100% Full Due") }
                        )
                    }
                }
                MathAmountTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeMathExpressionInput(it) },
                    label = { Text("Return Amount ($symbol)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(amountText) { amountText = it }
                        .testTag("return_to_fund_amount_input")
                )
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
                    if (sourceWalletId != 0L && destFundWalletId != 0L && amountVal > 0.0) {
                        performPocketHaptic(view)
                        val dstName = destSummary?.wallet?.name ?: "Fund"
                        val cleanUserNote = noteText.trim()
                        val finalNote = if (cleanUserNote.isNotBlank()) {
                            "[FUND_RETURN] $cleanUserNote"
                        } else {
                            "[FUND_RETURN] Returned personal borrow to $dstName Fund"
                        }
                        viewModel.saveTransfer(
                            transfer = TransferEntity(
                                sourceWalletId = sourceWalletId,
                                destinationWalletId = destFundWalletId,
                                principalAmount = AccountingEngine.roundCurrency(amountVal),
                                date = System.currentTimeMillis(),
                                note = finalNote
                            ),
                            sourceWalletName = selectedSource?.wallet?.name ?: "Wallet",
                            destWalletName = dstName,
                            navigateBackOnSave = false
                        )
                        onDismiss()
                    }
                },
                enabled = sourceWalletId != 0L && destFundWalletId != 0L && amountVal > 0.0,
                modifier = Modifier.testTag("confirm_return_to_fund_button")
            ) {
                Text("Return Money")
            }
        },
        dismissButton = null
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
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (existingGoal != null) "Edit Savings Goal" else "New Savings Goal",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_savings_goal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
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
        dismissButton = if (onDelete != null) {
            {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        } else null
    )
}

@Composable
private fun SavingsGoalFundDialog(
    goal: SavingsGoalEntity,
    isAdd: Boolean,
    uiState: PocketUiState,
    currencySymbol: String,
    onConfirm: (Double, Long?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val activeWallets = uiState.activeWalletSummaries
    var amountText by remember { mutableStateOf("") }
    // Enabled by default so Wallet + Personal, Fund, and Borrow options are immediately visible
    var transferFromOrToWallet by remember { mutableStateOf(activeWallets.isNotEmpty()) }
    var selectedWalletId by remember(activeWallets, uiState.settings.defaultWalletId) {
        mutableStateOf(
            uiState.settings.defaultWalletId?.takeIf { id -> activeWallets.any { it.wallet.id == id } }
                ?: activeWallets.firstOrNull()?.wallet?.id
        )
    }
    var goalFundMode by remember { mutableStateOf("PERSONAL") } // "PERSONAL", "FUND_EXPENSE", "FUND_BORROW", "FUND_IN"
    val selectedWalletSummary = remember(activeWallets, selectedWalletId) {
        activeWallets.firstOrNull { it.wallet.id == selectedWalletId }
    }
    val selectedWallet = selectedWalletSummary?.wallet
    val isFundSubWalletSelected = goalFundMode == "FUND_EXPENSE" || goalFundMode == "FUND_BORROW" || goalFundMode == "FUND_IN"
    val activeSymbol = if (transferFromOrToWallet && selectedWallet != null) {
        AccountingEngine.currencySymbolForCode(selectedWallet.currency)
    } else {
        currencySymbol
    }
    val amountVal = evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
    val exceedsSavedAmount = !isAdd && amountVal > (goal.savedAmount + 0.005)

    val availableSubWalletBalance = remember(selectedWalletSummary, uiState.isFundEnabled, goalFundMode) {
        when {
            selectedWalletSummary == null -> 0.0
            selectedWalletSummary.wallet.isFundWallet -> selectedWalletSummary.currentBalance
            uiState.isFundEnabled && (goalFundMode == "FUND_EXPENSE" || goalFundMode == "FUND_BORROW") ->
                selectedWalletSummary.fundBalance
            uiState.isFundEnabled ->
                selectedWalletSummary.personalBalance
            else ->
                selectedWalletSummary.currentBalance
        }
    }
    val willBecomeNegative = isAdd &&
        transferFromOrToWallet &&
        selectedWalletSummary != null &&
        amountVal > 0.0 &&
        (availableSubWalletBalance - amountVal) < -0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isAdd) "Add to ${goal.name}" else "Withdraw from ${goal.name}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_savings_goal_fund_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isAdd) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available in ${goal.name}: ${AccountingEngine.formatMoney(goal.savedAmount, activeSymbol)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (goal.savedAmount > 0.0) {
                            TextButton(
                                onClick = { amountText = formatNumericFieldInitialValue(goal.savedAmount) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Max", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                MathAmountTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeMathExpressionInput(it) },
                    label = { Text("Amount ($activeSymbol)") },
                    modifier = Modifier.fillMaxWidth()
                )
                MiniCalculatorBar(
                    expressionText = amountText,
                    currencySymbol = activeSymbol,
                    onExpressionChange = { amountText = it }
                )
                if (exceedsSavedAmount) {
                    Text(
                        text = "Cannot withdraw more than saved balance (${AccountingEngine.formatMoney(goal.savedAmount, activeSymbol)}).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (activeWallets.isNotEmpty()) {
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
                                    items(activeWallets, key = { it.wallet.id }) { summary ->
                                        val w = summary.wallet
                                        val chipSym = AccountingEngine.currencySymbolForCode(w.currency)
                                        val chipBal = if (uiState.isFundEnabled && w.name.trim().equals("Cash", ignoreCase = true) && !w.isFundWallet) {
                                            summary.personalBalance
                                        } else {
                                            summary.currentBalance
                                        }
                                        FilterChip(
                                            selected = selectedWalletId == w.id,
                                            onClick = { selectedWalletId = w.id },
                                            label = {
                                                Text(
                                                    if (uiState.settings.hideBalancesPrivacy) {
                                                        w.name
                                                    } else {
                                                        "${w.name} (${AccountingEngine.formatMoney(chipBal, chipSym, includeDecimals = false)})"
                                                    }
                                                )
                                            },
                                            modifier = Modifier.testTag("savings_goal_wallet_chip_${w.name}")
                                        )
                                    }
                                }

                                if (uiState.isFundEnabled && selectedWalletSummary != null && !selectedWalletSummary.wallet.isFundWallet) {
                                    SubWalletSelectorCards(
                                        walletSummary = selectedWalletSummary,
                                        selectedIsFund = isFundSubWalletSelected,
                                        onSelectSubWallet = { chooseFund ->
                                            goalFundMode = if (!chooseFund) {
                                                "PERSONAL"
                                            } else if (!isAdd) {
                                                "FUND_IN"
                                            } else {
                                                if (goalFundMode == "FUND_BORROW") "FUND_BORROW" else "FUND_EXPENSE"
                                            }
                                        },
                                        testTagPrefix = "savings_goal_fund",
                                        hideBalances = uiState.settings.hideBalancesPrivacy
                                    )

                                    AnimatedVisibility(visible = isAdd && isFundSubWalletSelected) {
                                        val wName = selectedWalletSummary.wallet.name
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "$wName-Fund Deposit Source",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val isOfficial = goalFundMode == "FUND_EXPENSE"
                                                val isBorrow = goalFundMode == "FUND_BORROW"
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { goalFundMode = "FUND_EXPENSE" }
                                                        .testTag("savings_goal_fund_official_chip"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isOfficial) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isOfficial) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Official Fund Expense",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "Deducts from $wName-Fund",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { goalFundMode = "FUND_BORROW" }
                                                        .testTag("savings_goal_fund_borrow_chip"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isBorrow) pocketColors.expenseText.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isBorrow) pocketColors.expenseText else pocketColors.cardBorder
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Borrow for Personal",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isBorrow) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "Adds to Borrowed Due",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isBorrow) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (willBecomeNegative && uiState.settings.warnOnNegativeBalance) {
                                    val wName = selectedWalletSummary?.wallet?.name ?: "Wallet"
                                    val bucketLabel = when {
                                        uiState.isFundEnabled && isFundSubWalletSelected -> "$wName-Fund"
                                        uiState.isFundEnabled -> "$wName-Personal"
                                        else -> wName
                                    }
                                    Text(
                                        text = "Note: Deducting ${AccountingEngine.formatMoney(amountVal, activeSymbol)} will make $bucketLabel negative (${AccountingEngine.formatMoney(availableSubWalletBalance - amountVal, activeSymbol)}).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pocketColors.expenseText
                                    )
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
                    if (amountVal > 0.0 && !exceedsSavedAmount) {
                        val effectiveFundMode = when {
                            !transferFromOrToWallet || !uiState.isFundEnabled -> "PERSONAL"
                            selectedWalletSummary?.wallet?.isFundWallet == true -> if (isAdd) "FUND_EXPENSE" else "FUND_IN"
                            else -> goalFundMode
                        }
                        onConfirm(
                            amountVal,
                            if (transferFromOrToWallet) selectedWalletId else null,
                            effectiveFundMode
                        )
                    }
                },
                enabled = amountVal > 0.0 && !exceedsSavedAmount && (!transferFromOrToWallet || selectedWalletId != null),
                modifier = Modifier.testTag("confirm_savings_goal_fund_button")
            ) {
                Text(if (isAdd) "Add Funds" else "Withdraw")
            }
        },
        dismissButton = null
    )
}

@Composable
private fun BalanceBreakdownRow(
    label: String,
    amount: Double,
    symbol: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    hideBalance: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (hideBalance) "$symbol ••••" else AccountingEngine.formatMoney(amount, symbol, showSign = true),
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
    var selectedReconcileReason by remember(existingWallet) { mutableStateOf("Missing") }
    var customReconcileNote by remember(existingWallet) { mutableStateOf("") }
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
    val isFundFeatureActive = viewModel.uiState.value.isFundEnabled
    val selectableWalletTypes = remember(existingWallet) {
        if (existingWallet?.isFundWallet == true) {
            WalletType.entries
        } else {
            WalletType.entries.filter { it != WalletType.FUND }
        }
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
                Text(
                    if (existingWallet != null) "Edit Wallet" else "New Wallet",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_wallet_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    items(selectableWalletTypes) { wt ->
                        FilterChip(
                            selected = selectedType == wt,
                            onClick = {
                                selectedType = wt
                                if (existingWallet == null && wt == WalletType.FUND) {
                                    selectedIcon = "FUND"
                                    selectedColor = "#0EA5E9"
                                    if (name.isBlank() || SubscriptionManager.isStarterWalletName(name)) {
                                        name = "Fund"
                                    }
                                }
                            },
                            label = { Text(wt.label) },
                            modifier = Modifier.testTag("wallet_type_chip_${wt.name}")
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
                        label = { Text("Adjust Current Balance ($activeCurrencySymbol $resolvedCurrencyCode)") },
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
                    Text(
                        text = "Reconciliation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Missing", "No source", "Other").forEach { option ->
                            FilterChip(
                                selected = selectedReconcileReason == option,
                                onClick = { selectedReconcileReason = option },
                                label = { Text(option) },
                                modifier = Modifier.testTag("wallet_reconcile_reason_${option.lowercase().replace(" ", "_")}")
                            )
                        }
                    }
                    if (selectedReconcileReason == "Other") {
                        OutlinedTextField(
                            value = customReconcileNote,
                            onValueChange = { customReconcileNote = capitalizeFirstLetter(it) },
                            label = { Text("Reconciliation Note") },
                            placeholder = { Text("Reason for balance adjustment") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_reconcile_custom_note_input")
                        )
                    }
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
                                val digits = PocketLocalizer.normalizeToAsciiDigits(raw).filter { it in '0'..'9' }.take(2)
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
                                val digits = PocketLocalizer.normalizeToAsciiDigits(raw).filter { it in '0'..'9' }.take(2)
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

                val visibleIconOptions = remember(isFundFeatureActive, existingWallet) {
                    if (isFundFeatureActive || existingWallet?.isFundWallet == true) {
                        WalletIconPickerOptions
                    } else {
                        WalletIconPickerOptions.filter { it.key != "FUND" }
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("wallet_icon_picker_row")
                ) {
                    items(visibleIconOptions, key = { it.key }) { option ->
                        val isChosen = selectedIcon == option.key
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedIcon = option.key
                                    selectedColor = option.defaultColorHex
                                    if (name.isBlank() || SubscriptionManager.isStarterWalletName(name)) {
                                        name = option.label
                                    }
                                    if (existingWallet == null && option.key == "SAVINGS") {
                                        selectedType = WalletType.SAVINGS
                                        includeInTotalBalance = false
                                    } else if (existingWallet == null && option.key == "FUND") {
                                        selectedType = WalletType.FUND
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
                            val baseWallet = WalletEntity(
                                id = existingWallet?.id ?: 0L,
                                name = name.trim(),
                                type = selectedType.name,
                                accountHint = existingWallet?.accountHint ?: "",
                                openingBalance = AccountingEngine.roundCurrency(openBal),
                                openingDate = existingWallet?.openingDate ?: System.currentTimeMillis(),
                                currency = resolvedCurrencyCode,
                                iconName = selectedIcon,
                                colorHex = selectedColor,
                                sortOrder = existingWallet?.sortOrder ?: 0,
                                isArchived = existingWallet?.isArchived ?: false,
                                includeInTotalBalance = includeInTotalBalance,
                                creditLimit = AccountingEngine.roundCurrency(parsedCreditLimit),
                                statementDay = parsedStatementDay,
                                paymentDueDay = parsedDueDay
                            )
                            viewModel.saveWallet(baseWallet) {
                                if (existingWallet != null && currentBalance != null && evaluatedActualBalance != null) {
                                    val diff = AccountingEngine.roundCurrency(evaluatedActualBalance - currentBalance)
                                    if (kotlin.math.abs(diff) > 0.004) {
                                        val resolvedReason = if (selectedReconcileReason == "Other") {
                                            customReconcileNote.trim().ifBlank { "Other" }
                                        } else {
                                            selectedReconcileReason
                                        }
                                        viewModel.createBalanceAdjustment(
                                            walletId = existingWallet.id,
                                            actualBalance = evaluatedActualBalance,
                                            calculatedBalanceBefore = currentBalance,
                                            date = System.currentTimeMillis(),
                                            reason = resolvedReason,
                                            note = if (selectedReconcileReason == "Other" && customReconcileNote.isNotBlank()) {
                                                customReconcileNote.trim()
                                            } else {
                                                "Reconciliation: $resolvedReason"
                                            },
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
        dismissButton = if (existingWallet != null && !showSafeDeleteStep) {
            {
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
        } else null
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
    var selectedSubBucket by remember(selectedWalletId) {
        mutableStateOf(0) // 0 = Total, 1 = Personal, 2 = Fund
    }
    val selectedSummary = wallets.firstOrNull { it.wallet.id == selectedWalletId }
    val showSubBuckets = uiState.isFundEnabled && selectedSummary != null && !selectedSummary.wallet.isFundWallet
    val calculatedBalance = when {
        selectedSummary == null -> 0.0
        showSubBuckets && selectedSubBucket == 1 -> selectedSummary.personalBalance
        showSubBuckets && selectedSubBucket == 2 -> selectedSummary.fundBalance
        else -> selectedSummary.currentBalance
    }
    var actualBalanceText by remember(selectedSummary?.wallet?.id, selectedSubBucket) {
        mutableStateOf(formatNumericFieldInitialValue(calculatedBalance))
    }
    val reasons = listOf("Correction", "Missing transaction", "Initial setup", "Other")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var noteText by remember { mutableStateOf("") }

    val symbol = AccountingEngine.currencySymbolForCode(selectedSummary?.wallet?.currency ?: "BDT")
    val pocketColors = LocalPocketColors.current
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
                                    selectedSubBucket = 0
                                    actualBalanceText = formatNumericFieldInitialValue(summary.currentBalance)
                                },
                                label = {
                                    Text("${summary.wallet.name} (${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)})")
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    if (showSubBuckets && selectedSummary != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val wName = selectedSummary.wallet.name
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "$wName Total",
                                1 to "$wName-Personal",
                                2 to "$wName-Fund"
                            ).forEach { (bucketIdx, bucketLabel) ->
                                FilterChip(
                                    selected = selectedSubBucket == bucketIdx,
                                    onClick = {
                                        selectedSubBucket = bucketIdx
                                        val targetCalc = when (bucketIdx) {
                                            1 -> selectedSummary.personalBalance
                                            2 -> selectedSummary.fundBalance
                                            else -> selectedSummary.currentBalance
                                        }
                                        actualBalanceText = formatNumericFieldInitialValue(targetCalc)
                                    },
                                    label = { Text(bucketLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .testTag("reconcile_subwallet_chip_$bucketIdx")
                                )
                            }
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
                                if (showSubBuckets && selectedSubBucket != 0) {
                                    viewModel.adjustSubWalletBalance(
                                        walletId = selectedWalletId,
                                        isFundSubWallet = selectedSubBucket == 2,
                                        actualSubBalance = actualBalance,
                                        calculatedSubBalanceBefore = calculatedBalance,
                                        date = System.currentTimeMillis(),
                                        reason = selectedReason,
                                        note = noteText.trim()
                                    )
                                } else {
                                    viewModel.createBalanceAdjustment(
                                        walletId = selectedWalletId,
                                        actualBalance = actualBalance,
                                        calculatedBalanceBefore = calculatedBalance,
                                        date = System.currentTimeMillis(),
                                        reason = selectedReason,
                                        note = noteText.trim(),
                                        navigateBackOnSave = false
                                    )
                                }
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
                        val adjWallet = walletMap[adj.walletId]
                        val wName = adjWallet?.name ?: "Wallet"
                        val adjSymbol = AccountingEngine.currencySymbolForCode(adjWallet?.currency ?: "BDT")
                        val adjDateFormatted = remember(adj.date) { formatRelativeDate(adj.date) }
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
                                    Text(
                                        text = adjDateFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (adj.note.isNotBlank()) {
                                        Text(adj.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = AccountingEngine.formatMoney(adj.difference, adjSymbol, showSign = true),
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
