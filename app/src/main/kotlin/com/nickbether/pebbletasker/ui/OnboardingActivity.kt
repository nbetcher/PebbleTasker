package com.nickbether.pebbletasker.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus
import com.nickbether.pebbletasker.databinding.ActivityOnboardingBinding
import com.nickbether.pebbletasker.util.BatteryOpt
import com.nickbether.pebbletasker.util.applyContentInsets
import kotlinx.coroutines.launch

/**
 * Master-switch-first onboarding (FINAL DESIGN §0 FIX D1, §1 ui/OnboardingActivity).
 *
 * Walks the user through, in order:
 *   1. Turn ON the Pebble app's Automation access master switch (OFF by default — until then the
 *      bridge returns NOT_AUTHORIZED and posts NO consent prompt, so this MUST come first).
 *   2. Approve "Pebble Tasker Plugin" in the Pebble app (reachable only once master is ON).
 *   3. (Optional) Exempt this app from battery optimization so events arrive promptly.
 *   4. Verify — shows the live [BridgeClient.status] and a re-check button.
 *
 * The screen does not itself flip switches in the Pebble app (its consent activity is exported=false);
 * it explains, links out, and reflects the resulting status reactively.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val bridge get() = BridgeClient.get(this)

    /** Best-effort POST_NOTIFICATIONS request (Android 13+) so the "not connected" warning can show. */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets()

        binding.btnOpenPebble.setOnClickListener { openPebbleApp() }
        binding.btnBattery.setOnClickListener { requestBatteryExemption() }
        binding.btnRecheck.setOnClickListener { bridge.retryHandshake() }
        binding.btnFinish.setOnClickListener { finish() }
        binding.btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bridge.status.collect {
                    renderStatus(it)
                    // Connected for the first time -> kick off the short getting-started flow.
                    GettingStartedActivity.maybeLaunch(this@OnboardingActivity, it)
                }
            }
        }

        maybeRequestNotificationPermission()
    }

    /** Ask for POST_NOTIFICATIONS once on Android 13+ so the "Pebble not connected" warning can show. */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryState()
        // Re-drive the handshake so returning from the Pebble app reflects new authorization.
        bridge.retryHandshake()
    }

    // --- step 3: battery optimization ---

    private fun refreshBatteryState() {
        if (BatteryOpt.isIgnoringBatteryOptimizations(this)) {
            binding.btnBattery.isEnabled = false
            binding.batteryStatus.visibility = View.VISIBLE
            binding.batteryStatus.text = getString(R.string.onboarding_battery_already)
        } else {
            binding.btnBattery.isEnabled = true
            binding.batteryStatus.visibility = View.GONE
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(BatteryOpt.requestIgnoreIntent(this))
        } catch (e: ActivityNotFoundException) {
            // Fall back to the full battery-optimization settings list, then to a toast.
            try {
                startActivity(BatteryOpt.settingsListIntent())
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.onboarding_battery_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- step 4: verify ---

    private fun renderStatus(status: ConnectionStatus) {
        binding.statusChip.text = getString(UiSupport.statusLabel(status))
        UiSupport.styleStatusChip(binding.statusChip, status)
        binding.statusDetail.text = UiSupport.statusDetail(this, status)
    }

    private fun openPebbleApp() {
        val intent = UiSupport.pebbleLaunchIntent(this)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.consent_app_absent_heading, Toast.LENGTH_LONG).show()
        }
    }
}
