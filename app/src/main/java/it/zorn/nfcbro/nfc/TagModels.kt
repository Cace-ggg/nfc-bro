package it.zorn.nfcbro.nfc

/** One decoded NDEF record, ready for display. */
data class NdefRecordInfo(
    val kind: String,        // Text, URI, MIME, App, Smart Poster, ...
    val summary: String,     // decoded human-readable value
    val details: String,     // extra info (mime type, language, tnf)
    val rawHex: String,
)

/** Everything we surface about a scanned tag. */
data class TagInfo(
    val uid: String,
    val techList: List<String>,
    val typeLabel: String,
    val manufacturer: String,
    val atqa: String?,
    val sak: String?,
    val ats: String?,
    val maxSizeBytes: Int?,
    val usedBytes: Int?,
    val writable: Boolean?,
    val canMakeReadOnly: Boolean?,
    val isNdef: Boolean,
    val records: List<NdefRecordInfo>,
    val scannedAtMs: Long,
)

/** Result of a memory read on a supported tag. */
data class MemoryDump(
    val label: String,
    val lines: List<String>,   // "00: AABBCCDD  ...."
    val totalBytes: Int,
)

sealed interface OpOutcome {
    val ok: Boolean
    val message: String

    data class Written(override val ok: Boolean, override val message: String) : OpOutcome
    data class Locked(override val ok: Boolean, override val message: String) : OpOutcome
    data class Erased(override val ok: Boolean, override val message: String) : OpOutcome
    data class Formatted(override val ok: Boolean, override val message: String) : OpOutcome
    data class Dumped(val dump: MemoryDump?, override val ok: Boolean, override val message: String) : OpOutcome
    data class Failed(override val message: String) : OpOutcome { override val ok = false }
}
