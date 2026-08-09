package com.nickbether.pebbletasker.tasker.state.watchface

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateWatchfaceBinding
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * S4 config activity — Pebble Active Watchface Is (STATE).
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S4WatchfaceActivity : PebbleConfigActivity<
    S4WatchfaceInput,
    S4WatchfaceOutput,
    S4WatchfaceRunner,
    S4WatchfaceHelper,
    ActivityStateWatchfaceBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateWatchfaceBinding =
        ActivityStateWatchfaceBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S4WatchfaceInput>) = S4WatchfaceHelper(config)

    override fun onConfigCreated(binding: ActivityStateWatchfaceBinding) {
        super.onConfigCreated(binding)
        // The active-watchface filter is a face UUID — offer the locker's faces + validate.
        CriteriaDropdown.attach(binding.pbFieldUuid, CriteriaDropdown.Source.LOCKER_FACE)
    }

    override fun assignFromInput(input: TaskerInput<S4WatchfaceInput>) {
        binding?.pbInputUuid?.setText(input.regular.uuid.orEmpty())
    }

    override val inputForTasker: TaskerInput<S4WatchfaceInput>
        get() = TaskerInput(
            S4WatchfaceInput(
                uuid = binding?.pbInputUuid?.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
}
