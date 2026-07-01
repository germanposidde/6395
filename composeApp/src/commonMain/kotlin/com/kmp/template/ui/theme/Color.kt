package com.kmp.hook.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aquatic palette — blue, cyan and turquoise inspired by freshwater fish farming.
 * Brand tokens are referenced by both the light and dark [androidx.compose.material3.ColorScheme]s
 * and by the custom charts / gradients.
 */

// Core brand
val AquaDeep = Color(0xFF015C7A)      // deep water blue
val AquaPrimary = Color(0xFF0091B5)   // primary cyan-blue
val AquaCyan = Color(0xFF00BCD4)      // bright cyan
val AquaTurquoise = Color(0xFF1FD3C4) // turquoise highlight
val AquaMint = Color(0xFF7DEAD7)      // pale mint
val AquaFoam = Color(0xFFE0F7FA)      // foam / surface tint

// Accents used in charts & status
val CoralAccent = Color(0xFFFF7A6B)   // warm contrast for alerts
val SeaweedGreen = Color(0xFF2BBF8E)  // healthy
val AmberWarn = Color(0xFFF5A623)     // monitor
val DeepCoral = Color(0xFFE5533D)     // critical

// Status colors (group health)
val StatusHealthy = SeaweedGreen
val StatusMonitor = AmberWarn
val StatusCritical = DeepCoral

// Chart series palette (cycled)
val ChartSeries = listOf(
    AquaPrimary,
    AquaTurquoise,
    Color(0xFF5B8DEF),
    SeaweedGreen,
    AmberWarn,
    CoralAccent,
    Color(0xFF8E7BEF),
)

// ---- Light scheme tokens ----
val LightPrimary = AquaPrimary
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFB7ECF6)
val LightOnPrimaryContainer = Color(0xFF002E3B)
val LightSecondary = Color(0xFF00ACC1)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC4F1F7)
val LightOnSecondaryContainer = Color(0xFF00363D)
val LightTertiary = Color(0xFF13B9A6)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFB9F5EB)
val LightOnTertiaryContainer = Color(0xFF00201C)
val LightBackground = Color(0xFFF3FAFC)
val LightOnBackground = Color(0xFF0C1416)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0C1416)
val LightSurfaceVariant = Color(0xFFDDE9EC)
val LightOnSurfaceVariant = Color(0xFF40484B)
val LightOutline = Color(0xFF6F797C)
val LightOutlineVariant = Color(0xFFBFCBCE)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightSurfaceContainer = Color(0xFFEAF4F7)
val LightSurfaceContainerHigh = Color(0xFFE3F0F3)

// ---- Dark scheme tokens ----
val DarkPrimary = Color(0xFF4FD4ED)
val DarkOnPrimary = Color(0xFF00363F)
val DarkPrimaryContainer = Color(0xFF004E5C)
val DarkOnPrimaryContainer = Color(0xFFB7ECF6)
val DarkSecondary = Color(0xFF4DD9E6)
val DarkOnSecondary = Color(0xFF00363D)
val DarkSecondaryContainer = Color(0xFF004F58)
val DarkOnSecondaryContainer = Color(0xFFC4F1F7)
val DarkTertiary = Color(0xFF5BDBCB)
val DarkOnTertiary = Color(0xFF003731)
val DarkTertiaryContainer = Color(0xFF005048)
val DarkOnTertiaryContainer = Color(0xFFB9F5EB)
val DarkBackground = Color(0xFF071417)
val DarkOnBackground = Color(0xFFDEE4E5)
val DarkSurface = Color(0xFF0B1A1E)
val DarkOnSurface = Color(0xFFDEE4E5)
val DarkSurfaceVariant = Color(0xFF3F484B)
val DarkOnSurfaceVariant = Color(0xFFBFCBCE)
val DarkOutline = Color(0xFF899295)
val DarkOutlineVariant = Color(0xFF3F484B)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkSurfaceContainer = Color(0xFF12242A)
val DarkSurfaceContainerHigh = Color(0xFF1A2E34)
