package com.phone.cleanerapp.storage.mediafiles.data

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

object Fs {
    fun root(): File = Environment.getExternalStorageDirectory()

    fun sizeOf(f: File): Long {
        if (f.isFile) return f.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(f)
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (k in kids) if (k.isDirectory) stack.add(k) else total += k.length()
        }
        return total
    }

    fun deleteRecursively(f: File): Boolean = try {
        f.deleteRecursively()
    } catch (_: Throwable) {
        false
    }

    /**
     * Iterative walk. [onDir] gets each directory with its children (return false to skip descending),
     * [onFile] gets each regular file. Never follows symlinks, never enters Android/data or Android/obb
     * (not readable on modern Android anyway).
     */
    suspend fun walk(
        start: File,
        onDir: (File, Array<File>) -> Boolean = { _, _ -> true },
        onFile: (File) -> Unit,
    ) {
        val stack = ArrayDeque<File>()
        stack.add(start)
        var n = 0
        while (stack.isNotEmpty()) {
            if (++n % 64 == 0) coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            if (!onDir(dir, kids)) continue
            for (k in kids) {
                try {
                    if (java.nio.file.Files.isSymbolicLink(k.toPath())) continue
                } catch (_: Throwable) {
                }
                if (k.isDirectory) {
                    if (isProtected(k)) continue
                    stack.add(k)
                } else onFile(k)
            }
        }
    }

    private fun isProtected(d: File): Boolean {
        val p = d.path
        val androidDir = File(root(), "Android").path
        return p == "$androidDir/data" || p == "$androidDir/obb"
    }
}

enum class FileKind(val label: String) { VIDEO("Videos"), AUDIO("Audio"), DOC("Documents"), ARCHIVE("Archives"), IMAGE("Images"), OTHER("Other");

    companion object {
        private val video = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v")
        private val audio = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "amr")
        private val doc = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv")
        private val archive = setOf("zip", "rar", "7z", "tar", "gz", "apk", "xapk", "iso", "obb")
        private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")
        fun of(f: File): FileKind {
            val e = f.extension.lowercase()
            return when (e) {
                in video -> VIDEO
                in audio -> AUDIO
                in doc -> DOC
                in archive -> ARCHIVE
                in image -> IMAGE
                else -> OTHER
            }
        }
    }
}

class FileItem(val file: File, val size: Long, val kind: FileKind = FileKind.of(file), val note: String = "") {
    var selected = false
}

// ---------------------------------------------------------------------------------------------
// Junk
// ---------------------------------------------------------------------------------------------

enum class JunkType(val title: String, val desc: String) {
    CACHE("App cache", "Temporary data apps rebuild on their own"),
    RESIDUAL("Residual files", "Logs, backups and thumbnails nobody uses"),
    APK("Obsolete APKs", "Installers you've already used"),
    TEMP("Temporary files", "Leftovers from downloads and editing"),
    EMPTY("Empty folders", "Folders with nothing inside"),
}

class JunkCategory(val type: JunkType) {
    val files = mutableListOf<File>()
    var bytes = 0L
    var selected = true
    var scanned = false
}

class JunkProgress(val fraction: Float, val currentPath: String, val foundBytes: Long, val categories: List<JunkCategory>)

class CleanStats(val bytes: Long, val files: Int, val folders: Int)

object JunkScanner {

    private val residualExt = setOf("log", "bak", "old", "dmp", "thumb", "thumbdata", "chk")
    private val tempExt = setOf("tmp", "temp", "cache", "crdownload", "part", "download", "partial")
    private val protectedTop = setOf(
        "android", "dcim", "pictures", "download", "downloads", "documents", "movies", "music", "alarms",
        "audiobooks", "notifications", "podcasts", "ringtones", "recordings"
    )

    suspend fun scan(ctx: Context, onProgress: (JunkProgress) -> Unit): List<JunkCategory> {
        val cats = JunkType.entries.map { JunkCategory(it) }
        val by = cats.associateBy { it.type }
        val ignored = com.phone.cleanerapp.storage.mediafiles.core.Prefs.get(ctx).ignoredFolders
        fun found() = cats.sumOf { it.bytes }
        fun add(t: JunkType, f: File, size: Long) {
            by.getValue(t).files.add(f); by.getValue(t).bytes += size
        }

        // 1) App cache: this app, plus any other app cache directory the system lets us list.
        val cacheDirs = mutableListOf<File>()
        ctx.cacheDir?.let { cacheDirs.add(it) }
        ctx.externalCacheDirs.filterNotNull().forEach { cacheDirs.add(it) }
        File(Fs.root(), "Android/data").listFiles()?.forEach { app ->
            val c = File(app, "cache")
            if (c.isDirectory && app.name != ctx.packageName) cacheDirs.add(c)
        }
        File(Fs.root(), "DCIM/.thumbnails").takeIf { it.isDirectory }?.let { cacheDirs.add(it) }
        for (dir in cacheDirs) {
            coroutineContext.ensureActive()
            dir.listFiles()?.forEach { add(JunkType.CACHE, it, Fs.sizeOf(it)) }
        }
        by.getValue(JunkType.CACHE).scanned = true
        onProgress(JunkProgress(0.1f, tr("App cache"), found(), cats))

        // 2) Storage walk, one top-level folder at a time so progress is real.
        val tops = Fs.root().listFiles()?.sortedBy { it.name } ?: emptyList()
        for ((idx, top) in tops.withIndex()) {
            coroutineContext.ensureActive()
            if (top.path in ignored) continue
            onProgress(JunkProgress(0.1f + 0.9f * idx / tops.size.coerceAtLeast(1), top.path, found(), cats))
            if (top.isFile) {
                classifyFile(top, ::add); continue
            }
            if (top.name.equals("Android", true)) continue
            if (top.name.equals("LOST.DIR", true)) {
                top.listFiles()?.forEach { add(JunkType.RESIDUAL, it, Fs.sizeOf(it)) }; continue
            }
            if (top.name == ".thumbnails") {
                add(JunkType.RESIDUAL, top, Fs.sizeOf(top)); continue
            }
            Fs.walk(
                top,
                onDir = { d, kids ->
                    if (d.path in ignored) false
                    else {
                        if (kids.isEmpty() && !(d.parentFile == Fs.root() && d.name.lowercase() in protectedTop)) {
                            add(JunkType.EMPTY, d, 0)
                        }
                        true
                    }
                },
                onFile = { classifyFile(it, ::add) },
            )
        }
        cats.forEach { it.scanned = true }
        onProgress(JunkProgress(1f, "", found(), cats))
        return cats
    }

