package com.kaze.motoplus.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import kotlin.math.cos
import kotlin.math.sin

// ---------- 5B: the odometer as an instrument dial ----------

@Composable
fun KmScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val today = vm.today
    val est = estimate(bike, today)
    var fixing by remember { mutableStateOf(false) }
    var value by remember(bike.readings) { mutableStateOf(est.km.toInt().toString()) }
    var ocrNote by remember { mutableStateOf<String?>(null) }
    var confirmJump by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) return@rememberLauncherForActivityResult
        fixing = true; ocrNote = "Leyendo el cuadro…"
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val n = pickOdometer(t.text, est.km)
                if (n != null) { value = n.toString(); ocrNote = "Leído de la foto: ${fmtKm(n)}. Revísalo antes de guardar." }
                else ocrNote = "No encontré el número en la foto. Escríbelo a mano."
            }
            .addOnFailureListener { ocrNote = "No pude leer la foto. Escríbelo a mano." }
    }
    val month = kmThisMonth(bike, today)
    val week = weekRides(bike, today)
    val stats = fuelStats(bike.fuel)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Dial(est.km, month, est.source, est.margin)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Repostar", { vm.screen = Screen.fuel }, Modifier.weight(1f), icon = "fuel")
            Btn("Foto", { photo.launch(null) }, Modifier.weight(1f), ghost = true, icon = "camera")
        }
        if (est.basis != EstimateBasis.todayReading) Row(verticalAlignment = Alignment.CenterVertically) {
            Hint("¿Marca ${fmtKm(est.km.toInt())}?", modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton({ vm.saveReading(est.km.toInt()) }) { Text("SÍ, GUARDAR") }
            TextButton({ fixing = !fixing }) { Text("CORREGIR") }
        } else TextButton({ fixing = !fixing }) { Text("CORREGIR LECTURA") }
        if (fixing) Sheet {
            OutlinedTextField(value, { value = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), label = { Text("Lo que marca el cuadro") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            ocrNote?.let { Hint(it) }
            val last = bike.readings.maxOf { it.km }
            val n = value.toIntOrNull()
            if (n != null && suspiciousJump(n, est.km)) Hint("Son ${fmtKm(n - est.km.toInt())} km más de lo estimado. Revisa que no sobre un dígito.", shop.warn)
            if (n != null && n < last) {
                Hint("Es menos que la última lectura (${fmtKm(last)} km). Si aquella estaba mal, reemplázala.", shop.warn)
                Btn("Reemplazar la última lectura", { vm.replaceLastReading(n); fixing = false; ocrNote = null }, Modifier.fillMaxWidth(), ghost = true)
            } else Btn("Guardar lectura", { if (suspiciousJump(n!!, est.km)) confirmJump = true else { vm.saveReading(n); fixing = false; ocrNote = null } }, Modifier.fillMaxWidth(), enabled = n != null)
            if (confirmJump && n != null) JumpDialog(n, est.km, { confirmJump = false }) { confirmJump = false; vm.saveReading(n); fixing = false; ocrNote = null }
        }
        if (stats.kmPerLiter != null || stats.costPerKm != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stats.kmPerLiter?.let { "%.1f".format(it) } ?: "—", "km/l media", Modifier.weight(1f))
            Stat(stats.costPerKm?.let { money(it) } ?: "—", "por km", Modifier.weight(1f))
        }
        WeekBars(week)
        TrackingCard(vm)
        Log(bike) { vm.deleteFuel(it) }
        Spacer(Modifier.height(4.dp))
    }
}

/** Instrument dial: the needle shows this month's km against the busiest month the rider would expect (1.000 km). */
@Composable
private fun Dial(km: Double, month: Int, source: String, margin: Int) {
    val shop = LocalShop.current
    val scale = maxOf(1000, ((month / 1000) + 1) * 1000)
    val frac = (month.toFloat() / scale).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(270.dp, 230.dp)) {
            val c = Offset(size.width / 2, size.width / 2 * .92f)
            val r = size.width / 2 * .86f
            val start = 150f; val sweep = 240f
            val arcTopLeft = Offset(c.x - r, c.y - r)
            drawArc(shop.line, start, sweep, false, arcTopLeft, Size(2 * r, 2 * r), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(shop.accent, start, sweep * frac, false, arcTopLeft, Size(2 * r, 2 * r), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0..40) {
                val a = Math.toRadians((start + sweep * i / 40).toDouble())
                val r1 = if (i % 5 == 0) r * .86f else r * .91f
                drawLine(shop.ink2, Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1), Offset(c.x + cos(a).toFloat() * r * .96f, c.y + sin(a).toFloat() * r * .96f),
                    (if (i % 5 == 0) 2.dp else 1.dp).toPx())
            }
            val na = Math.toRadians((start + sweep * frac).toDouble())
            // Short needle from the hub to the scale, so it never crosses the reading below the hub.
            drawLine(shop.accent, Offset(c.x + cos(na).toFloat() * r * .5f, c.y + sin(na).toFloat() * r * .5f), Offset(c.x + cos(na).toFloat() * r * .8f, c.y + sin(na).toFloat() * r * .8f), 3.dp.toPx(), cap = StrokeCap.Round)

        }
        Column(Modifier.align(Alignment.Center).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val digits = fmtKm(km.toInt())
            Text(digits, style = Mono.copy(fontSize = if (digits.length > 7) 28.sp else 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = shop.ink)
            Cap("Km · este mes +${fmtKm(month)}")
            Text(if (margin > 0) "± $margin km · $source" else source, style = Mono.copy(fontSize = 11.sp), color = shop.ink2, modifier = Modifier.padding(top = 4.dp))
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
        Cap("Esta semana · salidas")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0) Text("${v.toInt()}", style = Mono.copy(fontSize = 9.sp), color = shop.ink2)
                    Box(Modifier.fillMaxWidth().height((64 * v / max).dp.coerceAtLeast(2.dp))
                        .background(if (i == 6) shop.accent else shop.accent.copy(alpha = .35f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text("LMXJVSD"[days[i] - 1].toString(), style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
                }
            }
        }
        if (week.all { it == 0.0 }) Hint("Las barras se llenan con las salidas que registra el GPS.", modifier = Modifier.padding(top = 6.dp))
    }
}

