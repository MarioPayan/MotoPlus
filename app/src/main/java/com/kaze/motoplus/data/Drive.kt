package com.kaze.motoplus.data

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.kaze.motoplus.domain.BackupArchive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** drive.file: the app only ever sees the files it created itself. */
const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

fun driveAuthRequest(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()

/** Blocking: a token if the rider already allowed Drive, null if consent is needed (only the screen can ask). */
fun silentDriveToken(ctx: Context): String? {
    val r: AuthorizationResult = Tasks.await(Identity.getAuthorizationClient(ctx).authorize(driveAuthRequest()))
    return if (r.hasResolution()) null else r.accessToken
}

@Serializable data class DriveFile(val id: String, val name: String, val createdTime: String = "", val size: String = "0")
@Serializable private data class FileList(val files: List<DriveFile> = emptyList())

/** The few Drive v3 calls a backup needs, over plain HTTPS. */
class DriveClient(private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://www.googleapis.com/drive/v3/files"

    private fun call(url: String, method: String = "GET", body: ByteArray? = null, type: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", type); c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        if (code !in 200..299) throw DriveError(code, c.errorStream?.readBytes()?.decodeToString().orEmpty())
        return c.inputStream.use { it.readBytes() }
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The app's MotoPlus/ folder, created the first time. */
    fun folder(): String {
        val found = json.decodeFromString<FileList>(call("$api?spaces=drive&fields=files(id,name)&q=" +
            q("name='MotoPlus' and mimeType='application/vnd.google-apps.folder' and trashed=false")).decodeToString()).files.firstOrNull()
        if (found != null) return found.id
        val made = call("$api?fields=id,name", "POST", """{"name":"MotoPlus","mimeType":"application/vnd.google-apps.folder"}""".toByteArray(), "application/json")
        return json.decodeFromString<DriveFile>(made.decodeToString()).id
    }

    fun list(folder: String): List<DriveFile> = json.decodeFromString<FileList>(
        call("$api?orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)&q=" + q("'$folder' in parents and trashed=false")).decodeToString()
    ).files

    fun upload(folder: String, name: String, bytes: ByteArray, mime: String): DriveFile {
        val b = "motoplus${System.nanoTime()}"
        val meta = """{"name":"$name","parents":["$folder"]}"""
        val body = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: $mime\r\n\r\n".toByteArray() + bytes + "\r\n--$b--\r\n".toByteArray()
        val r = call("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,size", "POST", body, "multipart/related; boundary=$b")
        return json.decodeFromString(r.decodeToString())
    }

    fun download(id: String): ByteArray = call("$api/$id?alt=media")

    fun delete(id: String) { call("$api/$id", "DELETE") }
}

class DriveError(val code: Int, body: String) : Exception("Drive $code: ${body.take(200)}")

/** Backups kept in Drive; older ones are deleted after each upload. */
private const val KEEP = 7

/** Uploads today's backup (one per day, replacing an earlier one from today) and trims old ones. */
fun backupToDrive(ctx: Context, token: String): DriveFile {
    val g = Repo.garage.value
    val root = ctx.filesDir.path
    val files = BackupArchive.photos(g).mapNotNull { p -> File(p).takeIf { it.exists() }?.let { p to it.readBytes() } }.toMap()
    val zip = BackupArchive.pack(g, root, files)
    val d = DriveClient(token)
    val folder = d.folder()
    val name = "motoplus-${LocalDate.now()}.zip"
    val existing = d.list(folder)
    existing.filter { it.name == name }.forEach { d.delete(it.id) }
    val up = d.upload(folder, name, zip, "application/zip")
    existing.filter { it.name != name && it.name.startsWith("motoplus-") }.drop(KEEP - 1).forEach { d.delete(it.id) }
    return up
}

/** Replaces the garage and photos on this phone with a backup's. */
fun restoreBackup(ctx: Context, zip: ByteArray) {
    val u = BackupArchive.unpack(zip, ctx.filesDir.path)
    for ((path, bytes) in u.files) File(path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    Repo.update { u.garage }
}

/** Per-phone backup settings and status. */
object BackupPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("enabled", on).apply()
    fun lastAt(ctx: Context) = p(ctx).getLong("lastAt", 0)
    fun lastError(ctx: Context) = p(ctx).getString("lastError", null)
    fun done(ctx: Context) = p(ctx).edit().putLong("lastAt", System.currentTimeMillis()).remove("lastError").apply()
    fun failed(ctx: Context, why: String) = p(ctx).edit().putString("lastError", why).apply()
}
