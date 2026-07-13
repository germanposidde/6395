package com.kmp.hook.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/** Compact inline trend line for stat cards (no axes, no animation overhead). */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = true,
) {
    val fillTop = color.copy(alpha = 0.30f)
    val fillBottom = color.copy(alpha = 0f)
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val maxV = values.maxOrNull() ?: 1f
        val minV = values.minOrNull() ?: 0f
        val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
        val pad = h * 0.15f
        fun x(i: Int) = w * i / (values.size - 1)
        fun y(v: Float) = pad + (h - 2 * pad) * (1f - (v - minV) / range)
        val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val mx = (pts[i].x + pts[i + 1].x) / 2
                val my = (pts[i].y + pts[i + 1].y) / 2
                quadraticTo(pts[i].x, pts[i].y, mx, my)
            }
            lineTo(pts.last().x, pts.last().y)
        }
        if (filled) {
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, h)
                lineTo(pts.first().x, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(fillTop, fillBottom)))
        }
        drawPath(line, color, style = Stroke(width = 2f * density, cap = StrokeCap.Round))
    }
}
