package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.LocalPocketColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun Context.findComponentActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

suspend fun handleGoogleCredential(
    credential: Credential,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit
) {
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user
        if (user != null) {
            onAuthSuccess(user)
        } else {
            onAuthError("Sign-in succeeded, but no Firebase user was returned.")
        }
    } else {
        onAuthError("Unsupported credential type returned.")
    }
}

suspend fun attemptAutoSignIn(
    activityContext: Context,
    credentialManager: CredentialManager,
    webClientId: String,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit
) {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            context = activityContext,
            request = request
        )
        handleGoogleCredential(result.credential, onAuthSuccess, onAuthError)
    } catch (_: NoCredentialException) {
        // Silent pass: user hasn't authorized this app before; wait for interactive button click
    } catch (_: GetCredentialCancellationException) {
        // User dismissed bottom sheet
    } catch (e: GetCredentialException) {
        onAuthError(e.localizedMessage ?: "Auto sign-in failed")
    } catch (e: Exception) {
        onAuthError(e.localizedMessage ?: "Authentication error")
    }
}

suspend fun onGoogleSignInClicked(
    activityContext: Context,
    credentialManager: CredentialManager,
    webClientId: String,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit
) {
    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
        serverClientId = webClientId
    ).build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            context = activityContext,
            request = request
        )
        handleGoogleCredential(result.credential, onAuthSuccess, onAuthError)
    } catch (_: GetCredentialCancellationException) {
        // User cancelled interactive sign-in
    } catch (e: GetCredentialException) {
        onAuthError(e.localizedMessage ?: "Google Sign-In failed")
    } catch (e: Exception) {
        onAuthError(e.localizedMessage ?: "Authentication error")
    }
}

suspend fun signOutGoogleAccount(context: Context) {
    runCatching { Firebase.auth.signOut() }
    runCatching {
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}

/**
 * Reusable Google Cloud Sync card used on both the Startup (Onboarding) screen and in Settings.
 */
@Composable
fun GoogleAccountSyncCard(
    signedInEmail: String?,
    signedInName: String?,
    signedInPhotoUrl: String?,
    isSyncing: Boolean,
    lastSyncStatus: String?,
    autoAttemptSignInOnLaunch: Boolean = false,
    onGoogleSignedIn: (FirebaseUser) -> Unit,
    onSyncNowClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) {
        runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    }
    var isSigningIn by remember { mutableStateOf(false) }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    val isSignedIn = !signedInEmail.isNullOrBlank() || !signedInName.isNullOrBlank()

    LaunchedEffect(autoAttemptSignInOnLaunch, isSignedIn, webClientId) {
        if (autoAttemptSignInOnLaunch && !isSignedIn && webClientId.isNotBlank()) {
            val activity = context.findComponentActivity() ?: context
            attemptAutoSignIn(
                activityContext = activity,
                credentialManager = credentialManager,
                webClientId = webClientId,
                onAuthSuccess = { user -> onGoogleSignedIn(user) },
                onAuthError = { /* Silent on auto sign-in */ }
            )
        }
    }

    if (!isSignedIn) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    if (webClientId.isBlank()) {
                        localErrorMessage = "Google Web Client ID is not configured."
                        return@Button
                    }
                    localErrorMessage = null
                    isSigningIn = true
                    val activity = context.findComponentActivity() ?: context
                    scope.launch {
                        onGoogleSignInClicked(
                            activityContext = activity,
                            credentialManager = credentialManager,
                            webClientId = webClientId,
                            onAuthSuccess = { user ->
                                isSigningIn = false
                                onGoogleSignedIn(user)
                            },
                            onAuthError = { err ->
                                isSigningIn = false
                                localErrorMessage = err
                            }
                        )
                        isSigningIn = false
                    }
                },
                enabled = !isSigningIn && !isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSigningIn || isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing..." else "Signing in...")
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            }
            if (!localErrorMessage.isNullOrBlank()) {
                Text(
                    text = localErrorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!signedInPhotoUrl.isNullOrBlank()) {
                    UserProfileBadge(
                        userName = signedInName ?: "",
                        profilePhotoUri = signedInPhotoUrl,
                        avatarSize = 32.dp,
                        showNameBelow = false
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signedInEmail?.ifBlank { signedInName } ?: (signedInName ?: "Google Connected"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!lastSyncStatus.isNullOrBlank()) {
                        Text(
                            text = lastSyncStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onSyncNowClicked,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("google_sync_now_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = {
                        localErrorMessage = null
                        onSignOutClicked()
                    },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("google_sign_out_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
