package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure Black Base & 50% Transparent Grid Surfaces (0x80 = 50% opacity / 50% transparency)
val PocketCarbonBase = Color(0xFF000000)
val PocketBackground = Color(0x00000000) // Transparent so root CarbonFiberBackground shows through
val PocketSurface = Color(0x80141416) // 50% transparent neutral dark surface over pure black
val PocketSurfaceElevated = Color(0x801F1F23) // 50% transparent elevated neutral dark surface
val PocketSurfaceSelected = Color(0x804A2012) // 50% transparent selected tint (overridden dynamically by accentColor)
val PocketInputBackground = Color(0x80121215) // 50% transparent
val PocketDividerBorder = Color(0x803F3F46) // 50% transparent neutral gray border

// ~85% Opaque Overlay Popup Surfaces (0xD9 = 85% opacity / 15% transparency for popups, New Record, New Wallet, etc.)
val PocketOverlaySurface = Color(0xD9121215)
val PocketOverlayCard = Color(0xD91C1C21)

// Light Mode Base (Pure White base background, Black & Gray text, ~85% opaque overlays)
val PocketLightBase = Color(0xFFFFFFFF)
val PocketLightBackground = Color(0x00FFFFFF)
val PocketLightSurface = Color(0x80FFFFFF) // 50% transparent grid box over white base
val PocketLightSurfaceElevated = Color(0x80F4F4F5) // 50% transparent neutral light gray
val PocketLightSurfaceSelected = Color(0x80FFE0D4) // 50% transparent accent tint (overridden dynamically by accentColor)
val PocketLightInputBackground = Color(0x80FFFFFF) // 50% transparent
val PocketLightDividerBorder = Color(0xE6686873) // Darker neutral gray border for clear box visibility in Light mode
val PocketLightOverlaySurface = Color(0xD9FFFFFF) // ~85% opaque overlay popup in Light mode
val PocketLightOverlayCard = Color(0xD9F4F4F5) // ~85% opaque overlay card in Light mode
val PocketLightPrimaryText = Color(0xFF09090B) // Pure Black text in Light mode
val PocketLightSecondaryText = Color(0xFF52525B) // Neutral Gray secondary text in Light mode
val PocketLightMutedText = Color(0xFF71717A) // Neutral Gray muted text in Light mode
val PocketLightDefaultIcon = Color(0xFF09090B)

// Default Accent Palette (Orange #F15A24 by default; user can pick any accent color in Settings)
val ReddishOrangePrimary = Color(0xFFF15A24)
val ReddishOrangePrimaryPressed = Color(0xFFD64512)
val ReddishOrangePrimaryContainer = Color(0x804A2012)
val ReddishOrangeLightPrimaryContainer = Color(0x80FFE2D6)
val ReddishOrangePrimaryHighlight = Color(0xFFFF7A47)
val ReddishOrangeOnPrimary = Color(0xFFFFFFFF)
val ReddishOrangeOnPrimaryContainer = Color(0xFFFFFFFF)

// Backward-compatible aliases
val BurgundyPrimary = ReddishOrangePrimary
val BurgundyPrimaryPressed = ReddishOrangePrimaryPressed
val BurgundyPrimaryContainer = ReddishOrangePrimaryContainer
val BurgundyPrimaryHighlight = ReddishOrangePrimaryHighlight
val BurgundyOnPrimary = ReddishOrangeOnPrimary
val BurgundyOnPrimaryContainer = ReddishOrangeOnPrimaryContainer

// Dark Mode Text & icons (Strictly White & Neutral Gray tones on pure Black)
val PocketPrimaryText = Color(0xFFFFFFFF)
val PocketSecondaryText = Color(0xFFA1A1AA)
val PocketMutedText = Color(0xFF71717A)
val PocketDisabledText = Color(0xFF52525B)
val PocketDefaultIcon = Color(0xFFFFFFFF)

// Financial semantics (strictly for financial direction: + Income green, - Expense red)
val PocketIncomePositive = Color(0xFF4ADE80)
val PocketLightIncomePositive = Color(0xFF15803D)
val PocketIncomeContainer = Color(0x80193826)
val PocketLightIncomeContainer = Color(0x80DCFCE7)
val PocketOverlayIncomeContainer = Color(0xD9193826)
val PocketLightOverlayIncomeContainer = Color(0xD9DCFCE7)

val PocketExpenseNegative = Color(0xFFFF6B6B)
val PocketLightExpenseNegative = Color(0xFFDC2626)
val PocketExpenseContainer = Color(0x80421E22)
val PocketLightExpenseContainer = Color(0x80FEE2E2)
val PocketOverlayExpenseContainer = Color(0xD9421E22)
val PocketLightOverlayExpenseContainer = Color(0xD9FEE2E2)

val PocketTransferNeutral = Color(0xFF93C5FD)
val PocketLightTransferNeutral = Color(0xFF1D4ED8)
val PocketTransferContainer = Color(0x801E293B)
val PocketLightTransferContainer = Color(0x80DBEAFE)
val PocketOverlayTransferContainer = Color(0xD91E293B)
val PocketLightOverlayTransferContainer = Color(0xD9DBEAFE)

// Feedback
val PocketSuccess = Color(0xFF4ADE80)
val PocketWarning = Color(0xFFFF9838)
val PocketLightWarning = Color(0xFFD97706)
val PocketWarningContainer = Color(0x80422612)
val PocketLightWarningContainer = Color(0x80FEF3C7)
val PocketOverlayWarningContainer = Color(0xD9422612)
val PocketLightOverlayWarningContainer = Color(0xD9FEF3C7)
val PocketError = Color(0xFFFF6B6B)
val PocketInfo = Color(0xFF93C5FD)
