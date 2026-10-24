package com.phone.cleanerapp.storage.mediafiles.ui

import com.ads.AdRemoteConfig
import com.ads.AdsManager
import com.ads.module.billing.AppPurchase
import com.ads.native_tools
import com.ads.populateNativeAdView
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.dp
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.data.AppRepo
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentStorageBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentToolsBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageFragment : VBFragment<FragmentStorageBinding>(FragmentStorageBinding::inflate) {

    companion object {
        private var cached: List<Cat>? = null
    }

    private class Cat(val name: String, val icon: Int, val tone: Tone, val bytes: Long, val count: Int?, val dest: Int, val args: Bundle? = null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val st = DeviceInfo.storage()
        b.sub.text = trf("Internal storage · %1\$s", Fmt.size(st.total)) + "\n" + trf("%1\$s used · %2\$s free", Fmt.size(st.used), Fmt.size(st.free))
        b.usedPct.text = "${(st.used * 100 / st.total)}%"
        b.ring.setSegments(st.used.toFloat() / st.total to R.color.brand)
        val ctx = requireContext().applicationContext
        // Show the last result straight away, then refresh it in the background.
        cached?.let { show(it) }
        ui {
            val cats = withContext(Dispatchers.IO) { load(ctx, st.used) }
            cached = cats
            show(cats)
        }
    }

    private fun show(cats: List<Cat>) {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.list.removeAllViews()
        for (c in cats) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.tile(c.icon, c.tone)
            row.title.text = c.name
            row.sub.text = c.count?.let { trf("%1\$s items", "%,d".format(it)) } ?: ""
            row.sub.show(c.count != null)
            row.trail.text = Fmt.size(c.bytes)
            row.row.onClick { go(c.dest, c.args) }
            bind.list.addView(row.root)
        }
    }

    private fun load(ctx: Context, used: Long): List<Cat> {
        val (imgBytes, imgCount) = sum(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        val (vidBytes, vidCount) = sum(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null)
        val (audBytes, audCount) = sum(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null)
        val (docBytes, docCount) = sum(
            ctx, MediaStore.Files.getContentUri("external"),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=0 AND (${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')"
        )
        val apps = AppRepo.installed(ctx)
        val appBytes = apps.sumOf { it.size }
        val other = (used - imgBytes - vidBytes - audBytes - docBytes - appBytes).coerceAtLeast(0)
        return listOf(
            Cat(tr("Images"), R.drawable.ic_photo, Tone.CYAN, imgBytes, imgCount, R.id.photosFragment),
            Cat(tr("Videos"), R.drawable.ic_video, Tone.PINK, vidBytes, vidCount, R.id.fileListFragment, bundleOf("category" to "VIDEO")),
            Cat(tr("Audio"), R.drawable.ic_music, Tone.VIOLET, audBytes, audCount, R.id.fileListFragment, bundleOf("category" to "AUDIO")),
            Cat(tr("Documents"), R.drawable.ic_file, Tone.AMBER, docBytes, docCount, R.id.fileListFragment, bundleOf("category" to "DOC")),
            Cat(tr("Apps"), R.drawable.ic_grid, Tone.GREEN, appBytes, apps.size, R.id.appsFragment),
            Cat(tr("Other"), R.drawable.ic_folder, Tone.GRAY, other, null, R.id.fileListFragment, bundleOf("category" to "OTHER")),
        )
    }

    private fun sum(ctx: Context, uri: Uri, where: String?): Pair<Long, Int> {
        var bytes = 0L
        var n = 0
        try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), where, null, null)?.use { c ->
                while (c.moveToNext()) {
                    bytes += c.getLong(0); n++
                }
            }
        } catch (_: Throwable) {
        }
        return bytes to n
    }
}

class ToolsFragment : VBFragment<FragmentToolsBinding>(FragmentToolsBinding::inflate) {

    private class Tool(val name: String, val icon: Int, val tone: Tone, val dest: Int, val args: Bundle? = null)

    /** Medium native under the title. Shimmer first; the slot is removed when there is no ad. */
    private fun setupToolsAd() {
        val activity = requireActivity()
        val slot = b.layoutAdTools
        if (!AdRemoteConfig.native_tools.isEnable || AppPurchase.getInstance().isPurchased(activity)) {
            slot.visibility = View.GONE
            return
        }
        val shimmer = b.shimmerAds.shimmerNativeMedium
        shimmer.startShimmer()
        AdsManager.loadNativeTools(activity, R.layout.layout_native_ad_medium).observe(viewLifecycleOwner) { ad ->
            if (ad != null && (ad.admobNativeAd != null || ad.nativeView != null)) {
                populateNativeAdView(activity, ad, b.adHolder, shimmer)
            } else {
                shimmer.stopShimmer()
                slot.visibility = View.GONE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolsAd()
        group(
            tr("Clean up"), listOf(
                Tool(tr("Junk Clean"), R.drawable.ic_clean, Tone.GREEN, R.id.scanFragment),
                Tool(tr("Similar & Duplicate Photos"), R.drawable.ic_photo, Tone.CYAN, R.id.photosFragment),
                Tool(tr("Large Files"), R.drawable.ic_file, Tone.AMBER, R.id.fileListFragment),
                Tool(tr("Chat Media Cleaner"), R.drawable.ic_chat, Tone.GREEN, R.id.chatMediaFragment),
                Tool(tr("Photo Compressor"), R.drawable.ic_compress, Tone.PINK, R.id.compressorFragment),
            )
        )
        group(
            tr("Performance"), listOf(
                Tool(tr("Phone Booster"), R.drawable.ic_bolt, Tone.GREEN, R.id.boosterFragment),
                Tool(tr("CPU Cooler"), R.drawable.ic_thermo, Tone.AMBER, R.id.coolerFragment),
                Tool(tr("Battery Info"), R.drawable.ic_battery, Tone.CYAN, R.id.batteryFragment),
            )
        )
        group(
            tr("Manage"), listOf(
                Tool(tr("App Manager"), R.drawable.ic_grid, Tone.VIOLET, R.id.appsFragment),
                Tool(tr("Duplicate Contacts"), R.drawable.ic_user, Tone.VIOLET, R.id.contactsFragment),
                Tool(tr("Notification Cleaner"), R.drawable.ic_bell, Tone.AMBER, R.id.notificationsFragment),
                Tool(tr("Storage Analyzer"), R.drawable.ic_pie, Tone.CYAN, R.id.storageFragment),
            )
        )
    }

    private fun group(title: String, tools: List<Tool>) {
        val ctx = requireContext()
        val h = TextView(ctx).apply {
            text = title
            setTextAppearance(R.style.Txt_Section)
            setPadding(0, ctx.dp(24), 0, ctx.dp(10))
        }
        b.container.addView(h)
        val card = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_lg)
            setPadding(ctx.dp(16), ctx.dp(6), ctx.dp(16), ctx.dp(6))
        }
        for (t in tools) {
            val row = ItemRowBinding.inflate(layoutInflater, card, false)
            row.icon.tile(t.icon, t.tone)
            row.title.text = t.name
            row.title.textSize = 15f
            row.status.setImageResource(R.drawable.ic_chevron)
            row.status.imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.hint))
            row.status.show(true)
            row.row.onClick { go(t.dest, t.args ?: bundleOf()) }
            card.addView(row.root)
        }
        b.container.addView(card)
    }
}
