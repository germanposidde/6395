package com.kmp.hook.data.export

import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.format
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup

/** Pure CSV serialization for exporting records / statistics. */
object CsvWriter {

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\"" else field

    private fun row(cells: List<String>): String = cells.joinToString(",") { escape(it) }

    fun groups(list: List<FishGroup>): String {
        val header = row(
            listOf(
                "Species", "Quantity", "AvgWeight_g", "Biomass_kg",
                "AgeDays", "StockingDate", "Status", "TargetWeight_g", "Notes",
            )
        )
        val rows = list.map { g ->
            row(
                listOf(
                    g.species,
                    g.quantity.toString(),
                    g.avgWeightGrams.format(1),
                    g.biomassKg.format(2),
                    AppClock.daysBetween(g.stockingEpochMillis, AppClock.nowMillis()).toString(),
                    AppClock.formatDate(g.stockingEpochMillis),
                    g.status.name,
                    g.targetWeightGrams.format(0),
                    g.notes,
                )
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun feedHistory(list: List<FeedCalculation>): String {
        val header = row(
            listOf(
                "Date", "Label", "Biomass_kg", "Feeding_%", "DailyFeed_kg",
                "FCR", "PredictedWeight_g", "HorizonDays", "FeedCost",
            )
        )
        val rows = list.map { c ->
            row(
                listOf(
                    AppClock.formatDate(c.createdAtEpochMillis),
                    c.label,
                    c.biomassKg.format(2),
                    c.feedingPercent.format(1),
                    c.dailyFeedKg.format(2),
                    c.fcr.format(2),
                    c.predictedWeightGrams.format(1),
                    c.horizonDays.toString(),
                    c.feedCost.format(2),
                )
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    /** Combined analytics export: per-group summary + computed efficiency. */
    fun analytics(list: List<FishGroup>): String {
        val header = row(
            listOf("Species", "Quantity", "AvgWeight_g", "Biomass_kg", "DailyFeed_kg", "Status")
        )
        val rows = list.map { g ->
            val daily = FeedMath.dailyFeedKg(g.biomassKg, 3.0)
            row(
                listOf(
                    g.species,
                    g.quantity.toString(),
                    g.avgWeightGrams.format(1),
                    g.biomassKg.format(2),
                    daily.format(2),
                    g.status.name,
                )
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
