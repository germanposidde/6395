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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated smooth line chart with gradient fill, gridlines and optional x-axis labels.
 * Colours are captured to locals before the draw lambda (Compose theme getters are
 * @Composable and cannot be read inside DrawScope).
 */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    labels: List<String> = emptyList(),
    showPoints: Boolean = true,
) {
    val fillTop = lineColor.copy(alpha = 0.28f)
    val fillBottom = lineColor.copy(alpha = 0.02f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val pointColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontSize = 10.sp)

    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val progress = anim.value

    Canvas(modifier.height(height)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val labelSpace = if (labels.isNotEmpty()) 16f * density else 0f
        val h = size.height - labelSpace
        val maxV = (values.maxOrNull() ?: 1f)
        val minV = (values.minOrNull() ?: 0f)
        val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
        val padTop = h * 0.12f
        val padBottom = h * 0.08f
        val chartH = h - padTop - padBottom

        // gridlines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = padTop + chartH * i / gridLines
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f * density)
        }

        val n = values.size
        fun px(i: Int) = if (n == 1) w / 2 else w * i / (n - 1)
        fun pyFull(v: Float) = padTop + chartH * (1f - (v - minV) / range)
        // animate growth from the baseline
        fun py(v: Float): Float {
            val full = pyFull(v)
            val base = padTop + chartH
            return base - (base - full) * progress
        }

        val pts = values.mapIndexed { i, v -> Offset(px(i), py(v)) }

        // smooth path via midpoint quadratics
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val midX = (pts[i].x + pts[i + 1].x) / 2
                val midY = (pts[i].y + pts[i + 1].y) / 2
                quadraticTo(pts[i].x, pts[i].y, midX, midY)
            }
            lineTo(pts.last().x, pts.last().y)
        }

        // fill
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, padTop + chartH)
            lineTo(pts.first().x, padTop + chartH)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(fillTop, fillBottom),
                startY = padTop,
                endY = padTop + chartH,
            ),
        )
        drawPath(line, lineColor, style = Stroke(width = 2.5f * density, cap = StrokeCap.Round))

        if (showPoints) {
            pts.forEach { p ->
                drawCircle(lineColor, 4f * density, p)
                drawCircle(pointColor, 1.8f * density, p)
            }
        }

        // x labels
        if (labels.isNotEmpty()) {
            val step = (labels.size - 1).coerceAtLeast(1)
            labels.forEachIndexed { i, label ->
                if (labels.size <= 6 || i % ((labels.size / 5).coerceAtLeast(1)) == 0) {
                    val tl = measurer.measure(label, labelStyle)
                    val x = (px(i) - tl.size.width / 2).coerceIn(0f, w - tl.size.width)
                    drawText(tl, topLeft = Offset(x, h + 2f * density))
                }
            }
            step // keep referenced
        }
    }
}
