package com.nickbether.pebbletasker.tasker.action.insertpin

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.action.common.GenericFieldsActionActivity
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A8 — Insert Timeline Pin (FINAL DESIGN §2.3, normal tier).
 * Sends `timeline.insert` with a JSON pin definition (pin_json) and optional pin_uuid override.
 * Bridge success `data`: pin_uuid.
 */

@TaskerInputRoot
class InsertPinInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("pin_json", labelResIdName = "lbl_pin_json")
    var pinJson: String? = null,
    @field:TaskerInputField("pin_uuid", labelResIdName = "lbl_pin_uuid")
    var pinUuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class InsertPinOutput @JvmOverloads constructor(
    @field:TaskerInputField("pb_json")
    @get:TaskerOutputVariable(PbVars.JSON, labelResIdName = "lbl_out_json")
    val pbJson: String? = null,
    @field:TaskerInputField("pb_ok")
    @get:TaskerOutputVariable(PbVars.OK, labelResIdName = "lbl_out_ok")
    val pbOk: String? = null,
    @field:TaskerInputField("pb_err")
    @get:TaskerOutputVariable(PbVars.ERR, labelResIdName = "lbl_out_err")
    val pbErr: String? = null,
    @field:TaskerInputField("pb_errmsg")
    @get:TaskerOutputVariable(PbVars.ERRMSG, labelResIdName = "lbl_out_errmsg")
    val pbErrmsg: String? = null,
    @field:TaskerInputField("pb_pin_uuid")
    @get:TaskerOutputVariable(PbVars.PIN_UUID, labelResIdName = "lbl_out_pin_uuid")
    val pinUuid: String? = null,
)

class InsertPinRunner : PebbleActionRunner<InsertPinInput, InsertPinOutput>() {
    override fun execute(context: Context, input: TaskerInput<InsertPinInput>): BridgeResult<Map<String, String>> {
        val pin = input.regular.pinJson?.trim().orEmpty()
        if (pin.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "pin_json is required")
        val args = HashMap<String, String>()
        args["pin_json"] = pin
        input.regular.pinUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { args["pin_uuid"] = it }
        return ActionSend.send(context, CommandSender.Type.TIMELINE_INSERT, watch = input.regular.serial, args = args)
    }

    override fun buildOutput(input: TaskerInput<InsertPinInput>, result: CommandResult): InsertPinOutput =
        InsertPinOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            pinUuid = ActionOutputs.data(result, "pin_uuid") ?: input.regular.pinUuid,
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class InsertPinHelper(config: TaskerPluginConfig<InsertPinInput>) :
    ActionHelper<InsertPinInput, InsertPinOutput, InsertPinRunner>(config) {
    override val inputClass = InsertPinInput::class.java
    override val outputClass = InsertPinOutput::class.java
    override val runnerClass = InsertPinRunner::class.java
    override val defaultBlurb: String = "Pebble: Insert Timeline Pin"
    override fun blurbFor(input: InsertPinInput): String =
        "Insert pin${input.pinUuid?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
}

class InsertPinActivity :
    GenericFieldsActionActivity<InsertPinInput, InsertPinOutput, InsertPinRunner, InsertPinHelper>() {
    override val titleRes = R.string.act_pin_insert_title
    override val descRes = R.string.act_pin_insert_desc
    override val hintRes = R.string.hint_json_value
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_pin_json, multiline = true),
        FieldSpec(R.string.lbl_pin_uuid, lookup = CriteriaDropdown.Source.LOCKER_ANY),
    )
    override fun getNewHelper(config: TaskerPluginConfig<InsertPinInput>) = InsertPinHelper(config)
    override fun makeInput(values: List<String?>) =
        InsertPinInput(serial = values.getOrNull(0), pinJson = values.getOrNull(1), pinUuid = values.getOrNull(2))
    override fun valuesOf(input: InsertPinInput) = listOf(input.serial, input.pinJson, input.pinUuid)
}
