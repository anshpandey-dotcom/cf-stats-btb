package com.fleetmc.verity.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRewardedAdProvider : RewardedAdProvider {

    private class Session(val creative: FakeAdCreative, val onFinished: (Boolean) -> Unit)

    private val session = MutableStateFlow<Session?>(null)

    override fun show(activity: Activity, onFinished: (rewarded: Boolean) -> Unit) {
        if (session.value != null) return
        session.value = Session(FakeAdCreative.samples.random(), onFinished)
    }

    @Composable
    override fun Host() {
        val current by session.collectAsState()
        current?.let { active ->
            FakeAdScreen(
                creative = active.creative,
                onClose = { rewarded ->
                    session.value = null
                    active.onFinished(rewarded)
                },
            )
        }
    }
}
