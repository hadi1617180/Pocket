package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.lerp
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
import com.example.data.LedgerEntryKind
import com.example.data.TransactionType
import com.example.data.UnifiedLedgerEntry
import com.example.data.WalletEntity
import com.example.ui.BottomNavTab
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.LocalPocketColors
import com.example.ui.theme.ReddishOrangePrimary
import com.example.ui.theme.ReddishOrangePrimaryHighlight
import com.example.ui.theme.TabularMoneyMedium
import com.example.ui.theme.TabularMoneySmall
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ensures the first letter of any text input is always capitalized while typing.
 */
fun capitalizeFirstLetter(text: String): String {
    if (text.isEmpty()) return text
    val first = text[0]
    return if (first.isLowerCase()) {
        first.uppercaseChar() + text.substring(1)
    } else {
        text
    }
}

/**
 * Formats an initial numeric value for text fields: returns an empty string "" when null or 0.0
 * so the user never has to manually delete a pre-filled "0" or "0.0" before typing.
 */
fun formatNumericFieldInitialValue(value: Double?): String {
    if (value == null || abs(value) < 0.0001) return ""
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

/**
 * Sanitizes numeric input and automatically strips leading zeros (e.g. "05" -> "5")
 * so numbers typed into a field never get appended after a stuck leading "0".
 */
fun sanitizeNumericInput(raw: String, allowNegative: Boolean = false): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder()
    var hasDecimal = false
    for (ch in raw) {
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !hasDecimal -> {
                hasDecimal = true
                sb.append(ch)
            }
            ch == '-' && allowNegative && sb.isEmpty() -> sb.append(ch)
        }
    }
    val filtered = sb.toString()
    if (filtered.isEmpty() || filtered == "-") return filtered

    val isNeg = filtered.startsWith("-")
    val unsigned = if (isNeg) filtered.substring(1) else filtered
    val normalizedUnsigned = when {
        unsigned.startsWith(".") -> "0$unsigned"
        unsigned.contains('.') -> {
            val dotIdx = unsigned.indexOf('.')
            val intPart = unsigned.substring(0, dotIdx).trimStart('0').ifEmpty { "0" }
            val fracPart = unsigned.substring(dotIdx + 1)
            "$intPart.$fracPart"
        }
        unsigned.length > 1 && unsigned.startsWith("0") -> {
            unsigned.trimStart('0').ifEmpty { "0" }
        }
        else -> unsigned
    }
    return if (isNeg) "-$normalizedUnsigned" else normalizedUnsigned
}

/**
 * Automatically clears "0", "0.0", or "0.00" to an empty string the moment the numeric text field gains focus.
 */
fun Modifier.clearZeroOnFocus(
    currentValue: String,
    onValueChange: (String) -> Unit
): Modifier = this.onFocusChanged { focusState ->
    if (focusState.isFocused) {
        val trimmed = currentValue.trim()
        if (trimmed == "0" || trimmed == "0.0" || trimmed == "0.00") {
            onValueChange("")
        }
    }
}

private val decodedImageBitmapCache = android.util.LruCache<String, ImageBitmap>(24)

/**
 * Calculates a power-of-2 sample size so BitmapFactory decodes large wallpapers/camera photos
 * within a safe memory envelope before exact scaling.
 */
private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxDimension: Int): Int {
    var inSampleSize = 1
    if (rawWidth <= 0 || rawHeight <= 0 || maxDimension <= 0) return 1
    var halfW = rawWidth / 2
    var halfH = rawHeight / 2
    while (halfW / inSampleSize >= maxDimension && halfH / inSampleSize >= maxDimension) {
        inSampleSize *= 2
    }
    while (max(rawWidth / inSampleSize, rawHeight / inSampleSize) > maxDimension * 2) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

/**
 * Applies EXIF orientation and scales down any dimension exceeding [maxDimension] in a single Matrix pass
 * without manually recycling bitmaps (preventing Canvas recycled-bitmap crashes).
 */
private fun applyExifAndScale(bitmap: Bitmap, orientation: Int, maxDimension: Int): Bitmap {
    if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
    val matrix = Matrix()
    var swapsDimensions = false
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.postRotate(90f)
            swapsDimensions = true
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.postRotate(270f)
            swapsDimensions = true
        }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
            swapsDimensions = true
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
            swapsDimensions = true
        }
    }

    val orientedW = if (swapsDimensions) bitmap.height else bitmap.width
    val orientedH = if (swapsDimensions) bitmap.width else bitmap.height
    val longestEdge = max(orientedW, orientedH)
    if (maxDimension > 0 && longestEdge > maxDimension) {
        val scale = maxDimension.toFloat() / longestEdge.toFloat()
        matrix.postScale(scale, scale)
    }

    if (matrix.isIdentity) return bitmap

    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (_: Throwable) {
        bitmap
    }
}

