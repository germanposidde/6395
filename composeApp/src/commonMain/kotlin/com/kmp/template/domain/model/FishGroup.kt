package com.kmp.hook.domain.model

import kotlinx.serialization.Serializable

/** A single weight measurement point in a group's growth history (canonical unit = grams). */
@Serializable
data class GrowthPoint(
    val epochMillis: Long,
    val avgWeightGrams: Double,
)

/**
 * A managed group/batch of fish. All masses are stored in canonical metric units
 * (grams for individual weight) and converted only at the UI edge.
 */
@Serializable
data class FishGroup(
    val id: String,
    val species: String,
    val quantity: Int,
    val avgWeightGrams: Double,
    val stockingEpochMillis: Long,
    val notes: String = "",
    val status: GroupStatus = GroupStatus.HEALTHY,
    val targetWeightGrams: Double = 0.0,
    val feedPricePerKg: Double = 1.2,
    val growthPoints: List<GrowthPoint> = emptyList(),
    /** Optional group photo stored as a Base64-encoded JPEG (captured or picked). */
    val photoBase64: String? = null,
) {
    /** Total biomass in kilograms. */
    val biomassKg: Double get() = quantity * avgWeightGrams / 1000.0

    /** Progress toward target weight in 0f..1f (0 if no target set). */
    val growthProgress: Float
        get() = if (targetWeightGrams <= 0.0) 0f
        else (avgWeightGrams / targetWeightGrams).coerceIn(0.0, 1.0).toFloat()
}
