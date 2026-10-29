package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.R
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*

@Composable
fun PartsScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var reset by remember { mutableStateOf(false) }
    val changed = bike.parts.count { p -> bike.suggested(p.id)?.let { it.km != p.km || it.days != p.days } == true }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(R.string.part_limits), { vm.screen = Screen.settings }) {
            if (changed > 0) TextButton({ reset = true }) { Text(stringResource(R.string.p3_reset)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Hint(stringResource(R.string.p3_recommended))
            bike.parts.groupBy { it.zone }.entries.sortedBy { Zone.of(it.key)?.ordinal ?: 99 }.forEach { (zone, parts) ->
                Cap(zoneName(zone))
                parts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { p ->
                            val state = partState(p, estimate(bike, vm.today).km, vm.today)
                            Column(Modifier.weight(1f).border(1.dp, shop.line, RoundedCornerShape(3.dp)).clickable { vm.editing = p.id }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(partIcon(p), shop.status(state.status), 24.dp)
                                Text(partLabel(p), style = MaterialTheme.typography.titleMedium)
                                Hint(localizedInterval(p))
                                LinearProgressIndicator(progress = { state.used.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = shop.status(state.status), trackColor = shop.line)
                                Text(remaining(state), color = shop.status(state.status), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.p3_reset_limits)) },
        text = { Text(stringResource(R.string.p3_reset_text, changed)) },
        confirmButton = { TextButton({ vm.resetIntervals(); reset = false }) { Text(stringResource(R.string.p3_reset)) } },
        dismissButton = { TextButton({ reset = false }) { Text(stringResource(R.string.cancel)) } })
}
