package com.kaze.motoplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.*

// TODO(i18n): phase 3 rewrites the part-limit editor.
// ---------- 3A: every interval at once, yours next to the recommended one ----------

@Composable
fun PartsScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var reset by remember { mutableStateOf(false) }
    val changed = bike.parts.count { p -> bike.suggested(p.id)?.let { it.km != p.km || it.days != p.days } == true }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header("Plazos", { vm.screen = Screen.main }) {
            if (changed > 0) TextButton({ reset = true }) { Text("RESTABLECER", style = Mono.copy(fontSize = 12.sp), color = shop.accent) }
        }
        Hint(
            "Recomendados para tu tipo de moto (${bike.category.label}, ${bike.drive.label.lowercase()}). Lo que cambies se marca; el manual de tu moto manda.",
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)) {
            Cap("Pieza", modifier = Modifier.weight(1f)); Cap("Tuyo", modifier = Modifier.width(86.dp)); Cap("Recom.", modifier = Modifier.width(78.dp))
        }
        HorizontalDivider(color = shop.line)
        val groups = bike.parts.groupBy { it.zone }.entries.sortedBy { Zone.of(it.key)?.ordinal ?: 99 }
        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (zone, parts) ->
                item(zone) { Cap(zoneLabel(zone), modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp)) }
                items(parts, key = { it.id }) { p ->
                    val rec = bike.suggested(p.id)
                    val diff = rec != null && (rec.km != p.km || rec.days != p.days)
                    Row(
                        Modifier.fillMaxWidth().background(if (diff) shop.accent.copy(alpha = .08f) else shop.paper)
                            .clickable(onClickLabel = "Cambiar plazo") { vm.editing = p.id }.padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(partIcon(p), shop.ink2, 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(intervalCell(p), style = Mono.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = if (diff) shop.accent else shop.ink, modifier = Modifier.width(86.dp))
                        Text(rec?.let(::intervalCell) ?: "—", style = Mono.copy(fontSize = 12.sp), color = shop.ink2, modifier = Modifier.width(78.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (reset) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text("RESTABLECER PLAZOS") },
        text = { Text("Vuelven a los recomendados los $changed plazos que cambiaste. El historial no se toca.") },
        confirmButton = { TextButton({ vm.resetIntervals(); reset = false }) { Text("RESTABLECER") } },
        dismissButton = { TextButton({ reset = false }) { Text("CANCELAR") } },
    )
}

/** "6.000 km" over "12 m", compact for the table. */
private fun intervalCell(p: Part) = listOfNotNull(
    p.km?.let { "${fmtKm(it)} km" },
    p.days?.let { if (it >= 60) "${Math.round(it / 30.4)} m" else "$it d" },
).joinToString("\n").ifEmpty { "—" }

fun intervalText(p: Part) = listOfNotNull(
    p.km?.let { "cada ${fmtKm(it)} km" },
    p.days?.let { if (it >= 60 && it % 30 == 0 || it >= 365) "cada ${Math.round(it / 30.4)} meses" else "cada $it días" },
).joinToString(" o ").ifEmpty { "sin plazo" }
