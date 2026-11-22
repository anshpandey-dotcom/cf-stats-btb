package com.fleetmc.verity.ads

import androidx.compose.ui.graphics.Color

data class AdBlob(val x: Float, val y: Float, val radius: Float)

data class FakeAdCreative(
    val brand: String,
    val headline: String,
    val callToAction: String,
    val background: List<Color>,
    val accent: Color,
    val blobs: List<AdBlob>,
) {
    companion object {
        val samples = listOf(
            FakeAdCreative(
                brand = "Zorbly Snacks",
                headline = "Crunch like nobody is watching",
                callToAction = "Grab a bag",
                background = listOf(Color(0xFFFF8A3D), Color(0xFFE5316F)),
                accent = Color(0xFFFFE066),
                blobs = listOf(AdBlob(0.18f, 0.2f, 0.32f), AdBlob(0.85f, 0.45f, 0.24f), AdBlob(0.4f, 0.85f, 0.3f)),
            ),
            FakeAdCreative(
                brand = "Lumivo Phones",
                headline = "See the night in full colour",
                callToAction = "Explore",
                background = listOf(Color(0xFF0B1E5B), Color(0xFF14B8D4)),
                accent = Color(0xFFB8F1FF),
                blobs = listOf(AdBlob(0.8f, 0.18f, 0.3f), AdBlob(0.15f, 0.55f, 0.22f), AdBlob(0.7f, 0.88f, 0.28f)),
            ),
            FakeAdCreative(
                brand = "Quillo Learn",
                headline = "Ten minutes a day. A whole new you.",
                callToAction = "Start free",
                background = listOf(Color(0xFF5B2A86), Color(0xFF2EC4B6)),
                accent = Color(0xFFFFD6F6),
                blobs = listOf(AdBlob(0.25f, 0.15f, 0.26f), AdBlob(0.9f, 0.6f, 0.32f), AdBlob(0.2f, 0.9f, 0.24f)),
            ),
        )
    }
}
