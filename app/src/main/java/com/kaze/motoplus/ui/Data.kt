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
import com.kaze.motoplus.R
import androidx.compose.ui.res.stringResource
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
            e is ApiException && e.statusCode == 10 -> ctx.getString(R.string.drive_setup_error)
            e is ApiException && e.statusCode == 7 -> ctx.getString(R.string.offline_error)
            e is DriveError && e.code == 401 -> ctx.getString(R.string.drive_expired_error)
            else -> e.message ?: ctx.getString(R.string.generic_error)
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
            withContext(Dispatchers.Main) { vm.message = ctx.getString(R.string.backup_exported) }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(historyCsv(com.kaze.motoplus.data.Repo.garage.value).toByteArray()) }
        vm.message = ctx.getString(R.string.history_exported)
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(ctx.getString(R.string.data), { vm.screen = Screen.settings })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("cloud", if (token != null) shop.ok else shop.ink2, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (token != null) ctx.getString(R.string.drive_connected) else ctx.getString(R.string.drive_disconnected), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (lastAt > 0) ctx.getString(R.string.backup_last_time, ago(ctx, lastAt)) else ctx.getString(R.string.no_backups),
                            style = Mono.copy(fontSize = 11.sp), color = if (lastAt > 0) shop.ok else shop.ink2,
                        )
                    }
                }
                Hint(ctx.getString(R.string.drive_hint))
                if (token == null) Btn(ctx.getString(R.string.connect_drive), { connect() }, Modifier.fillMaxWidth(), icon = "cloud")
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(ctx.getString(R.string.backup_now), { work(ctx.getString(R.string.uploading_backup)) { t -> backupToDrive(ctx, t); BackupPrefs.done(ctx); lastAt = BackupPrefs.lastAt(ctx) } }, Modifier.weight(1f), enabled = busy == null)
                    Btn(ctx.getString(R.string.restore), { work(ctx.getString(R.string.finding_backups)) { t -> val d = DriveClient(t); backups = d.list(d.folder()) } }, Modifier.weight(1f), ghost = true, enabled = busy == null)
                }
                Toggle(ctx.getString(R.string.daily_backup), ctx.getString(R.string.wifi_only), daily) { on ->
                    if (on && token == null) connect { daily = true; BackupPrefs.setEnabled(ctx, true); BackupWorker.schedule(ctx, true) }
                    else { daily = on; BackupPrefs.setEnabled(ctx, on); BackupWorker.schedule(ctx, on) }
                }
                busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Hint(it) } }
                error?.let { Hint(it, shop.bad) }
            }
            Cap(ctx.getString(R.string.files), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Sheet {
                DataRow("share", ctx.getString(R.string.export_backup), ctx.getString(R.string.export_hint)) { saveZip.launch("motoplus-${LocalDate.now()}.zip") }
                HorizontalDivider(color = shop.line)
                DataRow("plus", ctx.getString(R.string.import_backup), ctx.getString(R.string.import_hint)) { openZip.launch(arrayOf("application/zip", "application/octet-stream")) }
                HorizontalDivider(color = shop.line)
                DataRow("history", ctx.getString(R.string.export_csv), ctx.getString(R.string.csv_hint)) { saveCsv.launch("motoplus-historial-${LocalDate.now()}.csv") }
            }
            Hint(ctx.getString(R.string.local_data_hint), modifier = Modifier.padding(4.dp))
            Spacer(Modifier.height(8.dp))
        }
    }

    backups?.let { list ->
        AlertDialog(
            onDismissRequest = { backups = null },
            title = { Text(ctx.getString(R.string.restore_drive)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (list.isEmpty()) Text(ctx.getString(R.string.empty_drive))
                    list.forEach { f ->
                        Row(Modifier.fillMaxWidth().clickable { restoring = f; backups = null }.padding(vertical = 10.dp)) {
                            Text(f.name.removePrefix("motoplus-").removeSuffix(".zip"), style = Mono.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Text(ctx.getString(R.string.backup_size, (f.size.toLongOrNull() ?: 0) / 1024), style = Mono.copy(fontSize = 12.sp), color = shop.ink2)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ backups = null }) { Text(ctx.getString(R.string.close)) } },
        )
    }
    restoring?.let { f ->
        ConfirmReplace(ctx.getString(R.string.backup_of, f.name.removePrefix("motoplus-").removeSuffix(".zip")), { restoring = null }) {
            restoring = null
            work(ctx.getString(R.string.restoring_backup)) { t -> restoreBackup(ctx, DriveClient(t).download(f.id)) }
            vm.message = ctx.getString(R.string.backup_restored)
        }
    }
    importing?.let { bytes ->
        ConfirmReplace(ctx.getString(R.string.chosen_file), { importing = null }) {
            importing = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { restoreBackup(ctx, bytes) } }
                    .onSuccess { vm.message = ctx.getString(R.string.backup_imported) }.onFailure { error = ctx.getString(R.string.invalid_backup) }
            }
        }
    }
}

@Composable
private fun ConfirmReplace(what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.replace_data)) },
    text = { Text(stringResource(R.string.replace_data_text, what)) },
    confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.replace)) } },
    dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
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

private fun ago(ctx: android.content.Context, ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val mins = (System.currentTimeMillis() - ms) / 60_000
    return when {
        mins < 2 -> ctx.getString(R.string.now_time)
        mins < 60 -> ctx.getString(R.string.minutes_ago, mins)
        mins < 24 * 60 -> ctx.getString(R.string.hours_ago, mins / 60)
        else -> java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(ctx.resources.configuration.locales[0]).format(d)
    }
}
