package com.phone.cleanerapp.storage.mediafiles.feature.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phone.cleanerapp.storage.mediafiles.MainActivity
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.ScreenPrefs
import com.phone.cleanerapp.storage.mediafiles.core.Tr
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import java.util.Calendar

/** What the reminder nudges the user to do. [title] and [text] are English and go through tr(). */
enum class ReminderKind(val title: String, val text: String, val label: String) {
    CLEAN("Time to clean your phone", "Clear junk and free up space in one tap.", "Clean junk"),
    BOOST("Speed up your phone", "Close background apps to free RAM and make it faster.", "Boost speed"),
    BATTERY("Check your battery", "See what drains your battery and cool your phone down.", "Battery care"),
    PHOTOS("Review your photos", "Remove similar and blurry photos to get space back.", "Photos"),
}

data class ReminderSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Sunday ... bit 6 = Saturday ([Calendar.DAY_OF_WEEK] minus 1). */
    val days: Int,
    val kind: ReminderKind,
) {
    fun hasDay(dayOfWeek: Int) = days and (1 shl (dayOfWeek - 1)) != 0
}

/** Stores the daily cleaning reminder and schedules it with AlarmManager (no exact-alarm permission). */
object Reminder {

    const val CHANNEL = "reminder"
    private const val NOTIFICATION_ID = 1101
    private const val REQUEST_CODE = 1102
    const val ALL_DAYS = 0b1111111

    private const val K_ENABLED = "reminder_enabled"
    private const val K_HOUR = "reminder_hour"
    private const val K_MINUTE = "reminder_minute"
    private const val K_DAYS = "reminder_days"
    private const val K_KIND = "reminder_kind"

    fun load(ctx: Context) = ReminderSettings(
        enabled = ScreenPrefs.getBool(ctx, K_ENABLED, true),
        hour = ScreenPrefs.getInt(ctx, K_HOUR, 20),
        minute = ScreenPrefs.getInt(ctx, K_MINUTE, 0),
        days = ScreenPrefs.getInt(ctx, K_DAYS, ALL_DAYS),
        kind = ReminderKind.entries.getOrElse(ScreenPrefs.getInt(ctx, K_KIND, 0)) { ReminderKind.CLEAN },
    )

    /** Saves [s] and schedules or cancels the alarm to match. A reminder with no day is off. */
    fun save(ctx: Context, s: ReminderSettings) {
        ScreenPrefs.putBool(ctx, K_ENABLED, s.enabled && s.days != 0)
        ScreenPrefs.putInt(ctx, K_HOUR, s.hour)
        ScreenPrefs.putInt(ctx, K_MINUTE, s.minute)
        ScreenPrefs.putInt(ctx, K_DAYS, s.days)
        ScreenPrefs.putInt(ctx, K_KIND, s.kind.ordinal)
        schedule(ctx)
    }

    /** The next time the reminder fires, or null when it is off. */
    fun nextTrigger(s: ReminderSettings, now: Calendar = Calendar.getInstance()): Calendar? {
        if (!s.enabled || s.days == 0) return null
        for (offset in 0..7) {
            val c = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, s.hour)
                set(Calendar.MINUTE, s.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.after(now) && s.hasDay(c.get(Calendar.DAY_OF_WEEK))) return c
        }
        return null
    }

    /** (Re)sets the alarm. Safe to call often: it replaces the previous one. */
    @SuppressLint("ScheduleExactAlarm")
    fun schedule(ctx: Context) {
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        alarms.cancel(pi)
        val next = nextTrigger(load(ctx)) ?: return
        // Not exact, so no special permission: it may slip a few minutes, and still fires in Doze
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
    }

    private fun pendingIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Cleaning reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    @SuppressLint("MissingPermission") // checked by canPostNotifications()
    fun notifyNow(ctx: Context) {
        if (!ChargingFeature.canPostNotifications(ctx)) return
        Tr.refresh(ctx)
        createChannel(ctx)
        val kind = load(ctx).kind
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_clean)
            .setContentTitle(tr(kind.title))
            .setContentText(tr(kind.text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(tr(kind.text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }
}

/** Fires the reminder, then sets the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Reminder.load(context).enabled) return
        Reminder.notifyNow(context)
        Reminder.schedule(context)
    }
}
