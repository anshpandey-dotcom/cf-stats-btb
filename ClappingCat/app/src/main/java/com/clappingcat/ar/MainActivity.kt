package com.clappingcat.ar

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import com.clappingcat.ar.camera.CameraController
import com.clappingcat.ar.scene.Mode
import com.clappingcat.ar.scene.SceneState
import com.clappingcat.ar.sensors.OrientationProvider
import com.clappingcat.ar.sprite.SpriteClip
import com.clappingcat.ar.view.CatOverlayView

class MainActivity : ComponentActivity() {

    private val scene = SceneState()
    private lateinit var sensors: OrientationProvider
    private lateinit var camera: CameraController
    private lateinit var statusView: TextView
    private lateinit var clapButton: Button
    private lateinit var handshakeButton: Button
    private lateinit var faceButton: Button
    private lateinit var soundButton: Button
    private lateinit var catView: CatOverlayView

    private var soundOn = true
    private var resumed = false
    private var cameraStarted = false
    private var clapPlayer: MediaPlayer? = null
    private var handshakePlayer: MediaPlayer? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        sensors = OrientationProvider(this)
        if (!sensors.available) {
            Toast.makeText(this, R.string.no_sensor, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        statusView = findViewById(R.id.status)
        clapButton = findViewById(R.id.btnClap)
        handshakeButton = findViewById(R.id.btnHandshake)
        faceButton = findViewById(R.id.btnFace)
        soundButton = findViewById(R.id.btnSound)
        catView = findViewById(R.id.cat)
        catView.attach(scene, sensors, ::showStatus)

        camera = CameraController(this, this, findViewById<PreviewView>(R.id.preview), sensors, scene)

        clapButton.setOnClickListener { selectMode(Mode.CLAP) }
        handshakeButton.setOnClickListener { selectMode(Mode.HANDSHAKE) }
        faceButton.setOnClickListener {
            scene.faceViewer = !scene.faceViewer
            updateButtons()
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            scene.resetCat()
            selectMode(Mode.CLAP)
        }
        soundButton.setOnClickListener {
            soundOn = !soundOn
            applyVolume()
            updateButtons()
        }
        installInsets()
        updateButtons()

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::sensors.isInitialized) return
        sensors.start()
        resumed = true
        if (clapPlayer == null) {
            clapPlayer = createPlayer(SpriteClip.CLAP)
            handshakePlayer = createPlayer(SpriteClip.HANDSHAKE)
        }
        applyMode()
    }

    override fun onPause() {
        super.onPause()
        if (!::sensors.isInitialized) return
        resumed = false
        sensors.stop()
        clapPlayer?.takeIf { it.isPlaying }?.pause()
        handshakePlayer?.takeIf { it.isPlaying }?.pause()
    }

    override fun onDestroy() {
        clapPlayer?.release()
        handshakePlayer?.release()
        clapPlayer = null
        handshakePlayer = null
        if (::camera.isInitialized) camera.stop()
        super.onDestroy()
    }

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        camera.start()
    }

    private fun showStatus(status: CatOverlayView.Status) {
        val text = when (status) {
            CatOverlayView.Status.WAITING_SENSORS -> R.string.hint_waiting
            CatOverlayView.Status.BEHIND -> R.string.hint_behind
            CatOverlayView.Status.LOCKED -> R.string.hint_locked
            CatOverlayView.Status.WEAK -> R.string.hint_weak
            CatOverlayView.Status.SEARCHING -> R.string.hint_searching
            CatOverlayView.Status.HANDSHAKE -> R.string.hint_handshake
        }
        runOnUiThread { statusView.setText(text) }
    }

    private fun selectMode(mode: Mode) {
        if (scene.mode == mode) return
        scene.mode = mode
        scene.modeStartedAtNanos = System.nanoTime()
        updateButtons()
        applyMode()
    }

    private fun updateButtons() {
        clapButton.isSelected = scene.mode == Mode.CLAP
        handshakeButton.isSelected = scene.mode == Mode.HANDSHAKE
        faceButton.isSelected = scene.faceViewer
        faceButton.setText(if (scene.faceViewer) R.string.face_on else R.string.face_off)
        soundButton.setText(if (soundOn) R.string.sound_on else R.string.sound_off)
        faceButton.visibility = if (scene.mode == Mode.CLAP) View.VISIBLE else View.GONE
    }

    private fun createPlayer(clip: SpriteClip): MediaPlayer? {
        val player = MediaPlayer.create(this, clip.audioRes) ?: return null
        player.isLooping = true
        return player
    }

    private fun applyMode() {
        val active = if (scene.mode == Mode.CLAP) clapPlayer else handshakePlayer
        val idle = if (scene.mode == Mode.CLAP) handshakePlayer else clapPlayer
        idle?.let {
            if (it.isPlaying) it.pause()
            it.seekTo(0)
        }
        applyVolume()
        if (resumed && active != null) {
            active.seekTo(0)
            active.start()
        }
    }

    private fun applyVolume() {
        val volume = if (soundOn) 1f else 0f
        clapPlayer?.setVolume(volume, volume)
        handshakePlayer?.setVolume(volume, volume)
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
}
