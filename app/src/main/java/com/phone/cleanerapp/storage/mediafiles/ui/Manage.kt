package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Fmt
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.data.AppItem
import com.phone.cleanerapp.storage.mediafiles.data.AppRepo
import com.phone.cleanerapp.storage.mediafiles.data.AppState
import com.phone.cleanerapp.storage.mediafiles.data.CleanResult
import com.phone.cleanerapp.storage.mediafiles.data.ContactGroup
import com.phone.cleanerapp.storage.mediafiles.data.ContactGroupType
import com.phone.cleanerapp.storage.mediafiles.data.ContactsRepo
import com.phone.cleanerapp.storage.mediafiles.data.DeviceInfo
import com.phone.cleanerapp.storage.mediafiles.data.Photo
import com.phone.cleanerapp.storage.mediafiles.data.PhotoScanner
import com.phone.cleanerapp.storage.mediafiles.data.ResultStat
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentGenericBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemRowBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemThumbBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// App manager
// ---------------------------------------------------------------------------------------------

class AppsFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private enum class Tab(val label: String) { ALL("All"), UNUSED("Unused"), LARGE("Large") }

    private var apps: List<AppItem> = emptyList()
    private var tab = Tab.ALL
    private val queue = ArrayDeque<String>()

    private val uninstall = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { AppRepo.invalidate(); next() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("App Manager"))
        b.skeletonAfterSetup(true)
        b.chipScroll.show(true)
        b.heroLabel.show(false)
        b.btnPrimary.onClick { startUninstall() }
        onEachResume { load() }
    }

    private suspend fun load() {
        val ctx = requireContext().applicationContext
        val selected = apps.filter { it.selected }.map { it.pkg }.toSet()
        AppRepo.cached()?.let { if (apps.isEmpty()) { apps = it; render() } }
        apps = withContext(Dispatchers.IO) { AppRepo.installed(ctx) }
        apps.forEach { it.selected = it.pkg in selected }
        AppState.appCount = apps.size
        AppState.unusedApps = if (Perms.hasUsage(ctx)) apps.count { AppRepo.unusedDays(it) >= UNUSED_DAYS } else null
        render()
    }

    private fun shown(): List<AppItem> = when (tab) {
        Tab.ALL -> apps
        Tab.UNUSED -> apps.filter { AppRepo.unusedDays(it) >= UNUSED_DAYS }.sortedByDescending { AppRepo.unusedDays(it) }
        Tab.LARGE -> apps.filter { it.size >= 300L * 1024 * 1024 }
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        bind.chips.removeAllViews()
        Tab.entries.forEach { t -> bind.chips.addView(chipView(ctx, tr(t.label), t == tab) { tab = t; render() }) }
        bind.heroValue.text = trf("%1\$s apps", apps.size)
        bind.heroSub.text = trf("%1\$s used by apps", Fmt.size(apps.sumOf { it.size }))
        bind.extra.removeAllViews()
        val hasUsage = Perms.hasUsage(ctx)
        if (!hasUsage) {
            bind.notice(this, tr("Allow usage access to see which apps you haven't opened lately and their real sizes."), tr("Allow")) {
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        val list = shown()
        bind.list.removeAllViews()
        bind.list.show(list.isNotEmpty())
        bind.emptyText.show(list.isEmpty())
        bind.emptyText.text = if (tab == Tab.UNUSED && !hasUsage) tr("Usage access is needed to find unused apps.") else tr("Nothing to show here.")
        for (a in list) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.appIcon(a.icon)
            row.title.text = a.label
            row.sub.text = if (a.lastUsed > 0) trf("Opened %1\$s", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(a.lastUsed))) else if (hasUsage) tr("Never opened") else trf("Installed %1\$s", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(a.installed)))
            row.trail.text = Fmt.size(a.size)
            row.check.show(true)
            row.check.isChecked = a.selected
            row.row.onClick { a.selected = !a.selected; render() }
            bind.list.addView(row.root)
        }
        val n = apps.count { it.selected }
        bind.btnPrimary.text = if (n == 0) tr("Select apps to uninstall") else trf("Uninstall %1\$s", n)
        bind.btnPrimary.alpha = if (n == 0) 0.5f else 1f
    }

    private fun startUninstall() {
        val sel = apps.filter { it.selected }.map { it.pkg }
        if (sel.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(trf("Uninstall %1\$s apps?", sel.size))
            .setMessage(tr("Android will ask you to confirm each one."))
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Continue")) { _, _ -> queue.clear(); queue.addAll(sel); next() }
            .show()
    }

    private fun next() {
        val pkg = queue.removeFirstOrNull() ?: return
        uninstall.launch(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        )
    }

    companion object {
        const val UNUSED_DAYS = 30
    }
}

