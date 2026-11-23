package it.zorn.nfcbro.nfc

import it.zorn.nfcbro.R

internal class BatchInputException(val resource: Int, val line: Int? = null, cause: Throwable? = null) :
    IllegalArgumentException(cause)

class CommandBatch private constructor(internal val commands: List<ByteArray>) {
    val size get() = commands.size
    val hasShortApdu get() = commands.any { it.size < 4 }

    fun requiresConfirmation(isApdu: Boolean): Boolean = !isApdu || commands.any { !it.isReadOnlyApdu() }

    companion object {
        fun parse(input: String): CommandBatch {
            val commands = input.lineSequence().mapIndexedNotNull { index, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                try {
                    Hex.decode(line.filterNot(Char::isWhitespace)).also {
                        if (it.isEmpty()) throw BatchInputException(R.string.enter_hex, index + 1)
                    }
                } catch (e: Hex.InputException) {
                    val resource = if (e.invalidCharacter) R.string.hex_character else R.string.hex_length
                    throw BatchInputException(resource, index + 1, e)
                }
            }.toList()
            if (commands.isEmpty()) throw BatchInputException(R.string.enter_commands)
            return CommandBatch(commands)
        }
    }
}

private fun ByteArray.isReadOnlyApdu(): Boolean {
    if (size < 4) return false
    val cla = this[0].toInt() and 0xFF
    if (cla !in 0x00..0x03 && cla !in 0x40..0x4F) return false
    return when (this[1].toInt() and 0xFF) {
        0xA4, 0xB0, 0xB1, 0xB2, 0xB3, 0xC0, 0xCA, 0xCB -> true
        else -> false
    }
}

data class CommandResult(
    val request: String,
    private val response: ByteArray? = null,
    private val isApdu: Boolean = false,
    val error: Int? = null,
) {
    private val status = if (isApdu && response != null && response.size >= 2) {
        val (sw1, sw2) = response.takeLast(2)
        ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)
    } else null
    val ok = error == null && (!isApdu || status == 0x9000 || status?.ushr(8) == 0x61)
    val responseHex = response?.let(Hex::pretty)
    val ascii = response?.let { Hex.ascii(it, to = if (status != null) it.size - 2 else it.size) }
    val statusHex = status?.let { "%04X".format(it) }
    val statusResource = status?.let(::statusDescription) ?: if (isApdu && response != null) R.string.sw_missing else null

    fun transcript(getString: (Int, Array<out Any>) -> String): String = buildString {
        append("→ ").append(request)
        append("\n← ").append(responseHex?.ifEmpty { getString(R.string.empty_response, emptyArray()) }
            ?: getString(R.string.no_response, emptyArray()))
        ascii?.takeIf { it.isNotEmpty() }?.let { append("\nASCII: ").append(it) }
        statusResource?.let {
            val args = if (it == R.string.sw_counter && status != null) arrayOf(status and 0xF) else emptyArray()
            val description = getString(it, args)
            append("\nSW: ").append(statusHex?.let { hex -> "$hex — $description" } ?: description)
        }
        error?.let { append('\n').append(getString(it, emptyArray())) }
    }
}

private fun statusDescription(status: Int): Int = when (status) {
    0x9000 -> R.string.sw_success
    0x6700 -> R.string.sw_wrong_length
    0x6982 -> R.string.sw_security
    0x6985 -> R.string.sw_conditions
    0x6A82 -> R.string.sw_file_missing
    0x6A83 -> R.string.sw_record_missing
    0x6A86 -> R.string.sw_incorrect_parameters
    0x6D00 -> R.string.sw_instruction
    0x6E00 -> R.string.sw_class
    in 0x6100..0x61FF -> R.string.sw_available
    in 0x6C00..0x6CFF -> R.string.sw_incorrect_le
    in 0x63C0..0x63CF -> R.string.sw_counter
    else -> R.string.sw_card_status
}
