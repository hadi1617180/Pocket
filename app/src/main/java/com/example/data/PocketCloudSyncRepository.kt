package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
) {
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()

    val providerInfoArray = JSONArray()
    currentUser?.providerData?.forEach { profile ->
        val providerObj = JSONObject().apply {
            put("providerId", profile.providerId)
            put("displayName", profile.displayName)
            put("email", profile.email)
            put("photoUrl", profile.photoUrl?.toString())
        }
        providerInfoArray.put(providerObj)
    }

    val authObj = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("isAnonymous", currentUser?.isAnonymous)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", providerInfoArray)
    }

    val errInfo = JSONObject().apply {
        put("error", exception.message ?: "Unknown Firestore error")
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authObj)
    }

    Log.e("FirestoreError", errInfo.toString(), exception)
    throw exception
}

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val themeMode: String = "DARK",
    val defaultWalletId: Long? = null,
    val warnOnNegativeBalance: Boolean = true,
    val loanDeadlineNotificationEnabled: Boolean = true,
    val dailyExpenseReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val backgroundOpacity: Float = 0.20f,
    val accentColorHex: String = "#F15A24",
    val boxColorHex: String = "#2A2A2A",
    val boxOpacity: Float = 0.50f,
    val appLanguage: String = "EN",
    val dateFormatPattern: String = "dd MMM yyyy",
    val masterMonthlyBudget: Double = 0.0,
    val hideBalancesPrivacy: Boolean = false,
    val subscriptionTier: String = "FREE",
    val subscriptionExpiresAt: Long? = null,
    val activationCode: String = "",
    val hasCompletedOnboarding: Boolean = true,
    val syncVersion: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudWalletDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val entityId: Long = 0L,
    val name: String = "",
    val type: String = WalletType.CASH.name,
    val accountHint: String = "",
    val openingBalance: Double = 0.0,
    val openingDate: Long = 0L,
    val currency: String = "BDT",
    val iconName: String = "CASH",
    val colorHex: String = "#2E9E5B",
    val sortOrder: Int = 0,
    @get:PropertyName("isArchived") @set:PropertyName("isArchived")
    var isArchived: Boolean = false,
    val includeInTotalBalance: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudAccountSnapshot(
    val profile: UserProfileDoc,
    val payload: PocketBackupPayload
)

class PocketCloudSyncRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- UserProfile Operations ---

    suspend fun saveUserProfile(
        userId: String,
        profile: UserProfileDoc
    ) {
        val docPath = "users/$userId"
        val docRef = db.collection("users").document(userId)
        try {
            val existingSnap = docRef.get().await()
            val createdAtValue: Any = if (existingSnap.exists()) {
                existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            val safeDisplayName = profile.displayName.trim().ifBlank { "Pocket User" }.take(100)
            val data = mutableMapOf<String, Any?>(
                "userId" to userId,
                "displayName" to safeDisplayName,
                "email" to profile.email.take(200),
                "photoUrl" to profile.photoUrl.take(1000),
                "currencyCode" to profile.currencyCode.ifBlank { "BDT" }.take(10),
                "currencySymbol" to profile.currencySymbol.ifBlank { "৳" }.take(10),
                "themeMode" to (if (profile.themeMode in listOf("DARK", "LIGHT", "SYSTEM")) profile.themeMode else "DARK"),
                "defaultWalletId" to profile.defaultWalletId,
                "warnOnNegativeBalance" to profile.warnOnNegativeBalance,
                "loanDeadlineNotificationEnabled" to profile.loanDeadlineNotificationEnabled,
                "dailyExpenseReminderEnabled" to profile.dailyExpenseReminderEnabled,
                "dailyReminderHour" to profile.dailyReminderHour.coerceIn(0, 23),
                "dailyReminderMinute" to profile.dailyReminderMinute.coerceIn(0, 59),
                "backgroundOpacity" to profile.backgroundOpacity.coerceIn(0.0f, 1.0f).toDouble(),
                "accentColorHex" to profile.accentColorHex.ifBlank { "#F15A24" }.take(20),
                "boxColorHex" to profile.boxColorHex.take(20),
                "boxOpacity" to profile.boxOpacity.coerceIn(0.15f, 1.0f).toDouble(),
                "appLanguage" to (if (profile.appLanguage.uppercase() == "BN") "BN" else "EN"),
                "dateFormatPattern" to "dd MMM yyyy",
                "masterMonthlyBudget" to profile.masterMonthlyBudget.coerceAtLeast(0.0),
                "hideBalancesPrivacy" to profile.hideBalancesPrivacy,
                "subscriptionTier" to (if (profile.subscriptionTier in listOf("FREE", "TRIAL_3_DAYS", "LIFETIME")) profile.subscriptionTier else "FREE"),
                "subscriptionExpiresAt" to profile.subscriptionExpiresAt,
                "activationCode" to profile.activationCode.take(100),
                "hasCompletedOnboarding" to profile.hasCompletedOnboarding,
                "syncVersion" to profile.syncVersion.coerceAtLeast(0L),
                "createdAt" to createdAtValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
        }
    }

    suspend fun updateDisplayNameAndPhoto(
        userId: String,
        displayName: String,
        photoUrl: String? = null
    ) {
        val docPath = "users/$userId"
        val docRef = db.collection("users").document(userId)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                saveUserProfile(
                    userId = userId,
                    profile = UserProfileDoc(
                        userId = userId,
                        displayName = displayName.trim().ifBlank { "Pocket User" },
                        photoUrl = photoUrl ?: ""
                    )
                )
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to displayName.trim().ifBlank { "Pocket User" }.take(100),
                    "syncVersion" to ((existing.getLong("syncVersion") ?: 1L) + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) {
                    updates["photoUrl"] = photoUrl.take(1000)
                }
                docRef.update(updates).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
        }
    }

    suspend fun getUserProfile(userId: String): UserProfileDoc? {
        val docPath = "users/$userId"
        return try {
            val snap = db.collection("users").document(userId).get().await()
            if (!snap.exists()) null else parseUserProfile(snap)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            null
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> = callbackFlow {
        val docPath = "users/$userId"
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    runCatching { handleFirestoreError(error, OperationType.GET, docPath) }
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(parseUserProfile(snapshot))
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Wallet Subcollection Operations ---

    suspend fun saveCloudWallet(userId: String, wallet: WalletEntity) {
        val docId = wallet.id.coerceAtLeast(1L).toString()
        val docPath = "users/$userId/wallets/$docId"
        val docRef = db.collection("users").document(userId).collection("wallets").document(docId)
        try {
            val existing = docRef.get().await()
            val createdAtValue: Any = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            docRef.set(walletToMap(userId, wallet, createdAtValue)).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
        }
    }

    suspend fun getCloudWallets(userId: String): List<WalletEntity> {
        val path = "users/$userId/wallets"
        return try {
            val snap = db.collection("users").document(userId).collection("wallets").get().await()
            snap.documents.mapNotNull { parseWallet(it) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    suspend fun deleteCloudWallet(userId: String, walletId: Long) {
        val docId = walletId.toString()
        val path = "users/$userId/wallets/$docId"
        try {
            db.collection("users").document(userId).collection("wallets").document(docId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    // --- Category Subcollection Operations ---

    suspend fun saveCloudCategory(userId: String, category: CategoryEntity) {
        val docId = category.id.coerceAtLeast(1L).toString()
        val path = "users/$userId/categories/$docId"
        val docRef = db.collection("users").document(userId).collection("categories").document(docId)
        try {
            val existing = docRef.get().await()
            val createdAtValue: Any = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            docRef.set(categoryToMap(userId, category, createdAtValue)).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getCloudCategories(userId: String): List<CategoryEntity> {
        val path = "users/$userId/categories"
        return try {
            val snap = db.collection("users").document(userId).collection("categories").get().await()
            snap.documents.mapNotNull { parseCategory(it) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    // --- Transaction Subcollection Operations ---

    suspend fun saveCloudTransaction(userId: String, tx: TransactionEntity) {
        val docId = tx.id.coerceAtLeast(1L).toString()
        val path = "users/$userId/transactions/$docId"
        val docRef = db.collection("users").document(userId).collection("transactions").document(docId)
        try {
            val existing = docRef.get().await()
            val createdAtValue: Any = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            docRef.set(transactionToMap(userId, tx, createdAtValue)).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getCloudTransactions(userId: String): List<TransactionEntity> {
        val path = "users/$userId/transactions"
        return try {
            val snap = db.collection("users").document(userId).collection("transactions").get().await()
            snap.documents.mapNotNull { parseTransaction(it) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    suspend fun deleteCloudTransaction(userId: String, transactionId: Long) {
        val docId = transactionId.toString()
        val path = "users/$userId/transactions/$docId"
        try {
            db.collection("users").document(userId).collection("transactions").document(docId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    // --- Full Account Sync (Push & Pull) ---

    suspend fun pushLocalPayloadToCloud(
        userId: String,
        email: String,
        googleDisplayName: String,
        googlePhotoUrl: String,
        payload: PocketBackupPayload,
        syncVersion: Long = System.currentTimeMillis()
    ) {
        val userRef = db.collection("users").document(userId)
        val settings = payload.settings
        val resolvedName = settings.userName.trim().ifBlank {
            googleDisplayName.trim().ifBlank { "Pocket User" }
        }
        val resolvedPhoto = when {
            settings.profilePhotoUri.startsWith("http://") ||
                settings.profilePhotoUri.startsWith("https://") ||
                settings.profilePhotoUri.startsWith("data:image/") -> settings.profilePhotoUri.take(250000)
            googlePhotoUrl.isNotBlank() -> googlePhotoUrl
            else -> ""
        }

        // 1. Save UserProfile document
        saveUserProfile(
            userId = userId,
            profile = UserProfileDoc(
                userId = userId,
                displayName = resolvedName,
                email = email,
                photoUrl = resolvedPhoto,
                currencyCode = "BDT",
                currencySymbol = "৳",
                themeMode = settings.themeMode,
                defaultWalletId = settings.defaultWalletId,
                warnOnNegativeBalance = settings.warnOnNegativeBalance,
                loanDeadlineNotificationEnabled = settings.loanDeadlineNotificationEnabled,
                dailyExpenseReminderEnabled = settings.dailyExpenseReminderEnabled,
                dailyReminderHour = settings.dailyReminderHour,
                dailyReminderMinute = settings.dailyReminderMinute,
                backgroundOpacity = settings.backgroundOpacity,
                accentColorHex = settings.accentColorHex.ifBlank { "#F15A24" },
                boxColorHex = settings.boxColorHex,
                boxOpacity = settings.boxOpacity.coerceIn(0.15f, 1.0f),
                appLanguage = settings.appLanguage.ifBlank { "EN" },
                dateFormatPattern = "dd MMM yyyy",
                masterMonthlyBudget = settings.masterMonthlyBudget.coerceAtLeast(0.0),
                hideBalancesPrivacy = settings.hideBalancesPrivacy,
                subscriptionTier = settings.subscriptionTier,
                subscriptionExpiresAt = settings.subscriptionExpiresAt,
                activationCode = settings.activationCode,
                hasCompletedOnboarding = settings.hasCompletedOnboarding,
                syncVersion = syncVersion
            )
        )

        // 2. Sync each subcollection
        syncSubcollection(
            userId = userId,
            collectionName = "wallets",
            items = payload.wallets,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> walletToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "categories",
            items = payload.categories,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> categoryToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "transactions",
            items = payload.transactions,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> transactionToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "transfers",
            items = payload.transfers,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> transferToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "loans",
            items = payload.loans,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> loanToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "loan_activities",
            items = payload.loanActivities,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> loanActivityToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "balance_adjustments",
            items = payload.adjustments,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> balanceAdjustmentToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "recurring_transactions",
            items = payload.recurringTransactions,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> recurringToMap(userId, item, createdAt) }
        )
        syncSubcollection(
            userId = userId,
            collectionName = "savings_goals",
            items = payload.savingsGoals,
            idSelector = { it.id.coerceAtLeast(1L).toString() },
            mapBuilder = { item, createdAt -> savingsGoalToMap(userId, item, createdAt) }
        )
    }

    suspend fun pullCloudPayloadIfPresent(
        userId: String,
        localSettingsFallback: AppSettingsEntity = AppSettingsEntity()
    ): CloudAccountSnapshot? {
        val userDocPath = "users/$userId"
        val profile = getUserProfile(userId) ?: return null

        return try {
            val userRef = db.collection("users").document(userId)
            val wallets = userRef.collection("wallets").get().await().documents.mapNotNull { parseWallet(it) }
            val categories = userRef.collection("categories").get().await().documents.mapNotNull { parseCategory(it) }
            val transactions = userRef.collection("transactions").get().await().documents.mapNotNull { parseTransaction(it) }
            val transfers = userRef.collection("transfers").get().await().documents.mapNotNull { parseTransfer(it) }
            val loans = userRef.collection("loans").get().await().documents.mapNotNull { parseLoan(it) }
            val loanActivities = userRef.collection("loan_activities").get().await().documents.mapNotNull { parseLoanActivity(it) }
            val adjustments = userRef.collection("balance_adjustments").get().await().documents.mapNotNull { parseBalanceAdjustment(it) }
            val recurringTransactions = userRef.collection("recurring_transactions").get().await().documents.mapNotNull { parseRecurring(it) }
            val savingsGoals = userRef.collection("savings_goals").get().await().documents.mapNotNull { parseSavingsGoal(it) }

            val hasCustomLocalPhoto = localSettingsFallback.profilePhotoUri.isNotBlank() &&
                !localSettingsFallback.profilePhotoUri.startsWith("http://", ignoreCase = true) &&
                !localSettingsFallback.profilePhotoUri.startsWith("https://", ignoreCase = true) &&
                (localSettingsFallback.profilePhotoUri.startsWith("data:image/") ||
                    java.io.File(localSettingsFallback.profilePhotoUri.removePrefix("CUSTOM_URI:")).exists())
            val resolvedPhotoUri = if (hasCustomLocalPhoto) {
                localSettingsFallback.profilePhotoUri
            } else {
                profile.photoUrl.ifBlank { localSettingsFallback.profilePhotoUri }
            }

            val remoteTier = profile.subscriptionTier.ifBlank { "FREE" }
            val localTier = localSettingsFallback.subscriptionTier.ifBlank { "FREE" }
            val useLocalSubscription = remoteTier.equals("FREE", ignoreCase = true) && !localTier.equals("FREE", ignoreCase = true)

            val mergedSettings = localSettingsFallback.copy(
                id = 1,
                hasCompletedOnboarding = profile.hasCompletedOnboarding,
                userName = profile.displayName,
                profilePhotoUri = resolvedPhotoUri,
                currencyCode = "BDT",
                currencySymbol = "৳",
                defaultWalletId = profile.defaultWalletId,
                warnOnNegativeBalance = profile.warnOnNegativeBalance,
                themeMode = profile.themeMode,
                loanDeadlineNotificationEnabled = profile.loanDeadlineNotificationEnabled,
                dailyExpenseReminderEnabled = profile.dailyExpenseReminderEnabled,
                dailyReminderHour = profile.dailyReminderHour,
                dailyReminderMinute = profile.dailyReminderMinute,
                backgroundOpacity = profile.backgroundOpacity.coerceIn(0.0f, 1.0f),
                accentColorHex = profile.accentColorHex.ifBlank { "#F15A24" },
                boxColorHex = profile.boxColorHex,
                boxOpacity = profile.boxOpacity.coerceIn(0.15f, 1.0f),
                dateFormatPattern = "dd MMM yyyy",
                masterMonthlyBudget = profile.masterMonthlyBudget.coerceAtLeast(0.0),
                hideBalancesPrivacy = profile.hideBalancesPrivacy,
                subscriptionTier = if (useLocalSubscription) localTier else remoteTier,
                subscriptionExpiresAt = if (useLocalSubscription) localSettingsFallback.subscriptionExpiresAt else profile.subscriptionExpiresAt,
                activationCode = if (useLocalSubscription) localSettingsFallback.activationCode else profile.activationCode,
                appLanguage = profile.appLanguage.ifBlank { localSettingsFallback.appLanguage.ifBlank { "EN" } }
            )

            CloudAccountSnapshot(
                profile = profile,
                payload = PocketBackupPayload(
                    formatVersion = "pocket-backup-v1",
                    exportedAtMillis = System.currentTimeMillis(),
                    wallets = wallets,
                    categories = categories.ifEmpty { PocketRepository.defaultCategories() },
                    transactions = transactions,
                    transfers = transfers,
                    loans = loans,
                    loanActivities = loanActivities,
                    adjustments = adjustments,
                    recurringTransactions = recurringTransactions,
                    savingsGoals = savingsGoals,
                    settings = mergedSettings
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userDocPath)
            null
        }
    }

    suspend fun deleteEntireCloudAccount(userId: String) {
        val userDocPath = "users/$userId"
        val subcollections = listOf(
            "wallets",
            "categories",
            "transactions",
            "transfers",
            "loans",
            "loan_activities",
            "balance_adjustments",
            "recurring_transactions",
            "savings_goals"
        )
        for (col in subcollections) {
            val subPath = "$userDocPath/$col"
            try {
                val colRef = db.collection("users").document(userId).collection(col)
                val snapshot = colRef.get().await()
                for (doc in snapshot.documents) {
                    runCatching { doc.reference.delete().await() }
                }
            } catch (e: Exception) {
                Log.w("PocketCloudSync", "Failed to clean subcollection $subPath: ${e.message}")
            }
        }
        try {
            db.collection("users").document(userId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDocPath)
        }
    }

    private suspend fun <T> syncSubcollection(
        userId: String,
        collectionName: String,
        items: List<T>,
        idSelector: (T) -> String,
        mapBuilder: (T, Any) -> Map<String, Any?>
    ) {
        val path = "users/$userId/$collectionName"
        val colRef = db.collection("users").document(userId).collection(collectionName)
        try {
            val existingDocs = colRef.get().await().documents.associateBy { it.id }
            val incomingIds = items.map { idSelector(it) }.toSet()

            // Delete documents removed locally
            for ((existingId, snap) in existingDocs) {
                if (existingId !in incomingIds) {
                    snap.reference.delete().await()
                }
            }

            // Upsert current documents preserving immutable createdAt
            for (item in items) {
                val docId = idSelector(item)
                val existingCreatedAt: Any = existingDocs[docId]?.getTimestamp("createdAt")
                    ?: FieldValue.serverTimestamp()
                colRef.document(docId).set(mapBuilder(item, existingCreatedAt)).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    // --- Serialization Helpers ---

    private fun walletToMap(userId: String, w: WalletEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to w.id.coerceAtLeast(1L),
        "name" to w.name.trim().ifBlank { "Wallet" }.take(100),
        "type" to w.type.ifBlank { WalletType.CASH.name }.take(40),
        "accountHint" to w.accountHint.take(200),
        "openingBalance" to w.openingBalance,
        "openingDate" to w.openingDate.coerceAtLeast(0L),
        "currency" to w.currency.ifBlank { "BDT" }.take(10),
        "iconName" to w.iconName.ifBlank { "CASH" }.take(250000),
        "colorHex" to w.colorHex.ifBlank { "#2E9E5B" }.take(20),
        "sortOrder" to w.sortOrder,
        "isArchived" to w.isArchived,
        "includeInTotalBalance" to w.includeInTotalBalance,
        "creditLimit" to w.creditLimit.coerceAtLeast(0.0),
        "statementDay" to w.statementDay.coerceIn(0, 31),
        "paymentDueDay" to w.paymentDueDay.coerceIn(0, 31),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun categoryToMap(userId: String, c: CategoryEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to c.id.coerceAtLeast(1L),
        "name" to c.name.trim().ifBlank { "Category" }.take(100),
        "type" to (if (c.type == "INCOME") "INCOME" else "EXPENSE"),
        "iconName" to c.iconName.ifBlank { "CATEGORY" }.take(100),
        "colorHex" to c.colorHex.ifBlank { "#15803D" }.take(20),
        "sortOrder" to c.sortOrder,
        "isArchived" to c.isArchived,
        "isSystemFee" to c.isSystemFee,
        "monthlyBudget" to c.monthlyBudget.coerceAtLeast(0.0),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun transactionToMap(userId: String, t: TransactionEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to t.id.coerceAtLeast(1L),
        "type" to (if (t.type in listOf("INCOME", "EXPENSE", "EXPENSE_REVERSAL")) t.type else "EXPENSE"),
        "amount" to t.amount.coerceAtLeast(0.0),
        "date" to t.date.coerceAtLeast(0L),
        "walletId" to t.walletId.coerceAtLeast(1L),
        "categoryId" to t.categoryId.coerceAtLeast(1L),
        "note" to t.note.take(1000),
        "reference" to t.reference.take(200),
        "tags" to t.tags.take(500),
        "transferId" to t.transferId,
        "loanActivityId" to t.loanActivityId,
        "parentTransactionId" to t.parentTransactionId,
        "attachmentUri" to t.attachmentUri.take(250000),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun transferToMap(userId: String, tr: TransferEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to tr.id.coerceAtLeast(1L),
        "sourceWalletId" to tr.sourceWalletId.coerceAtLeast(1L),
        "destinationWalletId" to tr.destinationWalletId.coerceAtLeast(1L),
        "principalAmount" to tr.principalAmount.coerceAtLeast(0.0),
        "destinationAmount" to tr.destinationAmount.coerceAtLeast(0.0),
        "conversionRate" to tr.conversionRate.coerceAtLeast(0.0),
        "feeMode" to tr.feeMode.ifBlank { TransferFeeMode.NONE.name }.take(50),
        "fixedFeeInput" to tr.fixedFeeInput.coerceAtLeast(0.0),
        "percentageInput" to tr.percentageInput.coerceAtLeast(0.0),
        "feeAmount" to tr.feeAmount.coerceAtLeast(0.0),
        "date" to tr.date.coerceAtLeast(0L),
        "note" to tr.note.take(1000),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun loanToMap(userId: String, l: LoanEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to l.id.coerceAtLeast(1L),
        "personName" to l.personName.trim().ifBlank { "Contact" }.take(150),
        "direction" to (if (l.direction == "BORROWED") "BORROWED" else "LENT"),
        "principalAmount" to l.principalAmount.coerceAtLeast(0.0),
        "initialWalletId" to (l.initialWalletId ?: 0L),
        "startDate" to l.startDate.coerceAtLeast(0L),
        "dueDate" to l.dueDate,
        "interestRate" to l.interestRate.coerceAtLeast(0.0),
        "interestType" to l.interestType.ifBlank { "NONE" }.take(40),
        "note" to l.note.take(1000),
        "reference" to l.reference.take(200),
        "isArchived" to l.isArchived,
        "attachmentUri" to l.attachmentUri.take(250000),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun loanActivityToMap(userId: String, la: LoanActivityEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to la.id.coerceAtLeast(1L),
        "loanId" to la.loanId.coerceAtLeast(1L),
        "activityType" to (if (la.activityType in listOf("DISBURSEMENT", "REPAYMENT", "ADJUSTMENT")) la.activityType else "REPAYMENT"),
        "principalPortion" to la.principalPortion.coerceAtLeast(0.0),
        "interestPortion" to la.interestPortion.coerceAtLeast(0.0),
        "walletId" to (la.walletId ?: 0L),
        "date" to la.date.coerceAtLeast(0L),
        "note" to la.note.take(1000),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun balanceAdjustmentToMap(userId: String, adj: BalanceAdjustmentEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to adj.id.coerceAtLeast(1L),
        "walletId" to adj.walletId.coerceAtLeast(1L),
        "actualBalance" to adj.actualBalance,
        "calculatedBalanceBefore" to adj.calculatedBalanceBefore,
        "difference" to adj.difference,
        "date" to adj.date.coerceAtLeast(0L),
        "reason" to adj.reason.trim().ifBlank { "Reconciliation" }.take(300),
        "note" to adj.note.take(1000),
        "isReversed" to adj.isReversed,
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun recurringToMap(userId: String, rec: RecurringTransactionEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to rec.id.coerceAtLeast(1L),
        "title" to rec.title.trim().ifBlank { "Recurring" }.take(150),
        "type" to (if (rec.type == "INCOME") "INCOME" else "EXPENSE"),
        "amountMode" to (if (rec.amountMode == "MANUAL") "MANUAL" else "FIXED"),
        "amount" to rec.amount.coerceAtLeast(0.0),
        "categoryId" to rec.categoryId.coerceAtLeast(1L),
        "defaultWalletId" to rec.defaultWalletId,
        "frequency" to rec.frequency.ifBlank { "MONTHLY" }.take(40),
        "dayOfMonth" to rec.dayOfMonth.coerceIn(1, 31),
        "customIntervalDays" to rec.customIntervalDays.coerceIn(1, 365),
        "nextDueDate" to rec.nextDueDate.coerceAtLeast(0L),
        "lastPaidDate" to rec.lastPaidDate,
        "note" to rec.note.take(1000),
        "isActive" to rec.isActive,
        "reminderEnabled" to rec.reminderEnabled,
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun savingsGoalToMap(userId: String, goal: SavingsGoalEntity, createdAt: Any): Map<String, Any?> = mapOf(
        "userId" to userId,
        "entityId" to goal.id.coerceAtLeast(1L),
        "title" to goal.title.trim().ifBlank { "Savings Goal" }.take(150),
        "targetAmount" to goal.targetAmount.coerceAtLeast(0.0),
        "savedAmount" to goal.savedAmount.coerceAtLeast(0.0),
        "iconName" to goal.iconName.ifBlank { "SAVINGS" }.take(100),
        "colorHex" to goal.colorHex.ifBlank { "#10B981" }.take(20),
        "deadlineMillis" to goal.deadlineMillis,
        "note" to goal.note.take(1000),
        "goalCreatedAt" to goal.createdAt.coerceAtLeast(0L),
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun parseUserProfile(doc: DocumentSnapshot): UserProfileDoc = UserProfileDoc(
        docId = doc.id,
        userId = doc.getString("userId") ?: doc.id,
        displayName = doc.getString("displayName") ?: "",
        email = doc.getString("email") ?: "",
        photoUrl = doc.getString("photoUrl") ?: "",
        currencyCode = doc.getString("currencyCode") ?: "BDT",
        currencySymbol = doc.getString("currencySymbol") ?: "৳",
        themeMode = doc.getString("themeMode") ?: "DARK",
        defaultWalletId = doc.getLong("defaultWalletId"),
        warnOnNegativeBalance = doc.getBoolean("warnOnNegativeBalance") ?: true,
        loanDeadlineNotificationEnabled = doc.getBoolean("loanDeadlineNotificationEnabled") ?: true,
        dailyExpenseReminderEnabled = doc.getBoolean("dailyExpenseReminderEnabled") ?: true,
        dailyReminderHour = (doc.getLong("dailyReminderHour") ?: 20L).toInt(),
        dailyReminderMinute = (doc.getLong("dailyReminderMinute") ?: 0L).toInt(),
        backgroundOpacity = (doc.getDouble("backgroundOpacity")?.let { if (kotlin.math.abs(it - 0.85) < 0.01) 0.20 else it } ?: 0.20).toFloat().coerceIn(0.0f, 1.0f),
        accentColorHex = doc.getString("accentColorHex")?.ifBlank { "#F15A24" } ?: "#F15A24",
        boxColorHex = doc.getString("boxColorHex")?.trim()?.let { if (it.isBlank() || it.equals("#141416", ignoreCase = true)) "#2A2A2A" else it } ?: "#2A2A2A",
        boxOpacity = (doc.getDouble("boxOpacity") ?: 0.50).toFloat().coerceIn(0.15f, 1.0f),
        appLanguage = doc.getString("appLanguage")?.trim()?.ifBlank { "EN" } ?: "EN",
        dateFormatPattern = "dd MMM yyyy",
        masterMonthlyBudget = (doc.getDouble("masterMonthlyBudget") ?: 0.0).coerceAtLeast(0.0),
        hideBalancesPrivacy = doc.getBoolean("hideBalancesPrivacy") ?: false,
        subscriptionTier = doc.getString("subscriptionTier") ?: "FREE",
        subscriptionExpiresAt = doc.getLong("subscriptionExpiresAt"),
        activationCode = doc.getString("activationCode") ?: "",
        hasCompletedOnboarding = doc.getBoolean("hasCompletedOnboarding") ?: true,
        syncVersion = doc.getLong("syncVersion") ?: 1L,
        createdAt = doc.getTimestamp("createdAt"),
        updatedAt = doc.getTimestamp("updatedAt")
    )

    private fun parseWallet(doc: DocumentSnapshot): WalletEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return WalletEntity(
            id = id,
            name = doc.getString("name") ?: return null,
            type = doc.getString("type") ?: WalletType.CASH.name,
            accountHint = doc.getString("accountHint") ?: "",
            openingBalance = doc.getDouble("openingBalance") ?: 0.0,
            openingDate = doc.getLong("openingDate") ?: 0L,
            currency = doc.getString("currency") ?: "BDT",
            iconName = doc.getString("iconName") ?: "CASH",
            colorHex = doc.getString("colorHex") ?: "#2E9E5B",
            sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
            isArchived = doc.getBoolean("isArchived") ?: false,
            includeInTotalBalance = doc.getBoolean("includeInTotalBalance") ?: true,
            creditLimit = (doc.getDouble("creditLimit") ?: 0.0).coerceAtLeast(0.0),
            statementDay = (doc.getLong("statementDay") ?: 0L).toInt().coerceIn(0, 31),
            paymentDueDay = (doc.getLong("paymentDueDay") ?: 0L).toInt().coerceIn(0, 31)
        )
    }

    private fun parseCategory(doc: DocumentSnapshot): CategoryEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return CategoryEntity(
            id = id,
            name = doc.getString("name") ?: return null,
            type = doc.getString("type") ?: "EXPENSE",
            iconName = doc.getString("iconName") ?: "CATEGORY",
            colorHex = doc.getString("colorHex") ?: "#15803D",
            sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
            isArchived = doc.getBoolean("isArchived") ?: false,
            isSystemFee = doc.getBoolean("isSystemFee") ?: false,
            monthlyBudget = (doc.getDouble("monthlyBudget") ?: 0.0).coerceAtLeast(0.0)
        )
    }

    private fun parseTransaction(doc: DocumentSnapshot): TransactionEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return TransactionEntity(
            id = id,
            type = doc.getString("type") ?: "EXPENSE",
            amount = doc.getDouble("amount") ?: 0.0,
            date = doc.getLong("date") ?: 0L,
            walletId = doc.getLong("walletId") ?: return null,
            categoryId = doc.getLong("categoryId") ?: return null,
            note = doc.getString("note") ?: "",
            reference = doc.getString("reference") ?: "",
            tags = doc.getString("tags") ?: "",
            transferId = doc.getLong("transferId"),
            loanActivityId = doc.getLong("loanActivityId"),
            parentTransactionId = doc.getLong("parentTransactionId"),
            attachmentUri = doc.getString("attachmentUri") ?: ""
        )
    }

    private fun parseTransfer(doc: DocumentSnapshot): TransferEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return TransferEntity(
            id = id,
            sourceWalletId = doc.getLong("sourceWalletId") ?: return null,
            destinationWalletId = doc.getLong("destinationWalletId") ?: return null,
            principalAmount = doc.getDouble("principalAmount") ?: 0.0,
            destinationAmount = doc.getDouble("destinationAmount") ?: 0.0,
            conversionRate = doc.getDouble("conversionRate") ?: 1.0,
            feeMode = doc.getString("feeMode") ?: TransferFeeMode.NONE.name,
            fixedFeeInput = doc.getDouble("fixedFeeInput") ?: 0.0,
            percentageInput = doc.getDouble("percentageInput") ?: 0.0,
            feeAmount = doc.getDouble("feeAmount") ?: 0.0,
            date = doc.getLong("date") ?: 0L,
            note = doc.getString("note") ?: ""
        )
    }

    private fun parseLoan(doc: DocumentSnapshot): LoanEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return LoanEntity(
            id = id,
            personName = doc.getString("personName") ?: return null,
            direction = doc.getString("direction") ?: LoanDirection.LENT.name,
            principalAmount = doc.getDouble("principalAmount") ?: 0.0,
            initialWalletId = doc.getLong("initialWalletId")?.takeIf { it > 0 },
            startDate = doc.getLong("startDate") ?: 0L,
            dueDate = doc.getLong("dueDate"),
            interestRate = doc.getDouble("interestRate") ?: 0.0,
            interestType = doc.getString("interestType") ?: "NONE",
            note = doc.getString("note") ?: "",
            reference = doc.getString("reference") ?: "",
            isArchived = doc.getBoolean("isArchived") ?: false,
            attachmentUri = doc.getString("attachmentUri") ?: ""
        )
    }

    private fun parseLoanActivity(doc: DocumentSnapshot): LoanActivityEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return LoanActivityEntity(
            id = id,
            loanId = doc.getLong("loanId") ?: return null,
            activityType = doc.getString("activityType") ?: LoanActivityType.REPAYMENT.name,
            principalPortion = doc.getDouble("principalPortion") ?: 0.0,
            interestPortion = doc.getDouble("interestPortion") ?: 0.0,
            walletId = doc.getLong("walletId")?.takeIf { it > 0 },
            date = doc.getLong("date") ?: 0L,
            note = doc.getString("note") ?: ""
        )
    }

    private fun parseBalanceAdjustment(doc: DocumentSnapshot): BalanceAdjustmentEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return BalanceAdjustmentEntity(
            id = id,
            walletId = doc.getLong("walletId") ?: return null,
            actualBalance = doc.getDouble("actualBalance") ?: 0.0,
            calculatedBalanceBefore = doc.getDouble("calculatedBalanceBefore") ?: 0.0,
            difference = doc.getDouble("difference") ?: 0.0,
            date = doc.getLong("date") ?: 0L,
            reason = doc.getString("reason") ?: "Reconciliation",
            note = doc.getString("note") ?: "",
            isReversed = doc.getBoolean("isReversed") ?: false
        )
    }

    private fun parseRecurring(doc: DocumentSnapshot): RecurringTransactionEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return RecurringTransactionEntity(
            id = id,
            title = doc.getString("title") ?: return null,
            type = doc.getString("type") ?: "EXPENSE",
            amountMode = doc.getString("amountMode") ?: "FIXED",
            amount = doc.getDouble("amount") ?: 0.0,
            categoryId = doc.getLong("categoryId") ?: return null,
            defaultWalletId = doc.getLong("defaultWalletId"),
            frequency = doc.getString("frequency") ?: "MONTHLY",
            dayOfMonth = (doc.getLong("dayOfMonth") ?: 1L).toInt().coerceIn(1, 31),
            customIntervalDays = (doc.getLong("customIntervalDays") ?: 30L).toInt().coerceIn(1, 365),
            nextDueDate = doc.getLong("nextDueDate") ?: System.currentTimeMillis(),
            lastPaidDate = doc.getLong("lastPaidDate"),
            note = doc.getString("note") ?: "",
            isActive = doc.getBoolean("isActive") ?: true,
            reminderEnabled = doc.getBoolean("reminderEnabled") ?: true
        )
    }

    private fun parseSavingsGoal(doc: DocumentSnapshot): SavingsGoalEntity? {
        val id = doc.getLong("entityId") ?: doc.id.toLongOrNull() ?: return null
        return SavingsGoalEntity(
            id = id,
            title = doc.getString("title") ?: return null,
            targetAmount = doc.getDouble("targetAmount") ?: 0.0,
            savedAmount = doc.getDouble("savedAmount") ?: 0.0,
            iconName = doc.getString("iconName") ?: "SAVINGS",
            colorHex = doc.getString("colorHex") ?: "#10B981",
            deadlineMillis = doc.getLong("deadlineMillis"),
            note = doc.getString("note") ?: "",
            createdAt = doc.getLong("goalCreatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun sha256Hex(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun redeemActivationCode(
        context: Context,
        rawCode: String,
        userId: String,
        userEmail: String
    ): ActivationResult {
        val cleanCode = rawCode.trim().uppercase()
        if (cleanCode.length < 8) {
            return ActivationResult.Error("Invalid activation code format.")
        }
        val isThreeDay = cleanCode.startsWith("PK3D-")
        val isLifetime = cleanCode.startsWith("PKLT-")
        if (!isThreeDay && !isLifetime) {
            return ActivationResult.Error("Unrecognized activation code prefix. Codes must start with PK3D- or PKLT-.")
        }
        val defaultTier = if (isThreeDay) SubscriptionTier.TRIAL_3_DAYS else SubscriptionTier.LIFETIME

        if (userId.isBlank()) {
            return ActivationResult.Error("Please sign in with your Google Account before redeeming so your license is permanently secured.")
        }

        // 1. Check SHA-256 digest against bundled activation_codes.json hash bank
        val codeHash = sha256Hex(cleanCode)
        val isValidInHashBank = try {
            val jsonStr = context.assets.open("activation_codes.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val hashArray = if (isThreeDay) {
                root.optJSONArray("trial_3_days_hashes") ?: root.optJSONArray("trial_3_days") ?: root.optJSONArray("threeDayCodes")
            } else {
                root.optJSONArray("lifetime_hashes") ?: root.optJSONArray("lifetime") ?: root.optJSONArray("lifetimeCodes")
            }
            var found = false
            if (hashArray != null) {
                for (i in 0 until hashArray.length()) {
                    val obj = hashArray.optJSONObject(i)
                    val candidate = (obj?.optString("code") ?: hashArray.optString(i)).trim()
                    if (candidate.equals(codeHash, ignoreCase = true) || candidate.equals(cleanCode, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
            }
            found
        } catch (e: Exception) {
            Log.w("PocketCloudSync", "Could not read activation_codes.json: ${e.message}")
            false
        }

        // 2. Query / Claim in Firestore (also supports codes dynamically added by Developer Admin in Firestore)
        val codeDocRef = db.collection("activation_codes").document(cleanCode)
        val userDocRef = db.collection("users").document(userId)

        suspend fun persistSubscriptionToUserProfile(resolvedTier: SubscriptionTier, resolvedExpiresAt: Long?) {
            val userSnap = userDocRef.get().await()
            if (userSnap.exists()) {
                userDocRef.update(
                    mapOf(
                        "subscriptionTier" to resolvedTier.name,
                        "subscriptionExpiresAt" to resolvedExpiresAt,
                        "activationCode" to cleanCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                saveUserProfile(
                    userId = userId,
                    profile = UserProfileDoc(
                        userId = userId,
                        displayName = "Pocket User",
                        email = userEmail,
                        subscriptionTier = resolvedTier.name,
                        subscriptionExpiresAt = resolvedExpiresAt,
                        activationCode = cleanCode
                    )
                )
            }
        }

        return try {
            val snap = codeDocRef.get().await()
            val now = System.currentTimeMillis()

            if (snap.exists()) {
                val isRevoked = snap.getBoolean("isRevoked") ?: false
                if (isRevoked) {
                    return ActivationResult.Error("This activation code has been disabled or removed by the developer.")
                }
                val docTierStr = snap.getString("tier")
                val tier = if (docTierStr == SubscriptionTier.LIFETIME.name) {
                    SubscriptionTier.LIFETIME
                } else if (docTierStr == SubscriptionTier.TRIAL_3_DAYS.name) {
                    SubscriptionTier.TRIAL_3_DAYS
                } else {
                    defaultTier
                }
                var expiresAt = if (tier == SubscriptionTier.TRIAL_3_DAYS) now + (3L * 24L * 60L * 60L * 1000L) else null

                val isClaimed = snap.getBoolean("isClaimed") ?: false
                val claimedByUid = snap.getString("claimedByUid") ?: ""
                if (isClaimed) {
                    if (claimedByUid == userId) {
                        val existingExp = snap.getLong("expiresAt")
                        if (tier == SubscriptionTier.TRIAL_3_DAYS && existingExp != null && existingExp > 0L) {
                            if (existingExp < now) {
                                return ActivationResult.Error("This 3 Days Premium code has already expired.")
                            }
                            expiresAt = existingExp
                        }
                        persistSubscriptionToUserProfile(tier, expiresAt)
                        ActivationResult.Success(
                            tier = tier,
                            expiresAt = expiresAt,
                            message = "Welcome back! Your ${if (tier == SubscriptionTier.LIFETIME) "Lifetime Premium" else "3 Days Premium"} has been restored for this Google account."
                        )
                    } else {
                        ActivationResult.Error("This activation code has already been redeemed by another Google account.")
                    }
                } else {
                    val updateMap = mutableMapOf<String, Any?>(
                        "isClaimed" to true,
                        "claimedByUid" to userId,
                        "claimedByEmail" to userEmail,
                        "claimedAt" to FieldValue.serverTimestamp()
                    )
                    if (expiresAt != null) {
                        updateMap["expiresAt"] = expiresAt
                    }
                    codeDocRef.update(updateMap).await()
                    persistSubscriptionToUserProfile(tier, expiresAt)

                    ActivationResult.Success(
                        tier = tier,
                        expiresAt = expiresAt,
                        message = if (tier == SubscriptionTier.LIFETIME) {
                            "Congratulations! Lifetime Premium is now active for your account."
                        } else {
                            "3 Days Premium successfully activated! All features unlocked."
                        }
                    )
                }
            } else {
                if (!isValidInHashBank) {
                    return ActivationResult.Error("Activation code is not valid. Please ensure you typed it correctly or contact developer on WhatsApp.")
                }
                val tier = defaultTier
                val expiresAt = if (tier == SubscriptionTier.TRIAL_3_DAYS) now + (3L * 24L * 60L * 60L * 1000L) else null
                val createMap = mutableMapOf<String, Any?>(
                    "code" to cleanCode,
                    "tier" to tier.name,
                    "isClaimed" to true,
                    "claimedByUid" to userId,
                    "claimedByEmail" to userEmail,
                    "claimedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (expiresAt != null) {
                    createMap["expiresAt"] = expiresAt
                }
                codeDocRef.set(createMap).await()
                persistSubscriptionToUserProfile(tier, expiresAt)

                ActivationResult.Success(
                    tier = tier,
                    expiresAt = expiresAt,
                    message = if (tier == SubscriptionTier.LIFETIME) {
                        "Congratulations! Lifetime Premium is now active for your account."
                    } else {
                        "3 Days Premium successfully activated! All features unlocked."
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("PocketCloudSync", "Failed to redeem code: ${e.message}", e)
            ActivationResult.Error("Verification error: ${e.localizedMessage ?: "Network or permissions issue"}. Please check your connection and try again.")
        }
    }

    // --- Developer Admin Code Management (Restricted to hadi1617180@gmail.com by Firestore Rules) ---
    suspend fun fetchAllActivationCodesForAdmin(): Result<List<CloudActivationCodeRecord>> {
        return runCatching {
            val snap = db.collection("activation_codes").get().await()
            snap.documents.mapNotNull { doc ->
                val code = doc.getString("code") ?: doc.id
                val tier = doc.getString("tier") ?: if (code.startsWith("PKLT-")) "LIFETIME" else "TRIAL_3_DAYS"
                val isClaimed = doc.getBoolean("isClaimed") ?: false
                val isRevoked = doc.getBoolean("isRevoked") ?: false
                val claimedByEmail = doc.getString("claimedByEmail") ?: ""
                val claimedByUid = doc.getString("claimedByUid") ?: ""
                val expiresAt = doc.getLong("expiresAt")
                CloudActivationCodeRecord(
                    code = code,
                    tier = tier,
                    isClaimed = isClaimed,
                    isRevoked = isRevoked,
                    claimedByEmail = claimedByEmail,
                    claimedByUid = claimedByUid,
                    expiresAt = expiresAt
                )
            }.sortedWith(compareBy<CloudActivationCodeRecord> { it.isRevoked }.thenBy { it.isClaimed }.thenBy { it.code })
        }
    }

    suspend fun createOrAddActivationCodeByAdmin(rawCode: String, tier: SubscriptionTier): Result<String> {
        return runCatching {
            val clean = rawCode.trim().uppercase()
            val prefix = if (tier == SubscriptionTier.LIFETIME) "PKLT-" else "PK3D-"
            val finalCode = if (clean.startsWith("PK3D-") || clean.startsWith("PKLT-")) clean else "$prefix$clean"
            require(finalCode.length >= 9) { "Code must be at least 9 characters (e.g. ${prefix}XXXX-XXXX)." }
            val docRef = db.collection("activation_codes").document(finalCode)
            docRef.set(
                mapOf(
                    "code" to finalCode,
                    "tier" to tier.name,
                    "isClaimed" to false,
                    "isRevoked" to false,
                    "claimedByUid" to "",
                    "claimedByEmail" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            finalCode
        }
    }

    suspend fun generateRandomActivationCodeByAdmin(tier: SubscriptionTier): Result<String> {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = java.security.SecureRandom()
        fun randomBlock(): String = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val prefix = if (tier == SubscriptionTier.LIFETIME) "PKLT" else "PK3D"
        val generated = "$prefix-${randomBlock()}-${randomBlock()}"
        return createOrAddActivationCodeByAdmin(generated, tier)
    }

    suspend fun revokeOrRemoveActivationCodeByAdmin(rawCode: String, deleteDocument: Boolean = false): Result<String> {
        return runCatching {
            val clean = rawCode.trim().uppercase()
            require(clean.isNotBlank()) { "Enter a code to remove or disable." }
            val docRef = db.collection("activation_codes").document(clean)
            if (deleteDocument) {
                docRef.delete().await()
            } else {
                val tierName = if (clean.startsWith("PKLT-")) SubscriptionTier.LIFETIME.name else SubscriptionTier.TRIAL_3_DAYS.name
                docRef.set(
                    mapOf(
                        "code" to clean,
                        "tier" to tierName,
                        "isClaimed" to false,
                        "isRevoked" to true,
                        "claimedByUid" to "",
                        "claimedByEmail" to "",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            clean
        }
    }

    // --- Automated Feedback & Bugs Report (Firestore + Background Email Delivery to hadi1617180@gmail.com) ---
    suspend fun submitFeedbackReport(
        uid: String,
        userEmail: String,
        userName: String,
        subject: String,
        details: String,
        platform: String = "Android"
    ): Result<Unit> {
        return runCatching {
            val cleanSubject = subject.trim().ifBlank { "Pocket App Feedback / Bug Report" }.take(300)
            val cleanDetails = details.trim().ifBlank { cleanSubject }.take(5000)
            val cleanEmail = userEmail.trim().take(200)
            val cleanName = userName.trim().ifBlank { "Pocket User" }.take(120)

            var firestoreSucceeded = false
            if (uid.isNotBlank()) {
                runCatching {
                    db.collection("feedback_reports")
                        .add(
                            mapOf(
                                "uid" to uid,
                                "userEmail" to cleanEmail,
                                "userName" to cleanName,
                                "subject" to cleanSubject,
                                "details" to cleanDetails,
                                "platform" to platform.take(40),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                    firestoreSucceeded = true
                }.onFailure { err ->
                    Log.w("PocketCloudSync", "Firestore feedback save warning: ${err.message}")
                }
            }

            var emailWebhookSucceeded = false
            runCatching {
                val url = java.net.URL("https://formsubmit.co/ajax/hadi1617180@gmail.com")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Referer", "https://pocket-finance.app/")
                    setRequestProperty("Origin", "https://pocket-finance.app")
                }
                val jsonPayload = JSONObject().apply {
                    put("_subject", "[Pocket Feedback] $cleanSubject")
                    put("subject", cleanSubject)
                    put("message", cleanDetails)
                    put("details", cleanDetails)
                    put("email", cleanEmail.ifBlank { "no-reply@pocket.app" })
                    put("_replyto", cleanEmail.ifBlank { "no-reply@pocket.app" })
                    put("user_gmail", cleanEmail)
                    put("name", cleanName)
                    put("platform", platform)
                    put("_template", "table")
                    put("_captcha", "false")
                }.toString()
                conn.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                emailWebhookSucceeded = code in 200..299
                conn.disconnect()
            }.onFailure { err ->
                Log.w("PocketCloudSync", "Background email relay warning: ${err.message}")
            }

            if (!firestoreSucceeded && !emailWebhookSucceeded) {
                throw IllegalStateException("Could not deliver feedback report. Please check your internet connection.")
            }
        }
    }

    suspend fun fetchAllFeedbackReportsForAdmin(): Result<List<CloudFeedbackReportRecord>> {
        return runCatching {
            val snap = db.collection("feedback_reports").get().await()
            snap.documents.mapNotNull { doc ->
                val subject = doc.getString("subject").orEmpty()
                val details = doc.getString("details").orEmpty()
                val userEmail = doc.getString("userEmail").orEmpty()
                val userName = doc.getString("userName").orEmpty()
                val uid = doc.getString("uid").orEmpty()
                val platform = doc.getString("platform").orEmpty().ifBlank { "App" }
                val tsMillis = doc.getTimestamp("createdAt")?.toDate()?.time
                    ?: doc.getLong("createdAt")
                    ?: 0L
                CloudFeedbackReportRecord(
                    id = doc.id,
                    uid = uid,
                    userEmail = userEmail,
                    userName = userName,
                    subject = subject,
                    details = details,
                    platform = platform,
                    createdAtMillis = tsMillis
                )
            }.sortedByDescending { it.createdAtMillis }
        }
    }

    suspend fun deleteFeedbackReportByAdmin(reportId: String): Result<Unit> {
        return runCatching {
            db.collection("feedback_reports").document(reportId).delete().await()
        }
    }
}

data class CloudFeedbackReportRecord(
    val id: String,
    val uid: String,
    val userEmail: String,
    val userName: String,
    val subject: String,
    val details: String,
    val platform: String,
    val createdAtMillis: Long
)

data class CloudActivationCodeRecord(
    val code: String,
    val tier: String,
    val isClaimed: Boolean,
    val isRevoked: Boolean,
    val claimedByEmail: String,
    val claimedByUid: String,
    val expiresAt: Long? = null
)

sealed class ActivationResult {
    data class Success(val tier: SubscriptionTier, val expiresAt: Long?, val message: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}
