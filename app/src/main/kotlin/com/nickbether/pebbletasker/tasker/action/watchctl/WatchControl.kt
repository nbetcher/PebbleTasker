package com.nickbether.pebbletasker.tasker.action.watchctl

import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.bridge.CommandSender.Type
import com.nickbether.pebbletasker.tasker.action.common.ActionResultOutput
import com.nickbether.pebbletasker.tasker.action.common.Args
import com.nickbether.pebbletasker.tasker.action.common.FormActionActivity
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandHelper
import com.nickbether.pebbletasker.tasker.action.common.WatchCommandRunner
import com.nickbether.pebbletasker.tasker.action.common.orNullIfBlank
import com.nickbether.pebbletasker.tasker.base.CriteriaDropdown
import com.nickbether.pebbletasker.tasker.vars.PbVars

internal val SERIAL_FIELD = FormActionActivity.Field("serial", "Watch serial (blank = active)", lookup = CriteriaDropdown.Source.WATCH_SERIAL)
internal const val RESULT_VARS = "%pbl_ok, %pbl_err, %pbl_errmsg, %pbl_err_code (wire code) and %pbl_json."

// ─────────────────────────────── Stop App ───────────────────────────────

@TaskerInputRoot
class StopAppInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("uuid") var uuid: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class StopAppOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.UUID) @field:TaskerInputField("pb_uuid") var uuid: String? = null,
) : ActionResultOutput()

class StopAppRunner : WatchCommandRunner<StopAppInput, StopAppOutput>() {
    override val command = Type.WATCH_STOP_APP
    override fun watchOf(input: StopAppInput) = input.serial
    override fun args(input: StopAppInput) = input.uuid.orNullIfBlank()?.let { mapOf("uuid" to it) } ?: emptyMap()
    override fun newOutput() = StopAppOutput()
    override fun fill(output: StopAppOutput, data: Map<String, String>) { output.uuid = data["uuid"] }
}

class StopAppHelper(config: TaskerPluginConfig<StopAppInput>) : WatchCommandHelper<StopAppInput, StopAppOutput, StopAppRunner>(config) {
    override val inputClass = StopAppInput::class.java
    override val outputClass = StopAppOutput::class.java
    override val runnerClass = StopAppRunner::class.java
    override val defaultBlurb = "Pebble: Stop App"
    override fun blurbFor(input: StopAppInput) = withWatch("Stop ${input.uuid.orNullIfBlank() ?: "running app"}", input.serial)
}

class StopAppActivity : FormActionActivity<StopAppInput, StopAppOutput, StopAppRunner, StopAppHelper>() {
    override val formTitle = "Stop Watch App"
    override val formDescription = "Closes an app on the watch. Leave the UUID blank to close whichever app is running."
    override val formOutputs = "Outputs: %pbl_uuid (the app that was stopped), $RESULT_VARS"
    override fun buildFields() = listOf(SERIAL_FIELD, FormActionActivity.Field("uuid", "App UUID (blank = running app)", lookup = CriteriaDropdown.Source.LOCKER_APP))
    override fun getNewHelper(config: TaskerPluginConfig<StopAppInput>) = StopAppHelper(config)
    override fun buildInput(values: Map<String, String>) = StopAppInput(values.opt("serial"), values.opt("uuid"))
    override fun extractValues(input: StopAppInput) = mapOf("serial" to input.serial.orEmpty(), "uuid" to input.uuid.orEmpty())
}

// ─────────────────────────────── Sync Time ───────────────────────────────

@TaskerInputRoot
class SyncTimeInput @JvmOverloads constructor(@field:TaskerInputField("serial") var serial: String? = null)

@TaskerInputRoot
@TaskerOutputObject
class SyncTimeOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.VERIFIED) @field:TaskerInputField("pb_verified") var verified: String? = null,
    @get:TaskerOutputVariable(PbVars.SKEW_S) @field:TaskerInputField("pb_skew_s") var skewS: String? = null,
) : ActionResultOutput()

class SyncTimeRunner : WatchCommandRunner<SyncTimeInput, SyncTimeOutput>() {
    override val command = Type.WATCH_SYNC_TIME
    override fun watchOf(input: SyncTimeInput) = input.serial
    override fun newOutput() = SyncTimeOutput()
    override fun fill(output: SyncTimeOutput, data: Map<String, String>) {
        output.verified = data["verified"]; output.skewS = data["skew_s"]
    }
}

