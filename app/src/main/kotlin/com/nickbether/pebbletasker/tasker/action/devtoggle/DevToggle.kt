package com.nickbether.pebbletasker.tasker.action.devtoggle

import android.content.Context
import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.R
import com.nickbether.pebbletasker.bridge.BridgeResult
import com.nickbether.pebbletasker.bridge.CommandSender
import com.nickbether.pebbletasker.databinding.ActivityActionDevToggleBinding
import com.nickbether.pebbletasker.tasker.action.common.ActionConfigActivity
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A17 — Toggle Developer Connection (FINAL DESIGN §2.3, DANGEROUS tier).
 *
 * Sends `dev.toggleConnection`. The bridge ALSO requires its app-wide developer-commands toggle ON;
 * otherwise it returns NOT_AUTHORIZED (surfaced as %pbl_ok=false).
 *
 * `enable` accepts true/false/on/off/1/0, or is omitted to flip. The config exposes a segmented
 * On/Off/Toggle group that WRITES INTO the editable `editEnable` field (which stays %var-capable and
 * is the source of truth) — the toggle detaches when the field holds a %var (FIX crit-1 #5).
 *
 * Bridge success `data`: dev_enabled ("true"/"false").
 */

@TaskerInputRoot
class DevToggleInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("enable", labelResIdName = "lbl_enable")
    var enable: String? = null,
    @field:TaskerInputField("transport", labelResIdName = "lbl_transport")
    var transport: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class DevToggleOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_dev_enabled")
    @get:TaskerOutputVariable(PbVars.DEV_ENABLED, labelResIdName = "lbl_out_dev_enabled")
    val devEnabled: String? = null,
)

class DevToggleRunner : PebbleActionRunner<DevToggleInput, DevToggleOutput>() {
    override fun execute(context: Context, input: TaskerInput<DevToggleInput>): BridgeResult<Map<String, String>> {
        val args = HashMap<String, String>()
        input.regular.enable?.trim()?.takeIf { it.isNotEmpty() }?.let { args["enable"] = it }
        input.regular.transport?.trim()?.takeIf { it.isNotEmpty() }?.let { args["transport"] = it }
        return ActionSend.send(
            context,
            CommandSender.Type.DEV_TOGGLE_CONNECTION,
            watch = input.regular.serial,
            args = args,
        )
    }

    override fun buildOutput(input: TaskerInput<DevToggleInput>, result: CommandResult): DevToggleOutput =
        DevToggleOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            devEnabled = ActionOutputs.data(result, "dev_enabled"),
        )
}

class DevToggleHelper(config: TaskerPluginConfig<DevToggleInput>) :
    ActionHelper<DevToggleInput, DevToggleOutput, DevToggleRunner>(config) {
    override val inputClass = DevToggleInput::class.java
    override val outputClass = DevToggleOutput::class.java
    override val runnerClass = DevToggleRunner::class.java
    override val defaultBlurb: String = "Pebble: Toggle Dev Connection"
    override fun blurbFor(input: DevToggleInput): String {
        val verb = when (input.enable?.trim()?.lowercase()) {
            "true", "on", "1" -> "Enable"
            "false", "off", "0" -> "Disable"
            null, "" -> "Toggle"
            else -> "Set"
        }
        return "$verb dev connection"
    }
}

class DevToggleActivity :
    ActionConfigActivity<DevToggleInput, DevToggleOutput, DevToggleRunner, DevToggleHelper, ActivityActionDevToggleBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionDevToggleBinding =
        ActivityActionDevToggleBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<DevToggleInput>) = DevToggleHelper(config)

    override fun onConfigCreated(binding: ActivityActionDevToggleBinding) {
        super.onConfigCreated(binding)
        wireWatchBrowse(binding.layoutSerial)
        // Segment selection writes a literal into the editable `enable` field (the source of truth).
        binding.toggleEnable.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val literal = when (checkedId) {
                R.id.btnEnableOn -> "true"
                R.id.btnEnableOff -> "false"
                else -> "" // Toggle = omit
            }
            binding.editEnable.setText(literal)
            binding.editEnable.setSelection(literal.length)
        }
    }

    override fun assignFromInput(input: TaskerInput<DevToggleInput>) {
        binding?.editSerial?.setText(input.regular.serial)
        binding?.editEnable?.setText(input.regular.enable)
        binding?.editTransport?.setText(input.regular.transport)
        // Reflect the saved value onto the segmented group when it's a known literal.
        binding?.let { b ->
            when (input.regular.enable?.trim()?.lowercase()) {
                "true", "on", "1" -> b.toggleEnable.check(R.id.btnEnableOn)
                "false", "off", "0" -> b.toggleEnable.check(R.id.btnEnableOff)
                null, "" -> b.toggleEnable.check(R.id.btnEnableToggle)
                else -> b.toggleEnable.clearChecked() // a %var -> detach the toggle
            }
        }
    }

    override val inputForTasker: TaskerInput<DevToggleInput>
        get() = TaskerInput(
            DevToggleInput(
                serial = binding?.editSerial?.text?.toString()?.ifBlank { null },
                enable = binding?.editEnable?.text?.toString()?.ifBlank { null },
                transport = binding?.editTransport?.text?.toString()?.ifBlank { null },
            ),
        )
}
