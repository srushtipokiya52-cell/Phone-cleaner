package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.ads.AdRemoteConfig
import com.ads.AdsManager
import com.ads.module.ads.wrapper.ApNativeAd
import com.ads.populateNativeAdView
import com.ads.ConsentHandler
import com.ads.RemoteConfigUtils
import com.ads.inter_splash
import com.ads.native_onboarding_fullscreen_1_3
import com.ads.native_onboarding_fullscreen_2_3
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.ads.module.funtion.AdCallback
import com.google.android.gms.ads.LoadAdError
import com.itg.template.data.pref.AppSharedPref
import com.itg.template.ui.bases.ext.isNetwork
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.dp
import com.phone.cleanerapp.storage.mediafiles.core.enterApp
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentIntroBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentLanguageBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentSplashBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemLanguageBinding
import kotlinx.coroutines.delay

class SplashFragment : VBFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate), RemoteConfigUtils.Listener {

    private companion object {
        const val WAIT_FIRST_RUN_MS = 10_000L   // nothing fetched yet: first fetch also registers with Firebase
        const val WAIT_RETURNING_MS = 4_000L    // cached live values already active: short wait
        const val INTER_WATCHDOG_MS = 20_000L   // 15s load timeout + 3s show delay + margin
    }

    private var animator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var appSharedPref: AppSharedPref? = null
    private var consentHandler: ConsentHandler? = null
    private var rcTimer: CountDownTimer? = null
    private var getConfigSuccess = false   // set by loadSuccess()
    private var isConfigChecked = false    // guards the one-time "RC done" step
    private var done = false               // guards the one-time navigation

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.version.text = trf("Version %1\$s", BuildConfig.VERSION_NAME)
        animator = ValueAnimator.ofInt(0, 100).apply {
            duration = 1600
            addUpdateListener { bOrNull?.progress?.setProgressCompat(it.animatedValue as Int, false) }
            start()
        }

        val activity = requireActivity()
        val pref = AppSharedPref(activity).also { appSharedPref = it }

        // 1. Start the Firebase fetch immediately; it runs while consent is shown
        RemoteConfigUtils.init(activity.applicationContext, this)

