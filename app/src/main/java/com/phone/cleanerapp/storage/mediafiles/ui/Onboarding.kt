package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
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

class SplashFragment : VBFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {
    private var animator: ValueAnimator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.version.text = trf("Version %1\$s", BuildConfig.VERSION_NAME)
        animator = ValueAnimator.ofInt(0, 100).apply {
            duration = 1600
            addUpdateListener { bOrNull?.progress?.setProgressCompat(it.animatedValue as Int, false) }
            start()
        }
        b.root.onClick { proceed() }
        ui {
            delay(1700)
            proceed()
        }
    }

    private var done = false
    private fun proceed() {
        if (done || !isAdded) return
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
        b.btnContinue.onClick {
            prefs.language = selected
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
            if (prefs.onboarded) back() else go(R.id.introFragment)
        }
    }
}

class IntroFragment : VBFragment<FragmentIntroBinding>(FragmentIntroBinding::inflate) {
    private var page = 0

    private val titles = listOf(tr("Free up space in one tap"), tr("Clear duplicate & similar photos"), tr("Faster, cooler, longer battery"))
    private val bodies = listOf(
        tr("Find junk, app cache, leftover files and empty folders that slow your phone down — and clear them safely."),
        tr("We group look-alike shots, blurry pictures and old screenshots, and pick the best one to keep. You review before anything is deleted."),
        tr("Stop heavy background apps, cool the CPU and keep an eye on battery health — all from one place."),
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.skip.onClick { go(R.id.permissionFragment) }
        b.btnNext.onClick { page++; render() }
        b.btnStart.onClick { go(R.id.permissionFragment) }
        render()
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
