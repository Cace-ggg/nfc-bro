package it.zorn.nfcbro

import android.app.Application
import it.zorn.nfcbro.data.Store

class NfcBroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
