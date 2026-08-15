package it.zorn.nfcbro.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcV

/** Reads out the raw memory of tags whose layout allows it. */
object MemoryDumper {

    fun dump(tag: Tag): OpOutcome.Dumped {
        val techs = tag.techList
        return when {
            MifareUltralight::class.java.name in techs -> dumpUltralight(tag)
            MifareClassic::class.java.name in techs -> dumpClassic(tag)
            NfcV::class.java.name in techs -> dumpNfcV(tag)
            else -> OpOutcome.Dumped(null, false, "Memory dump isn't supported for this tag type")
        }
    }

    private fun dumpUltralight(tag: Tag): OpOutcome.Dumped {
        val mfu = MifareUltralight.get(tag)
        val lines = mutableListOf<String>()
        var total = 0
        return try {
            mfu.connect()
            var page = 0
            loop@ while (page < 256) {
                val chunk = try {
                    mfu.readPages(page)
                } catch (_: Exception) {
                    break@loop
                }
                var i = 0
                while (i + 4 <= chunk.size) {
                    val slice = chunk.copyOfRange(i, i + 4)
                    lines += "P%02d: %s  %s".format(page, Hex.pretty(slice), Hex.ascii(slice))
                    total += 4
                    page++
                    i += 4
                }
            }
            OpOutcome.Dumped(MemoryDump("MIFARE Ultralight", lines, total), true, "${lines.size} pages read")
        } catch (e: Exception) {
            OpOutcome.Dumped(null, false, e.message ?: "Read failed")
        } finally {
            runCatching { mfu.close() }
        }
    }

    private fun dumpNfcV(tag: Tag): OpOutcome.Dumped {
        val nfcv = NfcV.get(tag)
        val lines = mutableListOf<String>()
        var total = 0
        return try {
            nfcv.connect()
            val uid = tag.id
            var block = 0
            loop@ while (block < 256) {
                // flags(addressed) + READ_SINGLE_BLOCK + UID + block number
                val cmd = byteArrayOf(0x20, 0x20) + uid + block.toByte()
                val resp = try {
                    nfcv.transceive(cmd)
                } catch (_: Exception) {
                    break@loop
                }
                if (resp.isEmpty() || resp[0].toInt() != 0x00) break@loop  // non-zero = error flag
                val data = resp.copyOfRange(1, resp.size)
                lines += "B%02d: %s  %s".format(block, Hex.pretty(data), Hex.ascii(data))
                total += data.size
                block++
            }
            if (lines.isEmpty()) OpOutcome.Dumped(null, false, "No blocks could be read")
            else OpOutcome.Dumped(MemoryDump("ISO 15693 (NfcV)", lines, total), true, "${lines.size} blocks read")
        } catch (e: Exception) {
            OpOutcome.Dumped(null, false, e.message ?: "Read failed")
        } finally {
            runCatching { nfcv.close() }
        }
    }

    private val CLASSIC_KEYS = listOf(
        MifareClassic.KEY_DEFAULT,
        MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
        MifareClassic.KEY_NFC_FORUM,
        byteArrayOf(0, 0, 0, 0, 0, 0),
    )

    private fun dumpClassic(tag: Tag): OpOutcome.Dumped {
        val mfc = MifareClassic.get(tag)
        val lines = mutableListOf<String>()
        var total = 0
        return try {
            mfc.connect()
            for (sector in 0 until mfc.sectorCount) {
                val authed = CLASSIC_KEYS.any { key ->
                    runCatching { mfc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
                }
                lines += "-- Sector $sector ${if (authed) "" else "(auth failed)"}"
                if (!authed) continue
                val first = mfc.sectorToBlock(sector)
                for (b in 0 until mfc.getBlockCountInSector(sector)) {
                    val idx = first + b
                    val data = runCatching { mfc.readBlock(idx) }.getOrNull()
                    if (data != null) {
                        lines += "B%03d: %s  %s".format(idx, Hex.pretty(data), Hex.ascii(data))
                        total += data.size
                    } else {
                        lines += "B%03d: <unreadable>".format(idx)
                    }
                }
            }
            OpOutcome.Dumped(MemoryDump("MIFARE Classic", lines, total), true, "$total bytes read")
        } catch (e: Exception) {
            OpOutcome.Dumped(null, false, e.message ?: "Read failed")
        } finally {
            runCatching { mfc.close() }
        }
    }
}
