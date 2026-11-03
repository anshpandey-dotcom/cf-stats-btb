package com.anshpandey.cleanaf.media;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import com.anshpandey.cleanaf.engine.DeepFilter;
import com.anshpandey.cleanaf.engine.Engine;
import com.anshpandey.cleanaf.engine.Resampler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioDecoder implements Engine.Source, AutoCloseable {
    private final MediaExtractor extractor;
    private final MediaCodec codec;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private Resampler resampler;
    private int channels;
    private int rate;
    private boolean floatPcm;
    private boolean inputDone;
    private boolean outputDone;
    private boolean flushed;
    private float[] mono = new float[8192];
    private float[] resampled = new float[16384];
    private float[] pending = new float[65536];
    private int pendingStart;
    private int pendingEnd;
    private long firstPtsUs = -1;

    public AudioDecoder(Context context, Uri uri) throws IOException {
        extractor = Probe.extractor(context, uri);
        int track = Probe.findTrack(extractor, "audio/");
        if (track < 0) {
            extractor.release();
            throw new IOException("This file has no audio track.");
        }
        extractor.selectTrack(track);
        MediaFormat format = extractor.getTrackFormat(track);
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();
    }

    public long firstPtsUs() {
        return Math.max(0, firstPtsUs);
    }

    @Override
    public int read(float[] buffer) throws IOException {
        while (pendingEnd - pendingStart < buffer.length && !flushed) decodeStep();
        int n = Math.min(buffer.length, pendingEnd - pendingStart);
        if (n == 0 && flushed) return -1;
        System.arraycopy(pending, pendingStart, buffer, 0, n);
        pendingStart += n;
        return n;
    }

    private void decodeStep() throws IOException {
        if (outputDone) {
            if (resampler != null) append(resampled, resampler.flush(ensureResampled(resampler.maxOutput(64))));
            flushed = true;
            return;
        }
        if (!inputDone) {
            int in = codec.dequeueInputBuffer(5000);
            if (in >= 0) {
                ByteBuffer buf = codec.getInputBuffer(in);
                int size = buf == null ? -1 : extractor.readSampleData(buf, 0);
                if (size < 0) {
                    codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                } else {
                    codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }
        }
        int out = codec.dequeueOutputBuffer(info, 5000);
        if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat f = codec.getOutputFormat();
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
            resampler = null;
        } else if (out >= 0) {
            ByteBuffer buf = codec.getOutputBuffer(out);
            if (buf != null && info.size > 0) {
                if (firstPtsUs < 0) firstPtsUs = info.presentationTimeUs;
                buf.position(info.offset);
                buf.limit(info.offset + info.size);
                convert(buf.slice().order(ByteOrder.nativeOrder()));
            }
            codec.releaseOutputBuffer(out, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
        }
    }

    private void convert(ByteBuffer data) {
        int bytes = floatPcm ? 4 : 2;
        int frames = data.remaining() / (bytes * Math.max(1, channels));
        if (mono.length < frames) mono = new float[frames];
        float k = 1f / Math.max(1, channels);
        for (int i = 0; i < frames; i++) {
            float s = 0;
            for (int c = 0; c < channels; c++) s += floatPcm ? data.getFloat() : data.getShort() / 32768f;
            mono[i] = s * k;
        }
        if (resampler == null) resampler = new Resampler(rate, DeepFilter.SAMPLE_RATE);
        int m = resampler.process(mono, frames, ensureResampled(resampler.maxOutput(frames)));
        append(resampled, m);
    }

    private float[] ensureResampled(int n) {
        if (resampled.length < n) resampled = new float[n];
        return resampled;
    }

    private void append(float[] src, int n) {
        if (n <= 0) return;
        if (pendingStart > 0 && pendingEnd + n > pending.length) {
            System.arraycopy(pending, pendingStart, pending, 0, pendingEnd - pendingStart);
            pendingEnd -= pendingStart;
            pendingStart = 0;
        }
        if (pendingEnd + n > pending.length) {
            float[] grown = new float[Math.max(pending.length * 2, pendingEnd + n)];
            System.arraycopy(pending, 0, grown, 0, pendingEnd);
            pending = grown;
        }
        System.arraycopy(src, 0, pending, pendingEnd, n);
        pendingEnd += n;
    }

    @Override
    public void close() {
        try {
            codec.stop();
        } catch (IllegalStateException ignored) {
        }
        codec.release();
        extractor.release();
    }
}
