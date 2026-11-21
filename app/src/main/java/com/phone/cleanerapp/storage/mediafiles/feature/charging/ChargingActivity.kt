package com.phone.cleanerapp.storage.mediafiles.feature.charging

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.phone.cleanerapp.storage.mediafiles.MainActivity
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.ScreenAds
import com.phone.cleanerapp.storage.mediafiles.core.ScreenConfig
import com.phone.cleanerapp.storage.mediafiles.core.Tr
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf
import com.phone.cleanerapp.storage.mediafiles.databinding.ActivityChargingBinding
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermission
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermissionSheet
import java.util.Locale

/**
 * Charger plugged in / unplugged / battery full. Opened by [ChargeMonitorService] (directly, or
 * from its full-screen notification). Shows over the lock screen and turns the screen on; while
 * visible it follows ACTION_BATTERY_CHANGED, so plug and unplug events update this same screen.
 *
 * The native ad sheet is shown only while the phone is unlocked: Google Play and AdMob policy don't
 * allow ads on the lock screen. Closing shows an interstitial, unlocking first when needed.
 */
class ChargingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONNECTED = "connected"

        /** Read by the service: no alert is needed while this screen is already showing. */
        @Volatile
        var isVisible = false
            private set

        private const val OVERLAY_ASK_DELAY_MS = 1500L
        private const val UNLOCK_RECHECK_MS = 700L
    }

    private enum class Mode { CHARGING, FULL, ON_BATTERY }
    private enum class AdState { NONE, LOADING, LOADED, FAILED }

    private lateinit var binding: ActivityChargingBinding
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    private var mode: Mode? = null
    private var animators = emptyList<ValueAnimator>()
    private var adState = AdState.NONE

    /** Set when the screen turns off, cleared once the user unlocks again. */
    private var screenOffSinceUnlock = false

    /**
     * Set by USER_PRESENT. It arrives while the lock screen is still animating away, when
     * isKeyguardLocked can still read true, so it counts as unlocked on its own.
     */
    private var userPresent = false
    private var isClosing = false
    private var receiversRegistered = false
    private var navBarInset = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            render(BatteryInfo.from(intent))
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffSinceUnlock = true
                    userPresent = false
                }
                Intent.ACTION_USER_PRESENT -> {
                    screenOffSinceUnlock = false
                    userPresent = true
                    // Check again once the lock screen has finished going away
                    binding.root.postDelayed({ updateAdSheet() }, UNLOCK_RECHECK_MS)
                }
            }
            updateAdSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tr.refresh(this)
        showOverLockScreen()
        enableEdgeToEdge()
        binding = ActivityChargingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val closeTop = (binding.btnCloseCharging.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCharging) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutContent.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.btnCloseCharging.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                topMargin = closeTop + bars.top
            }
            navBarInset = bars.bottom
            applyBottomInsets()
            insets
        }

        binding.btnOpenCleaner.text = tr("Clean my phone")
        binding.btnCloseCharging.contentDescription = tr("Close")
        binding.btnCloseCharging.setOnClickListener { closeWithAd() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeWithAd()
        })
        binding.btnOpenCleaner.setOnClickListener { openCleaner() }
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
        // Sticky: the current state arrives immediately, then every change while visible.
        // EXPORTED: these are protected broadcasts only the system can send, and on API < 33 the
        // compat NOT_EXPORTED flag blocked them on some Samsung builds.
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screenFilter, ContextCompat.RECEIVER_EXPORTED)
        receiversRegistered = true
        startAnimations()
    }

    override fun onResume() {
        super.onResume()
        if (!keyguard.isKeyguardLocked) screenOffSinceUnlock = false
        updateAdSheet()
        OverlayPermission.syncState(this)
        maybeAskOverlayPermission()
    }

    /**
     * Once per app session: without the permission this screen opens by itself only when the phone
     * is locked. Shown after a short beat so the charging screen is seen first.
     */
    private fun maybeAskOverlayPermission() {
        if (!OverlayPermission.shouldAskOnCharging(this)) return
        OverlayPermission.markAskedOnCharging(this)
        handler.postDelayed({
            if (isFinishing || isDestroyed || isClosing) return@postDelayed
            OverlayPermissionSheet(this, OverlayPermission.SOURCE_CHARGING) {
                Toast.makeText(this, tr("Done! Screens can now open by themselves"), Toast.LENGTH_SHORT).show()
            }.show()
        }, OVERLAY_ASK_DELAY_MS)
    }

    override fun onStop() {
        isVisible = false
        if (receiversRegistered) {
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(screenReceiver)
            receiversRegistered = false
        }
        stopAnimations()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleLaunch(intent: Intent) {
        ChargingFeature.cancelAlerts(this)
        // The live battery state wins: the alert that opened us can be older than the last
        // plug/unplug. Its extra only matters if the battery state can't be read.
        val battery = BatteryInfo.read(this)
        if (battery != null) {
            render(battery)
        } else {
            render(BatteryInfo(percent = 0, plugged = 0, status = 0), intent.getBooleanExtra(EXTRA_CONNECTED, false))
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    // region Battery state

    private fun render(battery: BatteryInfo, connected: Boolean = battery.isPlugged) = with(binding) {
        val newMode = when {
            !connected -> Mode.ON_BATTERY
            battery.isFull || battery.percent >= 100 -> Mode.FULL
            else -> Mode.CHARGING
        }

        // "78" with a half-size "%"
        tvBatteryPercent.text = SpannableString("${battery.percent}%").apply {
            setSpan(RelativeSizeSpan(0.5f), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        progressBattery.setProgressCompat(battery.percent, true)
        progressBattery.setIndicatorColor(
            when {
                connected -> 0xFFFFFFFF.toInt()
                battery.percent <= 15 -> 0xFFFF6B6B.toInt()
                else -> 0xFFD1FAE5.toInt()
            }
        )

        when (newMode) {
            Mode.CHARGING -> {
                tvBatteryLabel.text = tr("CHARGING")
                tvChargingStatus.text = tr("Charging started")
                tvChargingMessage.text = tr("Your phone is charging. Unplug at 80% to keep the battery healthy.")
            }
            Mode.FULL -> {
                tvBatteryLabel.text = tr("FULLY CHARGED")
                tvChargingStatus.text = tr("Battery is full")
                tvChargingMessage.text = tr("You can unplug the charger now to protect your battery.")
            }
            Mode.ON_BATTERY -> {
                tvBatteryLabel.text = tr("ON BATTERY")
                tvChargingStatus.text = tr("Charger removed")
                tvChargingMessage.text = if (battery.percent <= 20) {
                    tr("Battery is low. Close unused apps or plug in soon.")
                } else {
                    tr("Running on battery. Clean junk and close apps to make it last longer.")
                }
            }
        }
        renderStats(battery, newMode)

        if (newMode != mode) {
            mode = newMode
            ivChargingIcon.setImageResource(
                if (connected) R.drawable.ic_charging_bolt else R.drawable.ic_charging_unplugged
            )
            viewRing.setBackgroundResource(
                if (connected) R.drawable.bg_charging_ring_dashed else R.drawable.bg_charging_ring_dotted
            )
            if (isVisible) startAnimations()
        }
    }

    /** Battery temperature, then time to full while charging or the power source otherwise. */
    private fun renderStats(battery: BatteryInfo, mode: Mode) = with(binding) {
        tvStatFirst.text = if (battery.temperatureTenths > 0) {
            String.format(Locale.US, "%.1f°C", battery.temperatureC)
        } else {
            "--"
        }
        tvStatFirstLabel.text = tr("Temperature")

        when (mode) {
            Mode.FULL -> {
                tvStatSecondLabel.text = tr("Battery")
                tvStatSecond.text = tr("Full")
            }
            Mode.CHARGING -> {
                val remaining = chargeTimeRemainingMs()
                if (remaining > 0) {
                    tvStatSecondLabel.text = tr("Full in")
                    tvStatSecond.text = formatDuration(remaining)
                } else {
                    // Unknown (or API < 28): show what's charging the phone instead
                    tvStatSecondLabel.text = tr("Charger")
                    tvStatSecond.text = battery.chargerType()
                }
            }
            Mode.ON_BATTERY -> {
                tvStatSecondLabel.text = tr("Power")
                tvStatSecond.text = tr("On battery")
            }
        }
    }

    /** API 28+; -1 when the system can't estimate it. */
    private fun chargeTimeRemainingMs(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()
        } else {
            -1L
        }

    private fun formatDuration(ms: Long): String {
        val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        if (minutes < 60) return trf("%1\$d min", minutes.toInt())
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) trf("%1\$d hr", hours.toInt()) else trf("%1\$d hr %2\$d min", hours.toInt(), rest.toInt())
    }

    // endregion

    // region Animations

    /** Charging: pulsing glow, spinning dashed ring, blinking bolt. Full: glow only. On battery: still. */
    private fun startAnimations() = with(binding) {
        stopAnimations()
        val current = mode ?: return@with
        val running = mutableListOf<ValueAnimator>()
        if (current == Mode.ON_BATTERY) {
            viewGlow.alpha = 0.36f
        } else {
            running += ObjectAnimator.ofPropertyValuesHolder(
                viewGlow,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
            ).apply { duration = 1200; repeatMode = ValueAnimator.REVERSE }
        }
        if (current == Mode.CHARGING) {
            running += ObjectAnimator.ofFloat(viewRing, View.ROTATION, 0f, 360f).apply {
                duration = 40_000
                interpolator = LinearInterpolator()
                repeatMode = ValueAnimator.RESTART
            }
            running += ObjectAnimator.ofFloat(ivChargingIcon, View.ALPHA, 0.3f, 1f).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
            }
        }
        running.forEach {
            it.repeatCount = ValueAnimator.INFINITE
            it.start()
        }
        animators = running
    }

    private fun stopAnimations() = with(binding) {
        animators.forEach { it.cancel() }
        animators = emptyList()
        viewGlow.alpha = 1f
        viewGlow.scaleX = 1f
        viewGlow.scaleY = 1f
        viewRing.rotation = 0f
        ivChargingIcon.alpha = 1f
    }

    // endregion

    // region Ad sheet

    private fun isUnlocked() = (userPresent || !keyguard.isKeyguardLocked) && !screenOffSinceUnlock

    /** Loads the ad once the phone is unlocked and shows the sheet while it loads or is loaded. */
    private fun updateAdSheet() {
        if (adState == AdState.NONE && isUnlocked()) requestAd()
        setAdSheetShown((adState == AdState.LOADING || adState == AdState.LOADED) && isUnlocked())
    }

    private fun setAdSheetShown(show: Boolean) {
        if (binding.layoutAdSheet.isVisible == show) return
        binding.layoutAdSheet.isVisible = show
        // Dark nav-bar icons over the light sheet, light ones over the green background
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = show
        applyBottomInsets()
    }

    /** The nav bar sits under the ad sheet when it's shown, otherwise under the content. */
    private fun applyBottomInsets() = with(binding) {
        val density = resources.displayMetrics.density
        val sheetShown = layoutAdSheet.isVisible
        layoutContent.updatePadding(bottom = if (sheetShown) 0 else navBarInset)
        layoutAdSheet.updatePadding(bottom = (12 * density).toInt() + navBarInset)
    }

    private fun requestAd() {
        adState = AdState.LOADING
        ScreenAds.preloadInter(this)
        ScreenAds.loadNative(this, ScreenConfig.AD_CHARGING, binding.layoutAdsHolder) { shown ->
            if (isDestroyed) return@loadNative
            adState = if (shown) AdState.LOADED else AdState.FAILED
            updateAdSheet()
        }
    }

    // endregion

    /**
     * Back / close: shows the interstitial, then closes. Google's interstitial can't draw over the
     * lock screen, so a locked phone is asked to unlock first; if the user cancels the screen just
     * closes.
     */
    private fun closeWithAd() {
        if (isClosing) return
        isClosing = true
        val showThenFinish = { ScreenAds.showInter(this) { finish() } }
        if (keyguard.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = showThenFinish()
                override fun onDismissCancelled() = finish()
                override fun onDismissError() = finish()
            })
        } else {
            showThenFinish()
        }
    }

    /** Unlocks first when needed, then opens the app. */
    private fun openCleaner() {
        if (!keyguard.isKeyguardLocked) {
            launchApp()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchApp()
            })
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            launchApp()
        }
    }

    private fun launchApp() {
        // This screen has its own task, so the app opens in its own
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
