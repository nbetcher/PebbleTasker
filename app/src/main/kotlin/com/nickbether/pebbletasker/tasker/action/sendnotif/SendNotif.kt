package com.nickbether.pebbletasker.tasker.action.sendnotif

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
import com.nickbether.pebbletasker.databinding.ActivityActionSendNotifBinding
import com.nickbether.pebbletasker.tasker.action.common.ActionConfigActivity
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * A2 — Send Watch Notification (FINAL DESIGN §2.3, normal tier, SENSITIVE config UI).
 *
 * The headline ROUND-TRIP action. Sends `notification.send`. Custom actions (a JSON array of
 * {id,label,type} in `actions_json`) re-emit as `notif.action` events (E6) when pressed on the watch,
 * closing the round-trip without a callback API.
 *
 * All fields are %var-capable; `vibe` has a segmented convenience that writes into the editable
 * `editVibe` field (the source of truth, FIX crit-1 #5).
 *
 * Bridge success `data`: item_id, delivered -> %pb_item_id / %pb_delivered.
 */

@TaskerInputRoot
class SendNotifInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("title", labelResIdName = "lbl_title")
    var title: String? = null,
    @field:TaskerInputField("body", labelResIdName = "lbl_body")
    var body: String? = null,
    @field:TaskerInputField("subtitle", labelResIdName = "lbl_subtitle")
    var subtitle: String? = null,
    @field:TaskerInputField("icon", labelResIdName = "lbl_icon")
    var icon: String? = null,
    @field:TaskerInputField("vibe", labelResIdName = "lbl_vibe")
    var vibe: String? = "short",
    @field:TaskerInputField("actions_json", labelResIdName = "lbl_actions_json")
    var actionsJson: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class SendNotifOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_item_id")
    @get:TaskerOutputVariable(PbVars.ITEM_ID, labelResIdName = "lbl_out_item_id")
    val itemId: String? = null,
    @field:TaskerInputField("pb_delivered")
    @get:TaskerOutputVariable(PbVars.DELIVERED, labelResIdName = "lbl_out_delivered")
    val delivered: String? = null,
)

class SendNotifRunner : PebbleActionRunner<SendNotifInput, SendNotifOutput>() {
    override fun execute(context: Context, input: TaskerInput<SendNotifInput>): BridgeResult<Map<String, String>> {
        val r = input.regular
        val args = HashMap<String, String>()
        r.title?.takeIf { it.isNotBlank() }?.let { args["title"] = it }
        r.body?.takeIf { it.isNotBlank() }?.let { args["body"] = it }
        r.subtitle?.takeIf { it.isNotBlank() }?.let { args["subtitle"] = it }
        r.icon?.takeIf { it.isNotBlank() }?.let { args["icon"] = it }
        r.vibe?.takeIf { it.isNotBlank() }?.let { args["vibe"] = it }
        r.actionsJson?.takeIf { it.isNotBlank() }?.let { args["actions_json"] = it }
        return ActionSend.send(context, CommandSender.Type.NOTIFICATION_SEND, watch = r.serial, args = args)
    }

    override fun buildOutput(input: TaskerInput<SendNotifInput>, result: CommandResult): SendNotifOutput =
        SendNotifOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            itemId = ActionOutputs.data(result, "item_id"),
            delivered = ActionOutputs.data(result, "delivered"),
        )
}

class SendNotifHelper(config: TaskerPluginConfig<SendNotifInput>) :
    ActionHelper<SendNotifInput, SendNotifOutput, SendNotifRunner>(config) {
    override val inputClass = SendNotifInput::class.java
    override val outputClass = SendNotifOutput::class.java
    override val runnerClass = SendNotifRunner::class.java
    override val defaultBlurb: String = "Pebble: Send Watch Notification"
    override fun blurbFor(input: SendNotifInput): String {
        val t = input.title?.takeIf { it.isNotBlank() } ?: input.body?.takeIf { it.isNotBlank() } ?: "notification"
        return "Notify watch: $t"
    }
}

class SendNotifActivity :
    ActionConfigActivity<SendNotifInput, SendNotifOutput, SendNotifRunner, SendNotifHelper, ActivityActionSendNotifBinding>() {

    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionSendNotifBinding =
        ActivityActionSendNotifBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<SendNotifInput>) = SendNotifHelper(config)

    override fun onConfigCreated(binding: ActivityActionSendNotifBinding) {
        super.onConfigCreated(binding)
        wireWatchBrowse(binding.layoutSerial)
        binding.toggleVibe.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val literal = when (checkedId) {
                R.id.btnVibeNone -> "none"
                R.id.btnVibeShort -> "short"
                R.id.btnVibeLong -> "long"
                R.id.btnVibeDouble -> "double"
                else -> return@addOnButtonCheckedListener
            }
            binding.editVibe.setText(literal)
            binding.editVibe.setSelection(literal.length)
        }
    }

    override fun assignFromInput(input: TaskerInput<SendNotifInput>) {
        val r = input.regular
        binding?.let { b ->
            b.editSerial.setText(r.serial)
            b.editTitle.setText(r.title)
            b.editBody.setText(r.body)
            b.editSubtitle.setText(r.subtitle)
            b.editIcon.setText(r.icon)
            b.editVibe.setText(r.vibe)
            b.editActions.setText(r.actionsJson)
            when (r.vibe?.trim()?.lowercase()) {
                "none" -> b.toggleVibe.check(R.id.btnVibeNone)
                "short" -> b.toggleVibe.check(R.id.btnVibeShort)
                "long" -> b.toggleVibe.check(R.id.btnVibeLong)
                "double" -> b.toggleVibe.check(R.id.btnVibeDouble)
                else -> b.toggleVibe.clearChecked() // %var or custom -> detach
            }
        }
    }

    override val inputForTasker: TaskerInput<SendNotifInput>
        get() = TaskerInput(
            SendNotifInput(
                serial = binding?.editSerial?.text?.toString()?.ifBlank { null },
                title = binding?.editTitle?.text?.toString()?.ifBlank { null },
                body = binding?.editBody?.text?.toString()?.ifBlank { null },
                subtitle = binding?.editSubtitle?.text?.toString()?.ifBlank { null },
                icon = binding?.editIcon?.text?.toString()?.ifBlank { null },
                vibe = binding?.editVibe?.text?.toString()?.ifBlank { null },
                actionsJson = binding?.editActions?.text?.toString()?.ifBlank { null },
            ),
        )
}