private fun decodeFileWithBounds(
    file: File,
    maxDimension: Int,
    explicitOrientation: Int = ExifInterface.ORIENTATION_UNDEFINED
): Bitmap? {
    if (!file.exists() || file.length() <= 0L) return null
    return try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(rawW, rawH, maxDimension.coerceAtLeast(128))
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val fileOrientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val orientation = if (
            explicitOrientation != ExifInterface.ORIENTATION_UNDEFINED &&
            explicitOrientation != ExifInterface.ORIENTATION_NORMAL
        ) {
            explicitOrientation
        } else {
            fileOrientation
        }
        applyExifAndScale(raw, orientation, maxDimension)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Decodes a Bitmap from a local file path or content:// URI using two-pass bounds subsampling,
 * rotates it upright according to its EXIF orientation tag, and caps its maximum edge at [maxDimension]
 * so high-resolution photos never trigger OutOfMemoryError or Canvas oversized-bitmap crashes.
 */
fun decodeExifCorrectedBitmap(
    context: Context,
    pathOrUri: String,
    maxDimension: Int = 1024
): Bitmap? {
    if (pathOrUri.isBlank()) return null
    val clean = pathOrUri.removePrefix("CUSTOM_URI:").trim()
    if (clean.isBlank()) return null

    return try {
        when {
            clean.startsWith("data:image/", ignoreCase = true) -> {
                val commaIndex = clean.indexOf(',')
                if (commaIndex <= 0 || commaIndex >= clean.length - 1) return null
                val base64Part = clean.substring(commaIndex + 1).trim()
                val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                if (bytes.isEmpty()) return null
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                val rawW = boundsOptions.outWidth
                val rawH = boundsOptions.outHeight
                if (rawW <= 0 || rawH <= 0) return null
                var sampleSize = 1
                while ((rawW / sampleSize) > maxDimension * 2 || (rawH / sampleSize) > maxDimension * 2) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
                val maxEdge = max(decoded.width, decoded.height)
                if (maxEdge > maxDimension && maxDimension > 0) {
                    val scale = maxDimension.toFloat() / maxEdge.toFloat()
                    val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                } else {
                    decoded
                }
            }
            clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> {
                val cacheDir = File(context.cacheDir, "google_profile_photos").apply { mkdirs() }
                val cachedFile = File(cacheDir, "avatar_${clean.hashCode().toUInt()}.img")
                if (!cachedFile.exists() || cachedFile.length() <= 0L) {
                    val connection = (java.net.URL(clean).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        instanceFollowRedirects = true
                    }
                    connection.inputStream.use { input ->
                        cachedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (cachedFile.exists() && cachedFile.length() > 0L) {
                    decodeFileWithBounds(cachedFile, maxDimension)
                } else {
                    null
                }
            }
            clean.startsWith("/") || clean.startsWith("file://", ignoreCase = true) -> {
                val filePath = clean.removePrefix("file://")
                val file = File(filePath)
                if (file.exists() && file.length() > 0L) {
                    decodeFileWithBounds(file, maxDimension)
                } else {
                    null
                }
            }
            else -> {
                val file = File(clean)
                if (file.exists() && file.length() > 0L) {
                    decodeFileWithBounds(file, maxDimension)
                } else {
                    val uri = Uri.parse(clean)
                    val streamOrientation = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            ExifInterface(stream).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: ExifInterface.ORIENTATION_UNDEFINED
                    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

                    val cacheDir = File(context.cacheDir, "img_decode_tmp").apply { mkdirs() }
                    val tempFile = File(cacheDir, "tmp_${System.nanoTime()}.img")
                    try {
                        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                            true
                        } ?: false
                        if (!copied || !tempFile.exists() || tempFile.length() <= 0L) {
                            null
                        } else {
                            decodeFileWithBounds(
                                file = tempFile,
                                maxDimension = maxDimension,
                                explicitOrientation = streamOrientation
                            )
                        }
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                }
            }
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Loads and caches an [ImageBitmap] asynchronously on Dispatchers.IO with safe downsampling,
 * returning cached bitmaps immediately without blocking the main UI thread.
 */
@Composable
fun rememberDecodedImageBitmap(
    pathOrUri: String,
    maxDimension: Int = 512
): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = remember(pathOrUri, maxDimension) {
        val clean = pathOrUri.removePrefix("CUSTOM_URI:").trim()
        if (clean.isEmpty()) "" else "$clean@$maxDimension"
    }
    if (cacheKey.isEmpty()) return null

    var imageBitmap by remember(cacheKey) {
        mutableStateOf(decodedImageBitmapCache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        val cached = decodedImageBitmapCache.get(cacheKey)
        if (cached != null) {
            imageBitmap = cached
        } else {
            val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    decodeExifCorrectedBitmap(
                        context = context,
                        pathOrUri = pathOrUri,
                        maxDimension = maxDimension
                    )?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
            if (decoded != null) {
                decodedImageBitmapCache.put(cacheKey, decoded)
            }
            imageBitmap = decoded
        }
    }

    return imageBitmap
}

/**
 * Encodes a [Bitmap] into a compact, portable `data:image/jpeg;base64,...` data URI so receipt thumbnails
 * and custom wallet icons survive JSON backup/restore and Firestore cloud sync across devices.
 */
fun encodeBitmapToBase64DataUri(
    bitmap: Bitmap,
    maxDimension: Int = 640,
    initialQuality: Int = 74,
    maxBase64Chars: Int = 180_000
): String? {
    return try {
        val maxEdge = max(bitmap.width, bitmap.height)
        val scaled = if (maxEdge > maxDimension && maxDimension > 0) {
            val ratio = maxDimension.toFloat() / maxEdge.toFloat()
            val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }
        var quality = initialQuality.coerceIn(40, 92)
        var encoded: String
        do {
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            encoded = "data:image/jpeg;base64,$b64"
            quality -= 12
        } while (encoded.length > maxBase64Chars && quality >= 36)
        encoded
    } catch (_: Throwable) {
        null
    }
}

/**
 * Saves an already-cropped or processed Bitmap to internal app storage and returns its absolute file path,
 * or returns a compact Base64 data URI when [encodeAsDataUri] is enabled.
 */
fun saveBitmapToInternalStorage(
    context: Context,
    bitmap: Bitmap,
    subDir: String,
    prefix: String,
    compressAsJpeg: Boolean = false,
    encodeAsDataUri: Boolean = false
): String? {
    if (encodeAsDataUri) {
        val dataUri = encodeBitmapToBase64DataUri(
            bitmap = bitmap,
            maxDimension = if (compressAsJpeg) 640 else 256,
            initialQuality = if (compressAsJpeg) 74 else 84
        )
        if (!dataUri.isNullOrBlank()) return dataUri
    }
    return try {
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val ext = if (compressAsJpeg) "jpg" else "png"
        val format = if (compressAsJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val quality = if (compressAsJpeg) 88 else 100
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        dest.outputStream().use { out ->
            bitmap.compress(format, quality, out)
        }
        if (dest.exists() && dest.length() > 0L) dest.absolutePath else null
    } catch (_: Throwable) {
        null
    }
}

/**
 * Decodes an image from a picked content:// Uri with safe downsampling, applies EXIF orientation
 * correction so it is guaranteed upright, and writes it to internal storage (or encodes as Base64 data URI).
 */
fun saveUprightImageFromUri(
    context: Context,
    uri: Uri,
    subDir: String,
    prefix: String,
    maxDimension: Int = 1024,
    compressAsJpeg: Boolean = false,
    encodeAsDataUri: Boolean = false
): String? {
    return try {
        val correctedBitmap = decodeExifCorrectedBitmap(
            context = context,
            pathOrUri = uri.toString(),
            maxDimension = maxDimension
        ) ?: return null
        val savedPath = saveBitmapToInternalStorage(
            context = context,
            bitmap = correctedBitmap,
            subDir = subDir,
            prefix = prefix,
            compressAsJpeg = compressAsJpeg,
            encodeAsDataUri = encodeAsDataUri
        )
        if (savedPath != null) {
            runCatching {
                decodedImageBitmapCache.put("$savedPath@$maxDimension", correctedBitmap.asImageBitmap())
            }
        }
        savedPath
    } catch (_: Throwable) {
        null
    }
}

/**
 * Crops the source bitmap to a 1:1 square according to the user's pan and zoom in the square viewport.
 */
fun cropBitmapToSquare(
    source: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    viewportSizePx: Float,
    outputSizePx: Int = 512
): Bitmap {
    if (viewportSizePx <= 0f || source.width <= 0 || source.height <= 0) return source
    val minDim = min(source.width, source.height).toFloat()
    val baseScale = viewportSizePx / minDim
    val totalScale = (baseScale * zoom).coerceAtLeast(0.01f)

    // Center of viewport corresponds to source center minus offset / totalScale
    val srcCenterX = source.width / 2f - (offsetX / totalScale)
    val srcCenterY = source.height / 2f - (offsetY / totalScale)
    val srcCropSide = (viewportSizePx / totalScale).coerceIn(1f, minDim)

    val halfSide = srcCropSide / 2f
    val left = (srcCenterX - halfSide).roundToInt().coerceIn(0, (source.width - srcCropSide.roundToInt()).coerceAtLeast(0))
    val top = (srcCenterY - halfSide).roundToInt().coerceIn(0, (source.height - srcCropSide.roundToInt()).coerceAtLeast(0))
    val sideInt = srcCropSide.roundToInt().coerceAtLeast(1)
        .coerceAtMost(source.width - left)
        .coerceAtMost(source.height - top)

    return runCatching {
        val cropped = Bitmap.createBitmap(source, left, top, sideInt, sideInt)
        Bitmap.createScaledBitmap(cropped, outputSizePx, outputSizePx, true)
    }.getOrDefault(source)
}

/**
 * Interactive 1:1 square ratio image crop dialog with a circular outline guide for user profile photos.
 */
@Composable
fun ProfilePhotoCropDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val pocketColors = LocalPocketColors.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(600f) }

    val minSrc = min(sourceBitmap.width, sourceBitmap.height).toFloat().coerceAtLeast(1f)
    val baseScale = viewportPx / minSrc
    val renderedWidth = sourceBitmap.width * baseScale * zoom
    val renderedHeight = sourceBitmap.height * baseScale * zoom
    val maxOffsetX = ((renderedWidth - viewportPx) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((renderedHeight - viewportPx) / 2f).coerceAtLeast(0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text(
                text = "Crop Profile Photo (1:1)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Drag and zoom to position your photo inside the 1:1 square and circle outline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // 1:1 Square Crop Box with Circular Outline Guide
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clipToBounds()
                        .background(Color.Black)
                        .border(1.5.dp, pocketColors.cardBorder, RoundedCornerShape(16.dp))
                        .onSizeChanged { size ->
                            if (size.width > 0) {
                                viewportPx = size.width.toFloat()
                            }
                        }
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                zoom = newZoom
                                val curRenderedW = sourceBitmap.width * (viewportPx / minSrc) * newZoom
                                val curRenderedH = sourceBitmap.height * (viewportPx / minSrc) * newZoom
                                val boundX = ((curRenderedW - viewportPx) / 2f).coerceAtLeast(0f)
                                val boundY = ((curRenderedH - viewportPx) / 2f).coerceAtLeast(0f)
                                offsetX = (offsetX + pan.x).coerceIn(-boundX, boundX)
                                offsetY = (offsetY + pan.y).coerceIn(-boundY, boundY)
                            }
                        }
                        .testTag("profile_photo_crop_viewport"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = "Crop preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                translationY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                            }
                    )

                    // Darkened exterior mask outside the circle + crisp 1:1 square & circle outline
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val side = min(size.width, size.height)
                        val circlePath = Path().apply {
                            addOval(
                                Rect(
                                    left = (size.width - side) / 2f + 4.dp.toPx(),
                                    top = (size.height - side) / 2f + 4.dp.toPx(),
                                    right = (size.width + side) / 2f - 4.dp.toPx(),
                                    bottom = (size.height + side) / 2f - 4.dp.toPx()
                                )
                            )
                        }
                        clipPath(circlePath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.55f))
                        }
                        // 1:1 Square frame outline
                        drawRect(
                            color = Color.White.copy(alpha = 0.45f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Circle outline inside the 1:1 square
                        drawCircle(
                            color = primaryColor,
                            radius = (side / 2f) - 4.dp.toPx(),
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                // Zoom Slider & Reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    PocketThinSlider(
                        value = zoom,
                        onValueChange = { newZoom ->
                            zoom = newZoom
                            val curRenderedW = sourceBitmap.width * (viewportPx / minSrc) * newZoom
                            val curRenderedH = sourceBitmap.height * (viewportPx / minSrc) * newZoom
                            val boundX = ((curRenderedW - viewportPx) / 2f).coerceAtLeast(0f)
                            val boundY = ((curRenderedH - viewportPx) / 2f).coerceAtLeast(0f)
                            offsetX = offsetX.coerceIn(-boundX, boundX)
                            offsetY = offsetY.coerceIn(-boundY, boundY)
                        },
                        valueRange = 1f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_photo_crop_zoom_slider")
                    )
                    TextButton(
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Text("Reset")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cropped = cropBitmapToSquare(
                        source = sourceBitmap,
                        zoom = zoom,
                        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
                        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY),
                        viewportSizePx = viewportPx,
                        outputSizePx = 512
                    )
                    onCropConfirmed(cropped)
                },
                modifier = Modifier.testTag("confirm_crop_profile_photo_button")
            ) {
                Text("Crop & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Crops the source bitmap to a specific target aspect ratio (width / height, e.g. matching device screen dimensions)
 * based on user pan and zoom within the viewport.
 */
fun cropBitmapToAspectRatio(
    source: Bitmap,
    targetAspectRatio: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    maxTargetWidthPx: Int = 1080
): Bitmap {
    if (viewportWidthPx <= 0f || viewportHeightPx <= 0f || source.width <= 0 || source.height <= 0) return source
    val safeAspect = if (targetAspectRatio > 0f) targetAspectRatio else (viewportWidthPx / viewportHeightPx)

    // Scale required so image fills/covers the entire viewport at zoom = 1.0
    val scaleToCover = max(viewportWidthPx / source.width.toFloat(), viewportHeightPx / source.height.toFloat())
    val totalScale = (scaleToCover * zoom).coerceAtLeast(0.001f)

    // Center of viewport in source coordinates
    val srcCenterX = source.width / 2f - (offsetX / totalScale)
    val srcCenterY = source.height / 2f - (offsetY / totalScale)

    // Dimensions of crop region in source image pixels
    val srcCropWidth = (viewportWidthPx / totalScale).coerceIn(1f, source.width.toFloat())
    val srcCropHeight = (viewportHeightPx / totalScale).coerceIn(1f, source.height.toFloat())

    val halfW = srcCropWidth / 2f
    val halfH = srcCropHeight / 2f

    val maxLeft = (source.width - srcCropWidth.roundToInt()).coerceAtLeast(0)
    val maxTop = (source.height - srcCropHeight.roundToInt()).coerceAtLeast(0)

    val left = (srcCenterX - halfW).roundToInt().coerceIn(0, maxLeft)
    val top = (srcCenterY - halfH).roundToInt().coerceIn(0, maxTop)

    val widthInt = srcCropWidth.roundToInt().coerceAtLeast(1)
        .coerceAtMost(source.width - left)
    val heightInt = srcCropHeight.roundToInt().coerceAtLeast(1)
        .coerceAtMost(source.height - top)

    return runCatching {
        val cropped = Bitmap.createBitmap(source, left, top, widthInt, heightInt)
        val outWidth = maxTargetWidthPx.coerceAtMost(maxOf(720, widthInt))
        val outHeight = (outWidth / safeAspect).roundToInt().coerceAtLeast(outWidth)
        Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)
    }.getOrDefault(source)
}

/**
 * Interactive wallpaper crop dialog allowing the user to pick, pan, zoom, and crop an image
 * according to the exact screen size / aspect ratio of their device.
 */
@Composable
fun WallpaperCropDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val pocketColors = LocalPocketColors.current

    // Calculate the device's actual screen aspect ratio (width / height)
    val displayMetrics = remember(context) { context.resources.displayMetrics }
    val screenWidth = displayMetrics.widthPixels.toFloat()
    val screenHeight = displayMetrics.heightPixels.toFloat()
    val targetAspectRatio = remember(screenWidth, screenHeight) {
        if (screenWidth > 0f && screenHeight > 0f) {
            (screenWidth / screenHeight).coerceIn(0.40f, 0.75f)
        } else {
            9f / 19.5f
        }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(450f) }
    var viewportHeightPx by remember { mutableFloatStateOf(950f) }

    val baseScale = max(
        viewportWidthPx / sourceBitmap.width.toFloat(),
        viewportHeightPx / sourceBitmap.height.toFloat()
    )
    val renderedWidth = sourceBitmap.width * baseScale * zoom
    val renderedHeight = sourceBitmap.height * baseScale * zoom
    val maxOffsetX = ((renderedWidth - viewportWidthPx) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((renderedHeight - viewportHeightPx) / 2f).coerceAtLeast(0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text(
                text = "Crop Wallpaper to Screen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pan and zoom inside the phone screen frame to position your wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Phone mockup screen viewport matching device aspect ratio
                val previewWidth = 160.dp
                val previewHeight = (160.dp / targetAspectRatio).coerceIn(240.dp, 330.dp)

                Box(
                    modifier = Modifier
                        .size(width = previewWidth, height = previewHeight)
                        .clip(RoundedCornerShape(22.dp))
                        .clipToBounds()
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                        .onSizeChanged { size ->
                            if (size.width > 0 && size.height > 0) {
                                viewportWidthPx = size.width.toFloat()
                                viewportHeightPx = size.height.toFloat()
                            }
                        }
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                zoom = newZoom
                                val curScale = max(
                                    viewportWidthPx / sourceBitmap.width.toFloat(),
                                    viewportHeightPx / sourceBitmap.height.toFloat()
                                ) * newZoom
                                val curRenderedW = sourceBitmap.width * curScale
                                val curRenderedH = sourceBitmap.height * curScale
                                val boundX = ((curRenderedW - viewportWidthPx) / 2f).coerceAtLeast(0f)
                                val boundY = ((curRenderedH - viewportHeightPx) / 2f).coerceAtLeast(0f)
                                offsetX = (offsetX + pan.x).coerceIn(-boundX, boundX)
                                offsetY = (offsetY + pan.y).coerceIn(-boundY, boundY)
                            }
                        }
                        .testTag("wallpaper_crop_viewport"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = "Wallpaper crop preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                translationY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                            }
                    )

                    // Phone frame details: Camera punch hole & gesture pill guide
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .size(width = 42.dp, height = 3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                    )

                    // Subtle rule of thirds grid
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 1.dp.toPx()
                        val col1 = size.width / 3f
                        val col2 = size.width * 2f / 3f
                        val row1 = size.height / 3f
                        val row2 = size.height * 2f / 3f

                        drawLine(Color.White.copy(alpha = 0.2f), Offset(col1, 0f), Offset(col1, size.height), strokeW)
                        drawLine(Color.White.copy(alpha = 0.2f), Offset(col2, 0f), Offset(col2, size.height), strokeW)
                        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, row1), Offset(size.width, row1), strokeW)
                        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, row2), Offset(size.width, row2), strokeW)
                    }
                }

                // Zoom Slider & Reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    PocketThinSlider(
                        value = zoom,
                        onValueChange = { newZoom ->
                            zoom = newZoom
                            val curScale = max(
                                viewportWidthPx / sourceBitmap.width.toFloat(),
                                viewportHeightPx / sourceBitmap.height.toFloat()
                            ) * newZoom
                            val curRenderedW = sourceBitmap.width * curScale
                            val curRenderedH = sourceBitmap.height * curScale
                            val boundX = ((curRenderedW - viewportWidthPx) / 2f).coerceAtLeast(0f)
                            val boundY = ((curRenderedH - viewportHeightPx) / 2f).coerceAtLeast(0f)
                            offsetX = offsetX.coerceIn(-boundX, boundX)
                            offsetY = offsetY.coerceIn(-boundY, boundY)
                        },
                        valueRange = 1f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wallpaper_crop_zoom_slider")
                    )
                    TextButton(
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Text("Reset")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cropped = cropBitmapToAspectRatio(
                        source = sourceBitmap,
                        targetAspectRatio = targetAspectRatio,
                        zoom = zoom,
                        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
                        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY),
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        maxTargetWidthPx = 1080
                    )
                    onCropConfirmed(cropped)
                },
                modifier = Modifier.testTag("confirm_crop_wallpaper_button")
            ) {
                Text("Set Wallpaper")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class WalletIconOption(
    val key: String,
    val label: String,
    val defaultColorHex: String,
    val drawableRes: Int
)

val WalletIconPickerOptions = listOf(
    WalletIconOption("CASH", "Cash", "#2E9E5B", R.drawable.wallet_icon_cash),
    WalletIconOption("BANK", "Bank", "#2563EB", R.drawable.wallet_icon_bank),
    WalletIconOption("MTB", "MTB", "#006A4E", R.drawable.wallet_icon_mtb),
    WalletIconOption("BRAC", "Brac", "#0072BC", R.drawable.wallet_icon_brac),
    WalletIconOption("BKASH", "Bkash", "#E2136E", R.drawable.wallet_icon_bkash),
    WalletIconOption("NAGAD", "Nagad", "#F37021", R.drawable.wallet_icon_nagad),
    WalletIconOption("ROCKET", "Rocket", "#8C3494", R.drawable.wallet_icon_rocket),
    WalletIconOption("RAPID_PASS", "Rapid Pass", "#1E3A8A", R.drawable.wallet_icon_rapid_pass),
    WalletIconOption("CARD", "Card", "#DC2626", R.drawable.wallet_icon_card),
    WalletIconOption("SAVINGS", "Savings", "#F15A24", R.drawable.wallet_icon_savings)
)

val AvailableIconNames = listOf(
    "WORK",
    "LAPTOP",
    "CARD_GIFTCARD",
    "PAYMENTS",
    "TRENDING_UP",
    "MONETIZATION_ON",
    "WALLET",
    "ACCOUNT_BALANCE",
    "DIRECTIONS_SUBWAY",
    "PHONE_ANDROID",
    "CREDIT_CARD",
    "RESTAURANT",
    "SHOPPING_BASKET",
    "DIRECTIONS_BUS",
    "HOME",
    "BOLT",
    "SHOPPING_BAG",
    "MEDICATION",
    "MEDICAL_SERVICES",
    "VOLUNTEER_ACTIVISM",
    "BUILD",
    "SCHOOL",
    "MOVIE",
    "STORE",
    "RESTORE",
    "RECEIPT",
    "SWAP_HORIZ",
    "HANDSHAKE",
    "TUNE",
    "MORE_HORIZ"
)

val AvailableColorHexes = listOf(
    "#4ADE80",
    "#F87171",
    "#F15A24",
    "#FF7847",
    "#E2136E",
    "#F37021",
    "#8C3494",
    "#006A4E",
    "#60A5FA",
    "#FBBF24",
    "#2563EB"
)

fun iconForName(name: String): ImageVector {
    return when (name.uppercase(Locale.ENGLISH)) {
        "WALLET" -> Icons.Default.AccountBalanceWallet
        "PAYMENTS", "CLIENT_PAYMENT", "CASH" -> Icons.Default.Payments
        "MONETIZATION_ON", "INTEREST", "PAID" -> Icons.Default.MonetizationOn
        "ACCOUNT_BALANCE", "BANK", "MTB", "MTB_BANK", "BRAC", "BRAC_BANK" -> Icons.Default.AccountBalance
        "DIRECTIONS_SUBWAY", "RAPID_PASS" -> Icons.Default.DirectionsSubway
        "PHONE_ANDROID", "BKASH", "NAGAD" -> Icons.Default.PhoneAndroid
        "ROCKET" -> Icons.Default.RocketLaunch
        "SAVINGS" -> Icons.Default.Savings
        "CREDIT_CARD", "CARD" -> Icons.Default.CreditCard
        "RESTAURANT" -> Icons.Default.Restaurant
        "SHOPPING_BASKET" -> Icons.Default.ShoppingBasket
        "DIRECTIONS_BUS" -> Icons.Default.DirectionsBus
        "HOME" -> Icons.Default.Home
        "BOLT" -> Icons.Default.Bolt
        "SHOPPING_BAG" -> Icons.Default.ShoppingBag
        "MEDICATION" -> Icons.Default.Medication
        "MEDICAL_SERVICES" -> Icons.Default.MedicalServices
        "VOLUNTEER_ACTIVISM" -> Icons.Default.VolunteerActivism
        "BUILD" -> Icons.Default.Build
        "SCHOOL" -> Icons.Default.School
        "MOVIE" -> Icons.Default.Movie
        "WORK" -> Icons.Default.Work
        "LAPTOP" -> Icons.Default.Laptop
        "STORE" -> Icons.Default.Store
        "CARD_GIFTCARD" -> Icons.Default.CardGiftcard
        "RESTORE" -> Icons.Default.Restore
        "TRENDING_UP" -> Icons.AutoMirrored.Filled.TrendingUp
        "TRENDING_DOWN" -> Icons.AutoMirrored.Filled.TrendingDown
        "RECEIPT" -> Icons.AutoMirrored.Filled.ReceiptLong
        "SWAP_HORIZ" -> Icons.AutoMirrored.Filled.CompareArrows
        "HANDSHAKE" -> Icons.Default.Handshake
        "TUNE" -> Icons.Default.Tune
        "MORE_HORIZ" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Category
    }
}

fun parseHexColor(hex: String, fallback: Color = BurgundyPrimary): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or value)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun formatRelativeDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "Today"

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday"

    return try {
        SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(timestamp))
    } catch (_: Exception) {
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(timestamp))
    }
}

