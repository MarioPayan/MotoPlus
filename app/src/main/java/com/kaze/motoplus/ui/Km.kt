package com.kaze.motoplus.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.kaze.motoplus.R
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*
import com.kaze.motoplus.saveFuel
import com.kaze.motoplus.replaceLastReading
import com.kaze.motoplus.deleteFuel

// ---------- 5B: the odometer as an instrument dial ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val ctx = LocalContext.current
    val today = vm.today
    val est = estimate(bike, today)
    var fixing by remember { mutableStateOf(false) }
    var value by remember(bike.readings) { mutableStateOf(est.km.toInt().toString()) }
    var ocrNote by remember { mutableStateOf<String?>(null) }
    var confirmJump by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) return@rememberLauncherForActivityResult
        fixing = true; ocrNote = ctx.getString(R.string.ocr_reading)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val n = pickOdometer(t.text, est.km)
                if (n != null) { value = n.toString(); ocrNote = ctx.getString(R.string.ocr_found, formatKm(n, ctx.resources.configuration.locales[0])) }
                else ocrNote = ctx.getString(R.string.ocr_missing)
            }
            .addOnFailureListener { ocrNote = ctx.getString(R.string.photo_failed) }
    }
    // Sources opens the same photo confirmation flow.
    LaunchedEffect(vm.odometerPhoto) { if (vm.odometerPhoto) { vm.odometerPhoto = false; photo.launch(null) } }
    val month = kmThisMonth(bike, today)
    val week = weekRides(bike, today)
    val stats = fuelStats(bike.fuel)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Dial(bike, est, month) { value = est.km.toInt().toString(); fixing = true }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.prefs.on(Feature.fuel)) Btn(stringResource(R.string.refuel), { vm.screen = Screen.fuel }, Modifier.weight(1f), icon = "fuel")
            Btn(stringResource(R.string.photo), { photo.launch(null) }, Modifier.weight(1f), ghost = true, icon = "camera")
        }
        if (vm.prefs.on(Feature.fuel)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stats.kmPerLiter?.let { decimal(it) } ?: "—", stringResource(R.string.mean_kml), Modifier.weight(1f))
            Stat(stats.costPerKm?.let { currencyMoney(it, vm.garage.currency) } ?: "—", stringResource(R.string.per_km), Modifier.weight(1f))
        }
        WeekBars(week)
        if (vm.prefs.on(Feature.kmAuto)) TrackingSummary(vm)
        Log(bike, vm.garage.currency) { vm.deleteFuel(it) }
        Spacer(Modifier.height(4.dp))
    }
    if (fixing) ModalBottomSheet(onDismissRequest = { fixing = false; ocrNote = null }, containerColor = shop.sheet) {
        Column(Modifier.padding(horizontal = 20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(stringResource(R.string.odometer_reading))
            OdometerField(value, { value = it }, Modifier.fillMaxWidth())
            ocrNote?.let { Hint(it) }
            val last = bike.readings.maxOf { it.km }
            val n = value.toIntOrNull()
            if (n != null && suspiciousJump(n, est.km)) Hint(stringResource(R.string.jump_hint, distance(n - est.km.toInt())), shop.warn)
            if (n != null && n < last) {
                Hint(stringResource(R.string.lower_reading, distance(last)), shop.warn)
                Btn(stringResource(R.string.replace_reading), { vm.replaceLastReading(n); fixing = false; ocrNote = null }, Modifier.fillMaxWidth(), ghost = true)
            } else Btn(stringResource(R.string.save_reading), { if (suspiciousJump(n!!, est.km)) confirmJump = true else { vm.saveReading(n); fixing = false; ocrNote = null } }, Modifier.fillMaxWidth(), enabled = n != null)
            if (confirmJump && n != null) JumpDialog(n, est.km, { confirmJump = false }) { confirmJump = false; vm.saveReading(n); fixing = false; ocrNote = null }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Centred reading and monthly progress against the rider's own average. */
@Composable
private fun Dial(bike: Bike, est: OdoEstimate, month: Int, onCorrect: () -> Unit) {
    val shop = LocalShop.current
    val average = monthlyAverage(bike, java.time.LocalDate.now())
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cap(stringResource(R.string.odometer))
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 40.dp)) {
                val digits = distance(est.km.toInt())
                Text(digits, style = Display.copy(fontSize = if (digits.length > 7) 60.sp else 80.sp, fontWeight = FontWeight.SemiBold))
                Text(stringResource(R.string.km_lower), style = Mono.copy(fontSize = 11.sp), color = shop.ink2, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
            }
            Box(Modifier.align(Alignment.CenterEnd)) { IconBtn("edit", stringResource(R.string.correct_reading), onCorrect) }
        }
        Text((if (est.margin > 0) stringResource(R.string.estimate_margin, distance(est.margin)) else "") + estimateSource(est), style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
        Spacer(Modifier.height(22.dp))
        Cap(stringResource(R.string.month_distance, distance(month)), modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        if (average != null) {
            SegmentedMeter(month.toFloat() / average, shop.accent, stringResource(R.string.month_distance, distance(month)), 18.dp)
            Spacer(Modifier.height(8.dp))
            Hint(stringResource(R.string.month_average, distance(average)), modifier = Modifier.align(Alignment.Start))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) = Sheet(modifier, padding = 10.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Mono.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = LocalShop.current.ink)
        Cap(label)
    }
}

@Composable
private fun WeekBars(week: List<Double>) {
    val shop = LocalShop.current
    val max = week.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val days = (6 downTo 0).map { java.time.LocalDate.now().minusDays(it.toLong()).dayOfWeek.value }
    Column(Modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.week), style = Display.copy(fontSize = 23.sp), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.p2_km_value, distance(week.sum().toInt())), style = Mono.copy(fontSize = 18.sp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v > 0) "${v.toInt()}" else "—", style = Mono.copy(fontSize = 9.sp), color = shop.ink2)
                    Box(Modifier.fillMaxWidth().height((64 * v / max).dp.coerceAtLeast(2.dp))
                        .background(if (v == 0.0) shop.line else if (i == 6) shop.accent else shop.accent.copy(alpha = .35f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.week_days).split("|")[days[i] - 1], style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
                }
            }
        }
        if (week.all { it == 0.0 }) Hint(stringResource(R.string.weekly_hint), modifier = Modifier.padding(top = 6.dp))
    }
}

