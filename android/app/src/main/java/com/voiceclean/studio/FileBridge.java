package com.voiceclean.studio;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FileBridge {
    private static final String FOLDER = "VoiceClean";

    private final Activity activity;
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<Integer, Target> open = new HashMap<>();

    private static class Target {
        OutputStream stream;
        Uri uri;
        File file;
        String name;
    }

    public FileBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public synchronized int begin(String name, String mime) {
        try {
            Target t = new Target();
            t.name = sanitize(name);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, t.name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                ContentResolver resolver = activity.getContentResolver();
                t.uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (t.uri == null) return -1;
                t.stream = resolver.openOutputStream(t.uri);
            } else {
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return -1;
                if (!dir.exists() && !dir.mkdirs()) return -1;
                t.file = uniqueFile(dir, t.name);
                t.stream = new FileOutputStream(t.file);
            }
            if (t.stream == null) return -1;
            int id = counter.incrementAndGet();
            open.put(id, t);
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    @JavascriptInterface
    public synchronized boolean append(int id, String base64) {
        Target t = open.get(id);
        if (t == null) return false;
        try {
            t.stream.write(Base64.decode(base64, Base64.DEFAULT));
            return true;
        } catch (IOException e) {
            abort(id);
            return false;
        }
    }

    @JavascriptInterface
    public synchronized String finish(int id) {
        Target t = open.remove(id);
        if (t == null) return "";
        try {
            t.stream.close();
            if (t.uri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                activity.getContentResolver().update(t.uri, values, null, null);
            }
            String where = t.uri != null ? "Downloads/" + FOLDER + "/" + t.name : t.file.getAbsolutePath();
            activity.runOnUiThread(() -> Toast.makeText(activity, "Saved to " + where, Toast.LENGTH_LONG).show());
            return where;
        } catch (IOException e) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized void abort(int id) {
        Target t = open.remove(id);
        if (t == null) return;
        try {
            t.stream.close();
        } catch (IOException ignored) {
        }
        if (t.uri != null) activity.getContentResolver().delete(t.uri, null, null);
        if (t.file != null) t.file.delete();
    }

    private static String sanitize(String name) {
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() ? "voiceclean-export" : clean;
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int n = 1;
        while (f.exists()) f = new File(dir, stem + " (" + n++ + ")" + ext);
        return f;
    }
}
