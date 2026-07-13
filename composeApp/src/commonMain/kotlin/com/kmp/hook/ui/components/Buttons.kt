package com.kmp.hook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kmp.hook.domain.calc.AppClock

/**
 * Debounce wrapper — ignores repeat taps within [windowMs].
 * Guards navigation / add / save actions against double-submission (button-spam protection).
 */
@Composable
fun rememberDebouncedClick(windowMs: Long = 600L, onClick: () -> Unit): () -> Unit {
    var last by remember { mutableStateOf(0L) }
    return {
        val now = AppClock.nowMillis()
        if (now - last >= windowMs) {
            last = now
            onClick()
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val debounced = rememberDebouncedClick(onClick = onClick)
    Button(
        onClick = debounced,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    icon()
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                }
                Text(text)
            }
        }
    }
}
