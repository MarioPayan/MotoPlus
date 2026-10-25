package com.kaze.motoplus.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import androidx.compose.foundation.border
import com.kaze.motoplus.data.Catalog
import com.kaze.motoplus.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ES = Locale.forLanguageTag("es-ES")
internal fun prettyDate(iso: String) = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))

// ---------- Alta de moto ----------

@Composable
fun AddBikeScreen(vm: AppViewModel, canCancel: Boolean) {
    val shop = LocalShop.current
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Catalog.Hit?>(null) }
    var manual by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.naked) }
    var drive by remember { mutableStateOf(Drive.chain) }
    var cooling by remember { mutableStateOf(Cooling.liquid) }
    var km by remember { mutableStateOf("") }
    var allDone by remember { mutableStateOf(false) }

    fun choose(h: Catalog.Hit) {
        pick = h; brand = h.brand; model = h.model.n
        year = h.model.y.filter { it <= LocalDate.now().year }.maxOrNull()?.toString() ?: ""
        category = h.model.c; drive = defaultDrive(category); cooling = defaultCooling(category)
    }

    Column(
        Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AÑADE TU MOTO", style = Display.copy(fontSize = 30.sp), color = shop.ink)
        if (pick == null && !manual) {
            Hint("Busca por marca y modelo. ${vm.catalog?.brands?.size ?: "…"} marcas en el catálogo.")
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Ej.: yamaha mt-07") }, singleLine = true)
            val hits = remember(query, vm.catalog) { vm.catalog?.search(query).orEmpty() }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(hits) { h ->
                    Row(Modifier.fillMaxWidth().clickable { choose(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${h.brand} ${h.model.n}", fontWeight = FontWeight.SemiBold)
                            Hint(listOfNotNull(h.model.c.label, h.model.y.takeIf { it.isNotEmpty() }?.let { "${it.min()}–${it.max()}" }).joinToString(" · "))
                        }
                        Text("›", fontSize = 22.sp, color = shop.ink2)
                    }
                    HorizontalDivider(color = shop.line)
                }
                item {
                    TextButton({ manual = true; brand = ""; model = query }) { Text("No encuentro mi moto: escribirla a mano") }
                }
            }
            if (canCancel) TextButton({ vm.adding = false }) { Text("Cancelar") }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (manual) {
                OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("Marca") }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Modelo") }, singleLine = true)
            } else {
                Sheet {
                    Text("$brand $model", style = Display, color = shop.ink)
                    TextButton({ pick = null }, contentPadding = PaddingValues(0.dp)) { Text("Cambiar modelo") }
                }
            }
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Año") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Hint("Tipo")
            ChoiceRow(Category.entries.map { it.name to it.label }, category.name) {
                category = Category.valueOf(it); drive = defaultDrive(category); cooling = defaultCooling(category)
            }
            Hint("Transmisión")
            ChoiceRow(Drive.entries.map { it.name to it.label }, drive.name) { drive = Drive.valueOf(it) }
            Hint("Refrigeración")
            ChoiceRow(Cooling.entries.map { it.name to it.label }, cooling.name) { cooling = Cooling.valueOf(it) }
            OutlinedTextField(km, { km = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), label = { Text("Kilómetros que marca ahora") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allDone, { allDone = it })
                Text("Acabo de hacerle la revisión completa", fontSize = 14.sp)
            }
            Hint("Si no, todo empieza en rojo como \"sin registro\" y lo vas marcando a medida que lo revisas. Los intervalos son genéricos para este tipo de moto; se pueden ajustar.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ pick = null; manual = false }) { Text("Atrás") }
            Button(
                { vm.addBike(brand, model, year.toIntOrNull(), category, drive, cooling, km.toInt(), allDone) },
                Modifier.weight(1f), enabled = brand.isNotBlank() && model.isNotBlank() && km.isNotEmpty(),
            ) { Text("Guardar moto") }
        }
    }
}

// ---------- Moto ----------

