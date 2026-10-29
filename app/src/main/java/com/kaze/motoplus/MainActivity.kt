package com.kaze.motoplus

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kaze.motoplus.ui.App
import com.kaze.motoplus.ui.MotoTheme

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            MotoTheme(vm.theme) {
                val dark = com.kaze.motoplus.ui.LocalShop.current.dark
                androidx.compose.runtime.DisposableEffect(dark) {
                    val clear = android.graphics.Color.TRANSPARENT
                    val bars = if (dark) androidx.activity.SystemBarStyle.dark(clear) else androidx.activity.SystemBarStyle.light(clear, clear)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    onDispose { }
                }
                App(vm)
            }
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
        setIntent(intent)
        handle(intent)
    }

    /** All Android entry routes use the same core parser and require review. */
    private fun handle(intent: Intent) {
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        intent.getStringExtra("bike")?.let { vm.select(it) }
        intent.getStringExtra("papers")?.let { b -> if (b.isNotEmpty()) vm.select(b); vm.tab = Tab.papers }
        intent.getStringExtra("startRide")?.let { t -> com.kaze.motoplus.work.Tracker.start(this, runCatching { com.kaze.motoplus.domain.RideTrigger.valueOf(t) }.getOrDefault(com.kaze.motoplus.domain.RideTrigger.manual)); vm.tab = Tab.km }
        if (!text.isNullOrBlank()) {
            val provider = callingPackage ?: referrer?.host
            val source = when (provider) {
                "com.openai.chatgpt", "chatgpt.com", "chat.openai.com" -> getString(com.kaze.motoplus.R.string.p2_source_chatgpt)
                "com.google.android.apps.bard", "gemini.google.com" -> getString(com.kaze.motoplus.R.string.p2_source_gemini)
                "com.anthropic.claude", "claude.ai" -> getString(com.kaze.motoplus.R.string.p2_source_claude)
                else -> provider?.takeIf { it != packageName }?.let { pkg ->
                    runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                }
            }
            vm.incoming(text, source)
        }
    }
}
