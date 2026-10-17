package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.data.AppState
import com.phone.cleanerapp.storage.mediafiles.data.ChatApp
import com.phone.cleanerapp.storage.mediafiles.data.CleanResult
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.data.FileItem
import com.phone.cleanerapp.storage.mediafiles.data.FileKind
import com.phone.cleanerapp.storage.mediafiles.data.Fs
import com.phone.cleanerapp.storage.mediafiles.data.LargeFileScanner
import com.phone.cleanerapp.storage.mediafiles.data.PhotoGroup
import com.phone.cleanerapp.storage.mediafiles.data.PhotoGroupType
import com.phone.cleanerapp.storage.mediafiles.data.PhotoScanner
import com.phone.cleanerapp.storage.mediafiles.data.ResultStat
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentChatMediaBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentFileListBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentPhotosBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemFileBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemPhotoGroupBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemRowBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemThumbBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// Similar & duplicate photos
// ---------------------------------------------------------------------------------------------

class PhotosFragment : VBFragment<FragmentPhotosBinding>(FragmentPhotosBinding::inflate) {

    private var groups: List<PhotoGroup> = emptyList()
    private var tab = PhotoGroupType.SIMILAR
    private var pendingDelete: List<com.phone.cleanerapp.storage.mediafiles.data.Photo> = emptyList()

    private val adapter = RowAdapter<PhotoGroup, ItemPhotoGroupBinding>(ItemPhotoGroupBinding::inflate) { row, g, _ -> bindGroup(row, g) }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) afterDelete(pendingDelete) else toast(tr("Nothing was deleted"))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Photo Cleaner"))
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.btnDelete.onClick { deleteSelected() }
        repeat(3) { b.tabs.addView(chipView(requireContext(), "Similar (0)", false) { }) }
        b.header.subtitle.text = "00 groups · 00 MB can be freed"
        b.header.subtitle.show(true)
        Skeleton.start(b.tabs); Skeleton.start(b.header.subtitle); Skeleton.start(b.btnDelete)

        val cached = AppState.photos
        if (cached != null) {
            groups = cached; loaded()
        } else {
            val ctx = requireContext().applicationContext
            ui {
                groups = withContext(Dispatchers.IO) { PhotoScanner.scan(ctx) }
                AppState.photos = groups
                loaded()
            }
        }
    }

    private fun loaded() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        if (groups.none { it.type == tab }) groups.firstOrNull()?.let { tab = it.type }
        render()
    }

    private fun render() {
        val bind = bOrNull ?: return
        Skeleton.stop(bind.tabs); Skeleton.stop(bind.header.subtitle); Skeleton.stop(bind.btnDelete)
        val ctx = requireContext()
        val total = groups.sumOf { it.reclaimable }
        bind.header.subtitle.text = trf("%1\$s groups · %2\$s can be freed", groups.size, Fmt.size(total))
        bind.header.subtitle.show(true)
        bind.tabs.removeAllViews()
        for (t in PhotoGroupType.entries) {
            val n = groups.count { it.type == t }
            bind.tabs.addView(chipView(ctx, "${tr(t.label)} ($n)", t == tab) { tab = t; render() })
        }
        val shown = groups.filter { it.type == tab }
        adapter.items = shown
        bind.empty.show(shown.isEmpty())
        bind.empty.text = if (groups.isEmpty()) tr("No similar or duplicate photos found. Nice and tidy!") else tr("Nothing in this tab.")
        val sel = shown.flatMap { it.photos }.filter { it.selected }
        bind.btnDelete.text = if (sel.isEmpty()) tr("Select photos to remove") else trf("Move %1\$s to bin · %2\$s", sel.size, Fmt.size(sel.sumOf { it.size }))
        bind.btnDelete.alpha = if (sel.isEmpty()) 0.5f else 1f
    }

    private fun bindGroup(row: ItemPhotoGroupBinding, g: PhotoGroup) {
        row.title.text = g.title
        row.meta.text = trf("Keep the best · frees %1\$s", Fmt.size(g.reclaimable))
        row.thumbs.removeAllViews()
        g.photos.forEachIndexed { i, p ->
            val t = ItemThumbBinding.inflate(layoutInflater, row.thumbs, false)
            t.img.load(p.uri) { crossfade(true) }
            t.best.show(i == 0 && g.type != PhotoGroupType.SCREENSHOT)
            t.check.isChecked = p.selected
            t.root.onClick {
                val wouldKeepNone = g.type != PhotoGroupType.SCREENSHOT && !p.selected && g.photos.count { !it.selected } <= 1
                if (wouldKeepNone) toast(tr("Keep at least one photo")) else {
                    p.selected = !p.selected
                    t.check.isChecked = p.selected
                    render()
                }
            }
            row.thumbs.addView(t.root)
        }
    }

    private fun deleteSelected() {
        val sel = groups.filter { it.type == tab }.flatMap { it.photos }.filter { it.selected }
        if (sel.isEmpty()) return
        pendingDelete = sel
        val sender = PhotoScanner.trash(requireContext(), sel)
        if (sender == null) afterDelete(sel) else deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun afterDelete(deleted: List<com.phone.cleanerapp.storage.mediafiles.data.Photo>) {
        val ids = deleted.map { it.id }.toSet()
        val bytes = deleted.sumOf { it.size }
        groups = groups.mapNotNull { g ->
            val left = g.photos.filter { it.id !in ids }
            if (left.size > 1 || (g.type == PhotoGroupType.SCREENSHOT && left.isNotEmpty())) PhotoGroup(g.type, g.title, left) else null
        }
        AppState.photos = groups
        AppState.result = CleanResult(
            trf("%1\$s cleaned", Fmt.size(bytes)), trf("%1\$s photos moved to the Recycle Bin. They stay there for 30 days.", deleted.size),
            listOf(
                ResultStat(Fmt.size(DeviceInfo.storage().free), tr("Free now")),
                ResultStat("${deleted.size}", tr("Photos removed")),
                ResultStat(tr("30 days"), tr("In bin")),
            ),
        )
        go(R.id.cleanDoneFragment)
    }
}

