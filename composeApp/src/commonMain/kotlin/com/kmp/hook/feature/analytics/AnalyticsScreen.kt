package com.kmp.hook.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.data.export.CsvWriter
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.calc.Trends
import com.kmp.hook.domain.calc.UnitFormat
import com.kmp.hook.domain.calc.format
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.ui.components.ChartCard
import com.kmp.hook.ui.components.LegendDot
import com.kmp.hook.ui.components.ShareGlyph
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.charts.BarChart
import com.kmp.hook.ui.components.charts.BarDatum
import com.kmp.hook.ui.components.charts.DonutChart
import com.kmp.hook.ui.components.charts.LineChart
import com.kmp.hook.ui.components.charts.Slice
import com.kmp.hook.ui.components.rememberDebouncedClick
import com.kmp.hook.ui.theme.AquaPrimary
import com.kmp.hook.ui.theme.AquaTurquoise
import com.kmp.hook.ui.theme.ChartSeries
import com.kmp.hook.ui.theme.CoralAccent
import com.kmp.hook.ui.theme.SeaweedGreen
import kotlinx.coroutines.launch

private const val FEED_PCT = 3.0

@Composable
fun AnalyticsScreen() {
    val container = LocalAppContainer.current
    val units = LocalUnitSystem.current
    val groups by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val history by container.feedRepo.history.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val growthSeries = remember(groups) { Trends.farmAvgWeightSeries(groups, 8) }
    val forecast = remember(growthSeries) { withForecast(growthSeries, 4) }
    val totalBiomass = remember(groups) { groups.sumOf { it.biomassKg } }
    val totalCost = remember(history) { history.sumOf { it.feedCost } }
    val avgFcr = remember(history) { if (history.isEmpty()) 0.0 else history.sumOf { it.fcr } / history.size }
    val currency = container.settingsRepo.settings.collectAsStateWithLifecycle().value.currencySymbol

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Analytics", subtitle = "Trends, comparisons & forecasts")

        Column(Modifier.padding(16.dp)) {
            // Summary tiles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Total biomass", UnitFormat.mass(totalBiomass, units), AquaPrimary, Modifier.weight(1f))
                SummaryTile("Avg FCR", avgFcr.format(2), AquaTurquoise, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Feed spend", "$currency${totalCost.format(0)}", CoralAccent, Modifier.weight(1f))
                SummaryTile("Active groups", groups.size.toString(), SeaweedGreen, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            ChartCard(title = "Growth forecast", subtitle = "Avg weight + 4-week projection") {
                if (forecast.size > 1) {
                    LineChart(
                        values = forecast,
                        labels = (1..forecast.size).map { if (it <= growthSeries.size) "W$it" else "F${it - growthSeries.size}" },
                        lineColor = AquaPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    EmptyChartHint()
                }
            }

            Spacer(Modifier.height(16.dp))
            ChartCard(title = "Biomass by group", subtitle = "Comparison across groups") {
                val bars = remember(groups) {
                    groups.mapIndexed { i, g ->
                        BarDatum(g.species.take(6), g.biomassKg.toFloat(), ChartSeries[i % ChartSeries.size])
                    }
                }
                if (bars.isEmpty()) EmptyChartHint() else BarChart(bars, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))
            ChartCard(title = "Biomass distribution", subtitle = "Share of total farm biomass") {
                val slices = remember(groups) {
                    groups.mapIndexed { i, g -> Slice(g.species, g.biomassKg.toFloat(), ChartSeries[i % ChartSeries.size]) }
                }
                if (slices.isEmpty()) {
                    EmptyChartHint()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(slices, Modifier.size(150.dp))
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val total = slices.sumOf { it.value.toDouble() }.takeIf { it > 0 } ?: 1.0
                            slices.forEach { s ->
                                LegendDot(s.color, s.label, "${(s.value / total * 100).format(0)}%")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            ChartCard(title = "Feed efficiency", subtitle = "Daily feed need by group") {
                val bars = remember(groups) {
                    groups.mapIndexed { i, g ->
                        BarDatum(g.species.take(6), FeedMath.dailyFeedKg(g.biomassKg, FEED_PCT).toFloat(), ChartSeries[i % ChartSeries.size])
                    }
                }
                if (bars.isEmpty()) EmptyChartHint() else BarChart(bars, Modifier.fillMaxWidth(), height = 170.dp)
            }

            Spacer(Modifier.height(20.dp))
            Text("Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExportButton("Groups CSV", Modifier.weight(1f), rememberDebouncedClick {
                    scope.launch { container.fileExporter.shareText("fish_groups.csv", "text/csv", CsvWriter.groups(groups)) }
                })
                ExportButton("History CSV", Modifier.weight(1f), rememberDebouncedClick {
                    scope.launch { container.fileExporter.shareText("feed_history.csv", "text/csv", CsvWriter.feedHistory(history)) }
                })
            }
            Spacer(Modifier.height(12.dp))
            ExportButton("Analytics summary CSV", Modifier.fillMaxWidth(), rememberDebouncedClick {
                scope.launch { container.fileExporter.shareText("analytics.csv", "text/csv", CsvWriter.analytics(groups)) }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShareGlyph(Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyChartHint() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text("Add fish groups to see analytics", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Append a linear projection of [extra] points using the trailing slope. */
private fun withForecast(series: List<Float>, extra: Int): List<Float> {
    if (series.size < 2) return series
    val slope = series.last() - series[series.size - 2]
    val out = series.toMutableList()
    var v = series.last()
    repeat(extra) {
        v += slope
        out.add(v.coerceAtLeast(0f))
    }
    return out
}
