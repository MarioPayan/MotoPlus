package com.kaze.motoplus.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.*
import com.kaze.motoplus.MainActivity
import com.kaze.motoplus.R
import com.kaze.motoplus.data.Prefs
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.domain.Bike
import com.kaze.motoplus.domain.Feature
import com.kaze.motoplus.domain.DiscardReason
import com.kaze.motoplus.domain.DistanceMeter
import com.kaze.motoplus.domain.MIN_RIDE_KM
import com.kaze.motoplus.domain.Ride
import com.kaze.motoplus.domain.RideCheckpoint
import com.kaze.motoplus.domain.RideTrigger
import com.kaze.motoplus.domain.EstimateDrop
import com.kaze.motoplus.domain.EstimateSession
import com.kaze.motoplus.domain.TrackingMode
import com.kaze.motoplus.domain.discardReason
import com.kaze.motoplus.domain.endEstimate
import com.kaze.motoplus.domain.estimateIsOver
import com.kaze.motoplus.domain.isPastEstimateCap
import com.kaze.motoplus.domain.isParked
import com.kaze.motoplus.domain.orphanRide
import com.kaze.motoplus.domain.rideKm
import com.kaze.motoplus.ui.rideSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val CHANNEL_RIDE = "rides"
private const val ID_LIVE = 4100
private const val ID_DONE = 4101

/** [km] stays 0 for an [estimated] ride: its km come from the clock, see [com.kaze.motoplus.domain.estimatedKm]. */
data class LiveRide(val trigger: RideTrigger, val device: String?, val km: Double, val startedAt: Long, val estimated: Boolean = false) {
    fun label(ctx: Context) = rideSource(Ride("", km, trigger, device, estimated = estimated), ctx)
}

// ponytail: tester-only ride log so a "0 km" can be diagnosed without root (release isn't debuggable), read with
// `adb pull /sdcard/Android/data/com.kaze.motoplus/files/salidas.txt`. Remove before a public release, like ui/Feedback.kt.
private fun rideLog(ctx: Context, trigger: RideTrigger, event: String, km: Double, outcome: String = "", mode: TrackingMode = TrackingMode.precision) {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    runCatching { File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "salidas.txt").appendText("$stamp · $mode · $trigger · $event · ${"%.2f".format(Locale.ROOT, km)} km · $outcome\n") }
}

