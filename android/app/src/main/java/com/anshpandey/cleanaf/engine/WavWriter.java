package com.anshpandey.cleanaf.engine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Random;

public final class WavWriter implements AutoCloseable {
    private final File file;
    private final OutputStream out;
    private final int channels;
    private final byte[] buf = new byte[8192];
    private final Random random = new Random(7);
    private long dataBytes;

    public WavWriter(File file, int sampleRate, int channels) throws IOException {
        this.file = file;
        this.channels = channels;
        out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
        out.write(header(sampleRate, channels, 0));
    }

    public void write(float[] x, int n) throws IOException {
        int pos = 0;
        for (int i = 0; i < n; i++) {
            float dither = (random.nextFloat() - random.nextFloat()) / 32768f;
            float v = Math.max(-1f, Math.min(1f, x[i] + dither));
            int q = Math.round(v * 32767f);
            for (int c = 0; c < channels; c++) {
                buf[pos++] = (byte) q;
                buf[pos++] = (byte) (q >> 8);
                if (pos == buf.length) {
                    out.write(buf, 0, pos);
                    dataBytes += pos;
                    pos = 0;
                }
            }
        }
        out.write(buf, 0, pos);
        dataBytes += pos;
    }

    @Override
    public void close() throws IOException {
        out.close();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(4);
            raf.write(le32(36 + dataBytes));
            raf.seek(40);
            raf.write(le32(dataBytes));
        }
    }

    static byte[] header(int sampleRate, int channels, long dataBytes) {
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        System.arraycopy(le32(36 + dataBytes), 0, h, 4, 4);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        System.arraycopy(le32(16), 0, h, 16, 4);
        h[20] = 1;
        h[22] = (byte) channels;
        System.arraycopy(le32(sampleRate), 0, h, 24, 4);
        System.arraycopy(le32((long) sampleRate * channels * 2), 0, h, 28, 4);
        h[32] = (byte) (channels * 2);
        h[34] = 16;
        put(h, 36, "data");
        System.arraycopy(le32(dataBytes), 0, h, 40, 4);
        return h;
    }

    private static void put(byte[] h, int off, String s) {
        for (int i = 0; i < 4; i++) h[off + i] = (byte) s.charAt(i);
    }

    private static byte[] le32(long v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }
}
