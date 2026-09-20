package com.nickbether.pebbletasker.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import coredevices.coreapp.automation.IBridgeService

/** One binding per generation. Every drop releases the Android registration before retrying. */
class BridgeConnection(
    private val appContext: Context,
    private val onConnected: (IBridgeService) -> Unit,
    private val onDisconnected: () -> Unit,
    private val scheduleRebind: (Long) -> Unit,
    private val bindTimeoutMs: Long = BIND_TIMEOUT_MS,
) {
    enum class State { UNBOUND, BINDING, BOUND }
    @Volatile var state: State = State.UNBOUND; private set
    @Volatile var generation: Long = 0; private set
    @Volatile var lastFailure: String? = null; private set
    @Volatile private var service: IBridgeService? = null
    private var active: ServiceConnection? = null
    private val handler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    fun serviceOrNull(): IBridgeService? = service

    @Synchronized fun ensureBound() {
        if (state != State.UNBOUND) return
        val epoch = ++generation
        state = State.BINDING
        lastFailure = null
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                synchronized(this@BridgeConnection) {
                    if (epoch != generation || active !== this || state != State.BINDING) return
                    val svc = IBridgeService.Stub.asInterface(binder)
                    if (svc == null) { drop(epoch, "Pebble returned no Binder"); return }
                    watchdog?.let(handler::removeCallbacks); watchdog = null
                    service = svc
                    state = State.BOUND
                    onConnected(svc)
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) { drop(epoch, "Disconnected from Pebble") }
            override fun onBindingDied(name: ComponentName?) { drop(epoch, "Pebble binding died") }
            override fun onNullBinding(name: ComponentName?) { drop(epoch, "Pebble refused the binding") }
        }
        active = callback
        watchdog = Runnable { drop(epoch, "Timed out connecting to Pebble") }.also {
            handler.postDelayed(it, bindTimeoutMs)
        }
        val accepted = runCatching {
            appContext.bindService(Intent(BIND_ACTION).setPackage(CertPinner.BRIDGE_PACKAGE), callback, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!accepted) drop(epoch, "Pebble service is unavailable")
    }

    @Synchronized fun unbind() {
        ++generation
        watchdog?.let(handler::removeCallbacks); watchdog = null
        val old = active
        active = null
        service = null
        state = State.UNBOUND
        if (old != null) runCatching { appContext.unbindService(old) }
    }

    @Synchronized private fun drop(epoch: Long, reason: String) {
        if (epoch != generation || state == State.UNBOUND) return
        lastFailure = reason
        unbind()
        onDisconnected()
        scheduleRebind(MIN_BACKOFF_MS)
    }
    companion object {
        const val BIND_ACTION = "coredevices.coreapp.automation.BridgeService"
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val BIND_TIMEOUT_MS = 4_000L
    }
}
