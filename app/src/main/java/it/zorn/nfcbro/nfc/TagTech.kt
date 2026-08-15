package it.zorn.nfcbro.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef

/** Static helpers to name manufacturers and tag types from a scanned [Tag]. */
object TagTech {

    private val MANUFACTURERS = mapOf(
        0x04 to "NXP Semiconductors",
        0x02 to "STMicroelectronics",
        0x05 to "Infineon Technologies",
        0x07 to "Texas Instruments",
        0x08 to "Fujitsu",
        0x16 to "EM Microelectronic-Marin",
        0x21 to "EM Microelectronic",
        0x28 to "Kovio",
        0x2B to "Maxim",
        0x34 to "Gemalto",
        0x35 to "Renesas",
        0x39 to "MicroSensys",
    )

    fun manufacturer(id: ByteArray): String {
        if (id.isEmpty()) return "Unknown"
        val code = id[0].toInt() and 0xFF
        return MANUFACTURERS[code] ?: "Unknown (0x%02X)".format(code)
    }

    fun shortTech(tech: String): String = tech.substringAfterLast('.')

    fun typeLabel(tag: Tag): String {
        val techs = tag.techList
        if (MifareClassic::class.java.name in techs) {
            val size = runCatching { MifareClassic.get(tag)?.size ?: 0 }.getOrDefault(0)
            val k = if (size > 0) " ${size / 1024}K" else ""
            return "MIFARE Classic$k"
        }
        if (MifareUltralight::class.java.name in techs) {
            val t = runCatching { MifareUltralight.get(tag)?.type }.getOrNull()
            return when (t) {
                MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
                else -> "MIFARE Ultralight family"
            }
        }
        if (Ndef::class.java.name in techs) {
            val type = runCatching { Ndef.get(tag)?.type }.getOrNull()
            when (type) {
                Ndef.NFC_FORUM_TYPE_1 -> return "NFC Forum Type 1"
                Ndef.NFC_FORUM_TYPE_2 -> return "NFC Forum Type 2"
                Ndef.NFC_FORUM_TYPE_3 -> return "NFC Forum Type 3 (FeliCa)"
                Ndef.NFC_FORUM_TYPE_4 -> return "NFC Forum Type 4"
                Ndef.MIFARE_CLASSIC -> return "MIFARE Classic (NDEF)"
            }
        }
        return techs.firstOrNull()?.let { shortTech(it) } ?: "Unknown"
    }
}
