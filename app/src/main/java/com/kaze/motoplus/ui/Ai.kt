package com.kaze.motoplus.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.R
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*

@Composable
fun AiScreen(vm: AppViewModel, bike: Bike) {
    val ctx = LocalContext.current
    val invoice = vm.screen == Screen.invoiceAi
    val error = vm.screen == Screen.aiError
    var disclosure by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var answer by remember(vm.pendingImport) { mutableStateOf(if (error) vm.pendingImport.take(100_000) else "") }
    val prompt = buildPrompt(ctx, bike, vm.aiNotes, vm.today, vm.garage.currency, invoice, vm.maintenance?.takeIf { it.visit }?.let { d ->
        d.checked.map { id -> d.custom.firstOrNull { it.id == id }?.let { id + " · " + it.name } ?: id } })
    WorkshopPage(stringResource(when { error -> R.string.p2_ai_invalid_link; invoice -> R.string.p2_read_invoice_ai; else -> R.string.p2_consult_ai }),
        { vm.screen = if (invoice && vm.maintenance != null) Screen.maintenance else Screen.main }) {
        if (error) {
            Hint(stringResource(vm.aiError ?: R.string.p2_ai_invalid_text), LocalShop.current.bad)
        } else {
            Hint(stringResource(R.string.p2_ai_handoff))
            if (invoice) Hint(stringResource(R.string.p2_ai_invoice_hint))
            else {
                FormField(vm.aiNotes, stringResource(R.string.p2_symptom) + " · " + stringResource(R.string.p2_ai_optional), { vm.aiNotes = it.take(400) }, multiline = true)
                Hint(stringResource(R.string.p2_ai_dictate))
            }
            Btn(stringResource(R.string.p2_share_ai), {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt)
                ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.p2_share_ai)))
                waiting = true
            }, Modifier.fillMaxWidth(), icon = "share")
            TextButton({ disclosure = !disclosure }) { Text(stringResource(R.string.p2_ai_shared_what)) }
            if (disclosure) Sheet {
                Hint(listOf(stringResource(R.string.p2_ai_shared_bike), stringResource(R.string.p2_ai_shared_parts), stringResource(R.string.p2_ai_shared_notes)).joinToString(" · "))
                Hint(stringResource(R.string.p2_ai_no_personal))
                Text(prompt, style = MaterialTheme.typography.bodyMedium)
            }
            if (waiting) {
                SectionTitle(stringResource(R.string.p2_ai_waiting))
                Hint(stringResource(R.string.p2_ai_waiting_hint))
            }
        }
        Sheet {
            SectionTitle(stringResource(R.string.p2_fallback), 23)
            Hint(stringResource(R.string.p2_ai_paste_hint))
            FormField(answer, stringResource(R.string.p2_fallback), { answer = it.take(100_000) }, multiline = true)
            Btn(stringResource(R.string.p2_ai_review_text), { vm.incoming(answer) }, Modifier.fillMaxWidth(), enabled = answer.isNotBlank(), icon = "check")
        }
    }
}

@Composable
private fun levelOptions(urgency: Boolean = false) = Level.entries.map { it.name to stringResource(when (it) {
    Level.low -> if (urgency) R.string.p2_urgency_low else R.string.levels_low
    Level.medium -> if (urgency) R.string.p2_urgency_medium else R.string.levels_medium
    Level.high -> if (urgency) R.string.p2_urgency_high else R.string.levels_high
}) }

@Composable
internal fun IncludeRow(on: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, change)
        Text(stringResource(R.string.p2_ai_keep))
    }
}

