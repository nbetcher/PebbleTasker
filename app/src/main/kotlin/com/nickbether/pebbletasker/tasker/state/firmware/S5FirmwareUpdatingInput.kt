package com.nickbether.pebbletasker.tasker.state.firmware

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S5 — Pebble Firmware Updating (STATE). FINAL DESIGN §2.2. CAPABILITY-GATED (fw.status collector).
 *
 * Active while a firmware update is in progress. Backed by the last cached `fw.status` event.
 *
 * [serial] (String?, FIX C10): blank/null = any watch; a value matches the event's WatchRef.serial OR
 * address.
 */
@TaskerInputRoot
class S5FirmwareUpdatingInput @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_lbl_serial")
    var serial: String? = null,
)
