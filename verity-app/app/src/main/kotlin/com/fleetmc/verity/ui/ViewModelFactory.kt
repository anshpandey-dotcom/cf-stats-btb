package com.fleetmc.verity.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fleetmc.verity.VerityApp
import com.fleetmc.verity.ui.launch.DownloadViewModel

private fun CreationExtras.container() =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VerityApp).container

val VerityViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = container()
        CatalogViewModel(container.catalogRepository, container.unlockStore)
    }
    initializer {
        val container = container()
        DownloadViewModel(
            unlockStore = container.unlockStore,
            stager = container.stager,
            launcher = container.launcher,
            ads = container.adProvider,
        )
    }
}
