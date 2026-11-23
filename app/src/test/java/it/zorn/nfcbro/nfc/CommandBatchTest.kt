package it.zorn.nfcbro.nfc

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
        val invalid = assertThrows(IllegalArgumentException::class.java) {
            CommandBatch.parse("00A40000\n\n00XY")
        }
        assertEquals("Line 3: Invalid hex character", invalid.message)
        val odd = assertThrows(IllegalArgumentException::class.java) { CommandBatch.parse("ABC") }
        assertEquals("Line 1: Hex length must be even", odd.message)
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
        assertEquals("9000 — Success", result.statusText)
        assertEquals("→ 00 B0 00 00 04\n← 46 4C 00 FF 90 00\nASCII: FL..\nSW: 9000 — Success", result.transcript())
        assertTrue(result.ok)
        assertTrue(CommandResult("", Hex.decode("6104"), isApdu = true).ok)
        assertFalse(CommandResult("", Hex.decode("6A82"), isApdu = true).ok)
        assertEquals("6A83 — Record not found", CommandResult("", Hex.decode("6A83"), isApdu = true).statusText)
        assertFalse(CommandResult("", Hex.decode("90"), isApdu = true).ok)
        assertEquals("AB", CommandResult("", Hex.decode("4142")).ascii)
    }
}
