package com.nickbether.pebbletasker.tasker.event

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.tasker.vars.PbVars

/**
 * Shared output superclass for the event plugins (FINAL DESIGN §2.1 conventions).
 *
 * Carries the watch IDENTITY block + the universal %pbl_event_type / %pbl_seq / %pbl_boot_id + the
 * structured %pbl_json blob — the surface every event shares. Each concrete event Output extends this
 * and adds only its event-specific @get:TaskerOutputVariable getters.
 *
 * WHY inheritance is safe here: the library collects outputs via `realType.methods` (Java
 * Class.getMethods, verified TaskerPluginOutputBase:35), which INCLUDES inherited public getters. So
 * the identity/universal getters declared here are discovered on every subclass without re-declaring
 * them. The matching @field:TaskerInputField backing properties are `open var` so subclasses inherit
 * them as well; the library reads getter values reflectively when building the result bundle.
 *
 * @JvmOverloads on every subclass constructor remains mandatory (FIX C14). This base's primary
 * constructor is also @JvmOverloads-friendly (all defaulted) so subclasses can `super(...)` partially.
 */
open class BaseEventOutput(
    @get:TaskerOutputVariable(PbVars.JSON)
    @field:TaskerInputField("pb_json")
    open var pbJson: String? = null,

    @get:TaskerOutputVariable(PbVars.EVENT_TYPE)
    @field:TaskerInputField("pb_event_type")
    open var pbEventType: String? = null,

    @get:TaskerOutputVariable(PbVars.SEQ)
    @field:TaskerInputField("pb_seq")
    open var pbSeq: String? = null,

    @get:TaskerOutputVariable(PbVars.BOOT_ID)
    @field:TaskerInputField("pb_boot_id")
    open var pbBootId: String? = null,

    @get:TaskerOutputVariable(PbVars.SERIAL)
    @field:TaskerInputField("pb_serial")
    open var pbSerial: String? = null,

    @get:TaskerOutputVariable(PbVars.NAME)
    @field:TaskerInputField("pb_name")
    open var pbName: String? = null,

    @get:TaskerOutputVariable(PbVars.NICKNAME)
    @field:TaskerInputField("pb_nickname")
    open var pbNickname: String? = null,

    @get:TaskerOutputVariable(PbVars.MODEL)
    @field:TaskerInputField("pb_model")
    open var pbModel: String? = null,

    @get:TaskerOutputVariable(PbVars.FW)
    @field:TaskerInputField("pb_fw")
    open var pbFw: String? = null,

    @get:TaskerOutputVariable(PbVars.BATTERY)
    @field:TaskerInputField("pb_battery")
    open var pbBattery: String? = null,

    @get:TaskerOutputVariable(PbVars.ADDRESS)
    @field:TaskerInputField("pb_address")
    open var pbAddress: String? = null,
) {
    /** Fill the inherited identity + universal fields from a cached event. Returns `this`. */
    fun <T : BaseEventOutput> fillBase(cached: CachedEvent?, extraJson: Map<String, String> = emptyMap()): T {
        val id = EventSupport.Identity.from(cached?.watch)
        pbJson = EventSupport.buildJson(cached, extraJson)
        pbEventType = cached?.type.orEmpty()
        pbSeq = cached?.seq?.toString().orEmpty()
        pbBootId = cached?.bootId.orEmpty()
        pbSerial = id.serial
        pbName = id.name
        pbNickname = id.nickname
        pbModel = id.model
        pbFw = id.fw
        pbBattery = id.battery
        pbAddress = id.address
        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}
