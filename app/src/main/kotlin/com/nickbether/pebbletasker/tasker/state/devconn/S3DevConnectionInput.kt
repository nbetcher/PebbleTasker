package com.nickbether.pebbletasker.tasker.state.devconn

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * S3 — Pebble Dev Connection On (STATE). FINAL DESIGN §2.2. CAPABILITY-GATED (dev.state collector).
 *
 * No configuration: the state is global (the developer / PebbleKit connection is on or off). Backed by
 * the last cached `dev.state` event.
 *
 * NB: the library instantiates inputs via Class.newInstance(), so an input MUST have a no-arg
 * constructor. A Kotlin class with no declared primary constructor has an implicit no-arg one, so this
 * field-less @TaskerInputRoot is valid (TaskerInputInfos finds zero @TaskerInputField -> no keys).
 */
@TaskerInputRoot
class S3DevConnectionInput
