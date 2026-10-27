package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.notification.PocketNotificationManager
import com.example.ui.components.saveBitmapToInternalStorage
import com.example.ui.components.saveUprightImageFromUri
import com.example.widget.PocketWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

enum class BottomNavTab(val label: String) {
    BALANCES("Balances"),
    RECENT_TRANSACTIONS("History"),
    REPORTS("Report"),
    LOANS("Loans")
}

sealed class PocketScreen {
    data object Home : PocketScreen()
    data object Wallets : PocketScreen()
    data class WalletDetail(val walletId: Long) : PocketScreen()
    data object Reports : PocketScreen()
    data object Settings : PocketScreen()
    data class TransactionForm(
        val initialType: TransactionType = TransactionType.EXPENSE,
        val editTransactionId: Long? = null,
        val preselectedWalletId: Long? = null
    ) : PocketScreen()
    data class TransferForm(
        val editTransferId: Long? = null,
        val preselectedSourceWalletId: Long? = null
    ) : PocketScreen()
    data class TransactionHistory(
        val initialTypeFilter: LedgerEntryKind? = null,
        val initialCategoryId: Long? = null,
        val initialWalletId: Long? = null
    ) : PocketScreen()
    data class MonthlyStatement(val initialWalletId: Long? = null) : PocketScreen()
    data object CategoriesManagement : PocketScreen()
    data object LoansManagement : PocketScreen()
    data object RecurringManagement : PocketScreen()
    data class BalanceAdjustment(val preselectedWalletId: Long? = null) : PocketScreen()
    data object BackupRestore : PocketScreen()
    data object WidgetConfig : PocketScreen()
}

enum class CloudConflictChoice {
    MERGE_BOTH,
    KEEP_LOCAL,
    USE_CLOUD
}

data class PocketUiState(
    val isLoading: Boolean = true,
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val isAppUnlocked: Boolean = true,
    val currentScreen: PocketScreen = PocketScreen.Wallets,
    val activeBottomTab: BottomNavTab = BottomNavTab.BALANCES,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedReportCurrency: String = "BDT",
    val wallets: List<WalletEntity> = emptyList(),
    val walletSummaries: List<WalletBalanceSummary> = emptyList(),
    val activeWalletSummaries: List<WalletBalanceSummary> = emptyList(),
    val archivedWalletSummaries: List<WalletBalanceSummary> = emptyList(),
    val totalAvailableBalance: Double = 0.0,
    val foreignCurrencyTotals: List<Pair<String, Double>> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val loanActivities: List<LoanActivityEntity> = emptyList(),
    val loanSummaries: List<LoanCalculatedSummary> = emptyList(),
    val adjustments: List<BalanceAdjustmentEntity> = emptyList(),
    val recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList(),
    val pendingRecurringPayId: Long? = null,
    val pendingCloudConflict: CloudAccountSnapshot? = null,
    val unifiedLedger: List<UnifiedLedgerEntry> = emptyList(),
    val monthlyReport: MonthlyReportSummary? = null,
    val statusMessage: String? = null,
    val googleAccountEmail: String? = null,
    val googleAccountName: String? = null,
    val googleAccountPhotoUrl: String? = null,
    val isCloudSyncing: Boolean = false,
    val lastCloudSyncStatus: String? = null
) {
    val personLoanKhataSummaries: List<PersonLoanKhataSummary>
        get() = loanSummaries
            .groupBy { it.loan.personName.trim().lowercase(java.util.Locale.ENGLISH) }
            .values
            .map { group ->
                val displayName = group.first().loan.personName.trim().ifBlank { "Unknown" }
                val lentOut = group.filter { it.loan.direction == LoanDirection.LENT.name }
                    .sumOf { it.outstandingPrincipal }
                val borrowedOut = group.filter { it.loan.direction == LoanDirection.BORROWED.name }
                    .sumOf { it.outstandingPrincipal }
                PersonLoanKhataSummary(
                    personName = displayName,
                    loans = group,
                    totalLentOutstanding = AccountingEngine.roundCurrency(lentOut),
                    totalBorrowedOutstanding = AccountingEngine.roundCurrency(borrowedOut),
                    netReceivableMinusPayable = AccountingEngine.roundCurrency(lentOut - borrowedOut)
                )
            }
            .sortedByDescending { kotlin.math.abs(it.netReceivableMinusPayable) }
}

private data class CloudAuthUiState(
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncStatus: String? = null,
    val pendingConflict: CloudAccountSnapshot? = null
)

private data class RawDatabaseBundle(
    val wallets: List<WalletEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val loans: List<LoanEntity>,
    val loanActivities: List<LoanActivityEntity>,
    val adjustments: List<BalanceAdjustmentEntity>,
    val recurringTransactions: List<RecurringTransactionEntity>,
    val savingsGoals: List<SavingsGoalEntity>,
    val settings: AppSettingsEntity?
)

