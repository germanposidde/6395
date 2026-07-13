package com.kmp.hook.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.domain.model.ThemeMode
import com.kmp.hook.domain.model.UnitSystem
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.navigation.Route
import com.kmp.hook.platform.platformName
import com.kmp.hook.platform.platformPrivacyUrl
import com.kmp.hook.platform.platformShowsTerms
import com.kmp.hook.platform.platformTermsUrl
import com.kmp.hook.ui.components.ChevronRightGlyph
import com.kmp.hook.ui.components.ConfirmDialog
import com.kmp.hook.ui.components.InfoGlyph
import com.kmp.hook.ui.components.MoonGlyph
import com.kmp.hook.ui.components.ResetGlyph
import com.kmp.hook.ui.components.RulerGlyph
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.SectionHeader
import com.kmp.hook.ui.components.SegmentedControl
import com.kmp.hook.ui.components.SettingRow
import com.kmp.hook.ui.components.SunGlyph

@Composable
fun SettingsScreen(onNavigate: (Route) -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settingsRepo.settings.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Settings", subtitle = "Appearance, data & preferences")

        Column(Modifier.padding(16.dp)) {
            // Appearance
            SettingsCard {
                SectionHeader("Appearance")
                Spacer(Modifier.height(12.dp))
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                    onSelected = { i -> container.settingsRepo.update { it.copy(themeMode = ThemeMode.entries[i]) } },
                )
                Spacer(Modifier.height(16.dp))
                Text("Units", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SegmentedControl(
                    options = listOf("Metric", "Imperial"),
                    selectedIndex = UnitSystem.entries.indexOf(settings.units),
                    onSelected = { i -> container.settingsRepo.update { it.copy(units = UnitSystem.entries[i]) } },
                )
            }

            Spacer(Modifier.height(16.dp))
            // Data
            SettingsCard {
                SectionHeader("Data")
                SettingRow(
                    title = "Load sample data",
                    subtitle = "Replace with demo groups & history",
                    leading = { RulerGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
                    onClick = { container.reseedSampleData(); message = "Sample data loaded" },
                )
                SettingRow(
                    title = "Reset all data",
                    subtitle = "Delete every group and record",
                    leading = { ResetGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error) },
                    onClick = { confirmReset = true },
                )
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))
            // Legal & about
            SettingsCard {
                SectionHeader("About")
                SettingRow(
                    title = "Privacy Policy",
                    leading = { InfoGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
                    trailing = { ChevronRightGlyph(Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { onNavigate(Route.WebPage(platformPrivacyUrl, "Privacy Policy")) },
                )
                if (platformShowsTerms) {
                    SettingRow(
                        title = "Terms of Use",
                        leading = { InfoGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
                        trailing = { ChevronRightGlyph(Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { onNavigate(Route.WebPage(platformTermsUrl, "Terms of Use")) },
                    )
                }
                SettingRow(
                    title = "Version",
                    subtitle = "1.0 • $platformName",
                    leading = { InfoGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }

            Spacer(Modifier.height(16.dp))
            ThemeHintRow(dark = isDarkActive(settings.themeMode))
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all data?",
            message = "This permanently deletes every fish group, calculation and activity record.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { container.resetAllData(); message = "All data cleared" },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ThemeHintRow(dark: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (dark) MoonGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            else SunGlyph(Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Freshwater aquaculture, beautifully tracked.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun isDarkActive(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}
