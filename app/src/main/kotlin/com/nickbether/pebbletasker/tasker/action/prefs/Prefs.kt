package com.nickbether.pebbletasker.tasker.action.prefs

import android.content.Context
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.tasker.action.common.ActionResultOutput
import com.nickbether.pebbletasker.tasker.action.common.Args
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandHelper
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandRunner
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD
import com.nickbether.pebbletasker.tasker.prefs.PrefPicker
import com.nickbether.pebbletasker.tasker.prefs.WatchPrefs
import com.nickbether.pebbletasker.tasker.vars.PbVars

// ─────────────────────────────── List Preferences ───────────────────────────────

@TaskerInputRoot
class ListPrefsInput @JvmOverloads constructor(@field:TaskerInputField("serial") var serial: String? = null)

@TaskerInputRoot
@TaskerOutputObject
class ListPrefsOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.PREFS) @field:TaskerInputField("pb_prefs") var prefs: String? = null,
    @get:TaskerOutputVariable(PbVars.COUNT) @field:TaskerInputField("pb_count") var count: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_KEYS) @field:TaskerInputField("pb_pref_keys") var keys: Array<String>? = null,
    @get:TaskerOutputVariable(PbVars.PREF_LABELS) @field:TaskerInputField("pb_pref_labels") var labels: Array<String>? = null,
) : ActionResultOutput()

class ListPrefsRunner : WatchCommandRunner<ListPrefsInput, ListPrefsOutput>() {
    override val command = Type.WATCH_LIST_PREFS
    override fun watchOf(input: ListPrefsInput) = input.serial
    override fun newOutput() = ListPrefsOutput()
    override fun fill(output: ListPrefsOutput, data: Map<String, String>) {
        val parsed = WatchPrefs.parseList(data["prefs"])
        output.prefs = data["prefs"]
        output.count = data["count"] ?: parsed.size.toString()
        output.keys = parsed.map { it.key }.toTypedArray()
        output.labels = parsed.map { it.label }.toTypedArray()
    }
    override fun execute(context: Context, input: TaskerInput<ListPrefsInput>): BridgeResult<Map<String, String>> =
        super.execute(context, input).also { r ->
            if (r is BridgeResult.Ok) r.value["prefs"]?.let { WatchPrefs.store(context, input.regular.serial?.trim()?.takeIf { s -> s.isNotEmpty() && !s.contains('%') }, it) }
        }
}

class ListPrefsHelper(config: TaskerPluginConfig<ListPrefsInput>) : WatchCommandHelper<ListPrefsInput, ListPrefsOutput, ListPrefsRunner>(config) {
    override val inputClass = ListPrefsInput::class.java
    override val outputClass = ListPrefsOutput::class.java
    override val runnerClass = ListPrefsRunner::class.java
    override val defaultBlurb = "Pebble: List Preferences"
    override fun blurbFor(input: ListPrefsInput) = withWatch("List preferences", input.serial)
}

class ListPrefsActivity : FormActionActivity<ListPrefsInput, ListPrefsOutput, ListPrefsRunner, ListPrefsHelper>() {
    override val formTitle = "List Watch Preferences"
    override val formDescription = "Lists the watch settings the Pebble app manages, with each one's current value and whether " +
        "this watch supports it. After a firmware change keys can read \"unknown\" until the watch finishes a settings sync."
    override val formOutputs = "Outputs: %pbl_prefs (JSON array of {key,label,description,type,value,default,support,options,min,max,unit}), " +
        "%pbl_count, %pbl_pref_keys(), %pbl_pref_labels(), $RESULT_VARS"
    override fun buildFields() = listOf(SERIAL_FIELD)
    override fun getNewHelper(config: TaskerPluginConfig<ListPrefsInput>) = ListPrefsHelper(config)
    override fun buildInput(values: Map<String, String>) = ListPrefsInput(values.opt("serial"))
    override fun extractValues(input: ListPrefsInput) = mapOf("serial" to input.serial.orEmpty())
}

// ─────────────────────────────── Get Preference ───────────────────────────────

