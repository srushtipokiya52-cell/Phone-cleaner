package com.phone.cleanerapp.storage.mediafiles

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.phone.cleanerapp.storage.mediafiles.core.AdBanner
import com.phone.cleanerapp.storage.mediafiles.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var ads: AdBanner
    private var topInset = 0
    private var bottomInset = 0
    private var bottomNavShown = false

    private val tabs = setOf(R.id.homeFragment, R.id.toolsFragment, R.id.storageFragment, R.id.settingsFragment)
    private val noAds = setOf(R.id.splashFragment, R.id.languageFragment, R.id.introFragment, R.id.permissionFragment, R.id.scanFragment)

    /** Colours and icon style of the status bar (top) and navigation bar (bottom) for one screen. */
    private class Bars(val top: Int, val bottom: Int, val lightStatusIcons: Boolean, val lightNavIcons: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.phone.cleanerapp.storage.mediafiles.core.Tr.refresh(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            applySpaces()
            insets
        }

        ads = AdBanner(this, b.adContainer)
        ads.start()

        val nav = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        b.bottomNav.setupWithNavController(nav)
        b.bottomNav.setOnItemReselectedListener { }
        nav.addOnDestinationChangedListener { _, dest, _ ->
            bottomNavShown = dest.id in tabs
            val vis = if (bottomNavShown) View.VISIBLE else View.GONE
            b.bottomNav.visibility = vis
            b.navDivider.visibility = vis
            val adShown = dest.id !in noAds
            b.adContainer.visibility = if (adShown) View.VISIBLE else View.GONE
            apply(barsFor(dest.id, adShown))
            applySpaces()
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun barsFor(dest: Int, adShown: Boolean): Bars = when (dest) {
        // Dark green splash: white icons on both bars.
        R.id.splashFragment -> Bars(color(R.color.brand_dark), color(R.color.brand_dark), lightStatusIcons = false, lightNavIcons = false)
        // Intro: coloured top (the page sets the exact colour), white sheet at the bottom.
        R.id.introFragment, R.id.permissionFragment -> Bars(color(R.color.brand_dark), color(R.color.white), lightStatusIcons = false, lightNavIcons = true)
        R.id.languageFragment -> Bars(color(R.color.white), color(R.color.white), lightStatusIcons = true, lightNavIcons = true)
        else -> Bars(color(R.color.bg), if (adShown) color(R.color.card) else color(R.color.bg), lightStatusIcons = true, lightNavIcons = true)
    }

    private fun apply(bars: Bars) {
        b.root.setBackgroundColor(bars.bottom)
        setStatusColor(bars.top)
        b.navSpace.setBackgroundColor(bars.bottom)
        val c = WindowCompat.getInsetsController(window, b.root)
        c.isAppearanceLightStatusBars = bars.lightStatusIcons
        c.isAppearanceLightNavigationBars = bars.lightNavIcons
    }

    /** Lets a screen (the intro pages) tint the status bar area to match its own header. */
    fun setStatusColor(color: Int) = b.statusSpace.setBackgroundColor(color)

    private fun applySpaces() {
        b.statusSpace.layoutParams = b.statusSpace.layoutParams.also { it.height = topInset }
        // The bottom navigation view pads for the gesture bar itself; otherwise reserve the space here.
        b.navSpace.layoutParams = b.navSpace.layoutParams.also { it.height = if (bottomNavShown) 0 else bottomInset }
    }

    override fun onPause() {
        ads.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        ads.resume()
    }

    override fun onDestroy() {
        ads.destroy()
        super.onDestroy()
    }
}
