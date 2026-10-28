package com.clappingcat.ar.hands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.clappingcat.ar.scene.HandFrame
import com.clappingcat.ar.scene.SceneState
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

class HandTracker(context: Context, private val scene: SceneState) {

    private var landmarker: HandLandmarker? = null
    private var lastSubmitNanos = 0L
    private var lastTimestampMs = 0L

    init {
        landmarker = try {
            val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    val hands = result.landmarks().map { points ->
                        val flat = FloatArray(points.size * 2)
                        for (i in points.indices) {
                            flat[2 * i] = points[i].x()
                            flat[2 * i + 1] = points[i].y()
                        }
                        flat
                    }
                    scene.hands = HandFrame(hands, System.nanoTime())
                }
                .setErrorListener { }
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Throwable) {
            null
        }
    }

    fun submit(image: ImageProxy) {
        val detector = landmarker ?: return
        val now = System.nanoTime()
        if (now - lastSubmitNanos < MIN_INTERVAL_NS) return
        lastSubmitNanos = now
        val timestampMs = maxOf(image.imageInfo.timestamp / 1_000_000L, lastTimestampMs + 1)
        lastTimestampMs = timestampMs
        try {
            val source = image.toBitmap()
            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
            val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
            detector.detectAsync(BitmapImageBuilder(upright).build(), timestampMs)
        } catch (e: Throwable) {
            return
        }
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }

    private companion object {
        const val MODEL = "hand_landmarker.task"
        const val MIN_INTERVAL_NS = 45_000_000L
    }
}
