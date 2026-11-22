package com.fleetmc.verity.di

import android.content.Context
import com.fleetmc.verity.ads.FakeRewardedAdProvider
import com.fleetmc.verity.ads.RewardedAdProvider
import com.fleetmc.verity.data.CatalogRepository
import com.fleetmc.verity.data.UnlockStore
import com.fleetmc.verity.importer.AddonStager
import com.fleetmc.verity.importer.MinecraftLauncher

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val catalogRepository = CatalogRepository(appContext)
    val unlockStore = UnlockStore(appContext)
    val stager = AddonStager(appContext)
    val launcher = MinecraftLauncher(appContext)
    val adProvider: RewardedAdProvider = FakeRewardedAdProvider()
}
