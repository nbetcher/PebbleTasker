package com.nickbether.pebbletasker.tasker.action.fwcheck

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.tasker.action.common.ActionResultOutput
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandHelper
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandRunner
import com.nickbether.pebbletasker.tasker.action.common.orNullIfBlank
import com.nickbether.pebbletasker.tasker.action.watchctl.RESULT_VARS
import com.nickbether.pebbletasker.tasker.action.watchctl.SERIAL_FIELD
import com.nickbether.pebbletasker.tasker.vars.PbVars

@TaskerInputRoot
class InstallFirmwareInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("version") var version: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class InstallFirmwareOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.FW_VERSION) @field:TaskerInputField("pb_fw_version") var version: String? = null,
    @get:TaskerOutputVariable(PbVars.STARTED) @field:TaskerInputField("pb_started") var started: String? = null,
) : ActionResultOutput()

class InstallFirmwareRunner : WatchCommandRunner<InstallFirmwareInput, InstallFirmwareOutput>() {
    override val command = Type.WATCH_INSTALL_FIRMWARE
    override fun watchOf(input: InstallFirmwareInput) = input.serial
    override fun args(input: InstallFirmwareInput) = input.version.orNullIfBlank()?.let { mapOf("version" to it) } ?: emptyMap()
    override fun newOutput() = InstallFirmwareOutput()
    override fun fill(output: InstallFirmwareOutput, data: Map<String, String>) {
        output.version = data["version"]; output.started = data["started"]
    }
}

class InstallFirmwareHelper(config: TaskerPluginConfig<InstallFirmwareInput>) :
    WatchCommandHelper<InstallFirmwareInput, InstallFirmwareOutput, InstallFirmwareRunner>(config) {
    override val inputClass = InstallFirmwareInput::class.java
    override val outputClass = InstallFirmwareOutput::class.java
    override val runnerClass = InstallFirmwareRunner::class.java
    override val defaultBlurb = "Pebble: Install Firmware"
    override fun blurbFor(input: InstallFirmwareInput) =
        withWatch("Install firmware" + (input.version.orNullIfBlank()?.let { " $it only" } ?: ""), input.serial)
}

class InstallFirmwareActivity : FormActionActivity<InstallFirmwareInput, InstallFirmwareOutput, InstallFirmwareRunner, InstallFirmwareHelper>() {
    override val formTitle = "Install Firmware Update"
    override val formDescription = "Starts installing the firmware found by the last Check Firmware Update. " +
        "FIRMWARE_CHECK_STALE means no successful check is known from the last 24 hours: run Check Firmware Update first. " +
        "FIRMWARE_UPDATE_UNAVAILABLE means that check found nothing. WATCH_BUSY means an update is already running."
    override val formWarning = "The watch restarts during the update and is unusable until it finishes. Keep it near the phone and charged."
    override val formOutputs = "Outputs: %pbl_fw_version, %pbl_started, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("version", "Only if the offered version is (optional)",
            helper = "Guard: the install is refused unless the offered version matches. Blank installs whatever was offered."),
    )
    override fun getNewHelper(config: TaskerPluginConfig<InstallFirmwareInput>) = InstallFirmwareHelper(config)
    override fun buildInput(values: Map<String, String>) = InstallFirmwareInput(values.opt("serial"), values.opt("version"))
    override fun extractValues(input: InstallFirmwareInput) = mapOf("serial" to input.serial.orEmpty(), "version" to input.version.orEmpty())
}
