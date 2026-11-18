package com.kaze.motoplus.work

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kaze.motoplus.R
import com.kaze.motoplus.ui.partLabel
import com.kaze.motoplus.MainActivity
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.data.Prefs
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.*
import com.kaze.motoplus.ui.ICONS
import com.kaze.motoplus.ui.Shop
import com.kaze.motoplus.ui.partIcon
import com.kaze.motoplus.ui.shopFor
import com.kaze.motoplus.ui.remaining
import java.time.LocalDate

private fun dark(ctx: Context) = when (ctx.getSharedPreferences("display", Context.MODE_PRIVATE).getString("theme", "dark")) {
    "light" -> false
    "system" -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    else -> true
}
private fun Shop.argb(s: Status) = status(s).toArgb()

/** Most urgent watched part of the selected bike, for the small widget. */
private fun urgent(bike: Bike, today: LocalDate, soon: SoonThreshold) = bike.watchedParts.map { it to partState(it, estimate(bike, today).km, today, soon) }.maxByOrNull { it.second.used }

/** 2×2: the most urgent part in its life ring. */
class SmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        provideContent { Small(context) }
    }

    @Composable
    private fun Small(ctx: Context) {
        val bike = Repo.garage.collectAsState().value.selected
        val today = LocalDate.now()
        val shop = shopFor(dark(ctx))
        Column(
            GlanceModifier.fillMaxSize().background(shop.paper).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bike == null) { Text(ctx.getString(R.string.add_bike_title), style = TextStyle(color = ColorProvider(shop.ink))); return@Column }
            val (p, s) = urgent(bike, today, Prefs.load(ctx).soon) ?: return@Column
            Text(bike.model.uppercase(), style = TextStyle(color = ColorProvider(shop.ink2), fontSize = 10.sp, fontFamily = FontFamily.Monospace))
            Spacer(GlanceModifier.height(6.dp))
            Image(ImageProvider(ringBitmap(ctx, shop, partIcon(p), s.status, s.used, 180)), partLabel(p, ctx), GlanceModifier.size(64.dp))
            Spacer(GlanceModifier.height(6.dp))
            Text(partLabel(p, ctx), style = TextStyle(color = ColorProvider(shop.ink), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(remaining(s, ctx).uppercase(), style = TextStyle(color = ColorProvider(shop.status(s.status)), fontSize = 10.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
        }
    }
}

/**
 * 4×2, the wide one: health and odometer on top; the plate on the left and, beside it, the parts to look at soon
 * (overdue first) above Salgo. Only status: logging happens in the app.
 */
class LargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        provideContent { Large(context) }
    }

    @Composable
    private fun Large(ctx: Context) {
        val bike = Repo.garage.collectAsState().value.selected
        val today = LocalDate.now()
        val shop = shopFor(dark(ctx))
        Column(GlanceModifier.fillMaxSize().background(shop.paper).cornerRadius(24.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (bike == null) { Text(ctx.getString(R.string.add_bike_title), style = TextStyle(color = ColorProvider(shop.ink))); return@Column }
            val odo = estimate(bike, today)
            val soon = Prefs.load(ctx).soon
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.getString(R.string.widget_health, bike.model.uppercase(), health(bike, odo.km, today, soon)), style = TextStyle(color = ColorProvider(shop.ink2), fontSize = 10.sp, fontFamily = FontFamily.Monospace), modifier = GlanceModifier.defaultWeight())
                Text(ctx.getString(R.string.p2_km_value, formatKm(odo.km.toInt(), ctx.resources.configuration.locales[0])), style = TextStyle(color = ColorProvider(shop.ink), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(plateBitmap(ctx, bike, shop, today, soon, 720)), ctx.getString(R.string.widget_bike_description),
                    GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity<MainActivity>()), contentScale = androidx.glance.layout.ContentScale.Fit,
                )
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.width(DUE_COLUMN).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity<MainActivity>()), contentAlignment = Alignment.Center) {
                        DueParts(ctx, shop, bike.dueSoon(odo.km, today, soon))
                    }
                    RideButton(ctx, shop)
                }
            }
        }
    }
}

private val DUE_COLUMN = 84.dp
private const val DUE_SHOWN = 4

/** Up to four life rings, two per row, then "+N"; or Todo al día. */
@Composable
private fun DueParts(ctx: Context, shop: Shop, due: List<Pair<Part, PartState>>) {
    if (due.isEmpty()) {
        Text(ctx.getString(R.string.widget_all_good), style = TextStyle(color = ColorProvider(shop.ok), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        due.take(DUE_SHOWN).chunked(2).forEach { row ->
            Row {
                row.forEach { (p, s) ->
                    Image(ImageProvider(ringBitmap(ctx, shop, partIcon(p), s.status, s.used, 108)), partLabel(p, ctx) + ": " + remaining(s, ctx), GlanceModifier.size(36.dp))
                }
            }
        }
        if (due.size > DUE_SHOWN) Text(ctx.getString(R.string.widget_more, due.size - DUE_SHOWN), style = TextStyle(color = ColorProvider(shop.ink2), fontSize = 10.sp, fontFamily = FontFamily.Monospace))
    }
}

/** Salgo: starts a manual ride. Icon only; the label stays for TalkBack. */
@Composable
private fun RideButton(ctx: Context, shop: Shop) {
    Box(GlanceModifier.size(40.dp).background(shop.accent).cornerRadius(20.dp).clickable(actionRunCallback<RideAction>()), contentAlignment = Alignment.Center) {
        Image(ImageProvider(iconBitmap(ctx, "route", shop.onAccent.toArgb(), 66)), ctx.getString(R.string.widget_start_ride_description), GlanceModifier.size(22.dp))
    }
}

class RideAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        if (Tracker.canStart(context)) Tracker.start(context, RideTrigger.manual)
        else context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SmallWidget() }
class LargeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LargeWidget() }