class SyncTimeHelper(config: TaskerPluginConfig<SyncTimeInput>) : WatchCommandHelper<SyncTimeInput, SyncTimeOutput, SyncTimeRunner>(config) {
    override val inputClass = SyncTimeInput::class.java
    override val outputClass = SyncTimeOutput::class.java
    override val runnerClass = SyncTimeRunner::class.java
    override val defaultBlurb = "Pebble: Sync Time"
    override fun blurbFor(input: SyncTimeInput) = withWatch("Sync time", input.serial)
}

class SyncTimeActivity : FormActionActivity<SyncTimeInput, SyncTimeOutput, SyncTimeRunner, SyncTimeHelper>() {
    override val formTitle = "Sync Watch Time"
    override val formDescription = "Sets the watch clock from the phone and reads it back. A verified sync is within 2 seconds. " +
        "Repeating within 30 minutes of a verified sync (30 seconds after an unverified one) returns RATE_LIMITED with the seconds remaining."
    override val formOutputs = "Outputs: %pbl_verified, %pbl_skew_s, $RESULT_VARS"
    override fun buildFields() = listOf(SERIAL_FIELD)
    override fun getNewHelper(config: TaskerPluginConfig<SyncTimeInput>) = SyncTimeHelper(config)
    override fun buildInput(values: Map<String, String>) = SyncTimeInput(values.opt("serial"))
    override fun extractValues(input: SyncTimeInput) = mapOf("serial" to input.serial.orEmpty())
}

// ─────────────────────────────── Reboot ───────────────────────────────

@TaskerInputRoot
class RebootInput @JvmOverloads constructor(@field:TaskerInputField("serial") var serial: String? = null)

@TaskerInputRoot
@TaskerOutputObject
class RebootOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.REBOOTING) @field:TaskerInputField("pb_rebooting") var rebooting: String? = null,
) : ActionResultOutput()

class RebootRunner : WatchCommandRunner<RebootInput, RebootOutput>() {
    override val command = Type.WATCH_REBOOT
    override fun watchOf(input: RebootInput) = input.serial
    override fun newOutput() = RebootOutput()
    override fun fill(output: RebootOutput, data: Map<String, String>) { output.rebooting = data["rebooting"] ?: "true" }
}

class RebootHelper(config: TaskerPluginConfig<RebootInput>) : WatchCommandHelper<RebootInput, RebootOutput, RebootRunner>(config) {
    override val inputClass = RebootInput::class.java
    override val outputClass = RebootOutput::class.java
    override val runnerClass = RebootRunner::class.java
    override val defaultBlurb = "Pebble: Reboot Watch"
    override fun blurbFor(input: RebootInput) = withWatch("Reboot", input.serial)
}

class RebootActivity : FormActionActivity<RebootInput, RebootOutput, RebootRunner, RebootHelper>() {
    override val formTitle = "Reboot Watch"
    override val formDescription = "Restarts the watch. It disconnects and reconnects on its own. Another reboot within 60 seconds returns RATE_LIMITED."
    override val formOutputs = "Outputs: %pbl_rebooting, $RESULT_VARS"
    override fun buildFields() = listOf(SERIAL_FIELD)
    override fun getNewHelper(config: TaskerPluginConfig<RebootInput>) = RebootHelper(config)
    override fun buildInput(values: Map<String, String>) = RebootInput(values.opt("serial"))
    override fun extractValues(input: RebootInput) = mapOf("serial" to input.serial.orEmpty())
}

// ─────────────────────────────── Press Button ───────────────────────────────

@TaskerInputRoot
class PressButtonInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("button") var button: String? = null,
    @field:TaskerInputField("presses") var presses: String? = null,
    @field:TaskerInputField("hold_ms") var holdMs: String? = null,
    @field:TaskerInputField("gap_ms") var gapMs: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class PressButtonOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.ACCEPTED) @field:TaskerInputField("pb_accepted") var accepted: String? = null,
) : ActionResultOutput()

object RemoteInput {
    val BUTTONS = listOf("back", "up", "select", "down")
    val DIRECTIONS = listOf("up", "down", "left", "right")

    fun pressArgs(button: String?, presses: String?, holdMs: String?, gapMs: String?): Map<String, String> = linkedMapOf(
        "button" to Args.choice("button", button, BUTTONS),
        "presses" to Args.int("presses", presses, 1, 1, 255).toString(),
        "hold_ms" to Args.int("hold_ms", holdMs, 50, 0, 65535).toString(),
        "gap_ms" to Args.int("gap_ms", gapMs, 100, 0, 65535).toString(),
    )

    fun swipeArgs(direction: String?, durationMs: String?): Map<String, String> = linkedMapOf(
        "direction" to Args.choice("direction", direction, DIRECTIONS),
        "duration_ms" to Args.int("duration_ms", durationMs, 150, 1, 300).toString(),
    )

