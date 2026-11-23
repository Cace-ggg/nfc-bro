package it.zorn.nfcbro.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import it.zorn.nfcbro.R
import java.io.IOException

/** Write / lock / erase / format operations. Each opens its own connection. */
object TagWriter {

    fun write(context: Context, tag: Tag, message: NdefMessage): OpOutcome {
        val size = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> OpOutcome.Written(false, context.getString(R.string.tag_read_only))
                    ndef.maxSize < size ->
                        OpOutcome.Written(false, context.getString(R.string.tag_message_large, size, ndef.maxSize))
                    else -> {
                        ndef.writeNdefMessage(message)
                        OpOutcome.Written(true, context.getString(R.string.tag_wrote, size))
                    }
                }
            } catch (e: Exception) {
                OpOutcome.Written(false, e.friendly(context))
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return OpOutcome.Written(false, context.getString(R.string.tag_not_ndef))
        return try {
            formatable.connect()
            formatable.format(message)
            OpOutcome.Written(true, context.getString(R.string.tag_formatted_wrote, size))
        } catch (e: Exception) {
            OpOutcome.Written(false, e.friendly(context))
        } finally {
            runCatching { formatable.close() }
        }
    }

    fun makeReadOnly(context: Context, tag: Tag): OpOutcome {
        val ndef = Ndef.get(tag) ?: return OpOutcome.Locked(false, context.getString(R.string.tag_not_ndef))
        return try {
            ndef.connect()
            if (!ndef.canMakeReadOnly()) {
                OpOutcome.Locked(false, context.getString(R.string.tag_cannot_lock))
            } else if (ndef.makeReadOnly()) {
                OpOutcome.Locked(true, context.getString(R.string.tag_locked))
            } else {
                OpOutcome.Locked(false, context.getString(R.string.tag_lock_failed))
            }
        } catch (e: Exception) {
            OpOutcome.Locked(false, e.friendly(context))
        } finally {
            runCatching { ndef.close() }
        }
    }

    fun erase(context: Context, tag: Tag): OpOutcome {
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))))
        return when (val r = write(context, tag, empty)) {
            is OpOutcome.Written ->
                if (r.ok) OpOutcome.Erased(true, context.getString(R.string.tag_erased)) else OpOutcome.Erased(false, r.message)
            else -> OpOutcome.Erased(false, context.getString(R.string.tag_erase_failed))
        }
    }

    fun format(context: Context, tag: Tag): OpOutcome {
        val empty = NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))))
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(empty)
                OpOutcome.Formatted(true, context.getString(R.string.tag_formatted))
            } catch (e: Exception) {
                OpOutcome.Formatted(false, e.friendly(context))
            } finally {
                runCatching { formatable.close() }
            }
        }
        // Already NDEF formatted: fall back to erasing it.
        return when (val r = erase(context, tag)) {
            is OpOutcome.Erased ->
                if (r.ok) OpOutcome.Formatted(true, context.getString(R.string.tag_cleared))
                else OpOutcome.Formatted(false, r.message)
            else -> OpOutcome.Formatted(false, context.getString(R.string.tag_format_failed))
        }
    }

    private fun Exception.friendly(context: Context): String = when (this) {
        is IOException -> context.getString(R.string.tag_lost_contact)
        else -> context.getString(R.string.tag_operation_failed)
    }
}
