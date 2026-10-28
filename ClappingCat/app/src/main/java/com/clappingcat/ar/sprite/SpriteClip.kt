package com.clappingcat.ar.sprite

import com.clappingcat.ar.R

enum class SpriteClip(
    val directory: String,
    val frames: Int,
    val width: Int,
    val height: Int,
    val feetX: Float,
    val feetY: Float,
    val catHeight: Float,
    val pingPong: Boolean,
    val audioRes: Int,
) {
    CLAP("clap", 80, 658, 749, 502.3f, 740.6f, 728f, false, R.raw.clap_audio),
    HANDSHAKE("handshake", 53, 495, 940, 233.4f, 728f, 728f, true, R.raw.handshake_audio);

    val sequenceLength: Int get() = if (pingPong) frames * 2 - 2 else frames

    fun assetIndex(sequence: Int): Int = if (sequence < frames) sequence else 2 * frames - 2 - sequence

    companion object {
        const val FPS = 30
    }
}
