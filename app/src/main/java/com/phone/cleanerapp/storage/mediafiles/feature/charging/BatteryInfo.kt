package com.phone.cleanerapp.storage.mediafiles.feature.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.phone.cleanerapp.storage.mediafiles.core.tr

/** Snapshot of ACTION_BATTERY_CHANGED. */
data class BatteryInfo(
    val percent: Int,
    val plugged: Int,
    val status: Int,
    /** Tenths of a degree Celsius as the system reports it; 0 when unknown. */
    val temperatureTenths: Int = 0,
) {
    val isPlugged: Boolean get() = plugged != 0
    val isFull: Boolean get() = status == BatteryManager.BATTERY_STATUS_FULL
    val temperatureC: Float get() = temperatureTenths / 10f

    fun chargerType(): String = when {
        plugged == BatteryManager.BATTERY_PLUGGED_AC -> tr("AC charger")
        plugged == BatteryManager.BATTERY_PLUGGED_USB -> tr("USB")
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> tr("Wireless")
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            plugged == BatteryManager.BATTERY_PLUGGED_DOCK -> tr("Dock")
        plugged != 0 -> tr("Charger")
        else -> tr("On battery")
    }

    companion object {
        fun from(intent: Intent): BatteryInfo {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
            return BatteryInfo(
                percent = percent.coerceIn(0, 100),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
            )
        }

        /** Reads the sticky ACTION_BATTERY_CHANGED broadcast without keeping a receiver. */
        fun read(context: Context): BatteryInfo? =
            ContextCompat.registerReceiver(
                context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
            )?.let(::from)
    }
}
