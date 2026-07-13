package com.kmp.hook

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kmp.hook.elite.LifecyclePushEffect
import com.kmp.hook.elite.PushDialogAb
import com.kmp.hook.elite.StartCache
import com.kmp.hook.elite.localnav.InternetState
import com.kmp.hook.elite.localnav.LocalNavObj.point
import com.kmp.hook.elite.localnav.ScreenManager
import com.kmp.hook.elite.localnav.getInternetState
import com.kmp.hook.navigation.PlatformBackHandler
import com.kmp.hook.ui.components.FishGlyph
import com.kmp.hook.ui.theme.aquaHeaderGradient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LoadingScreenA(
    activity: ComponentActivity,
    startCache: StartCache
) {
    PlatformBackHandler(enabled = true) { /* gate is locked */ }

    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )
    PushDialogAb.PushPromptHost()
    var isNotStub: Boolean by remember { mutableStateOf(false) }
    LifecyclePushEffect(isNotStub)

    LaunchedEffect(Unit) {
        when (getInternetState(activity)) {
            InternetState.NoConnection -> {
                delay(1500.milliseconds)
                point(ScreenManager.InternetProblem)
            }

            InternetState.Connected -> {
                val result = runCatching {
                    startCache.ifConnected(activity, startCache)
                }

                if (result.isFailure) {
                    point(ScreenManager.MenuPoint)
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(aquaHeaderGradient(dark = isSystemInDarkTheme())),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp).scale(pulse).clip(CircleShape)
                        .background(Color.White.copy(alpha = ringAlpha)),
                )
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FishGlyph(Modifier.size(52.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Preparing your aquaculture data",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Just a moment…",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
