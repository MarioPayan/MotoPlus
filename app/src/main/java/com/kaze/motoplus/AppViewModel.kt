package com.kaze.motoplus

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaze.motoplus.data.Catalog
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.*
import com.kaze.motoplus.ui.localizedLocale
import com.kaze.motoplus.ui.localizedPlural
import com.kaze.motoplus.ui.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class Tab { bike, km, papers, report }
enum class Screen { main, parts, part, history, zone, doc, control, fuel, order, maintenance, check, data, kmSources, settings, ai, aiReview, aiError, invoiceAi }

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val drawings = mutableMapOf<String, Drawing>()
    fun drawingFor(b: Bike): Drawing = silhouette(b.category).let { name ->
        drawings.getOrPut(name) {
            try { Drawing.load(app, "bike_$name.json") } catch (e: java.io.FileNotFoundException) { Drawing.load(app, "bike_naked.json") }
        }
    }

    private val preferences = app.getSharedPreferences("display", Application.MODE_PRIVATE)
    var theme by mutableStateOf(preferences.getString("theme", "dark") ?: "dark"); private set
    fun chooseTheme(value: String) {
        theme = value; preferences.edit().putString("theme", value).apply()
        viewModelScope.launch { com.kaze.motoplus.work.updateWidgets(app) }
    }

    /**
     * AI features (consult, invoice reading, incoming AI links) are "Próximamente": Settings shows the switch
     * disabled and they stay off whatever the stored "ai" preference says. The key is kept so they can come back
     * by flipping [aiAvailable].
     */
    val aiAvailable = false
    var aiEnabled by mutableStateOf(aiAvailable && preferences.getBoolean("ai", false)); private set
    fun setAi(on: Boolean) {
        if (!aiAvailable) return
        aiEnabled = on; preferences.edit().putBoolean("ai", on).apply()
    }

    var garage by mutableStateOf(Repo.garage.value); private set
    var catalog by mutableStateOf<Catalog?>(null); private set
    var tab by mutableStateOf(Tab.bike)
    var screen by mutableStateOf(Screen.main)
    var openPart by mutableStateOf<String?>(null)
    var editing by mutableStateOf<String?>(null)
    var recording by mutableStateOf<RecordDraft?>(null)
    var selectedZone by mutableStateOf<String?>(null)
    var highlighted by mutableStateOf<Set<String>>(emptySet())
    var adding by mutableStateOf(garage.bikes.isEmpty())
    var editingBike by mutableStateOf(false)
    var pendingImport by mutableStateOf("")
    var aiRevision by mutableStateOf(0); private set
    var aiSource by mutableStateOf<String?>(null)
    var aiReview by mutableStateOf<ReportData?>(null)
    var aiError by mutableStateOf<Int?>(null)
    var maintenance by mutableStateOf<MaintenanceDraft?>(null)
    /** Deshacer for the snackbar that shows [message]; taken by the snackbar when it appears. */
    var messageUndo: MaintenanceUndo? = null
    var aiNotes by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    /** Zone that just had something marked done, plus a counter so the same zone can flash twice. */
    var flash by mutableStateOf<Pair<String, Int>?>(null); private set
    /** The document being added or edited on Screen.doc. */
    var docDraft by mutableStateOf<Document?>(null)
    /** Which document control mode opens on. */
    var showingDoc by mutableStateOf<String?>(null)
    /** The Km tab should open the camera to read the odometer as soon as it shows. */
    var odometerPhoto by mutableStateOf(false)
    /** Where the part screen goes back to. */
    var back by mutableStateOf(Screen.main); private set

    val today: LocalDate get() = LocalDate.now()
    val bike get() = garage.selected

    init {
        viewModelScope.launch { Repo.garage.collect { garage = it } }
        viewModelScope.launch { catalog = withContext(Dispatchers.IO) { Catalog.load(app) } }
    }

    internal fun updateBike(f: (Bike) -> Bike) {
        val b = bike ?: return
        Repo.updateBike(b.id, f)
    }

    fun addBike(brand: String, model: String, year: Int?, category: Category, drive: Drive, cooling: Cooling, km: Int, allDoneToday: Boolean) {
        val d = today.toString()
        var b = Bike(UUID.randomUUID().toString(), brand.trim(), model.trim(), year, category, drive, cooling, defaultParts(category, drive, cooling), listOf(Reading(d, km, System.currentTimeMillis())))
        if (allDoneToday) b.parts.forEach { p -> b = b.addRecord(ServiceRecord(UUID.randomUUID().toString(), p.id, d, km, notes = text(R.string.initial_service))) }
        Repo.update { it.copy(bikes = it.bikes + b, selectedId = b.id) }
        adding = false; tab = Tab.bike; screen = Screen.main; selectedZone = null
    }

    fun editBike(brand: String, model: String, year: Int?) {
        updateBike { it.copy(brand = brand.trim(), model = model.trim(), year = year) }
        editingBike = false
        message = text(R.string.bike_updated)
    }

    fun deleteBike() {
        val b = bike ?: return
        Repo.update { g -> g.copy(bikes = g.bikes.filter { it.id != b.id }, selectedId = g.bikes.firstOrNull { it.id != b.id }?.id) }
        b.history.mapNotNull { it.photo }.forEach { File(it).delete() }
        garage.documents.filter { it.bikeId == b.id }.flatMap { it.photos }.forEach { File(it).delete() }
        Repo.update { g -> g.copy(documents = g.documents.filter { it.bikeId != b.id }) }
        editingBike = false; screen = Screen.main; selectedZone = null; highlighted = emptySet()
        if (garage.bikes.isEmpty()) adding = true
        message = text(R.string.bike_deleted)
    }

    fun select(id: String) {
        aiReview = null; maintenance = null; pendingImport = ""
        Repo.update { it.copy(selectedId = id) }; selectedZone = null; highlighted = emptySet(); screen = Screen.main
    }

    fun markDone(partId: String) {
        val b = bike ?: return
        val km = estimate(b, today).km.toInt()
        updateBike { it.markDone(partId, km, today) }
        b.parts.firstOrNull { it.id == partId }?.let { flash = it.zone to ((flash?.second ?: 0) + 1) }
        message = text(R.string.registered_at, fmtKm(km))
    }

    fun newRecord(partId: String) {
        val b = bike ?: return
        recording = RecordDraft(null, partId, today.toString(), estimate(b, today).km.toInt().toString(), "", "", null)
    }

    fun editRecord(r: ServiceRecord) {
        recording = RecordDraft(r.id, r.partId, r.date, r.km.toString(), r.cost?.let { formatAmountInput(java.text.NumberFormat.getNumberInstance(localizedLocale(app)).format(it), garage.currency, localizedLocale(app)) } ?: "", r.notes, r.photo)
    }

    fun saveRecord(d: RecordDraft) {
        val km = d.km.toIntOrNull() ?: return
        val old = bike?.history?.firstOrNull { it.id == d.id }
        val r = (old ?: ServiceRecord(UUID.randomUUID().toString(), d.partId, d.date, km))
            .copy(date = d.date, km = km, cost = parseAmount(d.cost, localizedLocale(app)), notes = d.notes.trim(), photo = d.photo)
        updateBike { if (d.id == null) it.addRecord(r) else it.updateRecord(r) }
        recording = null
        message = if (d.id == null) text(R.string.record_saved) else text(R.string.record_updated)
    }

    fun deleteRecord(id: String) {
        bike?.history?.firstOrNull { it.id == id }?.photo?.let { path ->
            if (garage.bikes.flatMap { it.history }.count { it.photo == path } == 1) File(path).delete()
        }
        updateBike { it.deleteRecord(id) }
        recording = null
        message = text(R.string.record_deleted)
    }

    /** Copies a picked invoice photo into app storage so it survives the gallery deleting it. */
    fun importPhoto(uri: Uri): String? = try {
        val dir = File(app.filesDir, "invoices").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        app.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f.path
    } catch (e: Exception) {
        message = text(R.string.photo_save_failed); null
    }

    fun setInterval(partId: String, km: Int?, days: Int?, unit: IntervalUnit, value: String) {
        updateBike { it.withInterval(partId, km, days, unit, value) }
        editing = null
        message = text(R.string.interval_updated)
    }

    fun saveReading(km: Int) {
        updateBike { it.withReading(km, today, System.currentTimeMillis()) }
        com.kaze.motoplus.work.ReminderWorker.runNow(app)
        message = text(R.string.reading_saved)
    }

    /** Every incoming route stops at review; no garage data changes here. */
    fun incoming(answer: String, source: String? = null) {
        if (!aiEnabled) return
        aiRevision++
        pendingImport = answer
        aiSource = source
        aiReview = null
        aiError = null
        tab = Tab.report
        val b = bike
        when (val result = parseAiResponse(answer)) {
            is AiResult.Error -> aiError = R.string.p2_ai_invalid_text
            is AiResult.Report -> {
                val r = result.data
                if (b == null || (r.bikeId != null && r.bikeId != b.id)) aiError = R.string.p2_ai_wrong_bike
                else if (r.parts.any { id -> b.parts.none { it.id == id } }) aiError = R.string.p2_ai_invalid_text
                else { aiReview = r; screen = Screen.aiReview }
            }
            is AiResult.Invoice -> {
                val r = result.data
                when {
                    b == null || (r.bikeId != null && r.bikeId != b.id) -> aiError = R.string.p2_ai_wrong_bike
                    r.currency != garage.currency -> aiError = R.string.p2_currency_mismatch
                    r.jobs.any { j -> b.parts.none { it.id == j.partId } } -> aiError = R.string.p2_ai_invalid_text
                    else -> {
                        val base = maintenance?.takeIf { it.visit } ?: newMaintenance(b, visit = true).copy(back = Screen.main)
                        val extras = r.extras.map { DraftJob(UUID.randomUUID().toString(), it.name, ai = true) }
                        val stale = base.custom.filter { it.ai }.map { it.id }.toSet()
                        val number = java.text.NumberFormat.getNumberInstance(localizedLocale(app))
                        fun cost(n: Double?) = n?.let(number::format).orEmpty()
                        maintenance = base.copy(
                            checked = (base.checked.filterNot(stale::contains) + r.jobs.map { it.partId } + extras.map { it.id }).distinct(),
                            custom = base.custom.filterNot { it.ai } + extras,
                            costs = base.costs - stale + r.jobs.associate { it.partId to cost(it.cost) } + extras.zip(r.extras).associate { (j, x) -> j.id to cost(x.cost) },
                            date = r.date ?: base.date, km = r.km?.toString() ?: base.km, source = source, fromInvoice = true,
                        )
                        screen = Screen.maintenance
                    }
                }
            }
        }
        if (aiError != null) screen = Screen.aiError
    }

    fun saveReviewedReport(report: ReportData) {
        val b = bike ?: return
        updateBike { it.copy(reports = it.reports + SavedReport(today.toString(), estimate(b, today).km.toInt(), report, aiSource)) }
        if (report.parts.isNotEmpty()) {
            val o = bike?.openOrder()
            saveOrder((o?.partIds.orEmpty() + report.parts).distinct(), o?.notes.orEmpty())
        }
        highlighted = report.causes.mapNotNull { it.zone }.toSet()
        aiReview = null; pendingImport = ""; screen = Screen.main
        message = text(R.string.report_saved)
    }

    /** Registrar mantenimiento; [visit] opens it as Completar visita with the Taller list checked. */
    fun openMaintenance(visit: Boolean = false, preset: QuickPick? = null, partId: String? = null) {
        val b = bike ?: return
        maintenance = newMaintenance(b, visit, preset, partId)
        screen = Screen.maintenance
    }

    private fun newMaintenance(b: Bike, visit: Boolean, preset: QuickPick? = null, partId: String? = null): MaintenanceDraft {
        val odo = estimate(b, today).km
        val order = if (visit) b.openOrder() else null
        val checked = when {
            visit -> b.quickPick(QuickPick.pending, odo, today)
            preset != null -> b.quickPick(preset, odo, today)
            partId != null && b.parts.any { it.id == partId } -> listOf(partId)
            else -> emptyList()
        }
        return MaintenanceDraft(visit, screen, order?.id, today.toString(), odo.toInt().toString(), if (visit) Venue.shop else null,
            checked = checked,
            notes = order?.let { o -> o.itemNotes + o.customItems.filter { it.note.isNotBlank() }.associate { it.id to it.note } }.orEmpty(),
            custom = if (visit) b.workshopItems(odo, today).customItems.map { DraftJob(it.id, it.name, fromOrder = true) } else emptyList())
    }

    fun saveMaintenance(d: MaintenanceDraft) {
        val km = d.km.toIntOrNull() ?: return
        val locale = localizedLocale(app)
        val parts = bike?.parts.orEmpty().map { it.id }.toSet()
        val jobs = d.checked.filter { id -> id in parts || d.custom.any { it.id == id } }.map { id ->
            LoggedJob(id, d.custom.firstOrNull { it.id == id }?.name, d.costs[id]?.takeIf { it.isNotBlank() }?.let { parseAmount(it, locale) }, d.notes[id].orEmpty())
        }
        var undo: MaintenanceUndo? = null
        updateBike { old -> old.logMaintenance(jobs, d.date, km, today, d.venue, d.place, d.photo, d.orderId, { UUID.randomUUID().toString() }, System.currentTimeMillis()).also { undo = MaintenanceUndo(old, it) } }
        maintenance = null; pendingImport = ""
        if (d.visit) { tab = Tab.report; screen = Screen.main } else screen = d.back
        com.kaze.motoplus.work.ReminderWorker.runNow(app)
        messageUndo = undo
        message = localizedPlural(app, R.plurals.mt_saved, jobs.size)
    }

    fun undoMaintenance(u: MaintenanceUndo) {
        Repo.updateBike(u.after.id) { it.undoMaintenance(u.before, u.after) }
        val photos = Repo.garage.value.bikes.flatMap { b -> b.history.mapNotNull { it.photo } }.toSet()
        u.after.history.mapNotNull { it.photo }.filter { it !in photos }.distinct().forEach { File(it).delete() }
        com.kaze.motoplus.work.ReminderWorker.runNow(app)
        message = text(R.string.mt_undone)
    }

    fun showZones(zones: Set<String>) {
        highlighted = zones; selectedZone = null; tab = Tab.bike; screen = Screen.main
    }

    /** Saves how km are kept current, and starts or stops listening to the activity sensor to match. */
    fun setTracking(t: Tracking) {
        Repo.update { it.copy(tracking = t) }
        com.kaze.motoplus.work.Tracker.register(app)
    }

    /** Opens the Km tab's odometer photo reading, from anywhere. */
    fun readOdometerPhoto() { tab = Tab.km; screen = Screen.main; odometerPhoto = true }

    fun openPart(id: String) { back = if (screen == Screen.part) back else screen; openPart = id; screen = Screen.part }

    fun openZone(z: String) { selectedZone = z; highlighted = highlighted - z; screen = Screen.zone }

    fun resetIntervals() {
        updateBike { b -> b.copy(parts = b.parts.map { p -> b.suggested(p.id)?.let { p.copy(km = it.km, days = it.days, timeUnit = it.timeUnit, timeValue = it.timeValue) } ?: p }) }
        message = text(R.string.intervals_reset)
    }

    fun setPaperReminder(type: DocType, days: Int) {
        Repo.update { g -> g.copy(paperReminderDays = g.paperReminderDays + (type to days.coerceIn(0, 1095)),
            documents = g.documents.map { if (it.type == type) it.copy(alerted = null) else it }) }
        com.kaze.motoplus.work.ReminderWorker.runNow(app)
    }

    fun setCurrency(value: String) { if (value in listOf("COP", "USD", "EUR", "MXN")) Repo.update { it.copy(currency = value) } }

    fun removePart(id: String) {
        updateBike { it.withoutPart(id) }
        message = text(R.string.deleted)
    }

    fun snooze(partId: String, days: Long) = updateBike { b ->
        b.copy(parts = b.parts.map { if (it.id == partId) it.copy(snoozedUntil = today.plusDays(days).toString()) else it }, notified = b.notified - partId)
    }

}

