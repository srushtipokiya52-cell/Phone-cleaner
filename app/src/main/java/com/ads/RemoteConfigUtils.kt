package com.ads

import android.content.Context
import com.app.AppConstants
import com.app.AppConstants.DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON
import com.app.GlobalApp
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.itg.template.ads.AdUnitConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber
import kotlin.collections.set

object RemoteConfigUtils {
    private const val TAG = "RemoteConfig"

    /** Debug builds: false = live Firebase ad JSON with test ad IDs, true = live JSON with the real IDs. */
    private const val USE_LIVE_ADS_IN_DEBUG = false
    var completed = false
    private const val ON_SHOW_NAVIGATION_BUTTON = "on_show_navigation_button"
    private const val ON_SHOW_DIALOG_CONSENT = "on_show_dialog_consent"
    private const val DELAY_SHOW_LANGUAGE_DONE_BUTTON = "delay_button_done_language"
    private const val TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON = "time_delay_show_language_done_button"
    // Must match the parameter name in the Firebase console (whole ad JSON in one key)
    private const val AD_REMOTE_CONFIG = "ad_remote_config"


    private val mapConditionForAd: HashMap<String, Any> = hashMapOf(
        ON_SHOW_DIALOG_CONSENT to true,
        ON_SHOW_NAVIGATION_BUTTON to false,
        DELAY_SHOW_LANGUAGE_DONE_BUTTON to true,
        TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON to DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON,
    )

    //Default layout
    private const val AD_REMOTE_CONFIG_FILE_DEBUG = "ad_config_debug.json"
    private const val AD_REMOTE_CONFIG_FILE_RELEASE = "ad_config.json"

    fun getOnShowNavigationButton(): Boolean = getBoolean(ON_SHOW_NAVIGATION_BUTTON)
    fun getOnShowDialogConsent(): Boolean = getBoolean(ON_SHOW_DIALOG_CONSENT)
    /**
     * The last live ad JSON (ad_remote_config) saved after a fetch, or null. GlobalApp builds
     * AdRemoteConfig from it at process start, so screens opened by the service (charging,
     * after-call) use the live ad IDs instead of the bundled file before Remote Config is fetched.
     */
    fun getCachedAdConfig(context: Context): String? {
        val cached = cache(context).getString(AD_REMOTE_CONFIG, null) ?: return null
        return if (BuildConfig.DEBUG && !USE_LIVE_ADS_IN_DEBUG) withTestIds(cached) else cached
    }

    /**
     * Debug builds: [json] (the live ad JSON) with every unit's `id` replaced by Google's test ID
     * of the same type (native / inter / banner / reward / open), chosen from the key's first word.
     * Real ad units are never requested while testing (clicking your own live ads can get the
     * AdMob account limited). Null if the JSON can't be read.
     */
    private fun withTestIds(json: String): String? = try {
        val root = org.json.JSONObject(json)
        val keys = root.keys().asSequence().toList()
        for (key in keys) {
            val unit = root.optJSONObject(key) ?: continue
            val testId = when (key.substringBefore('_')) {
                "native" -> "ca-app-pub-3940256099942544/2247696110"
                "inter" -> "ca-app-pub-3940256099942544/1033173712"
                "banner" -> "ca-app-pub-3940256099942544/9214589741"
                "reward" -> "ca-app-pub-3940256099942544/5224354917"
                "open" -> "ca-app-pub-3940256099942544/9257395921"
                else -> null
            }
            if (testId != null) unit.put("id", testId)
        }
        root.toString()
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Could not apply test ad IDs to the live ad JSON")
        null
    }

