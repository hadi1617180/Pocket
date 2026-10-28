package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BottomNavTab
import com.example.ui.PocketScreen
import com.example.ui.PocketViewModel
import com.example.ui.components.LiquidBottomNavigationBar
import com.example.ui.screens.*
import com.example.ui.theme.PocketTheme
import com.example.widget.PocketWidgetUpdater

class MainActivity : FragmentActivity() {
    private var pendingImagePickerCallback: ((android.net.Uri) -> Unit)? = null

    private val localImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dataIntent = result.data
        val pickedUri = dataIntent?.data
            ?: dataIntent?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0)?.uri else null
            }
        if (pickedUri != null) {
            runCatching {
                val takeFlags = dataIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
                if (takeFlags != 0) {
                    contentResolver.takePersistableUriPermission(pickedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            pendingImagePickerCallback?.invoke(pickedUri)
        }
    }

    fun launchLocalImagePicker(onUriSelected: (android.net.Uri) -> Unit): Boolean {
        pendingImagePickerCallback = onUriSelected
        val candidates = buildLocalImagePickerIntents(this)
        for (intent in candidates) {
            val launched = runCatching {
                localImagePickerLauncher.launch(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val pocketViewModel: PocketViewModel = viewModel(
                factory = PocketViewModel.Factory(context)
            )

            // Handle initial widget deep-link intent if present
            LaunchedEffect(Unit) {
                dispatchWidgetIntent(intent, pocketViewModel)
                kotlinx.coroutines.delay(600)
                PocketWidgetUpdater.refreshAllWidgets(context)
            }

            // Listen for new widget intents when MainActivity is in singleTop mode
            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    setIntent(newIntent)
                    dispatchWidgetIntent(newIntent, pocketViewModel)
                }
                addOnNewIntentListener(listener)
                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            PocketApp(viewModel = pocketViewModel)
        }
    }

    private fun dispatchWidgetIntent(intent: Intent?, viewModel: PocketViewModel) {
        val destination = intent?.getStringExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION) ?: return
        val walletId = intent.getLongExtra(PocketWidgetUpdater.EXTRA_WIDGET_WALLET_ID, 0L)
        val recurringId = intent.getLongExtra(
            com.example.notification.PocketNotificationManager.EXTRA_RECURRING_ID,
            0L
        )
        viewModel.handleWidgetDeepLink(
            destination = destination,
            walletId = walletId,
            recurringId = recurringId
        )
        intent.removeExtra(PocketWidgetUpdater.EXTRA_WIDGET_DESTINATION)
        intent.removeExtra(com.example.notification.PocketNotificationManager.EXTRA_RECURRING_ID)
    }

    companion object {
        fun buildLocalImagePickerIntents(context: android.content.Context): List<Intent> {
            val intents = mutableListOf<Intent>()
            // 1. Standard Gallery / MediaStore ACTION_PICK preserving both content:// data URI and image/* MIME type
            intents.add(
                Intent(Intent.ACTION_PICK).apply {
                    setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 2. Gallery ACTION_PICK with EXTERNAL_CONTENT_URI only (no setType call that would clear mData)
            intents.add(
                Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 3. ACTION_GET_CONTENT for image/* without CATEGORY_OPENABLE (supports OEM Gallery & File Managers)
            intents.add(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 4. Standard ACTION_GET_CONTENT with CATEGORY_OPENABLE
            intents.add(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 5. Storage Access Framework ACTION_OPEN_DOCUMENT
            intents.add(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
            )
            // 6. Android Photo Picker contract intent
            runCatching {
                val pickVisualMediaIntent = ActivityResultContracts.PickVisualMedia().createIntent(
                    context,
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                intents.add(pickVisualMediaIntent)
            }
            // 7. ACTION_PICK with INTERNAL_CONTENT_URI
            intents.add(
                Intent(Intent.ACTION_PICK).apply {
                    setDataAndType(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 8. Generic ACTION_PICK with type image/*
            intents.add(
                Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 9. Unfiltered ACTION_GET_CONTENT for local file browsers
            intents.add(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            // 10. System Chooser wrapper (always resolves to ResolverActivity on Android)
            intents.add(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Select Profile Photo"
                )
            )
            return intents
        }
    }
}

@Composable
fun PocketApp(viewModel: PocketViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val s = uiState.settings
            com.example.notification.PocketNotificationManager.scheduleAllReminders(
                context = context,
                loanDeadlineEnabled = s.loanDeadlineNotificationEnabled,
                dailyReminderEnabled = s.dailyExpenseReminderEnabled,
                dailyHour = s.dailyReminderHour,
                dailyMinute = s.dailyReminderMinute
            )
        }
    }

    LaunchedEffect(uiState.settings.hasCompletedOnboarding) {
        if (uiState.settings.hasCompletedOnboarding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!com.example.notification.PocketNotificationManager.hasNotificationPermission(context)) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    PocketTheme(
        themeMode = uiState.settings.themeMode,
        accentColorHex = uiState.settings.accentColorHex,
        customBackgroundUri = uiState.settings.customBackgroundUri,
        backgroundOpacity = uiState.settings.backgroundOpacity
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize())
            return@PocketTheme
        }

        if (!uiState.settings.hasCompletedOnboarding) {
            OnboardingScreen(
                uiState = uiState,
                onGoogleSignedIn = { user -> viewModel.onGoogleUserSignedIn(user) },
                onSyncNow = { viewModel.syncCloudNow() },
                onSignOutGoogle = { viewModel.signOutGoogle(context) },
                onContinueWithoutLoggingIn = { viewModel.continueWithoutLoggingIn(context) },
                onBackToPage1 = { viewModel.returnToStartupPage1(context) },
                onComplete = { currencyCode, currencySymbol, selectedWallets, loadSampleTransactions, starterLoans, userName, profilePhotoUri ->
                    viewModel.completeOnboarding(
                        currencyCode = currencyCode,
                        currencySymbol = currencySymbol,
                        selectedWallets = selectedWallets,
                        loadSampleTransactions = loadSampleTransactions,
                        starterLoans = starterLoans,
                        userName = userName,
                        profilePhotoUri = profilePhotoUri
                    )
                }
            )
            return@PocketTheme
        }

        if (!uiState.isAppUnlocked) {
            AppLockScreen(
                biometricEnabled = uiState.settings.biometricUnlockEnabled,
                onBiometricUnlock = { viewModel.unlockAppWithBiometrics() },
                onUnlock = { pin -> viewModel.unlockAppWithPin(pin) }
            )
            return@PocketTheme
        }

        val isTopLevelScreen = uiState.currentScreen is PocketScreen.Home ||
            uiState.currentScreen is PocketScreen.Wallets ||
            uiState.currentScreen is PocketScreen.TransactionHistory ||
            uiState.currentScreen is PocketScreen.Reports ||
            uiState.currentScreen is PocketScreen.LoansManagement

        val isOverlayPopupScreen = uiState.currentScreen is PocketScreen.TransactionForm ||
            uiState.currentScreen is PocketScreen.TransferForm ||
            uiState.currentScreen is PocketScreen.BalanceAdjustment

        // Mandatory BackHandler for custom state-based navigation on all secondary screens or non-Balances tabs
        BackHandler(enabled = uiState.currentScreen !is PocketScreen.Wallets && uiState.currentScreen !is PocketScreen.Home) {
            viewModel.navigateBack()
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevelScreen) {
                    LiquidBottomNavigationBar(
                        activeTab = uiState.activeBottomTab,
                        onSelectTab = { tab -> viewModel.switchBottomTab(tab) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // When an overlay popup screen (New Record, Transfer, Reconcile) is open,
                // render the active background tab with a 12.dp blur and lighter scrim underneath
                if (isOverlayPopupScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(12.dp)
                    ) {
                        when (uiState.activeBottomTab) {
                            BottomNavTab.BALANCES -> WalletsOverviewScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            BottomNavTab.RECENT_TRANSACTIONS -> TransactionHistoryScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                initialTypeFilter = null,
                                initialCategoryId = null,
                                initialWalletId = null
                            )
                            BottomNavTab.REPORTS -> ReportsScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            BottomNavTab.LOANS -> LoansScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f))
                    )
                }

                AnimatedContent(
                    targetState = uiState.currentScreen,
                    transitionSpec = {
                        (fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 260f
                            )
                        ) + slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.74f,
                                stiffness = 280f
                            ),
                            initialOffsetX = { fullWidth -> fullWidth / 10 }
                        )) togetherWith (fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            targetOffsetX = { fullWidth -> -fullWidth / 14 }
                        ))
                    },
                    label = "liquid_screen_transition"
                ) { screen ->
                    when (screen) {
                        is PocketScreen.Home, is PocketScreen.Wallets -> WalletsOverviewScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        is PocketScreen.WalletDetail -> WalletDetailScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            walletId = screen.walletId
                        )
                        is PocketScreen.Reports -> ReportsScreen(uiState = uiState, viewModel = viewModel)
                        is PocketScreen.Settings -> SettingsScreen(uiState = uiState, viewModel = viewModel)
                        is PocketScreen.TransactionForm -> TransactionFormScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            initialType = screen.initialType,
                            editTransactionId = screen.editTransactionId,
                            preselectedWalletId = screen.preselectedWalletId
                        )
                        is PocketScreen.TransferForm -> TransferFormScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            editTransferId = screen.editTransferId,
                            preselectedSourceWalletId = screen.preselectedSourceWalletId
                        )
                        is PocketScreen.TransactionHistory -> TransactionHistoryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            initialTypeFilter = screen.initialTypeFilter,
                            initialCategoryId = screen.initialCategoryId,
                            initialWalletId = screen.initialWalletId
                        )
                        is PocketScreen.MonthlyStatement -> MonthlyStatementScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            initialWalletId = screen.initialWalletId
                        )
                        is PocketScreen.CategoriesManagement -> CategoriesScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        is PocketScreen.LoansManagement -> LoansScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        is PocketScreen.RecurringManagement -> RecurringTransactionsScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        is PocketScreen.BalanceAdjustment -> BalanceAdjustmentScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            preselectedWalletId = screen.preselectedWalletId
                        )
                        is PocketScreen.BackupRestore -> BackupRestoreScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        is PocketScreen.WidgetConfig -> WidgetConfigScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }

                uiState.pendingRecurringPayId?.let { pendingId ->
                    val targetRecurring = uiState.recurringTransactions.firstOrNull { it.id == pendingId }
                    if (targetRecurring != null) {
                        RecurringPayConfirmDialog(
                            recurring = targetRecurring,
                            uiState = uiState,
                            viewModel = viewModel,
                            onDismiss = { viewModel.dismissRecurringPayDialog() }
                        )
                    } else {
                        viewModel.dismissRecurringPayDialog()
                    }
                }

                uiState.pendingCloudConflict?.let { conflictSnapshot ->
                    CloudSyncConflictDialog(
                        conflictSnapshot = conflictSnapshot,
                        uiState = uiState,
                        onChoose = { choice -> viewModel.resolveCloudSyncConflict(choice) },
                        onDismiss = { viewModel.dismissCloudSyncConflict() }
                    )
                }
            }
        }
    }
}
