package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import it.zorn.nfcbro.nfc.TagInfo
import it.zorn.nfcbro.ui.NfcViewModel
import it.zorn.nfcbro.ui.components.KeyValue
import it.zorn.nfcbro.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadScreen(vm: NfcViewModel) {
    val scan by vm.lastScan.collectAsState()
    val clipboard = LocalClipboardManager.current

    val tag = scan
    if (tag == null) {
        EmptyState(icon = { Icon(Icons.Outlined.Nfc, null, Modifier.size(72.dp)) }, text = "Hold an NFC tag to the back of your phone")
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Tag") {
            KeyValue("UID", tag.uid, mono = true)
            KeyValue("Type", tag.typeLabel)
            KeyValue("Manufacturer", tag.manufacturer)
            tag.atqa?.let { KeyValue("ATQA", it, mono = true) }
            tag.sak?.let { KeyValue("SAK", it, mono = true) }
            tag.ats?.let { KeyValue("ATS", it, mono = true) }
            if (tag.maxSizeBytes != null) {
                KeyValue("Capacity", "${tag.usedBytes ?: 0} / ${tag.maxSizeBytes} bytes")
            }
            tag.writable?.let { KeyValue("Writable", if (it) "Yes" else "No (read-only)") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tag.techList.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(tag.uid.replace(" ", "")))
            }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Text("  Copy UID")
            }
        }

        if (tag.records.isNotEmpty()) {
            SectionCard(title = "NDEF · ${tag.records.size} record${if (tag.records.size == 1) "" else "s"}") {
                tag.records.forEachIndexed { i, r ->
                    Text("${i + 1}. ${r.kind}", style = MaterialTheme.typography.titleSmall)
                    Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                    if (r.details.isNotBlank()) {
                        Text(r.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.rawHex.isNotBlank()) {
                        Text(
                            r.rawHex,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            SectionCard(title = "NDEF") { Text("No NDEF data on this tag") }
        }
    }
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
