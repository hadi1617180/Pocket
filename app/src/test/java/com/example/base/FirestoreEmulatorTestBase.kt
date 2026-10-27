package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class FirestoreEmulatorTestBase {

    protected lateinit var db: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var projectId: String
    protected lateinit var databaseId: String

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseId = context.getString(R.string.firestore_database_id)

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        projectId = FirebaseApp.getInstance().options.projectId
            ?: throw IllegalStateException("FirebaseApp options.projectId is null. Check google-services.json.")

        auth = FirebaseAuth.getInstance()
        try {
            auth.useEmulator("127.0.0.1", 9099)
        } catch (_: IllegalStateException) {
            // Already connected
        }

        db = FirebaseFirestore.getInstance(databaseId)
        try {
            db.useEmulator("127.0.0.1", 8085)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        } catch (_: IllegalStateException) {
            // Already configured
        }
    }

    @After
    open fun tearDown() {
        runCatching { auth.signOut() }
        clearEmulatorData()
        runCatching {
            val terminateTask = db.terminate()
            val deadline = System.currentTimeMillis() + 5_000L
            while (!terminateTask.isComplete && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
        runCatching { FirebaseApp.clearInstancesForTest() }
    }

    protected suspend fun <T> runWithMainLooperPump(
        timeoutMs: Long = 15_000L,
        block: suspend () -> T
    ): T = coroutineScope {
        val deferred = async(Dispatchers.IO) { block() }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!deferred.isCompleted) {
            if (System.currentTimeMillis() > deadline) {
                deferred.cancel()
                throw AssertionError("Firestore operation timed out after ${timeoutMs}ms")
            }
            shadowOf(Looper.getMainLooper()).idle()
            withContext(Dispatchers.IO) { delay(10) }
        }
        shadowOf(Looper.getMainLooper()).idle()
        deferred.await()
    }

    protected suspend fun <T> Task<T>.awaitWithIdle(timeoutSeconds: Long = 10): T {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
        while (!isComplete) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(
                    "Firestore Task timed out after ${timeoutSeconds}s — is the emulator running on 127.0.0.1:8085?"
                )
            }
            shadowOf(Looper.getMainLooper()).idle()
            withContext(Dispatchers.IO) { delay(10) }
        }
        shadowOf(Looper.getMainLooper()).idle()
        if (isSuccessful) return result
        throw exception ?: RuntimeException("Task failed with no exception")
    }

    protected suspend fun signInTestUser(
        email: String = "alice@example.com",
        password: String = "password123"
    ): String {
        return try {
            auth.createUserWithEmailAndPassword(email, password).awaitWithIdle().user!!.uid
        } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).awaitWithIdle().user!!.uid
        }
    }

    protected fun signOut() {
        auth.signOut()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun clearEmulatorData() {
        listOf(
            "http://127.0.0.1:8085/emulator/v1/projects/$projectId/databases/$databaseId/documents",
            "http://127.0.0.1:9099/emulator/v1/projects/$projectId/accounts"
        ).forEach { endpoint ->
            runCatching {
                (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 2000
                    readTimeout = 2000
                    responseCode
                    disconnect()
                }
            }
        }
    }
}
