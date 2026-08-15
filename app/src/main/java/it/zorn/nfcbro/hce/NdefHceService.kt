package it.zorn.nfcbro.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.nfc.NdefBuilder

/**
 * Emulates an NFC Forum Type 4 tag over Host Card Emulation. A reader selects the
 * NDEF application, reads the capability container, then reads the NDEF file we
 * serve from the user's saved emulation payload.
 *
 * Note: stock Android only allows ISO-DEP (Type 4) card emulation via HCE — it
 * cannot clone a tag's UID or emulate MIFARE Classic/Ultralight tags.
 */
class NdefHceService : HostApduService() {

    private var selectedFile = FILE_NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_FAIL

        // SELECT by name (NDEF application AID) -> routed here by the OS, accept it.
        if (apdu.matches(0x00, 0xA4, 0x04, 0x00)) {
            selectedFile = FILE_NONE
            return SW_OK
        }

        // SELECT by file id: 00 A4 00 0C 02 <FID hi> <FID lo>
        if (apdu.size >= 7 && apdu.matches(0x00, 0xA4, 0x00, 0x0C)) {
            val fid = (apdu[5].u() shl 8) or apdu[6].u()
            selectedFile = when (fid) {
                0xE103 -> FILE_CC
                0xE104 -> FILE_NDEF
                else -> FILE_NONE
            }
            return if (selectedFile == FILE_NONE) SW_FILE_NOT_FOUND else SW_OK
        }

        // READ BINARY: 00 B0 <offset hi> <offset lo> <Le>
        if (apdu.size >= 5 && apdu.matches2(0x00, 0xB0)) {
            val file = when (selectedFile) {
                FILE_CC -> capabilityContainer()
                FILE_NDEF -> ndefFile()
                else -> return SW_FILE_NOT_FOUND
            }
            val offset = (apdu[2].u() shl 8) or apdu[3].u()
            val le = apdu[4].u().let { if (it == 0) 256 else it }
            if (offset > file.size) return SW_WRONG_OFFSET
            val end = minOf(offset + le, file.size)
            return file.copyOfRange(offset, end) + SW_OK
        }

        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = FILE_NONE
    }

    private fun capabilityContainer(): ByteArray = byteArrayOf(
        0x00, 0x0F,             // CCLEN = 15
        0x20,                   // mapping version 2.0
        0x00, 0x3B,             // MLe
        0x00, 0x34,             // MLc
        0x04, 0x06,             // NDEF file control TLV
        0xE1.toByte(), 0x04,    // file id
        0x7F, 0xFF.toByte(),    // max NDEF size
        0x00,                   // read access granted
        0xFF.toByte(),          // write access denied
    )

    private fun ndefFile(): ByteArray {
        val emu = Store.currentEmulation()
        val message = if (emu.enabled && emu.content.isNotBlank()) {
            val record = if (emu.kind == "url") NdefBuilder.uri(emu.content) else NdefBuilder.text(emu.content)
            NdefBuilder.message(record).toByteArray()
        } else {
            ByteArray(0)
        }
        val len = message.size
        return byteArrayOf(((len ushr 8) and 0xFF).toByte(), (len and 0xFF).toByte()) + message
    }

    private fun Byte.u(): Int = this.toInt() and 0xFF

    private fun ByteArray.matches(a: Int, b: Int, c: Int, d: Int): Boolean =
        size >= 4 && this[0].u() == a && this[1].u() == b && this[2].u() == c && this[3].u() == d

    private fun ByteArray.matches2(a: Int, b: Int): Boolean =
        size >= 2 && this[0].u() == a && this[1].u() == b

    companion object {
        private const val FILE_NONE = 0
        private const val FILE_CC = 1
        private const val FILE_NDEF = 2

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_WRONG_OFFSET = byteArrayOf(0x6B, 0x00)
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        private val SW_FAIL = byteArrayOf(0x6F, 0x00)
    }
}
