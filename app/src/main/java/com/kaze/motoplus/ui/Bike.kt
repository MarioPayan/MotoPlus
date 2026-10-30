package com.kaze.motoplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.R
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.domain.*

@Composable
fun BikeDialog(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var brand by remember { mutableStateOf(bike.brand) }
    var model by remember { mutableStateOf(bike.model) }
    var year by remember { mutableStateOf(bike.year?.toString() ?: "") }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.editingBike = false },
        title = { Text(stringResource(R.string.bike_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppInput(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.catalog_search)) }, singleLine = true)
                val hits = remember(query, vm.catalog) { vm.catalog?.search(query, 5).orEmpty() }
                hits.forEach { hit ->
                    TextButton({ brand = hit.brand; model = hit.model.n; query = "" }) { Text(listOf(hit.brand, hit.model.n).joinToString(" ")) }
                }
                AppInput(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.brand)) }, singleLine = true)
                AppInput(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
                IntegerField(year, { year = it }, stringResource(R.string.year), Modifier.fillMaxWidth(), maxDigits = 4, grouping = false)
                Hint(stringResource(R.string.bike_meta, categoryLabel(bike.category), driveLabel(bike.drive), coolingLabel(bike.cooling)))
                TextButton({ confirmDelete = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.delete_bike), color = shop.bad) }
            }
        },
        confirmButton = { TextButton({ vm.editBike(brand, model, year.toIntOrNull()) }, enabled = brand.isNotBlank() && model.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ vm.editingBike = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDelete) ConfirmDelete(bike.title, { confirmDelete = false }) { vm.deleteBike(); confirmDelete = false }
}
