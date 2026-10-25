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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class Tab { moto, km, informe }
enum class Screen { main, parts, part, history }

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val drawings = mutableMapOf<String, Drawing>()
    fun drawingFor(b: Bike): Drawing = silhouette(b.category).let { name ->
        drawings.getOrPut(name) {
            try { Drawing.load(app, "bike_$name.json") } catch (e: java.io.FileNotFoundException) { Drawing.load(app, "bike_naked.json") }
        }
    }

    var garage by mutableStateOf(Repo.garage.value); private set
    var catalog by mutableStateOf<Catalog?>(null); private set
    var tab by mutableStateOf(Tab.moto)
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

    val today: LocalDate get() = LocalDate.now()
    val bike get() = garage.selected

    init {
        viewModelScope.launch { Repo.garage.collect { garage = it } }
        viewModelScope.launch { catalog = withContext(Dispatchers.IO) { Catalog.load(app) } }
    }

    private fun updateBike(f: (Bike) -> Bike) {
        val b = bike ?: return
        Repo.updateBike(b.id, f)
    }

    fun addBike(brand: String, model: String, year: Int?, category: Category, drive: Drive, cooling: Cooling, km: Int, allDoneToday: Boolean) {
        val d = today.toString()
        var b = Bike(UUID.randomUUID().toString(), brand.trim(), model.trim(), year, category, drive, cooling, defaultParts(category, drive, cooling), listOf(Reading(d, km)))
        if (allDoneToday) b.parts.forEach { p -> b = b.addRecord(ServiceRecord(UUID.randomUUID().toString(), p.id, d, km, notes = "Revisión completa al dar de alta la moto")) }
        Repo.update { Garage(it.bikes + b, b.id) }
        adding = false; tab = Tab.moto; screen = Screen.main; selectedZone = null
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
        val dir = File(app.filesDir, "facturas").apply { mkdirs() }
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
        updateBike { it.withReading(km, today) }
        message = "Lectura guardada"
    }

    /** Text shared into the app or pasted by the rider. Returns an error to show, or null when saved. */
    fun importReport(text: String): String? {
        val b = bike ?: return "Primero añade una moto."
        return when (val r = parseReport(text)) {
            is ParseResult.Error -> r.message
            is ParseResult.Ok -> {
                updateBike { it.copy(reports = it.reports + SavedReport(today.toString(), estimate(b, today).km.toInt(), r.report)) }
                highlighted = r.report.causas.mapNotNull { it.zona }.toSet()
                pendingImport = ""
                message = "Informe guardado"
                null
            }
        }
    }

    fun incoming(text: String) {
        tab = Tab.informe; screen = Screen.main
        if (importReport(text) != null) pendingImport = text
    }

    fun showZones(zones: Set<String>) {
        highlighted = zones; selectedZone = zones.firstOrNull(); tab = Tab.moto; screen = Screen.main
    }

    fun openPart(id: String) { openPart = id; screen = Screen.part }
}

data class RecordDraft(val id: String?, val partId: String, val date: String, val km: String, val cost: String, val notes: String, val photo: String?)
