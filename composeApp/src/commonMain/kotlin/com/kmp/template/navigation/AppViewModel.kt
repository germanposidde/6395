package com.kmp.hook.navigation

import androidx.lifecycle.ViewModel

/** Root holder so the navigator survives configuration changes / process recreation. */
class AppViewModel : ViewModel() {
    val navigator = AppNavigator()
}
