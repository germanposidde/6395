package com.kmp.hook

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmp.hook.feature.analytics.AnalyticsScreen
import com.kmp.hook.feature.dashboard.DashboardScreen
import com.kmp.hook.feature.feedcalc.FeedCalculatorScreen
import com.kmp.hook.feature.fishgroups.FishGroupDetailScreen
import com.kmp.hook.feature.fishgroups.FishGroupEditScreen
import com.kmp.hook.feature.fishgroups.FishGroupsScreen
import com.kmp.hook.feature.settings.SettingsScreen
import com.kmp.hook.feature.settings.WebPageScreen
import com.kmp.hook.navigation.AppBottomBar
import com.kmp.hook.navigation.AppViewModel
import com.kmp.hook.navigation.PlatformBackHandler
import com.kmp.hook.navigation.Route

@Composable
fun AppNavGraph() {
    val appVm: AppViewModel = viewModel { AppViewModel() }
    val nav = appVm.navigator

    PlatformBackHandler(enabled = nav.canPop) { nav.pop() }

    val navigate: (Route) -> Unit = { route ->
        when (route) {
            Route.Dashboard, Route.FishGroups, Route.FeedCalculator,
            Route.Analytics, Route.Settings -> nav.selectTab(route)
            else -> nav.push(route)
        }
    }
    val onBack: () -> Unit = { nav.pop() }

    Scaffold(
        bottomBar = {
            if (!nav.canPop) AppBottomBar(selected = nav.tab, onSelect = nav::selectTab)
        },
    ) { innerPadding ->
        Crossfade(
            targetState = nav.current,
            animationSpec = tween(280),
            modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
        ) { route ->
            Box(Modifier.fillMaxSize()) {
                when (route) {
                    Route.Dashboard -> DashboardScreen(onNavigate = navigate)
                    Route.FishGroups -> FishGroupsScreen(onNavigate = navigate)
                    Route.FeedCalculator -> FeedCalculatorScreen()
                    Route.Analytics -> AnalyticsScreen()
                    Route.Settings -> SettingsScreen(onNavigate = navigate)
                    is Route.FishGroupDetail -> FishGroupDetailScreen(route.groupId, navigate, onBack)
                    is Route.FishGroupEdit -> FishGroupEditScreen(route.groupId, onBack)
                    is Route.WebPage -> WebPageScreen(route.url, route.title, onBack)
                }
            }
        }
    }
}
