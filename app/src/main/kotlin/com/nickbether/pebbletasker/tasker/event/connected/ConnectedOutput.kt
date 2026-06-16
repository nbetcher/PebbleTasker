package com.nickbether.pebbletasker.tasker.event.connected

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.nickbether.pebbletasker.tasker.event.BaseEventOutput

/**
 * E1 output — pure identity block + universal + %pb_json (no event-specific scalars). READY today.
 */
@TaskerInputRoot
@TaskerOutputObject
class ConnectedOutput @JvmOverloads constructor() : BaseEventOutput()
