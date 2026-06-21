package com.nickbether.pebbletasker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient.ConnectionStatus
import com.nickbether.pebbletasker.databinding.ActivityGettingStartedBinding
import com.nickbether.pebbletasker.util.applyContentInsets

/**
 * A short, stepped "how to use this plugin" flow. Shown ONCE automatically the first time the bridge
 * connects (see [maybeLaunch]) and re-openable from MainActivity. Deliberately light on words: a few
 * steps to make a trigger + action, then a handful of example tasks.
 */
class GettingStartedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGettingStartedBinding
    private var index = 0

    private data class Step(val title: String, val body: String)

    private val steps = listOf(
        Step(
            "You're connected",
            "The Pebble app is linked to Tasker. You can now react to your watch and send it commands " +
                "straight from your tasks.",
        ),
        Step(
            "Make a trigger",
            "In Tasker: Profiles → + → Event or State → Plugin → Pebble.\n\n" +
                "For example, \"Pebble Watch Connected\" (a state) or \"Pebble Watch Disconnected\" (an event).",
        ),
        Step(
            "Add a task",
            "Give the profile a task — or add a Pebble action with Task → + → Plugin → Pebble.\n\n" +
                "For example, \"Pebble Send Watch Notification\".",
        ),
        Step(
            "Use your watch data",
            "Pebble events fill %pb_ variables you can use anywhere in the task — e.g. %pb_serial, " +
                "%pb_battery, %pb_connected, and the whole payload as %pb_json.",
        ),
        Step(
            "Try these",
            "• Watch disconnects → flash + notify your phone\n" +
                "• Battery below 20% → \"Watch battery: %pb_battery%\"\n" +
                "• Watch connected → keep the phone screen awake\n" +
                "• Send a notification to the watch on demand\n\n" +
                "Ready-to-import copies of these live in the project's docs/examples folder.",
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGettingStartedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets()

        binding.progress.max = steps.size
        binding.btnSkip.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { if (index > 0) { index--; render() } }
        binding.btnNext.setOnClickListener {
            if (index < steps.lastIndex) { index++; render() } else finish()
        }
        render()
    }

    private fun render() {
        val step = steps[index]
        binding.stepCounter.text = "STEP ${index + 1} OF ${steps.size}"
        binding.stepTitle.text = step.title
        binding.stepBody.text = step.body
        binding.progress.setProgressCompat(index + 1, true)
        binding.btnBack.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.setText(if (index == steps.lastIndex) R.string.gs_done else R.string.gs_next)
    }

    companion object {
        private const val PREFS = "pb_onboarding"
        private const val KEY_SHOWN = "getting_started_shown"

        fun intentFor(context: Context): Intent = Intent(context, GettingStartedActivity::class.java)

        /** Launch the flow once — the first time the bridge is Ready. No-op if already shown / not Ready. */
        fun maybeLaunch(activity: Activity, status: ConnectionStatus) {
            if (status !is ConnectionStatus.Ready) return
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_SHOWN, false)) return
            prefs.edit().putBoolean(KEY_SHOWN, true).apply()
            activity.startActivity(intentFor(activity))
        }
    }
}
