package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.data.*
import com.example.ui.CloudConflictChoice
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CategoriesScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var openDialogInDeleteStep by remember { mutableStateOf(false) }
    val pocketColors = LocalPocketColors.current

    val displayedCategories = remember(uiState.categories, selectedType) {
        uiState.categories
            .filter { it.type == selectedType }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
    }

    val localOrderedCategories = remember { mutableStateListOf<CategoryEntity>() }
    var draggedCategoryId by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableStateOf(0f) }
    val itemHeightsPx = remember { mutableStateMapOf<Long, Int>() }
    val itemWindowTopY = remember { mutableStateMapOf<Long, Float>() }
    var deleteZoneBounds by remember { mutableStateOf<Rect?>(null) }
    var isHoveringDeleteZone by remember { mutableStateOf(false) }
    val spacingPx = with(density) { 10.dp.toPx() }

    LaunchedEffect(displayedCategories, draggedCategoryId) {
        if (draggedCategoryId == null) {
            localOrderedCategories.clear()
            localOrderedCategories.addAll(displayedCategories)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(if (showCategoryDialog) 14.dp else 0.dp)
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PocketTopBar(
                title = "Categories",
                onBack = { viewModel.navigateBack() },
                actions = {
                    val customCount = SubscriptionManager.countCustomCategories(uiState.categories)
                    val canAdd = SubscriptionManager.canAddCategory(customCount, uiState.settings.subscriptionTier, uiState.settings.subscriptionExpiresAt)
                    Button(
                        onClick = {
                            if (!canAdd) {
                                viewModel.showUpgradeDialog("Free tier allows 1 new custom category. Upgrade to Pocket Premium for unlimited categories.")
                            } else {
                                editingCategory = null
                                openDialogInDeleteStep = false
                                showCategoryDialog = true
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_category_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Category")
                    }
                }
            )

            // Pinned / Locked Top 2 Tabs (Expenses & Income) — stays fixed while scrolling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == "EXPENSE",
                    onClick = { selectedType = "EXPENSE" },
                    label = { Text("Expenses") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("categories_tab_expense")
                )
                FilterChip(
                    selected = selectedType == "INCOME",
                    onClick = { selectedType = "INCOME" },
                    label = { Text("Income") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("categories_tab_income")
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (localOrderedCategories.isEmpty()) {
                    item {
                        EmptyStateBox(
                            icon = Icons.Default.Category,
                            title = "No categories in this view",
                            subtitle = "Create a category to classify your transactions."
                        )
                    }
                } else {
                    items(localOrderedCategories, key = { it.id }) { cat ->
                        val isDefault = SubscriptionManager.isDefaultCategoryName(cat.name)
                        val isLockedForEdit = !uiState.isSubscriptionActive && isDefault
                        val isDragged = draggedCategoryId == cat.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 10f else 0f)
                                .then(if (!isDragged) Modifier.animateItem() else Modifier)
                                .onSizeChanged { itemHeightsPx[cat.id] = it.height }
                                .onGloballyPositioned { coords ->
                                    itemWindowTopY[cat.id] = coords.positionInWindow().y
                                }
                                .graphicsLayer {
                                    translationY = if (isDragged) draggedOffsetY else 0f
                                    scaleX = if (isDragged) 1.03f else 1f
                                    scaleY = if (isDragged) 1.03f else 1f
                                    shadowElevation = if (isDragged) 20f else 0f
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (draggedCategoryId == null) {
                                        if (isLockedForEdit) {
                                            viewModel.showUpgradeDialog("Editing default categories is a Pocket Premium feature. Upgrade to customize icons and names.")
                                        } else {
                                            editingCategory = cat
                                            openDialogInDeleteStep = false
                                            showCategoryDialog = true
                                        }
                                    }
                                }
                                .pointerInput(cat.id, isLockedForEdit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            performPocketHaptic(view, isLongPress = true)
                                            draggedCategoryId = cat.id
                                            draggedOffsetY = 0f
                                            isHoveringDeleteZone = false
                                        },
                                        onDragEnd = {
                                            val droppedOnDelete = isHoveringDeleteZone
                                            val orderedIds = localOrderedCategories.map { it.id }
                                            draggedCategoryId = null
                                            draggedOffsetY = 0f
                                            isHoveringDeleteZone = false
                                            if (droppedOnDelete) {
                                                if (cat.isSystemFee) {
                                                    viewModel.postStatusMessage("System fee category cannot be deleted.")
                                                } else if (isLockedForEdit) {
                                                    viewModel.showUpgradeDialog("Deleting default categories is a Pocket Premium feature. Upgrade to customize categories.")
                                                } else {
                                                    editingCategory = cat
                                                    openDialogInDeleteStep = true
                                                    showCategoryDialog = true
                                                }
                                            } else {
                                                viewModel.reorderCategoriesByIds(orderedIds)
                                            }
                                        },
                                        onDragCancel = {
                                            draggedCategoryId = null
                                            draggedOffsetY = 0f
                                            isHoveringDeleteZone = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedOffsetY += dragAmount.y

                                            val topY = (itemWindowTopY[cat.id] ?: 0f) + draggedOffsetY
                                            val itemH = (itemHeightsPx[cat.id] ?: 140).toFloat()
                                            val centerY = topY + itemH * 0.5f
                                            val dz = deleteZoneBounds
                                            val nowHovering = dz != null && centerY >= (dz.top - 24f)
                                            if (nowHovering != isHoveringDeleteZone) {
                                                isHoveringDeleteZone = nowHovering
                                                if (nowHovering) {
                                                    performPocketHaptic(view, isLongPress = true)
                                                }
                                            }

                                            if (!nowHovering) {
                                                val currentIndex = localOrderedCategories.indexOfFirst { it.id == cat.id }
                                                if (currentIndex != -1) {
                                                    val cardStep = itemH + spacingPx
                                                    val threshold = cardStep * 0.55f
                                                    if (draggedOffsetY > threshold && currentIndex < localOrderedCategories.lastIndex) {
                                                        val moved = localOrderedCategories.removeAt(currentIndex)
                                                        localOrderedCategories.add(currentIndex + 1, moved)
                                                        draggedOffsetY -= cardStep
                                                        performPocketHaptic(view)
                                                    } else if (draggedOffsetY < -threshold && currentIndex > 0) {
                                                        val moved = localOrderedCategories.removeAt(currentIndex)
                                                        localOrderedCategories.add(currentIndex - 1, moved)
                                                        draggedOffsetY += cardStep
                                                        performPocketHaptic(view)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                .testTag("category_item_${cat.name}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDragged) pocketColors.surfaceSelected else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (isDragged) 1.5.dp else 1.dp,
                                if (isDragged) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBadgeCircle(
                                    iconName = cat.iconName,
                                    colorHex = if (cat.type == "INCOME") PocketRepository.DEFAULT_INCOME_COLOR_HEX else PocketRepository.DEFAULT_EXPENSE_COLOR_HEX,
                                    size = 40.dp,
                                    iconSize = 20.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                        if (isDefault) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF334155),
                                                contentColor = Color(0xFF94A3B8)
                                            ) {
                                                Text(
                                                    text = "DEFAULT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        if (cat.isSystemFee) "System fee category" else cat.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isLockedForEdit) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Edit locked",
                                        tint = Color(0xFFF15A24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Hold and drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Delete Drop-Zone Popup while holding/dragging a category
        AnimatedVisibility(
            visible = draggedCategoryId != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        deleteZoneBounds = coords.boundsInWindow()
                    }
                    .graphicsLayer {
                        scaleX = if (isHoveringDeleteZone) 1.05f else 1f
                        scaleY = if (isHoveringDeleteZone) 1.05f else 1f
                    }
                    .testTag("category_drag_delete_drop_zone"),
                shape = RoundedCornerShape(18.dp),
                color = if (isHoveringDeleteZone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(
                    width = if (isHoveringDeleteZone) 2.dp else 1.dp,
                    color = MaterialTheme.colorScheme.error
                ),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete category",
                        tint = if (isHoveringDeleteZone) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isHoveringDeleteZone) "Release to Delete Category" else "Drag here to Delete Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHoveringDeleteZone) Color.White else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
    if (showCategoryDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
    }

    if (showCategoryDialog) {
        val effectiveType = editingCategory?.type ?: selectedType
        var name by remember(editingCategory) { mutableStateOf(editingCategory?.name ?: "") }
        var iconName by remember(editingCategory) { mutableStateOf(editingCategory?.iconName ?: "SHOPPING_BAG") }
        val colorHex = if (effectiveType == "INCOME") {
            PocketRepository.DEFAULT_INCOME_COLOR_HEX
        } else {
            PocketRepository.DEFAULT_EXPENSE_COLOR_HEX
        }
        var usageCount by remember(editingCategory) { mutableStateOf(0) }
        var showDeleteStep by remember(editingCategory, openDialogInDeleteStep) { mutableStateOf(openDialogInDeleteStep) }
        LaunchedEffect(editingCategory, openDialogInDeleteStep) {
            if (openDialogInDeleteStep && editingCategory != null) {
                usageCount = viewModel.countTransactionsForCategory(editingCategory!!.id)
                showDeleteStep = true
            }
        }
        var monthlyBudgetText by remember(editingCategory) {
            val init = editingCategory?.monthlyBudget ?: 0.0
            mutableStateOf(if (init > 0.0) formatNumericFieldInitialValue(init) else "")
        }
        val otherCategories = remember(uiState.categories, selectedType, editingCategory) {
            uiState.categories.filter { it.type == selectedType && it.id != editingCategory?.id && !it.isArchived }
        }
        var reassignId by remember(otherCategories) { mutableStateOf(otherCategories.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = {
                showCategoryDialog = false
                openDialogInDeleteStep = false
            },
            containerColor = pocketColors.overlaySurface,
            title = {
                Text(
                    if (editingCategory != null) {
                        if (effectiveType == "INCOME") "Edit Income Category" else "Edit Expense Category"
                    } else {
                        if (effectiveType == "INCOME") "New Income Category" else "New Expense Category"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = capitalizeFirstLetter(it) },
                        label = { Text("Category Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_name_input")
                    )
                    if (effectiveType == "EXPENSE") {
                        MathAmountTextField(
                            value = monthlyBudgetText,
                            onValueChange = { monthlyBudgetText = sanitizeMathExpressionInput(it) },
                            label = { Text("Monthly Limit (${uiState.settings.currencySymbol}) — Optional") },
                            placeholder = { Text("0 (No limit)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(monthlyBudgetText) { monthlyBudgetText = it }
                                .testTag("category_monthly_budget_input")
                        )
                    }
                    Text("Icon", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AvailableIconNames) { ic ->
                            val isSel = iconName == ic
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { iconName = ic },
                                shape = CircleShape,
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(iconForName(ic), contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    if (showDeleteStep && editingCategory != null) {
                        HorizontalDivider()
                        if (usageCount > 0) {
                            Text(
                                "Used in $usageCount transaction(s). Select a category to reassign those transactions before deleting:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(otherCategories, key = { it.id }) { target ->
                                    FilterChip(
                                        selected = reassignId == target.id,
                                        onClick = { reassignId = target.id },
                                        label = { Text(target.name) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                "This category has no transactions and can be deleted.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (showDeleteStep && editingCategory != null) {
                    Button(
                        onClick = {
                            viewModel.deleteOrReassignCategory(
                                categoryId = editingCategory!!.id,
                                reassignToCategoryId = if (usageCount > 0) reassignId else null
                            )
                            showCategoryDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = usageCount == 0 || reassignId != null
                    ) {
                        Text(if (usageCount > 0) "Reassign & Delete" else "Delete")
                    }
                } else {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val parsedCatBudget = (evaluateMathExpression(monthlyBudgetText) ?: monthlyBudgetText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                viewModel.saveCategory(
                                    CategoryEntity(
                                        id = editingCategory?.id ?: 0L,
                                        name = name.trim(),
                                        type = editingCategory?.type ?: selectedType,
                                        iconName = iconName,
                                        colorHex = colorHex,
                                        sortOrder = editingCategory?.sortOrder ?: 0,
                                        isArchived = editingCategory?.isArchived ?: false,
                                        isSystemFee = editingCategory?.isSystemFee ?: false,
                                        monthlyBudget = AccountingEngine.roundCurrency(parsedCatBudget)
                                    )
                                )
                                showCategoryDialog = false
                            }
                        },
                        modifier = Modifier.testTag("save_category_dialog_button")
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingCategory != null && !showDeleteStep) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    usageCount = viewModel.countTransactionsForCategory(editingCategory!!.id)
                                    showDeleteStep = true
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    }
                    TextButton(onClick = { showCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    initialOpenCreateDialog: Boolean = false
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var groupByPersonKhata by remember { mutableStateOf(true) }
    var expandedPersonName by remember { mutableStateOf<String?>(null) }
    var directionFilter by remember { mutableStateOf<String?>(null) }
    var showCreateLoanDialog by remember { mutableStateOf(initialOpenCreateDialog) }
    LaunchedEffect(initialOpenCreateDialog) {
        if (initialOpenCreateDialog) {
            showCreateLoanDialog = true
        }
    }
    var selectedLoanForRepayment by remember { mutableStateOf<LoanCalculatedSummary?>(null) }
    var loanForDeadlinePicker by remember { mutableStateOf<LoanEntity?>(null) }
    var pendingKhataForPdf by remember { mutableStateOf<PersonLoanKhataSummary?>(null) }
    val symbol = uiState.settings.currencySymbol
    val pocketColors = LocalPocketColors.current
    val deadlineFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    val exportKhataPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val khata = pendingKhataForPdf
        if (uri != null && khata != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    BackupManager.exportPersonKhataStatementToPdf(
                        khata = khata,
                        currencySymbol = khata.currencySymbol.ifBlank { symbol },
                        outputStream = out
                    )
                }
                viewModel.postStatusMessage("Saved ${khata.personName}'s Khata PDF statement.")
            }
        }
        pendingKhataForPdf = null
    }

    val receivableTotal = remember(uiState.loanSummaries) {
        val lent = uiState.loanSummaries.filter { it.loan.direction == LoanDirection.LENT.name }
        val primary = lent.filter { it.currencySymbol == symbol }
        (if (primary.isNotEmpty()) primary else lent).sumOf { it.outstandingPrincipal }
    }
    val receivableSymbol = remember(uiState.loanSummaries, symbol) {
        val lent = uiState.loanSummaries.filter { it.loan.direction == LoanDirection.LENT.name && it.outstandingPrincipal > 0.0 }
        val distinct = lent.map { it.currencySymbol }.distinct()
        if (distinct.size == 1) distinct.first() else symbol
    }
    val payableTotal = remember(uiState.loanSummaries) {
        val borrowed = uiState.loanSummaries.filter { it.loan.direction == LoanDirection.BORROWED.name }
        val primary = borrowed.filter { it.currencySymbol == symbol }
        (if (primary.isNotEmpty()) primary else borrowed).sumOf { it.outstandingPrincipal }
    }
    val payableSymbol = remember(uiState.loanSummaries, symbol) {
        val borrowed = uiState.loanSummaries.filter { it.loan.direction == LoanDirection.BORROWED.name && it.outstandingPrincipal > 0.0 }
        val distinct = borrowed.map { it.currencySymbol }.distinct()
        if (distinct.size == 1) distinct.first() else symbol
    }

    var loanSearchQuery by remember { mutableStateOf("") }

    val displayedLoans = remember(uiState.loanSummaries, directionFilter, loanSearchQuery) {
        val rawQ = loanSearchQuery.trim().lowercase(Locale.ENGLISH)
        val normQ = PocketLocalizer.normalizeDigits(rawQ)
        uiState.loanSummaries.filter { summary ->
            val matchesDirection = directionFilter == null || summary.loan.direction == directionFilter
            val matchesQuery = rawQ.isBlank() ||
                summary.loan.personName.lowercase(Locale.ENGLISH).contains(rawQ) ||
                summary.loan.note.lowercase(Locale.ENGLISH).contains(rawQ) ||
                summary.walletName.lowercase(Locale.ENGLISH).contains(rawQ) ||
                PocketLocalizer.translate(summary.walletName).lowercase(Locale.ENGLISH).contains(rawQ) ||
                summary.loan.principalAmount.toString().contains(normQ) ||
                summary.outstandingPrincipal.toString().contains(normQ)
            matchesDirection && matchesQuery
        }
    }

    val displayedKhataSummaries = remember(uiState.personLoanKhataSummaries, directionFilter, loanSearchQuery) {
        val q = loanSearchQuery.trim().lowercase(Locale.ENGLISH)
        val normQ = PocketLocalizer.normalizeDigits(q)
        uiState.personLoanKhataSummaries.filter { khata ->
            val matchesDirection = when (directionFilter) {
                LoanDirection.LENT.name -> khata.netReceivableMinusPayable > 0.0 || khata.totalLentOutstanding > 0.0
                LoanDirection.BORROWED.name -> khata.netReceivableMinusPayable < 0.0 || khata.totalBorrowedOutstanding > 0.0
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                khata.personName.lowercase(Locale.ENGLISH).contains(q) ||
                khata.loans.any {
                    it.loan.note.lowercase(Locale.ENGLISH).contains(q) ||
                        it.walletName.lowercase(Locale.ENGLISH).contains(q) ||
                        PocketLocalizer.translate(it.walletName).lowercase(Locale.ENGLISH).contains(q) ||
                        it.loan.principalAmount.toString().contains(normQ) ||
                        it.outstandingPrincipal.toString().contains(normQ)
                }
            matchesDirection && matchesQuery
        }
    }

    val isAnyLoanDialogOpen = showCreateLoanDialog || selectedLoanForRepayment != null || loanForDeadlinePicker != null

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(if (isAnyLoanDialogOpen) 14.dp else 0.dp)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = "",
            onLogoClick = { viewModel.navigateTo(PocketScreen.Settings) },
            actions = {
                Button(
                    onClick = {
                        performPocketHaptic(view)
                        showCreateLoanDialog = true
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_loan_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Loan")
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
            // Receivable vs Payable Overview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = pocketColors.incomeContainer,
                        border = BorderStroke(1.dp, pocketColors.incomeText.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("People owe you", style = MaterialTheme.typography.labelMedium, color = pocketColors.incomeText)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.settings.hideBalancesPrivacy) {
                                Text(
                                    text = "$receivableSymbol ••••",
                                    style = TabularMoneyMedium,
                                    color = pocketColors.incomeText,
                                    modifier = Modifier.testTag("loans_receivable_total")
                                )
                            } else {
                                AnimatedBalanceText(
                                    amount = receivableTotal,
                                    currencySymbol = receivableSymbol,
                                    includeDecimals = false,
                                    style = TabularMoneyMedium,
                                    color = pocketColors.incomeText,
                                    modifier = Modifier.testTag("loans_receivable_total")
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = pocketColors.loanContainer,
                        border = BorderStroke(1.dp, pocketColors.loanText.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("You owe others", style = MaterialTheme.typography.labelMedium, color = pocketColors.loanText)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.settings.hideBalancesPrivacy) {
                                Text(
                                    text = "$payableSymbol ••••",
                                    style = TabularMoneyMedium,
                                    color = pocketColors.loanText,
                                    modifier = Modifier.testTag("loans_payable_total")
                                )
                            } else {
                                AnimatedBalanceText(
                                    amount = payableTotal,
                                    currencySymbol = payableSymbol,
                                    includeDecimals = false,
                                    style = TabularMoneyMedium,
                                    color = pocketColors.loanText,
                                    modifier = Modifier.testTag("loans_payable_total")
                                )
                            }
                        }
                    }
                }
            }

            // View Mode Toggle: By Person (Khata) vs All Records
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = groupByPersonKhata,
                        onClick = {
                            performPocketHaptic(view)
                            groupByPersonKhata = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("By Person (Khata)") },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("loans_khata_mode_chip")
                    )
                    FilterChip(
                        selected = !groupByPersonKhata,
                        onClick = {
                            performPocketHaptic(view)
                            groupByPersonKhata = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Individual Loans (${uiState.loanSummaries.size})") },
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("loans_individual_mode_chip")
                    )
                }
            }

            // Filter Chips + Search Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = directionFilter == null,
                            onClick = { directionFilter = null },
                            label = { Text("All (${uiState.loanSummaries.size})") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = directionFilter == LoanDirection.LENT.name,
                            onClick = { directionFilter = LoanDirection.LENT.name },
                            label = { Text("Lent (Receivable)") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = directionFilter == LoanDirection.BORROWED.name,
                            onClick = { directionFilter = LoanDirection.BORROWED.name },
                            label = { Text("Borrowed (Payable)") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    OutlinedTextField(
                        value = loanSearchQuery,
                        onValueChange = { loanSearchQuery = it },
                        placeholder = { Text("Search person, note, wallet, or amount...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search loans and Khata"
                            )
                        },
                        trailingIcon = {
                            if (loanSearchQuery.isNotBlank()) {
                                IconButton(onClick = { loanSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear loan search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loans_search_input")
                    )
                }
            }

            val isCurrentLoanViewEmpty = if (groupByPersonKhata) displayedKhataSummaries.isEmpty() else displayedLoans.isEmpty()
            if (isCurrentLoanViewEmpty) {
                item {
                    EmptyStateBox(
                        icon = Icons.Default.Handshake,
                        title = "No loans recorded",
                        subtitle = "",
                        actionLabel = "Create First Loan",
                        onAction = { showCreateLoanDialog = true }
                    )
                }
            } else if (groupByPersonKhata) {
                items(displayedKhataSummaries, key = { it.personName.lowercase(Locale.ENGLISH) }) { khata ->
                    val net = khata.netReceivableMinusPayable
                    val khataSym = khata.currencySymbol.ifBlank { symbol }
                    val isExpanded = expandedPersonName.equals(khata.personName, ignoreCase = true)
                    val statusHeadline = when {
                        kotlin.math.abs(net) < 0.005 -> "${khata.personName} is settled"
                        net > 0 -> "${khata.personName} owes you net ${AccountingEngine.formatMoney(net, khataSym, includeDecimals = false)}"
                        else -> "You owe ${khata.personName} net ${AccountingEngine.formatMoney(kotlin.math.abs(net), khataSym, includeDecimals = false)}"
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                performPocketHaptic(view)
                                expandedPersonName = if (isExpanded) null else khata.personName
                            }
                            .testTag("khata_person_card_${khata.personName}"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = khata.personName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = statusHeadline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            kotlin.math.abs(net) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant
                                            net > 0 -> pocketColors.incomeText
                                            else -> pocketColors.loanText
                                        }
                                    )
                                    Text(
                                        text = "${khata.loans.size} loan record${if (khata.loans.size == 1) "" else "s"} · Tap to ${if (isExpanded) "hide" else "view"} khata ledger",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand Khata",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        performPocketHaptic(view)
                                        val statementText = BackupManager.formatPersonKhataStatementText(khata, khataSym)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Loan Khata Statement - ${khata.personName}")
                                            putExtra(Intent.EXTRA_TEXT, statementText)
                                        }
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Share ${khata.personName}'s Khata Statement")
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .testTag("share_khata_statement_button_${khata.personName}"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Khata Statement",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Share Khata Statement",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        performPocketHaptic(view)
                                        pendingKhataForPdf = khata
                                        val safeName = khata.personName.replace(Regex("[^\\p{L}\\p{M}\\p{N}_-]"), "_").ifBlank { "Person" }
                                        exportKhataPdfLauncher.launch("Khata_${safeName}.pdf")
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 42.dp)
                                        .testTag("export_khata_pdf_button_${khata.personName}"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Save Khata PDF",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PDF",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = pocketColors.cardBorder.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(10.dp))
                                khata.loans.forEach { summary ->
                                    val isLent = summary.loan.direction == LoanDirection.LENT.name
                                    val dueMillis = summary.loan.dueDate
                                    val loanSymbol = uiState.wallets.firstOrNull { it.id == summary.loan.initialWalletId }
                                        ?.currency?.takeIf { it.isNotBlank() }
                                        ?.let { AccountingEngine.currencySymbolForCode(it) }
                                        ?: symbol
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedLoanForRepayment = summary },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "${if (isLent) "Lent" else "Borrowed"} ${AccountingEngine.formatMoney(summary.originalPrincipal, loanSymbol, includeDecimals = false)} (${summary.walletName})",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (summary.loan.attachmentUri.isNotBlank()) {
                                                        Icon(
                                                            imageVector = Icons.Default.PhotoCamera,
                                                            contentDescription = "Has slip photo",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = buildString {
                                                        append("Outstanding: ${AccountingEngine.formatMoney(summary.outstandingPrincipal, loanSymbol, includeDecimals = false)}")
                                                        if (dueMillis != null) {
                                                            append(" · Due ${deadlineFormatter.format(Date(dueMillis))}")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { selectedLoanForRepayment = summary },
                                                modifier = Modifier.heightIn(min = 38.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (summary.status == "SETTLED") "Ledger" else "Repay",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(displayedLoans, key = { it.loan.id }) { summary ->
                    val isLent = summary.loan.direction == LoanDirection.LENT.name
                    val dueMillis = summary.loan.dueDate
                    val isOverdue = summary.status == "OVERDUE"
                    val loanSymbol = uiState.wallets.firstOrNull { it.id == summary.loan.initialWalletId }
                        ?.currency?.takeIf { it.isNotBlank() }
                        ?.let { AccountingEngine.currencySymbolForCode(it) }
                        ?: symbol
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedLoanForRepayment = summary }
                            .testTag("loan_card_${summary.loan.personName}"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(summary.loan.personName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${if (isLent) "Lent" else "Borrowed"} · Original ${AccountingEngine.formatMoney(summary.originalPrincipal, loanSymbol, includeDecimals = false)} · ${summary.walletName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (summary.status) {
                                        "SETTLED" -> pocketColors.incomeContainer
                                        "OVERDUE" -> pocketColors.expenseContainer
                                        else -> pocketColors.loanContainer
                                    }
                                ) {
                                    Text(
                                        text = summary.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (summary.status) {
                                            "SETTLED" -> pocketColors.incomeText
                                            "OVERDUE" -> pocketColors.expenseText
                                            else -> pocketColors.loanText
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Deadline Date Box (Partial or Full Payment/Receipt Deadline)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { loanForDeadlinePicker = summary.loan }
                                    .testTag("loan_deadline_box_${summary.loan.personName}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOverdue) pocketColors.expenseContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isOverdue) pocketColors.expenseText.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = "Deadline Date",
                                            tint = if (isOverdue) pocketColors.expenseText else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (isLent) "Deadline to receive (partial or full)" else "Deadline to pay (partial or full)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (dueMillis != null) deadlineFormatter.format(Date(dueMillis)) else "Tap to set deadline date",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOverdue) pocketColors.expenseText
                                                else if (dueMillis != null) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.EditCalendar,
                                        contentDescription = "Change deadline date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Outstanding: ${AccountingEngine.formatMoney(summary.outstandingPrincipal, loanSymbol)}",
                                        style = TabularMoneyMedium,
                                        color = if (isLent) pocketColors.incomeText else pocketColors.loanText
                                    )
                                    if (summary.principalRepaid > 0.0) {
                                        Text(
                                            text = "Repaid so far: ${AccountingEngine.formatMoney(summary.principalRepaid, loanSymbol)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { selectedLoanForRepayment = summary },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(if (summary.status == "SETTLED") "Ledger" else "Repay / View")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (isAnyLoanDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
    }

    // Standalone DatePickerDialog when tapping a loan card's Deadline Date box
    loanForDeadlinePicker?.let { targetLoan ->
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetLoan.dueDate ?: (System.currentTimeMillis() + 7L * 24L * 3600L * 1000L)
        )
        DatePickerDialog(
            onDismissRequest = { loanForDeadlinePicker = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateLoanDeadline(targetLoan, datePickerState.selectedDateMillis)
                        loanForDeadlinePicker = null
                    },
                    modifier = Modifier.testTag("confirm_loan_deadline_picker_button")
                ) {
                    Text("Save Deadline")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targetLoan.dueDate != null) {
                        TextButton(
                            onClick = {
                                viewModel.updateLoanDeadline(targetLoan, null)
                                loanForDeadlinePicker = null
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { loanForDeadlinePicker = null }) {
                        Text("Cancel")
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCreateLoanDialog) {
        var personName by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf(LoanDirection.LENT) }
        var principalText by remember { mutableStateOf("") }
        var walletId by remember {
            mutableStateOf(
                uiState.settings.defaultWalletId?.takeIf { defId -> uiState.activeWalletSummaries.any { it.wallet.id == defId } }
                    ?: uiState.activeWalletSummaries.firstOrNull()?.wallet?.id
                    ?: 0L
            )
        }
        var loanStartDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var showLoanStartDatePicker by remember { mutableStateOf(false) }
        var dueDateMillis by remember { mutableStateOf<Long?>(null) }
        var showNewLoanDatePicker by remember { mutableStateOf(false) }
        var noteText by remember { mutableStateOf("") }
        var loanAttachmentUri by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateLoanDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Text(
                    text = "Create Loan Record",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val selectedLoanWalletSymbol = remember(walletId, uiState.activeWalletSummaries, symbol) {
                    val wCurr = uiState.activeWalletSummaries.firstOrNull { it.wallet.id == walletId }?.wallet?.currency
                    if (wCurr.isNullOrBlank() || wCurr.equals("BDT", ignoreCase = true)) {
                        symbol
                    } else {
                        AccountingEngine.currencySymbolForCode(wCurr)
                    }
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = direction == LoanDirection.LENT,
                            onClick = { direction = LoanDirection.LENT },
                            label = { Text("Lent (Receivable)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        )
                        FilterChip(
                            selected = direction == LoanDirection.BORROWED,
                            onClick = { direction = LoanDirection.BORROWED },
                            label = { Text("Borrowed (Payable)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        )
                    }
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = capitalizeFirstLetter(it) },
                        label = { Text("Person or Institution Name", maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_person_name_input")
                    )
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = { principalText = sanitizeNumericInput(it) },
                        label = { Text("Principal Amount ($selectedLoanWalletSymbol)", maxLines = 1) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(principalText) { principalText = it }
                            .testTag("loan_principal_input")
                    )

                    // Single Date of Giving / Taking Loan Picker (Today selected by default)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showLoanStartDatePicker = true }
                            .testTag("loan_start_date_picker_badge"),
                        shape = RoundedCornerShape(12.dp),
                        color = pocketColors.surfaceElevated,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Loan Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${if (direction == LoanDirection.LENT) "Date of Giving Loan" else "Date of Taking Loan"}: ${deadlineFormatter.format(Date(loanStartDateMillis))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Change Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Single Optional Deadline Date Picker (same as Startup Page 1/2)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showNewLoanDatePicker = true }
                            .testTag("create_loan_deadline_box"),
                        shape = RoundedCornerShape(12.dp),
                        color = pocketColors.surfaceElevated,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Deadline Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = dueDateMillis?.let {
                                        "Deadline: ${deadlineFormatter.format(Date(it))}"
                                    } ?: "Deadline Date (Optional - Tap to pick)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (dueDateMillis != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (dueDateMillis != null) {
                                IconButton(
                                    onClick = { dueDateMillis = null },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear deadline",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text("Wallet for initial disbursement / receipt", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.activeWalletSummaries, key = { it.wallet.id }) { ws ->
                            val isSelected = walletId == ws.wallet.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { walletId = ws.wallet.id }
                                    .testTag("create_loan_wallet_${ws.wallet.name}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) pocketColors.surfaceSelected else pocketColors.surfaceElevated,
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconBadgeCircle(
                                        iconName = ws.wallet.iconName,
                                        colorHex = ws.wallet.colorHex,
                                        size = 24.dp,
                                        iconSize = 13.dp,
                                        walletNameHint = ws.wallet.name
                                    )
                                    val wsSymbol = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                    Column {
                                        Text(
                                            text = ws.wallet.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = AccountingEngine.formatMoney(ws.currentBalance, wsSymbol),
                                            style = TabularMoneySmall,
                                            color = if (ws.currentBalance < 0) pocketColors.expenseText else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = capitalizeFirstLetter(it) },
                        label = { Text("Note / Reference") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AttachmentPhotoRow(
                        attachmentUri = loanAttachmentUri,
                        onAttachmentChanged = { loanAttachmentUri = it },
                        label = "Attach Loan Proof (Optional)"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = principalText.toDoubleOrNull() ?: 0.0
                        if (personName.isNotBlank() && p > 0.0 && walletId != 0L) {
                            viewModel.createLoan(
                                LoanEntity(
                                    personName = personName.trim(),
                                    direction = direction.name,
                                    principalAmount = AccountingEngine.roundCurrency(p),
                                    initialWalletId = walletId,
                                    startDate = loanStartDateMillis,
                                    dueDate = dueDateMillis,
                                    note = noteText.trim(),
                                    attachmentUri = loanAttachmentUri
                                )
                            )
                            showCreateLoanDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_loan_dialog_button")
                ) {
                    Text("Save Loan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateLoanDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        if (showLoanStartDatePicker) {
            val startPickerState = rememberDatePickerState(
                initialSelectedDateMillis = loanStartDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showLoanStartDatePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            startPickerState.selectedDateMillis?.let { loanStartDateMillis = it }
                            showLoanStartDatePicker = false
                        }
                    ) {
                        Text("Select Date")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLoanStartDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = startPickerState)
            }
        }

        if (showNewLoanDatePicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = dueDateMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showNewLoanDatePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            dueDateMillis = pickerState.selectedDateMillis
                            showNewLoanDatePicker = false
                        }
                    ) {
                        Text("Select Date")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewLoanDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    selectedLoanForRepayment?.let { initialSummary ->
        val liveSummary = uiState.loanSummaries.firstOrNull { it.loan.id == initialSummary.loan.id } ?: initialSummary
        var principalPortionText by remember(liveSummary) {
            mutableStateOf(formatNumericFieldInitialValue(liveSummary.outstandingPrincipal))
        }
        var interestPortionText by remember { mutableStateOf("") }
        var repaymentWalletId by remember {
            mutableStateOf(liveSummary.loan.initialWalletId)
        }
        var repaymentNote by remember { mutableStateOf("") }
        var allowOverRepayment by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        val loanSymbol = remember(liveSummary.loan.initialWalletId, uiState.wallets, symbol) {
            uiState.wallets.firstOrNull { it.id == liveSummary.loan.initialWalletId }
                ?.currency?.takeIf { it.isNotBlank() }
                ?.let { AccountingEngine.currencySymbolForCode(it) }
                ?: symbol
        }
        val repaymentWalletSymbol = remember(repaymentWalletId, uiState.wallets, loanSymbol) {
            uiState.wallets.firstOrNull { it.id == repaymentWalletId }
                ?.currency?.takeIf { it.isNotBlank() }
                ?.let { AccountingEngine.currencySymbolForCode(it) }
                ?: loanSymbol
        }

        AlertDialog(
            onDismissRequest = { selectedLoanForRepayment = null },
            containerColor = pocketColors.overlaySurface,
            title = { Text("${liveSummary.loan.personName} · Loan Ledger") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Original: ${AccountingEngine.formatMoney(liveSummary.originalPrincipal, loanSymbol)} · Outstanding: ${AccountingEngine.formatMoney(liveSummary.outstandingPrincipal, loanSymbol)}",
                        style = TabularMoneySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Deadline Date Box inside Ledger Dialog
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { loanForDeadlinePicker = liveSummary.loan },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Deadline: ${liveSummary.loan.dueDate?.let { deadlineFormatter.format(Date(it)) } ?: "Not set (tap to set)"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Edit Deadline",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Attached Loan Slip / Proof Photo Viewer & Editor (Item 3)
                    AttachmentPhotoRow(
                        attachmentUri = liveSummary.loan.attachmentUri,
                        onAttachmentChanged = { newUri ->
                            viewModel.updateLoanAttachment(liveSummary.loan, newUri)
                        },
                        label = "Attach Loan Slip / Screenshot"
                    )

                    if (liveSummary.outstandingPrincipal > 0.005 || allowOverRepayment) {
                        HorizontalDivider()
                        Text("Record Repayment (Partial or Full)", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    principalPortionText = formatNumericFieldInitialValue(
                                        AccountingEngine.roundCurrency(liveSummary.outstandingPrincipal / 2.0)
                                    )
                                },
                                label = { Text("50% Partial") }
                            )
                            AssistChip(
                                onClick = {
                                    principalPortionText = formatNumericFieldInitialValue(
                                        AccountingEngine.roundCurrency(liveSummary.outstandingPrincipal)
                                    )
                                },
                                label = { Text("100% Full") }
                            )
                        }
                        OutlinedTextField(
                            value = principalPortionText,
                            onValueChange = {
                                principalPortionText = sanitizeNumericInput(it)
                                errorMsg = null
                            },
                            label = { Text("Principal Portion ($repaymentWalletSymbol)") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(principalPortionText) { principalPortionText = it }
                                .testTag("loan_repayment_principal_input")
                        )
                        OutlinedTextField(
                            value = interestPortionText,
                            onValueChange = { interestPortionText = sanitizeNumericInput(it) },
                            label = {
                                Text(
                                    if (liveSummary.loan.direction == LoanDirection.LENT.name)
                                        "Interest Received ($repaymentWalletSymbol — recorded as Income)"
                                    else
                                        "Interest Paid ($repaymentWalletSymbol — recorded as Expense)"
                                )
                            },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(interestPortionText) { interestPortionText = it }
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.activeWalletSummaries, key = { it.wallet.id }) { ws ->
                                FilterChip(
                                    selected = repaymentWalletId == ws.wallet.id,
                                    onClick = { repaymentWalletId = ws.wallet.id },
                                    label = { Text(ws.wallet.name) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = repaymentNote,
                            onValueChange = { repaymentNote = capitalizeFirstLetter(it) },
                            label = { Text("Repayment Note") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMsg != null) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = allowOverRepayment, onCheckedChange = { allowOverRepayment = it })
                                Text("Allow principal adjustment exceeding current outstanding", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Activity Ledger (${liveSummary.activities.size})", style = MaterialTheme.typography.labelLarge)
                    liveSummary.activities.forEach { act ->
                        val actDateText = remember(act.date) { formatRelativeDate(act.date) }
                        val actSymbol = uiState.wallets.firstOrNull { it.id == act.walletId }
                            ?.currency?.takeIf { it.isNotBlank() }
                            ?.let { AccountingEngine.currencySymbolForCode(it) }
                            ?: loanSymbol
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${act.activityType} · $actDateText: ${AccountingEngine.formatMoney(act.principalPortion, actSymbol)}${if (act.interestPortion > 0) " (+${AccountingEngine.formatMoney(act.interestPortion, actSymbol)} interest)" else ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (act.note.isNotBlank()) {
                                    Text(act.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (act.activityType != LoanActivityType.DISBURSEMENT.name) {
                                IconButton(
                                    onClick = { viewModel.deleteLoanActivity(act.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete activity", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (liveSummary.outstandingPrincipal > 0.005 || allowOverRepayment) {
                    Button(
                        onClick = {
                            val prin = principalPortionText.toDoubleOrNull() ?: 0.0
                            val intr = (interestPortionText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                            if (prin <= 0.0 && intr <= 0.0) {
                                errorMsg = "Enter a positive principal or interest amount."
                            } else if (prin > liveSummary.outstandingPrincipal + 0.01 && !allowOverRepayment) {
                                errorMsg = "Principal repayment exceeds outstanding (${AccountingEngine.formatMoney(liveSummary.outstandingPrincipal, loanSymbol)})."
                            } else {
                                viewModel.recordLoanRepayment(
                                    loan = liveSummary.loan,
                                    activity = LoanActivityEntity(
                                        loanId = liveSummary.loan.id,
                                        activityType = LoanActivityType.REPAYMENT.name,
                                        principalPortion = AccountingEngine.roundCurrency(prin.coerceAtLeast(0.0)),
                                        interestPortion = AccountingEngine.roundCurrency(intr),
                                        walletId = repaymentWalletId,
                                        date = System.currentTimeMillis(),
                                        note = repaymentNote.trim()
                                    )
                                )
                                selectedLoanForRepayment = null
                            }
                        },
                        modifier = Modifier.testTag("confirm_repayment_button")
                    ) {
                        Text("Record Repayment")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.deleteLoan(liveSummary.loan.id)
                            selectedLoanForRepayment = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Loan")
                    }
                    TextButton(onClick = { selectedLoanForRepayment = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = uiState.settings
    val pocketColors = LocalPocketColors.current
    var showPinDialog by remember { mutableStateOf(false) }
    var enableBiometricsAfterPin by remember { mutableStateOf(false) }
    var userNameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
    var pendingCropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingWallpaperCropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showDailyReminderTimePicker by remember { mutableStateOf(false) }
    var showResetDataConfirmDialog by remember { mutableStateOf(false) }
    var showAccentColorWheelDialog by remember { mutableStateOf(false) }
    var showBoxColorWheelDialog by remember { mutableStateOf(false) }
    var showDeveloperInfoDialog by remember { mutableStateOf(false) }
    var showDeveloperCodeManagerDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val profilePhotoPicker = rememberUniversalImagePickerLauncher(
        onUriSelected = { uri ->
            scope.launch {
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeExifCorrectedBitmap(context, uri.toString(), maxDimension = 1024)
                }
                if (bmp != null) {
                    pendingCropBitmap = bmp
                } else {
                    val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveUprightImageFromUri(context, uri, "profile_photos", "profile")
                    }
                    if (savedPath != null) {
                        viewModel.applyProfilePhotoPath(savedPath)
                    }
                }
            }
        }
    )

    val wallpaperPicker = rememberUniversalImagePickerLauncher(
        onUriSelected = { uri ->
            scope.launch {
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeExifCorrectedBitmap(context, uri.toString(), maxDimension = 2048)
                }
                if (bmp != null) {
                    pendingWallpaperCropBitmap = bmp
                } else {
                    val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveUprightImageFromUri(
                            context = context,
                            uri = uri,
                            subDir = "wallpapers",
                            prefix = "wallpaper",
                            maxDimension = 2048,
                            compressAsJpeg = true
                        )
                    }
                    if (savedPath != null) {
                        viewModel.applyCustomWallpaperPath(savedPath)
                    }
                }
            }
        }
    )

    val anyDialogOpen = showPinDialog ||
        pendingCropBitmap != null ||
        pendingWallpaperCropBitmap != null ||
        showDailyReminderTimePicker ||
        showResetDataConfirmDialog ||
        showAccentColorWheelDialog ||
        showBoxColorWheelDialog ||
        showDeveloperInfoDialog ||
        showDeveloperCodeManagerDialog ||
        showFeedbackDialog

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(if (anyDialogOpen) 14.dp else 0.dp)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = "Settings",
            onBack = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // i. Profile Photo & Name (tapping the user photo icon circle changes photo)
            item {
                val effectivePhotoUri = settings.profilePhotoUri.ifBlank {
                    uiState.googleAccountPhotoUrl ?: ""
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UserProfileBadge(
                            userName = settings.userName.ifBlank { uiState.googleAccountName ?: "" },
                            profilePhotoUri = effectivePhotoUri,
                            avatarSize = 56.dp,
                            onClick = { profilePhotoPicker() }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = {
                                    val capped = capitalizeFirstLetter(it)
                                    userNameInput = capped
                                    viewModel.updateSettings(settings.copy(userName = capped.trim()))
                                },
                                label = { Text("Your Name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_user_name_input")
                            )
                            if (!uiState.googleAccountPhotoUrl.isNullOrBlank() && effectivePhotoUri != uiState.googleAccountPhotoUrl) {
                                TextButton(
                                    onClick = { viewModel.useGoogleProfilePhoto() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.testTag("settings_use_google_photo_button")
                                ) {
                                    Text(
                                        text = "Use Google Photo",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ii. Free/Premium tier & sign in with google button (as it is)
            item {
                val isPremium = uiState.isSubscriptionActive
                val tier = uiState.activeTier
                val expiryText = uiState.settings.subscriptionExpiresAt?.let {
                    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(it))
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPremium) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF15A24).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isPremium) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF15A24).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showUpgradeDialog() }
                        .testTag("settings_subscription_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isPremium) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF15A24).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPremium) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isPremium) Color(0xFF10B981) else Color(0xFFF15A24),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isPremium) {
                                            if (tier == SubscriptionTier.LIFETIME) "Lifetime Premium" else "3 Days Premium"
                                        } else {
                                            "Free Tier Plan"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isPremium) Color(0xFF10B981) else Color(0xFFF15A24)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isPremium) Color(0xFF10B981) else Color(0xFFF15A24),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (isPremium) "PREMIUM" else "FREE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isPremium) {
                                        if (tier == SubscriptionTier.LIFETIME) "All features permanently unlocked" else "3 Days Premium active until $expiryText"
                                    } else {
                                        "Tap to upgrade to Pocket Premium"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Upgrade details",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            item {
                GoogleAccountSyncCard(
                    signedInEmail = uiState.googleAccountEmail,
                    signedInName = uiState.googleAccountName,
                    signedInPhotoUrl = uiState.googleAccountPhotoUrl,
                    isSyncing = uiState.isCloudSyncing,
                    lastSyncStatus = uiState.lastCloudSyncStatus,
                    autoAttemptSignInOnLaunch = false,
                    confirmBeforeOfflineSignIn = false,
                    onGoogleSignedIn = { user -> viewModel.onGoogleUserSignedIn(user) },
                    onSyncNowClicked = { viewModel.syncCloudNow() },
                    onSignOutClicked = { viewModel.signOutGoogle(context) },
                    onDeleteAccountClicked = { viewModel.deleteGoogleAccountPermanently(context) },
                    modifier = Modifier.testTag("settings_google_sync_card")
                )
            }

            // iii. Financial Modules
            item {
                Text("Financial Modules", style = MaterialTheme.typography.titleMedium)
            }
            item {
                SettingsNavCard(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = null,
                    testTag = "settings_nav_categories",
                    onClick = { viewModel.navigateTo(PocketScreen.CategoriesManagement) }
                )
            }
            item {
                SettingsNavCard(
                    icon = Icons.Default.EventRepeat,
                    title = "Recurring Bills",
                    subtitle = null,
                    testTag = "settings_nav_recurring",
                    onClick = { viewModel.navigateTo(PocketScreen.RecurringManagement) }
                )
            }
            item {
                SettingsNavCard(
                    icon = Icons.Default.Widgets,
                    title = "Widgets",
                    subtitle = null,
                    testTag = "settings_nav_widgets",
                    onClick = { viewModel.navigateTo(PocketScreen.WidgetConfig) }
                )
            }
            item {
                SettingsNavCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "Monthly Statement",
                    subtitle = null,
                    testTag = "settings_nav_statement",
                    onClick = { viewModel.navigateTo(PocketScreen.MonthlyStatement()) }
                )
            }
            item {
                SettingsNavCard(
                    icon = Icons.Default.Backup,
                    title = "Backup",
                    subtitle = null,
                    testTag = "settings_nav_backup",
                    onClick = { viewModel.navigateTo(PocketScreen.BackupRestore) }
                )
            }

            // iv. Preferences
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Preferences", style = MaterialTheme.typography.titleMedium)
            }

            // 1. App theme
            item {
                val isDarkTheme = settings.themeMode.uppercase() != "LIGHT"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App theme",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .testTag("settings_theme_toggle"),
                            shape = RoundedCornerShape(24.dp),
                            color = pocketColors.surfaceElevated,
                            border = BorderStroke(1.dp, pocketColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Triple("DARK", "Dark", Icons.Default.DarkMode),
                                    Triple("LIGHT", "Light", Icons.Default.LightMode)
                                ).forEach { (mode, label, icon) ->
                                    val selected = (mode == "DARK" && isDarkTheme) || (mode == "LIGHT" && !isDarkTheme)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                performPocketHaptic(view)
                                                viewModel.updateSettings(settings.copy(themeMode = mode))
                                            }
                                            .testTag("theme_mode_$mode"),
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Accent color
            item {
                val accentOptions = listOf(
                    "#F15A24" to "Orange",
                    "#14B8A6" to "Teal",
                    "#3B82F6" to "Blue",
                    "#8B5CF6" to "Violet",
                    "#F43F5E" to "Rose"
                )
                val currentAccent = settings.accentColorHex.ifBlank { "#F15A24" }
                val isCustomAccentSelected = accentOptions.none { it.first.equals(currentAccent, ignoreCase = true) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Accent color",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("accent_color_picker_row")
                        ) {
                            items(accentOptions, key = { it.first }) { (hex, label) ->
                                val isSelected = currentAccent.equals(hex, ignoreCase = true)
                                val swatchColor = parseHexColor(hex)
                                val isAllowedInFree = SubscriptionManager.isAccentColorAllowed(hex, settings.subscriptionTier, settings.subscriptionExpiresAt)
                                val isLocked = !uiState.isSubscriptionActive && !isAllowedInFree
                                Surface(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            performPocketHaptic(view)
                                            if (isLocked) {
                                                viewModel.showUpgradeDialog("Orange and Teal are free. Upgrade to Pocket Premium to unlock 3 more prebuilt colors and the custom color picker.")
                                            } else {
                                                viewModel.updateSettings(settings.copy(accentColorHex = hex))
                                            }
                                        }
                                        .testTag("accent_color_${label.lowercase()}"),
                                    shape = CircleShape,
                                    color = swatchColor,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else pocketColors.cardBorder
                                    )
                                ) {
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "$label locked",
                                                tint = Color.White.copy(alpha = 0.95f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "$label selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "rainbow_custom_wheel") {
                                RainbowColorWheelSwatch(
                                    isCustomSelected = isCustomAccentSelected,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    isLocked = !uiState.isSubscriptionActive,
                                    onClick = {
                                        performPocketHaptic(view)
                                        if (!uiState.isSubscriptionActive) {
                                            viewModel.showUpgradeDialog("Custom color picker is a Pocket Premium feature. Upgrade to pick any custom HEX color.")
                                        } else {
                                            showAccentColorWheelDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Background wallpaper & Opacity
            item {
                val hasCustomWallpaper = settings.customBackgroundUri.isNotBlank() &&
                    java.io.File(settings.customBackgroundUri.removePrefix("CUSTOM_URI:")).exists()
                val wallpaperBitmap = rememberDecodedImageBitmap(settings.customBackgroundUri, maxDimension = 256)

                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Background wallpaper & Opacity", style = MaterialTheme.typography.titleSmall)
                            }

                            if (hasCustomWallpaper && wallpaperBitmap != null) {
                                Surface(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Image(
                                        bitmap = wallpaperBitmap,
                                        contentDescription = "Active wallpaper thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { wallpaperPicker() },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_upload_wallpaper_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasCustomWallpaper) Icons.Default.Image else Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasCustomWallpaper) "Change Wallpaper" else "Upload Wallpaper")
                            }

                            if (hasCustomWallpaper) {
                                OutlinedButton(
                                    onClick = { viewModel.removeCustomWallpaper() },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_remove_wallpaper_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove wallpaper",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        }

                        HorizontalDivider(color = pocketColors.cardBorder)

                        val opacityPct = (settings.backgroundOpacity.coerceIn(0f, 1f) * 100).toInt()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Wallpaper Opacity",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$opacityPct%",
                                    style = TabularMoneySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PocketThinSlider(
                                value = settings.backgroundOpacity.coerceIn(0f, 1f),
                                onValueChange = { viewModel.updateBackgroundOpacity(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_wallpaper_opacity_slider")
                            )
                        }

                        HorizontalDivider(color = pocketColors.cardBorder)

                        // Box Color (2 free presets + 3 paid presets + 1 paid custom color picker) & Box Opacity slider (free)
                        val boxColorPresets = listOf(
                            "#141416" to "Carbon",
                            "#1E293B" to "Slate",
                            "#172554" to "Midnight",
                            "#14532D" to "Emerald",
                            "#31102F" to "Burgundy"
                        )
                        val currentBoxHex = settings.boxColorHex.trim()
                        val effectiveBoxHex = currentBoxHex.ifBlank {
                            if (settings.themeMode.equals("LIGHT", ignoreCase = true)) "#FFFFFF" else "#141416"
                        }
                        val isCustomBoxColor = currentBoxHex.isNotBlank() &&
                            boxColorPresets.none { it.first.equals(currentBoxHex, ignoreCase = true) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Box Color",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (currentBoxHex.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        performPocketHaptic(view)
                                        viewModel.updateBoxColor("")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .testTag("settings_reset_box_color_button")
                                ) {
                                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("box_color_picker_row")
                        ) {
                            items(boxColorPresets, key = { it.first }) { (hex, label) ->
                                val isSelected = effectiveBoxHex.equals(hex, ignoreCase = true)
                                val swatchColor = parseHexColor(hex)
                                val isAllowedInFree = SubscriptionManager.isBoxColorAllowed(hex, settings.subscriptionTier, settings.subscriptionExpiresAt)
                                val isLocked = !uiState.isSubscriptionActive && !isAllowedInFree
                                Surface(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            performPocketHaptic(view)
                                            if (isLocked) {
                                                viewModel.showUpgradeDialog("Carbon and Slate box colors and Box Opacity are free. Upgrade to Pocket Premium to unlock Midnight, Emerald, Burgundy, and custom box colors.")
                                            } else {
                                                viewModel.updateBoxColor(hex)
                                            }
                                        }
                                        .testTag("box_color_${label.lowercase()}"),
                                    shape = CircleShape,
                                    color = swatchColor,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                    )
                                ) {
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "$label locked",
                                                tint = Color.White.copy(alpha = 0.95f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "$label selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "box_color_custom_wheel") {
                                RainbowColorWheelSwatch(
                                    isCustomSelected = isCustomBoxColor,
                                    activeColor = if (isCustomBoxColor) parseHexColor(currentBoxHex) else MaterialTheme.colorScheme.primary,
                                    isLocked = !uiState.isSubscriptionActive,
                                    onClick = {
                                        performPocketHaptic(view)
                                        if (!uiState.isSubscriptionActive) {
                                            viewModel.showUpgradeDialog("Custom box color picker is a Pocket Premium feature. Carbon, Slate, and Box Opacity are free.")
                                        } else {
                                            showBoxColorWheelDialog = true
                                        }
                                    }
                                )
                            }
                        }

                        val boxOpacityPct = (settings.boxOpacity.coerceIn(0.15f, 1f) * 100).roundToInt()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Box Opacity",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$boxOpacityPct%",
                                    style = TabularMoneySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PocketThinSlider(
                                value = settings.boxOpacity.coerceIn(0.15f, 1f),
                                onValueChange = { viewModel.updateBoxOpacity(it) },
                                valueRange = 0.15f..1f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_box_opacity_slider")
                            )
                        }
                    }
                }
            }

            // 4. Language
            item {
                val isBangla = settings.appLanguage.uppercase() == "BN"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Language",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .testTag("settings_language_toggle"),
                            shape = RoundedCornerShape(24.dp),
                            color = pocketColors.surfaceElevated,
                            border = BorderStroke(1.dp, pocketColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "EN" to "English",
                                    "BN" to "বাংলা"
                                ).forEach { (code, label) ->
                                    val selected = (code == "BN" && isBangla) || (code == "EN" && !isBangla)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                performPocketHaptic(view)
                                                viewModel.setAppLanguage(code)
                                            }
                                            .testTag("language_mode_$code"),
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Default wallet for new Entries & 6. Negative-balance warnings
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
                        Text("Default wallet for new Entries", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.activeWalletSummaries, key = { it.wallet.id }) { ws ->
                                FilterChip(
                                    selected = settings.defaultWalletId == ws.wallet.id,
                                    onClick = {
                                        performPocketHaptic(view)
                                        viewModel.updateSettings(settings.copy(defaultWalletId = ws.wallet.id))
                                    },
                                    label = { Text(ws.wallet.name) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Negative-balance warnings",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.warnOnNegativeBalance,
                                onCheckedChange = {
                                    performPocketHaptic(view)
                                    viewModel.updateSettings(settings.copy(warnOnNegativeBalance = it))
                                },
                                modifier = Modifier.testTag("settings_negative_balance_warning_switch")
                            )
                        }
                    }
                }
            }

            // v. Security
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Security", style = MaterialTheme.typography.titleMedium)
            }

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
                        // 1. App Lock (PIN)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "App Lock (PIN)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    performPocketHaptic(view)
                                    if (enabled) {
                                        enableBiometricsAfterPin = settings.biometricUnlockEnabled && uiState.isSubscriptionActive
                                        showPinDialog = true
                                    } else {
                                        viewModel.updateSettings(
                                            settings.copy(
                                                appLockEnabled = false,
                                                appLockPin = "",
                                                biometricUnlockEnabled = false
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("settings_pin_lock_switch")
                            )
                        }

                        HorizontalDivider()

                        // 2. Biometric unlock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Biometric unlock",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (!uiState.isSubscriptionActive) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Premium feature locked",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = settings.appLockEnabled && settings.biometricUnlockEnabled,
                                onCheckedChange = { enabled ->
                                    performPocketHaptic(view)
                                    if (enabled) {
                                        if (!uiState.isSubscriptionActive) {
                                            viewModel.showUpgradeDialog("Biometric security is a Pocket Premium feature. Upgrade to enable fingerprint and face unlock.")
                                        } else if (!settings.appLockEnabled || settings.appLockPin.isBlank()) {
                                            enableBiometricsAfterPin = true
                                            showPinDialog = true
                                        } else {
                                            viewModel.updateSettings(settings.copy(biometricUnlockEnabled = true))
                                            viewModel.postStatusMessage("Biometric Fingerprint / Face unlock enabled.")
                                        }
                                    } else {
                                        viewModel.updateSettings(settings.copy(biometricUnlockEnabled = false))
                                    }
                                },
                                modifier = Modifier.testTag("settings_biometric_unlock_switch")
                            )
                        }
                    }
                }
            }

            // vi. Notification Menu (keep as it is)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Notification Menu", style = MaterialTheme.typography.titleMedium)
            }

            item {
                val formattedReminderTime = remember(settings.dailyReminderHour, settings.dailyReminderMinute) {
                    com.example.notification.PocketNotificationManager.formatReminderTime(
                        settings.dailyReminderHour,
                        settings.dailyReminderMinute
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_notification_menu_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Loan Deadline Notification ON / OFF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Loan Deadline Notification",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Switch(
                                checked = settings.loanDeadlineNotificationEnabled,
                                onCheckedChange = { enabled ->
                                    performPocketHaptic(view)
                                    viewModel.updateNotificationSettings(
                                        context = context,
                                        loanDeadlineEnabled = enabled
                                    )
                                },
                                modifier = Modifier.testTag("settings_loan_notification_switch")
                            )
                        }

                        HorizontalDivider()

                        // 2. Daily Log Entry Reminder ON / OFF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventRepeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Daily Log Entry Reminder",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Switch(
                                checked = settings.dailyExpenseReminderEnabled,
                                onCheckedChange = { enabled ->
                                    performPocketHaptic(view)
                                    viewModel.updateNotificationSettings(
                                        context = context,
                                        dailyReminderEnabled = enabled
                                    )
                                },
                                modifier = Modifier.testTag("settings_daily_reminder_switch")
                            )
                        }

                        // 3. Time Selection Option for Daily Log Entry
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDailyReminderTimePicker = true }
                                .testTag("settings_daily_reminder_time_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, pocketColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Daily Log Entry Time",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = formattedReminderTime,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.syncAndTestNotifications(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_sync_notifications_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Loan Deadline Reminders")
                        }
                    }
                }
            }

            // vii. Only a button saying "Reset all Data to zero" (no prior text or box)
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { showResetDataConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("settings_reset_all_data_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset all Data to zero", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            // viii. Feedback & Bugs Report (Visible only to logged-in Google users, both Free and Paid)
            if (!uiState.googleAccountEmail.isNullOrBlank()) {
                item {
                    OutlinedButton(
                        onClick = {
                            performPocketHaptic(view)
                            showFeedbackDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("settings_feedback_bugs_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Feedback & Bugs Report",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Private Developer Admin Code Manager Button (Only visible to hadi1617180@gmail.com)
            if (uiState.isDeveloperAdmin) {
                item {
                    Button(
                        onClick = {
                            performPocketHaptic(view)
                            showDeveloperCodeManagerDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("settings_admin_code_manager_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin: Manage Activation Codes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ix. App developed by Abdullahil Hadi
            item {
                OutlinedButton(
                    onClick = {
                        performPocketHaptic(view)
                        showDeveloperInfoDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("settings_developer_info_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App developed by Abdullahil Hadi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // x. Plain acknowledgement text at the bottom-most position (not a button, not in a box, 4 centered lines)
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("settings_acknowledgement_text"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Acknowledgement",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Special Thanks to my beloved wife",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "💖 Mahadia Afruj 💖",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "for inspiring core architecture, her love and endless support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    if (anyDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
    }

    if (showFeedbackDialog) {
        val feedbackScope = rememberCoroutineScope()
        var feedbackSubject by remember { mutableStateOf("") }
        var feedbackDetails by remember { mutableStateOf("") }
        var feedbackError by remember { mutableStateOf<String?>(null) }
        var isSubmittingFeedback by remember { mutableStateOf(false) }
        var showAdminInboxTab by remember { mutableStateOf(false) }
        var adminReports by remember { mutableStateOf<List<CloudFeedbackReportRecord>>(emptyList()) }
        var isLoadingAdminReports by remember { mutableStateOf(false) }

        LaunchedEffect(showAdminInboxTab) {
            if (showAdminInboxTab && uiState.isDeveloperAdmin) {
                isLoadingAdminReports = true
                viewModel.fetchAdminFeedbackReports().onSuccess { adminReports = it }
                isLoadingAdminReports = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingFeedback) showFeedbackDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Feedback & Bugs Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.isDeveloperAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !showAdminInboxTab,
                                onClick = { showAdminInboxTab = false },
                                label = { Text("Send Feedback", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = showAdminInboxTab,
                                onClick = { showAdminInboxTab = true },
                                label = { Text("Admin Inbox", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (showAdminInboxTab && uiState.isDeveloperAdmin) {
                        if (isLoadingAdminReports) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else if (adminReports.isEmpty()) {
                            Text(
                                text = "No feedback reports in cloud inbox yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(adminReports, key = { it.id }) { rep ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = pocketColors.surfaceElevated,
                                        border = BorderStroke(1.dp, pocketColors.cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = rep.subject.ifBlank { "Feedback" },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        feedbackScope.launch {
                                                            viewModel.deleteAdminFeedbackReport(rep.id)
                                                            adminReports = adminReports.filterNot { it.id == rep.id }
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete report",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "From: ${rep.userName.ifBlank { "User" }} (${rep.userEmail}) • ${rep.platform}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = rep.details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = pocketColors.surfaceElevated,
                            border = BorderStroke(1.dp, pocketColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "From: ${uiState.googleAccountEmail.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = feedbackSubject,
                            onValueChange = {
                                feedbackSubject = it
                                feedbackError = null
                            },
                            label = { Text("Subject") },
                            singleLine = true,
                            enabled = !isSubmittingFeedback,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feedback_subject_input")
                        )
                        OutlinedTextField(
                            value = feedbackDetails,
                            onValueChange = {
                                feedbackDetails = it
                                feedbackError = null
                            },
                            label = { Text("Details") },
                            minLines = 4,
                            maxLines = 8,
                            enabled = !isSubmittingFeedback,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .testTag("feedback_details_input")
                        )
                        feedbackError?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!showAdminInboxTab) {
                    Button(
                        enabled = !isSubmittingFeedback,
                        onClick = {
                            val subjectTrimmed = feedbackSubject.trim()
                            val detailsTrimmed = feedbackDetails.trim()
                            if (subjectTrimmed.isBlank() && detailsTrimmed.isBlank()) {
                                feedbackError = "Please enter a subject or details."
                                return@Button
                            }
                            val finalSubject = subjectTrimmed.ifBlank { "Pocket App Feedback / Bug Report" }
                            isSubmittingFeedback = true
                            feedbackError = null
                            feedbackScope.launch {
                                val res = viewModel.submitFeedbackReport(finalSubject, detailsTrimmed)
                                isSubmittingFeedback = false
                                res.onSuccess {
                                    showFeedbackDialog = false
                                    viewModel.postStatusMessage("Thank you! Your feedback has been sent.")
                                }.onFailure { err ->
                                    feedbackError = err.localizedMessage ?: "Could not send feedback. Please check your connection."
                                }
                            }
                        },
                        modifier = Modifier.testTag("feedback_submit_button")
                    ) {
                        if (isSubmittingFeedback) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingFeedback,
                    onClick = { showFeedbackDialog = false },
                    modifier = Modifier.testTag("feedback_cancel_button")
                ) {
                    Text(if (showAdminInboxTab) "Close" else "Cancel")
                }
            }
        )
    }

    if (showDeveloperCodeManagerDialog && uiState.isDeveloperAdmin) {
        DeveloperCodeManagerDialog(
            viewModel = viewModel,
            onDismiss = { showDeveloperCodeManagerDialog = false }
        )
    }

    if (showDeveloperInfoDialog) {
        DeveloperInfoDialog(
            onDismiss = { showDeveloperInfoDialog = false }
        )
    }

    if (showAccentColorWheelDialog) {
        AccentColorWheelDialog(
            initialColorHex = settings.accentColorHex.ifBlank { "#F15A24" },
            onDismiss = { showAccentColorWheelDialog = false },
            onColorSelected = { chosenHex ->
                performPocketHaptic(view)
                viewModel.updateSettings(settings.copy(accentColorHex = chosenHex))
                showAccentColorWheelDialog = false
            },
            dialogTitle = "Choose Custom Accent Color",
            selectedLabel = "Selected Accent"
        )
    }

    if (showBoxColorWheelDialog) {
        AccentColorWheelDialog(
            initialColorHex = settings.boxColorHex.ifBlank { "#1E293B" },
            onDismiss = { showBoxColorWheelDialog = false },
            onColorSelected = { chosenHex ->
                performPocketHaptic(view)
                viewModel.updateBoxColor(chosenHex)
                showBoxColorWheelDialog = false
            },
            dialogTitle = "Choose Custom Box Color",
            selectedLabel = "Selected Box Color"
        )
    }

    // TimePicker Dialog for Daily Log Entry Reminder
    if (showDailyReminderTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.dailyReminderHour.coerceIn(0, 23),
            initialMinute = settings.dailyReminderMinute.coerceIn(0, 59),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showDailyReminderTimePicker = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Select Daily Log Entry Time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNotificationSettings(
                            context = context,
                            dailyReminderEnabled = true,
                            dailyHour = timePickerState.hour,
                            dailyMinute = timePickerState.minute
                        )
                        showDailyReminderTimePicker = false
                    },
                    modifier = Modifier.testTag("settings_confirm_reminder_time_button")
                ) {
                    Text("Save Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDailyReminderTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Resetting All Financial Data to Zero
    if (showResetDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetDataConfirmDialog = false },
            containerColor = pocketColors.overlaySurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Reset All Data to Zero?") },
            text = {
                Text(
                    "Are you sure you want to reset all financial data? All income, expenses, loans, transfers, balance adjustments, and wallet balances will be set to 0. Your wallets and categories will be kept."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDataConfirmDialog = false
                        viewModel.resetAllNumericalDataToZero(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("settings_confirm_reset_data_button")
                ) {
                    Text("Yes, Reset to Zero", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDataConfirmDialog = false },
                    modifier = Modifier.testTag("settings_cancel_reset_data_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPinDialog) {
        var pinText by remember { mutableStateOf("") }
        var pairBiometrics by remember(enableBiometricsAfterPin, uiState.isSubscriptionActive) {
            mutableStateOf(
                uiState.isSubscriptionActive &&
                    (enableBiometricsAfterPin || BiometricAuthHelper.canAuthenticate(context))
            )
        }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = { Text("Set Device-Local App PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = com.example.data.PocketLocalizer.normalizeToAsciiDigits(it).filter { ch -> ch in '0'..'9' }.take(6) },
                        label = { Text("4-6 digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_pin_dialog_input")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (!uiState.isSubscriptionActive) {
                                    viewModel.showUpgradeDialog("Biometric fingerprint/face unlock is a Pocket Premium feature. Local App PIN is free.")
                                } else {
                                    pairBiometrics = !pairBiometrics
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = pairBiometrics,
                            onCheckedChange = { checked ->
                                if (!uiState.isSubscriptionActive) {
                                    viewModel.showUpgradeDialog("Biometric fingerprint/face unlock is a Pocket Premium feature. Local App PIN is free.")
                                } else {
                                    pairBiometrics = checked
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Also enable Fingerprint / Face unlock",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (!uiState.isSubscriptionActive) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Premium feature locked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinText.length >= 4) {
                            viewModel.updateSettings(
                                settings.copy(
                                    appLockEnabled = true,
                                    appLockPin = pinText,
                                    biometricUnlockEnabled = pairBiometrics && uiState.isSubscriptionActive
                                )
                            )
                            showPinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("settings_confirm_pin_button")
                ) {
                    Text("Enable Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingCropBitmap?.let { srcBmp ->
        ProfilePhotoCropDialog(
            sourceBitmap = srcBmp,
            onDismiss = { pendingCropBitmap = null },
            onCropConfirmed = { croppedBmp ->
                scope.launch {
                    val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveBitmapToInternalStorage(
                            context = context,
                            bitmap = croppedBmp,
                            subDir = "profile_photos",
                            prefix = "profile"
                        )
                    }
                    if (savedPath != null) {
                        viewModel.applyProfilePhotoPath(savedPath)
                    }
                    pendingCropBitmap = null
                }
            }
        )
    }

    pendingWallpaperCropBitmap?.let { srcBmp ->
        WallpaperCropDialog(
            sourceBitmap = srcBmp,
            onDismiss = { pendingWallpaperCropBitmap = null },
            onCropConfirmed = { croppedBmp ->
                scope.launch {
                    val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveBitmapToInternalStorage(
                            context = context,
                            bitmap = croppedBmp,
                            subDir = "wallpapers",
                            prefix = "wallpaper",
                            compressAsJpeg = true
                        )
                    }
                    if (savedPath != null) {
                        viewModel.applyCustomWallpaperPath(savedPath)
                    }
                    pendingWallpaperCropBitmap = null
                }
            }
        )
    }
}

@Composable
private fun SettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WidgetConfigScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    val symbol = uiState.settings.currencySymbol
    val activeWallets = uiState.activeWalletSummaries

    var selectedOverviewIds by remember(activeWallets) {
        val saved: Set<Long> = com.example.widget.WidgetPreferences.getOverviewWalletIds(context)
        val initial: Set<Long> = if (saved.isNotEmpty()) {
            saved.filter { id -> activeWallets.any { it.wallet.id == id } }.toSet()
        } else {
            activeWallets.take(4).map { it.wallet.id }.toSet()
        }
        mutableStateOf(initial.ifEmpty { activeWallets.take(4).map { it.wallet.id }.toSet() })
    }

    var selectedSingleWalletId by remember(activeWallets) {
        val saved: Long? = com.example.widget.WidgetPreferences.getSingleWalletId(context)
        val initial: Long = if (saved != null && activeWallets.any { it.wallet.id == saved }) {
            saved
        } else {
            activeWallets.firstOrNull()?.wallet?.id ?: 0L
        }
        mutableStateOf(initial)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = "Home-Screen Widgets",
            onBack = { viewModel.navigateBack() },
            actions = {
                Button(
                    onClick = {
                        com.example.widget.WidgetPreferences.setDefaultOverviewWalletIds(
                            context,
                            selectedOverviewIds
                        )
                        if (selectedSingleWalletId != 0L) {
                            com.example.widget.WidgetPreferences.setDefaultSingleWalletId(
                                context,
                                selectedSingleWalletId
                            )
                        }
                        // Also apply to any currently placed widget instances
                        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                        val overviewIds = appWidgetManager.getAppWidgetIds(
                            android.content.ComponentName(context, com.example.widget.WalletOverviewWidgetProvider::class.java)
                        )
                        overviewIds.forEach { id ->
                            com.example.widget.WidgetPreferences.saveOverviewWalletIds(
                                context,
                                id,
                                selectedOverviewIds
                            )
                        }
                        val singleIds = appWidgetManager.getAppWidgetIds(
                            android.content.ComponentName(context, com.example.widget.SingleWalletWidgetProvider::class.java)
                        )
                        if (selectedSingleWalletId != 0L && singleIds.size == 1) {
                            com.example.widget.WidgetPreferences.saveSingleWalletId(
                                context,
                                singleIds.first(),
                                selectedSingleWalletId
                            )
                        }
                        com.example.widget.PocketWidgetUpdater.refreshAllWidgets(context)
                        viewModel.postStatusMessage("Home-screen widget configuration saved & synced.")
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("save_widget_config_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Sync")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Widget 1 preview: Total Balance
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Widget 1 · Total Balance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, pocketColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Total Available Balance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = AccountingEngine.formatMoney(uiState.totalAvailableBalance, symbol),
                                    style = TabularMoneyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("widget_config_total_balance_preview")
                                )
                            }
                        }
                    }
                }
            }

            // Widget 2 config: Wallet Overview
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
                            text = "Widget 2 · Wallet Overview (Select up to 4 wallets)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        activeWallets.forEach { ws ->
                            val isChecked = selectedOverviewIds.contains(ws.wallet.id)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedOverviewIds = if (isChecked) {
                                            if (selectedOverviewIds.size > 1) selectedOverviewIds - ws.wallet.id else selectedOverviewIds
                                        } else {
                                            if (selectedOverviewIds.size < 4) selectedOverviewIds + ws.wallet.id else selectedOverviewIds
                                        }
                                    }
                                    .testTag("widget_overview_toggle_${ws.wallet.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isChecked) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconBadgeCircle(
                                        iconName = ws.wallet.iconName,
                                        colorHex = ws.wallet.colorHex,
                                        size = 34.dp,
                                        iconSize = 18.dp,
                                        walletNameHint = ws.wallet.name
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = ws.wallet.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val wsSymbol = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                    Text(
                                        text = AccountingEngine.formatMoney(ws.currentBalance, wsSymbol),
                                        style = TabularMoneySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Widget 3 config: Single Wallet
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
                            text = "Widget 3 · Individual Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        activeWallets.forEach { ws ->
                            val isSelected = selectedSingleWalletId == ws.wallet.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedSingleWalletId = ws.wallet.id }
                                    .testTag("widget_single_select_${ws.wallet.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconBadgeCircle(
                                        iconName = ws.wallet.iconName,
                                        colorHex = ws.wallet.colorHex,
                                        size = 34.dp,
                                        iconSize = 18.dp,
                                        walletNameHint = ws.wallet.name
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = ws.wallet.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val wsSymbol = AccountingEngine.currencySymbolForCode(ws.wallet.currency)
                                    Text(
                                        text = AccountingEngine.formatMoney(ws.currentBalance, wsSymbol),
                                        style = TabularMoneySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pocketColors = LocalPocketColors.current

    var jsonEditorText by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf<BackupValidationResult?>(null) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.generateBackupJson(null)
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.postStatusMessage("Backup saved to file.")
                }
            }
        }
    }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                jsonEditorText = content
                validationResult = BackupManager.validateAndParseBackup(content, null)
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = BackupManager.exportLedgerToCsv(uiState.unifiedLedger)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                viewModel.postStatusMessage("Ledger CSV exported.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = "Backup, Restore & CSV",
            onBack = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Export Complete Backup (pocket-backup-v1.json)
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
                        Text("Export Complete Backup", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    exportJsonLauncher.launch("pocket-backup-v1.json")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_backup_file_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save JSON")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        jsonEditorText = viewModel.generateBackupJson(null)
                                        viewModel.postStatusMessage("Generated backup JSON preview.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("generate_backup_preview_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Preview JSON")
                            }
                        }
                    }
                }
            }

            // Section 2: Import & Restore Backup
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
                        Text("Import & Restore Backup", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("select_backup_file_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Select Backup File")
                            }
                            Button(
                                onClick = {
                                    validationResult = BackupManager.validateAndParseBackup(jsonEditorText, null)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("validate_backup_json_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Validate JSON")
                            }
                        }

                        OutlinedTextField(
                            value = jsonEditorText,
                            onValueChange = {
                                jsonEditorText = it
                                validationResult = null
                            },
                            label = { Text("Backup JSON Content") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_json_editor_input")
                        )

                        validationResult?.let { res ->
                            if (!res.isValid) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.expenseContainer
                                ) {
                                    Text(
                                        text = res.errorMessage ?: "Invalid backup file.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = pocketColors.expenseText,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else if (res.payload != null) {
                                val p = res.payload
                                val exportedDate = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                                    .format(Date(p.exportedAtMillis))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pocketColors.incomeContainer
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Valid Backup Verified ($exportedDate)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = pocketColors.incomeText
                                        )
                                        Text(
                                            text = "• ${p.wallets.size} wallets · ${p.categories.size} categories\n• ${p.transactions.size} transactions · ${p.transfers.size} transfers\n• ${p.loans.size} loans · ${p.adjustments.size} adjustments",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.restoreFromValidatedBackup(p) {
                                                        validationResult = null
                                                    }
                                                },
                                                modifier = Modifier.testTag("confirm_replace_existing_data_button")
                                            ) {
                                                Text("Replace existing data")
                                            }
                                            TextButton(onClick = { validationResult = null }) {
                                                Text("Cancel")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Export CSV for Spreadsheets
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Export CSV for Spreadsheets", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { exportCsvLauncher.launch("pocket-transactions.csv") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_all_csv_button")
                            ) {
                                Text("Export CSV File")
                            }
                            OutlinedButton(
                                onClick = {
                                    val csv = BackupManager.exportLedgerToCsv(uiState.unifiedLedger)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Pocket Transactions CSV")
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share CSV"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Share CSV")
                            }
                        }
                        CsvBankImportButtonAndDialog(
                            wallets = uiState.wallets,
                            categories = uiState.categories,
                            defaultWalletId = uiState.settings.defaultWalletId,
                            isFullWidthButton = true,
                            buttonLabel = "Import Bank / MFS CSV Statement",
                            onImportTransactions = { importedList ->
                                viewModel.importCsvTransactions(
                                    importedTransactions = importedList,
                                    navigateBackOnSave = false
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringPayConfirmDialog(
    recurring: RecurringTransactionEntity,
    uiState: PocketUiState,
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val symbol = uiState.settings.currencySymbol
    val isFixed = recurring.amountMode == RecurringAmountMode.FIXED.name
    val isIncome = recurring.type == TransactionType.INCOME.name

    // Fixed Subscription pre-fills the saved amount; Manual Input starts clean so user types this cycle's bill
    var amountText by remember(recurring.id, recurring.amountMode, recurring.amount) {
        mutableStateOf(
            if (isFixed && recurring.amount > 0.0) {
                formatNumericFieldInitialValue(recurring.amount)
            } else {
                ""
            }
        )
    }

    val activeWallets = uiState.activeWalletSummaries
    var selectedWalletId by remember(recurring.id, recurring.defaultWalletId, activeWallets) {
        mutableStateOf(
            recurring.defaultWalletId?.takeIf { id -> activeWallets.any { it.wallet.id == id } }
                ?: uiState.settings.defaultWalletId?.takeIf { id -> activeWallets.any { it.wallet.id == id } }
                ?: activeWallets.firstOrNull()?.wallet?.id
                ?: 0L
        )
    }
    var noteText by remember(recurring.id) { mutableStateOf(recurring.note) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedWalletSummary = activeWallets.firstOrNull { it.wallet.id == selectedWalletId }
    val selectedWalletCurrency = selectedWalletSummary?.wallet?.currency?.ifBlank { "BDT" }?.uppercase() ?: "BDT"
    val walletSymbol = AccountingEngine.currencySymbolForCode(selectedWalletCurrency)
    val isForeignWallet = !selectedWalletCurrency.equals("BDT", ignoreCase = true)

    var conversionMode by remember(recurring.id, selectedWalletCurrency) {
        mutableStateOf("NONE") // "NONE", "RATE", "FIXED_CONVERTED"
    }
    var conversionRateText by remember(recurring.id) { mutableStateOf("") }
    var fixedConvertedText by remember(recurring.id) { mutableStateOf("") }
    var serviceFeeMode by remember(recurring.id) { mutableStateOf("NONE") } // "NONE", "PERCENTAGE", "FIXED"
    var serviceFeeInputText by remember(recurring.id) { mutableStateOf("") }

    val view = LocalView.current
    val rawInputAmount = remember(amountText) {
        evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
    }
    val effectivePrincipal = remember(rawInputAmount, conversionMode, conversionRateText, fixedConvertedText) {
        when (conversionMode) {
            "RATE" -> {
                val r = conversionRateText.toDoubleOrNull() ?: 1.0
                if (r > 0.0) AccountingEngine.roundCurrency(rawInputAmount * r) else rawInputAmount
            }
            "FIXED_CONVERTED" -> {
                val f = fixedConvertedText.toDoubleOrNull() ?: 0.0
                if (f > 0.0) AccountingEngine.roundCurrency(f) else rawInputAmount
            }
            else -> rawInputAmount
        }
    }
    val effectiveServiceFee = remember(effectivePrincipal, serviceFeeMode, serviceFeeInputText) {
        val feeVal = serviceFeeInputText.toDoubleOrNull() ?: 0.0
        when (serviceFeeMode) {
            "PERCENTAGE" -> if (feeVal > 0.0) AccountingEngine.roundCurrency(effectivePrincipal * (feeVal / 100.0)) else 0.0
            "FIXED" -> if (feeVal > 0.0) AccountingEngine.roundCurrency(feeVal) else 0.0
            else -> 0.0
        }
    }
    val totalWalletDebit = effectivePrincipal + effectiveServiceFee
    val insufficientBalance = !isIncome &&
        selectedWalletSummary != null &&
        totalWalletDebit > 0.0 &&
        (selectedWalletSummary.currentBalance - totalWalletDebit) < -0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text(
                text = if (isIncome) "Receive: ${recurring.title}" else "Pay Bill: ${recurring.title}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MathAmountTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = sanitizeMathExpressionInput(it)
                        errorMsg = null
                    },
                    label = {
                        Text(
                            if (conversionMode != "NONE") {
                                "Bill Amount Before Conversion"
                            } else if (isFixed) {
                                "Subscription Amount ($walletSymbol $selectedWalletCurrency)"
                            } else {
                                "Enter Bill Amount ($walletSymbol $selectedWalletCurrency)"
                            }
                        )
                    },
                    placeholder = {
                        if (!isFixed && recurring.amount > 0.0) {
                            Text("Last paid: ${formatNumericFieldInitialValue(recurring.amount)}")
                        } else {
                            Text("0")
                        }
                    },
                    textStyle = TabularMoneyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearZeroOnFocus(amountText) { amountText = it }
                        .testTag("recurring_pay_amount_input")
                )
                MiniCalculatorBar(
                    expression = amountText,
                    currencySymbol = walletSymbol,
                    onExpressionChange = {
                        amountText = it
                        errorMsg = null
                    }
                )

                Text(
                    text = if (isIncome) "Select Destination Wallet" else "Which Wallet to Pay From?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeWallets, key = { it.wallet.id }) { summary ->
                        val isSelected = summary.wallet.id == selectedWalletId
                        val chipSym = AccountingEngine.currencySymbolForCode(summary.wallet.currency)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedWalletId = summary.wallet.id
                                errorMsg = null
                            },
                            label = {
                                Text(
                                    "${summary.wallet.name} (${AccountingEngine.formatMoney(summary.currentBalance, chipSym, includeDecimals = false)})"
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("recurring_pay_wallet_chip_${summary.wallet.name}")
                        )
                    }
                }

                // Conversion Rate / Fixed Converted Amount & Service Charge for foreign or cross-currency payments
                if (isForeignWallet || conversionMode != "NONE" || serviceFeeMode != "NONE") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Currency Conversion ($selectedWalletCurrency)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = conversionMode == "NONE",
                                    onClick = { conversionMode = "NONE" },
                                    label = { Text("Direct") }
                                )
                                FilterChip(
                                    selected = conversionMode == "RATE",
                                    onClick = { conversionMode = "RATE" },
                                    label = { Text("Rate") }
                                )
                                FilterChip(
                                    selected = conversionMode == "FIXED_CONVERTED",
                                    onClick = { conversionMode = "FIXED_CONVERTED" },
                                    label = { Text("Fixed ($walletSymbol)") }
                                )
                            }
                            if (conversionMode == "RATE") {
                                OutlinedTextField(
                                    value = conversionRateText,
                                    onValueChange = { conversionRateText = sanitizeNumericInput(it) },
                                    label = { Text("Conversion Rate Multiplier") },
                                    placeholder = { Text("0.0082") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (conversionMode == "FIXED_CONVERTED") {
                                OutlinedTextField(
                                    value = fixedConvertedText,
                                    onValueChange = { fixedConvertedText = sanitizeNumericInput(it) },
                                    label = { Text("Fixed Converted Amount ($walletSymbol $selectedWalletCurrency)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text(
                                text = "Service Charge ($walletSymbol $selectedWalletCurrency)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = serviceFeeMode == "NONE",
                                    onClick = { serviceFeeMode = "NONE" },
                                    label = { Text("None") }
                                )
                                FilterChip(
                                    selected = serviceFeeMode == "PERCENTAGE",
                                    onClick = { serviceFeeMode = "PERCENTAGE" },
                                    label = { Text("%") }
                                )
                                FilterChip(
                                    selected = serviceFeeMode == "FIXED",
                                    onClick = { serviceFeeMode = "FIXED" },
                                    label = { Text("Fixed") }
                                )
                            }
                            if (serviceFeeMode != "NONE") {
                                OutlinedTextField(
                                    value = serviceFeeInputText,
                                    onValueChange = { serviceFeeInputText = sanitizeNumericInput(it) },
                                    label = {
                                        Text(
                                            if (serviceFeeMode == "PERCENTAGE") "Service Charge (%)"
                                            else "Fixed Service Charge ($walletSymbol)"
                                        )
                                    },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (insufficientBalance) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pocketColors.expenseContainer,
                        border = BorderStroke(1.dp, pocketColors.expenseText.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = pocketColors.expenseText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedWalletSummary?.wallet?.name} has ${AccountingEngine.formatMoney(selectedWalletSummary?.currentBalance ?: 0.0, walletSymbol)}. You can pick another wallet above (e.g., MTB or Bank) or proceed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = pocketColors.expenseText
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = capitalizeFirstLetter(it) },
                    label = { Text("Note (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recurring_pay_note_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = effectivePrincipal
                    if (amt <= 0.0) {
                        errorMsg = "Please enter an amount greater than 0."
                    } else if (selectedWalletId == 0L) {
                        errorMsg = "Please pick a wallet."
                    } else {
                        performPocketHaptic(view)
                        viewModel.confirmRecurringPayment(
                            recurringId = recurring.id,
                            chosenWalletId = selectedWalletId,
                            finalAmount = amt,
                            note = noteText,
                            serviceFeeAmount = effectiveServiceFee
                        )
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_recurring_pay_button")
            ) {
                Text(if (isIncome) "Confirm & Log Income" else "Confirm & Pay")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.snoozeRecurringToTomorrow(recurring.id) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dialog_recurring_remind_tomorrow_button")
                ) {
                    Text("Remind Tomorrow")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    val pocketColors = LocalPocketColors.current
    val symbol = uiState.settings.currencySymbol
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingRecurring by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    val upcoming7DaysItems = remember(uiState.recurringTransactions) {
        val horizon = System.currentTimeMillis() + 7L * 24L * 3600L * 1000L
        uiState.recurringTransactions
            .filter { it.isActive && it.type != TransactionType.INCOME.name && it.nextDueDate <= horizon }
    }
    val upcoming7DaysTotal = remember(upcoming7DaysItems, uiState.wallets) {
        val walletMap = uiState.wallets.associateBy { it.id }
        val primaryItems = upcoming7DaysItems.filter {
            val wCurr = it.defaultWalletId?.let { id -> walletMap[id]?.currency } ?: "BDT"
            wCurr.equals("BDT", ignoreCase = true)
        }
        (if (primaryItems.isNotEmpty()) primaryItems else upcoming7DaysItems).sumOf { it.amount }
    }
    val upcoming7DaysSymbol = remember(upcoming7DaysItems, uiState.wallets, symbol) {
        val walletMap = uiState.wallets.associateBy { it.id }
        val distinctCurr = upcoming7DaysItems.map {
            it.defaultWalletId?.let { id -> walletMap[id]?.currency }?.takeIf { c -> c.isNotBlank() } ?: "BDT"
        }.distinct()
        if (distinctCurr.size == 1) AccountingEngine.currencySymbolForCode(distinctCurr.first()) else symbol
    }
    val upcoming7DaysCount = remember(uiState.recurringTransactions) {
        val horizon = System.currentTimeMillis() + 7L * 24L * 3600L * 1000L
        uiState.recurringTransactions.count { it.isActive && it.nextDueDate <= horizon }
    }
    val isAnyRecurringDialogOpen = showAddOrEditDialog || uiState.pendingRecurringPayId != null

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(if (isAnyRecurringDialogOpen) 14.dp else 0.dp)
            .background(Color.Transparent)
    ) {
        PocketTopBar(
            title = "Recurring Bills & Income",
            onBack = { viewModel.navigateBack() },
            actions = {
                val canAddRec = SubscriptionManager.canAddRecurringBill(
                    uiState.recurringTransactions.size,
                    uiState.settings.subscriptionTier,
                    uiState.settings.subscriptionExpiresAt
                )
                TextButton(
                    onClick = {
                        if (!canAddRec) {
                            viewModel.showUpgradeDialog("Free tier allows up to 2 recurring bills. Upgrade to Pocket Premium for unlimited recurring bills and subscriptions.")
                        } else {
                            editingRecurring = null
                            showAddOrEditDialog = true
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_recurring_button")
                ) {
                    Icon(if (canAddRec) Icons.Default.Add else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.recurringTransactions.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recurring_7day_forecast_card"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upcoming,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Due in next 7 days ($upcoming7DaysCount)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (uiState.settings.hideBalancesPrivacy) {
                                    "$upcoming7DaysSymbol ••••"
                                } else {
                                    AccountingEngine.formatMoney(upcoming7DaysTotal, upcoming7DaysSymbol, includeDecimals = false)
                                },
                                style = TabularMoneyMedium,
                                fontWeight = FontWeight.Bold,
                                color = pocketColors.expenseText
                            )
                        }
                    }
                }
            }

            if (uiState.recurringTransactions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No Recurring Bills or Subscriptions Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = {
                                    editingRecurring = null
                                    showAddOrEditDialog = true
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("empty_state_add_recurring_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Recurring Transaction")
                            }
                        }
                    }
                }
            } else {
                items(uiState.recurringTransactions, key = { it.id }) { item ->
                    val isFixed = item.amountMode == RecurringAmountMode.FIXED.name
                    val isIncome = item.type == TransactionType.INCOME.name
                    val category = uiState.categories.firstOrNull { it.id == item.categoryId }
                    val defaultWallet = uiState.wallets.firstOrNull { it.id == item.defaultWalletId }
                    val isDueNow = PocketNotificationManager.shouldShowRecurringNotification(item)
                    val freqLabel = when (item.frequency) {
                        RecurringFrequency.CUSTOM_DAYS.name -> "Every ${item.customIntervalDays} days"
                        else -> item.frequency.lowercase().replaceFirstChar { it.uppercase() }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recurring_item_card_${item.title}"),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isDueNow) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconBadgeCircle(
                                        iconName = category?.iconName ?: "BOLT",
                                        colorHex = category?.colorHex ?: "#F87171",
                                        size = 42.dp,
                                        iconSize = 20.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = buildString {
                                                append(if (isFixed) "Fixed Subscription" else "Manual Input")
                                                append(" • ")
                                                append(freqLabel)
                                                if (defaultWallet != null) {
                                                    append(" • Default: ${defaultWallet.name}")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val itemSymbol = defaultWallet?.currency?.takeIf { it.isNotBlank() }
                                        ?.let { AccountingEngine.currencySymbolForCode(it) }
                                        ?: symbol
                                    Text(
                                        text = if (isFixed && item.amount > 0.0) {
                                            AccountingEngine.formatMoney(item.amount, itemSymbol)
                                        } else {
                                            "Manual"
                                        },
                                        style = TabularMoneyMedium,
                                        color = if (isIncome) pocketColors.incomeText else pocketColors.expenseText
                                    )
                                    Text(
                                        text = if (!item.isActive) {
                                            "Paused"
                                        } else if (isDueNow) {
                                            "Due Now (${dateFormatter.format(Date(item.nextDueDate))})"
                                        } else {
                                            "Next: ${dateFormatter.format(Date(item.nextDueDate))}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDueNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.openRecurringPayDialog(item.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("screen_recurring_pay_${item.title}"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (isIncome) "Receive (Pick Wallet)" else "Pay (Pick Wallet)")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.snoozeRecurringToTomorrow(item.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("screen_recurring_snooze_${item.title}"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Remind Tomorrow")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.toggleRecurringActive(item) },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text(if (item.isActive) "Pause" else "Resume")
                                }
                                TextButton(
                                    onClick = {
                                        editingRecurring = item
                                        showAddOrEditDialog = true
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text("Edit")
                                }
                                TextButton(
                                    onClick = { viewModel.deleteRecurringTransaction(item.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (isAnyRecurringDialogOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
    }

    if (showAddOrEditDialog) {
        val existing = editingRecurring
        var titleText by remember(existing) { mutableStateOf(existing?.title ?: "") }
        var selectedType by remember(existing) {
            mutableStateOf(
                if (existing?.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE
            )
        }
        var selectedMode by remember(existing) {
            mutableStateOf(
                if (existing?.amountMode == RecurringAmountMode.MANUAL.name) {
                    RecurringAmountMode.MANUAL
                } else {
                    RecurringAmountMode.FIXED
                }
            )
        }
        var amountText by remember(existing) {
            mutableStateOf(formatNumericFieldInitialValue(existing?.amount))
        }
        val filteredCats = remember(uiState.categories, selectedType) {
            val t = if (selectedType == TransactionType.INCOME) "INCOME" else "EXPENSE"
            uiState.categories.filter { it.type == t && !it.isArchived }
        }
        var selectedCategoryId by remember(existing, filteredCats) {
            mutableStateOf(
                existing?.categoryId?.takeIf { id -> filteredCats.any { it.id == id } }
                    ?: filteredCats.firstOrNull()?.id
                    ?: 0L
            )
        }
        var defaultWalletId by remember(existing, uiState.activeWalletSummaries) {
            mutableStateOf(
                existing?.defaultWalletId
                    ?: uiState.settings.defaultWalletId
                    ?: uiState.activeWalletSummaries.firstOrNull()?.wallet?.id
                    ?: 0L
            )
        }
        var selectedFrequency by remember(existing) {
            mutableStateOf(
                runCatching { RecurringFrequency.valueOf(existing?.frequency ?: "MONTHLY") }
                    .getOrDefault(RecurringFrequency.MONTHLY)
            )
        }
        var dayOfMonthText by remember(existing) {
            val defaultDay = existing?.dayOfMonth ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            mutableStateOf(defaultDay.toString())
        }
        var customIntervalDaysText by remember(existing) {
            val defaultDays = existing?.customIntervalDays ?: 30
            mutableStateOf(defaultDays.toString())
        }
        var dueTodayForFirstNotification by remember(existing) {
            mutableStateOf(existing == null)
        }
        var noteText by remember(existing) { mutableStateOf(existing?.note ?: "") }
        var formError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddOrEditDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Text(if (existing == null) "New Recurring Schedule" else "Edit Recurring Schedule")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == TransactionType.EXPENSE,
                            onClick = { selectedType = TransactionType.EXPENSE },
                            label = { Text("Expense / Bill") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == TransactionType.INCOME,
                            onClick = { selectedType = TransactionType.INCOME },
                            label = { Text("Income") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = titleText,
                        onValueChange = {
                            titleText = capitalizeFirstLetter(it)
                            formError = null
                        },
                        label = { Text("Title (e.g., Netflix, WiFi, DESCO Bill)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recurring_dialog_title_input")
                    )

                    Text(
                        text = "Amount Mode for Next Payment",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMode == RecurringAmountMode.FIXED,
                            onClick = { selectedMode = RecurringAmountMode.FIXED },
                            label = { Text("Fixed Subscription") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recurring_dialog_mode_fixed")
                        )
                        FilterChip(
                            selected = selectedMode == RecurringAmountMode.MANUAL,
                            onClick = { selectedMode = RecurringAmountMode.MANUAL },
                            label = { Text("Manual Input") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recurring_dialog_mode_manual")
                        )
                    }

                    val dialogWalletSymbol = remember(defaultWalletId, uiState.activeWalletSummaries, symbol) {
                        uiState.activeWalletSummaries.firstOrNull { it.wallet.id == defaultWalletId }
                            ?.wallet?.currency?.takeIf { it.isNotBlank() }
                            ?.let { AccountingEngine.currencySymbolForCode(it) }
                            ?: symbol
                    }
                    MathAmountTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = sanitizeMathExpressionInput(it)
                            formError = null
                        },
                        label = {
                            Text(
                                if (selectedMode == RecurringAmountMode.FIXED) {
                                    "Fixed Amount ($dialogWalletSymbol) · e.g. 500+150"
                                } else {
                                    "Estimated / Reference Amount ($dialogWalletSymbol, Optional)"
                                }
                            )
                        },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearZeroOnFocus(amountText) { amountText = it }
                            .testTag("recurring_dialog_amount_input")
                    )
                    MiniCalculatorBar(
                        expression = amountText,
                        currencySymbol = dialogWalletSymbol,
                        onExpressionChange = {
                            amountText = it
                            formError = null
                        }
                    )

                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredCats, key = { it.id }) { cat ->
                            FilterChip(
                                selected = cat.id == selectedCategoryId,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }

                    Text("Preferred Default Wallet (Can change when paying)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.activeWalletSummaries, key = { it.wallet.id }) { ws ->
                            FilterChip(
                                selected = ws.wallet.id == defaultWalletId,
                                onClick = { defaultWalletId = ws.wallet.id },
                                label = { Text(ws.wallet.name) }
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RecurringFrequency.entries) { freq ->
                            FilterChip(
                                selected = selectedFrequency == freq,
                                onClick = { selectedFrequency = freq },
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
                                }
                            )
                        }
                    }

                    if (selectedFrequency == RecurringFrequency.MONTHLY) {
                        OutlinedTextField(
                            value = dayOfMonthText,
                            onValueChange = { raw ->
                                val digits = PocketLocalizer.normalizeToAsciiDigits(raw).filter { it in '0'..'9' }.take(2)
                                val num = digits.toIntOrNull()
                                dayOfMonthText = if (num != null && num > 31) "31" else digits
                            },
                            label = { Text("Billing Day of Month (1–31)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(dayOfMonthText) { dayOfMonthText = it }
                        )
                    } else if (selectedFrequency == RecurringFrequency.CUSTOM_DAYS) {
                        OutlinedTextField(
                            value = customIntervalDaysText,
                            onValueChange = { raw ->
                                customIntervalDaysText = PocketLocalizer.normalizeToAsciiDigits(raw).filter { it in '0'..'9' }.take(3)
                            },
                            label = { Text("Repeat Every X Days (e.g., 14, 45, 90)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearZeroOnFocus(customIntervalDaysText) { customIntervalDaysText = it }
                        )
                    }

                    if (existing == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dueTodayForFirstNotification = !dueTodayForFirstNotification }
                        ) {
                            Checkbox(
                                checked = dueTodayForFirstNotification,
                                onCheckedChange = { dueTodayForFirstNotification = it }
                            )
                            Text(
                                text = "Due today (show Pay / Remind Tomorrow prompt now)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTitle = titleText.trim()
                        val amt = evaluateMathExpression(amountText) ?: amountText.toDoubleOrNull() ?: 0.0
                        if (cleanTitle.isBlank()) {
                            formError = "Please enter a title."
                        } else if (selectedMode == RecurringAmountMode.FIXED && amt <= 0.0) {
                            formError = "Please enter a fixed subscription amount greater than 0."
                        } else if (selectedCategoryId == 0L) {
                            formError = "Please select a category."
                        } else {
                            val day = (dayOfMonthText.toIntOrNull() ?: 1).coerceIn(1, 31)
                            val customDays = (customIntervalDaysText.toIntOrNull() ?: 30).coerceIn(1, 365)
                            val now = System.currentTimeMillis()
                            val nextDue = when {
                                existing != null -> existing.nextDueDate
                                dueTodayForFirstNotification -> now
                                else -> PocketRepository.calculateNextRecurringDueDate(
                                    fromMillis = now,
                                    frequency = selectedFrequency.name,
                                    dayOfMonth = day,
                                    customIntervalDays = customDays
                                )
                            }
                            viewModel.saveRecurringTransaction(
                                RecurringTransactionEntity(
                                    id = existing?.id ?: 0L,
                                    title = cleanTitle,
                                    type = selectedType.name,
                                    amountMode = selectedMode.name,
                                    amount = AccountingEngine.roundCurrency(amt.coerceAtLeast(0.0)),
                                    categoryId = selectedCategoryId,
                                    defaultWalletId = defaultWalletId.takeIf { it > 0L },
                                    frequency = selectedFrequency.name,
                                    dayOfMonth = day,
                                    customIntervalDays = customDays,
                                    nextDueDate = nextDue,
                                    lastPaidDate = existing?.lastPaidDate,
                                    note = noteText.trim(),
                                    isActive = existing?.isActive ?: true
                                )
                            ) {
                                showAddOrEditDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_recurring_dialog_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOrEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CloudSyncConflictDialog(
    conflictSnapshot: CloudAccountSnapshot,
    uiState: PocketUiState,
    onChoose: (CloudConflictChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val accountEmail = uiState.googleAccountEmail?.takeIf { it.isNotBlank() } ?: "your Google account"
    val hasExistingCloudData = conflictSnapshot.profile.hasCompletedOnboarding ||
        conflictSnapshot.payload.wallets.isNotEmpty() ||
        conflictSnapshot.payload.transactions.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Local Data Will Be Gone Forever",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You have been using Pocket without logging in. Signing in to $accountEmail will permanently delete all local offline data on this device — local data will NOT be merged and will be gone forever.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasExistingCloudData) {
                    Text(
                        text = "Your Google account backup (${conflictSnapshot.payload.wallets.size} wallets, ${conflictSnapshot.payload.transactions.size} transactions) will be loaded instead.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "This Google account has no saved cloud data yet, so you will proceed to wallet setup after deleting local data.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { onChoose(CloudConflictChoice.USE_CLOUD) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("cloud_conflict_use_cloud_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wipe Local Data & Continue", fontWeight = FontWeight.Bold)
                }
                if (!hasExistingCloudData) {
                    OutlinedButton(
                        onClick = { onChoose(CloudConflictChoice.KEEP_LOCAL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cloud_conflict_keep_local_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Keep & Backup Local Data to Google", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cloud_conflict_cancel_button")
            ) {
                Text("Cancel (Stay Offline)")
            }
        }
    )
}

/**
 * Package-level localized Text wrapper for com.example.ui.screens.
 * Takes precedence over star imports from androidx.compose.material3.* and com.example.ui.components.*.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    com.example.ui.components.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun Text(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    com.example.ui.components.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style
    )
}
