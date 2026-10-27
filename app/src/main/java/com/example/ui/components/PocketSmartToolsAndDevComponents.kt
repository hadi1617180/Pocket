package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.AccountingEngine
import com.example.data.CategoryEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.data.WalletEntity
import com.example.ui.theme.LocalPocketColors
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * OutlinedTextField wrapper for math-enabled number fields (`+`, `-`, `×`, `÷`).
 * Ensures that whenever an operator symbol (`+`, `-`, `×`, `÷`) or `=` is pressed in `MiniCalculatorBar`,
 * the text cursor / caret automatically moves to the very end (after the symbol) instead of staying before it.
 */
@Composable
fun MathAmountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    shape: androidx.compose.ui.graphics.Shape = OutlinedTextFieldDefaults.shape
) {
    var tfv by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    // When `value` changes externally (e.g. pressing +, -, ×, ÷, = in MiniCalculatorBar, or SMS paste),
    // place the cursor right at the end (`value.length`) after the newly inserted symbol.
    LaunchedEffect(value) {
        if (tfv.text != value) {
            tfv = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    val effectiveTfv = if (tfv.text != value) {
        TextFieldValue(text = value, selection = TextRange(value.length))
    } else {
        tfv
    }

    OutlinedTextField(
        value = effectiveTfv,
        onValueChange = { nextTfv ->
            val prevText = effectiveTfv.text
            val newText = nextTfv.text
            // Also handle typing +, -, *, / directly from the numeric keypad so the caret stays at the end if appended
            tfv = if (newText != prevText && newText.endsWith(" ") && newText.trimEnd().lastOrNull() in listOf('+', '-', '×', '÷', '*', '/')) {
                nextTfv.copy(selection = TextRange(newText.length))
            } else {
                nextTfv
            }
            if (newText != value) {
                onValueChange(newText)
            }
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        shape = shape
    )
}

/**
 * 100% Offline MFS / Bank SMS Parser (No SMS Permission Required).
 * Extracts amount, service fee, TrxID/reference, transaction direction, and matching wallet from pasted SMS text.
 */
data class ParsedFinancialSms(
    val amount: Double,
    val feeAmount: Double = 0.0,
    val reference: String = "",
    val inferredType: TransactionType = TransactionType.EXPENSE,
    val matchedWalletId: Long? = null,
    val providerName: String = "",
    val summaryNote: String = ""
)

fun parseFinancialSms(
    rawSms: String,
    wallets: List<WalletEntity>
): ParsedFinancialSms? {
    val text = rawSms.trim()
    if (text.isBlank()) return null
    val lower = text.lowercase(Locale.ENGLISH)

    // 1. Detect Provider / Wallet
    val providerHint = when {
        lower.contains("bkash") -> "bKash"
        lower.contains("nagad") -> "Nagad"
        lower.contains("rocket") || lower.contains("dbbl") -> "Rocket"
        lower.contains("upay") -> "Upay"
        lower.contains("mtb") || lower.contains("mutual trust") -> "MTB"
        lower.contains("brac") || lower.contains("astha") -> "Brac"
        lower.contains("city bank") || lower.contains("citytouch") || lower.contains("amex") -> "City Bank"
        lower.contains("ebl") || lower.contains("eastern bank") -> "EBL"
        lower.contains("islami bank") || lower.contains("ibbl") -> "Islami Bank"
        lower.contains("dutch-bangla") -> "DBBL"
        lower.contains("card") || lower.contains("visa") || lower.contains("mastercard") -> "Card"
        lower.contains("ac ") || lower.contains("a/c") || lower.contains("account") || lower.contains("bank") -> "Bank"
        else -> ""
    }

    val matchedWallet = if (providerHint.isNotBlank()) {
        wallets.firstOrNull { w ->
            w.name.contains(providerHint, ignoreCase = true) ||
                w.iconName.contains(providerHint, ignoreCase = true)
        } ?: wallets.firstOrNull { w ->
            (providerHint == "City Bank" || providerHint == "EBL" || providerHint == "Islami Bank" || providerHint == "DBBL") &&
                (w.name.contains("bank", ignoreCase = true) || w.type.equals("BANK", ignoreCase = true))
        }
    } else null

    // 2. Detect Fee first so we don't confuse Fee with Principal Amount
    val feeRegex = Regex(
        """(?:fee|charge|vat|comm\.?)\s*(?:amount)?\s*[:\-]?\s*(?:tk\.?|bdt|\$|usd)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    val feeMatch = feeRegex.find(text)
    val extractedFee = feeMatch?.groupValues?.getOrNull(1)
        ?.replace(",", "")
        ?.toDoubleOrNull() ?: 0.0

    // 3. Detect Principal Amount (avoid matching Balance or Fee)
    val balanceRanges = Regex(
        """(?:balance|bal|avl\.?\s*bal|current\s*balance)\s*[:\-]?\s*(?:tk\.?|bdt|\$|usd)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?""",
        RegexOption.IGNORE_CASE
    ).findAll(text).map { it.range }.toList()
    val feeRange = feeMatch?.range

    val currencyAmountRegex = Regex(
        """(?:tk\.?|bdt|taka|usd|\$|€|£|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:tk\.?|bdt|taka)""",
        RegexOption.IGNORE_CASE
    )

    var principalAmount: Double? = null
    for (match in currencyAmountRegex.findAll(text)) {
        val range = match.range
        val overlapsFee = feeRange != null && range.first <= feeRange.last && range.last >= feeRange.first
        val overlapsBalance = balanceRanges.any { b -> range.first <= b.last && range.last >= b.first }
        if (!overlapsFee && !overlapsBalance) {
            val rawNum = (match.groupValues[1].ifBlank { match.groupValues[2] }).replace(",", "")
            val parsed = rawNum.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                principalAmount = parsed
                break
            }
        }
    }

    if (principalAmount == null) {
        // Fallback: any standalone decimal number > 0 not overlapping fee/balance
        val fallbackNumRegex = Regex("""\b([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b""")
        for (match in fallbackNumRegex.findAll(text)) {
            val range = match.range
            val overlapsFee = feeRange != null && range.first <= feeRange.last && range.last >= feeRange.first
            val overlapsBalance = balanceRanges.any { b -> range.first <= b.last && range.last >= b.first }
            if (!overlapsFee && !overlapsBalance) {
                val rawNum = match.groupValues[1].replace(",", "")
                // Skip 11-digit BD phone numbers starting with 01
                if (rawNum.length == 11 && rawNum.startsWith("01")) continue
                val parsed = rawNum.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    principalAmount = parsed
                    break
                }
            }
        }
    }

    val finalAmount = principalAmount ?: return null

    // 4. Detect TrxID / Reference
    val trxRegex = Regex(
        """(?:trxid|txnid|txn\s*id|trx\s*id|trans\s*id|ref(?:erence)?\s*(?:no|id)?)\s*[:#\-]?\s*([A-Za-z0-9]{5,24})""",
        RegexOption.IGNORE_CASE
    )
    val extractedRef = trxRegex.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    // 5. Detect Transaction Type (INCOME vs EXPENSE)
    val inferredType = when {
        lower.contains("you have received") ||
            lower.contains("cash in") ||
            lower.contains("received tk") ||
            lower.contains("received bdt") ||
            lower.contains("money received") ||
            lower.contains("credited") ||
            lower.contains("deposited") ||
            lower.contains("refund") -> TransactionType.INCOME
        else -> TransactionType.EXPENSE
    }

    // 6. Build concise summary note
    val actionLabel = when {
        lower.contains("cash out") -> "Cash Out"
        lower.contains("cash in") -> "Cash In"
        lower.contains("send money") -> "Send Money"
        lower.contains("payment") -> "Merchant Payment"
        lower.contains("pay bill") || lower.contains("bill pay") -> "Bill Pay"
        lower.contains("mobile recharge") || lower.contains("airtime") -> "Mobile Recharge"
        lower.contains("add money") -> "Add Money"
        lower.contains("received") || lower.contains("credited") -> "Received"
        lower.contains("purchase") || lower.contains("pos") -> "Card Purchase"
        lower.contains("withdrawn") || lower.contains("atm") -> "ATM Withdrawal"
        else -> "SMS Entry"
    }
    val summaryNote = buildString {
        if (providerHint.isNotBlank()) append("$providerHint ")
        append(actionLabel)
    }.trim()

    return ParsedFinancialSms(
        amount = AccountingEngine.roundCurrency(finalAmount),
        feeAmount = AccountingEngine.roundCurrency(extractedFee),
        reference = extractedRef,
        inferredType = inferredType,
        matchedWalletId = matchedWallet?.id,
        providerName = providerHint,
        summaryNote = summaryNote
    )
}

@Composable
fun SmsPasteButtonAndDialog(
    wallets: List<WalletEntity>,
    onSmsParsed: (ParsedFinancialSms) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    var showDialog by remember { mutableStateOf(false) }
    var smsInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        onClick = {
            // Pre-populate from clipboard if available when opening dialog
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if (clipText.isNotBlank()) {
                val quickParsed = parseFinancialSms(clipText, wallets)
                if (quickParsed != null) {
                    onSmsParsed(quickParsed)
                    return@OutlinedButton
                }
                smsInput = clipText
            }
            errorText = null
            showDialog = true
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier.testTag("paste_sms_button")
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = "Paste MFS or Bank SMS",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Paste SMS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Text(
                    text = "Paste MFS / Bank SMS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste a bKash, Nagad, Rocket, or Bank SMS below. Pocket parses amount, fee, TrxID, and wallet 100% offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = smsInput,
                        onValueChange = {
                            smsInput = it
                            errorText = null
                        },
                        label = { Text("SMS Text") },
                        placeholder = {
                            Text("e.g. Cash Out Tk 2,000.00 successful. Fee Tk 37.00. TrxID ABC123XYZ")
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_parser_input_field")
                    )
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = parseFinancialSms(smsInput, wallets)
                        if (parsed != null) {
                            onSmsParsed(parsed)
                            showDialog = false
                            smsInput = ""
                        } else {
                            errorText = "Could not detect a valid amount in this SMS."
                        }
                    },
                    modifier = Modifier.testTag("confirm_parse_sms_button")
                ) {
                    Text("Auto-Fill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Compressed Photo / Receipt Attachment Row (Item 8).
 * Lets the user attach a receipt, warranty slip, bill, or MFS screenshot to any transaction or loan.
 */
@Composable
fun AttachmentPhotoRow(
    attachmentUri: String,
    onAttachmentChanged: (String) -> Unit,
    label: String = "Receipt / Bill Photo (Optional)",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    var showFullScreenViewer by remember { mutableStateOf(false) }

    val imagePicker = rememberUniversalImagePickerLauncher { uri ->
        val savedPath = saveUprightImageFromUri(
            context = context,
            uri = uri,
            subDir = "attachments",
            prefix = "attachment",
            maxDimension = 680,
            compressAsJpeg = true,
            encodeAsDataUri = true
        )
        if (!savedPath.isNullOrBlank()) {
            onAttachmentChanged(savedPath)
        }
    }

    val cleanPath = attachmentUri.removePrefix("CUSTOM_URI:").trim()
    val bitmap = rememberDecodedImageBitmap(cleanPath, maxDimension = 512)
    val hasValidPhoto = bitmap != null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pocketColors.surfaceElevated,
        border = BorderStroke(1.dp, pocketColors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Attached receipt photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .clickable { showFullScreenViewer = true }
                            .testTag("attachment_thumbnail")
                    )
                    Column {
                        Text(
                            text = "Photo Attached",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap thumbnail to view full size",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Warranty slip, bill, or MFS screenshot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasValidPhoto || cleanPath.isNotBlank()) {
                    IconButton(
                        onClick = { onAttachmentChanged("") },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("remove_attachment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove photo",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                TextButton(
                    onClick = { imagePicker() },
                    modifier = Modifier.testTag("attach_photo_button")
                ) {
                    Text(
                        text = if (hasValidPhoto) "Change" else "Attach",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showFullScreenViewer && hasValidPhoto) {
        FullScreenPhotoViewerDialog(
            photoUri = cleanPath,
            onDismiss = { showFullScreenViewer = false }
        )
    }
}

@Composable
fun FullScreenPhotoViewerDialog(
    photoUri: String,
    onDismiss: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val cleanPath = photoUri.removePrefix("CUSTOM_URI:").trim()
    val fullBitmap = rememberDecodedImageBitmap(cleanPath, maxDimension = 1600)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = pocketColors.overlaySurface,
            border = BorderStroke(1.dp, pocketColors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attached Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close viewer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap,
                        contentDescription = "Full size attachment",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 460.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Text(
                        text = "Image file not found on device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Custom vector icons for WhatsApp and Facebook rendered in the active accent color.
 */
@Composable
fun WhatsAppVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.095f
        val r = w * 0.40f
        val cx = w * 0.52f
        val cy = h * 0.48f

        // Chat bubble circle
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth)
        )
        // Bottom-left tail of WhatsApp bubble
        val tailPath = Path().apply {
            moveTo(cx - r * 0.65f, cy + r * 0.62f)
            lineTo(w * 0.12f, h * 0.88f)
            lineTo(cx - r * 0.25f, cy + r * 0.88f)
        }
        drawPath(
            path = tailPath,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Phone receiver curve inside bubble
        val receiverPath = Path().apply {
            moveTo(cx - r * 0.36f, cy - r * 0.28f)
            quadraticBezierTo(cx - r * 0.40f, cy + r * 0.28f, cx + r * 0.34f, cy + r * 0.34f)
        }
        drawPath(
            path = receiverPath,
            color = tint,
            style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun FacebookVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.095f

        // Outer circle
        drawCircle(
            color = tint,
            radius = w * 0.43f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = strokeWidth)
        )

        // Stylized 'f' inside circle
        val fPath = Path().apply {
            moveTo(w * 0.60f, h * 0.28f)
            lineTo(w * 0.53f, h * 0.28f)
            quadraticBezierTo(w * 0.46f, h * 0.28f, w * 0.46f, h * 0.37f)
            lineTo(w * 0.46f, h * 0.80f)
            moveTo(w * 0.36f, h * 0.50f)
            lineTo(w * 0.60f, h * 0.50f)
        }
        drawPath(
            path = fPath,
            color = tint,
            style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Developer Info Popup Dialog ("App developed by Abdullahil Hadi" - Item 12).
 * Shows circular-cropped photo with accent border, top-right close button, and clickable Phone, WhatsApp, Email, and Facebook rows.
 */
@Composable
fun DeveloperInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    val accentColor = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = pocketColors.overlaySurface,
            border = BorderStroke(1.dp, pocketColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("developer_info_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top-right close ('X') button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .testTag("close_developer_info_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close developer info",
                        tint = accentColor
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Big circular cropped photo of Abdullahil Hadi with accent color border
                    Image(
                        painter = painterResource(id = R.drawable.developer_hadi_photo),
                        contentDescription = "Abdullahil Hadi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(132.dp)
                            .clip(CircleShape)
                            .border(3.dp, accentColor, CircleShape)
                            .testTag("developer_photo_circle")
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Abdullahil Hadi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "App Developer",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Contact Info Rows
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Phone: +8801991617180
                        DeveloperContactRow(
                            label = "Phone",
                            value = "+8801991617180",
                            iconContent = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+8801991617180")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            testTag = "dev_contact_phone"
                        )

                        // 2. WhatsApp: +8801991617180
                        DeveloperContactRow(
                            label = "WhatsApp",
                            value = "+8801991617180",
                            iconContent = {
                                WhatsAppVectorIcon(
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://api.whatsapp.com/send?phone=8801991617180")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            testTag = "dev_contact_whatsapp"
                        )

                        // 3. Email: hadi280994@gmail.com
                        DeveloperContactRow(
                            label = "Email",
                            value = "hadi280994@gmail.com",
                            iconContent = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:hadi280994@gmail.com")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            testTag = "dev_contact_email"
                        )

                        // 4. Facebook: Abdullahil Hadi -> http://fb.me/hadi280994
                        DeveloperContactRow(
                            label = "Facebook",
                            value = "Abdullahil Hadi",
                            iconContent = {
                                FacebookVectorIcon(
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("http://fb.me/hadi280994")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            testTag = "dev_contact_facebook"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperContactRow(
    label: String,
    value: String,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String
) {
    val pocketColors = LocalPocketColors.current
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pocketColors.overlayCard,
        border = BorderStroke(1.dp, pocketColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Quote-aware CSV line splitter supporting quoted fields with commas (e.g. `"1,250.00"`).
 */
fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == ',' && !inQuotes -> {
                result.add(sb.toString().trim())
                sb.setLength(0)
            }
            else -> sb.append(c)
        }
        i++
    }
    result.add(sb.toString().trim())
    return result
}

private val CSV_DATE_FORMATS = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd",
    "dd/MM/yyyy HH:mm",
    "dd/MM/yyyy",
    "dd-MM-yyyy",
    "MM/dd/yyyy",
    "dd MMM yyyy",
    "dd-MMM-yyyy",
    "dd/MM/yy",
    "dd-MM-yy"
)

fun parseCsvDateMillis(raw: String, fallbackMillis: Long = System.currentTimeMillis()): Long {
    val cleaned = raw.trim().removeSurrounding("\"")
    if (cleaned.isBlank()) return fallbackMillis
    cleaned.toLongOrNull()?.let { num ->
        if (num > 1_000_000_000_000L) return num
        if (num > 1_000_000_000L) return num * 1000L
    }
    for (pattern in CSV_DATE_FORMATS) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
            val parsed = sdf.parse(cleaned)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {
        }
    }
    return fallbackMillis
}

fun parseCsvAmount(raw: String): Double? {
    val trimmed = raw.trim().removeSurrounding("\"")
    if (trimmed.isBlank()) return null
    val isParenthesizedNegative = trimmed.startsWith("(") && trimmed.endsWith(")")
    val isDrSuffix = trimmed.endsWith("DR", ignoreCase = true) || trimmed.endsWith("Debit", ignoreCase = true)
    val cleaned = trimmed
        .replace(Regex("[^0-9.-]"), "")
        .trim()
    val value = cleaned.toDoubleOrNull() ?: return null
    return when {
        isParenthesizedNegative && value > 0 -> -value
        isDrSuffix && value > 0 -> -value
        else -> value
    }
}

/**
 * Interactive Bank / MFS CSV Statement Importer with Column Mapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvBankImportButtonAndDialog(
    wallets: List<WalletEntity>,
    categories: List<CategoryEntity>,
    defaultWalletId: Long?,
    onImportTransactions: (List<TransactionEntity>) -> Unit,
    isFullWidthButton: Boolean = false,
    buttonLabel: String = "CSV",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current
    var showDialog by remember { mutableStateOf(false) }
    var rawCsvText by remember { mutableStateOf("") }
    var fileNameLabel by remember { mutableStateOf("") }
    var hasHeaderRow by remember { mutableStateOf(true) }

    val activeWallets = remember(wallets) { wallets.filter { !it.isArchived } }
    var selectedWalletId by remember(activeWallets, defaultWalletId) {
        mutableStateOf(
            defaultWalletId?.takeIf { id -> activeWallets.any { it.id == id } }
                ?: activeWallets.firstOrNull()?.id
                ?: 1L
        )
    }

    val expenseCategories = remember(categories) {
        categories.filter { it.type.equals(TransactionType.EXPENSE.name, ignoreCase = true) }
    }
    val incomeCategories = remember(categories) {
        categories.filter { it.type.equals(TransactionType.INCOME.name, ignoreCase = true) }
    }
    var defaultExpenseCategoryId by remember(expenseCategories) {
        mutableStateOf(expenseCategories.firstOrNull()?.id ?: 1L)
    }
    var defaultIncomeCategoryId by remember(incomeCategories) {
        mutableStateOf(incomeCategories.firstOrNull()?.id ?: 1L)
    }

    val csvLines = remember(rawCsvText) {
        rawCsvText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(500)
    }

    val parsedGrid = remember(csvLines) {
        csvLines.map { parseCsvLine(it) }.filter { row -> row.any { it.isNotBlank() } }
    }

    val maxColumns = remember(parsedGrid) {
        parsedGrid.maxOfOrNull { it.size } ?: 0
    }

    val headers = remember(parsedGrid, hasHeaderRow, maxColumns) {
        if (parsedGrid.isEmpty() || maxColumns == 0) {
            emptyList()
        } else if (hasHeaderRow) {
            val first = parsedGrid.first()
            (0 until maxColumns).map { idx ->
                first.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Column ${idx + 1}"
            }
        } else {
            (0 until maxColumns).map { idx -> "Column ${idx + 1}" }
        }
    }

    val dataRows = remember(parsedGrid, hasHeaderRow) {
        if (parsedGrid.isEmpty()) emptyList()
        else if (hasHeaderRow) parsedGrid.drop(1)
        else parsedGrid
    }

    var dateColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("date") || h.contains("time")
        }
        mutableIntStateOf(if (auto >= 0) auto else 0)
    }

    var descColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("desc") || h.contains("narrat") || h.contains("particular") ||
                h.contains("note") || h.contains("merchant") || h.contains("detail") || h.contains("remark")
        }
        mutableIntStateOf(if (auto >= 0) auto else 1.coerceAtMost((headers.size - 1).coerceAtLeast(0)))
    }

    var amountMode by remember(headers) {
        val hasDebit = headers.any {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("debit") || h.contains("withdraw") || h == "dr" || h.contains("paid out")
        }
        val hasCredit = headers.any {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("credit") || h.contains("deposit") || h == "cr" || h.contains("paid in")
        }
        mutableStateOf(if (hasDebit && hasCredit) "DEBIT_CREDIT" else "SINGLE")
    }

    var amountColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("amount") || h.contains("taka") || h.contains("bdt") || h.contains("total") || h.contains("value")
        }
        mutableIntStateOf(if (auto >= 0) auto else 2.coerceAtMost((headers.size - 1).coerceAtLeast(0)))
    }

    var debitColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("debit") || h.contains("withdraw") || h == "dr" || h.contains("out")
        }
        mutableIntStateOf(if (auto >= 0) auto else 2.coerceAtMost((headers.size - 1).coerceAtLeast(0)))
    }

    var creditColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("credit") || h.contains("deposit") || h == "cr" || h.contains("in")
        }
        mutableIntStateOf(if (auto >= 0) auto else 3.coerceAtMost((headers.size - 1).coerceAtLeast(0)))
    }

    var typeColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h == "type" || h.contains("dr/cr") || h.contains("direction") || h.contains("txn type")
        }
        mutableIntStateOf(auto) // -1 means None
    }

    var refColIdx by remember(headers) {
        val auto = headers.indexOfFirst {
            val h = it.lowercase(Locale.ENGLISH)
            h.contains("trx") || h.contains("ref") || h.contains("id")
        }
        mutableIntStateOf(auto) // -1 means None
    }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (text.isNotBlank()) {
                    rawCsvText = text
                    fileNameLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "statement.csv"
                }
            }
        }
    }

    val previewTransactions = remember(
        dataRows,
        selectedWalletId,
        defaultExpenseCategoryId,
        defaultIncomeCategoryId,
        dateColIdx,
        descColIdx,
        amountMode,
        amountColIdx,
        debitColIdx,
        creditColIdx,
        typeColIdx,
        refColIdx,
        categories
    ) {
        val now = System.currentTimeMillis()
        dataRows.mapNotNull { row ->
            val rawDate = row.getOrNull(dateColIdx).orEmpty()
            val rawDesc = row.getOrNull(descColIdx).orEmpty().removeSurrounding("\"").trim()
            val rawRef = if (refColIdx >= 0) row.getOrNull(refColIdx).orEmpty().removeSurrounding("\"").trim() else ""
            val rawTypeHint = if (typeColIdx >= 0) row.getOrNull(typeColIdx).orEmpty().lowercase(Locale.ENGLISH) else ""

            val (txType, absAmount) = if (amountMode == "DEBIT_CREDIT") {
                val debitVal = kotlin.math.abs(parseCsvAmount(row.getOrNull(debitColIdx).orEmpty()) ?: 0.0)
                val creditVal = kotlin.math.abs(parseCsvAmount(row.getOrNull(creditColIdx).orEmpty()) ?: 0.0)
                when {
                    debitVal > 0.0 -> TransactionType.EXPENSE to debitVal
                    creditVal > 0.0 -> TransactionType.INCOME to creditVal
                    else -> return@mapNotNull null
                }
            } else {
                val parsedAmt = parseCsvAmount(row.getOrNull(amountColIdx).orEmpty()) ?: return@mapNotNull null
                if (kotlin.math.abs(parsedAmt) < 0.005) return@mapNotNull null
                val inferredType = when {
                    rawTypeHint.contains("cr") || rawTypeHint.contains("income") ||
                        rawTypeHint.contains("deposit") || rawTypeHint.contains("cash in") ||
                        rawTypeHint.contains("received") -> TransactionType.INCOME
                    rawTypeHint.contains("dr") || rawTypeHint.contains("expense") ||
                        rawTypeHint.contains("withdraw") || rawTypeHint.contains("payment") ||
                        rawTypeHint.contains("send") -> TransactionType.EXPENSE
                    parsedAmt < 0.0 -> TransactionType.EXPENSE
                    else -> TransactionType.EXPENSE
                }
                inferredType to kotlin.math.abs(parsedAmt)
            }

            val matchedCatId = categories.firstOrNull { cat ->
                cat.type.equals(txType.name, ignoreCase = true) &&
                    rawDesc.isNotBlank() &&
                    rawDesc.contains(cat.name, ignoreCase = true)
            }?.id ?: if (txType == TransactionType.INCOME) defaultIncomeCategoryId else defaultExpenseCategoryId

            TransactionEntity(
                type = txType.name,
                amount = AccountingEngine.roundCurrency(absAmount),
                walletId = selectedWalletId,
                categoryId = matchedCatId,
                date = parseCsvDateMillis(rawDate, now),
                note = rawDesc.ifBlank { "CSV Import" },
                reference = rawRef
            )
        }
    }

    if (isFullWidthButton) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("csv_import_open_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonLabel, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = { showDialog = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = modifier
                .height(36.dp)
                .testTag("csv_import_open_button")
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = "Import Bank or MFS CSV",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = pocketColors.overlaySurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Import Bank / MFS CSV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close CSV Importer")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select a .CSV statement from your bank or MFS and map its columns to import transactions in bulk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openCsvLauncher.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("csv_pick_file_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick .CSV File")
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (clipText.isNotBlank()) {
                                    rawCsvText = clipText
                                    fileNameLabel = "Clipboard CSV"
                                } else {
                                    rawCsvText = "Date,Description,Debit,Credit,Reference\n2026-10-25,Shwapno Grocery,1250,0,TRX101\n2026-10-26,Salary Credit,0,45000,SAL202"
                                    fileNameLabel = "Sample CSV"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("csv_paste_clipboard_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste / Sample")
                        }
                    }

                    OutlinedTextField(
                        value = rawCsvText,
                        onValueChange = { rawCsvText = it },
                        label = {
                            Text(
                                if (fileNameLabel.isNotBlank()) "CSV Content ($fileNameLabel)"
                                else "Or paste CSV rows here (Date,Description,Amount...)"
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("csv_raw_text_input")
                    )

                    if (headers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "First row is column headers",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = hasHeaderRow,
                                onCheckedChange = { hasHeaderRow = it }
                            )
                        }

                        // Target Wallet Picker
                        Text(
                            text = "Target Wallet",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeWallets.forEach { w ->
                                FilterChip(
                                    selected = selectedWalletId == w.id,
                                    onClick = { selectedWalletId = w.id },
                                    label = { Text(w.name) }
                                )
                            }
                        }

                        // Column Mapping Controls
                        Text(
                            text = "Column Mapping (Tap chip to select column)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        CsvColumnSelectorRow(
                            label = "Date Column",
                            headers = headers,
                            selectedIndex = dateColIdx,
                            allowNone = false,
                            onSelect = { dateColIdx = it }
                        )

                        CsvColumnSelectorRow(
                            label = "Description / Note Column",
                            headers = headers,
                            selectedIndex = descColIdx,
                            allowNone = false,
                            onSelect = { descColIdx = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = amountMode == "SINGLE",
                                onClick = { amountMode = "SINGLE" },
                                label = { Text("Single Amount Col") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = amountMode == "DEBIT_CREDIT",
                                onClick = { amountMode = "DEBIT_CREDIT" },
                                label = { Text("Debit & Credit Cols") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (amountMode == "SINGLE") {
                            CsvColumnSelectorRow(
                                label = "Amount Column",
                                headers = headers,
                                selectedIndex = amountColIdx,
                                allowNone = false,
                                onSelect = { amountColIdx = it }
                            )
                            CsvColumnSelectorRow(
                                label = "Type / Dr-Cr Column (Optional)",
                                headers = headers,
                                selectedIndex = typeColIdx,
                                allowNone = true,
                                onSelect = { typeColIdx = it }
                            )
                        } else {
                            CsvColumnSelectorRow(
                                label = "Debit (Expense) Column",
                                headers = headers,
                                selectedIndex = debitColIdx,
                                allowNone = false,
                                onSelect = { debitColIdx = it }
                            )
                            CsvColumnSelectorRow(
                                label = "Credit (Income) Column",
                                headers = headers,
                                selectedIndex = creditColIdx,
                                allowNone = false,
                                onSelect = { creditColIdx = it }
                            )
                        }

                        CsvColumnSelectorRow(
                            label = "Reference / TrxID Column (Optional)",
                            headers = headers,
                            selectedIndex = refColIdx,
                            allowNone = true,
                            onSelect = { refColIdx = it }
                        )

                        // Preview Summary Box
                        val totalIn = previewTransactions.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
                        val totalOut = previewTransactions.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = pocketColors.surfaceElevated,
                            border = BorderStroke(1.dp, pocketColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Preview: ${previewTransactions.size} valid transactions detected",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Total Income: +${AccountingEngine.formatAmount(totalIn, "৳")} • Total Expense: −${AccountingEngine.formatAmount(totalOut, "৳")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                previewTransactions.take(3).forEach { tx ->
                                    val sign = if (tx.type == TransactionType.INCOME.name) "+" else "−"
                                    Text(
                                        text = "• ${tx.note.take(28)} ($sign${AccountingEngine.formatAmount(tx.amount, "৳")})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (previewTransactions.isNotEmpty()) {
                            onImportTransactions(previewTransactions)
                            showDialog = false
                        }
                    },
                    enabled = previewTransactions.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("csv_import_confirm_button")
                ) {
                    Text("Import ${previewTransactions.size} Rows")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CsvColumnSelectorRow(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    allowNone: Boolean,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (allowNone) {
                FilterChip(
                    selected = selectedIndex == -1,
                    onClick = { onSelect(-1) },
                    label = { Text("None") }
                )
            }
            headers.forEachIndexed { idx, header ->
                FilterChip(
                    selected = selectedIndex == idx,
                    onClick = { onSelect(idx) },
                    label = { Text(header.take(20)) }
                )
            }
        }
    }
}

