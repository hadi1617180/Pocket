package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SubscriptionTier
import com.example.ui.PocketUiState
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpgradeToPremiumDialog(
    uiState: PocketUiState,
    onDismiss: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onGoogleSignedIn: (FirebaseUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }

    val isAlreadyPremium = uiState.isSubscriptionActive
    val activeTier = uiState.activeTier
    val expiryDateString = remember(uiState.settings.subscriptionExpiresAt) {
        uiState.settings.subscriptionExpiresAt?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFFF15A24).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .testTag("upgrade_premium_dialog"),
            color = Color(0xFF14171D),
            contentColor = Color(0xFFE2E8F0)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Compact Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Premium Crown",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pocket Premium",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isAlreadyPremium) {
                                    if (activeTier == SubscriptionTier.LIFETIME) "Lifetime Unlocked" else "3 Days Premium Active"
                                } else {
                                    "Upgrade & Activation"
                                },
                                fontSize = 11.sp,
                                color = if (isAlreadyPremium) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("upgrade_dialog_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Optional Active Banner or Limit Trigger Reason Banner
                if (isAlreadyPremium) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (activeTier == SubscriptionTier.LIFETIME) {
                                    "Lifetime Premium Active • All features unlocked"
                                } else {
                                    "3 Days Premium Active • Expires ${expiryDateString ?: "soon"}"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (!uiState.upgradePromptReason.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF15A24).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF15A24).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFF15A24),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = uiState.upgradePromptReason,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFFFFEDD5),
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. Google Sign-In / Account Link Section at Top
                val hasGoogle = !uiState.googleAccountEmail.isNullOrBlank()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (hasGoogle) Color(0xFF0F2E22) else Color(0xFF2C1E14),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hasGoogle) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF97316).copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (hasGoogle) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (hasGoogle) Color(0xFF10B981) else Color(0xFFF97316),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (hasGoogle) {
                                    "Google Account Linked (${uiState.googleAccountEmail})"
                                } else {
                                    "Google Sign-In Required to Bind License"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = if (hasGoogle) Color(0xFF34D399) else Color(0xFFFDBA74),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!hasGoogle) {
                            Spacer(modifier = Modifier.height(6.dp))
                            GoogleSignInPrimaryButton(
                                isSyncing = uiState.isCloudSyncing,
                                onGoogleSignedIn = onGoogleSignedIn,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 3. Step 1: WhatsApp Developer Contact
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E232D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Step 1",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF15A24),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Contact developer to receive official 3 Days Premium or lifetime code",
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Button(
                            onClick = {
                                val userEmail = uiState.googleAccountEmail ?: "Not signed in yet"
                                val msg = "Hi Hadi, I want to upgrade Pocket to Premium. My Google account is: $userEmail"
                                val encoded = Uri.encode(msg)
                                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/8801991617180?text=$encoded"))
                                context.startActivity(waIntent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("whatsapp_upgrade_btn"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "WhatsApp Icon",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp Developer (+8801991617180)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 4. Step 2: Activation Code Box & Submit
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E232D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Step 2",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF15A24),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Enter the activation code you got from the developer.",
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputCode,
                                onValueChange = { inputCode = it.uppercase() },
                                placeholder = {
                                    Text(
                                        text = "XXXX-XXXX-XXXX",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                },
                                textStyle = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                                    .testTag("activation_code_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFF15A24),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val item = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!item.isNullOrBlank()) {
                                                inputCode = item.trim().uppercase()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("PASTE", fontSize = 10.sp, color = Color(0xFFF15A24), fontWeight = FontWeight.Bold)
                                    }
                                }
                            )

                            Button(
                                onClick = {
                                    if (inputCode.isNotBlank()) {
                                        onRedeemCode(inputCode)
                                    }
                                },
                                enabled = inputCode.isNotBlank() && !uiState.isRedeemingCode,
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("activate_code_submit_btn"),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF15A24)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (uiState.isRedeemingCode) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Activate", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        if (!uiState.activationError.isNullOrBlank()) {
                            Text(
                                text = uiState.activationError,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!uiState.activationSuccess.isNullOrBlank()) {
                            Text(
                                text = uiState.activationSuccess,
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 5. Shortened Benefits List at Bottom (No descriptions, compact 2-column grid)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E232D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Premium Benefits",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )

                        val benefitTitles = listOf(
                            "Unlimited Daily Logs",
                            "Unlimited Wallets",
                            "Unlimited Savings Goals",
                            "Unlimited Recurring Bills",
                            "Unlimited Loan Contacts",
                            "Custom Categories & Edit",
                            "Biometric App Lock",
                            "All Colors & Custom Picker"
                        )

                        benefitTitles.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowItems.forEach { title ->
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
