package com.clappingcat.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot
import kotlin.math.pow

class ArRenderer(private val activity: MainActivity) : GLSurfaceView.Renderer {

    @Volatile
    var session: Session? = null

    @Volatile
    var mode = CatMode.CLAP

    @Volatile
    var userScale = 1f

    @Volatile
    private var resetRequested = false

    private val taps = ConcurrentLinkedQueue<FloatArray>()
    private val background = BackgroundRenderer()
    private val catQuad = CatQuad()
    private val disc = DiscRenderer()
    private lateinit var clapSource: VideoSource
    private lateinit var handshakeSource: VideoSource

    private var boundSession: Session? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var geometryDirty = true
    private var lastRotation = -1
    private var anchor: Anchor? = null
    private var placedAtNanos = 0L
    private var lastStatus = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    fun requestTap(x: Float, y: Float) {
        taps.add(floatArrayOf(x, y))
    }

    fun requestReset() {
        resetRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.create()
        catQuad.create()
        disc.create()
        clapSource = VideoSource()
        handshakeSource = VideoSource()
        boundSession = null
        activity.onVideoSurfaces(clapSource.surface, handshakeSource.surface)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        geometryDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return
        if (viewWidth == 0) return

        if (session !== boundSession) {
            session.setCameraTextureNames(intArrayOf(background.textureId))
            boundSession = session
            geometryDirty = true
        }
        val rotation = activity.displayRotation()
        if (geometryDirty || rotation != lastRotation) {
            session.setDisplayGeometry(rotation, viewWidth, viewHeight)
            lastRotation = rotation
            geometryDirty = false
        }

        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            return
        }
        val camera = frame.camera
        background.draw(frame)

        clapSource.update()
        handshakeSource.update()

        if (resetRequested) {
            resetRequested = false
            anchor?.detach()
            anchor = null
            taps.clear()
        }

        camera.getProjectionMatrix(projection, 0, 0.05f, 50f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        val tracking = camera.trackingState == TrackingState.TRACKING
        val currentMode = mode

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        if (currentMode == CatMode.CLAP) {
            drawWorldCat(frame, camera, tracking)
        } else {
            drawHeldCat(camera)
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawWorldCat(frame: Frame, camera: Camera, tracking: Boolean) {
        var centerHit: Pose? = null
        if (tracking) {
            handleTaps(frame)
            if (anchor == null) {
                centerHit = findFloorHit(frame, viewWidth / 2f, viewHeight / 2f)
            }
        } else {
            taps.clear()
        }

        val placed = anchor
        if (placed != null && placed.trackingState == TrackingState.TRACKING) {
            val elapsed = (System.nanoTime() - placedAtNanos) / 1_000_000_000f
            val progress = (elapsed / POP_SECONDS).coerceIn(0f, 1f)
            val scale = easeOutBack(progress) * userScale
            val opacity = (progress * 2f).coerceAtMost(1f)
            val pose = placed.pose
            val cameraPose = camera.pose
            var fx = cameraPose.tx() - pose.tx()
            var fz = cameraPose.tz() - pose.tz()
            val length = hypot(fx, fz)
            if (length < 1e-3f) {
                fx = 0f
                fz = 1f
            } else {
                fx /= length
                fz /= length
            }

            Matrix.setIdentityM(model, 0)
            model[12] = pose.tx()
            model[13] = pose.ty() + 0.002f
            model[14] = pose.tz()
            disc.draw(viewProjection, model, SHADOW_RADIUS * scale, true)

            model[0] = fz
            model[1] = 0f
            model[2] = -fx
            model[4] = 0f
            model[5] = 1f
            model[6] = 0f
            model[8] = fx
            model[9] = 0f
            model[10] = fz
            model[13] = pose.ty()
            drawCat(CatMode.CLAP, clapSource, scale, opacity)
        } else if (centerHit != null) {
            centerHit.toMatrix(model, 0)
            disc.draw(viewProjection, model, RETICLE_RADIUS, false)
        }

        val status = when {
            !tracking -> STATUS_NO_TRACKING
            placed != null -> STATUS_PLACED
            centerHit != null -> STATUS_TAP_PLACE
            else -> STATUS_FIND_FLOOR
        }
        reportStatus(status)
    }

    private fun drawHeldCat(camera: Camera) {
        val pose = camera.displayOrientedPose.compose(Pose.makeTranslation(0f, HELD_OFFSET_Y, -HELD_DISTANCE))
        pose.toMatrix(model, 0)
        drawCat(CatMode.HANDSHAKE, handshakeSource, userScale, 1f)
        reportStatus(STATUS_HANDSHAKE)
    }

    private fun drawCat(catMode: CatMode, source: VideoSource, scale: Float, opacity: Float) {
        if (!source.hasFrame || scale <= 0f) return
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        val height = catMode.catHeightMeters * scale
        catQuad.draw(
            mvp,
            -catMode.feetXUnits * height,
            -(catMode.heightUnits - catMode.feetFromTopUnits) * height,
            catMode.widthUnits * height,
            catMode.heightUnits * height,
            source,
            opacity,
        )
    }

    private fun handleTaps(frame: Frame) {
        while (true) {
            val tap = taps.poll() ?: break
            val pose = findFloorHit(frame, tap[0], tap[1]) ?: continue
            anchor?.detach()
            anchor = session?.createAnchor(pose)
            placedAtNanos = System.nanoTime()
        }
    }

    private fun findFloorHit(frame: Frame, x: Float, y: Float): Pose? {
        for (hit in frame.hitTest(x, y)) {
            val trackable = hit.trackable
            if (trackable is Plane &&
                trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                trackable.isPoseInPolygon(hit.hitPose)
            ) {
                return hit.hitPose
            }
        }
        return null
    }

    private fun reportStatus(status: Int) {
        if (status != lastStatus) {
            lastStatus = status
            activity.showStatus(status)
        }
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    companion object {
        const val STATUS_NO_TRACKING = 0
        const val STATUS_FIND_FLOOR = 1
        const val STATUS_TAP_PLACE = 2
        const val STATUS_PLACED = 3
        const val STATUS_HANDSHAKE = 4

        private const val POP_SECONDS = 0.35f
        private const val SHADOW_RADIUS = 0.22f
        private const val RETICLE_RADIUS = 0.14f
        private const val HELD_DISTANCE = 0.7f
        private const val HELD_OFFSET_Y = -0.36f
    }
}
