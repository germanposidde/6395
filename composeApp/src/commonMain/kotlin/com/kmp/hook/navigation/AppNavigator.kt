package com.kmp.hook.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Lightweight, fully-multiplatform navigator: one selected tab + a detail back-stack. */
class AppNavigator {

    var tab by mutableStateOf<Route>(Route.Dashboard)
        private set

    private val detailStack = mutableStateListOf<Route>()

    val current: Route get() = detailStack.lastOrNull() ?: tab

    val canPop: Boolean get() = detailStack.isNotEmpty()

    fun selectTab(route: Route) {
        detailStack.clear()
        tab = route
    }

    fun push(route: Route) {
        detailStack.add(route)
    }

    /** Returns true if a detail screen was popped. */
    fun pop(): Boolean = if (detailStack.isNotEmpty()) {
        detailStack.removeAt(detailStack.lastIndex)
        true
    } else false
}