/**
 * Pocket App Logo composable using the attached circular wallet-and-taka logo with transparent background.
 */
@Composable
fun PocketBurgundyLogo(
    size: Dp = 44.dp,
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val baseModifier = modifier
        .size(size)
        .background(Color.Transparent)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_pocket_logo),
            contentDescription = if (onClick != null) "Open Settings" else "Pocket Logo",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Circular framed user profile picture (with EXIF rotation correction) and optional name label below it.
 */
@Composable
fun UserProfileBadge(
    userName: String,
    profilePhotoUri: String,
    avatarSize: Dp = 54.dp,
    showNameBelow: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imageBitmap = rememberDecodedImageBitmap(profilePhotoUri, maxDimension = 256)

    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(LocalPocketColors.current.surfaceElevated)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "User profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                val initial = userName.trim().firstOrNull()?.uppercaseChar()?.toString()
                if (!initial.isNullOrBlank()) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User avatar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(avatarSize * 0.52f)
                    )
                }
            }
        }
        if (showNameBelow) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = userName.trim().ifBlank { "User" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 110.dp)
                    .testTag("balances_user_name_text")
            )
        }
    }
}

/**
 * Resolves a wallet icon drawable from the 10 official wallet icons ONLY when rendering a wallet
 * (never for income/expense categories such as Client payment or Interest).
 */
