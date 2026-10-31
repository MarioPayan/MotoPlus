package com.kaze.motoplus.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import java.util.UUID

@Composable
fun WorkshopTab(vm: AppViewModel, bike: Bike) {
    val count = bike.workshopItems(estimate(bike, vm.today).km, vm.today).count
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sheet {
            SectionTitle(stringResource(R.string.workshop_tab))
            WorkshopRow("list-checks", stringResource(R.string.mt_title), null, hint = stringResource(R.string.mt_hint)) { vm.openMaintenance() }
            WorkshopRow("wrench", stringResource(R.string.p2_workshop_list), count) { vm.screen = Screen.order }
            WorkshopRow("check", stringResource(R.string.p2_from_workshop), count) { vm.openMaintenance(visit = true) }
        }
        if (vm.aiEnabled) Sheet { WorkshopRow("bulb", stringResource(R.string.p2_consult_ai), null, bike.reports.lastOrNull()?.let { localizedDate(it.at) }) { vm.screen = Screen.ai } }
        Sheet { WorkshopRow("history", stringResource(R.string.history), bike.history.size + bike.orders.size + bike.reports.size + bike.fuel.size + bike.rides.size + bike.readings.size + vm.papers.size) { vm.screen = Screen.history } }
        HistoryTimeline(vm, bike, compact = true)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WorkshopRow(icon: String, title: String, count: Int?, subtitle: String? = null, hint: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, LocalShop.current.ink2)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title)
            hint?.let { Hint(it) }
        }
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
    if (numeric) {
        IntegerField(value, onChange, label, Modifier.fillMaxWidth())
        return
    }
    AppInput(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
}

@Composable
fun OrderScreen(vm: AppViewModel, bike: Bike) {
    val open = bike.openOrder()
    val items = bike.workshopItems(estimate(bike, vm.today).km, vm.today)
    var ids by remember(bike.id, open?.id) { mutableStateOf(items.partIds) }
    var custom by remember(bike.id, open?.id) { mutableStateOf(items.customItems) }
    var notes by remember(bike.id, open?.id) { mutableStateOf(open?.notes.orEmpty()) }
    var itemNotes by remember(bike.id, open?.id) { mutableStateOf(open?.itemNotes.orEmpty()) }
    var notesOpen by remember { mutableStateOf(emptySet<String>()) }
    var orderNote by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deletingPart by remember { mutableStateOf<String?>(null) }
    var deletingCustom by remember { mutableStateOf<PendingJob?>(null) }
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
            Btn(stringResource(R.string.p2_from_workshop), { persist(); vm.openMaintenance(visit = true) }, Modifier.fillMaxWidth(), enabled = ids.isNotEmpty() || custom.isNotEmpty())
        }
    }) {
        if (ids.isEmpty() && custom.isEmpty()) Hint(stringResource(R.string.p2_empty_order))
        bike.parts.filter { it.id in ids }.sortedByCategory().map { it.id }.forEach { id -> key(id) {
            val part = bike.parts.firstOrNull { it.id == id }
            if (part != null) Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(partIcon(part), LocalShop.current.ink2)
                    Text(partLabel(part), Modifier.weight(1f).padding(start = 10.dp))
                    IconBtn("note-pencil", stringResource(R.string.p2_add_note), { notesOpen = notesOpen + id })
                    IconBtn("close", stringResource(R.string.delete), { deletingPart = id })
                }
                Collapse(id in notesOpen || !itemNotes[id].isNullOrBlank()) { FormField(itemNotes[id].orEmpty(), stringResource(R.string.p2_job_note), { itemNotes = itemNotes + (id to it.take(400)); persist() }, multiline = true) }
            }
        } }
        custom.forEach { job -> Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("wrench", LocalShop.current.ink2)
                Text(job.name, Modifier.weight(1f).padding(start = 10.dp))
                IconBtn("note-pencil", stringResource(R.string.p2_add_note), { notesOpen = notesOpen + job.id })
                IconBtn("close", stringResource(R.string.delete), { deletingCustom = job })
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
    deletingPart?.let { id ->
        val name = bike.parts.firstOrNull { it.id == id }?.let { partLabel(it) }.orEmpty()
        ConfirmDelete(name, { deletingPart = null }) {
            ids = ids - id; itemNotes = itemNotes - id; persist(); deletingPart = null
        }
    }
    deletingCustom?.let { job -> ConfirmDelete(job.name, { deletingCustom = null }) {
        custom = custom.filterNot { it.id == job.id }; persist(); deletingCustom = null
    } }
    if (picking) PartPicker(bike, ids.toSet(), { id ->
        if (id in ids) { picking = false; deletingPart = id } else { ids = ids + id; persist() }
    }, { picking = false })
    if (adding) NewJobDialog(onDismiss = { adding = false }) { name ->
        custom = custom + PendingJob(UUID.randomUUID().toString(), name)
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
            val groups = bike.parts.filter { partLabel(it).contains(search, ignoreCase = true) }.byCategory()
            if (groups.isEmpty()) Hint(stringResource(R.string.mt_no_matches))
            groups.forEach { (category, parts) ->
                CategoryCap(category)
                parts.forEach { part -> key(part.id) {
                    Row(Modifier.fillMaxWidth().clickable { onToggle(part.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).alpha(if (part.watched) 1f else .5f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(partIcon(part), LocalShop.current.ink2)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(partLabel(part))
                                if (!part.watched) Hint(stringResource(R.string.unwatched))
                            }
                        }
                        Checkbox(part.id in picked, { onToggle(part.id) })
                    }
                } }
            }
            Btn(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun AmountField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: String? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val currency = LocalCurrency.current
    val display = formatAmountInput(value, currency, locale)
    var field by remember { mutableStateOf(TextFieldValue(display, TextRange(display.length))) }
    LaunchedEffect(display) { if (field.text != display) field = TextFieldValue(display, TextRange(display.length)) }
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
    }, modifier, enabled = enabled, label = { Text(label ?: stringResource(R.string.p2_cost)) }, singleLine = true,
        textStyle = Mono.copy(fontSize = 16.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
internal fun NewJobDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.p2_custom_job)) }, text = {
        FormField(name, stringResource(R.string.p2_job_name), { name = it.take(80) })
    }, confirmButton = { TextButton({ onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}
