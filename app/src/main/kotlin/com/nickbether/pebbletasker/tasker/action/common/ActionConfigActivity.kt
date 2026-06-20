package com.nickbether.pebbletasker.tasker.action.common

import androidx.viewbinding.ViewBinding
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity
import com.nickbether.pebbletasker.util.SecureWindow

/**
 * Shared base for every ACTION config activity (FINAL DESIGN §2.3).
 *
 * Adds, on top of [PebbleConfigActivity] (which already re-implements TaskerPluginConfig on
 * AppCompatActivity and attaches the variable picker to every field):
 *   - optional FLAG_SECURE for sensitive actions (notification/health/AppMessage/serial) via
 *     [isSensitive] -> [SecureWindow];
 *   - a convenience to wire a `serial` field to the [CriteriaDropdown] (anchored "Any" + live watches
 *     on a field tap / start icon; the end icon stays the variable picker — FIX #3a).
 *
 * Subclasses still implement the four PebbleConfigActivity abstracts (inflateBinding / getNewHelper /
 * assignFromInput / inputForTasker) and may override [onConfigCreated] to wire pickers/toggles.
 */
abstract class ActionConfigActivity<
    TInput : Any,
    TOutput : Any,
    TRunner : TaskerPluginRunner<TInput, TOutput>,
    THelper : TaskerPluginConfigHelper<TInput, TOutput, TRunner>,
    TBinding : ViewBinding,
    > : PebbleConfigActivity<TInput, TOutput, TRunner, THelper, TBinding>() {

    /** Sensitive actions (notification/health/AppMessage/serial) set FLAG_SECURE. Default false. */
    protected open val isSensitive: Boolean = false

    override fun onConfigCreated(binding: TBinding) {
        if (isSensitive) SecureWindow.apply(this)
        super.onConfigCreated(binding)
    }

    /**
     * Wire a `serial` [TextInputLayout] to the shared [CriteriaDropdown]: an anchored "Any" + live
     * watches dropdown opened by a field tap or its start icon, writing the chosen serial into the
     * editable field (so a %var still works; the end icon remains the variable picker). Call from
     * [onConfigCreated].
     */
    protected fun wireWatchBrowse(layout: TextInputLayout) {
        CriteriaDropdown.attachWatchSerial(layout)
    }
}