// ---------------------------------------------------------------------------------------------
// Large files / storage category / chat media review
// ---------------------------------------------------------------------------------------------

class FileListFragment : VBFragment<FragmentFileListBinding>(FragmentFileListBinding::inflate) {

    private var all: List<FileItem> = emptyList()
    private var filter: FileKind? = null
    private var mode = Mode.LARGE
    private var kind: FileKind? = null

    private enum class Mode { LARGE, KIND, CHAT }

    private val adapter = RowAdapter<FileItem, ItemFileBinding>(ItemFileBinding::inflate) { row, f, _ -> bindFile(row, f) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val appId = arguments?.getString("app").orEmpty()
        val category = arguments?.getString("category").orEmpty()
        mode = when {
            appId.isNotEmpty() -> Mode.CHAT
            category.isNotEmpty() -> Mode.KIND
            else -> Mode.LARGE
        }
        kind = if (mode == Mode.KIND) FileKind.valueOf(category) else null
        val mb = Prefs.get(ctx).largeFileMb
        when (mode) {
            Mode.LARGE -> b.header.setup(this, tr("Large Files"), trf("Files bigger than %1\$s MB", mb))
            Mode.KIND -> b.header.setup(this, tr(kind!!.label), tr("Biggest files first"))
            Mode.CHAT -> b.header.setup(this, "$category", ChatApp.all.firstOrNull { it.id == appId }?.label)
        }
        b.list.layoutManager = LinearLayoutManager(ctx)
        b.list.adapter = adapter
        b.btnDelete.onClick { confirmDelete() }
        b.btnMove.onClick { moveToSd() }
        b.btnMove.show(sdCard() != null && mode == Mode.LARGE)
        if (mode == Mode.LARGE) repeat(4) { b.chips.addView(chipView(requireContext(), tr("Documents"), false) { }) }
        Skeleton.start(b.chips); Skeleton.start(b.btnDelete)

        val app = ctx.applicationContext
        if (mode == Mode.LARGE) AppState.largeFiles?.let { all = it; b.loading.show(false); render() }
        ui {
            all = withContext(Dispatchers.IO) {
                when (mode) {
                    Mode.LARGE -> LargeFileScanner.scan(app, mb * 1024L * 1024).also { AppState.largeFiles = it }
                    Mode.KIND -> LargeFileScanner.scanKind(app, kind!!, if (kind == FileKind.OTHER) 20L * 1024 * 1024 else 5L * 1024 * 1024)
                    Mode.CHAT -> ChatApp.all.firstOrNull { it.id == appId }?.categories()?.firstOrNull { it.name == category }?.files ?: emptyList()
                }
            }
            bOrNull?.loading?.show(false)
            render()
        }
    }

