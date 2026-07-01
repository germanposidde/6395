package com.kmp.hook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Polished, slightly tightened typography for a clean data-app feel. */
val AquaTypography: Typography
    get() {
        val default = Typography()
        val family = FontFamily.SansSerif
        return Typography(
            displaySmall = default.displaySmall.copy(
                fontFamily = family, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
            ),
            headlineMedium = default.headlineMedium.copy(
                fontFamily = family, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp
            ),
            headlineSmall = default.headlineSmall.copy(
                fontFamily = family, fontWeight = FontWeight.Bold
            ),
            titleLarge = default.titleLarge.copy(
                fontFamily = family, fontWeight = FontWeight.SemiBold
            ),
            titleMedium = default.titleMedium.copy(
                fontFamily = family, fontWeight = FontWeight.SemiBold
            ),
            titleSmall = default.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            bodyLarge = default.bodyLarge.copy(fontFamily = family),
            bodyMedium = default.bodyMedium.copy(fontFamily = family),
            bodySmall = default.bodySmall.copy(fontFamily = family),
            labelLarge = default.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            labelMedium = default.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            labelSmall = default.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        )
    }

/** Large number style used by stat cards / calculator outputs. */
val MetricNumberStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    letterSpacing = (-0.5).sp,
)
