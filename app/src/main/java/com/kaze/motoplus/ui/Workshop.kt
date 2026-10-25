package com.kaze.motoplus.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*
import kotlinx.coroutines.launch
import java.io.File

// ---------- 6A: the work order ----------

@Composable
fun OrderScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val today = vm.today
    val odo = estimate(bike, today).km
    val open = bike.openOrder()
    val states = bike.parts.associate { it.id to partState(it, odo, today) }
    var picked by remember { mutableStateOf(open?.partIds ?: bike.dueForWorkshop(odo, today)) }
    var notes by remember { mutableStateOf(open?.notes ?: "") }
    var more by remember { mutableStateOf(false) }
    val number = open?.number ?: bike.nextOrderNumber()
    val plate = vm.garage.documents.firstOrNull { it.bikeId == bike.id && it.type == DocType.transit_license }?.number
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header("Para el taller", { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The order as the mechanic sees it; this panel is also the shared image.
            Column(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                Sheet(padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Cap("Orden de trabajo")
                            Text("Nº %04d".format(number), style = Mono.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Cap(prettyDate(open?.date ?: today.toString()))
                            Text(listOfNotNull(bike.model, plate).joinToString(" · "), fontSize = 12.sp, color = shop.ink)
                            Text("${fmtKm(open?.km ?: odo.toInt())} km", style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
                        }
                    }
                    HorizontalDivider(color = shop.line)
                    picked.mapNotNull { id -> bike.parts.firstOrNull { it.id == id } }.forEach { p ->
                        val st = states.getValue(p.id)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).border(1.5.dp, shop.ink2, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Icon(partIcon(p), shop.status(st.status), 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Cap(orderWord(st.status), shop.status(st.status))
                        }
                    }
                    if (picked.isEmpty()) Hint("Nada pendiente. Añade lo que quieras revisar.", modifier = Modifier.padding(14.dp))
                    if (notes.isNotBlank()) {
                        HorizontalDivider(color = shop.line)
                        Column(Modifier.padding(14.dp)) { Cap("Notas para el mecánico"); Text(notes, fontSize = 12.sp, color = shop.ink2) }
                    }
                }
            }
            Box {
                Btn("Añadir o quitar trabajos", { more = true }, Modifier.fillMaxWidth(), ghost = true, icon = "plus")
                DropdownMenu(more, { more = false }, containerColor = shop.sheet2, modifier = Modifier.heightIn(max = 420.dp)) {
                    bike.parts.sortedByDescending { states.getValue(it.id).used }.forEach { p ->
                        val on = p.id in picked
                        DropdownMenuItem(
                            text = { Text(p.name, style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Checkbox(on, null) },
                            onClick = { picked = if (on) picked - p.id else picked + p.id },
                        )
                    }
                }
            }
            OutlinedTextField(notes, { notes = it.take(400) }, Modifier.fillMaxWidth(), label = { Text("Notas para el mecánico") }, minLines = 2, maxLines = 4)
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Enviar", {
                val o = vm.saveOrder(picked, notes) ?: return@Btn
                scope.launch { shareOrder(ctx, orderText(bike, o, plate, states), runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()) }
            }, Modifier.weight(1f), ghost = true, icon = "share", enabled = picked.isNotEmpty())
            Btn("Volví del taller", { vm.saveOrder(picked, notes); vm.screen = Screen.orderReturn }, Modifier.weight(1f), enabled = picked.isNotEmpty())
        }
    }
}

private fun orderWord(s: Status) = when (s) { Status.bad -> "Vencido"; Status.warn -> "Pronto"; Status.none -> "Revisar"; Status.ok -> "Al día" }

fun orderText(bike: Bike, o: WorkOrder, plate: String?, states: Map<String, PartState>) = buildString {
    appendLine("Orden de trabajo nº ${o.number} · ${listOfNotNull(bike.title, plate).joinToString(" · ")} · ${fmtKm(o.km)} km")
    o.partIds.mapNotNull { id -> bike.parts.firstOrNull { it.id == id } }.forEach { p ->
        appendLine("☐ ${p.name} (${orderWord(states[p.id]?.status ?: Status.none).lowercase()})")
    }
    if (o.notes.isNotBlank()) appendLine("Notas: ${o.notes}")
    append("Enviado desde MotoPlus")
}

/** Shares the order as text plus, when it could be captured, a picture of it. WhatsApp shows both. */
private fun shareOrder(ctx: Context, text: String, image: android.graphics.Bitmap?) {
    val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
    if (image != null) {
        val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "orden.png")
        f.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        send.setType("image/png").putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else send.setType("text/plain")
    ctx.startActivity(Intent.createChooser(send, "Enviar la orden al taller"))
}

// ---------- Back from the workshop ----------

@Composable
fun OrderReturnScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val o = bike.openOrder() ?: run { vm.screen = Screen.main; return }
    val parts = o.partIds.mapNotNull { id -> bike.parts.firstOrNull { it.id == id } }
    var done by remember(o.id) { mutableStateOf(parts.map { it.id }.toSet()) }
    val costs = remember(o.id) { mutableStateMapOf<String, String>() }
    var photo by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importPhoto(uri)?.let { photo = it } }
    fun cost(id: String) = costs[id]?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()
    val total = done.sumOf { cost(it) ?: 0.0 }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header("Volví del taller", { vm.screen = Screen.order })
        Hint("Marca lo que te hicieron y lo que costó. Se guarda en el historial de cada pieza.", modifier = Modifier.padding(horizontal = 18.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            parts.forEach { p ->
                val on = p.id in done
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { done = if (it) done + p.id else done - p.id })
                    Icon(partIcon(p), shop.ink2, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, fontSize = 13.sp, color = if (on) shop.ink else shop.ink2, modifier = Modifier.weight(1f))
                    OutlinedTextField(costs[p.id] ?: "", { costs[p.id] = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10) },
                        Modifier.width(120.dp), enabled = on, singleLine = true, placeholder = { Text("$", style = Mono) },
                        textStyle = Mono.copy(fontSize = 13.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                HorizontalDivider(color = shop.line)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cap("Total", modifier = Modifier.weight(1f))
                Text(money(total), style = Mono.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photo?.let { Thumb(it, Modifier.size(56.dp)) }
                Btn(if (photo == null) "Foto de la factura" else "Cambiar factura", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, ghost = true, icon = "camera")
            }
        }
        Btn(
            if (done.size == 1) "Guardar 1 trabajo" else "Guardar ${done.size} trabajos",
            { vm.closeOrder(done.associateWith { cost(it) }, photo) },
            Modifier.fillMaxWidth().padding(12.dp), enabled = done.isNotEmpty(),
        )
    }
}
