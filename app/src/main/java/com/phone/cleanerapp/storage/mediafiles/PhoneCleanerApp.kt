package com.phone.cleanerapp.storage.mediafiles

import com.app.GlobalApp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker

class PhoneCleanerApp : GlobalApp() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)
        com.phone.cleanerapp.storage.mediafiles.core.Tr.refresh(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(MaintenanceWorker.CHANNEL, "Cleaning reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        MaintenanceWorker.schedule(this, prefs.autoClean || prefs.junkReminder)
    }
}
