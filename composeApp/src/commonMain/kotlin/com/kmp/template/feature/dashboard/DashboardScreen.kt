package com.kmp.hook.feature.dashboard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.calc.Trends
import com.kmp.hook.domain.calc.UnitFormat
import com.kmp.hook.domain.calc.grouped
import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.ActivityType
import com.kmp.hook.domain.model.GroupStatus
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.navigation.Route
import com.kmp.hook.ui.components.AnalyticsGlyph
import com.kmp.hook.ui.components.FeedGlyph
import com.kmp.hook.ui.components.FishGlyph
import com.kmp.hook.ui.components.PlusGlyph
import com.kmp.hook.ui.components.RulerGlyph
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.SectionHeader
import com.kmp.hook.ui.components.StatCard
import com.kmp.hook.ui.components.WaterDropGlyph
import com.kmp.hook.ui.components.charts.BarChart
import com.kmp.hook.ui.components.charts.BarDatum
import com.kmp.hook.ui.components.charts.LineChart
import com.kmp.hook.ui.components.rememberDebouncedClick
import com.kmp.hook.ui.theme.AquaPrimary
import com.kmp.hook.ui.theme.AquaTurquoise
import com.kmp.hook.ui.theme.ChartSeries
import com.kmp.hook.ui.theme.CoralAccent
import com.kmp.hook.ui.theme.SeaweedGreen

private const val DEFAULT_FEED_PCT = 3.0

@Composable
fun DashboardScreen(onNavigate: (Route) -> Unit) {
    val container = LocalAppContainer.current
    val units = LocalUnitSystem.current
    val groups by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val activity by container.activityRepo.entries.collectAsStateWithLifecycle()

    val totalFish = remember(groups) { groups.sumOf { it.quantity } }
    val totalBiomassKg = remember(groups) { groups.sumOf { it.biomassKg } }
    val avgWeightG = remember(groups) {
        if (totalFish == 0) 0.0 else groups.sumOf { it.avgWeightGrams * it.quantity } / totalFish
    }
    val dailyFeedKg = remember(groups) {
        groups.sumOf { FeedMath.dailyFeedKg(it.biomassKg, DEFAULT_FEED_PCT) }
    }
    val growthSeries = remember(groups) { Trends.farmAvgWeightSeries(groups, 8) }
    val biomassSeries = remember(groups) { Trends.farmBiomassSeries(groups, 8) }
    val healthy = remember(groups) { groups.count { it.status == GroupStatus.HEALTHY } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Dashboard", subtitle = AppClock.formatDate(AppClock.nowMillis()))

        Column(Modifier.padding(16.dp)) {
            // Overview stat grid 2x2
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Fish Groups",
                    value = groups.size.toString(),
                    accent = AquaPrimary,
                    subtitle = "$healthy healthy",
                    modifier = Modifier.weight(1f),
                ) { FishGlyph(Modifier.size(20.dp), tint = it) }
                StatCard(
                    title = "Total Fish",
                    value = totalFish.grouped(),
                    accent = AquaTurquoise,
                    trend = biomassSeries.takeIf { it.size > 1 },
                    modifier = Modifier.weight(1f),
                ) { WaterDropGlyph(Modifier.size(20.dp), tint = it) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Avg Weight",
                    value = UnitFormat.weight(avgWeightG, units),
                    accent = SeaweedGreen,
                    trend = growthSeries.takeIf { it.size > 1 },
                    modifier = Modifier.weight(1f),
                ) { RulerGlyph(Modifier.size(20.dp), tint = it) }
                StatCard(
                    title = "Daily Feed",
                    value = UnitFormat.mass(dailyFeedKg, units),
                    accent = CoralAccent,
                    subtitle = "at ${DEFAULT_FEED_PCT.toInt()}% biomass",
                    modifier = Modifier.weight(1f),
                ) { FeedGlyph(Modifier.size(20.dp), tint = it) }
            }

            Spacer(Modifier.height(20.dp))
            // Growth chart
            com.kmp.hook.ui.components.ChartCard(
                title = "Farm growth",
                subtitle = "Avg weight • last 8 weeks",
            ) {
                LineChart(
                    values = growthSeries,
                    labels = Trends.weekLabels(growthSeries.size),
                    lineColor = AquaPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            // Feed consumption summary
            com.kmp.hook.ui.components.ChartCard(
                title = "Feed consumption",
                subtitle = "Daily requirement by group",
            ) {
                val bars = remember(groups) {
                    groups.mapIndexed { i, g ->
                        BarDatum(
                            label = g.species.take(6),
                            value = FeedMath.dailyFeedKg(g.biomassKg, DEFAULT_FEED_PCT).toFloat(),
                            color = ChartSeries[i % ChartSeries.size],
                        )
                    }
                }
                if (bars.isEmpty()) {
                    Text("No groups yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    BarChart(bars, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total ${UnitFormat.mass(dailyFeedKg, units)} / day",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Quick actions")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    label = "Add group",
                    accent = AquaPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = rememberDebouncedClick { onNavigate(Route.FishGroupEdit(null)) },
                ) { PlusGlyph(Modifier.size(22.dp), tint = it) }
                QuickAction(
                    label = "New calc",
                    accent = AquaTurquoise,
                    modifier = Modifier.weight(1f),
                    onClick = rememberDebouncedClick { onNavigate(Route.FeedCalculator) },
                ) { FeedGlyph(Modifier.size(22.dp), tint = it) }
                QuickAction(
                    label = "Analytics",
                    accent = SeaweedGreen,
                    modifier = Modifier.weight(1f),
                    onClick = rememberDebouncedClick { onNavigate(Route.Analytics) },
                ) { AnalyticsGlyph(Modifier.size(22.dp), tint = it) }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Recent activity")
            Spacer(Modifier.height(8.dp))
            if (activity.isEmpty()) {
                Text(
                    "No activity yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(4.dp)) {
                        activity.take(6).forEach { ActivityRow(it) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { icon(accent) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val (color, glyph) = activityVisual(entry.type)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { glyph(color) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (entry.detail.isNotEmpty()) {
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            AppClock.relative(entry.epochMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun activityVisual(type: ActivityType): Pair<Color, @Composable (Color) -> Unit> = when (type) {
    ActivityType.FEEDING -> CoralAccent to { c -> FeedGlyph(Modifier.size(18.dp), tint = c) }
    ActivityType.STOCKING -> AquaPrimary to { c -> FishGlyph(Modifier.size(18.dp), tint = c) }
    ActivityType.MEASUREMENT -> SeaweedGreen to { c -> RulerGlyph(Modifier.size(18.dp), tint = c) }
    ActivityType.CALCULATION -> AquaTurquoise to { c -> FeedGlyph(Modifier.size(18.dp), tint = c) }
    ActivityType.HARVEST -> AquaTurquoise to { c -> FishGlyph(Modifier.size(18.dp), tint = c) }
    ActivityType.NOTE -> AquaPrimary to { c -> WaterDropGlyph(Modifier.size(18.dp), tint = c) }
}
