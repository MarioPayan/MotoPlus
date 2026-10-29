package com.kaze.motoplus.work

import android.content.Context
import androidx.work.*
import com.kaze.motoplus.R
import com.kaze.motoplus.data.BackupPrefs
import com.kaze.motoplus.data.Repo
import com.kaze.motoplus.data.backupToDrive
import com.kaze.motoplus.data.silentDriveToken
import java.util.concurrent.TimeUnit

/** Daily backup to the rider's Drive, on Wi-Fi only. Skips quietly when Drive access has to be granted again. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        if (!BackupPrefs.enabled(applicationContext)) return Result.success()
        return try {
            val token = silentDriveToken(applicationContext) ?: run { BackupPrefs.failed(applicationContext, applicationContext.getString(R.string.drive_expired_error)); return Result.success() }
            backupToDrive(applicationContext, token)
            BackupPrefs.done(applicationContext)
            Result.success()
        } catch (e: Exception) {
            BackupPrefs.failed(applicationContext, e.message ?: applicationContext.getString(R.string.backup_network_error))
            Result.retry()
        }
    }

    companion object {
        fun schedule(ctx: Context, on: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!on) { wm.cancelUniqueWork("backup"); return }
            wm.enqueueUniquePeriodicWork(
                "backup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build(),
            )
        }
    }
}
