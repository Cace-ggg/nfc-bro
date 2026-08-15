package it.zorn.nfcbro.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset

object NdefParser {

    private val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
        "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
        "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
        "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
        "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    )

    fun parse(message: NdefMessage?): List<NdefRecordInfo> =
        message?.records?.map { parseRecord(it) } ?: emptyList()

    fun parseRecord(record: NdefRecord): NdefRecordInfo {
        val raw = Hex.pretty(record.payload)
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> parseWellKnown(record, raw)
            NdefRecord.TNF_MIME_MEDIA -> parseMime(record, raw)
            NdefRecord.TNF_EXTERNAL_TYPE -> parseExternal(record, raw)
            NdefRecord.TNF_ABSOLUTE_URI ->
                NdefRecordInfo("URI", String(record.type, Charsets.UTF_8), "Absolute URI", raw)
            NdefRecord.TNF_EMPTY ->
                NdefRecordInfo("Empty", "(empty record)", "TNF 0x00", raw)
            else ->
                NdefRecordInfo("Unknown", raw.ifEmpty { "(no payload)" }, "TNF 0x%02X".format(record.tnf), raw)
        }
    }

    private fun parseWellKnown(record: NdefRecord, raw: String): NdefRecordInfo {
        return when {
            record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val p = record.payload
                if (p.isEmpty()) return NdefRecordInfo("Text", "", "empty", raw)
                val status = p[0].toInt()
                val langLen = status and 0x3F
                val charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
                val lang = String(p, 1, langLen, Charsets.US_ASCII)
                val text = String(p, 1 + langLen, p.size - 1 - langLen, charset)
                NdefRecordInfo("Text", text, "Language: $lang", raw)
            }
            record.type.contentEquals(NdefRecord.RTD_URI) -> {
                val p = record.payload
                if (p.isEmpty()) return NdefRecordInfo("URI", "", "empty", raw)
                val idx = p[0].toInt() and 0xFF
                val prefix = URI_PREFIXES.getOrElse(idx) { "" }
                val uri = prefix + String(p, 1, p.size - 1, Charsets.UTF_8)
                NdefRecordInfo("URI", uri, "URI record", raw)
            }
            record.type.contentEquals(byteArrayOf('S'.code.toByte(), 'p'.code.toByte())) ->
                NdefRecordInfo("Smart Poster", summariseSmartPoster(record.payload), "Smart Poster", raw)
            else ->
                NdefRecordInfo("Well-known", String(record.payload, Charsets.UTF_8), "RTD: ${String(record.type, Charsets.UTF_8)}", raw)
        }
    }

    private fun summariseSmartPoster(payload: ByteArray): String = runCatching {
        val inner = NdefMessage(payload)
        parse(inner).joinToString(" · ") { "${it.kind}: ${it.summary}" }
    }.getOrDefault(Hex.pretty(payload))

    private fun parseMime(record: NdefRecord, raw: String): NdefRecordInfo {
        val mime = record.toMimeType() ?: "application/octet-stream"
        return when {
            mime.equals("application/vnd.wfa.wsc", true) ->
                NdefRecordInfo("Wi-Fi", parseWifiSsid(record.payload) ?: "Wi-Fi configuration", "Wi-Fi Simple Config", raw)
            mime.contains("vcard", true) ->
                NdefRecordInfo("Contact", String(record.payload, Charsets.UTF_8), mime, raw)
            mime.startsWith("text/") ->
                NdefRecordInfo("MIME", String(record.payload, Charsets.UTF_8), mime, raw)
            else ->
                NdefRecordInfo("MIME", "${record.payload.size} bytes", mime, raw)
        }
    }

    /** Best-effort SSID extraction from a WSC credential blob. */
    private fun parseWifiSsid(payload: ByteArray): String? {
        var i = 0
        while (i + 4 <= payload.size) {
            val type = ((payload[i].toInt() and 0xFF) shl 8) or (payload[i + 1].toInt() and 0xFF)
            val len = ((payload[i + 2].toInt() and 0xFF) shl 8) or (payload[i + 3].toInt() and 0xFF)
            val start = i + 4
            if (start + len > payload.size) break
            if (type == 0x1045) { // SSID attribute
                return "SSID: " + String(payload, start, len, Charsets.UTF_8)
            }
            i = start + len
        }
        return null
    }

    private fun parseExternal(record: NdefRecord, raw: String): NdefRecordInfo {
        val type = String(record.type, Charsets.UTF_8)
        return if (type.equals("android.com:pkg", true)) {
            NdefRecordInfo("Android App", String(record.payload, Charsets.UTF_8), "AAR", raw)
        } else {
            NdefRecordInfo("External", String(record.payload, Charsets.UTF_8), type, raw)
        }
    }
}
