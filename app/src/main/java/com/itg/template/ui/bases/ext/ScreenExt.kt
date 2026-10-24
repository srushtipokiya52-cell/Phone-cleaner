package com.itg.template.ui.bases.ext

import android.content.Context
import android.util.DisplayMetrics
import androidx.activity.enableEdgeToEdge

fun Number.dpToPx(context: Context): Float {
    return this.toFloat() * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}

fun Number.pxToDp(context: Context): Float {
    return this.toFloat() / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}

fun Context.getWidthScreenPx(): Int {
    return resources.displayMetrics.widthPixels
}

fun Context.getHeightScreenPx(): Int {
    return resources.displayMetrics.heightPixels
}

fun Context.isNetwork(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Draws behind transparent status/nav bars (also below Android 15, where it is not enforced). */
fun androidx.activity.ComponentActivity.enableTransparentEdgeToEdge(lightBackground: Boolean) {
    val style = if (lightBackground) {
        androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    } else {
        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

/** Hides the navigation bar; a swipe from the edge shows it temporarily. */
fun android.app.Activity.hideNavigationBar() {
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
