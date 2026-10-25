package com.kaze.motoplus

import android.app.Application
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.work.ReminderWorker
import com.kaze.motoplus.work.createChannels

class MotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        createChannels(this)
        com.kaze.motoplus.work.createRideChannel(this)
        com.kaze.motoplus.work.Tracker.register(this)
        ReminderWorker.schedule(this)
    }
}
