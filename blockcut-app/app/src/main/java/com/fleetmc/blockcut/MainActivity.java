package com.fleetmc.blockcut;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String ORIGIN = "https://" + HOST;
    private static final int REQ_FILE = 41;
    private static final int REQ_MIC = 42;
    private static final int REQ_STORAGE = 43;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;
    private TextToSpeech tts;
    private boolean ttsReady;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, OutputStream> saves = new HashMap<>();
    private final Map<String, Uri> saveUris = new HashMap<>();
    private final Map<String, String> savePlaces = new HashMap<>();
    private Uri lastSaved;
    private String lastSavedMime = "*/*";
    private int counter = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(14, 18, 16));
        w.setNavigationBarColor(Color.rgb(14, 18, 16));

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(14, 18, 16));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(14, 18, 16));
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.addJavascriptInterface(new Bridge(), "BlockcutNative");
        web.setWebViewClient(new LocalClient());
        web.setWebChromeClient(new Chrome());

        tts = new TextToSpeech(this, code -> ttsReady = code == TextToSpeech.SUCCESS);

        if (state != null) web.restoreState(state);
        else web.loadUrl(ORIGIN + "/www/index.html");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("window.__pause && window.__pause()", null);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        else if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;
        final Uri src = uri;
        new Thread(() -> {
            try {
                String name = displayName(src);
                File dir = new File(getCacheDir(), "inbox");
                dir.mkdirs();
                File out = new File(dir, System.currentTimeMillis() + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = getContentResolver().openInputStream(src); OutputStream os = new FileOutputStream(out)) {
                    copy(in, os);
                }
                String url = ORIGIN + "/inbox/" + out.getName();
                String js = "window.__openFile && window.__openFile(" + JSONObject.quote(url) + "," + JSONObject.quote(name) + ")";
                main.postDelayed(() -> web.evaluateJavascript(js, null), 1500);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String displayName(Uri uri) {
        String name = "file";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".zip")) return "application/zip";
        if (p.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private WebResourceResponse respond(InputStream in, String path) {
        String mime = mimeOf(path);
        String enc = mime.startsWith("text/") || mime.equals("application/json") ? "utf-8" : null;
        WebResourceResponse r = new WebResourceResponse(mime, enc, in);
        Map<String, String> h = new HashMap<>();
        h.put("Cache-Control", "no-cache");
        h.put("Access-Control-Allow-Origin", "*");
        r.setResponseHeaders(h);
        return r;
    }

    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (!HOST.equals(u.getHost())) return null;
            String path = u.getPath();
            if (path == null) return null;
            try {
                if (path.startsWith("/www/")) {
                    AssetManager am = getAssets();
                    return respond(am.open(path.substring(1)), path);
                }
                if (path.startsWith("/voice/")) {
                    File f = new File(new File(getCacheDir(), "voice"), new File(path).getName());
                    return respond(new FileInputStream(f), path);
                }
                if (path.startsWith("/inbox/")) {
                    File f = new File(new File(getCacheDir(), "inbox"), new File(path).getName());
                    return respond(new FileInputStream(f), path);
                }
            } catch (IOException e) {
                WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", null);
                r.setStatusCodeAndReasonPhrase(404, "Not Found");
                return r;
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) {
            }
            return true;
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            String[] types = params.getAcceptTypes();
            if (types != null && types.length == 1 && types[0] != null && types[0].startsWith("image/")) pick.setType("image/*");
            if (types != null && types.length == 1 && types[0] != null && types[0].startsWith("audio/")) pick.setType("audio/*");
            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
            try {
                startActivityForResult(Intent.createChooser(pick, "Choose file"), REQ_FILE);
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            boolean wantsMic = false;
            for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
            if (!wantsMic) {
                request.deny();
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingPermission = request;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage m) {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCallback != null) {
            Uri[] picked = null;
            if (result == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    picked = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) picked[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    picked = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(picked);
            fileCallback = null;
            return;
        }
        super.onActivityResult(code, result, data);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_MIC && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public String saveBegin(String name, String mime) {
            try {
                String id = "s" + (++counter);
                boolean video = mime.startsWith("video/");
                OutputStream os;
                String place;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    String dir = (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS) + "/Blockcut";
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, dir);
                    ContentResolver cr = getContentResolver();
                    Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri uri = cr.insert(base, v);
                    if (uri == null) return "";
                    os = cr.openOutputStream(uri);
                    saveUris.put(id, uri);
                    place = dir + "/" + name;
                } else {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        main.post(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE));
                    }
                    File dir = new File(Environment.getExternalStoragePublicDirectory(video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS), "Blockcut");
                    if (!dir.exists() && !dir.mkdirs()) dir = getExternalFilesDir(null);
                    File f = new File(dir, name);
                    os = new FileOutputStream(f);
                    saveUris.put(id, Uri.fromFile(f));
                    place = f.getAbsolutePath();
                }
                saves.put(id, os);
                savePlaces.put(id, place);
                lastSavedMime = mime;
                return id;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveChunk(String id, String b64) {
            OutputStream os = saves.get(id);
            if (os == null) return false;
            try {
                os.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String saveEnd(String id) {
            OutputStream os = saves.remove(id);
            if (os == null) return "";
            try {
                os.close();
            } catch (IOException ignored) {
            }
            Uri uri = saveUris.remove(id);
            lastSaved = uri;
            String place = savePlaces.remove(id);
            if (uri != null && "file".equals(uri.getScheme())) {
                MediaScannerConnection.scanFile(MainActivity.this, new String[]{uri.getPath()}, null, (p, u) -> lastSaved = u);
            }
            return place == null ? "" : place;
        }

        @JavascriptInterface
        public void shareLast() {
            if (lastSaved == null) return;
            main.post(() -> {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(lastSavedMime);
                    send.putExtra(Intent.EXTRA_STREAM, lastSaved);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Sharing is not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void copyText(String text) {
            main.post(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Blockcut", text));
            });
        }

        @JavascriptInterface
        public String pasteText() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipData c = cm.getPrimaryClip();
            if (c == null || c.getItemCount() == 0) return "";
            CharSequence t = c.getItemAt(0).coerceToText(MainActivity.this);
            return t == null ? "" : t.toString();
        }

        @JavascriptInterface
        public boolean ttsReady() {
            return ttsReady;
        }

        @JavascriptInterface
        public void speakToFile(String id, String text, float pitch, float rate) {
            if (!ttsReady) {
                done(id, "");
                return;
            }
            main.post(() -> {
                File dir = new File(getCacheDir(), "voice");
                dir.mkdirs();
                String safe = id.replaceAll("[^A-Za-z0-9_-]", "_");
                File out = new File(dir, safe + ".wav");
                tts.setPitch(Math.max(0.3f, Math.min(2.5f, pitch)));
                tts.setSpeechRate(Math.max(0.3f, Math.min(2.5f, rate)));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String u) {
                    }

                    @Override
                    public void onDone(String u) {
                        done(u, ORIGIN + "/voice/" + safe + ".wav");
                    }

                    @Override
                    public void onError(String u) {
                        done(u, "");
                    }
                });
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
                int r = tts.synthesizeToFile(text, params, out, id);
                if (r != TextToSpeech.SUCCESS) done(id, "");
            });
        }

        private void done(String id, String url) {
            String js = "window.__ttsDone && window.__ttsDone(" + JSONObject.quote(id) + "," + JSONObject.quote(url) + ")";
            main.post(() -> web.evaluateJavascript(js, null));
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            main.post(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void immersive(boolean on) {
            main.post(() -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController c = getWindow().getInsetsController();
                    if (c == null) return;
                    if (on) {
                        c.hide(WindowInsets.Type.systemBars());
                        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    } else {
                        c.show(WindowInsets.Type.systemBars());
                    }
                    return;
                }
                View d = getWindow().getDecorView();
                if (on) {
                    d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                } else {
                    d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                }
            });
        }

        @JavascriptInterface
        public void toast(String msg) {
            main.post(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }
}
