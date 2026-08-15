package it.zorn.nfcbro

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import it.zorn.nfcbro.data.Store
import it.zorn.nfcbro.ui.App
import it.zorn.nfcbro.ui.NfcState
import it.zorn.nfcbro.ui.NfcViewModel
import it.zorn.nfcbro.ui.theme.NfcBroTheme

class MainActivity : ComponentActivity() {

    private val vm: NfcViewModel by viewModels()
    private var adapter: NfcAdapter? = null
    private var shotMode = false
    private var shotTab: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)

        // Debug-only hook used to capture documentation screenshots.
        val startTab = if (BuildConfig.DEBUG) intent.getStringExtra("nfcbro.tab") else null
        val startOverlay = if (BuildConfig.DEBUG) intent.getStringExtra("nfcbro.overlay") else null
        shotMode = BuildConfig.DEBUG && intent.getBooleanExtra("nfcbro.shot", false)
        shotTab = startTab

        setContent {
            val settings by Store.settings.collectAsState()
            NfcBroTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm, startTab, startOverlay)
                }
            }
        }

        if (!shotMode) handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (shotMode) {
            vm.setNfcState(NfcState.READY)
            if (shotTab == "READ") vm.showSampleTag()
            return
        }
        updateNfcState()
        enableReader()
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun updateNfcState() {
        val a = adapter
        vm.setNfcState(
            when {
                a == null -> NfcState.MISSING
                !a.isEnabled -> NfcState.DISABLED
                else -> NfcState.READY
            }
        )
    }

    private fun enableReader() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        var flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        if (!Store.settings.value.soundOnScan) {
            flags = flags or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        }
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        a.enableReaderMode(this, { tag: Tag -> vm.handleTag(tag) }, flags, options)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        vm.handleTag(tag)
    }
}
