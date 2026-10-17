package com.phone.cleanerapp.storage.mediafiles.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Perms
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.enterApp
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentPermissionBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ViewBulletBinding

class PermissionFragment : VBFragment<FragmentPermissionBinding>(FragmentPermissionBinding::inflate) {

    private var asked = false
    private var done = false

    private val runtimeLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { check() }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { check() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val bullets = listOf(
            R.drawable.ic_clean to "Find junk, cache and leftover files",
            R.drawable.ic_file to "Spot large and duplicate files",
            R.drawable.ic_lock to "Your files never leave your phone",
        )
        val tones = listOf(Tone.GREEN, Tone.AMBER, Tone.CYAN)
        bullets.forEachIndexed { i, (icon, text) ->
            val row = ViewBulletBinding.inflate(layoutInflater, b.bullets, false)
            row.icon.tile(icon, tones[i])
            row.text.text = text
            b.bullets.addView(row.root)
        }
        b.btnContinue.onClick { request() }
        b.btnLater.onClick { finish() }
        // Coming back from Settings: if access was granted, go straight into the app.
        onEachResume { check() }
    }

    private fun check() {
        if (bOrNull == null) return
        val granted = Perms.hasFiles(requireContext())
        if (granted && (asked || !Prefs.get(requireContext()).onboarded)) finish()
    }

    private fun finish() {
        if (done || !isAdded) return
        done = true
        if (Prefs.get(requireContext()).onboarded) back() else enterApp()
    }

    private fun request() {
        val ctx = requireContext()
        if (Perms.hasFiles(ctx)) return finish()
        asked = true
        val runtime = Perms.runtime(Perms.Kind.FILES)
        if (runtime.isNotEmpty()) {
            runtimeLauncher.launch(runtime)
            return
        }
        val intent = Perms.settingsIntent(ctx, Perms.Kind.FILES) ?: return
        try {
            settingsLauncher.launch(intent)
        } catch (_: Throwable) {
            try {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Throwable) {
                settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }
        }
    }
}
