package com.kmp.hook.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupStatus { HEALTHY, MONITOR, CRITICAL }

@Serializable
enum class ActivityType { FEEDING, STOCKING, MEASUREMENT, NOTE, CALCULATION, HARVEST }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class UnitSystem { METRIC, IMPERIAL }
