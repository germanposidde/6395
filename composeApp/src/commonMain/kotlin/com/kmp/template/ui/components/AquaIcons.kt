package com.kmp.hook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn line icons (24x24 grid) — no external icon dependency, fully multiplatform,
 * and consistent with the aquatic visual language.
 */

@Composable
private fun Glyph(
    modifier: Modifier,
    tint: Color,
    draw: DrawScope.(k: Float, stroke: Stroke, color: Color) -> Unit,
) {
    val color = tint
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val stroke = Stroke(
            width = 2f * k,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        draw(k, stroke, color)
    }
}

private fun DrawScope.path(k: Float, vararg pts: Float): Path {
    val p = Path()
    var i = 0
    while (i < pts.size) {
        val x = pts[i] * k
        val y = pts[i + 1] * k
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        i += 2
    }
    return p
}

@Composable
fun DashboardGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        // four rounded panels
        val r = 2.5f * k
        drawRoundRectStroke(3f, 3f, 8.5f, 9f, k, r, s, c)
        drawRoundRectStroke(12.5f, 3f, 8.5f, 5.5f, k, r, s, c)
        drawRoundRectStroke(3f, 15f, 8.5f, 6f, k, r, s, c)
        drawRoundRectStroke(12.5f, 11.5f, 8.5f, 9.5f, k, r, s, c)
    }

@Composable
fun FishGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        val body = Path().apply {
            moveTo(4f * k, 12f * k)
            cubicTo(7f * k, 6f * k, 14f * k, 6f * k, 18f * k, 12f * k)
            cubicTo(14f * k, 18f * k, 7f * k, 18f * k, 4f * k, 12f * k)
        }
        drawPath(body, c, style = s)
        // tail
        drawPath(path(k, 18f, 12f, 21f, 8.5f, 21f, 15.5f, 18f, 12f), c, style = s)
        // eye
        drawCircle(c, 0.9f * k, Offset(8f * k, 11f * k), style = Fill)
    }

@Composable
fun FeedGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        // bowl with pellets
        val bowl = Path().apply {
            moveTo(4f * k, 12f * k)
            lineTo(20f * k, 12f * k)
            cubicTo(20f * k, 18f * k, 15f * k, 20f * k, 12f * k, 20f * k)
            cubicTo(9f * k, 20f * k, 4f * k, 18f * k, 4f * k, 12f * k)
        }
        drawPath(bowl, c, style = s)
        drawCircle(c, 1.1f * k, Offset(9f * k, 8.5f * k), style = Fill)
        drawCircle(c, 1.1f * k, Offset(13f * k, 6.5f * k), style = Fill)
        drawCircle(c, 1.1f * k, Offset(15f * k, 9.5f * k), style = Fill)
    }

@Composable
fun AnalyticsGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 4f, 20f, 20f, 20f), c, style = s)
        drawPath(path(k, 7f, 20f, 7f, 13f), c, style = s)
        drawPath(path(k, 12f, 20f, 12f, 8f), c, style = s)
        drawPath(path(k, 17f, 20f, 17f, 11f), c, style = s)
    }

@Composable
fun SettingsGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawCircle(c, 3f * k, Offset(12f * k, 12f * k), style = s)
        // spokes
        val spokes = listOf(12f to 3f, 12f to 21f, 3f to 12f, 21f to 12f, 5.5f to 5.5f, 18.5f to 18.5f, 5.5f to 18.5f, 18.5f to 5.5f)
        spokes.forEach { (x, y) ->
            val cx = 12f * k; val cy = 12f * k
            val dx = x * k - cx; val dy = y * k - cy
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val ux = dx / len; val uy = dy / len
            drawLine(c, Offset(cx + ux * 4f * k, cy + uy * 4f * k), Offset(x * k, y * k), s.width, s.cap)
        }
    }

@Composable
fun PlusGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawLine(c, Offset(12f * k, 5f * k), Offset(12f * k, 19f * k), s.width, s.cap)
        drawLine(c, Offset(5f * k, 12f * k), Offset(19f * k, 12f * k), s.width, s.cap)
    }

@Composable
fun SearchGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawCircle(c, 6f * k, Offset(10.5f * k, 10.5f * k), style = s)
        drawLine(c, Offset(15f * k, 15f * k), Offset(20f * k, 20f * k), s.width, s.cap)
    }

@Composable
fun BackGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 14f, 6f, 8f, 12f, 14f, 18f), c, style = s)
    }

@Composable
fun ChevronRightGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 10f, 6f, 16f, 12f, 10f, 18f), c, style = s)
    }

@Composable
fun CloseGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawLine(c, Offset(6f * k, 6f * k), Offset(18f * k, 18f * k), s.width, s.cap)
        drawLine(c, Offset(18f * k, 6f * k), Offset(6f * k, 18f * k), s.width, s.cap)
    }

@Composable
fun CheckGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 5f, 12.5f, 10f, 17.5f, 19f, 7f), c, style = s)
    }

@Composable
fun EditGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 4f, 20f, 4f, 16f, 16f, 4f, 20f, 8f, 8f, 20f, 4f, 20f), c, style = s)
        drawLine(c, Offset(13f * k, 7f * k), Offset(17f * k, 11f * k), s.width, s.cap)
    }

