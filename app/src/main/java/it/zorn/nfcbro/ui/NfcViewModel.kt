package it.zorn.nfcbro.ui

import android.app.Application
import android.nfc.NdefMessage
import android.nfc.Tag
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import it.zorn.nfcbro.R
import androidx.lifecycle.viewModelScope
import it.zorn.nfcbro.data.HistoryEntry
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.nfc.CommandBatch
import it.zorn.nfcbro.nfc.Hex
import it.zorn.nfcbro.nfc.MemoryDumper
import it.zorn.nfcbro.nfc.OpOutcome
import it.zorn.nfcbro.nfc.RawCommander
import it.zorn.nfcbro.nfc.RawConnection
import it.zorn.nfcbro.nfc.RawTranscript
import it.zorn.nfcbro.nfc.TagInfo
import it.zorn.nfcbro.nfc.TagReader
import it.zorn.nfcbro.nfc.TagWriter
import it.zorn.nfcbro.nfc.rawTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface Pending {
    val prompt: Int

    data class Write(val message: NdefMessage) : Pending {
        override val prompt = R.string.scan_write_prompt
    }
    data object Lock : Pending { override val prompt = R.string.scan_lock_prompt }
    data object Erase : Pending { override val prompt = R.string.scan_erase_prompt }
    data object Format : Pending { override val prompt = R.string.scan_format_prompt }
    data object Dump : Pending { override val prompt = R.string.scan_dump_prompt }
}

class NfcViewModel(app: Application) : AndroidViewModel(app) {

    private val _lastScan = MutableStateFlow<TagInfo?>(null)
    val lastScan: StateFlow<TagInfo?> = _lastScan.asStateFlow()

    private val _awaiting = MutableStateFlow<Pending?>(null)
    val awaiting: StateFlow<Pending?> = _awaiting.asStateFlow()

    private val _lastResult = MutableStateFlow<OpOutcome?>(null)
    val lastResult: StateFlow<OpOutcome?> = _lastResult.asStateFlow()

    private val _nfcState = MutableStateFlow(NfcState.UNKNOWN)
    val nfcState: StateFlow<NfcState> = _nfcState.asStateFlow()

    private val _rawState = MutableStateFlow(RawState())
    val rawState = _rawState.asStateFlow()

    private val transcript = RawTranscript()
    val rawLog = transcript.entries

    fun clearRawLog() = transcript.clear()

    private val nfcMutex = Mutex()
    private val raw = RawCommander()
    private var rawVisible = false
    private var foreground = false
    private var rawRevision = 0L
    private var rawTask: Job? = null
    private var presenceTask: Job? = null
    private var lastRawTag: Tag? = null
    private var ignoredRawTag: Tag? = null

    fun setNfcState(state: NfcState) { _nfcState.value = state }

    /** Debug-only: populate a representative tag for documentation screenshots. */
    fun showSampleTag() {
        val context = localizedContext()
        _lastScan.value = TagInfo(
            uid = "04 A2 26 B1 5C 3D 80",
            techList = listOf("NfcA", "MifareUltralight", "Ndef"),
            typeLabel = "NTAG215 · MIFARE Ultralight",
            manufacturer = "NXP Semiconductors",
            atqa = "00 44",
            sak = "00",
            ats = null,
            maxSizeBytes = 504,
            usedBytes = 51,
            writable = true,
            canMakeReadOnly = true,
            isNdef = true,
            records = listOf(
                it.zorn.nfcbro.nfc.NdefRecordInfo("URI", "https://zorn.it", context.getString(R.string.ndef_uri_record), "55 04 7A 6F 72 6E 2E 69 74"),
                it.zorn.nfcbro.nfc.NdefRecordInfo(context.getString(R.string.text), "Marco Zorn", context.getString(R.string.ndef_language, "en"), "02 65 6E 4D 61 72 63 6F"),
            ),
            scannedAtMs = System.currentTimeMillis(),
        )
    }

    fun start(pending: Pending) {
        if (rawVisible) closeRaw()
        _lastResult.value = null
        _awaiting.value = pending
    }

    fun cancel() { _awaiting.value = null }

    fun consumeResult() { _lastResult.value = null }

    fun setForeground(value: Boolean) {
        foreground = value
        updatePresenceTask()
    }

    fun setRawVisible(value: Boolean) {
        rawVisible = value
        updatePresenceTask()
    }

