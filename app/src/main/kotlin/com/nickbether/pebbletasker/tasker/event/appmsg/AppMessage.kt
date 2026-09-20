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

/** AppMessage events carry a typed dictionary and source watch. Desired subscriptions are saved
 * per profile and re-requested on Ready and supported host initialization. Observe is the default;
 * only the explicit Tasker-only ownership mode asks the bridge to acknowledge undeclared apps.
 */
@TaskerInputRoot
class AppMessageFilter @JvmOverloads constructor(
    @field:TaskerInputField("ownership")
    var ownership: String = "observe",
    @field:TaskerInputField("subscription_id")
    var subscriptionId: String? = null,
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
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
        if (!filter.serial.isNullOrBlank() && filter.serial != e.watch?.serial && filter.serial != e.watch?.address) return TaskerPluginResultConditionUnsatisfied()
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
    override fun isInputValid(input: TaskerInput<AppMessageFilter>): com.joaomgcd.taskerpluginlibrary.SimpleResult {
        val uuid = input.regular.uuid.orEmpty()
        if (!uuid.contains('%') && runCatching { java.util.UUID.fromString(uuid) }.isFailure) return com.joaomgcd.taskerpluginlibrary.SimpleResultError("Enter the watch application's UUID before saving.")
        if (input.regular.ownership !in setOf("observe", "tasker")) return com.joaomgcd.taskerpluginlibrary.SimpleResultError("Choose Observe or Tasker-only acknowledgement ownership.")
        val valid = super.isInputValid(input)
        if (valid.success) AppMessageSubscriptions.remember(config.context, input.regular.uuid, input.regular.serial, input.regular.ownership, input.regular.subscriptionId)
        return valid
    }
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

    private var subscriptionId = java.util.UUID.randomUUID().toString()
    private var previousInput: AppMessageFilter? = null
    private var saveDialogOpen = false

    override fun acceptConfig() {
        val previous = previousInput
        val current = inputForTasker.regular
        val changed = previous != null && listOf(previous.uuid, previous.serial, previous.ownership) !=
            listOf(current.uuid, current.serial, current.ownership)
        if (!changed || previous?.uuid.isNullOrBlank()) { saveSubscription(false); return }
        if (saveDialogOpen) return
        saveDialogOpen = true
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Keep the previous subscription?")
            .setMessage("If another Tasker profile still uses the previous watch/app settings, keep both. Otherwise replace the previous subscription.")
            .setPositiveButton("Replace previous") { _, _ -> saveSubscription(true) }
            .setNegativeButton("Keep both") { _, _ -> saveSubscription(false) }
            .setNeutralButton("Cancel", null)
            .setOnDismissListener { saveDialogOpen = false }
            .show()
    }

    private fun saveSubscription(retirePrevious: Boolean) {
        // A saved edit gets a fresh ID. Unedited copies are separated by full configuration.
        val current = inputForTasker.regular
        if (previousInput?.let { listOf(it.uuid, it.serial, it.ownership) != listOf(current.uuid, current.serial, current.ownership) } == true)
            subscriptionId = java.util.UUID.randomUUID().toString()
        super.acceptConfig()
        if (isFinishing) {
            if (retirePrevious) previousInput?.let { AppMessageSubscriptions.forget(this, it) }
            AppMessageSubscriptions.requestRestore(applicationContext)
        }
    }

    override val titleRes = R.string.pb_evt_appmsg_title
    override val descRes = R.string.pb_evt_appmsg_desc

    override fun buildFields() = listOf(
        FieldSpec("ownership", "Acknowledgement ownership", options = listOf(
            "Observe messages (companion owns ACK)" to "observe",
            "Tasker-only app (acknowledge messages)" to "tasker",
        )),
        FieldSpec("serial", getString(R.string.lbl_serial)),
        FieldSpec("uuid", getString(R.string.pb_evt_lbl_uuid), lookup = CriteriaDropdown.Source.LOCKER_ANY),
        FieldSpec("key", getString(R.string.pb_lbl_key)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<AppMessageFilter>) = AppMessageHelper(config)

    override fun buildInput(values: Map<String, String>): AppMessageFilter =
        AppMessageFilter(uuid = values["uuid"], key = values["key"], serial = values["serial"], ownership = values["ownership"].orEmpty().ifBlank { "observe" }, subscriptionId = subscriptionId)

    override fun extractValues(input: AppMessageFilter): Map<String, String> {
        previousInput = AppMessageFilter(input.ownership, input.subscriptionId, input.serial, input.uuid, input.key)
        subscriptionId = input.subscriptionId ?: subscriptionId
        return mapOf("uuid" to input.uuid.orEmpty(), "key" to input.key.orEmpty(), "serial" to input.serial.orEmpty(), "ownership" to input.ownership)
    }
}
