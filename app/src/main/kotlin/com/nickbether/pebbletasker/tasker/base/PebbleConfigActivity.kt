package com.nickbether.pebbletasker.tasker.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.MaterialToolbar
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.tasker.vars.RelevantVars
import com.nickbether.pebbletasker.tasker.vars.VariableFieldBinder
import com.nickbether.pebbletasker.ui.BridgeWarning
import com.nickbether.pebbletasker.ui.ConsentGuidanceActivity
import kotlinx.coroutines.launch

/**
 * Base class for ALL Pebble plugin config activities (FINAL DESIGN §4.2, FIX C2).
 *
 * WHY NOT extend the library's ActivityConfigTasker: that class extends plain android.app.Activity,
 * which makes Material3 / Theme.NeonGrid misrender or crash. This is a faithful re-implementation of
 * its body on AppCompatActivity so Material3 renders.
 *
 * On top of the library wiring, this base gives EVERY config screen, in one place:
 *   - a toolbar with ✓ (accept — validate + persist + finish, same as Back) and ✗ (discard — finish
 *     without saving), via [activity_config_scaffold] + menu/config_confirm;
 *   - window-inset padding so content sits BELOW the status bar (edge-to-edge is enforced on SDK 35+;
 *     we pad rather than go immersive);
 *   - save-on-back through the AndroidX OnBackPressedDispatcher, so the config is persisted on gesture
 *     / predictive back too. (The old onKeyDown(KEYCODE_BACK) path never fired for gesture back, so
 *     backing out silently DISCARDED the configuration — this is the fix.)
 *   - the variable picker on every TextInputLayout (host relevant vars ∪ the plugin's %pb_* outputs).
 *
 * Subclasses implement inflateBinding / getNewHelper / assignFromInput / inputForTasker, and may
 * override [onConfigCreated] to wire pickers/toggles after fields + var pickers are bound.
 */
abstract class PebbleConfigActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    TBinding : ViewBinding,
    > : AppCompatActivity(), TaskerPluginConfig<TInput> {

    protected abstract fun inflateBinding(inflater: LayoutInflater): TBinding
    protected abstract fun getNewHelper(config: TaskerPluginConfig<TInput>): THelper

    protected var binding: TBinding? = null

    /** Lazily built helper; available to subclasses for relevantVariables, blurb, etc. */
    protected val taskerHelper: THelper by lazy { getNewHelper(this) }

    /** Whether this config requires a UI. No-input/no-config plugins override to false. */
    protected open val isConfigurable: Boolean = true

    /** Whether to include the plugin's own %pb_* outputs in the picker suggestions (default true). */
    protected open val includePbVarsInPicker: Boolean = true

    override val context get() = applicationContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = inflateBinding(layoutInflater)
        binding = b
        if (!isConfigurable) {
            taskerHelper.finishForTasker()
            return
        }

        // Edge-to-edge is enforced on SDK 35+; opt in explicitly so the inset handling below is
        // consistent on older versions too. We pad the content rather than go immersive.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Wrap the subclass content in the shared toolbar scaffold (✓ accept / ✗ discard).
        val scaffold = layoutInflater.inflate(R.layout.activity_config_scaffold, null) as ViewGroup
        scaffold.findViewById<FrameLayout>(R.id.pbConfigContent).addView(b.root)
        setContentView(scaffold)

        val toolbar = scaffold.findViewById<MaterialToolbar>(R.id.pbToolbar)
        toolbar.title = title
        toolbar.inflateMenu(R.menu.config_confirm)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_accept -> { acceptConfig(); true }
                R.id.action_discard -> { discardConfig(); true }
                else -> false
            }
        }

        // Keep content out from under the status bar / nav bar / cutout / keyboard (no immersive mode).
        ViewCompat.setOnApplyWindowInsetsListener(scaffold) { v, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            windowInsets
        }

        // Save-on-back for gesture / predictive back (onKeyDown(KEYCODE_BACK) does NOT fire there).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = acceptConfig()
            },
        )

        // Warn (and link to setup) whenever the plugin is configured while NOT bridged to the Pebble
        // app — so nobody configures a state/event/action against a connection that doesn't exist.
        val warningBanner = scaffold.findViewById<TextView>(R.id.pbBridgeWarning)
        warningBanner.setOnClickListener { startActivity(ConsentGuidanceActivity.intentFor(this)) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BridgeClient.get(this@PebbleConfigActivity).status.collect { status ->
                    val warn = BridgeWarning.messageFor(status)
                    warningBanner.text = warn
                    warningBanner.visibility = if (warn == null) View.GONE else View.VISIBLE
                }
            }
        }
        // Nudge a (re)bind so a transient disconnect clears while the user is configuring.
        BridgeClient.get(this).retryHandshake()

        // Library wiring: populate the saved input into the UI, then attach variable pickers.
        taskerHelper.onCreate()
        attachVariablePickers(b.root)
        onConfigCreated(b)
    }

    /** Hook for subclasses to wire browse-pickers / toggles AFTER fields + var pickers are bound. */
    protected open fun onConfigCreated(binding: TBinding) {}

    /**
     * Attach the variable picker to all variable fields under [root]. Reads Tasker's inbound relevant
     * variables off this activity's intent (null-safe) and merges with the plugin's %pb_* outputs.
     */
    protected fun attachVariablePickers(root: View) {
        val hostVars = RelevantVars.fromIntent(intent)
        val suggestions = RelevantVars.suggestionsFor(hostVars, includePbVarsInPicker)
        VariableFieldBinder.attachAll(root, suggestions)
    }

    /** Tasker's inbound relevant variables for this session (possibly empty, never null). */
    protected val relevantVariables: Array<String> get() = RelevantVars.fromIntent(intent)

    /**
     * Accept: validate + persist the config for Tasker and finish — identical to a system Back press.
     * On a validation error the helper returns [SimpleResultError]; we surface it and stay on screen so
     * the user can fix it (or hit ✗ to bail).
     */
    protected fun acceptConfig() {
        val result = taskerHelper.onBackPressed()
        if (result is SimpleResultError) onInvalidConfig(result.message)
        // On success the helper has already finished the activity for Tasker (config persisted).
    }

    /** Discard: finish WITHOUT saving. Tasker keeps the previous config (or cancels a fresh add). */
    protected fun discardConfig() {
        setResult(RESULT_CANCELED)
        finish()
    }

    /** Surface a validation error. Default shows a short toast; subclasses may override for a dialog. */
    protected open fun onInvalidConfig(message: String?) {
        if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // TaskerPluginConfig requires these; assignFromInput / inputForTasker are subclass responsibility.
    abstract override fun assignFromInput(input: TaskerInput<TInput>)
    abstract override val inputForTasker: TaskerInput<TInput>
}
