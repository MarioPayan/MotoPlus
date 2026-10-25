package com.kaze.motoplus.ui

import android.graphics.Region
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.domain.Status
import com.kaze.motoplus.domain.ZoneState

private class Prim(val zone: String?, val kind: Set<String>, val path: Path, val width: Float?, val dash: List<Float>?)

/** The service-manual plate: every zone drawn in its status color; tap a zone to select it. */
@Composable
fun BikeCanvas(
    drawing: Drawing,
    zones: Map<String, ZoneState>,
    selected: String?,
    highlighted: Set<String>,
    onZone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shop = LocalShop.current
    val (vx, vy, vw, vh) = drawing.viewBox
    val prims = remember(drawing) {
        drawing.items.map { i ->
            Prim(i.z, i.k.split(' ').toSet(), PathParser().parsePathString(i.d).toPath().apply { if (i.eo) fillType = PathFillType.EvenOdd }, i.w, i.dash)
        }
    }
    // Hit regions in drawing coordinates: filled shapes plus a generous outline around strokes.
    val regions = remember(prims) {
        val clip = Region(-10000, -10000, 10000, 10000)
        prims.filter { it.zone != null }.groupBy { it.zone!! }.mapValues { (_, ps) ->
            val r = Region()
            for (p in ps) {
                val src = p.path.asAndroidPath()
                val shape = if ("sf" in p.kind) src else android.graphics.Path().also {
                    android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = maxOf(p.width ?: 0f, 14f) }.getFillPath(src, it)
                }
                r.op(Region().apply { setPath(shape, clip) }, Region.Op.UNION)
            }
            r
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Canvas(
        modifier.fillMaxWidth().aspectRatio(vw / vh).clipToBounds().pointerInput(regions) {
            detectTapGestures { o ->
                val s = size.width / vw
                val x = (o.x / s + vx).toInt(); val y = (o.y / s + vy).toInt()
                regions.filter { it.value.contains(x, y) }
                    .minByOrNull { it.value.bounds.width() * it.value.bounds.height() }
                    ?.let { onZone(it.key) }
            }
        }
    ) {
        val s = size.width / vw
        translate(-vx * s, -vy * s) {
            scale(s, s, pivot = Offset.Zero) {
                for (p in prims) {
                    val st = p.zone?.let { zones[it]?.status }
                    val zc = when (st) { null -> shop.ink; Status.none -> shop.bad; else -> shop.status(st) }
                    val tint = if (p.zone == null) shop.sheet else lerp(shop.sheet, zc, 0.13f)
                    val bold = p.zone != null && p.zone == selected
                    drawPrim(p, zc, tint, if (bold) 1.7f else 1f)
                }
                // Overdue and AI-flagged zones breathe.
                for (p in prims) {
                    val z = p.zone ?: continue
                    val st = zones[z]?.status
                    val hot = z in highlighted || st == Status.bad || st == Status.none
                    if (!hot || "pi" in p.kind) continue
                    val c = if (z in highlighted) shop.accent else shop.bad
                    drawPath(p.path, c.copy(alpha = 0.10f + 0.25f * pulse), style = Stroke(width = (p.width ?: 1.3f) + 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

private fun DrawScope.drawPrim(p: Prim, line: Color, tint: Color, boost: Float) {
    val k = p.kind
    val thin = "t" in k
    val base = when {
        "spring" in k -> 2.6f
        thin -> 0.75f
        else -> 1.3f
    } * boost
    val effect = when {
        p.dash != null -> PathEffect.dashPathEffect(p.dash.toFloatArray())
        "h" in k -> PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
        else -> null
    }
    when {
        "sf" in k -> {
            drawPath(p.path, tint, style = Fill)
            drawPath(p.path, line, style = Stroke(base, join = StrokeJoin.Round, pathEffect = effect))
        }
        "po" in k -> drawPath(p.path, line, style = Stroke(p.width ?: 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        "pi" in k -> drawPath(p.path, tint, style = Stroke(p.width ?: 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        "rollers" in k -> drawPath(p.path, line, style = Stroke(3.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 7.1f))))
        else -> drawPath(p.path, line, style = Stroke(p.width ?: base, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
}
