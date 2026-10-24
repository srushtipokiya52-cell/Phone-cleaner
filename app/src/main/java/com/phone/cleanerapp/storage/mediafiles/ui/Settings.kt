package com.phone.cleanerapp.storage.mediafiles.ui

import com.ads.AdRemoteConfig
import com.ads.AdsManager
import com.ads.module.billing.AppPurchase
import com.ads.native_settings
import com.ads.populateNativeAdView
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.dp
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.data.Fs
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentSettingsBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemSettingBinding
import com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker

class SettingsFragment : VBFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    // The sections are rebuilt on every resume, so the ad slot is created once and re-inserted each time
    private var adSlot: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSettingsAd()
        onEachResume { build() }
    }

    /** Medium native under the Cleaning card. Shimmer first; the slot is dropped when there is no ad. */
    private fun setupSettingsAd() {
        val activity = requireActivity()
        if (!AdRemoteConfig.native_settings.isEnable || AppPurchase.getInstance().isPurchased(activity)) return
        val slot = layoutInflater.inflate(R.layout.view_native_card_slot, b.container, false)
        val shimmer = slot.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_native_medium)
        val holder = slot.findViewById<android.widget.FrameLayout>(R.id.adHolder)
        slot.layoutParams = (slot.layoutParams as android.widget.LinearLayout.LayoutParams).apply { topMargin = requireContext().dp(20) }
        adSlot = slot
        shimmer.startShimmer()
        AdsManager.loadNativeSettings(activity, R.layout.layout_native_ad_medium).observe(viewLifecycleOwner) { ad ->
            if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
                populateNativeAdView(activity, ad, holder, shimmer)
            } else {
                shimmer.stopShimmer()
                (slot.parent as? android.view.ViewGroup)?.removeView(slot)
                adSlot = null
            }
        }
        build()
    }

    private fun placeAd() {
        val slot = adSlot ?: return
        (slot.parent as? android.view.ViewGroup)?.removeView(slot)
        b.container.addView(slot)
    }

    private fun build() {
        val bind = bOrNull ?: return
        // Keep the title (first child); rebuild the sections below them.
        while (bind.container.childCount > 1) bind.container.removeViewAt(1)
        val ctx = requireContext()
        val p = Prefs.get(ctx)
        val lang = LANGUAGES.firstOrNull { it.tag == p.language }?.name ?: "English"

        section(tr("Cleaning")) {
            toggle(R.drawable.ic_clean, Tone.GREEN, tr("Auto-clean"), if (p.autoClean) tr("On · weekly") else tr("Off"), p.autoClean) { on ->
                p.autoClean = on
                MaintenanceWorker.schedule(ctx, on || p.junkReminder)
                build()
            }
            toggle(R.drawable.ic_bell, Tone.AMBER, tr("Junk reminders"), tr("Notify when junk is over 1 GB"), p.junkReminder) { on ->
                p.junkReminder = on
                MaintenanceWorker.schedule(ctx, on || p.autoClean)
            }
            item(R.drawable.ic_file, Tone.CYAN, tr("Large file size"), trf("Over %1\$s MB", p.largeFileMb), tr("Change")) { pickSize() }
            item(R.drawable.ic_folder, Tone.VIOLET, tr("Ignore list"), trf("%1\$s folders", p.ignoredFolders.size), tr("Edit")) { editIgnored() }
            item(R.drawable.ic_trash, Tone.GRAY, tr("Recycle Bin"), tr("Keep 30 days"), "") {
                toast(tr("Deleted photos stay in your phone's Recycle Bin for 30 days"))
            }
        }
        placeAd()
        section(tr("General")) {
            item(R.drawable.ic_globe, Tone.CYAN, tr("Language"), lang, tr("Change")) { go(R.id.languageFragment) }
            item(R.drawable.ic_shield, Tone.GREEN, tr("Permissions"), tr("Review what the app can access"), tr("Manage")) { go(R.id.permissionFragment) }
        }
        section(tr("About")) {
            item(R.drawable.ic_star, Tone.AMBER, tr("Rate us"), "", "") { rate() }
            item(R.drawable.ic_share, Tone.PINK, tr("Share app"), "", "") { share() }
            item(R.drawable.ic_lock, Tone.GRAY, tr("Privacy policy"), "", "") {
                if (PRIVACY_URL.isBlank()) toast(tr("Privacy policy link is not set")) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            }
            item(R.drawable.ic_file, Tone.GRAY, tr("Version"), "v${BuildConfig.VERSION_NAME}", "") { }
        }
    }

    private fun section(title: String, rows: SectionBuilder.() -> Unit) {
        val ctx = requireContext()
        b.container.addView(TextView(ctx).apply {
            text = title
            setTextAppearance(R.style.Txt_Section)
            setPadding(0, ctx.dp(24), 0, ctx.dp(10))
        })
        val card = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_lg)
            setPadding(ctx.dp(16), ctx.dp(6), ctx.dp(16), ctx.dp(6))
        }
        SectionBuilder(card).rows()
        b.container.addView(card)
    }

    private inner class SectionBuilder(val card: android.widget.LinearLayout) {
        fun item(icon: Int, tone: Tone, title: String, sub: String, trail: String, click: () -> Unit) {
            val r = ItemSettingBinding.inflate(layoutInflater, card, false)
            r.icon.tile(icon, tone)
            r.title.text = title
            r.sub.text = sub
            r.sub.show(sub.isNotEmpty())
            r.trail.text = trail
            r.row.onClick(click)
            card.addView(r.root)
        }

        fun toggle(icon: Int, tone: Tone, title: String, sub: String, on: Boolean, change: (Boolean) -> Unit) {
            val r = ItemSettingBinding.inflate(layoutInflater, card, false)
            r.icon.tile(icon, tone)
            r.title.text = title
            r.sub.text = sub
            r.trail.show(false)
            r.toggle.show(true)
            r.toggle.isChecked = on
            r.toggle.setOnCheckedChangeListener { _, v -> change(v) }
            r.row.onClick { r.toggle.toggle() }
            card.addView(r.root)
        }
    }

    private fun pickSize() {
        val opts = listOf(50, 100, 250, 500)
        val p = Prefs.get(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(tr("Large file size"))
            .setSingleChoiceItems(opts.map { trf("Over %1\$s MB", it) }.toTypedArray(), opts.indexOf(p.largeFileMb)) { d, i ->
                p.largeFileMb = opts[i]; d.dismiss(); build()
            }
            .show()
    }

    private fun editIgnored() {
        val p = Prefs.get(requireContext())
        val tops = (Fs.root().listFiles()?.filter { it.isDirectory && it.name != "Android" } ?: emptyList()).sortedBy { it.name }
        val checked = BooleanArray(tops.size) { tops[it].path in p.ignoredFolders }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(tr("Skip these folders when scanning"))
            .setMultiChoiceItems(tops.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Save")) { _, _ ->
                p.ignoredFolders = tops.filterIndexed { i, _ -> checked[i] }.map { it.path }.toSet(); build()
            }
            .show()
    }

    private fun rate() {
        val pkg = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Throwable) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun share() {
        val pkg = requireContext().packageName
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Try Phone Cleaner: https://play.google.com/store/apps/details?id=$pkg"),
                tr("Share app"),
            )
        )
    }

    companion object {
        /** Put your hosted privacy policy URL here before release. */
        const val PRIVACY_URL = ""
    }
}
