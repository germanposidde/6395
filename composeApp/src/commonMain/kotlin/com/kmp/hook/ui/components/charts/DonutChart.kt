package com.kmp.hook.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class Slice(val label: String, val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 160.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val progress = anim.value
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f

    Canvas(modifier.size(diameter)) {
        val sw = strokeWidth.toPx()
        val inset = sw / 2
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(inset, inset)
        var start = -90f
        slices.forEach { s ->
            val sweep = (s.value / total) * 360f * progress
            drawArc(
                color = s.color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}
