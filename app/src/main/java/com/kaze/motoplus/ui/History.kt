package com.kaze.motoplus.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun money(v: Double): String = currencyMoney(v, LocalCurrency.current)

@Composable
fun PartScreen(vm: AppViewModel, bike: Bike, partId: String) {
    val part = bike.parts.firstOrNull { it.id == partId } ?: return
    val records = bike.recordsFor(partId)
    WorkshopPage(partLabel(part), { vm.screen = vm.back }) {
        Sheet {
            Cap(zoneName(part.zone))
            PartRow(vm, part, partState(part, estimate(bike, vm.today).km, vm.today), showInterval = true)
            Btn(stringResource(R.string.p2_record_with_details), { vm.newRecord(part.id) }, Modifier.fillMaxWidth(), ghost = true, icon = "calendar")
        }
        SectionTitle(stringResource(R.string.history))
        Text(money(records.sumOf { it.cost ?: 0.0 }), style = Mono)
        if (records.isEmpty()) Hint(stringResource(R.string.p2_empty_history))
        records.forEach { r -> RecordCard(r, partLabel(part)) { vm.editRecord(r) } }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, bike: Bike) {
    WorkshopPage(stringResource(R.string.history), { vm.screen = Screen.main }) {
        Cap(stringResource(R.string.p2_spent_bike, bike.title))
        Text(money(bike.totalCost), style = Mono.copy(fontSize = 29.sp, fontWeight = FontWeight.SemiBold))
        HistoryTimeline(vm, bike)
    }
}

private data class HistoryEvent(val date: String, val icon: String, val title: String, val sub: String, val group: List<ServiceRecord> = emptyList(), val open: () -> Unit)

/** Month headings and a continuous rule match the prototype's event timeline. */
@Composable
internal fun HistoryTimeline(vm: AppViewModel, bike: Bike, compact: Boolean = false) {
    var orderDetail by remember { mutableStateOf<WorkOrder?>(null) }
    var reportDetail by remember { mutableStateOf<SavedReport?>(null) }
    var eventDetail by remember { mutableStateOf<HistoryEvent?>(null) }
    var openGroups by remember { mutableStateOf(emptySet<String>()) }
    val locale = LocalConfiguration.current.locales[0]
    val records = bike.history.filter { it.group == null }.map { r ->
        HistoryEvent(r.date, "wrench", recordName(bike, r), stringResource(R.string.p2_km_value, distance(r.km)) + (r.cost?.let { " · " + money(it) } ?: "")) { vm.editRecord(r) }
    } + bike.history.filter { it.group != null }.groupBy { it.group!! }.map { (id, rs) ->
        val first = rs.first()
        val where = listOfNotNull(first.venue?.let { stringResource(if (it == Venue.shop) R.string.mt_shop else R.string.mt_self) }, first.place.ifBlank { null })
        val sub = listOf(stringResource(R.string.p2_km_value, distance(first.km))) + where + listOfNotNull(rs.takeIf { l -> l.any { it.cost != null } }?.let { l -> money(l.sumOf { it.cost ?: 0.0 }) })
        HistoryEvent(first.date, "list-checks", pluralStringResource(R.plurals.mt_group, rs.size, rs.size), sub.joinToString(" · "), rs) {
            openGroups = if (id in openGroups) openGroups - id else openGroups + id
        }
    }
    val orders = bike.orders.map { o -> HistoryEvent(o.date, "list-checks", stringResource(R.string.p2_workshop_list), stringResource(R.string.p2_km_value, distance(o.km))) { orderDetail = o } }
    val reports = bike.reports.map { r -> HistoryEvent(r.at, "bulb", r.data.title.ifBlank { stringResource(R.string.p2_report) }, stringResource(R.string.p2_km_value, distance(r.km))) { reportDetail = r } }
    val fuel = bike.fuel.map { f -> HistoryEvent(f.date, "fuel", stringResource(R.string.fillup), stringResource(R.string.p2_km_value, distance(f.km)) + (f.total?.let { " · " + money(it) } ?: "")) {} }
    val rides = bike.rides.map { r -> HistoryEvent(r.date, "route", rideSource(r), stringResource(R.string.p2_km_value, distance(r.km.toInt()))) {} }
    val readings = bike.readings.map { r -> HistoryEvent(r.date, "gauge", stringResource(R.string.reading), stringResource(R.string.p2_km_value, distance(r.km))) {} }
    val papers = vm.papers.map { d -> HistoryEvent(vm.today.toString(), "doc", documentLabel(d, androidx.compose.ui.platform.LocalContext.current), "") { vm.editDoc(d) } }
    val events = (records + orders + reports + fuel + rides + readings + papers).sortedByDescending { it.date }
    if (events.isEmpty()) Hint(stringResource(R.string.p2_empty_history))
    var month = ""
    val rowHeight = if (LocalCompact.current) 48.dp else 70.dp
    (if (compact) events.take(5) else events).forEach { event ->
        val key = event.date.take(7)
        if (month != key) {
            month = key
            Text(LocalDate.parse("$key-01").format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), style = Display.copy(fontSize = 23.sp), modifier = Modifier.padding(top = 12.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = rowHeight).clickable {
            if (event in fuel || event in rides || event in readings) eventDetail = event else event.open()
        }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp).height(rowHeight), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(LocalShop.current.line))
                Box(Modifier.size(7.dp).background(LocalShop.current.ink2, CircleShape))
            }
            Text(LocalDate.parse(event.date).dayOfMonth.toString(), style = Mono, modifier = Modifier.width(34.dp))
            Icon(event.icon, LocalShop.current.ink2, 20.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(event.title)
                if (event.sub.isNotBlank()) Hint(event.sub)
            }
            val open = event.group.isNotEmpty() && event.group.first().group in openGroups
            if (event.group.isEmpty()) Icon("chev", LocalShop.current.ink2, 16.dp)
            else Box(Modifier.rotate(90f)) { Chevron(open, size = 16.dp, openAngle = 180f) }
        }
        if (event.group.isNotEmpty()) Collapse(event.group.first().group in openGroups) { event.group.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { vm.editRecord(r) }.padding(start = 62.dp, top = rowPad, bottom = rowPad), verticalAlignment = Alignment.CenterVertically) {
                Icon(bike.parts.firstOrNull { it.id == r.partId }?.let(::partIcon) ?: "wrench", LocalShop.current.ink2, 18.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(recordName(bike, r))
                    if (r.notes.isNotBlank()) Hint(r.notes)
                }
                r.cost?.let { Text(money(it), style = Mono) }
            }
        } }
    }
    orderDetail?.let { o ->
        AlertDialog(onDismissRequest = { orderDetail = null }, title = { Text(stringResource(R.string.p2_workshop_list)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint(localizedDate(o.date) + " · " + stringResource(R.string.p2_km_value, distance(o.km)))
                o.partIds.forEach { id ->
                    Text(bike.parts.firstOrNull { it.id == id }?.let { partLabel(it) } ?: id)
                    o.itemNotes[id]?.let { Hint(it) }
                }
                o.customItems.forEach { Text(it.name); if (it.note.isNotBlank()) Hint(it.note) }
                if (o.notes.isNotBlank()) Hint(o.notes)
            }
        }, confirmButton = { TextButton({ orderDetail = null }) { Text(stringResource(R.string.close)) } })
    }
    reportDetail?.let { r ->
        AlertDialog(onDismissRequest = { reportDetail = null }, title = { Text(stringResource(R.string.p2_report)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { ReportCard(r) { reportDetail = null; vm.showZones(r.data.causes.mapNotNull { it.zone }.toSet()) } }
        }, confirmButton = { TextButton({ reportDetail = null }) { Text(stringResource(R.string.close)) } })
    }
    eventDetail?.let { event -> AlertDialog(onDismissRequest = { eventDetail = null }, title = { Text(event.title) }, text = { Text(localizedDate(event.date) + " · " + event.sub) }, confirmButton = { TextButton({ eventDetail = null }) { Text(stringResource(R.string.close)) } }) }
}

