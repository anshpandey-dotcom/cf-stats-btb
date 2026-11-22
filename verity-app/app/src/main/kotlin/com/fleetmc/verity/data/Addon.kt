package com.fleetmc.verity.data

import java.util.Locale

data class Addon(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val minVersion: String,
    val maxVersion: String,
    val file: String,
    val sizeBytes: Long,
    val previews: List<String>,
) {
    val versionRange: String
        get() = if (minVersion == maxVersion || maxVersion.isBlank()) minVersion else "$minVersion – $maxVersion"

    val filePath: String
        get() = "addons/$id/$file"

    val previewPaths: List<String>
        get() = previews.map { "addons/$id/$it" }

    val sizeLabel: String
        get() = formatSize(sizeBytes)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
