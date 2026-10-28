package com.clappingcat.ar.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.clappingcat.ar.scene.Mode
import com.clappingcat.ar.scene.SceneState
import com.clappingcat.ar.sensors.OrientationProvider
import com.clappingcat.ar.sprite.SpriteClip
import com.clappingcat.ar.sprite.SpritePlayer
import com.clappingcat.ar.tracking.FloorTracker
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class CatOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    enum class Status { WAITING_SENSORS, BEHIND, LOCKED, WEAK, SEARCHING, HANDSHAKE }

    private lateinit var scene: SceneState
    private lateinit var sensors: OrientationProvider
    private var onStatus: ((Status) -> Unit)? = null
    private var lastStatus: Status? = null

    private var clapPlayer: SpritePlayer? = null
    private var handshakePlayer: SpritePlayer? = null

    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = 0xFF000000.toInt()
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        it.strokeCap = Paint.Cap.ROUND
        it.strokeJoin = Paint.Join.ROUND
    }
    private val maskPath = Path()
    private val handPoints = FloatArray(42)

    private var heldX = Float.NaN
    private var heldY = Float.NaN
    private var heldScale = Float.NaN

    private var lastRotation: DoubleArray? = null
    private var lastCamera = doubleArrayOf(0.0, FloorTracker.DEFAULT_CAMERA_HEIGHT, 0.0)
    private var focal = 1f
    private var centerX = 0f
    private var centerY = 0f

    private val projected = DoubleArray(3)
    private var dragging = false
    private var dragOffsetX = 0.0
    private var dragOffsetZ = 0.0
    private var lastTwist = Double.NaN

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scene.scale = scene.clampScale(scene.scale * detector.scaleFactor)
            return true
        }
    })

    fun attach(scene: SceneState, sensors: OrientationProvider, onStatus: (Status) -> Unit) {
        this.scene = scene
        this.sensors = sensors
        this.onStatus = onStatus
        clapPlayer = SpritePlayer(context, SpriteClip.CLAP)
        handshakePlayer = SpritePlayer(context, SpriteClip.HANDSHAKE)
        shadowPaint.shader = RadialGradient(0f, 0f, SHADOW_UNIT, intArrayOf(0x99000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        clapPlayer?.release()
        handshakePlayer?.release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!::scene.isInitialized) return
        val now = System.nanoTime()
        focal = (scene.focalOverHeight * max(width / 0.75, height.toDouble())).toFloat()
        centerX = width / 2f
        centerY = height / 2f

        val handshake = scene.mode == Mode.HANDSHAKE
        val clip = if (handshake) SpriteClip.HANDSHAKE else SpriteClip.CLAP
        val player = if (handshake) handshakePlayer else clapPlayer
        val elapsed = (now - scene.modeStartedAtNanos).coerceAtLeast(0L)
        val sequence = ((elapsed * SpriteClip.FPS / 1_000_000_000L) % clip.sequenceLength).toInt()
        player?.request(sequence)

        val frame = scene.hands
        val hands = if (frame != null && now - frame.timeNanos < HAND_TTL_NS) frame.hands else emptyList()
        val layer = if (hands.isNotEmpty()) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1

        val rotation = sensors.at(now - RENDER_LATENCY_NS) ?: sensors.latest()
        if (rotation == null) {
            report(Status.WAITING_SENSORS)
        } else if (handshake) {
            drawHeld(canvas, clip, player?.current, hands)
            report(Status.HANDSHAKE)
        } else {
            drawWorld(canvas, clip, player?.current, rotation)
        }

        if (layer >= 0) {
            for (hand in hands) eraseHand(canvas, hand)
            canvas.restoreToCount(layer)
        }
        postInvalidateOnAnimation()
    }

    private fun drawHeld(canvas: Canvas, clip: SpriteClip, bitmap: android.graphics.Bitmap?, hands: List<FloatArray>) {
        val defaultScale = (focal * HELD_HEIGHT * scene.scale / HELD_DISTANCE).toFloat() / clip.catHeight
        val defaultX = centerX
        val defaultY = centerY + (focal * HELD_DROP / HELD_DISTANCE).toFloat()
        var targetX = defaultX
        var targetY = defaultY
        var targetScale = defaultScale
        if (hands.isNotEmpty()) {
            val hand = largestHand(hands)
            toScreen(hand)
            val wristX = handPoints[0]
            val wristY = handPoints[1]
            val tipX = handPoints[24]
            val tipY = handPoints[25]
            val length = hypot(tipX - wristX, tipY - wristY)
            val anchorX = wristX + (tipX - wristX) * PAW_ALONG_HAND
            val anchorY = wristY + (tipY - wristY) * PAW_ALONG_HAND
            targetScale = (length * PAW_SIZE_RATIO / PAW_LENGTH).coerceIn(defaultScale * 0.6f, defaultScale * 2.2f)
            targetX = anchorX + (clip.feetX - PAW_X) * targetScale
            targetY = anchorY + (clip.feetY - PAW_Y) * targetScale
        }
        if (heldX.isNaN()) {
            heldX = targetX
            heldY = targetY
            heldScale = targetScale
        } else {
            heldX += (targetX - heldX) * HELD_SMOOTHING
            heldY += (targetY - heldY) * HELD_SMOOTHING
            heldScale += (targetScale - heldScale) * HELD_SMOOTHING
        }
        bitmap ?: return
        drawSprite(canvas, clip, bitmap, heldX, heldY, 0f, heldScale, 1f)
    }

    private fun largestHand(hands: List<FloatArray>): FloatArray {
        var best = hands[0]
        var bestSize = -1f
        for (hand in hands) {
            val size = hypot(hand[0] - hand[24], hand[1] - hand[25])
            if (size > bestSize) {
                bestSize = size
                best = hand
            }
        }
        return best
    }

    private fun toScreen(hand: FloatArray) {
        val imageHeight = max(height.toFloat(), width / IMAGE_ASPECT)
        val imageWidth = imageHeight * IMAGE_ASPECT
        for (i in 0 until 21) {
            handPoints[2 * i] = width / 2f + (hand[2 * i] - 0.5f) * imageWidth
            handPoints[2 * i + 1] = height / 2f + (hand[2 * i + 1] - 0.5f) * imageHeight
        }
    }

    private fun eraseHand(canvas: Canvas, hand: FloatArray) {
        if (hand.size < 42) return
        toScreen(hand)
        val p = handPoints
        val palmWidth = hypot(p[10] - p[34], p[11] - p[35])
        val finger = palmWidth * FINGER_WIDTH
        maskPaint.style = Paint.Style.FILL_AND_STROKE
        maskPaint.strokeWidth = finger
        maskPath.reset()
        val palm = intArrayOf(0, 1, 5, 9, 13, 17)
        maskPath.moveTo(p[2 * palm[0]], p[2 * palm[0] + 1])
        for (k in 1 until palm.size) maskPath.lineTo(p[2 * palm[k]], p[2 * palm[k] + 1])
        maskPath.close()
        canvas.drawPath(maskPath, maskPaint)

        maskPaint.style = Paint.Style.STROKE
        val chains = arrayOf(intArrayOf(1, 2, 3, 4), intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12), intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20))
        for (chain in chains) {
            maskPath.reset()
            maskPath.moveTo(p[2 * chain[0]], p[2 * chain[0] + 1])
            for (k in 1 until chain.size) maskPath.lineTo(p[2 * chain[k]], p[2 * chain[k] + 1])
            canvas.drawPath(maskPath, maskPaint)
        }

        maskPaint.strokeWidth = palmWidth * FOREARM_WIDTH
        val armX = p[0] + (p[0] - p[18]) * FOREARM_LENGTH
        val armY = p[1] + (p[1] - p[19]) * FOREARM_LENGTH
        canvas.drawLine(p[0], p[1], armX, armY, maskPaint)
    }

    private fun drawWorld(canvas: Canvas, clip: SpriteClip, bitmap: android.graphics.Bitmap?, rotation: DoubleArray) {
        val camera = scene.tracker?.position ?: lastCamera
        lastRotation = rotation
        lastCamera = camera
        if (!scene.placed) placeInFront(rotation, camera)

        val catHeight = CAT_HEIGHT_METERS * scene.scale
        val feetVisible = project(rotation, camera, scene.catX, 0.0, scene.catZ)
        if (!feetVisible) {
            report(Status.BEHIND)
            return
        }
        val feetX = projected[0].toFloat()
        val feetY = projected[1].toFloat()
        val feetDepth = projected[2]
        if (!project(rotation, camera, scene.catX, catHeight, scene.catZ)) {
            report(Status.BEHIND)
            return
        }
        val dx = (projected[0] - feetX).toFloat()
        val dy = (projected[1] - feetY).toFloat()
        val heightPx = hypot(dx, dy)
        val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()

        val toCameraX = camera[0] - scene.catX
        val toCameraZ = camera[2] - scene.catZ
        val horizontal = hypot(toCameraX, toCameraZ)
        var turn = 1.0
        if (!scene.faceViewer && horizontal > 1e-3) {
            turn = (sin(scene.heading) * toCameraX + cos(scene.heading) * toCameraZ) / horizontal
            if (abs(turn) < MIN_TURN) turn = if (turn < 0) -MIN_TURN else MIN_TURN
        }

        val distance = sqrt(toCameraX * toCameraX + camera[1] * camera[1] + toCameraZ * toCameraZ)
        val elevation = (camera[1] / max(distance, 1e-3)).coerceIn(0.12, 1.0).toFloat()
        val pixelsPerMeter = heightPx / catHeight.toFloat()
        val shadowRadius = SHADOW_RADIUS_METERS * scene.scale.toFloat() * pixelsPerMeter
        canvas.save()
        canvas.translate(feetX, feetY)
        canvas.rotate(angle)
        canvas.scale(1f, elevation)
        canvas.scale(shadowRadius / SHADOW_UNIT, shadowRadius / SHADOW_UNIT)
        canvas.drawCircle(0f, 0f, SHADOW_UNIT, shadowPaint)
        canvas.restore()

        if (bitmap != null) {
            drawSprite(canvas, clip, bitmap, feetX, feetY, angle, heightPx / clip.catHeight, turn.toFloat())
        }

        val quality = scene.tracker?.quality ?: FloorTracker.Quality.LOST
        val onScreen = feetX in 0f..width.toFloat() && feetDepth > 0.0
        report(
            when {
                !onScreen -> Status.BEHIND
                quality == FloorTracker.Quality.GOOD -> Status.LOCKED
                quality == FloorTracker.Quality.WEAK -> Status.WEAK
                else -> Status.SEARCHING
            },
        )
    }

    private fun drawSprite(canvas: Canvas, clip: SpriteClip, bitmap: android.graphics.Bitmap, x: Float, y: Float, angle: Float, scale: Float, turn: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        canvas.scale(scale * turn, scale)
        canvas.translate(-clip.feetX, -clip.feetY)
        canvas.drawBitmap(bitmap, 0f, 0f, spritePaint)
        canvas.restore()
    }

    private fun placeInFront(r: DoubleArray, camera: DoubleArray) {
        var fx = -r[2]
        var fz = -r[8]
        var length = hypot(fx, fz)
        if (length < 0.3) {
            fx = r[1]
            fz = r[7]
            length = hypot(fx, fz)
        }
        if (length < 1e-6) {
            fx = 0.0
            fz = -1.0
            length = 1.0
        }
        scene.catX = camera[0] + PLACE_DISTANCE * fx / length
        scene.catZ = camera[2] + PLACE_DISTANCE * fz / length
        scene.heading = atan2(camera[0] - scene.catX, camera[2] - scene.catZ)
        scene.placed = true
    }

    private fun project(r: DoubleArray, c: DoubleArray, px: Double, py: Double, pz: Double): Boolean {
        val vx = px - c[0]
        val vy = py - c[1]
        val vz = pz - c[2]
        val x = r[0] * vx + r[3] * vy + r[6] * vz
        val y = r[1] * vx + r[4] * vy + r[7] * vz
        val z = r[2] * vx + r[5] * vy + r[8] * vz
        if (z > -NEAR_PLANE) return false
        val depth = -z
        projected[0] = centerX + focal * x / depth
        projected[1] = centerY - focal * y / depth
        projected[2] = depth
        return true
    }

    private fun rayToFloor(x: Float, y: Float): DoubleArray? {
        val r = lastRotation ?: return null
        val c = lastCamera
        val a = (x - centerX) / focal.toDouble()
        val b = -(y - centerY) / focal.toDouble()
        val dx = r[0] * a + r[1] * b - r[2]
        val dy = r[3] * a + r[4] * b - r[5]
        val dz = r[6] * a + r[7] * b - r[8]
        if (dy > -0.02) return null
        val t = c[1] / -dy
        val reach = hypot(t * dx, t * dz)
        val limit = if (reach > MAX_REACH) MAX_REACH / reach else 1.0
        return doubleArrayOf(c[0] + t * limit * dx, c[2] + t * limit * dz)
    }

    private fun beginDrag(x: Float, y: Float) {
        val hit = rayToFloor(x, y)
        if (hit == null) {
            dragging = false
            return
        }
        dragOffsetX = scene.catX - hit[0]
        dragOffsetZ = scene.catZ - hit[1]
        dragging = true
    }

    private fun twist(event: MotionEvent): Double =
        atan2((event.getY(1) - event.getY(0)).toDouble(), (event.getX(1) - event.getX(0)).toDouble())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::scene.isInitialized) return false
        scaleDetector.onTouchEvent(event)
        if (scene.mode != Mode.CLAP) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginDrag(event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragging = false
                if (event.pointerCount >= 2) lastTwist = twist(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && dragging) {
                    val hit = rayToFloor(event.x, event.y)
                    if (hit != null) {
                        scene.catX = hit[0] + dragOffsetX
                        scene.catZ = hit[1] + dragOffsetZ
                    }
                } else if (event.pointerCount >= 2) {
                    val angle = twist(event)
                    if (!scene.faceViewer && !lastTwist.isNaN()) {
                        var delta = angle - lastTwist
                        if (delta > Math.PI) delta -= 2 * Math.PI
                        if (delta < -Math.PI) delta += 2 * Math.PI
                        scene.heading += delta
                    }
                    lastTwist = angle
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastTwist = Double.NaN
                if (event.pointerCount == 2) {
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    beginDrag(event.getX(remaining), event.getY(remaining))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                lastTwist = Double.NaN
            }
        }
        return true
    }

    private fun report(status: Status) {
        if (status != lastStatus) {
            lastStatus = status
            onStatus?.invoke(status)
        }
    }

    private companion object {
        const val CAT_HEIGHT_METERS = 0.5
        const val SHADOW_RADIUS_METERS = 0.22f
        const val SHADOW_UNIT = 100f
        const val PLACE_DISTANCE = 1.6
        const val NEAR_PLANE = 0.05
        const val MAX_REACH = 15.0
        const val MIN_TURN = 0.03
        const val RENDER_LATENCY_NS = 70_000_000L
        const val HELD_HEIGHT = 0.55
        const val HELD_DISTANCE = 0.7
        const val HELD_DROP = 0.36
        const val HELD_SMOOTHING = 0.35f
        const val HAND_TTL_NS = 250_000_000L
        const val IMAGE_ASPECT = 0.75f
        const val FINGER_WIDTH = 0.28f
        const val FOREARM_WIDTH = 1.05f
        const val FOREARM_LENGTH = 1.2f
        const val PAW_X = 150f
        const val PAW_Y = 800f
        const val PAW_LENGTH = 250f
        const val PAW_SIZE_RATIO = 1.1f
        const val PAW_ALONG_HAND = 0.5f
    }
}
