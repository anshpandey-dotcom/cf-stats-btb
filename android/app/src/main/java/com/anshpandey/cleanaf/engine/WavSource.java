package com.anshpandey.cleanaf.engine;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class WavSource implements Engine.Source, AutoCloseable {
    private final InputStream in;
    private final byte[] raw = new byte[1 << 15];

    public WavSource(File wav) throws IOException {
        in = new BufferedInputStream(new FileInputStream(wav), 1 << 16);
        long skip = 44;
        while (skip > 0) {
            long s = in.skip(skip);
            if (s <= 0) throw new IOException("Invalid audio file.");
            skip -= s;
        }
    }

    @Override
    public int read(float[] buffer) throws IOException {
        int want = Math.min(buffer.length * 2, raw.length);
        int got = 0;
        while (got < want) {
            int r = in.read(raw, got, want - got);
            if (r < 0) break;
            got += r;
        }
        int n = got / 2;
        if (n == 0) return -1;
        for (int i = 0; i < n; i++) buffer[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8)) / 32768f;
        return n;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
