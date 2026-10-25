package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*
import kotlinx.coroutines.launch

/** One step of the pre-ride check: where to look on the drawing, what to do, and what the app already knows. */
private data class Step(val id: String, val zone: String?, val title: String, val icon: String, val how: String, val note: String?, val parts: List<String>)

private fun steps(vm: AppViewModel, bike: Bike): List<Step> {
    val today = vm.today
    val odo = estimate(bike, today).km
    fun note(vararg ids: String) = ids.mapNotNull { id -> bike.parts.firstOrNull { it.id == id } }
        .map { it to partState(it, odo, today) }.filter { it.second.status != Status.ok }
        .joinToString(" · ") { (p, s) -> "${p.name}: ${shortLeft(s)}" }.ifEmpty { null }
    val papers = vm.papers.map { it to docState(it, today) }.filter { it.second.status != Status.ok }
        .joinToString(" · ") { (d, s) -> "${d.name()}: ${expiryText(s, d).lowercase()}" }.ifEmpty { null }
    return listOfNotNull(
        Step("tires", "front_wheel", "Neumáticos", "tire", "Sin cortes ni bultos. Presión en frío según tu manual o la pegatina del basculante.", note("front_tire", "rear_tire"), listOf("front_tire", "rear_tire")),
        Step("lights", "lights", "Luces", "light", "Cruce, largas, luz de freno con maneta y pedal, e intermitentes.", null, listOf("lights_check")),
        when (bike.drive) {
            Drive.chain -> Step("chain", "drivetrain", "Cadena", "chain", "Que no esté seca ni cuelgue más de 2 o 3 cm en el punto medio.", note("chain_lube", "chain_tension"), listOf("chain_tension", "chain_lube"))
            Drive.belt -> Step("chain", "drivetrain", "Correa", "chain", "Sin grietas, piedras ni dientes rotos.", note("belt_tension"), listOf("belt_tension"))
            else -> null
        },
        Step("brakes", "front_brake", "Frenos", "disc", "Aprieta maneta y pedal: deben estar firmes y sin ruido.", note("front_pads", "rear_pads", "brake_fluid"), listOf("front_pads", "rear_pads", "brake_fluid")),
        Step("oil", "engine", "Aceite", "oil", "Con la moto recta, el nivel entre las dos marcas de la mirilla.", note("engine_oil"), listOf("engine_oil")),
        Step("papers", null, "Papeles", "doc", "Licencia de conducción, licencia de tránsito y SOAT contigo y al día.", papers, emptyList()),
    )
}

@Composable
fun CheckScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val list = remember(bike.id) { steps(vm, bike) }
    val results = remember(bike.id) { mutableStateMapOf<String, Boolean>() }
    val pager = rememberPagerState { list.size + 1 }
    val today = vm.today
    val zones = zoneStates(bike, estimate(bike, today).km, today)
    val plate = rememberPlate(vm.drawingFor(bike))
    fun answer(step: Step, fine: Boolean) {
        haptic.performHapticFeedback(if (fine) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        results[step.id] = fine
        scope.launch { pager.animateScrollToPage(list.indexOf(step) + 1) }
    }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header("Antes de salir", { vm.screen = Screen.main }) {
            Text("${results.size}/${list.size}", style = Mono.copy(fontSize = 13.sp), color = shop.ink2, modifier = Modifier.padding(end = 14.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            list.forEach { s ->
                val c = when (results[s.id]) { true -> shop.ok; false -> shop.bad; null -> shop.line }
                Box(Modifier.weight(1f).height(4.dp).background(c, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
            }
        }
        // The plate, with one numbered point per step; the current one breathes. Tapping a point jumps to its card.
        val pins = list.mapIndexedNotNull { i, s ->
            s.zone?.let { Pin(it, "${i + 1}", results[s.id]?.let { ok -> if (ok) Status.ok else Status.bad }, i == pager.currentPage) }
        }
        BikeCanvas(
            plate, zones, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), pins = pins,
            focus = list.getOrNull(pager.currentPage)?.zone,
            onZone = { z -> list.indexOfFirst { it.zone == z }.takeIf { it >= 0 }?.let { scope.launch { pager.animateScrollToPage(it) } } },
        )
        HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top, contentPadding = PaddingValues(horizontal = 22.dp), pageSpacing = 10.dp) { i ->
            val step = list.getOrNull(i)
            Sheet(padding = 18.dp) {
                if (step != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(step.icon, shop.accent, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Cap("Paso ${i + 1} de ${list.size}")
                            SectionTitle(step.title, 18)
                        }
                        results[step.id]?.let { Cap(if (it) "Bien" else "Falla", if (it) shop.ok else shop.bad) }
                    }
                    Text(step.how, fontSize = 14.sp, color = shop.ink)
                    step.note?.let { Text("⚠ $it", style = Mono.copy(fontSize = 11.sp), color = shop.warn) }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Algo falla", { answer(step, false) }, Modifier.weight(1f), ghost = true)
                        Btn("Bien", { answer(step, true) }, Modifier.weight(1f), icon = "check")
                    }
                } else Summary(vm, bike, list, results)
            }
        }
        Cap("Desliza para saltar un paso", modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
    }
}

@Composable
private fun Summary(vm: AppViewModel, bike: Bike, list: List<Step>, results: Map<String, Boolean>) {
    val shop = LocalShop.current
    val failed = list.filter { results[it.id] == false }
    val ok = list.filter { results[it.id] == true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cap("Resumen")
        if (failed.isEmpty()) {
            SectionTitle(if (ok.size == list.size) "Todo bien" else "Sin fallos", 20)
            Text(if (ok.size == list.size) "Buena ruta." else "Saltaste ${list.size - ok.size} ${if (list.size - ok.size == 1) "paso" else "pasos"}.", fontSize = 14.sp, color = shop.ink2)
        } else {
            SectionTitle("Revisa antes de salir", 18)
            failed.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(s.icon, shop.bad, 20.dp); Spacer(Modifier.width(10.dp)); Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (results.containsKey("tires") || results.containsKey("lights"))
            Hint("Lo que marcaste como bien en neumáticos y luces cuenta como revisado hoy.")
        Spacer(Modifier.height(6.dp))
        val toOrder = failed.flatMap { it.parts }.filter { id -> bike.parts.any { it.id == id } }
        if (toOrder.isNotEmpty()) Btn("Añadir a la lista del taller", { vm.saveCheck(ok.map { it.id }, failed.map { it.id }, toOrder) }, Modifier.fillMaxWidth(), ghost = true, icon = "wrench")
        Btn("Terminar", { vm.saveCheck(ok.map { it.id }, failed.map { it.id }, emptyList()) }, Modifier.fillMaxWidth(), enabled = results.isNotEmpty())
    }
}
