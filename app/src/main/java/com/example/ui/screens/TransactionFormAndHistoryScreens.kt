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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
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

    val selectedType = remember(existingTx, initialType) {
        when (existingTx?.type) {
            TransactionType.INCOME.name -> TransactionType.INCOME
            TransactionType.EXPENSE_REVERSAL.name -> TransactionType.INCOME
            TransactionType.EXPENSE.name -> TransactionType.EXPENSE
            else -> initialType
        }
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

    val filteredCategories = remember(uiState.categories, selectedType, existingTx?.categoryId) {
        val catType = if (selectedType == TransactionType.INCOME) "INCOME" else "EXPENSE"
        uiState.categories.filter { it.type == catType && (!it.isArchived || it.id == existingTx?.categoryId) }
    }

    var selectedCategoryId by remember(existingTx, filteredCategories) {
        mutableStateOf(
            existingTx?.categoryId?.takeIf { id -> filteredCategories.any { it.id == id } }
                ?: filteredCategories.firstOrNull()?.id
                ?: 0L
        )
    }

    var selectedDateMillis by remember(existingTx) {
        mutableStateOf(existingTx?.date ?: System.currentTimeMillis())
    }
    var showTxDatePicker by remember { mutableStateOf(false) }
    var noteText by remember(existingTx) { mutableStateOf(existingTx?.note ?: "") }
    var tagsText by remember(existingTx) { mutableStateOf(existingTx?.tags ?: "") }
    var showInlineCategoryDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showExpenseFundDropdown by remember { mutableStateOf(false) }
    val view = LocalView.current

    val selectedWalletSummary = activeWallets.firstOrNull { it.wallet.id == selectedWalletId }
    val selectedWalletCurrency = selectedWalletSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val symbol = AccountingEngine.currencySymbolForCode(selectedWalletCurrency)

    var txFundMode by remember(existingTx?.id) {
        mutableStateOf(
            when {
                existingTx?.tags?.contains("FUND_BORROW", ignoreCase = true) == true -> "FUND_BORROW"
                existingTx?.tags?.contains("FUND_EXPENSE", ignoreCase = true) == true -> "FUND_EXPENSE"
                existingTx?.tags?.contains("FUND_IN", ignoreCase = true) == true -> "FUND_IN"
                else -> "PERSONAL"
            }
        )
    }

    val parsedAmount = remember(amountText) {
        evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
    }
    val isSplitFundWalletSelected = uiState.isFundEnabled &&
        selectedWalletSummary?.hasVirtualFund == true &&
        selectedWalletSummary.wallet.isFundWallet != true

    val availableWalletBalanceForCheck = remember(selectedWalletSummary, existingTx, selectedWalletId, txFundMode, isSplitFundWalletSelected) {
        val baseBal = when {
            selectedWalletSummary == null -> 0.0
            isSplitFundWalletSelected && (txFundMode == "FUND_EXPENSE" || txFundMode == "FUND_BORROW") ->
                selectedWalletSummary.fundBalance
            isSplitFundWalletSelected ->
                selectedWalletSummary.personalBalance
            else ->
                selectedWalletSummary.currentBalance
        }
        if (existingTx == null || existingTx.walletId != selectedWalletId) {
            baseBal
        } else {
            val priorMainDelta = if (existingTx.type == TransactionType.EXPENSE.name) {
                existingTx.amount
            } else {
                -existingTx.amount
            }
            AccountingEngine.roundCurrency(baseBal + priorMainDelta)
        }
    }
    val willBecomeNegative = selectedType == TransactionType.EXPENSE &&
        selectedWalletSummary != null &&
        (availableWalletBalanceForCheck - parsedAmount) < -0.005

    val pocketColors = LocalPocketColors.current
    val formBlur by animateDpAsState(
        targetValue = if (showInlineCategoryDialog) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tx_form_blur"
    )

    fun handleSave(navigateBackOnSave: Boolean): Boolean {
        val finalType = if (selectedType == TransactionType.INCOME) {
            TransactionType.INCOME.name
        } else {
            TransactionType.EXPENSE.name
        }
        if (selectedWalletId == 0L) {
            validationError = "Please select or create a wallet first."
            return false
        }
        if (parsedAmount <= 0.0) {
            validationError = "Please enter a positive numeric amount greater than 0."
            return false
        }
        if (selectedCategoryId == 0L) {
            validationError = "Please select a category."
            return false
        }
        val baseTagsList = tagsText.split(",")
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.equals("FUND_BORROW", ignoreCase = true) &&
                    !it.equals("FUND_EXPENSE", ignoreCase = true) &&
                    !it.equals("FUND_IN", ignoreCase = true)
            }
            .toMutableList()
        if (uiState.isFundEnabled) {
            if (selectedType == TransactionType.EXPENSE) {
                if (txFundMode == "FUND_BORROW") {
                    baseTagsList.add("FUND_BORROW")
                } else if (txFundMode == "FUND_EXPENSE") {
                    baseTagsList.add("FUND_EXPENSE")
                }
            } else if (selectedType == TransactionType.INCOME && txFundMode == "FUND_IN") {
                baseTagsList.add("FUND_IN")
            }
        }
        val tx = TransactionEntity(
            id = existingTx?.id ?: 0L,
            type = finalType,
            amount = AccountingEngine.roundCurrency(parsedAmount),
            date = selectedDateMillis,
            walletId = selectedWalletId,
            categoryId = selectedCategoryId,
            note = noteText.trim(),
            reference = existingTx?.reference ?: "",
            tags = baseTagsList.joinToString(", "),
            transferId = existingTx?.transferId,
            loanActivityId = existingTx?.loanActivityId,
            parentTransactionId = existingTx?.parentTransactionId,
            attachmentUri = existingTx?.attachmentUri ?: ""
        )
        performPocketHaptic(view)
        viewModel.saveTransaction(
            transaction = tx,
            navigateBackOnSave = navigateBackOnSave,
            recurringSetup = null,
            serviceFeeAmount = 0.0
        )
        return true
    }

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
                title = when {
                    existingTx != null && selectedType == TransactionType.INCOME -> "Edit Income"
                    existingTx != null -> "Edit Expense"
                    selectedType == TransactionType.INCOME -> "Add Income"
                    else -> "Add Expense"
                },
                onBack = { viewModel.navigateBack() },
                actions = {
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete transaction",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { handleSave(navigateBackOnSave = true) }
                            .testTag("top_save_transaction_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                modifier = Modifier.size(20.dp)
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
                // i. Date (no "Date" title, no Today/Yesterday chips — just current selected date in clickable calendar picker)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTxDatePicker = true }
                            .testTag("transaction_date_picker_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(selectedDateMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ii. Amount (larger number field & larger font size)
                item {
                    MathAmountTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = sanitizeMathExpressionInput(it)
                            validationError = null
                        },
                        label = {
                            Text("Amount ($symbol $selectedWalletCurrency)")
                        },
                        placeholder = {
                            Text(
                                text = "0",
                                style = TabularMoneyLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        },
                        textStyle = TabularMoneyLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clearZeroOnFocus(amountText) { amountText = it }
                            .testTag("transaction_amount_input")
                    )
                }

                // iii. Source/Note (Optional)
                item {
                    val recentMerchants = remember(uiState.transactions, selectedType) {
                        val targetType = if (selectedType == TransactionType.INCOME) {
                            TransactionType.INCOME.name
                        } else {
                            TransactionType.EXPENSE.name
                        }
                        uiState.transactions
                            .asSequence()
                            .filter {
                                it.parentTransactionId == null &&
                                    it.note.isNotBlank() &&
                                    it.type == targetType
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
                                Text("Source / Note (Optional)")
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

                // iv. Destination Wallet (with right-side Toggle for Add Income, or Dropdown for Add Expense)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Destination Wallet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.isFundEnabled) {
                            if (selectedType == TransactionType.INCOME) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Add to Fund",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (txFundMode == "FUND_IN") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (txFundMode == "FUND_IN") FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Switch(
                                        checked = txFundMode == "FUND_IN",
                                        onCheckedChange = { isChecked ->
                                            txFundMode = if (isChecked) "FUND_IN" else "PERSONAL"
                                        },
                                        modifier = Modifier.testTag("income_mode_add_to_fund_chip")
                                    )
                                }
                            } else {
                                val selectedModeLabel = when (txFundMode) {
                                    "FUND_EXPENSE" -> "Fund Expense"
                                    "FUND_BORROW" -> "Borrow from fund"
                                    else -> "Personal"
                                }
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, pocketColors.cardBorder),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { showExpenseFundDropdown = true }
                                            .testTag("expense_fund_mode_dropdown")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = selectedModeLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (txFundMode == "PERSONAL") {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showExpenseFundDropdown,
                                        onDismissRequest = { showExpenseFundDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Personal") },
                                            onClick = {
                                                txFundMode = "PERSONAL"
                                                showExpenseFundDropdown = false
                                            },
                                            modifier = Modifier.testTag("fund_expense_personal_chip")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Fund Expense") },
                                            onClick = {
                                                txFundMode = "FUND_EXPENSE"
                                                showExpenseFundDropdown = false
                                            },
                                            modifier = Modifier.testTag("fund_expense_regular_chip")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Borrow from fund") },
                                            onClick = {
                                                txFundMode = "FUND_BORROW"
                                                showExpenseFundDropdown = false
                                            },
                                            modifier = Modifier.testTag("fund_expense_personal_borrow_chip")
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeWallets, key = { "wallet_${it.wallet.id}" }) { summary ->
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

                    if (willBecomeNegative && uiState.settings.warnOnNegativeBalance) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val balanceBucketLabel = when {
                            isSplitFundWalletSelected && (txFundMode == "FUND_EXPENSE" || txFundMode == "FUND_BORROW") -> "available Fund balance"
                            isSplitFundWalletSelected -> "available Personal balance"
                            else -> "available balance"
                        }
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
                                    text = "Heads up: This expense exceeds ${selectedWalletSummary?.wallet?.name}'s $balanceBucketLabel (${AccountingEngine.formatMoney(availableWalletBalanceForCheck, symbol)}) and will result in a negative balance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pocketColors.expenseText
                                )
                            }
                        }
                    }
                }

                // v. Category (with "New category" card at the end of the category list)
                item {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                        item(key = "new_category_card") {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val customCount = SubscriptionManager.countCustomCategories(uiState.categories)
                                    val s = uiState.settings
                                    if (!SubscriptionManager.canAddCategory(customCount, s.subscriptionTier, s.subscriptionExpiresAt)) {
                                        viewModel.showUpgradeDialog("Free tier allows 1 new custom category. Upgrade to Pocket Premium for unlimited categories.")
                                    } else {
                                        showInlineCategoryDialog = true
                                    }
                                },
                                label = { Text("New category") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("inline_add_category_button")
                            )
                        }
                    }
                }

                // vi. Tags (collapsed/minimalist by default unless user taps + Add Tags or already has tags)
                item {
                    var showTagsSection by remember(existingTx?.id) {
                        mutableStateOf(
                            existingTx?.tags
                                ?.split(",")
                                ?.map { it.trim() }
                                ?.any {
                                    it.isNotBlank() &&
                                        !it.equals("FUND_BORROW", ignoreCase = true) &&
                                        !it.equals("FUND_EXPENSE", ignoreCase = true) &&
                                        !it.equals("FUND_IN", ignoreCase = true)
                                } == true
                        )
                    }
                    val presetTags = remember(uiState.transactions) {
                        val userTags = uiState.transactions
                            .asSequence()
                            .flatMap { it.tags.split(",") }
                            .map { it.trim().removePrefix("#") }
                            .filter {
                                it.isNotBlank() &&
                                    !it.equals("recurring", ignoreCase = true) &&
                                    !it.equals("FUND_BORROW", ignoreCase = true) &&
                                    !it.equals("FUND_EXPENSE", ignoreCase = true) &&
                                    !it.equals("FUND_IN", ignoreCase = true)
                            }
                            .distinct()
                            .toList()
                        (userTags + listOf("SylhetTrip", "EidShopping", "Wedding", "Family", "Work")).distinct().take(10)
                    }
                    if (!showTagsSection) {
                        AssistChip(
                            onClick = { showTagsSection = true },
                            label = { Text("Add Tags (Optional)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("expand_tags_chip")
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tagsText,
                                onValueChange = { tagsText = it },
                                label = { Text("Tags") },
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

                // Save & Save & Add another
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { handleSave(navigateBackOnSave = true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("save_transaction_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (existingTx != null) "Save Changes" else "Save")
                        }

                        if (existingTx == null) {
                            OutlinedButton(
                                onClick = {
                                    if (handleSave(navigateBackOnSave = false)) {
                                        amountText = ""
                                        noteText = ""
                                        tagsText = ""
                                        txFundMode = "PERSONAL"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_and_add_another_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Save & Add another")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInlineCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        val targetCatType = if (selectedType == TransactionType.INCOME) "INCOME" else "EXPENSE"
        AlertDialog(
            onDismissRequest = { showInlineCategoryDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text(if (targetCatType == "INCOME") "New Income Category" else "New Expense Category") },
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
    val wallets = remember(uiState.activeWalletSummaries, uiState.walletSummaries, existingTransfer) {
        val active = uiState.activeWalletSummaries
        val extra = listOfNotNull(existingTransfer?.sourceWalletId, existingTransfer?.destinationWalletId)
            .distinct()
            .filter { id -> active.none { it.wallet.id == id } }
            .mapNotNull { id -> uiState.walletSummaries.firstOrNull { it.wallet.id == id } }
        active + extra
    }
    val pocketColors = LocalPocketColors.current

    var sourceWalletId by remember(existingTransfer, preselectedSourceWalletId, wallets) {
        mutableStateOf(
            existingTransfer?.sourceWalletId
                ?: preselectedSourceWalletId
                ?: uiState.settings.defaultWalletId?.takeIf { defId -> wallets.any { it.wallet.id == defId } }
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
    var fundTransferMode by remember(existingTransfer) {
        mutableStateOf(
            when {
                existingTransfer?.note?.contains("[FUND_TRANSFER]", ignoreCase = true) == true -> "FUND_TRANSFER"
                existingTransfer?.note?.contains("[FUND_BORROW]", ignoreCase = true) == true -> "BORROW"
                existingTransfer?.note?.contains("[FUND_RETURN]", ignoreCase = true) == true -> "RETURN"
                else -> "REGULAR"
            }
        )
    }
    var noteText by remember(existingTransfer) {
        mutableStateOf(
            existingTransfer?.note
                ?.replace("[FUND_TRANSFER]", "", ignoreCase = true)
                ?.replace("[FUND_BORROW]", "", ignoreCase = true)
                ?.replace("[FUND_RETURN]", "", ignoreCase = true)
                ?.trim()
                ?: ""
        )
    }
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
    val isSourceDedicatedFund = sourceSummary?.wallet?.isFundWallet == true
    val isDestDedicatedFund = destSummary?.wallet?.isFundWallet == true
    val isSourceFund = isSourceDedicatedFund || (sourceSummary?.fundBalance ?: 0.0) > 0.005
    val isDestFund = isDestDedicatedFund || (destSummary?.fundBalance ?: 0.0) > 0.005
    LaunchedEffect(sourceWalletId, destWalletId) {
        if (existingTransfer == null) {
            val srcW = wallets.firstOrNull { it.wallet.id == sourceWalletId }
            val dstW = wallets.firstOrNull { it.wallet.id == destWalletId }
            when {
                srcW?.wallet?.isFundWallet == true -> {
                    if (fundTransferMode != "BORROW") {
                        fundTransferMode = "FUND_TRANSFER"
                    }
                }
                dstW?.wallet?.isFundWallet == true && uiState.totalFundBorrowedDue > 0.005 && (srcW?.fundBalance ?: 0.0) <= 0.005 -> {
                    fundTransferMode = "RETURN"
                }
                dstW?.wallet?.isFundWallet == true && (srcW?.fundBalance ?: 0.0) > 0.005 -> {
                    fundTransferMode = "FUND_TRANSFER"
                }
                else -> {
                    if (fundTransferMode == "FUND_TRANSFER" && (srcW?.fundBalance ?: 0.0) <= 0.005 && dstW?.wallet?.isFundWallet != true) {
                        fundTransferMode = "REGULAR"
                    }
                }
            }
        }
    }
    val sourceCurrency = sourceSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val destCurrency = destSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val sourceSymbol = AccountingEngine.currencySymbolForCode(sourceCurrency)
    val destSymbol = AccountingEngine.currencySymbolForCode(destCurrency)
    val isCrossCurrency = !sourceCurrency.equals(destCurrency, ignoreCase = true)

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

    val availableSourceBeforeEdit = remember(sourceSummary, existingTransfer, sourceWalletId) {
        val currentBal = sourceSummary?.currentBalance ?: 0.0
        when {
            existingTransfer == null -> currentBal
            existingTransfer.sourceWalletId == sourceWalletId ->
                AccountingEngine.roundCurrency(currentBal + existingTransfer.principalAmount + existingTransfer.feeAmount)
            existingTransfer.destinationWalletId == sourceWalletId -> {
                val priorCredited = if (existingTransfer.destinationAmount > 0.0) {
                    existingTransfer.destinationAmount
                } else {
                    existingTransfer.principalAmount
                }
                AccountingEngine.roundCurrency(currentBal - priorCredited)
            }
            else -> currentBal
        }
    }
    val sourceBalanceAfter = availableSourceBeforeEdit - totalSourceDebit

    val isSameWalletInternalFundAllowed = uiState.isFundEnabled &&
        (fundTransferMode == "RETURN" || fundTransferMode == "BORROW")

    fun handleConfirmTransfer() {
        when {
            sourceWalletId == 0L || destWalletId == 0L ->
                validationError = "Please select both source and destination wallets."
            sourceWalletId == destWalletId && !isSameWalletInternalFundAllowed ->
                validationError = "Source and destination wallets must differ."
            principal <= 0.0 ->
                validationError = "Transfer amount must be greater than zero."
            isCrossCurrency && effectiveDestCredit <= 0.0 ->
                validationError = "Please enter a valid conversion rate or converted destination amount."
            calculatedFee < 0.0 ->
                validationError = "Transfer fee cannot be negative."
            else -> {
                performPocketHaptic(view)
                val effectiveSaveMode = if (!uiState.isFundEnabled) {
                    "REGULAR"
                } else if (isSourceDedicatedFund && fundTransferMode == "REGULAR") {
                    "FUND_TRANSFER"
                } else {
                    fundTransferMode
                }
                val finalNote = when {
                    uiState.isFundEnabled && (isSourceFund || isDestDedicatedFund) && effectiveSaveMode == "FUND_TRANSFER" ->
                        if (noteText.isNotBlank()) "[FUND_TRANSFER] ${noteText.trim()}" else "[FUND_TRANSFER] Fund transfer from ${sourceSummary?.wallet?.name ?: "Source"} to ${destSummary?.wallet?.name ?: "Destination"}"
                    uiState.isFundEnabled && isSourceFund && effectiveSaveMode == "BORROW" ->
                        if (noteText.isNotBlank()) "[FUND_BORROW] ${noteText.trim()}" else "[FUND_BORROW] Personal borrow from ${sourceSummary?.wallet?.name ?: "Fund"}"
                    uiState.isFundEnabled && effectiveSaveMode == "RETURN" ->
                        if (noteText.isNotBlank()) "[FUND_RETURN] ${noteText.trim()}" else "[FUND_RETURN] Returned personal borrow to ${destSummary?.wallet?.name ?: "Fund"}"
                    else -> noteText.trim()
                }
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
                        note = finalNote
                    ),
                    sourceWalletName = sourceSummary?.wallet?.name ?: "Source",
                    destWalletName = destSummary?.wallet?.name ?: "Destination"
                )
            }
        }
    }

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
                title = if (existingTransfer != null) "Edit Transfer" else "Transfer",
                onBack = { viewModel.navigateBack() },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { handleConfirmTransfer() }
                            .testTag("top_save_transfer_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm Transfer",
                                modifier = Modifier.size(20.dp)
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
                // i. Date (no "Date" title, no Today/Yesterday chips — just current selected date in clickable calendar picker)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTransferDatePicker = true }
                            .testTag("transfer_date_picker_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Pick Transfer Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(selectedTransferDateMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ii. Source
                item {
                    Text("Source", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.wallet.id }) { summary ->
                            val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                            FilterChip(
                                selected = summary.wallet.id == sourceWalletId,
                                onClick = {
                                    sourceWalletId = summary.wallet.id
                                    if (destWalletId == summary.wallet.id && !isSameWalletInternalFundAllowed) {
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

                // iii. Destination
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Destination", style = MaterialTheme.typography.titleMedium)
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

                    AnimatedVisibility(visible = uiState.isFundEnabled && (isSourceFund || isDestFund || isDestDedicatedFund || uiState.totalFundBorrowedDue > 0.005)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isSourceDedicatedFund && !isDestDedicatedFund && sourceWalletId != destWalletId) {
                                    item {
                                        FilterChip(
                                            selected = fundTransferMode == "REGULAR",
                                            onClick = { fundTransferMode = "REGULAR" },
                                            label = { Text("Regular Transfer") },
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("transfer_fund_mode_regular_chip")
                                        )
                                    }
                                }
                                if ((isSourceFund || isDestDedicatedFund) && sourceWalletId != destWalletId) {
                                    item {
                                        FilterChip(
                                            selected = fundTransferMode == "FUND_TRANSFER" || (isSourceDedicatedFund && fundTransferMode == "REGULAR"),
                                            onClick = { fundTransferMode = "FUND_TRANSFER" },
                                            label = { Text("Keep as Fund") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.BusinessCenter,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("transfer_fund_mode_fund_transfer_chip")
                                        )
                                    }
                                }
                                if (isSourceFund && !isDestDedicatedFund) {
                                    item {
                                        FilterChip(
                                            selected = fundTransferMode == "BORROW",
                                            onClick = { fundTransferMode = "BORROW" },
                                            label = { Text("Personal Borrow") },
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("transfer_fund_mode_borrow_chip")
                                        )
                                    }
                                }
                                if (isDestDedicatedFund || isDestFund || uiState.totalFundBorrowedDue > 0.005) {
                                    item {
                                        FilterChip(
                                            selected = fundTransferMode == "RETURN",
                                            onClick = { fundTransferMode = "RETURN" },
                                            label = { Text("Return to Fund") },
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .testTag("transfer_fund_mode_return_chip")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // iv. Amount (larger number field & larger font size)
                item {
                    MathAmountTextField(
                        value = principalText,
                        onValueChange = {
                            principalText = sanitizeMathExpressionInput(it)
                            validationError = null
                        },
                        label = { Text("Amount ($sourceSymbol $sourceCurrency)") },
                        placeholder = {
                            Text(
                                text = "0",
                                style = TabularMoneyLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        },
                        textStyle = TabularMoneyLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .clearZeroOnFocus(principalText) { principalText = it }
                            .testTag("transfer_amount_input")
                    )
                }

                // v. Transfer Charge (compact horizontal chip row)
                item {
                    Text("Transfer Charge ($sourceSymbol $sourceCurrency)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(TransferFeeMode.entries, key = { it.name }) { mode ->
                            val compactLabel = when (mode) {
                                TransferFeeMode.NONE -> "None"
                                TransferFeeMode.MANUAL -> "Fixed ($sourceSymbol)"
                                TransferFeeMode.PERCENTAGE -> "Percentage (%)"
                                TransferFeeMode.FIXED_PLUS_PERCENTAGE -> "Fixed + %"
                            }
                            FilterChip(
                                selected = feeMode == mode,
                                onClick = { feeMode = mode },
                                label = { Text(compactLabel) },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("fee_mode_${mode.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (feeMode == TransferFeeMode.MANUAL || feeMode == TransferFeeMode.FIXED_PLUS_PERCENTAGE) {
                        OutlinedTextField(
                            value = fixedFeeText,
                            onValueChange = { fixedFeeText = sanitizeNumericInput(it) },
                            label = {
                                Text(if (feeMode == TransferFeeMode.MANUAL) "Manual transfer charge ($sourceSymbol)" else "Fixed transfer fee ($sourceSymbol)")
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
                            label = { Text("Transfer charge percentage (%) — e.g. 1.5 or 1.85") },
                            placeholder = { Text("1.85") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(percentageText) { percentageText = it }
                                .testTag("transfer_percentage_fee_input")
                        )
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

                // vi. Currency Conversion (Only show if transferring between different currencies)
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

                // vii. Note
                item {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Note") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
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

                // viii. Confirm Transfer
                item {
                    Button(
                        onClick = { handleConfirmTransfer() },
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
    val walletCurrencyMap = remember(uiState.wallets) {
        uiState.wallets.associate { it.id to it.currency.trim().ifBlank { "BDT" }.uppercase(Locale.ENGLISH) }
    }
    val symbol = remember(walletFilterId, walletCurrencyMap, uiState.settings.currencySymbol) {
        val filteredCurr = walletFilterId?.let { walletCurrencyMap[it] }
        if (filteredCurr.isNullOrBlank() || filteredCurr == "BDT") {
            uiState.settings.currencySymbol
        } else {
            AccountingEngine.currencySymbolForCode(filteredCurr)
        }
    }
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
        val minAmt = PocketLocalizer.normalizeDigits(minAmountText).trim().toDoubleOrNull()
        val maxAmt = PocketLocalizer.normalizeDigits(maxAmountText).trim().toDoubleOrNull()
        val rawQ = searchQuery.trim().lowercase(Locale.ENGLISH)
        val normQ = PocketLocalizer.normalizeDigits(rawQ)

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
            val catNameRaw = e.categoryName.orEmpty()
            val catTranslated = PocketLocalizer.translate(catNameRaw).lowercase(Locale.ENGLISH)
            val walletTranslated = PocketLocalizer.translate(e.walletName).lowercase(Locale.ENGLISH)
            val inQuery = rawQ.isEmpty() ||
                e.title.lowercase(Locale.ENGLISH).contains(rawQ) ||
                e.subtitle.lowercase(Locale.ENGLISH).contains(rawQ) ||
                catNameRaw.lowercase(Locale.ENGLISH).contains(rawQ) ||
                catTranslated.contains(rawQ) ||
                e.walletName.lowercase(Locale.ENGLISH).contains(rawQ) ||
                walletTranslated.contains(rawQ) ||
                e.reference.lowercase(Locale.ENGLISH).contains(rawQ) ||
                e.tags.lowercase(Locale.ENGLISH).contains(rawQ) ||
                absAmt.toString().contains(normQ)
            inDate && inType && inWallet && inCat && inTag && inMin && inMax && inQuery
        }.sortedWith(compareByDescending<UnifiedLedgerEntry> { it.date }.thenByDescending { it.rawId })
    }

    val selectedEntriesList = remember(filteredEntries, selectedEntryKeys) {
        filteredEntries.filter { selectedEntryKeys.contains(it.uniqueKey) }
    }

    val groupedByDay = remember(filteredEntries) {
        filteredEntries.groupBy { formatRelativeDate(it.date) }
    }

    val isSubScreenFilter = initialTypeFilter != null || initialCategoryId != null || initialWalletId != null
    val hasActiveExtraFilters = walletFilterId != null || categoryFilterId != null || selectedTagFilter != null || minAmountText.isNotBlank() || maxAmountText.isNotBlank()

    val isAnyHistoryDialogOpen = showBulkCategoryDialog || selectedEntryForModal != null || showCustomStartPicker || showCustomEndPicker

    Box(modifier = Modifier.fillMaxSize()) {
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
            val selectionSymbol = remember(selectedEntriesList, walletCurrencyMap, symbol) {
                val currencies = selectedEntriesList.map { walletCurrencyMap[it.walletId] ?: "BDT" }.distinct()
                if (currencies.size == 1 && currencies.first() != "BDT") {
                    AccountingEngine.currencySymbolForCode(currencies.first())
                } else {
                    symbol
                }
            }
            val categorizableEntries = selectedEntriesList.filter {
                (it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE_REVERSAL) &&
                    !it.isTransferFeeRow && !it.isLoanInterestRow
            }
            val hasIncomeSelected = categorizableEntries.any { it.kind == LedgerEntryKind.INCOME }
            val hasExpenseSelected = categorizableEntries.any { it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.EXPENSE_REVERSAL }
            val canBulkChangeCat = categorizableEntries.isNotEmpty() && !(hasIncomeSelected && hasExpenseSelected)
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
                            text = "${selectedEntriesList.size} selected · Sum ${AccountingEngine.formatMoney(selectedAbsTotal, selectionSymbol, includeDecimals = false)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Net: ${AccountingEngine.formatMoney(selectedNet, selectionSymbol, showSign = true, includeDecimals = false)}",
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

            // Minimal Single-Line Combined Period & Type Filter Row
            item {
                val monthNameLabel = remember(uiState.selectedYear, uiState.selectedMonth) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, uiState.selectedYear)
                        set(Calendar.MONTH, uiState.selectedMonth)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
                }
                var showPeriodDropdown by remember { mutableStateOf(false) }
                val activePeriodText = if (dateRange == HistoryDateRange.THIS_MONTH) monthNameLabel else dateRange.label

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { showPeriodDropdown = true },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(activePeriodText, style = MaterialTheme.typography.labelMedium)
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("history_period_dropdown_chip")
                            )
                            DropdownMenu(
                                expanded = showPeriodDropdown,
                                onDismissRequest = { showPeriodDropdown = false }
                            ) {
                                HistoryDateRange.entries.forEach { range ->
                                    val labelText = if (range == HistoryDateRange.THIS_MONTH) monthNameLabel else range.label
                                    DropdownMenuItem(
                                        text = { Text(labelText) },
                                        onClick = {
                                            dateRange = range
                                            showPeriodDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                        val dayCurrencies = dayEntries.map { walletCurrencyMap[it.walletId] ?: "BDT" }.distinct()
                        val daySymbol = if (dayCurrencies.size == 1 && dayCurrencies.first() != "BDT") {
                            AccountingEngine.currencySymbolForCode(dayCurrencies.first())
                        } else {
                            symbol
                        }
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
                                text = "Daily net: ${AccountingEngine.formatMoney(dayNet, daySymbol, showSign = true, includeDecimals = false)}",
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
    if (isAnyHistoryDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
    }

    if (showBulkCategoryDialog) {
        val categorizableEntries = selectedEntriesList.filter {
            (it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE_REVERSAL) &&
                !it.isTransferFeeRow && !it.isLoanInterestRow
        }
        val targetCatType = if (categorizableEntries.all { it.kind == LedgerEntryKind.INCOME }) "INCOME" else "EXPENSE"
        val txIds = categorizableEntries
            .filter {
                if (targetCatType == "INCOME") it.kind == LedgerEntryKind.INCOME
                else (it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.EXPENSE_REVERSAL)
            }
            .map { it.rawId }
        val matchingCategories = uiState.categories.filter { !it.isArchived && !it.isSystemFee && it.type == targetCatType }
        AlertDialog(
            onDismissRequest = { showBulkCategoryDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Change Category (${txIds.size} items)") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matchingCategories, key = { it.id }) { cat ->
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
                    entry.kind == LedgerEntryKind.ADJUSTMENT -> viewModel.navigateTo(
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
