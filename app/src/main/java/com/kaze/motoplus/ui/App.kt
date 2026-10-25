package com.kaze.motoplus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Tab
import com.kaze.motoplus.domain.suggested

@OptIn(ExperimentalMaterial3Api::class)
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
    if (vm.screen != com.kaze.motoplus.Screen.main) {
        Box(Modifier.fillMaxSize()) {
            when (vm.screen) {
                com.kaze.motoplus.Screen.parts -> PartsScreen(vm, bike)
                com.kaze.motoplus.Screen.part -> PartScreen(vm, bike, vm.openPart ?: "")
                else -> HistoryScreen(vm, bike)
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        return
    }
    Scaffold(
        containerColor = shop.paper,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            var open by remember { mutableStateOf(false) }
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = shop.paper),
                title = {
                    Box {
                        Column(Modifier.clickable { open = true }) {
                            Text("MI MOTO ▾", style = Display.copy(fontSize = 24.sp), color = shop.ink)
                            Text(bike.title, fontSize = 12.sp, color = shop.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(open, onDismissRequest = { open = false }) {
                            vm.garage.bikes.forEach { b ->
                                DropdownMenuItem(text = { Text(b.title) }, onClick = { vm.select(b.id); open = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Piezas y plazos") }, onClick = { vm.screen = com.kaze.motoplus.Screen.parts; open = false })
                            DropdownMenuItem(text = { Text("Historial y gastos") }, onClick = { vm.screen = com.kaze.motoplus.Screen.history; open = false })
                            DropdownMenuItem(text = { Text("Editar o borrar esta moto") }, onClick = { vm.editingBike = true; open = false })
                            DropdownMenuItem(text = { Text("Añadir moto") }, onClick = { vm.adding = true; open = false })
                        }
                    }
                },
                actions = {
                    val est = com.kaze.motoplus.domain.estimate(bike, vm.today)
                    Text(
                        "~${com.kaze.motoplus.domain.fmtKm(est.km.toInt())} km",
                        style = Mono.copy(fontSize = 12.sp), color = shop.paper,
                        modifier = Modifier.padding(end = 12.dp).background(shop.ink, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = shop.sheet) {
                listOf(Tab.moto to "MOTO", Tab.km to "KM", Tab.informe to "INFORME IA").forEach { (t, label) ->
                    NavigationBarItem(
                        selected = vm.tab == t, onClick = { vm.tab = t },
                        icon = { TabIcon(t, if (vm.tab == t) shop.accent else shop.ink2) },
                        label = { Text(label, style = Display.copy(fontSize = 13.sp)) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = shop.accent, indicatorColor = shop.accent.copy(alpha = 0.12f)),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                Tab.moto -> MotoScreen(vm, bike)
                Tab.km -> KmScreen(vm, bike)
                Tab.informe -> ReportScreen(vm, bike)
            }
        }
    }
}

@Composable
private fun TabIcon(t: Tab, c: Color) = Canvas(Modifier.size(24.dp)) {
    val s = Stroke(2.dp.toPx())
    val w = size.width
    when (t) {
        Tab.moto -> {
            drawCircle(c, w * .17f, Offset(w * .22f, w * .66f), style = s)
            drawCircle(c, w * .17f, Offset(w * .78f, w * .66f), style = s)
            drawLine(c, Offset(w * .22f, w * .66f), Offset(w * .42f, w * .4f), s.width)
            drawLine(c, Offset(w * .42f, w * .4f), Offset(w * .62f, w * .4f), s.width)
            drawLine(c, Offset(w * .62f, w * .4f), Offset(w * .78f, w * .66f), s.width)
            drawLine(c, Offset(w * .62f, w * .4f), Offset(w * .7f, w * .2f), s.width)
        }
        Tab.km -> {
            drawArc(c, 180f, 180f, false, Offset(w * .1f, w * .25f), androidx.compose.ui.geometry.Size(w * .8f, w * .8f), style = s)
            drawLine(c, Offset(w * .5f, w * .65f), Offset(w * .7f, w * .42f), s.width)
        }
        Tab.informe -> {
            drawRoundRect(c, Offset(w * .12f, w * .18f), androidx.compose.ui.geometry.Size(w * .76f, w * .52f), androidx.compose.ui.geometry.CornerRadius(3f), style = s)
            drawLine(c, Offset(w * .3f, w * .7f), Offset(w * .2f, w * .88f), s.width)
            drawLine(c, Offset(w * .3f, w * .38f), Offset(w * .7f, w * .38f), s.width)
            drawLine(c, Offset(w * .3f, w * .52f), Offset(w * .55f, w * .52f), s.width)
        }
    }
}

/** A plain section card with the shop's hairline border. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shop = LocalShop.current
    Column(
        modifier.fillMaxWidth().background(shop.sheet, RoundedCornerShape(14.dp)).border(1.dp, shop.line, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) = Text(text.uppercase(), style = Display.copy(fontSize = 19.sp), color = LocalShop.current.ink)

@Composable
fun Hint(text: String, color: Color = LocalShop.current.ink2) = Text(text, fontSize = 12.sp, color = color)

@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (k, label) ->
            FilterChip(selected = k == selected, onClick = { onPick(k) }, label = { Text(label) })
        }
    }
}

@Composable
fun Pill(text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(6.dp))
    Text(text, fontSize = 12.sp)
}
