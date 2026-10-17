package com.phone.cleanerapp.storage.mediafiles.data

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageStats(val total: Long, val free: Long) {
    val used: Long get() = total - free
}

data class RamStats(val total: Long, val free: Long) {
    val used: Long get() = total - free
    val percent: Int get() = if (total == 0L) 0 else (used * 100 / total).toInt()
}

data class BatteryStats(
    val level: Int,
    val tempC: Float,
    val voltage: Float,
    val health: String,
    val status: String,
    val technology: String,
    val charging: Boolean,
    val capacityMah: Int?,
    val cycles: Int?,
    val minutesToFull: Int?,
)

object DeviceInfo {

    fun storage(): StorageStats {
        val st = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageStats(st.totalBytes, st.availableBytes)
    }

    fun ram(ctx: Context): RamStats {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamStats(mi.totalMem, mi.availMem)
    }

    fun battery(ctx: Context): BatteryStats {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val toFull = if (charging && Build.VERSION.SDK_INT >= 28) {
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) (ms / 60000).toInt() else null
        } else null
        return BatteryStats(
            level = level * 100 / scale,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltage = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
            health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> tr("Good")
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> tr("Overheat")
                BatteryManager.BATTERY_HEALTH_DEAD -> tr("Dead")
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> tr("Over voltage")
                BatteryManager.BATTERY_HEALTH_COLD -> tr("Cold")
                else -> tr("Unknown")
            },
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> tr("Charging")
                BatteryManager.BATTERY_STATUS_DISCHARGING -> tr("Discharging")
                BatteryManager.BATTERY_STATUS_FULL -> tr("Full")
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> tr("Not charging")
                else -> tr("Unknown")
            },
            technology = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "—",
            charging = charging,
            capacityMah = designCapacityMah(ctx),
            cycles = if (Build.VERSION.SDK_INT >= 34) i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null,
            minutesToFull = toFull,
        )
    }

    /** Design capacity from the framework's power profile (not a public API; null when unavailable). */
    private fun designCapacityMah(ctx: Context): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(ctx)
        (cls.getMethod("getBatteryCapacity").invoke(profile) as Double).toInt().takeIf { it > 0 }
    } catch (_: Throwable) {
        null
    }

    /**
     * CPU temperature in °C. Apps usually can't read the SoC sensors, so this tries the thermal
     * zones first and falls back to the battery temperature.
     */
    fun cpuTempC(ctx: Context): Float {
        try {
            File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }?.forEach { zone ->
                val type = File(zone, "type").takeIf { it.canRead() }?.readText()?.trim()?.lowercase() ?: return@forEach
                if (type.contains("cpu") || type.contains("soc") || type.contains("tsens")) {
                    val raw = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toFloatOrNull() ?: return@forEach
                    val c = if (raw > 1000) raw / 1000f else raw
                    if (c in 10f..120f) return c
                }
            }
        } catch (_: Throwable) {
        }
        return battery(ctx).tempC
    }
}
