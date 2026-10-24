package com.itg.template.ui.bases

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.ads.AdsManager
import com.ads.module.billing.AppPurchase
import com.bases.ext.goneView
import com.bases.ext.visibleView
import com.phone.cleanerapp.storage.mediafiles.R
import com.itg.template.ads.AdUnitConfig

data class BannerConfig(
    val adUnitConfig: AdUnitConfig = AdUnitConfig(
        id = "",
        isEnable = false,
        reloadIntervalSeconds = 0
    ),
    val isCollapse: Boolean = false
)

abstract class BaseActivityWithBanner : AppCompatActivity() {

    companion object {
        private const val DISTANCE_TIME_NEED_CHECK_RELOAD_BANNER = 2000L
    }

    abstract val bannerConfig: BannerConfig

    /** Screens that must never show the banner (also stops its periodic reload from showing it again). */
    protected open fun isBannerAllowed(): Boolean = true

    private var timeNeedReloadBanner = 0L
    private var reloadBannerHandler: Handler? = null

    private val reloadBannerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isDestroyed || isFinishing) return
            val shouldReloadBanner = shouldReloadBanner()

            if (timeNeedReloadBanner < System.currentTimeMillis() && shouldReloadBanner) {
                loadBanner()
            }

            if (shouldReloadBanner) {
                reloadBannerHandler?.removeCallbacks(this)
                reloadBannerHandler?.postDelayed(this, DISTANCE_TIME_NEED_CHECK_RELOAD_BANNER)
            } else {
                cleanupHandler()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadBanner()
    }

    override fun onResume() {
        super.onResume()
        reloadBannerIfNeeded()
    }

    override fun onPause() {
        cleanupHandler()
        super.onPause()
    }

    override fun onDestroy() {
        cleanupHandler()
        super.onDestroy()
    }

    fun loadBanner() {
        val frAds = findViewById<FrameLayout>(R.id.fr_banner)

        if (!shouldShowBanner()) {
            timeNeedReloadBanner = 0
            frAds?.goneView()
            cleanupHandler()
            return
        }

        frAds?.visibleView()
        val adUnitConfig = bannerConfig.adUnitConfig
        AdsManager.loadBanner(
            this@BaseActivityWithBanner,
            adUnitConfig,
            frAds,
            bannerConfig.isCollapse
        )

        val distanceReloadBanner = adUnitConfig.reloadIntervalSeconds ?: 0
        if (distanceReloadBanner > 0) {
            timeNeedReloadBanner = System.currentTimeMillis() + distanceReloadBanner * 1000L
        }
    }

    private fun reloadBannerIfNeeded() {
        if (shouldReloadBanner()) {
            if (reloadBannerHandler == null) {
                reloadBannerHandler = Handler(Looper.getMainLooper())
            }
            reloadBannerHandler?.postDelayed(
                reloadBannerRunnable,
                DISTANCE_TIME_NEED_CHECK_RELOAD_BANNER
            )
        } else {
            cleanupHandler()
        }
    }

    private fun shouldShowBanner(): Boolean {
        val isAdEnabled = isBannerAllowed() && bannerConfig.adUnitConfig.isEnable
                && !AppPurchase.getInstance().isPurchased(this)
        val frAds = findViewById<FrameLayout>(R.id.fr_banner)
        return isAdEnabled && frAds != null
    }

    private fun shouldReloadBanner(): Boolean {
        val shouldShowBanner = shouldShowBanner()
        val distanceReloadBanner = bannerConfig.adUnitConfig.reloadIntervalSeconds ?: 0
        return shouldShowBanner && distanceReloadBanner > 0
    }

    private fun cleanupHandler() {
        reloadBannerHandler?.removeCallbacksAndMessages(null)
        reloadBannerHandler = null
    }
}
