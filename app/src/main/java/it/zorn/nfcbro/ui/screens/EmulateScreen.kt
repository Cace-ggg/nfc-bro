package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.zorn.nfcbro.R
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.ui.components.SectionCard

@Composable
fun EmulateScreen() {
    val emu by Store.emulation.collectAsState()
    var kind by remember { mutableStateOf(emu.kind) }
    var content by remember { mutableStateOf(emu.content) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.tag_emulation)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.broadcast_ndef), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = emu.enabled,
                    onCheckedChange = { on -> Store.updateEmulation { it.copy(enabled = on) } },
                )
            }
            Text(
                stringResource(R.string.emulation_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.payload)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == "text", onClick = { kind = "text" }, label = { Text(stringResource(R.string.text)) })
                FilterChip(selected = kind == "url", onClick = { kind = "url" }, label = { Text(stringResource(R.string.link)) })
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(if (kind == "url") stringResource(R.string.url) else stringResource(R.string.text)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { Store.updateEmulation { it.copy(kind = kind, content = content) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Save, null)
                Text(stringResource(R.string.save_payload), modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.padding(0.dp))
        }

        SectionCard(title = stringResource(R.string.good_to_know)) {
            Text(
                stringResource(R.string.emulation_limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