fun resolveWalletDrawableRes(iconName: String, walletNameHint: String = ""): Int? {
    val upperIcon = iconName.uppercase(Locale.ENGLISH).trim()
    val lowerName = walletNameHint.lowercase(Locale.ENGLISH).trim()
    val hasWalletHint = lowerName.isNotEmpty()

    return when {
        upperIcon == "BKASH" || lowerName.contains("bkash") -> R.drawable.wallet_icon_bkash
        upperIcon == "NAGAD" || lowerName.contains("nagad") -> R.drawable.wallet_icon_nagad
        upperIcon == "ROCKET" || lowerName.contains("rocket") -> R.drawable.wallet_icon_rocket
        upperIcon == "MTB" || upperIcon == "MTB_BANK" || lowerName == "mtb" || lowerName.contains("mutual trust") -> R.drawable.wallet_icon_mtb
        upperIcon == "BRAC" || upperIcon == "BRAC_BANK" || lowerName.contains("brac") -> R.drawable.wallet_icon_brac
        upperIcon == "BANK" || (hasWalletHint && (lowerName == "bank" || lowerName == "bank account")) -> R.drawable.wallet_icon_bank
        upperIcon == "RAPID_PASS" || (hasWalletHint && (lowerName.contains("rapid pass") || lowerName.contains("metro"))) -> R.drawable.wallet_icon_rapid_pass
        upperIcon == "CARD" || (hasWalletHint && (lowerName == "card" || lowerName.contains("credit card") || lowerName.contains("debit card"))) -> R.drawable.wallet_icon_card
        (hasWalletHint && lowerName == "savings") || (upperIcon == "SAVINGS" && hasWalletHint) -> R.drawable.wallet_icon_savings
        upperIcon == "CASH" || (hasWalletHint && lowerName == "cash") -> R.drawable.wallet_icon_cash
        else -> null
    }
}

