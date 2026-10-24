package com.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ads.module.R
import com.ads.module.ads.ERainAd
import com.ads.module.ads.wrapper.ApInterstitialAd
import com.ads.module.ads.wrapper.ApNativeAd
import com.ads.module.billing.AppPurchase
import com.ads.module.funtion.AdCallback
import com.ads.module.funtion.RewardCallback
import com.ads.module.util.AppConstant
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.itg.template.ads.AdUnitConfig
import com.bases.ext.goneView
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object AdsManager {

    val nativeLanguageAdLive = MutableLiveData<ApNativeAd?>()
    val nativeLanguageClickAdLive = MutableLiveData<ApNativeAd?>()
    val nativeOnboarding1AdLive = MutableLiveData<ApNativeAd?>()
    val nativeOnboarding3AdLive = MutableLiveData<ApNativeAd?>()
    val nativeOnboarding4AdLive = MutableLiveData<ApNativeAd?>()
    val nativeAdOnBoardingFullLive = MutableLiveData<ApNativeAd?>()
    val nativeAdOnBoardingFull2Live = MutableLiveData<ApNativeAd?>()
    val nativeSurveyAdLive = MutableLiveData<ApNativeAd?>()
    val nativeConfirmUninstallAdLive = MutableLiveData<ApNativeAd?>()
    val nativeWelcomeAdLive = MutableLiveData<ApNativeAd?>()
    val nativePermissionAdLive = MutableLiveData<ApNativeAd?>()
    val nativeHomeAdLive = MutableLiveData<ApNativeAd?>()
    var interWelcomeAdLive = MutableLiveData<ApInterstitialAd?>()
    var interHomeAdLive = MutableLiveData<ApInterstitialAd?>()

    // Auto-resolve config for each loaded native ad
    private val adConfigMap = mutableMapOf<ApNativeAd, AdUnitConfig>()
    fun getAdConfig(ad: ApNativeAd): AdUnitConfig? = adConfigMap[ad]

    private var interSplashAd: ApInterstitialAd? = null
    private var interOnboarding: ApInterstitialAd? = null
    private var interHome: ApInterstitialAd? = null

    private var rewardExample: RewardedAd? = null

    private fun loadNativeInternal(
        activity: Activity,
        config: AdUnitConfig,
        layoutRes: Int,
        liveData: MutableLiveData<ApNativeAd?>,
        shouldDisplay: Boolean = true,
    ) {
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(activity)
            || !activity.isNetworkAvailable()
            || !shouldDisplay
        ) {
            liveData.postValue(null)
            return
        }
        ERainAd.getInstance()
            .loadNativeAdResultCallback(activity, config.id, layoutRes, object : AdCallback() {
                override fun onNativeAdLoaded(nativeAd: ApNativeAd) {
                    super.onNativeAdLoaded(nativeAd)
                    adConfigMap[nativeAd] = config
                    liveData.postValue(nativeAd)
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    super.onAdFailedToLoad(adError)
                    Log.e("Milind", "onAdFailedToLoad: ${adError}" )
                    liveData.postValue(null)
                }
            })
    }

    fun loadNativeLanguage(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_language_1 else AdRemoteConfig.native_language_2
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeLanguageAdLive
        )
    }

    fun loadNativeLanguageClick(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_language_1_click else AdRemoteConfig.native_language_2_click
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeLanguageClickAdLive
        )
    }

    fun loadNativeOnboarding1(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_1_1 else AdRemoteConfig.native_onboarding_2_1
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeOnboarding1AdLive
        )
    }

    fun loadNativeOnboarding3(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_1_3 else AdRemoteConfig.native_onboarding_2_3
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeOnboarding3AdLive,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingNormal1(config.enableUaCheck)
        )
    }

    fun loadNativeOnboarding4(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_1_4 else AdRemoteConfig.native_onboarding_2_4
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeOnboarding4AdLive,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingNormal2(config.enableUaCheck)
        )
    }

    fun loadNativeOnboardingFull(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_fullscreen_1_3 else AdRemoteConfig.native_onboarding_fullscreen_2_3
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeAdOnBoardingFullLive,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingFull1(config.enableUaCheck)
        )
    }

    fun loadNativeOnboardingFull2(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_fullscreen_1_3 else AdRemoteConfig.native_onboarding_fullscreen_2_3
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeAdOnBoardingFull2Live,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingFull2(config.enableUaCheck)
        )
    }

    fun loadNativePermission(activity: Activity, layoutRes: Int) {
        val config = AdRemoteConfig.native_permission
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativePermissionAdLive,
            ERainAd.getInstance().getShouldDisplayNativePermission(config.enableUaCheck)
        )
    }

    /** Home card native (RC key native_home). A fresh request per screen visit, so a revisit never gets a shown/expired ad. */
    fun loadNativeHomeCard(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_home, layoutRes, result)
        return result
    }

    /** All Tools screen native (RC key native_tools, separate from native_home). A fresh request per visit. */
    fun loadNativeTools(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_tools, layoutRes, result)
        return result
    }

    /** Settings screen native (RC key native_settings). A fresh request per visit. */
    fun loadNativeSettings(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_settings, layoutRes, result)
        return result
    }

    fun loadNativeHome(activity: Activity, layoutRes: Int) {
        val config = AdRemoteConfig.native_home
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeHomeAdLive,
            ERainAd.getInstance().getShouldDisplayNativeHome(config.enableUaCheck)
        )
    }

    /**
     * Medium native for the charging screen sheet (RC key native_charging_medium). A fresh request
     * per screen, so a reopened screen never gets an old or expired ad. Only call while the device
     * is unlocked: no ads on the lock screen.
     */
    /** Japa counter native banner (RC key native_japa). A fresh request per call, so each refresh gets a new ad. */
    fun loadNativeJapa(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_japa, layoutRes, result)
        return result
    }

    /** Wallpaper tab native banner (RC key native_wallpaper). A fresh request per call, so refreshes get a new ad. */
    fun loadNativeWallpaper(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_wallpaper, layoutRes, result)
        return result
    }

    /** Wallpaper preview native banner (RC key native_wallpaper_preview). A fresh request per call. */
    fun loadNativeWallpaperPreview(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_wallpaper_preview, layoutRes, result)
        return result
    }

    /** After-call screen sheet (RC key native_call_end). A fresh request per screen. */
    fun loadNativeCallEnd(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_call_end, layoutRes, result)
        return result
    }

    fun loadNativeChargingMedium(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_charging_medium, layoutRes, result)
        return result
    }

    /**
     * Jap Reminder screen sheet (RC key native_jap_reminder). A fresh request per screen, so a
     * reopened screen never gets an old or expired ad.
     */
    fun loadNativeJapReminder(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_jap_reminder, layoutRes, result)
        return result
    }

    /**
     * The bottom native shared by every screen that has none of its own (RC key native_full): one
     * config for all of them. A fresh request per screen, so a reopened screen never gets an old
     * or expired ad.
     */
    fun loadNativeFull(activity: Activity, layoutRes: Int): LiveData<ApNativeAd?> {
        val result = MutableLiveData<ApNativeAd?>()
        loadNativeInternal(activity, AdRemoteConfig.native_full, layoutRes, result)
        return result
    }

    fun loadNativeSurvey(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity, AdRemoteConfig.native_survey, layoutRes, nativeSurveyAdLive,
            ERainAd.getInstance()
                .getShouldDisplayWidgetUninstall(AdRemoteConfig.native_survey.enableUaCheck)
        )
    }

    fun loadNativeConfirmUninstall(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity,
            AdRemoteConfig.native_confirm_uninstall,
            layoutRes,
            nativeConfirmUninstallAdLive,
            ERainAd.getInstance()
                .getShouldDisplayWidgetUninstall(AdRemoteConfig.native_confirm_uninstall.enableUaCheck)
        )
    }

    fun loadNativeWelcome(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity,
            AdRemoteConfig.native_welcome,
            layoutRes,
            nativeWelcomeAdLive,
            ERainAd.getInstance().getShouldDisplayNativeWelcomeBack(AdRemoteConfig.native_welcome.enableUaCheck)
        )
    }

    val nativeDashboardPreviewLive = MutableLiveData<ApNativeAd?>()

    fun loadNativeForDashboard(activity: Activity, configKey: String, layoutRes: Int) {
        val config = try {
            AdRemoteConfig.getInstance().ads[configKey]
                ?: AdUnitConfig(id = "", isEnable = false)
        } catch (_: Exception) {
            AdUnitConfig(id = "", isEnable = false)
        }
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeDashboardPreviewLive,
            shouldDisplay = true
        )
    }

    fun loadNativeLanguageForDashboard(activity: Activity, layoutRes: Int) {
        loadNativeForDashboard(activity, "native_language_1", layoutRes)
    }

    fun loadNativeFullForDashboard(activity: Activity, layoutRes: Int) {
        loadNativeForDashboard(activity, "native_onboarding_fullscreen_1_3", layoutRes)
    }

    fun loadInterOnboarding(context: Context, ignoreLimit: Boolean = false) {
        val config = AdRemoteConfig.inter_onboarding
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance().getShouldDisplayInterOnboarding(config.enableUaCheck))
        ) {
            interOnboarding = null
            return
        }
        interOnboarding =
            ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {})
    }

    fun showInterOnboarding(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interOnboarding
        val allowShow = ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterOnboarding(AdRemoteConfig.inter_onboarding.enableUaCheck)
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && allowShow
        ) {
            ERainAd.getInstance()
                .forceShowInterstitial(context, interstitial, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        onAction()
                    }
                }, true)
        } else {
            onAction()
        }
    }

    fun loadInterWelcome(context: Context, ignoreLimit: Boolean = false) {
        val cached = interWelcomeAdLive.value?.takeIf { it.isReady }
        val request = MutableLiveData<ApInterstitialAd?>()
        interWelcomeAdLive = request
        fun emit(ad: ApInterstitialAd?) {
            request.value = ad
        }
        val config = AdRemoteConfig.inter_welcome
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance()
                .getShouldDisplayInterWelcomeBack(config.enableUaCheck))
        ) {
            emit(null)
            return
        }
        if (cached != null) {
            emit(cached)
            return
        }
        var loaded: ApInterstitialAd? = null
        loaded = ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {
            override fun onApInterstitialLoad(apInterstitialAd: ApInterstitialAd?) {
                super.onApInterstitialLoad(apInterstitialAd)
                emit(apInterstitialAd ?: loaded)
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                super.onAdFailedToLoad(adError)
                emit(null)
            }
        })
        if (loaded?.isReady == true) emit(loaded)
    }

    fun showInterWelcome(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interWelcomeAdLive.value
        val allowShow = ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_welcome.enableUaCheck)
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && allowShow
        ) {
            ERainAd.getInstance()
                .forceShowInterstitial(context, interstitial, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        interWelcomeAdLive = MutableLiveData()
                        onAction()
                    }
                }, false)
        } else {
            onAction()
        }
    }

    fun loadInterHome(context: Context, ignoreLimit: Boolean = false) {
        val config = AdRemoteConfig.inter_home
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || !context.isNetworkAvailable()
        ) {
            interHome = null
            interHomeAdLive.postValue(null)
            return
        }
        interHome =
            ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {
                override fun onApInterstitialLoad(apInterstitialAd: ApInterstitialAd?) {
                    super.onApInterstitialLoad(apInterstitialAd)
                    interHome = apInterstitialAd ?: interHome
                    interHomeAdLive.postValue(interHome)
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    super.onAdFailedToLoad(adError)
                    interHome = null
                    interHomeAdLive.postValue(null)
                }
            })
    }

    fun isInterHomeReady(): Boolean {
        val interstitial = interHome ?: interHomeAdLive.value
        return interstitial != null && interstitial.isReady
    }

    fun showInterHome(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interHome ?: interHomeAdLive.value
        val config = AdRemoteConfig.inter_home
        if (interstitial != null && interstitial.isReady && config.isEnable && !AppPurchase.getInstance().isPurchased(context)) {
            ERainAd.getInstance().forceShowInterstitial(context, interstitial, object : AdCallback() {
                override fun onNextAction() {
                    super.onNextAction()
                    interHome = null
                    interHomeAdLive.postValue(null)
                    loadInterHome(context, ignoreLimit)
                    onAction()
                }
            }, true)
        } else {
            if (interHome == null || interHome?.isReady != true) {
                loadInterHome(context, ignoreLimit)
            }
            onAction()
        }
    }

    private var interCharging: ApInterstitialAd? = null

    /** Charging screen interstitial (RC key inter_charging), shown when the screen is closed. */
    fun loadInterCharging(context: Context) {
        val config = AdRemoteConfig.inter_charging
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || !context.isNetworkAvailable()
            || interCharging?.isReady == true
        ) {
            return
        }
        interCharging =
            ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {
                override fun onApInterstitialLoad(apInterstitialAd: ApInterstitialAd?) {
                    super.onApInterstitialLoad(apInterstitialAd)
                    interCharging = apInterstitialAd ?: interCharging
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    super.onAdFailedToLoad(adError)
                    interCharging = null
                }
            })
    }

    /** Shows the charging interstitial if one is ready; [onAction] always runs (after it closes). */
    fun showInterCharging(context: Context, onAction: () -> Unit) {
        val interstitial = interCharging
        val config = AdRemoteConfig.inter_charging
        if (interstitial != null && interstitial.isReady && config.isEnable && !AppPurchase.getInstance().isPurchased(context)) {
            ERainAd.getInstance().forceShowInterstitial(context, interstitial, object : AdCallback() {
                override fun onNextAction() {
                    super.onNextAction()
                    interCharging = null
                    onAction()
                }
            }, true)
        } else {
            onAction()
        }
    }

    private var interCallEnd: ApInterstitialAd? = null

    /** Preloads the after-call screen interstitial (RC key inter_call_end). */
    fun loadInterCallEnd(context: Context) {
        val config = AdRemoteConfig.inter_call_end
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || !context.isNetworkAvailable()
            || interCallEnd?.isReady == true
        ) {
            return
        }
        interCallEnd =
            ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {
                override fun onApInterstitialLoad(apInterstitialAd: ApInterstitialAd?) {
                    super.onApInterstitialLoad(apInterstitialAd)
                    interCallEnd = apInterstitialAd ?: interCallEnd
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    super.onAdFailedToLoad(adError)
                    interCallEnd = null
                }
            })
    }

    /** Shows the after-call interstitial if one is ready; [onAction] always runs (after it closes). */
    fun showInterCallEnd(context: Context, onAction: () -> Unit) {
        val interstitial = interCallEnd
        val config = AdRemoteConfig.inter_call_end
        if (interstitial != null && interstitial.isReady && config.isEnable && !AppPurchase.getInstance().isPurchased(context)) {
            ERainAd.getInstance().forceShowInterstitial(context, interstitial, object : AdCallback() {
                override fun onNextAction() {
                    super.onNextAction()
                    interCallEnd = null
                    onAction()
                }
            }, true)
        } else {
            onAction()
        }
    }

    /** Is a rewarded ad worth asking for with [config]: enabled, and the user hasn't bought the app. */
    fun isRewardAvailable(context: Context, config: AdUnitConfig): Boolean =
        config.isEnable && config.id.isNotBlank() && !AppPurchase.getInstance().isPurchased(context)

    /**
     * Loads and shows a rewarded ad. Exactly one callback fires:
     * [onEarned] once the ad is closed after the reward was earned; [onSkipped] when it was closed
     * early (no reward: don't give the benefit); [onUnavailable] when it could not load or show
     * (no fill, network: the caller decides, e.g. let the user continue).
     */
    fun loadAndShowReward(
        activity: Activity,
        config: AdUnitConfig,
        onEarned: () -> Unit,
        onSkipped: () -> Unit,
        onUnavailable: () -> Unit,
        /** The ad finished loading and is about to show (or failed): hide any loading UI. */
        onLoadFinished: () -> Unit = {}
    ) {
        if (!isRewardAvailable(activity, config)) {
            onUnavailable()
            return
        }
        ERainAd.getInstance().initRewardAds(
            activity,
            config.id,
            object : AdCallback() {
                override fun onRewardAdLoaded(rewardedAd: RewardedAd?) {
                    super.onRewardAdLoaded(rewardedAd)
                    onLoadFinished()
                    var isEarn = false
                    ERainAd.getInstance().showRewardAds(
                        activity,
                        rewardedAd,
                        object : RewardCallback {
                            override fun onUserEarnedReward(var1: RewardItem?) {
                                isEarn = true
                            }

                            override fun onRewardedAdClosed() {
                                if (isEarn) onEarned() else onSkipped()
                            }

                            override fun onRewardedAdFailedToShow(codeError: Int) {
                                onUnavailable()
                            }

                            override fun onAdClicked() {}
                        }
                    )
                }

                override fun onAdFailedToLoad(i: LoadAdError?) {
                    super.onAdFailedToLoad(i)
                    onLoadFinished()
                    onUnavailable()
                }
            }
        )
    }

    fun loadAndShowReward(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        if (AdRemoteConfig.reward_example.isEnable.not() || AppPurchase.getInstance()
                .isPurchased(activity)
        ) {
            onFailed()
            return
        }

        ERainAd.getInstance().initRewardAds(
            activity,
            AdRemoteConfig.reward_example.id,
            object : AdCallback() {
                override fun onRewardAdLoaded(rewardedAd: RewardedAd?) {
                    super.onRewardAdLoaded(rewardedAd)
                    rewardExample = rewardedAd

                    var isEarn = false
                    ERainAd.getInstance().showRewardAds(
                        activity,
                        rewardExample,
                        object : RewardCallback {
                            override fun onUserEarnedReward(var1: RewardItem?) {
                                isEarn = true
                                rewardExample = null
                            }

                            override fun onRewardedAdClosed() {
                                if (isEarn) onSuccess()
                                else onFailed()
                            }

                            override fun onRewardedAdFailedToShow(codeError: Int) {
                                rewardExample = null
                                onFailed()
                            }

                            override fun onAdClicked() {}
                        }
                    )
                }

                override fun onAdFailedToLoad(i: LoadAdError?) {
                    super.onAdFailedToLoad(i)
                    onFailed()
                }
            }
        )
    }

    fun loadBanner(
        activity: AppCompatActivity,
        adUnitConfig: AdUnitConfig,
        frAds: FrameLayout,
        isCollapse: Boolean,
    ) {
        if (adUnitConfig.isEnable) {
            removeBannerView(activity, frAds)
            if (isCollapse) ERainAd.getInstance().loadCollapsibleBanner(
                activity,
                adUnitConfig.id,
                AppConstant.CollapsibleGravity.BOTTOM,
                object : AdCallback() {
                    override fun onAdFailedToLoad(i: LoadAdError?) {
                        super.onAdFailedToLoad(i)
                        frAds.goneView()
                        Timber.tag("AdsManager_Banner")
                            .d("Load banner on ${activity.javaClass.simpleName} failed by : ${i?.message}")
                    }
                })
            else ERainAd.getInstance()
                .loadBanner(activity, adUnitConfig.id, object : AdCallback() {
                    override fun onAdFailedToLoad(i: LoadAdError?) {
                        super.onAdFailedToLoad(i)
                        frAds.goneView()
                        Timber.tag("AdsManager_Banner")
                            .d("Load banner on ${activity.javaClass.simpleName} failed by : ${i?.message}")
                    }
                })
        } else {
            frAds.removeAllViews()
            frAds.goneView()
        }
    }

    @SuppressLint("InflateParams")
    private fun removeBannerView(activity: Activity, frAds: FrameLayout) {
        try {
            val container = frAds.findViewById<FrameLayout>(R.id.banner_container)
            if (container != null) {
                for (i in 0 until container.childCount) {
                    val view = container.getChildAt(i)
                    if (view is AdView) {
                        view.destroy()
                        container.removeView(view)
                    }
                }
            }
            val shimmerFrameLayout = LayoutInflater.from(activity)
                .inflate(R.layout.layout_banner_control, null)
            frAds.removeAllViews()
            frAds.addView(shimmerFrameLayout)
        } catch (_: Exception) {
        }
    }

    fun clearAll() {
        nativeLanguageAdLive.postValue(null)
        nativeLanguageClickAdLive.postValue(null)
        nativeOnboarding1AdLive.postValue(null)
        nativeOnboarding3AdLive.postValue(null)
        nativeOnboarding4AdLive.postValue(null)
        nativeAdOnBoardingFullLive.postValue(null)
        nativeSurveyAdLive.postValue(null)
        nativeConfirmUninstallAdLive.postValue(null)
        nativeWelcomeAdLive.postValue(null)
        interWelcomeAdLive = MutableLiveData()
        interSplashAd = null
        interOnboarding = null
        interHome = null
        interHomeAdLive = MutableLiveData()
    }

    private fun Context.isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }
}