/** Fill-ups, rides and readings in one list, newest first. */
@Composable
private fun Log(bike: Bike, currency: String, onDeleteFuel: (String) -> Unit) {
    val shop = LocalShop.current
    var deletingFuel by remember { mutableStateOf<String?>(null) }
    data class Row(val sort: String, val icon: String, val title: String, val sub: String, val date: String, val fuelId: String? = null)
    val rows = bike.fuel.map { f -> Row(f.date + f.at, "fuel", stringResource(R.string.fillup_line, decimal(f.liters)), stringResource(R.string.p2_km_value, distance(f.km)) + (f.total?.let { " · ${currencyMoney(it, currency)}" } ?: ""), f.date, f.id) } +
        bike.rides.map { r -> Row(r.date + r.at, "route", stringResource(R.string.ride_line, rideSource(r)), "+" + stringResource(R.string.p2_km_value, decimal(r.km)), r.date) } +
        bike.readings.filter { r -> bike.fuel.none { it.at == r.at && it.km == r.km } }.map { r -> Row(r.date + r.at, "gauge", stringResource(R.string.reading), stringResource(R.string.p2_km_value, distance(r.km)), r.date) }
    Column(Modifier.padding(horizontal = 6.dp)) {
        Cap(stringResource(R.string.log))
        rows.sortedByDescending { it.sort }.take(25).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(r.icon, shop.ink2, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(r.sub, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
                }
                Text(localizedDate(r.date).uppercase(), style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
                r.fuelId?.let { id -> IconBtn("trash", stringResource(R.string.delete_fuel), { deletingFuel = id }, size = 18.dp) }
            }
            HorizontalDivider(color = shop.line)
        }
    }
    deletingFuel?.let { id -> ConfirmDelete(stringResource(R.string.fillup), { deletingFuel = null }) {
        onDeleteFuel(id); deletingFuel = null
    } }

}

