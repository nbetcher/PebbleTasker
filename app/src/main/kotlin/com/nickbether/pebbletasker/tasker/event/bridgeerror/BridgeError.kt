package com.nickbether.pebbletasker.tasker.event.bridgeerror

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnknown
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/** Real errors, explicit history-loss notices and local access diagnostics use the exact delivered
 * payload. No latest-cache lookup can replace a pending error. Gaps remain an explicit opt-in.
 */
@TaskerInputRoot
class BridgeErrorFilter @JvmOverloads constructor(
    @field:TaskerInputField("error_type", labelResIdName = "pb_lbl_error_type")
    var errorType: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class BridgeErrorOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.ERROR_TYPE)
    @field:TaskerInputField("pb_error_type")
    var pbErrorType: String? = null,
    @get:TaskerOutputVariable(PbVars.ERROR_MSG)
    @field:TaskerInputField("pb_error_msg")
    var pbErrorMsg: String? = null,
    @get:TaskerOutputVariable(PbVars.GAP_FROM)
    @field:TaskerInputField("pb_gap_from")
    var pbGapFrom: String? = null,
    @get:TaskerOutputVariable(PbVars.GAP_TO)
    @field:TaskerInputField("pb_gap_to")
    var pbGapTo: String? = null,
) : BaseEventOutput()

class BridgeErrorRunner : PebbleEventRunner<BridgeErrorFilter, BridgeErrorOutput>() {
    // Primary backing type for the base's cache read; gap is fetched explicitly below.
    override val eventType: String = EventRouting.TYPE_SYSTEM_ERROR
    override fun accepts(type: String) = type == eventType || type == CachedEvent.TYPE_GAP || type == "plugin.access"
    override val isLocalDiagnostic = true

    override fun evaluate(
        context: Context,
        filter: BridgeErrorFilter,
        cached: CachedEvent?,
        update: BridgeErrorOutput?,
    ): TaskerPluginResultCondition<BridgeErrorOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()

        val isGap = e.type == CachedEvent.TYPE_GAP
        val errorType = if (isGap) "gap" else ((e.str("error_type") ?: e.str("type")) ?: "error")
        val matches = if (isGap) {
            // A gap is an opt-in RECOVERY notice, not an error: a blank ("any") filter must NOT fire on
            // it. A reconnect/boot gap is expected and would otherwise spam every catch-all error
            // profile. Subscribe explicitly with error_type = "gap". Nothing is lost by ignoring it —
            // state conditions already re-converge via EventRouter.requestQueryAll on reconnect, and the
            // genuinely-missed one-shot events can't be replayed anyway (that's what defines the gap).
            // "any" opts back in to everything, since blank alone can't express "errors AND gaps".
            !filter.errorType.isNullOrBlank() &&
                (FilterMatch.eq(filter.errorType, "gap") || FilterMatch.eq(filter.errorType, ERROR_TYPE_ANY))
        } else {
            // Real errors: blank = any; else match the error_type or a keyword in the code/message.
            FilterMatch.eq(filter.errorType, ERROR_TYPE_ANY) ||
                FilterMatch.eq(filter.errorType, errorType) ||
                FilterMatch.contains(filter.errorType, (e.str("error_msg") ?: e.str("message")))
        }
        if (!matches) return TaskerPluginResultConditionUnsatisfied()

        val out = BridgeErrorOutput(
            pbErrorType = errorType,
            pbErrorMsg = (e.str("error_msg") ?: e.str("message")) ?: e.str("reason"),
            pbGapFrom = e.str("gap_from"),
            pbGapTo = e.str("gap_to"),
        ).fillBase<BridgeErrorOutput>(
            e,
            buildMap {
                put("error_type", errorType)
                (e.str("error_msg") ?: e.str("message"))?.let { put("error_msg", it) }
                e.str("gap_from")?.let { put("gap_from", it) }
                e.str("gap_to")?.let { put("gap_to", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }

    companion object {
        /** Opt in to everything, including recovery gaps — blank alone can only mean "real errors". */
        const val ERROR_TYPE_ANY = "any"
    }
}

class BridgeErrorHelper(config: TaskerPluginConfig<BridgeErrorFilter>) :
    PebbleEventHelper<BridgeErrorFilter, BridgeErrorOutput, BridgeErrorRunner>(config) {
    override val inputClass = BridgeErrorFilter::class.java
    override val outputClass = BridgeErrorOutput::class.java
    override val runnerClass = BridgeErrorRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<BridgeErrorFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires on a real bridge error. Blank = any real error; \"gap\" = only missed-event gaps; \"any\" = both.")
            .append("\nOutputs: %pbl_error_type %pbl_error_msg %pbl_gap_from %pbl_gap_to + %pbl_json.")
    }
}

class BridgeErrorActivity :
    GenericEventConfigActivity<BridgeErrorFilter, BridgeErrorOutput, BridgeErrorRunner, BridgeErrorHelper>() {

    override val titleRes = R.string.pb_evt_bridgeerror_title
    override val descRes = R.string.pb_evt_bridgeerror_desc

    override fun buildFields() = listOf(
        FieldSpec(
            "error_type",
            getString(R.string.pb_lbl_error_type),
            // "Any" (blank) stays real-errors-only so a reconnect gap can't spam a catch-all profile;
            // the explicit rows opt in to gaps.
            options = listOf(
                getString(R.string.pb_err_opt_any_incl_gaps) to BridgeErrorRunner.ERROR_TYPE_ANY,
                getString(R.string.pb_err_opt_gap_only) to "gap",
            ),
        ),
    )

    override fun getNewHelper(config: TaskerPluginConfig<BridgeErrorFilter>) = BridgeErrorHelper(config)

    override fun buildInput(values: Map<String, String>) =
        BridgeErrorFilter(errorType = values["error_type"])

    override fun extractValues(input: BridgeErrorFilter) =
        mapOf("error_type" to input.errorType.orEmpty())
}
