package com.phone.cleanerapp.storage.mediafiles.feature.callend

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.AppForeground
import com.phone.cleanerapp.storage.mediafiles.core.ScreenConfig
import com.phone.cleanerapp.storage.mediafiles.core.ScreenPrefs
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermission

/**
 * After-call screen. Calls are detected by [CallMonitor] inside ChargeMonitorService, so the
 * service runs while the charging screen OR this feature is on. Rules ([ScreenConfig]):
 * `call_end_enabled`, `call_end_min_seconds` (default 10), `call_end_max_per_day` (default 5).
 */
object CallEndFeature {

    private const val TAG = "CallEnd"
    const val CHANNEL = "call_end"
    private const val NOTIFICATION_ID = 4201

    private const val KEY_ENABLED = "key_call_end_enabled"
    private const val KEY_SHOWN_DAY = "key_call_end_shown_day"
    private const val KEY_SHOWN_COUNT = "key_call_end_shown_count"

    /** The user's switch in Settings (on by default) and the config kill switch. */
    fun isEnabled(context: Context): Boolean = isUserEnabled(context) && ScreenConfig.callEndEnabled(context)

    fun isUserEnabled(context: Context): Boolean = ScreenPrefs.getBool(context, KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        ScreenPrefs.putBool(context, KEY_ENABLED, enabled)
        // The monitor service also serves the charging screen: (re)start it, or stop it if unused
        if (ChargingFeature.shouldRunMonitor(context)) {
            ChargingFeature.startMonitor(context)
        } else {
            ChargingFeature.stopMonitor(context)
        }
    }

    /** Called by the monitor service when a phone call ends. */
    fun onCallEnded(context: Context, call: CallInfo) {
        val reason = skipReason(context, call)
        if (reason != null) {
            Log.d(TAG, "Call ended (${call.durationMs / 1000}s): skip, $reason")
            return
        }
        countShown(context)
        val intent = CallEndActivity.intent(context, call)
        if (OverlayPermission.isGranted(context)) {
            try {
                context.startActivity(intent)
                Log.d(TAG, "Call ended (${call.durationMs / 1000}s): screen opened")
                return
            } catch (e: RuntimeException) {
                Log.w(TAG, "Direct start blocked, posting a notification", e)
            }
        }
        postNotification(context, intent)
    }

    private fun skipReason(context: Context, call: CallInfo): String? = when {
        !isUserEnabled(context) -> "turned off in Settings"
        !ScreenConfig.callEndEnabled(context) -> "config call_end_enabled=false"
        call.durationMs < ScreenConfig.callEndMinSeconds(context) * 1000 ->
            "shorter than call_end_min_seconds=${ScreenConfig.callEndMinSeconds(context)}"
        shownToday(context) >= ScreenConfig.callEndMaxPerDay(context) ->
            "daily cap call_end_max_per_day=${ScreenConfig.callEndMaxPerDay(context)} reached"
        // The user is already in the app: nothing to interrupt them with
        AppForeground.isOpen -> "app is open"
        else -> null
    }

    private fun shownToday(context: Context): Int =
        if (ScreenPrefs.getString(context, KEY_SHOWN_DAY) == ScreenPrefs.today()) {
            ScreenPrefs.getInt(context, KEY_SHOWN_COUNT, 0)
        } else {
            0
        }

    private fun countShown(context: Context) {
        val count = shownToday(context) + 1
        ScreenPrefs.putString(context, KEY_SHOWN_DAY, ScreenPrefs.today())
        ScreenPrefs.putInt(context, KEY_SHOWN_COUNT, count)
    }

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(tr("After-call screen"))
                .setDescription(tr("Shows quick actions and tips after a phone call"))
                .setShowBadge(false)
                .build()
        )
    }

    /** Without "Display over other apps" the screen can't open by itself: a heads-up instead. */
    @SuppressLint("MissingPermission") // checked by canPostNotifications()
    private fun postNotification(context: Context, screenIntent: Intent) {
        if (!ChargingFeature.canPostNotifications(context)) return
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, screenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_charging)
            .setContentTitle(tr("Call ended"))
            .setContentText(tr("Tap for quick actions"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification posted")
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
