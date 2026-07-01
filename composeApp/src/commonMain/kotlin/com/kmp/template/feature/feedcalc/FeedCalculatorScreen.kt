package com.kmp.hook.feature.feedcalc

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.calc.UnitFormat
import com.kmp.hook.domain.calc.format
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.ui.components.AppTextField
import com.kmp.hook.ui.components.LabeledSlider
import com.kmp.hook.ui.components.PrimaryActionButton
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.TrashGlyph
import com.kmp.hook.ui.components.clickableNoRipple
import com.kmp.hook.ui.components.dismissKeyboardOnTap
import com.kmp.hook.ui.theme.AquaPrimary
import com.kmp.hook.ui.theme.AquaTurquoise
import com.kmp.hook.ui.theme.CoralAccent
import com.kmp.hook.ui.theme.SeaweedGreen
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun FeedCalculatorScreen() {
    val container = LocalAppContainer.current
    val units = LocalUnitSystem.current
    val vm: FeedCalculatorViewModel = viewModel {
        FeedCalculatorViewModel(container.feedRepo, container.activityRepo)
    }
    val groups by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val history by container.feedRepo.history.collectAsStateWithLifecycle()
    val r = vm.result
    val currency = container.settingsRepo.settings.collectAsStateWithLifecycle().value.currencySymbol

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).dismissKeyboardOnTap(),
    ) {
        ScreenHeader(title = "Feed Calculator", subtitle = "Biomass • FCR • growth • cost")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
        ) {
            // Result hero card
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(),
            ) {
                Box(Modifier.background(Brush.linearGradient(listOf(AquaPrimary, AquaTurquoise)))) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("Daily feed requirement", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            UnitFormat.mass(r.dailyFeedKg, units),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HeroStat("Biomass", UnitFormat.mass(r.biomassKg, units))
                            HeroStat("Predicted", UnitFormat.weight(r.predictedWeightGrams, units))
                            HeroStat("Cost", "$currency${r.feedCost.format(0)}")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Group prefill chips
            if (groups.isNotEmpty()) {
                Text("Use a group", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        val selected = vm.selectedGroupId == g.id
                        Surface(
                            shape = CircleShape,
                            color = if (selected) AquaPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.clickableNoRipple { vm.prefillFrom(g) },
                        ) {
                            Text(
                                g.species,
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) AquaPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Inputs card
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppTextField(vm.label, { vm.label = it }, "Label")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppTextField(
                            vm.quantity.toString(),
                            { vm.quantity = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                            "Quantity", Modifier.weight(1f), KeyboardType.Number,
                        )
                        AppTextField(
                            vm.avgWeight.toInt().toString(),
                            { vm.avgWeight = it.filter { c -> c.isDigit() }.toDoubleOrNull() ?: 0.0 },
                            "Avg weight", Modifier.weight(1f), KeyboardType.Number, suffix = "g",
                        )
                    }
                    LabeledSlider(
                        "Feeding rate", vm.feedingPercent.toFloat(), { vm.feedingPercent = it.toDouble() },
                        1f..6f, "${vm.feedingPercent.format(1)}%", steps = 9,
                    )
                    LabeledSlider(
                        "FCR (feed conversion)", vm.fcr.toFloat(), { vm.fcr = it.toDouble() },
                        1f..2.5f, vm.fcr.format(2), steps = 14,
                    )
                    LabeledSlider(
                        "Horizon", vm.horizonDays.toFloat(), { vm.horizonDays = it.toInt() },
                        7f..120f, "${vm.horizonDays} days",
                    )
                    AppTextField(
                        vm.pricePerKg.format(2),
                        { vm.pricePerKg = it.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0 },
                        "Feed price per kg", keyboardType = KeyboardType.Decimal,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // Breakdown grid
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultTile("Daily gain/fish", UnitFormat.weight(r.dailyGainPerFishGrams, units), SeaweedGreen, Modifier.weight(1f))
                ResultTile("Total feed", UnitFormat.mass(r.totalFeedKg, units), AquaPrimary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultTile("FCR", r.fcr.format(2), AquaTurquoise, Modifier.weight(1f))
                ResultTile("Feed cost", "$currency${r.feedCost.format(2)}", CoralAccent, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            PrimaryActionButton(
                text = "Save calculation",
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth(),
            )

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                history.take(15).forEach { calc ->
                    HistoryRow(calc, units, currency) { container.feedRepo.delete(calc.id) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ResultTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(
    calc: FeedCalculation,
    units: com.kmp.hook.domain.model.UnitSystem,
    currency: String,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(calc.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${UnitFormat.mass(calc.dailyFeedKg, units)}/day • FCR ${calc.fcr.format(2)} • $currency${calc.feedCost.format(0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(AppClock.relative(calc.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickableNoRipple(onDelete), contentAlignment = Alignment.Center) {
                TrashGlyph(Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
