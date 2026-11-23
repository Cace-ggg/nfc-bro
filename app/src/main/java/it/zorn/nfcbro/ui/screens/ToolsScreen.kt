package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.zorn.nfcbro.nfc.CommandBatch
import it.zorn.nfcbro.nfc.OpOutcome
import it.zorn.nfcbro.ui.NfcViewModel
import it.zorn.nfcbro.ui.Pending
import it.zorn.nfcbro.ui.components.SectionCard

@Composable
fun ToolsScreen(vm: NfcViewModel) {
    var confirmLock by remember { mutableStateOf(false) }
    DisposableEffect(vm) {
        vm.setRawVisible(true)
        onDispose { vm.setRawVisible(false) }
    }

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

        ApduConsole(vm)
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
private fun ApduConsole(vm: NfcViewModel) {
    val state by vm.rawState.collectAsState()
    val log by vm.rawLog.collectAsState()
    val connection = state.connection
    var input by rememberSaveable { mutableStateOf("") }
    var stopOnError by rememberSaveable { mutableStateOf(true) }
    var allowWrites by remember(connection?.id) { mutableStateOf(false) }
    var confirmation by remember(connection?.id) { mutableStateOf<CommandBatch?>(null) }
    val parsed = remember(input) { runCatching { CommandBatch.parse(input) } }
    val batch = parsed.getOrNull()
    val requiresConfirmation = connection != null && batch?.requiresConfirmation(connection.isApdu) == true
    val inputError = when {
        input.isBlank() -> null
        batch == null -> parsed.exceptionOrNull()?.message
        connection?.isApdu == true && batch.hasShortApdu -> "Each APDU needs at least four header bytes."
        else -> null
    }
    val transcript = remember(log) { log.joinToString("\n\n") { it.transcript() } }
    val clipboard = LocalClipboardManager.current

    SectionCard(title = "Advanced · APDU console") {
        Text(if (connection == null) state.message else "Connected via ${connection.transport} · ${connection.uid}")
        if (connection != null) {
            OutlinedButton(onClick = vm::disconnectRaw) { Text("Disconnect") }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Hex commands (one per line)") },
            supportingText = { Text(inputError ?: "Example: 00 A4 04 00 07 D2 76 00 00 85 01 01 00") },
            isError = inputError != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stopOnError, onCheckedChange = { stopOnError = it }, enabled = !state.busy)
            Text("Stop batch on card error")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowWrites,
                onCheckedChange = { allowWrites = it },
                enabled = connection != null && !state.busy,
            )
            Text("Allow writes and unrecognized commands")
        }
        if (requiresConfirmation && !allowWrites) {
            Text("Read-only mode: enable writes to send this input.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                if (batch != null && connection != null) {
                    if (requiresConfirmation) confirmation = batch
                    else vm.sendRaw(batch, connection.id, stopOnError)
                }
            },
            enabled = connection != null && batch != null && inputError == null &&
                !state.busy && (!requiresConfirmation || allowWrites),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, null)
            Text(when {
                state.busy -> "  Sending…"
                (batch?.size ?: 0) > 1 -> "  Send batch"
                else -> "  Send command"
            })
        }
    }

    if (log.isNotEmpty()) {
        SectionCard(title = "Communication log") {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(transcript)) }) {
                Text("Copy full log")
            }
            Text(transcript, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }

    val toConfirm = confirmation
    if (toConfirm != null && connection != null) {
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Send commands that may modify the tag?") },
            text = { Text("This will send ${toConfirm.size} command(s) to the connected tag. Changes may be permanent.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (allowWrites) vm.sendRaw(toConfirm, connection.id, stopOnError, confirmed = true)
                }) { Text("Confirm and send") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
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
fun ResultDialog(outcome: OpOutcome.Dumped, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val title = outcome.dump?.label ?: "Memory dump"
    val body = outcome.dump?.lines?.joinToString("\n") ?: outcome.message

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
