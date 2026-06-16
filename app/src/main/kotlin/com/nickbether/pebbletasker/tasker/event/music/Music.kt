package com.nickbether.pebbletasker.tasker.event.music

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
 * E10 — Pebble Music Command (COLLECTOR). Fires when the watch sends a media control command
 * (play/pause/next/prev/volume). Backing type media.command. Capability-gated.
 */
@TaskerInputRoot
class MusicFilter @JvmOverloads constructor(
    @field:TaskerInputField("music_action", labelResIdName = "pb_lbl_music_action")
    var musicAction: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class MusicOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.MUSIC_ACTION)
    @field:TaskerInputField("pb_music_action")
    var pbMusicAction: String? = null,
) : BaseEventOutput()

class MusicRunner : PebbleEventRunner<MusicFilter, MusicOutput>() {
    override val eventType: String = EventRouting.TYPE_MUSIC

    override fun evaluate(
        context: Context,
        filter: MusicFilter,
        cached: CachedEvent?,
        update: MusicOutput?,
    ): TaskerPluginResultCondition<MusicOutput> {
        val e = cached ?: return TaskerPluginResultConditionUnknown()
        val action = e.str("music_action") ?: e.str("action")
        if (!FilterMatch.eq(filter.musicAction, action)) return TaskerPluginResultConditionUnsatisfied()
        val out = MusicOutput(pbMusicAction = action).fillBase<MusicOutput>(
            e,
            buildMap { action?.let { put("music_action", it) } },
        )
        return TaskerPluginResultConditionSatisfied(context, out)
    }
}

class MusicHelper(config: TaskerPluginConfig<MusicFilter>) :
    PebbleEventHelper<MusicFilter, MusicOutput, MusicRunner>(config) {
    override val inputClass = MusicFilter::class.java
    override val outputClass = MusicOutput::class.java
    override val runnerClass = MusicRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<MusicFilter>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Fires when the watch sends a music command.")
            .append("\nOutputs: %pb_music_action + %pb_json.")
    }
}

class MusicActivity :
    GenericEventConfigActivity<MusicFilter, MusicOutput, MusicRunner, MusicHelper>() {

    override val titleRes = R.string.pb_evt_music_title
    override val descRes = R.string.pb_evt_music_desc

    override fun buildFields() = listOf(
        FieldSpec("music_action", getString(R.string.pb_lbl_music_action)),
    )

    override fun getNewHelper(config: TaskerPluginConfig<MusicFilter>) = MusicHelper(config)

    override fun buildInput(values: Map<String, String>) =
        MusicFilter(musicAction = values["music_action"])

    override fun extractValues(input: MusicFilter) =
        mapOf("music_action" to input.musicAction.orEmpty())
}
