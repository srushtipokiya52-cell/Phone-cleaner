package com.phone.cleanerapp.storage.mediafiles.feature.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts [ChargeMonitorService] after a reboot or an app update, if a screen is turned on. Both
 * broadcasts are allowed exemptions for starting a foreground service from the background
 * (including API 34's specialUse type).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ChargingFeature.createChannels(context)
                com.phone.cleanerapp.storage.mediafiles.feature.reminder.Reminder.schedule(context)
                ChargingFeature.startMonitor(context)
            }
        }
    }
}
