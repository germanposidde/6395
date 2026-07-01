package com.kmp.hook.domain.calc

import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.model.GrowthPoint
import kotlin.math.min

/** Derived time-series helpers for dashboards & analytics. */
object Trends {

    /** Resample a growth series to exactly [n] points by index interpolation. */
    fun resample(points: List<GrowthPoint>, n: Int): List<Float> {
        if (points.isEmpty()) return List(n) { 0f }
        if (points.size == 1) return List(n) { points[0].avgWeightGrams.toFloat() }
        return (0 until n).map { i ->
            val f = if (n == 1) 0f else i / (n - 1).toFloat()
            val idx = f * (points.size - 1)
            val lo = idx.toInt()
            val hi = min(lo + 1, points.size - 1)
            val t = idx - lo
            (points[lo].avgWeightGrams * (1 - t) + points[hi].avgWeightGrams * t).toFloat()
        }
    }

    /** Biomass-weighted average-weight trend across all groups. */
    fun farmAvgWeightSeries(groups: List<FishGroup>, n: Int = 8): List<Float> {
        if (groups.isEmpty()) return emptyList()
        val series = groups.map { g ->
            val pts = g.growthPoints.ifEmpty { listOf(GrowthPoint(0L, g.avgWeightGrams)) }
            resample(pts, n) to g.quantity
        }
        return (0 until n).map { i ->
            val totalQty = series.sumOf { it.second.toDouble() }
            if (totalQty == 0.0) 0f
            else (series.sumOf { it.first[i].toDouble() * it.second } / totalQty).toFloat()
        }
    }

    /** Total farm biomass (kg) trend across all groups. */
    fun farmBiomassSeries(groups: List<FishGroup>, n: Int = 8): List<Float> {
        if (groups.isEmpty()) return emptyList()
        val perGroup = groups.map { g ->
            val pts = g.growthPoints.ifEmpty { listOf(GrowthPoint(0L, g.avgWeightGrams)) }
            resample(pts, n).map { it * g.quantity / 1000f }
        }
        return (0 until n).map { i -> perGroup.sumOf { it[i].toDouble() }.toFloat() }
    }

    fun weekLabels(n: Int): List<String> = (0 until n).map { "W${it + 1}" }
}