private fun rideOutcome(km: Double, discarded: DiscardReason?, kept: String) = when {
    discarded != null -> "discarded $discarded"
    km < MIN_RIDE_KM -> "too short"
    else -> kept
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

    /** Estimación needs no location to start a ride. */
    fun canStart(ctx: Context) = Repo.garage.value.tracking.mode == TrackingMode.estimate || hasLocation(ctx)

    fun start(ctx: Context, trigger: RideTrigger, device: String? = null) {
        // A session that lost its end event mustn't hold back the next ride; one still in its time keeps its clock.
        state.value?.let { if (it.estimated && isPastEstimateCap(it.startedAt, System.currentTimeMillis())) stop(ctx) }
        if (state.value != null) return
        if (Repo.garage.value.tracking.mode == TrackingMode.estimate) { startEstimate(ctx, trigger, device); return }
        if (!hasLocation(ctx)) return
        val i = Intent(ctx, RideService::class.java).putExtra("trigger", trigger.name).putExtra("device", device)
        try {
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            // Android refused a background start (battery optimisation on): ask the rider to start it.
            val open = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).putExtra("startRide", trigger.name), PendingIntent.FLAG_IMMUTABLE)
            notify(ctx, ID_DONE, NotificationCompat.Builder(ctx, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle(ctx.getString(R.string.ride_start_title)).setContentText(ctx.getString(R.string.ride_start_hint))
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    fun stop(ctx: Context) {
        val live = state.value ?: return
        if (live.estimated) endEstimate(ctx)
        else ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.STOP))
    }

    /** Listens for "in vehicle" start/stop from the phone's activity sensor while motion detection is on, and stops listening when it's off. */
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        // Hiding Detección automática de km stops listening; the rider's detector settings stay for when it comes back.
        if (!Repo.garage.value.tracking.motion || !Prefs.load(ctx).on(Feature.kmAuto)) { unregister(ctx); return }
        if (!hasActivity(ctx)) return
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

    /** One of the rider's cars connected: the live ride checks again whether it is a car trip. */
    fun carConnected(ctx: Context) {
        val live = state.value ?: return
        if (live.estimated) { estimateSawCar(ctx); return }
        runCatching { ctx.startService(Intent(ctx, RideService::class.java).setAction(RideService.CAR)) }
    }

    private fun checkpointFile(ctx: Context) = File(ctx.filesDir, "ride_checkpoint.json")

    /** Called on every location update, on the main thread: a rename survives a process kill without the cost of an fsync each time. */
    internal fun writeCheckpoint(ctx: Context, c: RideCheckpoint) = writeAtomically(ctx, checkpointFile(ctx), c.encode())

    private fun writeAtomically(ctx: Context, file: File, text: String) {
        val tmp = File(ctx.filesDir, file.name + ".tmp")
        runCatching { tmp.writeText(text); tmp.renameTo(file) }
    }

    internal fun deleteCheckpoint(ctx: Context) { checkpointFile(ctx).delete() }

    /**
     * Saves the ride a killed process left behind. Runs from [com.kaze.motoplus.MotoApp.onCreate], which every way back
     * into the process passes through and which always precedes [RideService.onStartCommand], so whatever
     * checkpoint is here belongs to a dead process, never to a live ride. The service is START_NOT_STICKY: nothing else revives it.
     */
    fun recoverOrphan(ctx: Context) {
        val file = checkpointFile(ctx)
        if (!file.exists()) return
        runCatching { file.readText() }.getOrNull()?.let(RideCheckpoint::decode)?.let { c ->
            val g = Repo.garage.value
            val ride = g.orphanRide(c, ZoneId.systemDefault())
            ride?.let { saveRide(ctx, g.bikes.first { it.id == c.bikeId }, it) }
            rideLog(ctx, c.trigger, "end", c.km, rideOutcome(c.km, discardReason(c.trigger, c.carBluetooth, c.androidAuto, g.tracking),
                if (ride != null) "recovered" else "not recovered: already saved or bike gone"))
        }
        file.delete()
    }

    // ---------- Estimación: a start time on disk, no service and no location ----------

    /** A separate file from the checkpoint, so [recoverOrphan] can never mistake an open session for a dead GPS ride. */
    private fun sessionFile(ctx: Context) = File(ctx.filesDir, "estimate_session.json")

    private fun readSession(ctx: Context) = runCatching { sessionFile(ctx).readText() }.getOrNull()?.let(EstimateSession::decode)

    private fun startEstimate(ctx: Context, trigger: RideTrigger, device: String?) {
        val g = Repo.garage.value
        val bike = g.selected ?: return
        val s = EstimateSession(bike.id, trigger, device, System.currentTimeMillis())
        writeAtomically(ctx, sessionFile(ctx), s.encode())
        state.value = LiveRide(trigger, device, 0.0, s.startedAt, estimated = true)
        rideLog(ctx, trigger, "start", 0.0, mode = TrackingMode.estimate)
        notifyEstimate(ctx)
        // A car that connected before the ride started sends no event now, so ask the phone once.
        if (trigger == RideTrigger.motion) whenCarConnected(ctx.applicationContext, g.tracking.carDevices.keys) { estimateSawCar(ctx) }
    }

    /** Puts an open session back after the process died: [live] and the Km tab read it as if nothing happened. */
    fun restoreEstimate(ctx: Context) {
        val s = readSession(ctx) ?: return
        state.value = LiveRide(s.trigger, s.device, 0.0, s.startedAt, estimated = true)
        if (Repo.garage.value.estimateIsOver(s, System.currentTimeMillis(), ZoneId.systemDefault())) endEstimate(ctx) else notifyEstimate(ctx)
    }

    private fun estimateSawCar(ctx: Context) {
        val s = readSession(ctx)?.copy(carBluetooth = true) ?: return
        writeAtomically(ctx, sessionFile(ctx), s.encode())
        if (discardReason(s.trigger, true, false, Repo.garage.value.tracking) != null) endEstimate(ctx)
    }

    private fun endEstimate(ctx: Context) {
        readSession(ctx)?.let { s ->
            val g = Repo.garage.value
            val end = g.endEstimate(s, System.currentTimeMillis(), ZoneId.systemDefault())
            end.ride?.let { saveRide(ctx, g.bikes.first { b -> b.id == s.bikeId }, it) }
            rideLog(ctx, s.trigger, "end", end.km, end.dropped?.let { "dropped $it" } ?: "saved", TrackingMode.estimate)
            val why = when (end.dropped) {
                EstimateDrop.tooLong -> R.string.ride_too_long
                EstimateDrop.carBluetooth -> R.string.p3_car_connected
                else -> null
            }
            if (why != null) notify(ctx, ID_DONE, NotificationCompat.Builder(ctx, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle(ctx.getString(R.string.ride_discarded, ctx.getString(why))).setAutoCancel(true).build())
        }
        sessionFile(ctx).delete()
        state.value = null
        NotificationManagerCompat.from(ctx).cancel(ID_LIVE)
    }

    private fun notifyEstimate(ctx: Context) {
        val stop = PendingIntent.getBroadcast(ctx, 12, Intent(ctx, EstimateStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, ID_LIVE, NotificationCompat.Builder(ctx, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle(ctx.getString(R.string.ride_estimating)).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, ctx.getString(R.string.ride_stop), stop).build())
    }

    /** Asks the phone once whether one of [cars] is connected: a car connects for calls (headset profile) and music (A2DP); either one counts. */
    @SuppressLint("MissingPermission")
    internal fun whenCarConnected(ctx: Context, cars: Set<String>, then: () -> Unit) {
        if (cars.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    val connected = runCatching { proxy.connectedDevices.any { it.address in cars } }.getOrDefault(false)
                    adapter.closeProfileProxy(p, proxy)
                    if (connected) then()
                }
                override fun onServiceDisconnected(p: Int) {}
            }, profile)
        }
    }

    internal fun saveRide(ctx: Context, bike: Bike, ride: Ride) {
        Repo.updateBike(bike.id) { it.copy(rides = it.rides + ride) }
        ReminderWorker.runNow(ctx)
        notify(ctx, ID_DONE, NotificationCompat.Builder(ctx, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle(ctx.getString(R.string.ride_recorded, "%.1f".format(ride.km))).setContentText("${bike.brand} ${bike.model} · ${rideSource(ride, ctx).lowercase()}")
            .setContentIntent(PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).build())
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
 * after 6 minutes without movement (parked), or unsaved as soon as a motion ride turns out to be a car trip.
 */
class RideService : Service() {
    companion object {
        const val STOP = "com.kaze.motoplus.STOP_RIDE"
        const val CAR = "com.kaze.motoplus.CAR_CONNECTED"
    }

    private val meter = DistanceMeter()
    private var trigger = RideTrigger.manual
    private var device: String? = null
    private var startedAt = 0L
    // Car signals for this ride. Read from the phone when the ride starts, never kept across process deaths.
    private var carBluetooth = false
    private var androidAuto = false
    private var carConnection: LiveData<Int>? = null
    private val autoObserver = Observer<Int> { type ->
        androidAuto = type == CarConnection.CONNECTION_TYPE_PROJECTION
        checkCar()
    }
    private lateinit var client: FusedLocationProviderClient
    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            for (l in r.locations) meter.add(l.latitude, l.longitude, l.accuracy, l.time)
            Tracker.set(LiveRide(trigger, device, km(), startedAt))
            Tracker.notify(this@RideService, ID_LIVE, liveNotification())
            checkpoint()
        }
    }
    // Lives outside the location callback: with a minimum update distance a parked phone gets no callbacks at all.
    private val handler = Handler(Looper.getMainLooper())
    private val parkedCheck = object : Runnable {
        override fun run() {
            if (isParked(System.currentTimeMillis(), meter.lastMovingAt, startedAt)) finish()
            else handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish(); return START_NOT_STICKY }
        if (intent?.action == CAR) {
            if (startedAt == 0L) stopSelf() else { carBluetooth = true; checkCar() }
            return START_NOT_STICKY
        }
        if (startedAt != 0L) return START_NOT_STICKY
        Repo.init(this)
        trigger = intent?.getStringExtra("trigger")?.let { runCatching { RideTrigger.valueOf(it) }.getOrNull() } ?: RideTrigger.manual
        device = intent?.getStringExtra("device")
        startedAt = System.currentTimeMillis()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, ID_LIVE, liveNotification(), type)
        Tracker.set(LiveRide(trigger, device, 0.0, startedAt))
        rideLog(this, trigger, "start", 0.0)
        checkpoint()
        client = LocationServices.getFusedLocationProviderClient(this)
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).build(),
            callback, Looper.getMainLooper(),
        )
        handler.postDelayed(parkedCheck, 60_000)
        if (trigger == RideTrigger.motion) watchCars()
        return START_NOT_STICKY
    }

    /** Asks the phone whether a car is connected now, and keeps watching Android Auto for the rest of the ride. */
    private fun watchCars() {
        val t = Repo.garage.value.tracking
        if (t.androidAutoInCar) carConnection = CarConnection(this).type.also { it.observeForever(autoObserver) }
        Tracker.whenCarConnected(this, t.carDevices.keys) { carBluetooth = true; checkCar() }
    }

    /** The ride's km so far, from the last reading the rider typed during it if there is one. */
    private fun km() = Repo.garage.value.selected?.rideKm(startedAt, meter) ?: meter.km

    private fun checkpoint() {
        val bike = Repo.garage.value.selected ?: return
        Tracker.writeCheckpoint(this, RideCheckpoint(bike.id, trigger, device, bike.rideKm(startedAt, meter), startedAt, System.currentTimeMillis(), carBluetooth, androidAuto))
    }

    private fun checkCar() {
        if (discardReason(trigger, carBluetooth, androidAuto, Repo.garage.value.tracking) != null) finish()
    }

    private fun liveNotification(): Notification {
        val stop = PendingIntent.getService(this, 3, Intent(this, RideService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
            .setContentTitle(getString(R.string.ride_recording, "%.1f".format(km())))
            .setContentText(Tracker.live.value?.label(this) ?: "").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, getString(R.string.ride_stop), stop).build()
    }

    private var finished = false
    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(parkedCheck)
        if (::client.isInitialized) client.removeLocationUpdates(callback)
        carConnection?.removeObserver(autoObserver)
        Tracker.set(null)
        val bike = Repo.garage.value.selected
        val km = km()
        val discarded = discardReason(trigger, carBluetooth, androidAuto, Repo.garage.value.tracking)
        if (discarded != null) {
            Tracker.notify(this, ID_DONE, NotificationCompat.Builder(this, CHANNEL_RIDE).setSmallIcon(R.drawable.ic_stat_moto)
                .setContentTitle(getString(R.string.ride_discarded, getString(if (discarded == DiscardReason.carBluetooth) R.string.p3_car_connected else R.string.android_auto))).setAutoCancel(true).build())
        } else if (km >= MIN_RIDE_KM && bike != null) {
            Tracker.saveRide(this, bike, Ride(LocalDate.now().toString(), km, trigger, device, at = System.currentTimeMillis(), durationMs = maxOf(meter.lastMovingAt, startedAt) - startedAt))
        }
        Tracker.deleteCheckpoint(this)
        if (startedAt != 0L) rideLog(this, trigger, "end", km, rideOutcome(km, discarded, if (bike != null) "saved" else "not saved: no bike"))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (!finished) finish(); super.onDestroy() }
}