class PocketViewModel(
    private val repository: PocketRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val cloudSyncRepository: PocketCloudSyncRepository? by lazy {
        appContext?.let { ctx ->
            runCatching { PocketCloudSyncRepository(ctx) }.getOrNull()
        }
    }

    private val _navStack = MutableStateFlow<List<PocketScreen>>(listOf(PocketScreen.Wallets))
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    private val _selectedReportCurrency = MutableStateFlow("BDT")
    private val _isAppUnlocked = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _pendingRecurringPayId = MutableStateFlow<Long?>(null)
    private val _cloudAuthState = MutableStateFlow(CloudAuthUiState())

    private var remoteProfileObserverJob: kotlinx.coroutines.Job? = null
    private var debouncedCloudPushJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastKnownSyncVersion: Long = 0L
    @Volatile
    private var isApplyingRemoteSnapshot: Boolean = false
    @Volatile
    private var lastBackgroundTimestampMillis: Long = 0L

    companion object {
        const val APP_LOCK_GRACE_PERIOD_MS = 30_000L
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultCategoriesExist()
            appContext?.let { ctx ->
                PocketNotificationManager.scheduleAllReminders(ctx)
                PocketNotificationManager.syncLoanNotifications(ctx)
                PocketNotificationManager.syncRecurringNotifications(ctx)
                PocketNotificationManager.syncCreditCardNotifications(ctx)
            }
            val initialSettings = runCatching { repository.buildBackupPayload().settings }.getOrNull()
            val existingUser = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }.getOrNull()
            if (existingUser != null) {
                if (initialSettings?.hasCompletedOnboarding == true) {
                    onGoogleUserSignedIn(existingUser, isSilentStartupRestore = true)
                } else {
                    // Fresh install or cleared data: stay on Startup / Onboarding screen until user explicitly signs in or sets up
                    runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
                }
            }
        }
    }

    private fun notifyWidgetsChanged() {
        scheduleCloudPush()
        appContext?.let { ctx ->
            PocketWidgetUpdater.refreshAllWidgets(ctx)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { PocketNotificationManager.syncLoanNotifications(ctx) }
                runCatching { PocketNotificationManager.syncRecurringNotifications(ctx) }
                runCatching { PocketNotificationManager.syncCreditCardNotifications(ctx) }
            }
        }
    }

    fun scheduleCloudPush() {
        val authState = _cloudAuthState.value
        val uid = authState.uid ?: return
        val cloudRepo = cloudSyncRepository ?: return
        if (isApplyingRemoteSnapshot) return

        debouncedCloudPushJob?.cancel()
        debouncedCloudPushJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            if (isApplyingRemoteSnapshot) return@launch
            _cloudAuthState.update { it.copy(isSyncing = true) }
            runCatching {
                val payload = repository.buildBackupPayload()
                val nextVersion = maxOf(System.currentTimeMillis(), lastKnownSyncVersion + 1L)
                lastKnownSyncVersion = nextVersion
                cloudRepo.pushLocalPayloadToCloud(
                    userId = uid,
                    email = authState.email ?: "",
                    googleDisplayName = authState.displayName ?: "",
                    googlePhotoUrl = authState.photoUrl ?: "",
                    payload = payload,
                    syncVersion = nextVersion
                )
                _cloudAuthState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncStatus = "Synced with Google Cloud"
                    )
                }
            }.onFailure { err ->
                _cloudAuthState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncStatus = "Cloud sync paused: ${err.localizedMessage ?: "offline"}"
                    )
                }
            }
        }
    }

    fun useGoogleProfilePhoto() {
        val googlePhoto = _cloudAuthState.value.photoUrl?.trim().orEmpty()
        if (googlePhoto.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = repository.buildBackupPayload().settings
            repository.updateSettings(currentSettings.copy(profilePhotoUri = googlePhoto))
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Switched to Google Account profile photo."
                notifyWidgetsChanged()
            }
        }
    }

    fun onGoogleUserSignedIn(
        user: com.google.firebase.auth.FirebaseUser,
        isSilentStartupRestore: Boolean = false
    ) {
        val uid = user.uid
        val googleName = user.displayName?.trim().orEmpty()
        val googleEmail = user.email?.trim().orEmpty()
        val googlePhoto = user.photoUrl?.toString()?.trim().orEmpty()

        _cloudAuthState.update {
            it.copy(
                uid = uid,
                email = googleEmail,
                displayName = googleName,
                photoUrl = googlePhoto,
                isSyncing = true,
                lastSyncStatus = "Connecting to Google Cloud..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentPayload = repository.buildBackupPayload()
            val currentSettings = currentPayload.settings
            val hasCustomLocalPhoto = currentSettings.profilePhotoUri.isNotBlank() &&
                !currentSettings.profilePhotoUri.startsWith("http://", ignoreCase = true) &&
                !currentSettings.profilePhotoUri.startsWith("https://", ignoreCase = true) &&
                (currentSettings.profilePhotoUri.startsWith("data:image/", ignoreCase = true) ||
                    File(currentSettings.profilePhotoUri.removePrefix("CUSTOM_URI:")).exists())
            val updatedName = currentSettings.userName.trim().ifBlank { googleName }
            val updatedPhoto = when {
                hasCustomLocalPhoto -> currentSettings.profilePhotoUri
                currentSettings.profilePhotoUri.isNotBlank() -> currentSettings.profilePhotoUri
                googlePhoto.isNotBlank() -> googlePhoto
                else -> ""
            }

            if (updatedName != currentSettings.userName || updatedPhoto != currentSettings.profilePhotoUri) {
                repository.updateSettings(
                    currentSettings.copy(
                        userName = updatedName,
                        profilePhotoUri = updatedPhoto
                    )
                )
            }

            val cloudRepo = cloudSyncRepository
            if (cloudRepo != null) {
                var shouldStartObserver = true
                runCatching {
                    val refreshedSettings = repository.buildBackupPayload().settings
                    val remoteSnapshot = cloudRepo.pullCloudPayloadIfPresent(uid, refreshedSettings)

                    if (!isSilentStartupRestore && currentSettings.hasCompletedOnboarding) {
                        // User used the app without logging in. Do NOT merge local data with Google account.
                        // Show a warning that all local data will be gone forever once logged in.
                        shouldStartObserver = false
                        val targetSnapshot = if (remoteSnapshot != null && (remoteSnapshot.profile.hasCompletedOnboarding || remoteSnapshot.payload.wallets.isNotEmpty())) {
                            remoteSnapshot
                        } else {
                            val cleanSettings = AppSettingsEntity(
                                id = 1,
                                currencyCode = "BDT",
                                currencySymbol = "৳",
                                themeMode = currentSettings.themeMode,
                                accentColorHex = currentSettings.accentColorHex,
                                hasCompletedOnboarding = true,
                                userName = googleName,
                                profilePhotoUri = googlePhoto
                            )
                            CloudAccountSnapshot(
                                profile = UserProfileDoc(
                                    docId = uid,
                                    userId = uid,
                                    displayName = googleName,
                                    email = googleEmail,
                                    photoUrl = googlePhoto,
                                    themeMode = currentSettings.themeMode,
                                    accentColorHex = currentSettings.accentColorHex,
                                    hasCompletedOnboarding = true,
                                    syncVersion = 0L
                                ),
                                payload = PocketBackupPayload(
                                    formatVersion = "pocket-backup-v1",
                                    exportedAtMillis = System.currentTimeMillis(),
                                    wallets = emptyList(),
                                    categories = currentPayload.categories.map { it.copy(monthlyBudget = 0.0) },
                                    transactions = emptyList(),
                                    transfers = emptyList(),
                                    loans = emptyList(),
                                    loanActivities = emptyList(),
                                    adjustments = emptyList(),
                                    recurringTransactions = emptyList(),
                                    savingsGoals = emptyList(),
                                    settings = cleanSettings
                                )
                            )
                        }
                        _cloudAuthState.update {
                            it.copy(
                                isSyncing = false,
                                pendingConflict = targetSnapshot,
                                lastSyncStatus = "Confirm switching to Google Account"
                            )
                        }
                    } else if (remoteSnapshot != null && (remoteSnapshot.profile.hasCompletedOnboarding || remoteSnapshot.payload.wallets.isNotEmpty())) {
                        isApplyingRemoteSnapshot = true
                        try {
                            lastKnownSyncVersion = remoteSnapshot.profile.syncVersion
                            val finalName = remoteSnapshot.profile.displayName.trim().ifBlank { googleName.ifBlank { updatedName } }
                            val finalPhoto = remoteSnapshot.profile.photoUrl.trim().ifBlank { googlePhoto.ifBlank { updatedPhoto } }
                            repository.restoreBackupPayload(
                                remoteSnapshot.payload.copy(
                                    settings = remoteSnapshot.payload.settings.copy(
                                        id = 1,
                                        hasCompletedOnboarding = true,
                                        userName = finalName,
                                        profilePhotoUri = finalPhoto
                                    )
                                )
                            )
                        } finally {
                            isApplyingRemoteSnapshot = false
                        }
                        withContext(Dispatchers.Main) {
                            _navStack.value = listOf(PocketScreen.Wallets)
                            if (!isSilentStartupRestore) {
                                _statusMessage.value = "Restored account data for ${remoteSnapshot.profile.displayName.ifBlank { googleEmail }} from Google Cloud."
                            }
                            notifyWidgetsChanged()
                        }
                        _cloudAuthState.update {
                            it.copy(
                                isSyncing = false,
                                pendingConflict = null,
                                lastSyncStatus = "Synced (${remoteSnapshot.payload.wallets.size} wallets, ${remoteSnapshot.payload.transactions.size} transactions)"
                            )
                        }
                    } else if (refreshedSettings.hasCompletedOnboarding) {
                        val latestPayload = repository.buildBackupPayload()
                        val version = System.currentTimeMillis()
                        lastKnownSyncVersion = version
                        cloudRepo.pushLocalPayloadToCloud(
                            userId = uid,
                            email = googleEmail,
                            googleDisplayName = googleName,
                            googlePhotoUrl = googlePhoto,
                            payload = latestPayload,
                            syncVersion = version
                        )
                        withContext(Dispatchers.Main) {
                            if (!isSilentStartupRestore) {
                                _statusMessage.value = "Signed in as ${googleName.ifBlank { googleEmail }} and backed up data to Google Cloud."
                            }
                        }
                        _cloudAuthState.update {
                            it.copy(
                                isSyncing = false,
                                pendingConflict = null,
                                lastSyncStatus = "Backed up to Google Cloud"
                            )
                        }
                    } else {
                        _cloudAuthState.update {
                            it.copy(
                                isSyncing = false,
                                pendingConflict = null,
                                lastSyncStatus = "Signed in as ${googleName.ifBlank { googleEmail }}"
                            )
                        }
                    }
                }.onFailure { err ->
                    _cloudAuthState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncStatus = "Signed in (${err.localizedMessage ?: "cloud sync pending"})"
                        )
                    }
                }

                if (shouldStartObserver) {
                    startObservingRemoteProfile(uid)
                }
            } else {
                _cloudAuthState.update {
                    it.copy(isSyncing = false, lastSyncStatus = "Signed in with Google")
                }
            }
        }
    }

    fun resolveCloudSyncConflict(choice: CloudConflictChoice) {
        val authState = _cloudAuthState.value
        val conflictSnapshot = authState.pendingConflict ?: return
        val uid = authState.uid ?: return
        val cloudRepo = cloudSyncRepository

        _cloudAuthState.update {
            it.copy(isSyncing = true, pendingConflict = null, lastSyncStatus = "Switching to Google Account...")
        }

        viewModelScope.launch(Dispatchers.IO) {
            val targetPayload = conflictSnapshot.payload.copy(
                settings = conflictSnapshot.payload.settings.copy(
                    id = 1,
                    hasCompletedOnboarding = true,
                    userName = conflictSnapshot.profile.displayName.ifBlank { authState.displayName.orEmpty() },
                    profilePhotoUri = conflictSnapshot.profile.photoUrl.ifBlank { authState.photoUrl.orEmpty() }
                )
            )

            isApplyingRemoteSnapshot = true
            try {
                repository.restoreBackupPayload(targetPayload)
            } finally {
                isApplyingRemoteSnapshot = false
            }

            if (cloudRepo != null && conflictSnapshot.profile.syncVersion == 0L) {
                runCatching {
                    val nextVersion = System.currentTimeMillis()
                    lastKnownSyncVersion = nextVersion
                    cloudRepo.pushLocalPayloadToCloud(
                        userId = uid,
                        email = authState.email ?: "",
                        googleDisplayName = authState.displayName ?: "",
                        googlePhotoUrl = authState.photoUrl ?: "",
                        payload = targetPayload,
                        syncVersion = nextVersion
                    )
                }
            } else {
                lastKnownSyncVersion = conflictSnapshot.profile.syncVersion
            }

            startObservingRemoteProfile(uid)

            val summaryMsg = "Local data cleared. Switched to Google Account (${targetPayload.wallets.size} wallets, ${targetPayload.transactions.size} transactions)."

            _cloudAuthState.update {
                it.copy(
                    isSyncing = false,
                    pendingConflict = null,
                    lastSyncStatus = "Synced (${targetPayload.wallets.size} wallets, ${targetPayload.transactions.size} transactions)"
                )
            }

            withContext(Dispatchers.Main) {
                _navStack.value = listOf(PocketScreen.Wallets)
                _statusMessage.value = summaryMsg
                notifyWidgetsChanged()
            }
        }
    }

    fun dismissCloudSyncConflict() {
        remoteProfileObserverJob?.cancel()
        debouncedCloudPushJob?.cancel()
        viewModelScope.launch {
            appContext?.let { ctx ->
                com.example.ui.components.signOutGoogleAccount(ctx)
            } ?: runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            }
            _cloudAuthState.value = CloudAuthUiState()
            _statusMessage.value = "Sign-in cancelled. Staying offline with current local data."
        }
    }

    private fun startObservingRemoteProfile(uid: String) {
        val cloudRepo = cloudSyncRepository ?: return
        remoteProfileObserverJob?.cancel()
        remoteProfileObserverJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cloudRepo.observeUserProfile(uid).collect { remoteProfile ->
                    if (remoteProfile != null &&
                        remoteProfile.syncVersion > lastKnownSyncVersion &&
                        !isApplyingRemoteSnapshot
                    ) {
                        lastKnownSyncVersion = remoteProfile.syncVersion
                        val currentPayload = repository.buildBackupPayload()
                        val remoteSnapshot = cloudRepo.pullCloudPayloadIfPresent(uid, currentPayload.settings)
                        if (remoteSnapshot != null && remoteSnapshot.profile.hasCompletedOnboarding) {
                            isApplyingRemoteSnapshot = true
                            try {
                                repository.restoreBackupPayload(
                                    remoteSnapshot.payload.copy(
                                        recurringTransactions = remoteSnapshot.payload.recurringTransactions.ifEmpty {
                                            currentPayload.recurringTransactions
                                        }
                                    )
                                )
                            } finally {
                                isApplyingRemoteSnapshot = false
                            }
                            withContext(Dispatchers.Main) {
                                notifyWidgetsChanged()
                            }
                            _cloudAuthState.update {
                                it.copy(
                                    isSyncing = false,
                                    lastSyncStatus = "Synced from another device"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun syncCloudNow() {
        val authState = _cloudAuthState.value
        val uid = authState.uid ?: return
        val cloudRepo = cloudSyncRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _cloudAuthState.update { it.copy(isSyncing = true, lastSyncStatus = "Syncing with Google Cloud...") }
            runCatching {
                val payload = repository.buildBackupPayload()
                val nextVersion = maxOf(System.currentTimeMillis(), lastKnownSyncVersion + 1L)
                lastKnownSyncVersion = nextVersion
                cloudRepo.pushLocalPayloadToCloud(
                    userId = uid,
                    email = authState.email ?: "",
                    googleDisplayName = authState.displayName ?: "",
                    googlePhotoUrl = authState.photoUrl ?: "",
                    payload = payload,
                    syncVersion = nextVersion
                )
                _cloudAuthState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncStatus = "All data synced to Google Cloud"
                    )
                }
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Synced all wallets, transactions, loans, and profile with Google Cloud."
                }
            }.onFailure { err ->
                _cloudAuthState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncStatus = "Sync failed: ${err.localizedMessage ?: "check connection"}"
                    )
                }
            }
        }
    }

    fun signOutGoogle(context: Context) {
        remoteProfileObserverJob?.cancel()
        debouncedCloudPushJob?.cancel()
        viewModelScope.launch {
            com.example.ui.components.signOutGoogleAccount(context)
            _cloudAuthState.value = CloudAuthUiState()
            _statusMessage.value = "Signed out of Google Cloud Sync. Local data remains on this device."
        }
    }

    private val rawDbFlow: Flow<RawDatabaseBundle> = combine(
        repository.walletsFlow,
        repository.categoriesFlow,
        repository.transactionsFlow,
        repository.transfersFlow,
        repository.loansFlow,
        repository.loanActivitiesFlow,
        repository.adjustmentsFlow,
        repository.recurringTransactionsFlow,
        repository.savingsGoalsFlow,
        repository.settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        RawDatabaseBundle(
            wallets = values[0] as List<WalletEntity>,
            categories = values[1] as List<CategoryEntity>,
            transactions = values[2] as List<TransactionEntity>,
            transfers = values[3] as List<TransferEntity>,
            loans = values[4] as List<LoanEntity>,
            loanActivities = values[5] as List<LoanActivityEntity>,
            adjustments = values[6] as List<BalanceAdjustmentEntity>,
            recurringTransactions = values[7] as List<RecurringTransactionEntity>,
            savingsGoals = values[8] as List<SavingsGoalEntity>,
            settings = values[9] as AppSettingsEntity?
        )
    }

    val uiState: StateFlow<PocketUiState> = combine(
        rawDbFlow,
        _navStack,
        _selectedYear,
        _selectedMonth,
        combine(_isAppUnlocked, _statusMessage, _cloudAuthState, _pendingRecurringPayId, _selectedReportCurrency) { u, m, c, p, rc ->
            listOf(u, m, c, p, rc)
        }
    ) { db, navStack, year, month, extraList ->
        val unlocked = extraList[0] as Boolean
        val message = extraList[1] as String?
        val cloudAuth = extraList[2] as CloudAuthUiState
        val pendingRecurringId = extraList[3] as Long?
        val requestedReportCurrency = (extraList[4] as String).ifBlank { "BDT" }
        val rawSettings = db.settings ?: AppSettingsEntity()
        val settings = rawSettings.copy(currencyCode = "BDT", currencySymbol = "৳")
        val effectiveUnlocked = !settings.appLockEnabled || settings.appLockPin.isBlank() || unlocked

        val sortedWallets = db.wallets.sortedWith(
            compareBy<WalletEntity> {
                if (it.sortOrder > 0) it.sortOrder else (PocketRepository.canonicalWalletSortOrder(it.name) ?: 100)
            }.thenBy { it.id }
        )

        val walletSummaries = sortedWallets.map { w ->
            AccountingEngine.calculateWalletSummary(
                wallet = w.copy(isArchived = false),
                transactions = db.transactions,
                transfers = db.transfers,
                loans = db.loans,
                loanActivities = db.loanActivities,
                adjustments = db.adjustments
            )
        }
        val activeSummaries = walletSummaries
        val archivedSummaries = emptyList<WalletBalanceSummary>()
        val totalBalance = AccountingEngine.roundCurrency(
            activeSummaries.filter {
                it.wallet.includeInTotalBalance &&
                    (it.wallet.currency.isBlank() || it.wallet.currency.equals("BDT", ignoreCase = true))
            }.sumOf { it.currentBalance }
        )
        val foreignCurrencyTotals = activeSummaries
            .filter {
                it.wallet.includeInTotalBalance &&
                    it.wallet.currency.isNotBlank() &&
                    !it.wallet.currency.equals("BDT", ignoreCase = true)
            }
            .groupBy { it.wallet.currency.trim().uppercase() }
            .map { (code, list) ->
                code to AccountingEngine.roundCurrency(list.sumOf { it.currentBalance })
            }

        val availableReportCurrencies = buildList {
            add("BDT")
            sortedWallets.mapNotNull { w ->
                w.currency.trim().uppercase().takeIf { it.isNotBlank() && it != "BDT" }
            }.distinct().forEach { add(it) }
        }
        val activeReportCurrency = if (requestedReportCurrency.uppercase() in availableReportCurrencies) {
            requestedReportCurrency.uppercase()
        } else {
            "BDT"
        }

        val loanSummaries = AccountingEngine.calculateLoanSummaries(
            loans = db.loans,
            loanActivities = db.loanActivities,
            wallets = sortedWallets
        )

        val ledger = AccountingEngine.buildUnifiedLedger(
            wallets = sortedWallets,
            categories = db.categories,
            transactions = db.transactions,
            transfers = db.transfers,
            loans = db.loans,
            loanActivities = db.loanActivities,
            adjustments = db.adjustments
        )

        val monthlyReport = AccountingEngine.calculateMonthlyReport(
            year = year,
            month = month,
            transactions = db.transactions,
            categories = db.categories,
            loans = db.loans,
            loanActivities = db.loanActivities,
            wallets = sortedWallets,
            reportCurrency = activeReportCurrency
        )

        val currentScreen = navStack.lastOrNull() ?: PocketScreen.Wallets
        val bottomTab = when (currentScreen) {
            is PocketScreen.Home, is PocketScreen.Wallets, is PocketScreen.WalletDetail, is PocketScreen.BalanceAdjustment -> BottomNavTab.BALANCES
            is PocketScreen.TransactionHistory -> BottomNavTab.RECENT_TRANSACTIONS
            is PocketScreen.Reports, is PocketScreen.MonthlyStatement -> BottomNavTab.REPORTS
            is PocketScreen.LoansManagement -> BottomNavTab.LOANS
            else -> BottomNavTab.BALANCES
        }

        PocketUiState(
            isLoading = false,
            settings = settings,
            isAppUnlocked = effectiveUnlocked,
            currentScreen = currentScreen,
            activeBottomTab = bottomTab,
            selectedYear = year,
            selectedMonth = month,
            selectedReportCurrency = activeReportCurrency,
            wallets = sortedWallets,
            walletSummaries = walletSummaries,
            activeWalletSummaries = activeSummaries,
            archivedWalletSummaries = archivedSummaries,
            totalAvailableBalance = totalBalance,
            foreignCurrencyTotals = foreignCurrencyTotals,
            categories = db.categories,
            transactions = db.transactions,
            transfers = db.transfers,
            loans = db.loans,
            loanActivities = db.loanActivities,
            loanSummaries = loanSummaries,
            adjustments = db.adjustments,
            recurringTransactions = db.recurringTransactions,
            savingsGoals = db.savingsGoals,
            pendingRecurringPayId = pendingRecurringId,
            pendingCloudConflict = cloudAuth.pendingConflict,
            unifiedLedger = ledger,
            monthlyReport = monthlyReport,
            statusMessage = message,
            googleAccountEmail = cloudAuth.email,
            googleAccountName = cloudAuth.displayName,
            googleAccountPhotoUrl = cloudAuth.photoUrl,
            isCloudSyncing = cloudAuth.isSyncing,
            lastCloudSyncStatus = cloudAuth.lastSyncStatus
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PocketUiState()
        )

    // --- Navigation ---
    fun navigateTo(screen: PocketScreen) {
        _navStack.update { current ->
            if (current.lastOrNull() == screen) current else current + screen
        }
    }

    fun switchBottomTab(tab: BottomNavTab) {
        val rootScreen = when (tab) {
            BottomNavTab.BALANCES -> PocketScreen.Wallets
            BottomNavTab.RECENT_TRANSACTIONS -> PocketScreen.TransactionHistory()
            BottomNavTab.REPORTS -> PocketScreen.Reports
            BottomNavTab.LOANS -> PocketScreen.LoansManagement
        }
        _navStack.value = listOf(rootScreen)
    }

    fun navigateBack(): Boolean {
        val current = _navStack.value
        return if (current.size > 1) {
            _navStack.value = current.dropLast(1)
            true
        } else if (current.firstOrNull() !is PocketScreen.Wallets && current.firstOrNull() !is PocketScreen.Home) {
            _navStack.value = listOf(PocketScreen.Wallets)
            true
        } else {
            false
        }
    }

    fun handleWidgetDeepLink(destination: String?, walletId: Long?, recurringId: Long? = null) {
        when (destination) {
            PocketWidgetUpdater.DEST_HOME -> _navStack.value = listOf(PocketScreen.Wallets)
            PocketWidgetUpdater.DEST_ADD_INCOME -> {
                _navStack.value = listOf(
                    PocketScreen.Wallets,
                    PocketScreen.TransactionForm(initialType = TransactionType.INCOME)
                )
            }
            PocketWidgetUpdater.DEST_ADD_EXPENSE -> {
                _navStack.value = listOf(
                    PocketScreen.Wallets,
                    PocketScreen.TransactionForm(initialType = TransactionType.EXPENSE)
                )
            }
            PocketWidgetUpdater.DEST_WALLET_DETAIL -> {
                if (walletId != null && walletId > 0L) {
                    _navStack.value = listOf(
                        PocketScreen.Wallets,
                        PocketScreen.WalletDetail(walletId)
                    )
                } else {
                    _navStack.value = listOf(PocketScreen.Wallets)
                }
            }
            "WALLETS" -> {
                _navStack.value = listOf(PocketScreen.Wallets)
            }
            "LOANS" -> {
                _navStack.value = listOf(PocketScreen.LoansManagement)
            }
            "RECURRING_PAY" -> {
                if (recurringId != null && recurringId > 0L) {
                    _pendingRecurringPayId.value = recurringId
                } else {
                    _navStack.value = listOf(PocketScreen.Wallets, PocketScreen.RecurringManagement)
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun selectReportCurrency(currencyCode: String) {
        _selectedReportCurrency.value = currencyCode.trim().uppercase().ifBlank { "BDT" }
    }

    // --- Month Selection ---
    fun selectPreviousMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        _selectedYear.value = cal.get(Calendar.YEAR)
        _selectedMonth.value = cal.get(Calendar.MONTH)
    }

    fun selectNextMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        _selectedYear.value = cal.get(Calendar.YEAR)
        _selectedMonth.value = cal.get(Calendar.MONTH)
    }

    // --- Onboarding & Sample Data ---
    fun completeOnboarding(
        currencyCode: String,
        currencySymbol: String,
        selectedWallets: List<Pair<StarterWalletOption, Double>>,
        loadSampleTransactions: Boolean,
        starterLoans: List<StarterLoanInput> = emptyList(),
        userName: String = "",
        profilePhotoUri: String = ""
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                currencyCode = "BDT",
                currencySymbol = "৳",
                selectedWallets = selectedWallets,
                loadSampleTransactions = loadSampleTransactions,
                starterLoans = starterLoans,
                userName = userName,
                profilePhotoUri = profilePhotoUri
            )
            _navStack.value = listOf(PocketScreen.Wallets)
            notifyWidgetsChanged()
        }
    }

    fun loadShowcaseSampleData() {
        viewModelScope.launch {
            val s = uiState.value.settings
            repository.seedShowcaseData(
                currencyCode = s.currencyCode,
                currencySymbol = s.currencySymbol
            )
            _statusMessage.value = "Loaded showcase wallets, transactions, transfers, and loans."
            notifyWidgetsChanged()
        }
    }

    // --- App Lock (with 30-second background grace period) ---
    fun unlockAppWithPin(pin: String): Boolean {
        val expected = uiState.value.settings.appLockPin
        return if (pin == expected) {
            lastBackgroundTimestampMillis = 0L
            _isAppUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun unlockAppWithBiometrics() {
        lastBackgroundTimestampMillis = 0L
        _isAppUnlocked.value = true
    }

    fun onAppBackgrounded(nowMillis: Long = System.currentTimeMillis()) {
        val settings = uiState.value.settings
        if (settings.appLockEnabled && settings.appLockPin.isNotBlank() && _isAppUnlocked.value) {
            lastBackgroundTimestampMillis = nowMillis
        }
    }

    fun onAppForegrounded(nowMillis: Long = System.currentTimeMillis()) {
        val settings = uiState.value.settings
        if (settings.appLockEnabled && settings.appLockPin.isNotBlank()) {
            val bgTime = lastBackgroundTimestampMillis
            if (bgTime > 0L && (nowMillis - bgTime) >= APP_LOCK_GRACE_PERIOD_MS) {
                _isAppUnlocked.value = false
            }
        }
        lastBackgroundTimestampMillis = 0L
    }

    // --- Wallet Actions ---
    fun saveWallet(wallet: WalletEntity, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.saveWallet(wallet)
            _statusMessage.value = "Wallet '${wallet.name}' saved."
            notifyWidgetsChanged()
            onSaved?.invoke()
        }
    }

    fun reorderWallet(walletId: Long, moveUp: Boolean) {
        viewModelScope.launch {
            repository.reorderWallet(walletId, moveUp)
            notifyWidgetsChanged()
        }
    }

    fun moveWalletOrder(walletId: Long, action: String) {
        viewModelScope.launch {
            repository.moveWalletOrder(walletId, action)
            val label = when (action.uppercase()) {
                "TOP" -> "Wallet pinned to top."
                "BOTTOM" -> "Wallet moved to bottom."
                "UP" -> "Wallet moved up."
                "DOWN" -> "Wallet moved down."
                else -> "Wallet order updated."
            }
            _statusMessage.value = label
            notifyWidgetsChanged()
        }
    }

    fun reorderWalletsByIds(orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderWalletsByIds(orderedIds)
            notifyWidgetsChanged()
            scheduleCloudPush()
        }
    }

    suspend fun canDirectlyDeleteWallet(walletId: Long): Boolean {
        return repository.canDirectlyDeleteWallet(walletId)
    }

    fun deleteOrMigrateWallet(walletId: Long, migrateToWalletId: Long?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteOrMigrateWallet(walletId, migrateToWalletId)
            _statusMessage.value = "Wallet removed safely."
            notifyWidgetsChanged()
            onDone()
        }
    }

    // --- Category Actions ---
    fun saveCategory(category: CategoryEntity, onSaved: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.saveCategory(category)
            _statusMessage.value = "Category '${category.name}' saved."
            scheduleCloudPush()
            onSaved?.invoke(id)
        }
    }

    fun updateCategoryMonthlyBudget(categoryId: Long, budget: Double) {
        val cat = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return
        val cleanBudget = AccountingEngine.roundCurrency(budget.coerceAtLeast(0.0))
        viewModelScope.launch {
            repository.saveCategory(cat.copy(monthlyBudget = cleanBudget))
            val symbol = uiState.value.settings.currencySymbol
            _statusMessage.value = if (cleanBudget > 0.0) {
                "Budget for '${cat.name}' set to ${AccountingEngine.formatAmount(cleanBudget, symbol)}."
            } else {
                "Budget for '${cat.name}' cleared."
            }
            scheduleCloudPush()
        }
    }

    fun reorderCategory(categoryId: Long, moveUp: Boolean) {
        viewModelScope.launch {
            repository.reorderCategory(categoryId, moveUp)
            scheduleCloudPush()
        }
    }

    suspend fun countTransactionsForCategory(categoryId: Long): Int {
        return repository.countTransactionsForCategory(categoryId)
    }

    fun deleteOrReassignCategory(categoryId: Long, reassignToCategoryId: Long?) {
        viewModelScope.launch {
            repository.deleteOrReassignCategory(categoryId, reassignToCategoryId)
            _statusMessage.value = "Category removed."
            scheduleCloudPush()
        }
    }

    // --- Transaction Actions ---
    fun saveTransaction(
        transaction: TransactionEntity,
        navigateBackOnSave: Boolean = true,
        recurringSetup: RecurringTransactionEntity? = null,
        serviceFeeAmount: Double = 0.0
    ) {
        viewModelScope.launch {
            repository.saveTransactionWithOptionalFee(
                transaction = transaction,
                serviceFeeAmount = serviceFeeAmount
            )
            if (recurringSetup != null) {
                repository.saveRecurringTransaction(recurringSetup)
                _statusMessage.value = "Transaction saved & recurring schedule created."
            } else {
                _statusMessage.value = "Transaction saved."
            }
            notifyWidgetsChanged()
            if (navigateBackOnSave) {
                navigateBack()
            }
        }
    }

    fun saveSplitTransactions(
        splitTransactions: List<TransactionEntity>,
        serviceFeeAmount: Double = 0.0,
        navigateBackOnSave: Boolean = true
    ) {
        if (splitTransactions.isEmpty()) return
        viewModelScope.launch {
            splitTransactions.forEachIndexed { index, tx ->
                repository.saveTransactionWithOptionalFee(
                    transaction = tx,
                    serviceFeeAmount = if (index == 0) serviceFeeAmount else 0.0
                )
            }
            _statusMessage.value = "Saved ${splitTransactions.size} split transactions."
            notifyWidgetsChanged()
            if (navigateBackOnSave) {
                navigateBack()
            }
        }
    }

    fun importCsvTransactions(
        importedTransactions: List<TransactionEntity>,
        navigateBackOnSave: Boolean = false
    ) {
        if (importedTransactions.isEmpty()) return
        viewModelScope.launch {
            importedTransactions.forEach { tx ->
                repository.saveTransaction(tx)
            }
            _statusMessage.value = "Imported ${importedTransactions.size} transactions from CSV."
            notifyWidgetsChanged()
            if (navigateBackOnSave) {
                navigateBack()
            }
        }
    }

    // --- Recurring Transaction Actions (Option A: Ask to Confirm) ---
    fun openRecurringPayDialog(recurringId: Long) {
        _pendingRecurringPayId.value = recurringId
    }

    fun dismissRecurringPayDialog() {
        _pendingRecurringPayId.value = null
    }

    fun saveRecurringTransaction(
        item: RecurringTransactionEntity,
        onSaved: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.saveRecurringTransaction(item)
            _statusMessage.value = "Recurring '${item.title}' saved."
            notifyWidgetsChanged()
            onSaved?.invoke()
        }
    }

    fun toggleRecurringActive(item: RecurringTransactionEntity) {
        viewModelScope.launch {
            repository.saveRecurringTransaction(item.copy(isActive = !item.isActive))
            _statusMessage.value = if (item.isActive) {
                "'${item.title}' paused."
            } else {
                "'${item.title}' resumed."
            }
            notifyWidgetsChanged()
        }
    }

    fun deleteRecurringTransaction(id: Long) {
        viewModelScope.launch {
            if (_pendingRecurringPayId.value == id) {
                _pendingRecurringPayId.value = null
            }
            repository.deleteRecurringTransaction(id)
            _statusMessage.value = "Recurring schedule deleted."
            notifyWidgetsChanged()
        }
    }

    fun snoozeRecurringToTomorrow(recurringId: Long) {
        viewModelScope.launch {
            val updated = repository.snoozeRecurringToTomorrow(recurringId)
            if (_pendingRecurringPayId.value == recurringId) {
                _pendingRecurringPayId.value = null
            }
            if (updated != null) {
                _statusMessage.value = "Will remind tomorrow for '${updated.title}'."
            }
            notifyWidgetsChanged()
        }
    }

    fun confirmRecurringPayment(
        recurringId: Long,
        chosenWalletId: Long,
        finalAmount: Double,
        note: String = "",
        serviceFeeAmount: Double = 0.0
    ) {
        viewModelScope.launch {
            val recurring = uiState.value.recurringTransactions.firstOrNull { it.id == recurringId }
            val wallet = uiState.value.wallets.firstOrNull { it.id == chosenWalletId }
            val walletName = wallet?.name ?: "Wallet"
            val txId = repository.confirmAndPayRecurringTransaction(
                recurringId = recurringId,
                chosenWalletId = chosenWalletId,
                finalAmount = finalAmount,
                note = note,
                serviceFeeAmount = serviceFeeAmount
            )
            _pendingRecurringPayId.value = null
            if (txId != 0L) {
                val symbol = AccountingEngine.currencySymbolForCode(wallet?.currency ?: "BDT")
                val formatted = AccountingEngine.formatMoney(finalAmount, symbol)
                val verb = if (recurring?.type == TransactionType.INCOME.name) "Received" else "Paid"
                _statusMessage.value = "$verb $formatted for '${recurring?.title ?: "Recurring"}' via $walletName."
            }
            notifyWidgetsChanged()
        }
    }

    fun duplicateTransaction(transactionId: Long) {
        viewModelScope.launch {
            val orig = uiState.value.transactions.firstOrNull { it.id == transactionId } ?: return@launch
            repository.saveTransaction(
                orig.copy(
                    id = 0L,
                    date = System.currentTimeMillis(),
                    transferId = null,
                    loanActivityId = null
                )
            )
            _statusMessage.value = "Transaction duplicated."
            notifyWidgetsChanged()
        }
    }

    fun deleteLedgerEntry(entry: UnifiedLedgerEntry) {
        viewModelScope.launch {
            when (entry.kind) {
                LedgerEntryKind.INCOME, LedgerEntryKind.EXPENSE, LedgerEntryKind.EXPENSE_REVERSAL -> {
                    val tx = uiState.value.transactions.firstOrNull { it.id == entry.rawId }
                    if (tx?.transferId != null) {
                        repository.deleteTransfer(tx.transferId)
                        _statusMessage.value = "Transfer and associated fee deleted."
                    } else if (tx?.loanActivityId != null) {
                        repository.deleteLoanActivity(tx.loanActivityId)
                        _statusMessage.value = "Loan repayment and interest deleted."
                    } else {
                        repository.deleteTransaction(entry.rawId)
                        _statusMessage.value = "Transaction deleted."
                    }
                }
                LedgerEntryKind.TRANSFER -> {
                    repository.deleteTransfer(entry.rawId)
                    _statusMessage.value = "Transfer reversed and deleted."
                }
                LedgerEntryKind.LOAN_PRINCIPAL -> {
                    repository.deleteLoanActivity(entry.rawId)
                    _statusMessage.value = "Loan activity deleted."
                }
                LedgerEntryKind.ADJUSTMENT -> {
                    val adj = uiState.value.adjustments.firstOrNull { it.id == entry.rawId }
                    if (adj != null) {
                        repository.reverseBalanceAdjustment(adj)
                        _statusMessage.value = "Balance adjustment reversed."
                    }
                }
            }
            notifyWidgetsChanged()
        }
    }

    fun bulkDeleteLedgerEntries(entries: List<UnifiedLedgerEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repository.bulkDeleteLedgerEntries(entries)
            _statusMessage.value = "${entries.size} entries deleted."
            notifyWidgetsChanged()
        }
    }

    fun bulkUpdateTransactionsCategory(transactionIds: List<Long>, newCategoryId: Long) {
        if (transactionIds.isEmpty()) return
        viewModelScope.launch {
            repository.bulkUpdateTransactionsCategory(transactionIds, newCategoryId)
            val catName = uiState.value.categories.firstOrNull { it.id == newCategoryId }?.name ?: "Category"
            _statusMessage.value = "Updated ${transactionIds.size} transactions to '$catName'."
            notifyWidgetsChanged()
        }
    }

    // --- Savings Goals (Virtual Pots / Wishlists) ---
    fun saveSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.saveSavingsGoal(goal)
            _statusMessage.value = "Savings goal '${goal.name}' saved."
            scheduleCloudPush()
        }
    }

    fun addOrWithdrawSavingsGoal(
        goal: SavingsGoalEntity,
        delta: Double,
        transferWalletId: Long? = null
    ) {
        viewModelScope.launch {
            val nextSaved = AccountingEngine.roundCurrency((goal.savedAmount + delta).coerceAtLeast(0.0))
            repository.saveSavingsGoal(goal.copy(savedAmount = nextSaved))
            val absAmount = AccountingEngine.roundCurrency(kotlin.math.abs(delta))
            if (transferWalletId != null && absAmount > 0.0) {
                val isAddingToGoal = delta >= 0.0
                val txType = if (isAddingToGoal) TransactionType.EXPENSE else TransactionType.INCOME
                val categories = uiState.value.categories
                val matchedCategory = categories.firstOrNull {
                    it.type == txType.name && (
                        it.name.contains("saving", ignoreCase = true) ||
                            it.name.contains("goal", ignoreCase = true) ||
                            it.name.contains("other", ignoreCase = true)
                        )
                } ?: categories.firstOrNull { it.type == txType.name }
                    ?: categories.firstOrNull()
                if (matchedCategory != null) {
                    repository.saveTransaction(
                        TransactionEntity(
                            amount = absAmount,
                            type = txType.name,
                            categoryId = matchedCategory.id,
                            walletId = transferWalletId,
                            date = System.currentTimeMillis(),
                            note = if (isAddingToGoal) {
                                "Savings Goal Deposit: ${goal.name}"
                            } else {
                                "Savings Goal Withdrawal: ${goal.name}"
                            },
                            tags = "SavingsGoal"
                        )
                    )
                }
            }
            val verb = if (delta >= 0) "Added to" else "Withdrawn from"
            val walletSuffix = if (transferWalletId != null) {
                val wName = uiState.value.wallets.firstOrNull { it.id == transferWalletId }?.name ?: "Wallet"
                if (delta >= 0) " (deducted from $wName)" else " (credited to $wName)"
            } else ""
            _statusMessage.value = "$verb '${goal.name}'$walletSuffix."
            notifyWidgetsChanged()
        }
    }

    fun deleteSavingsGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(id)
            _statusMessage.value = "Savings goal removed."
            scheduleCloudPush()
        }
    }

    // --- Transfer Actions ---
    fun saveTransfer(
        transfer: TransferEntity,
        sourceWalletName: String,
        destWalletName: String,
        navigateBackOnSave: Boolean = true
    ) {
        viewModelScope.launch {
            repository.saveTransfer(transfer, sourceWalletName, destWalletName)
            _statusMessage.value = "Transfer recorded."
            notifyWidgetsChanged()
            if (navigateBackOnSave) {
                navigateBack()
            }
        }
    }

    // --- Loan Actions ---
    fun createLoan(loan: LoanEntity) {
        viewModelScope.launch {
            repository.createLoan(loan)
            _statusMessage.value = "Loan record created."
            notifyWidgetsChanged()
        }
    }

    fun updateLoanDeadline(loan: LoanEntity, dueDate: Long?) {
        viewModelScope.launch {
            repository.updateLoanMetadata(loan.copy(dueDate = dueDate))
            _statusMessage.value = "Loan deadline updated."
            notifyWidgetsChanged()
        }
    }

    fun updateLoanAttachment(loan: LoanEntity, newAttachmentUri: String) {
        viewModelScope.launch {
            repository.updateLoanMetadata(loan.copy(attachmentUri = newAttachmentUri))
            _statusMessage.value = if (newAttachmentUri.isBlank()) {
                "Loan slip photo removed."
            } else {
                "Loan slip photo updated."
            }
            notifyWidgetsChanged()
        }
    }

    fun recordLoanRepayment(loan: LoanEntity, activity: LoanActivityEntity) {
        viewModelScope.launch {
            repository.recordLoanRepayment(loan, activity)
            _statusMessage.value = "Repayment recorded."
            notifyWidgetsChanged()
        }
    }

    fun deleteLoanActivity(activityId: Long) {
        viewModelScope.launch {
            repository.deleteLoanActivity(activityId)
            _statusMessage.value = "Loan entry removed."
            notifyWidgetsChanged()
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch {
            repository.deleteLoan(loanId)
            _statusMessage.value = "Loan deleted."
            notifyWidgetsChanged()
        }
    }

    // --- Balance Adjustment Actions ---
    fun createBalanceAdjustment(
        walletId: Long,
        actualBalance: Double,
        calculatedBalanceBefore: Double,
        date: Long,
        reason: String,
        note: String,
        navigateBackOnSave: Boolean = false
    ) {
        viewModelScope.launch {
            val diff = AccountingEngine.roundCurrency(actualBalance - calculatedBalanceBefore)
            repository.createBalanceAdjustment(
                BalanceAdjustmentEntity(
                    walletId = walletId,
                    actualBalance = AccountingEngine.roundCurrency(actualBalance),
                    calculatedBalanceBefore = AccountingEngine.roundCurrency(calculatedBalanceBefore),
                    difference = diff,
                    date = date,
                    reason = reason,
                    note = note
                )
            )
            _statusMessage.value = "Balance reconciled (${if (diff >= 0) "+" else ""}$diff)."
            notifyWidgetsChanged()
            if (navigateBackOnSave) {
                navigateBack()
            }
        }
    }

    fun toggleReverseAdjustment(adjustment: BalanceAdjustmentEntity) {
        viewModelScope.launch {
            repository.reverseBalanceAdjustment(adjustment)
            _statusMessage.value = if (adjustment.isReversed) "Adjustment restored." else "Adjustment reversed."
            notifyWidgetsChanged()
        }
    }

    // --- Settings & Backup Actions ---
    fun updateSettings(settings: AppSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(settings)
            if (settings.appLockEnabled && settings.appLockPin.isNotBlank()) {
                _isAppUnlocked.value = true
            }
            notifyWidgetsChanged()
        }
    }

    fun applyProfilePhotoPath(savedPath: String) {
        viewModelScope.launch {
            repository.updateProfilePhotoUri(savedPath)
            scheduleCloudPush()
        }
    }

    fun applyCustomWallpaperPath(savedPath: String) {
        viewModelScope.launch {
            repository.updateCustomBackgroundUri(savedPath)
            _statusMessage.value = "Custom background wallpaper applied."
            notifyWidgetsChanged()
        }
    }

    fun removeCustomWallpaper() {
        viewModelScope.launch {
            repository.updateCustomBackgroundUri("")
            _statusMessage.value = "Background reset to Carbon Fiber theme."
            notifyWidgetsChanged()
        }
    }

    fun updateBackgroundOpacity(opacity: Float) {
        viewModelScope.launch {
            repository.updateBackgroundOpacity(opacity.coerceIn(0.0f, 1.0f))
            scheduleCloudPush()
        }
    }

    fun toggleBalancePrivacy() {
        val current = uiState.value.settings
        viewModelScope.launch {
            repository.updateSettings(current.copy(hideBalancesPrivacy = !current.hideBalancesPrivacy))
            notifyWidgetsChanged()
        }
    }

    fun updateMasterMonthlyBudget(budget: Double?) {
        val current = uiState.value.settings
        val cleanBudget = AccountingEngine.roundCurrency((budget ?: 0.0).coerceAtLeast(0.0))
        viewModelScope.launch {
            repository.updateSettings(current.copy(masterMonthlyBudget = cleanBudget))
            _statusMessage.value = if (cleanBudget > 0.0) {
                "Master monthly spending budget set to ${AccountingEngine.formatAmount(cleanBudget, current.currencySymbol)}."
            } else {
                "Master monthly spending budget cleared."
            }
            scheduleCloudPush()
        }
    }

    fun updateNotificationSettings(
        context: Context,
        loanDeadlineEnabled: Boolean = uiState.value.settings.loanDeadlineNotificationEnabled,
        dailyReminderEnabled: Boolean = uiState.value.settings.dailyExpenseReminderEnabled,
        dailyHour: Int = uiState.value.settings.dailyReminderHour,
        dailyMinute: Int = uiState.value.settings.dailyReminderMinute
    ) {
        val appCtx = context.applicationContext
        val updated = uiState.value.settings.copy(
            loanDeadlineNotificationEnabled = loanDeadlineEnabled,
            dailyExpenseReminderEnabled = dailyReminderEnabled,
            dailyReminderHour = dailyHour.coerceIn(0, 23),
            dailyReminderMinute = dailyMinute.coerceIn(0, 59)
        )
        viewModelScope.launch {
            repository.updateSettings(updated)
            scheduleCloudPush()
            withContext(Dispatchers.IO) {
                PocketNotificationManager.scheduleAllReminders(
                    context = appCtx,
                    loanDeadlineEnabled = updated.loanDeadlineNotificationEnabled,
                    dailyReminderEnabled = updated.dailyExpenseReminderEnabled,
                    dailyHour = updated.dailyReminderHour,
                    dailyMinute = updated.dailyReminderMinute
                )
                PocketNotificationManager.rescheduleDailyLogReminder(
                    context = appCtx,
                    enabled = updated.dailyExpenseReminderEnabled,
                    hour = updated.dailyReminderHour,
                    minute = updated.dailyReminderMinute
                )
                PocketNotificationManager.syncLoanNotifications(appCtx)
                PocketNotificationManager.syncRecurringNotifications(appCtx)
                PocketNotificationManager.syncCreditCardNotifications(appCtx)
            }
            val timeFormatted = PocketNotificationManager.formatReminderTime(updated.dailyReminderHour, updated.dailyReminderMinute)
            _statusMessage.value = "Notification preferences updated (Daily log reminder: $timeFormatted)."
        }
    }

    fun syncAndTestNotifications(context: Context) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PocketNotificationManager.syncLoanNotifications(appCtx)
                PocketNotificationManager.syncRecurringNotifications(appCtx)
                PocketNotificationManager.syncCreditCardNotifications(appCtx)
            }
            _statusMessage.value = "Loan, card & recurring bill reminders synced."
        }
    }

    fun resetAllNumericalDataToZero(context: Context) {
        val appCtx = context.applicationContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.resetAllFinancialDataToZero()
                PocketNotificationManager.cancelAllLoanNotifications(appCtx)
            }
            notifyWidgetsChanged()
            _statusMessage.value = "All income, expenses, loans, transfers, and wallet balances have been reset to 0."
        }
    }

    suspend fun generateBackupJson(passphrase: String? = null): String {
        val payload = repository.buildBackupPayload()
        return BackupManager.exportToJson(payload, passphrase)
    }

    fun restoreFromValidatedBackup(payload: PocketBackupPayload, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.restoreBackupPayload(payload)
            _statusMessage.value = "Backup restored (${payload.transactions.size} transactions, ${payload.wallets.size} wallets)."
            notifyWidgetsChanged()
            onComplete()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val db = PocketDatabase.getDatabase(appCtx)
            val repo = PocketRepository(db.pocketDao())
            return PocketViewModel(repo, appCtx) as T
        }
    }
}
