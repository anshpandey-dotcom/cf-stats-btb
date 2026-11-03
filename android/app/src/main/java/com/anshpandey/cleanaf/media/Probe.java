package com.anshpandey.cleanaf.media;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;

public final class Probe {
    public final long durationUs;
    public final boolean hasVideo;
    public final String videoMime;
    public final int rotation;
    public final String name;

    private Probe(long durationUs, boolean hasVideo, String videoMime, int rotation, String name) {
        this.durationUs = durationUs;
        this.hasVideo = hasVideo;
        this.videoMime = videoMime;
        this.rotation = rotation;
        this.name = name;
    }

    public static Probe of(Context context, Uri uri) throws IOException {
        MediaExtractor ex = extractor(context, uri);
        try {
            int audio = findTrack(ex, "audio/");
            if (audio < 0) throw new IOException("This file has no audio track.");
            long duration = 0;
            MediaFormat af = ex.getTrackFormat(audio);
            if (af.containsKey(MediaFormat.KEY_DURATION)) duration = af.getLong(MediaFormat.KEY_DURATION);
            int video = findTrack(ex, "video/");
            String vmime = null;
            int rotation = 0;
            if (video >= 0) {
                MediaFormat vf = ex.getTrackFormat(video);
                vmime = vf.getString(MediaFormat.KEY_MIME);
                if (vf.containsKey(MediaFormat.KEY_ROTATION)) rotation = vf.getInteger(MediaFormat.KEY_ROTATION);
                if (vf.containsKey(MediaFormat.KEY_DURATION)) duration = Math.max(duration, vf.getLong(MediaFormat.KEY_DURATION));
            }
            return new Probe(duration, video >= 0, vmime, rotation, displayName(context, uri));
        } finally {
            ex.release();
        }
    }

    static MediaExtractor extractor(Context context, Uri uri) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        try {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) ex.setDataSource(uri.getPath());
            else ex.setDataSource(context, uri, null);
        } catch (IOException | RuntimeException e) {
            ex.release();
            throw new IOException("This file format is not supported.", e);
        }
        return ex;
    }

    static int findTrack(MediaExtractor ex, String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    public static String displayName(Context context, Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) name = c.getString(0);
            } catch (RuntimeException ignored) {
            }
        }
        if (name == null) name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) name = "audio";
        return name;
    }

    public boolean mp4Compatible() {
        if (videoMime == null) return false;
        switch (videoMime) {
            case MediaFormat.MIMETYPE_VIDEO_AVC:
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
            case MediaFormat.MIMETYPE_VIDEO_MPEG4:
            case MediaFormat.MIMETYPE_VIDEO_H263:
            case "video/av01":
                return true;
            default:
                return false;
        }
    }
}
