package it.zorn.nfcbro.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import it.zorn.nfcbro.R
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

    fun parse(context: Context, message: NdefMessage?): List<NdefRecordInfo> =
        message?.records?.map { parseRecord(context, it) } ?: emptyList()

    fun parseRecord(context: Context, record: NdefRecord): NdefRecordInfo {
        val raw = Hex.pretty(record.payload)
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> parseWellKnown(context, record, raw)
            NdefRecord.TNF_MIME_MEDIA -> parseMime(context, record, raw)
            NdefRecord.TNF_EXTERNAL_TYPE -> parseExternal(context, record, raw)
            NdefRecord.TNF_ABSOLUTE_URI ->
                NdefRecordInfo("URI", String(record.type, Charsets.UTF_8), context.getString(R.string.ndef_absolute_uri), raw)
            NdefRecord.TNF_EMPTY ->
                NdefRecordInfo(context.getString(R.string.ndef_empty), context.getString(R.string.ndef_empty_record), "TNF 0x00", raw)
            else ->
                NdefRecordInfo(context.getString(R.string.unknown), raw.ifEmpty { context.getString(R.string.ndef_no_payload) }, "TNF 0x%02X".format(record.tnf), raw)
        }
    }

    private fun parseWellKnown(context: Context, record: NdefRecord, raw: String): NdefRecordInfo {
        return when {
            record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val p = record.payload
                if (p.isEmpty()) return NdefRecordInfo(context.getString(R.string.text), "", context.getString(R.string.ndef_empty), raw)
                val status = p[0].toInt()
                val langLen = status and 0x3F
                val charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
                val lang = String(p, 1, langLen, Charsets.US_ASCII)
                val text = String(p, 1 + langLen, p.size - 1 - langLen, charset)
                NdefRecordInfo(context.getString(R.string.text), text, context.getString(R.string.ndef_language, lang), raw)
            }
            record.type.contentEquals(NdefRecord.RTD_URI) -> {
                val p = record.payload
                if (p.isEmpty()) return NdefRecordInfo("URI", "", context.getString(R.string.ndef_empty), raw)
                val idx = p[0].toInt() and 0xFF
                val prefix = URI_PREFIXES.getOrElse(idx) { "" }
                val uri = prefix + String(p, 1, p.size - 1, Charsets.UTF_8)
                NdefRecordInfo("URI", uri, context.getString(R.string.ndef_uri_record), raw)
            }
            record.type.contentEquals(byteArrayOf('S'.code.toByte(), 'p'.code.toByte())) ->
                NdefRecordInfo(context.getString(R.string.ndef_smart_poster), summariseSmartPoster(context, record.payload), context.getString(R.string.ndef_smart_poster), raw)
            else ->
                NdefRecordInfo(context.getString(R.string.ndef_well_known), String(record.payload, Charsets.UTF_8), "RTD: ${String(record.type, Charsets.UTF_8)}", raw)
        }
    }

    private fun summariseSmartPoster(context: Context, payload: ByteArray): String = runCatching {
        val inner = NdefMessage(payload)
        parse(context, inner).joinToString(" · ") { "${it.kind}: ${it.summary}" }
    }.getOrDefault(Hex.pretty(payload))

    private fun parseMime(context: Context, record: NdefRecord, raw: String): NdefRecordInfo {
        val mime = record.toMimeType() ?: "application/octet-stream"
        return when {
            mime.equals("application/vnd.wfa.wsc", true) ->
                NdefRecordInfo(context.getString(R.string.wifi), parseWifiSsid(record.payload) ?: context.getString(R.string.ndef_wifi_config), context.getString(R.string.ndef_wifi_simple_config), raw)
            mime.contains("vcard", true) ->
                NdefRecordInfo(context.getString(R.string.contact), String(record.payload, Charsets.UTF_8), mime, raw)
            mime.startsWith("text/") ->
                NdefRecordInfo("MIME", String(record.payload, Charsets.UTF_8), mime, raw)
            else ->
                NdefRecordInfo("MIME", context.getString(R.string.ndef_bytes, record.payload.size), mime, raw)
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

    private fun parseExternal(context: Context, record: NdefRecord, raw: String): NdefRecordInfo {
        val type = String(record.type, Charsets.UTF_8)
        return if (type.equals("android.com:pkg", true)) {
            NdefRecordInfo(context.getString(R.string.ndef_android_app), String(record.payload, Charsets.UTF_8), "AAR", raw)
        } else {
            NdefRecordInfo(context.getString(R.string.ndef_external), String(record.payload, Charsets.UTF_8), type, raw)
        }
    }
}
