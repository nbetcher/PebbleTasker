package com.nickbether.pebbletasker.tasker.state.bluetooth

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S6 — Pebble Bluetooth On (STATE). FINAL DESIGN §2.2. CAPABILITY-GATED (bt.state collector).
 *
 * Active when the phone's Bluetooth (as the bridge observes it) is on. No configuration; backed by the
 * last cached `bt.state` event. Field-less @TaskerInputRoot (implicit no-arg constructor — see
 * S3DevConnectionInput note).
 */
@TaskerInputRoot
class S6BluetoothInput
