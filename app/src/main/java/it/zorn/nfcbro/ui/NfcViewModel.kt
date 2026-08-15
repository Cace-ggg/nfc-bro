package it.zorn.nfcbro.ui

import android.app.Application
import android.nfc.NdefMessage
import android.nfc.Tag
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.zorn.nfcbro.data.HistoryEntry
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.nfc.MemoryDumper
import it.zorn.nfcbro.nfc.OpOutcome
import it.zorn.nfcbro.nfc.RawCommander
import it.zorn.nfcbro.nfc.TagInfo
import it.zorn.nfcbro.nfc.TagReader
import it.zorn.nfcbro.nfc.TagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Pending {
    val prompt: String

    data class Write(val message: NdefMessage) : Pending {
        override val prompt = "Hold a writable tag to the phone"
    }
    data object Lock : Pending { override val prompt = "Hold a tag to lock it (permanent)" }
    data object Erase : Pending { override val prompt = "Hold a tag to erase it" }
    data object Format : Pending { override val prompt = "Hold a tag to format it" }
    data object Dump : Pending { override val prompt = "Hold a tag to read its memory" }
    data class Raw(val command: ByteArray) : Pending {
        override val prompt = "Hold a tag to send the command"
    }
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

    fun setNfcState(state: NfcState) { _nfcState.value = state }

    /** Debug-only: populate a representative tag for documentation screenshots. */
    fun showSampleTag() {
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
                it.zorn.nfcbro.nfc.NdefRecordInfo("URI", "https://zorn.it", "URI record", "55 04 7A 6F 72 6E 2E 69 74"),
                it.zorn.nfcbro.nfc.NdefRecordInfo("Text", "Marco Zorn", "Language: en", "02 65 6E 4D 61 72 63 6F"),
            ),
            scannedAtMs = System.currentTimeMillis(),
        )
    }

    fun start(pending: Pending) {
        _lastResult.value = null
        _awaiting.value = pending
    }

    fun cancel() { _awaiting.value = null }

    fun consumeResult() { _lastResult.value = null }

    fun handleTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            feedback()
            when (val pending = _awaiting.value) {
                null -> passiveRead(tag)
                is Pending.Write -> finish(TagWriter.write(tag, pending.message))
                Pending.Lock -> finish(TagWriter.makeReadOnly(tag))
                Pending.Erase -> finish(TagWriter.erase(tag))
                Pending.Format -> finish(TagWriter.format(tag))
                Pending.Dump -> finish(MemoryDumper.dump(tag))
                is Pending.Raw -> finish(RawCommander.send(tag, pending.command))
            }
        }
    }

    private fun passiveRead(tag: Tag) {
        val info = TagReader.read(tag)
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
