package com.clappingcat.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

class MainActivity : Activity() {

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusView: TextView
    private lateinit var clapButton: Button
    private lateinit var handshakeButton: Button
    private lateinit var soundButton: Button
    private lateinit var renderer: ArRenderer

    private var session: Session? = null
    private var installRequested = false
    private var resumed = false
    private var soundOn = true
    private var mode = CatMode.CLAP
    private var clapPlayer: MediaPlayer? = null
    private var handshakePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface)
        statusView = findViewById(R.id.status)
        clapButton = findViewById(R.id.btnClap)
        handshakeButton = findViewById(R.id.btnHandshake)
        soundButton = findViewById(R.id.btnSound)

        renderer = ArRenderer(this)
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surfaceView.setWillNotDraw(false)

        installTouchHandling()
        installInsets()

        clapButton.setOnClickListener { selectMode(CatMode.CLAP) }
        handshakeButton.setOnClickListener { selectMode(CatMode.HANDSHAKE) }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            renderer.requestReset()
            renderer.userScale = 1f
            selectMode(CatMode.CLAP)
        }
        soundButton.setOnClickListener {
            soundOn = !soundOn
            soundButton.setText(if (soundOn) R.string.sound_on else R.string.sound_off)
            applyVolume()
        }
        updateModeButtons()
    }

    override fun onResume() {
        super.onResume()
        if (!ensureSession()) return
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, R.string.ar_failed, Toast.LENGTH_LONG).show()
            session = null
            return
        }
        renderer.session = session
        surfaceView.onResume()
        resumed = true
        applyMode()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        surfaceView.onPause()
        session?.pause()
        clapPlayer?.takeIf { it.isPlaying }?.pause()
        handshakePlayer?.takeIf { it.isPlaying }?.pause()
    }

    override fun onDestroy() {
        releasePlayers()
        renderer.session = null
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    fun displayRotation(): Int = windowManager.defaultDisplay.rotation

    fun showStatus(status: Int) {
        val text = when (status) {
            ArRenderer.STATUS_NO_TRACKING -> R.string.hint_no_tracking
            ArRenderer.STATUS_FIND_FLOOR -> R.string.hint_find_floor
            ArRenderer.STATUS_TAP_PLACE -> R.string.hint_tap_place
            ArRenderer.STATUS_PLACED -> R.string.hint_placed
            else -> R.string.hint_handshake
        }
        runOnUiThread { statusView.setText(text) }
    }

    fun onVideoSurfaces(clap: Surface, handshake: Surface) {
        runOnUiThread {
            releasePlayers()
            clapPlayer = createPlayer(CatMode.CLAP, clap)
            handshakePlayer = createPlayer(CatMode.HANDSHAKE, handshake)
            applyMode()
        }
    }

    private fun createPlayer(catMode: CatMode, surface: Surface): MediaPlayer? {
        val player = MediaPlayer.create(this, catMode.rawRes) ?: return null
        player.setSurface(surface)
        player.isLooping = true
        return player
    }

    private fun releasePlayers() {
        clapPlayer?.release()
        handshakePlayer?.release()
        clapPlayer = null
        handshakePlayer = null
    }

    private fun selectMode(newMode: CatMode) {
        if (newMode == mode) return
        mode = newMode
        renderer.mode = newMode
        updateModeButtons()
        applyMode()
    }

    private fun updateModeButtons() {
        clapButton.isSelected = mode == CatMode.CLAP
        handshakeButton.isSelected = mode == CatMode.HANDSHAKE
    }

    private fun applyMode() {
        val active = if (mode == CatMode.CLAP) clapPlayer else handshakePlayer
        val idle = if (mode == CatMode.CLAP) handshakePlayer else clapPlayer
        idle?.let {
            if (it.isPlaying) it.pause()
            it.seekTo(0)
        }
        applyVolume()
        if (resumed && active != null && !active.isPlaying) active.start()
    }

    private fun applyVolume() {
        val volume = if (soundOn) 1f else 0f
        clapPlayer?.setVolume(volume, volume)
        handshakePlayer?.setVolume(volume, volume)
    }

    private fun ensureSession(): Boolean {
        if (session != null) return true
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
                return false
            }
            val created = Session(this)
            val config = Config(created)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            created.configure(config)
            session = created
            return true
        } catch (e: UnavailableException) {
            Toast.makeText(this, R.string.ar_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.ar_failed, Toast.LENGTH_LONG).show()
        }
        return false
    }

    private fun installTouchHandling() {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.userScale = (renderer.userScale * detector.scaleFactor).coerceIn(0.4f, 3f)
                return true
            }
        })
        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!scaleDetector.isInProgress) renderer.requestTap(e.x, e.y)
                return true
            }
        })
        surfaceView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun installInsets() {
        val overlay = findViewById<View>(R.id.overlay)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            overlay.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
    }

    private companion object {
        const val CAMERA_REQUEST = 1
    }
}
