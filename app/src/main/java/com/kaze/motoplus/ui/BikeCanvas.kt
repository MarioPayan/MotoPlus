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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.domain.Status
import com.kaze.motoplus.domain.ZoneState

internal class Prim(val zone: String?, val kind: Set<String>, val path: Path, val width: Float?, val dash: List<Float>?)

/** A label on a leader line, pointing at a zone: "CADENA 40 KM". */
data class Callout(val zone: String, val text: String, val status: Status)

/** A numbered point on the drawing, for the pre-ride check. A non-null [status] means checked and fills the point. */
data class Pin(val zone: String, val label: String, val status: Status?, val active: Boolean)

/** Parsed drawing plus each zone's hit region and bounds, in drawing coordinates. */
class Plate(drawing: Drawing) {
    val viewBox = drawing.viewBox
    internal val prims = drawing.items.map { i ->
        Prim(i.z, i.k.split(' ').toSet(), PathParser().parsePathString(i.d).toPath().apply { if (i.eo) fillType = PathFillType.EvenOdd }, i.w, i.dash)
    }
    internal val regions: Map<String, Region> = run {
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
    val bounds: Map<String, Rect> = regions.mapValues { (_, r) -> r.bounds.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) } }

    /**
     * Where a pin for [zone] goes: the centre of its biggest piece (the headlight, not halfway to the tail light),
     * or the top of the tyre for wheels so it does not sit on the brake disc.
     */
    fun anchor(zone: String): Offset? {
        val mine = prims.filter { it.zone == zone }
        // Filled shapes are the part itself; long strokes (hoses, cables) would drag the pin away.
        val big = (mine.filter { "sf" in it.kind }.ifEmpty { mine }).map { it.path.getBounds() }.maxByOrNull { it.width * it.height } ?: return null
        return if (zone == "front_wheel" || zone == "rear_wheel") Offset(big.center.x, big.top + big.height * .1f) else big.center
    }

    /** A square window around [zones], for the zoomed "detail" view. */
    fun detail(zones: Collection<String>, pad: Float = .08f): Rect {
        val b = zones.mapNotNull { bounds[it] }.reduce { a, c -> Rect(minOf(a.left, c.left), minOf(a.top, c.top), maxOf(a.right, c.right), maxOf(a.bottom, c.bottom)) }
        val ratio = .7f
        val w = maxOf(b.width, b.height / ratio) * (1 + 2 * pad)
        return Rect(Offset(b.center.x - w / 2, b.center.y - w * ratio / 2), androidx.compose.ui.geometry.Size(w, w * ratio))
    }
}

@Composable
fun rememberPlate(drawing: Drawing) = remember(drawing) { Plate(drawing) }

/**
 * The service-manual plate on a blueprint grid: every zone drawn in its status colour; tap a zone to select it.
 * [window] zooms into part of the drawing; [focus] fades every other zone; [callouts] annotate trouble zones in the margins.
 */
