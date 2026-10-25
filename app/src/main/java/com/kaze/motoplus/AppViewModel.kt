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
import com.kaze.motoplus.ui.message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class Tab { bike, km, papers, report }
enum class Screen { main, parts, part, history, zone, doc, control, fuel, order, orderReturn, check }

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val drawings = mutableMapOf<String, Drawing>()
    fun drawingFor(b: Bike): Drawing = silhouette(b.category).let { name ->
        drawings.getOrPut(name) {
            try { Drawing.load(app, "bike_$name.json") } catch (e: java.io.FileNotFoundException) { Drawing.load(app, "bike_naked.json") }
        }
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
    var message by mutableStateOf<String?>(null)
    /** Zone that just had something marked done, plus a counter so the same zone can flash twice. */
    var flash by mutableStateOf<Pair<String, Int>?>(null); private set
    /** The document being added or edited on Screen.doc. */
    var docDraft by mutableStateOf<Document?>(null)
    /** Which document control mode opens on. */
    var showingDoc by mutableStateOf<String?>(null)
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
        if (allDoneToday) b.parts.forEach { p -> b = b.addRecord(ServiceRecord(UUID.randomUUID().toString(), p.id, d, km, notes = "Revisión completa al dar de alta la moto")) }
        Repo.update { it.copy(bikes = it.bikes + b, selectedId = b.id) }
        adding = false; tab = Tab.bike; screen = Screen.main; selectedZone = null
    }

    fun editBike(brand: String, model: String, year: Int?) {
        updateBike { it.copy(brand = brand.trim(), model = model.trim(), year = year) }
        editingBike = false
        message = "Moto actualizada"
    }

    fun deleteBike() {
        val b = bike ?: return
        Repo.update { g -> g.copy(bikes = g.bikes.filter { it.id != b.id }, selectedId = g.bikes.firstOrNull { it.id != b.id }?.id) }
        b.history.mapNotNull { it.photo }.forEach { File(it).delete() }
        garage.documents.filter { it.bikeId == b.id }.flatMap { it.photos }.forEach { File(it).delete() }
        Repo.update { g -> g.copy(documents = g.documents.filter { it.bikeId != b.id }) }
        editingBike = false; screen = Screen.main; selectedZone = null; highlighted = emptySet()
        if (garage.bikes.isEmpty()) adding = true
        message = "Moto borrada"
    }

    fun select(id: String) {
        Repo.update { it.copy(selectedId = id) }; selectedZone = null; highlighted = emptySet(); screen = Screen.main
    }

    fun markDone(partId: String) {
        val b = bike ?: return
        val km = estimate(b, today).km.toInt()
        updateBike { it.markDone(partId, km, today) }
        b.parts.firstOrNull { it.id == partId }?.let { flash = it.zone to ((flash?.second ?: 0) + 1) }
        message = "Registrado a ${fmtKm(km)} km"
    }

    fun newRecord(partId: String) {
        val b = bike ?: return
        recording = RecordDraft(null, partId, today.toString(), estimate(b, today).km.toInt().toString(), "", "", null)
    }

    fun editRecord(r: ServiceRecord) {
        recording = RecordDraft(r.id, r.partId, r.date, r.km.toString(), r.cost?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "", r.notes, r.photo)
    }

    fun saveRecord(d: RecordDraft) {
        val km = d.km.toIntOrNull() ?: return
        val r = ServiceRecord(d.id ?: UUID.randomUUID().toString(), d.partId, d.date, km, d.cost.replace(',', '.').toDoubleOrNull(), d.notes.trim(), d.photo)
        updateBike { if (d.id == null) it.addRecord(r) else it.updateRecord(r) }
        recording = null
        message = if (d.id == null) "Mantenimiento registrado" else "Registro actualizado"
    }

    fun deleteRecord(id: String) {
        bike?.history?.firstOrNull { it.id == id }?.photo?.let { File(it).delete() }
        updateBike { it.deleteRecord(id) }
        recording = null
        message = "Registro borrado"
    }

    /** Copies a picked invoice photo into app storage so it survives the gallery deleting it. */
    fun importPhoto(uri: Uri): String? = try {
        val dir = File(app.filesDir, "invoices").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        app.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f.path
    } catch (e: Exception) {
        message = "No pude guardar la foto"; null
    }

    fun setInterval(partId: String, km: Int?, days: Int?) {
        updateBike { it.withInterval(partId, km, days) }
        editing = null
        message = "Plazo actualizado"
    }

    fun saveReading(km: Int) {
        updateBike { it.withReading(km, today, System.currentTimeMillis()) }
        com.kaze.motoplus.work.ReminderWorker.runNow(app)
        message = "Lectura guardada"
    }

    /** Text shared into the app or pasted by the rider. Returns an error to show, or null when saved. */
    fun importReport(text: String): String? {
        val b = bike ?: return "Primero añade una moto."
        return when (val r = parseReport(text)) {
            is ParseResult.Error -> r.error.message
            is ParseResult.Ok -> {
                updateBike { it.copy(reports = it.reports + SavedReport(today.toString(), estimate(b, today).km.toInt(), r.report)) }
                highlighted = r.report.causes.mapNotNull { it.zone }.toSet()
                pendingImport = ""
                message = "Informe guardado"
                null
            }
        }
    }

    fun incoming(text: String) {
        tab = Tab.report; screen = Screen.main
        if (importReport(text) != null) pendingImport = text
    }

    fun showZones(zones: Set<String>) {
        highlighted = zones; selectedZone = null; tab = Tab.bike; screen = Screen.main
    }

    fun setTracking(enabled: Boolean) {
        Repo.update { it.copy(tracking = it.tracking.copy(enabled = enabled)) }
        if (enabled) com.kaze.motoplus.work.Tracker.register(app) else com.kaze.motoplus.work.Tracker.unregister(app)
    }

    fun setTrackedDevices(devices: Map<String, String>) = Repo.update { it.copy(tracking = it.tracking.copy(devices = devices)) }

    fun openPart(id: String) { back = if (screen == Screen.part) back else screen; openPart = id; screen = Screen.part }

    fun openZone(z: String) { selectedZone = z; highlighted = highlighted - z; screen = Screen.zone }

    fun resetIntervals() {
        updateBike { b -> b.copy(parts = b.parts.map { p -> b.suggested(p.id)?.let { p.copy(km = it.km, days = it.days) } ?: p }) }
        message = "Plazos restablecidos"
    }

    fun setAccent(a: Accent) = updateBike { it.copy(accent = a) }
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
    message = "Papel guardado"
}

