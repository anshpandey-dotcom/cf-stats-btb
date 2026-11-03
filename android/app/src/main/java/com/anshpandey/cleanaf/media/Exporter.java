package com.anshpandey.cleanaf.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.anshpandey.cleanaf.engine.Mp3Encoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Exporter {
    public enum Format {
        MP4("mp4", "video/mp4", true),
        WEBM("webm", "video/webm", true),
        MP3("mp3", "audio/mpeg", false),
        M4A("m4a", "audio/mp4", false),
        WAV("wav", "audio/wav", false);

        public final String ext;
        public final String mime;
        public final boolean video;

        Format(String ext, String mime, boolean video) {
            this.ext = ext;
            this.mime = mime;
            this.video = video;
        }
    }

    public static final class Saved {
        public final Uri uri;
        public final String location;

        Saved(Uri uri, String location) {
            this.uri = uri;
            this.location = location;
        }
    }

    private static final String FOLDER = "CleanAF";

    private Exporter() {
    }

    public static Saved export(Context context, Uri source, File masterWav, Format format, String name, long audioOffsetUs,
                               long durationUs, int rotation, AudioEncoder.Progress progress) throws IOException {
        File dir = new File(context.getCacheDir(), "export");
        deleteTree(dir);
        if (!dir.mkdirs()) throw new IOException("Could not prepare export folder.");
        File out = new File(dir, "out." + format.ext);
        switch (format) {
            case WAV:
                copy(masterWav, out, progress);
                break;
            case MP3:
                mp3(masterWav, out, progress);
                break;
            case M4A: {
                AudioEncoder.Packets p = AudioEncoder.encode(masterWav, new File(dir, "a.pkt"), false, f -> progress.update(f * 0.95));
                Muxer.audioOnly(p, out);
                break;
            }
            case MP4:
            case WEBM: {
                boolean webm = format == Format.WEBM;
                AudioEncoder.Packets p = AudioEncoder.encode(masterWav, new File(dir, "a.pkt"), webm, f -> progress.update(f * 0.5));
                Muxer.video(context, source, p, out, webm, rotation, audioOffsetUs, durationUs, f -> progress.update(0.5 + f * 0.45));
                break;
            }
        }
        Saved saved = publish(context, out, name + "." + format.ext, format);
        deleteTree(dir);
        return saved;
    }

    private static void mp3(File wav, File out, AudioEncoder.Progress progress) throws IOException {
        long total = Math.max(1, wav.length() - 44);
        long done = 0;
        byte[] raw = new byte[48000 * 2];
        short[] pcm = new short[48000];
        try (InputStream in = new BufferedInputStream(new FileInputStream(wav), 1 << 16);
             Mp3Encoder enc = new Mp3Encoder(new BufferedOutputStream(new FileOutputStream(out), 1 << 16), 48000, 320)) {
            if (in.skip(44) != 44) throw new IOException("Invalid audio file.");
            int n;
            while ((n = readFully(in, raw)) > 0) {
                int s = n / 2;
                for (int i = 0; i < s; i++) pcm[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8));
                enc.write(pcm, s);
                done += n;
                progress.update(0.95 * done / total);
            }
        }
    }

    private static Saved publish(Context context, File file, String displayName, Format format) throws IOException {
        String base = format.video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = format.video
                    ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, format.mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, base + "/" + FOLDER);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(collection, values);
            if (uri == null) throw new IOException("Could not create the file.");
            try (OutputStream os = resolver.openOutputStream(uri); InputStream is = new FileInputStream(file)) {
                if (os == null) throw new IOException("Could not write the file.");
                pipe(is, os);
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            String shown = displayName;
            try (android.database.Cursor c = resolver.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) shown = c.getString(0);
            }
            return new Saved(uri, base + "/" + FOLDER + "/" + shown);
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(base), FOLDER);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
        File target = unique(dir, displayName);
        try (InputStream is = new FileInputStream(file); OutputStream os = new FileOutputStream(target)) {
            pipe(is, os);
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getPath()}, new String[]{format.mime}, null);
        return new Saved(Uri.fromFile(target), base + "/" + FOLDER + "/" + target.getName());
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; f.exists(); i++) f = new File(dir, stem + " (" + i + ")" + ext);
        return f;
    }

    private static void copy(File src, File dst, AudioEncoder.Progress progress) throws IOException {
        long total = Math.max(1, src.length());
        long done = 0;
        byte[] buf = new byte[1 << 16];
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                progress.update(0.9 * done / total);
            }
        }
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static int readFully(InputStream in, byte[] b) throws IOException {
        int got = 0;
        while (got < b.length) {
            int r = in.read(b, got, b.length - got);
            if (r < 0) break;
            got += r;
        }
        return got & ~1;
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    public static String sanitize(String name) {
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (clean.startsWith(".")) clean = clean.substring(1);
        return clean.isEmpty() ? "clean" : clean;
    }
}