    const val NOTE = "Needs PebbleOS 4.35.0 or later (UNSUPPORTED_COMMAND otherwise). It succeeds once the watch accepts " +
        "the sequence, not when it finishes. WATCH_BUSY means another injected sequence is still running: retry later."
}

class PressButtonRunner : WatchCommandRunner<PressButtonInput, PressButtonOutput>() {
    override val command = Type.WATCH_PRESS_BUTTON
    override fun watchOf(input: PressButtonInput) = input.serial
    override fun args(input: PressButtonInput) = RemoteInput.pressArgs(input.button, input.presses, input.holdMs, input.gapMs)
    override fun newOutput() = PressButtonOutput()
    override fun fill(output: PressButtonOutput, data: Map<String, String>) { output.accepted = data["accepted"] ?: "true" }
}

class PressButtonHelper(config: TaskerPluginConfig<PressButtonInput>) : WatchCommandHelper<PressButtonInput, PressButtonOutput, PressButtonRunner>(config) {
    override val inputClass = PressButtonInput::class.java
    override val outputClass = PressButtonOutput::class.java
    override val runnerClass = PressButtonRunner::class.java
    override val defaultBlurb = "Pebble: Press Button"
    override fun blurbFor(input: PressButtonInput): String {
        val n = input.presses.orNullIfBlank() ?: "1"
        val hold = input.holdMs.orNullIfBlank()?.let { " hold ${it}ms" } ?: ""
        return withWatch("Press ${input.button.orNullIfBlank() ?: "?"} x$n$hold", input.serial)
    }
}

class PressButtonActivity : FormActionActivity<PressButtonInput, PressButtonOutput, PressButtonRunner, PressButtonHelper>() {
    override val formTitle = "Press Watch Button"
    override val formDescription = "Presses a watch button remotely. For a long press, set the hold time (e.g. 1000 ms); " +
        "the watch releases the button itself. ${RemoteInput.NOTE}"
    override val formOutputs = "Outputs: %pbl_accepted, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("button", "Button", options = RemoteInput.BUTTONS.map { it.replaceFirstChar(Char::uppercase) to it }),
        FormActionActivity.Field("presses", "Presses (1-255, default 1)", numeric = true),
        FormActionActivity.Field("hold_ms", "Hold per press, ms (0-65535, default 50)", numeric = true, helper = "1000 or more for a long press"),
        FormActionActivity.Field("gap_ms", "Gap between presses, ms (0-65535, default 100)", numeric = true),
    )
    override fun getNewHelper(config: TaskerPluginConfig<PressButtonInput>) = PressButtonHelper(config)
    override fun buildInput(values: Map<String, String>) = PressButtonInput(values.opt("serial"), values.opt("button"), values.opt("presses"), values.opt("hold_ms"), values.opt("gap_ms"))
    override fun extractValues(input: PressButtonInput) = mapOf(
        "serial" to input.serial.orEmpty(), "button" to input.button.orEmpty(), "presses" to input.presses.orEmpty(),
        "hold_ms" to input.holdMs.orEmpty(), "gap_ms" to input.gapMs.orEmpty(),
    )
}

// ─────────────────────────────── Swipe ───────────────────────────────

@TaskerInputRoot
class SwipeInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("direction") var direction: String? = null,
    @field:TaskerInputField("duration_ms") var durationMs: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class SwipeOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.ACCEPTED) @field:TaskerInputField("pb_accepted") var accepted: String? = null,
) : ActionResultOutput()

class SwipeRunner : WatchCommandRunner<SwipeInput, SwipeOutput>() {
    override val command = Type.WATCH_SWIPE
    override fun watchOf(input: SwipeInput) = input.serial
    override fun args(input: SwipeInput) = RemoteInput.swipeArgs(input.direction, input.durationMs)
    override fun newOutput() = SwipeOutput()
    override fun fill(output: SwipeOutput, data: Map<String, String>) { output.accepted = data["accepted"] ?: "true" }
}

class SwipeHelper(config: TaskerPluginConfig<SwipeInput>) : WatchCommandHelper<SwipeInput, SwipeOutput, SwipeRunner>(config) {
    override val inputClass = SwipeInput::class.java
    override val outputClass = SwipeOutput::class.java
    override val runnerClass = SwipeRunner::class.java
    override val defaultBlurb = "Pebble: Swipe"
    override fun blurbFor(input: SwipeInput) = withWatch("Swipe ${input.direction.orNullIfBlank() ?: "?"}", input.serial)
}

