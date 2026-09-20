package com.nickbether.pebbletasker.bridge

import android.content.Context
import android.os.RemoteException
import com.nickbether.pebbletasker.bridge.dto.*
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.setup.SetupState
import com.nickbether.pebbletasker.tasker.ErrCodes
import coredevices.coreapp.automation.IBridgeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.RejectedExecutionException

/** Shared demand-driven readiness. Binding, handshake and registration are a single bounded attempt.
 * A Binder worker never publishes state: the cancellable owner checks its generation on completion. */
class BridgeClient internal constructor(
    private val appContext: Context,
    private val certPinner: CertPinner = CertPinner(appContext),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val calls: BinderCalls = BinderCalls(),
) {
    private val lock = Any()
    private val bound = MutableStateFlow<IBridgeService?>(null)
    private val cache get() = EventCache.get(appContext)
    @Volatile private var generation = 0L
    @Volatile private var started = false
    @Volatile private var session: BridgeSession? = null
    private var listener: BridgeListener? = null
    private var attempt: Deferred<BridgeResult<BridgeSession>>? = null
    private var rebindJob: Job? = null
    private var recoveryAttempts = 0
    private val _status = MutableStateFlow<ConnectionStatus>(
        if (SetupState.isAccessDenied(appContext)) ConnectionStatus.Error(ErrCodes.ACCESS_DENIED, DENIED_MESSAGE)
        else ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()
    val currentSession: BridgeSession? get() = session.takeIf { status.value is ConnectionStatus.Ready && bound.value != null }

    private val connection = BridgeConnection(appContext,
        onConnected = { bound.value = it },
        onDisconnected = { connectionDropped() },
        scheduleRebind = { delayMs -> scheduleRecovery(delayMs) })

    fun start() { retryHandshake() }

    private fun connectionDropped() {
        val reason = connection.lastFailure ?: "Disconnected from Pebble"
        // Retire while the old Binder is still available for best-effort token cleanup.
        invalidate(BridgeResult.err(if (reason.startsWith("Timed out")) ErrCodes.TIMEOUT else ErrCodes.BRIDGE_UNREACHABLE, reason))
        bound.value = null
    }

    fun stop() {
        val old = synchronized(lock) {
            started = false
            rebindJob?.cancel(); rebindJob = null
            retireLocked()
        }
        cleanup(old)
        connection.unbind()
        bound.value = null
        synchronized(lock) { if (!isDenied()) _status.value = ConnectionStatus.Idle }
    }

    /** Ordinary demand never resets an explicit denial and never creates an approval polling loop. */
    fun retryHandshake() {
        val shared = synchronized(lock) {
            if (isDenied() || currentSession != null) return
            requestAttemptLocked()
        }
        shared.start()
    }

    /** Deliberate UI action after reviewing the plugin identity/decision in Pebble. */
    fun reconsiderAccess() {
        synchronized(lock) {
            SetupState.setAccessDenied(appContext, false)
            if (isDenied()) _status.value = ConnectionStatus.Idle
        }
        retryHandshake()
    }

    /** Only the fingerprint-confirmation button may establish or replace the durable host pin. */
    fun retrustCert() {
        if (status.value !is ConnectionStatus.CertMismatch) return
        if (certPinner.repinToCurrent() != null) retryHandshake()
    }

    suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS): BridgeResult<BridgeSession> {
        currentCoroutineContext().ensureActive()
        if (timeoutMs <= 0) return timeout()
        val shared = synchronized(lock) {
            if (isDenied()) return denied()
            currentSession?.let { return BridgeResult.Ok(it) }
            requestAttemptLocked()
        }
        shared.start()
        return try {
            withTimeout(timeoutMs) { shared.await() }
        } catch (_: TimeoutCancellationException) { timeout() }
        catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            readinessError()
        }
    }

    /** Called with lock held, so stop can always find and cancel even a not-yet-started owner. */
    private fun requestAttemptLocked(): Deferred<BridgeResult<BridgeSession>> {
        started = true
        return attempt?.takeIf { !it.isCompleted } ?: run {
            val epoch = ++generation
            scope.async(start = CoroutineStart.LAZY) { establish(epoch) }.also { attempt = it }
        }
    }

    private suspend fun establish(epoch: Long): BridgeResult<BridgeSession> {
        var candidate: Pair<IBridgeService, BridgeSession>? = null
        try {
            return withTimeout(READY_TIMEOUT_MS) {
                synchronized(lock) {
                    ensureCurrent(epoch)
                    // A process may have missed revocation while dead. Retained content is not a
                    // grant: keep replay progress, but require fresh authorized snapshots/delivery.
                    cache.suspendAuthority()
                }
                when (certPinner.verify()) {
                    CertPinner.PinResult.APP_ABSENT -> return@withTimeout fail(epoch, BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Pebble app is not installed"), ConnectionStatus.AppAbsent)
                    CertPinner.PinResult.MISMATCH, CertPinner.PinResult.UNTRUSTED -> return@withTimeout fail(epoch,
                        BridgeResult.err(ErrCodes.CERT_MISMATCH, "Review and trust the Pebble app signature"),
                        ConnectionStatus.CertMismatch(certPinner.currentSha()))
                    CertPinner.PinResult.PINNED, CertPinner.PinResult.OK -> Unit
                }
                ensureCurrent(epoch)
                connection.ensureBound()
                val svc = withTimeout(BridgeConnection.BIND_TIMEOUT_MS + 250) { bound.filterNotNull().first() }
                ensureCurrent(epoch)
                val hello = ipc(HANDSHAKE_TIMEOUT_MS) {
                    BridgeCodec.decodeHello(svc.handshake(BridgeCodec.encodeHello(ClientHello(
                        clientLabel = CLIENT_LABEL, clientProtocol = CLIENT_PROTOCOL, wants = WANTS))))
                }
                if (hello is BridgeResult.Err) return@withTimeout fail(epoch, hello)
                val sess = BridgeSession.from((hello as BridgeResult.Ok).value)
                candidate = svc to sess
                ensureCurrent(epoch, svc)
                synchronized(lock) {
                    ensureCurrent(epoch, svc)
                    cache.acceptAuthority(sess.bootId, sess.authorityId)
                    cache.seedFromHandshake(sess.bootId, sess.latestSeq, recover = SetupState.isSetupComplete(appContext))
                }
                val fresh = BridgeListener(sess.bootId, sess.clientToken,
                    isCurrent = { started && generation == epoch && bound.value === svc },
                    onGoodbye = { raw ->
                        val err = BridgeCodec.decodeResult(raw) as? BridgeResult.Err
                            ?: BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Pebble ended this session")
                        invalidate(err, epoch)
                        if (err.code != ErrCodes.ACCESS_DENIED) scheduleRecovery(BridgeConnection.MIN_BACKOFF_MS)
                    })
                synchronized(lock) { ensureCurrent(epoch, svc); listener = fresh }
                val from = cache.highWaterSeqFor(sess.bootId).coerceAtLeast(0)
                val registered = ipc(HANDSHAKE_TIMEOUT_MS) {
                    ensureCurrent(epoch, svc)
                    svc.registerEventListener(sess.clientToken, fresh, from)
                    ensureCurrent(epoch, svc)
                    BridgeCodec.decodeBatch(svc.getEventsSince(if (sess.has("events.registration_ack_only")) Long.MAX_VALUE else from, sess.bootId))
                }
                if (registered is BridgeResult.Err) return@withTimeout fail(epoch, registered)
                val proof = (registered as BridgeResult.Ok).value
                if (proof.bootId != sess.bootId || proof.subscriptionToken != sess.clientToken)
                    return@withTimeout fail(epoch, BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Pebble did not acknowledge this listener registration"))
                synchronized(lock) {
                    ensureCurrent(epoch, svc)
                    session = sess
                    SetupState.markSetupComplete(appContext)
                    recoveryAttempts = 0
                    _status.value = ConnectionStatus.Ready(sess)
                }
                // The probe is proof only. Ingesting its replay could overtake earlier queued listener
                // callbacks. The server sends replay and live batches through one ordered worker.
                scope.launch {
                    for (batch in fresh.batches) {
                        synchronized(lock) {
                            if (generation != epoch || session !== sess || bound.value !== svc) return@launch
                            val routed = cache.ingestBatch(batch)
                            EventRouter.routeAll(appContext, routed)
                        }
                    }
                }
                EventRouter.requestQueryAll(appContext) // states only; never replay event profiles
                com.nickbether.pebbletasker.ui.BridgeWarning.cancel(appContext)
                scope.launch { com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageSubscriptions.restore(appContext) }
                candidate = null
                BridgeResult.Ok(sess)
            }
        } catch (_: TimeoutCancellationException) {
            return fail(epoch, timeout())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            return fail(epoch, BridgeResult.err(ErrCodes.INTERNAL, t.message ?: "Bridge readiness failed"))
        } finally {
            candidate?.let(::cleanup)
        }
    }

    suspend fun getState(timeoutMs: Long = STATE_TIMEOUT_MS): BridgeResult<StateResult> = try {
        withTimeout(timeoutMs) {
            val ready = awaitReady()
            if (ready is BridgeResult.Err) return@withTimeout ready
            val sess = (ready as BridgeResult.Ok).value
            verifiedCall(sess) { svc -> BridgeCodec.decodeState(svc.getState("{}")) }.also { result ->
                if (result is BridgeResult.Ok) synchronized(lock) {
                    if (session === sess) cache.seedState(result.value)
                }
            }
        }
    } catch (_: TimeoutCancellationException) { timeout() }

    suspend fun execute(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> = try {
        withTimeout(PLUGIN_TIMEOUT_MS) {
            val ready = awaitReady()
            if (ready is BridgeResult.Err) return@withTimeout ready
            val sess = (ready as BridgeResult.Ok).value
            if (!sess.has("command.${cmd.type}")) return@withTimeout BridgeResult.err(
                ErrCodes.UNSUPPORTED_COMMAND, "Pebble does not advertise command ${cmd.type}")
            verifiedCall(sess) { svc -> BridgeCodec.decodeResult(svc.execute(sess.clientToken, BridgeCodec.encodeCommand(cmd))) }
        }
    } catch (_: TimeoutCancellationException) { timeout() }

    private suspend fun <T> verifiedCall(sess: BridgeSession, block: (IBridgeService) -> BridgeResult<T>): BridgeResult<T> {
        val svc: IBridgeService
        val epoch: Long
        synchronized(lock) {
            if (currentSession !== sess) return readinessError()
            svc = bound.value ?: return readinessError()
            epoch = generation
        }
        val result = ipc(EXECUTE_TIMEOUT_MS) {
            val current = synchronized(lock) { started && generation == epoch && currentSession === sess && bound.value === svc }
            if (current) block(svc) else readinessError()
        }
        synchronized(lock) {
            if (generation != epoch || currentSession !== sess || bound.value !== svc) return readinessError()
        }
        if (result is BridgeResult.Err && result.bridgeCode != "COMMAND_NOT_AUTHORIZED" &&
            !(result.code == ErrCodes.TIMEOUT && result.bridgeCode == "TIMEOUT") &&
            result.code in setOf(ErrCodes.ACCESS_DENIED, ErrCodes.NOT_AUTHORIZED, ErrCodes.CONSENT_PENDING,
                ErrCodes.CERT_MISMATCH, ErrCodes.BRIDGE_UNREACHABLE, ErrCodes.TIMEOUT)) {
            invalidate(result, epoch)
            if (result.code in setOf(ErrCodes.NOT_AUTHORIZED, ErrCodes.BRIDGE_UNREACHABLE, ErrCodes.TIMEOUT))
                scheduleRecovery(BridgeConnection.MIN_BACKOFF_MS)
        }
        return result
    }

    private suspend fun <T> ipc(timeoutMs: Long, block: () -> BridgeResult<T>): BridgeResult<T> = try {
        calls.call(timeoutMs, block)
    } catch (_: TimeoutCancellationException) { timeout() }
    catch (e: CancellationException) { throw e }
    catch (_: RejectedExecutionException) { BridgeResult.err(ErrCodes.TIMEOUT, "Pebble IPC workers are busy; retry after recovery") }
    catch (_: RemoteException) { BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Pebble transport disconnected") }
    catch (e: Exception) { BridgeResult.err(ErrCodes.INTERNAL, e.message ?: "Bridge call failed") }

    private fun ensureCurrent(epoch: Long, svc: IBridgeService? = null) {
        if (!started || generation != epoch || (svc != null && bound.value !== svc)) throw CancellationException("Obsolete bridge generation")
    }

    private fun fail(epoch: Long, err: BridgeResult.Err, status: ConnectionStatus = ConnectionStatus.fromError(err)): BridgeResult.Err {
        synchronized(lock) {
            if (generation != epoch || !started) return readinessError()
            session = null
            if (err.code in setOf(ErrCodes.ACCESS_DENIED, ErrCodes.CERT_MISMATCH, ErrCodes.CONSENT_PENDING)) cache.invalidateAuthority()
            else cache.suspendAuthority()
            listener?.close(); listener = null
            if (err.code == ErrCodes.ACCESS_DENIED) SetupState.setAccessDenied(appContext, true)
            _status.value = status
        }
        return if (err.code == ErrCodes.ACCESS_DENIED) denied() else err
    }

    private fun invalidate(err: BridgeResult.Err, expected: Long? = null) {
        val old = synchronized(lock) {
            if (expected != null && expected != generation) return
            val previous = retireLocked(preserveAuthority = err.code !in setOf(ErrCodes.ACCESS_DENIED, ErrCodes.CERT_MISMATCH, ErrCodes.CONSENT_PENDING))
            if (err.code == ErrCodes.ACCESS_DENIED) SetupState.setAccessDenied(appContext, true)
            if (!isDenied()) _status.value = ConnectionStatus.fromError(err)
            else _status.value = ConnectionStatus.Error(ErrCodes.ACCESS_DENIED, DENIED_MESSAGE)
            previous
        }
        cleanup(old)
    }

    private fun retireLocked(preserveAuthority: Boolean = true): Pair<IBridgeService, BridgeSession>? {
        ++generation
        attempt?.cancel(); attempt = null
        val old = session?.let { sess -> bound.value?.let { it to sess } }
        session = null
        if (preserveAuthority) cache.suspendAuthority() else cache.invalidateAuthority()
        listener?.close(); listener = null
        return old
    }

    private fun cleanup(old: Pair<IBridgeService, BridgeSession>?) {
        old ?: return
        scope.launch { ipc(1_000) { old.first.unregisterEventListener(old.second.clientToken); BridgeResult.Ok(Unit) } }
    }

    private fun scheduleRecovery(delayMs: Long) {
        synchronized(lock) {
            if (!started || isDenied() || recoveryAttempts >= 3 || rebindJob?.isActive == true) return
            recoveryAttempts++
            rebindJob = scope.launch {
                delay(delayMs)
                synchronized(lock) { rebindJob = null }
                val result = awaitReady()
                if (result is BridgeResult.Err && result.code in setOf(ErrCodes.BRIDGE_UNREACHABLE, ErrCodes.TIMEOUT))
                    scheduleRecovery(delayMs * 2)
            }
        }
    }

    private fun isDenied() = (_status.value as? ConnectionStatus.Error)?.code == ErrCodes.ACCESS_DENIED || SetupState.isAccessDenied(appContext)
    fun readinessError(): BridgeResult.Err = synchronized(lock) {
        when (val state = _status.value) {
            is ConnectionStatus.Error -> if (state.code == ErrCodes.ACCESS_DENIED) denied() else BridgeResult.Err(state.code, state.message, state.bridgeCode)
            is ConnectionStatus.ConsentPending -> BridgeResult.Err(ErrCodes.CONSENT_PENDING, state.message, "CONSENT_PENDING")
            is ConnectionStatus.NotAuthorized -> BridgeResult.Err(ErrCodes.NOT_AUTHORIZED, state.message, "NOT_AUTHORIZED")
            is ConnectionStatus.CertMismatch -> BridgeResult.err(ErrCodes.CERT_MISMATCH, "Review and trust the Pebble app signature")
            ConnectionStatus.AppAbsent -> BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Pebble app is not installed")
            else -> BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "Disconnected from Pebble")
        }
    }

    fun awaitReadyBlocking(timeoutMs: Long = READY_TIMEOUT_MS): BridgeResult<BridgeSession> = runBlocking { awaitReady(timeoutMs) }
    fun getStateBlocking(): BridgeResult<StateResult> {
        val budget = com.nickbether.pebbletasker.tasker.base.ConditionQueryBudget.remaining(STATE_TIMEOUT_MS)
        return runBlocking { getState(budget) }
    }
    fun executeBlocking(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> = runBlocking { execute(cmd) }

    sealed class ConnectionStatus {
        object Idle : ConnectionStatus()
        object Disconnected : ConnectionStatus()
        object AppAbsent : ConnectionStatus()
        data class Ready(val session: BridgeSession) : ConnectionStatus()
        data class NotAuthorized(val message: String) : ConnectionStatus()
        data class ConsentPending(val message: String) : ConnectionStatus()
        data class CertMismatch(val currentSha: String?) : ConnectionStatus()
        data class Error(val code: Int, val message: String, val bridgeCode: String? = null) : ConnectionStatus()
        companion object {
            fun fromError(err: BridgeResult.Err): ConnectionStatus = when (err.code) {
                ErrCodes.NOT_AUTHORIZED -> NotAuthorized(err.message)
                ErrCodes.CONSENT_PENDING -> ConsentPending(err.message)
                // A remote CERT_MISMATCH rejects the plugin identity, never the local host pin.
                ErrCodes.ACCESS_DENIED -> Error(err.code, DENIED_MESSAGE, "ACCESS_DENIED")
                else -> Error(err.code, err.message, err.bridgeCode)
            }
        }
    }
    companion object {
        const val CLIENT_LABEL = "Pebble Tasker Plugin"
        const val CLIENT_PROTOCOL = 1
        val WANTS = listOf(BridgeSession.CAP_EVENTS_CORE)
        const val STATE_TIMEOUT_MS = 4_000L
        const val PLUGIN_TIMEOUT_MS = 25_000L
        const val READY_TIMEOUT_MS = 12_000L
        const val HANDSHAKE_TIMEOUT_MS = 3_000L
        const val EXECUTE_TIMEOUT_MS = 8_000L
        const val DENIED_MESSAGE = "Denied in the Pebble app"
        private fun denied() = BridgeResult.Err(ErrCodes.ACCESS_DENIED, DENIED_MESSAGE, "ACCESS_DENIED")
        private fun timeout() = BridgeResult.err(ErrCodes.TIMEOUT, "Timed out waiting for Pebble")
        @Volatile private var instance: BridgeClient? = null
        fun init(context: Context): BridgeClient = get(context).also { it.start() }
        fun get(context: Context): BridgeClient = instance ?: synchronized(this) {
            instance ?: BridgeClient(context.applicationContext).also { instance = it }
        }
    }
}
