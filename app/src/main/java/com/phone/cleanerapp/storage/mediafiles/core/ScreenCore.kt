package com.phone.cleanerapp.storage.mediafiles.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.phone.cleanerapp.storage.mediafiles.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Values that a remote source (Remote Config, a server) could change later. Nothing here needs a
 * network: every getter falls back to a default, and [set] stores an override that the screens
 * read in any process (the monitor service starts the app process without opening the UI).
 */
object ScreenConfig {
    const val CALL_END_ENABLED = "call_end_enabled"
    const val CALL_END_MIN_SECONDS = "call_end_min_seconds"
    const val CALL_END_MAX_PER_DAY = "call_end_max_per_day"
    const val OVERLAY_ASK_SESSIONS = "overlay_ask_sessions"
    const val OVERLAY_ASK_ON_CHARGING = "overlay_ask_on_charging"
    const val AD_CHARGING = "native_charging"
    const val AD_CALL_END = "native_call_end"
    const val AD_INTER_CHARGING = "inter_charging"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("screen_config", Context.MODE_PRIVATE)

    fun set(ctx: Context, key: String, value: Any) = sp(ctx).edit {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Long -> putLong(key, value)
            is Int -> putLong(key, value.toLong())
            else -> putString(key, value.toString())
        }
    }

    /** Kill switch for the after-call screen (on by default). */
    fun callEndEnabled(ctx: Context) = sp(ctx).getBoolean(CALL_END_ENABLED, true)

    /** Calls shorter than this never show the after-call screen. */
    fun callEndMinSeconds(ctx: Context) = sp(ctx).getLong(CALL_END_MIN_SECONDS, 10L)

    /** Daily cap of after-call screens. */
    fun callEndMaxPerDay(ctx: Context) = sp(ctx).getLong(CALL_END_MAX_PER_DAY, 5L)

    /** App sessions (Home opens) on which the overlay permission sheet is shown. */
    fun overlayAskSessions(ctx: Context) = sp(ctx).getString(OVERLAY_ASK_SESSIONS, "2,5,8") ?: "2,5,8"

    fun overlayAskOnCharging(ctx: Context) = sp(ctx).getBoolean(OVERLAY_ASK_ON_CHARGING, true)

    /** A unit missing from the config is on; set it to false to switch one ad off. */
    fun adEnabled(ctx: Context, unit: String) = sp(ctx).getBoolean("ad_$unit", true)
}

/** Switches that belong to the user: Settings toggles and the counters behind the caps. */
object ScreenPrefs {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("screen_prefs", Context.MODE_PRIVATE)

    fun getBool(ctx: Context, key: String, def: Boolean) = sp(ctx).getBoolean(key, def)
    fun putBool(ctx: Context, key: String, v: Boolean) = sp(ctx).edit { putBoolean(key, v) }
    fun getInt(ctx: Context, key: String, def: Int) = sp(ctx).getInt(key, def)
    fun putInt(ctx: Context, key: String, v: Int) = sp(ctx).edit { putInt(key, v) }
    fun getString(ctx: Context, key: String): String? = sp(ctx).getString(key, null)
    fun putString(ctx: Context, key: String, v: String) = sp(ctx).edit { putString(key, v) }
    fun getStringSet(ctx: Context, key: String): Set<String> = sp(ctx).getStringSet(key, emptySet()).orEmpty()
    fun putStringSet(ctx: Context, key: String, v: Set<String>) = sp(ctx).edit { putStringSet(key, v) }
    fun contains(ctx: Context, key: String) = sp(ctx).contains(key)

    fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}

/**
 * App session counter: one session per fresh launch of [com.phone.cleanerapp.storage.mediafiles.MainActivity]
 * (not rotation or process restore). Drives the "ask for overlay on sessions 2, 5, 8" logic.
 */
object AppSession {
    private const val KEY = "key_session_count"

    fun start(ctx: Context) {
        val session = count(ctx) + 1
        ScreenPrefs.putInt(ctx, KEY, session)
        Log.d("AppSession", "Session $session started")
    }

