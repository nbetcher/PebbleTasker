package com.nickbether.pebbletasker.tasker.action.deletepin

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
 * A9 — Delete Timeline Pin (FINAL DESIGN §2.3, normal tier). Sends `timeline.delete` (pin_uuid).
 */

@TaskerInputRoot
class DeletePinInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("pin_uuid", labelResIdName = "lbl_pin_uuid")
    var pinUuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class DeletePinOutput @JvmOverloads constructor(
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

class DeletePinRunner : PebbleActionRunner<DeletePinInput, DeletePinOutput>() {
    override fun execute(context: Context, input: TaskerInput<DeletePinInput>): BridgeResult<Map<String, String>> {
        val uuid = input.regular.pinUuid?.trim().orEmpty()
        if (uuid.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "pin_uuid is required")
        return ActionSend.send(
            context,
            CommandSender.Type.TIMELINE_DELETE,
            watch = input.regular.serial,
            args = mapOf("pin_uuid" to uuid),
        )
    }

    override fun buildOutput(input: TaskerInput<DeletePinInput>, result: CommandResult): DeletePinOutput =
        DeletePinOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            pinUuid = ActionOutputs.data(result, "pin_uuid") ?: input.regular.pinUuid,
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class DeletePinHelper(config: TaskerPluginConfig<DeletePinInput>) :
    ActionHelper<DeletePinInput, DeletePinOutput, DeletePinRunner>(config) {
    override val inputClass = DeletePinInput::class.java
    override val outputClass = DeletePinOutput::class.java
    override val runnerClass = DeletePinRunner::class.java
    override val defaultBlurb: String = "Pebble: Delete Timeline Pin"
    override fun blurbFor(input: DeletePinInput): String =
        "Delete pin: ${input.pinUuid?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class DeletePinActivity :
    GenericFieldsActionActivity<DeletePinInput, DeletePinOutput, DeletePinRunner, DeletePinHelper>() {
    override val titleRes = R.string.act_pin_delete_title
    override val descRes = R.string.act_pin_delete_desc
    override val fields = listOf(
        FieldSpec(R.string.lbl_serial, isSerial = true),
        FieldSpec(R.string.lbl_pin_uuid, lookup = CriteriaDropdown.Source.LOCKER_ANY),
    )
    override fun getNewHelper(config: TaskerPluginConfig<DeletePinInput>) = DeletePinHelper(config)
    override fun makeInput(values: List<String?>) =
        DeletePinInput(serial = values.getOrNull(0), pinUuid = values.getOrNull(1))
    override fun valuesOf(input: DeletePinInput) = listOf(input.serial, input.pinUuid)
}
