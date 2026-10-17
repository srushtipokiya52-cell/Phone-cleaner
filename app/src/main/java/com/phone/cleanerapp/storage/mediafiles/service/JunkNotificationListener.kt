package com.phone.cleanerapp.storage.mediafiles.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.phone.cleanerapp.storage.mediafiles.core.Prefs

/** Dismisses notifications from apps the user chose to block and keeps a short log of what was blocked. */
class JunkNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs.get(this)
        if (sbn.packageName !in prefs.blockedPackages) return
        if (sbn.isOngoing || sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Throwable) {
            sbn.packageName
        }
        cancelNotification(sbn.key)
        val line = listOf(sbn.packageName, label, System.currentTimeMillis().toString(), "$title $text".trim())
            .joinToString("|") { it.replace('|', ' ').replace('\n', ' ') }
        prefs.blockedLog = prefs.blockedLog + line
        prefs.blockedTodayCount = prefs.blockedTodayCount + 1
    }
}
