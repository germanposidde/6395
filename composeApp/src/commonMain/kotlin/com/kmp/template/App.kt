package com.kmp.hook

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.di.AppContainer
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.domain.calc.LocalUnitSystem
import com.kmp.hook.domain.model.ThemeMode
import com.kmp.hook.ui.theme.AquaTheme

@Composable
@Preview
fun App() {
    val container = remember { AppContainer() }
    CompositionLocalProvider(LocalAppContainer provides container) {
        val settings by container.settingsRepo.settings.collectAsStateWithLifecycle()
        val dark = when (settings.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
        AquaTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalUnitSystem provides settings.units) {
                Gray(
                    loading = { LoadingScreen() },
                    noInternet = { NoInternetScreen(it) },
                    white = { AppNavGraph() },
                )
            }
        }
    }
}