/** Terminar on the Estimación notification: there is no service to send it to, and the process may be long gone. */
class EstimateStopReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Tracker.stop(ctx)
}

class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        Repo.init(ctx)
        if (!Repo.garage.value.tracking.motion || !Prefs.load(ctx).on(Feature.kmAuto)) return
        val last = ActivityTransitionResult.extractResult(intent)!!.transitionEvents.lastOrNull { it.activityType == DetectedActivity.IN_VEHICLE } ?: return
        if (last.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) Tracker.start(ctx, RideTrigger.motion)
        else if (Tracker.live.value?.trigger == RideTrigger.motion) Tracker.stop(ctx)
    }
}

/**
 * Helmet intercom or bike connects → ride starts; disconnects → ride ends.
 * One of the rider's cars connects → a motion ride in progress is a car trip.
 */
class BluetoothReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val t = Repo.garage.value.tracking
        if (!Prefs.load(ctx).on(Feature.kmAuto)) return
        val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
        when {
            t.bluetooth && dev.address in t.devices -> when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> Tracker.start(ctx, RideTrigger.bluetooth, t.devices[dev.address])
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (Tracker.live.value?.trigger == RideTrigger.bluetooth) Tracker.stop(ctx)
            }
            dev.address in t.carDevices && intent.action == BluetoothDevice.ACTION_ACL_CONNECTED -> Tracker.carConnected(ctx)
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
        NotificationChannel(CHANNEL_RIDE, ctx.getString(R.string.ride_channel), NotificationManager.IMPORTANCE_LOW).apply { description = ctx.getString(R.string.ride_channel_hint) }
    )
}