@Composable
fun BikeCanvas(
    plate: Plate,
    zones: Map<String, ZoneState>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    highlighted: Set<String> = emptySet(),
    focus: String? = null,
    window: Rect? = null,
    callouts: List<Callout> = emptyList(),
    pins: List<Pin> = emptyList(),
    flash: Pair<String, Int>? = null,
    grid: Boolean = true,
    onZone: ((String) -> Unit)? = null,
) {
    val shop = LocalShop.current
    val (vx0, vy0, vw0, vh0) = plate.viewBox
    // Callouts need a margin above and below the drawing for their labels.
    val margin = if (callouts.isNotEmpty()) 70f else 0f
    val view = window ?: Rect(vx0, vy0 - margin, vx0 + vw0, vy0 + vh0 + margin)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val flashA = remember { Animatable(0f) }
    LaunchedEffect(flash) { if (flash != null) { flashA.snapTo(1f); flashA.animateTo(0f, tween(1100, easing = LinearOutSlowInEasing)) } }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Caption.fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.sp)
    val tap = Modifier.pointerInput(plate, view, onZone) {
        if (onZone == null) return@pointerInput
        detectTapGestures { o ->
            val s = size.width / view.width
            val x = (o.x / s + view.left).toInt(); val y = (o.y / s + view.top).toInt()
            plate.regions.filter { it.value.contains(x, y) }.minByOrNull { it.value.bounds.width() * it.value.bounds.height() }?.let { onZone(it.key) }
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(view.width / view.height).clipToBounds().then(tap)) {
        val s = size.width / view.width
        translate(-view.left * s, -view.top * s) {
            scale(s, s, pivot = Offset.Zero) {
                if (grid) drawGrid(view, shop.plate, shop.dark, s)
                for (p in plate.prims) {
                    val st = p.zone?.let { zones[it]?.status }
                    val faded = focus != null && p.zone != focus
                    val zc = if (st == null || faded) shop.plate else shop.status(st)
                    val tint = if (p.zone == null || faded) shop.sheet else lerp(shop.sheet, zc, shop.tint)
                    val alpha = when { faded -> .4f; p.zone == null -> if (shop.dark) .7f else .8f; else -> 1f }
                    // Trouble zones glow a little on the dark drawing, like warning lights.
                    if (shop.dark && !faded && (st == Status.warn || st == Status.bad) && "pi" !in p.kind)
                        drawPath(p.path, zc.copy(alpha = .22f), style = Stroke((p.width ?: 1.3f) + 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPrim(p, zc.copy(alpha = alpha), tint, if (p.zone != null && p.zone == selected) 1.7f else 1f, dashed = st == Status.none && !faded)
                }
                for (p in plate.prims) {
                    val z = p.zone ?: continue
                    if ("pi" in p.kind) continue
                    val st = zones[z]?.status
                    if (z == flash?.first && flashA.value > 0f)
                        drawPath(p.path, shop.ok.copy(alpha = .7f * flashA.value), style = Stroke((p.width ?: 1.3f) + 10f * flashA.value, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (focus != null && z != focus) continue
                    val hot = z in highlighted || st == Status.bad
                    if (!hot) continue
                    val c = if (z in highlighted) shop.accent else shop.bad
                    drawPath(p.path, c.copy(alpha = .08f + .22f * pulse), style = Stroke((p.width ?: 1.3f) + 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        // Annotations are drawn in screen space so text keeps its size whatever the zoom.
        fun toScreen(x: Float, y: Float) = Offset((x - view.left) * s, (y - view.top) * s)
        drawCallouts(plate, callouts, view, ::toScreen, measurer, labelStyle, shop)
        for (pin in pins) {
            val a = plate.anchor(pin.zone) ?: continue
            val o = toScreen(a.x, a.y)
            val c = pin.status?.let { shop.status(it) } ?: shop.accent
            val r = 11.sp.toPx()
            if (pin.active) drawCircle(c.copy(alpha = .25f + .25f * pulse), r * 1.7f, o)
            drawCircle(if (pin.status != null) c else shop.paper, r, o)
            drawCircle(c, r, o, style = Stroke(2.sp.toPx()))
            val t = measurer.measure(pin.label, labelStyle.copy(fontSize = 11.sp, color = if (pin.status != null) shop.paper else c))
            drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
        }
    }
}

private fun DrawScope.drawGrid(view: Rect, c: Color, dark: Boolean, s: Float) {
    val fine = c.copy(alpha = if (dark) .10f else .08f)
    val bold = c.copy(alpha = if (dark) .18f else .14f)
    var x = (Math.floor(view.left / 20.0) * 20).toFloat()
    while (x <= view.right) { drawLine(if (x % 100f == 0f) bold else fine, Offset(x, view.top), Offset(x, view.bottom), (if (x % 100f == 0f) 1f else .7f) / s * 1.6f); x += 20f }
    var y = (Math.floor(view.top / 20.0) * 20).toFloat()
    while (y <= view.bottom) { drawLine(if (y % 100f == 0f) bold else fine, Offset(view.left, y), Offset(view.right, y), (if (y % 100f == 0f) 1f else .7f) / s * 1.6f); y += 20f }
}

/**
 * Leader lines from each zone to a label in the top or bottom margin, whichever is closer.
 * Labels in a margin are pushed apart left to right so they never overlap.
 */
private fun DrawScope.drawCallouts(
    plate: Plate, callouts: List<Callout>, view: Rect, toScreen: (Float, Float) -> Offset,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, shop: Shop,
) {
    if (callouts.isEmpty()) return
    val mid = (plate.viewBox[1] + plate.viewBox[3] / 2)
    val gap = 6.sp.toPx()
    for (top in listOf(true, false)) {
        val row = callouts.mapNotNull { c -> plate.bounds[c.zone]?.let { c to it } }
            .filter { (_, b) -> (b.center.y < mid) == top }
            .take(3) // callers pass the most urgent first
            .map { (c, b) -> Triple(c, toScreen(b.center.x, b.center.y), measurer.measure(c.text, style.copy(color = shop.status(c.status)))) }
            .sortedBy { it.second.x }
        val y = if (top) toScreen(0f, view.top + 34f).y else toScreen(0f, view.bottom - 34f).y
        var cursor = 0f
        for ((c, anchor, text) in row) {
            val w = text.size.width.toFloat()
            // Prefer the label centred over its zone, clamped to the canvas and after the previous label.
            // ponytail: a label that no longer fits runs off the right edge; split rows if bikes ever need 4+ callouts per margin
            val left = maxOf(maxOf(cursor, 4f), minOf(anchor.x - w / 2, size.width - w - 4f))
            cursor = left + w + gap
            val col = shop.status(c.status)
            val elbowX = anchor.x.coerceIn(left, left + w)
            val textY = if (top) y - text.size.height else y + 2f
            drawCircle(shop.paper, 3.5.sp.toPx(), anchor)
            drawCircle(col, 3.5.sp.toPx(), anchor, style = Stroke(1.6.sp.toPx()))
            drawLine(col, anchor, Offset(elbowX, y), 1.2.sp.toPx())
            drawLine(col, Offset(left, y), Offset(left + w, y), 1.2.sp.toPx())
            drawText(text, topLeft = Offset(left, textY))
        }
    }
}

private fun DrawScope.drawPrim(p: Prim, line: Color, tint: Color, boost: Float, dashed: Boolean) {
    val k = p.kind
    val base = when {
        "spring" in k -> 2.6f
        "t" in k -> 0.75f
        else -> 1.3f
    } * boost
    val effect = when {
        p.dash != null -> PathEffect.dashPathEffect(p.dash.toFloatArray())
        "h" in k || dashed -> PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
        else -> null
    }
    when {
        "sf" in k -> {
            drawPath(p.path, tint, style = Fill)
            drawPath(p.path, line, style = Stroke(base, join = StrokeJoin.Round, pathEffect = effect))
        }
        "po" in k -> drawPath(p.path, line, style = Stroke(p.width ?: 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        "pi" in k -> drawPath(p.path, tint, style = Stroke(p.width ?: 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        "rollers" in k -> drawPath(p.path, line, style = Stroke(3.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 7.1f))))
        else -> drawPath(p.path, line, style = Stroke(p.width ?: base, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
}
