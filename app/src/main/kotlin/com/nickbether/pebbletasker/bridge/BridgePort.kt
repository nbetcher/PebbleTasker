package com.nickbether.pebbletasker.bridge

import android.content.Context
import android.net.Uri
import com.nickbether.pebbletasker.bridge.dto.CommandEnvelope
import com.nickbether.pebbletasker.bridge.dto.WatchRef
import com.nickbether.pebbletasker.cache.CachedEvent
import com.nickbether.pebbletasker.cache.LiveEvents

/**
 * The narrow bridge surface used by long-running flows (job waits, watch diagnostics). Everything goes
 * through the same IPC as Tasker actions; the interface exists so those flows can be tested without
 * a device.
 */
interface BridgePort {
    val session: BridgeSession?
    suspend fun ready(): BridgeResult<BridgeSession>
    suspend fun execute(type: String, watch: String?, args: Map<String, String> = emptyMap()): BridgeResult<Map<String, String>>
    suspend fun watches(): BridgeResult<List<WatchRef>>
    fun eventMark(): Long
    suspend fun awaitEvent(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean): CachedEvent?
    /** Reads a host content:// URI granted to this package; null when unreadable. */
    suspend fun read(uri: String): ByteArray?
}

class AndroidBridgePort(context: Context) : BridgePort {
    private val appContext = context.applicationContext
    private val client get() = BridgeClient.get(appContext)

    override val session: BridgeSession? get() = client.currentSession
    override suspend fun ready() = client.awaitReady()

    override suspend fun execute(type: String, watch: String?, args: Map<String, String>): BridgeResult<Map<String, String>> =
        when (val r = client.execute(CommandEnvelope(type = type, watch = watch?.ifBlank { null }, args = args))) {
            is BridgeResult.Ok -> BridgeResult.Ok(r.value.data ?: emptyMap())
            is BridgeResult.Err -> r
        }

    override suspend fun watches(): BridgeResult<List<WatchRef>> = client.getState().map { it.data.watches }
    override fun eventMark(): Long = LiveEvents.mark()
    override suspend fun awaitEvent(after: Long, timeoutMs: Long, predicate: (CachedEvent) -> Boolean) =
        LiveEvents.await(after, timeoutMs, predicate)

    override suspend fun read(uri: String): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }.getOrNull()
    }
}
