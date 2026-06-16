package com.nickbether.pebbletasker.tasker.base

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.tasker.vars.RelevantVars
import com.nickbether.pebbletasker.tasker.vars.VariableFieldBinder

/**
 * Base class for ALL Pebble plugin config activities (FINAL DESIGN §4.2, FIX C2).
 *
 * WHY NOT extend the library's ActivityConfigTasker: that class extends plain android.app.Activity,
 * which makes Material3 / Theme.NeonGrid misrender or crash. This is a faithful re-implementation of
 * its body on AppCompatActivity so Material3 renders. (Verified against the library's
 * TaskerPluginConfig interface and the reference ActivityConfigTasker.)
 *
 * It ALSO wires the variable framework: after inflating the binding, it reads Tasker's inbound
 * relevant variables (null-safe) and attaches [VariableFieldBinder] to every TextInputLayout in the
 * view tree, so every variable field gets the (mandatory) variable picker seeded with Tasker's
 * suggestions ∪ the plugin's own %pb_* outputs.
 *
 * Subclasses implement:
 *   - [inflateBinding]  inflate the ViewBinding (e.g. ActivityConfigBatteryBinding.inflate(it))
 *   - [getNewHelper]    return the plugin's helper
 *   - [assignFromInput] push the saved input into the UI
 *   - [inputForTasker]  read the UI into a fresh input object
 * Save-on-back is handled here via finishForTasker().
 *
 * Type params mirror the library's: TInput/TOutput/TRunner/THelper/TBinding.
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
        setContentView(b.root)
        // Let the helper populate the input object into the UI first...
        taskerHelper.onCreate()
        // ...then attach the variable picker to every field, seeded with host + plugin suggestions.
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            val result = taskerHelper.onBackPressed()
            if (result is SimpleResultError) {
                onInvalidConfig(result.message)
                return false
            }
            return result.success
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Intentionally empty: onKeyDown -> taskerHelper.onBackPressed() drives the Tasker save/finish,
     * mirroring the library's reference ActivityConfigTasker. Deprecated on ComponentActivity but
     * still the correct override point for this plugin pattern.
     */
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // no-op
    }

    /** Override to surface a validation error (default: no-op; subclass can show a dialog). */
    protected open fun onInvalidConfig(message: String?) {}

    // TaskerPluginConfig requires these; assignFromInput / inputForTasker are subclass responsibility.
    abstract override fun assignFromInput(input: TaskerInput<TInput>)
    abstract override val inputForTasker: TaskerInput<TInput>
}
