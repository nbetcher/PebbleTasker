package com.nickbether.pebbletasker.tasker.action.fwcheck

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.BridgeClient
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.action.common.Args
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandHelper
import com.nickbether.pebbletasker.tasker.action.common.orNullIfBlank
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD
import com.nickbether.pebbletasker.tasker.base.FeatureSupport
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * Check Firmware Update. Uses `watch.checkFirmware` (status available/none/failed/pending) when the
 * Pebble app offers it, else the older `fw.check`. With force off, the last known result returns at once.
 * A config saved before the force field existed keeps forcing a fresh check, as before.
 */
@TaskerInputRoot
class FwCheckInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("force")
    var force: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class FwCheckOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_err_code")
    @get:TaskerOutputVariable(PbVars.ERR_CODE)
    val pbErrCode: String? = null,
    @field:TaskerInputField("pb_fw_available")
    @get:TaskerOutputVariable(PbVars.FW_AVAILABLE, labelResIdName = "lbl_out_fw_available")
    val fwAvailable: String? = null,
    @field:TaskerInputField("pb_fw_version")
    @get:TaskerOutputVariable(PbVars.FW_VERSION, labelResIdName = "lbl_out_fw_version")
    val fwVersion: String? = null,
    @field:TaskerInputField("pb_fw_status")
    @get:TaskerOutputVariable(PbVars.FW_STATUS)
    val fwStatus: String? = null,
)

class FwCheckRunner : PebbleActionRunner<FwCheckInput, FwCheckOutput>() {
    override fun execute(context: Context, input: TaskerInput<FwCheckInput>): BridgeResult<Map<String, String>> {
        val caps = BridgeClient.get(context).let { it.currentSession ?: it.awaitReadyBlocking().valueOrNull() }?.capabilities
        val command = FeatureSupport.supportedCommand(FwCheckRunner::class.java, caps) ?: CommandSender.Type.WATCH_CHECK_FIRMWARE
        if (command == CommandSender.Type.FW_CHECK) return ActionSend.send(context, command, watch = input.regular.serial)
        val force = try { Args.bool(input.regular.force, true) } catch (e: Args.Invalid) {
            return BridgeResult.Err(ErrCodes.INVALID_ARGS, e.message ?: "Invalid force value", "INVALID_ARGS")
        }
        return ActionSend.send(context, command, watch = input.regular.serial, args = if (force) mapOf("force" to "true") else emptyMap())
    }

    override fun buildOutput(input: TaskerInput<FwCheckInput>, result: CommandResult): FwCheckOutput {
        val status = ActionOutputs.data(result, "status")
        return FwCheckOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            pbErrCode = ActionOutputs.wireCodeStr(result),
            fwAvailable = ActionOutputs.data(result, "fw_available") ?: status?.let { (it == "available").toString() },
            fwVersion = ActionOutputs.data(result, "fw_version") ?: ActionOutputs.data(result, "version"),
            fwStatus = status,
        )
    }
}

class FwCheckHelper(config: TaskerPluginConfig<FwCheckInput>) :
    WatchCommandHelper<FwCheckInput, FwCheckOutput, FwCheckRunner>(config) {
    override val inputClass = FwCheckInput::class.java
    override val outputClass = FwCheckOutput::class.java
    override val runnerClass = FwCheckRunner::class.java
    override val defaultBlurb: String = "Pebble: Check Firmware Update"
    override fun blurbFor(input: FwCheckInput): String {
        val cached = input.force.orNullIfBlank()?.lowercase() in setOf("false", "off", "0", "no")
        return withWatch(if (cached) "Firmware check (last result)" else "Firmware check", input.serial)
    }
}

class FwCheckActivity : FormActionActivity<FwCheckInput, FwCheckOutput, FwCheckRunner, FwCheckHelper>() {
    override val formTitle = "Check Firmware Update"
    override val formDescription = "Asks the update server whether newer watch firmware exists. With \"Check now\" off, the last " +
        "known result returns at once. Install Firmware needs a successful check within the last 24 hours."
    override val formOutputs = "Outputs: %pbl_fw_status (available, none, failed or pending), %pbl_fw_version, %pbl_fw_available, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("force", "Check now (default true)", options = listOf("Check now" to "true", "Last known result" to "false")),
    )
    override fun getNewHelper(config: TaskerPluginConfig<FwCheckInput>) = FwCheckHelper(config)
    override fun buildInput(values: Map<String, String>) = FwCheckInput(values.opt("serial"), values.opt("force"))
    override fun extractValues(input: FwCheckInput) = mapOf("serial" to input.serial.orEmpty(), "force" to input.force.orEmpty())
}
