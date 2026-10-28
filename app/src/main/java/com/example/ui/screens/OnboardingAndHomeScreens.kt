package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.OnboardingStage
import com.example.ui.PocketUiState
import com.example.ui.PocketViewModel
import com.example.ui.components.*
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.TabularMoneyMedium
import com.example.ui.theme.TabularMoneySmall
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    uiState: PocketUiState = PocketUiState(),
    onGoogleSignedIn: (com.google.firebase.auth.FirebaseUser) -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSignOutGoogle: () -> Unit = {},
    onContinueWithoutLoggingIn: () -> Unit = {},
    onBackToPage1: () -> Unit = {},
    onComplete: (
        currencyCode: String,
        currencySymbol: String,
        selectedWallets: List<Pair<StarterWalletOption, Double>>,
        loadSampleTransactions: Boolean,
        starterLoans: List<StarterLoanInput>,
        userName: String,
        profilePhotoUri: String
    ) -> Unit
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    val fixedCurrencyCode = "BDT"
    val fixedCurrencySymbol = "৳"

    // Startup Page 1: Centered Logo, App Name ("Pocket"), "App developed by Abdullahil Hadi", and 2 Buttons
    if (uiState.onboardingStage == OnboardingStage.PAGE_1_WELCOME) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PocketBurgundyLogo(
                    size = 96.dp,
                    modifier = Modifier.testTag("startup_page1_logo")
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Pocket",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (pocketColors.isDark) Color.White else Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_page1_app_name")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "App developed by Abdullahil Hadi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_page1_subtitle_hadi")
                )
                Spacer(modifier = Modifier.height(36.dp))

                // Button a: Sign in with Google
                GoogleSignInPrimaryButton(
                    isSyncing = uiState.isCloudSyncing,
                    onGoogleSignedIn = onGoogleSignedIn
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Button b: Continue without logging in
                OutlinedButton(
                    onClick = onContinueWithoutLoggingIn,
                    enabled = !uiState.isCloudSyncing,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("startup_page1_continue_offline_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Continue without logging in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        return
    }

    // Startup Page 2: Wallet & Profile Setup (Sign in with Google card removed from top)
    BackHandler {
        onBackToPage1()
    }

    val isGoogleSignedInOnPage2 = !uiState.googleAccountEmail.isNullOrBlank() || !uiState.googleAccountName.isNullOrBlank()

    // 1st Section: User Name & Profile Photo (populated from Google Account when signed in, username & photo remain editable)
    var userNameInput by remember(uiState.googleAccountEmail, uiState.googleAccountName) {
        mutableStateOf(uiState.settings.userName.ifBlank { uiState.googleAccountName ?: "" })
    }
    var profilePhotoPath by remember(uiState.googleAccountEmail, uiState.googleAccountPhotoUrl) {
        mutableStateOf(uiState.settings.profilePhotoUri.ifBlank { uiState.googleAccountPhotoUrl ?: "" })
    }
    var pendingCropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Automatically populate name and profile photo from Google account upon sign-in only if not already customized
    LaunchedEffect(uiState.googleAccountName, uiState.googleAccountPhotoUrl, uiState.settings.userName, uiState.settings.profilePhotoUri) {
        if (userNameInput.isBlank()) {
            val candidateName = uiState.settings.userName.ifBlank { uiState.googleAccountName ?: "" }
            if (candidateName.isNotBlank()) {
                userNameInput = candidateName
            }
        }
        if (profilePhotoPath.isBlank()) {
            val candidatePhoto = uiState.settings.profilePhotoUri.ifBlank { uiState.googleAccountPhotoUrl ?: "" }
            if (candidatePhoto.isNotBlank()) {
                profilePhotoPath = candidatePhoto
            }
        }
    }

    val scope = rememberCoroutineScope()
    val profilePhotoLauncher = rememberUniversalImagePickerLauncher(
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
                        profilePhotoPath = savedPath
                    }
                }
            }
        }
    )

    // Starter Wallets: all unchecked initially; balance fields start clean & empty with a "0" placeholder
    val starterOptions = remember { PocketRepository.defaultStarterWallets() }
    val primaryWalletNames = remember { setOf("cash", "bank", "bkash") }
    var showMoreWallets by remember { mutableStateOf(false) }
    val visibleStarterOptions = remember(starterOptions, showMoreWallets) {
        if (showMoreWallets) {
            starterOptions
        } else {
            starterOptions.filter { it.name.lowercase(Locale.ENGLISH) in primaryWalletNames }
        }
    }
    val selectedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            starterOptions.forEach { put(it.name, false) }
        }
    }
    val balanceTextMap = remember {
        mutableStateMapOf<String, String>().apply {
            starterOptions.forEach { put(it.name, "") }
        }
    }

    // Existing loan section state
    val addedLoans = remember { mutableStateListOf<StarterLoanInput>() }
    var loanPersonInput by remember { mutableStateOf("") }
    var loanAmountInput by remember { mutableStateOf("") }
    var loanDirectionInput by remember { mutableStateOf(LoanDirection.LENT) }
    var loanDeadlineMillis by remember { mutableStateOf<Long?>(null) }
    var showLoanDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App Logo + Pocket Name + "App developed by Abdullahil Hadi" + Back / Switch Account Action
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            PocketBurgundyLogo(
                                size = 44.dp,
                                modifier = Modifier.testTag("onboarding_app_logo")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Pocket",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pocketColors.isDark) Color.White else Color.Black
                                )
                                Text(
                                    text = "App developed by Abdullahil Hadi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("onboarding_subtitle_hadi")
                                )
                            }
                        }
                        if (!isGoogleSignedInOnPage2) {
                            TextButton(
                                onClick = onBackToPage1,
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("onboarding_page2_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Startup Page 1",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        }
                    }

                    if (isGoogleSignedInOnPage2) {
                        val accountDisplay = uiState.googleAccountEmail?.takeIf { it.isNotBlank() }
                            ?: uiState.googleAccountName?.takeIf { it.isNotBlank() }
                            ?: "Google Account"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_page2_google_account_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Signed in as $accountDisplay",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                                TextButton(
                                    onClick = onBackToPage1,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("onboarding_page2_switch_account_button")
                                ) {
                                    Text(
                                        text = "Switch account",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1st Section: Compact User Profile Row (Avatar + Name Input + Camera Icon)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserProfileBadge(
                            userName = userNameInput,
                            profilePhotoUri = profilePhotoPath,
                            avatarSize = 48.dp,
                            showNameBelow = false,
                            onClick = { profilePhotoLauncher() },
                            modifier = Modifier.testTag("onboarding_profile_avatar")
                        )
                        OutlinedTextField(
                            value = userNameInput,
                            onValueChange = { userNameInput = capitalizeFirstLetter(it) },
                            label = { Text("Your Name") },
                            placeholder = { Text("Enter your name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_user_name_input")
                        )
                        IconButton(
                            onClick = { profilePhotoLauncher() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("onboarding_upload_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Upload Profile Photo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Starter Wallets Heading
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wallets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = { showMoreWallets = !showMoreWallets },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("onboarding_show_more_wallets_button")
                    ) {
                        Text(
                            text = if (showMoreWallets) "Show less" else "Show more",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showMoreWallets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showMoreWallets) "Show less wallets" else "Show more wallets",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            items(visibleStarterOptions, key = { it.name }) { opt ->
                val checked = selectedMap[opt.name] == true
                val currentBalText = balanceTextMap[opt.name] ?: ""
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = if (checked) pocketColors.surfaceSelected else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (checked) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMap[opt.name] = !checked
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selectedMap[opt.name] = isChecked
                            },
                            modifier = Modifier.testTag("onboarding_wallet_checkbox_${opt.name}")
                        )
                        IconBadgeCircle(
                            iconName = opt.iconName,
                            colorHex = opt.colorHex,
                            size = 36.dp,
                            iconSize = 18.dp,
                            walletNameHint = opt.name
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = opt.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedVisibility(
                            visible = checked,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            OutlinedTextField(
                                value = currentBalText,
                                onValueChange = { balanceTextMap[opt.name] = sanitizeNumericInput(it) },
                                label = { Text("Balance ($fixedCurrencySymbol)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .width(130.dp)
                                    .clearZeroOnFocus(currentBalText) { balanceTextMap[opt.name] = it }
                                    .testTag("onboarding_wallet_balance_${opt.name}")
                            )
                        }
                    }
                }
            }

            if (!showMoreWallets) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showMoreWallets = true }
                            .testTag("onboarding_show_more_row"),
                        shape = RoundedCornerShape(14.dp),
                        color = pocketColors.surfaceElevated,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Show more wallets",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Show more wallets (${starterOptions.size - visibleStarterOptions.size} more)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (showMoreWallets) {
                // Existing Loan Section (revealed when Show More is expanded)
                item {
                    Text(
                        text = "Existing loan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("onboarding_existing_loan_header")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pocketColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = loanDirectionInput == LoanDirection.LENT,
                                    onClick = { loanDirectionInput = LoanDirection.LENT },
                                    label = { Text("Lent") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                )
                                FilterChip(
                                    selected = loanDirectionInput == LoanDirection.BORROWED,
                                    onClick = { loanDirectionInput = LoanDirection.BORROWED },
                                    label = { Text("Borrowed") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = loanPersonInput,
                                    onValueChange = { loanPersonInput = capitalizeFirstLetter(it) },
                                    label = { Text("Person") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("onboarding_loan_person_input")
                                )
                                OutlinedTextField(
                                    value = loanAmountInput,
                                    onValueChange = { loanAmountInput = sanitizeNumericInput(it) },
                                    label = { Text("Amount ($fixedCurrencySymbol)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clearZeroOnFocus(loanAmountInput) { loanAmountInput = it }
                                        .testTag("onboarding_loan_amount_input")
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showLoanDatePicker = true }
                                    .testTag("onboarding_loan_deadline_box"),
                                shape = RoundedCornerShape(12.dp),
                                color = pocketColors.surfaceElevated,
                                border = BorderStroke(1.dp, pocketColors.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = loanDeadlineMillis?.let {
                                                "Deadline: ${SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it))}"
                                            } ?: "Deadline Date (Optional — Tap to pick)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (loanDeadlineMillis != null) {
                                        IconButton(
                                            onClick = { loanDeadlineMillis = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear deadline", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val amt = loanAmountInput.toDoubleOrNull() ?: 0.0
                                    if (loanPersonInput.isNotBlank() && amt > 0.0) {
                                        addedLoans.add(
                                            StarterLoanInput(
                                                personName = loanPersonInput.trim(),
                                                direction = loanDirectionInput,
                                                principalAmount = amt,
                                                dueDate = loanDeadlineMillis
                                            )
                                        )
                                        loanPersonInput = ""
                                        loanAmountInput = ""
                                        loanDeadlineMillis = null
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("onboarding_add_loan_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Existing Loan")
                            }

                            addedLoans.forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(pocketColors.surfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.personName} · ${if (item.direction == LoanDirection.LENT) "Lent" else "Borrowed"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (item.dueDate != null) {
                                            Text(
                                                text = "Due: ${SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(item.dueDate))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = AccountingEngine.formatMoney(item.principalAmount, fixedCurrencySymbol, includeDecimals = false),
                                            style = TabularMoneySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = { addedLoans.removeAt(idx) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove loan", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fixed Bottom Action Bar: "Get started" and "Skip setup" always visible at first glance without scrolling
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    val finalLoans = addedLoans.toMutableList()
                    val pendingAmt = loanAmountInput.toDoubleOrNull() ?: 0.0
                    if (loanPersonInput.isNotBlank() && pendingAmt > 0.0) {
                        finalLoans.add(
                            StarterLoanInput(
                                personName = loanPersonInput.trim(),
                                direction = loanDirectionInput,
                                principalAmount = pendingAmt,
                                dueDate = loanDeadlineMillis
                            )
                        )
                    }

                    val chosen = starterOptions.mapNotNull { opt ->
                        if (selectedMap[opt.name] == true) {
                            val bal = (balanceTextMap[opt.name]?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                            opt to bal
                        } else null
                    }
                    onComplete(
                        fixedCurrencyCode,
                        fixedCurrencySymbol,
                        chosen,
                        false,
                        finalLoans,
                        userNameInput.trim(),
                        profilePhotoPath
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("get_started_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Get started", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }

    if (showLoanDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = loanDeadlineMillis ?: (System.currentTimeMillis() + 14L * 24L * 3600L * 1000L)
        )
        DatePickerDialog(
            onDismissRequest = { showLoanDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        loanDeadlineMillis = pickerState.selectedDateMillis
                        showLoanDatePicker = false
                    }
                ) {
                    Text("Set Deadline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoanDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    pendingCropBitmap?.let { srcBmp ->
        ProfilePhotoCropDialog(
            sourceBitmap = srcBmp,
            onDismiss = { pendingCropBitmap = null },
            onCropConfirmed = { croppedBmp ->
                saveBitmapToInternalStorage(context, croppedBmp, "profile_photos", "profile")?.let { savedPath ->
                    profilePhotoPath = savedPath
                }
                pendingCropBitmap = null
            }
        )
    }
}

@Composable
fun AppLockScreen(
    biometricEnabled: Boolean = false,
    onBiometricUnlock: () -> Unit = {},
    onUnlock: (String) -> Boolean
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var pinInput by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && BiometricAuthHelper.canAuthenticate(context)) {
            BiometricAuthHelper.showBiometricPrompt(
                context = context,
                onSuccess = onBiometricUnlock,
                onError = { msg -> biometricMessage = msg }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PocketBurgundyLogo(size = 52.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pocket Locked", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (biometricEnabled) "Enter your PIN or use Fingerprint / Face" else "Enter your PIN to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        performPocketHaptic(view)
                        pinInput = it
                        hasError = false
                        biometricMessage = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = hasError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_pin_input")
                )
                if (hasError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Incorrect PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!biometricMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = biometricMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        performPocketHaptic(view)
                        if (!onUnlock(pinInput)) {
                            hasError = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("app_lock_unlock_button")
                ) {
                    Text("Unlock")
                }
                if (biometricEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            biometricMessage = null
                            BiometricAuthHelper.showBiometricPrompt(
                                context = context,
                                onSuccess = onBiometricUnlock,
                                onError = { msg -> biometricMessage = msg }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("app_lock_biometric_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Unlock with Fingerprint or Face",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with Fingerprint / Face")
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    uiState: PocketUiState,
    viewModel: PocketViewModel
) {
    WalletsOverviewScreen(uiState = uiState, viewModel = viewModel)
}

@Composable
fun BigRecordActionTile(
    icon: ImageVector,
    title: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Composable
fun LedgerEntryActionDialog(
    entry: UnifiedLedgerEntry,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showFullPhoto by remember { mutableStateOf(false) }
    val cleanAttachmentUri = entry.attachmentUri.removePrefix("CUSTOM_URI:").trim()
    val attachmentBitmap = if (cleanAttachmentUri.isNotBlank()) {
        com.example.ui.components.rememberDecodedImageBitmap(cleanAttachmentUri, maxDimension = 512)
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPocketColors.current.overlaySurface,
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Amount: ${AccountingEngine.formatMoney(entry.signedAmount, currencySymbol, showSign = true)}",
                    style = TabularMoneyMedium
                )
                Text("Category: ${entry.categoryName}")
                Text("Wallet: ${entry.walletName}${if (entry.secondaryWalletName != null) " → ${entry.secondaryWalletName}" else ""}")
                if (entry.feeAmount > 0.0) {
                    Text("Transfer Fee: ${AccountingEngine.formatMoney(entry.feeAmount, currencySymbol)}")
                }
                if (entry.reference.isNotBlank()) {
                    Text("Reference: ${entry.reference}")
                }
                if (entry.tags.isNotBlank()) {
                    Text("Tags: ${entry.tags}")
                }
                if (attachmentBitmap != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LocalPocketColors.current.surfaceElevated,
                        border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullPhoto = true }
                            .testTag("ledger_entry_attachment_preview")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = attachmentBitmap,
                                contentDescription = "Receipt photo preview",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Attached Receipt / Slip",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to view full screen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "View full screen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (confirmDelete) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Delete this record and update wallet balances?",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_entry_button")
                ) {
                    Text("Confirm Delete")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onDuplicate != null) {
                        OutlinedButton(onClick = onDuplicate) {
                            Text("Duplicate")
                        }
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_entry_button")
                    ) {
                        Text("Edit")
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!confirmDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_entry_button")
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )

    if (showFullPhoto && attachmentBitmap != null) {
        com.example.ui.components.FullScreenPhotoViewerDialog(
            photoUri = cleanAttachmentUri,
            onDismiss = { showFullPhoto = false }
        )
    }
}
