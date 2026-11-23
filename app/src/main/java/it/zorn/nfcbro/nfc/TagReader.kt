package it.zorn.nfcbro.nfc

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.nfc.tech.Ndef

/**
 * Turns a freshly discovered [Tag] into a [TagInfo]. Reads only from the discovery
 * cache (no I/O / no connect), so it is fast and easy on the battery.
 */
object TagReader {

    fun read(context: Context, tag: Tag): TagInfo {
        val techs = tag.techList.map { TagTech.shortTech(it) }

        val nfcA = runCatching { NfcA.get(tag) }.getOrNull()
        val atqa = nfcA?.atqa?.let { Hex.pretty(it) }
        val sak = nfcA?.let { "%02X".format(it.sak.toInt() and 0xFF) }

        val isoDep = runCatching { IsoDep.get(tag) }.getOrNull()
        val ats = isoDep?.historicalBytes?.takeIf { it.isNotEmpty() }?.let { Hex.pretty(it) }
            ?: isoDep?.hiLayerResponse?.takeIf { it.isNotEmpty() }?.let { Hex.pretty(it) }

        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
        val cached = ndef?.cachedNdefMessage
        val records = NdefParser.parse(context, cached)

        return TagInfo(
            uid = Hex.pretty(tag.id),
            techList = techs,
            typeLabel = TagTech.typeLabel(context, tag),
            manufacturer = TagTech.manufacturer(context, tag.id),
            atqa = atqa,
            sak = sak,
            ats = ats,
            maxSizeBytes = ndef?.maxSize,
            usedBytes = cached?.toByteArray()?.size,
            writable = ndef?.isWritable,
            canMakeReadOnly = runCatching { ndef?.canMakeReadOnly() }.getOrNull(),
            isNdef = ndef != null,
            records = records,
            scannedAtMs = System.currentTimeMillis(),
        )
    }
}
