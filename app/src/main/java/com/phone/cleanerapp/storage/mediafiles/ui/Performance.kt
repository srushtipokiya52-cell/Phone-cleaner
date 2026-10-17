package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.data.AppItem
import com.phone.cleanerapp.storage.mediafiles.data.AppRepo
import com.phone.cleanerapp.storage.mediafiles.data.AppState
import com.phone.cleanerapp.storage.mediafiles.data.CleanResult
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.data.ResultStat
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentGenericBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemRowBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewNoticeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows a real app icon instead of a tinted glyph tile. */
fun ImageView.appIcon(d: Drawable?) {
    imageTintList = null
    backgroundTintList = null
    background = null
    setPadding(0, 0, 0, 0)
    setImageDrawable(d)
}

fun FragmentGenericBinding.notice(f: androidx.fragment.app.Fragment, text: String, button: String, click: () -> Unit) {
    val n = ViewNoticeBinding.inflate(f.layoutInflater, extra, false)
    n.text.text = text
    n.btn.text = button
    n.btn.onClick(click)
    extra.addView(n.root)
}

fun FragmentGenericBinding.hideHeroTexts() {
    heroLabel.show(false); heroValue.show(false); heroSub.show(false)
}

class BoosterFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private var apps: List<AppItem> = emptyList()
    private val picked = HashSet<String>()
    private var firstLoad = true
    private var boosting = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Phone Booster"))
        b.skeletonAfterSetup()
        b.ringBox.show(true)
        b.heroLabel.show(false); b.heroValue.show(false)
        b.btnPrimary.onClick { boost() }
        onEachResume { load() }
    }

    private suspend fun load() {
        val ctx = requireContext().applicationContext
        apps = withContext(Dispatchers.IO) { AppRepo.recentlyUsed(ctx) }
        if (firstLoad) {
            picked.addAll(apps.map { it.pkg }); firstLoad = false
        }
        render()
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        val ram = DeviceInfo.ram(ctx)
        bind.ring.setSegments(ram.percent / 100f to R.color.brand)
        bind.ringValue.text = "${ram.percent}%"
        bind.ringLabel.text = tr("RAM used")
        bind.heroSub.text = trf("Used %1\$s · Free %2\$s · Total %3\$s", Fmt.size(ram.used), Fmt.size(ram.free), Fmt.size(ram.total))
        bind.extra.removeAllViews()
        if (!Perms.hasUsage(ctx)) {
            bind.notice(this, tr("Allow usage access to see which apps were running recently."), tr("Allow")) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        bind.listTitle.show(true)
        bind.listTitle.text = tr("Recently active apps")
        bind.list.removeAllViews()
        bind.list.show(apps.isNotEmpty())
        bind.emptyText.show(apps.isEmpty())
        bind.emptyText.text = if (Perms.hasUsage(ctx)) tr("No apps were active recently. Your memory is in good shape.") else tr("Usage access is needed to list apps.")
        for (a in apps) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.appIcon(a.icon)
            row.title.text = a.label
            row.sub.text = trf("Used %1\$s", agoText(a.lastUsed))
            row.check.show(true)
            row.check.isChecked = a.pkg in picked
            row.row.onClick { if (!picked.remove(a.pkg)) picked.add(a.pkg); render() }
            bind.list.addView(row.root)
        }
        bind.btnPrimary.text = if (boosting) "Boosting…" else if (picked.isEmpty()) tr("Boost memory") else trf("Boost · stop %1\$s apps", picked.size)
    }

    private fun boost() {
        if (boosting) return
        boosting = true
        render()
        val ctx = requireContext().applicationContext
        val pkgs = picked.toList()
        ui {
            val freed = withContext(Dispatchers.IO) { AppRepo.boost(ctx, pkgs) }
            val ram = DeviceInfo.ram(ctx)
            AppState.result = CleanResult(
                if (freed > 0) trf("%1\$s freed", Fmt.size(freed)) else tr("Memory optimized"),
                tr("Background apps were asked to stop. Android may restart some of them when needed."),
                listOf(
                    ResultStat("${ram.percent}%", tr("RAM used")),
                    ResultStat(Fmt.size(ram.free), tr("RAM free")),
                    ResultStat("${pkgs.size}", tr("Apps stopped")),
                ),
            )
            boosting = false
            go(R.id.cleanDoneFragment)
        }
    }
}

fun agoText(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        ms <= 0 -> tr("a long time ago")
        min < 1 -> tr("just now")
        min < 60 -> trf("%1\$s min ago", min)
        min < 24 * 60 -> trf("%1\$s h ago", min / 60)
        else -> trf("%1\$s days ago", min / (24 * 60))
    }
}

class CoolerFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private var apps: List<AppItem> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("CPU Cooler"))
        b.skeletonAfterSetup()
        b.ringBox.show(true)
        b.heroLabel.show(false); b.heroValue.show(false)
        b.btnPrimary.text = tr("Cool Down")
        b.btnPrimary.onClick { cool() }
        onEachResume { load() }
    }

    private suspend fun load() {
        val ctx = requireContext().applicationContext
        apps = withContext(Dispatchers.IO) { AppRepo.recentlyUsed(ctx, 2).take(6) }
        render()
    }

    private fun state(t: Float) = when {
        t < 35 -> tr("Cool") to R.color.brand
        t < 42 -> tr("Normal") to R.color.cyan
        t < 48 -> tr("Warm") to R.color.amber
        else -> tr("Hot") to R.color.red
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        val t = DeviceInfo.cpuTempC(ctx)
        val (label, color) = state(t)
        bind.ring.setSegments(((t - 20) / 40f).coerceIn(0.05f, 1f) to color)
        bind.ringValue.text = "${t.toInt()}°C"
        bind.ringLabel.text = label
        bind.heroSub.text = tr("Cool below 35° · Normal to 42° · Warm to 48° · Hot above")
        bind.extra.removeAllViews()
        bind.listTitle.show(true)
        bind.listTitle.text = tr("Recently active apps")
        bind.list.removeAllViews()
        bind.list.show(apps.isNotEmpty())
        if (!Perms.hasUsage(ctx)) {
            bind.notice(this, tr("Allow usage access to see which apps are active right now."), tr("Allow")) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        for (a in apps) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.appIcon(a.icon)
            row.title.text = a.label
            row.sub.text = trf("Used %1\$s", agoText(a.lastUsed))
            bind.list.addView(row.root)
        }
        bind.emptyText.show(apps.isEmpty())
        bind.emptyText.text = tr("Remove the case and avoid charging while gaming to cool down faster.")
    }

    private fun cool() {
        val ctx = requireContext().applicationContext
        val pkgs = apps.map { it.pkg }
        val before = DeviceInfo.cpuTempC(ctx)
        ui {
            withContext(Dispatchers.IO) { AppRepo.boost(ctx, pkgs) }
            val after = DeviceInfo.cpuTempC(ctx)
            AppState.result = CleanResult(
                tr("Cooling started"),
                tr("Background apps were stopped. Temperature can take a few minutes to drop."),
                listOf(
                    ResultStat("${before.toInt()}°C", tr("Before")),
                    ResultStat("${after.toInt()}°C", tr("Now")),
                    ResultStat("${pkgs.size}", tr("Apps stopped")),
                ),
            )
            go(R.id.cleanDoneFragment)
        }
    }
}

class BatteryFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Battery"))
        b.ringBox.show(true)
        b.heroLabel.show(false); b.heroValue.show(false)
        b.btnPrimary.text = tr("Boost apps")
        b.btnPrimary.onClick { go(R.id.boosterFragment) }
        b.btnSecondary.show(true)
        b.btnSecondary.text = tr("Battery saver")
        b.btnSecondary.onClick {
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            } catch (_: Throwable) {
                toast(tr("Not available on this device"))
            }
        }
        onEachResume { render() }
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        val s = DeviceInfo.battery(ctx)
        bind.ring.setSegments(s.level / 100f to if (s.level <= 20) R.color.red else R.color.cyan)
        bind.ringValue.text = "${s.level}%"
        bind.ringLabel.text = s.status
        bind.heroSub.text = when {
            s.charging && s.minutesToFull != null -> trf("About %1\$s h %2\$s min to full · Health: %3\$s", s.minutesToFull / 60, s.minutesToFull % 60, s.health)
            else -> trf("Health: %1\$s", s.health)
        }
        bind.list.removeAllViews()
        fun add(icon: Int, tone: Tone, name: String, value: String) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.tile(icon, tone)
            row.title.text = name
            row.trail.text = value
            bind.list.addView(row.root)
        }
        add(R.drawable.ic_thermo, Tone.AMBER, tr("Temperature"), "%.1f°C".format(s.tempC))
        add(R.drawable.ic_bolt, Tone.CYAN, tr("Voltage"), "%.2f V".format(s.voltage))
        add(R.drawable.ic_battery, Tone.GREEN, tr("Capacity"), s.capacityMah?.let { "$it mAh" } ?: "—")
        add(R.drawable.ic_clock, Tone.GRAY, tr("Status"), s.status)
        add(R.drawable.ic_shield, Tone.GREEN, tr("Health"), s.health)
        add(R.drawable.ic_file, Tone.GRAY, tr("Type"), s.technology)
        add(R.drawable.ic_clean, Tone.VIOLET, tr("Cycles"), s.cycles?.toString() ?: "—")
    }
}

/** Skeleton for the shared screen layout: hero card, filter chips and the main button. */
fun FragmentGenericBinding.skeleton(on: Boolean) {
    if (on) {
        if (chipScroll.visibility == View.VISIBLE && chips.childCount == 0) {
            repeat(3) { chips.addView(chipView(root.context, "Loading", false) { }) }
        }
        Skeleton.start(hero)
        Skeleton.start(chips)
        Skeleton.start(btnPrimary)
    } else {
        Skeleton.stop(hero)
        Skeleton.stop(chips)
        Skeleton.stop(btnPrimary)
    }
}

fun FragmentGenericBinding.skeletonAfterSetup(withChips: Boolean = false) {
    if (withChips) chipScroll.show(true)
    skeleton(true)
}