// ---------------------------------------------------------------------------------------------
// Notification cleaner
// ---------------------------------------------------------------------------------------------

class NotificationsFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private var showBlocked = true
    private var apps: List<AppItem> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Notification Cleaner"))
        b.skeletonAfterSetup(true)
        b.chipScroll.show(true)
        b.heroLabel.show(false)
        b.btnPrimary.text = tr("Clear all")
        b.btnPrimary.onClick {
            Prefs.get(requireContext()).blockedLog = emptyList()
            render()
        }
        val ctx = requireContext().applicationContext
        ui {
            apps = withContext(Dispatchers.IO) { AppRepo.installed(ctx) }.sortedBy { it.label.lowercase() }
            render()
        }
        onEachResume { render() }
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        val prefs = Prefs.get(ctx)
        bind.chips.removeAllViews()
        bind.chips.addView(chipView(ctx, tr("Blocked"), showBlocked) { showBlocked = true; render() })
        bind.chips.addView(chipView(ctx, tr("Block from these apps"), !showBlocked) { showBlocked = false; render() })
        bind.heroValue.text = "${prefs.blockedTodayCount}"
        bind.heroSub.text = tr("junk notifications blocked today")
        bind.extra.removeAllViews()
        if (!Perms.has(ctx, Perms.Kind.NOTIF_ACCESS)) {
            bind.notice(this, tr("Notification access is needed to block notifications."), tr("Allow")) {
                startActivity(Perms.settingsIntent(ctx, Perms.Kind.NOTIF_ACCESS)!!)
            }
        }
        bind.list.removeAllViews()
        bind.btnPrimary.show(showBlocked)
        if (showBlocked) {
            val log = prefs.blockedLog.reversed().map { it.split('|') }.filter { it.size >= 4 }
            bind.list.show(log.isNotEmpty())
            bind.emptyText.show(log.isEmpty())
            bind.emptyText.text = tr("All clear. New junk will be caught here.")
            for (e in log) {
                val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
                val icon = apps.firstOrNull { it.pkg == e[0] }?.icon
                if (icon != null) row.icon.appIcon(icon) else row.icon.tile(R.drawable.ic_bell, Tone.AMBER)
                row.title.text = e[1]
                row.sub.text = e[3]
                row.trail.text = agoText(e[2].toLongOrNull() ?: 0)
                row.trail.textSize = 11f
                bind.list.addView(row.root)
            }
        } else {
            bind.emptyText.show(false)
            bind.list.show(apps.isNotEmpty())
            val blocked = prefs.blockedPackages
            for (a in apps) {
                val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
                row.icon.appIcon(a.icon)
                row.title.text = a.label
                row.check.show(true)
                row.check.isChecked = a.pkg in blocked
                row.row.onClick {
                    prefs.blockedPackages = if (a.pkg in prefs.blockedPackages) prefs.blockedPackages - a.pkg else prefs.blockedPackages + a.pkg
                    row.check.isChecked = a.pkg in prefs.blockedPackages
                }
                bind.list.addView(row.root)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Duplicate contacts
// ---------------------------------------------------------------------------------------------

class ContactsFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private var groups: List<ContactGroup> = emptyList()
    private var tab = ContactGroupType.NAME

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Duplicate Contacts"))
        b.skeletonAfterSetup(true)
        b.chipScroll.show(true)
        b.heroLabel.show(false)
        b.btnPrimary.onClick { mergeAll() }
        load()
    }

    private fun load() {
        val ctx = requireContext().applicationContext
        if (!Perms.hasContacts(ctx)) {
            render(); return
        }
        ui {
            groups = withContext(Dispatchers.IO) { ContactsRepo.load(ctx) }
            render()
        }
    }

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        val ctx = requireContext()
        bind.chips.removeAllViews()
        bind.list.removeAllViews()
        bind.extra.removeAllViews()
        if (!Perms.hasContacts(ctx)) {
            bind.heroValue.text = "—"
            bind.heroSub.text = tr("Allow contacts access to find duplicates.")
            bind.btnPrimary.text = tr("Allow contacts access")
            bind.btnPrimary.onClick { permLauncher.launch(Perms.runtime(Perms.Kind.CONTACTS)) }
            bind.list.show(false)
            return
        }
        ContactGroupType.entries.forEach { t ->
            bind.chips.addView(chipView(ctx, "${tr(t.label)} (${groups.count { it.type == t }})", t == tab) { tab = t; render() })
        }
        val shown = groups.filter { it.type == tab }
        bind.heroValue.text = trf("%1\$s left", groups.size)
        bind.heroSub.text = tr("groups of duplicate or incomplete contacts")
        bind.list.show(shown.isNotEmpty())
        bind.emptyText.show(shown.isEmpty())
        bind.emptyText.text = tr("No contacts here.")
        for (g in shown) {
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.tile(R.drawable.ic_user, Tone.VIOLET)
            row.title.text = g.title
            row.sub.text = g.meta
            row.trail.text = if (g.type == ContactGroupType.EMPTY) tr("Delete") else tr("Merge")
            row.trail.setTextColor(ctx.getColor(if (g.type == ContactGroupType.EMPTY) R.color.red else R.color.brand))
            row.row.onClick { act(g) }
            bind.list.addView(row.root)
        }
        bind.btnPrimary.show(tab != ContactGroupType.EMPTY)
        bind.btnPrimary.text = trf("Merge all %1\$s", shown.size)
        bind.btnPrimary.onClick { mergeAll() }
    }

    private fun act(g: ContactGroup) {
        val ctx = requireContext().applicationContext
        if (g.type == ContactGroupType.EMPTY) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(trf("Delete %1\$s?", g.title))
                .setMessage(tr("This contact has no phone number. It will be deleted."))
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(tr("Delete")) { _, _ -> ui { withContext(Dispatchers.IO) { ContactsRepo.delete(ctx, g.contacts.first()) }; load() } }
                .show()
        } else ui {
            withContext(Dispatchers.IO) { ContactsRepo.merge(ctx, g) }
            toast(trf("Merged %1\$s", g.title))
            load()
        }
    }

    private fun mergeAll() {
        val ctx = requireContext().applicationContext
        val todo = groups.filter { it.type == tab && it.type != ContactGroupType.EMPTY }
        if (todo.isEmpty()) return
        ui {
            withContext(Dispatchers.IO) { todo.forEach { ContactsRepo.merge(ctx, it) } }
            toast(trf("Merged %1\$s groups", todo.size))
            load()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photo compressor
// ---------------------------------------------------------------------------------------------

class CompressorFragment : VBFragment<FragmentGenericBinding>(FragmentGenericBinding::inflate) {

    private class Level(val label: String, val desc: String, val quality: Int, val scale: Float, val saves: Float)

    private val levels = listOf(
        Level(tr("High quality"), tr("Looks the same, files are smaller"), 85, 1.0f, 0.45f),
        Level(tr("Balanced"), tr("Great for sharing and storage"), 70, 0.8f, 0.65f),
        Level(tr("Small file"), tr("Smallest size, some detail lost"), 55, 0.6f, 0.80f),
    )
    private var level = 1
    private var deleteOriginals = false
    private var picked: List<Photo> = emptyList()
    private var working = false
    private var pendingTrash: List<Photo> = emptyList()
    private var savedBytes = 0L
    private var madeCount = 0

    private val trashLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { finish() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.header.setup(this, tr("Photo Compressor"))
        b.heroLabel.text = tr("Selected photos")
        b.btnSecondary.show(true)
        b.btnSecondary.text = tr("Choose photos")
        b.btnSecondary.onClick { choose() }
        b.btnPrimary.onClick { compress() }
        render()
    }

    private fun estimate() = (picked.sumOf { it.size } * levels[level].saves).toLong()

    private fun render() {
        val bind = bOrNull ?: return
        bind.loading.show(false)
        bind.skeleton(false)
        bind.heroValue.text = "${picked.size}"
        bind.heroSub.text = if (picked.isEmpty()) tr("Pick photos to see how much space you can save.") else trf("%1\$s now · save about %2\$s", Fmt.size(picked.sumOf { it.size }), Fmt.size(estimate()))
        bind.list.removeAllViews()
        levels.forEachIndexed { i, l ->
            val row = ItemRowBinding.inflate(layoutInflater, bind.list, false)
            row.icon.tile(R.drawable.ic_compress, Tone.PINK)
            row.title.text = l.label
            row.sub.text = l.desc
            row.trail.text = "−${(l.saves * 100).toInt()}%"
            row.check.show(true)
            row.check.isChecked = i == level
            row.row.onClick { level = i; render() }
            bind.list.addView(row.root)
        }
        val del = ItemRowBinding.inflate(layoutInflater, bind.list, false)
        del.icon.tile(R.drawable.ic_trash, Tone.GRAY)
        del.title.text = tr("Delete originals after")
        del.sub.text = tr("Originals go to Recycle Bin")
        del.check.show(true)
        del.check.isChecked = deleteOriginals
        del.row.onClick { deleteOriginals = !deleteOriginals; render() }
        bind.list.addView(del.root)
        bind.btnPrimary.text = when {
            working -> tr("Compressing…")
            picked.isEmpty() -> tr("Choose photos first")
            else -> trf("Compress · save ~%1\$s", Fmt.size(estimate()))
        }
        bind.btnPrimary.alpha = if (picked.isEmpty() || working) 0.5f else 1f
    }

    private fun choose() {
        val ctx = requireContext()
        ui {
            val all = withContext(Dispatchers.IO) { PhotoScanner.largest(ctx.applicationContext) }
            if (all.isEmpty()) return@ui toast(tr("No photos found. Allow file access first."))
            val sel = all.filter { p -> picked.any { it.id == p.id } }.toSet()
            all.forEach { it.selected = it in sel }
            val rv = RecyclerView(ctx).apply {
                layoutManager = GridLayoutManager(ctx, 3)
                setPadding(16, 16, 16, 0)
                adapter = RowAdapter<Photo, ItemThumbBinding>(ItemThumbBinding::inflate) { t, p, _ ->
                    t.img.load(p.uri)
                    t.best.text = Fmt.size(p.size)
                    t.check.isChecked = p.selected
                    t.root.onClick { p.selected = !p.selected; t.check.isChecked = p.selected }
                }.also { it.items = all }
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(tr("Largest photos first"))
                .setView(rv)
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(tr("Select")) { _, _ -> picked = all.filter { it.selected }; render() }
                .show()
        }
    }

    private fun compress() {
        if (picked.isEmpty() || working) return
        working = true
        render()
        val ctx = requireContext().applicationContext
        val lv = levels[level]
        val todo = picked
        ui {
            val done = withContext(Dispatchers.IO) { todo.mapNotNull { p -> compressOne(ctx, p, lv)?.let { p to it } } }
            savedBytes = done.sumOf { it.first.size - it.second }
            madeCount = done.size
            working = false
            if (deleteOriginals && done.isNotEmpty()) {
                pendingTrash = done.map { it.first }
                val sender = PhotoScanner.trash(ctx, pendingTrash)
                if (sender != null) {
                    trashLauncher.launch(IntentSenderRequest.Builder(sender).build()); return@ui
                }
            }
            finish()
        }
    }

    private fun finish() {
        AppState.photos = null
        AppState.result = CleanResult(
            trf("%1\$s saved", Fmt.size(savedBytes.coerceAtLeast(0))),
            trf("%1\$s photos compressed and saved in Pictures/PhoneCleaner.", madeCount),
            listOf(
                ResultStat(Fmt.size(DeviceInfo.storage().free), tr("Free now")),
                ResultStat("$madeCount", tr("Photos compressed")),
                ResultStat(if (deleteOriginals) tr("In bin") else tr("Kept"), tr("Originals")),
            ),
        )
        go(R.id.cleanDoneFragment)
    }

    /** Returns the new file size, or null if the photo couldn't be compressed or wouldn't get smaller. */
    private fun compressOne(ctx: android.content.Context, p: Photo, lv: Level): Long? = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val target = (maxOf(bounds.outWidth, bounds.outHeight) * lv.scale).toInt().coerceAtLeast(64)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        var bmp = cr.openInputStream(p.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bmp == null) null else {
            val longest = maxOf(bmp.width, bmp.height)
            if (longest > target) {
                val f = target.toFloat() / longest
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
            }
            val rot = cr.openInputStream(p.uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rot != 0f) bmp = Bitmap.createBitmap(bmp!!, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            val out = ByteArrayOutputStream()
            bmp!!.compress(Bitmap.CompressFormat.JPEG, lv.quality, out)
            if (out.size() >= p.size) null else {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, p.name.substringBeforeLast('.') + "_compressed.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhoneCleaner")
                }
                val dest = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (dest == null) null else {
                    cr.openOutputStream(dest)?.use { it.write(out.toByteArray()) }
                    out.size().toLong()
                }
            }
        }
    } catch (_: Throwable) {
        null
    }
}
