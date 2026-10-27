package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.components.rememberDecodedImageBitmap
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Immutable
data class PocketSemanticColors(
    val isDark: Boolean,
    val incomeText: Color,
    val incomeContainer: Color,
    val overlayIncomeContainer: Color,
    val expenseText: Color,
    val expenseContainer: Color,
    val overlayExpenseContainer: Color,
    val transferText: Color,
    val transferContainer: Color,
    val overlayTransferContainer: Color,
    val loanText: Color,
    val loanContainer: Color,
    val overlayLoanContainer: Color,
    val mutedText: Color,
    val cardBorder: Color,
    val surfaceElevated: Color,
    val surfaceSelected: Color,
    val overlaySurface: Color,
    val overlayCard: Color,
    val inputBackground: Color,
    val primaryHighlight: Color,
    val defaultIcon: Color
)

val DarkReddishOrangeSemanticColors = PocketSemanticColors(
    isDark = true,
    incomeText = PocketIncomePositive,
    incomeContainer = PocketIncomeContainer,
    overlayIncomeContainer = PocketOverlayIncomeContainer,
    expenseText = PocketExpenseNegative,
    expenseContainer = PocketExpenseContainer,
    overlayExpenseContainer = PocketOverlayExpenseContainer,
    transferText = PocketTransferNeutral,
    transferContainer = PocketTransferContainer,
    overlayTransferContainer = PocketOverlayTransferContainer,
    loanText = PocketWarning,
    loanContainer = PocketWarningContainer,
    overlayLoanContainer = PocketOverlayWarningContainer,
    mutedText = PocketMutedText,
    cardBorder = PocketDividerBorder,
    surfaceElevated = PocketSurfaceElevated,
    surfaceSelected = PocketSurfaceSelected,
    overlaySurface = PocketOverlaySurface,
    overlayCard = PocketOverlayCard,
    inputBackground = PocketInputBackground,
    primaryHighlight = ReddishOrangePrimaryHighlight,
    defaultIcon = PocketDefaultIcon
)

val LightReddishOrangeSemanticColors = PocketSemanticColors(
    isDark = false,
    incomeText = PocketLightIncomePositive,
    incomeContainer = PocketLightIncomeContainer,
    overlayIncomeContainer = PocketLightOverlayIncomeContainer,
    expenseText = PocketLightExpenseNegative,
    expenseContainer = PocketLightExpenseContainer,
    overlayExpenseContainer = PocketLightOverlayExpenseContainer,
    transferText = PocketLightTransferNeutral,
    transferContainer = PocketLightTransferContainer,
    overlayTransferContainer = PocketLightOverlayTransferContainer,
    loanText = PocketLightWarning,
    loanContainer = PocketLightWarningContainer,
    overlayLoanContainer = PocketLightOverlayWarningContainer,
    mutedText = PocketLightMutedText,
    cardBorder = PocketLightDividerBorder,
    surfaceElevated = PocketLightSurfaceElevated,
    surfaceSelected = PocketLightSurfaceSelected,
    overlaySurface = PocketLightOverlaySurface,
    overlayCard = PocketLightOverlayCard,
    inputBackground = PocketLightInputBackground,
    primaryHighlight = ReddishOrangePrimary,
    defaultIcon = PocketLightDefaultIcon
)

val DefaultReddishOrangeSemanticColors = DarkReddishOrangeSemanticColors

val LocalPocketColors = staticCompositionLocalOf { DefaultReddishOrangeSemanticColors }

private val DarkReddishOrangeColorScheme = darkColorScheme(
    primary = ReddishOrangePrimary,
    onPrimary = Color.White,
    primaryContainer = ReddishOrangePrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = ReddishOrangePrimaryHighlight,
    onSecondary = Color.White,
    secondaryContainer = PocketSurfaceSelected,
    onSecondaryContainer = Color.White,
    tertiary = ReddishOrangePrimaryHighlight,
    onTertiary = Color.White,
    tertiaryContainer = PocketTransferContainer,
    onTertiaryContainer = Color.White,
    background = PocketBackground,
    onBackground = Color.White,
    surface = PocketSurface,
    onSurface = Color.White,
    surfaceVariant = PocketSurfaceElevated,
    onSurfaceVariant = PocketSecondaryText,
    surfaceContainerLowest = PocketInputBackground,
    surfaceContainerLow = PocketSurface,
    surfaceContainer = PocketSurfaceElevated,
    surfaceContainerHigh = PocketOverlaySurface, // 20% transparent (80% opaque) for AlertDialogs & popups
    surfaceContainerHighest = PocketOverlayCard, // 20% transparent (80% opaque)
    outline = PocketDividerBorder,
    outlineVariant = PocketDividerBorder,
    error = PocketError,
    onError = Color.White,
    errorContainer = PocketExpenseContainer,
    onErrorContainer = PocketExpenseNegative
)

