package com.clappingcat.ar.scene

import com.clappingcat.ar.tracking.FloorTracker

enum class Mode { CLAP, HANDSHAKE }

class SceneState {

    @Volatile
    var tracker: FloorTracker? = null

    @Volatile
    var focalOverHeight = DEFAULT_FOCAL_OVER_HEIGHT

    @Volatile
    var mode = Mode.CLAP

    @Volatile
    var faceViewer = true

    @Volatile
    var placed = false

    @Volatile
    var catX = 0.0

    @Volatile
    var catZ = 0.0

    @Volatile
    var heading = 0.0

    @Volatile
    var scale = 1.0

    @Volatile
    var modeStartedAtNanos = System.nanoTime()

    fun clampScale(value: Double): Double = value.coerceIn(MIN_SCALE, MAX_SCALE)

    fun resetCat() {
        placed = false
        scale = 1.0
        tracker?.requestReset()
    }

    companion object {
        const val DEFAULT_FOCAL_OVER_HEIGHT = 0.75
        const val MIN_SCALE = 0.25
        const val MAX_SCALE = 4.0
    }
}
