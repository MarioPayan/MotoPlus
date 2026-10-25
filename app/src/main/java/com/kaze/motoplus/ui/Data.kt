package com.kaze.motoplus.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.Screen
import com.kaze.motoplus.data.*
import com.kaze.motoplus.domain.BackupArchive
import com.kaze.motoplus.domain.historyCsv
import com.kaze.motoplus.work.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 12B: Google Drive backup, plus export and import as files. */
@Composable
fun DataScreen(vm: AppViewModel) {
    val shop = LocalShop.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(BackupPrefs.lastError(ctx)) }
    var daily by remember { mutableStateOf(BackupPrefs.enabled(ctx)) }
    var lastAt by remember { mutableLongStateOf(BackupPrefs.lastAt(ctx)) }
    var backups by remember { mutableStateOf<List<DriveFile>?>(null) }
    var restoring by remember { mutableStateOf<DriveFile?>(null) }
    var importing by remember { mutableStateOf<ByteArray?>(null) }
    var after by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun fail(e: Throwable) {
        error = when {
            e is ApiException && e.statusCode == 10 -> "Drive aún no está configurado para esta app (falta el cliente OAuth en Google Cloud)."
            e is ApiException && e.statusCode == 7 -> "Sin conexión. Inténtalo con internet."
            e is DriveError && e.code == 401 -> "El acceso a Drive caducó. Vuelve a conectar."
            else -> e.message ?: "Algo falló."
        }
        busy = null
    }
    fun work(label: String, block: suspend (String) -> Unit) {
        val t = token ?: return
        busy = label; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block(t) } }.onFailure(::fail).onSuccess { busy = null }
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        runCatching { Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data).accessToken }
            .onSuccess { t -> token = t; t?.let { tk -> after?.invoke(tk) }; after = null }.onFailure(::fail)
    }
    /** Gets a token, asking for consent the first time, then runs [then]. */
    fun connect(then: (String) -> Unit = {}) {
        error = null
        Identity.getAuthorizationClient(ctx).authorize(driveAuthRequest())
            .addOnSuccessListener { r ->
                if (r.hasResolution()) { after = then; consent.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build()) }
                else { token = r.accessToken; r.accessToken?.let(then) }
            }
            .addOnFailureListener(::fail)
    }
    LaunchedEffect(Unit) { if (BackupPrefs.enabled(ctx)) runCatching { withContext(Dispatchers.IO) { silentDriveToken(ctx) } }.onSuccess { token = it } }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val g = com.kaze.motoplus.data.Repo.garage.value
            val files = BackupArchive.photos(g).mapNotNull { p -> File(p).takeIf { it.exists() }?.let { p to it.readBytes() } }.toMap()
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(BackupArchive.pack(g, ctx.filesDir.path, files)) }
            withContext(Dispatchers.Main) { vm.message = "Copia exportada" }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(historyCsv(com.kaze.motoplus.data.Repo.garage.value).toByteArray()) }
        vm.message = "Historial exportado"
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header("Datos", { vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("cloud", if (token != null) shop.ok else shop.ink2, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (token != null) "Google Drive conectado" else "Google Drive sin conectar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (lastAt > 0) "Última copia: ${ago(lastAt)}" else "Aún no hay copias",
                            style = Mono.copy(fontSize = 11.sp), color = if (lastAt > 0) shop.ok else shop.ink2,
                        )
                    }
                }
                Hint("Las copias van a una carpeta MotoPlus/ de tu Drive: un archivo por día con los datos y las fotos de facturas y papeles. La app solo ve los archivos que crea ella.")
                if (token == null) Btn("Conectar Google Drive", { connect() }, Modifier.fillMaxWidth(), icon = "cloud")
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Copia ahora", { work("Subiendo copia…") { t -> backupToDrive(ctx, t); BackupPrefs.done(ctx); lastAt = BackupPrefs.lastAt(ctx) } }, Modifier.weight(1f), enabled = busy == null)
                    Btn("Restaurar", { work("Buscando copias…") { t -> val d = DriveClient(t); backups = d.list(d.folder()) } }, Modifier.weight(1f), ghost = true, enabled = busy == null)
                }
                Row(Modifier.fillMaxWidth().clickable {
                    val on = !daily
                    if (on && token == null) connect { daily = true; BackupPrefs.setEnabled(ctx, true); BackupWorker.schedule(ctx, true) }
                    else { daily = on; BackupPrefs.setEnabled(ctx, on); BackupWorker.schedule(ctx, on) }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Copia diaria en Drive", fontSize = 14.sp); Hint("Solo con Wi-Fi") }
                    Switch(daily, null)
                }
                busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Hint(it) } }
                error?.let { Hint(it, shop.bad) }
            }
            Cap("Archivos", modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Sheet {
                DataRow("share", "Exportar copia", "Un .zip con todo; guárdalo donde quieras, también en Drive") { saveZip.launch("motoplus-${LocalDate.now()}.zip") }
                HorizontalDivider(color = shop.line)
                DataRow("plus", "Importar copia", "Desde un .zip exportado por MotoPlus") { openZip.launch(arrayOf("application/zip", "application/octet-stream")) }
                HorizontalDivider(color = shop.line)
                DataRow("history", "Historial en hoja de cálculo", "Mantenimientos y repostajes en .csv") { saveCsv.launch("motoplus-historial-${LocalDate.now()}.csv") }
            }
            Hint("Todo vive en este móvil; no hay servidor de MotoPlus. Android también incluye la app en la copia de seguridad de tu cuenta de Google.", modifier = Modifier.padding(4.dp))
            Spacer(Modifier.height(8.dp))
        }
    }

    backups?.let { list ->
        AlertDialog(
            onDismissRequest = { backups = null },
            title = { Text("RESTAURAR DESDE DRIVE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (list.isEmpty()) Text("No hay copias en tu Drive todavía.")
                    list.forEach { f ->
                        Row(Modifier.fillMaxWidth().clickable { restoring = f; backups = null }.padding(vertical = 10.dp)) {
                            Text(f.name.removePrefix("motoplus-").removeSuffix(".zip"), style = Mono.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Text("${(f.size.toLongOrNull() ?: 0) / 1024} KB", style = Mono.copy(fontSize = 12.sp), color = shop.ink2)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ backups = null }) { Text("CERRAR") } },
        )
    }
    restoring?.let { f ->
        ConfirmReplace("de la copia del ${f.name.removePrefix("motoplus-").removeSuffix(".zip")}", { restoring = null }) {
            restoring = null
            work("Restaurando…") { t -> restoreBackup(ctx, DriveClient(t).download(f.id)) }
            vm.message = "Copia restaurada"
        }
    }
    importing?.let { bytes ->
        ConfirmReplace("del archivo elegido", { importing = null }) {
            importing = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { restoreBackup(ctx, bytes) } }
                    .onSuccess { vm.message = "Copia importada" }.onFailure { error = "Ese archivo no es una copia de MotoPlus." }
            }
        }
    }
}

@Composable
private fun ConfirmReplace(what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("REEMPLAZAR DATOS") },
    text = { Text("Las motos, historiales y papeles de este móvil se cambian por los $what. Si quieres conservar lo actual, exporta una copia antes.") },
    confirmButton = { TextButton(onConfirm) { Text("REEMPLAZAR") } },
    dismissButton = { TextButton(onDismiss) { Text("CANCELAR") } },
)

@Composable
private fun DataRow(icon: String, title: String, sub: String, onClick: () -> Unit) {
    val shop = LocalShop.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, shop.ink2, 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Hint(sub) }
        Icon("chev", shop.ink2, 16.dp)
    }
}

private fun ago(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val mins = (System.currentTimeMillis() - ms) / 60_000
    return when {
        mins < 2 -> "ahora mismo"
        mins < 60 -> "hace $mins min"
        mins < 24 * 60 -> "hace ${mins / 60} h"
        else -> prettyDate(d.toLocalDate().toString())
    }
}
