package com.clappingcat.ar

enum class CatMode(
    val rawRes: Int,
    val widthUnits: Float,
    val heightUnits: Float,
    val feetXUnits: Float,
    val feetFromTopUnits: Float,
    val catHeightMeters: Float,
) {
    CLAP(R.raw.clap, 0.9231f, 1.0462f, 0.6989f, 1.0173f, 0.5f),
    HANDSHAKE(R.raw.handshake, 0.6974f, 1.2923f, 0.3284f, 1.0f, 0.55f),
}
