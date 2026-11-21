package com.phone.cleanerapp.storage.mediafiles.feature.callend

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** A phone call that just ended, as seen through the audio mode. */
data class CallInfo(
    val durationMs: Long,
    /** Rang before it was answered; otherwise the user dialled it. */
    val incoming: Boolean,
    val endedAt: Long = System.currentTimeMillis()
)

/**
 * Detects the end of a phone call without any phone permission, from the audio mode:
 * MODE_RINGTONE (ringing) to MODE_IN_CALL (talking) to MODE_NORMAL (ended).
 * - API 31+: AudioManager.OnModeChangedListener, pushed by the system.
 * - Older: polls the mode every [POLL_MS] (the screen is usually off during a call, so this
 *   can't wait for the screen).
 * Internet calls (MODE_IN_COMMUNICATION: WhatsApp, Meet) and missed calls are ignored on purpose.
 */
class CallMonitor(context: Context, private val onCallEnded: (CallInfo) -> Unit) {

    private companion object {
        const val TAG = "CallMonitor"
        const val POLL_MS = 2_000L
    }

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var running = false

    private var lastMode = AudioManager.MODE_NORMAL
    private var ringing = false
    private var callStartedAt = 0L
    private var callIncoming = false

    private val poll = object : Runnable {
        override fun run() {
            onMode(audio.mode)
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastMode = audio.mode
        // Started mid-call (service restart): time it from now
        if (lastMode == AudioManager.MODE_IN_CALL) callStartedAt = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = AudioManager.OnModeChangedListener { onMode(it) }
            audio.addOnModeChangedListener(appContext.mainExecutor, listener)
            modeListener = listener
        } else {
            handler.postDelayed(poll, POLL_MS)
        }
        Log.d(TAG, "Started (mode=$lastMode, ${if (modeListener != null) "listener" else "polling"})")
    }

    fun stop() {
        if (!running) return
        running = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener?.let { audio.removeOnModeChangedListener(it) }
        }
        modeListener = null
        handler.removeCallbacks(poll)
        Log.d(TAG, "Stopped")
    }

    private fun onMode(mode: Int) {
        if (mode == lastMode) return
        Log.d(TAG, "Audio mode $lastMode -> $mode")
        lastMode = mode
        when (mode) {
            AudioManager.MODE_RINGTONE -> ringing = true
            AudioManager.MODE_IN_CALL -> if (callStartedAt == 0L) {
                callStartedAt = SystemClock.elapsedRealtime()
                callIncoming = ringing
            }
            AudioManager.MODE_NORMAL -> {
                if (callStartedAt != 0L) {
                    val info = CallInfo(SystemClock.elapsedRealtime() - callStartedAt, callIncoming)
                    Log.d(TAG, "Call ended: ${info.durationMs / 1000}s, incoming=${info.incoming}")
                    onCallEnded(info)
                }
                ringing = false
                callStartedAt = 0L
                callIncoming = false
            }
        }
    }
}
