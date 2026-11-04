package com.ansh.recalm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.ansh.recalm.ui.RecalmNav
import com.ansh.recalm.ui.RecalmTheme

sealed interface Incoming {
    data class File(val uri: Uri) : Incoming
    data class Text(val text: String) : Incoming
}

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<Incoming?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        (application as RecalmApp).settings.update { it.copy(onboarded = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            RecalmTheme {
                RecalmNav(incoming = incoming.value, onIncomingHandled = { incoming.value = null })
            }
        }
        requestNotificationsOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { incoming.value = Incoming.File(it) }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                incoming.value = when {
                    stream != null -> Incoming.File(stream)
                    !text.isNullOrBlank() -> Incoming.Text(text)
                    else -> null
                }
            }
        }
    }

    private fun requestNotificationsOnce() {
        val app = application as RecalmApp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && !app.settings.value.onboarded) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        app.settings.update { it.copy(onboarded = true) }
    }
}
