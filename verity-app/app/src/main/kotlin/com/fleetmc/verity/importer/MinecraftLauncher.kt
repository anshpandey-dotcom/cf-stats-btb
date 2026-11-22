package com.fleetmc.verity.importer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

class MinecraftLauncher(private val context: Context) {

    fun isInstalled(): Boolean =
        context.packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE) != null

    fun directIntent(uri: Uri): Intent = viewIntent(uri).setPackage(MINECRAFT_PACKAGE)

    fun chooserIntent(uri: Uri, title: String): Intent =
        Intent.createChooser(viewIntent(uri), title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }

    private fun viewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }

    private companion object {
        const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
        const val MIME_TYPE = "application/octet-stream"
    }
}
