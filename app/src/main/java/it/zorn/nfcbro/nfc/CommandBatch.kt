package it.zorn.nfcbro.nfc

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
                        require(it.isNotEmpty()) { "Enter a hex command" }
                    }
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Line ${index + 1}: ${e.message}", e)
                }
            }.toList()
            require(commands.isNotEmpty()) { "Enter at least one command" }
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
    val error: String? = null,
) {
    private val status = if (isApdu && response != null && response.size >= 2) {
        val (sw1, sw2) = response.takeLast(2)
        ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)
    } else null
    val ok = error == null && (!isApdu || status == 0x9000 || status?.ushr(8) == 0x61)
    val responseHex = response?.let(Hex::pretty)
    val ascii = response?.let { Hex.ascii(it, to = if (status != null) it.size - 2 else it.size) }
    val statusText = status?.let { "%04X — %s".format(it, statusDescription(it)) }
        ?: if (isApdu && response != null) "Missing APDU status word" else null

    fun transcript(): String = buildString {
        append("→ ").append(request)
        append("\n← ").append(responseHex?.ifEmpty { "(empty response)" } ?: "(no response)")
        ascii?.takeIf { it.isNotEmpty() }?.let { append("\nASCII: ").append(it) }
        statusText?.let { append("\nSW: ").append(it) }
        error?.let { append('\n').append(it) }
    }
}

private fun statusDescription(status: Int): String = when (status) {
    0x9000 -> "Success"
    0x6700 -> "Wrong length"
    0x6982 -> "Security status not satisfied"
    0x6985 -> "Conditions of use not satisfied"
    0x6A82 -> "File or application not found"
    0x6A83 -> "Record not found"
    0x6A86 -> "Incorrect P1/P2"
    0x6D00 -> "Instruction not supported"
    0x6E00 -> "Class not supported"
    in 0x6100..0x61FF -> "Response bytes available"
    in 0x6C00..0x6CFF -> "Wrong Le; correct length in SW2"
    in 0x63C0..0x63CF -> "Warning; counter ${status and 0xF}"
    else -> "Card status"
}
