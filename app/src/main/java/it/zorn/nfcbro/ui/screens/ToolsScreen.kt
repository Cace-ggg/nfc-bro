package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.zorn.nfcbro.R
import it.zorn.nfcbro.nfc.CommandBatch
import it.zorn.nfcbro.nfc.BatchInputException
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
        SectionCard(title = stringResource(R.string.tag_operations)) {
            ToolButton(stringResource(R.string.read_memory_dump), Icons.Outlined.Memory) { vm.start(Pending.Dump) }
            ToolButton(stringResource(R.string.erase_tag), Icons.Outlined.DeleteOutline) { vm.start(Pending.Erase) }
            ToolButton(stringResource(R.string.format_ndef), Icons.Outlined.Refresh) { vm.start(Pending.Format) }
            ToolButton(stringResource(R.string.lock_read_only), Icons.Outlined.Lock) { confirmLock = true }
        }

        ApduConsole(vm)
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text(stringResource(R.string.lock_prompt)) },
            text = { Text(stringResource(R.string.lock_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLock = false
                    vm.start(Pending.Lock)
                }) { Text(stringResource(R.string.lock)) }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        batch == null -> (parsed.exceptionOrNull() as? BatchInputException)?.let {
            val message = stringResource(it.resource)
            if (it.line == null) message else stringResource(R.string.batch_line_error, it.line, message)
        }
        connection?.isApdu == true && batch.hasShortApdu -> stringResource(R.string.apdu_short)
        else -> null
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val transcript = remember(log, context, configuration) {
        log.joinToString("\n\n") { result -> result.transcript { id, args -> context.getString(id, *args) } }
    }
    val clipboard = LocalClipboardManager.current

    SectionCard(title = stringResource(R.string.apdu_console)) {
        Text(if (connection == null) stringResource(state.message) else stringResource(R.string.nfc_connected, connection.transport, connection.uid))
        if (connection != null) {
            OutlinedButton(onClick = vm::disconnectRaw) { Text(stringResource(R.string.disconnect)) }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.apdu_input)) },
            supportingText = { Text(inputError ?: stringResource(R.string.apdu_example)) },
            isError = inputError != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stopOnError, onCheckedChange = { stopOnError = it }, enabled = !state.busy)
            Text(stringResource(R.string.batch_stop))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowWrites,
                onCheckedChange = { allowWrites = it },
                enabled = connection != null && !state.busy,
            )
            Text(stringResource(R.string.allow_writes))
        }
        if (requiresConfirmation && !allowWrites) {
            Text(stringResource(R.string.read_only_mode), style = MaterialTheme.typography.bodySmall)
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
                state.busy -> stringResource(R.string.sending)
                (batch?.size ?: 0) > 1 -> stringResource(R.string.send_batch)
                else -> stringResource(R.string.send_command)
            })
        }
    }

    SectionCard(title = stringResource(R.string.communication_log)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(transcript)) },
                enabled = log.isNotEmpty(),
            ) {
                Text(stringResource(R.string.copy_full_log))
            }
            OutlinedButton(onClick = vm::clearRawLog, enabled = log.isNotEmpty()) {
                Text(stringResource(R.string.clear_log))
            }
        }
        if (log.isNotEmpty()) {
            Text(transcript, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }

    val toConfirm = confirmation
    if (toConfirm != null && connection != null) {
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(R.string.confirm_write_title)) },
            text = { Text(stringResource(R.string.confirm_write_body, toConfirm.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (allowWrites) vm.sendRaw(toConfirm, connection.id, stopOnError, confirmed = true)
                }) { Text(stringResource(R.string.confirm_send)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.cancel)) } },
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
    val title = outcome.dump?.label ?: stringResource(R.string.memory_dump)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(body)) }) { Text(stringResource(R.string.copy)) }
        },
    )
}
