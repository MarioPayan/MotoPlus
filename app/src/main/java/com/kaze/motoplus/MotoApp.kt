package com.kaze.motoplus

import android.app.Application
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.work.ReminderWorker
import com.kaze.motoplus.work.createChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        createChannels(this)
        com.kaze.motoplus.work.createRideChannel(this)
        com.kaze.motoplus.work.Tracker.recoverOrphan(this)
        com.kaze.motoplus.work.Tracker.register(this)
        ReminderWorker.schedule(this)
        // Widgets follow the garage: any change redraws them, a moment later so bursts of edits redraw once.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            Repo.garage.drop(1).debounce(800).collect { runCatching { com.kaze.motoplus.work.updateWidgets(this@MotoApp) } }
        }
    }
}
