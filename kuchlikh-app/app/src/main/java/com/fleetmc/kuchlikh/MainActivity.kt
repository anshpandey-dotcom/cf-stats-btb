package com.fleetmc.kuchlikh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.ui.EditorScreen
import com.fleetmc.kuchlikh.ui.KuchLikhTheme
import com.fleetmc.kuchlikh.ui.LibraryScreen
import com.fleetmc.kuchlikh.ui.ReaderScreen
import com.fleetmc.kuchlikh.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = (application as KuchLikhApp).prefs
        setContent {
            val dark = when (prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            KuchLikhTheme(prefs.theme) {
                val editor: EditorViewModel = viewModel()
                var route by rememberSaveable { mutableStateOf("library") }
                val parts = route.split("/")
                when (parts[0]) {
                    "editor" -> {
                        editor.open(parts[1])
                        EditorScreen(
                            editor,
                            onBack = {
                                editor.close()
                                route = "library"
                            },
                            onRead = { route = "reader/${parts[1]}/editor" },
                        )
                    }
                    "reader" -> {
                        editor.open(parts[1])
                        ReaderScreen(editor) {
                            if (parts.getOrNull(2) == "editor") {
                                route = "editor/${parts[1]}"
                            } else {
                                editor.close()
                                route = "library"
                            }
                        }
                    }
                    "settings" -> SettingsScreen { route = "library" }
                    else -> LibraryScreen(
                        onOpen = { route = "editor/$it" },
                        onRead = { route = "reader/$it/library" },
                        onSettings = { route = "settings" },
                    )
                }
            }
        }
    }
}
