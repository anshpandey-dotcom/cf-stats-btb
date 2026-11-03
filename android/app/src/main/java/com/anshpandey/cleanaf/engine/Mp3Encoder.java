package com.anshpandey.cleanaf.engine;

import java.io.IOException;
import java.io.OutputStream;

import de.sciss.jump3r.mp3.BitStream;
import de.sciss.jump3r.mp3.GainAnalysis;
import de.sciss.jump3r.mp3.GetAudio;
import de.sciss.jump3r.mp3.ID3Tag;
import de.sciss.jump3r.mp3.Lame;
import de.sciss.jump3r.mp3.LameGlobalFlags;
import de.sciss.jump3r.mp3.MPEGMode;
import de.sciss.jump3r.mp3.Parse;
import de.sciss.jump3r.mp3.Presets;
import de.sciss.jump3r.mp3.Quantize;
import de.sciss.jump3r.mp3.QuantizePVT;
import de.sciss.jump3r.mp3.Reservoir;
import de.sciss.jump3r.mp3.Takehiro;
import de.sciss.jump3r.mp3.VBRTag;
import de.sciss.jump3r.mp3.VbrMode;
import de.sciss.jump3r.mp3.Version;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;

public final class Mp3Encoder implements AutoCloseable {
    private final Lame lame = new Lame();
    private final LameGlobalFlags flags;
    private final OutputStream out;
    private final byte[] buffer = new byte[1 << 17];
    private final int[] pcm = new int[4096];

    public Mp3Encoder(OutputStream out, int sampleRate, int kbps) throws IOException {
        this.out = out;
        GetAudio gaud = new GetAudio();
        GainAnalysis ga = new GainAnalysis();
        BitStream bs = new BitStream();
        Presets presets = new Presets();
        QuantizePVT qupvt = new QuantizePVT();
        Quantize qu = new Quantize();
        VBRTag vbr = new VBRTag();
        Version ver = new Version();
        ID3Tag id3 = new ID3Tag();
        Reservoir rv = new Reservoir();
        Takehiro tak = new Takehiro();
        Parse parse = new Parse();
        MPGLib mpg = new MPGLib();
        Interface intf = new Interface();
        Common common = new Common();
        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg);
        bs.setModules(ga, mpg, ver, vbr);
        id3.setModules(bs, ver);
        presets.setModules(lame);
        qu.setModules(bs, rv, qupvt, tak);
        qupvt.setModules(tak, rv, lame.enc.psy);
        rv.setModules(bs);
        tak.setModules(qupvt);
        vbr.setModules(lame, bs, ver);
        gaud.setModules(parse, mpg);
        parse.setModules(ver, id3, presets);
        mpg.setModules(intf, common);
        intf.setModules(vbr, common);
        flags = lame.lame_init();
        flags.num_channels = 1;
        flags.in_samplerate = sampleRate;
        flags.out_samplerate = sampleRate;
        flags.mode = MPEGMode.MONO;
        flags.VBR = VbrMode.vbr_off;
        flags.brate = kbps;
        flags.quality = 2;
        flags.bWriteVbrTag = false;
        flags.write_id3tag_automatic = false;
        flags.findReplayGain = false;
        if (lame.lame_init_params(flags) < 0) throw new IOException("MP3 encoder setup failed");
    }

    public void write(short[] samples, int n) throws IOException {
        int off = 0;
        while (off < n) {
            int m = Math.min(pcm.length, n - off);
            for (int i = 0; i < m; i++) pcm[i] = samples[off + i] << 16;
            int bytes = lame.lame_encode_buffer_int(flags, pcm, pcm, m, buffer, 0, buffer.length);
            if (bytes < 0) throw new IOException("MP3 encoding failed (" + bytes + ")");
            out.write(buffer, 0, bytes);
            off += m;
        }
    }

    @Override
    public void close() throws IOException {
        int bytes = lame.lame_encode_flush(flags, buffer, 0, buffer.length);
        if (bytes > 0) out.write(buffer, 0, bytes);
        lame.lame_close(flags);
        out.close();
    }
}
