package com.app

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import com.ads.ITGTrackingHelper
import timber.log.Timber

/**
 * App session counter: one session per Splash open (launcher icon, notification tap), not on
 * rotation or process restore. Used by Remote Config driven prompts (e.g. overlay_ask_sessions)
 * and reported to Firebase Analytics:
 * - event `app_session` with param `session_number` on every session
 * - event `session_<n>` for the first [MAX_NUMBERED_EVENTS] sessions (easy funnels in the console)
 * - user property `session_count` with the latest number
 */
object AppSession {

    private const val PREFS_NAME = "japa_prefs"
    private const val KEY_SESSION_COUNT = "key_session_count"
    private const val MAX_NUMBERED_EVENTS = 20

    fun start(context: Context) {
        val session = count(context) + 1
        prefs(context).edit { putInt(KEY_SESSION_COUNT, session) }
        Timber.tag("AppSession").d("Session %d started", session)

        ITGTrackingHelper.logEvent("app_session", Bundle().apply { putLong("session_number", session.toLong()) })
        if (session <= MAX_NUMBERED_EVENTS) ITGTrackingHelper.logEvent("session_$session", null)
        ITGTrackingHelper.userProperty("session_count", session.toString())
    }

    /** Sessions started so far; 0 before the first Splash open. */
    fun count(context: Context): Int = prefs(context).getInt(KEY_SESSION_COUNT, 0)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
