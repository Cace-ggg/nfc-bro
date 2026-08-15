package it.zorn.nfcbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import android.nfc.NdefMessage
import it.zorn.nfcbro.nfc.NdefBuilder
import it.zorn.nfcbro.ui.NfcViewModel
import it.zorn.nfcbro.ui.Pending
import it.zorn.nfcbro.ui.components.SectionCard

private enum class RecordType(val label: String) {
    TEXT("Text"), URL("Link"), WIFI("Wi-Fi"), CONTACT("Contact"),
    PHONE("Phone"), SMS("SMS"), EMAIL("Email"), LOCATION("Location"),
    APP("App"), CUSTOM("Custom"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(vm: NfcViewModel) {
    var type by remember { mutableStateOf(RecordType.TEXT) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("WPA2") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Record type") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label) },
                    )
                }
            }
        }

        SectionCard(title = type.label) {
            when (type) {
                RecordType.TEXT -> field(a, { a = it }, "Text")
                RecordType.URL -> field(a, { a = it }, "URL (https://…)", KeyboardType.Uri)
                RecordType.WIFI -> {
                    field(a, { a = it }, "Network name (SSID)")
                    field(b, { b = it }, "Password")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Open", "WPA", "WPA2").forEach { s ->
                            FilterChip(selected = security == s, onClick = { security = s }, label = { Text(s) })
                        }
                    }
                }
                RecordType.CONTACT -> {
                    field(a, { a = it }, "Full name")
                    field(b, { b = it }, "Phone", KeyboardType.Phone)
                    field(c, { c = it }, "Email", KeyboardType.Email)
                    field(d, { d = it }, "Organisation")
                }
                RecordType.PHONE -> field(a, { a = it }, "Phone number", KeyboardType.Phone)
                RecordType.SMS -> {
                    field(a, { a = it }, "Phone number", KeyboardType.Phone)
                    field(b, { b = it }, "Message")
                }
                RecordType.EMAIL -> {
                    field(a, { a = it }, "Address", KeyboardType.Email)
                    field(b, { b = it }, "Subject")
                    field(c, { c = it }, "Body")
                }
                RecordType.LOCATION -> {
                    field(a, { a = it }, "Latitude", KeyboardType.Number)
                    field(b, { b = it }, "Longitude", KeyboardType.Number)
                }
                RecordType.APP -> field(a, { a = it }, "Package name (com.example.app)")
                RecordType.CUSTOM -> {
                    field(a, { a = it }, "MIME type (application/json)")
                    field(b, { b = it }, "Payload")
                }
            }
        }

        val message = remember(type, a, b, c, d, security) { buildMessage(type, a, b, c, d, security) }
        Button(
            onClick = { message?.let { vm.start(Pending.Write(it)) } },
            enabled = message != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Save, null)
            Text("  Write to tag")
        }
    }
}

@Composable
private fun field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = keyboard != KeyboardType.Text,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildMessage(
    type: RecordType,
    a: String, b: String, c: String, d: String, security: String,
): NdefMessage? = runCatching {
    when (type) {
        RecordType.TEXT -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.text(a))
        RecordType.URL -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.uri(a))
        RecordType.WIFI -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.wifi(a, b, security))
        RecordType.CONTACT -> if (a.isBlank() && b.isBlank()) null else NdefBuilder.message(NdefBuilder.contact(a, b, c, d))
        RecordType.PHONE -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.tel(a))
        RecordType.SMS -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.sms(a, b))
        RecordType.EMAIL -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.email(a, b, c))
        RecordType.LOCATION -> if (a.isBlank() || b.isBlank()) null else NdefBuilder.message(NdefBuilder.geo(a, b))
        RecordType.APP -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.app(a))
        RecordType.CUSTOM -> if (a.isBlank()) null else NdefBuilder.message(NdefBuilder.mime(a, b.toByteArray()))
    }
}.getOrNull()
