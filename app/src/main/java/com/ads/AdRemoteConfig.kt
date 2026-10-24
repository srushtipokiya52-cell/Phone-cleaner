package com.ads

import android.content.Context
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.itg.template.ads.AdUnitConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber
import java.io.InputStream

data class AdRemoteConfig(
    val ads: Map<String, AdUnitConfig> = emptyMap()
) {
    companion object {
        private const val RELEASE_FILE_NAME = "ad_config.json"
        private const val DEBUG_FILE_NAME = "ad_config_debug.json"

        @Volatile
        private var instance: AdRemoteConfig? = null

        private val moshi: Moshi = Moshi.Builder()
            .add(AdRemoteConfigJsonAdapterFactory())
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val adapter = moshi.adapter(AdRemoteConfig::class.java)

        fun getInstance(): AdRemoteConfig {
            return instance ?: synchronized(this) {
                instance ?: throw IllegalStateException("AdRemoteConfig not initialized. Call initialize() first.")
            }
        }

        fun initialize(context: Context, json: String? = null) {
            synchronized(this) {
                // Live JSON wins in every build; the bundled file is only the fallback
                val fileName = if (BuildConfig.DEBUG) DEBUG_FILE_NAME else RELEASE_FILE_NAME
                instance = fromJsonOrAssets(context, json, fileName)
                Timber.tag("RemoteConfig").d("AdRemoteConfig initialized: %s", instance?.ads)
            }
        }

        fun initializeFromAssets(context: Context) {
            synchronized(this) {
                val fileName = if (BuildConfig.DEBUG) DEBUG_FILE_NAME else RELEASE_FILE_NAME
                instance = fromAssets(context, fileName)
            }
        }

        fun initializeFromJson(json: String) {
            synchronized(this) {
                instance = fromJson(json)
            }
        }

        fun updateInstance(newConfig: AdRemoteConfig) {
            synchronized(this) {
                instance = newConfig
            }
        }

        /** Parses a single ad unit JSON (e.g. one Remote Config value) with the same lenient rules as the asset file. */
        fun parseAdUnitConfig(json: String): AdUnitConfig? {
            return try {
                adapter.fromJson("{\"unit\":$json}")?.ads?.get("unit")
            } catch (e: Exception) {
                Timber.w(e, "Invalid ad unit JSON: %s", json)
                null
            }
        }

        /** Parses a whole ad config JSON (the `ad_config` Remote Config value), or null if invalid. */
        fun parseOrNull(json: String): AdRemoteConfig? {
            return try {
                fromJson(json)
            } catch (e: Exception) {
                Timber.w(e, "Invalid ad config JSON")
                null
            }
        }

        fun isInitialized(): Boolean {
            return instance != null
        }

        fun reset() {
            synchronized(this) {
                instance = null
            }
        }

        private fun fromJson(json: String): AdRemoteConfig {
            return adapter.fromJson(json) ?: throw IllegalArgumentException("Invalid JSON")
        }

        private fun fromInputStream(inputStream: InputStream): AdRemoteConfig {
            return inputStream.bufferedReader().use { reader ->
                adapter.fromJson(reader.readText()) ?: throw IllegalArgumentException("Invalid JSON")
            }
        }

        private fun fromAssets(context: Context, fileName: String = RELEASE_FILE_NAME): AdRemoteConfig {
            return context.assets.open(fileName).use { inputStream ->
                fromInputStream(inputStream)
            }
        }

        fun fromRawResource(context: Context, resId: Int): AdRemoteConfig {
            return context.resources.openRawResource(resId).use { inputStream ->
                fromInputStream(inputStream)
            }
        }

        private fun fromJsonOrAssets(context: Context, json: String?, fileName: String = RELEASE_FILE_NAME): AdRemoteConfig {
            if (!json.isNullOrBlank()) {
                try {
                    return fromJson(json)
                } catch (e: Exception) {
                    Timber.tag("RemoteConfig").w(e, "Live ad config JSON invalid, using %s", fileName)
                }
            }
            return fromAssets(context, fileName)
        }

        fun fromInputStreamOrAssets(context: Context, inputStream: InputStream?, fileName: String = RELEASE_FILE_NAME): AdRemoteConfig {
            if (BuildConfig.DEBUG) {
                return fromAssets(context, DEBUG_FILE_NAME)
            }
            if (inputStream != null) {
                try {
                    return fromInputStream(inputStream)
                } catch (e: Exception) {
                    // Fallback to assets if InputStream parsing fails
                }
            }
            return fromAssets(context, fileName)
        }
    }

    private fun getAdUnit(key: String): AdUnitConfig {
        val adUnitConfig = ads[key]
        if (adUnitConfig == null) {
            Timber.w("Ad unit '%s' not found in configuration.", key)
            return AdUnitConfig(id = "", isEnable = false)
        }
        return adUnitConfig
    }

    val inter_splash: AdUnitConfig
        get() = getAdUnit("inter_splash")

    val inter_splash_uninstall: AdUnitConfig
        get() = getAdUnit("inter_splash_uninstall")

    val banner_splash: AdUnitConfig
        get() = getAdUnit("banner_splash")

    val banner_splash_uninstall: AdUnitConfig
        get() = getAdUnit("banner_splash_uninstall")

    val open_resume: AdUnitConfig
        get() = getAdUnit("open_resume")

    val native_language_1: AdUnitConfig
        get() = getAdUnit("native_language_1")

    val native_language_1_click: AdUnitConfig
        get() = getAdUnit("native_language_1_click")

    val native_language_2: AdUnitConfig
        get() = getAdUnit("native_language_2")

    val native_language_2_click: AdUnitConfig
        get() = getAdUnit("native_language_2_click")

    val native_onboarding_1_1: AdUnitConfig
        get() = getAdUnit("native_onboarding_1_1")

    val native_onboarding_2_1: AdUnitConfig
        get() = getAdUnit("native_onboarding_2_1")

    val native_onboarding_1_3: AdUnitConfig
        get() = getAdUnit("native_onboarding_1_3")

    val native_onboarding_2_3: AdUnitConfig
        get() = getAdUnit("native_onboarding_2_3")

    val native_onboarding_1_4: AdUnitConfig
        get() = getAdUnit("native_onboarding_1_4")

    val native_onboarding_2_4: AdUnitConfig
        get() = getAdUnit("native_onboarding_2_4")

    val native_onboarding_fullscreen_1_3: AdUnitConfig
        get() = getAdUnit("native_onboarding_fullscreen_1_3")

    val native_onboarding_fullscreen_2_3: AdUnitConfig
        get() = getAdUnit("native_onboarding_fullscreen_2_3")

    val native_onboarding_fullscreen_1_4: AdUnitConfig
        get() = getAdUnit("native_onboarding_fullscreen_1_4")

    val native_onboarding_fullscreen_2_4: AdUnitConfig
        get() = getAdUnit("native_onboarding_fullscreen_2_4")

    val native_permission: AdUnitConfig
        get() = getAdUnit("native_permission")

    val native_home: AdUnitConfig
        get() = getAdUnit("native_home")

    val native_tools: AdUnitConfig
        get() = getAdUnit("native_tools")

    val native_settings: AdUnitConfig
        get() = getAdUnit("native_settings")

    val native_charging_medium: AdUnitConfig
        get() = getAdUnit("native_charging_medium")

    val inter_charging: AdUnitConfig
        get() = getAdUnit("inter_charging")

    val native_jap_reminder: AdUnitConfig
        get() = getAdUnit("native_jap_reminder")

    /** Small native styled as a banner at the bottom of the Japa counter; reloadIntervalSeconds = refresh. */
    val native_japa: AdUnitConfig
        get() = getAdUnit("native_japa")

    /** Native banner at the bottom of the Wallpaper tab; reloadIntervalSeconds = refresh. */
    val native_wallpaper: AdUnitConfig
        get() = getAdUnit("native_wallpaper")

    /** Native banner at the bottom of the wallpaper preview screen; reloadIntervalSeconds = refresh. */
    val native_wallpaper_preview: AdUnitConfig
        get() = getAdUnit("native_wallpaper_preview")

    /** Interstitial shown when the after-call screen is closed (preloaded when it opens). */
    val inter_call_end: AdUnitConfig
        get() = getAdUnit("inter_call_end")

    /** The 304px sheet on the after-call screen (21a). */
    val native_call_end: AdUnitConfig
        get() = getAdUnit("native_call_end")

    /** The small native pinned to the bottom of every screen that has no native of its own. */
    val native_full: AdUnitConfig
        get() = getAdUnit("native_full")

    val inter_onboarding: AdUnitConfig
        get() = getAdUnit("inter_onboarding")

    val banner_home: AdUnitConfig
        get() = getAdUnit("banner_home")

    val native_survey: AdUnitConfig
        get() = getAdUnit("native_survey")

    val native_confirm_uninstall: AdUnitConfig
        get() = getAdUnit("native_confirm_uninstall")

    val native_welcome: AdUnitConfig
        get() = getAdUnit("native_welcome")

    val inter_welcome: AdUnitConfig
        get() = getAdUnit("inter_welcome")

    val reward_example: AdUnitConfig
        get() = getAdUnit("reward_example")

    /** Rewarded ad before saving / setting a wallpaper (23c / 23d). */
    val reward_wallpaper: AdUnitConfig
        get() = getAdUnit("reward_wallpaper")

    val inter_home: AdUnitConfig
        get() = getAdUnit("inter_home")
}
