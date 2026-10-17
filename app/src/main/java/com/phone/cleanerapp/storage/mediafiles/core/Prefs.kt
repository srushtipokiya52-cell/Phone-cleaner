package com.phone.cleanerapp.storage.mediafiles.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("phone_cleaner", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit { putBoolean("onboarded", v) }

    var language: String
        get() = sp.getString("language", "en") ?: "en"
        set(v) = sp.edit { putString("language", v) }

    var autoClean: Boolean
        get() = sp.getBoolean("auto_clean", false)
        set(v) = sp.edit { putBoolean("auto_clean", v) }

    var junkReminder: Boolean
        get() = sp.getBoolean("junk_reminder", true)
        set(v) = sp.edit { putBoolean("junk_reminder", v) }

    var largeFileMb: Int
        get() = sp.getInt("large_mb", 100)
        set(v) = sp.edit { putInt("large_mb", v) }

    var blockedPackages: Set<String>
        get() = sp.getStringSet("blocked_pkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("blocked_pkgs", v) }

    var ignoredFolders: Set<String>
        get() = sp.getStringSet("ignored", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("ignored", v) }

    /** Blocked-notification log: one line per entry "pkg|appName|epochMs|text". */
    var blockedLog: List<String>
        get() = (sp.getString("blocked_log", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit { putString("blocked_log", v.takeLast(60).joinToString("\n")) }

    var blockedTodayCount: Int
        get() = if (sp.getString("blocked_day", "") == today()) sp.getInt("blocked_count", 0) else 0
        set(v) = sp.edit { putString("blocked_day", today()); putInt("blocked_count", v) }

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx.applicationContext).also { inst = it } }
    }
}
