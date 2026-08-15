package it.zorn.nfcbro.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val theme: String = "system",      // system | light | dark
    val dynamicColor: Boolean = false,
    val soundOnScan: Boolean = true,
    val vibrateOnScan: Boolean = true,
    val addToHistory: Boolean = true,
)

@Serializable
data class Emulation(
    val enabled: Boolean = false,
    val kind: String = "text",         // text | url
    val content: String = "",
)

@Serializable
data class HistoryEntry(
    val id: String,
    val timestampMs: Long,
    val uid: String,
    val techList: List<String>,
    val typeLabel: String,
    val sizeBytes: Int,
    val writable: Boolean,
    val records: List<String>,
)

/**
 * Tiny persistence layer backed by SharedPreferences + JSON. Shared by the UI and
 * the HCE service (same process), so no database or DI framework is needed.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _emulation = MutableStateFlow(Emulation())
    val emulation: StateFlow<Emulation> = _emulation.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("nfcbro", Context.MODE_PRIVATE)
        _settings.value = load("settings") ?: Settings()
        _emulation.value = load("emulation") ?: Emulation()
        _history.value = load<List<HistoryEntry>>("history") ?: emptyList()
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> persist(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    fun updateSettings(block: (Settings) -> Settings) {
        _settings.value = block(_settings.value)
        persist("settings", _settings.value)
    }

    fun updateEmulation(block: (Emulation) -> Emulation) {
        _emulation.value = block(_emulation.value)
        persist("emulation", _emulation.value)
    }

    fun addHistory(entry: HistoryEntry) {
        _history.value = (listOf(entry) + _history.value).take(300)
        persist("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist("history", _history.value)
    }

    /** Snapshot read for the HCE service, which lives outside the Compose world. */
    fun currentEmulation(): Emulation = _emulation.value
}
