package com.anshpandey.cleanaf;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.anshpandey.cleanaf.media.Exporter;
import com.anshpandey.cleanaf.ui.LevelView;
import com.anshpandey.cleanaf.ui.RecordButton;
import com.anshpandey.cleanaf.ui.WaveformView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity implements Jobs.Listener {
    private enum Page {HOME, RECORD, WORK, RESULT}

    private final Jobs jobs = Jobs.get();
    private final AudioRecorder recorder = new AudioRecorder();
    private View pageHome;
    private View pageRecord;
    private View pageWork;
    private View pageResult;
    private Page page = Page.HOME;
    private boolean recordOpen;
    private ComparePlayer player;
    private Jobs.Session playerSession;
    private Uri pendingCapture;
    private Runnable afterAudioPermission;
    private Runnable afterStoragePermission;

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                startClean(uri);
            });

    private final ActivityResultLauncher<Uri> captureVideo = registerForActivityResult(
            new ActivityResultContracts.CaptureVideo(), ok -> {
                Uri uri = pendingCapture;
                pendingCapture = null;
                if (Boolean.TRUE.equals(ok) && uri != null) {
                    closeRecorder();
                    startClean(uri);
                }
            });

    private final ActivityResultLauncher<String> audioPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Runnable r = afterAudioPermission;
                afterAudioPermission = null;
                if (granted && r != null) r.run();
                else if (!granted) toast("Microphone permission is needed to record.");
            });

    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Runnable r = afterStoragePermission;
                afterStoragePermission = null;
                if (granted && r != null) r.run();
            });

    private final ActivityResultLauncher<String> notifyPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat systemBars = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        systemBars.setAppearanceLightStatusBars(!night);
        systemBars.setAppearanceLightNavigationBars(!night);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        pageHome = findViewById(R.id.pageHome);
        pageRecord = findViewById(R.id.pageRecord);
        pageWork = findViewById(R.id.pageWork);
        pageResult = findViewById(R.id.pageResult);

        findViewById(R.id.chooseButton).setOnClickListener(v -> pickFile.launch(new String[]{"audio/*", "video/*"}));
        findViewById(R.id.recordButton).setOnClickListener(v -> withAudioPermission(this::openRecorder));
        findViewById(R.id.recordClose).setOnClickListener(v -> closeRecorder());
        findViewById(R.id.shutter).setOnClickListener(v -> toggleRecording());
        findViewById(R.id.recordVideo).setOnClickListener(v -> launchCamera());
        findViewById(R.id.workCancel).setOnClickListener(v -> startService(new Intent(this, WorkService.class).setAction(WorkService.ACTION_CANCEL)));
        findViewById(R.id.resultBack).setOnClickListener(v -> leaveResult());
        findViewById(R.id.playButton).setOnClickListener(v -> {
            if (player != null) player.toggle();
        });
        findViewById(R.id.saveButton).setOnClickListener(v -> openSaveSheet());
        MaterialButtonToggleGroup group = findViewById(R.id.compareGroup);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            boolean enhanced = id == R.id.showEnhanced;
            if (player != null) player.setEnhanced(enhanced);
            Jobs.Session s = jobs.session;
            if (s != null) ((WaveformView) findViewById(R.id.waveform)).setPeaks(enhanced ? s.outputPeaks : s.inputPeaks);
        });
        ((WaveformView) findViewById(R.id.waveform)).setListener(f -> {
            if (player != null) player.seek(f);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (page == Page.RECORD) closeRecorder();
                else if (page == Page.RESULT) leaveResult();
                else if (page == Page.WORK) moveTaskToBack(true);
                else finish();
            }
        });
        handleShare(getIntent());
        showLastCrash();
    }

    private void showLastCrash() {
        File f = App.crashFile(this);
        if (!f.exists()) return;
        String text;
        try {
            text = new String(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            text = String.valueOf(e);
        }
        f.delete();
        String report = text;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Something went wrong")
                .setMessage("Copy this report and send it to Ansh so it can be fixed.\n\n" + (report.length() > 1500 ? report.substring(0, 1500) + "…" : report))
                .setPositiveButton("Copy report", (d, w) -> {
                    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("CleanAF crash", report));
                    toast("Report copied");
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        jobs.addListener(this);
        render();
    }

    @Override
    protected void onStop() {
        jobs.removeListener(this);
        if (player != null) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        recorder.stop();
        releasePlayer();
        super.onDestroy();
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        intent.setAction(null);
        if (uri != null) startClean(uri);
    }

    private void startClean(Uri uri) {
        try {
            beginClean(uri);
        } catch (RuntimeException e) {
            App.report(this, e);
            jobs.state = Jobs.State.IDLE;
            render();
            showLastCrash();
        }
    }

    private void beginClean(Uri uri) {
        if (jobs.busy()) {
            toast("Please wait for the current file to finish.");
            return;
        }
        releasePlayer();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        WorkService.clean(this, uri);
    }

    @Override
    public void onJobsChanged() {
        render();
    }

    private void render() {
        Jobs.State st = jobs.state;
        if (st == Jobs.State.CLEANING) {
            show(Page.WORK);
            CircularProgressIndicator ring = findViewById(R.id.workRing);
            ring.setIndeterminate(jobs.progress <= 0);
            ring.setProgressCompat((int) (jobs.progress * 1000), true);
            ((TextView) findViewById(R.id.workPercent)).setText(String.format(Locale.US, "%d%%", Math.round(jobs.progress * 100)));
            ((TextView) findViewById(R.id.workStage)).setText(jobs.stage);
            ((TextView) findViewById(R.id.workFile)).setText(jobs.fileName);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (st == Jobs.State.FAILED) {
            String msg = jobs.message;
            jobs.message = null;
            jobs.state = Jobs.State.IDLE;
            show(recordOpen ? Page.RECORD : Page.HOME);
            if (msg != null) snack(msg);
            showLastCrash();
            return;
        }
        Jobs.Session s = jobs.session;
        if ((st == Jobs.State.READY || st == Jobs.State.EXPORTING) && s != null) {
            show(Page.RESULT);
            bindResult(s);
            View row = findViewById(R.id.exportRow);
            boolean exporting = st == Jobs.State.EXPORTING;
            row.setVisibility(exporting ? View.VISIBLE : View.GONE);
            findViewById(R.id.saveButton).setEnabled(!exporting);
            if (exporting) {
                ((LinearProgressIndicator) findViewById(R.id.exportBar)).setProgressCompat((int) (jobs.progress * 1000), true);
                ((TextView) findViewById(R.id.exportText)).setText(String.format(Locale.US, "Saving · %d%%", Math.round(jobs.progress * 100)));
            }
            String msg = jobs.message;
            if (msg != null && !exporting) {
                jobs.message = null;
                snack(msg);
            }
            return;
        }
        show(recordOpen ? Page.RECORD : Page.HOME);
    }

    private void show(Page p) {
        page = p;
        pageHome.setVisibility(p == Page.HOME ? View.VISIBLE : View.GONE);
        pageRecord.setVisibility(p == Page.RECORD ? View.VISIBLE : View.GONE);
        pageWork.setVisibility(p == Page.WORK ? View.VISIBLE : View.GONE);
        pageResult.setVisibility(p == Page.RESULT ? View.VISIBLE : View.GONE);
    }

    private void bindResult(Jobs.Session s) {
        if (playerSession == s && player != null) return;
        releasePlayer();
        playerSession = s;
        ((TextView) findViewById(R.id.resultTitle)).setText(s.name);
        View video = findViewById(R.id.videoView);
        video.setVisibility(s.video ? View.VISIBLE : View.GONE);
        findViewById(R.id.audioArt).setVisibility(s.video ? View.GONE : View.VISIBLE);
        MaterialButtonToggleGroup group = findViewById(R.id.compareGroup);
        group.check(R.id.showEnhanced);
        WaveformView wave = findViewById(R.id.waveform);
        wave.setPeaks(s.outputPeaks);
        wave.setPosition(0);
        MaterialButton play = findViewById(R.id.playButton);
        TextView time = findViewById(R.id.resultTime);
        try {
            player = new ComparePlayer(this, s.source, s.master, s.inputLufs, s.outputLufs,
                    s.video ? (android.view.TextureView) video : null, (pos, dur, playing) -> {
                wave.setPosition(dur > 0 ? (float) pos / dur : 0);
                time.setText(fmt(pos) + " / " + fmt(dur));
                play.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            });
            player.setEnhanced(true);
        } catch (Exception e) {
            player = null;
            snack("Preview is not available for this file.");
        }
    }

    private void releasePlayer() {
        if (player != null) player.release();
        player = null;
        playerSession = null;
    }

    private void leaveResult() {
        if (jobs.state == Jobs.State.EXPORTING) {
            moveTaskToBack(true);
            return;
        }
        releasePlayer();
        Jobs.Session s = jobs.session;
        jobs.session = null;
        jobs.state = Jobs.State.IDLE;
        if (s != null && s.master != null) Exporter.deleteTree(s.master.getParentFile());
        show(Page.HOME);
    }

    private void withAudioPermission(Runnable r) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            r.run();
        } else {
            afterAudioPermission = r;
            audioPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void openRecorder() {
        if (jobs.busy()) {
            toast("Please wait for the current file to finish.");
            return;
        }
        recordOpen = true;
        ((TextView) findViewById(R.id.recordTime)).setText("0:00");
        ((TextView) findViewById(R.id.recordHint)).setText(R.string.tap_to_record);
        ((LevelView) findViewById(R.id.levelView)).setLevel(0, false);
        findViewById(R.id.recordVideo).setVisibility(View.VISIBLE);
        show(Page.RECORD);
    }

    private void closeRecorder() {
        if (recorder.running()) {
            recordOpen = false;
            recorder.stop();
            return;
        }
        recordOpen = false;
        ((RecordButton) findViewById(R.id.shutter)).setRecording(false);
        render();
    }

    private void toggleRecording() {
        RecordButton shutter = findViewById(R.id.shutter);
        LevelView level = findViewById(R.id.levelView);
        TextView time = findViewById(R.id.recordTime);
        TextView hint = findViewById(R.id.recordHint);
        if (recorder.running()) {
            recorder.stop();
            shutter.setRecording(false);
            return;
        }
        File dir = new File(getCacheDir(), "captures");
        if (!dir.exists() && !dir.mkdirs()) {
            toast("Not enough storage.");
            return;
        }
        File out = new File(dir, "Recording " + stamp() + ".wav");
        shutter.setRecording(true);
        hint.setText(R.string.recording);
        findViewById(R.id.recordVideo).setVisibility(View.INVISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        recorder.start(out, new AudioRecorder.Listener() {
            @Override
            public void onLevel(float l, long ms) {
                level.setLevel(l, true);
                time.setText(fmt(ms));
            }

            @Override
            public void onFinished(File file, java.io.IOException error) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                shutter.setRecording(false);
                level.setLevel(0, false);
                boolean keep = recordOpen;
                recordOpen = false;
                if (error != null) {
                    file.delete();
                    snack(error.getMessage());
                    render();
                } else if (!keep || file.length() < 48000) {
                    file.delete();
                    render();
                } else {
                    startClean(Uri.fromFile(file));
                }
            }
        });
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "captures");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Not enough storage.");
            File out = new File(dir, "Video " + stamp() + ".mp4");
            pendingCapture = FileProvider.getUriForFile(this, "com.anshpandey.cleanaf.files", out);
            captureVideo.launch(pendingCapture);
        } catch (Exception e) {
            pendingCapture = null;
            toast("No camera app found.");
        }
    }

    private void openSaveSheet() {
        Jobs.Session s = jobs.session;
        if (s == null || jobs.busy()) return;
        if (player != null) player.pause();
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.save_sheet, null);
        sheet.setContentView(v);
        TextInputEditText input = v.findViewById(R.id.nameInput);
        String base = s.name.replaceAll("\\.[^.]+$", "");
        input.setText(base + "-clean");
        ChipGroup chips = v.findViewById(R.id.formatGroup);
        TextView info = v.findViewById(R.id.formatInfo);
        Exporter.Format[] formats = s.video
                ? new Exporter.Format[]{s.webmOnly ? Exporter.Format.WEBM : Exporter.Format.MP4, Exporter.Format.MP3, Exporter.Format.M4A, Exporter.Format.WAV}
                : new Exporter.Format[]{Exporter.Format.MP3, Exporter.Format.M4A, Exporter.Format.WAV};
        Exporter.Format[] chosen = {formats[0]};
        for (Exporter.Format f : formats) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.format_chip, chips, false);
            chip.setText(label(f));
            chip.setId(View.generateViewId());
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked) {
                    chosen[0] = f;
                    info.setText(describe(f));
                }
            });
            chips.addView(chip);
            if (f == formats[0]) chip.setChecked(true);
        }
        info.setText(describe(formats[0]));
        v.findViewById(R.id.sheetSave).setOnClickListener(b -> {
            String name = input.getText() == null ? "" : input.getText().toString();
            sheet.dismiss();
            Runnable go = () -> WorkService.export(this, chosen[0], Exporter.sanitize(name));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                afterStoragePermission = go;
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                go.run();
            }
        });
        sheet.show();
    }

    private static String label(Exporter.Format f) {
        switch (f) {
            case MP4:
                return "MP4 video";
            case WEBM:
                return "WebM video";
            case MP3:
                return "MP3";
            case M4A:
                return "M4A";
            default:
                return "WAV";
        }
    }

    private static String describe(Exporter.Format f) {
        switch (f) {
            case MP4:
            case WEBM:
                return "Original picture untouched, clean audio. Saved to Movies/CleanAF.";
            case MP3:
                return "320 kbps · plays everywhere. Saved to Music/CleanAF.";
            case M4A:
                return "AAC 256 kbps · small and high quality. Saved to Music/CleanAF.";
            default:
                return "Lossless · best for editing. Saved to Music/CleanAF.";
        }
    }

    private static String stamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new java.util.Date());
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void snack(String msg) {
        Snackbar.make(findViewById(R.id.root), msg, Snackbar.LENGTH_LONG).show();
    }
}
