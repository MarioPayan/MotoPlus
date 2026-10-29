package com.kaze.motoplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.editingBike = false },
        title = { Text(stringResource(R.string.bike_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.brand)) }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.year)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Hint(stringResource(R.string.bike_meta, categoryLabel(bike.category), driveLabel(bike.drive), coolingLabel(bike.cooling)))
                if (!confirmDelete) TextButton({ confirmDelete = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.delete_bike), color = shop.bad) }
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Hint(stringResource(R.string.delete_bike_text), shop.bad)
                    Button({ vm.deleteBike() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = shop.bad)) { Text(stringResource(R.string.delete_bike_yes, bike.model.uppercase())) }
                }
            }
        },
        confirmButton = { TextButton({ vm.editBike(brand, model, year.toIntOrNull()) }, enabled = brand.isNotBlank() && model.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ vm.editingBike = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
