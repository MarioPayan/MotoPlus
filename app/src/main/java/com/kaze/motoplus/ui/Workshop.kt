package com.kaze.motoplus.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun WorkshopTab(vm: AppViewModel, bike: Bike) {
    val count = bike.openOrder()?.let { it.partIds.size + it.customItems.size } ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sheet {
            SectionTitle(stringResource(R.string.workshop_tab))
            WorkshopRow("wrench", stringResource(R.string.p2_workshop_list), count) { vm.screen = Screen.order }
            WorkshopRow("check", stringResource(R.string.p2_from_workshop), count) { vm.startVisit() }
        }
        if (vm.aiEnabled) Sheet { WorkshopRow("bulb", stringResource(R.string.p2_consult_ai), null, bike.reports.lastOrNull()?.let { localizedDate(it.at) }) { vm.screen = Screen.ai } }
        Sheet { WorkshopRow("history", stringResource(R.string.history), bike.history.size + bike.orders.size + bike.reports.size + bike.fuel.size + bike.rides.size + bike.readings.size + vm.papers.size) { vm.screen = Screen.history } }
        HistoryTimeline(vm, bike, compact = true)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WorkshopRow(icon: String, title: String, count: Int?, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, LocalShop.current.ink2)
        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp))
        if (count != null) Text(count.toString(), style = Mono, color = LocalShop.current.ink2)
        subtitle?.let { Hint(it) }
        Spacer(Modifier.width(12.dp))
        Icon("chev", LocalShop.current.ink2, 18.dp)
    }
}

@Composable
internal fun WorkshopPage(title: String, onBack: () -> Unit, footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalShop.current.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { footer() }
    }
}

