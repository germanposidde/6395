package com.kmp.hook

import androidx.compose.runtime.Composable

@Composable
expect fun Gray(
    loading: @Composable () -> Unit,
    noInternet: @Composable (onRetry: () -> Unit) -> Unit,
    white: @Composable () -> Unit
)