package com.kmp.hook.data.export

import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.AppSettings
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup
import kotlinx.serialization.Serializable

/** Full, lossless snapshot used for backup / restore / import-export. */
@Serializable
data class BackupBundle(
    val version: Int = 1,
    val exportedAtMillis: Long = 0L,
    val groups: List<FishGroup> = emptyList(),
    val feedHistory: List<FeedCalculation> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
)