// ---------- Fill-up form ----------

@Composable
fun FuelScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val est = estimate(bike, vm.today)
    var km by remember { mutableStateOf(est.km.toInt().toString()) }
    var liters by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    val kmN = km.toIntOrNull()
    val litersN = liters.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val totalN = parseAmount(total, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
    val last = bike.readings.maxOf { it.km }
    val preview = if (kmN != null && litersN != null) fuelStats(bike.fuel + FuelEntry("new", vm.today.toString(), kmN, litersN, totalN, full)) else null
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(stringResource(R.string.fillup), { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OdometerField(km, { km = it }, Modifier.fillMaxWidth())
            Hint(stringResource(R.string.fuel_hint, distance(est.km.toInt())))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField(liters, { liters = it }, stringResource(R.string.liters), Modifier.weight(1f), maxFractionDigits = 2)
                AmountField(total, { total = it }, Modifier.weight(1f), label = stringResource(R.string.paid))
            }
            Row(Modifier.fillMaxWidth().toggleable(full, role = Role.Checkbox) { full = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(full, null); Text(stringResource(R.string.full_tank), fontSize = 14.sp)
            }
            Hint(stringResource(R.string.consumption_hint))
            preview?.let { p ->
                Sheet {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        PreviewStat(p.kmPerLiter?.let { decimal(it) } ?: "—", stringResource(R.string.kml))
                        PreviewStat(if (totalN != null && litersN != null) currencyMoney(totalN / litersN, vm.garage.currency) else "—", stringResource(R.string.per_liter))
                        PreviewStat(p.costPerKm?.let { currencyMoney(it, vm.garage.currency) } ?: "—", stringResource(R.string.per_km))
                    }
                }
            }
            if (kmN != null && kmN < last) Hint(stringResource(R.string.fuel_lower, distance(last)), shop.bad)
            if (kmN != null && suspiciousJump(kmN, est.km)) Hint(stringResource(R.string.jump_hint, distance(kmN - est.km.toInt())), shop.warn)
        }
        var confirm by remember { mutableStateOf(false) }
        Btn(stringResource(R.string.save), { if (suspiciousJump(kmN!!, est.km)) confirm = true else vm.saveFuel(kmN, litersN!!, totalN, full) },
            Modifier.fillMaxWidth().padding(16.dp), enabled = kmN != null && kmN >= last && litersN != null)
        if (confirm) JumpDialog(kmN!!, est.km, { confirm = false }) { confirm = false; vm.saveFuel(kmN, litersN!!, totalN, full) }
    }
}

@Composable
private fun PreviewStat(v: String, l: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, style = Mono.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = LocalShop.current.ink)
    Cap(l)
}

/** A typed odometer far above the estimate is usually an extra digit: ask before saving it. */
@Composable
private fun JumpDialog(km: Int, estimate: Double, onFix: () -> Unit, onSave: () -> Unit) = AlertDialog(
    onDismissRequest = onFix,
    title = { Text(stringResource(R.string.jump_title, distance(km))) },
    text = { Text(stringResource(R.string.jump_text, distance(km - estimate.toInt()), distance(estimate.toInt()))) },
    confirmButton = { TextButton(onSave) { Text(stringResource(R.string.yes_correct)) } },
    dismissButton = { TextButton(onFix) { Text(stringResource(R.string.correct)) } },
)
