package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*

@Composable
fun IntervalDialog(part: Part, suggested: Part?, onSave: (Int?, Int?, IntervalUnit, String) -> Unit, onDismiss: () -> Unit) {
    var km by remember(part) { mutableStateOf(part.km?.toString().orEmpty()) }
    var time by remember(part) { mutableStateOf(IntervalInput.from(part)) }
    val kmN = km.toIntOrNull()?.takeIf { it > 0 }
    val days = time.toDays()
    val each = stringResource(when (time.unit) {
        IntervalUnit.days -> R.string.p3_each_days
        IntervalUnit.months -> R.string.p3_each_months
        IntervalUnit.years -> R.string.p3_each_years
    })
    AlertDialog(
        onDismissRequest = onDismiss, title = { SectionTitle(partLabel(part)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(km, stringResource(R.string.p2_each_km), { km = it.filter(Char::isDigit).take(6) }, numeric = true)
                AppInput(time.text, { value ->
                    if (value.matches(Regex("[0-9]{0,5}([.,][0-9]{0,2})?"))) time = time.type(value.replace(',', '.'))
                }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p3_time)) }, placeholder = { Text(each) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                ChoiceRow(IntervalUnit.entries.map { it.name to intervalUnitLabel(it) }, time.unit.name) { time = time.switchTo(IntervalUnit.valueOf(it)) }
                suggested?.let { sp ->
                    Cap(stringResource(R.string.p3_suggested))
                    Row(Modifier.fillMaxWidth().background(LocalShop.current.sheet2).border(1.dp, LocalShop.current.line), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Column(Modifier.weight(1f).padding(12.dp)) {
                            Cap(stringResource(R.string.p3_km))
                            Text(sp.km?.let { stringResource(R.string.p2_km_value, distance(it)) } ?: stringResource(R.string.p3_dash), style = Mono)
                        }
                        VerticalDivider(Modifier.height(64.dp), color = LocalShop.current.line)
                        Column(Modifier.weight(1f).padding(12.dp)) {
                            Cap(stringResource(R.string.p3_time))
                            Text(sp.days?.let { stringResource(R.string.p3_days_value, it) } ?: stringResource(R.string.p3_dash), style = Mono)
                        }
                    }
                    TextButton({ km = sp.km?.toString().orEmpty(); time = IntervalInput.from(sp) }) { Text(stringResource(R.string.p2_use_suggested)) }
                }
            }
        },
        confirmButton = { TextButton({ onSave(kmN, days, time.unit, time.text) }, enabled = (kmN != null || days != null) && (time.text.isBlank() || days != null) && (km.isBlank() || kmN != null)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun intervalUnitLabel(unit: IntervalUnit) = stringResource(when (unit) {
    IntervalUnit.days -> R.string.p2_days; IntervalUnit.months -> R.string.p2_months; IntervalUnit.years -> R.string.p3_years
}).lowercase()
