package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.goHome
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.data.AppState
import com.phone.cleanerapp.storage.mediafiles.data.CleanResult
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.data.JunkCategory
import com.phone.cleanerapp.storage.mediafiles.data.JunkProgress
import com.phone.cleanerapp.storage.mediafiles.data.JunkScanner
import com.phone.cleanerapp.storage.mediafiles.data.JunkType
import com.phone.cleanerapp.storage.mediafiles.data.ResultStat
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentCleanDoneBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentJunkResultsBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentScanBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemRowBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemStatBoxBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.LayoutHeaderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

fun LayoutHeaderBinding.setup(f: Fragment, titleText: String, subtitleText: String? = null) {
    title.text = titleText
    if (subtitleText != null) {
        subtitle.text = subtitleText
        subtitle.show(true)
    }
    back.onClick { f.back() }
}

fun junkIcon(t: JunkType): Pair<Int, Tone> = when (t) {
    JunkType.CACHE -> R.drawable.ic_folder to Tone.GREEN
    JunkType.RESIDUAL -> R.drawable.ic_trash to Tone.AMBER
    JunkType.APK -> R.drawable.ic_download to Tone.CYAN
    JunkType.TEMP -> R.drawable.ic_clock to Tone.GRAY
    JunkType.EMPTY -> R.drawable.ic_folder_empty to Tone.GRAY
}

class ScanFragment : VBFragment<FragmentScanBinding>(FragmentScanBinding::inflate) {
    private var job: Job? = null
    private var finished = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Junk Clean"))
        b.btnResults.alpha = 0.5f
        b.btnStop.onClick { job?.cancel(); back() }
        b.btnResults.onClick { if (finished) go(R.id.junkResultsFragment) }
        render(JunkProgress(0f, tr("Starting…"), 0, JunkType.entries.map { JunkCategory(it) }))

        val ctx = requireContext().applicationContext
        job = ui {
            val cats = withContext(Dispatchers.IO) {
                JunkScanner.scan(ctx) { p -> bOrNull?.root?.post { if (!finished) render(p) } }
            }
            AppState.junk = cats
            finished = true
            val bind = bOrNull ?: return@ui
            render(JunkProgress(1f, "", cats.sumOf { it.bytes }, cats))
            bind.btnResults.alpha = 1f
        }
    }

    private fun render(p: JunkProgress) {
        val bind = bOrNull ?: return
        val (n, unit) = Fmt.sizeParts(p.foundBytes)
        bind.found.text = android.text.SpannableString("$n $unit").apply {
            setSpan(android.text.style.RelativeSizeSpan(0.4f), n.length, length, 0)
        }
        val pct = (p.fraction * 100).toInt()
        bind.percent.text = if (p.fraction >= 1f) tr("Scan complete") else trf("%1\$s scanned", "$pct%")
        bind.ring.setSegments(p.fraction to R.color.brand)
        bind.path.text = if (p.fraction >= 1f) tr("Scan finished") else trf("Scanning %1\$s…", p.currentPath)
        bind.list.removeAllViews()
        for (c in p.categories) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            val (icon, tone) = junkIcon(c.type)
            row.icon.tile(icon, tone)
            row.title.text = tr(c.type.title)
            row.trail.text = Fmt.size(c.bytes)
            if (p.fraction >= 1f) {
                row.status.setImageResource(R.drawable.ic_check_on)
                row.status.show(true)
            } else row.spin.show(true)
            bind.list.addView(row.root)
        }
    }
}

class JunkResultsFragment : VBFragment<FragmentJunkResultsBinding>(FragmentJunkResultsBinding::inflate) {
    private var cats: List<JunkCategory> = emptyList()
    private var cleaning = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cats = AppState.junk?.filter { it.bytes > 0 || it.files.isNotEmpty() } ?: emptyList()
        b.header.setup(this, tr("Scan Results"))
        b.header.action.show(true)
        b.header.action.onClick {
            val all = cats.all { it.selected }
            cats.forEach { it.selected = !all }
            render()
        }
        b.btnClean.onClick { clean() }
        render()
    }

    private fun render() {
        val bind = bOrNull ?: return
        val total = cats.sumOf { it.bytes }
        val (n, unit) = Fmt.sizeParts(total)
        bind.total.text = android.text.SpannableString("$n $unit").apply {
            setSpan(android.text.style.RelativeSizeSpan(0.4f), n.length, length, 0)
        }
        bind.header.action.text = if (cats.all { it.selected }) tr("Deselect all") else tr("Select all")
        bind.list.removeAllViews()
        for (c in cats) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            val (icon, tone) = junkIcon(c.type)
            row.icon.tile(icon, tone)
            row.title.text = tr(c.type.title)
            row.sub.text = if (c.type == JunkType.EMPTY) trf("%1\$s folders", c.files.size) else tr(c.type.desc)
            row.trail.text = if (c.type == JunkType.EMPTY) "" else Fmt.size(c.bytes)
            row.check.show(true)
            row.check.isChecked = c.selected
            row.row.onClick { c.selected = !c.selected; render() }
            bind.list.addView(row.root)
        }
        val sel = cats.filter { it.selected }.sumOf { it.bytes }
        bind.btnClean.text = if (cleaning) tr("Cleaning…") else trf("Clean %1\$s", Fmt.size(sel))
        bind.btnClean.alpha = if (cats.any { it.selected } && !cleaning) 1f else 0.5f
    }

    private fun clean() {
        if (cleaning || cats.none { it.selected }) return
        cleaning = true
        render()
        ui {
            val stats = withContext(Dispatchers.IO) { JunkScanner.clean(cats) }
            val free = DeviceInfo.storage().free
            AppState.junk = null
            AppState.result = CleanResult(
                trf("%1\$s cleaned", Fmt.size(stats.bytes)),
                trf("Your phone now has %1\$s free space.", Fmt.size(free)),
                listOf(
                    ResultStat(Fmt.size(free), tr("Free now")),
                    ResultStat("%,d".format(stats.files), tr("Files removed")),
                    ResultStat("%,d".format(stats.folders), tr("Empty folders")),
                ),
            )
            go(R.id.cleanDoneFragment)
        }
    }
}

class CleanDoneFragment : VBFragment<FragmentCleanDoneBinding>(FragmentCleanDoneBinding::inflate) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val r = AppState.result ?: CleanResult(tr("All clean"), tr("Nothing to remove right now."), emptyList())
        b.headline.text = r.headline
        b.sub.text = r.sub
        r.stats.forEach { s ->
            val box = ItemStatBoxBinding.inflate(layoutInflater, b.stats, false)
            box.value.text = s.value
            box.label.text = s.label
            b.stats.addView(box.root)
        }
        val items = listOf(
            Triple(tr("Similar photos"), tr("Review look-alike shots"), R.id.photosFragment) to (R.drawable.ic_photo to Tone.CYAN),
            Triple(tr("Boost memory"), tr("Stop apps running in background"), R.id.boosterFragment) to (R.drawable.ic_bolt to Tone.GREEN),
        )
        for ((t, ic) in items) {
            val row = ItemRowBinding.inflate(layoutInflater, b.suggestions, false)
            row.icon.tile(ic.first, ic.second)
            row.title.text = t.first
            row.sub.text = t.second
            row.trail.text = tr("Review")
            row.trail.setTextColor(requireContext().getColor(R.color.brand))
            row.row.onClick { go(t.third) }
            b.suggestions.addView(row.root)
        }
        b.btnDone.onClick { goHome() }
    }
}
