package com.phone.cleanerapp.storage.mediafiles.feature.callend

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.phone.cleanerapp.storage.mediafiles.MainActivity
import com.phone.cleanerapp.storage.mediafiles.core.ScreenAds
import com.phone.cleanerapp.storage.mediafiles.core.ScreenConfig
import com.phone.cleanerapp.storage.mediafiles.core.Tr
import com.phone.cleanerapp.storage.mediafiles.core.tr
import com.phone.cleanerapp.storage.mediafiles.core.trf
import com.phone.cleanerapp.storage.mediafiles.databinding.ActivityCallEndBinding
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * After-call screen, opened by [CallEndFeature] when a phone call ends. No phone permission is
 * used, so it shows the call's duration, direction and time but no contact. The tiles open the
 * system dialer / messages / new-contact screens (the user picks the person there); the tip card
 * opens the cleaner.
 */
class CallEndActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallEnd"
        private const val EXTRA_DURATION_MS = "duration_ms"
        private const val EXTRA_INCOMING = "incoming"
        private const val EXTRA_ENDED_AT = "ended_at"

        fun intent(context: Context, call: CallInfo): Intent =
            Intent(context, CallEndActivity::class.java)
                .putExtra(EXTRA_DURATION_MS, call.durationMs)
                .putExtra(EXTRA_INCOMING, call.incoming)
                .putExtra(EXTRA_ENDED_AT, call.endedAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private lateinit var binding: ActivityCallEndBinding
    private var navBarInset = 0
    private var adRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tr.refresh(this)
        enableEdgeToEdge()
        binding = ActivityCallEndBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        val content = binding.layoutCallEndContent
        val contentTop = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCallEnd) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.updatePadding(top = contentTop + bars.top, left = bars.left + dp(20), right = bars.right + dp(20))
            navBarInset = bars.bottom
            applyBottomInsets()
            insets
        }

        binding.btnCloseCallEnd.contentDescription = tr("Close")
        binding.btnCloseCallEnd.setOnClickListener { finish() }
        binding.tvTileCallBack.text = tr("Call back")
        binding.tvTileMessage.text = tr("Message")
        binding.tvTileRemind.text = tr("Clean")
        binding.tvTileSave.text = tr("Save")
        binding.tvTipLabel.text = tr("TIP FOR TODAY")

        // Tiles without any phone permission: each opens the system app, where the user picks the
        // call / contact themselves
        binding.btnCallEndCallBack.setOnClickListener { openSystem(Intent(Intent.ACTION_DIAL)) }
        binding.btnCallEndMessage.setOnClickListener {
            openSystem(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
        }
        binding.btnCallEndRemind.setOnClickListener { openCleaner() }
        binding.btnCallEndSave.setOnClickListener {
            openSystem(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE))
        }
        binding.layoutCallEndTip.setOnClickListener { openCleaner() }

        render(intent)
        requestAd()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render(intent: Intent) = with(binding) {
        CallEndFeature.cancelNotification(this@CallEndActivity)
        val durationSec = intent.getLongExtra(EXTRA_DURATION_MS, 0L) / 1000
        tvCallEndedPill.text = trf(
            "Call ended · %1\$s",
            String.format(Locale.US, "%02d:%02d", durationSec / 60, durationSec % 60)
        )
        tvCallEndTitle.text = tr("Nice chat! Keep your phone fast")
        tvCallEndSubtitle.text = tr("Quick actions after your call")

        val endedAt = intent.getLongExtra(EXTRA_ENDED_AT, System.currentTimeMillis())
        val time = DateFormat.getTimeFormat(this@CallEndActivity).format(Date(endedAt))
        tvCallEndTime.text = trf(
            if (intent.getBooleanExtra(EXTRA_INCOMING, false)) "Incoming · Today, %1\$s" else "Outgoing · Today, %1\$s",
            time
        )

        val tip = CallTips.forToday(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
        tvCallEndTip.text = tr(tip.first)
        tvCallEndTipMeaning.text = tr(tip.second)
    }

    private fun openSystem(target: Intent) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app for ${target.action}", e)
        }
    }

    /** This screen has its own task, so the app opens in its own. */
    private fun openCleaner() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    // region Ad sheet

    private fun applyBottomInsets() {
        val sheet = binding.layoutCallEndAdSheet
        if (sheet.isVisible) {
            sheet.updatePadding(bottom = dp(12) + navBarInset)
            binding.layoutCallEndContent.updatePadding(bottom = dp(14))
        } else {
            binding.layoutCallEndContent.updatePadding(bottom = dp(14) + navBarInset)
        }
    }

    private fun setAdSheetShown(show: Boolean) {
        binding.layoutCallEndAdSheet.isVisible = show
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = show
        applyBottomInsets()
    }

    /** Native ad below the tip card; the sheet is hidden when the ad is off or fails. */
    private fun requestAd() {
        if (adRequested) return
        adRequested = true
        setAdSheetShown(true)
        ScreenAds.loadNative(this, ScreenConfig.AD_CALL_END, binding.layoutAdsHolder) { shown ->
            if (isDestroyed) return@loadNative
            if (!shown) Log.d(TAG, "native_call_end off or failed: no ad sheet")
            setAdSheetShown(shown)
        }
    }

    // endregion
}

/** Short phone-care tips for the tip card, one per day of the year. English text goes through tr(). */
object CallTips {
    private val tips = listOf(
        "Clear junk files regularly" to "Cache and leftover files can take up gigabytes without you noticing.",
        "Delete similar photos" to "Burst shots and near-duplicates are the easiest space to win back.",
        "Close apps you do not use" to "Background apps drain battery and slow your phone down.",
        "Review large files" to "A few big videos often use more space than hundreds of photos.",
        "Unplug at 80 percent" to "Keeping the charge between 20 and 80 percent helps the battery live longer.",
        "Clean chat media" to "Forwarded images and videos in chat apps pile up fast.",
        "Remove unused apps" to "Apps you have not opened in months still hold storage and permissions.",
    )

    /** Pair of (headline, explanation) for [dayOfYear]. */
    fun forToday(dayOfYear: Int): Pair<String, String> = tips[Math.floorMod(dayOfYear, tips.size)]
}