    private fun updatePresenceTask() {
        if (!foreground || !rawVisible) {
            presenceTask?.cancel()
            presenceTask = null
            closeRaw()
        } else if (presenceTask == null) {
            presenceTask = viewModelScope.launch {
                while (true) {
                    delay(300)
                    if (_rawState.value.connection == null || _rawState.value.busy) continue
                    nfcMutex.withLock {
                        if (_rawState.value.connection == null || _rawState.value.busy) return@withLock
                        if (!withContext(Dispatchers.IO) { raw.checkPresence() }) {
                            lastRawTag = null
                            _rawState.value = RawState(message = R.string.nfc_removed)
                        }
                    }
                }
            }
        }
    }

    fun disconnectRaw() {
        ignoredRawTag = lastRawTag
        closeRaw(R.string.nfc_disconnected)
    }

    private fun closeRaw(message: Int = R.string.nfc_waiting) {
        rawRevision++
        rawTask?.cancel()
        lastRawTag = null
        _rawState.value = RawState(message = message)
        viewModelScope.launch {
            nfcMutex.withLock { withContext(Dispatchers.IO) { raw.close() } }
        }
    }

    fun sendRaw(batch: CommandBatch, connectionId: Long, stopOnError: Boolean, confirmed: Boolean = false) {
        val state = _rawState.value
        val connection = state.connection ?: return
        if (!foreground || !rawVisible || state.busy || connection.id != connectionId) return
        if (connection.isApdu && batch.hasShortApdu) return
        if (batch.requiresConfirmation(connection.isApdu) && !confirmed) return
        rawTask = viewModelScope.launch {
            _rawState.value = state.copy(busy = true)
            nfcMutex.withLock {
                if (_rawState.value.connection?.id != connectionId) return@withLock
                val error = withContext(Dispatchers.IO) {
                    if (raw.connection?.id != connectionId) return@withContext R.string.nfc_not_connected
                    raw.execute(batch, stopOnError, transcript::append)
                }
                _rawState.value = if (error == null) state else RawState(message = error)
                if (error != null) lastRawTag = null
            }
        }
    }

    fun handleTag(tag: Tag, fromReader: Boolean = false) {
        viewModelScope.launch {
            if (fromReader && !foreground) return@launch
            nfcMutex.withLock {
                if (fromReader && !foreground) return@withLock
                val pending = _awaiting.value
                if (pending == null && foreground && rawVisible) {
                    if (tag !== ignoredRawTag) {
                        ignoredRawTag = null
                        connectRaw(tag)
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        feedback()
                        val context = localizedContext()
                        when (pending) {
                            null -> passiveRead(tag)
                            is Pending.Write -> finish(TagWriter.write(context, tag, pending.message))
                            Pending.Lock -> finish(TagWriter.makeReadOnly(context, tag))
                            Pending.Erase -> finish(TagWriter.erase(context, tag))
                            Pending.Format -> finish(TagWriter.format(context, tag))
                            Pending.Dump -> finish(MemoryDumper.dump(context, tag))
                        }
                    }
                }
            }
        }
    }

    private suspend fun connectRaw(tag: Tag) {
        val revision = rawRevision
        val (connection, error) = withContext(Dispatchers.IO) {
            val previous = raw.connection
            val error = raw.connect(Hex.pretty(tag.id ?: byteArrayOf())) { rawTransport(tag) }
            if (error != null || raw.connection?.id != previous?.id) {
                feedback()
                passiveRead(tag)
            }
            raw.connection to error
        }
        if (revision != rawRevision) return
        lastRawTag = if (connection != null) tag else null
        if (_rawState.value.connection?.id != connection?.id || error != null) {
            _rawState.value = RawState(connection, error ?: R.string.nfc_waiting)
        }
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { nfcMutex.withLock { raw.close() } }
        super.onCleared()
    }

    private fun localizedContext(): Context = ContextCompat.getContextForLanguage(getApplication<Application>())

    private fun passiveRead(tag: Tag) {
        val info = TagReader.read(localizedContext(), tag)
        _lastScan.value = info
        if (Store.settings.value.addToHistory) Store.addHistory(info.toHistoryEntry())
    }

    private fun finish(outcome: OpOutcome) {
        _lastResult.value = outcome
        _awaiting.value = null
    }

    private fun feedback() {
        if (!Store.settings.value.vibrateOnScan) return
        val ctx = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        }
    }
}

enum class NfcState { UNKNOWN, MISSING, DISABLED, READY }

data class RawState(
    val connection: RawConnection? = null,
    val message: Int = R.string.nfc_waiting,
    val busy: Boolean = false,
)

private fun TagInfo.toHistoryEntry() = HistoryEntry(
    id = "$uid-$scannedAtMs",
    timestampMs = scannedAtMs,
    uid = uid,
    techList = techList,
    typeLabel = typeLabel,
    sizeBytes = maxSizeBytes ?: 0,
    writable = writable ?: false,
    records = records.map { "${it.kind}: ${it.summary}" },
)