private val LightReddishOrangeColorScheme = lightColorScheme(
    primary = ReddishOrangePrimary,
    onPrimary = Color.White,
    primaryContainer = ReddishOrangeLightPrimaryContainer,
    onPrimaryContainer = PocketLightPrimaryText,
    secondary = ReddishOrangePrimary,
    onSecondary = Color.White,
    secondaryContainer = PocketLightSurfaceSelected,
    onSecondaryContainer = PocketLightPrimaryText,
    tertiary = PocketLightTransferNeutral,
    onTertiary = Color.White,
    tertiaryContainer = PocketLightTransferContainer,
    onTertiaryContainer = PocketLightPrimaryText,
    background = PocketLightBackground,
    onBackground = PocketLightPrimaryText,
    surface = PocketLightSurface,
    onSurface = PocketLightPrimaryText,
    surfaceVariant = PocketLightSurfaceElevated,
    onSurfaceVariant = PocketLightSecondaryText,
    surfaceContainerLowest = PocketLightInputBackground,
    surfaceContainerLow = PocketLightSurface,
    surfaceContainer = PocketLightSurfaceElevated,
    surfaceContainerHigh = PocketLightOverlaySurface, // 20% transparent (80% opaque) for AlertDialogs & popups
    surfaceContainerHighest = PocketLightOverlayCard, // 20% transparent (80% opaque)
    outline = PocketLightDividerBorder,
    outlineVariant = PocketLightDividerBorder,
    error = PocketLightExpenseNegative,
    onError = Color.White,
    errorContainer = PocketLightExpenseContainer,
    onErrorContainer = PocketLightExpenseNegative
)

val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun Color.toAndroidArgb(): Int {
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Renders the pure Black (`#000000`) or pure White (`#FFFFFF`) solid base,
 * with either the user's custom wallpaper or the default Carbon Fiber weave
 * rendered as an overlay whose visibility is controlled by `backgroundOpacity` (`0f`..`1f`).
 *
 * Uses a hardware-cached 2x2 repeating BitmapShader tile inside `drawWithCache` so the entire
 * screen background renders in a single GPU quad instead of thousands of individual draw calls.
 */
@Composable
fun CarbonFiberBackground(
    isDark: Boolean = true,
    accentColor: Color = ReddishOrangePrimary,
    customBackgroundUri: String = "",
    backgroundOpacity: Float = 0.85f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val clampedOpacity = backgroundOpacity.coerceIn(0.0f, 1.0f)
    val baseBg = if (isDark) Color.Black else Color.White

    val wallpaperBitmap = rememberDecodedImageBitmap(
        pathOrUri = customBackgroundUri,
        maxDimension = 2048
    )

    if (wallpaperBitmap != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(baseBg)
        ) {
            if (clampedOpacity > 0.005f) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = "Custom wallpaper background",
                    contentScale = ContentScale.Crop,
                    alpha = clampedOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            }
            content()
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawWithCache {
                if (clampedOpacity <= 0.005f || size.width <= 0f || size.height <= 0f) {
                    onDrawBehind {}
                } else {
                    val cellPx = 7.dp.toPx().roundToInt().coerceAtLeast(6)
                    val halfCell = cellPx * 0.5f
                    val tileSize = cellPx * 2

                    val weaveLight = (if (isDark) Color(0xFF1A1A1E) else Color(0xFFF6F7FA)).copy(alpha = clampedOpacity).toAndroidArgb()
                    val weaveMid = (if (isDark) Color(0xFF121215) else Color(0xFFEAECEF)).copy(alpha = clampedOpacity).toAndroidArgb()
                    val weaveShadow = (if (isDark) Color(0xFF08080A) else Color(0xFFDADDE3)).copy(alpha = clampedOpacity).toAndroidArgb()
                    val weaveHighlight = (if (isDark) Color(0xFF242429) else Color(0xFFFFFFFF)).copy(alpha = clampedOpacity).toAndroidArgb()

                    val tileBitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
                    val androidCanvas = AndroidCanvas(tileBitmap)
                    val paint = AndroidPaint()

                    for (r in 0 until 2) {
                        val y = r * cellPx.toFloat()
                        for (c in 0 until 2) {
                            val x = c * cellPx.toFloat()
                            val phase = (r + c) % 2 == 0
                            if (phase) {
                                paint.color = weaveLight
                                androidCanvas.drawRect(x, y, x + halfCell - 0.5f, y + cellPx - 0.5f, paint)
                                paint.color = weaveHighlight
                                androidCanvas.drawRect(x, y, x + halfCell - 0.5f, y + 1.3f, paint)
                                paint.color = weaveMid
                                androidCanvas.drawRect(x + halfCell, y, x + cellPx - 0.5f, y + cellPx - 0.5f, paint)
                                paint.color = weaveShadow
                                androidCanvas.drawRect(x + halfCell, y + cellPx - 1.5f, x + cellPx - 0.5f, y + cellPx - 0.4f, paint)
                            } else {
                                paint.color = weaveMid
                                androidCanvas.drawRect(x, y, x + cellPx - 0.5f, y + halfCell - 0.5f, paint)
                                paint.color = weaveShadow
                                androidCanvas.drawRect(x + cellPx - 1.5f, y, x + cellPx - 0.4f, y + halfCell - 0.5f, paint)
                                paint.color = weaveLight
                                androidCanvas.drawRect(x, y + halfCell, x + cellPx - 0.5f, y + cellPx - 0.5f, paint)
                                paint.color = weaveHighlight
                                androidCanvas.drawRect(x, y + halfCell, x + 1.3f, y + cellPx - 0.5f, paint)
                            }
                        }
                    }

                    val tileBrush = ShaderBrush(
                        BitmapShader(tileBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    )

                    val topGlowBrush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(
                                accentColor.copy(alpha = 0.14f * clampedOpacity),
                                accentColor.copy(alpha = 0.05f * clampedOpacity),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                accentColor.copy(alpha = 0.09f * clampedOpacity),
                                accentColor.copy(alpha = 0.03f * clampedOpacity),
                                Color.Transparent
                            )
                        },
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = (size.width * 0.9f).coerceAtLeast(1f)
                    )

                    val bottomGlowBrush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(
                                accentColor.copy(alpha = 0.09f * clampedOpacity),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                accentColor.copy(alpha = 0.06f * clampedOpacity),
                                Color.Transparent
                            )
                        },
                        center = Offset(size.width * 0.15f, size.height * 0.88f),
                        radius = (size.width * 0.75f).coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(brush = tileBrush)
                        drawRect(brush = topGlowBrush)
                        drawRect(brush = bottomGlowBrush)
                    }
                }
            }
    ) {
        content()
    }
}

