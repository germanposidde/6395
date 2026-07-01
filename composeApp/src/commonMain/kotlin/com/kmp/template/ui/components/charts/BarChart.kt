package com.kmp.hook.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BarDatum(val label: String, val value: Float, val color: Color)

@Composable
fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontSize = 10.sp)

    val anim = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val progress = anim.value

    Canvas(modifier.height(height)) {
        if (bars.isEmpty()) return@Canvas
        val w = size.width
        val labelSpace = 18f * density
        val h = size.height - labelSpace
        val maxV = bars.maxOf { it.value }.takeIf { it > 0f } ?: 1f

        // gridlines
        for (i in 0..3) {
            val y = h * i / 3
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f * density)
        }

        val n = bars.size
        val slot = w / n
        val barW = (slot * 0.5f).coerceAtMost(34f * density)
        bars.forEachIndexed { i, b ->
            val cx = slot * i + slot / 2
            val barH = (b.value / maxV) * h * 0.92f * progress
            val top = h - barH
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(b.color, b.color.copy(alpha = 0.65f)),
                    startY = top,
                    endY = h,
                ),
                topLeft = Offset(cx - barW / 2, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2.6f, barW / 2.6f),
            )
            val tl = measurer.measure(b.label, labelStyle)
            val x = (cx - tl.size.width / 2).coerceIn(0f, w - tl.size.width)
            drawText(tl, topLeft = Offset(x, h + 4f * density))
        }
    }
}
