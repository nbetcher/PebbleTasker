package com.nickbether.pebbletasker.bridge

import com.nickbether.pebbletasker.bridge.dto.ErrorBody
import com.nickbether.pebbletasker.tasker.ErrCodes

/**
 * Result wrapper returned by every [BridgeClient] suspend call.
 *
 * Distinguishes:
 *  - [Ok]  the call completed and the bridge returned its nominal success type.
 *  - [Err] the call reached the bridge but it returned an error envelope ({ok:false}), OR the call
 *          could not complete (not bound, timeout, RemoteException). [code] is an [ErrCodes] int.
 *
 * Runners map [Err] differently depending on plugin kind (see PebbleActionRunner / PebbleEventRunner
 * / PebbleStateRunner): actions surface it as %pb_ok=false + %pb_err/%pb_errmsg; conditions return
 * Unknown so contexts don't flap.
 */
sealed class BridgeResult<out T> {
    data class Ok<T>(val value: T) : BridgeResult<T>()

    data class Err(
        val code: Int,
        val message: String,
        /** The original bridge string code, when this Err came from a {ok:false} envelope. */
        val bridgeCode: String? = null,
    ) : BridgeResult<Nothing>()

    val isOk: Boolean get() = this is Ok

    inline fun <R> map(transform: (T) -> R): BridgeResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    fun valueOrNull(): T? = (this as? Ok)?.value

    companion object {
        fun err(code: Int, message: String): Err = Err(code, message)

        /** Build an [Err] from a parsed bridge [ErrorBody], translating the string code to int. */
        fun fromError(error: ErrorBody?): Err {
            val code = error?.code
            return Err(
                code = ErrCodes.toInt(code),
                message = error?.message ?: code ?: "bridge error",
                bridgeCode = code,
            )
        }
    }
}
