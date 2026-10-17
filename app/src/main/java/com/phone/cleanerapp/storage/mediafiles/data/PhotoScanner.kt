package com.phone.cleanerapp.storage.mediafiles.data

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class Photo(
    val id: Long,
    val uri: Uri,
    val path: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val takenMs: Long,
) {
    var selected = false
    val name: String get() = path.substringAfterLast('/')
}

enum class PhotoGroupType(val label: String) { SIMILAR("Similar"), DUPLICATE("Duplicates"), SCREENSHOT("Screenshots") }

/** [photos] is sorted best-first; the first photo is the one we suggest keeping. */
class PhotoGroup(val type: PhotoGroupType, val title: String, val photos: List<Photo>) {
    val reclaimable get() = photos.drop(1).sumOf { it.size }
}

object PhotoScanner {

    suspend fun scan(ctx: Context): List<PhotoGroup> {
        val all = queryImages(ctx.contentResolver)
        val groups = mutableListOf<PhotoGroup>()
        val used = HashSet<Long>()

        // Exact duplicates: same byte size, then same content fingerprint.
        all.groupBy { it.size }.filter { it.value.size > 1 }.forEach { (_, same) ->
            coroutineContext.ensureActive()
            same.groupBy { fingerprint(it) }.filter { it.key != null && it.value.size > 1 }.forEach { (_, dups) ->
                val sorted = dups.sortedBy { it.takenMs }
                groups.add(PhotoGroup(PhotoGroupType.DUPLICATE, trf("%1\$s identical photos", dups.size), sorted))
                dups.forEach { used.add(it.id) }
            }
        }

        // Similar: same dimensions, taken within a few seconds of each other (burst / retakes).
        val rest = all.filter { it.id !in used && it.takenMs > 0 && it.width > 0 }
            .sortedWith(compareBy({ it.width }, { it.height }, { it.takenMs }))
        var run = mutableListOf<Photo>()
        fun flush() {
            if (run.size > 1) {
                groups.add(PhotoGroup(PhotoGroupType.SIMILAR, trf("%1\$s similar shots", run.size), run.sortedByDescending { it.size }))
                run.forEach { used.add(it.id) }
            }
            run = mutableListOf()
        }
        for (p in rest) {
            val last = run.lastOrNull()
            if (last != null && (last.width != p.width || last.height != p.height || p.takenMs - last.takenMs > 5_000)) flush()
            run.add(p)
        }
        flush()

        // Screenshots: not a "group of look-alikes", just a bucket worth reviewing.
        val shots = all.filter { it.id !in used && it.path.contains("screenshot", ignoreCase = true) }
        if (shots.isNotEmpty()) groups.add(PhotoGroup(PhotoGroupType.SCREENSHOT, trf("%1\$s screenshots", shots.size), shots.sortedBy { it.takenMs }))

        // Pre-select everything except the suggested keeper (screenshots start unselected).
        groups.forEach { g -> if (g.type != PhotoGroupType.SCREENSHOT) g.photos.drop(1).forEach { it.selected = true } }
        return groups.sortedByDescending { it.reclaimable }
    }

    /** The [n] biggest photos on the phone – the best candidates for compression. */
    fun largest(ctx: Context, n: Int = 120): List<Photo> = queryImages(ctx.contentResolver).sortedByDescending { it.size }.take(n)

    private fun queryImages(cr: ContentResolver): List<Photo> {
        val out = mutableListOf<Photo>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val taken = c.getLong(5).takeIf { it > 0 } ?: (c.getLong(6) * 1000)
                out.add(
                    Photo(
                        id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        c.getString(1) ?: "", c.getLong(2), c.getInt(3), c.getInt(4), taken,
                    )
                )
            }
        }
        return out
    }

    /** MD5 of the first and last 256 KB plus the length – cheap and collision-safe enough for same-size files. */
    private fun fingerprint(p: Photo): String? = try {
        val f = File(p.path)
        RandomAccessFile(f, "r").use { raf ->
            val md = MessageDigest.getInstance("MD5")
            val buf = ByteArray(256 * 1024)
            var n = raf.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (raf.length() > buf.size * 2) {
                raf.seek(raf.length() - buf.size)
                n = raf.read(buf)
                if (n > 0) md.update(buf, 0, n)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Moves photos to the system trash (kept 30 days) on Android 11+, which needs a user confirmation:
     * returns the IntentSender to launch. On older versions deletes directly and returns null.
     */
    fun trash(ctx: Context, photos: List<Photo>): IntentSender? {
        val uris = photos.map { it.uri }
        if (Build.VERSION.SDK_INT >= 30) {
            return MediaStore.createTrashRequest(ctx.contentResolver, uris, true).intentSender
        }
        uris.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
        return null
    }
}
