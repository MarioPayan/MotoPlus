package com.kaze.motoplus

import android.app.Application
import com.kaze.motoplus.data.Repo

class MotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
