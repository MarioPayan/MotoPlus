package com.kaze.motoplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.kaze.motoplus.domain.Density
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
    LaunchedEffect(vm.message) {
        vm.message?.let { text ->
            val action = vm.messageAction.also { vm.messageAction = null }
            val result = snack.showSnackbar(text, actionLabel = action?.label, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            vm.message = null
            if (result == SnackbarResult.ActionPerformed) action?.run?.invoke()
        }
    }
    // Hidden features close their screens and tabs: whatever pointed there (a deep link, an old state) shows Home.
    val route: Any = when {
        vm.welcome != null -> "welcome"
        vm.adding || vm.bike == null -> "add"
        else -> if (vm.routeOpen(vm.screen)) vm.screen else Screen.main
    }
    CompositionLocalProvider(LocalCompact provides (vm.prefs.density == Density.compact)) {
        Box(Modifier.fillMaxSize().background(shop.paper)) {
            AnimatedContent(route, transitionSpec = {
                (fadeIn(motion()) + slideInVertically(motion()) { it / 80 }) togetherWith fadeOut(motion())
            }, label = "screen") { r ->
                when (r) {
                    "welcome" -> WelcomeScreen(vm)
                    "add" -> AddBikeScreen(vm, canCancel = vm.bike != null)
                    Screen.main -> vm.bike?.let { MainTabs(vm, it) }
                    else -> vm.bike?.let { bike ->
                        Box(Modifier.fillMaxSize().background(shop.paper)) {
                            when (r as Screen) {
                                Screen.setup -> SetupScreen(vm, bike)
                                Screen.customize -> CustomizeScreen(vm, bike)
                                Screen.part -> PartScreen(vm, bike, vm.openPart ?: "")
                                Screen.zone -> ZoneScreen(vm, bike, vm.selectedZone ?: "engine")
                                Screen.doc -> vm.docDraft?.let { DocScreen(vm, it) } ?: run { vm.screen = Screen.main }
                                Screen.control -> ControlScreen(vm, bike)
                                Screen.fuel -> FuelScreen(vm, bike)
                                Screen.order -> OrderScreen(vm, bike)
                                Screen.maintenance -> vm.maintenance?.let { MaintenanceScreen(vm, bike, it) } ?: run { vm.screen = Screen.main }
                                Screen.ai, Screen.invoiceAi, Screen.aiError -> AiScreen(vm, bike)
                                Screen.aiReview -> AiReviewScreen(vm, bike)
                                Screen.check -> CheckScreen(vm, bike)
                                Screen.settings -> SettingsScreen(vm)
                                Screen.data -> DataScreen(vm)
                                Screen.kmSources -> KmSourcesScreen(vm)
                                Screen.thresholds -> ThresholdsScreen(vm, bike)
                                else -> HistoryScreen(vm, bike)
                            }
                        }
                    }
                }
            }
            val overTabs = route == Screen.main
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (overTabs) tabBarHeight() else 0.dp)) { ShopSnackbar(it) }
            FeedbackButton(if (overTabs) "main/${vm.tab}" else route.toString(), Modifier.align(Alignment.CenterEnd))
        }
        val bike = vm.bike
        if (route is Screen && bike != null) {
            vm.editing?.let { id ->
                bike.parts.firstOrNull { it.id == id }?.let { p ->
                    IntervalDialog(p, bike.suggested(id), onSave = { km, d, unit, value -> vm.setInterval(id, km, d, unit, value) }, onDismiss = { vm.editing = null })
                }
            }
            vm.recording?.let { RecordDialog(vm, bike, it) }
        }
    }
}

@Composable
private fun tabBarHeight() = if (LocalCompact.current) 62.dp else 80.dp

@Composable
private fun MainTabs(vm: AppViewModel, bike: Bike) {
    val tab = if (vm.tabOpen(vm.tab)) vm.tab else Tab.bike
    Column(Modifier.fillMaxSize()) {
        TopBar(vm, bike, tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(tab, transitionSpec = { fadeIn(motion()) togetherWith fadeOut(motion()) }, label = "tab") { t ->
                when (t) {
                    Tab.bike -> BikeScreen(vm, bike)
                    Tab.km -> KmScreen(vm, bike)
                    Tab.papers -> PapersScreen(vm, bike)
                    Tab.report -> WorkshopTab(vm, bike)
                }
            }
        }
        TabBar(vm, tab)
    }
}

/** A tab whose feature is turned off shrinks out and the others take its room; one turned back on grows in. */
@Composable
private fun TabBar(vm: AppViewModel, current: Tab) {
    val shop = LocalShop.current
    Row(Modifier.fillMaxWidth().background(shop.sheet).drawBehind { drawLine(shop.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
        .navigationBarsPadding().height(tabBarHeight()).selectableGroup()) {
        TABS.forEach { (t, label, icon) ->
            val shown by animateFloatAsState(if (vm.tabOpen(t)) 1f else 0f, motion(), label = "tab")
            if (shown > 0.001f) {
                val on = current == t
                val color by animateColorAsState(if (on) shop.accent else shop.ink2, motion(), label = "tabColor")
                val bar by animateColorAsState(if (on) shop.accent else Color.Transparent, motion(), label = "tabBar")
                Column(Modifier.weight(shown).fillMaxHeight().alpha(shown).clipToBounds().selectable(on, role = Role.Tab) { vm.tab = t },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(28.dp).height(2.dp).background(bar))
                    Spacer(Modifier.height(8.dp))
                    Icon(if (on) "$icon-fill" else icon, color, 22.dp)
                    Text(stringResource(label), style = Display.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(vm: AppViewModel, bike: Bike, tab: Tab) {
    val shop = LocalShop.current
    var open by remember { mutableStateOf(false) }
    val est = estimate(bike, vm.today)
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = if (LocalCompact.current) 8.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.switch_bike)) { open = true }) {
                Text(bike.model, style = Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(bike.brand, bike.year?.toString()).joinToString(" · "), style = Mono.copy(fontSize = 10.sp), color = shop.ink2)
            }
            if (tab == Tab.bike && vm.routeOpen(Screen.check)) Box(Modifier.border(1.dp, shop.line, RoundedCornerShape(3.dp))) { IconBtn("list-checks", stringResource(R.string.before_ride), { vm.screen = Screen.check }) }
            Box(Modifier.padding(start = 6.dp).border(1.dp, shop.line, RoundedCornerShape(3.dp))) { IconBtn("settings", stringResource(R.string.settings), { vm.screen = Screen.settings }) }
        }
        Row(Modifier.fillMaxWidth().clickable { vm.screen = Screen.kmSources }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cap(stringResource(if (est.basis == EstimateBasis.todayReading) R.string.read_today else R.string.estimated), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.p2_km_value, distance(est.km.toInt())), style = Mono.copy(fontSize = 14.sp), color = shop.accent)
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
                    IconBtn("edit", stringResource(R.string.my_bike) + " · " + b.title, { vm.openSetup(Screen.main, b.id); open = false })
                }
            }
            SettingsEntry("plus", stringResource(R.string.add_bike)) { vm.adding = true; open = false }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ShopSnackbar(d: SnackbarData) {
    val shop = LocalShop.current
    Snackbar(d, shape = RoundedCornerShape(6.dp), containerColor = shop.sheet2, contentColor = shop.ink, actionColor = shop.accent,
        modifier = Modifier.padding(12.dp))
}