/**
 * Renders the 10 official circular wallet icons with uniform alignment, custom user-attached images from
 * local storage (`CUSTOM_URI:<path>`), or Material category icons (in green for income / red for expense).
 */
@Composable
fun IconBadgeCircle(
    iconName: String,
    colorHex: String,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    walletNameHint: String = ""
) {
    val pocketColors = LocalPocketColors.current
    val isDark = pocketColors.isDark

    if (iconName.startsWith("CUSTOM_URI:")) {
        val path = iconName.removePrefix("CUSTOM_URI:")
        val imageBitmap = rememberDecodedImageBitmap(path, maxDimension = 256)
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = walletNameHint.ifBlank { "Custom wallet icon" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
            return
        }
    }

    val walletDrawable = resolveWalletDrawableRes(iconName, walletNameHint)
    if (walletDrawable != null) {
        val accentColor = MaterialTheme.colorScheme.primary
        // Always render wallet logos in the active accent color (`MaterialTheme.colorScheme.primary`)
        // over a clean dark or light circular badge background.
        // Both white and orange artwork in the default wallet PNGs have high Red (R ≈ 241..255), while the circle background has R ≈ 28..30.
        val accentMatrix = remember(isDark, accentColor) {
            val bgR = if (isDark) 20f else 244f
            val bgG = if (isDark) 20f else 244f
            val bgB = if (isDark) 22f else 246f
            val fgR = accentColor.red * 255f
            val fgG = accentColor.green * 255f
            val fgB = accentColor.blue * 255f
            val span = 217f // 245f - 28f
            val scaleR = (fgR - bgR) / span
            val scaleG = (fgG - bgG) / span
            val scaleB = (fgB - bgB) / span
            val offsetR = bgR - 28f * scaleR
            val offsetG = bgG - 28f * scaleG
            val offsetB = bgB - 28f * scaleB
            ColorMatrix(
                floatArrayOf(
                    scaleR, 0f, 0f, 0f, offsetR,
                    scaleG, 0f, 0f, 0f, offsetG,
                    scaleB, 0f, 0f, 0f, offsetB,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(0.75.dp, pocketColors.cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = walletDrawable),
                contentDescription = walletNameHint.ifBlank { iconName },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(accentMatrix),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        return
    }

    val isWalletIcon = walletNameHint.isNotBlank()
    val tint = if (isWalletIcon) {
        MaterialTheme.colorScheme.primary
    } else {
        parseHexColor(colorHex)
    }
    val badgeBg = if (isWalletIcon) {
        if (isDark) Color(0xFF18181B) else Color(0xFFF4F4F5)
    } else {
        tint.copy(alpha = 0.22f)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(badgeBg)
            .then(
                if (isWalletIcon) Modifier.border(0.75.dp, pocketColors.cardBorder, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconForName(iconName),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Top bar across screens:
 * - On top-level tabs (`onLogoClick != null`): Shows Pocket Logo + "Pocket" app name beside it, without any other tab heading.
 * - On secondary screens (`onBack != null`): Shows Back button + screen title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onLogoClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = LocalPocketColors.current.defaultIcon
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (onLogoClick != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onLogoClick)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketBurgundyLogo(
                        size = 42.dp,
                        onClick = onLogoClick,
                        modifier = Modifier.testTag("top_bar_pocket_logo")
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pocket",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (LocalPocketColors.current.isDark) Color.White else Color.Black,
                        maxLines = 1
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Pocket" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/**
 * Liquid-themed Bottom Navigation Bar with an orange metaball/liquid droplet transition pill.
 */
@Composable
fun LiquidBottomNavigationBar(
    activeTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val pocketColors = LocalPocketColors.current
    val tabs = BottomNavTab.entries
    val selectedIndex = tabs.indexOf(activeTab).coerceAtLeast(0).toFloat()

    // Two spring animations with different stiffness create the authentic liquid stretch & snap effect
    val leadIndex by animateFloatAsState(
        targetValue = selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 380f
        ),
        label = "liquid_nav_lead"
    )
    val trailIndex by animateFloatAsState(
        targetValue = selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = 170f
        ),
        label = "liquid_nav_trail"
    )

    val activePrimary = MaterialTheme.colorScheme.primary
    val activeHighlight = pocketColors.primaryHighlight

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = pocketColors.surfaceElevated,
        border = BorderStroke(1.dp, pocketColors.cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .drawBehind {
                    val tabCount = tabs.size.coerceAtLeast(1)
                    val slotWidth = size.width / tabCount
                    val minIdx = min(leadIndex, trailIndex)
                    val maxIdx = max(leadIndex, trailIndex)

                    val pillHorizontalPad = 10.dp.toPx()
                    val pillVerticalPad = 7.dp.toPx()

                    val leftX = minIdx * slotWidth + pillHorizontalPad
                    val rightX = (maxIdx + 1f) * slotWidth - pillHorizontalPad
                    val pillWidth = (rightX - leftX).coerceAtLeast(slotWidth - pillHorizontalPad * 2f)

                    // Subtle pinch in height when stretched across tabs for liquid volume conservation
                    val stretchAmount = abs(leadIndex - trailIndex).coerceIn(0f, 1.5f)
                    val pinchPx = stretchAmount * 5.dp.toPx()
                    val topY = pillVerticalPad + pinchPx * 0.5f
                    val pillHeight = (size.height - pillVerticalPad * 2f - pinchPx).coerceAtLeast(24f)

                    // Outer liquid accent glow
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                activePrimary.copy(alpha = 0.32f),
                                activeHighlight.copy(alpha = 0.38f),
                                activePrimary.copy(alpha = 0.32f)
                            ),
                            startX = leftX,
                            endX = rightX
                        ),
                        topLeft = Offset(leftX - 3.dp.toPx(), topY - 2.dp.toPx()),
                        size = Size(pillWidth + 6.dp.toPx(), pillHeight + 4.dp.toPx()),
                        cornerRadius = CornerRadius(pillHeight / 2f + 4.dp.toPx(), pillHeight / 2f + 4.dp.toPx())
                    )

                    // Main liquid accent droplet pill
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                activePrimary,
                                activeHighlight
                            ),
                            start = Offset(leftX, topY),
                            end = Offset(rightX, topY + pillHeight)
                        ),
                        topLeft = Offset(leftX, topY),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f)
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab == activeTab
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.12f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_icon_scale_${tab.name}"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tab_color_${tab.name}"
                    )

                    val icon = when (tab) {
                        BottomNavTab.BALANCES -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                        BottomNavTab.RECENT_TRANSACTIONS -> if (selected) Icons.Filled.History else Icons.Outlined.History
                        BottomNavTab.REPORTS -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
                        BottomNavTab.LOANS -> if (selected) Icons.Filled.Handshake else Icons.Outlined.Handshake
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onSelectTab(tab) }
                            .testTag("bottom_nav_${tab.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(iconScale)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSelectorPill(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val label = remember(year, month) {
        SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time).uppercase(Locale.ENGLISH)
    }

    Surface(
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        shape = RoundedCornerShape(16.dp),
        color = LocalPocketColors.current.surfaceElevated,
        border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("prev_month_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("next_month_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next month",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerEntryRowCard(
    entry: UnifiedLedgerEntry,
    currencySymbol: String,
    dateFormatPattern: String = "dd MMM yyyy",
    runningBalanceAfter: Double? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val (amountColor, signPrefix) = when (entry.kind) {
        LedgerEntryKind.INCOME -> pocketColors.incomeText to "+"
        LedgerEntryKind.EXPENSE_REVERSAL -> pocketColors.incomeText to "+"
        LedgerEntryKind.EXPENSE -> pocketColors.expenseText to "−"
        LedgerEntryKind.TRANSFER -> pocketColors.transferText to "⇄ "
        LedgerEntryKind.LOAN_PRINCIPAL -> {
            if (entry.signedAmount >= 0) pocketColors.loanText to "+" else pocketColors.loanText to "−"
        }
        LedgerEntryKind.ADJUSTMENT -> {
            if (entry.signedAmount >= 0) pocketColors.incomeText to "+" else pocketColors.expenseText to "−"
        }
    }

    val formattedAmount = remember(entry.signedAmount, currencySymbol, entry.kind) {
        val absText = AccountingEngine.formatMoney(
            kotlin.math.abs(entry.signedAmount),
            symbol = currencySymbol,
            showSign = false
        )
        "$signPrefix$absText"
    }

    val dateLabel = remember(entry.date, dateFormatPattern) {
        formatRelativeDate(entry.date, dateFormatPattern)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    if (selectionMode) performPocketHaptic(view)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        performPocketHaptic(view, isLongPress = true)
                        it()
                    }
                }
            )
            .testTag("ledger_row_${entry.uniqueKey}"),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else pocketColors.mutedText,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            IconBadgeCircle(
                iconName = entry.iconName,
                colorHex = entry.colorHex,
                size = 42.dp,
                iconSize = 20.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!entry.badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = pocketColors.surfaceElevated
                        ) {
                            Text(
                                text = entry.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (entry.attachmentUri.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Has attached receipt photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                                    .size(13.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.walletName}${if (entry.secondaryWalletName != null) " → ${entry.secondaryWalletName}" else ""} · $dateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = pocketColors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = TabularMoneyMedium,
                    color = amountColor
                )
                if (entry.kind == LedgerEntryKind.TRANSFER && entry.feeAmount > 0.0) {
                    Text(
                        text = "Fee: ${AccountingEngine.formatMoney(entry.feeAmount, currencySymbol)}",
                        style = TabularMoneySmall,
                        color = pocketColors.expenseText
                    )
                }
                if (runningBalanceAfter != null) {
                    Text(
                        text = "Bal: ${AccountingEngine.formatMoney(runningBalanceAfter, currencySymbol)}",
                        style = TabularMoneySmall,
                        color = pocketColors.mutedText
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateBox(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalPocketColors.current.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Renders a monetary balance with subtle spring scale, kinetic vertical nudge, and directional
 * green/red color flash micro-animations whenever the balance value changes.
 */
@Composable
fun AnimatedBalanceText(
    amount: Double,
    currencySymbol: String = "৳",
    includeDecimals: Boolean = true,
    showSign: Boolean = false,
    style: TextStyle = TabularMoneyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val pocketColors = LocalPocketColors.current
    var previousAmount by remember { mutableDoubleStateOf(amount) }
    var lastDelta by remember { mutableDoubleStateOf(0.0) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(amount) {
        val delta = amount - previousAmount
        if (abs(delta) >= 0.005) {
            lastDelta = delta
            previousAmount = amount
            pulse.snapTo(1f)
            pulse.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            previousAmount = amount
        }
    }

    val pulseVal = pulse.value.coerceIn(0f, 1.25f)
    val highlightColor = if (lastDelta >= 0.0) pocketColors.incomeText else pocketColors.expenseText
    val animatedColor = lerp(color, highlightColor, (pulseVal * 0.72f).coerceIn(0f, 1f))
    val scaleFactor = 1f + (0.06f * pulseVal)
    val verticalShiftPx = (if (lastDelta >= 0.0) -1f else 1f) * 8f * pulseVal

    val formattedText = remember(amount, currencySymbol, includeDecimals, showSign) {
        AccountingEngine.formatMoney(
            amount = amount,
            symbol = currencySymbol,
            showSign = showSign,
            includeDecimals = includeDecimals
        )
    }

    Text(
        text = formattedText,
        style = style,
        color = animatedColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer {
            scaleX = scaleFactor
            scaleY = scaleFactor
            translationY = verticalShiftPx
        }
    )
}

/**
 * Helper for Android BiometricPrompt (Fingerprint / Face Unlock) paired with Pocket's 4-digit PIN lock.
 */
object BiometricAuthHelper {
    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canAuthenticate(context: Context): Boolean {
        return runCatching {
            BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    fun biometricStatusText(context: Context): String {
        return runCatching {
            when (BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
                BiometricManager.BIOMETRIC_SUCCESS -> "Ready (Fingerprint / Face enrolled)"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face enrolled on device"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric sensor on this device"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric sensor temporarily unavailable"
                else -> "Use 4-digit PIN or enrolled biometrics"
            }
        }.getOrDefault("Use 4-digit PIN or enrolled biometrics")
    }

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return current as? FragmentActivity
    }

    fun showBiometricPrompt(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("Biometric prompt requires an active window.")
            return
        }
        val status = BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            val msg = when (status) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "No fingerprint or face enrolled on this device. Please unlock with your PIN."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "No biometric hardware detected on this device. Please unlock with your PIN."
                else ->
                    "Biometric sensor unavailable right now. Please unlock with your PIN."
            }
            onError(msg)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Pocket")
            .setSubtitle("Verify your fingerprint or face to access your wallets")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        runCatching {
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        }.onFailure { e ->
            onError(e.localizedMessage ?: "Could not launch biometric prompt.")
        }
    }
}

/**
 * Launches the device's local photo storage / Gallery picker so the user can select a profile photo
 * from local storage, apply EXIF orientation correction (`decodeExifCorrectedBitmap`), and crop it
 * in `ProfilePhotoCropDialog`.
 */
@Composable
fun rememberUniversalImagePickerLauncher(
    onUriSelected: (Uri) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnSelected by rememberUpdatedState(onUriSelected)

    val startActivityContract = remember { ActivityResultContracts.StartActivityForResult() }
    val fallbackActivityResultLauncher = rememberLauncherForActivityResult(
        contract = startActivityContract
    ) { result ->
        val dataIntent = result.data
        val uri = dataIntent?.data
            ?: dataIntent?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0)?.uri else null
            }
        if (uri != null) {
            currentOnSelected(uri)
        }
    }

    return remember(context, fallbackActivityResultLauncher) {
        {
            val mainActivity = BiometricAuthHelper.findFragmentActivity(context) as? com.example.MainActivity
            val launchedViaActivity = mainActivity?.launchLocalImagePicker { uri ->
                currentOnSelected(uri)
            } ?: false

            if (!launchedViaActivity) {
                val candidateIntents = com.example.MainActivity.buildLocalImagePickerIntents(context)
                for (intent in candidateIntents) {
                    val ok = runCatching {
                        fallbackActivityResultLauncher.launch(intent)
                        true
                    }.getOrDefault(false)
                    if (ok) break
                }
            }
        }
    }
}

/**
 * Triggers strong, tactile haptic feedback for buttons, switches, PIN pad entries, speed-dial actions, and transactions.
 */
fun performPocketHaptic(view: View?, isLongPress: Boolean = false) {
    if (view == null) return
    runCatching {
        val context = view.context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ContextCompat.getSystemService(context, VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = if (isLongPress) 85L else 45L
                val amplitude = if (isLongPress) 255 else 225
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isLongPress) 85L else 45L)
            }
        } else {
            val constant = if (isLongPress) {
                HapticFeedbackConstants.LONG_PRESS
            } else {
                HapticFeedbackConstants.CONFIRM
            }
            view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }
}

/**
 * Sleek, minimal 4.dp thin track slider with a compact circular thumb (replaces thick 16.dp M3 Slider).
 */
@Composable
fun PocketThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var widthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { size ->
                if (size.width > 0) widthPx = size.width.toFloat()
            }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newFrac = (offset.x / widthPx).coerceIn(0f, 1f)
                    currentOnValueChange(valueRange.start + newFrac * span)
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                    currentOnValueChange(valueRange.start + newFrac * span)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            val trackHeightPx = 4.dp.toPx()
            val thumbRadiusPx = 7.dp.toPx()
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            val thumbCenterX = thumbRadiusPx + usableWidth * fraction
            val centerY = size.height / 2f

            // Inactive 4.dp track
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(thumbRadiusPx, centerY - trackHeightPx / 2f),
                size = Size(usableWidth, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            )
            // Active 4.dp track
            val activeW = (thumbCenterX - thumbRadiusPx).coerceAtLeast(0f)
            if (activeW > 0f) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(thumbRadiusPx, centerY - trackHeightPx / 2f),
                    size = Size(activeW, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
                )
            }
            // Compact circular thumb
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbCenterX, centerY)
            )
        }
    }
}

