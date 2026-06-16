package com.nickbether.pebbletasker.tasker.event.notifaction

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
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.base.PebbleEventHelper
import com.nickbether.pebbletasker.tasker.base.PebbleEventRunner
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput
import com.nickbether.pebbletasker.tasker.event.EventRouting
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E6 — Pebble Notification Action (COLLECTOR). Fires when the user acts on a watch notification
 * (dismiss / open / quick-reply). This is also the round-trip closer for custom Send-Notification
 * actions: their presses re-emit as notif.action. Capability-gated until the bridge emits it.
 */
@TaskerInputRoot
class NotifActionFilter @JvmOverloads constructor(
    @field:TaskerInputField("action", labelResIdName = "pb_lbl_action")
    var action: String? = null,
    @field:TaskerInputField("pkg", labelResIdName = "pb_lbl_pkg")
    var pkg: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class NotifActionOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.ACTION)
    @field:TaskerInputField("pb_action")
    var pbAction: String? = null,
    @get:TaskerOutputVariable(PbVars.ACTION_ID)
    @field:TaskerInputField("pb_action_id")
    var pbActionId: String? = null,
    @get:TaskerOutputVariable(PbVars.REPLY_TEXT)
    @field:TaskerInputField("pb_reply_text")
    var pbReplyText: String? = null,
    @get:TaskerOutputVariable(PbVars.PKG)
    @field:TaskerInputField("pb_pkg")
    var pbPkg: String? = null,
) : BaseEventOutput()

class NotifActionRunner : PebbleEventRunner<NotifActionFilter, NotifActionOutput>() {
    override val eventType: String = EventRouting.TYPE_NOTIF_ACTION

    override fun evaluate(
        context: Context,
        filter: NotifActionFilter,
        cached: CachedEvent?,
        update: NotifActionOutput?,
    ): TaskerPluginResultCondition<NotifActionOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val action = e.str("action")
        val pkg = e.str("pkg")
        if (!FilterMatch.eq(filter.action, action)) return TaskerPluginResultConditionUnsatisfied()
        if (!FilterMatch.eq(filter.pkg, pkg)) return TaskerPluginResultConditionUnsatisfied()
        val out = NotifActionOutput(
            pbAction = action,
            pbActionId = e.str("action_id"),
            pbReplyText = e.str("reply_text"),
            pbPkg = pkg,
        ).fillBase<NotifActionOutput>(
            e,
            buildMap {
                action?.let { put("action", it) }
                e.str("action_id")?.let { put("action_id", it) }
                e.str("reply_text")?.let { put("reply_text", it) }
                pkg?.let { put("pkg", it) }
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class NotifActionHelper(config: TaskerPluginConfig<NotifActionFilter>) :
    PebbleEventHelper<NotifActionFilter, NotifActionOutput, NotifActionRunner>(config) {
    override val inputClass = NotifActionFilter::class.java
    override val outputClass = NotifActionOutput::class.java
    override val runnerClass = NotifActionRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<NotifActionFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when a notification action is taken on the watch.")
            .append("\nOutputs: %pb_action %pb_action_id %pb_reply_text %pb_pkg + %pb_json.")
    }
}

class NotifActionActivity :
    GenericEventConfigActivity<NotifActionFilter, NotifActionOutput, NotifActionRunner, NotifActionHelper>() {

    override val titleRes = R.string.pb_evt_notifaction_title
    override val descRes = R.string.pb_evt_notifaction_desc

    override fun buildFields() = listOf(
        FieldSpec("action", getString(R.string.pb_lbl_action)),
        FieldSpec("pkg", getString(R.string.pb_lbl_pkg)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<NotifActionFilter>) = NotifActionHelper(config)

    override fun buildInput(values: Map<String, String>) =
        NotifActionFilter(action = values["action"], pkg = values["pkg"])

    override fun extractValues(input: NotifActionFilter) =
        mapOf("action" to input.action.orEmpty(), "pkg" to input.pkg.orEmpty())
}
