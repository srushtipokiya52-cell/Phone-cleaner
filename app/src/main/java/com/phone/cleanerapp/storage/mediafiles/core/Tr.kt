package com.phone.cleanerapp.storage.mediafiles.core

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Looks UI text up by its English wording. The wording is turned into a resource name
 * (see tools/gen_strings.pl) so the same text can be translated in res/values-xx/strings.xml.
 * Text without a translation falls back to the English you passed in.
 */
object Tr {
    @Volatile private var ctx: Context? = null
    private val ids = HashMap<String, Int>()

    /** Call on app start and whenever an activity starts, so the chosen app language is picked up. */
    @Synchronized
    fun refresh(app: Context) {
        val base = app.applicationContext
        val cfg = Configuration(base.resources.configuration)
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) cfg.setLocales(locales.unwrap() as android.os.LocaleList)
        ctx = base.createConfigurationContext(cfg)
        ids.clear()
    }

    fun slug(en: String): String =
        en.replace("…", " ellipsis ").replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(64)

    @Synchronized
    fun tr(en: String): String {
        val c = ctx ?: return en
        val id = ids.getOrPut(en) { c.resources.getIdentifier("tr_" + slug(en), "string", c.packageName) }
        return if (id != 0) c.getString(id) else en
    }

    fun trf(en: String, vararg args: Any?): String {
        val locale: Locale = ctx?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
        return try {
            String.format(locale, tr(en), *args)
        } catch (_: Throwable) {
            String.format(Locale.US, en, *args)
        }
    }
}

fun tr(en: String): String = Tr.tr(en)

fun trf(en: String, vararg args: Any?): String = Tr.trf(en, *args)
