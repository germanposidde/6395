package com.kmp.hook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kmp.hook.navigation.PlatformBackHandler
import com.kmp.hook.ui.components.PrimaryActionButton
import com.kmp.hook.ui.components.ResetGlyph
import com.kmp.hook.ui.components.WaterDropGlyph
import com.kmp.hook.ui.theme.AquaPrimary

@Composable
fun NoInternetScreen(onRetry: () -> Unit) {
    PlatformBackHandler(enabled = true) { /* gate is locked */ }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            WaterDropGlyph(Modifier.size(56.dp), tint = AquaPrimary)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "You're offline",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We couldn't reach the network. Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            icon = { ResetGlyph(Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary) },
        )
    }
}
