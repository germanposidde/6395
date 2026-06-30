package com.kmp.template

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    Gray(
        loading = { LoadingScreen() },
        noInternet = { NoInternetScreen(it) },
        white = { AppNavGraph() }
    )
}