    private fun render() {
        val bind = bOrNull ?: return
        Skeleton.stop(bind.chips); Skeleton.stop(bind.btnDelete)
        val ctx = requireContext()
        bind.chips.removeAllViews()
        if (mode == Mode.LARGE) {
            val kinds = listOf<FileKind?>(null) + FileKind.entries.filter { k -> all.any { it.kind == k } }
            for (k in kinds) {
                bind.chips.addView(chipView(ctx, k?.let { tr(it.label) } ?: tr("All"), k == filter) { filter = k; render() })
            }
        }
        val shown = if (filter == null) all else all.filter { it.kind == filter }
        adapter.items = shown
        bind.empty.show(shown.isEmpty())
        bind.empty.text = tr("No files found here.")
        val sel = all.filter { it.selected }
        bind.btnDelete.text = if (sel.isEmpty()) tr("Select files to delete") else trf("Delete %1\$s · %2\$s", sel.size, Fmt.size(sel.sumOf { it.size }))
        bind.btnDelete.alpha = if (sel.isEmpty()) 0.5f else 1f
    }

    private fun bindFile(row: ItemFileBinding, f: FileItem) {
        val ctx = requireContext()
        row.name.text = f.file.name
        row.size.text = Fmt.size(f.size)
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(f.file.lastModified()))
        row.meta.text = "$date · ${f.file.parent?.removePrefix(Fs.root().path)?.ifEmpty { "/" }}"
        if (f.kind == FileKind.IMAGE) {
            row.icon.setPadding(0, 0, 0, 0)
            row.icon.imageTintList = null
            row.icon.load(f.file) { crossfade(true) }
        } else {
            val (icon, tone) = when (f.kind) {
                FileKind.VIDEO -> R.drawable.ic_video to Tone.PINK
                FileKind.AUDIO -> R.drawable.ic_music to Tone.VIOLET
                FileKind.DOC -> R.drawable.ic_file to Tone.AMBER
                FileKind.ARCHIVE -> R.drawable.ic_download to Tone.CYAN
                else -> R.drawable.ic_file to Tone.GRAY
            }
            val p = (12 * ctx.resources.displayMetrics.density).toInt()
            row.icon.setPadding(p, p, p, p)
            row.icon.tile(icon, tone)
        }
        row.check.isChecked = f.selected
        row.row.onClick { f.selected = !f.selected; row.check.isChecked = f.selected; render() }
    }

    private fun confirmDelete() {
        val sel = all.filter { it.selected }
        if (sel.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(trf("Delete %1\$s files?", sel.size))
            .setMessage(trf("%1\$s will be permanently deleted. This can't be undone.", Fmt.size(sel.sumOf { it.size })))
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Delete")) { _, _ -> delete(sel) }
            .show()
    }

    private fun delete(sel: List<FileItem>) {
        val app = requireContext().applicationContext
        ui {
            val removed = withContext(Dispatchers.IO) {
                val done = sel.filter { Fs.deleteRecursively(it.file) }
                MediaScannerConnection.scanFile(app, done.map { it.file.path }.toTypedArray(), null, null)
                done
            }
            val bytes = removed.sumOf { it.size }
            all = all - removed.toSet()
            if (mode == Mode.LARGE) AppState.largeFiles = all
            AppState.result = CleanResult(
                trf("%1\$s cleaned", Fmt.size(bytes)), trf("%1\$s files deleted.", removed.size),
                listOf(
                    ResultStat(Fmt.size(DeviceInfo.storage().free), tr("Free now")),
                    ResultStat("${removed.size}", tr("Files removed")),
                    ResultStat("${sel.size - removed.size}", tr("Skipped")),
                ),
            )
            go(R.id.cleanDoneFragment)
        }
    }

    private fun sdCard(): File? = ContextCompat.getExternalFilesDirs(requireContext(), null).getOrNull(1)

    private fun moveToSd() {
        val sd = sdCard() ?: return toast(tr("No SD card found"))
        val sel = all.filter { it.selected }
        if (sel.isEmpty()) return toast(tr("Select files first"))
        ui {
            val moved = withContext(Dispatchers.IO) {
                val target = File(sd, "Moved").apply { mkdirs() }
                sel.filter { f ->
                    try {
                        val dest = File(target, f.file.name)
                        f.file.copyTo(dest, overwrite = true)
                        dest.length() == f.file.length() && f.file.delete()
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
            all = all - moved.toSet()
            toast(trf("Moved %1\$s files to the SD card", moved.size))
            render()
        }
    }
}

class ChatMediaFragment : VBFragment<FragmentChatMediaBinding>(FragmentChatMediaBinding::inflate) {

    private var data: Map<ChatApp, List<com.phone.cleanerapp.storage.mediafiles.data.ChatCategory>> = emptyMap()
    private var current: ChatApp? = null

    companion object {
        private var cachedData: Map<ChatApp, List<com.phone.cleanerapp.storage.mediafiles.data.ChatCategory>>? = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Chat Media Cleaner"))
        repeat(3) { b.chips.addView(chipView(requireContext(), "WhatsApp", false) { }) }
        b.heroLabel.text = "WhatsApp media"; b.heroSize.text = "000 MB"; b.heroSub.text = "Sent files make up 000 MB"
        Skeleton.start(b.chips); Skeleton.start(b.heroLabel.parent as View)
        cachedData?.let { data = it; current = it.keys.firstOrNull(); b.loading.show(false); render() }
        ui {
            data = withContext(Dispatchers.IO) { ChatApp.all.associateWith { it.categories() }.filter { it.value.isNotEmpty() } }
            cachedData = data
            AppState.chatTotal = data.values.sumOf { cats -> cats.sumOf { it.bytes } }
            current = data.keys.firstOrNull()
            bOrNull?.loading?.show(false)
            render()
        }
    }

    private fun render() {
        val bind = bOrNull ?: return
        Skeleton.stop(bind.chips); Skeleton.stop(bind.heroLabel.parent as View)
        val ctx = requireContext()
        bind.chips.removeAllViews()
        data.keys.forEach { a -> bind.chips.addView(chipView(ctx, a.label, a == current) { current = a; render() }) }
        bind.list.removeAllViews()
        val app = current
        if (app == null) {
            bind.heroLabel.text = tr("No chat media found")
            bind.heroSize.text = "0 MB"
            bind.heroSub.text = tr("WhatsApp and Telegram media will show up here. Needs All files access.")
            return
        }
        val cats = data.getValue(app)
        val total = cats.sumOf { it.bytes }
        val sent = cats.sumOf { c -> c.files.filter { it.note == "Sent" }.sumOf { it.size } }
        bind.heroLabel.text = trf("%1\$s media", app.label)
        bind.heroSize.text = Fmt.size(total)
        bind.heroSub.text = trf("Sent files make up %1\$s", Fmt.size(sent))
        for (c in cats) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            val (icon, tone) = when {
                c.name.contains("Image", true) -> R.drawable.ic_photo to Tone.CYAN
                c.name.contains("Video", true) -> R.drawable.ic_video to Tone.PINK
                c.name.contains("Audio", true) || c.name.contains("Voice", true) -> R.drawable.ic_music to Tone.VIOLET
                c.name.contains("Document", true) -> R.drawable.ic_file to Tone.AMBER
                else -> R.drawable.ic_folder to Tone.GRAY
            }
            row.icon.tile(icon, tone)
            row.title.text = tr(c.name)
            row.sub.text = trf("%1\$s files · %2\$s", c.files.size, Fmt.size(c.bytes))
            row.trail.text = tr("Review & clean")
            row.trail.setTextColor(ctx.getColor(R.color.brand))
            row.row.onClick { findNavController().navigate(R.id.fileListFragment, bundleOf("app" to app.id, "category" to c.name)) }
            bind.list.addView(row.root)
        }
    }
}
