package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*

@Composable
fun IntervalDialog(part: Part, suggested: Part?, onSave: (Int?, Int?, IntervalUnit, String) -> Unit, onDismiss: () -> Unit) {
    var km by remember(part) { mutableStateOf(part.km?.toString().orEmpty()) }
    var time by remember(part) { mutableStateOf(IntervalInput.from(part)) }
    val kmN = km.toIntOrNull()?.takeIf { it > 0 }
    val days = time.toDays()
    AlertDialog(
        onDismissRequest = onDismiss, title = { SectionTitle(partLabel(part)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(km, stringResource(R.string.p2_each_km), { km = it.filter(Char::isDigit).take(6) }, numeric = true)
                if (time.unit == IntervalUnit.days) {
                    val wholeDays = time.text.toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)?.toPlainString().orEmpty()
                    IntegerField(wholeDays, { time = time.type(it) }, stringResource(R.string.p3_time), Modifier.fillMaxWidth(), maxDigits = 5)
                } else DecimalField(time.text, { time = time.type(it) }, stringResource(R.string.p3_time), Modifier.fillMaxWidth())
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
                            Text(sp.days?.let { localizedTimeInterval(sp) } ?: stringResource(R.string.p3_dash), style = Mono)
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

/** Use the same inferred/chosen unit in lists, the editor and suggestions. */
@Composable
fun localizedTimeInterval(part: Part): String {
    val input = IntervalInput.from(part)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val quantity = if (input.text.toDoubleOrNull() == 1.0) 1 else 2
    val unit = androidx.compose.ui.res.pluralStringResource(when (input.unit) {
        IntervalUnit.days -> R.plurals.interval_days
        IntervalUnit.months -> R.plurals.interval_months
        IntervalUnit.years -> R.plurals.interval_years
    }, quantity)
    return stringResource(R.string.p3_time_value, formatDecimalInput(input.text, locale), unit)
}