@Composable
fun MotoScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val today = vm.today
    val odo = estimate(bike, today).km
    val zones = zoneStates(bike, odo, today)
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${health(bike, odo, today)}", style = Display.copy(fontSize = 34.sp), color = shop.ink)
                Text(" / 100 salud", fontSize = 14.sp, color = shop.ink2, modifier = Modifier.weight(1f))
                InfoButton { info = true }
            }
            BikeCanvas(vm.drawingFor(bike), zones, vm.selectedZone, vm.highlighted, onZone = {
                vm.selectedZone = it; vm.highlighted = vm.highlighted - it
            })
            OutlinedButton({ vm.screen = Screen.parts }, Modifier.fillMaxWidth()) {
                Text("Ver todas las piezas (${bike.parts.size})")
            }
        }
        val sel = vm.selectedZone
        Sheet {
            if (sel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle(ZONES.getValue(sel)) }
                    TextButton({ vm.selectedZone = null }) { Text("Cerrar") }
                }
                zones.getValue(sel).parts.forEach { (p, s) -> PartRow(p, s, { vm.markDone(p.id) }, { vm.editing = p.id }, { vm.openPart(p.id) }) }
                if (zones.getValue(sel).parts.isEmpty()) Hint("No hay piezas de mantenimiento en esta zona para tu moto.")
            } else {
                SectionTitle("Lo próximo")
                bike.parts.map { it to partState(it, odo, today) }.sortedByDescending { it.second.used }.take(3)
                    .forEach { (p, s) -> PartRow(p, s, { vm.markDone(p.id) }, { vm.editing = p.id }, { vm.openPart(p.id) }) }
                Hint("Toca una zona de la moto para ver sus piezas.")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    if (info) LegendDialog { info = false }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    val shop = LocalShop.current
    Box(
        Modifier.size(32.dp).clickable(onClickLabel = "Cómo leer los colores", onClick = onClick).padding(4.dp)
            .border(1.5.dp, shop.ink2, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("i", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = shop.ink2) }
}

@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    val shop = LocalShop.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Entendido") } },
        title = { Text("Cómo leer la moto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Verde: le queda más de una cuarta parte del plazo.", shop.ok)
                Pill("Ámbar: le queda menos de una cuarta parte. Ve planeándolo.", shop.warn)
                Pill("Rojo: plazo vencido o nunca registrado. Las zonas vencidas laten.", shop.bad)
                Pill("Naranja que late: zona señalada por tu último informe de la IA.", shop.accent)
                Text("Cada zona toma el color de su pieza más urgente. Cada plazo cuenta por kilómetros o por tiempo, lo que llegue antes, y los puedes cambiar en Piezas y plazos.", fontSize = 13.sp, color = shop.ink2)
            }
        },
    )
}

@Composable
fun PartRow(p: Part, s: PartState, onDone: () -> Unit, onEdit: () -> Unit, onOpen: (() -> Unit)? = null) {
    val shop = LocalShop.current
    val c = shop.status(s.status)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = shop.line)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (onOpen != null) "${p.name}  ›" else p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable(onClickLabel = "Ver historial", onClick = onOpen) else Modifier),
            )
            Text(s.text, color = c, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { s.used.toFloat().coerceIn(0f, 1f) }, color = c, trackColor = shop.line.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {}, gapSize = 0.dp,
        )
        val last = p.lastDate?.let { "Última: ${p.lastKm?.let { fmtKm(it) + " km · " } ?: ""}${prettyDate(it)}" } ?: "Nunca registrada"
        Hint(last, Modifier)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${intervalText(p)}  ✎", fontSize = 12.sp, color = shop.accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "Cambiar plazo", onClick = onEdit).padding(vertical = 6.dp),
            )
            Button(onDone, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text("Hecho hoy", fontSize = 13.sp) }
        }
    }
}

fun intervalText(p: Part) = listOfNotNull(
    p.km?.let { "cada ${fmtKm(it)} km" },
    p.days?.let { if (it >= 60 && it % 30 == 0 || it >= 365) "cada ${Math.round(it / 30.4)} meses" else "cada $it días" },
).joinToString(" o ").ifEmpty { "sin plazo" }

// ---------- Piezas y plazos ----------

