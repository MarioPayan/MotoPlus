package com.kaze.motoplus.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.*
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*
import java.time.LocalDate
import java.util.UUID

/** Registrar mantenimiento: many jobs at one date and odometer reading. Completar visita is the same screen with the Taller list checked. */
@Composable
fun MaintenanceScreen(vm: AppViewModel, bike: Bike, d: MaintenanceDraft) {
    val shop = LocalShop.current
    val locale = LocalConfiguration.current.locales[0]
    val today = vm.today
    val odo = estimate(bike, today).km
    fun set(f: (MaintenanceDraft) -> MaintenanceDraft) { vm.maintenance = vm.maintenance?.let(f) }
    fun toggle(id: String, on: Boolean) = set { m ->
        if (on) m.copy(checked = (m.checked + id).distinct())
        else m.copy(checked = m.checked - id, custom = m.custom.filterNot { it.id == id && !it.fromOrder })
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)?.let { path -> set { it.copy(photo = path) } }
    }
    val jobs = d.checked.filter { id -> d.custom.any { it.id == id } || bike.parts.any { it.id == id } }
    val km = d.km.toIntOrNull()
    val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
    val valid = jobs.isNotEmpty() && km != null && date != null && !date.isAfter(today) &&
        d.custom.filter { it.id in jobs }.all { it.name.isNotBlank() } &&
        jobs.all { d.costs[it].isNullOrBlank() || parseAmount(d.costs[it]!!, locale) != null }
    val total = jobs.sumOf { id -> d.costs[id]?.let { parseAmount(it, locale) } ?: 0.0 }
    val back = { vm.maintenance = null; vm.screen = d.back }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DraftJob?>(null) }

    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(stringResource(if (d.visit) R.string.p2_from_workshop else R.string.mt_title), back)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().background(shop.sheet, RoundedCornerShape(3.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ServiceDateField(d.date) { value -> set { it.copy(date = value) } } }
                OdometerField(d.km, { value -> set { it.copy(km = value) } }, Modifier.weight(1f))
            }
            if (km != null && suspiciousJump(km, odo)) Hint(stringResource(R.string.jump_hint, distance(km - odo.toInt())), shop.warn)
            Cap(stringResource(R.string.mt_where))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Venue.shop to R.string.mt_shop, Venue.self to R.string.mt_self).forEach { (venue, label) ->
                    Chip(d.venue == venue, stringResource(label)) { set { it.copy(venue = if (it.venue == venue) null else venue) } }
                }
                AppInput(d.place, { value -> set { it.copy(place = value.take(60)) } }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(stringResource(R.string.mt_where_placeholder), maxLines = 1) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (d.visit && vm.aiEnabled) Btn(stringResource(R.string.p2_read_invoice_ai), { vm.screen = Screen.invoiceAi }, Modifier.fillMaxWidth(), ghost = true, icon = "camera")
            if (d.fromInvoice) {
                knownAiSource(d.source)?.let { Hint(stringResource(R.string.p2_ai_source, it)) }
                Hint(stringResource(R.string.p2_ai_invoice_review))
            }
            QuickPicks(bike, d, odo, today, vm.prefs.soon) { ids ->
                set { m -> if (ids.all(m.checked::contains)) m.copy(checked = m.checked - ids.toSet()) else m.copy(checked = (m.checked + ids).distinct()) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cap(stringResource(R.string.mt_jobs), modifier = Modifier.weight(1f))
                Cap(pluralStringResource(R.plurals.mt_checked, jobs.size, jobs.size))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("search", shop.ink2, 20.dp, modifier = Modifier.padding(end = 8.dp))
                FormField(d.query, stringResource(R.string.p2_search_parts), { value -> set { it.copy(query = value.take(80)) } })
            }
            val query = d.query.trim()
            fun matches(name: String) = query.isEmpty() || name.contains(query, ignoreCase = true)
            val groups = bike.parts.filter { matches(partLabel(it)) }.byCategory()
            val custom = d.custom.filter { matches(it.name) }
            if (groups.isEmpty() && custom.isEmpty()) Hint(stringResource(R.string.mt_no_matches))
            groups.forEach { (category, parts) ->
                CategoryCap(category)
                parts.forEach { p -> key(p.id) {
                    val s = partState(p, odo, today, vm.prefs.soon)
                    val status = if (p.watched) s.status else Status.none
                    val on = p.id in d.checked
                    JobRow(d, p.id, on, ::set) {
                        Row(Modifier.weight(1f).fillMaxHeight().toggleable(on, role = Role.Checkbox) { toggle(p.id, it) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, null)
                            Row(Modifier.weight(1f).alpha(if (p.watched) 1f else .5f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.padding(start = 4.dp).size(9.dp).background(shop.status(status), CircleShape))
                                JobText(partLabel(p), if (p.watched) remaining(s) else stringResource(R.string.unwatched), shop.status(status), Modifier.weight(1f).padding(start = 10.dp))
                            }
                        }
                    }
                } }
            }
            if (custom.isNotEmpty()) Cap(stringResource(R.string.p2_custom_job), modifier = Modifier.padding(top = 6.dp))
            custom.forEach { job -> key(job.id) {
                val on = job.id in d.checked
                JobRow(d, job.id, on, ::set) {
                    Checkbox(on, { toggle(job.id, it) })
                    JobText(job.name, stringResource(R.string.p2_custom_job), shop.ink2,
                        Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = stringResource(R.string.p2_job_name)) { renaming = job }.padding(start = 2.dp))
                }
            } }
            Btn(stringResource(R.string.p2_custom_job), { adding = true }, Modifier.fillMaxWidth(), ghost = true, icon = "plus")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.photo?.let { Thumb(it, Modifier.size(56.dp)) }
                Btn(stringResource(if (d.photo == null) R.string.p2_invoice_photo else R.string.p2_invoice_replace),
                    { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, ghost = true, icon = "camera")
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val summary = listOfNotNull(
                pluralStringResource(R.plurals.mt_count, jobs.size, jobs.size),
                date?.let { localizedDate(d.date) },
                stringResource(R.string.p2_km_value, km?.let { distance(it) } ?: "—"),
                total.takeIf { it > 0 }?.let { money(it) },
            ).joinToString(" · ")
            Text(summary, style = Mono.copy(fontSize = 12.sp), color = shop.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Btn(if (jobs.size == 1) stringResource(R.string.p2_save_job) else stringResource(R.string.p2_save_jobs, jobs.size), { vm.saveMaintenance(d) },
                Modifier.fillMaxWidth(), enabled = valid)
        }
    }
    if (adding) NewJobDialog(onDismiss = { adding = false }) { name ->
        val id = UUID.randomUUID().toString()
        set { it.copy(custom = it.custom + DraftJob(id, name), checked = it.checked + id, query = "") }
        adding = false
    }
    renaming?.let { job ->
        NewJobDialog(job.name, { renaming = null }) { name ->
            set { m -> m.copy(custom = m.custom.map { if (it.id == job.id) it.copy(name = name) else it }) }
            renaming = null
        }
    }
    d.noteOpen.firstOrNull()?.let { id ->
        val name = bike.parts.firstOrNull { it.id == id }?.let { partLabel(it) } ?: d.custom.firstOrNull { it.id == id }?.name.orEmpty()
        key(id) {
            JobNoteDialog(name, d.notes[id].orEmpty(), { set { it.copy(noteOpen = it.noteOpen - id) } }) { note ->
                set { it.copy(notes = it.notes + (id to note), noteOpen = it.noteOpen - id) }
            }
        }
    }
}

