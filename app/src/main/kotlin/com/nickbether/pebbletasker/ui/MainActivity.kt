package com.nickbether.pebbletasker.ui

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
import com.nickbether.pebbletasker.databinding.ActivityMainBinding
import com.nickbether.pebbletasker.setup.SetupState
import com.nickbether.pebbletasker.util.applyContentInsets
import kotlinx.coroutines.launch

/**
 * Launcher home (FINAL DESIGN §1 ui/MainActivity).
 *
 * Shows the live bridge connection status, an overview of what the plugin offers, and entry points
 * to onboarding (setup/fix) and diagnostics. It is a thin reactive shell over [BridgeClient.status];
 * all connection logic lives in BridgeClient.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val bridge get() = BridgeClient.get(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets()

        binding.btnSetup.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        binding.btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        binding.btnGettingStarted.setOnClickListener {
            startActivity(GettingStartedActivity.intentFor(this))
        }
        binding.btnOpenPebble.setOnClickListener { openPebbleApp() }

        // Collect status only while STARTED; re-collected on resume so it always reflects reality.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bridge.status.collect {
                    render(it)
                    // The first time we're actually connected, kick off the short getting-started flow.
                    GettingStartedActivity.maybeLaunch(this@MainActivity, it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A return from the Pebble app (after the user flipped the master switch / approved) should
        // re-drive the handshake so the status reflects the new authorization promptly.
        bridge.retryHandshake()
    }

    private fun render(status: ConnectionStatus) {
        binding.statusChip.text = getString(UiSupport.statusLabel(status))
        UiSupport.styleStatusChip(binding.statusChip, status)
        binding.statusDetail.text = UiSupport.statusDetail(this, status)

        val needsAction = UiSupport.needsUserAction(status)
        binding.btnPrimaryAction.visibility = if (needsAction) View.VISIBLE else View.GONE
        binding.btnPrimaryAction.text = getString(
            if (status is ConnectionStatus.AppAbsent) R.string.main_btn_open_pebble
            else R.string.main_btn_fix,
        )
        binding.btnPrimaryAction.setOnClickListener {
            when (status) {
                is ConnectionStatus.AppAbsent -> openPebbleApp()
                else -> startActivity(
                    ConsentGuidanceActivity.intentFor(this, status),
                )
            }
        }

        // Offer a quick "open Pebble app" shortcut whenever the user must act in that app.
        val showOpen = status is ConnectionStatus.NotAuthorized ||
            status is ConnectionStatus.ConsentPending ||
            status is ConnectionStatus.CertMismatch
        binding.btnOpenPebble.visibility = if (showOpen) View.VISIBLE else View.GONE

        // Once setup is complete, the "Set up access" button is just noise — hide it. (Diagnostics and
        // the getting-started recap stay available.) Treat either the persisted flag or a live Ready
        // status as "done" so it disappears the moment the connection succeeds.
        val setUp = SetupState.isSetupComplete(this) || status is ConnectionStatus.Ready
        binding.btnSetup.visibility = if (setUp) View.GONE else View.VISIBLE
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
