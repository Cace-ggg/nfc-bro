package it.zorn.nfcbro.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/** Builds NDEF records/messages for the write flow and for tag emulation. */
object NdefBuilder {

    fun message(vararg records: NdefRecord): NdefMessage = NdefMessage(records)

    fun text(text: String, language: String = "en"): NdefRecord {
        val lang = language.toByteArray(Charsets.US_ASCII)
        val payload = ByteArrayOutputStream().apply {
            write(lang.size and 0x3F)            // status byte: UTF-8, lang length
            write(lang)
            write(text.toByteArray(Charsets.UTF_8))
        }.toByteArray()
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    fun uri(uri: String): NdefRecord = NdefRecord.createUri(uri)

    fun mime(type: String, payload: ByteArray): NdefRecord = NdefRecord.createMime(type, payload)

    fun app(packageName: String): NdefRecord = NdefRecord.createApplicationRecord(packageName)

    fun tel(number: String): NdefRecord = uri("tel:${number.trim()}")

    fun sms(number: String, body: String): NdefRecord =
        uri("sms:${number.trim()}?body=${enc(body)}")

    fun email(address: String, subject: String, body: String): NdefRecord =
        uri("mailto:${address.trim()}?subject=${enc(subject)}&body=${enc(body)}")

    fun geo(lat: String, lon: String): NdefRecord = uri("geo:${lat.trim()},${lon.trim()}")

    fun contact(name: String, phone: String, email: String, org: String): NdefRecord {
        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            if (name.isNotBlank()) append("FN:$name\r\n")
            if (org.isNotBlank()) append("ORG:$org\r\n")
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:$phone\r\n")
            if (email.isNotBlank()) append("EMAIL:$email\r\n")
            append("END:VCARD\r\n")
        }
        return mime("text/vcard", vcard.toByteArray(Charsets.UTF_8))
    }

    /** Wi-Fi Simple Config credential (application/vnd.wfa.wsc). */
    fun wifi(ssid: String, password: String, security: String): NdefRecord {
        val (auth, enc) = when (security.uppercase()) {
            "WPA2", "WPA2-PSK", "WPA/WPA2" -> 0x0020 to 0x0008
            "WPA", "WPA-PSK" -> 0x0002 to 0x0004
            else -> 0x0001 to 0x0001 // open
        }
        val credential = ByteArrayOutputStream().apply {
            tlv(0x1026, byteArrayOf(0x01))                       // network index
            tlv(0x1045, ssid.toByteArray(Charsets.UTF_8))        // SSID
            tlv(0x1003, u16(auth))                               // auth type
            tlv(0x100F, u16(enc))                                // encryption type
            tlv(0x1027, password.toByteArray(Charsets.UTF_8))    // network key
            tlv(0x1020, ByteArray(6))                            // MAC address
        }.toByteArray()

        val payload = ByteArrayOutputStream().apply {
            tlv(0x100E, credential)                              // credential wrapper
        }.toByteArray()

        return mime("application/vnd.wfa.wsc", payload)
    }

    private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
        write((type ushr 8) and 0xFF); write(type and 0xFF)
        write((value.size ushr 8) and 0xFF); write(value.size and 0xFF)
        write(value)
    }

    private fun u16(v: Int) = byteArrayOf(((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
