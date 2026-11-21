package com.phone.cleanerapp.storage.mediafiles.ui

import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.phone.cleanerapp.storage.mediafiles.feature.callend.CallEndFeature
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermission
import com.phone.cleanerapp.storage.mediafiles.feature.overlay.OverlayPermissionSheet
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phone.cleanerapp.storage.mediafiles.BuildConfig
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.Prefs
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.dp
import com.phone.cleanerapp.storage.mediafiles.core.go
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.data.Fs
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentSettingsBinding
import com.phone.cleanerapp.storage.mediafiles.databinding.ItemSettingBinding
import com.phone.cleanerapp.storage.mediafiles.service.MaintenanceWorker

class SettingsFragment : VBFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onEachResume { build() }
    }

    private fun build() {
        val bind = bOrNull ?: return
        // Keep the title (first child); rebuild the sections below them.
        while (bind.container.childCount > 1) bind.container.removeViewAt(1)
        val ctx = requireContext()
        val p = Prefs.get(ctx)
        val lang = LANGUAGES.firstOrNull { it.tag == p.language }?.name ?: "English"

        section(tr("Cleaning")) {
            toggle(R.drawable.ic_clean, Tone.GREEN, tr("Auto-clean"), if (p.autoClean) tr("On · weekly") else tr("Off"), p.autoClean) { on ->
                p.autoClean = on
                MaintenanceWorker.schedule(ctx, on || p.junkReminder)
                build()
            }
            toggle(R.drawable.ic_bell, Tone.AMBER, tr("Junk reminders"), tr("Notify when junk is over 1 GB"), p.junkReminder) { on ->
                p.junkReminder = on
                MaintenanceWorker.schedule(ctx, on || p.autoClean)
            }
            item(R.drawable.ic_clock, Tone.PINK, tr("Cleaning reminder"), reminderSummary(p), tr("Set")) { go(R.id.reminderFragment) }
            item(R.drawable.ic_file, Tone.CYAN, tr("Large file size"), trf("Over %1\$s MB", p.largeFileMb), tr("Change")) { pickSize() }
            item(R.drawable.ic_folder, Tone.VIOLET, tr("Ignore list"), trf("%1\$s folders", p.ignoredFolders.size), tr("Edit")) { editIgnored() }
            item(R.drawable.ic_trash, Tone.GRAY, tr("Recycle Bin"), tr("Keep 30 days"), "") {
                toast(tr("Deleted photos stay in your phone's Recycle Bin for 30 days"))
            }
        }
        section(tr("Charging & call screens")) {
            toggle(R.drawable.ic_battery, Tone.GREEN, tr("Charging screen"), tr("Show battery status when you plug in"), ChargingFeature.isEnabled(ctx)) { on ->
                ChargingFeature.setEnabled(ctx, on)
                build()
            }
            toggle(R.drawable.ic_chat, Tone.CYAN, tr("After-call screen"), tr("Quick actions after a phone call"), CallEndFeature.isUserEnabled(ctx)) { on ->
                CallEndFeature.setEnabled(ctx, on)
                build()
            }
            val overlayOk = OverlayPermission.isGranted(ctx)
            item(R.drawable.ic_shield, Tone.AMBER, tr("Display over other apps"),
                if (overlayOk) tr("Allowed · screens open by themselves") else tr("Needed to open screens while you use your phone"),
                if (overlayOk) tr("On") else tr("Allow")) {
                if (overlayOk) openAppSettings() else OverlayPermissionSheet(requireActivity(), OverlayPermission.SOURCE_SETTINGS) { if (isAdded) build() }.show()
            }
            val notifOk = ChargingFeature.canPostNotifications(ctx)
            item(R.drawable.ic_bell, Tone.PINK, tr("Notifications"),
                if (notifOk) tr("Allowed") else tr("Needed for alerts when the screen cannot open directly"),
                if (notifOk) tr("On") else tr("Allow")) { askNotifications() }
            if (Build.VERSION.SDK_INT >= 34) {
                val fullOk = ChargingFeature.canUseFullScreenIntent(ctx)
                item(R.drawable.ic_bolt, Tone.VIOLET, tr("Full-screen alerts"),
                    if (fullOk) tr("Allowed · wakes your locked phone") else tr("Needed to show screens on a locked phone"),
                    if (fullOk) tr("On") else tr("Allow")) { openFullScreenSettings() }
            }
            val batteryOk = ChargingFeature.isIgnoringBatteryOptimizations(ctx)
            item(R.drawable.ic_clock, Tone.CYAN, tr("Run in background"),
                if (batteryOk) tr("Unrestricted · works after the app is closed") else tr("Stops the system from pausing the screens"),
                if (batteryOk) tr("On") else tr("Allow")) { if (batteryOk) openAppSettings() else askBatteryOptimization() }
        }
        section(tr("General")) {
            item(R.drawable.ic_globe, Tone.CYAN, tr("Language"), lang, tr("Change")) { go(R.id.languageFragment) }
            item(R.drawable.ic_shield, Tone.GREEN, tr("Permissions"), tr("Review what the app can access"), tr("Manage")) { go(R.id.permissionFragment) }
        }
        section(tr("About")) {
            item(R.drawable.ic_star, Tone.AMBER, tr("Rate us"), "", "") { rate() }
            item(R.drawable.ic_share, Tone.PINK, tr("Share app"), "", "") { share() }
            item(R.drawable.ic_lock, Tone.GRAY, tr("Privacy policy"), "", "") {
                if (PRIVACY_URL.isBlank()) toast(tr("Privacy policy link is not set")) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            }
            item(R.drawable.ic_file, Tone.GRAY, tr("Version"), "v${BuildConfig.VERSION_NAME}", "") { }
        }
    }


    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied twice (or "don't ask again"): the system dialog no longer shows, so go to Settings
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) openNotificationSettings()
        if (isAdded) build()
    }

    private fun askNotifications() {
        val ctx = requireContext()
        when {
            ChargingFeature.canPostNotifications(ctx) -> openNotificationSettings()
            Build.VERSION.SDK_INT >= 33 -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> openNotificationSettings()
        }
    }

    private fun openNotificationSettings() = launchSafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
    )

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            launchSafely(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${requireContext().packageName}")))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryOptimization() {
        val pkg = Uri.parse("package:${requireContext().packageName}")
        // The direct dialog first; the full list if this build drops it
        if (!launchSafely(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))) {
            launchSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAppSettings() {
        launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
    }

    private fun launchSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    private fun section(title: String, rows: SectionBuilder.() -> Unit) {
        val ctx = requireContext()
        b.container.addView(TextView(ctx).apply {
            text = title
            setTextAppearance(R.style.Txt_Section)
            setPadding(0, ctx.dp(24), 0, ctx.dp(10))
        })
        val card = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_lg)
            setPadding(ctx.dp(16), ctx.dp(6), ctx.dp(16), ctx.dp(6))
        }
        SectionBuilder(card).rows()
        b.container.addView(card)
    }

    private inner class SectionBuilder(val card: android.widget.LinearLayout) {
        fun item(icon: Int, tone: Tone, title: String, sub: String, trail: String, click: () -> Unit) {
            val r = ItemSettingBinding.inflate(layoutInflater, card, false)
            r.icon.tile(icon, tone)
            r.title.text = title
            r.sub.text = sub
            r.sub.show(sub.isNotEmpty())
            r.trail.text = trail
            r.row.onClick(click)
            card.addView(r.root)
        }

        fun toggle(icon: Int, tone: Tone, title: String, sub: String, on: Boolean, change: (Boolean) -> Unit) {
            val r = ItemSettingBinding.inflate(layoutInflater, card, false)
            r.icon.tile(icon, tone)
            r.title.text = title
            r.sub.text = sub
            r.trail.show(false)
            r.toggle.show(true)
            r.toggle.isChecked = on
            r.toggle.setOnCheckedChangeListener { _, v -> change(v) }
            r.row.onClick { r.toggle.toggle() }
            card.addView(r.root)
        }
    }

    private fun reminderSummary(p: Prefs): String {
        val r = com.phone.cleanerapp.storage.mediafiles.feature.reminder.Reminder.load(requireContext())
        if (!r.enabled) return tr("Off")
        val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, r.hour); set(java.util.Calendar.MINUTE, r.minute) }
        return trf("Daily at %1\$s", android.text.format.DateFormat.getTimeFormat(requireContext()).format(c.time))
    }

    private fun pickSize() {
        val opts = listOf(50, 100, 250, 500)
        val p = Prefs.get(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(tr("Large file size"))
            .setSingleChoiceItems(opts.map { trf("Over %1\$s MB", it) }.toTypedArray(), opts.indexOf(p.largeFileMb)) { d, i ->
                p.largeFileMb = opts[i]; d.dismiss(); build()
            }
            .show()
    }

    private fun editIgnored() {
        val p = Prefs.get(requireContext())
        val tops = (Fs.root().listFiles()?.filter { it.isDirectory && it.name != "Android" } ?: emptyList()).sortedBy { it.name }
        val checked = BooleanArray(tops.size) { tops[it].path in p.ignoredFolders }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(tr("Skip these folders when scanning"))
            .setMultiChoiceItems(tops.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setNegativeButton(tr("Cancel"), null)
            .setPositiveButton(tr("Save")) { _, _ ->
                p.ignoredFolders = tops.filterIndexed { i, _ -> checked[i] }.map { it.path }.toSet(); build()
            }
            .show()
    }

    private fun rate() {
        val pkg = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Throwable) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun share() {
        val pkg = requireContext().packageName
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Try Phone Cleaner: https://play.google.com/store/apps/details?id=$pkg"),
                tr("Share app"),
            )
        )
    }

    companion object {
        /** Put your hosted privacy policy URL here before release. */
        const val PRIVACY_URL = ""
    }
}
