package com.kaze.motoplus.data

import android.content.Context
import com.kaze.motoplus.domain.CHECK_STEPS
import com.kaze.motoplus.domain.Density
import com.kaze.motoplus.domain.Feature
import com.kaze.motoplus.domain.Preferences
import com.kaze.motoplus.domain.SoonThreshold

/**
 * [Preferences] in the "display" shared preferences, next to the theme. Plain keys, no reflection: the worker,
 * the tracker and the UI read the same file.
 */
object Prefs {
    private const val FILE = "display"

    fun load(ctx: Context): Preferences {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = Preferences()
        return Preferences(
            onboarded = p.getBoolean("onboarded", defaults.onboarded),
            features = Feature.entries.filter { p.getBoolean("feature_${it.name}", true) }.toSet(),
            checks = p.getStringSet("checks", null)?.filter { it in CHECK_STEPS }?.toSet() ?: defaults.checks,
            drawing = p.getBoolean("ui_drawing", defaults.drawing),
            density = runCatching { Density.valueOf(p.getString("ui_density", null)!!) }.getOrDefault(defaults.density),
            reminders = p.getBoolean("ui_reminders", defaults.reminders),
            soon = runCatching { SoonThreshold(p.getInt("soon_km_pct", defaults.soon.kmPct), p.getInt("soon_time_pct", defaults.soon.timePct)) }.getOrDefault(defaults.soon),
        )
    }

    fun save(ctx: Context, v: Preferences) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            putBoolean("onboarded", v.onboarded)
            Feature.entries.forEach { putBoolean("feature_${it.name}", v.on(it)) }
            putStringSet("checks", v.checks)
            putBoolean("ui_drawing", v.drawing)
            putString("ui_density", v.density.name)
            putBoolean("ui_reminders", v.reminders)
            putInt("soon_km_pct", v.soon.kmPct)
            putInt("soon_time_pct", v.soon.timePct)
        }.apply()
    }
}
