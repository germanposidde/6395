package com.kmp.hook.feature.fishgroups

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.calc.UnitFormat
import com.kmp.hook.domain.calc.format
import com.kmp.hook.domain.calc.grouped
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.model.GroupStatus
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.navigation.Route
import com.kmp.hook.ui.components.AppSearchBar
import com.kmp.hook.ui.components.FishGlyph
import com.kmp.hook.ui.components.HeaderIconButton
import com.kmp.hook.ui.components.PlusGlyph
import com.kmp.hook.ui.components.PrimaryActionButton
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.SegmentedControl
import com.kmp.hook.ui.components.StatusPill
import com.kmp.hook.ui.components.EmptyState
import com.kmp.hook.ui.components.charts.ProgressRing
import com.kmp.hook.ui.components.clickableNoRipple
import com.kmp.hook.ui.components.displayName
import com.kmp.hook.ui.components.rememberDebouncedClick
import com.kmp.hook.ui.components.statusColor
import com.kmp.hook.ui.theme.AquaPrimary

@Composable
fun FishGroupsScreen(onNavigate: (Route) -> Unit) {
    val container = LocalAppContainer.current
    val vm: FishGroupsViewModel = viewModel {
        FishGroupsViewModel(container.fishGroupRepo, container.activityRepo)
    }
    val units = LocalUnitSystem.current
    val all by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val visible = vm.visible(all)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = "Fish Groups",
            subtitle = "${all.size} groups • ${all.sumOf { it.quantity }.grouped()} fish",
        ) {
            HeaderIconButton(onClick = rememberDebouncedClick { onNavigate(Route.FishGroupEdit(null)) }) {
                PlusGlyph(Modifier.size(22.dp), tint = Color.White)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            AppSearchBar(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search species or notes")
            Spacer(Modifier.height(12.dp))
            StatusFilterRow(selected = vm.statusFilter, onSelect = { vm.statusFilter = it })
            Spacer(Modifier.height(10.dp))
            SegmentedControl(
                options = GroupSort.entries.map { it.label },
                selectedIndex = GroupSort.entries.indexOf(vm.sort),
                onSelected = { vm.sort = GroupSort.entries[it] },
            )
        }

        if (visible.isEmpty()) {
            EmptyState(
                title = if (all.isEmpty()) "No fish groups yet" else "No matches",
                subtitle = if (all.isEmpty()) "Add your first group to start tracking growth and feed."
                else "Try a different search or filter.",
                icon = { FishGlyph(Modifier.size(34.dp), tint = AquaPrimary) },
                action = {
                    if (all.isEmpty()) {
                        PrimaryActionButton(
                            text = "Add group",
                            onClick = { onNavigate(Route.FishGroupEdit(null)) },
                            icon = { PlusGlyph(Modifier.size(18.dp), tint = Color.White) },
                        )
                    }
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        units = units,
                        onClick = rememberDebouncedClick { onNavigate(Route.FishGroupDetail(group.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(selected: GroupStatus?, onSelect: (GroupStatus?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All", selected == null, MaterialTheme.colorScheme.primary) { onSelect(null) }
        GroupStatus.entries.forEach { st ->
            FilterPill(st.displayName(), selected == st, statusColor(st)) {
                onSelect(if (selected == st) null else st)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun GroupCard(group: FishGroup, units: com.kmp.hook.domain.model.UnitSystem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = group.growthProgress,
                    size = 60.dp,
                    color = statusColor(group.status),
                ) {
                    Text(
                        "${(group.growthProgress * 100).format(0)}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.species,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(8.dp))
                    StatusPill(group.status)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${group.quantity.grouped()} fish • ${UnitFormat.weight(group.avgWeightGrams, units)} avg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Biomass ${UnitFormat.mass(group.biomassKg, units)} • ${AppClock.daysBetween(group.stockingEpochMillis, AppClock.nowMillis())}d old",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
