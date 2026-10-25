package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*

// ---------- 1A: the bike on its blueprint, with callouts and what's next ----------

@Composable
fun BikeScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val today = vm.today
    val odo = estimate(bike, today).km
    val zones = zoneStates(bike, odo, today)
    val plate = rememberPlate(vm.drawingFor(bike))
    val states = bike.parts.map { it to partState(it, odo, today) }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Sheet(padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                Cap("Fig. 1 · Vista lateral", modifier = Modifier.weight(1f))
                Cap("Toca una zona")
            }
            BikeCanvas(
                plate, zones, Modifier.padding(horizontal = 4.dp),
                highlighted = vm.highlighted, callouts = callouts(bike, zones), flash = vm.flash,
                onZone = { vm.openZone(it) },
            )
            HealthBar(health(bike, odo, today), states.map { it.second.status }) { info = true }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cap("Lo próximo", modifier = Modifier.weight(1f).padding(start = 6.dp))
                TextButton({ vm.screen = Screen.parts }) { Text("VER LAS ${bike.parts.size} →", style = Mono.copy(fontSize = 12.sp), color = shop.accent) }
            }
            states.sortedByDescending { it.second.used }.take(3).forEach { (p, s) -> PartRow(vm, p, s) }
            HorizontalDivider(color = shop.line)
            val open = bike.openOrder()
            Btn(
                if (open != null) "Orden nº ${open.number} abierta · volví del taller" else "Lista para el taller",
                { vm.screen = if (open != null) Screen.orderReturn else Screen.order },
                Modifier.fillMaxWidth().padding(top = 10.dp), ghost = true, icon = "wrench",
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    if (info) LegendDialog { info = false }
}

/** Callouts for zones that need attention, most urgent first (overdue, coming due, never recorded), labelled with their worst part. */
fun callouts(bike: Bike, zones: Map<String, ZoneState>): List<Callout> = zones.entries
    .sortedWith(compareBy<Map.Entry<String, ZoneState>> { listOf(Status.bad, Status.warn, Status.none, Status.ok).indexOf(it.value.status) }.thenByDescending { it.value.worst })
    .mapNotNull { (z, st) ->
    if (st.status == Status.ok) return@mapNotNull null
    val (_, s) = st.parts.firstOrNull { it.second.status == st.status } ?: return@mapNotNull null
    val value = when {
        s.measure == null -> "?"
        s.measure == Measure.km -> (if (s.left < 0) "−" else "") + fmtKm(kotlin.math.abs(s.left)) + " KM"
        else -> (if (s.left < 0) "−" else "") + kotlin.math.abs(s.left) + " D"
    }
    Callout(z, "${zoneShort(z, bike.drive)} $value", st.status)
}

@Composable
private fun HealthBar(score: Int, statuses: List<Status>, onInfo: () -> Unit) {
    val shop = LocalShop.current
    val n = statuses.size.coerceAtLeast(1)
    val cells = 20
    val bad = Math.round(statuses.count { it == Status.bad } * cells / n.toFloat())
    val warn = Math.round(statuses.count { it == Status.warn } * cells / n.toFloat())
    val none = Math.round(statuses.count { it == Status.none } * cells / n.toFloat())
    val alerts = statuses.count { it != Status.ok }
    HorizontalDivider(color = shop.line)
    Row(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$score", style = Mono.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
        Text("/100", style = Mono.copy(fontSize = 12.sp), color = shop.ink2, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(cells) { i ->
                    val c = when { i < bad -> shop.bad; i < bad + warn -> shop.warn; i < bad + warn + none -> shop.ink2.copy(alpha = .45f); else -> shop.ok }
                    Box(Modifier.weight(1f).height(8.dp).background(c))
                }
            }
            Spacer(Modifier.height(5.dp))
            Cap(if (alerts == 0) "Salud · todo al día" else "Salud · $alerts ${if (alerts == 1) "aviso" else "avisos"}")
        }
        IconBtn("info", "Cómo leer la moto", onInfo, size = 18.dp)
    }
}

@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    val shop = LocalShop.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("ENTENDIDO") } },
        title = { Text("CÓMO LEER LA MOTO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(Status.ok, "Al día: le queda más de una cuarta parte del plazo.")
                Legend(Status.warn, "Pronto: le queda menos de una cuarta parte. Ve planeándolo.")
                Legend(Status.bad, "Vencido: pasó el plazo. Las zonas vencidas laten.")
                Legend(Status.none, "Sin registrar: aún no sabemos cuándo se hizo. Toca la pieza y añade el último cambio.")
                Text("El anillo muestra cuánto del plazo has gastado. Cada zona toma el color de su pieza más urgente, y las cotas del dibujo señalan lo que pide atención. Cada plazo cuenta por kilómetros o por tiempo, lo que llegue antes.", fontSize = 13.sp, color = shop.ink2)
            }
        },
    )
}

@Composable
private fun Legend(s: Status, text: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    LifeRing("wrench", s, when (s) { Status.ok -> .3; Status.warn -> .85; else -> 1.0 }, 38.dp)
    Text(text, fontSize = 13.sp)
}

