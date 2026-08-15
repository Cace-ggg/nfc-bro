package it.zorn.nfcbro.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/** Write / lock / erase / format operations. Each opens its own connection. */
object TagWriter {

    fun write(tag: Tag, message: NdefMessage): OpOutcome {
        val size = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> OpOutcome.Written(false, "Tag is read-only")
                    ndef.maxSize < size ->
                        OpOutcome.Written(false, "Message too large: $size B, tag holds ${ndef.maxSize} B")
                    else -> {
                        ndef.writeNdefMessage(message)
                        OpOutcome.Written(true, "Wrote $size bytes to the tag")
                    }
                }
            } catch (e: Exception) {
                OpOutcome.Written(false, e.friendly())
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return OpOutcome.Written(false, "Tag is not NDEF compatible")
        return try {
            formatable.connect()
            formatable.format(message)
            OpOutcome.Written(true, "Formatted and wrote $size bytes")
        } catch (e: Exception) {
            OpOutcome.Written(false, e.friendly())
        } finally {
            runCatching { formatable.close() }
        }
    }

    fun makeReadOnly(tag: Tag): OpOutcome {
        val ndef = Ndef.get(tag) ?: return OpOutcome.Locked(false, "Tag is not NDEF compatible")
        return try {
            ndef.connect()
            if (!ndef.canMakeReadOnly()) {
                OpOutcome.Locked(false, "This tag cannot be locked")
            } else if (ndef.makeReadOnly()) {
                OpOutcome.Locked(true, "Tag is now permanently read-only")
            } else {
                OpOutcome.Locked(false, "Lock failed")
            }
        } catch (e: Exception) {
            OpOutcome.Locked(false, e.friendly())
        } finally {
            runCatching { ndef.close() }
        }
    }

    fun erase(tag: Tag): OpOutcome {
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))))
        return when (val r = write(tag, empty)) {
            is OpOutcome.Written ->
                if (r.ok) OpOutcome.Erased(true, "Tag erased") else OpOutcome.Erased(false, r.message)
            else -> OpOutcome.Erased(false, "Erase failed")
        }
    }

    fun format(tag: Tag): OpOutcome {
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))))
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(empty)
                OpOutcome.Formatted(true, "Tag formatted to NDEF")
            } catch (e: Exception) {
                OpOutcome.Formatted(false, e.friendly())
            } finally {
                runCatching { formatable.close() }
            }
        }
        // Already NDEF formatted: fall back to erasing it.
        return when (val r = erase(tag)) {
            is OpOutcome.Erased ->
                if (r.ok) OpOutcome.Formatted(true, "Tag already formatted; content cleared")
                else OpOutcome.Formatted(false, r.message)
            else -> OpOutcome.Formatted(false, "Format failed")
        }
    }

    private fun Exception.friendly(): String = when (this) {
        is IOException -> "Lost contact with the tag — hold it steady and try again"
        else -> message ?: this::class.java.simpleName
    }
}
