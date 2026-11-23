package it.zorn.nfcbro.nfc

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawCommanderTest {
    @Test
    fun sendsSeparateCommandsAndBatchOnOneConnection() {
        val raw = RawCommander()
        val tag = FakeTransport { Hex.decode("9000") }
        assertNull(raw.connect("01") { tag })
        val connection = raw.connection
        assertNull(raw.connect("01") { error("Duplicate discovery must not reconnect") })
        val log = mutableListOf<CommandResult>()
        runBlocking {
            raw.execute(CommandBatch.parse("00A40000"), true, log::add)
            raw.execute(CommandBatch.parse("00B0000004\n00B2000004"), true, log::add)
        }
        assertEquals(listOf("00 A4 00 00", "00 B0 00 00 04", "00 B2 00 00 04"), log.map { it.request })
        assertEquals(connection, raw.connection)
        assertEquals(1, tag.connections)
        assertEquals(0, tag.closes)
        raw.close()
        assertEquals(1, tag.closes)
        assertNull(raw.connection)
    }

    @Test
    fun stopsOnCardErrorOnlyWhenSelected() = runBlocking {
        val raw = RawCommander()
        val batch = CommandBatch.parse("00A40000\n00B0000004")
        raw.connect("01") { FakeTransport { Hex.decode("6A82") } }
        val stopped = mutableListOf<CommandResult>()
        raw.execute(batch, true, stopped::add)
        assertEquals(listOf("6A82 — File or application not found"), stopped.map { it.statusText })
        val continued = mutableListOf<CommandResult>()
        raw.execute(batch, false, continued::add)
        assertEquals(2, continued.size)
        raw.close()
    }

    @Test
    fun lossDuringBatchStopsEvenWithoutStopOnCardErrorAndAllowsRetap() = runBlocking {
        val raw = RawCommander()
        val tag = FakeTransport { throw RawTagLostException() }
        raw.connect("01") { tag }
        val first = raw.connection
        val log = mutableListOf<CommandResult>()
        val error = raw.execute(CommandBatch.parse("00A40000\n00B0000004"), false, log::add)
        assertEquals("Tag removed. Tap again.", error)
        assertEquals(error, log.single().error)
        assertNull(raw.connection)
        assertEquals(1, tag.closes)
        assertNull(raw.connect("01") { FakeTransport { Hex.decode("9000") } })
        assertNotEquals(first, raw.connection)
    }

    @Test
    fun idlePresenceLossAndTransceiveTimeoutCloseSession() = runBlocking {
        val raw = RawCommander()
        val tag = FakeTransport { throw IOException("timed out") }
        raw.connect("01") { tag }
        tag.isConnected = false
        assertFalse(raw.checkPresence())
        assertNull(raw.connection)

        raw.connect("01") { tag }
        val log = mutableListOf<CommandResult>()
        val error = raw.execute(CommandBatch.parse("00A40000"), true, log::add)
        assertEquals("NFC I/O error or timeout. Tap again.", error)
        assertEquals(error, log.single().error)
        assertNull(raw.connection)
        assertEquals(2, tag.closes)
    }

    private class FakeTransport(private val respond: () -> ByteArray) : RawTransport {
        override val name = "Fake IsoDep"
        override val isApdu = true
        override var isConnected = false
        var connections = 0
        var closes = 0

        override fun connect() {
            connections++
            isConnected = true
        }

        override fun transceive(command: ByteArray): ByteArray = respond()

        override fun close() {
            closes++
            isConnected = false
        }
    }
}