fun AppViewModel.deleteDoc(id: String) {
    garage.documents.firstOrNull { it.id == id }?.photos?.forEach { File(it).delete() }
    Repo.update { g -> g.copy(documents = g.documents.filter { it.id != id }) }
    docDraft = null; screen = Screen.main
    message = "Papel borrado"
}

/** A new file for the camera to write a document photo into. */
fun AppViewModel.newDocPhoto(): File = File(File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

/** Copies a picked image into app storage, as document photos must outlive the gallery. */
fun AppViewModel.importDocPhoto(uri: Uri): String? = try {
    val f = newDocPhoto()
    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
    f.path
} catch (e: Exception) {
    message = "No pude guardar la foto"; null
}

/** The selected bike's papers, then the rider's own. */
val AppViewModel.papers get() = garage.documents.filter { it.bikeId == bike?.id } + garage.documents.filter { it.bikeId == null }

// ---------- Fuel ----------

fun AppViewModel.saveFuel(km: Int, liters: Double, total: Double?, full: Boolean) {
    updateBike { it.withFuel(FuelEntry(UUID.randomUUID().toString(), today.toString(), km, liters, total, full, System.currentTimeMillis()), today) }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    screen = Screen.main
    message = "Repostaje guardado · odómetro en ${fmtKm(km)} km"
}

fun AppViewModel.replaceLastReading(km: Int) {
    updateBike { it.replaceLastReading(km, today, System.currentTimeMillis()) }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    message = "Lectura corregida"
}

fun AppViewModel.deleteFuel(id: String) {
    updateBike { it.deleteFuel(id) }
    message = "Repostaje borrado"
}

// ---------- Workshop ----------

/** Creates the open work order, or updates it, and returns it. */
fun AppViewModel.saveOrder(partIds: List<String>, notes: String): WorkOrder? {
    val b = bike ?: return null
    val open = b.openOrder()
    val o = open?.copy(partIds = partIds, notes = notes)
        ?: WorkOrder(UUID.randomUUID().toString(), b.nextOrderNumber(), today.toString(), estimate(b, today).km.toInt(), partIds, notes)
    updateBike { it.copy(orders = if (open == null) it.orders + o else it.orders.map { x -> if (x.id == o.id) o else x }) }
    return o
}

fun AppViewModel.closeOrder(done: Map<String, Double?>, photo: String?) {
    val b = bike ?: return
    val o = b.openOrder() ?: return
    updateBike { it.closeOrder(o.id, done, today.toString(), estimate(b, today).km.toInt(), photo) { UUID.randomUUID().toString() } }
    com.kaze.motoplus.work.ReminderWorker.runNow(getApplication())
    screen = Screen.main
    message = "${done.size} ${if (done.size == 1) "trabajo guardado" else "trabajos guardados"} en el historial"
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
        message = if (failed.isEmpty()) "Chequeo hecho. Buena ruta" else "Chequeo guardado con ${failed.size} ${if (failed.size == 1) "fallo" else "fallos"}"
    }
}