        // 2. Consent (first run, online, non-global user), then 3. wait for Remote Config
        val consent = ConsentHandler(
            activity = activity,
            appSharedPref = pref,
            trackingSuffix = 1,
            onConsentFlowCompleted = { waitForRemoteConfig() }
        ).also { consentHandler = it }
        if (!pref.isConfirmConsent && !pref.isUserGlobal && activity.isNetwork()) {
            consent.requestConsent()
        } else {
            waitForRemoteConfig()
        }
    }

    /** RemoteConfigUtils.Listener: fetchAndActivate finished (success or failure). */
    override fun loadSuccess() {
        getConfigSuccess = true
        activity?.runOnUiThread { proceedIfReady() }
    }

    private fun waitForRemoteConfig() {
        if (isConfigChecked) return
        if (getConfigSuccess) { proceedIfReady(); return }

        val waitMs = if (RemoteConfigUtils.hasFetchedBefore()) WAIT_RETURNING_MS else WAIT_FIRST_RUN_MS
        rcTimer?.cancel()
        rcTimer = object : CountDownTimer(waitMs, 100) {
            override fun onTick(left: Long) {
                if (getConfigSuccess && !isConfigChecked) { proceedIfReady(); cancel() }
            }
            override fun onFinish() {
                // Timed out: continue with cached live or bundled JSON
                if (!isConfigChecked) proceedIfReady()
            }
        }.start()
    }

    private fun proceedIfReady() {
        if (isConfigChecked || !isAdded) return
        isConfigChecked = true
        rcTimer?.cancel()
        loadAdsAndProceed()
    }

    private fun loadAdsAndProceed() {
        val activity = requireActivity()
        // 4. Ad config from the live JSON (or bundled default). From here AdRemoteConfig.xxx is final.
        AdRemoteConfig.initialize(activity, RemoteConfigUtils.getAdRemoteConfig())

        // 5. First run only: warm the Language and Onboarding 1 native ads while the splash runs
        if (!Prefs.get(activity).onboarded) {
            val isFirst = appSharedPref?.firstOnBoarding ?: true
            AdsManager.loadNativeLanguage(activity, isFirst, R.layout.layout_native_onboarding_kumkum)
            AdsManager.loadNativeOnboarding1(activity, isFirst, R.layout.layout_native_onboarding_kumkum)
        }

        // 6. Splash interstitial, then next screen. Every path ends in proceed().
        val cfg = AdRemoteConfig.inter_splash
        val purchased = AppPurchase.getInstance().isPurchased(activity)
        if (cfg.isEnable && !purchased && activity.isNetwork()) {
            ERainAd.getInstance().loadSplashInterstitialAds(activity, cfg.id, 15_000, 3_000, object : AdCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError?) { proceed() }
                override fun onNextAction() { proceed() }
            })
            handler.postDelayed({ proceed() }, INTER_WATCHDOG_MS)
        } else {
            proceed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Inter loaded while backgrounded: show it now or fall through, never stuck
        if (isConfigChecked) {
            ERainAd.getInstance().onCheckShowSplashWhenFail(requireActivity() as androidx.appcompat.app.AppCompatActivity, object : AdCallback() {
                override fun onNextAction() { proceed() }
            }, 1000)
        }
    }

    private fun proceed() {
        if (done || !isAdded || isDetached) return
        done = true
        val nav = findNavController()
        if (Prefs.get(requireContext()).onboarded) {
            nav.navigate(R.id.homeFragment, null, navOptions { popUpTo(R.id.splashFragment) { inclusive = true } })
        } else {
            nav.navigate(R.id.languageFragment, null, navOptions { popUpTo(R.id.splashFragment) { inclusive = true } })
        }
    }

    override fun onDestroyView() {
        animator?.cancel()
        rcTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        consentHandler?.clear()
        super.onDestroyView()
    }
}

class Lang(val tag: String, val code: String, val native: String, val name: String)

val LANGUAGES = listOf(
    Lang("en", "EN", "English", "English"),
    Lang("hi", "HI", "हिन्दी", "Hindi"),
    Lang("gu", "GU", "ગુજરાતી", "Gujarati"),
    Lang("es", "ES", "Español", "Spanish"),
    Lang("fr", "FR", "Français", "French"),
    Lang("de", "DE", "Deutsch", "German"),
    Lang("pt", "PT", "Português", "Portuguese"),
    Lang("ar", "AR", "العربية", "Arabic"),
    Lang("in", "ID", "Bahasa Indonesia", "Indonesian"),
    Lang("ru", "RU", "Русский", "Russian"),
)

class LanguageFragment : VBFragment<FragmentLanguageBinding>(FragmentLanguageBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = Prefs.get(requireContext())
        var selected = prefs.language
        val adapter = RowAdapter<Lang, ItemLanguageBinding>(ItemLanguageBinding::inflate) { row, l, _ ->
            row.code.text = l.code
            row.nativeName.text = l.native
            row.name.text = l.name
            val on = l.tag == selected
            row.row.setBackgroundResource(if (on) R.drawable.bg_lang_on else R.drawable.bg_lang_off)
            row.tick.show(on)
            row.row.onClick {
                selected = l.tag
                b.list.adapter?.notifyDataSetChanged()
            }
        }
        adapter.items = LANGUAGES
        b.list.adapter = adapter
        if (prefs.onboarded) b.btnContinue.text = tr("Apply")
        setupLanguageAd(prefs.onboarded)
        b.btnContinue.onClick {
            prefs.language = selected
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
            if (prefs.onboarded) back() else go(R.id.introFragment)
        }
    }
}

