package com.kaze.motoplus.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.RecordDraft
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun money(v: Double): String = currencyMoney(v, LocalCurrency.current)

// TODO(i18n): phase 2 rewrites history and service recording.

/** One part: its state, its interval and every time it was done. */
@Composable
fun PartScreen(vm: AppViewModel, bike: Bike, partId: String) {
    val shop = LocalShop.current
    val part = bike.parts.firstOrNull { it.id == partId } ?: run { vm.screen = Screen.main; return }
    val state = partState(part, estimate(bike, vm.today).km, vm.today)
    val records = bike.recordsFor(partId)
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(part.name, { vm.screen = vm.back })
        LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Sheet {
                    Cap(zoneLabel(part.zone), shop.status(state.status))
                    PartRow(vm, part, state, showInterval = true)
                    Btn("Registrar con fecha, coste o factura", { vm.newRecord(part.id) }, Modifier.fillMaxWidth(), ghost = true, icon = "calendar")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Historial") }
                    val spent = records.sumOf { it.cost ?: 0.0 }
                    if (spent > 0) Text("Gastado: ${money(spent)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            if (records.isEmpty()) item { Hint("Aún no hay registros. Cada \"Hecho hoy\" o registro queda aquí.") }
            items(records, key = { it.id }) { r -> RecordCard(r, null) { vm.editRecord(r) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Every job on the bike, newest first, with the total spent. */
@Composable
fun HistoryScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val records = bike.history.sortedWith(compareByDescending<ServiceRecord> { it.date }.thenByDescending { it.km })
    val names = bike.parts.associate { it.id to it.name }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header("Historial y gastos", { vm.screen = Screen.main })
        LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Sheet {
                    Cap("Gastado en ${bike.title}")
                    Text(money(bike.totalCost), style = Mono.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
                    Hint("${records.size} ${if (records.size == 1) "registro" else "registros"} · ${records.count { it.photo != null }} con factura")
                }
            }
            if (records.isEmpty()) item { Hint("Todavía no hay mantenimientos registrados.") }
            items(records, key = { it.id }) { r -> RecordCard(r, names[r.partId] ?: r.partName ?: r.partId) { vm.editRecord(r) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RecordCard(r: ServiceRecord, partName: String?, onClick: () -> Unit) {
    val shop = LocalShop.current
    Sheet(Modifier.clickable(onClickLabel = "Editar registro", onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                partName?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                Text("${prettyDate(r.date)} · ${fmtKm(r.km)} km", fontSize = 13.sp, color = if (partName == null) shop.ink else shop.ink2)
                r.cost?.let { Text(money(it), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                if (r.notes.isNotBlank()) Text(r.notes, fontSize = 13.sp, color = shop.ink2)
            }
            r.photo?.let { Thumb(it, Modifier.size(64.dp)) }
        }
    }
}

@Composable
fun Thumb(path: String, modifier: Modifier) {
    val bmp = remember(path) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            val sample = Integer.highestOneBit(maxOf(1, maxOf(o.outWidth, o.outHeight) / 400))
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
    bmp?.let { Image(it, "Factura", modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDialog(vm: AppViewModel, bike: Bike, draft: RecordDraft) {
    val shop = LocalShop.current
    var d by remember(draft.id, draft.partId) { mutableStateOf(draft) }
    var picking by remember { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)?.let { d = d.copy(photo = it) }
    }
    val km = d.km.toIntOrNull()
    val future = LocalDate.parse(d.date).isAfter(vm.today)
    AlertDialog(
        onDismissRequest = { vm.recording = null },
        title = { Text(bike.parts.firstOrNull { it.id == d.partId }?.name ?: "Mantenimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(prettyDate(d.date), { picking = true }, Modifier.fillMaxWidth(), ghost = true, icon = "calendar")
                OutlinedTextField(d.km, { d = d.copy(km = it.filter(Char::isDigit).take(7)) }, Modifier.fillMaxWidth(), label = { Text("Kilómetros") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(d.cost, { d = d.copy(cost = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(9)) }, Modifier.fillMaxWidth(),
                    label = { Text("Coste (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(d.notes, { d = d.copy(notes = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text("Notas (taller, marca, referencia…)") }, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.photo?.let { Thumb(it, Modifier.size(56.dp)) }
                    TextButton({ photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (d.photo == null) "Añadir foto de la factura" else "Cambiar foto")
                    }
                }
                if (future) Hint("La fecha no puede ser futura.", shop.bad)
                if (d.id != null) TextButton({ vm.deleteRecord(d.id!!) }, contentPadding = PaddingValues(0.dp)) { Text("Borrar este registro", color = shop.bad) }
            }
        },
        confirmButton = { TextButton({ vm.saveRecord(d) }, enabled = km != null && !future) { Text("Guardar") } },
        dismissButton = { TextButton({ vm.recording = null }) { Text("Cancelar") } },
    )
    if (picking) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(d.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    ps.selectedDateMillis?.let { d = d.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancelar") } },
        ) { DatePicker(ps) }
    }
}
