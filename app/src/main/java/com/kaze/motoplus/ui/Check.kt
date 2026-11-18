package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.*
import com.kaze.motoplus.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.domain.*
import kotlinx.coroutines.launch

/** One step of the pre-ride check: where to look on the drawing, what to do, and what the app already knows. */
private data class Step(val id: String, val zone: String?, val title: String, val icon: String, val how: String, val note: String?, val parts: List<String>)

private fun steps(ctx: android.content.Context, vm: AppViewModel, bike: Bike): List<Step> {
    val today = vm.today
    val odo = estimate(bike, today).km
    fun note(vararg ids: String) = ids.mapNotNull { id -> bike.parts.firstOrNull { it.id == id && it.watched } }
        .map { it to partState(it, odo, today, vm.prefs.soon) }.filter { it.second.status != Status.ok }
        .joinToString(ctx.getString(R.string.or)) { (p, s) -> "${partLabel(p, ctx)}: ${remaining(s, ctx)}" }.ifEmpty { null }
    val papers = vm.papers.map { it to docState(it, today, vm.garage.paperReminderDays[it.type] ?: 30) }.filter { it.second.status != Status.ok }
        .joinToString(ctx.getString(R.string.or)) { (d, s) -> "${documentLabel(d, ctx)}: ${documentExpiry(d, s, ctx)}" }.ifEmpty { null }
    return listOfNotNull(
        Step("tires", "front_wheel", ctx.getString(R.string.check_tires), "tire", ctx.getString(R.string.tires_how), note("front_tire", "rear_tire"), listOf("front_tire", "rear_tire")),
        Step("lights", "lights", ctx.getString(R.string.check_lights), "light", ctx.getString(R.string.lights_how), null, listOf("lights_check")),
        when (bike.drive) {
            Drive.chain -> Step("chain", "drivetrain", ctx.getString(R.string.check_chain), "chain", ctx.getString(R.string.chain_how), note("chain_lube", "chain_tension"), listOf("chain_tension", "chain_lube"))
            Drive.belt -> Step("chain", "drivetrain", ctx.getString(R.string.drives_belt), "chain", ctx.getString(R.string.belt_how), note("belt_tension"), listOf("belt_tension"))
            else -> null
        },
        Step("brakes", "front_brake", ctx.getString(R.string.check_brakes), "disc", ctx.getString(R.string.brakes_how), note("front_pads", "rear_pads", "brake_fluid"), listOf("front_pads", "rear_pads", "brake_fluid")),
        Step("oil", "engine", ctx.getString(R.string.check_oil), "oil", ctx.getString(R.string.oil_how), note("engine_oil"), listOf("engine_oil")),
        Step("papers", null, ctx.getString(R.string.check_papers), "doc", ctx.getString(R.string.papers_how), papers, emptyList()),
    ).filter { it.id in vm.checkSteps() }
}

@Composable
fun CheckScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val list = remember(bike, vm.garage.documents, vm.garage.paperReminderDays, vm.prefs, ctx.resources.configuration) { steps(ctx, vm, bike) }
    val results = remember(bike.id) { mutableStateMapOf<String, Boolean>() }
    val pager = rememberPagerState { list.size + 1 }
    val today = vm.today
    val zones = zoneStates(bike, estimate(bike, today).km, today, vm.prefs.soon)
    val plate = rememberPlate(vm.drawingFor(bike))
    fun answer(step: Step, fine: Boolean) {
        haptic.performHapticFeedback(if (fine) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        results[step.id] = fine
        scope.launch { pager.animateScrollToPage(list.indexOf(step) + 1) }
    }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(R.string.before_ride), { vm.screen = Screen.main }) {
            Text(stringResource(R.string.p3_progress, results.size, list.size), style = Mono.copy(fontSize = 13.sp), color = shop.ink2, modifier = Modifier.padding(end = 14.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            list.forEachIndexed { index, s ->
                val c = when (results[s.id]) { true -> shop.ok; false -> shop.bad; null -> shop.line }
                Box(Modifier.weight(1f).height(24.dp).clickable(onClickLabel = s.title) { scope.launch { pager.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(c))
                }
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
        HorizontalPager(pager, Modifier.weight(1f).padding(horizontal = 22.dp).clipToBounds(), verticalAlignment = Alignment.Top, pageSpacing = 10.dp) { i ->
            val step = list.getOrNull(i)
            Sheet(Modifier.verticalScroll(rememberScrollState()), padding = 18.dp) {
                if (step != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(step.icon, shop.accent, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Cap(stringResource(R.string.step, i + 1, list.size))
                            SectionTitle(step.title, 24)
                        }
                        results[step.id]?.let { Cap(if (it) stringResource(R.string.fine) else stringResource(R.string.fails), if (it) shop.ok else shop.bad) }
                    }
                    Text(step.how, style = MaterialTheme.typography.bodyLarge, color = shop.ink)
                    step.note?.let { Row(verticalAlignment = Alignment.CenterVertically) { Icon("warning", shop.warn, 18.dp); Spacer(Modifier.width(8.dp)); Hint(it, shop.warn) } }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(stringResource(R.string.something_wrong), { answer(step, false) }, Modifier.weight(1f), ghost = true)
                        Btn(stringResource(R.string.fine), { answer(step, true) }, Modifier.weight(1f), icon = "check")
                    }
                } else Summary(vm, bike, list, results)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) IconBtn("back", stringResource(R.string.atras), { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (pager.currentPage < list.size) Cap(stringResource(R.string.skip_step)) }
            if (pager.currentPage < list.size) IconBtn("chev", stringResource(R.string.p3_next), { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
        }
    }
}

@Composable
private fun Summary(vm: AppViewModel, bike: Bike, list: List<Step>, results: Map<String, Boolean>) {
    val shop = LocalShop.current
    val failed = list.filter { results[it.id] == false }
    val ok = list.filter { results[it.id] == true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cap(stringResource(R.string.summary))
        if (failed.isEmpty()) {
            SectionTitle(if (ok.size == list.size) stringResource(R.string.all_fine) else stringResource(R.string.no_failures), 20)
            Text(if (ok.size == list.size) stringResource(R.string.good_ride) else if (list.size - ok.size == 1) stringResource(R.string.one_skipped) else stringResource(R.string.skipped, list.size - ok.size), fontSize = 14.sp, color = shop.ink2)
        } else {
            SectionTitle(stringResource(R.string.review_before_ride), 18)
            failed.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(s.icon, shop.bad, 20.dp); Spacer(Modifier.width(10.dp)); Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (results.containsKey("tires") || results.containsKey("lights"))
            Hint(stringResource(R.string.check_counts))
        Spacer(Modifier.height(6.dp))
        val toOrder = failed.flatMap { it.parts }.filter { id -> bike.parts.any { it.id == id } }
        if (toOrder.isNotEmpty() && vm.prefs.on(Feature.workshop)) Btn(stringResource(R.string.add_to_order), { vm.saveCheck(ok.map { it.id }, failed.map { it.id }, toOrder) }, Modifier.fillMaxWidth(), ghost = true, icon = "wrench")
        Btn(stringResource(R.string.finish), { vm.saveCheck(ok.map { it.id }, failed.map { it.id }, emptyList()) }, Modifier.fillMaxWidth(), enabled = results.isNotEmpty())
    }
}
