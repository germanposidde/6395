package com.kmp.hook.feature.fishgroups

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.calc.UnitFormat
import com.kmp.hook.domain.calc.format
import com.kmp.hook.domain.calc.grouped
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.navigation.Route
import com.kmp.hook.platform.decodeBase64OrNull
import com.kmp.hook.platform.decodeToImageBitmap
import com.kmp.hook.ui.components.ChartCard
import com.kmp.hook.ui.components.ConfirmDialog
import com.kmp.hook.ui.components.EditGlyph
import com.kmp.hook.ui.components.HeaderIconButton
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.StatusPill
import com.kmp.hook.ui.components.TrashGlyph
import com.kmp.hook.ui.components.charts.LineChart
import com.kmp.hook.ui.components.charts.ProgressRing
import com.kmp.hook.ui.components.rememberDebouncedClick
import com.kmp.hook.ui.components.statusColor
import com.kmp.hook.ui.theme.AquaPrimary

@Composable
fun FishGroupDetailScreen(
    groupId: String,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val units = LocalUnitSystem.current
    val groups by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val group = groups.firstOrNull { it.id == groupId }
    var confirmDelete by remember { mutableStateOf(false) }

    if (group == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenHeader(title = "Group", onBack = onBack)
            Text("This group was removed.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = group.species,
            subtitle = "${group.quantity.grouped()} fish • stocked ${AppClock.formatDate(group.stockingEpochMillis)}",
            onBack = onBack,
        ) {
            HeaderIconButton(onClick = rememberDebouncedClick { onNavigate(Route.FishGroupEdit(group.id)) }) {
                EditGlyph(Modifier.size(20.dp), tint = Color.White)
            }
            Spacer(Modifier.size(8.dp))
            HeaderIconButton(onClick = { confirmDelete = true }) {
                TrashGlyph(Modifier.size(20.dp), tint = Color.White)
            }
        }

        Column(Modifier.padding(16.dp)) {
            val photo = remember(group.photoBase64) {
                group.photoBase64?.decodeBase64OrNull()?.let { decodeToImageBitmap(it) }
            }
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = "${group.species} photo",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
            }
            // Status + growth ring summary
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress = group.growthProgress, size = 78.dp, color = statusColor(group.status)) {
                        Text("${(group.growthProgress * 100).format(0)}%", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        StatusPill(group.status)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Target ${UnitFormat.weight(group.targetWeightGrams, units)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Current ${UnitFormat.weight(group.avgWeightGrams, units)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Stat grid
            val daily = FeedMath.dailyFeedKg(group.biomassKg, 3.0)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Quantity", group.quantity.grouped(), AquaPrimary, Modifier.weight(1f))
                MetricTile("Avg weight", UnitFormat.weight(group.avgWeightGrams, units), AquaPrimary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Biomass", UnitFormat.mass(group.biomassKg, units), AquaPrimary, Modifier.weight(1f))
                MetricTile("Daily feed", UnitFormat.mass(daily, units), AquaPrimary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "Age",
                    "${AppClock.daysBetween(group.stockingEpochMillis, AppClock.nowMillis())} days",
                    AquaPrimary, Modifier.weight(1f),
                )
                MetricTile("Feed price", "${UnitFormat.massUnit(units)} ${group.feedPricePerKg.format(2)}", AquaPrimary, Modifier.weight(1f))
            }

            if (group.growthPoints.size > 1) {
                Spacer(Modifier.height(16.dp))
                ChartCard(title = "Growth history", subtitle = "Average weight over time") {
                    LineChart(
                        values = group.growthPoints.map { it.avgWeightGrams.toFloat() },
                        labels = group.growthPoints.map { AppClock.formatDayMonth(it.epochMillis) },
                        lineColor = statusColor(group.status),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (group.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(group.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete group?",
            message = "${group.species} and its history will be permanently removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                container.fishGroupRepo.delete(group.id)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
