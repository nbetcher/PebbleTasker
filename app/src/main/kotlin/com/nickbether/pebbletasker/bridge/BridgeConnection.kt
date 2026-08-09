package com.nickbether.pebbletasker.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.nickbether.pebbletasker.log.PLog
import coredevices.coreapp.automation.IBridgeService
import java.util.concurrent.atomic.AtomicReference

/**
 * Sticky bind + exponential-backoff state machine for the bound [IBridgeService] (FINAL DESIGN §3.1).
 *
 * Owns exactly one [ServiceConnection]. On connect it hands the live [IBridgeService] to [onConnected]
 * (the BridgeClient runs the TOFU cert check + handshake + register there). On disconnect/death it
 * clears the binder and schedules a rebind with backoff (1s -> 60s).
 *
 * Bind target (verified): action "coredevices.coreapp.automation.BridgeService", package
 * "coredevices.coreapp", BIND_AUTO_CREATE. The <queries> entry in the manifest makes it resolvable on
 * Android 11+.
 *
 * Scheduling is delegated to [scheduleRebind] (a callback) so the owner controls the dispatcher /
 * coroutine used for the delay; this class stays free of coroutine machinery and is trivially testable.
 */
class BridgeConnection(
    private val appContext: Context,
    private val onConnected: (IBridgeService) -> Unit,
    private val onDisconnected: () -> Unit,
    /** Invoked when a (re)bind should be attempted after [delayMs]. Owner runs ensureBound() then. */
    private val scheduleRebind: (delayMs: Long) -> Unit,
) {
    enum class State { UNBOUND, BINDING, BOUND }

    private val stateRef = AtomicReference(State.UNBOUND)
    val state: State get() = stateRef.get()

    @Volatile private var service: IBridgeService? = null
    @Volatile private var backoffMs: Long = MIN_BACKOFF_MS

    /** The live service binder, or null if not currently bound. */
    fun serviceOrNull(): IBridgeService? = service

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IBridgeService.Stub.asInterface(binder)
            service = svc
            stateRef.set(State.BOUND)
            backoffMs = MIN_BACKOFF_MS // reset backoff on a clean connect
            if (svc != null) {
                PLog.i { "conn: onServiceConnected -> BOUND ($name)" }
                runCatching { onConnected(svc) }
            } else {
                // Null interface — treat as a failed bind and retry.
                PLog.w { "conn: onServiceConnected but binder is null; treating as drop" }
                handleDrop()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            PLog.w { "conn: onServiceDisconnected ($name)" }
            handleDrop()
        }

        override fun onBindingDied(name: ComponentName?) {
            PLog.w { "conn: onBindingDied ($name)" }
            handleDrop()
        }

        override fun onNullBinding(name: ComponentName?) {
            // Service exists but returned no binder (e.g. unexported / refused). Back off and retry.
            PLog.w { "conn: onNullBinding ($name) — service refused to return a binder" }
            handleDrop()
        }
    }

    /** Bind if not already bound/binding. Idempotent. */
    fun ensureBound() {
        if (stateRef.get() != State.UNBOUND) {
            PLog.d { "conn: ensureBound skipped (state=${stateRef.get()})" }
            return
        }
        if (!stateRef.compareAndSet(State.UNBOUND, State.BINDING)) return
        val intent = Intent(BIND_ACTION).setPackage(CertPinner.BRIDGE_PACKAGE)
        PLog.i { "conn: bindService -> ${CertPinner.BRIDGE_PACKAGE} / $BIND_ACTION" }
        val ok = try {
            appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            PLog.e(t) { "conn: bindService threw" }
            false
        }
        if (!ok) {
            // App not installed / not queryable / refused. Roll back and schedule a retry.
            PLog.w { "conn: bindService returned false (app absent / not queryable / refused)" }
            runCatching { appContext.unbindService(conn) }
            stateRef.set(State.UNBOUND)
            scheduleNextRebind()
        } else {
            PLog.d { "conn: bindService accepted; awaiting onServiceConnected" }
        }
    }

    /** Tear down the binding (e.g. on app shutdown). */
    fun unbind() {
        service = null
        if (stateRef.getAndSet(State.UNBOUND) != State.UNBOUND) {
            runCatching { appContext.unbindService(conn) }
        }
    }

    private fun handleDrop() {
        service = null
        stateRef.set(State.UNBOUND)
        runCatching { onDisconnected() }
        scheduleNextRebind()
    }

    private fun scheduleNextRebind() {
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        PLog.i { "conn: scheduling rebind in ${delay}ms (next backoff ${backoffMs}ms)" }
        scheduleRebind(delay)
    }

    companion object {
        /** Verified bridge service action string. */
        const val BIND_ACTION = "coredevices.coreapp.automation.BridgeService"
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