    private fun loadAsset(fileName: String): String = try {
        GlobalApp.instance.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (ex: Exception) {
        "{}"
    }

    private const val CACHE_PREFS = "remote_config_cache"

    private fun cache(context: Context = GlobalApp.instance) =
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    /** Saves the values the background service needs; only live (REMOTE) values are cached. */
    private fun saveCache(context: Context) {
        val adValue = remoteConfig.getValue(AD_REMOTE_CONFIG)
        if (adValue.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE && adValue.asString().isNotBlank()) {
            cache(context).edit().putString(AD_REMOTE_CONFIG, adValue.asString()).apply()
        }
        Timber.tag(TAG).d("Ad config cache: %s", if (adValue.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) "live" else "not live (kept previous)")
    }

    interface Listener {
        fun loadSuccess()
    }

    lateinit var listener: Listener
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adUnitConfigAdapter = moshi.adapter(AdUnitConfig::class.java)

    fun init(context: Context, mListener: Listener) {
        listener = mListener
        try {
            remoteConfig = getFirebaseRemoteConfig(context)
        } catch (e: Exception) {
            // No google-services.json / Firebase not initialised: keep the bundled ad_config(.debug).json
            Timber.tag(TAG).w(e, "Remote Config unavailable, using bundled ad config")
            completed = false
            listener.loadSuccess()
        }
    }

    private fun getDefaultsFromAdConfig(context: Context): Map<String, Any> {
        val defaults = mutableMapOf<String, Any>()
        try {
            if (!AdRemoteConfig.isInitialized()) {
                AdRemoteConfig.initializeFromAssets(context)
            }
            val adConfig = AdRemoteConfig.getInstance()
            adConfig.ads.forEach { (key, adUnitConfig) ->
                val jsonString = adUnitConfigAdapter.toJson(adUnitConfig)
                defaults[key] = jsonString
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return defaults
    }

    private fun getFirebaseRemoteConfig(context: Context): FirebaseRemoteConfig {
        remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                0
            } else {
                60 * 60
            }
            // First fetch of a fresh install also registers with Firebase installations: 3s is often too short
            fetchTimeoutInSeconds = 10L
        }
        val defaults = getDefaultsFromAdConfig(context).toMutableMap()
        mapConditionForAd.forEach { (key, value) ->
            // In-app default only: the live value is logged as "[REMOTE]" after the fetch
            Timber.tag(TAG).d("%s [IN-APP DEFAULT, before fetch] -> %s", key, value)
            defaults[key] = value
        }
        // In-app default for the whole ad JSON key: the bundled ad_config(.debug).json
        defaults[AD_REMOTE_CONFIG] = loadDefaultAdRemoteConfig()
        val appContext = context.applicationContext
        // Chain the async setup so fetch runs with our fetch interval (not the 12h SDK default)
        remoteConfig.setConfigSettingsAsync(configSettings)
            .continueWithTask { remoteConfig.setDefaultsAsync(defaults) }
            .continueWithTask {
                // Values activated by an earlier session are readable right now: use them while the
                // new fetch is running so a slow/failed fetch never drops back to the bundled file
                if (hasFetchedBefore()) {
                    completed = true
                    AdRemoteConfig.initialize(appContext, getAdRemoteConfig())
                }
                remoteConfig.fetchAndActivate()
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Timber.tag(TAG).d("fetchAndActivate success, newValuesActivated=%s", task.result)
                } else {
                    Timber.tag(TAG).w(task.exception, "fetchAndActivate failed, using last activated values")
                }
                // completed must be true BEFORE the listener: getAdRemoteConfig() returns the
                // bundled default while completed is false
                completed = true
                logConditionValues()
                saveCache(appContext)
                AdRemoteConfig.initialize(appContext, getAdRemoteConfig())
                listener.loadSuccess()
            }
        return remoteConfig
    }

    private fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getBoolean(key)
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }


    /** Logcat (tag RemoteConfig): every non-ad flag with where its value came from. */
    private fun logConditionValues() {
        mapConditionForAd.keys.sorted().forEach { key ->
            val value = remoteConfig.getValue(key)
            val source = when (value.source) {
                FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "REMOTE"
                FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "DEFAULT"
                else -> "STATIC"
            }
            Timber.tag(TAG).d("%s [%s] -> %s", key, source, value.asString())
        }
    }

    private fun getString(key: String, defaultValue: String = ""): String {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getString(key)
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }

    private fun getLong(key: String, defaultValue: Long = 0): Long {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getLong(key)
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }


    /** False on a fresh install (or cleared data): nothing live has ever been activated yet. */
    fun hasFetchedBefore(): Boolean = try {
        ::remoteConfig.isInitialized && remoteConfig.info.fetchTimeMillis > 0
    } catch (e: Exception) {
        false
    }

    /**
     * Debug builds use the live Firebase ad JSON with Google test ad IDs swapped in ([withTestIds]);
     * until a live value exists they use the bundled assets/ad_config_debug.json. Release uses the
     * live value, else the cache, else assets/ad_config.json. Flip [USE_LIVE_ADS_IN_DEBUG] to true
     * to also use the REAL ad IDs in a debug build (risky: don't click your own ads).
     */
    fun getAdRemoteConfig(): String {
        val defaultValue = loadDefaultAdRemoteConfig()
        if (!completed) {
            Timber.tag(TAG).d("%s [NOT_FETCHED] -> bundled default", AD_REMOTE_CONFIG)
            return defaultValue
        } else {
            val value = remoteConfig.getValue(AD_REMOTE_CONFIG)
            val configValue = value.asString()
            val source = when (value.source) {
                FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "REMOTE"
                FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "DEFAULT"
                else -> "STATIC"
            }
            Timber.tag(TAG).d("%s [%s] -> %s", AD_REMOTE_CONFIG, source, configValue.ifBlank { "bundled default" })
            val json = configValue.ifBlank { defaultValue }
            // Debug builds never request real ad units
            return if (BuildConfig.DEBUG && !USE_LIVE_ADS_IN_DEBUG) withTestIds(json) ?: defaultValue else json
        }
    }


    private fun loadDefaultAdRemoteConfig(): String {
        val fileName = if (BuildConfig.DEBUG) {
            AD_REMOTE_CONFIG_FILE_DEBUG
        } else {
            AD_REMOTE_CONFIG_FILE_RELEASE
        }
        return try {
            GlobalApp.instance.assets.open(fileName).bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (ex: Exception) {
            "{}"
        }
    }


    fun shouldDelayLanguageDoneButton(): Boolean = getBoolean(DELAY_SHOW_LANGUAGE_DONE_BUTTON, true)
    fun getTimeDelayButtonDoneLanguage(): Long = getLong(
        TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON,
        AppConstants.DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON
    )
}
