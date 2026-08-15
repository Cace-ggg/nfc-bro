package it.zorn.nfcbro.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology

/** Sends a raw command to the tag over the most capable transport available. */
object RawCommander {

    fun send(tag: Tag, command: ByteArray): OpOutcome.Raw {
        val request = Hex.pretty(command)
        val (tech, transceive) = pick(tag)
            ?: return OpOutcome.Raw(request, "", false, "No transceive-capable transport on this tag")
        return try {
            tech.connect()
            val resp = transceive(command)
            OpOutcome.Raw(request, Hex.pretty(resp), true, "${resp.size} bytes received")
        } catch (e: Exception) {
            OpOutcome.Raw(request, "", false, e.message ?: "Command failed")
        } finally {
            runCatching { tech.close() }
        }
    }

    private fun pick(tag: Tag): Pair<TagTechnology, (ByteArray) -> ByteArray>? {
        val techs = tag.techList
        IsoDep::class.java.name.let { if (it in techs) IsoDep.get(tag)?.let { t -> return t to t::transceive } }
        NfcA::class.java.name.let { if (it in techs) NfcA.get(tag)?.let { t -> return t to t::transceive } }
        NfcV::class.java.name.let { if (it in techs) NfcV.get(tag)?.let { t -> return t to t::transceive } }
        return null
    }
}
