package com.nickbether.pebbletasker.tasker.event.battery

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * E4 — Pebble Battery Level filter (FINAL DESIGN §2.1, READY on today's bridge).
 *
 * All fields are String? so a %var that resolves to a non-numeric value never crashes parsing
 * (FINAL DESIGN §0 FIX C10 — a safety convention; any type accepts %vars). @JvmOverloads is
 * mandatory: the library reflectively instantiates with generated partial constructors (FIX C14).
 *
 * direction: "below" (fire when level <= threshold) or "above" (fire when level >= threshold).
 */
@TaskerInputRoot
class BatteryFilter @JvmOverloads constructor(
    @field:TaskerInputField("serial", labelResIdName = "pb_evt_lbl_serial")
    var serial: String? = null,
    @field:TaskerInputField("threshold", labelResIdName = "pb_lbl_threshold")
    var threshold: String? = "20",
    @field:TaskerInputField("direction", labelResIdName = "pb_lbl_direction")
    var direction: String? = "below",
)
