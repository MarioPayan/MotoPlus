package com.kaze.motoplus

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kaze.motoplus.ui.App
import com.kaze.motoplus.ui.MotoTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            MotoTheme(vm.bike?.accent ?: com.kaze.motoplus.domain.Accent.cyan) { App(vm) }
            // Ask for notifications once the rider has a bike to be reminded about.
            val hasBike = vm.garage.bikes.isNotEmpty()
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) com.kaze.motoplus.work.ReminderWorker.runNow(this)
            }
            androidx.compose.runtime.LaunchedEffect(hasBike) {
                val prefs = getPreferences(MODE_PRIVATE)
                if (hasBike && android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotifications", false)) {
                    prefs.edit().putBoolean("askedNotifications", true).apply()
                    ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Reports come back either shared as text from the AI app or as a https://motoplus.app/i#r=… link. */
    private fun handle(intent: Intent) {
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        intent.getStringExtra("bike")?.let { vm.select(it) }
        intent.getStringExtra("startRide")?.let { t -> com.kaze.motoplus.work.Tracker.start(this, runCatching { com.kaze.motoplus.domain.RideTrigger.valueOf(t) }.getOrDefault(com.kaze.motoplus.domain.RideTrigger.manual)); vm.tab = Tab.km }
        if (!text.isNullOrBlank()) vm.incoming(text)
    }
}
