package com.anshpandey.cleanaf.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class AudioEncoder {
    public interface Progress {
        void update(double fraction) throws IOException;
    }

    public static final class Packets {
        public final File file;
        public final MediaFormat format;

        Packets(File file, MediaFormat format) {
            this.file = file;
            this.format = format;
        }
    }

    private AudioEncoder() {
    }

    public static Packets encode(File wav, File packets, boolean opus, Progress progress) throws IOException {
        int rate = 48000;
        String mime = opus ? MediaFormat.MIMETYPE_AUDIO_OPUS : MediaFormat.MIMETYPE_AUDIO_AAC;
        MediaFormat format = MediaFormat.createAudioFormat(mime, rate, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, opus ? 160000 : 256000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        if (!opus) format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaFormat outFormat = null;
        long total = Math.max(1, wav.length() - 44);
        long fed = 0;
        long frames = 0;
        boolean inputDone = false;
        boolean outputDone = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] chunk = new byte[8192];
        byte[] copy = new byte[0];
        try (InputStream in = new BufferedInputStream(new FileInputStream(wav), 1 << 16);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(packets), 1 << 16))) {
            skipFully(in, 44);
            while (!outputDone) {
                if (!inputDone) {
                    int idx = codec.dequeueInputBuffer(5000);
                    if (idx >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(idx);
                        int cap = Math.min(buf.capacity(), chunk.length) & ~1;
                        int n = readFully(in, chunk, cap);
                        long pts = frames * 1_000_000L / rate;
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            buf.clear();
                            buf.put(chunk, 0, n);
                            codec.queueInputBuffer(idx, 0, n, pts, 0);
                            frames += n / 2;
                            fed += n;
                            progress.update((double) fed / total);
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 5000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = codec.getOutputFormat();
                } else if (o >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(o);
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (buf != null && info.size > 0 && !config) {
                        if (copy.length < info.size) copy = new byte[info.size];
                        buf.position(info.offset);
                        buf.get(copy, 0, info.size);
                        out.writeInt(info.size);
                        out.writeLong(info.presentationTimeUs);
                        out.writeInt(info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        out.write(copy, 0, info.size);
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
        } finally {
            try {
                codec.stop();
            } catch (IllegalStateException ignored) {
            }
            codec.release();
        }
        if (outFormat == null) throw new IOException("Audio encoder produced no output.");
        return new Packets(packets, outFormat);
    }

    static final class Reader implements AutoCloseable {
        private final DataInputStream in;
        byte[] data = new byte[0];
        int size;
        long pts;
        int flags;

        Reader(File f) throws IOException {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16));
        }

        boolean next() throws IOException {
            try {
                size = in.readInt();
            } catch (EOFException e) {
                return false;
            }
            pts = in.readLong();
            flags = in.readInt();
            if (data.length < size) data = new byte[size];
            in.readFully(data, 0, size);
            return true;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static int readFully(InputStream in, byte[] b, int len) throws IOException {
        int got = 0;
        while (got < len) {
            int r = in.read(b, got, len - got);
            if (r < 0) break;
            got += r;
        }
        return got;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            n -= s;
        }
    }
}
