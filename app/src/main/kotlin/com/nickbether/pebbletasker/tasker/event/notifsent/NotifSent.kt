package com.nickbether.pebbletasker.tasker.event.notifsent

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
import com.nickbether.pebbletasker.tasker.event.EventSupport
import com.nickbether.pebbletasker.tasker.event.FilterMatch
import com.nickbether.pebbletasker.tasker.event.GenericEventConfigActivity
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * E5 — Pebble Notification Sent (COLLECTOR + consent). Capability-gated until the bridge emits
 * notif.sent.
 *
 * REDACTION GATE (FINAL DESIGN FIX A5): today's bridge hardcodes contentRedacted = true, so title/
 * text are NOT available. When redacted, the runner emits "(redacted)" for %pb_title/%pb_text rather
 * than blank, so a user task can tell "redacted" apart from "empty". On a future bridge that sets
 * contentRedacted = false (carried in the event data as `redacted`), the real content flows through.
 */
@TaskerInputRoot
class NotifSentFilter @JvmOverloads constructor(
    @field:TaskerInputField("pkg", labelResIdName = "pb_lbl_pkg")
    var pkg: String? = null,
    @field:TaskerInputField("app_name", labelResIdName = "pb_lbl_app_name")
    var appName: String? = null,
    @field:TaskerInputField("text_contains", labelResIdName = "pb_lbl_text_contains")
    var textContains: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class NotifSentOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.PKG)
    @field:TaskerInputField("pb_pkg")
    var pbPkg: String? = null,
    @get:TaskerOutputVariable(PbVars.APP_NAME)
    @field:TaskerInputField("pb_app_name")
    var pbAppName: String? = null,
    @get:TaskerOutputVariable(PbVars.TITLE)
    @field:TaskerInputField("pb_title")
    var pbTitle: String? = null,
    @get:TaskerOutputVariable(PbVars.TEXT)
    @field:TaskerInputField("pb_text")
    var pbText: String? = null,
    @get:TaskerOutputVariable(PbVars.CHANNEL_ID)
    @field:TaskerInputField("pb_channel_id")
    var pbChannelId: String? = null,
    @get:TaskerOutputVariable(PbVars.ACTIONS)
    @field:TaskerInputField("pb_actions")
    var pbActions: Array<String>? = null,
    @get:TaskerOutputVariable(PbVars.ACTIONS_COUNT)
    @field:TaskerInputField("pb_actions_count")
    var pbActionsCount: String? = null,
) : BaseEventOutput()

class NotifSentRunner : PebbleEventRunner<NotifSentFilter, NotifSentOutput>() {
    override val eventType: String = EventRouting.TYPE_NOTIF_SENT

    override fun evaluate(
        context: Context,
        filter: NotifSentFilter,
        cached: CachedEvent?,
        update: NotifSentOutput?,
    ): TaskerPluginResultCondition<NotifSentOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val pkg = e.str("pkg")
        val appName = e.str("app_name")
        val redacted = e.bool("redacted") ?: true
        val title = if (redacted) "(redacted)" else e.str("title").orEmpty()
        val text = if (redacted) "(redacted)" else e.str("text").orEmpty()

        if (!FilterMatch.eq(filter.pkg, pkg)) return TaskerPluginResultConditionUnsatisfied()
        if (!FilterMatch.contains(filter.appName, appName)) return TaskerPluginResultConditionUnsatisfied()
        // text_contains can only match when content is NOT redacted.
        if (!filter.textContains.isNullOrBlank()) {
            if (redacted || !FilterMatch.contains(filter.textContains, text)) {
                return TaskerPluginResultConditionUnsatisfied()
            }
        }

        // Bridge encodes the action list as a unit-separator (U+001F) joined string.
        val actions = e.str("actions")
            ?.split('')
            ?.filter { it.isNotBlank() }
            ?.toTypedArray()
            ?: emptyArray()
        val out = NotifSentOutput(
            pbPkg = pkg,
            pbAppName = appName,
            pbTitle = title,
            pbText = text,
            pbChannelId = e.str("channel_id"),
            pbActions = actions,
            pbActionsCount = actions.size.toString(),
        ).fillBase<NotifSentOutput>(
            e,
            buildMap {
                pkg?.let { put("pkg", it) }
                appName?.let { put("app_name", it) }
                put("title", title)
                put("text", text)
                put("actions_count", actions.size.toString())
            },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class NotifSentHelper(config: TaskerPluginConfig<NotifSentFilter>) :
    PebbleEventHelper<NotifSentFilter, NotifSentOutput, NotifSentRunner>(config) {
    override val inputClass = NotifSentFilter::class.java
    override val outputClass = NotifSentOutput::class.java
    override val runnerClass = NotifSentRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<NotifSentFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when a notification is sent to the watch.")
            .append("\nOutputs: %pb_pkg %pb_app_name %pb_title %pb_text %pb_channel_id")
            .append(" %pb_actions() %pb_actions_count + %pb_json.")
            .append("\nNote: title/text are \"(redacted)\" unless the Pebble app allows content.")
    }
}

class NotifSentActivity :
    GenericEventConfigActivity<NotifSentFilter, NotifSentOutput, NotifSentRunner, NotifSentHelper>() {

    override val titleRes = R.string.pb_evt_notifsent_title
    override val descRes = R.string.pb_evt_notifsent_desc

    override fun buildFields() = listOf(
        FieldSpec("pkg", getString(R.string.pb_lbl_pkg)),
        FieldSpec("app_name", getString(R.string.pb_lbl_app_name)),
        FieldSpec("text_contains", getString(R.string.pb_lbl_text_contains)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<NotifSentFilter>) = NotifSentHelper(config)

    override fun buildInput(values: Map<String, String>) = NotifSentFilter(
        pkg = values["pkg"],
        appName = values["app_name"],
        textContains = values["text_contains"],
    )

    override fun extractValues(input: NotifSentFilter) = mapOf(
        "pkg" to input.pkg.orEmpty(),
        "app_name" to input.appName.orEmpty(),
        "text_contains" to input.textContains.orEmpty(),
    )
}
