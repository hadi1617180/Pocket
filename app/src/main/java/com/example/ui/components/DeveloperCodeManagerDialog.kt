package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CloudActivationCodeRecord
import com.example.data.SubscriptionTier
import com.example.ui.PocketViewModel
import com.example.ui.theme.LocalPocketColors
import kotlinx.coroutines.launch

@Composable
fun DeveloperCodeManagerDialog(
    viewModel: PocketViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    val scope = rememberCoroutineScope()

    var codes by remember { mutableStateOf<List<CloudActivationCodeRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf(SubscriptionTier.TRIAL_3_DAYS) }

    fun copyToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Pocket Activation Code", code))
        Toast.makeText(context, "Copied: $code", Toast.LENGTH_SHORT).show()
    }

    fun refreshList() {
        scope.launch {
            isLoading = true
            val res = viewModel.fetchAdminActivationCodes()
            isLoading = false
            res.onSuccess { list ->
                codes = list
            }.onFailure { err ->
                isError = true
                statusMessage = err.localizedMessage ?: "Failed to load codes from cloud."
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("developer_code_manager_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = pocketColors.overlaySurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Admin Code Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "hadi1617180@gmail.com • SHA-256 Protected",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { refreshList() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh codes")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // 1-Tap Random Code Generators
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Instant 1-Tap Code Generator (Auto-Copies)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = viewModel.generateAdminActivationCode(SubscriptionTier.TRIAL_3_DAYS)
                                        res.onSuccess { newCode ->
                                            isError = false
                                            statusMessage = "Generated & copied 3 Days Premium: $newCode"
                                            copyToClipboard(newCode)
                                            refreshList()
                                        }.onFailure { err ->
                                            isError = true
                                            statusMessage = err.localizedMessage ?: "Failed to generate code."
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_generate_3d_btn"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("3 Days Premium", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = viewModel.generateAdminActivationCode(SubscriptionTier.LIFETIME)
                                        res.onSuccess { newCode ->
                                            isError = false
                                            statusMessage = "Generated & copied Lifetime: $newCode"
                                            copyToClipboard(newCode)
                                            refreshList()
                                        }.onFailure { err ->
                                            isError = true
                                            statusMessage = err.localizedMessage ?: "Failed to generate code."
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_generate_lt_btn"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lifetime Code", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Add Custom Code OR Disable/Remove Any Code
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, pocketColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Add Custom Code or Disable Any Code",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase() },
                            placeholder = { Text("XXXX-XXXX-XXXX", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_code_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedTier == SubscriptionTier.TRIAL_3_DAYS,
                                onClick = { selectedTier = SubscriptionTier.TRIAL_3_DAYS },
                                label = { Text("3 Days (PK3D)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedTier == SubscriptionTier.LIFETIME,
                                onClick = { selectedTier = SubscriptionTier.LIFETIME },
                                label = { Text("Lifetime (PKLT)", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (codeInput.isNotBlank()) {
                                        scope.launch {
                                            val res = viewModel.addCustomAdminActivationCode(codeInput, selectedTier)
                                            res.onSuccess { savedCode ->
                                                isError = false
                                                statusMessage = "Added code: $savedCode"
                                                codeInput = ""
                                                copyToClipboard(savedCode)
                                                refreshList()
                                            }.onFailure { err ->
                                                isError = true
                                                statusMessage = err.localizedMessage ?: "Could not add code."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Add", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (codeInput.isNotBlank()) {
                                        scope.launch {
                                            val res = viewModel.revokeOrDeleteAdminActivationCode(codeInput, deleteDocument = false)
                                            res.onSuccess { disabledCode ->
                                                isError = false
                                                statusMessage = "Disabled code: $disabledCode"
                                                codeInput = ""
                                                refreshList()
                                            }.onFailure { err ->
                                                isError = true
                                                statusMessage = err.localizedMessage ?: "Could not disable code."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Disable", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                    )
                }

                // Cloud Codes List
                Text(
                    text = "Cloud Managed & Claimed Codes (${codes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (codes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cloud-created or claimed codes yet.\nYour 200 SHA-256 protected codes are active, or tap Generate above to create new cloud codes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(codes, key = { it.code }) { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        item.isRevoked -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                        item.isClaimed -> Color(0xFF10B981).copy(alpha = 0.4f)
                                        else -> pocketColors.cardBorder
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.code,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (item.tier == SubscriptionTier.LIFETIME.name) {
                                                    Color(0xFF10B981).copy(alpha = 0.16f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                }
                                            ) {
                                                Text(
                                                    text = if (item.tier == SubscriptionTier.LIFETIME.name) "LIFETIME" else "3 DAYS PREMIUM",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.tier == SubscriptionTier.LIFETIME.name) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        val statusText = when {
                                            item.isRevoked -> "Disabled / Revoked"
                                            item.isClaimed -> "Claimed by ${item.claimedByEmail.ifBlank { item.claimedByUid }}"
                                            else -> "Available (Unclaimed)"
                                        }
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = when {
                                                item.isRevoked -> MaterialTheme.colorScheme.error
                                                item.isClaimed -> Color(0xFF10B981)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { copyToClipboard(item.code) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy code",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    viewModel.revokeOrDeleteAdminActivationCode(
                                                        rawCode = item.code,
                                                        deleteDocument = item.isRevoked
                                                    ).onSuccess {
                                                        statusMessage = if (item.isRevoked) "Deleted ${item.code}" else "Disabled ${item.code}"
                                                        refreshList()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isRevoked) Icons.Default.DeleteForever else Icons.Default.Block,
                                                contentDescription = if (item.isRevoked) "Delete code" else "Disable code",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(17.dp)
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
    }
}