@Composable
fun PartsScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val today = vm.today
    val odo = estimate(bike, today).km
    val zones = zoneStates(bike, odo, today)
    androidx.activity.compose.BackHandler { vm.screen = Screen.main }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ vm.screen = Screen.main }) { Text("‹ Volver") }
            Text("PIEZAS Y PLAZOS", style = Display.copy(fontSize = 22.sp), color = shop.ink)
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Hint("Toca un plazo para cambiarlo. Los sugeridos son genéricos para este tipo de moto (${bike.category.label}, ${bike.drive.label.lowercase()}); el manual de tu moto manda.") }
            items(zones.keys.filter { zones.getValue(it).parts.isNotEmpty() }.sortedByDescending { zones.getValue(it).worst }) { z ->
                val st = zones.getValue(z)
                Sheet {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Pill(ZONES.getValue(z).uppercase(), shop.status(st.status)) }
                        TextButton({ vm.selectedZone = z; vm.screen = Screen.main }) { Text("Ver en la moto") }
                    }
                    st.parts.forEach { (p, s) -> PartRow(p, s, { vm.markDone(p.id) }, { vm.editing = p.id }, { vm.openPart(p.id) }) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun IntervalDialog(part: Part, suggested: Part?, onSave: (Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    val shop = LocalShop.current
    var km by remember { mutableStateOf(part.km?.toString() ?: "") }
    val monthsUnit = part.days == null || part.days >= 60
    var months by remember { mutableStateOf(monthsUnit) }
    var time by remember { mutableStateOf(part.days?.let { if (monthsUnit) Math.round(it / 30.4).toString() else it.toString() } ?: "") }
    val kmN = km.toIntOrNull()?.takeIf { it > 0 }
    val days = time.toIntOrNull()?.takeIf { it > 0 }?.let { if (months) (it * 30.4).toInt() else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(part.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(km, { km = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Cada (km)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Sin límite por km") })
                OutlinedTextField(time, { time = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text(if (months) "Cada (meses)" else "Cada (días)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Sin límite por tiempo") })
                ChoiceRow(listOf("m" to "Meses", "d" to "Días"), if (months) "m" else "d") { months = it == "m" }
                Hint("Lo que llegue antes. Deja uno vacío para contar solo por el otro.")
                suggested?.takeIf { it.km != kmN || it.days != days }?.let { sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint("Sugerido: ${intervalText(sp)}", Modifier.weight(1f))
                        TextButton({
                            km = sp.km?.toString() ?: ""
                            months = sp.days == null || sp.days >= 60
                            time = sp.days?.let { if (months) Math.round(it / 30.4).toString() else it.toString() } ?: ""
                        }) { Text("Usar sugerido") }
                    }
                }
                if (kmN == null && days == null) Hint("Pon al menos un plazo.", shop.bad)
            }
        },
        confirmButton = { TextButton({ onSave(kmN, days) }, enabled = kmN != null || days != null) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier) = Text(text, fontSize = 12.sp, color = LocalShop.current.ink2, modifier = modifier)

// ---------- Km ----------

@Composable
fun KmScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val est = estimate(bike, vm.today)
    var fixing by remember { mutableStateOf(false) }
    var value by remember(bike.readings) { mutableStateOf(est.km.toInt().toString()) }
    var ocrNote by remember { mutableStateOf<String?>(null) }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sheet {
            Hint("ODÓMETRO ESTIMADO")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                fmtKm(est.km.toInt()).forEach { ch ->
                    if (ch == '.') Text(".", style = Mono.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
                    else Text("$ch", style = Mono.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold), color = shop.paper,
                        modifier = Modifier.padding(horizontal = 1.5.dp).background(shop.ink, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp))
                }
            }
            Text("± ${est.margin} km · ${est.source}", style = Mono.copy(fontSize = 12.sp), color = shop.ink2, modifier = Modifier.align(Alignment.CenterHorizontally))
            Hint("¿Es lo que marca tu moto?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.saveReading(est.km.toInt()) }) { Text("Sí, ${fmtKm(est.km.toInt())}") }
                OutlinedButton({ fixing = !fixing }) { Text("Corregir") }
                OutlinedButton({ photo.launch(null) }) { Text("Foto") }
            }
            if (fixing) {
                OutlinedTextField(value, { value = it.filter(Char::isDigit).take(7) }, Modifier.fillMaxWidth(), label = { Text("Lo que marca el cuadro") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                ocrNote?.let { Hint(it) }
                val last = bike.readings.maxOf { it.km }
                val n = value.toIntOrNull()
                Button({ vm.saveReading(n!!); fixing = false; ocrNote = null }, enabled = n != null && n >= last) { Text("Guardar lectura") }
                if (n != null && n < last) Hint("Debe ser mayor o igual que la última lectura (${fmtKm(last)} km).", shop.bad)
            }
        }
        Sheet {
            SectionTitle("Salidas detectadas")
            if (bike.rides.isEmpty()) Hint("La detección automática (al conectar el casco o al ir en vehículo) llega en la siguiente fase. Mientras tanto, la app estima con tu media de km por día.")
            bike.rides.reversed().forEach { r -> Row { Text(prettyDate(r.date), Modifier.weight(1f), fontSize = 13.sp); Text("${r.km.toInt()} km", fontWeight = FontWeight.SemiBold) } }
        }
        Sheet {
            SectionTitle("Lecturas reales")
            bike.readings.sortedByDescending { it.date }.forEach { r ->
                Row { Text(prettyDate(r.date), Modifier.weight(1f), fontSize = 13.sp); Text("${fmtKm(r.km)} km", fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------- Informe IA ----------

@Composable
fun ReportScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val ctx = LocalContext.current
    var symptom by remember { mutableStateOf("") }
    var answer by remember(vm.pendingImport) { mutableStateOf(vm.pendingImport) }
    var error by remember(vm.pendingImport) { mutableStateOf(if (vm.pendingImport.isNotEmpty()) "No pude leer el informe compartido. Revísalo o pide a la IA que repita el bloque motoplus." else null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sheet {
            SectionTitle("¿Qué le pasa?")
            OutlinedTextField(symptom, { symptom = it }, Modifier.fillMaxWidth(), label = { Text("Describe el síntoma con tus palabras") }, minLines = 2)
            Button({
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, buildPrompt(bike, symptom, vm.today))
                ctx.startActivity(Intent.createChooser(send, "Enviar el informe a tu IA"))
            }, Modifier.fillMaxWidth()) { Text("Compartir con mi IA") }
            Hint("Se abre el menú Compartir: elige ChatGPT, Claude, Gemini o la que uses. Cuando conteste, comparte su respuesta de vuelta a MotoPlus o cópiala y pégala abajo.")
        }
        Sheet {
            SectionTitle("Traer la respuesta")
            OutlinedTextField(answer, { answer = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Respuesta completa o enlace de la IA") }, minLines = 3, maxLines = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ error = vm.importReport(answer); if (error == null) answer = "" }, enabled = answer.isNotBlank()) { Text("Guardar informe") }
                OutlinedButton({
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    answer = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty(); error = null
                }) { Text("Pegar") }
            }
            error?.let { Hint(it, shop.bad) }
        }
        bike.reports.reversed().forEach { r -> ReportCard(r) { vm.showZones(r.data.causas.mapNotNull { it.zona }.toSet()) } }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ReportCard(r: SavedReport, onSee: () -> Unit) {
    val shop = LocalShop.current
    val d = r.data
    val (label, c) = when (d.urgencia) {
        "baja" -> "Urgencia baja · puedes rodar" to shop.ok
        "alta" -> "Urgencia alta · no ruedes" to shop.bad
        else -> "Urgencia media · revísalo pronto" to shop.warn
    }
    Sheet {
        Hint("${prettyDate(r.at)} · ${fmtKm(r.km)} km")
        Text(label.uppercase(), style = Display.copy(fontSize = 13.sp), color = c,
            modifier = Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        SectionTitle(d.titulo)
        Text(d.resumen, fontSize = 13.sp)
        d.causas.forEach { k ->
            HorizontalDivider(color = shop.line)
            Row {
                Column(Modifier.weight(1f)) {
                    Text(k.causa, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    k.zona?.let { Hint(ZONES.getValue(it)) }
                }
                Text(k.probabilidad, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
            }
        }
        if (d.comprobaciones.isNotEmpty()) {
            Hint("QUÉ MIRAR")
            d.comprobaciones.forEach { Text("• $it", fontSize = 13.sp) }
        }
        if (d.recomendacion.isNotBlank()) Text("Recomendación: ${d.recomendacion}", fontSize = 13.sp)
        Hint("Orientativo, generado por tu IA. No sustituye la revisión en taller.")
        if (d.causas.any { it.zona != null }) OutlinedButton(onSee) { Text("Ver en la moto") }
    }
}
