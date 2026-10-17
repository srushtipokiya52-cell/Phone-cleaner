package com.phone.cleanerapp.storage.mediafiles.core

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

/** Every permission the app asks for, with a "do we have it" check and the intent that grants it. */
object Perms {
    enum class Kind { FILES, USAGE, NOTIF_ACCESS, CONTACTS, ALERTS }

    fun has(ctx: Context, kind: Kind): Boolean = when (kind) {
        Kind.FILES -> hasFiles(ctx)
        Kind.USAGE -> hasUsage(ctx)
        Kind.NOTIF_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Kind.CONTACTS -> hasContacts(ctx)
        Kind.ALERTS -> Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    }

    fun hasFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasContacts(ctx: Context) =
        granted(ctx, Manifest.permission.READ_CONTACTS) && granted(ctx, Manifest.permission.WRITE_CONTACTS)

    fun hasUsage(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Runtime permissions to request for [kind] (empty when it needs a settings screen instead). */
    fun runtime(kind: Kind): Array<String> = when (kind) {
        Kind.FILES -> when {
            Build.VERSION.SDK_INT >= 30 -> emptyArray()
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        Kind.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        Kind.ALERTS -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        else -> emptyArray()
    }

    /** Settings screen intent for permissions that can't be granted from a dialog. */
    fun settingsIntent(ctx: Context, kind: Kind): Intent? = when (kind) {
        Kind.FILES -> if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        else null
        Kind.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        Kind.NOTIF_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        else -> null
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}
