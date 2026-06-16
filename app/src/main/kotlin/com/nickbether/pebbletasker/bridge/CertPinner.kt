package com.nickbether.pebbletasker.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.security.MessageDigest

/**
 * Trust-on-first-use (TOFU) pinning of the bridge app's (`coredevices.coreapp`) signing certificate
 * (FINAL DESIGN §3.6).
 *
 * Symmetry with the bridge's own AndroidPackageInspector (PackageInspector.kt):
 *   - reads `signingInfo.apkContentsSigners.firstOrNull()` (verified PackageInspector:25,32);
 *   - SHA-256, lowercase hex.
 *
 * On first successful resolution we store the digest. On every subsequent bind we verify the current
 * signer against the stored pin USING PackageManager.hasSigningCertificate (which tolerates the app's
 * own key-rotation lineage) — this leniency is ONLY for the bridge app's cert. The bridge in turn
 * pins THIS plugin's cert with exact contentEquals and NO rotation lineage, which is why the plugin
 * must never rotate its own signing key without re-consent (FINAL DESIGN §3.6 / FIX D2).
 *
 * Pin storage is delegated to [PinStore] so the persistence mechanism (SharedPreferences/DataStore)
 * is swappable; a process-memory default is provided for the scaffold.
 */
class CertPinner(
    private val context: Context,
    private val store: PinStore = PinStore.inMemory(),
    private val targetPackage: String = BRIDGE_PACKAGE,
) {
    enum class PinResult {
        /** First-ever success: the current digest was just stored. Trust established. */
        PINNED,

        /** Current signer matches the stored pin (directly or via the app's rotation lineage). */
        OK,

        /** Current signer does NOT match the stored pin. Block commands; require user re-trust. */
        MISMATCH,

        /** The bridge app is not installed / not resolvable. */
        APP_ABSENT,
    }

    /** SHA-256 (lowercase hex) of the bridge app's first apkContentsSigner, or null if absent. */
    fun currentSha(): String? {
        val sig = firstSignerBytes() ?: return null
        return sha256Hex(sig)
    }

    /**
     * Verify (and, on first run, establish) the pin. Call before every handshake.
     */
    fun verify(): PinResult {
        val current = currentSha() ?: return PinResult.APP_ABSENT
        val stored = store.read()
        if (stored == null) {
            store.write(current)
            return PinResult.PINNED
        }
        if (stored == current) return PinResult.OK
        // Stored digest differs from the current first-signer; tolerate the app's own rotation
        // lineage via PackageManager before declaring a mismatch.
        return if (matchesViaLineage(stored)) PinResult.OK else PinResult.MISMATCH
    }

    /** User-confirmed re-pin to the current signer (used by the CERT_MISMATCH re-trust flow). */
    fun repinToCurrent(): String? {
        val current = currentSha() ?: return null
        store.write(current)
        return current
    }

    fun clearPin() = store.clear()

    // --- internals ---

    private fun firstSignerBytes(): ByteArray? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            firstSignerBytesApi28()
        } else {
            firstSignerBytesLegacy()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (t: Throwable) {
        null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun firstSignerBytesApi28(): ByteArray? {
        val info = context.packageManager.getPackageInfo(
            targetPackage,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        return info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun firstSignerBytesLegacy(): ByteArray? {
        val info = context.packageManager.getPackageInfo(
            targetPackage,
            PackageManager.GET_SIGNATURES,
        )
        return info.signatures?.firstOrNull()?.toByteArray()
    }

    /**
     * Returns true if the bridge app's current signing identity is consistent with [storedSha] under
     * the platform's certificate-rotation rules. We can't pass a raw digest to hasSigningCertificate,
     * so we re-derive the candidate cert bytes from the live package and confirm the stored digest
     * still equals one of the package's certificate-chain digests (past or present).
     */
    private fun matchesViaLineage(storedSha: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val info = context.packageManager.getPackageInfo(
                targetPackage,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val signing = info.signingInfo ?: return false
            val chain = buildList {
                addAll(signing.apkContentsSigners ?: emptyArray())
                if (signing.hasMultipleSigners()) {
                    // multi-signer apps: all current signers count
                } else {
                    addAll(signing.signingCertificateHistory ?: emptyArray())
                }
            }
            chain.any { sha256Hex(it.toByteArray()) == storedSha }
        } catch (t: Throwable) {
            false
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Pluggable pin persistence. */
    interface PinStore {
        fun read(): String?
        fun write(sha: String)
        fun clear()

        companion object {
            fun inMemory(): PinStore = object : PinStore {
                @Volatile private var v: String? = null
                override fun read() = v
                override fun write(sha: String) { v = sha }
                override fun clear() { v = null }
            }
        }
    }

    companion object {
        const val BRIDGE_PACKAGE = "coredevices.coreapp"
    }
}
