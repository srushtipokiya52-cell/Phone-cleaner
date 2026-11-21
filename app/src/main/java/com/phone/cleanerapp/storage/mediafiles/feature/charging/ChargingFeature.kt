package com.phone.cleanerapp.storage.mediafiles.feature.charging

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phone.cleanerapp.storage.mediafiles.core.ScreenPrefs
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallEndFeature
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermission

/** Setting, notification channels, permission helpers and service control for the charging screen. */
object ChargingFeature {

    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERT = "charging_alert"
    const val NOTIFICATION_ID_MONITOR = 4101

    /** The alert alternates between two IDs: see [ChargeMonitorService]. */
    const val NOTIFICATION_ID_ALERT = 4102
    const val NOTIFICATION_ID_ALERT_ALT = 4103

    private const val KEY_ENABLED = "key_charging_screen_enabled"

    /** On by default, so it works right after install; the user can turn it off in Settings. */
    fun isEnabled(context: Context): Boolean = ScreenPrefs.getBool(context, KEY_ENABLED, true)

    /** Saves the setting and starts or stops the monitor service to match. */
    fun setEnabled(context: Context, enabled: Boolean) {
        ScreenPrefs.putBool(context, KEY_ENABLED, enabled)
        if (shouldRunMonitor(context)) startMonitor(context) else stopMonitor(context)
    }

    /** The monitor service serves the charging screen and the after-call screen. */
    fun shouldRunMonitor(context: Context): Boolean =
        isEnabled(context) || CallEndFeature.isEnabled(context)

    /**
     * Starts [ChargeMonitorService] if a feature is on. Safe to call repeatedly: a running service
     * only gets another onStartCommand. Returns false when Android refuses a background start.
     */
    fun startMonitor(context: Context): Boolean {
        if (!shouldRunMonitor(context)) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, ChargeMonitorService::class.java))
            true
        } catch (e: IllegalStateException) {
            // API 31+: ForegroundServiceStartNotAllowedException when started from the background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("ChargingFeature", "Charge monitor start not allowed from background", e)
                false
            } else {
                throw e
            }
        } catch (e: SecurityException) {
            // API 34+: missing FOREGROUND_SERVICE_* permission or disallowed type for this start
            Log.w("ChargingFeature", "Charge monitor start rejected", e)
            false
        }
    }

    fun stopMonitor(context: Context) {
        context.stopService(Intent(context, ChargeMonitorService::class.java))
    }

    fun cancelAlerts(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(NOTIFICATION_ID_ALERT)
            cancel(NOTIFICATION_ID_ALERT_ALT)
        }
    }

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_MONITOR, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(tr("Charging and call screens"))
                .setDescription(tr("Keeps the charging and after-call screens ready"))
                .setShowBadge(false)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERT, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(tr("Charger alerts"))
                .setDescription(tr("Shows the charging screen when you plug in or unplug"))
                .setShowBadge(false)
                .build()
        )
        CallEndFeature.createChannel(context)
    }

    /** API 33+: POST_NOTIFICATIONS is a runtime permission. */
    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** API 34+: full-screen intents need a user grant for apps that aren't calling or alarm apps. */
    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /**
     * "Display over other apps": lets the service start the screens itself while the phone is in
     * use (Android 10+ blocks background activity starts otherwise, and a full-screen intent then
     * only shows a heads-up notification).
     */
    fun canOpenScreenDirectly(context: Context): Boolean = OverlayPermission.isGranted(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
