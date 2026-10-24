package com.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ads.AdRemoteConfig
import com.ads.inter_welcome
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase

class AppLifecycleObserver : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        val currentActivity = GlobalApp.currentActivity ?: return
        if (ResumeAdsEntryRule.shouldShowWelcomeOnResume()
            && !AppOpenManager.getInstance().isInterstitialShowing
            && !AppPurchase.getInstance().isPurchased(currentActivity.applicationContext)
            && ERainAd.getInstance().getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_welcome.enableUaCheck)
        ) {
            // Trigger welcome interstitial on app resume
        }
    }

    override fun onStop(owner: LifecycleOwner) {}
}
