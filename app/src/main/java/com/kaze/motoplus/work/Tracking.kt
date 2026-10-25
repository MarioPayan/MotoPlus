package com.kaze.motoplus.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.kaze.motoplus.MainActivity
import com.kaze.motoplus.R
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.DistanceMeter
import com.kaze.motoplus.domain.Ride
import com.kaze.motoplus.domain.RideTrigger
import com.kaze.motoplus.ui.sourceLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

const val CHANNEL_RIDE = "rides"
private const val ID_LIVE = 4100
private const val ID_DONE = 4101

data class LiveRide(val trigger: RideTrigger, val device: String?, val km: Double, val startedAt: Long) {
    val label get() = Ride("", km, trigger, device).sourceLabel
}

/** Starts and stops ride recording; the UI watches [live]. */
object Tracker {
    private val state = MutableStateFlow<LiveRide?>(null)
    val live: StateFlow<LiveRide?> = state
    internal fun set(r: LiveRide?) { state.value = r }

    fun hasLocation(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasBackground(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun hasActivity(ctx: Context) = granted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context, trigger: RideTrigger, device: String? = null) {
        if (state.value != null || !hasLocation(ctx)) return
        val i = Intent(ctx, RideService::class.java).putExtra("trigger", trigger.name).putExtra("device", device)
        try {
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            // Android refused a background start (battery optimisation on): ask the rider to start it.
            val open = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).putExtra("startRide", trigger.name), PendingIntent.FLAG_IMMUTABLE)
            notify(ctx, ID_DONE, NotificationCompat.Builder(ctx, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle("¿Sales en moto?").setContentText("Toca para registrar la salida. Quita la optimización de batería para que empiece sola.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    fun stop(ctx: Context) {
        if (state.value == null) return
        ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.STOP))
    }

    /** Listens for "in vehicle" start/stop from the phone's activity sensor. */
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        val g = Repo.garage.value.tracking
        if (!g.enabled || !hasActivity(ctx)) return
        val req = ActivityTransitionRequest(listOf(
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
            ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
        ))
        ActivityRecognition.getClient(ctx).requestActivityTransitionUpdates(req, transitionIntent(ctx))
    }

    @SuppressLint("MissingPermission")
    fun unregister(ctx: Context) {
        if (hasActivity(ctx)) ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(transitionIntent(ctx))
    }

    private fun transitionIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 11, Intent(ctx, TransitionReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    internal fun notify(ctx: Context, id: Int, n: Notification) {
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }
}

/**
 * Foreground GPS recorder for one ride. Ends when asked, when the trigger goes away,
 * or after 6 minutes without movement (parked).
 */
class RideService : Service() {
    companion object { const val STOP = "com.kaze.motoplus.STOP_RIDE" }

    private val meter = DistanceMeter()
    private var trigger = RideTrigger.manual
    private var device: String? = null
    private var startedAt = 0L
    private lateinit var client: FusedLocationProviderClient
    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            for (l in r.locations) meter.add(l.latitude, l.longitude, l.accuracy, l.time)
            Tracker.set(LiveRide(trigger, device, meter.km, startedAt))
            Tracker.notify(this@RideService, ID_LIVE, liveNotification())
            val idle = System.currentTimeMillis() - maxOf(meter.lastMovingAt, startedAt)
            if (idle > 6 * 60_000) finish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish(); return START_NOT_STICKY }
        if (startedAt != 0L) return START_NOT_STICKY
        Repo.init(this)
        trigger = intent?.getStringExtra("trigger")?.let { runCatching { RideTrigger.valueOf(it) }.getOrNull() } ?: RideTrigger.manual
        device = intent?.getStringExtra("device")
        startedAt = System.currentTimeMillis()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, ID_LIVE, liveNotification(), type)
        Tracker.set(LiveRide(trigger, device, 0.0, startedAt))
        client = LocationServices.getFusedLocationProviderClient(this)
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).setMinUpdateDistanceMeters(15f).build(),
            callback, Looper.getMainLooper(),
        )
        return START_NOT_STICKY
    }

    private fun liveNotification(): Notification {
        val stop = PendingIntent.getService(this, 3, Intent(this, RideService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle("Registrando salida · ${"%.1f".format(meter.km)} km")
            .setContentText(Tracker.live.value?.label ?: "").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, "Terminar", stop).build()
    }

    private var finished = false
    private fun finish() {
        if (finished) return
        finished = true
        if (::client.isInitialized) client.removeLocationUpdates(callback)
        val km = meter.km
        Tracker.set(null)
        val bike = Repo.garage.value.selected
        if (km >= 0.3 && bike != null) {
            Repo.updateBike(bike.id) { it.copy(rides = it.rides + Ride(LocalDate.now().toString(), km, trigger, device, at = System.currentTimeMillis())) }
            ReminderWorker.runNow(this)
            Tracker.notify(this, ID_DONE, NotificationCompat.Builder(this, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle("Salida registrada: ${"%.1f".format(km)} km").setContentText("${bike.brand} ${bike.model} · ${Ride("", km, trigger, device).sourceLabel.lowercase()}")
                .setContentIntent(PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true).build())
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (!finished) finish(); super.onDestroy() }
}

class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        Repo.init(ctx)
        if (!Repo.garage.value.tracking.enabled) return
        val last = ActivityTransitionResult.extractResult(intent)!!.transitionEvents.lastOrNull { it.activityType == DetectedActivity.IN_VEHICLE } ?: return
        if (last.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) Tracker.start(ctx, RideTrigger.motion)
        else if (Tracker.live.value?.trigger == RideTrigger.motion) Tracker.stop(ctx)
    }
}

/** Helmet intercom or bike connects → ride starts; disconnects → ride ends. */
class BluetoothReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val t = Repo.garage.value.tracking
        val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
        if (!t.enabled || dev.address !in t.devices) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> Tracker.start(ctx, RideTrigger.bluetooth, t.devices[dev.address])
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (Tracker.live.value?.trigger == RideTrigger.bluetooth) Tracker.stop(ctx)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Tracker.register(ctx)
    }
}

fun createRideChannel(ctx: Context) {
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_RIDE, "Salidas", NotificationManager.IMPORTANCE_LOW).apply { description = "Registro de kilómetros de cada salida" }
    )
}
