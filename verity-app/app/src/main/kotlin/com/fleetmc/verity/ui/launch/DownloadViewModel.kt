package com.fleetmc.verity.ui.launch

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetmc.verity.ads.RewardedAdProvider
import com.fleetmc.verity.data.Addon
import com.fleetmc.verity.data.UnlockStore
import com.fleetmc.verity.importer.AddonStager
import com.fleetmc.verity.importer.LaunchEvent
import com.fleetmc.verity.importer.MinecraftLauncher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

class DownloadViewModel(
    private val unlockStore: UnlockStore,
    private val stager: AddonStager,
    private val launcher: MinecraftLauncher,
    private val ads: RewardedAdProvider,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _events = Channel<LaunchEvent>(Channel.BUFFERED)

    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val events: Flow<LaunchEvent> = _events.receiveAsFlow()

    fun download(activity: Activity, addon: Addon, unlocked: Boolean) {
        if (_busy.value) return
        if (unlocked) {
            open(addon)
            return
        }
        ads.show(activity) { rewarded ->
            if (rewarded) {
                viewModelScope.launch {
                    unlockStore.unlock(addon.id)
                    prepare(addon)
                }
            }
        }
    }

    fun open(addon: Addon) {
        if (_busy.value) return
        viewModelScope.launch { prepare(addon) }
    }

    private suspend fun prepare(addon: Addon) {
        _busy.value = true
        val event = try {
            val uri = stager.stage(addon)
            val chooser = launcher.chooserIntent(uri, CHOOSER_TITLE)
            if (launcher.isInstalled()) {
                LaunchEvent.Open(launcher.directIntent(uri), chooser)
            } else {
                LaunchEvent.MinecraftMissing(chooser)
            }
        } catch (error: IOException) {
            LaunchEvent.Failed("The add-on file could not be prepared. Please try again.")
        }
        _events.send(event)
        _busy.value = false
    }

    private companion object {
        const val CHOOSER_TITLE = "Open add-on with"
    }
}
