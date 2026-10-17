package com.phone.cleanerapp.storage.mediafiles.data

/** One-line stat shown on the "done" screen. */
class ResultStat(val value: String, val label: String)

class CleanResult(val headline: String, val sub: String, val stats: List<ResultStat>)

/** In-memory results shared between screens (scan → results → done) and used by Home for summaries. */
object AppState {
    @Volatile var junk: List<JunkCategory>? = null
    @Volatile var photos: List<PhotoGroup>? = null
    @Volatile var largeFiles: List<FileItem>? = null
    @Volatile var chatTotal: Long? = null
    @Volatile var appCount: Int? = null
    @Volatile var unusedApps: Int? = null
    @Volatile var result: CleanResult? = null

    val junkBytes: Long get() = junk?.sumOf { it.bytes } ?: 0L
}
