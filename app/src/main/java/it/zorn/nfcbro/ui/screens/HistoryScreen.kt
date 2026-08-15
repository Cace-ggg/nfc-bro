package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.zorn.nfcbro.data.HistoryEntry
import it.zorn.nfcbro.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val history by Store.history.collectAsState()

    if (history.isEmpty()) {
        EmptyState(icon = { Icon(Icons.Outlined.History, null, Modifier.size(72.dp)) }, text = "No scans yet")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = { Store.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear history")
            }
        }
        items(history) { entry -> HistoryCard(entry) }
    }
}

private val timeFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.uid, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace))
            Text(
                "${entry.typeLabel} · ${timeFormat.format(Date(entry.timestampMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.records.isNotEmpty()) {
                entry.records.take(3).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
