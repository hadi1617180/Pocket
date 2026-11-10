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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
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
    var tagsText by remember(existingTx) {
        mutableStateOf(
            existingTx?.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter {
                    it.isNotBlank() &&
                        !it.equals("FUND_BORROW", ignoreCase = true) &&
                        !it.equals("FUND_EXPENSE", ignoreCase = true) &&
                        !it.equals("FUND_IN", ignoreCase = true)
                }
                ?.joinToString(", ")
                .orEmpty()
        )
    }
    var showInlineCategoryDialog by remember { mutableStateOf(false) }
    var showQuickCategoryModal by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showExpenseFundDropdown by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val transportPrefs = remember(context) {
        context.getSharedPreferences("pocket_transport_prefs", android.content.Context.MODE_PRIVATE)
    }
    val defaultRideModes = remember {
        listOf("Rickshaw", "Bike", "Car", "CNG", "Bus", "Plane")
    }
    val rideModes = remember(transportPrefs) {
        val raw = transportPrefs.getString("ride_modes_list", null)
        val initialList = if (raw == null) {
            defaultRideModes
        } else {
            raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        mutableStateListOf<String>().apply { addAll(initialList) }
    }
    fun persistRideModes() {
        transportPrefs.edit().putString("ride_modes_list", rideModes.joinToString("|")).apply()
    }

    val initialParsedTransport = remember(existingTx?.id) {
        val existingNote = existingTx?.note?.trim().orEmpty()
        val match = Regex("^(.*?)\\s+to\\s+(.*?)\\s+by\\s+(.*)$", RegexOption.IGNORE_CASE).matchEntire(existingNote)
        if (match != null) {
            Triple(
                match.groupValues[1].trim(),
                match.groupValues[2].trim(),
                match.groupValues[3].trim()
            )
        } else {
            Triple("", "", "")
        }
    }
    var transportFrom by remember(existingTx?.id) { mutableStateOf(initialParsedTransport.first) }
    var transportTo by remember(existingTx?.id) { mutableStateOf(initialParsedTransport.second) }
    var selectedRide by remember(existingTx?.id) { mutableStateOf(initialParsedTransport.third) }
    var showRideDropdown by remember { mutableStateOf(false) }
    var showRideDialog by remember { mutableStateOf(false) }
    var editingRideOriginalName by remember { mutableStateOf<String?>(null) }
    var rideDialogInputText by remember { mutableStateOf("") }

    fun updateTransportAutofillNote(fromVal: String, toVal: String, rideVal: String) {
        val f = capitalizeFirstLetter(fromVal.trim())
        val t = capitalizeFirstLetter(toVal.trim())
        val r = capitalizeFirstLetter(rideVal.trim())
        noteText = when {
            f.isNotBlank() && t.isNotBlank() && r.isNotBlank() -> "$f to $t by $r"
            f.isNotBlank() && t.isNotBlank() -> "$f to $t"
            f.isNotBlank() && r.isNotBlank() -> "From $f by $r"
            t.isNotBlank() && r.isNotBlank() -> "To $t by $r"
            r.isNotBlank() -> "By $r"
            f.isNotBlank() -> "From $f"
            t.isNotBlank() -> "To $t"
            else -> ""
        }
    }

    val selectedCategory = filteredCategories.firstOrNull { it.id == selectedCategoryId }
    val isTransportExpense = selectedType == TransactionType.EXPENSE &&
        selectedCategory?.name?.trim()?.equals("Transport", ignoreCase = true) == true

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
    val isFundSubWalletSelected = uiState.isFundEnabled &&
        (txFundMode == "FUND_IN" || txFundMode == "FUND_EXPENSE" || txFundMode == "FUND_BORROW")

    val availableWalletBalanceForCheck = remember(selectedWalletSummary, existingTx, selectedWalletId, txFundMode, uiState.isFundEnabled) {
        val checkingFundBucket = uiState.isFundEnabled && (txFundMode == "FUND_EXPENSE" || txFundMode == "FUND_BORROW")
        val baseBal = when {
            selectedWalletSummary == null -> 0.0
            checkingFundBucket -> selectedWalletSummary.fundBalance
            uiState.isFundEnabled -> selectedWalletSummary.personalBalance
            else -> selectedWalletSummary.currentBalance
        }
        if (existingTx == null || existingTx.walletId != selectedWalletId) {
            baseBal
        } else {
            val existingWasFundBucket = uiState.isFundEnabled && (
                existingTx.tags.contains("FUND_EXPENSE", ignoreCase = true) ||
                    existingTx.tags.contains("FUND_BORROW", ignoreCase = true) ||
                    existingTx.tags.contains("FUND_IN", ignoreCase = true)
                )
            if (uiState.isFundEnabled && checkingFundBucket != existingWasFundBucket) {
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
    }
    val willBecomeNegative = selectedType == TransactionType.EXPENSE &&
        selectedWalletSummary != null &&
        (availableWalletBalanceForCheck - parsedAmount) < -0.005

    val pocketColors = LocalPocketColors.current
    val formBlur by animateDpAsState(
        targetValue = if (showInlineCategoryDialog || showQuickCategoryModal || showRideDialog || showRideDropdown) 10.dp else 0.dp,
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
        performPocketHaptic(view, hapticType = PocketHapticType.SUCCESS_MULTI_PULSE)
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
        border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush())
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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

                // ii. Category (Top 5 most-used categories inline + "More ▾" Liquid Glass sheet + "New category" chip)
                item {
                    val categoryUsageCounts = remember(uiState.transactions) {
                        uiState.transactions.groupingBy { it.categoryId }.eachCount()
                    }
                    val sortedCategories = remember(filteredCategories, categoryUsageCounts, selectedCategoryId) {
                        val byUsage = filteredCategories.sortedWith(
                            compareByDescending<CategoryEntity> { categoryUsageCounts[it.id] ?: 0 }
                                .thenBy { it.id }
                        )
                        val topFive = byUsage.take(5).toMutableList()
                        val selectedCat = filteredCategories.firstOrNull { it.id == selectedCategoryId }
                        if (selectedCat != null && topFive.none { it.id == selectedCat.id }) {
                            if (topFive.size >= 5) {
                                topFive[topFive.lastIndex] = selectedCat
                            } else {
                                topFive.add(selectedCat)
                            }
                        }
                        topFive
                    }
                    val hasMoreCategories = filteredCategories.size > sortedCategories.size

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Category",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (hasMoreCategories) {
                                Text(
                                    text = "All (${filteredCategories.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showQuickCategoryModal = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sortedCategories, key = { it.id }) { cat ->
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
                            if (hasMoreCategories) {
                                item(key = "more_categories_chip") {
                                    AssistChip(
                                        onClick = { showQuickCategoryModal = true },
                                        label = { Text("More ▾") },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("quick_category_more_chip")
                                    )
                                }
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

                        // Transport Category Minimal Fields: 1. From, 2. To, and 1 Dropdown (Ride)
                        AnimatedVisibility(visible = isTransportExpense) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = transportFrom,
                                    onValueChange = { updated ->
                                        val formatted = capitalizeFirstLetter(updated)
                                        transportFrom = formatted
                                        updateTransportAutofillNote(formatted, transportTo, selectedRide)
                                    },
                                    label = { Text("From", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("transport_from_input")
                                )
                                OutlinedTextField(
                                    value = transportTo,
                                    onValueChange = { updated ->
                                        val formatted = capitalizeFirstLetter(updated)
                                        transportTo = formatted
                                        updateTransportAutofillNote(transportFrom, formatted, selectedRide)
                                    },
                                    label = { Text("To", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("transport_to_input")
                                )
                                Box(modifier = Modifier.weight(1.05f)) {
                                    OutlinedTextField(
                                        value = selectedRide,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Ride", style = MaterialTheme.typography.labelSmall) },
                                        placeholder = { Text("Select", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select Ride",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showRideDropdown = true }
                                            .testTag("transport_ride_dropdown")
                                    )
                                    DropdownMenu(
                                        expanded = showRideDropdown,
                                        onDismissRequest = { showRideDropdown = false },
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = if (pocketColors.isDark) Color(0xF518181F) else Color(0xF8FFFFFF),
                                        tonalElevation = 12.dp,
                                        shadowElevation = 16.dp,
                                        border = BorderStroke(
                                            1.dp,
                                            if (pocketColors.isDark) Color.White.copy(alpha = 0.18f) else pocketColors.cardBorder
                                        ),
                                        modifier = Modifier.widthIn(min = 220.dp)
                                    ) {
                                        rideModes.forEach { rideOption ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = rideOption,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (selectedRide.equals(rideOption, ignoreCase = true)) {
                                                                FontWeight.Bold
                                                            } else {
                                                                FontWeight.Normal
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    showRideDropdown = false
                                                                    editingRideOriginalName = rideOption
                                                                    rideDialogInputText = rideOption
                                                                    showRideDialog = true
                                                                },
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .testTag("edit_ride_mode_$rideOption")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = "Edit $rideOption",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    rideModes.remove(rideOption)
                                                                    persistRideModes()
                                                                    if (selectedRide.equals(rideOption, ignoreCase = true)) {
                                                                        selectedRide = ""
                                                                        updateTransportAutofillNote(transportFrom, transportTo, "")
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .testTag("delete_ride_mode_$rideOption")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DeleteOutline,
                                                                    contentDescription = "Delete $rideOption",
                                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedRide = rideOption
                                                    showRideDropdown = false
                                                    updateTransportAutofillNote(transportFrom, transportTo, rideOption)
                                                },
                                                modifier = Modifier.testTag("ride_option_$rideOption")
                                            )
                                        }
                                        HorizontalDivider(color = pocketColors.cardBorder.copy(alpha = 0.6f))
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "+ Add new ride",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showRideDropdown = false
                                                editingRideOriginalName = null
                                                rideDialogInputText = ""
                                                showRideDialog = true
                                            },
                                            modifier = Modifier.testTag("add_new_ride_dropdown_item")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // iii. Amount (larger number field & larger font size)
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

                // iv. Note (Optional)
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
                                Text("Note (Optional)")
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

                // v. Destination Wallet + Sub-Wallet (<Wallet>-Personal / <Wallet>-Fund) Cards + Option 1 Borrow Selector
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Destination Wallet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(activeWallets, key = { "wallet_${it.wallet.id}" }) { summary ->
                                val isSelected = summary.wallet.id == selectedWalletId
                                val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                                val isCashWallet = summary.wallet.name.trim().equals("Cash", ignoreCase = true) ||
                                    summary.wallet.type.trim().equals("CASH", ignoreCase = true)
                                val chipDisplayBal = if (isCashWallet && uiState.isFundEnabled && !summary.wallet.isFundWallet) {
                                    summary.personalBalance
                                } else {
                                    summary.currentBalance
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedWalletId = summary.wallet.id },
                                    label = {
                                        Text(
                                            "${summary.wallet.name} (${AccountingEngine.formatMoney(chipDisplayBal, chipSym, includeDecimals = false)})"
                                        )
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("select_wallet_chip_${summary.wallet.name}")
                                )
                            }
                        }

                        if (uiState.isFundEnabled && selectedWalletSummary != null) {
                            SubWalletSelectorCards(
                                walletSummary = selectedWalletSummary,
                                selectedIsFund = isFundSubWalletSelected,
                                onSelectSubWallet = { chooseFund ->
                                    txFundMode = if (!chooseFund) {
                                        "PERSONAL"
                                    } else if (selectedType == TransactionType.INCOME) {
                                        "FUND_IN"
                                    } else {
                                        if (txFundMode == "FUND_BORROW") "FUND_BORROW" else "FUND_EXPENSE"
                                    }
                                },
                                testTagPrefix = "tx_form",
                                hideBalances = uiState.settings.hideBalancesPrivacy
                            )

                            // Option 1: When <Wallet>-Fund is selected in Add Expense, show Official Fund Expense vs Borrow for Personal
                            AnimatedVisibility(visible = selectedType == TransactionType.EXPENSE && isFundSubWalletSelected) {
                                val wName = selectedWalletSummary.wallet.name
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$wName-Fund Expense Type",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isOfficial = txFundMode == "FUND_EXPENSE"
                                            val isBorrow = txFundMode == "FUND_BORROW"
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { txFundMode = "FUND_EXPENSE" }
                                                    .testTag("fund_expense_regular_chip"),
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
                                                        style = MaterialTheme.typography.labelMedium,
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
                                                    .clickable { txFundMode = "FUND_BORROW" }
                                                    .testTag("fund_expense_personal_borrow_chip"),
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
                                                        style = MaterialTheme.typography.labelMedium,
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
                        }

                        if (willBecomeNegative && uiState.settings.warnOnNegativeBalance) {
                            val wName = selectedWalletSummary?.wallet?.name ?: "Wallet"
                            val balanceBucketLabel = when {
                                uiState.isFundEnabled && isFundSubWalletSelected -> "$wName-Fund balance"
                                uiState.isFundEnabled -> "$wName-Personal balance"
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
                                        text = "Heads up: This expense exceeds $balanceBucketLabel (${AccountingEngine.formatMoney(availableWalletBalanceForCheck, symbol)}) and will result in a negative balance.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = pocketColors.expenseText
                                    )
                                }
                            }
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
                                        transportFrom = ""
                                        transportTo = ""
                                        selectedRide = ""
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
    }

    if (showQuickCategoryModal) {
        var catSearchQuery by remember { mutableStateOf("") }
        val displayedCategories = remember(filteredCategories, catSearchQuery) {
            val q = catSearchQuery.trim()
            if (q.isEmpty()) filteredCategories
            else filteredCategories.filter { it.name.contains(q, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showQuickCategoryModal = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedType == TransactionType.INCOME) "Select Income Category" else "Select Expense Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                showQuickCategoryModal = false
                                showInlineCategoryDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New")
                        }
                        IconButton(
                            onClick = { showQuickCategoryModal = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catSearchQuery,
                        onValueChange = { catSearchQuery = it },
                        placeholder = { Text("Search category...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedCategories, key = { it.id }) { cat ->
                            val isSelected = cat.id == selectedCategoryId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedCategoryId = cat.id
                                        showQuickCategoryModal = false
                                    }
                                    .testTag("modal_category_item_${cat.name}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else pocketColors.surfaceElevated.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    0.6.dp,
                                    pocketLiquidGlassBorderBrush(isHighlighted = isSelected)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconBadgeCircle(
                                            iconName = cat.iconName,
                                            colorHex = cat.colorHex,
                                            size = 32.dp,
                                            iconSize = 16.dp
                                        )
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showRideDialog) {
        AlertDialog(
            onDismissRequest = {
                showRideDialog = false
                editingRideOriginalName = null
            },
            containerColor = pocketColors.overlaySurface,
            text = {
                OutlinedTextField(
                    value = rideDialogInputText,
                    onValueChange = { rideDialogInputText = capitalizeFirstLetter(it) },
                    placeholder = { Text("Ride name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ride_dialog_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = capitalizeFirstLetter(rideDialogInputText.trim())
                        if (cleaned.isNotBlank()) {
                            val orig = editingRideOriginalName
                            if (orig != null) {
                                val idx = rideModes.indexOfFirst { it.equals(orig, ignoreCase = true) }
                                if (idx >= 0) {
                                    rideModes[idx] = cleaned
                                } else if (rideModes.none { it.equals(cleaned, ignoreCase = true) }) {
                                    rideModes.add(cleaned)
                                }
                                persistRideModes()
                                if (selectedRide.equals(orig, ignoreCase = true)) {
                                    selectedRide = cleaned
                                    updateTransportAutofillNote(transportFrom, transportTo, cleaned)
                                }
                            } else {
                                val existingMatch = rideModes.firstOrNull { it.equals(cleaned, ignoreCase = true) }
                                val chosen = existingMatch ?: cleaned.also {
                                    rideModes.add(it)
                                    persistRideModes()
                                }
                                selectedRide = chosen
                                updateTransportAutofillNote(transportFrom, transportTo, chosen)
                            }
                            showRideDialog = false
                            editingRideOriginalName = null
                        }
                    },
                    modifier = Modifier.testTag("ride_dialog_confirm_button")
                ) {
                    Text(if (editingRideOriginalName != null) "Save" else "Add")
                }
            }
        )
    }

    if (showInlineCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        val targetCatType = if (selectedType == TransactionType.INCOME) "INCOME" else "EXPENSE"
        AlertDialog(
            onDismissRequest = { showInlineCategoryDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (targetCatType == "INCOME") "New Income Category" else "New Expense Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showInlineCategoryDialog = false },
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
            dismissButton = null
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
    var sourceIsFund by remember(existingTransfer) {
        mutableStateOf(
            existingTransfer?.note?.contains("[FUND_TRANSFER]", ignoreCase = true) == true ||
                existingTransfer?.note?.contains("[FUND_BORROW]", ignoreCase = true) == true ||
                existingTransfer?.note?.contains("[FUND_SETTLE]", ignoreCase = true) == true
        )
    }
    var destIsFund by remember(existingTransfer) {
        mutableStateOf(
            existingTransfer?.note?.contains("[FUND_TRANSFER]", ignoreCase = true) == true ||
                existingTransfer?.note?.contains("[FUND_RETURN]", ignoreCase = true) == true ||
                existingTransfer?.note?.contains("[FUND_TOPUP]", ignoreCase = true) == true
        )
    }
    var sourceFundIsBorrow by remember(existingTransfer) {
        mutableStateOf(
            when {
                existingTransfer?.note?.contains("[FUND_SETTLE]", ignoreCase = true) == true -> false
                existingTransfer?.note?.contains("[FUND_BORROW]", ignoreCase = true) == true -> true
                else -> false
            }
        )
    }
    var destFundIsReturnDue by remember(existingTransfer, uiState.totalFundBorrowedDue) {
        mutableStateOf(
            when {
                existingTransfer?.note?.contains("[FUND_TOPUP]", ignoreCase = true) == true -> false
                existingTransfer?.note?.contains("[FUND_RETURN]", ignoreCase = true) == true -> true
                else -> uiState.totalFundBorrowedDue > 0.005
            }
        )
    }
    val fundTransferMode = remember(uiState.isFundEnabled, sourceIsFund, destIsFund, sourceFundIsBorrow, destFundIsReturnDue) {
        if (!uiState.isFundEnabled) {
            "REGULAR"
        } else {
            when {
                sourceIsFund && destIsFund -> "FUND_TRANSFER"
                sourceIsFund && !destIsFund -> if (sourceFundIsBorrow) "BORROW" else "SETTLE"
                !sourceIsFund && destIsFund -> if (destFundIsReturnDue) "RETURN" else "TOPUP"
                else -> "REGULAR"
            }
        }
    }
    var noteText by remember(existingTransfer) {
        val rawCleaned = existingTransfer?.note
            ?.replace("[FUND_TRANSFER]", "", ignoreCase = true)
            ?.replace("[FUND_BORROW]", "", ignoreCase = true)
            ?.replace("[FUND_SETTLE]", "", ignoreCase = true)
            ?.replace("[FUND_RETURN]", "", ignoreCase = true)
            ?.replace("[FUND_TOPUP]", "", ignoreCase = true)
            ?.trim()
            .orEmpty()
        val isAutoGeneratedDefault = rawCleaned.matches(
            Regex("^(Borrowed from|Official Fund out from|Returned from|Official Fund top-up from)\\s+.*-.*\\s+to\\s+.*-.*$", RegexOption.IGNORE_CASE)
        ) || rawCleaned.matches(
            Regex("^.*-Fund\\s*→\\s*.*-Fund$", RegexOption.IGNORE_CASE)
        )
        mutableStateOf(if (isAutoGeneratedDefault) "" else rawCleaned)
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

    val availableSourceBeforeEdit = remember(sourceSummary, existingTransfer, sourceWalletId, uiState.isFundEnabled, sourceIsFund) {
        val currentBal = when {
            sourceSummary == null -> 0.0
            uiState.isFundEnabled && sourceIsFund -> sourceSummary.fundBalance
            uiState.isFundEnabled -> sourceSummary.personalBalance
            else -> sourceSummary.currentBalance
        }
        val existingSourceWasFund = existingTransfer != null && (
            existingTransfer.note.contains("[FUND_TRANSFER]", ignoreCase = true) ||
                existingTransfer.note.contains("[FUND_BORROW]", ignoreCase = true) ||
                existingTransfer.note.contains("[FUND_SETTLE]", ignoreCase = true)
            )
        val existingDestWasFund = existingTransfer != null && (
            existingTransfer.note.contains("[FUND_TRANSFER]", ignoreCase = true) ||
                existingTransfer.note.contains("[FUND_RETURN]", ignoreCase = true) ||
                existingTransfer.note.contains("[FUND_TOPUP]", ignoreCase = true)
            )
        when {
            existingTransfer == null -> currentBal
            existingTransfer.sourceWalletId == sourceWalletId && (!uiState.isFundEnabled || existingSourceWasFund == sourceIsFund) ->
                AccountingEngine.roundCurrency(currentBal + existingTransfer.principalAmount + existingTransfer.feeAmount)
            existingTransfer.destinationWalletId == sourceWalletId && (!uiState.isFundEnabled || existingDestWasFund == sourceIsFund) -> {
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
        (fundTransferMode == "RETURN" || fundTransferMode == "TOPUP" || fundTransferMode == "BORROW" || fundTransferMode == "SETTLE")

    fun handleConfirmTransfer() {
        val srcWName = sourceSummary?.wallet?.name ?: "Source"
        val dstWName = destSummary?.wallet?.name ?: "Destination"
        when {
            sourceWalletId == 0L || destWalletId == 0L ->
                validationError = "Please select both source and destination wallets."
            sourceWalletId == destWalletId && !isSameWalletInternalFundAllowed ->
                validationError = if (uiState.isFundEnabled) {
                    "For same-wallet transfer ($srcWName), select Personal on one side and Fund on the other."
                } else {
                    "Source and destination wallets must differ."
                }
            principal <= 0.0 ->
                validationError = "Transfer amount must be greater than zero."
            isCrossCurrency && effectiveDestCredit <= 0.0 ->
                validationError = "Please enter a valid conversion rate or converted destination amount."
            calculatedFee < 0.0 ->
                validationError = "Transfer fee cannot be negative."
            else -> {
                performPocketHaptic(view, hapticType = PocketHapticType.SUCCESS_MULTI_PULSE)
                val finalNote = when {
                    uiState.isFundEnabled && fundTransferMode == "FUND_TRANSFER" ->
                        if (noteText.isNotBlank()) "[FUND_TRANSFER] ${noteText.trim()}" else "[FUND_TRANSFER] $srcWName-Fund → $dstWName-Fund"
                    uiState.isFundEnabled && fundTransferMode == "BORROW" ->
                        if (noteText.isNotBlank()) "[FUND_BORROW] ${noteText.trim()}" else "[FUND_BORROW] Borrowed from $srcWName-Fund to $dstWName-Personal"
                    uiState.isFundEnabled && fundTransferMode == "SETTLE" ->
                        if (noteText.isNotBlank()) "[FUND_SETTLE] ${noteText.trim()}" else "[FUND_SETTLE] Official Fund out from $srcWName-Fund to $dstWName-Personal"
                    uiState.isFundEnabled && fundTransferMode == "RETURN" ->
                        if (noteText.isNotBlank()) "[FUND_RETURN] ${noteText.trim()}" else "[FUND_RETURN] Returned from $srcWName-Personal to $dstWName-Fund"
                    uiState.isFundEnabled && fundTransferMode == "TOPUP" ->
                        if (noteText.isNotBlank()) "[FUND_TOPUP] ${noteText.trim()}" else "[FUND_TOPUP] Official Fund top-up from $srcWName-Personal to $dstWName-Fund"
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
                    sourceWalletName = srcWName,
                    destWalletName = dstWName
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
        border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush())
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                // i. Date
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

                // ii. Source Wallet + Sub-Wallet (<FromWallet>-Personal / <FromWallet>-Fund)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Source", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(wallets, key = { it.wallet.id }) { summary ->
                                val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                                val isCashWallet = summary.wallet.name.trim().equals("Cash", ignoreCase = true) ||
                                    summary.wallet.type.trim().equals("CASH", ignoreCase = true)
                                val chipDisplayBal = if (isCashWallet && uiState.isFundEnabled && !summary.wallet.isFundWallet) {
                                    summary.personalBalance
                                } else {
                                    summary.currentBalance
                                }
                                FilterChip(
                                    selected = summary.wallet.id == sourceWalletId,
                                    onClick = {
                                        sourceWalletId = summary.wallet.id
                                        if (!uiState.isFundEnabled && destWalletId == summary.wallet.id) {
                                            destWalletId = wallets.firstOrNull { it.wallet.id != sourceWalletId }?.wallet?.id ?: 0L
                                        }
                                    },
                                    label = {
                                        Text("${summary.wallet.name} · ${AccountingEngine.formatMoney(chipDisplayBal, chipSym, includeDecimals = false)}")
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("transfer_from_wallet_${summary.wallet.name}")
                                )
                            }
                        }
                        if (uiState.isFundEnabled && sourceSummary != null) {
                            SubWalletSelectorCards(
                                walletSummary = sourceSummary,
                                selectedIsFund = sourceIsFund,
                                onSelectSubWallet = { chooseFund ->
                                    sourceIsFund = chooseFund
                                    // If same wallet is selected on both sides, automatically flip destination sub-wallet so it's valid
                                    if (sourceWalletId == destWalletId && destIsFund == chooseFund) {
                                        destIsFund = !chooseFund
                                    }
                                },
                                testTagPrefix = "transfer_source",
                                hideBalances = uiState.settings.hideBalancesPrivacy
                            )
                        }
                    }
                }

                // iii. Destination Wallet + Sub-Wallet (<ToWallet>-Personal / <ToWallet>-Fund)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Destination", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    val tmpW = sourceWalletId
                                    sourceWalletId = destWalletId
                                    destWalletId = tmpW
                                    val tmpF = sourceIsFund
                                    sourceIsFund = destIsFund
                                    destIsFund = tmpF
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
                                val isCashWallet = summary.wallet.name.trim().equals("Cash", ignoreCase = true) ||
                                    summary.wallet.type.trim().equals("CASH", ignoreCase = true)
                                val chipDisplayBal = if (isCashWallet && uiState.isFundEnabled && !summary.wallet.isFundWallet) {
                                    summary.personalBalance
                                } else {
                                    summary.currentBalance
                                }
                                FilterChip(
                                    selected = summary.wallet.id == destWalletId,
                                    onClick = {
                                        destWalletId = summary.wallet.id
                                        if (uiState.isFundEnabled && summary.wallet.id == sourceWalletId && destIsFund == sourceIsFund) {
                                            destIsFund = !sourceIsFund
                                        }
                                    },
                                    label = {
                                        Text("${summary.wallet.name} · ${AccountingEngine.formatMoney(chipDisplayBal, chipSym, includeDecimals = false)}")
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("transfer_to_wallet_${summary.wallet.name}")
                                )
                            }
                        }
                        if (uiState.isFundEnabled && destSummary != null) {
                            SubWalletSelectorCards(
                                walletSummary = destSummary,
                                selectedIsFund = destIsFund,
                                onSelectSubWallet = { chooseFund ->
                                    destIsFund = chooseFund
                                    if (sourceWalletId == destWalletId && sourceIsFund == chooseFund) {
                                        sourceIsFund = !chooseFund
                                    }
                                },
                                testTagPrefix = "transfer_dest",
                                hideBalances = uiState.settings.hideBalancesPrivacy
                            )

                            val srcWName = sourceSummary?.wallet?.name ?: "Source"
                            val dstWName = destSummary.wallet.name
                            val srcSubLabel = "$srcWName-${if (sourceIsFund) "Fund" else "Personal"}"
                            val dstSubLabel = "$dstWName-${if (destIsFund) "Fund" else "Personal"}"

                            // 2-Card Fund Transfer Type Selector whenever Source or Destination has <Wallet>-Fund selected
                            AnimatedVisibility(visible = sourceIsFund || destIsFund) {
                                val selectorHeader = when {
                                    sourceIsFund && !destIsFund -> "$srcWName-Fund Transfer Type"
                                    !sourceIsFund && destIsFund -> "$dstWName-Fund Transfer Type"
                                    else -> "$srcWName-Fund → $dstWName-Fund Transfer Type"
                                }
                                val isOfficialSelected = when {
                                    sourceIsFund && !destIsFund -> !sourceFundIsBorrow
                                    !sourceIsFund && destIsFund -> !destFundIsReturnDue
                                    else -> true
                                }
                                val isSecondOptionSelected = !isOfficialSelected
                                val officialTitle = when {
                                    sourceIsFund && !destIsFund -> "Official Fund"
                                    !sourceIsFund && destIsFund -> "Official Fund Top-up"
                                    else -> "Official Fund Transfer"
                                }
                                val officialSubtitle = when {
                                    sourceIsFund && !destIsFund -> "No return needed (No Due)"
                                    !sourceIsFund && destIsFund -> "Adds to $dstWName-Fund"
                                    else -> "Keeps in $dstWName-Fund"
                                }
                                val secondTitle = when {
                                    !sourceIsFund && destIsFund -> "Return Borrowed Due"
                                    else -> "Borrow for Personal"
                                }
                                val secondSubtitle = when {
                                    sourceIsFund && !destIsFund -> "Adds to Borrowed Due"
                                    !sourceIsFund && destIsFund -> "Reduces Borrowed Due"
                                    else -> "To $dstWName-Personal (Adds Due)"
                                }
                                val secondActiveColor = if (!sourceIsFund && destIsFund) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    pocketColors.expenseText
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pocketColors.surfaceElevated,
                                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = selectorHeader,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        when {
                                                            sourceIsFund && !destIsFund -> sourceFundIsBorrow = false
                                                            !sourceIsFund && destIsFund -> destFundIsReturnDue = false
                                                        }
                                                    }
                                                    .testTag("transfer_fund_official_chip"),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isOfficialSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isOfficialSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = officialTitle,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isOfficialSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = officialSubtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        when {
                                                            sourceIsFund && !destIsFund -> sourceFundIsBorrow = true
                                                            !sourceIsFund && destIsFund -> destFundIsReturnDue = true
                                                            sourceIsFund && destIsFund -> {
                                                                destIsFund = false
                                                                sourceFundIsBorrow = true
                                                            }
                                                        }
                                                    }
                                                    .testTag("transfer_fund_borrow_or_return_chip"),
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSecondOptionSelected) secondActiveColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSecondOptionSelected) secondActiveColor else pocketColors.cardBorder
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = secondTitle,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSecondOptionSelected) secondActiveColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = secondSubtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSecondOptionSelected) secondActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val modeDescription = when (fundTransferMode) {
                                "FUND_TRANSFER" -> "$srcSubLabel → $dstSubLabel • Official Fund Transfer"
                                "BORROW" -> "$srcSubLabel → $dstSubLabel • Borrow for Personal (Adds to Due)"
                                "SETTLE" -> "$srcSubLabel → $dstSubLabel • Official Fund (No Return Needed)"
                                "RETURN" -> "$srcSubLabel → $dstSubLabel • Return to Fund (Reduces Due)"
                                "TOPUP" -> "$srcSubLabel → $dstSubLabel • Official Fund Top-up (No Due Change)"
                                else -> "$srcSubLabel → $dstSubLabel • Personal Transfer"
                            }
                            val modeColor = when (fundTransferMode) {
                                "BORROW" -> pocketColors.expenseText
                                "RETURN", "FUND_TRANSFER", "SETTLE", "TOPUP" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = pocketColors.surfaceElevated,
                                border = BorderStroke(1.dp, pocketColors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transfer_subwallet_mode_pill")
                            ) {
                                Text(
                                    text = modeDescription,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = modeColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionHistoryScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    initialTypeFilter: LedgerEntryKind?,
    initialCategoryId: Long?,
    initialWalletId: Long?,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
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
    var subWalletHistoryFilter by remember { mutableStateOf("ALL") } // ALL, PERSONAL, FUND
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
    var showPeriodDropdown by remember { mutableStateOf(false) }
    val historyBlur by animateDpAsState(
        targetValue = if (selectedEntryForModal != null || showBulkCategoryDialog || showPeriodDropdown) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "history_blur"
    )

    val allAvailableTags = remember(uiState.unifiedLedger) {
        uiState.unifiedLedger
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
        subWalletHistoryFilter,
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
            val isFundRelated = e.tags.contains("FUND_IN", ignoreCase = true) ||
                e.tags.contains("FUND_EXPENSE", ignoreCase = true) ||
                e.tags.contains("FUND_BORROW", ignoreCase = true) ||
                e.tags.contains("FUND_RETURN", ignoreCase = true) ||
                e.tags.contains("FUND_TOPUP", ignoreCase = true) ||
                e.note.contains("[FUND_TRANSFER]", ignoreCase = true) ||
                e.note.contains("[FUND_BORROW]", ignoreCase = true) ||
                e.note.contains("[FUND_RETURN]", ignoreCase = true) ||
                e.note.contains("[FUND_SETTLE]", ignoreCase = true) ||
                e.note.contains("[FUND_TOPUP]", ignoreCase = true) ||
                e.note.contains("[FUND_LOAN]", ignoreCase = true) ||
                e.note.contains("[FUND_LOAN_BORROW]", ignoreCase = true) ||
                e.note.contains("-Fund", ignoreCase = true)
            val isBorrowRelated = e.tags.contains("FUND_BORROW", ignoreCase = true) ||
                e.tags.contains("FUND_RETURN", ignoreCase = true) ||
                e.tags.contains("FUND_TOPUP", ignoreCase = true) ||
                e.note.contains("[FUND_BORROW]", ignoreCase = true) ||
                e.note.contains("[FUND_RETURN]", ignoreCase = true) ||
                e.note.contains("[FUND_SETTLE]", ignoreCase = true) ||
                e.note.contains("[FUND_TOPUP]", ignoreCase = true) ||
                e.note.contains("[FUND_LOAN_BORROW]", ignoreCase = true)
            val inSubWallet = when (subWalletHistoryFilter) {
                "FUND" -> isFundRelated
                "PERSONAL" -> !isFundRelated || isBorrowRelated
                else -> true
            }
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
            inDate && inType && inWallet && inCat && inSubWallet && inTag && inMin && inMax && inQuery
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 180.dp),
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
                                onDismissRequest = { showPeriodDropdown = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = if (pocketColors.isDark) Color(0xF21C1C22) else Color(0xF6FFFFFF),
                                shadowElevation = 14.dp,
                                border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush())
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
                            selected = typeFilter == null && subWalletHistoryFilter == "ALL",
                            onClick = {
                                typeFilter = null
                                subWalletHistoryFilter = "ALL"
                            },
                            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }
                    if (uiState.isFundEnabled) {
                        item {
                            FilterChip(
                                selected = subWalletHistoryFilter == "PERSONAL",
                                onClick = {
                                    subWalletHistoryFilter = if (subWalletHistoryFilter == "PERSONAL") "ALL" else "PERSONAL"
                                },
                                label = { Text("Personal", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("history_subwallet_chip_personal")
                            )
                        }
                        item {
                            FilterChip(
                                selected = subWalletHistoryFilter == "FUND",
                                onClick = {
                                    subWalletHistoryFilter = if (subWalletHistoryFilter == "FUND") "ALL" else "FUND"
                                },
                                label = { Text("Fund", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("history_subwallet_chip_fund")
                            )
                        }
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
                    stickyHeader(key = "header_$dayLabel") {
                        val dayNet = dayEntries
                            .filter { it.kind == LedgerEntryKind.INCOME || it.kind == LedgerEntryKind.EXPENSE || it.kind == LedgerEntryKind.EXPENSE_REVERSAL }
                            .sumOf { it.signedAmount }
                        val dayCurrencies = dayEntries.map { walletCurrencyMap[it.walletId] ?: "BDT" }.distinct()
                        val daySymbol = if (dayCurrencies.size == 1 && dayCurrencies.first() != "BDT") {
                            AccountingEngine.currencySymbolForCode(dayCurrencies.first())
                        } else {
                            symbol
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f),
                            border = BorderStroke(0.6.dp, pocketLiquidGlassBorderBrush(isHighlighted = true))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Net: ${AccountingEngine.formatMoney(dayNet, daySymbol, showSign = true, includeDecimals = false)}",
                                    style = TabularMoneySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }
                    }
                    items(dayEntries, key = { it.uniqueKey }) { entry ->
                        val entryWalletCurrency = uiState.wallets.firstOrNull { it.id == entry.walletId }?.currency ?: "BDT"
                        val entrySymbol = AccountingEngine.currencySymbolForCode(entryWalletCurrency)
                        val isSelected = selectedEntryKeys.contains(entry.uniqueKey)
                        val historySubWalletContext = when (subWalletHistoryFilter) {
                            "PERSONAL" -> 1
                            "FUND" -> 2
                            else -> 0
                        }
                        LedgerEntryRowCard(
                            entry = entry,
                            currencySymbol = entrySymbol,
                            subWalletViewContext = historySubWalletContext,
                            showDateInSubtitle = false,
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
