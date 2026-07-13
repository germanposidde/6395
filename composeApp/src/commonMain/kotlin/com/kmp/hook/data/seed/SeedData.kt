package com.kmp.hook.data.seed

import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.IdGen
import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.ActivityType
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.model.GroupStatus
import com.kmp.hook.domain.model.GrowthPoint

/** Realistic first-launch sample data so dashboards & analytics look rich immediately. */
object SeedData {

    data class Bundle(
        val groups: List<FishGroup>,
        val feed: List<FeedCalculation>,
        val activity: List<ActivityEntry>,
    )

    private fun growth(
        stockedDaysAgo: Int,
        startWeight: Double,
        currentWeight: Double,
        points: Int = 8,
    ): List<GrowthPoint> {
        val now = AppClock.nowMillis()
        val start = now - stockedDaysAgo * AppClock.DAY_MS
        return (0 until points).map { i ->
            val t = i / (points - 1).toDouble()
            // slight ease so the curve looks organic rather than perfectly linear
            val eased = t * t * (3 - 2 * t)
            GrowthPoint(
                epochMillis = (start + (now - start) * t).toLong(),
                avgWeightGrams = startWeight + (currentWeight - startWeight) * eased,
            )
        }
    }

    fun build(): Bundle {
        val now = AppClock.nowMillis()
        val groups = listOf(
            FishGroup(
                id = IdGen.next("g_"),
                species = "Nile Tilapia",
                quantity = 1200,
                avgWeightGrams = 182.0,
                stockingEpochMillis = now - 84 * AppClock.DAY_MS,
                notes = "Pond A — recirculating system, 28°C.",
                status = GroupStatus.HEALTHY,
                targetWeightGrams = 450.0,
                feedPricePerKg = 1.15,
                growthPoints = growth(84, 25.0, 182.0),
            ),
            FishGroup(
                id = IdGen.next("g_"),
                species = "African Catfish",
                quantity = 800,
                avgWeightGrams = 318.0,
                stockingEpochMillis = now - 98 * AppClock.DAY_MS,
                notes = "Tank 3 — high stocking density.",
                status = GroupStatus.HEALTHY,
                targetWeightGrams = 800.0,
                feedPricePerKg = 1.05,
                growthPoints = growth(98, 30.0, 318.0),
            ),
            FishGroup(
                id = IdGen.next("g_"),
                species = "Rainbow Trout",
                quantity = 500,
                avgWeightGrams = 236.0,
                stockingEpochMillis = now - 70 * AppClock.DAY_MS,
                notes = "Raceway — watch dissolved oxygen.",
                status = GroupStatus.MONITOR,
                targetWeightGrams = 500.0,
                feedPricePerKg = 1.40,
                growthPoints = growth(70, 42.0, 236.0),
            ),
            FishGroup(
                id = IdGen.next("g_"),
                species = "Common Carp",
                quantity = 950,
                avgWeightGrams = 96.0,
                stockingEpochMillis = now - 35 * AppClock.DAY_MS,
                notes = "Pond B — newly stocked fingerlings.",
                status = GroupStatus.HEALTHY,
                targetWeightGrams = 600.0,
                feedPricePerKg = 0.95,
                growthPoints = growth(35, 20.0, 96.0, points = 6),
            ),
            FishGroup(
                id = IdGen.next("g_"),
                species = "Koi (ornamental)",
                quantity = 280,
                avgWeightGrams = 58.0,
                stockingEpochMillis = now - 50 * AppClock.DAY_MS,
                notes = "Display pond — slow growth, fungal watch.",
                status = GroupStatus.CRITICAL,
                targetWeightGrams = 400.0,
                feedPricePerKg = 1.80,
                growthPoints = growth(50, 32.0, 58.0, points = 6),
            ),
        )

        val feed = buildList {
            var day = 2
            repeat(14) { i ->
                val g = groups[i % groups.size]
                val r = FeedMath.compute(
                    quantity = g.quantity,
                    avgWeightGrams = g.avgWeightGrams,
                    feedingPercent = 2.5 + (i % 3) * 0.5,
                    fcr = 1.4 + (i % 4) * 0.1,
                    horizonDays = 30,
                    pricePerKg = g.feedPricePerKg,
                )
                add(
                    FeedCalculation(
                        id = IdGen.next("c_"),
                        createdAtEpochMillis = now - day * AppClock.DAY_MS,
                        label = g.species,
                        biomassKg = r.biomassKg,
                        feedingPercent = 2.5 + (i % 3) * 0.5,
                        dailyFeedKg = r.dailyFeedKg,
                        fcr = r.fcr,
                        predictedWeightGrams = r.predictedWeightGrams,
                        horizonDays = 30,
                        feedCost = r.feedCost,
                        groupId = g.id,
                    )
                )
                day += 3 + (i % 2)
            }
        }

        val activity = buildList {
            add(act(now - 1 * AppClock.DAY_MS / 6, ActivityType.FEEDING, "Morning feed logged", "Nile Tilapia • 32.7 kg"))
            add(act(now - 1 * AppClock.DAY_MS / 2, ActivityType.MEASUREMENT, "Sampled average weight", "Rainbow Trout • 236 g"))
            add(act(now - 1 * AppClock.DAY_MS, ActivityType.CALCULATION, "Saved feed calculation", "African Catfish"))
            add(act(now - 2 * AppClock.DAY_MS, ActivityType.FEEDING, "Evening feed logged", "Common Carp • 18.2 kg"))
            add(act(now - 3 * AppClock.DAY_MS, ActivityType.NOTE, "Water quality check", "Pond B • pH 7.4, DO 6.8 mg/L"))
            add(act(now - 5 * AppClock.DAY_MS, ActivityType.STOCKING, "New batch stocked", "Common Carp • 950 fingerlings"))
            add(act(now - 8 * AppClock.DAY_MS, ActivityType.MEASUREMENT, "Growth sample recorded", "Nile Tilapia • +12 g/week"))
        }

        return Bundle(groups, feed, activity)
    }

    private fun act(millis: Long, type: ActivityType, title: String, detail: String) =
        ActivityEntry(IdGen.next("a_"), millis, type, title, detail)
}