@TaskerInputRoot
class GetPrefInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("pref_key") var prefKey: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class GetPrefOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.PREF_KEY) @field:TaskerInputField("pb_pref_key") var key: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_LABEL) @field:TaskerInputField("pb_pref_label") var label: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_DESCRIPTION) @field:TaskerInputField("pb_pref_description") var description: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_TYPE) @field:TaskerInputField("pb_pref_type") var type: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_VALUE) @field:TaskerInputField("pb_pref_value") var value: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_DEFAULT) @field:TaskerInputField("pb_pref_default") var default: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_SUPPORT) @field:TaskerInputField("pb_pref_support") var support: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_OPTIONS) @field:TaskerInputField("pb_pref_options") var options: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_MIN) @field:TaskerInputField("pb_pref_min") var min: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_MAX) @field:TaskerInputField("pb_pref_max") var max: String? = null,
    @get:TaskerOutputVariable(PbVars.PREF_UNIT) @field:TaskerInputField("pb_pref_unit") var unit: String? = null,
) : ActionResultOutput()

class GetPrefRunner : WatchCommandRunner<GetPrefInput, GetPrefOutput>() {
    override val command = Type.WATCH_GET_PREF
    override fun watchOf(input: GetPrefInput) = input.serial
    override fun args(input: GetPrefInput) = mapOf("pref_key" to Args.required("pref_key", input.prefKey))
    override fun newOutput() = GetPrefOutput()
    override fun fill(output: GetPrefOutput, data: Map<String, String>) {
        output.key = data["pref_key"]; output.label = data["label"]; output.description = data["description"]
        output.type = data["type"]; output.value = data["value"]; output.default = data["default"]
        output.support = data["support"]; output.options = data["options"]; output.min = data["min"]
        output.max = data["max"]; output.unit = data["unit"]
    }
}

class GetPrefHelper(config: TaskerPluginConfig<GetPrefInput>) : WatchCommandHelper<GetPrefInput, GetPrefOutput, GetPrefRunner>(config) {
    override val inputClass = GetPrefInput::class.java
    override val outputClass = GetPrefOutput::class.java
    override val runnerClass = GetPrefRunner::class.java
    override val defaultBlurb = "Pebble: Get Preference"
    override fun blurbFor(input: GetPrefInput) = withWatch("Get pref ${input.prefKey?.trim()?.ifEmpty { null } ?: "(unset)"}", input.serial)
}

class GetPrefActivity : FormActionActivity<GetPrefInput, GetPrefOutput, GetPrefRunner, GetPrefHelper>() {
    override val formTitle = "Get Watch Preference"
    override val formDescription = "Reads one watch setting. A key this watch refuses returns PREF_UNSUPPORTED."
    override val formOutputs = "Outputs: %pbl_pref_value, %pbl_pref_label, %pbl_pref_description, %pbl_pref_type, %pbl_pref_default, " +
        "%pbl_pref_support, %pbl_pref_options (JSON), %pbl_pref_min, %pbl_pref_max, %pbl_pref_unit, %pbl_pref_key, $RESULT_VARS"
    override fun buildFields() = listOf(SERIAL_FIELD, FormActionActivity.Field("pref_key", "Preference"))
    override fun onFieldsBuilt(fields: Map<String, TextInputEditText>) {
        PrefPicker(this, fields.getValue("pref_key").layoutOf(), null, { fields["serial"]?.text?.toString() }).attach()
    }
    override fun getNewHelper(config: TaskerPluginConfig<GetPrefInput>) = GetPrefHelper(config)
    override fun buildInput(values: Map<String, String>) = GetPrefInput(values.opt("serial"), values.opt("pref_key"))
    override fun extractValues(input: GetPrefInput) = mapOf("serial" to input.serial.orEmpty(), "pref_key" to input.prefKey.orEmpty())
}

/** The TextInputLayout that owns a field built by GenericFieldBuilder. */
internal fun TextInputEditText.layoutOf(): TextInputLayout {
    var p = parent
    while (p != null && p !is TextInputLayout) p = p.parent
    return p as TextInputLayout
}
