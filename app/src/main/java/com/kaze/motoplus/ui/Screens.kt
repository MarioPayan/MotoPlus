package com.kaze.motoplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ES = Locale.forLanguageTag("es-ES")
internal fun prettyDate(iso: String) = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))

@Composable
fun IntervalDialog(part: Part, suggested: Part?, onSave: (Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    val shop = LocalShop.current
    var km by remember { mutableStateOf(part.km?.toString() ?: "") }
    val monthsUnit = (part.days ?: 60) >= 60
    var months by remember { mutableStateOf(monthsUnit) }
    var time by remember { mutableStateOf(part.days?.let { if (monthsUnit) Math.round(it / 30.4).toString() else it.toString() } ?: "") }
    val kmN = km.toIntOrNull()?.takeIf { it > 0 }
    val days = time.toIntOrNull()?.takeIf { it > 0 }?.let { if (months) (it * 30.4).toInt() else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(partLabel(part)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(km, { km = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p2_each_km)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text(stringResource(R.string.p2_no_km_limit)) })
                OutlinedTextField(time, { time = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text(stringResource(if (months) R.string.p2_each_months else R.string.p2_each_days)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text(stringResource(R.string.p2_no_time_limit)) })
                ChoiceRow(listOf("m" to stringResource(R.string.p2_months), "d" to stringResource(R.string.p2_days)), if (months) "m" else "d") { months = it == "m" }
                Hint(stringResource(R.string.p2_interval_hint))
                suggested?.takeIf { it.km != kmN || it.days != days }?.let { sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint(stringResource(R.string.p2_suggested, localizedInterval(sp)), modifier = Modifier.weight(1f))
                        TextButton({
                            km = sp.km?.toString() ?: ""
                            months = (sp.days ?: 60) >= 60
                            time = sp.days?.let { if (months) Math.round(it / 30.4).toString() else it.toString() } ?: ""
                        }) { Text(stringResource(R.string.p2_use_suggested)) }
                    }
                }
                if (kmN == null && days == null) Hint(stringResource(R.string.p2_need_interval), shop.bad)
            }
        },
        confirmButton = { TextButton({ onSave(kmN, days) }, enabled = kmN != null || days != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