    /** Sessions started so far; 0 before the first launch. */
    fun count(ctx: Context): Int = ScreenPrefs.getInt(ctx, KEY, 0)
}

/**
 * True while one of the app's normal screens is on screen. The charging and after-call screens
 * don't count: they are opened by the service, and the "app is open" rule is about the user
 * already being inside the app.
 */
object AppForeground {
    @Volatile var isOpen = false
        private set
    private var started = 0

    fun register(app: Application, ignore: Set<Class<out Activity>>) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity.javaClass in ignore) return
                started++
                isOpen = true
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity.javaClass in ignore) return
                started = (started - 1).coerceAtLeast(0)
                isOpen = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/** Native ad sheet and the charging interstitial for the two service-opened screens. */
object ScreenAds {
    private const val TAG = "ScreenAds"

    /** Google's public test units. Replace with your own AdMob unit IDs before release. */
    private const val NATIVE_TEST = "ca-app-pub-3940256099942544/2247696110"
    private const val INTER_TEST = "ca-app-pub-3940256099942544/1033173712"

    private var inter: InterstitialAd? = null

    /**
     * Loads a native ad into [holder]. [onResult] gets true when an ad is shown, false on failure
     * or when the unit is switched off, so the caller can hide its sheet.
     */
    fun loadNative(activity: Activity, unit: String, holder: ViewGroup, onResult: (Boolean) -> Unit) {
        if (!ScreenConfig.adEnabled(activity, unit)) return onResult(false)
        MobileAds.initialize(activity) { }
        val loader = AdLoader.Builder(activity, NATIVE_TEST)
            .forNativeAd { ad ->
                if (activity.isDestroyed || activity.isFinishing) {
                    ad.destroy()
                    return@forNativeAd
                }
                holder.removeAllViews()
                holder.addView(populate(activity, holder, ad))
                onResult(true)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native $unit failed: ${error.message}")
                    onResult(false)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    private fun populate(ctx: Context, parent: ViewGroup, ad: NativeAd): NativeAdView {
        val view = LayoutInflater.from(ctx).inflate(R.layout.layout_native_ad, parent, false) as NativeAdView
        val headline = view.findViewById<TextView>(R.id.adHeadline)
        val body = view.findViewById<TextView>(R.id.adBody)
        val cta = view.findViewById<TextView>(R.id.adCta)
        val icon = view.findViewById<ImageView>(R.id.adIcon)
        val media = view.findViewById<MediaView>(R.id.adMedia)

        headline.text = ad.headline
        view.headlineView = headline
        body.text = ad.body
        body.visibility = if (ad.body == null) android.view.View.GONE else android.view.View.VISIBLE
        view.bodyView = body
        cta.text = ad.callToAction
        cta.visibility = if (ad.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE
        view.callToActionView = cta
        val drawable = ad.icon?.drawable
        if (drawable != null) icon.setImageDrawable(drawable) else icon.visibility = android.view.View.GONE
        view.iconView = icon
        ad.mediaContent?.let { media.mediaContent = it }
        view.mediaView = media
        view.setNativeAd(ad)
        return view
    }

    /** Preloads the interstitial shown when the charging screen is closed. */
    fun preloadInter(activity: Activity) {
        if (inter != null || !ScreenConfig.adEnabled(activity, ScreenConfig.AD_INTER_CHARGING)) return
        MobileAds.initialize(activity) { }
        InterstitialAd.load(activity, INTER_TEST, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                inter = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Interstitial failed: ${error.message}")
                inter = null
            }
        })
    }

    /** Shows the interstitial if one is ready, then runs [done]; runs it at once otherwise. */
    fun showInter(activity: Activity, done: () -> Unit) {
        val ad = inter
        if (ad == null || activity.isFinishing || activity.isDestroyed) return done()
        inter = null
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                done()
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = finish()
        }
        ad.show(activity)
    }
}
