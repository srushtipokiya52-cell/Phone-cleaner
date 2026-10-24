package com.itg.template.data.pref

import android.content.Context
import android.content.SharedPreferences

class AppSharedPref(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_ads_pref", Context.MODE_PRIVATE)

    var isConfirmConsent: Boolean
        get() = prefs.getBoolean("is_confirm_consent", false)
        set(value) = prefs.edit().putBoolean("is_confirm_consent", value).apply()

    var isUserGlobal: Boolean
        get() = prefs.getBoolean("is_user_global", false)
        set(value) = prefs.edit().putBoolean("is_user_global", value).apply()

    var firstOnBoarding: Boolean
        get() = prefs.getBoolean("first_onboarding", true)
        set(value) = prefs.edit().putBoolean("first_onboarding", value).apply()

    /** Language picked on the language screen ("" until chosen). */
    var languageCode: String
        get() = prefs.getString("language_code", "") ?: ""
        set(value) = prefs.edit().putString("language_code", value).apply()
}
