package com.app

import android.annotation.SuppressLint
import android.app.Activity
import androidx.lifecycle.ProcessLifecycleOwner
import com.ads.AdRemoteConfig
import com.ads.RemoteConfigUtils
import com.ads.module.admob.Admob
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.application.AdsMultiDexApplication
import com.ads.module.config.AdjustConfig
import com.ads.module.config.ERainAdConfig
import com.ads.open_resume
import com.google.android.gms.ads.MobileAds
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.phone.cleanerapp.storage.mediafiles.R
import timber.log.Timber

open class GlobalApp : AdsMultiDexApplication() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: GlobalApp

        @SuppressLint("StaticFieldLeak")
        var currentActivity: Activity? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        MobileAds.initialize(this) {}
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        initAdRemoteConfig()
        initAds()

        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        registerActivityLifecycleCallbacks(AppActivityLifecycleCallbacks())
    }

    /** Last live ad config saved by RemoteConfigUtils, else the bundled assets/ad_config(.debug).json. */
    private fun initAdRemoteConfig() {
        AdRemoteConfig.initialize(this, RemoteConfigUtils.getCachedAdConfig(this))
    }

    private fun initAds() {
        val environment =
            if (BuildConfig.DEBUG) ERainAdConfig.ENVIRONMENT_DEVELOP else ERainAdConfig.ENVIRONMENT_PRODUCTION
        mERainAdConfig = ERainAdConfig(this, environment)

        mERainAdConfig.adjustConfig = AdjustConfig(true, resources.getString(R.string.adjust_token))
        mERainAdConfig.facebookClientToken = resources.getString(R.string.facebook_client_token)
        mERainAdConfig.adjustTokenTiktok = resources.getString(R.string.event_token)
        mERainAdConfig.intervalInterstitialAd = 35
        mERainAdConfig.idAdResume = ""

        ERainAd.getInstance().init(this, mERainAdConfig)

        Admob.getInstance().setDisableAdResumeWhenClickAds(true)
        Admob.getInstance().setOpenActivityAfterShowInterAds(false)
        initAppOpen()
    }

    /** App Open (resume) ads; the id comes from the last live config. */
    private fun initAppOpen() {
        val appOpen = AppOpenManager.getInstance() ?: return
        val resume = AdRemoteConfig.open_resume
        if (resume.isEnable && resume.id.isNotBlank()) appOpen.setAppResumeAdId(resume.id)
    }
}