data class RecordDraft(val id: String?, val partId: String, val date: String, val km: String, val cost: String, val notes: String, val photo: String?)

// ---------- Papers ----------

fun AppViewModel.newDoc(type: DocType) {
    docDraft = Document(UUID.randomUUID().toString(), type, if (type.forRider) null else bike?.id)
    screen = Screen.doc
}

fun AppViewModel.editDoc(d: Document) { docDraft = d; screen = Screen.doc }

fun AppViewModel.saveDoc(d: Document) {
    Repo.update { g ->
        val old = g.documents.firstOrNull { it.id == d.id }
        // A new expiry date restarts its reminders.
        val fixed = d.copy(bikeId = if (d.type.forRider) null else d.bikeId ?: bike?.id, alerted = if (old != null && old.expires == d.expires) old.alerted else null)
        old?.photos?.filter { it !in fixed.photos }?.forEach { File(it).delete() }
        g.copy(documents = if (old == null) g.documents + fixed else g.documents.map { if (it.id == d.id) fixed else it })
    }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    docDraft = null; screen = Screen.main
    message = text(R.string.paper_saved)
}

fun AppViewModel.deleteDoc(id: String) {
    garage.documents.firstOrNull { it.id == id }?.photos?.forEach { File(it).delete() }
    Repo.update { g -> g.copy(documents = g.documents.filter { it.id != id }) }
    docDraft = null; screen = Screen.main
    message = text(R.string.paper_deleted)
}