/** Language ad: first run only (preloaded by the Splash). Opened from Settings it shows none. */
private fun LanguageFragment.setupLanguageAd(fromSettings: Boolean) {
    val sheet = view?.findViewById<android.view.View>(R.id.adSheet) ?: return
    if (fromSettings) { sheet.visibility = View.GONE; return }
    val activity = requireActivity()
    val isFirst = AppSharedPref(activity).firstOnBoarding
    if (AdsManager.nativeLanguageAdLive.value == null) {
        AdsManager.loadNativeLanguage(activity, isFirst, R.layout.layout_native_onboarding_kumkum)
    }
    val shimmer = sheet.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_native_medium)
    val holder = sheet.findViewById<android.widget.FrameLayout>(R.id.adHolder)
    AdsManager.nativeLanguageAdLive.observe(viewLifecycleOwner) { ad ->
        if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
            populateNativeAdView(activity, ad, holder, shimmer)
        } else {
            sheet.visibility = View.GONE   // no empty band
        }
    }
}

/** Optional fullscreen native between intro pages 2 and 3. Any failure or timeout moves on to page 3. */
class OnboardingFullFragment : VBFragment<com.phone.cleanerapp.storage.mediafiles.databinding.FragmentOnboardingFullBinding>(
    com.phone.cleanerapp.storage.mediafiles.databinding.FragmentOnboardingFullBinding::inflate
) {
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        val shimmer = b.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_native_full)
        b.btnClose.onClick { navigateNext() }
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateNext()
        })

        AdsManager.nativeAdOnBoardingFullLive.observe(viewLifecycleOwner) { ad ->
            if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
                populateNativeAdView(activity, ad, b.adHolder, shimmer)
                b.btnClose.visibility = View.VISIBLE   // only once the ad is on screen
            } else {
                navigateNext()   // no ad (disabled, offline, failed): never block the flow
            }
        }
        // The SDK may never call back: don't hold the user here
        handler.postDelayed({ if (b.btnClose.visibility != View.VISIBLE) navigateNext() }, 6_000)
    }

    private fun navigateNext() {
        if (hasNavigated || !isAdded) return
        hasNavigated = true
        AdsManager.nativeAdOnBoardingFullLive.postValue(null)   // never reuse a shown ad
        back()   // intro is already on page 3
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}

class IntroFragment : VBFragment<FragmentIntroBinding>(FragmentIntroBinding::inflate) {
    private var page = 0
    private val handler = Handler(Looper.getMainLooper())

    private val titles = listOf(tr("Free up space in one tap"), tr("Clear duplicate & similar photos"), tr("Faster, cooler, longer battery"))
    private val bodies = listOf(
        tr("Find junk, app cache, leftover files and empty folders that slow your phone down — and clear them safely."),
        tr("We group look-alike shots, blurry pictures and old screenshots, and pick the best one to keep. You review before anything is deleted."),
        tr("Stop heavy background apps, cool the CPU and keep an eye on battery health — all from one place."),
    )