/** One tap checks a whole set; tapping it again when all of it is checked unchecks it. */
@Composable
private fun QuickPicks(bike: Bike, d: MaintenanceDraft, odo: Double, today: LocalDate, soon: SoonThreshold, onPick: (List<String>) -> Unit) {
    val picks = listOfNotNull(
        if (d.visit) QuickPick.pending to R.string.mt_pending else null,
        QuickPick.overdue to R.string.mt_overdue, QuickPick.soon to R.string.mt_soon,
        QuickPick.oil to R.string.mt_oil, QuickPick.full to R.string.mt_full,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        picks.forEach { (pick, label) ->
            val ids = bike.quickPick(pick, odo, today, soon)
            Chip(ids.isNotEmpty() && ids.all(d.checked::contains), stringResource(label, ids.size), enabled = ids.isNotEmpty()) { onPick(ids) }
        }
    }
}

/**
 * One job. Checking it slides the cost and note in from the right and only narrows [content]: the height is fixed, room for
 * two lines of name and the status line, so the rows below never move.
 */
@Composable
private fun JobRow(d: MaintenanceDraft, id: String, on: Boolean, set: ((MaintenanceDraft) -> MaintenanceDraft) -> Unit, content: @Composable RowScope.() -> Unit) {
    val height = with(LocalDensity.current) { maxOf(56.sp.toDp(), 56.dp) }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = if (LocalCompact.current) 0.dp else 8.dp).height(height), verticalAlignment = Alignment.CenterVertically) {
            content()
            AnimatedVisibility(on, enter = expandHorizontally(motion()) + fadeIn(motion()), exit = shrinkHorizontally(motion()) + fadeOut(motion())) { JobExtra(d, id, set) }
        }
        HorizontalDivider(color = LocalShop.current.line)
    }
}

/** A job's name, at most two lines, over its one-line status. */
@Composable
private fun JobText(name: String, status: String, statusColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 6.dp), verticalArrangement = Arrangement.Center) {
        Text(name, fontSize = 18.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(status, style = Mono.copy(fontSize = 11.sp), color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A checked job's cost and note. The field is wide enough for a seven-figure COP amount at 360 dp and has no label above it, to fit the row. */
@Composable
private fun JobExtra(d: MaintenanceDraft, id: String, set: ((MaintenanceDraft) -> MaintenanceDraft) -> Unit) {
    val shop = LocalShop.current
    val currency = LocalCurrency.current
    val locale = LocalConfiguration.current.locales[0]
    val hasNote = !d.notes[id].isNullOrBlank()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AmountField(d.costs[id].orEmpty(), { value -> set { it.copy(costs = it.costs + (id to value)) } }, Modifier.width(124.dp),
            placeholder = stringResource(R.string.p2_cost) + " · " + currencySymbol(currency, locale))
        IconBtn("note-pencil", stringResource(if (hasNote) R.string.p2_job_note else R.string.p2_add_note), { set { it.copy(noteOpen = it.noteOpen + id) } },
            color = if (hasNote) shop.accent else shop.ink2)
    }
}

@Composable
private fun JobNoteDialog(name: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = LocalShop.current.sheet, title = { Text(name) },
        text = { FormField(note, stringResource(R.string.p2_job_note), { note = it.take(400) }, multiline = true) },
        confirmButton = { TextButton({ onSave(note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}
