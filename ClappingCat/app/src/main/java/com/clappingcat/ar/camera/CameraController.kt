package com.clappingcat.ar.camera

import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.clappingcat.ar.hands.HandTracker
import com.clappingcat.ar.scene.SceneState
import com.clappingcat.ar.sensors.OrientationProvider
import com.clappingcat.ar.tracking.FloorTracker
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

class CameraController(
    private val owner: LifecycleOwner,
    private val context: android.content.Context,
    private val previewView: PreviewView,
    private val sensors: OrientationProvider,
    private val scene: SceneState,
) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handTracker = HandTracker(context, scene)
    private var realtimeTimestamps = true

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ bind(future.get()) }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        executor.shutdown()
        handTracker.close()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bind(provider: ProcessCameraProvider) {
        val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
            .build()
        preview.surfaceProvider = previewView.surfaceProvider

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ratio)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(executor, ::analyze)

        provider.unbindAll()
        val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)

        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val focalLength = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val sensorSize = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val array = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (focalLength != null && sensorSize != null && array != null && sensorSize.width > 0f) {
            val focalPixels = focalLength * array.width() / sensorSize.width
            val longSide = min(array.width().toDouble(), array.height() * 4.0 / 3.0)
            val value = focalPixels / longSide
            if (value in 0.4..1.6) scene.focalOverHeight = value
        }
        val source = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        realtimeTimestamps = source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
    }

    private fun analyze(image: ImageProxy) {
        try {
            handTracker.submit(image)
            val rotation = image.imageInfo.rotationDegrees
            val stamp = if (realtimeTimestamps) image.imageInfo.timestamp else SystemClock.elapsedRealtimeNanos() - FALLBACK_LATENCY_NS
            val orientation = sensors.at(stamp) ?: return
            val plane = image.planes[0]
            val sourceWidth = image.width
            val sourceHeight = image.height
            val uprightWidth = if (rotation == 90 || rotation == 270) sourceHeight else sourceWidth
            val uprightHeight = if (rotation == 90 || rotation == 270) sourceWidth else sourceHeight
            val targetHeight = TARGET_HEIGHT
            val targetWidth = (uprightWidth * targetHeight / uprightHeight.toDouble()).roundToInt()
            val gray = ByteArray(targetWidth * targetHeight)
            val buffer = plane.buffer
            val stride = plane.rowStride
            val stepX = uprightWidth.toDouble() / targetWidth
            val stepY = uprightHeight.toDouble() / targetHeight
            for (yo in 0 until targetHeight) {
                for (xo in 0 until targetWidth) {
                    var sum = 0
                    for (sy in 0..1) {
                        for (sx in 0..1) {
                            val xu = min(uprightWidth - 1, ((xo + 0.25 + 0.5 * sx) * stepX).toInt())
                            val yu = min(uprightHeight - 1, ((yo + 0.25 + 0.5 * sy) * stepY).toInt())
                            val xs: Int
                            val ys: Int
                            when (rotation) {
                                90 -> { xs = yu; ys = sourceHeight - 1 - xu }
                                180 -> { xs = sourceWidth - 1 - xu; ys = sourceHeight - 1 - yu }
                                270 -> { xs = sourceWidth - 1 - yu; ys = xu }
                                else -> { xs = xu; ys = yu }
                            }
                            sum += buffer.get(ys * stride + xs).toInt() and 0xFF
                        }
                    }
                    gray[yo * targetWidth + xo] = (sum / 4).toByte()
                }
            }
            var tracker = scene.tracker
            if (tracker == null) {
                tracker = FloorTracker(targetWidth, targetHeight, scene.focalOverHeight * targetHeight)
                scene.tracker = tracker
            }
            tracker.process(gray, orientation)
        } finally {
            image.close()
        }
    }

    private companion object {
        const val TARGET_HEIGHT = 320
        const val FALLBACK_LATENCY_NS = 50_000_000L
    }
}
