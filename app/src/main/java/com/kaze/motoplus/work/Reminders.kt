package com.kaze.motoplus.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.kaze.motoplus.MainActivity
import com.kaze.motoplus.R
import com.kaze.motoplus.data.Prefs
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.Feature
import com.kaze.motoplus.domain.Status
import com.kaze.motoplus.domain.dueAlerts
import com.kaze.motoplus.domain.estimate
import com.kaze.motoplus.ui.partLabel
import com.kaze.motoplus.ui.remaining
import com.kaze.motoplus.ui.documentLabel
import com.kaze.motoplus.domain.docAlerts
import com.kaze.motoplus.domain.docState
import java.time.LocalDate
import java.util.concurrent.TimeUnit

const val CHANNEL_MAINTENANCE = "maintenance"

/** Twice a day, and after each ride: tells the rider about parts that just came due or went overdue. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        notifyDue(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "reminders", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build(),
            )
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("reminders-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
    }
}

fun createChannels(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    listOf("mantenimiento", "salida").forEach(nm::deleteNotificationChannel) // ids used up to 0.9.0
    nm.createNotificationChannel(NotificationChannel(CHANNEL_MAINTENANCE, ctx.getString(R.string.maintenance_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = ctx.getString(R.string.maintenance_channel_hint)
    })
}

fun notifyDue(ctx: Context) {
    // Recordatorios off silences notifications only; baselines stay, so what is due shows once they are back on.
    val prefs = Prefs.load(ctx)
    if (!prefs.reminders) return
    val today = LocalDate.now()
    val allowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    for (bike in Repo.garage.value.bikes) {
        val (alerts, baseline) = dueAlerts(bike, estimate(bike, today).km, today)
        if (alerts.isNotEmpty() && allowed) {
            val (first, state) = alerts.first()
            val overdue = alerts.count { it.second.status == Status.bad }
            val title = ctx.getString(if (overdue > 0) R.string.maintenance_due else R.string.maintenance_soon, bike.title)
            val more = if (alerts.size > 1) ctx.getString(R.string.alerts_more, alerts.size - 1) else ""
            val open = PendingIntent.getActivity(ctx, bike.id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("bike", bike.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL_MAINTENANCE)
                .setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle(title)
                .setContentText("${partLabel(first, ctx)}: ${remaining(state, ctx).lowercase()}$more")
                .setStyle(NotificationCompat.BigTextStyle().bigText(alerts.joinToString("\n") { "• ${partLabel(it.first, ctx)}: ${remaining(it.second, ctx).lowercase()}" }))
                .setContentIntent(open).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(ctx).notify(bike.id.hashCode(), n) } catch (e: SecurityException) { return }
        }
        // Only move the baseline when the rider could actually see the alert.
        if (alerts.isEmpty() || allowed) Repo.updateBike(bike.id) { it.copy(notified = baseline) }
    }
    if (prefs.on(Feature.papers)) notifyPapers(ctx, today, allowed)
}

/** One notification per paper reaching 30, 7 or 1 day before expiry, or expiring today. */
private fun notifyPapers(ctx: Context, today: LocalDate, allowed: Boolean) {
    if (!allowed) return
    val g = Repo.garage.value
    val sent = mutableMapOf<String, Int>()
    for ((d, stage) in docAlerts(g.documents, today, g.paperReminderDays)) {
        val left = docState(d, today).daysLeft ?: continue
        val whose = g.bikes.firstOrNull { it.id == d.bikeId }?.let { "${it.brand} ${it.model}" } ?: ctx.getString(R.string.rider_papers)
        val text = when {
            left < 0 -> ctx.getString(if (left == -1L) R.string.doc_expired_day else R.string.doc_expired_days, documentLabel(d, ctx), -left)
            left == 0L -> ctx.getString(R.string.doc_expires_today, documentLabel(d, ctx))
            else -> ctx.getString(if (left == 1L) R.string.doc_expires_day else R.string.doc_expires_days, documentLabel(d, ctx), left)
        }
        val open = PendingIntent.getActivity(ctx, d.id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("papers", d.bikeId ?: ""), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MAINTENANCE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle(whose).setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(d.id.hashCode(), n) } catch (e: SecurityException) { return }
        sent[d.id] = stage
    }
    if (sent.isNotEmpty()) Repo.update { gg -> gg.copy(documents = gg.documents.map { d -> sent[d.id]?.let { d.copy(alerted = it) } ?: d }) }
}