private fun parseAccentHex(hex: String): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val longVal = cleaned.toLong(16)
        if (cleaned.length == 6) {
            Color(0xFF000000 or longVal)
        } else if (cleaned.length == 8) {
            Color(longVal)
        } else {
            ReddishOrangePrimary
        }
    } catch (_: Exception) {
        ReddishOrangePrimary
    }
}

private fun blendColors(c1: Color, c2: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = c1.red * (1f - r) + c2.red * r,
        green = c1.green * (1f - r) + c2.green * r,
        blue = c1.blue * (1f - r) + c2.blue * r,
        alpha = c1.alpha * (1f - r) + c2.alpha * r
    )
}

@Composable
fun PocketTheme(
    themeMode: String = "DARK",
    accentColorHex: String = "#F15A24",
    customBackgroundUri: String = "",
    backgroundOpacity: Float = 0.85f,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        else -> true
    }

    val accentPrimary = parseAccentHex(accentColorHex)
    val accentHighlight = blendColors(accentPrimary, Color.White, 0.24f)
    val darkSelected = blendColors(Color(0xFF141416), accentPrimary, 0.28f).copy(alpha = 0.55f)
    val lightSelected = blendColors(Color.White, accentPrimary, 0.16f).copy(alpha = 0.75f)
    val darkPrimaryContainer = blendColors(Color(0xFF141416), accentPrimary, 0.34f).copy(alpha = 0.65f)
    val lightPrimaryContainer = blendColors(Color.White, accentPrimary, 0.20f).copy(alpha = 0.80f)

    val semanticColors = if (isDark) {
        DarkReddishOrangeSemanticColors.copy(
            surfaceSelected = darkSelected,
            primaryHighlight = accentHighlight
        )
    } else {
        LightReddishOrangeSemanticColors.copy(
            surfaceSelected = lightSelected,
            primaryHighlight = accentHighlight
        )
    }

    val colorScheme = if (isDark) {
        DarkReddishOrangeColorScheme.copy(
            primary = accentPrimary,
            primaryContainer = darkPrimaryContainer,
            secondary = accentHighlight,
            secondaryContainer = darkSelected,
            tertiary = accentHighlight
        )
    } else {
        LightReddishOrangeColorScheme.copy(
            primary = accentPrimary,
            primaryContainer = lightPrimaryContainer,
            secondary = accentPrimary,
            secondaryContainer = lightSelected
        )
    }

    CompositionLocalProvider(LocalPocketColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PocketShapes
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                CarbonFiberBackground(
                    isDark = isDark,
                    accentColor = accentPrimary,
                    customBackgroundUri = customBackgroundUri,
                    backgroundOpacity = backgroundOpacity
                ) {
                    content()
                }
            }
        }
    }
}
