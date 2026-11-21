package com.phone.cleanerapp.storage.mediafiles

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.phone.cleanerapp.storage.mediafiles.core.AppForeground
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallEndActivity
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingActivity
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker

class PhoneCleanerApp : Application() {
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

        // The two service-opened screens don't count as "the user is in the app"
        AppForeground.register(this, setOf(ChargingActivity::class.java, CallEndActivity::class.java))
        // Any process start (boot, update, the service waking the app) re-arms the monitor
        ChargingFeature.createChannels(this)
        ChargingFeature.startMonitor(this)
        // Alarms don't survive a force-stop or reboot: re-arm the daily reminder on every start
        com.phone.cleanerapp.storage.mediafiles.feature.reminder.Reminder.schedule(this)
    }
}
