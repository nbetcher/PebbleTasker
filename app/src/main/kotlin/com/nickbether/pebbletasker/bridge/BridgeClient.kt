package com.nickbether.pebbletasker.bridge

import android.content.Context
import android.os.RemoteException
import com.nickbether.pebbletasker.bridge.dto.BridgeHello
import com.nickbether.pebbletasker.bridge.dto.ClientHello
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.bridge.dto.ResultEnvelope
import com.nickbether.pebbletasker.bridge.dto.StateResult
import com.nickbether.pebbletasker.cache.EventCache
import com.nickbether.pebbletasker.cache.EventRouter
import com.nickbether.pebbletasker.tasker.ErrCodes
import coredevices.coreapp.automation.IBridgeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

/**
 * Process-singleton facade over the bound bridge AIDL service (FINAL DESIGN §3).
 *
 * Responsibilities:
 *  - own the [BridgeConnection] (sticky bind + backoff);
 *  - on every (re)connect: TOFU cert-pin check -> handshake -> register the event listener
 *    (always re-handshaking, never reusing a token — FIX A1);
 *  - serialize ALL cross-process AIDL on a single-thread [bridgeDispatcher] (every AIDL call is
 *    blocking and must never touch the main thread);
 *  - expose suspend [getState] / [execute] with staggered 9s timeouts (bridge ~7s, Tasker 10s);
 *  - surface consent/trust state ([ConnectionStatus]) so the UI can guide the user through
 *    NOT_AUTHORIZED / CONSENT_PENDING / CERT_MISMATCH (FIX D1);
 *  - drain the listener's routed-event channel and hand it to [EventRouter] off the Binder thread.
 *
 * USAGE (feature implementers):
 *   - Call [init] once from PebbleTaskerApp.onCreate.
 *   - In a state runner: `BridgeClient.get(ctx).getStateBlocking()` (runner threads aren't coroutines)
 *     or `runBlocking { getState() }`; map BridgeResult.Err -> ConditionUnknown.
 *   - In an action runner: `BridgeClient.get(ctx).executeBlocking(cmd)`; map per the
 *     success-with-ok=false model.
 *   - Observe [status] for onboarding/diagnostics.
 */
class BridgeClient private constructor(private val appContext: Context) {

