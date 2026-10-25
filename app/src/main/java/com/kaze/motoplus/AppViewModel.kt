package com.kaze.motoplus

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaze.motoplus.data.Catalog
import com.kaze.motoplus.data.Drawing
import com.kaze.motoplus.data.GarageStore
import com.kaze.motoplus.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

enum class Tab { moto, km, informe }
enum class Screen { main, parts }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GarageStore(app)
    val drawing: Drawing = Drawing.load(app)

    var garage by mutableStateOf(store.load()); private set
    var catalog by mutableStateOf<Catalog?>(null); private set
    var tab by mutableStateOf(Tab.moto)
    var screen by mutableStateOf(Screen.main)
    var editing by mutableStateOf<String?>(null)
    var selectedZone by mutableStateOf<String?>(null)
    var highlighted by mutableStateOf<Set<String>>(emptySet())
    var adding by mutableStateOf(garage.bikes.isEmpty())
    var pendingImport by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    val today: LocalDate get() = LocalDate.now()
    val bike get() = garage.selected

    init {
        viewModelScope.launch { catalog = withContext(Dispatchers.IO) { Catalog.load(app) } }
    }

    private fun update(g: Garage) {
        garage = g
        viewModelScope.launch(Dispatchers.IO) { store.save(g) }
    }

    private fun updateBike(f: (Bike) -> Bike) {
        val b = bike ?: return
        update(garage.copy(bikes = garage.bikes.map { if (it.id == b.id) f(it) else it }))
    }

    fun addBike(brand: String, model: String, year: Int?, category: Category, drive: Drive, cooling: Cooling, km: Int, allDoneToday: Boolean) {
        val d = today.toString()
        val parts = defaultParts(category, drive, cooling).map { if (allDoneToday) it.copy(lastKm = km, lastDate = d) else it }
        val b = Bike(UUID.randomUUID().toString(), brand.trim(), model.trim(), year, category, drive, cooling, parts, listOf(Reading(d, km)))
        update(Garage(garage.bikes + b, b.id))
        adding = false
        tab = Tab.moto
        selectedZone = null
    }

    fun select(id: String) {
        update(garage.copy(selectedId = id)); selectedZone = null; highlighted = emptySet()
    }

    fun markDone(partId: String) {
        val b = bike ?: return
        val km = estimate(b, today).km.toInt()
        updateBike { it.markDone(partId, km, today) }
        message = "Registrado a ${fmtKm(km)} km"
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
        tab = Tab.informe
        if (importReport(text) != null) pendingImport = text
    }

    fun showZones(zones: Set<String>) {
        highlighted = zones; selectedZone = zones.firstOrNull(); tab = Tab.moto
    }
}