@Composable
fun AiReviewScreen(vm: AppViewModel, bike: Bike) {
    val initial = vm.aiReview ?: return
    var d by remember(vm.aiRevision) { mutableStateOf(initial.copy(checks = initial.checks.filter { it.isNotBlank() })) }
    var causes by remember(vm.aiRevision) { mutableStateOf(initial.causes.indices.toSet()) }
    var checks by remember(vm.aiRevision) { mutableStateOf(initial.checks.filter { it.isNotBlank() }.indices.toSet()) }
    var parts by remember(vm.aiRevision) { mutableStateOf(initial.parts.indices.toSet()) }
    var pickParts by remember { mutableStateOf(false) }
    val valid = d.summary.isNotBlank() && d.causes.filterIndexed { i, _ -> i in causes }.all { it.cause.isNotBlank() } && d.checks.filterIndexed { i, _ -> i in checks }.all { it.isNotBlank() }
    WorkshopPage(stringResource(R.string.p2_ai_review), { vm.screen = Screen.ai }, footer = {
        Btn(stringResource(R.string.save), {
            vm.saveReviewedReport(d.copy(v = REPORT_VERSION, causes = d.causes.filterIndexed { i, _ -> i in causes },
                checks = d.checks.filterIndexed { i, _ -> i in checks }, advice = d.advice.trim(), parts = d.parts.filterIndexed { i, _ -> i in parts }.distinct()))
        }, Modifier.fillMaxWidth(), enabled = valid, icon = "check")
    }) {
        knownAiSource(vm.aiSource)?.let { Hint(stringResource(R.string.p2_ai_source, it)) }
        Hint(stringResource(R.string.p2_ai_review_hint))
        FormField(d.title, stringResource(R.string.p2_report), { d = d.copy(title = it.take(100)) })
        FormField(d.summary, stringResource(R.string.p2_ai_diagnosis), { d = d.copy(summary = it.take(1200)) }, multiline = true)
        Cap(stringResource(R.string.p2_ai_urgency))
        ChoiceRow(levelOptions(true), d.urgency.name) { d = d.copy(urgency = Level.valueOf(it)) }
        d.causes.forEachIndexed { i, cause -> Sheet {
            IncludeRow(i in causes) { causes = if (it) causes + i else causes - i }
            FormField(cause.cause, stringResource(R.string.p2_ai_cause), { value -> d = d.copy(causes = d.causes.mapIndexed { j, c -> if (j == i) c.copy(cause = value.take(500)) else c }) }, multiline = true)
            Cap(stringResource(R.string.p2_zone))
            ChoiceRow(listOf("" to stringResource(R.string.p2_general_review)) + Zone.entries.map { it.id to zoneName(it.id) }, cause.zone.orEmpty()) { value -> d = d.copy(causes = d.causes.mapIndexed { j, c -> if (i == j) c.copy(zone = value.ifBlank { null }) else c }) }
            Cap(stringResource(R.string.p2_ai_likelihood))
            ChoiceRow(levelOptions(), cause.likelihood.name) { value -> d = d.copy(causes = d.causes.mapIndexed { j, c -> if (i == j) c.copy(likelihood = Level.valueOf(value)) else c }) }
        } }
        SectionTitle(stringResource(R.string.p2_what_check))
        d.checks.forEachIndexed { i, check -> Sheet {
            IncludeRow(i in checks) { checks = if (it) checks + i else checks - i }
            FormField(check, stringResource(R.string.p2_ai_check), { value -> d = d.copy(checks = d.checks.mapIndexed { j, c -> if (i == j) value.take(500) else c }) }, multiline = true)
        } }
        TextButton({ checks = checks + d.checks.size; d = d.copy(checks = d.checks + "") }, enabled = d.checks.size < 60) { Text(stringResource(R.string.p2_ai_add_check)) }
        FormField(d.advice, stringResource(R.string.p2_ai_advice), { d = d.copy(advice = it.take(800)) }, multiline = true)
        if (d.parts.isNotEmpty()) SectionTitle(stringResource(R.string.p2_ai_pending_parts))
        d.parts.forEachIndexed { i, id -> Sheet {
            IncludeRow(i in parts) { parts = if (it) parts + i else parts - i }
            ChoiceRow(bike.parts.map { it.id to partLabel(it) }, id) { value -> d = d.copy(parts = d.parts.mapIndexed { j, p -> if (j == i) value else p }) }
        } }
        TextButton({ pickParts = true }, enabled = d.parts.size < 60) { Text(stringResource(R.string.p2_ai_add_part)) }
        Hint(stringResource(R.string.p2_report_hint))
    }
    if (pickParts) PartPicker(bike, d.parts.filterIndexed { i, _ -> i in parts }.toSet(), { id ->
        if (id !in d.parts) { parts = parts + d.parts.size; d = d.copy(parts = d.parts + id) }
        else { val index = d.parts.indexOf(id); parts = if (index in parts) parts - index else parts + index }
    }, { pickParts = false })
}

@Composable
internal fun ReportCard(r: SavedReport, onSee: () -> Unit) {
    Sheet {
        Hint(localizedDate(r.at) + " · " + stringResource(R.string.p2_km_value, distance(r.km)))
        knownAiSource(r.source)?.let { Hint(it) }
        SectionTitle(r.data.title.ifBlank { stringResource(R.string.p2_report) })
        Text(r.data.summary)
        Text(levelOptions(true).first { it.first == r.data.urgency.name }.second)
        r.data.causes.forEach { c ->
            Text(c.cause)
            c.zone?.let { Hint(zoneName(it)) }
            Hint(levelOptions().first { it.first == c.likelihood.name }.second)
        }
        r.data.checks.forEach { Text(it) }
        if (r.data.advice.isNotBlank()) Text(r.data.advice)
        Hint(stringResource(R.string.p2_report_hint))
        if (r.data.causes.any { it.zone != null }) Btn(stringResource(R.string.see_bike), onSee, ghost = true, icon = "bike")
    }
}
