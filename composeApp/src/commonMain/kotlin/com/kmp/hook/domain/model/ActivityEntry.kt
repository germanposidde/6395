package com.kmp.hook.domain.model

import kotlinx.serialization.Serializable

/** A timeline event surfaced on the Dashboard recent-activity feed. */
@Serializable
data class ActivityEntry(
    val id: String,
    val epochMillis: Long,
    val type: ActivityType,
    val title: String,
    val detail: String = "",
)
