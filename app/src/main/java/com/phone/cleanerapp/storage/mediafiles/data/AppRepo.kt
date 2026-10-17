package com.phone.cleanerapp.storage.mediafiles.data

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.storage.StorageManager
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import java.io.File

class AppItem(
    val pkg: String,
    val label: String,
    val size: Long,
    /** Epoch ms of the last time the user opened it; 0 = unknown / never. */
    val lastUsed: Long,
    val installed: Long,
    val icon: Drawable?,
) {
    var selected = false
}

object AppRepo {
    private const val DAY = 24L * 3600 * 1000

    /** Apps the user can open (launcher apps), excluding this one. */
    @Volatile private var cache: List<AppItem>? = null
    @Volatile private var cacheAt = 0L
    @Volatile private var cacheUsage = false

    /** Last loaded list, so screens can draw instantly while a refresh runs. */
    fun cached(): List<AppItem>? = cache

    fun invalidate() {
        cache = null
    }

    /** Installed launcher apps. Cached for 60 s; sizes are read in parallel because each one is a slow system call. */
    fun installed(ctx: Context, force: Boolean = false): List<AppItem> {
        val hasUsage = Perms.hasUsage(ctx)
        val c = cache
        if (!force && c != null && hasUsage == cacheUsage && System.currentTimeMillis() - cacheAt < 60_000) return c
        return load(ctx).also { cache = it; cacheAt = System.currentTimeMillis(); cacheUsage = hasUsage }
    }

    private fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        val usage = lastUsedMap(ctx)
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val canStats = Perms.hasUsage(ctx)
        return launcher.filter { it != ctx.packageName }.parallelStream().map { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val pi = pm.getPackageInfo(pkg, 0)
                val size = if (canStats) try {
                    val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
                    s.appBytes + s.dataBytes + s.cacheBytes
                } catch (_: Throwable) {
                    File(info.sourceDir).length()
                } else File(info.sourceDir).length()
                AppItem(pkg, pm.getApplicationLabel(info).toString(), size, usage[pkg] ?: 0L, pi.firstInstallTime, pm.getApplicationIcon(info))
            } catch (_: Throwable) {
                null
            }
        }.collect(java.util.stream.Collectors.toList()).filterNotNull().sortedByDescending { it.size }
    }

    fun isSystem(info: ApplicationInfo) = info.flags and ApplicationInfo.FLAG_SYSTEM != 0

    /** Days without being opened, or null when usage access isn't granted. */
    fun unusedDays(item: AppItem, now: Long = System.currentTimeMillis()): Int =
        if (item.lastUsed <= 0) ((now - item.installed) / DAY).toInt() else ((now - item.lastUsed) / DAY).toInt()

    private fun lastUsedMap(ctx: Context): Map<String, Long> {
        if (!Perms.hasUsage(ctx)) return emptyMap()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return usm.queryAndAggregateUsageStats(now - 90 * DAY, now).mapValues { it.value.lastTimeUsed }
    }

    /** Apps used in the last [hours] hours (best proxy for "running in the background" that Android allows). */
    fun recentlyUsed(ctx: Context, hours: Int = 6): List<AppItem> {
        if (!Perms.hasUsage(ctx)) return emptyList()
        val cutoff = System.currentTimeMillis() - hours * 3600_000L
        return installed(ctx).filter { it.lastUsed >= cutoff }.sortedByDescending { it.lastUsed }
    }

    /** Asks the system to stop background activity for [pkgs]; returns RAM actually released in bytes. */
    fun boost(ctx: Context, pkgs: List<String>): Long {
        val before = DeviceInfo.ram(ctx).free
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        pkgs.forEach { runCatching { am.killBackgroundProcesses(it) } }
        try {
            Thread.sleep(600)
        } catch (_: InterruptedException) {
        }
        return (DeviceInfo.ram(ctx).free - before).coerceAtLeast(0)
    }
}
