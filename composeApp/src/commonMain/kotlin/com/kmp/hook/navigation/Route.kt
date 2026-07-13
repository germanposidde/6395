package com.kmp.hook.navigation

sealed interface Route {
    data object Dashboard : Route
    data object FishGroups : Route
    data object FeedCalculator : Route
    data object Analytics : Route
    data object Settings : Route

    data class FishGroupDetail(val groupId: String) : Route
    data class FishGroupEdit(val groupId: String?) : Route
    data class WebPage(val url: String, val title: String) : Route
}

/** Bottom-navigation destinations in display order. */
enum class TabDest(val route: Route, val label: String) {
    DASHBOARD(Route.Dashboard, "Home"),
    GROUPS(Route.FishGroups, "Groups"),
    CALCULATOR(Route.FeedCalculator, "Feed"),
    ANALYTICS(Route.Analytics, "Stats"),
    SETTINGS(Route.Settings, "Settings"),
}