/** Redraws both widgets; called whenever the garage changes. */
suspend fun updateWidgets(ctx: Context) {
    SmallWidget().updateAll(ctx)
    LargeWidget().updateAll(ctx)
}

// ---------- Bitmaps: widgets have no canvas of their own ----------

private val drawings = mutableMapOf<String, Drawing>()

private fun plateBitmap(ctx: Context, bike: Bike, shop: Shop, today: LocalDate, soon: SoonThreshold, width: Int): Bitmap {
    val name = silhouette(bike.category)
    val d = drawings.getOrPut(name) { runCatching { Drawing.load(ctx, "bike_$name.json") }.getOrElse { Drawing.load(ctx) } }
    val (vx, vy, vw, vh) = d.viewBox
    val s = width / vw
    val bmp = Bitmap.createBitmap(width, (vh * s).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(s, s); c.translate(-vx, -vy)
    val zones = zoneStates(bike, estimate(bike, today).km, today, soon)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val grid = Paint().apply { color = shop.plate.copy(alpha = .1f).toArgb(); strokeWidth = 1f }
    var x = vx; while (x < vx + vw) { c.drawLine(x, vy, x, vy + vh, grid); x += 20f }
    var y = vy; while (y < vy + vh) { c.drawLine(vx, y, vx + vw, y, grid); y += 20f }
    for (i in d.items) {
        // The whole-bike widget shows every zone's state, ok included; a zone with no watched part stays neutral.
        val st = i.z?.let { z -> zones[z]?.takeIf { it.parts.isNotEmpty() }?.status }
        val line = if (st == null) shop.plate.copy(alpha = .75f) else shop.status(st)
        val path = PathParser().parsePathString(i.d).toPath().asAndroidPath().apply { if (i.eo) fillType = android.graphics.Path.FillType.EVEN_ODD }
        val k = i.k.split(' ')
        stroke.color = line.toArgb()
        // Lines go thicker than in the app: the bitmap is scaled down on a small widget.
        stroke.strokeWidth = (i.w ?: if ("t" in k) .75f else if ("spring" in k) 2.6f else 1.3f) * 1.8f
        stroke.pathEffect = if ("h" in k || st == Status.none) DashPathEffect(floatArrayOf(5f, 4f), 0f) else null
        when {
            "sf" in k -> { fill.color = (if (i.z == null) shop.sheet else lerp(shop.sheet, line, shop.tint)).toArgb(); c.drawPath(path, fill); c.drawPath(path, stroke) }
            "pi" in k -> { stroke.color = (if (i.z == null) shop.sheet else lerp(shop.sheet, line, shop.tint)).toArgb(); stroke.strokeWidth = i.w ?: 6f; c.drawPath(path, stroke) }
            else -> c.drawPath(path, stroke)
        }
    }
    return bmp
}

/** A Phosphor icon in one colour, drawn here like the rings so it looks the same on every launcher. */
private fun iconBitmap(ctx: Context, icon: String, color: Int, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    ICONS[icon]?.let { id ->
        androidx.core.content.ContextCompat.getDrawable(ctx, id)?.mutate()?.let { d -> d.setTint(color); d.setBounds(0, 0, size, size); d.draw(Canvas(bmp)) }
    }
    return bmp
}

private fun ringBitmap(ctx: Context, shop: Shop, icon: String, status: Status, used: Double, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val col = shop.argb(status)
    val w = size.toFloat(); val sw = w * .07f; val inset = sw / 2 + w * .06f
    val arc = RectF(inset, inset, w - inset, w - inset)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
    p.color = shop.line.toArgb(); c.drawOval(arc, p)
    if (status == Status.none) { p.color = shop.ink2.toArgb(); p.pathEffect = DashPathEffect(floatArrayOf(w * .06f, w * .08f), 0f); c.drawOval(arc, p); p.pathEffect = null }
    else { p.color = col; c.drawArc(arc, -90f, 360f * used.toFloat().coerceIn(.02f, 1f), false, p) }
    ICONS[icon]?.let { id ->
        androidx.core.content.ContextCompat.getDrawable(ctx, id)?.mutate()?.let { drawable ->
            drawable.setTint((if (status == Status.none) shop.ink2 else shop.ink).toArgb())
            drawable.setBounds((w * .28f).toInt(), (w * .28f).toInt(), (w * .72f).toInt(), (w * .72f).toInt())
            drawable.draw(c)
        }
    }
    return bmp
}
