package com.nickbether.pebbletasker.tasker.event.appmsg

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
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * E8 — Pebble AppMessage Received (COLLECTOR + EXECUTE).
 *
 * Requires the user to specify the watch-app `uuid` so the bridge knows which app's messages to relay.
 * The config activity issues an internal `appmessage.subscribe` command (gated behind commands.core)
 * on save so the bridge starts forwarding that app's messages — this is NOT a user-facing action.
 * On today's bridge (no execute()) the subscribe is a silent no-op and the event simply never fires.
 *
 * The dict is delivered as a flat JSON object in data["dict_json"]; the runner also splits it into
 * parallel %pbl_keys()/%pbl_values() arrays for convenience.
 */
@TaskerInputRoot
class AppMessageFilter @JvmOverloads constructor(
    @field:TaskerInputField("uuid", labelResIdName = "pb_evt_lbl_uuid")
    var uuid: String? = null,
    @field:TaskerInputField("key", labelResIdName = "pb_lbl_key")
    var key: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class AppMessageOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.UUID)
    @field:TaskerInputField("pb_uuid")
    var pbUuid: String? = null,
    @get:TaskerOutputVariable(PbVars.DICT_JSON)
    @field:TaskerInputField("pb_dict_json")
    var pbDictJson: String? = null,
    @get:TaskerOutputVariable(PbVars.KEYS)
    @field:TaskerInputField("pb_keys")
    var pbKeys: Array<String>? = null,
    @get:TaskerOutputVariable(PbVars.VALUES)
    @field:TaskerInputField("pb_values")
    var pbValues: Array<String>? = null,
) : BaseEventOutput()

class AppMessageRunner : PebbleEventRunner<AppMessageFilter, AppMessageOutput>() {
    override val eventType: String = EventRouting.TYPE_APPMSG

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun evaluate(
        context: Context,
        filter: AppMessageFilter,
        cached: CachedEvent?,
        update: AppMessageOutput?,
    ): TaskerPluginResultCondition<AppMessageOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val uuid = e.str("uuid")
        // uuid is the required scope; only match the configured app.
        if (!FilterMatch.eq(filter.uuid, uuid)) return TaskerPluginResultConditionUnsatisfied()

        val dictJson = e.str("dict_json").orEmpty()
        val (keys, values) = parseDict(dictJson)

        // Optional single-key filter: the dict must contain the configured key.
        if (!filter.key.isNullOrBlank() && keys.none { it.equals(filter.key!!.trim(), ignoreCase = true) }) {
            return TaskerPluginResultConditionUnsatisfied()
        }

        val out = AppMessageOutput(
            pbUuid = uuid,
            pbDictJson = dictJson,
            pbKeys = keys.toTypedArray(),
            pbValues = values.toTypedArray(),
        ).fillBase<AppMessageOutput>(
            e,
            buildMap {
                uuid?.let { put("uuid", it) }
                if (dictJson.isNotEmpty()) put("dict_json", dictJson)
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }

    private fun parseDict(dictJson: String): Pair<List<String>, List<String>> {
        if (dictJson.isBlank()) return emptyList<String>() to emptyList()
        return try {
            val obj: JsonObject = lenientJson.parseToJsonElement(dictJson).jsonObject
            val keys = ArrayList<String>(obj.size)
            val values = ArrayList<String>(obj.size)
            for ((k, v) in obj) {
                keys += k
                values += (v as? JsonPrimitive)?.content ?: v.toString()
            }
            keys to values
        } catch (t: Throwable) {
            emptyList<String>() to emptyList()
        }
    }
}

class AppMessageHelper(config: TaskerPluginConfig<AppMessageFilter>) :
    PebbleEventHelper<AppMessageFilter, AppMessageOutput, AppMessageRunner>(config) {
    override val inputClass = AppMessageFilter::class.java
    override val outputClass = AppMessageOutput::class.java
    override val runnerClass = AppMessageRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<AppMessageFilter>, blurbBuilder: StringBuilder) {
        val uuid = input.regular.uuid?.takeIf { it.isNotBlank() } ?: "(set a UUID)"
        blurbBuilder.append("Fires when app ").append(uuid).append(" sends an AppMessage.")
            .append("\nOutputs: %pbl_uuid %pbl_dict_json %pbl_keys() %pbl_values() + %pbl_json.")
    }
}

class AppMessageActivity :
    GenericEventConfigActivity<AppMessageFilter, AppMessageOutput, AppMessageRunner, AppMessageHelper>() {

    override val titleRes = R.string.pb_evt_appmsg_title
    override val descRes = R.string.pb_evt_appmsg_desc

    override fun buildFields() = listOf(
        FieldSpec("uuid", getString(R.string.pb_evt_lbl_uuid), lookup = CriteriaDropdown.Source.LOCKER_ANY),
        FieldSpec("key", getString(R.string.pb_lbl_key)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<AppMessageFilter>) = AppMessageHelper(config)

    override fun buildInput(values: Map<String, String>): AppMessageFilter {
        val input = AppMessageFilter(uuid = values["uuid"], key = values["key"])
        // Best-effort internal subscribe so the bridge starts relaying this app's messages.
        // No-ops on today's bridge (no execute / commands.core) and never blocks the save path.
        maybeSubscribe(input.uuid)
        return input
    }

    override fun extractValues(input: AppMessageFilter) =
        mapOf("uuid" to input.uuid.orEmpty(), "key" to input.key.orEmpty())

    /** Fire-and-forget appmessage.subscribe; skips literal %vars (can't subscribe to a variable). */
    private fun maybeSubscribe(uuid: String?) {
        if (uuid.isNullOrBlank() || uuid.contains('%')) return
        runCatching {
            Thread {
                runCatching {
                    BridgeClient.get(applicationContext).executeBlocking(
                        CommandEnvelope(
                            type = "appmessage.subscribe",
                            args = mapOf("uuid" to uuid),
                        ),
                    )
                }
            }.start()
        }
    }
}