@Composable
fun TrashGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawLine(c, Offset(5f * k, 7f * k), Offset(19f * k, 7f * k), s.width, s.cap)
        drawPath(path(k, 9f, 7f, 9f, 4.5f, 15f, 4.5f, 15f, 7f), c, style = s)
        drawPath(path(k, 6.5f, 7f, 7.5f, 20f, 16.5f, 20f, 17.5f, 7f), c, style = s)
        drawLine(c, Offset(10f * k, 10.5f * k), Offset(10f * k, 17f * k), s.width, s.cap)
        drawLine(c, Offset(14f * k, 10.5f * k), Offset(14f * k, 17f * k), s.width, s.cap)
    }

@Composable
fun ShareGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 8f, 8f, 12f, 4f, 16f, 8f), c, style = s)
        drawLine(c, Offset(12f * k, 4f * k), Offset(12f * k, 15f * k), s.width, s.cap)
        drawPath(path(k, 6f, 12f, 6f, 20f, 18f, 20f, 18f, 12f), c, style = s)
    }

@Composable
fun ImportGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawPath(path(k, 8f, 11f, 12f, 15f, 16f, 11f), c, style = s)
        drawLine(c, Offset(12f * k, 15f * k), Offset(12f * k, 4f * k), s.width, s.cap)
        drawPath(path(k, 6f, 12f, 6f, 20f, 18f, 20f, 18f, 12f), c, style = s)
    }

@Composable
fun InfoGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawCircle(c, 8f * k, Offset(12f * k, 12f * k), style = s)
        drawLine(c, Offset(12f * k, 11f * k), Offset(12f * k, 16.5f * k), s.width, s.cap)
        drawCircle(c, 0.9f * k, Offset(12f * k, 8f * k), style = Fill)
    }

@Composable
fun ResetGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawArcStroke(4f, 4f, 16f, 16f, 70f, 250f, k, s, c)
        drawPath(path(k, 5f, 6f, 5f, 11f, 10f, 11f), c, style = s)
    }

@Composable
fun BellGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        val bell = Path().apply {
            moveTo(6f * k, 17f * k)
            cubicTo(6f * k, 11f * k, 6f * k, 5f * k, 12f * k, 5f * k)
            cubicTo(18f * k, 5f * k, 18f * k, 11f * k, 18f * k, 17f * k)
            close()
        }
        drawPath(bell, c, style = s)
        drawLine(c, Offset(4.5f * k, 17f * k), Offset(19.5f * k, 17f * k), s.width, s.cap)
        drawArcStroke(10f, 17.5f, 4f, 4f, 0f, 180f, k, s, c)
    }

@Composable
fun SunGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawCircle(c, 4f * k, Offset(12f * k, 12f * k), style = s)
        listOf(12f to 2f, 12f to 22f, 2f to 12f, 22f to 12f, 5f to 5f, 19f to 19f, 5f to 19f, 19f to 5f).forEach { (x, y) ->
            val cx = 12f * k; val cy = 12f * k
            val dx = x * k - cx; val dy = y * k - cy
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            drawLine(c, Offset(cx + dx / len * 6.5f * k, cy + dy / len * 6.5f * k), Offset(x * k, y * k), s.width, s.cap)
        }
    }

@Composable
fun MoonGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        val moon = Path().apply {
            moveTo(19f * k, 14.5f * k)
            cubicTo(13f * k, 17f * k, 7f * k, 11f * k, 9.5f * k, 5f * k)
            cubicTo(5f * k, 6.5f * k, 3.5f * k, 12f * k, 6.5f * k, 16f * k)
            cubicTo(9.5f * k, 20f * k, 16f * k, 19.5f * k, 19f * k, 14.5f * k)
            close()
        }
        drawPath(moon, c, style = s)
    }

@Composable
fun RulerGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        drawRoundRectStroke(3f, 8f, 18f, 8f, k, 1.5f * k, s, c)
        listOf(7f, 11f, 15f, 19f).forEach { x ->
            drawLine(c, Offset(x * k, 8f * k), Offset(x * k, 12f * k), s.width, s.cap)
        }
    }

@Composable
fun WaterDropGlyph(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) =
    Glyph(modifier, tint) { k, s, c ->
        val drop = Path().apply {
            moveTo(12f * k, 3f * k)
            cubicTo(12f * k, 3f * k, 5f * k, 11f * k, 5f * k, 15f * k)
            cubicTo(5f * k, 19f * k, 8f * k, 21f * k, 12f * k, 21f * k)
            cubicTo(16f * k, 21f * k, 19f * k, 19f * k, 19f * k, 15f * k)
            cubicTo(19f * k, 11f * k, 12f * k, 3f * k, 12f * k, 3f * k)
            close()
        }
        drawPath(drop, c, style = s)
    }

// --- helpers ---

private fun DrawScope.drawRoundRectStroke(
    x: Float, y: Float, w: Float, h: Float, k: Float, radius: Float, stroke: Stroke, color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * k, y * k),
        size = Size(w * k, h * k),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = stroke,
    )
}

private fun DrawScope.drawArcStroke(
    x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float, k: Float, stroke: Stroke, color: Color,
) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(x * k, y * k),
        size = Size(w * k, h * k),
        style = stroke,
    )
}

@Suppress("unused")
private fun DrawScope.unusedRectRef() = Rect(Offset.Zero, Size.Zero)
