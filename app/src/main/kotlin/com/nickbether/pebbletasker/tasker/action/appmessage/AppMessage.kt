package com.nickbether.pebbletasker.tasker.action.appmessage

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
import com.nickbether.pebbletasker.databinding.ActivityActionAppmessageBinding
import com.nickbether.pebbletasker.tasker.ErrCodes
import com.nickbether.pebbletasker.tasker.action.common.ActionConfigActivity
import com.nickbether.pebbletasker.tasker.action.common.ActionHelper
import com.nickbether.pebbletasker.tasker.action.common.ActionOutputs
import com.nickbether.pebbletasker.tasker.action.common.ActionSend
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleActionRunner
import com.nickbether.pebbletasker.tasker.vars.PbVars
import org.json.JSONObject

/** Send typed AppMessage JSON without flattening or coercing strings. The bridge validates tuple
 * types again and preserves empty strings, unsigned integers and byte arrays.
 */

@TaskerInputRoot
class AppMessageInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("uuid", labelResIdName = "lbl_uuid")
    var uuid: String? = null,
    @field:TaskerInputField("dict_json", labelResIdName = "lbl_dict_json")
    var dictJson: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject()
class AppMessageOutput @JvmOverloads constructor(
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
    @field:TaskerInputField("pb_delivered")
    @get:TaskerOutputVariable(PbVars.DELIVERED, labelResIdName = "lbl_out_acked")
    val delivered: String? = null,
)

class AppMessageRunner : PebbleActionRunner<AppMessageInput, AppMessageOutput>() {
    override fun execute(context: Context, input: TaskerInput<AppMessageInput>): BridgeResult<Map<String, String>> {
        val uuid = input.regular.uuid?.trim().orEmpty()
        if (uuid.isEmpty()) return BridgeResult.err(ErrCodes.INVALID_ARGS, "uuid is required")
        val args = HashMap<String, String>()
        args["uuid"] = uuid
        val dict = input.regular.dictJson?.trim()
        if (!dict.isNullOrEmpty()) {
            val parsed = runCatching { flattenDict(dict) }.getOrElse {
                return BridgeResult.err(ErrCodes.INVALID_ARGS, "dict_json is not a valid JSON object")
            }
            args["dict_json"] = parsed
        }
        return ActionSend.send(context, CommandSender.Type.APPMESSAGE_SEND, watch = input.regular.serial, args = args)
    }

    /** Validate typed tuples without coercing numeric strings, empty strings, uints or byte arrays. */
    internal fun flattenDict(json: String): String = TypedAppMessage.validate(json)

    override fun buildOutput(input: TaskerInput<AppMessageInput>, result: CommandResult): AppMessageOutput =
        AppMessageOutput(
            pbJson = ActionOutputs.jsonBlob(result),
            pbOk = ActionOutputs.okStr(result),
            pbErr = ActionOutputs.errStr(result),
            pbErrmsg = ActionOutputs.errMsgStr(result),
            uuid = ActionOutputs.data(result, "uuid") ?: input.regular.uuid,
            delivered = ActionOutputs.data(result, "acked"),
        )

    override fun isHardFailure(code: Int): Boolean =
        code != ErrCodes.INVALID_ARGS && super.isHardFailure(code)
}

class AppMessageHelper(config: TaskerPluginConfig<AppMessageInput>) :
    ActionHelper<AppMessageInput, AppMessageOutput, AppMessageRunner>(config) {
    override val inputClass = AppMessageInput::class.java
    override val outputClass = AppMessageOutput::class.java
    override val runnerClass = AppMessageRunner::class.java
    override val defaultBlurb: String = "Pebble: Send AppMessage"
    override fun blurbFor(input: AppMessageInput): String =
        "AppMessage -> ${input.uuid?.takeIf { it.isNotBlank() } ?: "(unset)"}"
}

class AppMessageActivity :
    ActionConfigActivity<AppMessageInput, AppMessageOutput, AppMessageRunner, AppMessageHelper, ActivityActionAppmessageBinding>() {

    override val isSensitive = false // FLAG_SECURE candidate; intentionally off (flip to true to enable)

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionAppmessageBinding =
        ActivityActionAppmessageBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<AppMessageInput>) = AppMessageHelper(config)

    override fun onConfigCreated(binding: ActivityActionAppmessageBinding) {
        super.onConfigCreated(binding)
        wireWatchBrowse(binding.layoutSerial)
        CriteriaDropdown.attach(binding.layoutUuid, CriteriaDropdown.Source.LOCKER_ANY)
    }

    override fun assignFromInput(input: TaskerInput<AppMessageInput>) {
        binding?.editSerial?.setText(input.regular.serial)
        binding?.editUuid?.setText(input.regular.uuid)
        binding?.editDict?.setText(input.regular.dictJson)
    }

    override val inputForTasker: TaskerInput<AppMessageInput>
        get() = TaskerInput(
            AppMessageInput(
                serial = binding?.editSerial?.text?.toString()?.ifBlank { null },
                uuid = binding?.editUuid?.text?.toString()?.ifBlank { null },
                dictJson = binding?.editDict?.text?.toString()?.ifBlank { null },
            ),
        )
}