    // Native sheets on pages 0 and 2; page 1 is an ad-free breather.
    private var ad1: ApNativeAd? = null
    private var ad3: ApNativeAd? = null
    private var done1 = false
    private var done3 = false
    private var populated: ApNativeAd? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.skip.onClick { finishIntro() }
        b.btnNext.onClick {
            page++
            if (page == 2 && shouldShowFullScreen()) go(R.id.onboardingFullFragment) else render()
        }
        b.btnStart.onClick { finishIntro() }
        populated = null   // the view was recreated (e.g. back from the fullscreen page)
        setupAds()
        render()
    }

    private fun setupAds() {
        val activity = requireActivity()
        val isFirst = AppSharedPref(activity).firstOnBoarding
        val layout = R.layout.layout_native_onboarding_kumkum
        // Page 0's ad is normally preloaded by the Splash; load only if nothing is there
        if (AdsManager.nativeOnboarding1AdLive.value == null) AdsManager.loadNativeOnboarding1(activity, isFirst, layout)
        AdsManager.nativeOnboarding1AdLive.observe(viewLifecycleOwner) { ad1 = it; done1 = true; renderAd() }
        AdsManager.nativeOnboarding3AdLive.observe(viewLifecycleOwner) { ad3 = it; done3 = true; renderAd() }
        // Warm what the next pages need after the first frame
        handler.postDelayed({
            if (!isAdded) return@postDelayed
            if (AdsManager.nativeOnboarding3AdLive.value == null) AdsManager.loadNativeOnboarding3(activity, isFirst, layout)
            if (AdsManager.nativeAdOnBoardingFullLive.value == null) {
                AdsManager.loadNativeOnboardingFull(activity, isFirst, R.layout.layout_native_onboarding_full)
            }
            AdsManager.loadInterOnboarding(activity)
        }, 100)
    }

    private fun shouldShowFullScreen(): Boolean {
        val isFirst = AppSharedPref(requireContext()).firstOnBoarding
        val cfg = if (isFirst) AdRemoteConfig.native_onboarding_fullscreen_1_3 else AdRemoteConfig.native_onboarding_fullscreen_2_3
        return cfg.isEnable &&
            ERainAd.getInstance().getShouldDisplayNativeOnboardingFull1(cfg.enableUaCheck) &&
            requireContext().isNetwork()
    }

    private fun renderAd() {
        val sheet = bOrNull?.adSheet?.root ?: return
        val slot = when (page) { 0 -> 1; 2 -> 3; else -> 0 }
        if (slot == 0) { sheet.visibility = View.GONE; return }
        val done = if (slot == 1) done1 else done3
        val ad = if (slot == 1) ad1 else ad3
        val shimmer = sheet.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_native_medium)
        val holder = sheet.findViewById<android.widget.FrameLayout>(R.id.adHolder)
        when {
            !done -> {
                sheet.visibility = View.VISIBLE
                holder.visibility = View.GONE
                shimmer.visibility = View.VISIBLE
                shimmer.startShimmer()
            }
            ad == null || (ad.admobNativeAd == null && ad.nativeView == null) -> sheet.visibility = View.GONE
            populated !== ad -> {
                populated = ad
                sheet.visibility = View.VISIBLE
                holder.removeAllViews()
                populateNativeAdView(requireActivity(), ad, holder, shimmer)
            }
            else -> sheet.visibility = View.VISIBLE
        }
    }

    /** Skip / Get started: the onboarding interstitial, then the next screen (always runs). */
    private fun finishIntro() {
        val activity = requireActivity()
        AppSharedPref(activity).firstOnBoarding = false
        AdsManager.showInterOnboarding(activity) {
            if (isAdded) go(R.id.permissionFragment)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun render() {
        val arts = listOf(R.drawable.art_intro1, R.drawable.art_intro2, R.drawable.art_intro3)
        val tops = listOf(R.drawable.bg_intro_top1, R.drawable.bg_intro_top2, R.drawable.bg_intro_top3)
        val starts = listOf("#0B5D41", "#164E63", "#0F172A")
        b.art.setImageResource(arts[page])
        b.top.setBackgroundResource(tops[page])
        (requireActivity() as com.phone.cleanerapp.storage.mediafiles.MainActivity).setStatusColor(android.graphics.Color.parseColor(starts[page]))
        b.introTitle.text = titles[page]
        b.introBody.text = bodies[page]
        b.btnNext.show(page < 2)
        b.btnStart.show(page == 2)
        if (bOrNull != null) renderAd()
        b.dots.removeAllViews()
        for (i in 0..2) {
            val d = View(requireContext())
            val w = if (i == page) 24 else 8
            d.layoutParams = LinearLayout.LayoutParams(requireContext().dp(w), requireContext().dp(8)).apply { marginEnd = requireContext().dp(6) }
            d.setBackgroundResource(R.drawable.bg_dot)
            d.backgroundTintList = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(if (i == page) R.color.brand else R.color.hint)
            )
            b.dots.addView(d)
        }
    }
}
