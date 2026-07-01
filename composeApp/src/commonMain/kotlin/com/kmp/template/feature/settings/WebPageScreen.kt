package com.kmp.hook.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kmp.hook.PlatformWebView
import com.kmp.hook.ui.components.ScreenHeader

@Composable
fun WebPageScreen(url: String, title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = title, onBack = onBack)
        PlatformWebView(url = url, modifier = Modifier.fillMaxSize())
    }
}
