package it.zorn.nfcbro.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import it.zorn.nfcbro.nfc.OpOutcome
import it.zorn.nfcbro.ui.screens.EmulateScreen
import it.zorn.nfcbro.ui.screens.HistoryScreen
import it.zorn.nfcbro.ui.screens.ReadScreen
import it.zorn.nfcbro.ui.screens.ResultDialog
import it.zorn.nfcbro.ui.screens.SettingsScreen
import it.zorn.nfcbro.ui.screens.ToolsScreen
import it.zorn.nfcbro.ui.screens.WriteScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

private enum class Tab(val label: String, val icon: ImageVector) {
    READ("Read", Icons.Outlined.Nfc),
    WRITE("Write", Icons.Outlined.Edit),
    EMULATE("Emulate", Icons.Outlined.Contactless),
    TOOLS("Tools", Icons.Outlined.Build),
}

private enum class Overlay { HISTORY, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: NfcViewModel, startTab: String? = null, startOverlay: String? = null) {
    var tab by remember { mutableStateOf(startTab?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.READ) }
    var overlay by remember { mutableStateOf(startOverlay?.let { runCatching { Overlay.valueOf(it) }.getOrNull() }) }

    val awaiting by vm.awaiting.collectAsState()
    val result by vm.lastResult.collectAsState()
    val nfcState by vm.nfcState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Simple outcomes flash a snackbar; data-bearing ones open a dialog (handled below).
    LaunchedEffect(result) {
        val r = result
        if (r != null && r !is OpOutcome.Dumped && r !is OpOutcome.Raw) {
            snackbar.showSnackbar(r.message)
            vm.consumeResult()
        }
    }

    val title = overlay?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: tab.label

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (overlay == null) "NFC Bro" else title) },
                navigationIcon = {
                    if (overlay != null) {
                        IconButton(onClick = { overlay = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { overlay = Overlay.HISTORY }) {
                        Icon(Icons.Outlined.History, contentDescription = "History")
                    }
                    IconButton(onClick = { overlay = Overlay.SETTINGS }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (overlay == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (nfcState == NfcState.DISABLED || nfcState == NfcState.MISSING) {
                NfcBanner(nfcState)
            }
            Box(Modifier.fillMaxSize()) {
                when (overlay) {
                    Overlay.HISTORY -> HistoryScreen(onBack = { overlay = null })
                    Overlay.SETTINGS -> SettingsScreen(onBack = { overlay = null })
                    null -> when (tab) {
                        Tab.READ -> ReadScreen(vm)
                        Tab.WRITE -> WriteScreen(vm)
                        Tab.EMULATE -> EmulateScreen()
                        Tab.TOOLS -> ToolsScreen(vm)
                    }
                }
            }
        }
    }

    awaiting?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancel() },
            title = { Text("Ready to scan") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(pending.prompt)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cancel() }) { Text("Cancel") }
            },
        )
    }

    val r = result
    if (r is OpOutcome.Dumped || r is OpOutcome.Raw) {
        ResultDialog(outcome = r, onDismiss = { vm.consumeResult() })
    }
}

@Composable
private fun NfcBanner(state: NfcState) {
    val context = LocalContext.current
    it.zorn.nfcbro.ui.components.SectionCard(
        modifier = Modifier.padding(12.dp),
    ) {
        Text(
            if (state == NfcState.MISSING) "This device has no NFC hardware."
            else "NFC is turned off.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        if (state == NfcState.DISABLED) {
            Row {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text("Open NFC settings") }
            }
        }
    }
}
