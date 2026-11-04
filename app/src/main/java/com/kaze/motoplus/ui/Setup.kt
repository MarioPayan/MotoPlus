package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.R
import com.kaze.motoplus.Screen
import com.kaze.motoplus.data.Catalog
import com.kaze.motoplus.domain.*
import java.time.LocalDate

/**
 * Mi moto: the bike (catalog model, year, type, transmission, cooling) and what to watch. It replaces Límites de las
 * piezas and Editar moto: tapping a part opens its limits, and only parts the rider added can be deleted.
 */
@Composable
fun SetupScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    val s = vm.setup
    var reset by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Part?>(null) }
    var deletingBike by remember { mutableStateOf(false) }
    val odo = estimate(bike, vm.today).km
    val changed = bike.parts.count { p -> bike.suggested(p.id)?.let { it.km != p.km || it.days != p.days } == true }
    val done = { vm.screen = if (s.fromAdd) Screen.main else s.back }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(stringResource(R.string.my_bike), done) {
            if (changed > 0) TextButton({ reset = true }) { Text(stringResource(R.string.p3_reset)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Sheet {
                SectionTitle(stringResource(R.string.setup_bike))
                ModelPicker(vm.catalog, bike.brand, bike.model,
                    onPick = { h -> vm.setModel(h.brand, h.model.n, catalogYear(h), h.model.c) },
                    onManual = { brand, model -> if (brand.isNotBlank() && model.isNotBlank()) vm.setModel(brand, model, null, null) })
                IntegerField(bike.year?.toString().orEmpty(), { vm.setYear(it.toIntOrNull()) }, stringResource(R.string.year), Modifier.fillMaxWidth(), maxDigits = 4, grouping = false)
                Hint(stringResource(R.string.type))
                ChoiceRow(Category.entries.map { it.name to categoryLabel(it) }, bike.category.name) { vm.setCategory(Category.valueOf(it)) }
                Hint(stringResource(R.string.transmission))
                ChoiceRow(Drive.entries.map { it.name to driveLabel(it) }, bike.drive.name) { vm.setDrivetrain(Drive.valueOf(it), bike.cooling) }
                Hint(stringResource(R.string.cooling))
                ChoiceRow(Cooling.entries.map { it.name to coolingLabel(it) }, bike.cooling.name) { vm.setDrivetrain(bike.drive, Cooling.valueOf(it)) }
                Collapse(s.notice != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon("info", shop.warn, 16.dp, modifier = Modifier.padding(top = 3.dp))
                        Spacer(Modifier.width(8.dp))
                        Hint(s.notice.orEmpty(), shop.warn)
                    }
                }
            }
            SectionTitle(stringResource(R.string.setup_watch))
            Hint(stringResource(R.string.setup_watch_hint))
            Column {
                bike.parts.byCategory().forEach { (c, parts) ->
                    WatchCategory(c, parts) { vm.watchCategory(c, it) }
                    parts.forEach { p -> key(p.id) { SetupRow(p, partState(p, odo, vm.today), { vm.editing = p.id }, { vm.watch(p.id, it) }, if (p.isCustom) ({ deleting = p }) else null) } }
                }
            }
            Btn(stringResource(R.string.add_own_part), { adding = true }, Modifier.fillMaxWidth(), ghost = true, icon = "plus")
            TextButton({ deletingBike = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.delete_bike), color = shop.bad) }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().background(shop.paper).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.watch_summary, bike.watchedParts.size, bike.parts.size), style = Mono.copy(fontSize = 12.sp), color = shop.ink2,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (s.fromAdd) Btn(stringResource(R.string.setup_done), done, Modifier.fillMaxWidth())
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.p3_reset_limits)) },
        text = { Text(stringResource(R.string.p3_reset_text, changed)) },
        confirmButton = { TextButton({ vm.resetIntervals(); reset = false }) { Text(stringResource(R.string.p3_reset)) } },
        dismissButton = { TextButton({ reset = false }) { Text(stringResource(R.string.cancel)) } })
    if (adding) CustomPartDialog({ adding = false }) { name, c, km, days, unit, value -> vm.addCustomPart(name, c, km, days, unit, value); adding = false }
    deleting?.let { p -> ConfirmDelete(partLabel(p), { deleting = null }) { vm.removePart(p.id); deleting = null } }
    if (deletingBike) ConfirmDelete(bike.title, { deletingBike = false }) { vm.deleteBike(); deletingBike = false }
}

fun catalogYear(h: Catalog.Hit): Int? = h.model.y.filter { it <= LocalDate.now().year }.maxOrNull()

