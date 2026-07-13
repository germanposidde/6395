package com.kmp.hook.domain.model

import kotlinx.serialization.Serializable

/** A saved Feed Calculator run, kept in history. Masses in kg, weights in grams. */
@Serializable
data class FeedCalculation(
    val id: String,
    val createdAtEpochMillis: Long,
    val label: String,
    val biomassKg: Double,
    val feedingPercent: Double,
    val dailyFeedKg: Double,
    val fcr: Double,
    val predictedWeightGrams: Double,
    val horizonDays: Int,
    val feedCost: Double,
    val groupId: String? = null,
)