/** A new file for the camera to write a document photo into. */
fun AppViewModel.newDocPhoto(): File = File(File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

/** Copies a picked image into app storage, as document photos must outlive the gallery. */
fun AppViewModel.importDocPhoto(uri: Uri): String? = try {
    val f = newDocPhoto()
    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
    f.path
} catch (e: Exception) {
    message = text(R.string.photo_save_failed); null
}

/** The selected bike's papers, then the rider's own. */
val AppViewModel.papers get() = garage.documents.filter { it.bikeId == bike?.id } + garage.documents.filter { it.bikeId == null }

// ---------- Fuel ----------

fun AppViewModel.saveFuel(km: Int, liters: Double, total: Double?, full: Boolean) {
    updateBike { it.withFuel(FuelEntry(UUID.randomUUID().toString(), today.toString(), km, liters, total, full, System.currentTimeMillis()), today) }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    screen = Screen.main
    message = text(R.string.fuel_saved_at, fmtKm(km))
}

fun AppViewModel.replaceLastReading(km: Int) {
    updateBike { it.replaceLastReading(km, today, System.currentTimeMillis()) }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    message = text(R.string.reading_corrected)
}

fun AppViewModel.deleteFuel(id: String) {
    updateBike { it.deleteFuel(id) }
    message = text(R.string.fuel_deleted)
}

// ---------- Workshop ----------

/** Creates the open work order, or updates it, and returns it. */
fun AppViewModel.saveOrder(partIds: List<String>, notes: String, customItems: List<PendingJob>? = null, itemNotes: Map<String, String>? = null): WorkOrder? {
    val b = bike ?: return null
    val open = b.openOrder()
    val o = open?.copy(partIds = partIds, notes = notes, customItems = customItems ?: open.customItems, itemNotes = itemNotes ?: open.itemNotes)
        ?: WorkOrder(UUID.randomUUID().toString(), b.nextOrderNumber(), today.toString(), estimate(b, today).km.toInt(), partIds, notes, customItems = customItems.orEmpty(), itemNotes = itemNotes.orEmpty())
    updateBike { it.copy(orders = if (open == null) it.orders + o else it.orders.map { x -> if (x.id == o.id) o else x }) }
    return o
}

// ---------- Pre-ride check ----------

/** Saves the check; failed steps' parts go onto the workshop list when [toOrder] is not empty. */
fun AppViewModel.saveCheck(ok: List<String>, failed: List<String>, toOrder: List<String>) {
    val b = bike ?: return
    updateBike { it.withCheck(PreRideCheck(today.toString(), System.currentTimeMillis(), ok, failed), estimate(b, today).km.toInt(), today) }
    if (toOrder.isNotEmpty()) {
        val current = bike?.openOrder()
        saveOrder(((current?.partIds ?: emptyList()) + toOrder).distinct(), current?.notes ?: "")
        screen = Screen.order
    } else {
        screen = Screen.main
        message = if (failed.isEmpty()) text(R.string.check_saved) else if (failed.size == 1) text(R.string.check_failed_one) else text(R.string.check_failed, failed.size)
    }
}

private fun AppViewModel.text(id: Int, vararg args: Any) = localizedString(getApplication<Application>(), id, *args)

/** The state before and after one maintenance log, kept for Deshacer. */
data class MaintenanceUndo(val before: Bike, val after: Bike)

/** A free job on the maintenance screen: [fromOrder] ones come from the Taller list, [ai] ones from a read invoice. */
data class DraftJob(val id: String, val name: String, val fromOrder: Boolean = false, val ai: Boolean = false)

/** Unsaved Registrar mantenimiento (or Completar visita) state; it survives the AI invoice handoff. Costs keep the typed text. */
data class MaintenanceDraft(
    val visit: Boolean,
    val back: Screen,
    val orderId: String?,
    val date: String,
    val km: String,
    val venue: Venue?,
    val place: String = "",
    val checked: List<String> = emptyList(),
    val costs: Map<String, String> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val noteOpen: Set<String> = emptySet(),
    val custom: List<DraftJob> = emptyList(),
    val query: String = "",
    val photo: String? = null,
    val source: String? = null,
    /** Filled from an invoice the rider's AI read. */
    val fromInvoice: Boolean = false,
)
