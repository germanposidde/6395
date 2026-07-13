package com.kmp.hook.elite.localnav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LocalNavObj {
    private val _screen = MutableStateFlow<ScreenManager>(ScreenManager.Welcome)
    val screen: StateFlow<ScreenManager> = _screen

    fun point(screen: ScreenManager) {
        _screen.value = screen
    }
}

sealed class ScreenManager {
    data object Welcome : ScreenManager()
    data object InternetProblem : ScreenManager()
    data object Game : ScreenManager()
    data object MenuPoint : ScreenManager()
    data object Settings : ScreenManager()
}
