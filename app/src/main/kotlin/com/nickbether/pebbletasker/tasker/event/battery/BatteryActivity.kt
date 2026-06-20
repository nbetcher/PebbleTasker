package com.nickbether.pebbletasker.tasker.event.battery

import android.view.LayoutInflater
import com.google.android.material.button.MaterialButton
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.nickbether.pebbletasker.databinding.ActivityConfigBatteryBinding
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.base.PebbleConfigActivity

/**
 * E4 config activity (Neon-Grid themed). AppCompatActivity-based (FIX C2) so Material3 renders.
 *
 * Variable-field rules (FINAL DESIGN §4.3):
 *  - serial / threshold are Material TextInputEditText so Tasker's %var substitution attaches and the
 *    base class's attachVariablePickers() adds the (sole) variable picker end-icon to each.
 *  - The direction toggle (below/above) is a MaterialButtonToggleGroup convenience that WRITES INTO
 *    the editable `direction` EditText (the source of truth, which stays focusable so a %var survives).
 *  - The watch-browse affordance is a separate icon button that writes the chosen serial INTO the
 *    serial EditText (never the end-icon slot, which the variable picker owns).
 */
class BatteryActivity :
    PebbleConfigActivity<BatteryFilter, BatteryOutput, BatteryRunner, BatteryHelper, ActivityConfigBatteryBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityConfigBatteryBinding =
        ActivityConfigBatteryBinding.inflate(inflater)

    override fun getNewHelper(config: TaskerPluginConfig<BatteryFilter>) = BatteryHelper(config)

    override fun onConfigCreated(binding: ActivityConfigBatteryBinding) {
        // The serial field now carries its own "Any" + watches dropdown; the old button is redundant.
        binding.pbBtnPickWatch.visibility = android.view.View.GONE
        CriteriaDropdown.attachWatchSerial(binding.pbLayoutSerial)
        // Direction toggle mirrors into the editable direction field.
        binding.pbToggleDirection.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = if (checkedId == binding.pbBtnAbove.id) "above" else "below"
            binding.pbEditDirection.setText(value)
        }
    }

    override fun assignFromInput(input: TaskerInput<BatteryFilter>) {
        val f = input.regular
        binding?.apply {
            pbEditSerial.setText(f.serial.orEmpty())
            pbEditThreshold.setText(f.threshold ?: "20")
            pbEditDirection.setText(f.direction ?: "below")
            syncDirectionToggle(this, f.direction ?: "below")
        }
    }

    override val inputForTasker: TaskerInput<BatteryFilter>
        get() = TaskerInput(
            BatteryFilter(
                serial = binding?.pbEditSerial?.text?.toString()?.trim().orEmpty(),
                threshold = binding?.pbEditThreshold?.text?.toString()?.trim().orEmpty(),
                direction = binding?.pbEditDirection?.text?.toString()?.trim()
                    ?.ifEmpty { "below" } ?: "below",
            ),
        )

    private fun syncDirectionToggle(binding: ActivityConfigBatteryBinding, direction: String) {
        val btn: MaterialButton =
            if (direction.equals("above", ignoreCase = true)) binding.pbBtnAbove else binding.pbBtnBelow
        binding.pbToggleDirection.check(btn.id)
    }
}