/** The chosen model with Cambiar modelo, the catalog search, or brand and model typed by hand. Mi moto and the intro share it. */
@Composable
fun ModelPicker(catalog: Catalog?, brand: String, model: String, onPick: (Catalog.Hit) -> Unit, onManual: (String, String) -> Unit) {
    val shop = LocalShop.current
    var stage by rememberSaveable { mutableStateOf(if (model.isBlank()) "search" else "view") }
    var query by rememberSaveable { mutableStateOf("") }
    var typedBrand by rememberSaveable { mutableStateOf(brand) }
    var typedModel by rememberSaveable { mutableStateOf(model) }
    when (stage) {
        "search" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppInput(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.catalog_search)) }, singleLine = true)
            val hits = remember(query, catalog) { catalog?.search(query, 8).orEmpty() }
            hits.forEach { h ->
                Row(Modifier.fillMaxWidth().clickable { onPick(h); stage = "view"; query = "" }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOf(h.brand, h.model.n).joinToString(" "), fontWeight = FontWeight.SemiBold)
                        Hint(listOfNotNull(categoryLabel(h.model.c), h.model.y.takeIf { it.isNotEmpty() }?.let { "${it.min()}–${it.max()}" }).joinToString(stringResource(R.string.or)))
                    }
                    Icon("chev", shop.ink2, 18.dp)
                }
                HorizontalDivider(color = shop.line)
            }
            TextButton({ stage = "manual"; typedBrand = ""; typedModel = query }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.manual_bike)) }
            if (model.isNotBlank()) TextButton({ stage = "view" }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.cancel)) }
        }
        "manual" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppInput(typedBrand, { typedBrand = it; onManual(it, typedModel) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.brand)) }, singleLine = true)
            AppInput(typedModel, { typedModel = it; onManual(typedBrand, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
            TextButton({ stage = "view" }, enabled = typedBrand.isNotBlank() && typedModel.isNotBlank(), contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.setup_done)) }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(listOf(brand, model).joinToString(" "), style = Display.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
            TextButton({ stage = "search" }) { Text(stringResource(R.string.change_model)) }
        }
    }
}

/** A category's switch is on while any of its parts is watched and sets the whole group; [open] makes the whole row expand it, with the intro's chevron. */
@Composable
fun WatchCategory(c: PartCategory, parts: List<Part>, open: Boolean? = null, onOpen: () -> Unit = {}, onWatch: (Boolean) -> Unit) {
    val shop = LocalShop.current
    val on = parts.count { it.watched }
    val label = categoryLabel(c)
    Column {
        val expand = if (open != null) Modifier.clickable(onClickLabel = stringResource(R.string.show_parts), role = Role.Button, onClick = onOpen) else Modifier
        Row(Modifier.fillMaxWidth().then(expand).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(c), shop.ink, 18.dp)
            Spacer(Modifier.width(10.dp))
            Cap(stringResource(R.string.cat_count, label, on, parts.size), shop.ink, Modifier.weight(1f))
            ToggleKnob(on > 0, stringResource(R.string.watch_item, label), onWatch)
            if (open != null) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Chevron(open) }
        }
        HorizontalDivider(color = shop.line)
    }
}

/** Mi moto row: status-coloured icon, interval, a trash can for custom parts and the Vigilar switch. Tapping opens the limits. */
@Composable
private fun SetupRow(p: Part, s: PartState, onOpen: () -> Unit, onWatch: (Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val shop = LocalShop.current
    val name = partLabel(p)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).alpha(if (p.watched) 1f else .5f).clickable(onClickLabel = stringResource(R.string.view_limits), onClick = onOpen).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(partIcon(p), shop.status(if (p.watched) s.status else Status.none), 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (p.watched) "" else stringResource(R.string.unwatched) + " · ") + localizedInterval(p), style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
                }
            }
            onDelete?.let { IconBtn("trash", stringResource(R.string.delete) + " · " + name, it) }
            ToggleKnob(p.watched, stringResource(R.string.watch_item, name), onWatch)
        }
        HorizontalDivider(color = shop.line)
    }
}

/** The intro's row: icon, name and the switch; the whole row toggles. */
@Composable
fun WatchRow(p: Part, s: PartState, onWatch: (Boolean) -> Unit) {
    val shop = LocalShop.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(partIcon(p), shop.status(if (p.watched) s.status else Status.none), 22.dp, modifier = Modifier.alpha(if (p.watched) 1f else .5f))
        Text(partLabel(p), fontSize = 18.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp).alpha(if (p.watched) 1f else .5f))
        ToggleKnob(p.watched, stringResource(R.string.watch_item, partLabel(p)), onWatch)
    }
}

/** Añadir pieza propia: a name, its category and a limit by km, time or both. */
@Composable
private fun CustomPartDialog(onDismiss: () -> Unit, onAdd: (String, PartCategory, Int?, Int?, IntervalUnit, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PartCategory.other) }
    var km by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(IntervalInput(unit = IntervalUnit.months)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_own_part)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(name, stringResource(R.string.part_name), { name = it.take(60) })
            Cap(stringResource(R.string.part_category))
            ChoiceRow(PartCategory.entries.map { it.name to categoryLabel(it) }, category.name) { category = PartCategory.valueOf(it) }
            IntervalFields(km, { km = it }, time, { time = it })
        }
    }, confirmButton = {
        TextButton({ onAdd(name.trim(), category, km.toIntOrNull()?.takeIf { it > 0 }, time.toDays(), time.unit, time.text) },
            enabled = name.isNotBlank() && intervalValid(km, time)) { Text(stringResource(R.string.add_own_part)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}