    private fun classifyFile(f: File, add: (JunkType, File, Long) -> Unit) {
        val ext = f.extension.lowercase()
        val len = f.length()
        when {
            ext == "apk" -> add(JunkType.APK, f, len)
            ext in tempExt || f.name.endsWith("~") -> add(JunkType.TEMP, f, len)
            ext in residualExt || f.name.startsWith(".thumbdata") -> add(JunkType.RESIDUAL, f, len)
        }
    }

    /** Deletes the selected categories. Returns what was actually removed. */
    fun clean(cats: List<JunkCategory>): CleanStats {
        var bytes = 0L
        var files = 0
        var folders = 0
        for (c in cats.filter { it.selected }) {
            for (f in c.files) {
                if (!f.exists()) continue
                if (c.type == JunkType.EMPTY) {
                    if (f.isDirectory && f.listFiles()?.isEmpty() == true && f.delete()) folders++
                } else {
                    val size = Fs.sizeOf(f)
                    val count = if (f.isDirectory) countFiles(f) else 1
                    if (Fs.deleteRecursively(f)) {
                        bytes += size; files += count
                    }
                }
            }
        }
        return CleanStats(bytes, files, folders)
    }

    private fun countFiles(d: File): Int {
        var n = 0
        val stack = ArrayDeque<File>()
        stack.add(d)
        while (stack.isNotEmpty()) {
            stack.removeLast().listFiles()?.forEach { if (it.isDirectory) stack.add(it) else n++ }
        }
        return n
    }

}

// ---------------------------------------------------------------------------------------------
// Large files and chat media
// ---------------------------------------------------------------------------------------------

object LargeFileScanner {
    suspend fun scan(ctx: Context, minBytes: Long): List<FileItem> {
        val out = mutableListOf<FileItem>()
        val ignored = com.phone.cleanerapp.storage.mediafiles.core.Prefs.get(ctx).ignoredFolders
        Fs.walk(
            Fs.root(),
            onDir = { d, _ -> d.path !in ignored },
            onFile = { if (it.length() >= minBytes) out.add(FileItem(it, it.length())) },
        )
        return out.sortedByDescending { it.size }
    }

    /** Biggest files of one kind (used by the Storage screen's Videos / Audio / Documents / Other rows). */
    suspend fun scanKind(ctx: Context, kind: FileKind, minBytes: Long, limit: Int = 300): List<FileItem> {
        val out = mutableListOf<FileItem>()
        val ignored = com.phone.cleanerapp.storage.mediafiles.core.Prefs.get(ctx).ignoredFolders
        Fs.walk(
            Fs.root(),
            onDir = { d, _ -> d.path !in ignored },
            onFile = { if (it.length() >= minBytes && FileKind.of(it) == kind) out.add(FileItem(it, it.length())) },
        )
        return out.sortedByDescending { it.size }.take(limit)
    }
}

class ChatCategory(val name: String, val files: List<FileItem>) {
    val bytes get() = files.sumOf { it.size }
}

class ChatApp(val id: String, val label: String, private val roots: List<String>) {
    fun categories(): List<ChatCategory> {
        val out = linkedMapOf<String, MutableList<FileItem>>()
        for (r in roots) {
            val media = File(Fs.root(), r)
            media.listFiles()?.filter { it.isDirectory }?.forEach { cat ->
                val name = cat.name.removePrefix("WhatsApp ").removePrefix("Telegram ").trim()
                val list = out.getOrPut(name) { mutableListOf() }
                val stack = ArrayDeque<File>()
                stack.add(cat)
                while (stack.isNotEmpty()) {
                    stack.removeLast().listFiles()?.forEach { f ->
                        if (f.isDirectory) stack.add(f)
                        else if (f.name != ".nomedia") list.add(FileItem(f, f.length(), note = if (f.parentFile?.name == "Sent") "Sent" else ""))
                    }
                }
            }
        }
        return out.filter { it.value.isNotEmpty() }.map { ChatCategory(it.key, it.value.sortedByDescending { f -> f.size }) }
            .sortedByDescending { it.bytes }
    }

    companion object {
        val all = listOf(
            ChatApp("wa", "WhatsApp", listOf("Android/media/com.whatsapp/WhatsApp/Media", "WhatsApp/Media")),
            ChatApp("wab", "WhatsApp Business", listOf("Android/media/com.whatsapp.w4b/WhatsApp Business/Media", "WhatsApp Business/Media")),
            ChatApp("tg", "Telegram", listOf("Android/media/org.telegram.messenger/Telegram", "Telegram")),
        )
    }
}