/** Fill-ups, rides and readings in one list, newest first. */
@Composable
private fun Log(bike: Bike, onDeleteFuel: (String) -> Unit) {
    val shop = LocalShop.current
    data class Row(val sort: String, val icon: String, val title: String, val sub: String, val date: String, val fuelId: String? = null)
    val rows = bike.fuel.map { f -> Row(f.date + f.at, "fuel", "Repostaje · ${"%.1f".format(f.liters)} L", "${fmtKm(f.km)} km" + (f.total?.let { " · ${money(it)}" } ?: ""), f.date, f.id) } +
        bike.rides.map { r -> Row(r.date + r.at, "route", "Salida · ${r.sourceLabel.lowercase()}", "+${"%.1f".format(r.km)} km", r.date) } +
        bike.readings.filter { r -> bike.fuel.none { it.at == r.at && it.km == r.km } }.map { r -> Row(r.date + r.at, "gauge", "Lectura", "${fmtKm(r.km)} km", r.date) }
    Column(Modifier.padding(horizontal = 6.dp)) {
        Cap("Registro")
        rows.sortedByDescending { it.sort }.take(25).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(r.icon, shop.ink2, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(r.sub, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
                }
                Text(prettyDate(r.date).uppercase(), style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
                r.fuelId?.let { id -> IconBtn("trash", "Borrar repostaje", { onDeleteFuel(id) }, size = 18.dp) }
            }
            HorizontalDivider(color = shop.line)
        }
    }
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
    val totalN = total.replace(".", "").replace(',', '.').toDoubleOrNull()
    val last = bike.readings.maxOf { it.km }
    val preview = if (kmN != null && litersN != null) fuelStats(bike.fuel + FuelEntry("new", vm.today.toString(), kmN, litersN, totalN, full)) else null
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header("Repostaje", { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(km, { km = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), label = { Text("Odómetro (km)") }, singleLine = true,
                textStyle = Mono.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Hint("La app estimaba ${fmtKm(est.km.toInt())} km. Este dato la corrige.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(liters, { liters = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, Modifier.weight(1f), label = { Text("Litros") }, singleLine = true,
                    textStyle = Mono.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(total, { total = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) }, Modifier.weight(1f), label = { Text("Total pagado") }, singleLine = true,
                    textStyle = Mono.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Row(Modifier.fillMaxWidth().toggleable(full, role = Role.Checkbox) { full = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(full, null); Text("Tanque lleno", fontSize = 14.sp)
            }
            Hint("El consumo se mide entre dos repostajes con el tanque lleno.")
            preview?.let { p ->
                Sheet {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        PreviewStat(p.kmPerLiter?.let { "%.1f".format(it) } ?: "—", "km/l")
                        PreviewStat(if (totalN != null && litersN != null) money(totalN / litersN) else "—", "por litro")
                        PreviewStat(p.costPerKm?.let { money(it) } ?: "—", "por km")
                    }
                }
            }
            if (kmN != null && kmN < last) Hint("Debe ser mayor o igual que la última lectura (${fmtKm(last)} km).", shop.bad)
            if (kmN != null && suspiciousJump(kmN, est.km)) Hint("Son ${fmtKm(kmN - est.km.toInt())} km más de lo estimado. Revisa que no sobre un dígito.", shop.warn)
        }
        var confirm by remember { mutableStateOf(false) }
        Btn("Guardar", { if (suspiciousJump(kmN!!, est.km)) confirm = true else vm.saveFuel(kmN, litersN!!, totalN, full) },
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
    title = { Text("¿${fmtKm(km)} KM?") },
    text = { Text("Son ${fmtKm(km - estimate.toInt())} km más de lo que la app estimaba (${fmtKm(estimate.toInt())}). Si sobra un dígito, corrígelo.") },
    confirmButton = { TextButton(onSave) { Text("SÍ, ES CORRECTO") } },
    dismissButton = { TextButton(onFix) { Text("CORREGIR") } },
)
