package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.R
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.Tab
import com.kaze.motoplus.domain.Bike
import com.kaze.motoplus.domain.EstimateBasis
import com.kaze.motoplus.domain.estimate
import com.kaze.motoplus.domain.suggested

private val TABS = listOf(
    Triple(Tab.bike, R.string.moto_tab, "bike"), Triple(Tab.km, R.string.km_tab, "route"),
    Triple(Tab.report, R.string.workshop_tab, "wrench"), Triple(Tab.papers, R.string.papers_tab, "doc"),
)

@Composable
fun App(vm: AppViewModel) {
    CompositionLocalProvider(LocalCurrency provides vm.garage.currency) { AppContent(vm) }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    val shop = LocalShop.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    if (vm.adding || vm.bike == null) {
        AddBikeScreen(vm, canCancel = vm.bike != null)
        return
    }
    val bike = vm.bike!!
    vm.editing?.let { id ->
        bike.parts.firstOrNull { it.id == id }?.let { p ->
            IntervalDialog(p, bike.suggested(id), onSave = { km, d -> vm.setInterval(id, km, d) }, onDismiss = { vm.editing = null })
        }
    }
    vm.recording?.let { RecordDialog(vm, bike, it) }
    if (vm.editingBike) BikeDialog(vm, bike)
    if (vm.screen != Screen.main) {
        Box(Modifier.fillMaxSize().background(shop.paper)) {
            when (vm.screen) {
                Screen.parts -> PartsScreen(vm, bike)
                Screen.part -> PartScreen(vm, bike, vm.openPart ?: "")
                Screen.zone -> ZoneScreen(vm, bike, vm.selectedZone ?: "engine")
                Screen.doc -> vm.docDraft?.let { DocScreen(vm, it) } ?: run { vm.screen = Screen.main }
                Screen.control -> ControlScreen(vm, bike)
                Screen.fuel -> FuelScreen(vm, bike)
                Screen.order -> OrderScreen(vm, bike)
                Screen.orderReturn -> OrderReturnScreen(vm, bike)
                Screen.check -> CheckScreen(vm, bike)
                Screen.settings -> SettingsScreen(vm)
                Screen.data -> DataScreen(vm)
                Screen.kmSources -> KmSourcesScreen(vm)
                else -> HistoryScreen(vm, bike)
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { ShopSnackbar(it) }
        }
        return
    }
    Scaffold(
        containerColor = shop.paper,
        snackbarHost = { SnackbarHost(snack) { ShopSnackbar(it) } },
        topBar = { TopBar(vm, bike) },
        bottomBar = {
            NavigationBar(containerColor = shop.sheet, tonalElevation = 0.dp, modifier = Modifier.border(width = 0.5.dp, color = shop.line)) {
                TABS.forEach { (t, label, icon) ->
                    val on = vm.tab == t
                    NavigationBarItem(
                        selected = on, onClick = { vm.tab = t },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(28.dp).height(2.dp).background(if (on) shop.accent else androidx.compose.ui.graphics.Color.Transparent))
                                Spacer(Modifier.height(8.dp))
                                Icon(if (on) "$icon-fill" else icon, if (on) shop.accent else shop.ink2, 22.dp)
                            }
                        },
                        label = { Text(stringResource(label), style = Display.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = shop.accent, unselectedTextColor = shop.ink2, indicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                Tab.bike -> BikeScreen(vm, bike)
                Tab.km -> KmScreen(vm, bike)
                Tab.papers -> PapersScreen(vm, bike)
                Tab.report -> WorkshopTab(vm, bike)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var open by remember { mutableStateOf(false) }
    val est = estimate(bike, vm.today)
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.switch_bike)) { open = true }) {
                Text(bike.model, style = Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(bike.brand, bike.year?.toString()).joinToString(" · "), style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
            }
            if (vm.tab == Tab.bike) Box(Modifier.border(1.dp, shop.line, RoundedCornerShape(3.dp))) { IconBtn("list-checks", stringResource(R.string.before_ride), { vm.screen = Screen.check }) }
            Box(Modifier.padding(start = 6.dp).border(1.dp, shop.line, RoundedCornerShape(3.dp))) { IconBtn("settings", stringResource(R.string.settings), { vm.screen = Screen.settings }) }
        }
        Row(Modifier.fillMaxWidth().clickable { vm.screen = Screen.kmSources }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cap(stringResource(if (est.basis == EstimateBasis.todayReading) R.string.read_today else R.string.estimated), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.km_left, distance(est.km.toInt())), style = Mono.copy(fontSize = 14.sp), color = shop.accent)
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = shop.sheet) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.bikes))
            vm.garage.bikes.forEach { b ->
                val selected = b.id == bike.id
                Row(Modifier.fillMaxWidth().border(1.dp, if (selected) shop.accent else shop.line, RoundedCornerShape(8.dp))
                    .background(if (selected) shop.accent.copy(alpha = .14f) else shop.sheet, RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { vm.select(b.id); open = false }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon("bike", if (selected) shop.accent else shop.ink2)
                        Text(b.title, Modifier.padding(start = 12.dp))
                    }
                    IconBtn("edit", stringResource(R.string.bike_edit_title), { vm.select(b.id); vm.editingBike = true; open = false })
                }
            }
            SettingsEntry("plus", stringResource(R.string.add_bike)) { vm.adding = true; open = false }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Existing workshop, report and history remain reachable until phase 2. */
@Composable
private fun WorkshopTab(vm: AppViewModel, bike: Bike) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val order = bike.orders.firstOrNull { !it.closed }
            Btn(stringResource(if (order == null) R.string.to_workshop else R.string.from_workshop),
                { vm.screen = if (order == null) Screen.order else Screen.orderReturn }, Modifier.weight(1f), ghost = true, icon = "wrench")
            IconBtn("history", stringResource(R.string.history), { vm.screen = Screen.history })
        }
        Box(Modifier.weight(1f)) { ReportScreen(vm, bike) }
    }
}

@Composable
private fun ShopSnackbar(d: SnackbarData) {
    val shop = LocalShop.current
    Snackbar(d, shape = RoundedCornerShape(6.dp), containerColor = shop.sheet2, contentColor = shop.ink, actionColor = shop.accent,
        modifier = Modifier.padding(12.dp))
}
