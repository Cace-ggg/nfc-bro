package it.zorn.nfcbro.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import it.zorn.nfcbro.BuildConfig
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.ui.components.KeyValue
import it.zorn.nfcbro.ui.components.SectionCard

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    FilterChip(
                        selected = settings.theme == key,
                        onClick = { Store.updateSettings { it.copy(theme = key) } },
                        label = { Text(label) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Dynamic colour (Material You)", settings.dynamicColor) { on ->
                    Store.updateSettings { it.copy(dynamicColor = on) }
                }
            }
        }

        SectionCard(title = "Scanning") {
            SwitchRow("Sound on scan", settings.soundOnScan) { on ->
                Store.updateSettings { it.copy(soundOnScan = on) }
            }
            SwitchRow("Vibrate on scan", settings.vibrateOnScan) { on ->
                Store.updateSettings { it.copy(vibrateOnScan = on) }
            }
            SwitchRow("Save scans to history", settings.addToHistory) { on ->
                Store.updateSettings { it.copy(addToHistory = on) }
            }
        }

        SectionCard(title = "About") {
            KeyValue("Version", BuildConfig.VERSION_NAME)
            Text(
                "NFC Bro — a fast, low-power NFC toolkit for reading, writing and emulating tags.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val link = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append("github.com/MarcoZorn/nfc-bro")
                }
            }
            Text(
                link,
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/MarcoZorn/nfc-bro") },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
