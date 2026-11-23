package it.zorn.nfcbro.nfc

object Hex {
    internal class InputException(val invalidCharacter: Boolean, message: String) : IllegalArgumentException(message)

    private const val DIGITS = "0123456789ABCDEF"

    fun encode(bytes: ByteArray, separator: String = ""): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        for ((i, b) in bytes.withIndex()) {
            if (i > 0 && separator.isNotEmpty()) sb.append(separator)
            val v = b.toInt() and 0xFF
            sb.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    /** "AA BB CC" grouped in bytes. */
    fun pretty(bytes: ByteArray): String = encode(bytes, " ")

    /** Parse a hex string, ignoring spaces, colons and dashes. Throws on odd/invalid input. */
    fun decode(input: String): ByteArray {
        val clean = input.filterNot { it == ' ' || it == ':' || it == '-' || it == '\n' || it == '\t' }
        if (clean.length % 2 != 0) throw InputException(false, "Hex length must be even")
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val hi = Character.digit(clean[i], 16)
            val lo = Character.digit(clean[i + 1], 16)
            if (hi < 0 || lo < 0) throw InputException(true, "Invalid hex character")
            out[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    /** Printable ASCII rendering of a byte range, non-printables shown as '.'. */
    fun ascii(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String {
        val sb = StringBuilder()
        for (i in from until to.coerceAtMost(bytes.size)) {
            val c = bytes[i].toInt() and 0xFF
            sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
        }
        return sb.toString()
    }
}
