package com.kaze.motoplus.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
        SectionTitle("Añade tu moto", 24)
        if (pick == null && !manual) {
            Hint("Busca por marca, modelo o línea de la tarjeta. ${vm.catalog?.brands?.size ?: "…"} marcas en el catálogo.")
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
            Row(
                Modifier.fillMaxWidth().toggleable(allDone, role = androidx.compose.ui.semantics.Role.Checkbox) { allDone = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(allDone, null)
                Text("Acabo de hacerle la revisión completa", fontSize = 14.sp)
            }
            Hint("Si no, todo empieza en rojo como \"sin registro\" y lo vas marcando a medida que lo revisas. Los intervalos son genéricos para este tipo de moto; se pueden ajustar.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Atrás", { pick = null; manual = false }, ghost = true)
            Btn("Guardar moto", { vm.addBike(brand, model, year.toIntOrNull(), category, drive, cooling, km.toInt(), allDone) },
                Modifier.weight(1f), enabled = brand.isNotBlank() && model.isNotBlank() && km.isNotEmpty())
        }
    }
}

@Composable
fun IntervalDialog(part: Part, suggested: Part?, onSave: (Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    val shop = LocalShop.current
    var km by remember { mutableStateOf(part.km?.toString() ?: "") }
    val monthsUnit = (part.days ?: 60) >= 60
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
                        Hint("Sugerido: ${intervalText(sp)}", modifier = Modifier.weight(1f))
                        TextButton({
                            km = sp.km?.toString() ?: ""
                            months = (sp.days ?: 60) >= 60
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
            Btn("Compartir con mi IA", {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, buildPrompt(bike, symptom, vm.today))
                ctx.startActivity(Intent.createChooser(send, "Enviar el informe a tu IA"))
            }, Modifier.fillMaxWidth(), icon = "share")
            Hint("Se abre el menú Compartir: elige ChatGPT, Claude, Gemini o la que uses. Cuando conteste, comparte su respuesta de vuelta a MotoPlus o cópiala y pégala abajo.")
        }
        Sheet {
            SectionTitle("Traer la respuesta")
            OutlinedTextField(answer, { answer = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Respuesta completa o enlace de la IA") }, minLines = 3, maxLines = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Guardar informe", { error = vm.importReport(answer); if (error == null) answer = "" }, Modifier.weight(1f), enabled = answer.isNotBlank())
                Btn("Pegar", {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    answer = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty(); error = null
                }, ghost = true)
            }
            error?.let { Hint(it, shop.bad) }
        }
        bike.reports.reversed().forEach { r -> ReportCard(r) { vm.showZones(r.data.causes.mapNotNull { it.zone }.toSet()) } }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ReportCard(r: SavedReport, onSee: () -> Unit) {
    val shop = LocalShop.current
    val d = r.data
    val (label, c) = when (d.urgency) {
        Level.low -> "Urgencia baja · puedes rodar" to shop.ok
        Level.high -> "Urgencia alta · no ruedes" to shop.bad
        Level.medium -> "Urgencia media · revísalo pronto" to shop.warn
    }
    Sheet {
        Hint("${prettyDate(r.at)} · ${fmtKm(r.km)} km")
        Text(label.uppercase(), style = Caption.copy(fontSize = 11.sp), color = c,
            modifier = Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        SectionTitle(d.title.ifBlank { "Informe" })
        Text(d.summary, fontSize = 13.sp)
        d.causes.forEach { k ->
            HorizontalDivider(color = shop.line)
            Row {
                Column(Modifier.weight(1f)) {
                    Text(k.cause, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    k.zone?.let { Hint(zoneLabel(it)) }
                }
                Text(k.likelihood.label, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
            }
        }
        if (d.checks.isNotEmpty()) {
            Hint("QUÉ MIRAR")
            d.checks.forEach { Text("• $it", fontSize = 13.sp) }
        }
        if (d.advice.isNotBlank()) Text("Recomendación: ${d.advice}", fontSize = 13.sp)
        Hint("Orientativo, generado por tu IA. No sustituye la revisión en taller.")
        if (d.causes.any { it.zone != null }) Btn("Ver en la moto", onSee, ghost = true, icon = "bike")
    }
}
