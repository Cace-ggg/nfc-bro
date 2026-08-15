package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.zorn.nfcbro.nfc.Hex
import it.zorn.nfcbro.nfc.OpOutcome
import it.zorn.nfcbro.ui.NfcViewModel
import it.zorn.nfcbro.ui.Pending
import it.zorn.nfcbro.ui.components.SectionCard

@Composable
fun ToolsScreen(vm: NfcViewModel) {
    var confirmLock by remember { mutableStateOf(false) }
    var rawInput by remember { mutableStateOf("") }
    val rawBytes = remember(rawInput) { runCatching { Hex.decode(rawInput) }.getOrNull() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Tag operations") {
            ToolButton("Read memory dump", Icons.Outlined.Memory) { vm.start(Pending.Dump) }
            ToolButton("Erase tag", Icons.Outlined.DeleteOutline) { vm.start(Pending.Erase) }
            ToolButton("Format to NDEF", Icons.Outlined.Refresh) { vm.start(Pending.Format) }
            ToolButton("Lock — make read-only", Icons.Outlined.Lock) { confirmLock = true }
        }

        SectionCard(title = "Advanced · raw command") {
            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                label = { Text("Hex command (APDU)") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { rawBytes?.let { vm.start(Pending.Raw(it)) } },
                enabled = rawBytes != null && rawBytes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, null)
                Text("  Send command")
            }
            Text(
                "Sent over IsoDep when available, otherwise NfcA / NfcV. " +
                    "Example: 00 A4 04 00 07 D2 76 00 00 85 01 01 00 (select NDEF app).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text("Lock this tag?") },
            text = { Text("Making a tag read-only is permanent and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLock = false
                    vm.start(Pending.Lock)
                }) { Text("Lock") }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null)
        Text("  $label")
    }
}

@Composable
fun ResultDialog(outcome: OpOutcome, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val body: String
    val title: String
    when (outcome) {
        is OpOutcome.Dumped -> {
            title = outcome.dump?.label ?: "Memory dump"
            body = outcome.dump?.lines?.joinToString("\n") ?: outcome.message
        }
        is OpOutcome.Raw -> {
            title = "Raw command"
            body = "→ ${outcome.request}\n← ${outcome.response.ifBlank { "(no response)" }}\n\n${outcome.message}"
        }
        else -> {
            title = "Result"
            body = outcome.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(body)) }) { Text("Copy") }
        },
    )
}
