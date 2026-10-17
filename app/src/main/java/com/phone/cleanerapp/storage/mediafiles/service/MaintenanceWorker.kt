package com.phone.cleanerapp.storage.mediafiles.service

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phone.cleanerapp.storage.mediafiles.MainActivity
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.data.JunkScanner
import com.phone.cleanerapp.storage.mediafiles.data.JunkType
import java.util.concurrent.TimeUnit

/** Weekly job: auto-clean safe junk, or remind the user when junk has piled up. */
class MaintenanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs.get(applicationContext)
        if (!Perms.hasFiles(applicationContext)) return Result.success()
        val cats = JunkScanner.scan(applicationContext) { }
        val total = cats.sumOf { it.bytes }
        if (prefs.autoClean) {
            // Never auto-delete installers; the user might still want them.
            cats.forEach { it.selected = it.type != JunkType.APK }
            val stats = JunkScanner.clean(cats)
            notify(tr("Auto-clean finished"), trf("Freed %1\$s of junk.", Fmt.size(stats.bytes)))
        } else if (prefs.junkReminder && total > 1024L * 1024 * 1024) {
            notify(tr("Junk is piling up"), trf("%1\$s can be cleaned safely.", Fmt.size(total)))
        }
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val ctx = applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_clean)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(1001, n)
    }

    companion object {
        const val CHANNEL = "cleaning"
        private const val NAME = "maintenance"

        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (enabled) {
                wm.enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<MaintenanceWorker>(7, TimeUnit.DAYS).build()
                )
            } else wm.cancelUniqueWork(NAME)
        }
    }
}
