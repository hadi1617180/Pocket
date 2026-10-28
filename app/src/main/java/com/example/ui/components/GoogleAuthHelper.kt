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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Primary "Sign in with Google" button used on Startup Page 1.
 */
@Composable
fun GoogleSignInPrimaryButton(
    isSyncing: Boolean,
    onGoogleSignedIn: (FirebaseUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) {
        runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    }
    var isSigningIn by remember { mutableStateOf(false) }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                .heightIn(min = 52.dp)
                .testTag("startup_page1_google_sign_in_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isSigningIn || isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSyncing) "Syncing..." else "Signing in...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign in with Google",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
}

/**
 * Reusable Google Cloud Sync card used in Settings.
 */
@Composable
fun GoogleAccountSyncCard(
    signedInEmail: String?,
    signedInName: String?,
    signedInPhotoUrl: String?,
    isSyncing: Boolean,
    lastSyncStatus: String?,
    autoAttemptSignInOnLaunch: Boolean = false,
    confirmBeforeOfflineSignIn: Boolean = false,
    onGoogleSignedIn: (FirebaseUser) -> Unit,
    onSyncNowClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAccountClicked: (() -> Unit)? = null,
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
    var showOfflineWipeConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

    val isSignedIn = !signedInEmail.isNullOrBlank() || !signedInName.isNullOrBlank()

    fun launchInteractiveSignIn() {
        if (webClientId.isBlank()) {
            localErrorMessage = "Google Web Client ID is not configured."
            return
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
    }

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

    if (showOfflineWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineWipeConfirmDialog = false },
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
                    text = "Switch from Offline to Google Account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You are currently using Pocket without logging in. Signing in with Google will delete local offline data on this device and continue with your Google account data (or take you to wallet setup if your Google account is new).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOfflineWipeConfirmDialog = false
                        launchInteractiveSignIn()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_offline_wipe_sign_in_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Local Data & Sign In", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOfflineWipeConfirmDialog = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_offline_wipe_sign_in_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        val accountLabel = signedInEmail?.takeIf { it.isNotBlank() }
            ?: signedInName?.takeIf { it.isNotBlank() }
            ?: "your Google Cloud account"
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            containerColor = pocketColors.overlaySurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Sign Out of Google Account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Your wallets, transactions, and loans are safely backed up to your Google Cloud account ($accountLabel). Signing out will clear cached data from this device and return to the startup screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        localErrorMessage = null
                        onSignOutClicked()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_google_sign_out_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirmDialog = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_google_sign_out_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountConfirmDialog) {
        val accountLabel = signedInEmail?.takeIf { it.isNotBlank() }
            ?: signedInName?.takeIf { it.isNotBlank() }
            ?: "your Google Account"
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirmDialog = false },
            containerColor = pocketColors.overlaySurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Account Permanently?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete your Pocket account ($accountLabel)?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "WARNING: All your cloud data, wallets, transactions, transfers, loans, and settings will be permanently erased from Google Cloud. Local device data will also be wiped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Next time you sign in with this Google account, it will start completely fresh as a brand-new account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirmDialog = false
                        onDeleteAccountClicked?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_account_permanently_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmDialog = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_delete_account_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (!isSignedIn) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    if (confirmBeforeOfflineSignIn) {
                        showOfflineWipeConfirmDialog = true
                    } else {
                        launchInteractiveSignIn()
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            showSignOutConfirmDialog = true
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

                if (onDeleteAccountClicked != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.8.dp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Delete account & data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showDeleteAccountConfirmDialog = true },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("delete_google_account_button"),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "Delete Account",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
