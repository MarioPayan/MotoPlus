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
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.domain.RideTrigger
import com.kaze.motoplus.domain.Tracking
import com.kaze.motoplus.work.Tracker

/** Km tab: the live ride or a manual start, and the way into "Mantener al día el km" with what is switched on. */
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
            Text("Registrando salida · ${"%.1f".format(r.km)} km", fontWeight = FontWeight.SemiBold, color = shop.accent)
            Hint(r.label)
            Btn("Terminar salida", { Tracker.stop(ctx) }, Modifier.fillMaxWidth())
        } ?: Btn("Empezar salida ahora", {
            if (Tracker.hasLocation(ctx)) Tracker.start(ctx, RideTrigger.manual)
            else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, Modifier.fillMaxWidth(), ghost = true, icon = "route")
        HorizontalDivider(color = shop.line)
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir") { vm.screen = Screen.kmSources }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mantener al día el km", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val on = listOfNotNull(
                    "Sensor de movimiento".takeIf { t.motion }, "Intercomunicador".takeIf { t.bluetooth },
                    "Sin viajes en coche".takeIf { t.motion && (t.carDevices.isNotEmpty() || t.androidAuto) },
                )
                Hint(on.joinToString(" · ").ifEmpty { "Nada automático: cuentan las salidas que empiezas tú, las fotos y los repostajes." })
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
        Header("Mantener al día el km", { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hint("Cada método suma o corrige los km de la moto. Activa los que uses; los repostajes y las lecturas que escribas también cuentan.")

            Sheet {
                Cap("Contar salidas solas")
                Toggle("Sensor de movimiento", "Empieza al ir en vehículo y termina al aparcar.", t.motion) { on ->
                    if (on) enable(t.copy(motion = true)) else vm.setTracking(t.copy(motion = false))
                }
                HorizontalDivider(color = shop.line)
                Toggle("Intercomunicador o Bluetooth de la moto", "Empieza al conectar el casco o la moto y termina al desconectar.", t.bluetooth) { on ->
                    if (on) enable(t.copy(bluetooth = true)) else vm.setTracking(t.copy(bluetooth = false))
                }
                if (t.bluetooth) {
                    if (t.devices.isEmpty()) Hint("Ningún dispositivo. Elige el intercomunicador del casco o el Bluetooth de la moto.")
                    t.devices.values.forEach { Text("• $it", fontSize = 13.sp) }
                    TextButton({ pick(Picker.intercom) }, contentPadding = PaddingValues(0.dp)) { Text(if (t.devices.isEmpty()) "Elegir casco o moto" else "Cambiar dispositivos") }
                }
                if (t.detects) {
                    val missing = listOfNotNull(
                        "ubicación".takeIf { !loc }, "ubicación todo el tiempo".takeIf { !bg }, "actividad física".takeIf { t.motion && !act },
                    )
                    if (missing.isNotEmpty()) {
                        Hint("Falta permiso de ${missing.joinToString(", ")}.", shop.bad)
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Abrir permisos de MotoPlus")
                        }
                    }
                    if (!battery) {
                        Hint("Para que Android no corte el registro, quita la optimización de batería de MotoPlus.${brandHint()}", shop.warn)
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, contentPadding = PaddingValues(0.dp)) { Text("Ajustes de batería") }
                    }
                }
            }

            Sheet {
                Cap("Solo la moto, no el coche")
                Hint("El sensor de movimiento no distingue el coche de la moto. Si va en coche, la salida se descarta. Las del casco y las que empiezas tú cuentan siempre.")
                Toggle("Bluetooth del coche", "Mientras esté conectado, no cuenta.", t.carDevices.isNotEmpty()) { on ->
                    if (on) pick(Picker.car) else vm.setTracking(t.copy(carDevices = emptyMap()))
                }
                if (t.carDevices.isNotEmpty()) {
                    t.carDevices.values.forEach { Text("• $it", fontSize = 13.sp) }
                    TextButton({ pick(Picker.car) }, contentPadding = PaddingValues(0.dp)) { Text("Cambiar coches") }
                }
                HorizontalDivider(color = shop.line)
                Toggle("Android Auto", "Mientras el móvil esté en Android Auto, no cuenta.", t.androidAuto) { on -> vm.setTracking(t.copy(androidAuto = on)) }
                if (!t.motion && (t.carDevices.isNotEmpty() || t.androidAuto)) Hint("Solo se aplica con el sensor de movimiento activado.", shop.warn)
            }

            Sheet {
                Cap("Leer el cuadro")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Foto del cuadro", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Hint("Lee el odómetro de una foto y corrige la estimación.")
                    }
                    Btn("Foto", { vm.readOdometerPhoto() }, ghost = true, icon = "camera")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    disclosure?.let { next ->
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text("Usar tu ubicación") },
            text = {
                Text("MotoPlus usa la ubicación para contar los kilómetros de cada salida, también cuando la app está cerrada o no la estás usando. " +
                    "Solo guarda la distancia, nunca la ruta, y no sale de tu móvil. También usa el sensor de actividad para saber cuándo vas en vehículo.")
            },
            confirmButton = { TextButton({ disclosure = null; withPermissions(next) }) { Text("Continuar") } },
            dismissButton = { TextButton({ disclosure = null }) { Text("Ahora no") } },
        )
    }
    when (picking) {
        Picker.intercom -> DevicePicker("Casco o moto", "Empareja el intercomunicador en los ajustes de Bluetooth y vuelve.", t.devices,
            onDone = { vm.setTracking(vm.garage.tracking.copy(devices = it)); picking = null }, onDismiss = { picking = null })
        Picker.car -> DevicePicker("Tu coche", "Empareja el coche en los ajustes de Bluetooth y vuelve.", t.carDevices,
            onDone = { vm.setTracking(vm.garage.tracking.copy(carDevices = it)); picking = null }, onDismiss = { picking = null })
        null -> {}
    }
}

@Composable
private fun Toggle(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Hint(hint)
        }
        Switch(on, onChange)
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
                if (bonded.isEmpty()) Hint("No hay dispositivos emparejados. $noneHint")
                bonded.forEach { (addr, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(addr in chosen, { on -> chosen = if (on) chosen + (addr to name) else chosen - addr })
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDone(chosen) }) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

private fun brandHint() = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> " En Xiaomi, activa también \"Inicio automático\" y pon la batería en \"Sin restricciones\"."
    "samsung" -> " En Samsung, saca MotoPlus de \"Aplicaciones en suspensión\"."
    "huawei", "honor" -> " En Huawei, permite el \"Inicio manual\" en Inicio de aplicaciones."
    else -> ""
}
