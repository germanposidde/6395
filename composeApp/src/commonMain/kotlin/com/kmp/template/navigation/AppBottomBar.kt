package com.kmp.hook.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kmp.hook.ui.components.AnalyticsGlyph
import com.kmp.hook.ui.components.DashboardGlyph
import com.kmp.hook.ui.components.FeedGlyph
import com.kmp.hook.ui.components.FishGlyph
import com.kmp.hook.ui.components.SettingsGlyph

@Composable
fun AppBottomBar(selected: Route, onSelect: (Route) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        TabDest.entries.forEach { dest ->
            NavigationBarItem(
                selected = selected == dest.route,
                onClick = { onSelect(dest.route) },
                icon = { TabIcon(dest) },
                label = { Text(dest.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun TabIcon(dest: TabDest) {
    val m = Modifier.size(24.dp)
    when (dest) {
        TabDest.DASHBOARD -> DashboardGlyph(m)
        TabDest.GROUPS -> FishGlyph(m)
        TabDest.CALCULATOR -> FeedGlyph(m)
        TabDest.ANALYTICS -> AnalyticsGlyph(m)
        TabDest.SETTINGS -> SettingsGlyph(m)
    }
}
