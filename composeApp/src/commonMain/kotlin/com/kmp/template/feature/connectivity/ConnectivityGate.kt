package com.kmp.hook.feature.connectivity

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kmp.hook.navigation.PlatformBackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val MIN_DWELL_MS = 950L

private enum class GatePhase { CHECKING, ONLINE, OFFLINE }

/**
 * Shared connectivity gate. The Loading screen is the only doorway in: cold start, Retry,
 * and auto-reconnect-while-offline all route through CHECKING (with its minimum dwell)
 * before the app is revealed. System back is locked until the app is shown.
 */
@Composable
fun ConnectivityGate(
    isOnline: Flow<Boolean>,
    loading: @Composable () -> Unit,
    noInternet: @Composable (onRetry: () -> Unit) -> Unit,
    white: @Composable () -> Unit,
) {
    var phase by remember { mutableStateOf(GatePhase.CHECKING) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        phase = GatePhase.CHECKING
        val dwell = launch { delay(MIN_DWELL_MS) }
        isOnline.collectLatest { online ->
            if (online) {
                if (phase == GatePhase.OFFLINE) {
                    phase = GatePhase.CHECKING
                    delay(MIN_DWELL_MS)
                } else {
                    dwell.join()
                }
                phase = GatePhase.ONLINE
            } else {
                if (phase == GatePhase.CHECKING) dwell.join()
                phase = GatePhase.OFFLINE
            }
        }
    }

    PlatformBackHandler(enabled = phase != GatePhase.ONLINE) { /* swallow */ }

    Crossfade(targetState = phase, animationSpec = tween(350)) { p ->
        when (p) {
            GatePhase.CHECKING -> loading()
            GatePhase.OFFLINE -> noInternet { retryKey++ }
            GatePhase.ONLINE -> white()
        }
    }
}