    // Single-threaded dispatcher: serializes every blocking AIDL call.
    private val bridgeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pb-bridge-ipc").apply { isDaemon = true }
    }
    private val bridgeDispatcher = bridgeExecutor.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val certPinner = CertPinner(appContext)
    private val cache get() = EventCache.get(appContext)

    @Volatile private var session: BridgeSession? = null

    private val listener = BridgeListener(
        appContext = appContext,
        currentBootId = { session?.bootId },
        onGoodbye = { onGoodbye() },
    )

    private val connection: BridgeConnection = BridgeConnection(
        appContext = appContext,
        onConnected = { svc -> onConnected(svc) },
        onDisconnected = { onDisconnected() },
        scheduleRebind = { delayMs -> scope.launch { delay(delayMs); connection.ensureBound() } },
    )

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)

    /** Observable connection/consent status for UI (onboarding, diagnostics, consent guidance). */
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    /** The live session snapshot, or null if not handshaken. Exposes caps/grants for capability gating. */
    val currentSession: BridgeSession? get() = session

    // --- lifecycle ---

    /** Start sticky binding and drain the routed-event channel. Idempotent. */
    fun start() {
        // Drain routed events off the Binder thread and dispatch to Tasker.
        scope.launch {
            for (routed in listener.routedChannel) {
                runCatching { EventRouter.routeAll(appContext, routed) }
            }
        }
        connection.ensureBound()
    }

    fun stop() {
        runCatching { unregisterQuietly() }
        connection.unbind()
        session = null
        _status.value = ConnectionStatus.Idle
    }

    // --- connect / handshake / register (runs on bridgeDispatcher) ---

    private fun onConnected(svc: IBridgeService) {
        scope.launch(bridgeDispatcher) {
            handshakeAndRegister(svc)
        }
    }

    private fun onDisconnected() {
        session = null
        _status.value = ConnectionStatus.Disconnected
    }

    private fun onGoodbye() {
        // Revocation/shutdown: clear session and proactively rebind (events have stopped).
        session = null
        _status.value = ConnectionStatus.Disconnected
        scope.launch { delay(BridgeConnection.MIN_BACKOFF_MS); connection.ensureBound() }
    }

    /**
     * The full connect sequence. ALWAYS re-handshakes (fresh token) and re-checks the cert pin first.
     * Surfaces consent/trust errors to [status] for the UI.
     */
    private suspend fun handshakeAndRegister(svc: IBridgeService) {
        // 1. TOFU cert check BEFORE trusting anything.
        when (certPinner.verify()) {
            CertPinner.PinResult.MISMATCH -> {
                _status.value = ConnectionStatus.CertMismatch(certPinner.currentSha())
                return
            }
            CertPinner.PinResult.APP_ABSENT -> {
                _status.value = ConnectionStatus.AppAbsent
                return
            }
            CertPinner.PinResult.PINNED, CertPinner.PinResult.OK -> Unit
        }

        // 2. Handshake (envelope-aware decode).
        val helloJson = BridgeCodec.encodeHello(
            ClientHello(
                clientLabel = CLIENT_LABEL,
                clientProtocol = CLIENT_PROTOCOL,
                wants = WANTS,
            ),
        )
        val helloResult = callBridge { BridgeCodec.decodeHello(svc.handshake(helloJson)) }
        val hello: BridgeHello = when (helloResult) {
            is BridgeResult.Ok -> helloResult.value
            is BridgeResult.Err -> {
                _status.value = ConnectionStatus.fromError(helloResult)
                maybeScheduleConsentPoll(helloResult)
                return
            }
        }

        val sess = BridgeSession.from(hello)
        session = sess

        // 3. Seed the event-cache high-water from BridgeHello.latestSeq (NOT an empty batch).
        cache.seedFromHandshake(sess.bootId, sess.latestSeq)

        // 4. Register the listener with the FRESH token at our current high-water for this boot.
        val fromSeq = cache.highWaterSeqFor(sess.bootId).let { if (it < 0) sess.latestSeq else it }
        val registered = callBridge {
            svc.registerEventListener(sess.clientToken, listener, fromSeq)
            // registerEventListener is void + fails SILENTLY on a stale token; no ack exists.
            // Liveness-probe via getEventsSince to confirm registration took.
            BridgeCodec.decodeBatch(svc.getEventsSince(fromSeq, sess.bootId))
        }
        when (registered) {
            is BridgeResult.Ok -> {
                // Ingest any replay the probe returned (deduped by EventCache).
                val routed = cache.putBatch(registered.value.events)
                if (routed.isNotEmpty()) listener.routedChannel.trySend(routed)
                _status.value = ConnectionStatus.Ready(sess)
                // Freshly (re)connected: re-query every registered condition so Tasker states/events
                // reflect the CURRENT bridge snapshot now — even when the triggering connect/disconnect
                // happened before this session existed (so no event would ever push them).
                EventRouter.requestQueryAll(appContext)
            }
            is BridgeResult.Err -> {
                // Probe failed -> registration likely dropped; surface and let backoff re-handshake.
                _status.value = ConnectionStatus.fromError(registered)
            }
        }
    }

    /** If the error is CONSENT_PENDING, poll handshake with its own backoff until resolved. */
    private fun maybeScheduleConsentPoll(err: BridgeResult.Err) {
        if (err.code != ErrCodes.CONSENT_PENDING) return
        scope.launch {
            var wait = CONSENT_POLL_MIN_MS
            while (status.value is ConnectionStatus.ConsentPending) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(CONSENT_POLL_MAX_MS)
                val svc = connection.serviceOrNull() ?: break
                withContext(bridgeDispatcher) { handshakeAndRegister(svc) }
            }
        }
    }

    // --- public AIDL surface (suspend) ---

    /** Snapshot query. queryJson is ignored by the bridge; we pass "{}". 9s timeout. */
    suspend fun getState(): BridgeResult<StateResult> {
        val svc = connection.serviceOrNull()
            ?: return BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "not bound")
        return callBridgeTimed { BridgeCodec.decodeState(svc.getState("{}")) }
    }

    /**
     * Execute a command on the watch/app. Gated behind the `commands.core` capability (the bridge
     * does not advertise it yet -> UNSUPPORTED_COMMAND). 9s timeout; RemoteException (old bridge with
     * no execute() transaction) is mapped to UNSUPPORTED_COMMAND.
     *
     * NOTE: execute() is the planned bridge AIDL addition (txn code 6). The AIDL stub in this app
     * declares it, so this compiles; against a running bridge that lacks it, the call throws
     * RemoteException, handled below.
     */
    suspend fun execute(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> {
        val sess = session
            ?: return BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "no session")
        if (!sess.has(BridgeSession.CAP_COMMANDS_CORE)) {
            return BridgeResult.err(ErrCodes.UNSUPPORTED_COMMAND, "bridge does not support commands yet")
        }
        val svc = connection.serviceOrNull()
            ?: return BridgeResult.err(ErrCodes.BRIDGE_UNREACHABLE, "not bound")
        val json = BridgeCodec.encodeCommand(cmd)
        return callBridgeTimed { BridgeCodec.decodeResult(svc.execute(sess.clientToken, json)) }
    }

    /** Force a fresh bind + handshake (e.g. after the user flips the master switch or re-trusts). */
    fun retryHandshake() {
        when (connection.state) {
            BridgeConnection.State.BOUND -> connection.serviceOrNull()?.let { onConnected(it) }
            else -> connection.ensureBound()
        }
    }

    /** User-confirmed re-pin after CERT_MISMATCH, then re-handshake. */
    fun retrustCert() {
        certPinner.repinToCurrent()
        retryHandshake()
    }

    // --- blocking convenience for runner threads (NOT coroutines) ---

    /** Blocking getState for use on a Tasker runner's IntentService thread. */
    fun getStateBlocking(): BridgeResult<StateResult> =
        kotlinx.coroutines.runBlocking { getState() }

    /** Blocking execute for use on a Tasker runner's IntentService thread. */
    fun executeBlocking(cmd: CommandEnvelope): BridgeResult<ResultEnvelope> =
        kotlinx.coroutines.runBlocking { execute(cmd) }

    // --- internals ---

    /** Run a blocking AIDL block on the bridge dispatcher, mapping RemoteException to an Err. */
    private suspend fun <T> callBridge(block: () -> BridgeResult<T>): BridgeResult<T> =
        withContext(bridgeDispatcher) {
            try {
                block()
            } catch (e: RemoteException) {
                BridgeResult.err(ErrCodes.UNSUPPORTED_COMMAND, "remote call failed: ${e.message}")
            } catch (t: Throwable) {
                BridgeResult.err(ErrCodes.INTERNAL, t.message ?: "bridge call failed")
            }
        }

    /** Like [callBridge] but wrapped in the plugin-side 9s timeout (staggered below Tasker's 10s). */
    private suspend fun <T> callBridgeTimed(block: () -> BridgeResult<T>): BridgeResult<T> =
        try {
            withTimeout(PLUGIN_TIMEOUT_MS) { callBridge(block) }
        } catch (e: TimeoutCancellationException) {
            BridgeResult.err(ErrCodes.TIMEOUT, "bridge timed out")
        }

    private fun unregisterQuietly() {
        val sess = session ?: return
        val svc = connection.serviceOrNull() ?: return
        scope.launch(bridgeDispatcher) {
            runCatching { svc.unregisterEventListener(sess.clientToken) }
        }
    }

    /** High-level connection status for UI. */
    sealed class ConnectionStatus {
        object Idle : ConnectionStatus()
        object Disconnected : ConnectionStatus()
        object AppAbsent : ConnectionStatus()
        data class Ready(val session: BridgeSession) : ConnectionStatus()

        /** Master Automation switch is OFF (default) or access revoked — user must enable in Pebble. */
        data class NotAuthorized(val message: String) : ConnectionStatus()

        /** Master is ON; bridge is awaiting the user's in-app approval. Auto-polls. */
        data class ConsentPending(val message: String) : ConnectionStatus()

        /** Bridge app's signing cert changed since TOFU pin — user must re-trust. */
        data class CertMismatch(val currentSha: String?) : ConnectionStatus()

        /** Any other bridge error. */
        data class Error(val code: Int, val message: String) : ConnectionStatus()

        companion object {
            fun fromError(err: BridgeResult.Err): ConnectionStatus = when (err.code) {
                ErrCodes.NOT_AUTHORIZED -> NotAuthorized(err.message)
                ErrCodes.CONSENT_PENDING -> ConsentPending(err.message)
                ErrCodes.CERT_MISMATCH -> CertMismatch(null)
                else -> Error(err.code, err.message)
            }
        }
    }

    companion object {
        const val CLIENT_LABEL = "Pebble Tasker Plugin"
        const val CLIENT_PROTOCOL = 1

        /** Categories the plugin wants at handshake. Today the bridge only grants events.core. */
        val WANTS = listOf(BridgeSession.CAP_EVENTS_CORE)

        const val PLUGIN_TIMEOUT_MS = 9_000L
        const val CONSENT_POLL_MIN_MS = 3_000L
        const val CONSENT_POLL_MAX_MS = 30_000L

        @Volatile private var instance: BridgeClient? = null

        /** Initialize and start the singleton (call once from Application.onCreate). */
        fun init(context: Context): BridgeClient = get(context).also { it.start() }

        /** Get the singleton, creating it if needed. Safe from any thread. */
        fun get(context: Context): BridgeClient =
            instance ?: synchronized(this) {
                instance ?: BridgeClient(context.applicationContext).also { instance = it }
            }
    }
}