/**
 * Sanitizes an amount expression string so digits, decimal point, spaces, and +, -, ×, *, ÷, / are allowed.
 */
fun sanitizeMathExpressionInput(raw: String): String {
    return raw
        .replace('*', '×')
        .replace('/', '÷')
        .filter { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '×' || it == '÷' || it == ' ' }
}

/**
 * Evaluates a basic arithmetic expression supporting +, -, × (*), and ÷ (/) with standard operator precedence.
 * Returns null if the expression is empty or invalid.
 */
fun evaluateMathExpression(raw: String): Double? {
    val cleaned = raw
        .replace(" ", "")
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .trimEnd('+', '-', '*', '/', '.')
    if (cleaned.isEmpty()) return null
    cleaned.toDoubleOrNull()?.let { return if (it.isFinite()) AccountingEngine.roundCurrency(it) else null }

    return runCatching {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < cleaned.length) {
            val ch = cleaned[i]
            if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                if ((ch == '+' || ch == '-') && (tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/"))) {
                    val sb = StringBuilder().append(ch)
                    i++
                    while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) {
                        sb.append(cleaned[i])
                        i++
                    }
                    if (sb.length > 1) tokens.add(sb.toString())
                } else {
                    tokens.add(ch.toString())
                    i++
                }
            } else if (ch.isDigit() || ch == '.') {
                val sb = StringBuilder()
                while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) {
                    sb.append(cleaned[i])
                    i++
                }
                tokens.add(sb.toString())
            } else {
                i++
            }
        }
        if (tokens.isEmpty()) return@runCatching null

        // Pass 1: * and /
        val pass1 = mutableListOf<String>()
        var idx = 0
        while (idx < tokens.size) {
            val tk = tokens[idx]
            if ((tk == "*" || tk == "/") && pass1.isNotEmpty() && idx + 1 < tokens.size) {
                val left = pass1.removeAt(pass1.lastIndex).toDoubleOrNull() ?: return@runCatching null
                val right = tokens[idx + 1].toDoubleOrNull() ?: return@runCatching null
                val res = if (tk == "*") left * right else {
                    if (abs(right) < 1e-12) return@runCatching null
                    left / right
                }
                pass1.add(res.toString())
                idx += 2
            } else {
                pass1.add(tk)
                idx++
            }
        }

        // Pass 2: + and -
        var acc = pass1.firstOrNull()?.toDoubleOrNull() ?: return@runCatching null
        idx = 1
        while (idx + 1 < pass1.size) {
            val op = pass1[idx]
            val rhs = pass1[idx + 1].toDoubleOrNull() ?: return@runCatching null
            acc = when (op) {
                "+" -> acc + rhs
                "-" -> acc - rhs
                else -> return@runCatching null
            }
            idx += 2
        }
        if (acc.isFinite()) AccountingEngine.roundCurrency(acc) else null
    }.getOrNull()
}

