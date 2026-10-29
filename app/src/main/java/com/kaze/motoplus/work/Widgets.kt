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
import com.kaze.motoplus.MainActivity
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.*
import com.kaze.motoplus.ui.ICONS
import com.kaze.motoplus.ui.Shop
import com.kaze.motoplus.ui.partIcon
import com.kaze.motoplus.ui.shopFor
import com.kaze.motoplus.ui.shortLeft
import com.kaze.motoplus.ui.zoneShort
import java.time.LocalDate

private fun dark(ctx: Context) = when (ctx.getSharedPreferences("display", Context.MODE_PRIVATE).getString("theme", "dark")) {
    "light" -> false
    "system" -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    else -> true
}
private fun Shop.argb(s: Status) = status(s).toArgb()

/** Most urgent part of the selected bike, for the small widget. */
private fun urgent(bike: Bike, today: LocalDate) = bike.parts.map { it to partState(it, estimate(bike, today).km, today) }.maxByOrNull { it.second.used }

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
            if (bike == null) { Text("Añade tu moto", style = TextStyle(color = ColorProvider(shop.ink))); return@Column }
            val (p, s) = urgent(bike, today) ?: return@Column
            Text(bike.model.uppercase(), style = TextStyle(color = ColorProvider(shop.ink2), fontSize = 10.sp, fontFamily = FontFamily.Monospace))
            Spacer(GlanceModifier.height(6.dp))
            Image(ImageProvider(ringBitmap(ctx, shop, partIcon(p), s.status, s.used, 180)), p.name, GlanceModifier.size(64.dp))
            Spacer(GlanceModifier.height(6.dp))
            Text(p.name, style = TextStyle(color = ColorProvider(shop.ink), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(shortLeft(s).uppercase(), style = TextStyle(color = ColorProvider(shop.status(s.status)), fontSize = 10.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
        }
    }
}

/** 4×2: the plate with its status colours, the odometer and two shortcuts. */
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
            if (bike == null) { Text("Añade tu moto", style = TextStyle(color = ColorProvider(shop.ink))); return@Column }
            val odo = estimate(bike, today)
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                Text("${bike.model.uppercase()} · SALUD ${health(bike, odo.km, today)}", style = TextStyle(color = ColorProvider(shop.ink2), fontSize = 10.sp, fontFamily = FontFamily.Monospace), modifier = GlanceModifier.defaultWeight())
                Text("${fmtKm(odo.km.toInt())} KM", style = TextStyle(color = ColorProvider(shop.ink), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            }
            Image(
                ImageProvider(plateBitmap(ctx, bike, shop, today, 720)), "La moto con el estado de cada zona",
                GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity<MainActivity>()), contentScale = androidx.glance.layout.ContentScale.Fit,
            )
            Row(GlanceModifier.fillMaxWidth()) {
                val chain = bike.parts.any { it.id == "chain_lube" }
                Box(GlanceModifier.defaultWeight().height(34.dp).background(shop.accent).cornerRadius(6.dp)
                    .clickable(if (chain) actionRunCallback<LubeAction>() else actionStartActivity<MainActivity>()), contentAlignment = Alignment.Center) {
                    Text(if (chain) "ENGRASÉ" else "ABRIR", style = TextStyle(color = ColorProvider(shop.onAccent), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.defaultWeight().height(34.dp).background(shop.sheet2).cornerRadius(6.dp).clickable(actionRunCallback<RideAction>()), contentAlignment = Alignment.Center) {
                    Text("SALGO", style = TextStyle(color = ColorProvider(shop.ink), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

class LubeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        val b = Repo.garage.value.selected ?: return
        val today = LocalDate.now()
        Repo.updateBike(b.id) { it.markDone("chain_lube", estimate(it, today).km.toInt(), today) }
        ReminderWorker.runNow(context)
    }
}

class RideAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        if (Tracker.hasLocation(context)) Tracker.start(context, RideTrigger.manual)
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

private fun plateBitmap(ctx: Context, bike: Bike, shop: Shop, today: LocalDate, width: Int): Bitmap {
    val name = silhouette(bike.category)
    val d = drawings.getOrPut(name) { runCatching { Drawing.load(ctx, "bike_$name.json") }.getOrElse { Drawing.load(ctx) } }
    val (vx, vy, vw, vh) = d.viewBox
    val s = width / vw
    val bmp = Bitmap.createBitmap(width, (vh * s).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(s, s); c.translate(-vx, -vy)
    val zones = zoneStates(bike, estimate(bike, today).km, today)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val grid = Paint().apply { color = shop.plate.copy(alpha = .1f).toArgb(); strokeWidth = 1f }
    var x = vx; while (x < vx + vw) { c.drawLine(x, vy, x, vy + vh, grid); x += 20f }
    var y = vy; while (y < vy + vh) { c.drawLine(vx, y, vx + vw, y, grid); y += 20f }
    for (i in d.items) {
        val st = i.z?.let { zones[it]?.status }
        val line = if (st == null || st == Status.ok) shop.plate.copy(alpha = .75f) else shop.status(st)
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