@Composable
private fun recordName(bike: Bike, r: ServiceRecord) = bike.parts.firstOrNull { it.id == r.partId }?.let { partLabel(it) } ?: r.partName ?: r.partId

@Composable
private fun RecordCard(r: ServiceRecord, name: String, onClick: () -> Unit) {
    Sheet(Modifier.clickable(onClick = onClick)) {
        Text(name, fontWeight = FontWeight.SemiBold)
        Hint(localizedDate(r.date) + " · " + stringResource(R.string.p2_km_value, distance(r.km)))
        r.cost?.let { Text(money(it), style = Mono) }
        if (r.notes.isNotBlank()) Hint(r.notes)
        r.photo?.let { Thumb(it, Modifier.size(64.dp)) }
    }
}

@Composable
fun Thumb(path: String, modifier: Modifier) {
    val bmp = remember(path) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            val sample = Integer.highestOneBit(maxOf(1, maxOf(o.outWidth, o.outHeight) / 400))
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
    bmp?.let { Image(it, stringResource(R.string.p2_invoice_photo), modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}

/** Service dates cannot be in the future. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
    override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceDateField(iso: String, onDate: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    AppInput(localizedDate(iso), {}, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p2_date)) }, readOnly = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        trailingIcon = { IconBtn("calendar", stringResource(R.string.p2_date), { picking = true }) })
    if (picking) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(iso).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = PastDates)
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton({ ps.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; picking = false }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(ps) }
    }
}

@Composable
fun RecordDialog(vm: AppViewModel, bike: Bike, draft: RecordDraft) {
    var d by remember(draft) { mutableStateOf(draft) }
    var deleting by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val part = bike.parts.firstOrNull { it.id == draft.partId }
    val record = bike.history.firstOrNull { it.id == draft.id }
    val name = part?.let { partLabel(it) } ?: record?.partName ?: draft.partId
    val valid = d.km.toIntOrNull() != null && !LocalDate.parse(d.date).isAfter(vm.today) && (d.cost.isBlank() || parseAmount(d.cost, locale) != null)
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importPhoto(uri)?.let { d = d.copy(photo = it) } }
    AlertDialog(onDismissRequest = { vm.recording = null }, title = { Text(name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ServiceDateField(d.date) { d = d.copy(date = it) }
            OdometerField(d.km, { d = d.copy(km = it) }, Modifier.fillMaxWidth())
            AmountField(d.cost, { d = d.copy(cost = it) }, Modifier.fillMaxWidth())
            FormField(d.notes, stringResource(R.string.p2_record_notes), { d = d.copy(notes = it.take(500)) }, multiline = true)
            d.photo?.let { Thumb(it, Modifier.size(72.dp)) }
            TextButton({ photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(if (d.photo == null) R.string.p2_invoice_add else R.string.p2_invoice_change)) }
            if (!valid) Hint(stringResource(R.string.p2_amount_invalid), LocalShop.current.bad)
            if (d.id != null) TextButton({ deleting = true }) { Text(stringResource(R.string.p2_delete_record), color = LocalShop.current.bad) }
        }
    }, confirmButton = { TextButton({ vm.saveRecord(d) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ vm.recording = null }) { Text(stringResource(R.string.cancel)) } })
    if (deleting) ConfirmDelete(name, { deleting = false }) { d.id?.let(vm::deleteRecord); deleting = false }
}
