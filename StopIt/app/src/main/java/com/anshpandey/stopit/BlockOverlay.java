package com.anshpandey.stopit;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

final class BlockOverlay implements SurfaceHolder.Callback {

    interface Listener {
        void onClipFinished();
    }

    private static final int FALLBACK_MS = 23000;

    private final Context context;
    private final WindowManager windowManager;
    private final AudioManager audioManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();

    private View root;
    private SurfaceView video;
    private ProgressBar progress;
    private TextView caption;
    private TextView remaining;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private AudioFocusRequest focusRequest;
    private boolean prepared;
    private boolean paused;
    private boolean failed;
    private long fallbackLeft;
    private int videoWidth;
    private int videoHeight;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 200);
        }
    };

    BlockOverlay(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    boolean isShowing() {
        return root != null;
    }

    void show(String appName) {
        if (root == null) {
            attach();
        } else {
            releasePlayer();
        }
        caption.setText(context.getString(R.string.overlay_caption, appName));
        progress.setProgress(0);
        remaining.setText("");
        failed = false;
        fallbackLeft = FALLBACK_MS;
        createPlayer();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    void hide() {
        handler.removeCallbacks(ticker);
        releasePlayer();
        abandonFocus();
        if (root != null) {
            try {
                windowManager.removeViewImmediate(root);
            } catch (RuntimeException ignored) {
            }
        }
        root = null;
        video = null;
        holder = null;
        paused = false;
    }

    void pause() {
        paused = true;
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
    }

    void resume() {
        if (!paused) {
            return;
        }
        paused = false;
        if (player != null && prepared) {
            requestFocus();
            player.start();
        }
    }

    private void attach() {
        Context themed = new ContextThemeWrapper(context, R.style.AppTheme);
        root = LayoutInflater.from(themed).inflate(R.layout.overlay, null);
        video = root.findViewById(R.id.video);
        progress = root.findViewById(R.id.progress);
        caption = root.findViewById(R.id.caption);
        remaining = root.findViewById(R.id.remaining);
        video.getHolder().addCallback(this);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowManager.addView(root, params);
        root.requestFocus();
    }

    private void createPlayer() {
        prepared = false;
        player = new MediaPlayer();
        player.setAudioAttributes(attributes);
        player.setOnPreparedListener(mp -> {
            prepared = true;
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            fit();
            if (!paused) {
                requestFocus();
                mp.start();
            }
        });
        player.setOnCompletionListener(mp -> finish());
        player.setOnErrorListener((mp, what, extra) -> {
            failed = true;
            return true;
        });
        try (AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.stop_clip)) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            if (holder != null) {
                player.setDisplay(holder);
            }
            player.prepareAsync();
        } catch (Exception e) {
            failed = true;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.setOnPreparedListener(null);
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
        }
        player = null;
        prepared = false;
    }

    private void tick() {
        if (root == null) {
            return;
        }
        if (failed) {
            if (!paused) {
                fallbackLeft -= 200;
            }
            render(FALLBACK_MS - fallbackLeft, FALLBACK_MS);
            if (fallbackLeft <= 0) {
                finish();
            }
            return;
        }
        if (player != null && prepared) {
            try {
                render(player.getCurrentPosition(), player.getDuration());
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void render(long position, long duration) {
        if (duration <= 0) {
            return;
        }
        progress.setProgress((int) (position * 1000 / duration));
        long left = Math.max(0, (duration - position + 999) / 1000);
        remaining.setText(left + "s");
    }

    private void finish() {
        handler.removeCallbacks(ticker);
        if (root != null) {
            progress.setProgress(1000);
        }
        listener.onClipFinished();
    }

    private void fit() {
        if (root == null || video == null || videoWidth == 0 || videoHeight == 0) {
            return;
        }
        int width = root.getWidth();
        int height = root.getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        float scale = Math.max((float) width / videoWidth, (float) height / videoHeight);
        int targetWidth = Math.round(videoWidth * scale);
        int targetHeight = Math.round(videoHeight * scale);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) video.getLayoutParams();
        if (lp.width != targetWidth || lp.height != targetHeight) {
            lp.width = targetWidth;
            lp.height = targetHeight;
            video.setLayoutParams(lp);
        }
    }

    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes)
                        .build();
            }
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        holder = surfaceHolder;
        if (player != null) {
            try {
                player.setDisplay(surfaceHolder);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        holder = null;
        if (player != null) {
            try {
                player.setDisplay(null);
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
