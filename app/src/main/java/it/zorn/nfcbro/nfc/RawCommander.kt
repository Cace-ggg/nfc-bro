package it.zorn.nfcbro.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal interface RawTransport {
    val name: String
    val isApdu: Boolean
    val isConnected: Boolean
    fun connect()
    fun transceive(command: ByteArray): ByteArray
    fun close()
}

internal class RawTagLostException(cause: Throwable? = null) : IOException(cause)

internal class RawCommander {
    var connection: RawConnection? = null
        private set

    private var transport: RawTransport? = null
    private var nextConnectionId = 0L

    fun connect(uid: String, openTransport: () -> RawTransport?): String? {
        if (connection?.uid == uid && checkPresence()) return null
        close()
        val next = openTransport() ?: return "This tag does not support IsoDep, NfcA or NfcV."
        transport = next
        return try {
            next.connect()
            connection = RawConnection(++nextConnectionId, uid, next.name, next.isApdu)
            null
        } catch (e: IOException) {
            close()
            e.nfcMessage()
        } catch (e: SecurityException) {
            close()
            "Tag is no longer available. Tap again."
        }
    }

    fun checkPresence(): Boolean {
        val present = try {
            transport?.isConnected == true
        } catch (_: SecurityException) {
            false
        }
        if (!present) close()
        return present
    }

    suspend fun execute(batch: CommandBatch, stopOnError: Boolean, onResult: (CommandResult) -> Unit): String? {
        val link = transport ?: return "Not connected. Tap a tag."
        for (command in batch.commands) {
            currentCoroutineContext().ensureActive()
            val request = Hex.pretty(command)
            val response = try {
                link.transceive(command)
            } catch (e: IOException) {
                return reportDisconnect(request, e.nfcMessage(), onResult)
            } catch (_: SecurityException) {
                return reportDisconnect(request, "Tag is no longer available. Tap again.", onResult)
            }
            val result = CommandResult(request, response, link.isApdu)
            onResult(result)
            if (stopOnError && !result.ok) break
        }
        return null
    }

    private fun reportDisconnect(request: String, error: String, onResult: (CommandResult) -> Unit): String {
        close()
        onResult(CommandResult(request, error = error))
        return error
    }

    fun close() {
        try {
            transport?.close()
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        } finally {
            transport = null
            connection = null
        }
    }
}

data class RawConnection(val id: Long, val uid: String, val transport: String, val isApdu: Boolean)

internal fun rawTransport(tag: Tag): RawTransport? {
    IsoDep.get(tag)?.let { tech ->
        return tagTransport(tech, "IsoDep", tech::transceive, isApdu = true) { tech.timeout = 3_000 }
    }
    NfcA.get(tag)?.let { tech ->
        return tagTransport(tech, "NfcA", tech::transceive) { tech.timeout = 3_000 }
    }
    NfcV.get(tag)?.let { tech -> return tagTransport(tech, "NfcV", tech::transceive) }
    return null
}

private fun tagTransport(
    tech: TagTechnology,
    name: String,
    exchange: (ByteArray) -> ByteArray,
    isApdu: Boolean = false,
    configure: () -> Unit = {},
): RawTransport = object : RawTransport {
    override val name = name
    override val isApdu = isApdu
    override val isConnected get() = tech.isConnected
    override fun connect() {
        withTagLoss {
            tech.connect()
            configure()
        }
    }
    override fun transceive(command: ByteArray) = withTagLoss { exchange(command) }
    override fun close() = tech.close()
}

private inline fun <T> withTagLoss(block: () -> T): T = try {
    block()
} catch (e: TagLostException) {
    throw RawTagLostException(e)
}

private fun IOException.nfcMessage(): String =
    if (this is RawTagLostException) "Tag removed. Tap again."
    else "NFC I/O error or timeout. Tap again."
