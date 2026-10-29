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
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.Status
import com.kaze.motoplus.domain.dueAlerts
import com.kaze.motoplus.domain.estimate
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
    nm.createNotificationChannel(NotificationChannel(CHANNEL_MAINTENANCE, "Mantenimiento", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Piezas que toca revisar o cambiar"
    })
}

fun notifyDue(ctx: Context) {
    val today = LocalDate.now()
    val allowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    for (bike in Repo.garage.value.bikes) {
        val (alerts, baseline) = dueAlerts(bike, estimate(bike, today).km, today)
        if (alerts.isNotEmpty() && allowed) {
            val (first, state) = alerts.first()
            val overdue = alerts.count { it.second.status == Status.bad }
            val title = if (overdue > 0) "${bike.brand} ${bike.model}: toca mantenimiento" else "${bike.brand} ${bike.model}: se acerca un mantenimiento"
            val more = if (alerts.size > 1) " y ${alerts.size - 1} más" else ""
            val open = PendingIntent.getActivity(ctx, bike.id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("bike", bike.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL_MAINTENANCE)
                .setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle(title)
                .setContentText("${first.name}: ${remaining(state, ctx).lowercase()}$more")
                .setStyle(NotificationCompat.BigTextStyle().bigText(alerts.joinToString("\n") { "• ${it.first.name}: ${remaining(it.second, ctx).lowercase()}" }))
                .setContentIntent(open).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(ctx).notify(bike.id.hashCode(), n) } catch (e: SecurityException) { return }
        }
        // Only move the baseline when the rider could actually see the alert.
        if (alerts.isEmpty() || allowed) Repo.updateBike(bike.id) { it.copy(notified = baseline) }
    }
    notifyPapers(ctx, today, allowed)
}

/** One notification per paper reaching 30, 7 or 1 day before expiry, or expiring today. */
private fun notifyPapers(ctx: Context, today: LocalDate, allowed: Boolean) {
    if (!allowed) return
    val g = Repo.garage.value
    val sent = mutableMapOf<String, Int>()
    for ((d, stage) in docAlerts(g.documents, today, g.paperReminderDays)) {
        val left = docState(d, today).daysLeft ?: continue
        val whose = g.bikes.firstOrNull { it.id == d.bikeId }?.let { "${it.brand} ${it.model}" } ?: "Tus papeles"
        val text = when {
            left < 0 -> "${documentLabel(d, ctx)} venció hace ${-left} ${if (left == -1L) "día" else "días"}"
            left == 0L -> "${documentLabel(d, ctx)} vence hoy"
            else -> "${documentLabel(d, ctx)} vence en $left ${if (left == 1L) "día" else "días"}"
        }
        val open = PendingIntent.getActivity(ctx, d.id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra("papers", d.bikeId ?: ""), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MAINTENANCE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle(whose).setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(d.id.hashCode(), n) } catch (e: SecurityException) { return }
        sent[d.id] = stage
    }
    if (sent.isNotEmpty()) Repo.update { gg -> gg.copy(documents = gg.documents.map { d -> sent[d.id]?.let { d.copy(alerted = it) } ?: d }) }
}
