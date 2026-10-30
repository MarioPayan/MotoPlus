package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.data.Catalog
import com.kaze.motoplus.domain.*
import java.time.LocalDate

// ---------- Onboarding ----------

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
        SectionTitle(stringResource(R.string.add_bike_title), 24)
        if (pick == null && !manual) {
            Hint(stringResource(R.string.catalog_hint, vm.catalog?.brands?.size ?: "…"))
            AppInput(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.catalog_search)) }, singleLine = true)
            val hits = remember(query, vm.catalog) { vm.catalog?.search(query).orEmpty() }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(hits) { h ->
                    Row(Modifier.fillMaxWidth().clickable { choose(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOf(h.brand, h.model.n).joinToString(" "), fontWeight = FontWeight.SemiBold)
                            Hint(listOfNotNull(categoryLabel(h.model.c), h.model.y.takeIf { it.isNotEmpty() }?.let { "${it.min()}–${it.max()}" }).joinToString(stringResource(R.string.or)))
                        }
                        Icon("chev", shop.ink2, 18.dp)
                    }
                    HorizontalDivider(color = shop.line)
                }
                item {
                    TextButton({ manual = true; brand = ""; model = query }) { Text(stringResource(R.string.manual_bike)) }
                }
            }
            if (canCancel) TextButton({ vm.adding = false }) { Text(stringResource(R.string.cancel)) }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (manual) {
                AppInput(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.brand)) }, singleLine = true)
                AppInput(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
            } else {
                Sheet {
                    Text(listOf(brand, model).joinToString(" "), style = Display, color = shop.ink)
                    TextButton({ pick = null }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change_model)) }
                }
            }
            IntegerField(year, { year = it }, stringResource(R.string.year), Modifier.fillMaxWidth(), maxDigits = 4, grouping = false)
            Hint(stringResource(R.string.type))
            ChoiceRow(Category.entries.map { it.name to categoryLabel(it) }, category.name) {
                category = Category.valueOf(it); drive = defaultDrive(category); cooling = defaultCooling(category)
            }
            Hint(stringResource(R.string.transmission))
            ChoiceRow(Drive.entries.map { it.name to driveLabel(it) }, drive.name) { drive = Drive.valueOf(it) }
            Hint(stringResource(R.string.cooling))
            ChoiceRow(Cooling.entries.map { it.name to coolingLabel(it) }, cooling.name) { cooling = Cooling.valueOf(it) }
            IntegerField(km, { km = it }, stringResource(R.string.current_km), Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().toggleable(allDone, role = androidx.compose.ui.semantics.Role.Checkbox) { allDone = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(allDone, null)
                Text(stringResource(R.string.full_service), fontSize = 14.sp)
            }
            Hint(stringResource(R.string.add_hint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(stringResource(R.string.atras), { pick = null; manual = false }, ghost = true)
            Btn(stringResource(R.string.save_bike), { vm.addBike(brand, model, year.toIntOrNull(), category, drive, cooling, km.toInt(), allDone) },
                Modifier.weight(1f), enabled = brand.isNotBlank() && model.isNotBlank() && km.isNotEmpty())
        }
    }
}
