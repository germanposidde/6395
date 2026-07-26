package com.kmp.hook

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable

@Composable
expect fun Gray(
    loading: @Composable () -> Unit,
    noInternet: @Composable (onRetry: () -> Unit) -> Unit,
    white: @Composable () -> Unit,
    transitionSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        fadeIn().togetherWith(fadeOut())
    }
)