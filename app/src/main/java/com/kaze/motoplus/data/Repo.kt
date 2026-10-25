package com.kaze.motoplus.data

import android.content.Context
import com.kaze.motoplus.domain.Bike
import com.kaze.motoplus.domain.Garage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one owner of the garage in this process. The UI, the ride tracker and the reminder worker
 * all go through here, so a ride saved in the background never races an edit made on screen.
 */
object Repo {
    private lateinit var store: GarageStore
    private val state = MutableStateFlow(Garage())
    val garage: StateFlow<Garage> = state

    fun init(context: Context) = synchronized(this) {
        if (::store.isInitialized) return
        store = GarageStore(context.applicationContext)
        state.value = store.load()
    }

    // ponytail: writes on the caller's thread; the file is a few KB. Move to a writer coroutine if it grows.
    fun update(f: (Garage) -> Garage): Garage = synchronized(this) {
        f(state.value).also { state.value = it; store.save(it) }
    }

    fun updateBike(id: String, f: (Bike) -> Bike) = update { g -> g.copy(bikes = g.bikes.map { if (it.id == id) f(it) else it }) }
}
