package com.phone.cleanerapp.storage.mediafiles.feature.overlay

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.databinding.DialogOverlayPermissionBinding

/**
 * "Display over other apps" bottom sheet. "Open Settings" goes to the system page and brings
 * [activity] back by itself once granted ([OverlayPermission.openSettings]); "Maybe later", back
 * and tapping the scrim all decline.
 */
class OverlayPermissionSheet(
    private val activity: Activity,
    private val source: String,
    private val onGranted: () -> Unit = {},
    private val onDeclined: () -> Unit = {}
) {

    private val binding = DialogOverlayPermissionBinding.inflate(activity.layoutInflater)
    private val dialog = Dialog(activity)
    private var handled = false

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // The scrim and the sheet run under the status and nav bars; the sheet pads itself
            WindowCompat.setDecorFitsSystemWindows(this, false)
            @Suppress("DEPRECATION")
            statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            setWindowAnimations(com.google.android.material.R.style.Animation_Design_BottomSheetDialog)
        }
        val sheet = binding.sheetOverlayPermission
        val basePaddingBottom = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOverlayPermission) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            sheet.updatePadding(bottom = basePaddingBottom + bars.bottom)
            insets
        }

        val density = activity.resources.displayMetrics.density
        binding.layoutOverlayPreview.clipToOutline = true
        binding.layoutOverlayPreview.outlineProvider = RoundedOutline(22 * density)

        val appName = activity.getString(R.string.app_name).trim()
        binding.tvOverlayPreview.text = tr("Battery 78% · Charging")
        binding.tvOverlayTitle.text = tr("Show screens over other apps")
        binding.tvOverlayBody.text = tr("Allow \"Display over other apps\" so the charging and after-call screens can open by themselves, even while you use another app.")
        binding.tvOverlayStep1.text = html(String.format(tr("Find <b>%1\$s</b> in the list"), appName))
        binding.tvOverlayStep2.text = html(tr("Turn on <b>Allow display over other apps</b>"))
        binding.tvOverlayToggle.text = tr("ON")
        binding.btnOverlayOpenSettings.text = tr("Open Settings")
        binding.btnOverlayMaybeLater.text = tr("Maybe later")

        binding.btnOverlayOpenSettings.setOnClickListener {
            handled = true
            dialog.dismiss()
            OverlayPermission.openSettings(activity, source, onGranted)
        }
        binding.btnOverlayMaybeLater.setOnClickListener { dialog.dismiss() }
        binding.rootOverlayPermission.setOnClickListener { dialog.cancel() }
        dialog.setOnDismissListener {
            if (!handled) {
                OverlayPermission.track("overlay_not_now", source, activity)
                onDeclined()
            }
        }

        OverlayPermission.track("overlay_prompt_shown", source, activity)
        dialog.show()
    }

    private fun html(text: String) = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)

    private class RoundedOutline(private val radius: Float) : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
}
