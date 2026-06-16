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

/**
 * E15 — Pebble Bridge/Watch Error (COLLECTOR + client-synthesized gap).
 *
 * Fires on a real bridge `system.error` event OR the client-side `system.gap` event that EventCache
 * synthesizes on a bootId change / >50-event seq discontinuity (FINAL DESIGN FIX A2). Because this
 * plugin straddles two backing types, the runner reads BOTH from the cache and evaluates the most
 * recent one (the base-passed `cached` is the system.error slot; gap lives under its own type).
 *
 * error_type filter: "error" | "gap" | a specific bridge error code (blank = any).
 * Gap events carry %pb_gap_from / %pb_gap_to; real errors carry %pb_error_msg.
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

    override fun evaluate(
        context: Context,
        filter: BridgeErrorFilter,
        cached: CachedEvent?,
        update: BridgeErrorOutput?,
    ): TaskerPluginResultCondition<BridgeErrorOutput> {
        // Consider both the real error slot (passed in as `cached`) and the synthesized gap slot,
        // and evaluate whichever is newest.
        val gap = runCatching { EventCache.get(context).latest(CachedEvent.TYPE_GAP) }.getOrNull()
        val e = newest(cached, gap) ?: return TaskerPluginResultConditionUnknown()

        val isGap = e.type == CachedEvent.TYPE_GAP
        val errorType = if (isGap) "gap" else (e.str("error_type") ?: "error")
        if (!FilterMatch.eq(filter.errorType, errorType)) {
            // Also allow filtering on the raw error code/message keyword.
            if (!FilterMatch.contains(filter.errorType, e.str("error_msg"))) {
                return TaskerPluginResultConditionUnsatisfied()
            }
        }

        val out = BridgeErrorOutput(
            pbErrorType = errorType,
            pbErrorMsg = e.str("error_msg") ?: e.str("reason"),
            pbGapFrom = e.str("gap_from"),
            pbGapTo = e.str("gap_to"),
        ).fillBase<BridgeErrorOutput>(
            e,
            buildMap {
                put("error_type", errorType)
                e.str("error_msg")?.let { put("error_msg", it) }
                e.str("gap_from")?.let { put("gap_from", it) }
                e.str("gap_to")?.let { put("gap_to", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }

    private fun newest(a: CachedEvent?, b: CachedEvent?): CachedEvent? = when {
        a == null -> b
        b == null -> a
        else -> if (b.ts >= a.ts) b else a
    }
}

class BridgeErrorHelper(config: TaskerPluginConfig<BridgeErrorFilter>) :
    PebbleEventHelper<BridgeErrorFilter, BridgeErrorOutput, BridgeErrorRunner>(config) {
    override val inputClass = BridgeErrorFilter::class.java
    override val outputClass = BridgeErrorOutput::class.java
    override val runnerClass = BridgeErrorRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<BridgeErrorFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires on a bridge error or a detected event gap.")
            .append("\nOutputs: %pb_error_type %pb_error_msg %pb_gap_from %pb_gap_to + %pb_json.")
    }
}

class BridgeErrorActivity :
    GenericEventConfigActivity<BridgeErrorFilter, BridgeErrorOutput, BridgeErrorRunner, BridgeErrorHelper>() {

    override val titleRes = R.string.pb_evt_bridgeerror_title
    override val descRes = R.string.pb_evt_bridgeerror_desc

    override fun buildFields() = listOf(
        FieldSpec("error_type", getString(R.string.pb_lbl_error_type)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<BridgeErrorFilter>) = BridgeErrorHelper(config)

    override fun buildInput(values: Map<String, String>) =
        BridgeErrorFilter(errorType = values["error_type"])

    override fun extractValues(input: BridgeErrorFilter) =
        mapOf("error_type" to input.errorType.orEmpty())
}