/** Ring, name and time left; a quick "done" check and the ⋮ menu with the rarer actions. */
@Composable
fun PartRow(vm: AppViewModel, p: Part, s: PartState, showInterval: Boolean = false) {
    val shop = LocalShop.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Column {
        HorizontalDivider(color = shop.line)
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Ver historial") { vm.openPart(p.id) }.padding(start = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LifeRing(partIcon(p), s.status, s.used)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    shortLeft(s) + if (showInterval) " · ${intervalText(p)}" else "",
                    style = Mono.copy(fontSize = 12.sp), color = if (s.status == Status.none) shop.ink2 else shop.status(s.status),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconBtn("check", "Hecho hoy", {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                vm.markDone(p.id)
            }, shop.ink2, 20.dp)
            Box {
                IconBtn("more", "Más acciones", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = shop.sheet2) {
                    MenuItem("check", "Hecho hoy") { menu = false; haptic.performHapticFeedback(HapticFeedbackType.Confirm); vm.markDone(p.id) }
                    MenuItem("calendar", "Hecho en otra fecha…") { menu = false; vm.newRecord(p.id) }
                    MenuItem("edit", "Cambiar plazo") { menu = false; vm.editing = p.id }
                    MenuItem("history", "Ver historial") { menu = false; vm.openPart(p.id) }
                }
            }
        }
    }
}

@Composable
fun MenuItem(icon: String, text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, style = MaterialTheme.typography.bodyLarge) }, onClick = onClick, leadingIcon = { Icon(icon, LocalShop.current.ink2, 18.dp) })

fun intervalText(p: Part) = listOfNotNull(
    p.km?.let { "cada ${fmtKm(it)} km" },
    p.days?.let { if (it >= 60 && it % 30 == 0 || it >= 365) "cada ${Math.round(it / 30.4)} meses" else "cada $it días" },
).joinToString(" o ").ifEmpty { "sin plazo" }

// ---------- 2B: zone detail, zoomed like a manual's "Detail A" ----------

@Composable
fun ZoneScreen(vm: AppViewModel, bike: Bike, zone: String) {
    val shop = LocalShop.current
    val today = vm.today
    val odo = estimate(bike, today).km
    val zones = zoneStates(bike, odo, today)
    val plate = rememberPlate(vm.drawingFor(bike))
    val st = zones.getValue(zone)
    val last = bike.history.filter { r -> st.parts.any { it.first.id == r.partId } }.maxWithOrNull(compareBy({ it.date }, { it.km }))
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(zoneLabel(zone), { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Sheet(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp)) {
                    Cap("Det. A · ${zoneLabel(zone)}", modifier = Modifier.weight(1f))
                    Cap(statusWord(st.status), shop.status(st.status))
                }
                if (plate.bounds[zone] != null)
                    BikeCanvas(plate, zones, focus = zone, window = plate.detail(listOf(zone)), flash = vm.flash, highlighted = vm.highlighted)
            }
            Column {
                st.parts.forEach { (p, s) -> PartRow(vm, p, s, showInterval = true) }
                if (st.parts.isEmpty()) Hint("No hay tareas de mantenimiento en esta zona para tu moto.", modifier = Modifier.padding(6.dp))
            }
            if (last != null) Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Cap("Último trabajo")
                val name = bike.parts.firstOrNull { it.id == last.partId }?.name ?: ""
                Text(listOfNotNull(prettyDate(last.date), "${fmtKm(last.km)} km", name, last.cost?.let { money(it) }).joinToString(" · "), fontSize = 13.sp, color = shop.ink2)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- 3A: every interval at once, yours next to the recommended one ----------

@Composable
fun PartsScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var reset by remember { mutableStateOf(false) }
    val changed = bike.parts.count { p -> bike.suggested(p.id)?.let { it.km != p.km || it.days != p.days } == true }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header("Plazos", { vm.screen = Screen.main }) {
            if (changed > 0) TextButton({ reset = true }) { Text("RESTABLECER", style = Mono.copy(fontSize = 12.sp), color = shop.accent) }
        }
        Hint(
            "Recomendados para tu tipo de moto (${bike.category.label}, ${bike.drive.label.lowercase()}). Lo que cambies se marca; el manual de tu moto manda.",
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)) {
            Cap("Pieza", modifier = Modifier.weight(1f)); Cap("Tuyo", modifier = Modifier.width(86.dp)); Cap("Recom.", modifier = Modifier.width(78.dp))
        }
        HorizontalDivider(color = shop.line)
        val groups = bike.parts.groupBy { it.zone }.entries.sortedBy { Zone.of(it.key)?.ordinal ?: 99 }
        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (zone, parts) ->
                item(zone) { Cap(zoneLabel(zone), modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp)) }
                items(parts, key = { it.id }) { p ->
                    val rec = bike.suggested(p.id)
                    val diff = rec != null && (rec.km != p.km || rec.days != p.days)
                    Row(
                        Modifier.fillMaxWidth().background(if (diff) shop.accent.copy(alpha = .08f) else shop.paper)
                            .clickable(onClickLabel = "Cambiar plazo") { vm.editing = p.id }.padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(partIcon(p), shop.ink2, 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(intervalCell(p), style = Mono.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = if (diff) shop.accent else shop.ink, modifier = Modifier.width(86.dp))
                        Text(rec?.let(::intervalCell) ?: "—", style = Mono.copy(fontSize = 12.sp), color = shop.ink2, modifier = Modifier.width(78.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (reset) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text("RESTABLECER PLAZOS") },
        text = { Text("Vuelven a los recomendados los $changed plazos que cambiaste. El historial no se toca.") },
        confirmButton = { TextButton({ vm.resetIntervals(); reset = false }) { Text("RESTABLECER") } },
        dismissButton = { TextButton({ reset = false }) { Text("CANCELAR") } },
    )
}

/** "6.000 km" over "12 m", compact for the table. */
private fun intervalCell(p: Part) = listOfNotNull(
    p.km?.let { "${fmtKm(it)} km" },
    p.days?.let { if (it >= 60) "${Math.round(it / 30.4)} m" else "$it d" },
).joinToString("\n").ifEmpty { "—" }