class SwipeActivity : FormActionActivity<SwipeInput, SwipeOutput, SwipeRunner, SwipeHelper>() {
    override val formTitle = "Swipe Watch Screen"
    override val formDescription = "Swipes on a touch-screen watch. Watches without a touch screen, or with touch turned off, " +
        "return INVALID_ARGS. ${RemoteInput.NOTE}"
    override val formOutputs = "Outputs: %pbl_accepted, $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("direction", "Direction", options = RemoteInput.DIRECTIONS.map { it.replaceFirstChar(Char::uppercase) to it }),
        FormActionActivity.Field("duration_ms", "Duration, ms (1-300, default 150)", numeric = true),
    )
    override fun getNewHelper(config: TaskerPluginConfig<SwipeInput>) = SwipeHelper(config)
    override fun buildInput(values: Map<String, String>) = SwipeInput(values.opt("serial"), values.opt("direction"), values.opt("duration_ms"))
    override fun extractValues(input: SwipeInput) = mapOf(
        "serial" to input.serial.orEmpty(), "direction" to input.direction.orEmpty(), "duration_ms" to input.durationMs.orEmpty(),
    )
}

// ─────────────────────────────── Factory Reset ───────────────────────────────

@TaskerInputRoot
class FactoryResetInput @JvmOverloads constructor(
    @field:TaskerInputField("serial") var serial: String? = null,
    @field:TaskerInputField("confirm_serial") var confirmSerial: String? = null,
)

@TaskerInputRoot
@TaskerOutputObject
class FactoryResetOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable(PbVars.FACTORY_RESET) @field:TaskerInputField("pb_factory_reset") var factoryReset: String? = null,
) : ActionResultOutput()

class FactoryResetRunner : WatchCommandRunner<FactoryResetInput, FactoryResetOutput>() {
    override val command = Type.WATCH_FACTORY_RESET
    override fun watchOf(input: FactoryResetInput) = input.serial
    override fun args(input: FactoryResetInput) = mapOf("confirm_serial" to Args.required("confirm_serial", input.confirmSerial))
    override fun newOutput() = FactoryResetOutput()
    override fun fill(output: FactoryResetOutput, data: Map<String, String>) { output.factoryReset = data["factory_reset"] }
}

class FactoryResetHelper(config: TaskerPluginConfig<FactoryResetInput>) : WatchCommandHelper<FactoryResetInput, FactoryResetOutput, FactoryResetRunner>(config) {
    override val inputClass = FactoryResetInput::class.java
    override val outputClass = FactoryResetOutput::class.java
    override val runnerClass = FactoryResetRunner::class.java
    override val defaultBlurb = "Pebble: FACTORY RESET"
    override fun blurbFor(input: FactoryResetInput) = "FACTORY RESET watch ${input.confirmSerial.orNullIfBlank() ?: "(serial not confirmed)"}"
    override fun isInputValid(input: com.joaomgcd.taskerpluginlibrary.input.TaskerInput<FactoryResetInput>): com.joaomgcd.taskerpluginlibrary.SimpleResult {
        if (input.regular.confirmSerial.orNullIfBlank() == null)
            return com.joaomgcd.taskerpluginlibrary.SimpleResultError("Type the target watch's serial into the confirmation field.")
        return super.isInputValid(input)
    }
}

class FactoryResetActivity : FormActionActivity<FactoryResetInput, FactoryResetOutput, FactoryResetRunner, FactoryResetHelper>() {
    override val formTitle = "Factory Reset Watch"
    override val formDescription = "Erases the watch and returns it to factory settings. The Pebble app refuses unless the confirmation " +
        "matches the target watch's serial. Needs the extremely dangerous tier and the \"Allow dangerous commands\" switch."
    override val formWarning = "⚠ IRREVERSIBLE. This erases every app, watchface, setting, timeline pin and stored health " +
        "record on the watch. It cannot be undone. Only save this action if you really mean to wipe the watch."
    override val formOutputs = "Outputs: %pbl_factory_reset (\"started\"), $RESULT_VARS"
    override fun buildFields() = listOf(
        SERIAL_FIELD,
        FormActionActivity.Field("confirm_serial", "Confirm: type the target watch serial", helper = "Required. Must equal the serial of the watch being reset."),
    )
    override fun getNewHelper(config: TaskerPluginConfig<FactoryResetInput>) = FactoryResetHelper(config)
    override fun buildInput(values: Map<String, String>) = FactoryResetInput(values.opt("serial"), values.opt("confirm_serial"))
    override fun extractValues(input: FactoryResetInput) = mapOf("serial" to input.serial.orEmpty(), "confirm_serial" to input.confirmSerial.orEmpty())
}
