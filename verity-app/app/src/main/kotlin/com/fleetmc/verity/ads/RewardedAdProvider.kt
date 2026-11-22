package com.fleetmc.verity.ads

import android.app.Activity
import androidx.compose.runtime.Composable

interface RewardedAdProvider {

    fun show(activity: Activity, onFinished: (rewarded: Boolean) -> Unit)

    @Composable
    fun Host()
}
