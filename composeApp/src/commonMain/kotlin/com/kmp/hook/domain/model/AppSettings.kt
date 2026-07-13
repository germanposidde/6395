package com.kmp.hook.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val units: UnitSystem = UnitSystem.METRIC,
    val currencySymbol: String = "$",
)
