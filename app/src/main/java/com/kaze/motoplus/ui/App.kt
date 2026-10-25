package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.Tab
import com.kaze.motoplus.domain.Bike
import com.kaze.motoplus.domain.EstimateBasis
import com.kaze.motoplus.domain.estimate
import com.kaze.motoplus.domain.fmtKm
import com.kaze.motoplus.domain.suggested

private val TABS = listOf(Tab.bike to ("MOTO" to "gauge"), Tab.km to ("KM" to "route"), Tab.papers to ("PAPELES" to "doc"), Tab.report to ("IA" to "bulb"))

@Composable
fun App(vm: AppViewModel) {
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
                Screen.data -> DataScreen(vm)
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
                TABS.forEach { (t, l) ->
                    val (label, icon) = l
                    val on = vm.tab == t
                    NavigationBarItem(
                        selected = on, onClick = { vm.tab = t },
                        icon = { Icon(icon, if (on) shop.accent else shop.ink2, 22.dp) },
                        label = { Text(label, style = Caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = shop.accent, unselectedTextColor = shop.ink2, indicatorColor = shop.accent.copy(alpha = 0.16f)),
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
                Tab.report -> ReportScreen(vm, bike)
            }
        }
    }
}

/** "MI MOTO ▾" with the garage menu, and the odometer box. */
@Composable
private fun TopBar(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var open by remember { mutableStateOf(false) }
    val est = estimate(bike, vm.today)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.clickable(onClickLabel = "Cambiar de moto y más opciones") { open = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MI MOTO", style = Display.copy(fontSize = 21.sp), color = shop.ink)
                    Text(" ▾", fontSize = 13.sp, color = shop.ink2)
                }
                Text(bike.title, fontSize = 12.sp, color = shop.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, containerColor = shop.sheet2) {
                vm.garage.bikes.forEach { b ->
                    DropdownMenuItem(text = { Text(b.title, fontWeight = if (b.id == bike.id) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { vm.select(b.id); open = false },
                        leadingIcon = { Box(Modifier.size(10.dp).background(b.accent.color(shop.dark), RoundedCornerShape(5.dp))) })
                }
                HorizontalDivider(color = shop.line)
                MenuItem("wrench", "Plazos de todas las piezas") { vm.screen = Screen.parts; open = false }
                MenuItem("history", "Historial y gastos") { vm.screen = Screen.history; open = false }
                MenuItem("share", "Lista para el taller") { vm.screen = Screen.order; open = false }
                MenuItem("edit", "Editar esta moto") { vm.editingBike = true; open = false }
                MenuItem("cloud", "Datos y copia en Drive") { vm.screen = Screen.data; open = false }
                MenuItem("plus", "Añadir moto") { vm.adding = true; open = false }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                Modifier.background(shop.sheet, RoundedCornerShape(6.dp)).border(1.dp, shop.line, RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(fmtKm(est.km.toInt()), style = Mono.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = shop.ink)
                Text(" KM", style = Mono.copy(fontSize = 10.sp), color = shop.ink2, modifier = Modifier.padding(bottom = 2.dp))
            }
            val exact = est.basis == EstimateBasis.todayReading
            Text(if (exact) "LEÍDO HOY" else "≈ ESTIMADO", style = Caption.copy(fontSize = 9.sp), color = if (exact) shop.ok else shop.ink2, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ShopSnackbar(d: SnackbarData) {
    val shop = LocalShop.current
    Snackbar(d, shape = RoundedCornerShape(6.dp), containerColor = shop.sheet2, contentColor = shop.ink, actionColor = shop.accent,
        modifier = Modifier.padding(12.dp))
}
