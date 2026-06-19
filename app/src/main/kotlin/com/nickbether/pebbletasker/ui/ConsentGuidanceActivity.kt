package com.nickbether.pebbletasker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus
import com.nickbether.pebbletasker.databinding.ActivityConsentGuidanceBinding
import kotlinx.coroutines.launch

/**
 * Trust / consent guidance (FINAL DESIGN §3.5, §1 ui/ConsentGuidanceActivity).
 *
 * Reactively renders the current [BridgeClient.status] as one of four blocker screens —
 * NOT_AUTHORIZED, CONSENT_PENDING, CERT_MISMATCH, AppAbsent — plus a generic error and a "you're
 * already fine" state. Each picks the right heading/body and the matching action:
 *   - NOT_AUTHORIZED / CONSENT_PENDING -> open the Pebble app (the user must act there).
 *   - CERT_MISMATCH -> a danger "Re-trust this signature" button (calls BridgeClient.retrustCert()),
 *     showing the new SHA-256 so the user can compare. Never auto-retries.
 *   - AppAbsent -> open the Pebble app (install).
 *
 * Because CONSENT_PENDING auto-resolves (BridgeClient polls handshake), this screen flips to the
 * "All set" state on its own once approval lands — no user refresh needed.
 */
class ConsentGuidanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentGuidanceBinding

    private val bridge get() = BridgeClient.get(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentGuidanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener { bridge.retryHandshake() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnRetrust.setOnClickListener {
            bridge.retrustCert()
            Toast.makeText(this, R.string.action_retry, Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bridge.status.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bridge.retryHandshake()
    }

    private fun render(status: ConnectionStatus) {
        binding.statusChip.text = getString(UiSupport.statusLabel(status))
        UiSupport.styleStatusChip(binding.statusChip, status)

        // Defaults; each branch overrides what it needs.
        binding.certLabel.visibility = View.GONE
        binding.certValue.visibility = View.GONE
        binding.btnRetrust.visibility = View.GONE

        when (status) {
            is ConnectionStatus.NotAuthorized -> {
                setText(R.string.consent_not_authorized_heading, R.string.consent_not_authorized_body)
                primaryOpensPebble()
            }
            is ConnectionStatus.ConsentPending -> {
                setText(R.string.consent_pending_heading, R.string.consent_pending_body)
                primaryOpensPebble()
            }
            is ConnectionStatus.CertMismatch -> {
                setText(R.string.consent_cert_mismatch_heading, R.string.consent_cert_mismatch_body)
                binding.certLabel.visibility = View.VISIBLE
                binding.certValue.visibility = View.VISIBLE
                binding.certValue.text = status.currentSha ?: getString(R.string.value_unknown)
                binding.btnRetrust.visibility = View.VISIBLE
                primaryOpensPebble()
            }
            is ConnectionStatus.AppAbsent -> {
                setText(R.string.consent_app_absent_heading, R.string.consent_app_absent_body)
                primaryOpensPebble()
            }
            is ConnectionStatus.Error -> {
                setText(R.string.consent_error_heading, R.string.consent_error_body)
                primaryRetries()
            }
            is ConnectionStatus.Ready -> {
                setText(R.string.consent_ok_heading, R.string.consent_ok_body)
                binding.btnPrimary.visibility = View.GONE
            }
            is ConnectionStatus.Idle,
            is ConnectionStatus.Disconnected -> {
                setText(R.string.consent_error_heading, R.string.main_status_connecting)
                primaryRetries()
            }
        }
    }

    private fun setText(headingRes: Int, bodyRes: Int) {
        binding.guidanceHeading.setText(headingRes)
        binding.guidanceBody.setText(bodyRes)
    }

    private fun primaryOpensPebble() {
        binding.btnPrimary.visibility = View.VISIBLE
        binding.btnPrimary.setText(R.string.action_open_pebble_app)
        binding.btnPrimary.setOnClickListener { openBridgeConsent() }
    }

    private fun primaryRetries() {
        binding.btnPrimary.visibility = View.VISIBLE
        binding.btnPrimary.setText(R.string.action_retry)
        binding.btnPrimary.setOnClickListener { bridge.retryHandshake() }
    }

    private fun openPebbleApp() {
        val intent = UiSupport.pebbleLaunchIntent(this)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.consent_app_absent_heading, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Deep-link straight to the Pebble app's automation consent/settings screen (exported via the
     * REVIEW_CLIENTS action) so the user lands on the approve/toggle screen rather than the app home.
     * Falls back to the app launcher if an older bridge can't resolve the action.
     */
    private fun openBridgeConsent() {
        val consent = Intent("coredevices.coreapp.automation.REVIEW_CLIENTS")
            .setPackage("coredevices.coreapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (consent.resolveActivity(packageManager) != null) {
            startActivity(consent)
        } else {
            openPebbleApp()
        }
    }

    companion object {
        /**
         * Build a launch intent for this guidance screen. The screen is driven by the LIVE
         * [BridgeClient.status], so [status] is only an initial hint and may be omitted; this overload
         * exists so callers (MainActivity) can express intent without coupling to extras.
         */
        fun intentFor(context: Context, status: ConnectionStatus? = null): Intent =
            Intent(context, ConsentGuidanceActivity::class.java)
    }
}
