package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.PocketScreen
import com.example.ui.PocketUiState
import com.example.ui.PocketViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.TabularMoneyLarge
import com.example.ui.theme.TabularMoneyMedium
import com.example.ui.theme.TabularMoneySmall
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val report = uiState.monthlyReport ?: return
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val availableCurrencies = remember(uiState.wallets) {
        buildList {
            add("BDT")
            uiState.wallets.mapNotNull { w ->
                w.currency.trim().uppercase().takeIf { it.isNotBlank() && it != "BDT" }
            }.distinct().forEach { add(it) }
        }
    }
    val activeReportCurrency = uiState.selectedReportCurrency.ifBlank { "BDT" }
    val symbol = remember(activeReportCurrency, uiState.settings.currencySymbol) {
        if (activeReportCurrency.equals("BDT", ignoreCase = true)) {
            uiState.settings.currencySymbol
        } else {
            AccountingEngine.currencySymbolForCode(activeReportCurrency)
        }
    }
    val pocketColors = LocalPocketColors.current
    var showLineChart by remember { mutableStateOf(false) }
    var showIncomeCategories by remember { mutableStateOf(false) }
    var showMasterBudgetDialog by remember { mutableStateOf(false) }
    var editingBudgetCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var inspectedPointText by remember(report) { mutableStateOf<String?>(null) }

    val statementForExport = remember(
        uiState.selectedYear,
        uiState.selectedMonth,
        uiState.wallets,
        uiState.categories,
        uiState.transactions,
        uiState.transfers,
        uiState.loans,
        uiState.loanActivities,
        uiState.adjustments
    ) {
        AccountingEngine.generateMonthlyStatement(
            year = uiState.selectedYear,
            month = uiState.selectedMonth,
            walletFilterId = null,
            wallets = uiState.wallets,
            categories = uiState.categories,
            transactions = uiState.transactions,
            transfers = uiState.transfers,
            loans = uiState.loans,
            loanActivities = uiState.loanActivities,
            adjustments = uiState.adjustments
        )
    }

    val createReportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    BackupManager.exportMonthlyReportToPdf(
                        report = report,
                        statement = statementForExport,
                        currencySymbol = symbol,
                        outputStream = out
                    )
                }
                viewModel.postStatusMessage("Monthly report PDF exported.")
            }
        }
    }

    val createReportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = BackupManager.exportMonthlyReportToCsv(
                    report = report,
                    statement = statementForExport,
                    currencySymbol = symbol
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                viewModel.postStatusMessage("Monthly report CSV exported.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
    ) {
        PocketTopBar(
            title = "Report",
            onLogoClick = { viewModel.navigateTo(PocketScreen.Settings) },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            performPocketHaptic(view)
                            val fileName = "pocket-report-${report.monthLabel.lowercase(java.util.Locale.ENGLISH).replace(" ", "-")}.pdf"
                            createReportPdfLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("export_report_pdf_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            performPocketHaptic(view)
                            val fileName = "pocket-report-${report.monthLabel.lowercase(java.util.Locale.ENGLISH).replace(" ", "-")}.csv"
                            createReportCsvLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("export_report_csv_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV")
                    }
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(PocketScreen.MonthlyStatement()) },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("open_monthly_statement_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Statement")
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Currency Pill Selector (shown whenever foreign-currency wallets exist - Item 5)
            if (availableCurrencies.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_currency_selector_row")
                    ) {
                        items(availableCurrencies, key = { it }) { currCode ->
                            val selected = currCode.equals(activeReportCurrency, ignoreCase = true)
                            val currSym = AccountingEngine.currencySymbolForCode(currCode)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    performPocketHaptic(view)
                                    viewModel.selectReportCurrency(currCode)
                                },
                                label = {
                                    Text(
                                        text = "$currCode ($currSym)",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("report_currency_pill_$currCode")
                            )
                        }
                    }
                }
            }

            // Month Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = report.monthLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    MonthSelectorPill(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        onPreviousMonth = { viewModel.selectPreviousMonth() },
                        onNextMonth = { viewModel.selectNextMonth() }
                    )
                }
            }

            // Monthly Overview Card with Month-over-Month Comparison
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedBalanceText(
                                    amount = report.totalIncome,
                                    currencySymbol = symbol,
                                    includeDecimals = false,
                                    style = TabularMoneyMedium,
                                    color = pocketColors.incomeText,
                                    modifier = Modifier.testTag("reports_income_total")
                                )
                                report.incomeChangePercentVsPreviousMonth?.let { pct ->
                                    val arrow = if (pct >= 0f) "↑" else "↓"
                                    Text(
                                        text = "$arrow ${kotlin.math.abs(pct).roundToInt()}% vs last mo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pct >= 0f) pocketColors.incomeText else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedBalanceText(
                                    amount = report.totalExpenses,
                                    currencySymbol = symbol,
                                    includeDecimals = false,
                                    style = TabularMoneyMedium,
                                    color = pocketColors.expenseText,
                                    modifier = Modifier.testTag("reports_expense_total")
                                )
                                report.expenseChangePercentVsPreviousMonth?.let { pct ->
                                    val arrow = if (pct >= 0f) "↑" else "↓"
                                    Text(
                                        text = "$arrow ${kotlin.math.abs(pct).roundToInt()}% vs last mo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pct <= 0f) pocketColors.incomeText else pocketColors.expenseText,
                                        modifier = Modifier.testTag("reports_expense_mom_badge")
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Net Cash Flow", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedBalanceText(
                                    amount = report.netCashFlow,
                                    currencySymbol = symbol,
                                    showSign = true,
                                    includeDecimals = false,
                                    style = TabularMoneyMedium,
                                    color = if (report.netCashFlow >= 0) pocketColors.incomeText else pocketColors.expenseText
                                )
                            }
                        }

                        // Visual Income vs Expense Bar (Spring-animated)
                        val maxTotal = max(1.0, max(report.totalIncome, report.totalExpenses))
                        val rawIncFraction = (report.totalIncome / maxTotal).toFloat().coerceIn(0.02f, 1f)
                        val rawExpFraction = (report.totalExpenses / maxTotal).toFloat().coerceIn(0.02f, 1f)
                        val incFraction by animateFloatAsState(
                            targetValue = rawIncFraction,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "report_inc_bar"
                        )
                        val expFraction by animateFloatAsState(
                            targetValue = rawExpFraction,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "report_exp_bar"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(64.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(incFraction)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(pocketColors.incomeText)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Expenses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(64.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(expFraction)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(pocketColors.expenseText)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overall Monthly Spending Budget (Master Budget) Card
            item {
                val masterBudget = uiState.settings.masterMonthlyBudget
                val spent = report.totalExpenses
                val calNow = java.util.Calendar.getInstance()
                val daysInMonth = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, uiState.selectedYear)
                    set(java.util.Calendar.MONTH, uiState.selectedMonth)
                }.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val isCurrentMonth = calNow.get(java.util.Calendar.YEAR) == uiState.selectedYear &&
                    calNow.get(java.util.Calendar.MONTH) == uiState.selectedMonth
                val remainingDays = if (isCurrentMonth) {
                    (daysInMonth - calNow.get(java.util.Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
                } else {
                    daysInMonth
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            performPocketHaptic(view)
                            showMasterBudgetDialog = true
                        }
                        .testTag("master_monthly_budget_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Monthly Spending Limit",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = {
                                    performPocketHaptic(view)
                                    showMasterBudgetDialog = true
                                }
                            ) {
                                Text(if (masterBudget != null && masterBudget > 0.0) "Edit Limit" else "Set Budget")
                            }
                        }

                        if (masterBudget != null && masterBudget > 0.0) {
                            val ratio = (spent / masterBudget).toFloat().coerceIn(0f, 1f)
                            val remainingBudget = masterBudget - spent
                            val safePerDay = if (remainingBudget > 0.0) remainingBudget / remainingDays else 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${AccountingEngine.formatMoney(spent, symbol, includeDecimals = false)} of ${AccountingEngine.formatMoney(masterBudget, symbol, includeDecimals = false)}",
                                    style = TabularMoneyMedium,
                                    color = if (spent > masterBudget) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${((spent / masterBudget) * 100.0).roundToInt()}%",
                                    style = TabularMoneySmall,
                                    color = if (spent > masterBudget) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    spent > masterBudget -> pocketColors.expenseText
                                    ratio >= 0.8f -> pocketColors.loanText
                                    else -> pocketColors.incomeText
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = if (remainingBudget >= 0.0) {
                                    "Safe to spend ${AccountingEngine.formatMoney(safePerDay, symbol, includeDecimals = false)}/day for remaining $remainingDays day${if (remainingDays == 1) "" else "s"}"
                                } else {
                                    "Over monthly budget by ${AccountingEngine.formatMoney(kotlin.math.abs(remainingBudget), symbol, includeDecimals = false)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (remainingBudget >= 0.0) MaterialTheme.colorScheme.onSurfaceVariant else pocketColors.expenseText
                            )
                        } else {
                            Text(
                                text = "Tap to set an overall monthly spending cap and see your daily safe-to-spend allowance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Interactive Chart (Bar Chart: 6-month Income vs Expense | Line Chart: Daily Spending Trend)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (showLineChart) "Daily Spending Trend" else "6-Month Income vs. Expenses",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AnimatedContent(
                                    targetState = inspectedPointText ?: "Tap or drag across the chart to scrub exact figures",
                                    transitionSpec = {
                                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                    },
                                    label = "chart_scrub_readout"
                                ) { readout ->
                                    Text(
                                        text = readout,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (inspectedPointText != null) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = showLineChart,
                                onClick = {
                                    showLineChart = !showLineChart
                                    inspectedPointText = null
                                },
                                label = { Text(if (showLineChart) "Bar View" else "Line Trend") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("toggle_chart_type_button")
                            )
                        }

                        AnimatedContent(
                            targetState = showLineChart,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleIn(
                                        initialScale = 0.95f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )) togetherWith
                                    (fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                        scaleOut(targetScale = 0.96f))
                            },
                            label = "chart_type_switch_transition"
                        ) { isLineMode ->
                            if (!isLineMode) {
                                SixMonthBarChart(
                                    points = report.sixMonthTrend,
                                    incomeColor = pocketColors.incomeText,
                                    expenseColor = pocketColors.expenseText,
                                    currencySymbol = symbol,
                                    onInspect = { pt ->
                                        inspectedPointText = "${pt.shortLabel} ${pt.year}: Income ${AccountingEngine.formatMoney(pt.income, symbol, includeDecimals = false)} · Expense ${AccountingEngine.formatMoney(pt.expense, symbol, includeDecimals = false)}"
                                    }
                                )
                            } else {
                                DailySpendingLineChart(
                                    points = report.dailyPoints,
                                    expenseColor = pocketColors.expenseText,
                                    currencySymbol = symbol,
                                    onInspect = { pt ->
                                        inspectedPointText = "${pt.label}: Spent ${AccountingEngine.formatMoney(pt.expense, symbol)} · Income ${AccountingEngine.formatMoney(pt.income, symbol)}"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Spending / Income by Category Breakdown
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showIncomeCategories) "Income by category" else "Spending by category",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showIncomeCategories,
                            onClick = { showIncomeCategories = false },
                            label = { Text("Expenses") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = showIncomeCategories,
                            onClick = { showIncomeCategories = true },
                            label = { Text("Income") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            val categoryList = if (showIncomeCategories) report.incomeByCategory else report.expenseByCategory
            if (categoryList.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = Icons.Default.PieChartOutline,
                        title = "No category activity this month",
                        subtitle = "Recorded ${if (showIncomeCategories) "income" else "expenses"} for ${report.monthLabel} will appear here."
                    )
                }
            } else {
                items(categoryList, key = { it.category.id }) { item ->
                    val catBudget = item.monthlyBudget
                    val isOverspent = !showIncomeCategories && catBudget > 0.0 && item.amount > catBudget
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.navigateTo(
                                    PocketScreen.TransactionHistory(initialCategoryId = item.category.id)
                                )
                            }
                            .testTag("report_category_row_${item.category.name}"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isOverspent) 1.5.dp else 1.dp,
                            color = if (isOverspent) pocketColors.expenseText else pocketColors.cardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBadgeCircle(
                                    iconName = item.category.iconName,
                                    colorHex = item.category.colorHex,
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!showIncomeCategories && !item.category.isSystemFee) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (catBudget > 0.0) {
                                                    if (isOverspent) pocketColors.expenseText.copy(alpha = 0.16f)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                } else {
                                                    pocketColors.surfaceElevated
                                                },
                                                border = BorderStroke(
                                                    0.75.dp,
                                                    if (isOverspent) pocketColors.expenseText else pocketColors.cardBorder
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { editingBudgetCategory = item.category }
                                                    .testTag("category_budget_chip_${item.category.name}")
                                            ) {
                                                Text(
                                                    text = if (catBudget > 0.0) {
                                                        "Limit: ${AccountingEngine.formatMoney(catBudget, symbol, includeDecimals = false)}"
                                                    } else {
                                                        "+ Limit"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = when {
                                                        isOverspent -> pocketColors.expenseText
                                                        catBudget > 0.0 -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    val momBadge = item.changePercentVsPreviousMonth?.let { pct ->
                                        val arrow = if (pct >= 0f) "↑" else "↓"
                                        " · $arrow ${kotlin.math.abs(pct).roundToInt()}% vs last mo"
                                    } ?: ""
                                    Text(
                                        "${item.transactionCount} transaction${if (item.transactionCount == 1) "" else "s"}$momBadge",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = AccountingEngine.formatMoney(item.amount, symbol, includeDecimals = false),
                                        style = TabularMoneyMedium,
                                        color = if (isOverspent) pocketColors.expenseText else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${((item.percentage * 10f).roundToInt() / 10f)}%",
                                        style = TabularMoneySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!showIncomeCategories && catBudget > 0.0) {
                                val budgetRatio = (item.amount / catBudget).toFloat()
                                val barColor = when {
                                    budgetRatio > 1f -> pocketColors.expenseText
                                    budgetRatio >= 0.85f -> Color(0xFFF59E0B)
                                    else -> pocketColors.incomeText
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(budgetRatio.coerceIn(0.02f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val diff = AccountingEngine.roundCurrency(catBudget - item.amount)
                                Text(
                                    text = if (diff >= 0.0) {
                                        "${(budgetRatio * 100f).roundToInt()}% of ${AccountingEngine.formatMoney(catBudget, symbol, includeDecimals = false)} limit · ${AccountingEngine.formatMoney(diff, symbol, includeDecimals = false)} left"
                                    } else {
                                        "Overspent by ${AccountingEngine.formatMoney(-diff, symbol, includeDecimals = false)} (Limit: ${AccountingEngine.formatMoney(catBudget, symbol, includeDecimals = false)})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (diff < 0.0) FontWeight.Bold else FontWeight.Medium,
                                    color = barColor
                                )
                            } else {
                                val barColor = parseHexColor(item.category.colorHex)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((item.percentage / 100f).coerceIn(0.02f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Wallet Balance Comparison
            if (uiState.activeWalletSummaries.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
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
                                text = "Wallet Balance Comparison",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val maxBal = max(1.0, uiState.activeWalletSummaries.maxOfOrNull { it.currentBalance } ?: 1.0)
                            uiState.activeWalletSummaries.forEach { ws ->
                                val wSym = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = ws.wallet.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = AccountingEngine.formatMoney(ws.currentBalance, wSym, includeDecimals = false),
                                            style = TabularMoneySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth((ws.currentBalance / maxBal).toFloat().coerceIn(0.02f, 1f))
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(parseHexColor(ws.wallet.colorHex))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Loan Receivables and Payables Summary
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                    modifier = Modifier.clickable { viewModel.navigateTo(PocketScreen.LoansManagement) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("People owe you (Receivable)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                AccountingEngine.formatMoney(report.loanReceivableOutstanding, symbol, includeDecimals = false),
                                style = TabularMoneyMedium,
                                color = pocketColors.incomeText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("You owe others (Payable)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                AccountingEngine.formatMoney(report.loanPayableOutstanding, symbol, includeDecimals = false),
                                style = TabularMoneyMedium,
                                color = pocketColors.loanText
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMasterBudgetDialog) {
        var budgetInput by remember {
            mutableStateOf(
                uiState.settings.masterMonthlyBudget?.let {
                    if (it > 0.0) AccountingEngine.roundCurrency(it).toInt().toString() else ""
                } ?: ""
            )
        }
        AlertDialog(
            onDismissRequest = { showMasterBudgetDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Monthly Spending Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MathAmountTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = sanitizeMathExpressionInput(it) },
                        label = { Text("Monthly Limit ($symbol)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_budget_input")
                    )
                    MiniCalculatorBar(
                        expression = budgetInput,
                        onExpressionChange = { budgetInput = it },
                        currencySymbol = symbol
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = AccountingEngine.evaluateMathExpression(budgetInput)
                            ?: budgetInput.trim().toDoubleOrNull()
                        viewModel.updateMasterMonthlyBudget(parsed?.takeIf { it > 0.0 })
                        showMasterBudgetDialog = false
                    },
                    modifier = Modifier.testTag("save_master_budget_button")
                ) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.settings.masterMonthlyBudget != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateMasterMonthlyBudget(null)
                                showMasterBudgetDialog = false
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { showMasterBudgetDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    editingBudgetCategory?.let { cat ->
        var catBudgetInput by remember(cat.id) {
            mutableStateOf(
                if (cat.monthlyBudget > 0.0) {
                    if (cat.monthlyBudget % 1.0 == 0.0) cat.monthlyBudget.toLong().toString()
                    else cat.monthlyBudget.toString()
                } else ""
            )
        }
        AlertDialog(
            onDismissRequest = { editingBudgetCategory = null },
            containerColor = pocketColors.overlaySurface,
            title = { Text("${cat.name} Monthly Limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Set an optional monthly spending limit for ${cat.name} to highlight overspending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MathAmountTextField(
                        value = catBudgetInput,
                        onValueChange = { catBudgetInput = sanitizeMathExpressionInput(it) },
                        label = { Text("Monthly Limit ($symbol)") },
                        placeholder = { Text("e.g. 8000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_budget_input")
                    )
                    MiniCalculatorBar(
                        expression = catBudgetInput,
                        onExpressionChange = { catBudgetInput = it },
                        currencySymbol = symbol
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = AccountingEngine.evaluateMathExpression(catBudgetInput)
                            ?: catBudgetInput.trim().toDoubleOrNull()
                            ?: 0.0
                        viewModel.updateCategoryMonthlyBudget(cat.id, parsed)
                        editingBudgetCategory = null
                    },
                    modifier = Modifier.testTag("save_category_budget_button")
                ) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cat.monthlyBudget > 0.0) {
                        TextButton(
                            onClick = {
                                viewModel.updateCategoryMonthlyBudget(cat.id, 0.0)
                                editingBudgetCategory = null
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { editingBudgetCategory = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun SixMonthBarChart(
    points: List<MonthlyComparisonPoint>,
    incomeColor: Color,
    expenseColor: Color,
    currencySymbol: String,
    onInspect: (MonthlyComparisonPoint) -> Unit
) {
    val maxVal = remember(points) {
        max(1.0, points.maxOfOrNull { max(it.income, it.expense) } ?: 1.0)
    }
    var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary

    var entryStarted by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { entryStarted = true }
    val entryProgress by animateFloatAsState(
        targetValue = if (entryStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "six_month_bar_entry"
    )

    val animatedSelectedIdx by animateFloatAsState(
        targetValue = (selectedIdx ?: 0).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 380f
        ),
        label = "six_month_scrub_idx"
    )
    val scrubHighlightAlpha by animateFloatAsState(
        targetValue = if (selectedIdx != null) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "six_month_scrub_alpha"
    )

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty() && size.width > 0) {
                            val slotWidth = size.width.toFloat() / points.size
                            val idx = (offset.x / slotWidth).toInt().coerceIn(points.indices)
                            selectedIdx = idx
                            onInspect(points[idx])
                        }
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (points.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / points.size
                                val idx = (offset.x / slotWidth).toInt().coerceIn(points.indices)
                                selectedIdx = idx
                                onInspect(points[idx])
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (points.isNotEmpty() && size.width > 0) {
                                val slotWidth = size.width.toFloat() / points.size
                                val idx = (change.position.x / slotWidth).toInt().coerceIn(points.indices)
                                if (selectedIdx != idx) {
                                    selectedIdx = idx
                                    onInspect(points[idx])
                                }
                            }
                        }
                    )
                }
        ) {
            val count = points.size.coerceAtLeast(1)
            val slotW = size.width / count
            val baseBarW = (slotW * 0.28f).coerceAtLeast(6f)
            val chartH = size.height - 8f

            // Animated scrub column pill & vertical guide line
            if (scrubHighlightAlpha > 0.01f) {
                val scrubCenterX = animatedSelectedIdx * slotW + slotW / 2f
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.14f * scrubHighlightAlpha),
                    topLeft = Offset(scrubCenterX - slotW * 0.42f, 0f),
                    size = Size(slotW * 0.84f, chartH + 4f),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.45f * scrubHighlightAlpha),
                    start = Offset(scrubCenterX, 4f),
                    end = Offset(scrubCenterX, chartH),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            points.forEachIndexed { idx, pt ->
                val isSelected = selectedIdx == idx
                val barScale = if (isSelected) 1.12f else 1.0f
                val barW = baseBarW * barScale
                val centerX = idx * slotW + slotW / 2f
                val incH = (((pt.income / maxVal).toFloat() * chartH) * entryProgress).coerceAtLeast(4f)
                val expH = (((pt.expense / maxVal).toFloat() * chartH) * entryProgress).coerceAtLeast(4f)
                val barAlpha = if (selectedIdx == null || isSelected) 1f else 0.62f

                drawRoundRect(
                    color = incomeColor.copy(alpha = barAlpha),
                    topLeft = Offset(centerX - barW - 2f, chartH - incH),
                    size = Size(barW, incH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = expenseColor.copy(alpha = barAlpha),
                    topLeft = Offset(centerX + 2f, chartH - expH),
                    size = Size(barW, expH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEachIndexed { idx, pt ->
                val isSelected = selectedIdx == idx
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "bar_label_color_$idx"
                )
                Text(
                    text = pt.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor
                )
            }
        }
    }
}

@Composable
private fun DailySpendingLineChart(
    points: List<DailySpendingPoint>,
    expenseColor: Color,
    currencySymbol: String,
    onInspect: (DailySpendingPoint) -> Unit
) {
    val maxVal = remember(points) {
        max(1.0, points.maxOfOrNull { it.expense } ?: 1.0)
    }
    var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary

    var entryStarted by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { entryStarted = true }
    val entryProgress by animateFloatAsState(
        targetValue = if (entryStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "daily_line_entry"
    )

    val animatedScrubIndex by animateFloatAsState(
        targetValue = (selectedIdx ?: 0).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 420f
        ),
        label = "daily_line_scrub_idx"
    )
    val scrubAlpha by animateFloatAsState(
        targetValue = if (selectedIdx != null) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "daily_line_scrub_alpha"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty() && size.width > 0) {
                        val step = size.width.toFloat() / points.size
                        val idx = (offset.x / step).toInt().coerceIn(points.indices)
                        selectedIdx = idx
                        onInspect(points[idx])
                    }
                }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (points.isNotEmpty() && size.width > 0) {
                            val step = size.width.toFloat() / points.size
                            val idx = (offset.x / step).toInt().coerceIn(points.indices)
                            selectedIdx = idx
                            onInspect(points[idx])
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (points.isNotEmpty() && size.width > 0) {
                            val step = size.width.toFloat() / points.size
                            val idx = (change.position.x / step).toInt().coerceIn(points.indices)
                            if (selectedIdx != idx) {
                                selectedIdx = idx
                                onInspect(points[idx])
                            }
                        }
                    }
                )
            }
    ) {
        if (points.isEmpty()) return@Canvas
        val path = Path()
        val fillPath = Path()
        val w = size.width
        val h = size.height - 12f
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        points.forEachIndexed { index, pt ->
            val x = index * stepX
            val y = h - (((pt.expense / maxVal).toFloat() * (h - 16f)) * entryProgress)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        // Subtle area gradient under the spending curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    expenseColor.copy(alpha = 0.26f),
                    expenseColor.copy(alpha = 0.02f)
                ),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = path,
            color = expenseColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        points.forEachIndexed { index, pt ->
            if (pt.expense > 0.0) {
                val x = index * stepX
                val y = h - (((pt.expense / maxVal).toFloat() * (h - 16f)) * entryProgress)
                drawCircle(color = expenseColor, radius = 5.5f, center = Offset(x, y))
            }
        }

        // Smoothly interpolated scrub crosshair & glowing indicator dot
        if (scrubAlpha > 0.01f && points.isNotEmpty()) {
            val clampedIdx = animatedScrubIndex.coerceIn(0f, (points.size - 1).toFloat())
            val lower = clampedIdx.toInt().coerceIn(points.indices)
            val upper = (lower + 1).coerceIn(points.indices)
            val frac = clampedIdx - lower
            val interpExpense = points[lower].expense + (points[upper].expense - points[lower].expense) * frac
            val scrubX = clampedIdx * stepX
            val scrubY = h - (((interpExpense / maxVal).toFloat() * (h - 16f)) * entryProgress)

            drawLine(
                color = primaryColor.copy(alpha = 0.55f * scrubAlpha),
                start = Offset(scrubX, 4f),
                end = Offset(scrubX, h),
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.28f * scrubAlpha),
                radius = 12.dp.toPx() * scrubAlpha,
                center = Offset(scrubX, scrubY)
            )
            drawCircle(
                color = Color.White.copy(alpha = scrubAlpha),
                radius = 6.dp.toPx() * scrubAlpha,
                center = Offset(scrubX, scrubY)
            )
            drawCircle(
                color = primaryColor.copy(alpha = scrubAlpha),
                radius = 4.dp.toPx() * scrubAlpha,
                center = Offset(scrubX, scrubY)
            )
        }
    }
}

@Composable
fun MonthlyStatementScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    initialWalletId: Long?
) {
    val context = LocalContext.current
    var walletFilterId by remember(initialWalletId) { mutableStateOf(initialWalletId) }
    val symbol = uiState.settings.currencySymbol
    val pocketColors = LocalPocketColors.current

    val statement = remember(
        uiState.selectedYear,
        uiState.selectedMonth,
        walletFilterId,
        uiState.wallets,
        uiState.categories,
        uiState.transactions,
        uiState.transfers,
        uiState.loans,
        uiState.loanActivities,
        uiState.adjustments
    ) {
        AccountingEngine.generateMonthlyStatement(
            year = uiState.selectedYear,
            month = uiState.selectedMonth,
            walletFilterId = walletFilterId,
            wallets = uiState.wallets,
            categories = uiState.categories,
            transactions = uiState.transactions,
            transfers = uiState.transfers,
            loans = uiState.loans,
            loanActivities = uiState.loanActivities,
            adjustments = uiState.adjustments
        )
    }
    var statementSearchQuery by remember(walletFilterId, uiState.selectedYear, uiState.selectedMonth) {
        mutableStateOf("")
    }
    val filteredStatementRows = remember(statement.rows, statementSearchQuery) {
        val q = statementSearchQuery.trim().lowercase(java.util.Locale.ENGLISH)
        if (q.isBlank()) {
            statement.rows
        } else {
            statement.rows.filter { row ->
                row.description.lowercase(java.util.Locale.ENGLISH).contains(q) ||
                    row.walletName.lowercase(java.util.Locale.ENGLISH).contains(q) ||
                    row.typeLabel.lowercase(java.util.Locale.ENGLISH).contains(q) ||
                    row.dateFormatted.lowercase(java.util.Locale.ENGLISH).contains(q) ||
                    row.signedAmount.toString().contains(q)
            }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = BackupManager.exportStatementToCsv(statement)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                viewModel.postStatusMessage("Monthly statement CSV saved.")
            }
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val report = uiState.monthlyReport
            if (report != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        BackupManager.exportMonthlyReportToPdf(
                            report = report,
                            statement = statement,
                            currencySymbol = symbol,
                            outputStream = out
                        )
                    }
                    viewModel.postStatusMessage("Monthly statement PDF saved.")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
    ) {
        PocketTopBar(
            title = "Monthly Statement",
            subtitle = "${statement.periodLabel} · ${statement.walletFilterLabel}",
            onBack = { viewModel.navigateBack() },
            actions = {
                IconButton(
                    onClick = {
                        val csv = BackupManager.exportStatementToCsv(statement)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "Pocket Statement - ${statement.periodLabel}")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Statement CSV"))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share CSV")
                }
                OutlinedButton(
                    onClick = {
                        val fileName = "pocket-statement-${statement.periodLabel.lowercase(java.util.Locale.ENGLISH).replace(" ", "-")}.pdf"
                        createPdfLauncher.launch(fileName)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_statement_pdf_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("PDF")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        val fileName = "pocket-statement-${statement.periodLabel.lowercase(java.util.Locale.ENGLISH).replace(" ", "-")}.csv"
                        createCsvLauncher.launch(fileName)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_statement_csv_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CSV")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month & Wallet Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonthSelectorPill(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        onPreviousMonth = { viewModel.selectPreviousMonth() },
                        onNextMonth = { viewModel.selectNextMonth() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = walletFilterId == null,
                            onClick = { walletFilterId = null },
                            label = { Text("All Wallets Combined") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    items(uiState.wallets, key = { it.id }) { w ->
                        FilterChip(
                            selected = walletFilterId == w.id,
                            onClick = { walletFilterId = w.id },
                            label = { Text(w.name) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            // Reconciled Statement Summary Box (Sections 1–7)
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Statement Reconciliation",
                            style = MaterialTheme.typography.titleMedium
                        )
                        StatementSummaryRow("1. Opening balance", statement.openingBalance, symbol)
                        StatementSummaryRow("2. Total income (+)", statement.totalIncome, symbol)
                        StatementSummaryRow("3. Total expenses (−)", -statement.totalExpenses, symbol)
                        StatementSummaryRow(
                            "   ↳ Transfer fees included in expenses",
                            -statement.transferFeesIncludedInExpenses,
                            symbol
                        )
                        StatementSummaryRow("4a. Transfers in (+)", statement.transfersIn, symbol)
                        StatementSummaryRow("4b. Transfers out (−)", -statement.transfersOut, symbol)
                        StatementSummaryRow("5a. Loan principal received (+)", statement.loanPrincipalReceived, symbol)
                        StatementSummaryRow("5b. Loan principal repaid (−)", -statement.loanPrincipalRepaid, symbol)
                        if (statement.adjustmentsNet != 0.0) {
                            StatementSummaryRow("6. Balance adjustments (±)", statement.adjustmentsNet, symbol)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("7. Closing balance", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = AccountingEngine.formatMoney(statement.closingBalance, symbol),
                                style = TabularMoneyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("statement_closing_balance_text")
                            )
                        }
                    }
                }
            }

            // Section 8: Transaction-by-transaction Ledger Table
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (statementSearchQuery.isBlank()) {
                            "Ledger Movements (${statement.rows.size} rows)"
                        } else {
                            "Ledger Movements (${filteredStatementRows.size} of ${statement.rows.size} rows)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedTextField(
                        value = statementSearchQuery,
                        onValueChange = { statementSearchQuery = it },
                        placeholder = { Text("Search description, wallet, type, or amount...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search monthly statement"
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
                            .testTag("monthly_statement_search_input")
                    )
                }
            }

            items(filteredStatementRows.indices.toList(), key = { it }) { idx ->
                val row = filteredStatementRows[idx]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (row.isFee) pocketColors.expenseContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.dateFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${row.walletName} · ${row.typeLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = AccountingEngine.formatMoney(
                                    row.signedAmount,
                                    symbol,
                                    showSign = row.typeLabel != "Opening"
                                ),
                                style = TabularMoneySmall,
                                color = when {
                                    row.typeLabel == "Opening" -> MaterialTheme.colorScheme.onSurface
                                    row.signedAmount >= 0 -> pocketColors.incomeText
                                    else -> pocketColors.expenseText
                                }
                            )
                            Text(
                                text = "Bal: ${AccountingEngine.formatMoney(row.runningBalance, symbol, includeDecimals = false)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementSummaryRow(label: String, amount: Double, symbol: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            AccountingEngine.formatMoney(amount, symbol, showSign = true),
            style = TabularMoneySmall
        )
    }
}
