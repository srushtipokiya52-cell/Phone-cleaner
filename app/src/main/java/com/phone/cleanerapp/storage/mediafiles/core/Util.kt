package com.phone.cleanerapp.storage.mediafiles.core

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import java.util.Locale

object Fmt {
    /** "13.8 GB", "420 MB", "12 KB". */
    fun size(bytes: Long): String {
        val b = bytes.coerceAtLeast(0).toDouble()
        return when {
            b >= GB -> String.format(Locale.US, "%.1f GB", b / GB)
            b >= MB -> String.format(Locale.US, "%.0f MB", b / MB)
            b >= KB -> String.format(Locale.US, "%.0f KB", b / KB)
            else -> "${b.toLong()} B"
        }
    }

    /** Number part and unit part separately, for big headline numbers. */
    fun sizeParts(bytes: Long): Pair<String, String> {
        val s = size(bytes)
        val i = s.indexOf(' ')
        return s.substring(0, i) to s.substring(i + 1)
    }

    fun gb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / GB)

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Fragment.toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

fun Fragment.go(dest: Int, args: Bundle? = null) = findNavController().navigate(dest, args)

fun Fragment.back() {
    findNavController().popBackStack()
}

/** Pop everything above Home (keeping Home) – used after a clean finishes. */
fun Fragment.goHome() {
    val nav = findNavController()
    if (!nav.popBackStack(com.phone.cleanerapp.storage.mediafiles.R.id.homeFragment, false)) {
        nav.navigate(com.phone.cleanerapp.storage.mediafiles.R.id.homeFragment, null, navOptions {
            popUpTo(nav.graph.id) { inclusive = true }
        })
    }
}

/** Leave onboarding for good: Home becomes the root. */
fun Fragment.enterApp() {
    Prefs.get(requireContext()).onboarded = true
    val nav = findNavController()
    nav.navigate(com.phone.cleanerapp.storage.mediafiles.R.id.homeFragment, null, navOptions {
        popUpTo(nav.graph.id) { inclusive = true }
    })
}

fun View.onClick(block: () -> Unit) = setOnClickListener { block() }
