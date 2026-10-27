package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    initialType: TransactionType,
    editTransactionId: Long?,
    preselectedWalletId: Long?
) {
    val existingTx = remember(editTransactionId, uiState.transactions) {
        uiState.transactions.firstOrNull { it.id == editTransactionId }
    }
    val existingChildFeeTx = remember(existingTx, uiState.transactions) {
        if (existingTx != null) {
            uiState.transactions.firstOrNull { it.parentTransactionId == existingTx.id }
        } else null
    }

    var selectedType by remember(existingTx, initialType) {
        mutableStateOf(
            when (existingTx?.type) {
                TransactionType.INCOME.name -> TransactionType.INCOME
                TransactionType.EXPENSE_REVERSAL.name -> TransactionType.INCOME
                TransactionType.EXPENSE.name -> TransactionType.EXPENSE
                else -> initialType
            }
        )
    }
    var recordRefundAsExpenseReversal by remember(existingTx) {
        mutableStateOf(existingTx?.type == TransactionType.EXPENSE_REVERSAL.name)
    }

    var amountText by remember(existingTx) {
        mutableStateOf(formatNumericFieldInitialValue(existingTx?.amount))
    }

    val activeWallets = remember(uiState.activeWalletSummaries, existingTx) {
        val list = uiState.activeWalletSummaries.toMutableList()
        if (existingTx != null && list.none { it.wallet.id == existingTx.walletId }) {
            uiState.walletSummaries.firstOrNull { it.wallet.id == existingTx.walletId }?.let { list.add(it) }
        }
        list
    }

    var selectedWalletId by remember(existingTx, preselectedWalletId, activeWallets, uiState.settings.defaultWalletId) {
        mutableStateOf(
            existingTx?.walletId
                ?: preselectedWalletId
                ?: uiState.settings.defaultWalletId
                ?: activeWallets.firstOrNull()?.wallet?.id
                ?: 0L
        )
    }

    val filteredCategories = remember(uiState.categories, selectedType, recordRefundAsExpenseReversal) {
        val catType = if (selectedType == TransactionType.INCOME && !recordRefundAsExpenseReversal) "INCOME" else "EXPENSE"
        uiState.categories.filter { it.type == catType && !it.isArchived }
    }

    var selectedCategoryId by remember(existingTx, filteredCategories) {
        mutableStateOf(
            existingTx?.categoryId?.takeIf { id -> filteredCategories.any { it.id == id } }
                ?: filteredCategories.firstOrNull()?.id
                ?: 0L
        )
    }

    // Split Transaction state (One Payment -> Multiple Categories - Item 7)
    var isSplitMode by remember(existingTx) { mutableStateOf(false) }
    val splitEntries = remember(existingTx) {
        mutableStateListOf<Pair<Long, String>>()
    }
    LaunchedEffect(isSplitMode, filteredCategories) {
        if (isSplitMode && splitEntries.isEmpty() && filteredCategories.isNotEmpty()) {
            val firstCat = selectedCategoryId.takeIf { id -> filteredCategories.any { it.id == id } }
                ?: filteredCategories.first().id
            val secondCat = filteredCategories.firstOrNull { it.id != firstCat }?.id ?: firstCat
            splitEntries.add(firstCat to amountText)
            splitEntries.add(secondCat to "")
        }
    }

    var selectedDateMillis by remember(existingTx) {
        mutableStateOf(existingTx?.date ?: System.currentTimeMillis())
    }
    var showTxDatePicker by remember { mutableStateOf(false) }
    var noteText by remember(existingTx) { mutableStateOf(existingTx?.note ?: "") }
    var referenceText by remember(existingTx) { mutableStateOf(existingTx?.reference ?: "") }
    var tagsText by remember(existingTx) { mutableStateOf(existingTx?.tags ?: "") }
    var attachmentUri by remember(existingTx) { mutableStateOf(existingTx?.attachmentUri ?: "") }
    var showMoreDetails by remember(existingTx) {
        mutableStateOf(existingTx != null && (existingTx.reference.isNotBlank() || existingTx.tags.isNotBlank()))
    }
    var showInlineCategoryDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // 1st-time Recurring Transaction setup state (Option A: Ask to Confirm)
    var makeRecurring by remember(existingTx) { mutableStateOf(false) }
    var recurringTitle by remember(existingTx) { mutableStateOf("") }
    var recurringAmountMode by remember(existingTx) { mutableStateOf(RecurringAmountMode.FIXED) }
    var recurringFrequency by remember(existingTx) { mutableStateOf(RecurringFrequency.MONTHLY) }
    var recurringDayOfMonthText by remember(selectedDateMillis) {
        val day = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }.get(Calendar.DAY_OF_MONTH)
        mutableStateOf(day.toString())
    }
    var recurringCustomDaysText by remember(existingTx) { mutableStateOf("30") }
    val view = LocalView.current

    val selectedWalletSummary = activeWallets.firstOrNull { it.wallet.id == selectedWalletId }
    val selectedWalletCurrency = selectedWalletSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val symbol = AccountingEngine.currencySymbolForCode(selectedWalletCurrency)

    // Cross-currency conversion & service charge options when paying/receiving (and when editing linked fee)
    var useConversionOrCharge by remember(existingTx, existingChildFeeTx) {
        mutableStateOf(existingChildFeeTx != null)
    }
    var txConversionMode by remember(existingTx) { mutableStateOf("NONE") } // "NONE", "RATE", "FIXED_CONVERTED"
    var txConversionRateText by remember(existingTx) { mutableStateOf("") }
    var txConvertedFixedText by remember(existingTx) { mutableStateOf("") }
    var txServiceFeeMode by remember(existingTx, existingChildFeeTx) {
        mutableStateOf(if (existingChildFeeTx != null) "FIXED" else "NONE")
    } // "NONE", "PERCENTAGE", "FIXED"
    var txServiceFeeInputText by remember(existingTx, existingChildFeeTx) {
        mutableStateOf(formatNumericFieldInitialValue(existingChildFeeTx?.amount))
    }

    val splitTotalAmount = remember(isSplitMode, splitEntries.toList()) {
        if (!isSplitMode) 0.0
        else AccountingEngine.roundCurrency(
            splitEntries.sumOf { (_, expr) ->
                evaluateMathExpression(expr) ?: expr.toDoubleOrNull() ?: 0.0
            }
        )
    }

    val rawInputAmount = remember(amountText, isSplitMode, splitTotalAmount) {
        if (isSplitMode && splitTotalAmount > 0.0) {
            splitTotalAmount
        } else {
            evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
        }
    }
    val effectiveTxPrincipal = remember(rawInputAmount, useConversionOrCharge, txConversionMode, txConversionRateText, txConvertedFixedText) {
        if (!useConversionOrCharge) {
            rawInputAmount
        } else {
            when (txConversionMode) {
                "RATE" -> {
                    val rate = txConversionRateText.toDoubleOrNull() ?: 1.0
                    if (rate > 0.0) AccountingEngine.roundCurrency(rawInputAmount * rate) else rawInputAmount
                }
                "FIXED_CONVERTED" -> {
                    val fixedConv = txConvertedFixedText.toDoubleOrNull() ?: 0.0
                    if (fixedConv > 0.0) AccountingEngine.roundCurrency(fixedConv) else rawInputAmount
                }
                else -> rawInputAmount
            }
        }
    }
    val effectiveTxServiceFee = remember(effectiveTxPrincipal, useConversionOrCharge, txServiceFeeMode, txServiceFeeInputText) {
        if (!useConversionOrCharge) {
            0.0
        } else {
            val feeIn = evaluateMathExpression(txServiceFeeInputText) ?: txServiceFeeInputText.toDoubleOrNull() ?: 0.0
            when (txServiceFeeMode) {
                "PERCENTAGE" -> if (feeIn > 0.0) AccountingEngine.roundCurrency(effectiveTxPrincipal * (feeIn / 100.0)) else 0.0
                "FIXED" -> if (feeIn > 0.0) AccountingEngine.roundCurrency(feeIn) else 0.0
                else -> 0.0
            }
        }
    }
    val parsedAmount = effectiveTxPrincipal
    val totalWalletOutflow = effectiveTxPrincipal + effectiveTxServiceFee
    val willBecomeNegative = selectedType == TransactionType.EXPENSE &&
        selectedWalletSummary != null &&
        (selectedWalletSummary.currentBalance - totalWalletOutflow) < -0.005

    val pocketColors = LocalPocketColors.current
    val formBlur by animateDpAsState(
        targetValue = if (showInlineCategoryDialog) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tx_form_blur"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .blur(formBlur),
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
                title = if (existingTx != null) "Edit Transaction" else if (selectedType == TransactionType.INCOME) "Add Income" else "Add Expense",
                onBack = { viewModel.navigateBack() },
                actions = {
                    if (existingTx == null) {
                        CsvBankImportButtonAndDialog(
                            wallets = activeWallets.map { it.wallet },
                            categories = uiState.categories,
                            defaultWalletId = selectedWalletId,
                            onImportTransactions = { importedList ->
                                viewModel.importCsvTransactions(
                                    importedTransactions = importedList,
                                    navigateBackOnSave = true
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SmsPasteButtonAndDialog(
                            wallets = activeWallets.map { it.wallet },
                            onSmsParsed = { parsed ->
                                selectedType = parsed.inferredType
                                recordRefundAsExpenseReversal = false
                                amountText = formatNumericFieldInitialValue(parsed.amount)
                                if (parsed.matchedWalletId != null) {
                                    selectedWalletId = parsed.matchedWalletId
                                }
                                if (parsed.feeAmount > 0.0) {
                                    useConversionOrCharge = true
                                    txServiceFeeMode = "FIXED"
                                    txServiceFeeInputText = formatNumericFieldInitialValue(parsed.feeAmount)
                                }
                                if (parsed.reference.isNotBlank()) {
                                    referenceText = parsed.reference
                                    showMoreDetails = true
                                }
                                if (noteText.isBlank() && parsed.summaryNote.isNotBlank()) {
                                    noteText = parsed.summaryNote
                                }
                                viewModel.postStatusMessage("Auto-filled from ${parsed.providerName.ifBlank { "SMS" }}.")
                            }
                        )
                    }
                    if (existingTx != null) {
                        IconButton(
                            onClick = {
                                uiState.unifiedLedger.firstOrNull {
                                    (it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.EXPENSE_REVERSAL) &&
                                        it.rawId == existingTx.id
                                }?.let {
                                    viewModel.deleteLedgerEntry(it)
                                    viewModel.navigateBack()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete transaction",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Type Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = {
                            selectedType = TransactionType.EXPENSE
                            recordRefundAsExpenseReversal = false
                        },
                        label = { Text("Expense") },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("form_type_expense_chip")
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Income") },
                        leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("form_type_income_chip")
                    )
                }
            }

            // Amount Input + Built-in Mini Calculator (with cursor auto-advance after +, -, ×, ÷)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MathAmountTextField(
                        value = if (isSplitMode && splitTotalAmount > 0.0) {
                            formatNumericFieldInitialValue(splitTotalAmount)
                        } else {
                            amountText
                        },
                        onValueChange = {
                            if (!isSplitMode) {
                                amountText = sanitizeMathExpressionInput(it)
                                validationError = null
                            }
                        },
                        label = {
                            Text(
                                when {
                                    isSplitMode -> "Total Split Amount ($symbol $selectedWalletCurrency)"
                                    useConversionOrCharge && txConversionMode != "NONE" -> "Entered Amount (Before Conversion)"
                                    else -> "Amount ($symbol $selectedWalletCurrency) · e.g. 120+45"
                                }
                            )
                        },
                        placeholder = { Text("0") },
                        textStyle = TabularMoneyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(amountText) { if (!isSplitMode) amountText = it }
                            .testTag("transaction_amount_input")
                    )
                    if (!isSplitMode) {
                        MiniCalculatorBar(
                            expression = amountText,
                            currencySymbol = symbol,
                            onExpressionChange = {
                                amountText = it
                                validationError = null
                            }
                        )
                    }
                }
            }

            // Refund as Expense Reversal option (when in Income mode)
            if (selectedType == TransactionType.INCOME) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { recordRefundAsExpenseReversal = !recordRefundAsExpenseReversal }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = recordRefundAsExpenseReversal,
                                onCheckedChange = { recordRefundAsExpenseReversal = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Refund / Expense Reversal", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Increases wallet balance and offsets an expense category instead of inflating income.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Wallet Selector
            item {
                Text(
                    text = if (selectedType == TransactionType.INCOME) "Destination Wallet" else "Payment Wallet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeWallets, key = { it.wallet.id }) { summary ->
                        val isSelected = summary.wallet.id == selectedWalletId
                        val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedWalletId = summary.wallet.id },
                            label = {
                                Text(
                                    "${summary.wallet.name} (${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)})"
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("select_wallet_chip_${summary.wallet.name}")
                        )
                    }
                }

                // Currency Conversion & Linked Service Charge option (available for both new and edited transactions)
                Spacer(modifier = Modifier.height(8.dp))
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { useConversionOrCharge = !useConversionOrCharge }
                                    .testTag("tx_conversion_service_charge_toggle"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (selectedWalletCurrency != "BDT") {
                                            "Currency Conversion & Service Charge ($selectedWalletCurrency)"
                                        } else {
                                            "Currency Conversion / Service Charge"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Convert from another currency (rate or fixed) or add a % / fixed service charge",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useConversionOrCharge,
                                    onCheckedChange = { useConversionOrCharge = it }
                                )
                            }

                            AnimatedVisibility(visible = useConversionOrCharge) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HorizontalDivider(color = pocketColors.cardBorder)
                                    Text(
                                        text = "Currency Conversion to $selectedWalletCurrency ($symbol)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = txConversionMode == "NONE",
                                            onClick = { txConversionMode = "NONE" },
                                            label = { Text("No Conversion") }
                                        )
                                        FilterChip(
                                            selected = txConversionMode == "RATE",
                                            onClick = { txConversionMode = "RATE" },
                                            label = { Text("Conversion Rate") },
                                            modifier = Modifier.testTag("tx_conv_mode_rate_chip")
                                        )
                                        FilterChip(
                                            selected = txConversionMode == "FIXED_CONVERTED",
                                            onClick = { txConversionMode = "FIXED_CONVERTED" },
                                            label = { Text("Fixed Amount") },
                                            modifier = Modifier.testTag("tx_conv_mode_fixed_chip")
                                        )
                                    }

                                    if (txConversionMode == "RATE") {
                                        OutlinedTextField(
                                            value = txConversionRateText,
                                            onValueChange = { txConversionRateText = sanitizeNumericInput(it) },
                                            label = { Text("Conversion Multiplier Rate (e.g., 122 or 0.0082)") },
                                            placeholder = { Text("1.0") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(txConversionRateText) { txConversionRateText = it }
                                                .testTag("tx_conversion_rate_input")
                                        )
                                    } else if (txConversionMode == "FIXED_CONVERTED") {
                                        OutlinedTextField(
                                            value = txConvertedFixedText,
                                            onValueChange = { txConvertedFixedText = sanitizeNumericInput(it) },
                                            label = { Text("Fixed Converted Amount ($symbol $selectedWalletCurrency)") },
                                            placeholder = { Text("0") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(txConvertedFixedText) { txConvertedFixedText = it }
                                                .testTag("tx_converted_fixed_input")
                                        )
                                    }

                                    Text(
                                        text = "Service Charge ($symbol $selectedWalletCurrency)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = txServiceFeeMode == "NONE",
                                            onClick = { txServiceFeeMode = "NONE" },
                                            label = { Text("None") }
                                        )
                                        FilterChip(
                                            selected = txServiceFeeMode == "PERCENTAGE",
                                            onClick = { txServiceFeeMode = "PERCENTAGE" },
                                            label = { Text("Percentage (%)") },
                                            modifier = Modifier.testTag("tx_fee_mode_pct_chip")
                                        )
                                        FilterChip(
                                            selected = txServiceFeeMode == "FIXED",
                                            onClick = { txServiceFeeMode = "FIXED" },
                                            label = { Text("Fixed ($symbol)") },
                                            modifier = Modifier.testTag("tx_fee_mode_fixed_chip")
                                        )
                                    }

                                    if (txServiceFeeMode != "NONE") {
                                        OutlinedTextField(
                                            value = txServiceFeeInputText,
                                            onValueChange = { txServiceFeeInputText = sanitizeNumericInput(it) },
                                            label = {
                                                Text(
                                                    if (txServiceFeeMode == "PERCENTAGE") "Service Charge (%) — e.g. 1.85"
                                                    else "Fixed Service Charge ($symbol $selectedWalletCurrency)"
                                                )
                                            },
                                            placeholder = { Text("0") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(txServiceFeeInputText) { txServiceFeeInputText = it }
                                                .testTag("tx_service_fee_input")
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = pocketColors.overlayCard,
                                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            SummaryKvRow(
                                                "Wallet amount ($selectedWalletCurrency):",
                                                AccountingEngine.formatMoney(effectiveTxPrincipal, symbol)
                                            )
                                            if (effectiveTxServiceFee > 0.0) {
                                                SummaryKvRow(
                                                    "Service charge:",
                                                    AccountingEngine.formatMoney(effectiveTxServiceFee, symbol)
                                                )
                                                SummaryKvRow(
                                                    "Total wallet impact:",
                                                    AccountingEngine.formatMoney(effectiveTxPrincipal + effectiveTxServiceFee, symbol),
                                                    bold = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                if (willBecomeNegative && uiState.settings.warnOnNegativeBalance) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pocketColors.expenseContainer,
                        border = BorderStroke(1.dp, pocketColors.expenseText.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = pocketColors.expenseText
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Heads up: This expense exceeds ${selectedWalletSummary?.wallet?.name}'s available balance (${AccountingEngine.formatMoney(selectedWalletSummary?.currentBalance ?: 0.0, symbol)}) and will result in a negative balance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = pocketColors.expenseText
                            )
                        }
                    }
                }
            }

            // Category Selector + Inline Add Category + Split Transaction Mode
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (recordRefundAsExpenseReversal) "Expense Category to Reverse" else "Category",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (existingTx == null && !recordRefundAsExpenseReversal && filteredCategories.size >= 2) {
                            FilterChip(
                                selected = isSplitMode,
                                onClick = {
                                    isSplitMode = !isSplitMode
                                    if (isSplitMode && splitEntries.isEmpty()) {
                                        val firstCatId = selectedCategoryId.takeIf { it != 0L } ?: filteredCategories.first().id
                                        val secondCatId = filteredCategories.firstOrNull { it.id != firstCatId }?.id ?: firstCatId
                                        val half = if (effectiveTxPrincipal > 0.0) AccountingEngine.roundCurrency(effectiveTxPrincipal / 2.0) else 0.0
                                        val rem = if (effectiveTxPrincipal > 0.0) AccountingEngine.roundCurrency(effectiveTxPrincipal - half) else 0.0
                                        splitEntries.clear()
                                        splitEntries.add(firstCatId to (if (half > 0) half.toString().removeSuffix(".0") else ""))
                                        splitEntries.add(secondCatId to (if (rem > 0) rem.toString().removeSuffix(".0") else ""))
                                    }
                                },
                                label = { Text("Split") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CallSplit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("toggle_split_transaction_chip")
                            )
                        }
                        TextButton(
                            onClick = { showInlineCategoryDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("inline_add_category_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Category")
                        }
                    }
                }

                if (!isSplitMode) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = cat.id == selectedCategoryId,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconForName(cat.iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("select_category_chip_${cat.name}")
                            )
                        }
                    }
                } else {
                    val enteredAmount = evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
                    val splitTotal = splitEntries.sumOf { (_, expr) -> evaluateMathExpression(expr) ?: expr.toDoubleOrNull() ?: 0.0 }
                    val remainingToSplit = AccountingEngine.roundCurrency(enteredAmount - splitTotal)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, pocketColors.cardBorder),
                        modifier = Modifier.padding(top = 6.dp)
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
                                Text(
                                    text = "Split Payment Into Multiple Categories",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (enteredAmount <= 0.0) {
                                        "Total: ${AccountingEngine.formatMoney(splitTotal, symbol)}"
                                    } else if (remainingToSplit == 0.0) {
                                        "✓ Balanced"
                                    } else if (remainingToSplit > 0.0) {
                                        "Remaining: ${AccountingEngine.formatMoney(remainingToSplit, symbol)}"
                                    } else {
                                        "Over by: ${AccountingEngine.formatMoney(-remainingToSplit, symbol)}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingToSplit == 0.0 && enteredAmount > 0.0) {
                                        pocketColors.incomeText
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }

                            splitEntries.forEachIndexed { idx, (entryCatId, entryAmountText) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.overlayCard,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Split #${idx + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (splitEntries.size > 2) {
                                                IconButton(
                                                    onClick = {
                                                        splitEntries.removeAt(idx)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Split",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(filteredCategories, key = { "${idx}_${it.id}" }) { cat ->
                                                FilterChip(
                                                    selected = entryCatId == cat.id,
                                                    onClick = {
                                                        splitEntries[idx] = cat.id to entryAmountText
                                                    },
                                                    label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = iconForName(cat.iconName),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        MathAmountTextField(
                                            value = entryAmountText,
                                            onValueChange = { updated ->
                                                splitEntries[idx] = entryCatId to sanitizeMathExpressionInput(updated)
                                            },
                                            label = { Text("Split Amount ($symbol)") },
                                            placeholder = { Text("0") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(entryAmountText) { cleared ->
                                                    splitEntries[idx] = entryCatId to cleared
                                                }
                                                .testTag("split_amount_input_$idx")
                                        )
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    val defaultCatId = filteredCategories.firstOrNull { cat ->
                                        splitEntries.none { it.first == cat.id }
                                    }?.id ?: filteredCategories.first().id
                                    val remText = if (remainingToSplit > 0.0) remainingToSplit.toString().removeSuffix(".0") else ""
                                    splitEntries.add(defaultCatId to remText)
                                },
                                modifier = Modifier.testTag("add_split_row_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Another Split Category")
                            }
                        }
                    }
                }
            }

            // Merchant / Source / Note with Smart Auto-Complete Chips & Category Auto-Select (Item 10)
            item {
                val recentMerchants = remember(uiState.transactions, selectedType, recordRefundAsExpenseReversal) {
                    val targetType = when {
                        selectedType == TransactionType.INCOME && recordRefundAsExpenseReversal -> TransactionType.EXPENSE_REVERSAL.name
                        selectedType == TransactionType.INCOME -> TransactionType.INCOME.name
                        else -> TransactionType.EXPENSE.name
                    }
                    uiState.transactions
                        .asSequence()
                        .filter {
                            it.parentTransactionId == null &&
                                it.note.isNotBlank() &&
                                (it.type == targetType || (targetType == TransactionType.EXPENSE.name && it.type == TransactionType.EXPENSE.name))
                        }
                        .sortedByDescending { it.date }
                        .distinctBy { it.note.trim().lowercase(Locale.ENGLISH) }
                        .toList()
                }
                val matchingMerchantSuggestions = remember(noteText, recentMerchants) {
                    val q = noteText.trim()
                    if (q.isEmpty()) {
                        recentMerchants.take(6)
                    } else {
                        recentMerchants.filter {
                            it.note.contains(q, ignoreCase = true) &&
                                !it.note.trim().equals(q, ignoreCase = true)
                        }.take(6)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { updated ->
                            val formatted = capitalizeFirstLetter(updated)
                            noteText = formatted
                            if (existingTx == null && formatted.trim().length >= 2) {
                                val exactMatch = recentMerchants.firstOrNull {
                                    it.note.trim().equals(formatted.trim(), ignoreCase = true)
                                }
                                if (exactMatch != null && filteredCategories.any { it.id == exactMatch.categoryId }) {
                                    selectedCategoryId = exactMatch.categoryId
                                }
                            }
                        },
                        label = {
                            Text(if (selectedType == TransactionType.INCOME) "Source / Note (Optional)" else "Merchant / Note (Optional)")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_note_input")
                    )

                    if (matchingMerchantSuggestions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("merchant_autocomplete_chips_row")
                        ) {
                            items(matchingMerchantSuggestions, key = { it.id }) { pastTx ->
                                val catName = filteredCategories.firstOrNull { it.id == pastTx.categoryId }?.name
                                AssistChip(
                                    onClick = {
                                        noteText = pastTx.note.trim()
                                        if (filteredCategories.any { it.id == pastTx.categoryId }) {
                                            selectedCategoryId = pastTx.categoryId
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (catName != null) "${pastTx.note.trim()} · $catName" else pastTx.note.trim(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 34.dp)
                                        .testTag("merchant_chip_${pastTx.note.trim()}")
                                )
                            }
                        }
                    }
                }
            }

            // Receipt / Bill / MFS Screenshot Attachment (Optional)
            item {
                AttachmentPhotoRow(
                    attachmentUri = attachmentUri,
                    onAttachmentChanged = { attachmentUri = it },
                    label = "Attach Receipt, Warranty Slip, or Screenshot (Optional)"
                )
            }

            // Date Quick Selector + Clickable Date Badge opening DatePickerDialog (Item 1)
            item {
                Text("Date", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { selectedDateMillis = System.currentTimeMillis() },
                        label = { Text("Today") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    AssistChip(
                        onClick = {
                            selectedDateMillis = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -1)
                            }.timeInMillis
                        },
                        label = { Text("Yesterday") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showTxDatePicker = true }
                            .testTag("transaction_date_picker_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(selectedDateMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Tags / Event Mode (#SylhetTrip, #EidShopping, #Wedding, etc.)
            item {
                val presetTags = remember(uiState.transactions) {
                    val userTags = uiState.transactions
                        .asSequence()
                        .flatMap { it.tags.split(",") }
                        .map { it.trim().removePrefix("#") }
                        .filter { it.isNotBlank() && !it.equals("recurring", ignoreCase = true) }
                        .distinct()
                        .toList()
                    (userTags + listOf("SylhetTrip", "EidShopping", "Wedding", "Family", "Work")).distinct().take(10)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Event / #Tags (e.g., #SylhetTrip, #EidShopping)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_tags_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetTags) { tag ->
                            val normalizedTag = "#$tag"
                            val currentTags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val isTagSelected = currentTags.any {
                                it.removePrefix("#").equals(tag, ignoreCase = true)
                            }
                            FilterChip(
                                selected = isTagSelected,
                                onClick = {
                                    val updated = if (isTagSelected) {
                                        currentTags.filterNot { it.removePrefix("#").equals(tag, ignoreCase = true) }
                                    } else {
                                        currentTags + normalizedTag
                                    }
                                    tagsText = updated.joinToString(", ")
                                },
                                label = { Text(normalizedTag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    TextButton(
                        onClick = { showMoreDetails = !showMoreDetails },
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(
                            imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reference / Invoice #")
                    }
                    AnimatedVisibility(visible = showMoreDetails) {
                        OutlinedTextField(
                            value = referenceText,
                            onValueChange = { referenceText = capitalizeFirstLetter(it) },
                            label = { Text("Reference (e.g., Salary Month, Invoice #)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Recurring Transaction Setup (Option A: Ask to Confirm)
            if (existingTx == null && !recordRefundAsExpenseReversal && !isSplitMode) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { makeRecurring = !makeRecurring },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventRepeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (selectedType == TransactionType.INCOME) {
                                                "Make this Recurring Income"
                                            } else {
                                                "Make this a Recurring Bill / Subscription"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Notifies you next cycle to Pay (pick wallet) or Remind Tomorrow.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = makeRecurring,
                                    onCheckedChange = { makeRecurring = it },
                                    modifier = Modifier.testTag("form_make_recurring_switch")
                                )
                            }

                            AnimatedVisibility(visible = makeRecurring) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    HorizontalDivider(color = pocketColors.cardBorder)

                                    OutlinedTextField(
                                        value = recurringTitle,
                                        onValueChange = { recurringTitle = capitalizeFirstLetter(it) },
                                        label = {
                                            Text(
                                                if (selectedType == TransactionType.INCOME) {
                                                    "Recurring Title (e.g., Monthly Salary, Rent Income)"
                                                } else {
                                                    "Bill / Subscription Name (e.g., Netflix, WiFi, DESCO)"
                                                }
                                            )
                                        },
                                        placeholder = {
                                            val defaultTitle = noteText.trim().ifBlank {
                                                filteredCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Monthly Bill"
                                            }
                                            Text(defaultTitle)
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("form_recurring_title_input")
                                    )

                                    Text(
                                        text = "Next Cycle Amount Type",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = recurringAmountMode == RecurringAmountMode.FIXED,
                                            onClick = { recurringAmountMode = RecurringAmountMode.FIXED },
                                            label = { Text("Fixed Subscription") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("form_recurring_mode_fixed_chip")
                                        )
                                        FilterChip(
                                            selected = recurringAmountMode == RecurringAmountMode.MANUAL,
                                            onClick = { recurringAmountMode = RecurringAmountMode.MANUAL },
                                            label = { Text("Manual Input") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("form_recurring_mode_manual_chip")
                                        )
                                    }
                                    Text(
                                        text = if (recurringAmountMode == RecurringAmountMode.FIXED) {
                                            "Fixed Subscription: Next time you tap Pay, ${if (parsedAmount > 0) AccountingEngine.formatMoney(parsedAmount, symbol) else "this amount"} will be pre-filled automatically."
                                        } else {
                                            "Manual Input: Next time you tap Pay, you will be asked to enter the exact bill amount before choosing a wallet."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = "Billing Cycle",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(RecurringFrequency.entries) { freq ->
                                            FilterChip(
                                                selected = recurringFrequency == freq,
                                                onClick = { recurringFrequency = freq },
                                                label = {
                                                    Text(
                                                        when (freq) {
                                                            RecurringFrequency.MONTHLY -> "Monthly"
                                                            RecurringFrequency.WEEKLY -> "Weekly"
                                                            RecurringFrequency.QUARTERLY -> "Quarterly"
                                                            RecurringFrequency.YEARLY -> "Yearly"
                                                            RecurringFrequency.CUSTOM_DAYS -> "Custom Days"
                                                        }
                                                    )
                                                },
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            )
                                        }
                                    }

                                    if (recurringFrequency == RecurringFrequency.MONTHLY) {
                                        OutlinedTextField(
                                            value = recurringDayOfMonthText,
                                            onValueChange = { raw ->
                                                val digits = raw.filter { it.isDigit() }.take(2)
                                                recurringDayOfMonthText = digits
                                            },
                                            label = { Text("Billing Day of Month (1–31)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(recurringDayOfMonthText) { recurringDayOfMonthText = it }
                                                .testTag("form_recurring_day_input")
                                        )
                                    } else if (recurringFrequency == RecurringFrequency.CUSTOM_DAYS) {
                                        OutlinedTextField(
                                            value = recurringCustomDaysText,
                                            onValueChange = { raw ->
                                                recurringCustomDaysText = raw.filter { it.isDigit() }.take(3)
                                            },
                                            label = { Text("Repeat Every X Days (e.g., 14, 45, 90)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clearZeroOnFocus(recurringCustomDaysText) { recurringCustomDaysText = it }
                                                .testTag("form_recurring_custom_days_input")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                item {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Save Actions
            item {
                fun handleSave(navigateBackOnSave: Boolean): Boolean {
                    val finalType = when {
                        selectedType == TransactionType.INCOME && recordRefundAsExpenseReversal -> TransactionType.EXPENSE_REVERSAL.name
                        selectedType == TransactionType.INCOME -> TransactionType.INCOME.name
                        else -> TransactionType.EXPENSE.name
                    }
                    if (selectedWalletId == 0L) {
                        validationError = "Please select or create a wallet first."
                        return false
                    }

                    if (isSplitMode && existingTx == null) {
                        val validSplits = splitEntries.mapIndexedNotNull { idx, (catId, exprText) ->
                            val splitAmt = evaluateMathExpression(exprText) ?: exprText.toDoubleOrNull() ?: 0.0
                            if (splitAmt > 0.0 && catId != 0L) {
                                val catName = filteredCategories.firstOrNull { it.id == catId }?.name ?: "Split #${idx + 1}"
                                val splitNote = if (noteText.isNotBlank()) "${noteText.trim()} ($catName)" else "Split: $catName"
                                TransactionEntity(
                                    id = 0L,
                                    type = finalType,
                                    amount = AccountingEngine.roundCurrency(splitAmt),
                                    date = selectedDateMillis,
                                    walletId = selectedWalletId,
                                    categoryId = catId,
                                    note = splitNote,
                                    reference = referenceText.trim(),
                                    tags = listOfNotNull(tagsText.trim().takeIf { it.isNotBlank() }, "split").joinToString(", "),
                                    attachmentUri = attachmentUri
                                )
                            } else null
                        }
                        if (validSplits.size < 2) {
                            validationError = "Please enter valid positive amounts for at least 2 split categories."
                            return false
                        }
                        performPocketHaptic(view)
                        viewModel.saveSplitTransactions(
                            splitTransactions = validSplits,
                            serviceFeeAmount = effectiveTxServiceFee,
                            navigateBackOnSave = navigateBackOnSave
                        )
                        return true
                    }

                    val amt = effectiveTxPrincipal
                    if (amt <= 0.0) {
                        validationError = "Please enter a positive numeric amount greater than 0."
                        return false
                    }
                    if (selectedCategoryId == 0L) {
                        validationError = "Please select a category."
                        return false
                    }
                    val tx = TransactionEntity(
                        id = existingTx?.id ?: 0L,
                        type = finalType,
                        amount = AccountingEngine.roundCurrency(amt),
                        date = selectedDateMillis,
                        walletId = selectedWalletId,
                        categoryId = selectedCategoryId,
                        note = noteText.trim(),
                        reference = referenceText.trim(),
                        tags = if (makeRecurring && existingTx == null) {
                            listOfNotNull(tagsText.trim().takeIf { it.isNotBlank() }, "recurring").joinToString(", ")
                        } else {
                            tagsText.trim()
                        },
                        transferId = existingTx?.transferId,
                        loanActivityId = existingTx?.loanActivityId,
                        parentTransactionId = existingTx?.parentTransactionId,
                        attachmentUri = attachmentUri
                    )

                    val recurringEntity = if (makeRecurring && existingTx == null && !recordRefundAsExpenseReversal) {
                        val categoryName = filteredCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Recurring"
                        val resolvedTitle = recurringTitle.trim().ifBlank {
                            noteText.trim().ifBlank { categoryName }
                        }
                        val dayOfMonth = (recurringDayOfMonthText.toIntOrNull() ?: Calendar.getInstance().apply {
                            timeInMillis = selectedDateMillis
                        }.get(Calendar.DAY_OF_MONTH)).coerceIn(1, 31)
                        val customDays = (recurringCustomDaysText.toIntOrNull() ?: 30).coerceIn(1, 365)
                        val nextDueMillis = PocketRepository.calculateNextRecurringDueDate(
                            fromMillis = selectedDateMillis,
                            frequency = recurringFrequency.name,
                            dayOfMonth = dayOfMonth,
                            customIntervalDays = customDays
                        )
                        RecurringTransactionEntity(
                            title = resolvedTitle,
                            type = finalType,
                            amountMode = recurringAmountMode.name,
                            amount = AccountingEngine.roundCurrency(amt),
                            categoryId = selectedCategoryId,
                            defaultWalletId = selectedWalletId,
                            frequency = recurringFrequency.name,
                            dayOfMonth = dayOfMonth,
                            customIntervalDays = customDays,
                            nextDueDate = nextDueMillis,
                            lastPaidDate = selectedDateMillis,
                            note = noteText.trim(),
                            isActive = true
                        )
                    } else {
                        null
                    }
                    performPocketHaptic(view)
                    viewModel.saveTransaction(
                        transaction = tx,
                        navigateBackOnSave = navigateBackOnSave,
                        recurringSetup = recurringEntity,
                        serviceFeeAmount = effectiveTxServiceFee
                    )
                    return true
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { handleSave(navigateBackOnSave = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_transaction_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (existingTx != null) "Save Changes" else if (isSplitMode) "Save Split Transactions" else "Save")
                    }

                    if (existingTx == null) {
                        OutlinedButton(
                            onClick = {
                                if (handleSave(navigateBackOnSave = false)) {
                                    amountText = ""
                                    noteText = ""
                                    referenceText = ""
                                    makeRecurring = false
                                    recurringTitle = ""
                                    txServiceFeeInputText = ""
                                    attachmentUri = ""
                                    isSplitMode = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_and_add_another_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save & Add Another")
                        }
                    }
                }
            }
        }
        }
    }

    if (showInlineCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        val targetCatType = if (selectedType == TransactionType.INCOME && !recordRefundAsExpenseReversal) "INCOME" else "EXPENSE"
        AlertDialog(
            onDismissRequest = { showInlineCategoryDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Add $targetCatType Category") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = capitalizeFirstLetter(it) },
                    label = { Text("Category Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            viewModel.saveCategory(
                                CategoryEntity(
                                    name = newCatName.trim(),
                                    type = targetCatType,
                                    iconName = if (targetCatType == "INCOME") "TRENDING_UP" else "SHOPPING_BAG",
                                    colorHex = if (targetCatType == "INCOME") PocketRepository.DEFAULT_INCOME_COLOR_HEX else PocketRepository.DEFAULT_EXPENSE_COLOR_HEX
                                )
                            ) { createdId ->
                                selectedCategoryId = createdId
                            }
                            showInlineCategoryDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInlineCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showTxDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showTxDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtc ->
                            val nowCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtc
                            }
                            nowCal.set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                            selectedDateMillis = nowCal.timeInMillis
                        }
                        showTxDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTxDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFormScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    editTransferId: Long?,
    preselectedSourceWalletId: Long?
) {
    val existingTransfer = remember(editTransferId, uiState.transfers) {
        uiState.transfers.firstOrNull { it.id == editTransferId }
    }
    val wallets = uiState.activeWalletSummaries
    val symbol = uiState.settings.currencySymbol
    val pocketColors = LocalPocketColors.current

    var sourceWalletId by remember(existingTransfer, preselectedSourceWalletId, wallets) {
        mutableStateOf(
            existingTransfer?.sourceWalletId
                ?: preselectedSourceWalletId
                ?: wallets.firstOrNull()?.wallet?.id
                ?: 0L
        )
    }
    var destWalletId by remember(existingTransfer, wallets, sourceWalletId) {
        mutableStateOf(
            existingTransfer?.destinationWalletId
                ?: wallets.firstOrNull { it.wallet.id != sourceWalletId }?.wallet?.id
                ?: 0L
        )
    }
    var principalText by remember(existingTransfer) {
        mutableStateOf(formatNumericFieldInitialValue(existingTransfer?.principalAmount))
    }
    var feeMode by remember(existingTransfer) {
        mutableStateOf(
            existingTransfer?.feeMode?.let { runCatching { TransferFeeMode.valueOf(it) }.getOrNull() }
                ?: TransferFeeMode.NONE
        )
    }
    var fixedFeeText by remember(existingTransfer) {
        mutableStateOf(
            if ((existingTransfer?.fixedFeeInput ?: 0.0) > 0.0) formatNumericFieldInitialValue(existingTransfer!!.fixedFeeInput)
            else if (existingTransfer?.feeMode == TransferFeeMode.MANUAL.name && existingTransfer.feeAmount > 0.0) formatNumericFieldInitialValue(existingTransfer.feeAmount)
            else ""
        )
    }
    var percentageText by remember(existingTransfer) {
        mutableStateOf(
            if ((existingTransfer?.percentageInput ?: 0.0) > 0.0) formatNumericFieldInitialValue(existingTransfer!!.percentageInput) else ""
        )
    }
    var noteText by remember(existingTransfer) { mutableStateOf(existingTransfer?.note ?: "") }
    var selectedTransferDateMillis by remember(existingTransfer) {
        mutableStateOf(existingTransfer?.date ?: System.currentTimeMillis())
    }
    var showTransferDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current

    val principal = remember(principalText) {
        evaluateMathExpression(principalText) ?: principalText.toDoubleOrNull() ?: 0.0
    }
    val fixedFee = fixedFeeText.toDoubleOrNull() ?: 0.0
    val pctRate = percentageText.toDoubleOrNull() ?: 0.0

    val calculatedFee = remember(principal, feeMode, fixedFee, pctRate) {
        AccountingEngine.calculateTransferFee(
            principalAmount = principal,
            feeMode = feeMode,
            fixedFeeInput = fixedFee,
            percentageInput = pctRate
        )
    }
    val totalSourceDebit = AccountingEngine.roundCurrency(principal + calculatedFee)
    val sourceSummary = wallets.firstOrNull { it.wallet.id == sourceWalletId }
    val destSummary = wallets.firstOrNull { it.wallet.id == destWalletId }
    val sourceCurrency = sourceSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val destCurrency = destSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val sourceSymbol = AccountingEngine.currencySymbolForCode(sourceCurrency)
    val destSymbol = AccountingEngine.currencySymbolForCode(destCurrency)
    val isCrossCurrency = !sourceCurrency.equals(destCurrency, ignoreCase = true)

    // Cross-currency conversion state ("RATE" vs "FIXED_AMOUNT")
    var conversionInputMode by remember(existingTransfer) {
        mutableStateOf(
            if ((existingTransfer?.destinationAmount ?: 0.0) > 0.0 && (existingTransfer?.conversionRate ?: 1.0) == 1.0) {
                "FIXED_AMOUNT"
            } else {
                "RATE"
            }
        )
    }
    var conversionRateText by remember(existingTransfer) {
        mutableStateOf(
            if (existingTransfer != null && existingTransfer.conversionRate > 0.0 && existingTransfer.conversionRate != 1.0) {
                formatNumericFieldInitialValue(existingTransfer.conversionRate)
            } else {
                ""
            }
        )
    }
    var fixedDestAmountText by remember(existingTransfer) {
        mutableStateOf(
            if (existingTransfer != null && existingTransfer.destinationAmount > 0.0) {
                formatNumericFieldInitialValue(existingTransfer.destinationAmount)
            } else {
                ""
            }
        )
    }

    val effectiveDestCredit = remember(isCrossCurrency, principal, conversionInputMode, conversionRateText, fixedDestAmountText) {
        if (!isCrossCurrency) {
            principal
        } else if (conversionInputMode == "FIXED_AMOUNT") {
            val fixedVal = fixedDestAmountText.toDoubleOrNull() ?: 0.0
            if (fixedVal > 0.0) AccountingEngine.roundCurrency(fixedVal) else principal
        } else {
            val rateVal = conversionRateText.toDoubleOrNull() ?: 0.0
            if (rateVal > 0.0) AccountingEngine.roundCurrency(principal * rateVal) else principal
        }
    }
    val effectiveConversionRate = remember(isCrossCurrency, principal, effectiveDestCredit, conversionInputMode, conversionRateText) {
        if (!isCrossCurrency) {
            1.0
        } else if (conversionInputMode == "RATE") {
            val rateVal = conversionRateText.toDoubleOrNull() ?: 0.0
            if (rateVal > 0.0) rateVal else 1.0
        } else {
            if (principal > 0.0 && effectiveDestCredit > 0.0) {
                effectiveDestCredit / principal
            } else {
                1.0
            }
        }
    }

    val sourceBalanceAfter = (sourceSummary?.currentBalance ?: 0.0) - totalSourceDebit

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
                title = if (existingTransfer != null) "Edit Wallet Transfer" else "Transfer Between Wallets",
                onBack = { viewModel.navigateBack() },
                actions = {
                    if (existingTransfer == null) {
                        SmsPasteButtonAndDialog(
                            wallets = uiState.wallets.filter { !it.isArchived },
                            onSmsParsed = { parsed ->
                                if (parsed.amount > 0.0) {
                                    principalText = parsed.amount.toString().removeSuffix(".0")
                                }
                                if (parsed.feeAmount > 0.0) {
                                    feeMode = TransferFeeMode.MANUAL
                                    fixedFeeText = parsed.feeAmount.toString().removeSuffix(".0")
                                }
                                if (parsed.matchedWalletId != null) {
                                    sourceWalletId = parsed.matchedWalletId
                                    if (destWalletId == sourceWalletId) {
                                        destWalletId = wallets.firstOrNull { it.wallet.id != sourceWalletId }?.wallet?.id ?: 0L
                                    }
                                }
                                if (parsed.summaryNote.isNotBlank() && noteText.isBlank()) {
                                    noteText = if (parsed.reference.isNotBlank()) "${parsed.summaryNote} (TrxID: ${parsed.reference})" else parsed.summaryNote
                                }
                            }
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Source Wallet
                item {
                    Text("From Wallet (Source)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.wallet.id }) { summary ->
                            val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                            FilterChip(
                                selected = summary.wallet.id == sourceWalletId,
                                onClick = {
                                    sourceWalletId = summary.wallet.id
                                    if (destWalletId == summary.wallet.id) {
                                        destWalletId = wallets.firstOrNull { it.wallet.id != sourceWalletId }?.wallet?.id ?: 0L
                                    }
                                },
                                label = {
                                    Text("${summary.wallet.name} · ${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)}")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("transfer_from_wallet_${summary.wallet.name}")
                            )
                        }
                    }
                }

                // Swap Button + Destination Wallet
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To Wallet (Destination)", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                val tmp = sourceWalletId
                                sourceWalletId = destWalletId
                                destWalletId = tmp
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Swap")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.wallet.id }) { summary ->
                            val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                            FilterChip(
                                selected = summary.wallet.id == destWalletId,
                                onClick = { destWalletId = summary.wallet.id },
                                label = {
                                    Text("${summary.wallet.name} · ${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)}")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("transfer_to_wallet_${summary.wallet.name}")
                            )
                        }
                    }
                }

                // Amount to Transfer + Built-in Mini Calculator
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MathAmountTextField(
                            value = principalText,
                            onValueChange = {
                                principalText = sanitizeMathExpressionInput(it)
                                validationError = null
                            },
                            label = { Text("Amount to transfer ($sourceSymbol $sourceCurrency) · e.g. 500+250") },
                            placeholder = { Text("0") },
                            textStyle = TabularMoneyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(principalText) { principalText = it }
                                .testTag("transfer_amount_input")
                        )
                        MiniCalculatorBar(
                            expression = principalText,
                            currencySymbol = sourceSymbol,
                            onExpressionChange = {
                                principalText = it
                                validationError = null
                            }
                        )
                    }
                }

                // Automatic Cross-Currency Conversion Section when sourceCurrency != destCurrency
                if (isCrossCurrency) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_cross_currency_card"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Currency Conversion ($sourceCurrency → $destCurrency)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Input the conversion rate or fixed converted amount in $destCurrency:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = conversionInputMode == "RATE",
                                        onClick = { conversionInputMode = "RATE" },
                                        label = { Text("Conversion Rate") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("transfer_conv_mode_rate_chip")
                                    )
                                    FilterChip(
                                        selected = conversionInputMode == "FIXED_AMOUNT",
                                        onClick = { conversionInputMode = "FIXED_AMOUNT" },
                                        label = { Text("Fixed Converted Amount") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("transfer_conv_mode_fixed_chip")
                                    )
                                }

                                if (conversionInputMode == "RATE") {
                                    OutlinedTextField(
                                        value = conversionRateText,
                                        onValueChange = {
                                            conversionRateText = sanitizeNumericInput(it)
                                            validationError = null
                                        },
                                        label = { Text("Conversion Rate (1 $sourceCurrency = ? $destCurrency)") },
                                        placeholder = { Text(if (sourceCurrency == "USD" && destCurrency == "BDT") "122" else "1.0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clearZeroOnFocus(conversionRateText) { conversionRateText = it }
                                            .testTag("transfer_conversion_rate_input")
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = fixedDestAmountText,
                                        onValueChange = {
                                            fixedDestAmountText = sanitizeNumericInput(it)
                                            validationError = null
                                        },
                                        label = { Text("Fixed Converted Amount ($destSymbol $destCurrency)") },
                                        placeholder = { Text("0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clearZeroOnFocus(fixedDestAmountText) { fixedDestAmountText = it }
                                            .testTag("transfer_fixed_dest_amount_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // Transfer / Service Charge Mode
                item {
                    Text("Service / Transfer charge ($sourceSymbol $sourceCurrency)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransferFeeMode.entries.forEach { mode ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { feeMode = mode }
                                    .testTag("fee_mode_${mode.name}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (feeMode == mode) pocketColors.surfaceSelected else pocketColors.overlayCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (feeMode == mode) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = feeMode == mode,
                                        onClick = { feeMode = mode }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (feeMode == TransferFeeMode.MANUAL || feeMode == TransferFeeMode.FIXED_PLUS_PERCENTAGE) {
                        OutlinedTextField(
                            value = fixedFeeText,
                            onValueChange = { fixedFeeText = sanitizeNumericInput(it) },
                            label = {
                                Text(if (feeMode == TransferFeeMode.MANUAL) "Manual service charge ($sourceSymbol)" else "Fixed service fee ($sourceSymbol)")
                            },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(fixedFeeText) { fixedFeeText = it }
                                .testTag("transfer_fixed_fee_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (feeMode == TransferFeeMode.PERCENTAGE || feeMode == TransferFeeMode.FIXED_PLUS_PERCENTAGE) {
                        OutlinedTextField(
                            value = percentageText,
                            onValueChange = { percentageText = sanitizeNumericInput(it) },
                            label = { Text("Service charge percentage (%) — e.g. 1.5 or 1.85") },
                            placeholder = { Text("1.85") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(percentageText) { percentageText = it }
                                .testTag("transfer_percentage_fee_input")
                        )
                    }
                }

                // Live Calculation Preview Box
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = pocketColors.overlayTransferContainer,
                        border = BorderStroke(1.dp, pocketColors.transferText.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Transfer Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                color = pocketColors.transferText
                            )
                            SummaryKvRow(
                                "Destination credit (${destSummary?.wallet?.name ?: "To"}):",
                                "+${AccountingEngine.formatMoney(effectiveDestCredit, destSymbol)} $destCurrency"
                            )
                            if (isCrossCurrency) {
                                SummaryKvRow(
                                    "Conversion rate:",
                                    "1 $sourceCurrency = ${String.format(Locale.ENGLISH, "%.4f", effectiveConversionRate)} $destCurrency"
                                )
                            }
                            SummaryKvRow(
                                "Service / transfer fee (recorded as Expense):",
                                "${AccountingEngine.formatMoney(calculatedFee, sourceSymbol)} $sourceCurrency"
                            )
                            HorizontalDivider(color = pocketColors.transferText.copy(alpha = 0.2f))
                            SummaryKvRow(
                                "Total source debit (${sourceSummary?.wallet?.name ?: "From"}):",
                                "−${AccountingEngine.formatMoney(totalSourceDebit, sourceSymbol)} $sourceCurrency",
                                bold = true
                            )
                            SummaryKvRow(
                                "Source balance after transfer:",
                                "${AccountingEngine.formatMoney(sourceBalanceAfter, sourceSymbol)} $sourceCurrency"
                            )
                        }
                    }

                    if (sourceBalanceAfter < -0.005 && uiState.settings.warnOnNegativeBalance) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Warning: Source wallet lacks sufficient funds and will have a negative balance after this transfer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = pocketColors.expenseText
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Note (e.g., Cash out, Metro top-up)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Date Quick Selector + Clickable Date Badge opening DatePickerDialog (Item 1)
                item {
                    Text("Date", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { selectedTransferDateMillis = System.currentTimeMillis() },
                            label = { Text("Today") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        AssistChip(
                            onClick = {
                                selectedTransferDateMillis = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, -1)
                                }.timeInMillis
                            },
                            label = { Text("Yesterday") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showTransferDatePicker = true }
                                .testTag("transfer_date_picker_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Transfer Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(selectedTransferDateMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    item {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            when {
                                sourceWalletId == 0L || destWalletId == 0L ->
                                    validationError = "Please select both source and destination wallets."
                                sourceWalletId == destWalletId ->
                                    validationError = "Source and destination wallets must differ."
                                principal <= 0.0 ->
                                    validationError = "Transfer amount must be greater than zero."
                                isCrossCurrency && effectiveDestCredit <= 0.0 ->
                                    validationError = "Please enter a valid conversion rate or converted destination amount."
                                calculatedFee < 0.0 ->
                                    validationError = "Transfer fee cannot be negative."
                                else -> {
                                    performPocketHaptic(view)
                                    viewModel.saveTransfer(
                                        transfer = TransferEntity(
                                            id = existingTransfer?.id ?: 0L,
                                            sourceWalletId = sourceWalletId,
                                            destinationWalletId = destWalletId,
                                            principalAmount = AccountingEngine.roundCurrency(principal),
                                            destinationAmount = if (isCrossCurrency) effectiveDestCredit else 0.0,
                                            conversionRate = if (isCrossCurrency) effectiveConversionRate else 1.0,
                                            feeMode = feeMode.name,
                                            fixedFeeInput = fixedFee.coerceAtLeast(0.0),
                                            percentageInput = pctRate.coerceAtLeast(0.0),
                                            feeAmount = calculatedFee,
                                            date = selectedTransferDateMillis,
                                            note = noteText.trim()
                                        ),
                                        sourceWalletName = sourceSummary?.wallet?.name ?: "Source",
                                        destWalletName = destSummary?.wallet?.name ?: "Destination"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_transfer_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (existingTransfer != null) "Update Transfer" else "Confirm Transfer")
                    }
                }
            }
        }
    }

    if (showTransferDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTransferDateMillis)
        DatePickerDialog(
            onDismissRequest = { showTransferDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtc ->
                            val nowCal = Calendar.getInstance().apply { timeInMillis = selectedTransferDateMillis }
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtc
                            }
                            nowCal.set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                            selectedTransferDateMillis = nowCal.timeInMillis
                        }
                        showTransferDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SummaryKvRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = if (bold) TabularMoneyMedium else TabularMoneySmall
        )
    }
}

private enum class HistoryDateRange(val label: String) {
    THIS_MONTH("Selected Month"),
    LAST_MONTH("Last Month"),
    LAST_30_DAYS("30 Days"),
    ALL_TIME("All Time"),
    CUSTOM("Custom Range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    initialTypeFilter: LedgerEntryKind?,
    initialCategoryId: Long?,
    initialWalletId: Long?
) {
    var searchQuery by remember { mutableStateOf("") }
    // Automatically sync to THIS_MONTH (Selected Month) when drilling down from Report or Home monthly totals (Item 5)
    var dateRange by remember(initialCategoryId, initialTypeFilter) {
        mutableStateOf(
            if (initialCategoryId != null || initialTypeFilter != null) {
                HistoryDateRange.THIS_MONTH
            } else {
                HistoryDateRange.ALL_TIME
            }
        )
    }
    val defaultMonthBounds = remember(uiState.selectedYear, uiState.selectedMonth) {
        AccountingEngine.getMonthBounds(uiState.selectedYear, uiState.selectedMonth)
    }
    var customStartMillis by remember { mutableStateOf(defaultMonthBounds.first) }
    var customEndMillis by remember { mutableStateOf(defaultMonthBounds.second) }
    var showCustomStartPicker by remember { mutableStateOf(false) }
    var showCustomEndPicker by remember { mutableStateOf(false) }

    var typeFilter by remember(initialTypeFilter) { mutableStateOf(initialTypeFilter) }
    var walletFilterId by remember(initialWalletId) { mutableStateOf(initialWalletId) }
    var categoryFilterId by remember(initialCategoryId) { mutableStateOf(initialCategoryId) }
    var minAmountText by remember { mutableStateOf("") }
    var maxAmountText by remember { mutableStateOf("") }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var selectedEntryForModal by remember { mutableStateOf<UnifiedLedgerEntry?>(null) }
    var selectedEntryKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val symbol = uiState.settings.currencySymbol
    val pocketColors = LocalPocketColors.current
    val historyBlur by animateDpAsState(
        targetValue = if (selectedEntryForModal != null || showBulkCategoryDialog) 14.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "history_blur"
    )

    val allAvailableTags = remember(uiState.unifiedLedger) {
        uiState.unifiedLedger
            .asSequence()
            .flatMap { it.tags.split(",") }
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() && !it.equals("recurring", ignoreCase = true) }
            .distinct()
            .toList()
    }

    val filteredEntries = remember(
        uiState.unifiedLedger,
        searchQuery,
        dateRange,
        customStartMillis,
        customEndMillis,
        typeFilter,
        walletFilterId,
        categoryFilterId,
        selectedTagFilter,
        minAmountText,
        maxAmountText,
        uiState.selectedYear,
        uiState.selectedMonth
    ) {
        val now = System.currentTimeMillis()
        val (startMillis, endMillis) = when (dateRange) {
            HistoryDateRange.THIS_MONTH -> AccountingEngine.getMonthBounds(uiState.selectedYear, uiState.selectedMonth)
            HistoryDateRange.LAST_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, uiState.selectedYear)
                    set(Calendar.MONTH, uiState.selectedMonth)
                    add(Calendar.MONTH, -1)
                }
                AccountingEngine.getMonthBounds(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            }
            HistoryDateRange.LAST_30_DAYS -> (now - 30L * 24L * 3600L * 1000L) to Long.MAX_VALUE
            HistoryDateRange.ALL_TIME -> 0L to Long.MAX_VALUE
            HistoryDateRange.CUSTOM -> {
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = minOf(customStartMillis, customEndMillis)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = maxOf(customStartMillis, customEndMillis)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
        }
        val minAmt = minAmountText.toDoubleOrNull()
        val maxAmt = maxAmountText.toDoubleOrNull()
        val q = searchQuery.trim().lowercase(Locale.ENGLISH)

        uiState.unifiedLedger.filter { e ->
            val inDate = e.date in startMillis..endMillis
            val inType = typeFilter == null || e.kind == typeFilter
            val inWallet = walletFilterId == null || e.walletId == walletFilterId || e.secondaryWalletId == walletFilterId
            val inCat = categoryFilterId == null || e.categoryId == categoryFilterId
            val inTag = selectedTagFilter == null || e.tags.split(",").any {
                it.trim().removePrefix("#").equals(selectedTagFilter, ignoreCase = true)
            }
            val absAmt = kotlin.math.abs(e.signedAmount)
            val inMin = minAmt == null || absAmt >= minAmt
            val inMax = maxAmt == null || absAmt <= maxAmt
            val inQuery = q.isEmpty() ||
                e.title.lowercase(Locale.ENGLISH).contains(q) ||
                e.subtitle.lowercase(Locale.ENGLISH).contains(q) ||
                e.categoryName.lowercase(Locale.ENGLISH).contains(q) ||
                e.walletName.lowercase(Locale.ENGLISH).contains(q) ||
                e.reference.lowercase(Locale.ENGLISH).contains(q) ||
                e.tags.lowercase(Locale.ENGLISH).contains(q)
            inDate && inType && inWallet && inCat && inTag && inMin && inMax && inQuery
        }.sortedWith(compareByDescending<UnifiedLedgerEntry> { it.date }.thenByDescending { it.rawId })
    }

    val selectedEntriesList = remember(filteredEntries, selectedEntryKeys) {
        filteredEntries.filter { selectedEntryKeys.contains(it.uniqueKey) }
    }

    val groupedByDay = remember(filteredEntries, uiState.settings.dateFormatPattern) {
        filteredEntries.groupBy { formatRelativeDate(it.date, uiState.settings.dateFormatPattern) }
    }

    val isSubScreenFilter = initialTypeFilter != null || initialCategoryId != null || initialWalletId != null
    val hasActiveExtraFilters = walletFilterId != null || categoryFilterId != null || selectedTagFilter != null || minAmountText.isNotBlank() || maxAmountText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(historyBlur)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = if (selectedEntryKeys.isNotEmpty()) "${selectedEntryKeys.size} Selected" else "History",
            onBack = if (isSubScreenFilter) ({ viewModel.navigateBack() }) else null,
            onLogoClick = if (!isSubScreenFilter) ({ viewModel.navigateTo(PocketScreen.Settings) }) else null,
            actions = {
                if (selectedEntryKeys.isNotEmpty()) {
                    TextButton(onClick = { selectedEntryKeys = emptySet() }) {
                        Text("Clear")
                    }
                }
                IconButton(
                    onClick = { showAdvancedFilters = !showAdvancedFilters },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Toggle filters",
                        tint = if (showAdvancedFilters || hasActiveExtraFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        )

        // Multi-Select Bulk Action Bar (Long-Press to Select Multiple Items)
        AnimatedVisibility(visible = selectedEntryKeys.isNotEmpty()) {
            val selectedNet = selectedEntriesList.sumOf { it.signedAmount }
            val selectedAbsTotal = selectedEntriesList.sumOf { kotlin.math.abs(it.signedAmount) }
            val canBulkChangeCat = selectedEntriesList.any {
                it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.INCOME
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("history_bulk_action_bar"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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
                            text = "${selectedEntriesList.size} selected · Sum ${AccountingEngine.formatMoney(selectedAbsTotal, symbol, includeDecimals = false)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Net: ${AccountingEngine.formatMoney(selectedNet, symbol, showSign = true, includeDecimals = false)}",
                            style = TabularMoneySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (canBulkChangeCat) {
                            OutlinedButton(
                                onClick = { showBulkCategoryDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("bulk_change_category_button")
                            ) {
                                Text("Category", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Button(
                            onClick = {
                                performPocketHaptic(view)
                                viewModel.bulkDeleteLedgerEntries(selectedEntriesList)
                                selectedEntryKeys = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("bulk_delete_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Minimal Search + Filter Row (Item 6)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = capitalizeFirstLetter(it) },
                    placeholder = { Text("Search history or #tags...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )
            }

            // Minimal Combined Period & Type Filter Row
            item {
                val monthNameLabel = remember(uiState.selectedYear, uiState.selectedMonth) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, uiState.selectedYear)
                        set(Calendar.MONTH, uiState.selectedMonth)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(HistoryDateRange.entries) { range ->
                        val chipText = if (range == HistoryDateRange.THIS_MONTH) monthNameLabel else range.label
                        FilterChip(
                            selected = dateRange == range,
                            onClick = { dateRange = range },
                            label = { Text(chipText, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 38.dp)
                        )
                    }
                }
            }

            if (dateRange == HistoryDateRange.CUSTOM) {
                item {
                    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showCustomStartPicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_custom_start_date_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From: ${dateFmt.format(Date(customStartMillis))}", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { showCustomEndPicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_custom_end_date_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("To: ${dateFmt.format(Date(customEndMillis))}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Minimal Type Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { typeFilter = null },
                            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == LedgerEntryKind.EXPENSE,
                            onClick = { typeFilter = if (typeFilter == LedgerEntryKind.EXPENSE) null else LedgerEntryKind.EXPENSE },
                            label = { Text("Expense", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == LedgerEntryKind.INCOME,
                            onClick = { typeFilter = if (typeFilter == LedgerEntryKind.INCOME) null else LedgerEntryKind.INCOME },
                            label = { Text("Income", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == LedgerEntryKind.TRANSFER,
                            onClick = { typeFilter = if (typeFilter == LedgerEntryKind.TRANSFER) null else LedgerEntryKind.TRANSFER },
                            label = { Text("Transfer", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == LedgerEntryKind.LOAN_PRINCIPAL,
                            onClick = { typeFilter = if (typeFilter == LedgerEntryKind.LOAN_PRINCIPAL) null else LedgerEntryKind.LOAN_PRINCIPAL },
                            label = { Text("Loan", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    if (categoryFilterId != null) {
                        val activeCatName = uiState.categories.firstOrNull { it.id == categoryFilterId }?.name ?: "Category"
                        item {
                            InputChip(
                                selected = true,
                                onClick = { categoryFilterId = null },
                                label = { Text("$activeCatName ✕", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.heightIn(min = 36.dp)
                            )
                        }
                    }
                }
            }

            // Advanced Filters (Wallet, Tags, Category, Amount Range — hidden behind top-right filter icon)
            if (showAdvancedFilters) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Wallet", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = walletFilterId == null,
                                        onClick = { walletFilterId = null },
                                        label = { Text("All Wallets") }
                                    )
                                }
                                items(uiState.wallets, key = { it.id }) { w ->
                                    FilterChip(
                                        selected = walletFilterId == w.id,
                                        onClick = { walletFilterId = if (walletFilterId == w.id) null else w.id },
                                        label = { Text(w.name) }
                                    )
                                }
                            }

                            if (allAvailableTags.isNotEmpty()) {
                                Text("Events / #Tags", style = MaterialTheme.typography.labelLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedTagFilter == null,
                                            onClick = { selectedTagFilter = null },
                                            label = { Text("All") }
                                        )
                                    }
                                    items(allAvailableTags) { tag ->
                                        FilterChip(
                                            selected = selectedTagFilter.equals(tag, ignoreCase = true),
                                            onClick = {
                                                selectedTagFilter = if (selectedTagFilter.equals(tag, ignoreCase = true)) null else tag
                                            },
                                            label = { Text("#$tag") },
                                            modifier = Modifier.testTag("history_tag_chip_$tag")
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = minAmountText,
                                    onValueChange = { minAmountText = sanitizeNumericInput(it) },
                                    label = { Text("Min ($symbol)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clearZeroOnFocus(minAmountText) { minAmountText = it }
                                )
                                OutlinedTextField(
                                    value = maxAmountText,
                                    onValueChange = { maxAmountText = sanitizeNumericInput(it) },
                                    label = { Text("Max ($symbol)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clearZeroOnFocus(maxAmountText) { maxAmountText = it }
                                )
                            }

                            if (hasActiveExtraFilters) {
                                TextButton(
                                    onClick = {
                                        walletFilterId = null
                                        categoryFilterId = null
                                        selectedTagFilter = null
                                        minAmountText = ""
                                        maxAmountText = ""
                                    }
                                ) {
                                    Text("Reset Extra Filters")
                                }
                            }
                        }
                    }
                }
            }

            if (groupedByDay.isEmpty()) {
                item {
                    EmptyStateBox(
                        icon = Icons.Default.SearchOff,
                        title = "No matching transactions",
                        subtitle = "Try clearing your search query or adjusting the date/wallet filters."
                    )
                }
            } else {
                groupedByDay.forEach { (dayLabel, dayEntries) ->
                    item(key = "header_$dayLabel") {
                        val dayNet = dayEntries
                            .filter { it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.EXPENSE_REVERSAL }
                            .sumOf { it.signedAmount }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Daily net: ${AccountingEngine.formatMoney(dayNet, symbol, showSign = true, includeDecimals = false)}",
                                style = TabularMoneySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(dayEntries, key = { it.uniqueKey }) { entry ->
                        val entryWalletCurrency = uiState.wallets.firstOrNull { it.id == entry.walletId }?.currency ?: "BDT"
                        val entrySymbol = AccountingEngine.currencySymbolForCode(entryWalletCurrency)
                        val isSelected = selectedEntryKeys.contains(entry.uniqueKey)
                        LedgerEntryRowCard(
                            entry = entry,
                            currencySymbol = entrySymbol,
                            dateFormatPattern = uiState.settings.dateFormatPattern,
                            isSelected = isSelected,
                            selectionMode = selectedEntryKeys.isNotEmpty(),
                            onClick = {
                                if (selectedEntryKeys.isNotEmpty()) {
                                    performPocketHaptic(view)
                                    selectedEntryKeys = if (isSelected) {
                                        selectedEntryKeys - entry.uniqueKey
                                    } else {
                                        selectedEntryKeys + entry.uniqueKey
                                    }
                                } else {
                                    selectedEntryForModal = entry
                                }
                            },
                            onLongClick = {
                                performPocketHaptic(view)
                                selectedEntryKeys = if (isSelected) {
                                    selectedEntryKeys - entry.uniqueKey
                                } else {
                                    selectedEntryKeys + entry.uniqueKey
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBulkCategoryDialog) {
        val txIds = selectedEntriesList
            .filter { it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE_REVERSAL }
            .map { it.rawId }
        AlertDialog(
            onDismissRequest = { showBulkCategoryDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Change Category (${txIds.size} items)") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.categories.filter { !it.isArchived }, key = { it.id }) { cat ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    performPocketHaptic(view)
                                    viewModel.bulkUpdateTransactionsCategory(txIds, cat.id)
                                    showBulkCategoryDialog = false
                                    selectedEntryKeys = emptySet()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconBadgeCircle(iconName = cat.iconName, colorHex = cat.colorHex, size = 32.dp, iconSize = 16.dp)
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedEntryForModal?.let { entry ->
        val entryWalletCurrency = uiState.wallets.firstOrNull { it.id == entry.walletId }?.currency ?: "BDT"
        val entrySymbol = AccountingEngine.currencySymbolForCode(entryWalletCurrency)
        LedgerEntryActionDialog(
            entry = entry,
            currencySymbol = entrySymbol,
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
                        PocketScreen.BalanceAdjustment(preselectedWalletId = entry.walletId)
                    )
                }
            },
            onDuplicate = if (entry.kind == LedgerEntryKind.INCOME || entry.kind == LedgerEntryKind.EXPENSE) {
                {
                    viewModel.duplicateTransaction(entry.rawId)
                    selectedEntryForModal = null
                }
            } else null,
            onDelete = {
                viewModel.deleteLedgerEntry(entry)
                selectedEntryForModal = null
            }
        )
    }

    if (showCustomStartPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartMillis)
        DatePickerDialog(
            onDismissRequest = { showCustomStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { customStartMillis = it }
                        showCustomStartPicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomStartPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCustomEndPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndMillis)
        DatePickerDialog(
            onDismissRequest = { showCustomEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { customEndMillis = it }
                        showCustomEndPicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomEndPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
