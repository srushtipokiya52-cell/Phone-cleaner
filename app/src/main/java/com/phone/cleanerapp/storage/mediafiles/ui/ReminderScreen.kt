package com.phone.cleanerapp.storage.mediafiles.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.phone.cleanerapp.storage.mediafiles.R
import com.phone.cleanerapp.storage.mediafiles.core.back
import com.phone.cleanerapp.storage.mediafiles.core.onClick
import com.phone.cleanerapp.storage.mediafiles.core.toast
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf
import com.phone.cleanerapp.storage.mediafiles.databinding.FragmentReminderBinding
import com.phone.cleanerapp.storage.mediafiles.feature.charging.ChargingFeature
import com.phone.cleanerapp.storage.mediafiles.feature.reminder.Reminder
import com.phone.cleanerapp.storage.mediafiles.feature.reminder.ReminderKind
import com.phone.cleanerapp.storage.mediafiles.feature.reminder.ReminderSettings
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

/** Lets the user pick when (time and weekdays) and what the daily cleaning reminder is about. */
class ReminderFragment : VBFragment<FragmentReminderBinding>(FragmentReminderBinding::inflate) {

    private lateinit var draft: ReminderSettings

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && bOrNull != null) {
            draft = draft.copy(enabled = false)
            render()
            toast(tr("Allow notifications to get reminders"))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        draft = Reminder.load(requireContext())
        b.header.setup(this, tr("Cleaning reminder"), tr("Get a nudge to keep your phone fast"))
        b.tvSwitchTitle.text = tr("Remind me")
        b.tvTimeSection.text = tr("Time")
        b.tvTimeHint.text = tr("Tap to change")
        b.tvDaysSection.text = tr("Repeat on")
        b.tvKindSection.text = tr("Remind me to")
        b.tvPreviewSection.text = tr("Preview")
        b.ivPreviewIcon.tile(R.drawable.ic_clean, Tone.GREEN)
        b.btnSave.text = tr("Save reminder")

        b.cardTime.onClick { pickTime() }
        b.switchReminder.setOnCheckedChangeListener { _, on ->
            if (on == draft.enabled) return@setOnCheckedChangeListener
            draft = draft.copy(enabled = on)
            if (on && !ChargingFeature.canPostNotifications(requireContext()) && Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            render()
        }
        b.btnSave.onClick { save() }
        render()
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(draft.hour)
            .setMinute(draft.minute)
            .setTitleText(tr("Reminder time"))
            .build()
        picker.addOnPositiveButtonClickListener {
            draft = draft.copy(hour = picker.hour, minute = picker.minute)
            render()
        }
        picker.show(childFragmentManager, "time")
    }

    private fun save() {
        val ctx = requireContext()
        if (draft.enabled && draft.days == 0) {
            toast(tr("Pick at least one day"))
            return
        }
        Reminder.save(ctx, draft)
        val next = Reminder.nextTrigger(Reminder.load(ctx))
        toast(if (next != null) trf("Reminder set for %1\$s", describe(next)) else tr("Reminder is off"))
        back()
    }

    private fun render() {
        val bind = bOrNull ?: return
        val ctx = requireContext()
        bind.switchReminder.isChecked = draft.enabled
        bind.tvTime.text = timeText(draft.hour, draft.minute)

        val next = Reminder.nextTrigger(draft)
        bind.tvSwitchTitle.text = tr(if (draft.enabled) "Reminder is on" else "Reminder is off")
        bind.tvNext.text = when {
            next != null -> trf("Next: %1\$s", describe(next))
            draft.enabled -> tr("Pick at least one day")
            else -> tr("Turn on to get a daily nudge")
        }

        // Weekday chips, Monday first
        val names = DateFormatSymbols.getInstance().shortWeekdays
        bind.days.removeAllViews()
        for (dow in listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)) {
            bind.days.addView(chipView(ctx, names[dow], draft.hasDay(dow)) {
                draft = draft.copy(days = draft.days xor (1 shl (dow - 1)))
                render()
            })
        }

        bind.kinds.removeAllViews()
        for (kind in ReminderKind.entries) {
            bind.kinds.addView(chipView(ctx, tr(kind.label), draft.kind == kind) {
                draft = draft.copy(kind = kind)
                render()
            })
        }
        bind.tvPreviewTitle.text = tr(draft.kind.title)
        bind.tvPreviewText.text = tr(draft.kind.text)

        val blocked = draft.enabled && !ChargingFeature.canPostNotifications(ctx)
        bind.tvNotifWarning.show(blocked)
        bind.tvNotifWarning.text = tr("Notifications are off for this app, so the reminder cannot show. Turn them on in Settings.")
    }

    private fun timeText(hour: Int, minute: Int): String {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
        return DateFormat.getTimeFormat(requireContext()).format(Date(c.timeInMillis))
    }

    /** "Today, 8:00 PM" / "Tomorrow, ..." / "Wed, ...". */
    private fun describe(c: Calendar): String {
        val today = Calendar.getInstance()
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val day = when {
            same(c, today) -> tr("Today")
            same(c, tomorrow) -> tr("Tomorrow")
            else -> DateFormatSymbols.getInstance().shortWeekdays[c.get(Calendar.DAY_OF_WEEK)]
        }
        return "$day, ${timeText(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))}"
    }
}
