package com.ads

import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

object ITGTrackingHelper {
    const val CONSENT_ERROR_1: String = "consent_error_1"
    const val CONSENT_ERROR_2: String = "consent_error_2"
    const val LOAD_CONSENT_1 = "new_load_consent_1"
    const val DISPLAY_CONSENT_1 = "new_display_consent_1"
    const val NOT_REQUIRE_DISPLAY_CONSENT_1 = "new_not_require_consent_1"
    const val NOT_USING_DISPLAY_CONSENT_1 = "new_not_using_display_consent_1"
    const val AGREE_CONSENT_1 = "new_agree_consent_1"
    const val REFUSE_CONSENT_1 = "new_refuse_consent_1"

    const val LOAD_CONSENT_2 = "load_consent_2"
    const val DISPLAY_CONSENT_2 = "display_consent_2"
    const val NOT_USING_DISPLAY_CONSENT_2 = "not_using_display_consent_2"
    const val AGREE_CONSENT_2 = "agree_consent_2"
    const val REFUSE_CONSENT_2 = "refuse_consent_2"

    const val LOAD_CONSENT_3 = "load_consent_3"
    const val DISPLAY_CONSENT_3 = "display_consent_3"
    const val AGREE_CONSENT_3 = "agree_consent_3"
    const val REFUSE_CONSENT_3 = "refuse_consent_3"

    fun userProperty(name: String, value: String): ITGTrackingHelper {
        runCatching { Firebase.analytics.setUserProperty(name, value) }
        return this
    }

    fun logEvent(eventName: String, params: Bundle?): ITGTrackingHelper {
        runCatching { Firebase.analytics.logEvent(eventName, params) }
        return this
    }

    fun logEventClick(activityName: String, eventName: String, params: Bundle?): ITGTrackingHelper {
        Log.d("ITGTrackingHelper", "logEvent: $activityName click $eventName")
        runCatching { Firebase.analytics.logEvent("${activityName}_click_$eventName", params) }
        return this
    }

    fun addScreenTrack(screenName: String): ITGTrackingHelper {
        runCatching { Firebase.analytics.logEvent(screenName, null) }
        return this
    }

    fun fromScreenToScreen(fromScreen: String, toScreen: String): ITGTrackingHelper {
        Log.d("ITGTrackingHelper", "fromScreenToScreen: $fromScreen to $toScreen")
        runCatching { Firebase.analytics.logEvent("${fromScreen}_${toScreen}", null) }
        return this
    }

    object Params {}
}
