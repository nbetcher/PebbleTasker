package com.nickbether.pebbletasker.tasker.action.getinfo

import android.view.LayoutInflater
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityActionGetInfoBinding
import com.nickbether.pebbletasker.tasker.action.common.ActionConfigActivity

/**
 * A1 config activity — Get Watch Info. One %var-capable `serial` field with the watch browse-picker
 * on its start icon and the Tasker variable picker on its end icon (wired by the bases).
 */
class GetInfoActivity :
    ActionConfigActivity<GetInfoInput, GetInfoOutput, GetInfoRunner, GetInfoHelper, ActivityActionGetInfoBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityActionGetInfoBinding =
        ActivityActionGetInfoBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<GetInfoInput>): GetInfoHelper =
        GetInfoHelper(config)

    override fun onConfigCreated(binding: ActivityActionGetInfoBinding) {
        super.onConfigCreated(binding)
        wireWatchBrowse(binding.layoutSerial)
    }

    override fun assignFromInput(input: TaskerInput<GetInfoInput>) {
        binding?.editSerial?.setText(input.regular.serial)
    }

    override val inputForTasker: TaskerInput<GetInfoInput>
        get() = TaskerInput(
            GetInfoInput(
                serial = binding?.editSerial?.text?.toString()?.ifBlank { null },
            ),
        )
}
