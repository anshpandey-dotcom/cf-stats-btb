package com.fleetmc.kuchlikh

import android.app.Application
import com.fleetmc.kuchlikh.data.BookStore
import com.fleetmc.kuchlikh.data.Prefs
import com.fleetmc.kuchlikh.text.Autocorrect
import com.fleetmc.kuchlikh.text.Transliterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KuchLikhApp : Application() {

    val store by lazy { BookStore(this) }
    val prefs by lazy { Prefs(this) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            Transliterator.load(assets)
            Autocorrect.load(assets)
        }
    }
}
