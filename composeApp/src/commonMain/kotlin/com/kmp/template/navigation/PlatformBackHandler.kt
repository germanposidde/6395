package com.kmp.hook.navigation

import androidx.compose.runtime.Composable

/** Intercepts the system back gesture on platforms that have one (Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
