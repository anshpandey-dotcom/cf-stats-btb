package com.fleetmc.verity.importer

import android.content.Intent

sealed interface LaunchEvent {
    data class Open(val direct: Intent, val chooser: Intent) : LaunchEvent
    data class MinecraftMissing(val chooser: Intent) : LaunchEvent
    data class Failed(val message: String) : LaunchEvent
}
