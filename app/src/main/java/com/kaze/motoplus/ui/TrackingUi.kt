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
import androidx.compose.foundation.layout.*
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
import com.kaze.motoplus.work.Tracker

/** Ride detection: live ride, manual start, the automatic switch with its permissions, trigger devices and battery. */
@Composable
fun TrackingCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val shop = LocalShop.current
    val live by Tracker.live.collectAsState()
    val tracking = vm.garage.tracking
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val loc = remember(tick) { Tracker.hasLocation(ctx) }
    val bg = remember(tick) { Tracker.hasBackground(ctx) }
    val act = remember(tick) { Tracker.hasActivity(ctx) }
    val battery = remember(tick) { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) }
    var disclosure by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        if (!Tracker.hasBackground(ctx)) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        vm.setTracking(true)
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    val askLocationForManual = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) Tracker.start(ctx, com.kaze.motoplus.domain.RideTrigger.manual)
    }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) picking = true }

    Sheet {
        SectionTitle("Detección de salidas")
        live?.let { r ->
            Text("Registrando salida · ${"%.1f".format(r.km)} km", fontWeight = FontWeight.SemiBold, color = shop.accent)
            Hint(r.label)
            Button({ Tracker.stop(ctx) }, Modifier.fillMaxWidth()) { Text("Terminar salida") }
        } ?: OutlinedButton({
            if (loc) Tracker.start(ctx, com.kaze.motoplus.domain.RideTrigger.manual)
            else askLocationForManual.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, Modifier.fillMaxWidth()) { Text("Empezar salida ahora") }

        HorizontalDivider(color = shop.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Detectar salidas solas", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Hint("Empieza al ir en vehículo o al conectar tu casco, y termina al aparcar.")
            }
            Switch(tracking.enabled, { on -> if (on) disclosure = true else vm.setTracking(false) })
        }
        if (tracking.enabled) {
            val missing = listOfNotNull(
                "ubicación".takeIf { !loc }, "ubicación todo el tiempo".takeIf { !bg }, "actividad física".takeIf { !act },
            )
            if (missing.isNotEmpty()) {
                Hint("Falta permiso de ${missing.joinToString(", ")}.", shop.bad)
                TextButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Abrir permisos de MotoPlus")
                }
            }
            Text("Empezar al conectar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (tracking.devices.isEmpty()) Hint("Ningún dispositivo. Elige el intercomunicador del casco o el Bluetooth de la moto.")
            tracking.devices.values.forEach { Text("• $it", fontSize = 13.sp) }
            TextButton({
                if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) picking = true
                else askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }, contentPadding = PaddingValues(0.dp)) { Text(if (tracking.devices.isEmpty()) "Elegir casco o moto" else "Cambiar dispositivos") }
            if (!battery) {
                Hint("Para que Android no corte el registro, quita la optimización de batería de MotoPlus.${brandHint()}", shop.warn)
                TextButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, contentPadding = PaddingValues(0.dp)) { Text("Ajustes de batería") }
            }
        }
    }

    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text("Usar tu ubicación") },
        text = {
            Text("MotoPlus usa la ubicación para contar los kilómetros de cada salida, también cuando la app está cerrada o no la estás usando. " +
                "Solo guarda la distancia, nunca la ruta, y no sale de tu móvil. También usa el sensor de actividad para saber cuándo vas en vehículo.")
        },
        confirmButton = {
            TextButton({
                disclosure = false
                if (loc) { if (act) { vm.setTracking(true); if (!bg) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) } else askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Continuar") }
        },
        dismissButton = { TextButton({ disclosure = false }) { Text("Ahora no") } },
    )
    if (picking) DevicePicker(vm.garage.tracking.devices, onDone = { vm.setTrackedDevices(it); picking = false }, onDismiss = { picking = false })
}

@SuppressLint("MissingPermission")
@Composable
private fun DevicePicker(current: Map<String, String>, onDone: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val bonded = remember {
        runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty().associate { it.address to (it.name ?: it.address) } }
            .getOrDefault(emptyMap())
    }
    var chosen by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Casco o moto") },
        text = {
            Column {
                if (bonded.isEmpty()) Hint("No hay dispositivos emparejados. Empareja el intercomunicador en los ajustes de Bluetooth y vuelve.")
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
