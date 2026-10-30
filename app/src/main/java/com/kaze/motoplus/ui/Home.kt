package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.saveOrder
import com.kaze.motoplus.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*

@Composable
fun BikeScreen(vm: AppViewModel, bike: Bike) {
    val today = vm.today
    val odo = estimate(bike, today).km
    val zones = zoneStates(bike, odo, today)
    val states = bike.parts.map { it to partState(it, odo, today) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HealthBar(health(bike, odo, today), states.count { it.second.status != Status.ok })
        BikeCanvas(rememberPlate(vm.drawingFor(bike)), zones, highlighted = vm.highlighted,
            callouts = callouts(bike, zones), flash = vm.flash, onZone = { vm.openZone(it) })
        Column {
            Box(Modifier.padding(bottom = 10.dp)) { SectionTitle(stringResource(R.string.next)) }
            val alerts = states.filter { it.second.status != Status.ok && !it.first.snoozed(today) }.sortedByDescending { it.second.used }
            alerts.forEach { (p, state) -> PartRow(vm, p, state) }
            if (alerts.isEmpty()) Hint(stringResource(R.string.no_alerts))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Callouts for zones that need attention, most urgent first (overdue, coming due, never recorded), labelled with their worst part. */
@Composable
fun callouts(bike: Bike, zones: Map<String, ZoneState>): List<Callout> = zones.entries
    .sortedWith(compareBy<Map.Entry<String, ZoneState>> { listOf(Status.bad, Status.warn, Status.none, Status.ok).indexOf(it.value.status) }.thenByDescending { it.value.worst })
    .mapNotNull { (z, st) ->
    if (st.status == Status.ok) return@mapNotNull null
    val (_, s) = st.parts.firstOrNull { it.second.status == st.status } ?: return@mapNotNull null
    val value = when {
        s.measure == null -> "?"
        s.measure == Measure.km -> (if (s.left < 0) "−" else "") + distance(kotlin.math.abs(s.left)) + " " + stringResource(R.string.km_unit)
        else -> (if (s.left < 0) "−" else "") + kotlin.math.abs(s.left) + " " + stringResource(R.string.day_short)
    }
    Callout(z, "${zoneAbbreviation(z, bike.drive)} $value", st.status)
}

@Composable
private fun HealthBar(score: Int, alerts: Int) {
    val shop = LocalShop.current
    val color = shop.status(healthBand(score))
    Column(Modifier.fillMaxWidth().background(shop.sheet).drawBehind {
        drawLine(color, Offset.Zero, Offset(0f, size.height), 3.dp.toPx())
    }.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Cap(stringResource(R.string.health_title), modifier = Modifier.weight(1f))
            Text(score.toString(), style = Display.copy(fontSize = 44.sp), color = color)
            Text(stringResource(R.string.p3_score_max), style = Mono.copy(fontSize = 11.sp), color = shop.ink2, modifier = Modifier.padding(start = 3.dp, top = 16.dp))
        }
        Spacer(Modifier.height(8.dp))
        SegmentedMeter(score / 100f, color, stringResource(R.string.health_title))
        Spacer(Modifier.height(10.dp))
        Text(when (alerts) { 0 -> stringResource(R.string.health_ok); 1 -> stringResource(R.string.health_one); else -> stringResource(R.string.health, alerts) }.uppercase(),
            style = Caption.copy(fontSize = 9.sp, letterSpacing = .3.sp), color = shop.ink2)
    }
}

/** The same segmented instrument is used for health and monthly distance. */
@Composable
fun SegmentedMeter(fraction: Float, color: androidx.compose.ui.graphics.Color, label: String, height: Dp = 11.dp) {
    val track = LocalShop.current.line
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(20) { i ->
            val cell = if (i < Math.round(fraction.coerceIn(0f, 1f) * 20)) color else track
            Canvas(Modifier.weight(1f).height(height)) {
                val skew = 2.dp.toPx().coerceAtMost(size.width / 3)
                drawPath(Path().apply { moveTo(skew, 0f); lineTo(size.width, 0f); lineTo(size.width - skew, size.height); lineTo(0f, size.height); close() }, cell)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartRow(vm: AppViewModel, p: Part, s: PartState, showInterval: Boolean = false) {
    val shop = LocalShop.current
    var menu by remember { mutableStateOf(false) }
    var snoozing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf("7") }
    val name = partLabel(p)
    val left = remaining(s)
    Column {
        HorizontalDivider(color = shop.line)
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.alert_actions)) { menu = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(partIcon(p), shop.status(s.status), 24.dp)
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(left + if (showInterval) " · ${localizedInterval(p)}" else "", style = Mono.copy(fontSize = 11.sp), color = shop.status(s.status))
            }
            Icon("chev", shop.ink2, 18.dp)
        }
    }
    if (menu && !deleting) ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = shop.sheet) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SectionTitle(name)
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LifeRing(partIcon(p), s.status, s.used)
                Hint(left, modifier = Modifier.padding(start = 12.dp))
            }
            SettingsEntry("check", stringResource(R.string.done)) { vm.markDone(p.id); menu = false }
            SettingsEntry("calendar", stringResource(R.string.other_date)) { vm.newRecord(p.id); menu = false }
            SettingsEntry("clock", stringResource(R.string.snooze)) { snoozing = true; menu = false }
            SettingsEntry("wrench", stringResource(R.string.add_to_order)) {
                val order = vm.bike?.openOrder()
                vm.saveOrder(((order?.partIds ?: emptyList()) + p.id).distinct(), order?.notes ?: "")
                menu = false
            }
            SettingsEntry("history", stringResource(R.string.history_action)) { vm.openPart(p.id); menu = false }
            Btn(stringResource(R.string.view_limits), { vm.editing = p.id; menu = false }, Modifier.fillMaxWidth(), ghost = true, icon = "settings")
            TextButton({ deleting = true }) { Text(stringResource(R.string.delete), color = shop.bad) }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (deleting) ConfirmDelete(name, { deleting = false }) { vm.removePart(p.id); deleting = false; menu = false }
    if (snoozing) AlertDialog(onDismissRequest = { snoozing = false }, title = { Text(stringResource(R.string.snooze)) }, text = {
        IntegerField(days, { days = it }, stringResource(R.string.snooze_days), maxDigits = 3)
    }, confirmButton = { TextButton({ vm.snooze(p.id, days.toLong()); snoozing = false }, enabled = days.toIntOrNull() in 1..365) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ snoozing = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun localizedInterval(p: Part): String = listOfNotNull(
    p.km?.let { stringResource(R.string.every_km, distance(it)) },
    p.days?.let { stringResource(R.string.p3_every_time_value, localizedTimeInterval(p)) },
).joinToString(stringResource(R.string.or)).ifEmpty { stringResource(R.string.no_interval) }

@Composable
fun MenuItem(icon: String, text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, style = MaterialTheme.typography.bodyLarge) }, onClick = onClick, leadingIcon = { Icon(icon, LocalShop.current.ink2, 18.dp) })

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
        Header(zoneName(zone), { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Sheet(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp)) {
                    Cap(stringResource(R.string.detail, zoneName(zone)), modifier = Modifier.weight(1f))
                    Cap(statusLabel(st.status), shop.status(st.status))
                }
                if (plate.bounds[zone] != null)
                    BikeCanvas(plate, zones, focus = zone, window = plate.detail(listOf(zone)), flash = vm.flash, highlighted = vm.highlighted)
            }
            Column {
                st.parts.forEach { (p, s) -> PartRow(vm, p, s, showInterval = true) }
                if (st.parts.isEmpty()) Hint(stringResource(R.string.empty_zone), modifier = Modifier.padding(6.dp))
            }
            if (last != null) Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Cap(stringResource(R.string.last_work))
                val name = bike.parts.firstOrNull { it.id == last.partId }?.let { partLabel(it) } ?: ""
                Text(listOfNotNull(localizedDate(last.date), stringResource(R.string.p2_km_value, distance(last.km)), name, last.cost?.let { currencyMoney(it, vm.garage.currency) }).joinToString(" · "), fontSize = 13.sp, color = shop.ink2)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
