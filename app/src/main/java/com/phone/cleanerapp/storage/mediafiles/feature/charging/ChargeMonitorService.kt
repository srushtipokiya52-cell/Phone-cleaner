package com.phone.cleanerapp.storage.mediafiles.feature.charging

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phone.cleanerapp.storage.mediafiles.MainActivity
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Tr
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallEndFeature
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallMonitor

/**
 * Foreground service that listens for the charger and, through [CallMonitor], for the end of phone
 * calls. Runs while either feature is on. ACTION_POWER_CONNECTED/DISCONNECTED reach only
 * runtime-registered receivers on Android 8+, and Android 10+ blocks background activity starts,
 * so each event opens [ChargingActivity] directly (with "Display over other apps") or through a
 * full-screen-intent notification.
 */
class ChargeMonitorService : Service() {

    private companion object {
        const val TAG = "ChargeMonitorService"
        const val ALERT_TIMEOUT_MS = 60_000L
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPowerChanged(connected = true)
                Intent.ACTION_POWER_DISCONNECTED -> onPowerChanged(connected = false)
            }
        }
    }
    private var isReceiverRegistered = false
    private var useAltAlertId = false

    /** After-call screen: listens to the audio mode while that feature is on. */
    private val callMonitor by lazy {
        CallMonitor(this) { call -> CallEndFeature.onCallEnded(this, call) }
    }

    override fun onCreate() {
        super.onCreate()
        Tr.refresh(this)
        ChargingFeature.createChannels(this)
        // onCreate runs once per service instance, so restarts never stack up receivers
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must follow every startForegroundService() call, including repeat starts and ones we are
        // about to stop: skipping it crashes with ForegroundServiceDidNotStartInTimeException.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, ChargingFeature.NOTIFICATION_ID_MONITOR, buildMonitorNotification(), type)
        } catch (e: Exception) {
            // API 31+ background-start denial, API 34+ missing type/permission: don't crash the app
            Log.w(TAG, "Charge monitor could not enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ChargingFeature.shouldRunMonitor(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Every start re-reads the switches: the call listener follows the after-call setting
        if (CallEndFeature.isEnabled(this)) callMonitor.start() else callMonitor.stop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        callMonitor.stop()
        if (isReceiverRegistered) {
            unregisterReceiver(powerReceiver)
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun buildMonitorNotification() =
        NotificationCompat.Builder(this, ChargingFeature.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_charging)
            .setContentTitle(tr("Charging and call screens are on"))
            .setContentText(tr("Tap to open Phone Cleaner"))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    @SuppressLint("MissingPermission") // checked by canPostNotifications()
    private fun onPowerChanged(connected: Boolean) {
        // The service may be running only for the after-call screen
        if (!ChargingFeature.isEnabled(this)) return
        // The open screen follows the battery itself; a notification would only cover it
        if (ChargingActivity.isVisible) return

        val screenIntent = Intent(this, ChargingActivity::class.java).apply {
            putExtra(ChargingActivity.EXTRA_CONNECTED, connected)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // With "Display over other apps" the screen opens directly, locked or in use: the activity
        // shows over the lock screen and turns the screen on. Without it, the full-screen
        // notification below covers the locked phone (in use it only pops up as a heads-up).
        if (ChargingFeature.canOpenScreenDirectly(this)) {
            try {
                startActivity(screenIntent)
                return
            } catch (e: RuntimeException) {
                // Blocked by the system after all (some OEM builds): fall back to the notification
                Log.w(TAG, "Direct charging screen start blocked", e)
            }
        }

        if (!ChargingFeature.canPostNotifications(this)) return
        val pendingIntent = PendingIntent.getActivity(
            this, 0, screenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = tr(if (connected) "Charger connected" else "Charger disconnected")
        val text = trf("Battery at %1\$s%%", (BatteryInfo.read(this)?.percent ?: "--").toString())

        val notification = NotificationCompat.Builder(this, ChargingFeature.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_charging)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // A missed alert shouldn't linger in the shade
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        // System UI fires a full-screen intent only for a newly added notification; re-posting a
        // still-showing ID is an update and only pops up. So clear the old alert and post the new
        // one under the other ID.
        ChargingFeature.cancelAlerts(this)
        useAltAlertId = !useAltAlertId
        val id = if (useAltAlertId) ChargingFeature.NOTIFICATION_ID_ALERT_ALT else ChargingFeature.NOTIFICATION_ID_ALERT
        NotificationManagerCompat.from(this).notify(id, notification)
    }
}
