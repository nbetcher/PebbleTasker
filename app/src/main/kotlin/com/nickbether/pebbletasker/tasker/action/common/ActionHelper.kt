package com.nickbether.pebbletasker.tasker.action.common

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.tasker.base.PebbleActionHelper
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner

/**
 * Shared helper base for ACTION plugins (FINAL DESIGN §2.3 / §4.2).
 *
 * Extends [PebbleActionHelper] (plain TaskerPluginConfigHelper with timeoutSeconds = 9) and supplies
 * a dynamic Tasker "blurb" summarising the configured action. Subclasses provide the three *Class
 * members and override [blurbFor] to render the one-line summary shown in the Tasker task list.
 *
 * Output-suggestion chaining (manner 4) is automatic: the library forwards this plugin's
 * @TaskerOutputVariable names into downstream pickers for the action path (verified reliable).
 */
abstract class ActionHelper<TInput : Any, TOutput : Any, TRunner : PebbleActionRunner<TInput, TOutput>>(
    config: TaskerPluginConfig<TInput>,
) : PebbleActionHelper<TInput, TOutput, TRunner>(config) {

    /** One-line summary of the configured action for the Tasker task list. Default: action name. */
    protected open fun blurbFor(input: TInput): String = defaultBlurb

    /** Fallback blurb (the action's display name). */
    protected abstract val defaultBlurb: String

    override fun addToStringBlurb(input: TaskerInput<TInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(runCatching { blurbFor(input.regular) }.getOrDefault(defaultBlurb))
    }
}
