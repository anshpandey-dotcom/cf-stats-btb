package com.attackmode.planner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.attackmode.planner.data.Store
import com.attackmode.planner.platform.FocusTimer
import com.attackmode.planner.platform.Scheduler
import com.attackmode.planner.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Store.updateExtras { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        FocusTimer.init(this)
        Bridge.ensureChannels(this)
        pendingRoute = com.attackmode.planner.ui.routeForUrl(intent?.getStringExtra(Bridge.EXTRA_URL).orEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Bridge.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AppRoot(pendingRoute) { pendingRoute = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = com.attackmode.planner.ui.routeForUrl(intent.getStringExtra(Bridge.EXTRA_URL).orEmpty())
    }

    override fun onResume() {
        super.onResume()
        FocusTimer.resume()
        Scheduler.drainWidgetChecks(this)
        Scheduler.syncUnlockNudges(this, Store.state.value)
        Store.updateExtras { it }
    }

    override fun onStop() {
        super.onStop()
        Store.flushNow()
    }
}
