package com.phone.cleanerapp.storage.mediafiles.core

import android.app.Activity
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AdConfig {
    /** Google's public test banner unit. Replace with your own AdMob banner unit ID before release. */
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
}

/** One adaptive banner that lives in the bottom slot of the main screen. */
class AdBanner(private val activity: Activity, private val slot: FrameLayout) {
    private var adView: AdView? = null

    fun start() {
        MobileAds.initialize(activity) { }
        val widthDp = (slot.resources.displayMetrics.widthPixels / slot.resources.displayMetrics.density).toInt()
        val view = AdView(activity).apply {
            adUnitId = AdConfig.BANNER_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        adView = view
        slot.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.CENTER
        })
        view.loadAd(AdRequest.Builder().build())
    }

    fun pause() = adView?.pause()
    fun resume() = adView?.resume()
    fun destroy() = adView?.destroy()
}
