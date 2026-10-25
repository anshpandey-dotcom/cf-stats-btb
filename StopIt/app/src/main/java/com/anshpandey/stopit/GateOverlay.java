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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

final class GateOverlay implements SurfaceHolder.Callback {

    interface Listener {
        void onGrant(int minutes, String purpose, boolean emergency);

        void onLeave();
    }

    static final int NONE = 0;
    static final int GATE = 1;
    static final int TIME_UP = 2;

    private static final int FALLBACK_MS = 23000;
    private static final long BREATH_MS = 4000;
    private static final int BREATHS = 3;
    private static final int THINK_SECONDS = 5;
    private static final int[] CHOICES = {5, 10, 15, 30};

    private final Context context;
    private final Store store;
    private final WindowManager windowManager;
    private final AudioManager audioManager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();

    private View root;
    private View stageVideo;
    private View stagePanel;
    private LinearLayout panel;
    private SurfaceView video;
    private ProgressBar progress;
    private TextView caption;
    private TextView remaining;
    private TextView notice;
    private Button emergency;
    private View circleBox;
    private View circle;
    private TextView circleText;
    private View panelLogo;
    private TextView title;
    private TextView body;
    private View planBox;
    private EditText purpose;
    private EditText customMinutes;
    private LinearLayout chips;
    private TextView error;
    private Button primary;
    private Button secondary;