fun hasMathOperators(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val body = if (trimmed.startsWith("-") || trimmed.startsWith("+")) trimmed.drop(1) else trimmed
    return body.any { it == '+' || it == '-' || it == '×' || it == '÷' || it == '*' || it == '/' }
}

/**
 * Compact inline operator bar (+, -, ×, ÷, =) and live evaluated preview for Amount fields.
 */
@Composable
fun MiniCalculatorBar(
    expression: String = "",
    expressionText: String = expression,
    currencySymbol: String = "৳",
    onExpressionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pocketColors = LocalPocketColors.current
    val view = LocalView.current
    val evaluated = remember(expressionText) { evaluateMathExpression(expressionText) }
    val showLivePreview = remember(expressionText, evaluated) {
        hasMathOperators(expressionText) && evaluated != null
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val operators = listOf("+", "-", "×", "÷")
        operators.forEach { op ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = pocketColors.surfaceElevated,
                border = BorderStroke(1.dp, pocketColors.cardBorder),
                modifier = Modifier
                    .height(32.dp)
                    .clickable {
                        performPocketHaptic(view)
                        val trimmed = expressionText.trimEnd()
                        if (trimmed.isNotEmpty()) {
                            val lastChar = trimmed.last()
                            val next = if (lastChar in listOf('+', '-', '×', '÷', '*', '/')) {
                                trimmed.dropLast(1).trimEnd() + " $op "
                            } else {
                                "$trimmed $op "
                            }
                            onExpressionChange(next)
                        }
                    }
                    .testTag("calc_op_$op")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = op,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showLivePreview && evaluated != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .height(32.dp)
                    .clickable {
                        performPocketHaptic(view)
                        val formatted = if (evaluated % 1.0 == 0.0) {
                            evaluated.toLong().toString()
                        } else {
                            evaluated.toString()
                        }
                        onExpressionChange(formatted)
                    }
                    .testTag("calc_equals_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "= ${AccountingEngine.formatMoney(evaluated, currencySymbol)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Rainbow Color Wheel button shown at the end of the Accent Color Picker row.
 */
@Composable
fun RainbowColorWheelSwatch(
    isCustomSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pocketColors = LocalPocketColors.current
    val rainbowColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFF7F00),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFF8B00FF),
            Color(0xFFFF007F),
            Color(0xFFFF0000)
        )
    }
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(
                width = if (isCustomSelected) 2.5.dp else 1.dp,
                color = if (isCustomSelected) MaterialTheme.colorScheme.onSurface else pocketColors.cardBorder,
                shape = CircleShape
            )
            .testTag("accent_color_rainbow_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(colors = rainbowColors, center = center),
                radius = radius
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                    center = center,
                    radius = radius * 0.72f
                ),
                radius = radius
            )
            if (isCustomSelected) {
                drawCircle(
                    color = activeColor,
                    radius = radius * 0.52f
                )
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.52f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        if (isCustomSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Custom color selected",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Full-spectrum interactive 2D HSV Color Wheel + Brightness Slider dialog allowing any color possible.
 */
@Composable
fun AccentColorWheelDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val pocketColors = LocalPocketColors.current
    val initialColor = remember(initialColorHex) { parseHexColor(initialColorHex, ReddishOrangePrimary) }
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (initialColor.red * 255f).roundToInt().coerceIn(0, 255),
            (initialColor.green * 255f).roundToInt().coerceIn(0, 255),
            (initialColor.blue * 255f).roundToInt().coerceIn(0, 255),
            hsv
        )
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0].coerceIn(0f, 360f)) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f)) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0.15f, 1f)) }

    val selectedColor = remember(hue, saturation, brightness) {
        Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))
    }
    val selectedHex = remember(selectedColor) {
        val r = (selectedColor.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (selectedColor.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (selectedColor.blue * 255f).roundToInt().coerceIn(0, 255)
        String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    val wheelHueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pocketColors.overlaySurface,
        title = {
            Text(
                text = "Choose Custom Accent Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive 2D Color Wheel
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = min(cx, cy).coerceAtLeast(1f)
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val angleDeg = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                                val dist = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                                hue = angleDeg
                                saturation = dist
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val offset = change.position
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = min(cx, cy).coerceAtLeast(1f)
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val angleDeg = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                                val dist = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                                hue = angleDeg
                                saturation = dist
                            }
                        }
                        .testTag("accent_color_wheel_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        // 1. Hue sweep
                        drawCircle(
                            brush = Brush.sweepGradient(colors = wheelHueColors, center = center),
                            radius = radius,
                            center = center
                        )
                        // 2. White radial saturation gradient (0 saturation at center -> 1 at circumference)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                        // 3. Brightness darkening layer
                        if (brightness < 0.999f) {
                            drawCircle(
                                color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.85f)),
                                radius = radius,
                                center = center
                            )
                        }
                        // 4. Wheel outline
                        drawCircle(
                            color = pocketColors.cardBorder,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // 5. Selector thumb at (hue, saturation)
                        val rad = (hue * PI / 180.0).toFloat()
                        val thumbDist = saturation * radius
                        val thumbCenter = Offset(
                            x = center.x + cos(rad) * thumbDist,
                            y = center.y + sin(rad) * thumbDist
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = 12.dp.toPx(),
                            center = thumbCenter,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = selectedColor,
                            radius = 10.dp.toPx(),
                            center = thumbCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = thumbCenter,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                // Brightness Slider
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Brightness",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(brightness * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    PocketThinSlider(
                        value = brightness,
                        onValueChange = { brightness = it.coerceIn(0.15f, 1f) },
                        valueRange = 0.15f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accent_color_wheel_brightness_slider")
                    )
                }

                // Selected Color Preview Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pocketColors.overlayCard,
                    border = BorderStroke(1.dp, pocketColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(selectedColor)
                                    .border(1.5.dp, pocketColors.cardBorder, CircleShape)
                            )
                            Text(
                                text = "Selected Accent",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = selectedHex,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = selectedColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(selectedHex) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_accent_color_wheel_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
