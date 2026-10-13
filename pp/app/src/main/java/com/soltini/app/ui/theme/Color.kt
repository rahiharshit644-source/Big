package com.soltini.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// Soft Light Glassmorphic Design System
// ==========================================

// Background Gradient Colors: soft lavender-to-mint (#F3EEFC -> #EAF6EE)
val BgLavender = Color(0xFFF3EEFC)
val BgMidLilac = Color(0xFFF7F4FD)
val BgMint = Color(0xFFEAF6EE)

// Primary Accent Gradient: purple-to-pink (#B993F0 -> #E8A0E0)
val PrimaryPurple = Color(0xFFB993F0)
val PrimaryPink = Color(0xFFE8A0E0)
val PrimaryDeepPurple = Color(0xFF8B5CF6) // High-contrast text/icon tint when needed on white

// Secondary Accent: soft mint green (#A9E8C9) & deeper mint for readable text/icons
val SecondaryMint = Color(0xFFA9E8C9)
val SecondaryMintDark = Color(0xFF1E9E6A)

// Glass & Card Surfaces
val GlassCardWhite = Color(0xEBFFFFFF)      // ~92% opacity white floating panel
val GlassCardSolid = Color(0xFFFFFFFF)      // Pure white surface
val GlassSurfaceSubtle = Color(0xFFF5F3FA)  // Light pastel gray/lavender surface variant
val GlassBorderWhite = Color(0xCCFFFFFF)    // Subtle luminous white glass edge
val SoftDivider = Color(0xFFEAE6F3)         // Soft hairline-free separator when needed

// Soft Drop Shadow Colors
val GlassShadowAmbient = Color(0x24B993F0)
val GlassShadowSpot = Color(0x2EB993F0)

// Typography Colors
val TextCharcoal = Color(0xFF2A2A35)        // Dark charcoal primary text
val TextMidGray = Color(0xFF8A8A95)         // Mid-gray secondary text
val TextMutedGray = Color(0xFFA8A8B3)       // Muted caption text

// Pastel Icon Badge Fills & Tints
val BadgePurpleBg = Color(0xFFF1E8FD)
val BadgePurpleTint = Color(0xFF8E5AE8)

val BadgePinkBg = Color(0xFFFCEBF9)
val BadgePinkTint = Color(0xFFD65DB1)

val BadgeMintBg = Color(0xFFE5F9EF)
val BadgeMintTint = Color(0xFF249E6B)

val BadgeYellowBg = Color(0xFFFFF6DF)
val BadgeYellowTint = Color(0xFFD9822B)

val BadgeBlueBg = Color(0xFFE8F3FE)
val BadgeBlueTint = Color(0xFF3B82F6)

// Code / Log Monospace Inner Block (Dark for readability, wrapped in white outer card)
val CodeBlockDarkBg = Color(0xFF1E1E2A)
val CodeBlockDarkSurface = Color(0xFF262636)
val CodeBlockTextLight = Color(0xFFEAEAF2)
val CodeBlockTextMuted = Color(0xFF9898AA)

// Reusable Brushes
val SoftBackgroundBrush = Brush.verticalGradient(
    colors = listOf(BgLavender, BgMidLilac, BgMint)
)

val PrimaryGradientBrush = Brush.linearGradient(
    colors = listOf(PrimaryPurple, PrimaryPink)
)

val PrimaryHorizontalBrush = Brush.horizontalGradient(
    colors = listOf(PrimaryPurple, PrimaryPink)
)

val MintGradientBrush = Brush.linearGradient(
    colors = listOf(SecondaryMint, Color(0xFF7AD9B0))
)

// ==========================================
// Semantic Aliases & Shorthands across Screens
// ==========================================
val PurpleAccentStart = BadgePurpleTint
val PinkAccentEnd = PrimaryPink
val MintAccent = SecondaryMint
val MintAccentDark = SecondaryMintDark

val GlassCardWhiteSolid = GlassCardSolid
val PastelPurpleBadge = BadgePurpleBg
val PastelPinkBadge = BadgePinkBg
val PastelMintBadge = BadgeMintBg
val PastelYellowBadge = BadgeYellowBg
val PastelBlueBadge = BadgeBlueBg

val DarkBackground = BgLavender            // Remapped from black to soft lavender
val DarkSurface = GlassCardWhite           // Remapped from dark surface to floating white card
val DarkSurfaceVariant = GlassSurfaceSubtle // Remapped to soft light lavender-gray pill surface
val DarkBorder = SoftDivider               // Soft subtle divider

// Orb & Semantic Accents
val AccentTeal = SecondaryMintDark         // Mint green accent
val AccentBlue = Color(0xFF9B72EF)         // Soft lavender-purple accent
val AccentPink = PrimaryPink               // Soft pink accent
val AccentPurple = PrimaryPurple           // Soft purple accent
val AccentIndigo = Color(0xFFA78BFA)       // Soft violet accent

val TextPrimary = TextCharcoal             // Dark charcoal (#2A2A35)
val TextSecondary = TextMidGray            // Mid-gray (#8A8A95)

val RedError = Color(0xFFE5484D)           // Soft coral red
val GreenLive = Color(0xFF249E6B)          // Crisp mint green for text/icons
val AmberConnecting = Color(0xFFE59524)    // Soft warm amber
