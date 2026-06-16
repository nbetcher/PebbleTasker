package com.nickbether.pebbletasker.tasker.state.devconn

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityStateDevconnBinding
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * S3 config activity — Pebble Dev Connection On (STATE). No input fields.
 * Manifest intent-filter: com.twofortyfouram.locale.intent.action.EDIT_CONDITION.
 */
class S3DevConnectionActivity : PebbleConfigActivity<
    S3DevConnectionInput,
    S3DevConnectionOutput,
    S3DevConnectionRunner,
    S3DevConnectionHelper,
    ActivityStateDevconnBinding,
    >() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityStateDevconnBinding =
        ActivityStateDevconnBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<S3DevConnectionInput>) =
        S3DevConnectionHelper(config)

    override fun assignFromInput(input: TaskerInput<S3DevConnectionInput>) {
        // No fields to populate.
    }

    override val inputForTasker: TaskerInput<S3DevConnectionInput>
        get() = TaskerInput(S3DevConnectionInput())
}
