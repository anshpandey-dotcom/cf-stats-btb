package com.anshpandey.cleanaf.media;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class Muxer {
    private Muxer() {
    }

    public static void audioOnly(AudioEncoder.Packets packets, File out) throws IOException {
        MediaMuxer muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try (AudioEncoder.Reader reader = new AudioEncoder.Reader(packets.file)) {
            int track = muxer.addTrack(packets.format);
            muxer.start();
            ByteBuffer buf = ByteBuffer.allocate(1 << 16);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (reader.next()) {
                buf = put(buf, reader.data, reader.size);
                info.set(0, reader.size, reader.pts, reader.flags);
                muxer.writeSampleData(track, buf, info);
            }
            muxer.stop();
        } finally {
            muxer.release();
        }
    }

    public static void video(Context context, Uri source, AudioEncoder.Packets packets, File out, boolean webm, int rotation,
                             long audioOffsetUs, long durationUs, AudioEncoder.Progress progress) throws IOException {
        MediaExtractor ex = Probe.extractor(context, source);
        MediaMuxer muxer = new MediaMuxer(out.getPath(), webm ? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try (AudioEncoder.Reader reader = new AudioEncoder.Reader(packets.file)) {
            int vTrack = Probe.findTrack(ex, "video/");
            if (vTrack < 0) throw new IOException("No video track found.");
            ex.selectTrack(vTrack);
            MediaFormat vf = ex.getTrackFormat(vTrack);
            int outVideo = muxer.addTrack(vf);
            int outAudio = muxer.addTrack(packets.format);
            if (!webm && rotation != 0) muxer.setOrientationHint(rotation);
            muxer.start();
            int cap = vf.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ? vf.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 0;
            ByteBuffer vbuf = ByteBuffer.allocateDirect(Math.max(cap, 8 << 20));
            ByteBuffer abuf = ByteBuffer.allocate(1 << 16);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean haveAudio = reader.next();
            int vSize = ex.readSampleData(vbuf, 0);
            while (vSize >= 0 || haveAudio) {
                long vPts = vSize >= 0 ? ex.getSampleTime() : Long.MAX_VALUE;
                long aPts = haveAudio ? reader.pts + audioOffsetUs : Long.MAX_VALUE;
                if (vPts <= aPts) {
                    int flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    info.set(0, vSize, vPts, flags);
                    muxer.writeSampleData(outVideo, vbuf, info);
                    ex.advance();
                    vSize = ex.readSampleData(vbuf, 0);
                    if (durationUs > 0) progress.update(Math.min(1, (double) vPts / durationUs));
                } else {
                    abuf = put(abuf, reader.data, reader.size);
                    info.set(0, reader.size, aPts, reader.flags);
                    muxer.writeSampleData(outAudio, abuf, info);
                    haveAudio = reader.next();
                }
            }
            muxer.stop();
        } finally {
            muxer.release();
            ex.release();
        }
    }

    private static ByteBuffer put(ByteBuffer buf, byte[] data, int size) {
        if (buf.capacity() < size) buf = ByteBuffer.allocate(size * 2);
        buf.clear();
        buf.put(data, 0, size);
        buf.flip();
        return buf;
    }
}
