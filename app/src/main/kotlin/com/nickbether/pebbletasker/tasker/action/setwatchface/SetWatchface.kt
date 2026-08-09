package com.nickbether.pebbletasker.tasker.action.setwatchface

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
import com.nickbether.pebbletasker.tasker.action.common.SerialUuidActionActivity
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A4 — Set Watchface (FINAL DESIGN §2.3, normal tier). Sends `watch.setWatchface` (uuid required).
 */

@TaskerInputRoot
class SetWatchfaceInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("uuid", labelResIdName = "lbl_uuid")
    var uuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class SetWatchfaceOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_uuid")
    @get:TaskerOutputVariable(PbVars.UUID, labelResIdName = "lbl_uuid")
    val uuid: String? = null,
)

class SetWatchfaceRunner : PebbleActionRunner<SetWatchfaceInput, SetWatchfaceOutput>() {
    override fun execute(context: Context, input: TaskerInput<SetWatchfaceInput>): BridgeResult<Map<String, String>> {
        val uuid = input.regular.uuid?.trim().orEmpty()
        if (uuid.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "uuid is required")
        return ActionSend.send(
            context,
            CommandSender.Type.WATCH_SET_WATCHFACE,
            watch = input.regular.serial,
            args = mapOf("uuid" to uuid),
        )
    }

    override fun buildOutput(input: TaskerInput<SetWatchfaceInput>, result: CommandResult): SetWatchfaceOutput =
        SetWatchfaceOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            uuid = ActionOutputs.data(result, "uuid") ?: input.regular.uuid,
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class SetWatchfaceHelper(config: TaskerPluginConfig<SetWatchfaceInput>) :
    ActionHelper<SetWatchfaceInput, SetWatchfaceOutput, SetWatchfaceRunner>(config) {
    override val inputClass = SetWatchfaceInput::class.java
    override val outputClass = SetWatchfaceOutput::class.java
    override val runnerClass = SetWatchfaceRunner::class.java
    override val defaultBlurb: String = "Pebble: Set Watchface"
    override fun blurbFor(input: SetWatchfaceInput): String =
        "Set watchface: ${input.uuid?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class SetWatchfaceActivity :
    SerialUuidActionActivity<SetWatchfaceInput, SetWatchfaceOutput, SetWatchfaceRunner, SetWatchfaceHelper>() {
    override val titleRes = R.string.act_set_watchface_title
    override val descRes = R.string.act_set_watchface_desc

    // Setting a watchface expects a face UUID -> list (and validate against) the locker's watchfaces.
    override val uuidLookup = CriteriaDropdown.Source.LOCKER_FACE
    override fun getNewHelper(config: TaskerPluginConfig<SetWatchfaceInput>) = SetWatchfaceHelper(config)
    override fun makeInput(serial: String?, uuid: String?) = SetWatchfaceInput(serial = serial, uuid = uuid)
    override fun serialOf(input: SetWatchfaceInput) = input.serial
    override fun uuidOf(input: SetWatchfaceInput) = input.uuid
}
