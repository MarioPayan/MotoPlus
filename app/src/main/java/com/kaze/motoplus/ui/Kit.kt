package com.kaze.motoplus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.domain.Part
import com.kaze.motoplus.domain.Status

/** Phosphor vector, tinted by the theme. */
@Composable
fun Icon(name: String, color: Color, size: Dp = 22.dp, stroke: Float = 1.5f, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        painter = androidx.compose.ui.res.painterResource(ICONS[name] ?: ICONS.getValue("wrench")),
        contentDescription = null, tint = color, modifier = modifier.size(size),
    )
}

/** Which icon draws each maintenance item. */
fun partIcon(p: Part): String = when (p.id) {
    "engine_oil", "shaft_oil", "gear_oil" -> "oil"
    "oil_filter" -> "oilfilter"
    "spark_plugs" -> "plug"
    "valve_clearance" -> "valve"
    "air_filter" -> "airfilter"
    "front_pads", "rear_pads" -> "pad"
    "brake_fluid" -> "fluid"
    "front_tire", "rear_tire" -> "tire"
    "tire_pressure" -> "gauge"
    "fork_oil" -> "fork"
    "shock_service" -> "shock"
    "battery_check" -> "battery"
    "lights_check" -> "light"
    "coolant" -> "coolant"
    "chain_lube", "drive_belt", "cvt_belt" -> "chain"
    "chain_tension", "belt_tension" -> "tension"
    "chain_kit", "cvt_rollers" -> "sprocket"
    else -> zoneIcon(p.zone)
}

fun zoneIcon(zone: String) = when (zone) {
    "engine" -> "oil"; "intake" -> "airfilter"; "front_brake", "rear_brake" -> "disc"; "front_wheel", "rear_wheel" -> "tire"
    "fork" -> "fork"; "rear_shock" -> "shock"; "battery" -> "battery"; "lights" -> "light"; "cooling" -> "coolant"; "drivetrain" -> "chain"
    else -> "wrench"
}

/**
 * Life ring: how much of the part's interval is used, with the part drawn inside and a status badge
 * whose shape (check, clock, !, +) tells the state without relying on colour.
 */
@Composable
fun LifeRing(icon: String, status: Status, used: Double, size: Dp = 46.dp) {
    val shop = LocalShop.current
    val c = shop.status(status)
    val description = statusLabel(status)
    val anim by animateFloatAsState(used.toFloat().coerceIn(0.02f, 1f), tween(700), label = "ring")
    Box(Modifier.size(size).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val stroke = w * .07f
            val inset = stroke / 2 + w * .06f
            val arcSize = Size(w - 2 * inset, w - 2 * inset)
            drawCircle(c.copy(alpha = if (shop.dark) .08f else .06f), arcSize.width / 2)
            drawCircle(shop.line, arcSize.width / 2, style = Stroke(stroke * .9f))
            if (status == Status.none) {
                drawCircle(shop.ink2, arcSize.width / 2, style = Stroke(stroke * .85f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .06f, w * .08f))))
            } else {
                if (shop.dark && status != Status.ok) drawArc(c.copy(alpha = .3f), -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(c, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Box(Modifier.align(Alignment.Center)) { Icon(icon, if (status == Status.none) shop.ink2 else shop.ink, size * .44f) }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(size * .37f).background(c, CircleShape).border(2.dp, shop.sheet, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(when (status) { Status.ok -> "check"; Status.warn -> "clock"; Status.bad -> "warning"; Status.none -> "plus" }, shop.sheet, size * .26f)
        }
    }
}

/** A drawing panel: flat sheet with a hairline border. */
@Composable
fun Sheet(modifier: Modifier = Modifier, padding: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    val shop = LocalShop.current
    Column(
        modifier.fillMaxWidth().padding(vertical = 2.dp).drawBehind { drawLine(shop.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(vertical = padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, size: Int = 23) = Text(text.uppercase(), style = Display.copy(fontSize = size.sp), color = LocalShop.current.ink)

@Composable
fun Cap(text: String, color: Color = LocalShop.current.ink2, modifier: Modifier = Modifier) = Text(text.uppercase(), style = Caption, color = color, modifier = modifier)

@Composable
fun Hint(text: String, color: Color = LocalShop.current.ink2, modifier: Modifier = Modifier) = Text(text, fontSize = 16.sp, color = color, modifier = modifier)

/** Condensed capital label, filled or outlined. */
@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, ghost: Boolean = false, enabled: Boolean = true, icon: String? = null) {
    val shop = LocalShop.current
    val shape = RoundedCornerShape(3.dp)
    val content: @Composable RowScope.() -> Unit = {
        icon?.let { Icon(it, if (ghost) shop.ink else shop.onAccent, 16.dp, 1.8f); Spacer(Modifier.width(6.dp)) }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    if (ghost) OutlinedButton(onClick, modifier, enabled, shape = shape, border = androidx.compose.foundation.BorderStroke(1.dp, shop.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = shop.ink), content = content)
    else Button(onClick, modifier, enabled, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = shop.accent, contentColor = shop.onAccent), content = content)
}

/** A 44 dp touch target around an icon. */
@Composable
fun IconBtn(icon: String, label: String, onClick: () -> Unit, color: Color = LocalShop.current.ink2, size: Dp = 22.dp) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, color, size)
    }
}

/** Back arrow, title and optional actions on the right. */
@Composable
fun Header(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn("back", stringResource(R.string.back), onBack, LocalShop.current.ink)
        Box(Modifier.weight(1f)) { SectionTitle(title, 30) }
        actions()
    }
}

@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (k, label) ->
            FilterChip(selected = k == selected, onClick = { onPick(k) }, label = { Text(label, style = MaterialTheme.typography.bodyMedium) }, shape = RoundedCornerShape(3.dp))
        }
    }
}

@Composable
fun Pill(text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(text, fontSize = 12.sp)
}
