package it.zorn.nfcbro.nfc

import it.zorn.nfcbro.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBatchTest {
    @Test
    fun parsesSingleLineAndMultilineHex() {
        assertEquals(1, CommandBatch.parse("00B0000004").size)
        val batch = CommandBatch.parse("00 a4 00 00\r\n \t\r\n 00:B0-00\t0004")
        assertEquals(listOf("00A40000", "00B0000004"), batch.commands.map { Hex.encode(it) })
        assertFalse(batch.hasShortApdu)
        assertTrue(CommandBatch.parse("30 04").hasShortApdu)
    }

    @Test
    fun reportsInvalidLineBeforeAnyCommandsCanBeSent() {
        val invalid = assertThrows(BatchInputException::class.java) {
            CommandBatch.parse("00A40000\n\n00XY")
        }
        assertEquals(3, invalid.line)
        assertEquals(R.string.hex_character, invalid.resource)
        val odd = assertThrows(BatchInputException::class.java) { CommandBatch.parse("ABC") }
        assertEquals(1, odd.line)
        assertEquals(R.string.hex_length, odd.resource)
        assertThrows(IllegalArgumentException::class.java) { CommandBatch.parse(" \n\t") }
    }

    @Test
    fun requiresConfirmationForWritesUnrecognizedClassesAndRawFrames() {
        val reads = CommandBatch.parse("00A40000\n00B0000004")
        assertFalse(reads.requiresConfirmation(isApdu = true))
        assertTrue(reads.requiresConfirmation(isApdu = false))
        for (ins in listOf("D6", "DC", "E2", "D0", "DA", "E0", "E4", "FF")) {
            assertTrue(CommandBatch.parse("00A40000\n00${ins}0000").requiresConfirmation(isApdu = true))
        }
        assertTrue(CommandBatch.parse("80B00000").requiresConfirmation(isApdu = true))
    }

    @Test
    fun rendersHexPayloadAsciiAndStatus() {
        val result = CommandResult("00 B0 00 00 04", Hex.decode("464C00FF9000"), isApdu = true)
        assertEquals("46 4C 00 FF 90 00", result.responseHex)
        assertEquals("FL..", result.ascii)
        assertEquals("9000", result.statusHex)
        assertEquals(R.string.sw_success, result.statusResource)
        assertEquals("→ 00 B0 00 00 04\n← 46 4C 00 FF 90 00\nASCII: FL..\nSW: 9000 — Success",
            result.transcript { id, _ -> if (id == R.string.sw_success) "Success" else error("Unexpected resource") })
        assertTrue(result.ok)
        assertTrue(CommandResult("", Hex.decode("6104"), isApdu = true).ok)
        assertFalse(CommandResult("", Hex.decode("6A82"), isApdu = true).ok)
        val missingRecord = CommandResult("", Hex.decode("6A83"), isApdu = true)
        assertEquals("6A83", missingRecord.statusHex)
        assertEquals(R.string.sw_record_missing, missingRecord.statusResource)
        assertFalse(CommandResult("", Hex.decode("90"), isApdu = true).ok)
        assertEquals("AB", CommandResult("", Hex.decode("4142")).ascii)
    }

    @Test
    fun rendersLocalizedCounterWithoutChangingTechnicalValues() {
        val reply = CommandResult("00 B2 00 00 04", Hex.decode("63C2"), isApdu = true)
        val transcript = reply.transcript { id, args ->
            assertEquals(R.string.sw_counter, id)
            assertEquals(listOf(2), args.toList())
            "警告；计数为 ${args.single()}"
        }
        assertEquals("→ 00 B2 00 00 04\n← 63 C2\nSW: 63C2 — 警告；计数为 2", transcript)
    }
}
