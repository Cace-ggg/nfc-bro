package it.zorn.nfcbro.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class RawTranscript {
    private val results = MutableStateFlow<List<CommandResult>>(emptyList())
    val entries = results.asStateFlow()

    fun append(result: CommandResult) {
        results.update { it + result }
    }

    fun clear() {
        results.value = emptyList()
    }
}