    private MediaPlayer player;
    private SurfaceHolder holder;
    private AudioFocusRequest focusRequest;
    private int mode = NONE;
    private String appName;
    private boolean prepared;
    private boolean paused;
    private boolean failed;
    private long fallbackLeft;
    private int videoWidth;
    private int videoHeight;
    private int step;
    private int chosen;
    private float clipVolume = 1f;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 200);
        }
    };

    private final Runnable breathStep = new Runnable() {
        @Override
        public void run() {
            if (step >= BREATHS * 2) {
                startThinking();
                return;
            }
            boolean inhale = step % 2 == 0;
            title.setText(inhale ? R.string.breathe_in : R.string.breathe_out);
            body.setText(context.getString(R.string.breath_count, step / 2 + 1, BREATHS));
            float target = inhale ? 1f : 0.55f;
            circle.animate().scaleX(target).scaleY(target).setDuration(BREATH_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            step++;
            handler.postDelayed(this, BREATH_MS);
        }
    };

    private final Runnable thinkStep = new Runnable() {
        @Override
        public void run() {
            if (step <= 0) {
                showAsk();
                return;
            }
            circleText.setText(String.valueOf(step));
            step--;
            handler.postDelayed(this, 1000);
        }
    };

    GateOverlay(Context context, Store store, Listener listener) {
        this.context = context;
        this.store = store;
        this.listener = listener;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    boolean isShowing() {
        return root != null;
    }

    int mode() {
        return mode;
    }

    boolean showGate(String appName, Store.Lock lock) {
        if (!ensureAttached()) {
            return false;
        }
        mode = GATE;
        this.appName = appName;
        cancelSteps();
        releasePlayer();
        stageVideo.setVisibility(View.VISIBLE);
        stagePanel.setVisibility(View.GONE);
        caption.setText(context.getString(R.string.overlay_caption, appName));
        if (lock != null) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(lock.at));
            String text = context.getString(R.string.locked_notice, appName, time, lock.minutes);
            if (!lock.purpose.isEmpty()) {
                text += "\n" + context.getString(R.string.you_came_for, lock.purpose);
            }
            notice.setText(text);
            notice.setVisibility(View.VISIBLE);
        } else {
            notice.setVisibility(View.GONE);
        }
        int left = store.emergencyLeft();
        if (left > 0) {
            emergency.setText(context.getString(R.string.emergency_button, store.emergencyMinutes(), left));
            emergency.setVisibility(View.VISIBLE);
        } else {
            emergency.setVisibility(View.GONE);
        }
        progress.setProgress(0);
        remaining.setText("");
        failed = false;
        fallbackLeft = FALLBACK_MS;
        silenceOthers();
        createPlayer();
        handler.post(ticker);
        return true;
    }

    boolean showTimeUp(String appName, Store.Session session) {
        if (!ensureAttached()) {
            return false;
        }
        mode = TIME_UP;
        this.appName = appName;
        cancelSteps();
        releasePlayer();
        showPanel(false, true, Gravity.CENTER);
        title.setText(R.string.time_up);
        String text = context.getString(R.string.time_up_body, session.minutes, appName);
        if (!session.purpose.isEmpty()) {
            text += "\n" + context.getString(R.string.you_came_for, session.purpose);
        }
        body.setText(text);
        primary.setText(context.getString(R.string.close_app, appName));
        primary.setOnClickListener(v -> listener.onLeave());
        secondary.setVisibility(View.GONE);
        return true;
    }

    void hide() {
        cancelSteps();
        releasePlayer();
        abandonFocus();
        restoreOthers();
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
        mode = NONE;
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

    static void restoreAudio(Context context, Store store) {
        if (!store.mutedMusic()) {
            return;
        }
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } catch (RuntimeException ignored) {
        }
        store.setMutedMusic(false);
    }

    private boolean ensureAttached() {
        return root != null || attach();
    }

    private boolean attach() {
        Context themed = new ContextThemeWrapper(context, R.style.AppTheme);
        View view = LayoutInflater.from(themed).inflate(R.layout.overlay, null);
        stageVideo = view.findViewById(R.id.stage_video);
        stagePanel = view.findViewById(R.id.stage_panel);
        panel = view.findViewById(R.id.panel);
        video = view.findViewById(R.id.video);
        progress = view.findViewById(R.id.progress);
        caption = view.findViewById(R.id.caption);
        remaining = view.findViewById(R.id.remaining);
        notice = view.findViewById(R.id.notice);
        emergency = view.findViewById(R.id.emergency);
        circleBox = view.findViewById(R.id.circle_box);
        circle = view.findViewById(R.id.circle);
        circleText = view.findViewById(R.id.circle_text);
        panelLogo = view.findViewById(R.id.panel_logo);
        title = view.findViewById(R.id.panel_title);
        body = view.findViewById(R.id.panel_body);
        planBox = view.findViewById(R.id.plan_box);
        purpose = view.findViewById(R.id.purpose);
        customMinutes = view.findViewById(R.id.custom_minutes);
        chips = view.findViewById(R.id.chips);
        error = view.findViewById(R.id.panel_error);
        primary = view.findViewById(R.id.primary);
        secondary = view.findViewById(R.id.secondary);

        video.getHolder().addCallback(this);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
        emergency.setOnClickListener(v -> listener.onGrant(store.emergencyMinutes(),
                context.getString(R.string.emergency_purpose), true));
        buildChips(themed);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        params.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0);
            params.setFitInsetsSides(0);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            windowManager.addView(view, params);
        } catch (RuntimeException e) {
            return false;
        }
        root = view;
        root.requestFocus();
        return true;
    }

    private void buildChips(Context themed) {
        float density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < CHOICES.length; i++) {
            int minutes = CHOICES[i];
            TextView chip = new TextView(themed);
            chip.setText(context.getString(R.string.minutes_short, minutes));
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(context.getColor(R.color.text));
            chip.setTextSize(14);
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setTag(minutes);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                lp.setMarginStart((int) (8 * density));
            }
            chip.setOnClickListener(v -> {
                customMinutes.setText("");
                select(minutes);
            });
            chips.addView(chip, lp);
        }
        customMinutes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    select(0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void select(int minutes) {
        chosen = minutes;
        for (int i = 0; i < chips.getChildCount(); i++) {
            View chip = chips.getChildAt(i);
            chip.setSelected(((Integer) chip.getTag()) == minutes);
        }
    }

    private void showPanel(boolean withCircle, boolean withLogo, int gravity) {
        stageVideo.setVisibility(View.GONE);
        stagePanel.setVisibility(View.VISIBLE);
        panel.setGravity(gravity);
        circleBox.setVisibility(withCircle ? View.VISIBLE : View.GONE);
        panelLogo.setVisibility(withLogo ? View.VISIBLE : View.GONE);
        planBox.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        primary.setVisibility(View.VISIBLE);
        secondary.setVisibility(View.VISIBLE);
        circleText.setText("");
    }

    private void showError(int text) {
        error.setText(text);
        error.setVisibility(View.VISIBLE);
    }

    private void startBreathing() {
        handler.removeCallbacks(ticker);
        releasePlayer();
        abandonFocus();
        showPanel(true, false, Gravity.CENTER);
        primary.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
        circle.setScaleX(0.55f);
        circle.setScaleY(0.55f);
        step = 0;
        handler.post(breathStep);
    }

    private void startThinking() {
        circle.animate().cancel();
        circle.animate().scaleX(0.8f).scaleY(0.8f).setDuration(600).start();
        title.setText(R.string.think_title);
        body.setText(context.getString(R.string.think_body, appName));
        step = THINK_SECONDS;
        handler.post(thinkStep);
    }

    private void showAsk() {
        showPanel(false, true, Gravity.CENTER);
        title.setText(context.getString(R.string.ask_title, appName));
        body.setText(R.string.ask_body);
        primary.setText(R.string.ask_no);
        primary.setOnClickListener(v -> listener.onLeave());
        secondary.setText(R.string.ask_yes);
        secondary.setOnClickListener(v -> showPlan());
    }

    private void showPlan() {
        showPanel(false, false, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        title.setText(R.string.plan_title);
        body.setText(context.getString(R.string.plan_body, appName));
        planBox.setVisibility(View.VISIBLE);
        purpose.setText("");
        customMinutes.setText("");
        select(0);
        primary.setText(context.getString(R.string.open_app, appName));
        primary.setOnClickListener(v -> submitPlan());
        secondary.setText(R.string.never_mind);
        secondary.setOnClickListener(v -> listener.onLeave());
    }

    private void submitPlan() {
        String reason = purpose.getText().toString().trim();
        if (reason.length() < 2) {
            showError(R.string.need_purpose);
            return;
        }
        int minutes = chosen;
        String custom = customMinutes.getText().toString().trim();
        if (!custom.isEmpty()) {
            try {
                minutes = Integer.parseInt(custom);
            } catch (NumberFormatException e) {
                minutes = 0;
            }
        }
        if (minutes < 1 || minutes > 180) {
            showError(R.string.need_minutes);
            return;
        }
        listener.onGrant(minutes, reason, false);
    }

    private void cancelSteps() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(breathStep);
        handler.removeCallbacks(thinkStep);
        if (circle != null) {
            circle.animate().cancel();
        }
    }

    private void createPlayer() {
        prepared = false;
        player = new MediaPlayer();
        player.setAudioAttributes(attributes);
        player.setOnPreparedListener(mp -> {
            prepared = true;
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            mp.setVolume(clipVolume, clipVolume);
            fit();
            if (!paused) {
                requestFocus();
                mp.start();
            }
        });
        player.setOnCompletionListener(mp -> startBreathing());
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
        if (root == null || mode != GATE) {
            return;
        }
        if (failed) {
            if (!paused) {
                fallbackLeft -= 200;
            }
            render(FALLBACK_MS - fallbackLeft, FALLBACK_MS);
            if (fallbackLeft <= 0) {
                startBreathing();
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
        remaining.setText(context.getString(R.string.seconds_short, left));
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

    private void silenceOthers() {
        try {
            int musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int alarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            if (!store.mutedMusic()) {
                float music = musicMax > 0 ? (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / musicMax : 0f;
                float alarmRatio = alarmMax > 0 ? (float) alarm / alarmMax : 0f;
                clipVolume = alarmRatio > 0 ? Math.min(1f, music / alarmRatio) : 0f;
                if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    store.setMutedMusic(true);
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void restoreOthers() {
        restoreAudio(context, store);
    }

    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .build();
            }
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN);
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