@Composable
internal fun FormField(value: String, label: String, onChange: (String) -> Unit, numeric: Boolean = false, multiline: Boolean = false) {
    AppInput(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        textStyle = if (numeric) Mono.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyLarge,
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
}

@Composable
fun OrderScreen(vm: AppViewModel, bike: Bike) {
    val open = bike.openOrder()
    var ids by remember(bike.id, open?.id) { mutableStateOf(open?.partIds ?: bike.dueForWorkshop(estimate(bike, vm.today).km, vm.today)) }
    var custom by remember(bike.id, open?.id) { mutableStateOf(open?.customItems.orEmpty()) }
    var notes by remember(bike.id, open?.id) { mutableStateOf(open?.notes.orEmpty()) }
    var itemNotes by remember(bike.id, open?.id) { mutableStateOf(open?.itemNotes.orEmpty()) }
    var notesOpen by remember { mutableStateOf(emptySet<String>()) }
    var orderNote by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    fun persist() = vm.saveOrder(ids, notes, custom, itemNotes)
    WorkshopPage(stringResource(R.string.p2_workshop_list), { persist(); vm.screen = Screen.main }, footer = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(stringResource(R.string.p2_send_order), {
                persist()
                val text = buildString {
                    appendLine(ctx.getString(R.string.p2_workshop_list) + " · " + bike.title)
                    ids.forEach { id -> bike.parts.firstOrNull { it.id == id }?.let { appendLine("☐ " + partLabel(it, ctx)); itemNotes[id]?.let(::appendLine) } }
                    custom.forEach { appendLine("☐ " + it.name); if (it.note.isNotBlank()) appendLine(it.note) }
                    if (notes.isNotBlank()) appendLine(notes)
                }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), ctx.getString(R.string.p2_send_order)))
            }, Modifier.fillMaxWidth(), ghost = true, enabled = ids.isNotEmpty() || custom.isNotEmpty(), icon = "share")
            Btn(stringResource(R.string.p2_from_workshop), { persist(); vm.startVisit() }, Modifier.fillMaxWidth(), enabled = ids.isNotEmpty() || custom.isNotEmpty())
        }
    }) {
        if (ids.isEmpty() && custom.isEmpty()) Hint(stringResource(R.string.p2_empty_order))
        ids.forEach { id ->
            val part = bike.parts.firstOrNull { it.id == id }
            if (part != null) Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(partIcon(part), LocalShop.current.ink2)
                    Text(partLabel(part), Modifier.weight(1f).padding(start = 10.dp))
                    IconBtn("note-pencil", stringResource(R.string.p2_add_note), { notesOpen = notesOpen + id })
                    IconBtn("close", stringResource(R.string.delete), { ids = ids - id; itemNotes = itemNotes - id; persist() })
                }
                if (id in notesOpen || !itemNotes[id].isNullOrBlank()) FormField(itemNotes[id].orEmpty(), stringResource(R.string.p2_job_note), { itemNotes = itemNotes + (id to it.take(400)); persist() }, multiline = true)
            }
        }
        custom.forEach { job -> Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("wrench", LocalShop.current.ink2)
                Text(job.name, Modifier.weight(1f).padding(start = 10.dp))
                IconBtn("note-pencil", stringResource(R.string.p2_add_note), { notesOpen = notesOpen + job.id })
                IconBtn("close", stringResource(R.string.delete), { custom = custom.filterNot { it.id == job.id }; persist() })
            }
            if (job.id in notesOpen || job.note.isNotBlank()) FormField(job.note, stringResource(R.string.p2_job_note), { value -> custom = custom.map { if (it.id == job.id) it.copy(note = value.take(400)) else it }; persist() }, multiline = true)
        } }
        Btn(stringResource(R.string.p2_choose_jobs), { picking = true }, Modifier.fillMaxWidth(), ghost = true, icon = "plus")
        Btn(stringResource(R.string.p2_custom_job), { adding = true }, Modifier.fillMaxWidth(), ghost = true, icon = "wrench")
        if (orderNote || notes.isNotBlank()) FormField(notes, stringResource(R.string.p2_mechanic_notes), { notes = it.take(400); persist() }, multiline = true)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn("note-pencil", stringResource(R.string.p2_add_note), { orderNote = true })
            Hint(stringResource(R.string.p2_notes_label))
        }
        Btn(stringResource(R.string.save), { persist(); vm.screen = Screen.main; vm.message = ctx.getString(R.string.p2_pending_saved) }, Modifier.fillMaxWidth(), ghost = true)
    }
    if (picking) PartPicker(bike, ids.toSet(), { id -> ids = if (id in ids) ids - id else ids + id; persist() }, { picking = false })
    if (adding) NewJobDialog(bike, ids.toSet(), allowParts = false, onDismiss = { adding = false }) { id, name, _ ->
        custom = custom + PendingJob(id ?: UUID.randomUUID().toString(), name)
        persist(); adding = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartPicker(bike: Bike, picked: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalShop.current.sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.p2_choose_jobs))
            FormField(search, stringResource(R.string.p2_search_parts), { search = it.take(80) })
            bike.parts.forEach { part ->
                val label = partLabel(part)
                if (label.contains(search, ignoreCase = true)) Row(Modifier.fillMaxWidth().clickable { onToggle(part.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(partIcon(part), LocalShop.current.ink2)
                    Text(label, Modifier.weight(1f).padding(start = 12.dp))
                    Checkbox(part.id in picked, { onToggle(part.id) })
                }
            }
            Btn(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun AmountField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val locale = LocalConfiguration.current.locales[0]
    val currency = LocalCurrency.current
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) { if (field.text != value) field = TextFieldValue(value, TextRange(value.length)) }
    AppInput(field, { next ->
        val formatted = formatAmountInput(next.text, currency, locale)
        val significant = next.text.take(next.selection.end).count { it.isDigit() || it == java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator }
        var seen = 0
        val cursor = formatted.indexOfFirst { ch ->
            if (ch.isDigit() || ch == java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator) seen++
            seen >= significant
        }.let { if (significant == 0) 0 else if (it < 0) formatted.length else it + 1 }
        field = TextFieldValue(formatted, TextRange(cursor))
        onChange(formatted)
    }, modifier, enabled = enabled, label = { Text(stringResource(R.string.p2_cost)) }, singleLine = true,
        textStyle = Mono.copy(fontSize = 16.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
internal fun NewJobDialog(bike: Bike, excluded: Set<String>, allowParts: Boolean = true, onDismiss: () -> Unit, onAdd: (String?, String, Double?) -> Unit) {
    var id by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (allowParts) R.string.p2_extra_work else R.string.p2_custom_job)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (allowParts) {
                Cap(stringResource(R.string.p2_existing_part))
                ChoiceRow(listOf("" to stringResource(R.string.p2_free_work)) + bike.parts.filter { it.id !in excluded }.map { it.id to partLabel(it) }, id.orEmpty()) { id = it.ifEmpty { null } }
            }
            if (id == null) FormField(name, stringResource(R.string.p2_job_name), { name = it.take(80) })
            if (allowParts) AmountField(amount, { amount = it }, Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton({ onAdd(id, name.trim(), parseAmount(amount, locale)) }, enabled = id != null || name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun OrderReturnScreen(vm: AppViewModel, bike: Bike) {
    val d = vm.visitDraft ?: return
    val locale = LocalConfiguration.current.locales[0]
    val currency = LocalCurrency.current
    val costs = remember(d.sessionId) { mutableStateMapOf<String, String>().apply { d.costs.forEach { (id, n) -> put(id, n?.let { java.text.NumberFormat.getNumberInstance(locale).format(it) }.orEmpty()) } } }
    var extra by remember(d.sessionId) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importPhoto(uri)?.let { vm.visitDraft = vm.visitDraft?.copy(photo = it) } }
    val parsedDate = runCatching { LocalDate.parse(d.date) }.getOrNull()
    val valid = parsedDate != null && !parsedDate.isAfter(vm.today) && d.km.toIntOrNull()?.let { it >= 0 } == true && d.done.isNotEmpty() && d.customItems.filter { it.id in d.done }.all { it.name.isNotBlank() } && d.done.all { costs[it].isNullOrBlank() || parseAmount(costs[it]!!, locale) != null }
    val jobs = d.partIds.mapNotNull { id -> bike.parts.firstOrNull { it.id == id }?.let { id to partLabel(it) } } + d.customItems.map { it.id to it.name }
    WorkshopPage(stringResource(R.string.p2_from_workshop), { vm.screen = Screen.main }, footer = {
        Btn(stringResource(if (d.done.size == 1) R.string.p2_save_job else R.string.p2_save_jobs, d.done.size), { vm.saveVisit(d) }, Modifier.fillMaxWidth(), enabled = valid)
    }) {
        if (vm.aiEnabled) Btn(stringResource(R.string.p2_read_invoice_ai), { vm.screen = Screen.invoiceAi }, Modifier.fillMaxWidth(), ghost = true, icon = "camera")
        knownAiSource(d.source)?.let { Hint(stringResource(R.string.p2_ai_source, it)) }
        Hint(stringResource(if (d.source != null || d.costs.isNotEmpty()) R.string.p2_ai_invoice_review else R.string.p2_return_hint))
        jobs.forEach { (id, name) ->
            val on = id in d.done
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { checked -> vm.visitDraft = d.copy(done = if (checked) d.done + id else d.done - id) })
                    Text(name, Modifier.weight(1f))
                    AmountField(costs[id].orEmpty(), { value -> costs[id] = value; vm.visitDraft = d.copy(costs = d.costs + (id to parseAmount(value, locale))) }, Modifier.width(140.dp), enabled = on)
                }
                d.customItems.firstOrNull { it.id == id }?.let { job ->
                    FormField(job.name, stringResource(R.string.p2_job_name), { value -> vm.visitDraft = d.copy(customItems = d.customItems.map { if (it.id == id) it.copy(name = value.take(80)) else it }) })
                }
                val note = bike.openOrder()?.itemNotes?.get(id) ?: d.customItems.firstOrNull { it.id == id }?.note
                if (!note.isNullOrBlank()) Hint(note)
            }
        }
        Btn(stringResource(R.string.p2_extra_work), { extra = true }, Modifier.fillMaxWidth(), ghost = true, icon = "plus")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cap(stringResource(R.string.p2_total), modifier = Modifier.weight(1f))
            Text(currencyMoney(d.done.sumOf { d.costs[it] ?: 0.0 }, currency), style = Mono.copy(fontSize = 24.sp))
        }
        ServiceDateField(d.date) { vm.visitDraft = d.copy(date = it) }
        FormField(d.km, stringResource(R.string.odo_km), { vm.visitDraft = d.copy(km = it.filter(Char::isDigit).take(7)) }, numeric = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            d.photo?.let { Thumb(it, Modifier.size(56.dp)) }
            Btn(stringResource(if (d.photo == null) R.string.p2_invoice_photo else R.string.p2_invoice_replace), { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, ghost = true, icon = "camera")
        }
        if (!valid) Hint(stringResource(R.string.p2_amount_invalid))
    }
    if (extra) NewJobDialog(bike, d.partIds.toSet(), onDismiss = { extra = false }) { partId, name, amount ->
        val id = partId ?: UUID.randomUUID().toString()
        if (partId == null) vm.visitDraft = d.copy(customItems = d.customItems + PendingJob(id, name), done = d.done + id, costs = d.costs + (id to amount))
        else vm.visitDraft = d.copy(partIds = d.partIds + partId, done = d.done + partId, costs = d.costs + (id to amount))
        costs[id] = amount?.let { java.text.NumberFormat.getNumberInstance(locale).format(it) }.orEmpty()
        extra = false
    }
}
