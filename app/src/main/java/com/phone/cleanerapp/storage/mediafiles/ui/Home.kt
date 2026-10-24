package com.phone.cleanerapp.storage.mediafiles.ui

import com.ads.AdRemoteConfig
import com.ads.AdsManager
import com.ads.module.billing.AppPurchase
import com.ads.native_home
import com.ads.populateNativeAdView
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.data.AppState
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentHomeBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewLegendBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewStatCardBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewToolCardBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewToolMiniBinding
import com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker
import java.util.Calendar

class HomeFragment : VBFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    /** Medium native under the Clean Junk card. Shimmer first; the slot is removed when there is no ad. */
    private fun setupHomeAd() {
        val activity = requireActivity()
        val slot = b.layoutAdHome
        val cfg = AdRemoteConfig.native_home
        if (!cfg.isEnable || AppPurchase.getInstance().isPurchased(activity)) { slot.visibility = View.GONE; return }
        val shimmer = b.shimmerAds.shimmerNativeMedium
        shimmer.startShimmer()
        AdsManager.loadNativeHomeCard(activity, R.layout.layout_native_ad_medium).observe(viewLifecycleOwner) { ad ->
            if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
                populateNativeAdView(activity, ad, b.adHolder, shimmer)
            } else {
                shimmer.stopShimmer()
                slot.visibility = View.GONE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        b.greeting.text = when {
            h < 12 -> tr("Good morning")
            h < 18 -> tr("Good afternoon")
            else -> tr("Good evening")
        }
        b.btnAllTools.onClick { go(R.id.toolsFragment) }
        setupHomeAd()
        b.btnClean.onClick { if (Perms.hasFiles(ctx)) go(R.id.scanFragment) else go(R.id.permissionFragment) }

        legend(b.legendUsed, R.color.brand, tr("Used"))
        legend(b.legendJunk, R.color.amber, tr("Junk"))
        legend(b.legendFree, R.color.border, tr("Free"))

        stat(b.statRam, "RAM", R.color.brand) { go(R.id.boosterFragment) }
        stat(b.statCpu, "CPU", R.color.amber) { go(R.id.coolerFragment) }
        stat(b.statBattery, tr("Battery"), R.color.cyan) { go(R.id.batteryFragment) }
        b.statRam.action.text = tr("Boost"); b.statRam.action.setTextColor(ctx.getColor(R.color.brand))
        b.statCpu.action.text = tr("Cool down"); b.statCpu.action.setTextColor(ctx.getColor(R.color.amber_dark))
        b.statBattery.action.text = tr("Details"); b.statBattery.action.setTextColor(ctx.getColor(R.color.cyan))

        card(b.cardPhotos, R.drawable.ic_photo, Tone.CYAN, tr("Similar Photos")) { go(R.id.photosFragment) }
        card(b.cardLarge, R.drawable.ic_file, Tone.AMBER, tr("Large Files")) { go(R.id.fileListFragment) }
        card(b.cardChat, R.drawable.ic_chat, Tone.GREEN, tr("Chat Media")) { go(R.id.chatMediaFragment) }
        card(b.cardApps, R.drawable.ic_grid, Tone.VIOLET, tr("App Manager")) { go(R.id.appsFragment) }

        mini(b.miniCompress, R.drawable.ic_compress, Tone.PINK, tr("Compress")) { go(R.id.compressorFragment) }
        mini(b.miniContacts, R.drawable.ic_user, Tone.VIOLET, tr("Contacts")) { go(R.id.contactsFragment) }
        mini(b.miniNotif, R.drawable.ic_bell, Tone.AMBER, tr("Notifications")) { go(R.id.notificationsFragment) }
        mini(b.miniStorage, R.drawable.ic_pie, Tone.CYAN, tr("Storage")) { go(R.id.storageFragment) }

        b.btnAuto.onClick {
            val p = Prefs.get(ctx)
            if (p.autoClean) {
                go(R.id.settingsFragment)
            } else {
                p.autoClean = true
                MaintenanceWorker.schedule(ctx, true)
                renderAuto()
            }
        }
        onEachResume { refresh() }
    }

    private fun refresh() {
        val bind = bOrNull ?: return
        val ctx = requireContext()
        val st = DeviceInfo.storage()
        val junk = AppState.junkBytes.coerceAtMost(st.used)
        val used = st.used - junk
        val (n, unit) = Fmt.sizeParts(st.used)
        bind.usedBig.text = android.text.SpannableString("$n $unit").apply {
            setSpan(android.text.style.RelativeSizeSpan(0.5f), n.length, length, 0)
        }
        bind.ofTotal.text = trf("of %1\$s used", Fmt.size(st.total))
        bind.ring.setSegments(
            used.toFloat() / st.total to R.color.brand,
            junk.toFloat() / st.total to R.color.amber,
        )
        bind.legendUsed.value.text = Fmt.size(used)
        bind.legendJunk.value.text = if (AppState.junk == null) "—" else Fmt.size(junk)
        bind.legendFree.value.text = Fmt.size(st.free)

        val ram = DeviceInfo.ram(ctx)
        bind.statRam.value.text = "${ram.percent}%"
        bind.statRam.bar.setProgressCompat(ram.percent, false)
        val cpu = DeviceInfo.cpuTempC(ctx)
        bind.statCpu.value.text = "${cpu.toInt()}°C"
        bind.statCpu.bar.setProgressCompat(((cpu - 20) / 40f * 100).toInt().coerceIn(5, 100), false)
        val bat = DeviceInfo.battery(ctx)
        bind.statBattery.value.text = "${bat.level}%"
        bind.statBattery.bar.setProgressCompat(bat.level, false)

        val photos = AppState.photos
        bind.cardPhotos.sub.text = if (photos == null) tr("Tap to scan") else {
            val count = photos.sumOf { it.photos.size - 1 }
            trf("%1\$s photos · %2\$s", count, Fmt.size(photos.sumOf { it.reclaimable }))
        }
        val large = AppState.largeFiles
        bind.cardLarge.sub.text = if (large == null) trf("Over %1\$s MB", Prefs.get(ctx).largeFileMb) else trf("%1\$s files · %2\$s", large.size, Fmt.size(large.sumOf { it.size }))
        bind.cardChat.sub.text = AppState.chatTotal?.let { trf("Media · %1\$s", Fmt.size(it)) } ?: "WhatsApp, Telegram"
        val launcherApps = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet().size
        bind.cardApps.sub.text = AppState.unusedApps?.let { trf("%1\$s unused apps", it) } ?: trf("%1\$s apps", launcherApps)
        renderAuto()
    }

    private fun renderAuto() {
        val bind = bOrNull ?: return
        val on = Prefs.get(requireContext()).autoClean
        bind.autoTitle.text = if (on) tr("Auto-clean is on") else tr("Auto-clean is off")
        bind.autoSub.text = if (on) tr("Junk is cleaned every week") else tr("Clean junk every week on its own")
        bind.btnAuto.text = if (on) tr("Manage") else tr("Turn on")
    }

    private fun legend(l: ViewLegendBinding, color: Int, label: String) {
        l.dot.setTint(color)
        l.label.text = label
    }

    private fun stat(s: ViewStatCardBinding, label: String, color: Int, click: () -> Unit) {
        s.label.text = label
        s.bar.setIndicatorColor(ContextCompat.getColor(requireContext(), color))
        s.card.onClick(click)
    }

    private fun card(c: ViewToolCardBinding, icon: Int, tone: Tone, title: String, click: () -> Unit) {
        c.icon.tile(icon, tone)
        c.title.text = title
        c.card.onClick(click)
    }

    private fun mini(c: ViewToolMiniBinding, icon: Int, tone: Tone, label: String, click: () -> Unit) {
        c.icon.tile(icon, tone)
        c.label.text = label
        c.card.onClick(click)
    }
}
