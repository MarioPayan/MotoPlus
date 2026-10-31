package com.kaze.motoplus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kaze.motoplus.R
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.RideTrigger
import com.kaze.motoplus.domain.Tracking
import com.kaze.motoplus.domain.Feature
import com.kaze.motoplus.work.Tracker

/** Km tab: the live ride or a manual start, and the way into ctx.getString(R.string.sources) with what is switched on. */
@Composable
fun TrackingSummary(vm: AppViewModel) {
    val ctx = LocalContext.current
    val shop = LocalShop.current
    val live by Tracker.live.collectAsState()
    val t = vm.garage.tracking
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) Tracker.start(ctx, RideTrigger.manual)
    }
    Sheet {
        live?.let { r ->
            Text(ctx.getString(R.string.live_ride, decimal(r.km)), fontWeight = FontWeight.SemiBold, color = shop.accent)
            Hint(ctx.getString(when (r.trigger) { RideTrigger.manual -> R.string.manual; RideTrigger.motion -> R.string.motion; RideTrigger.bluetooth -> R.string.bluetooth }))
            Btn(ctx.getString(R.string.finish_ride), { Tracker.stop(ctx) }, Modifier.fillMaxWidth())
        } ?: Btn(ctx.getString(R.string.start_ride), {
            if (Tracker.hasLocation(ctx)) Tracker.start(ctx, RideTrigger.manual)
            else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, Modifier.fillMaxWidth(), ghost = true, icon = "route")
        HorizontalDivider(color = shop.line)
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = ctx.getString(R.string.open)) { vm.screen = Screen.kmSources }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ctx.getString(R.string.sources), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val on = listOfNotNull(
                    ctx.getString(R.string.motion).takeIf { t.motion }, ctx.getString(R.string.intercom).takeIf { t.bluetooth },
                    ctx.getString(R.string.no_car_rides).takeIf { t.motion && (t.carDevices.isNotEmpty() || t.androidAuto) },
                )
                Hint(on.joinToString(ctx.getString(R.string.or)).ifEmpty { ctx.getString(R.string.nothing_automatic) })
            }
            Icon("chev", shop.ink2, 16.dp)
        }
    }
}

private enum class Picker { intercom, car }

