package com.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.itg.template.ads.AdUnitConfig

/**
 * Loads a native ad styled as a banner (layout_native_banner) into [container] and refreshes it
 * every `reloadIntervalSeconds` of the ad JSON unit [config] (0 or missing = no refresh, never
 * faster than [MIN_REFRESH_S]). One controller per screen:
 * - [start] when the screen is created, [onResume] / [onPause] from its lifecycle, [stop] when destroyed.
 * - The old ad stays up until the new one is ready, so a refresh never flashes empty.
 * - If the first ad fails, [container] is hidden. Disabled units and buyers show nothing.
 * Used by the Wallpaper tab (RC key native_wallpaper); same behaviour as the Japa banner.
 */
class NativeBannerController(
    private val activity: Activity,
    private val owner: LifecycleOwner,
    private val container: View,
    private val shimmer: ShimmerFrameLayout,
    private val holder: FrameLayout,
    private val config: () -> AdUnitConfig,
    private val loader: (Activity, Int) -> androidx.lifecycle.LiveData<ApNativeAd?>,
    private val layoutRes: Int,
    private val isPurchased: () -> Boolean
) {

    companion object {
        const val MIN_REFRESH_S = 15
    }

    private val handler = Handler(Looper.getMainLooper())
    private var loadedOnce = false
    private var loading = false
    private var stopped = false
    private var nextReloadAt = 0L
    private val reload = Runnable { load() }

    private fun enabled() = config().isEnable && !isPurchased()

    fun start() {
        stopped = false
        if (!enabled()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        shimmer.startShimmer()
        load()
    }

    fun onPause() {
        handler.removeCallbacks(reload)
    }

    /** Back on screen: reload now if the interval ran out meanwhile, else wait out the rest of it. */
    fun onResume() {
        if (stopped || !loadedOnce || loading || !enabled()) return
        val seconds = config().reloadIntervalSeconds ?: 0
        if (seconds <= 0) return
        handler.removeCallbacks(reload)
        val left = nextReloadAt - SystemClock.elapsedRealtime()
        if (left <= 0) load() else handler.postDelayed(reload, left)
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun load() {
        if (loading || stopped || activity.isDestroyed || activity.isFinishing || !enabled()) return
        loading = true
        loader(activity, layoutRes).observe(owner) { ad ->
            loading = false
            if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
                loadedOnce = true
                populateNativeAdView(activity, ad, holder, shimmer)
            } else if (!loadedOnce) {
                container.visibility = View.GONE
            }
            scheduleRefresh()
        }
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(reload)
        val seconds = config().reloadIntervalSeconds ?: 0
        if (seconds <= 0 || stopped || !enabled()) return
        val delay = maxOf(seconds, MIN_REFRESH_S) * 1000L
        nextReloadAt = SystemClock.elapsedRealtime() + delay
        handler.postDelayed(reload, delay)
    }
}
