package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PocketCloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: PocketCloudSyncRepository

    @Before
    override fun setUp() {
        super.setUp()
        if (isDbInitialized) {
            repository = PocketCloudSyncRepository(db)
        }
    }

    @Test
    fun saveUserProfile_whenUnauthenticated_throwsPermissionDenied() = runTest {
        signOut()
        try {
            runWithMainLooperPump {
                repository.saveUserProfile(
                    userId = "unauthed_user",
                    profile = UserProfileDoc(
                        userId = "unauthed_user",
                        displayName = "Unauthed"
                    )
                )
            }
            fail("Expected PERMISSION_DENIED for unauthenticated write")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun pushAndPullPayload_whenAuthenticatedOwner_succeedsAndRoundTripsAllEntities() = runTest {
        val uid = signInTestUser("hadi@example.com", "password123")

        val payload = PocketBackupPayload(
            formatVersion = "pocket-backup-v1",
            exportedAtMillis = 1700000000000L,
            wallets = listOf(
                WalletEntity(
                    id = 1L,
                    name = "Cash",
                    type = WalletType.CASH.name,
                    openingBalance = 2500.0,
                    openingDate = 1700000000000L,
                    currency = "BDT",
                    iconName = "CASH",
                    colorHex = "#2E9E5B",
                    sortOrder = 1,
                    isArchived = false,
                    includeInTotalBalance = true
                )
            ),
            categories = listOf(
                CategoryEntity(
                    id = 1L,
                    name = "Salary",
                    type = "INCOME",
                    iconName = "WORK",
                    colorHex = "#4ADE80",
                    sortOrder = 1,
                    isArchived = false,
                    isSystemFee = false
                )
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 10L,
                    type = TransactionType.INCOME.name,
                    amount = 30000.0,
                    date = 1700000000000L,
                    walletId = 1L,
                    categoryId = 1L,
                    note = "Monthly salary",
                    reference = "SAL-01",
                    tags = "income"
                )
            ),
            transfers = emptyList(),
            loans = listOf(
                LoanEntity(
                    id = 5L,
                    personName = "Rahim",
                    direction = LoanDirection.LENT.name,
                    principalAmount = 1500.0,
                    initialWalletId = 1L,
                    startDate = 1700000000000L,
                    note = "Quick loan"
                )
            ),
            loanActivities = listOf(
                LoanActivityEntity(
                    id = 6L,
                    loanId = 5L,
                    activityType = LoanActivityType.DISBURSEMENT.name,
                    principalPortion = 1500.0,
                    interestPortion = 0.0,
                    walletId = 1L,
                    date = 1700000000000L,
                    note = "Initial disbursement"
                )
            ),
            adjustments = listOf(
                BalanceAdjustmentEntity(
                    id = 8L,
                    walletId = 1L,
                    actualBalance = 31000.0,
                    calculatedBalanceBefore = 31000.0,
                    difference = 0.0,
                    date = 1700000000000L,
                    reason = "Verified"
                )
            ),
            settings = AppSettingsEntity(
                id = 1,
                hasCompletedOnboarding = true,
                userName = "Hadi",
                profilePhotoUri = "https://lh3.googleusercontent.com/a/hadi_avatar"
            )
        )

        val snapshot = runWithMainLooperPump {
            repository.pushLocalPayloadToCloud(
                userId = uid,
                email = "hadi@example.com",
                googleDisplayName = "Hadi Google",
                googlePhotoUrl = "https://lh3.googleusercontent.com/a/hadi_avatar",
                payload = payload,
                syncVersion = 10L
            )
            repository.pullCloudPayloadIfPresent(uid)
        }

        assertNotNull(snapshot)
        assertEquals("Hadi", snapshot!!.profile.displayName)
        assertEquals("https://lh3.googleusercontent.com/a/hadi_avatar", snapshot.profile.photoUrl)
        assertEquals(1, snapshot.payload.wallets.size)
        assertEquals("Cash", snapshot.payload.wallets.first().name)
        assertEquals(1, snapshot.payload.transactions.size)
        assertEquals(30000.0, snapshot.payload.transactions.first().amount, 0.001)
        assertEquals(1, snapshot.payload.loans.size)
        assertEquals("Rahim", snapshot.payload.loans.first().personName)
    }

    @Test
    fun getUserProfile_whenAnotherUserTriesToRead_throwsPermissionDenied() = runTest {
        val aliceUid = signInTestUser("alice2@example.com", "password123")
        runWithMainLooperPump {
            repository.saveUserProfile(
                userId = aliceUid,
                profile = UserProfileDoc(
                    userId = aliceUid,
                    displayName = "Alice"
                )
            )
        }

        signOut()
        signInTestUser("bob2@example.com", "password123")
        try {
            runWithMainLooperPump {
                repository.getUserProfile(aliceUid)
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