/** Every way the odometer estimate stays current, each switched on its own, with the permissions it needs. */
@Composable
fun KmSourcesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val shop = LocalShop.current
    val t = vm.garage.tracking
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val loc = remember(tick) { Tracker.hasLocation(ctx) }
    val bg = remember(tick) { Tracker.hasBackground(ctx) }
    val act = remember(tick) { Tracker.hasActivity(ctx) }
    val battery = remember(tick) { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) }
    // Settings waiting for the location disclosure, then for the permissions it leads to.
    var disclosure by remember { mutableStateOf<Tracking?>(null) }
    var pending by remember { mutableStateOf<Tracking?>(null) }
    var picking by remember { mutableStateOf<Picker?>(null) }
    var pickAfterPermission by remember { mutableStateOf<Picker?>(null) }

    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun save() {
        pending?.let { vm.setTracking(it) }
        pending = null
        if (!Tracker.hasBackground(ctx)) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    // Denied activity recognition still saves: the missing permission shows as a hint below.
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; save() }
    fun afterLocation() {
        if (pending?.motion == true && !Tracker.hasActivity(ctx)) askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION) else save()
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) afterLocation() else pending = null
    }
    fun withPermissions(next: Tracking) {
        pending = next
        if (Tracker.hasLocation(ctx)) afterLocation()
        else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    /** Turning on the first detector explains location use before Android asks for it. */
    fun enable(next: Tracking) { if (t.detects) withPermissions(next) else disclosure = next }

    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) picking = pickAfterPermission }
    fun pick(p: Picker) {
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) picking = p
        else { pickAfterPermission = p; askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    }

    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(ctx.getString(R.string.sources), { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hint(ctx.getString(R.string.sources_intro))

            if (vm.prefs.on(Feature.kmAuto)) Sheet {
                Cap(ctx.getString(R.string.auto_rides))
                Toggle(ctx.getString(R.string.motion), ctx.getString(R.string.motion_hint), t.motion) { on ->
                    if (on) enable(t.copy(motion = true)) else vm.setTracking(t.copy(motion = false))
                }
                HorizontalDivider(color = shop.line)
                Toggle(ctx.getString(R.string.bluetooth_title), ctx.getString(R.string.bluetooth_hint), t.bluetooth) { on ->
                    if (on) enable(t.copy(bluetooth = true)) else vm.setTracking(t.copy(bluetooth = false))
                }
                if (t.bluetooth) {
                    if (t.devices.isEmpty()) Hint(ctx.getString(R.string.no_devices))
                    t.devices.values.forEach { Text(ctx.getString(R.string.p3_bullet, it), fontSize = 13.sp) }
                    TextButton({ pick(Picker.intercom) }, contentPadding = PaddingValues(0.dp)) { Text(if (t.devices.isEmpty()) ctx.getString(R.string.choose_helmet) else ctx.getString(R.string.change_devices)) }
                }
                if (t.detects) {
                    val missing = listOfNotNull(
                        ctx.getString(R.string.permission_location).takeIf { !loc }, ctx.getString(R.string.permission_background).takeIf { !bg }, ctx.getString(R.string.permission_activity).takeIf { t.motion && !act },
                    )
                    if (missing.isNotEmpty()) {
                        Hint(ctx.getString(R.string.missing_permissions, missing.joinToString(", ")), shop.bad)
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }, contentPadding = PaddingValues(0.dp)) {
                            Text(ctx.getString(R.string.permissions))
                        }
                    }
                    if (!battery) {
                        Hint(ctx.getString(R.string.battery_hint) + brandHint(ctx), shop.warn)
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, contentPadding = PaddingValues(0.dp)) { Text(ctx.getString(R.string.battery_settings)) }
                    }
                }
            }

            if (vm.prefs.on(Feature.kmAuto)) Sheet {
                Cap(ctx.getString(R.string.car_only))
                Hint(ctx.getString(R.string.car_hint))
                Toggle(ctx.getString(R.string.car_bluetooth), ctx.getString(R.string.car_connected), t.carDevices.isNotEmpty()) { on ->
                    if (on) pick(Picker.car) else vm.setTracking(t.copy(carDevices = emptyMap()))
                }
                if (t.carDevices.isNotEmpty()) {
                    t.carDevices.values.forEach { Text(ctx.getString(R.string.p3_bullet, it), fontSize = 13.sp) }
                    TextButton({ pick(Picker.car) }, contentPadding = PaddingValues(0.dp)) { Text(ctx.getString(R.string.change_cars)) }
                }
                HorizontalDivider(color = shop.line)
                Toggle(ctx.getString(R.string.android_auto), ctx.getString(R.string.android_auto_hint), t.androidAuto) { on -> vm.setTracking(t.copy(androidAuto = on)) }
                if (!t.motion && (t.carDevices.isNotEmpty() || t.androidAuto)) Hint(ctx.getString(R.string.motion_only), shop.warn)
            }

            Sheet {
                Cap(ctx.getString(R.string.read_odo))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ctx.getString(R.string.photo_odo), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Hint(ctx.getString(R.string.photo_odo_hint))
                    }
                    Btn(ctx.getString(R.string.photo), { vm.readOdometerPhoto() }, ghost = true, icon = "camera")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    disclosure?.let { next ->
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(ctx.getString(R.string.location_title)) },
            text = {
                Text(ctx.getString(R.string.location_text))
            },
            confirmButton = { TextButton({ disclosure = null; withPermissions(next) }) { Text(ctx.getString(R.string.action_continue)) } },
            dismissButton = { TextButton({ disclosure = null }) { Text(ctx.getString(R.string.not_now)) } },
        )
    }
    when (picking) {
        Picker.intercom -> DevicePicker(ctx.getString(R.string.helmet_picker), ctx.getString(R.string.helmet_pair), t.devices,
            onDone = { vm.setTracking(vm.garage.tracking.copy(devices = it)); picking = null }, onDismiss = { picking = null })
        Picker.car -> DevicePicker(ctx.getString(R.string.car_picker), ctx.getString(R.string.car_pair), t.carDevices,
            onDone = { vm.setTracking(vm.garage.tracking.copy(carDevices = it)); picking = null }, onDismiss = { picking = null })
        null -> {}
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DevicePicker(title: String, noneHint: String, current: Map<String, String>, onDone: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val bonded = remember {
        runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty().associate { it.address to (it.name ?: it.address) } }
            .getOrDefault(emptyMap())
    }
    var chosen by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (bonded.isEmpty()) Hint(ctx.getString(R.string.no_paired_devices) + " " + noneHint)
                bonded.forEach { (addr, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(addr in chosen, { on -> chosen = if (on) chosen + (addr to name) else chosen - addr })
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDone(chosen) }) { Text(ctx.getString(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(ctx.getString(R.string.cancel)) } },
    )
}

private fun brandHint(ctx: Context) = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> ctx.getString(R.string.xiaomi_battery)
    "samsung" -> ctx.getString(R.string.samsung_battery)
    "huawei", "honor" -> ctx.getString(R.string.huawei_battery)
    else -> ""
}
