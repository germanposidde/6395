package com.kmp.hook.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses an in-UI back affordance; no system back to intercept.
}
