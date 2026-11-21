package com.phone.cleanerapp.storage.mediafiles.feature.overlay

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.phone.cleanerapp.storage.mediafiles.core.AppSession
import com.phone.cleanerapp.storage.mediafiles.core.ScreenConfig
import com.phone.cleanerapp.storage.mediafiles.core.ScreenPrefs
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallEndFeature
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import java.lang.ref.WeakReference

/**
 * "Display over other apps": lets the charging and after-call screens open by themselves, whether
 * the phone is locked or in use. Asked with the sheet ([OverlayPermissionSheet]):
 * - Home: on the app sessions listed in `overlay_ask_sessions` (default "2,5,8"). Pending: a listed
 *   session that was missed is asked on the next one.
 * - Charging screen: once per app session while `overlay_ask_on_charging` is on.
 * - Settings: the row always opens it.
 * A session is one fresh launch of the app ([AppSession]).
 */
object OverlayPermission {

    private const val TAG = "OverlayPermission"

    const val SOURCE_HOME = "home"
    const val SOURCE_CHARGING = "charging"
    const val SOURCE_SETTINGS = "settings"

    /** Changed outside our sheet (system settings directly, or a sheet whose watch had ended). */
    const val SOURCE_SYSTEM = "system"

    private const val KEY_HOME_ASKED_SESSIONS = "key_overlay_home_asked_sessions"
    private const val KEY_CHARGING_ASKED_SESSION = "key_overlay_charging_asked_session"
    private const val KEY_LAST_GRANTED = "key_overlay_last_granted"

    private const val POLL_MS = 400L
    private const val WATCH_MS = 60_000L

    private val handler = Handler(Looper.getMainLooper())

    private fun sessionCount(context: Context): Int = AppSession.count(context)

    /** Below Android 10 background activity starts aren't restricted, so nothing is needed. */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

    /**
     * Logs a change of the permission: granted when it was just allowed, revoked when it was turned
     * off. Safe to call often: it only logs on a change. The first call only records the state.
     */
    fun syncState(context: Context, source: String = SOURCE_SYSTEM) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val granted = isGranted(context)
        val known = ScreenPrefs.contains(context, KEY_LAST_GRANTED)
        val wasGranted = ScreenPrefs.getBool(context, KEY_LAST_GRANTED, false)
        if (known && granted == wasGranted) return
        ScreenPrefs.putBool(context, KEY_LAST_GRANTED, granted)
        if (!known) return
        track(if (granted) "overlay_granted" else "overlay_revoked", source, context)
    }

    /** Worth asking only while a feature that opens a screen by itself is on. */
    private fun isNeeded(context: Context): Boolean =
        !isGranted(context) && (ChargingFeature.isEnabled(context) || CallEndFeature.isEnabled(context))

    fun shouldAskOnHome(context: Context): Boolean {
        val pending = pendingHomeSessions(context)
        val ask = isNeeded(context) && pending.isNotEmpty()
        Log.d(TAG, "Home check: session=${sessionCount(context)}, pending=$pending -> ${if (ask) "SHOW sheet" else "skip"}")
        return ask
    }

    /** Every listed session up to now counts as asked, so one sheet covers several missed ones. */
    fun markAskedOnHome(context: Context) {
        val asked = askedHomeSessions(context) + pendingHomeSessions(context).map { it.toString() }
        ScreenPrefs.putStringSet(context, KEY_HOME_ASKED_SESSIONS, asked)
    }

    fun shouldAskOnCharging(context: Context): Boolean {
        val enabled = ScreenConfig.overlayAskOnCharging(context)
        val askedSession = ScreenPrefs.getInt(context, KEY_CHARGING_ASKED_SESSION, -1)
        val ask = enabled && isNeeded(context) && askedSession != sessionCount(context)
        Log.d(TAG, "Charging check: session=${sessionCount(context)}, lastAsked=$askedSession -> ${if (ask) "SHOW sheet" else "skip"}")
        return ask
    }

    fun markAskedOnCharging(context: Context) {
        ScreenPrefs.putInt(context, KEY_CHARGING_ASKED_SESSION, sessionCount(context))
    }

    private fun pendingHomeSessions(context: Context): List<Int> {
        val session = sessionCount(context)
        val asked = askedHomeSessions(context)
        // Tolerant of how the value is typed: "2,5,8", 2, 5, 8, [2,5,8]
        return ScreenConfig.overlayAskSessions(context)
            .split(',')
            .mapNotNull { it.trim().trim('"', '\'', '[', ']', ' ').toIntOrNull() }
            .filter { it in 1..session && it.toString() !in asked }
    }

    private fun askedHomeSessions(context: Context): Set<String> =
        ScreenPrefs.getStringSet(context, KEY_HOME_ASKED_SESSIONS)

    /**
     * Opens the system "Display over other apps" page (unlocking first when shown over the lock
     * screen), then watches for the grant and brings [activity] back to the front by itself:
     * once granted, the app may start activities from the background.
     */
    fun openSettings(activity: Activity, source: String, onGranted: () -> Unit) {
        track("overlay_allow_click", source, activity)
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchSettings(activity, source, onGranted)
            })
        } else {
            launchSettings(activity, source, onGranted)
        }
    }

    private fun launchSettings(activity: Activity, source: String, onGranted: () -> Unit) {
        val pkg = Uri.parse("package:${activity.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
            // Some OEM builds drop the overlay page
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        )
        val opened = candidates.any { intent ->
            try {
                activity.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (opened) watchForGrant(activity, source, onGranted)
    }

    private fun watchForGrant(activity: Activity, source: String, onGranted: () -> Unit) {
        val screen = WeakReference(activity)
        val startedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(object : Runnable {
            override fun run() {
                val current = screen.get() ?: return
                if (current.isFinishing || current.isDestroyed) return
                when {
                    isGranted(current) -> {
                        syncState(current, source)
                        bringToFront(current)
                        onGranted()
                    }
                    SystemClock.elapsedRealtime() - startedAt < WATCH_MS -> handler.postDelayed(this, POLL_MS)
                }
            }
        }, POLL_MS)
    }

    private fun bringToFront(activity: Activity) {
        try {
            activity.startActivity(
                Intent(activity, activity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: RuntimeException) {
            // Blocked after all: the user comes back with Back as before
        }
    }

    fun track(event: String, source: String, context: Context) {
        Log.d(TAG, "Event $event (source=$source, session=${sessionCount(context)})")
    }
}
