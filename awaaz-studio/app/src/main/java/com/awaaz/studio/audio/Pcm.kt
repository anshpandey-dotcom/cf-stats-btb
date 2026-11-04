package com.awaaz.studio.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class Pcm(val samples: FloatArray, val rate: Int) {
    val seconds get() = samples.size.toFloat() / rate
}

class WavWriter(file: File, private val rate: Int) : AutoCloseable {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(44)) }
    private var bytes = 0L

    fun write(data: FloatArray) {
        val buf = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in data) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        raf.write(buf.array())
        bytes += data.size * 2L
    }

    fun silence(seconds: Float) {
        if (seconds > 0f) write(FloatArray((seconds * rate).toInt()))
    }

    override fun close() {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + bytes).toInt()).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(bytes.toInt())
        raf.seek(0)
        raf.write(h.array())
        raf.close()
    }
}

object Audio {

    fun writeWav(file: File, pcm: Pcm) = WavWriter(file, pcm.rate).use { it.write(pcm.samples) }

    fun read(context: Context, uri: Uri): Pcm {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        return decode(ex)
    }

    fun read(file: File): Pcm {
        if (file.extension.equals("wav", true)) readWav(file)?.let { return it }
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        return decode(ex)
    }

    private fun readWav(file: File): Pcm? {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (b.remaining() < 44 || String(b.array(), 0, 4) != "RIFF") return null
        b.position(12)
        var channels = 1
        var rate = 16000
        var bits = 16
        while (b.remaining() >= 8) {
            val id = String(ByteArray(4).also { b.get(it) })
            val size = b.int
            val start = b.position()
            when (id) {
                "fmt " -> {
                    if (b.short.toInt() != 1) return null
                    channels = b.short.toInt()
                    rate = b.int
                    b.int; b.short
                    bits = b.short.toInt()
                }
                "data" -> {
                    if (bits != 16) return null
                    val frames = minOf(size, b.remaining()) / (2 * channels)
                    val out = FloatArray(frames)
                    for (i in 0 until frames) {
                        var acc = 0f
                        repeat(channels) { acc += b.short / 32768f }
                        out[i] = acc / channels
                    }
                    return Pcm(out, rate)
                }
            }
            b.position(start + size + (size and 1))
        }
        return null
    }

    private fun decode(ex: MediaExtractor): Pcm {
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No audio found in this file")
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = FloatList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10000)
                if (i >= 0) {
                    val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.asShortBuffer()
                val frames = shorts.remaining() / channels
                for (f in 0 until frames) {
                    var acc = 0f
                    repeat(channels) { acc += shorts.get() / 32768f }
                    out.add(acc / channels)
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop()
        codec.release()
        ex.release()
        return Pcm(out.toArray(), rate)
    }

    fun resample(pcm: Pcm, rate: Int): Pcm {
        if (pcm.rate == rate) return pcm
        val ratio = pcm.rate.toDouble() / rate
        val n = (pcm.samples.size / ratio).toInt()
        val src = pcm.samples
        val out = FloatArray(n)
        for (i in 0 until n) {
            val x = i * ratio
            val j = x.toInt()
            val f = (x - j).toFloat()
            val a = src[minOf(j, src.size - 1)]
            val b = src[minOf(j + 1, src.size - 1)]
            out[i] = a + (b - a) * f
        }
        return Pcm(out, rate)
    }

    fun writeM4a(file: File, pcm: Pcm, bitrate: Int = 160_000) {
        val rate = if (pcm.rate in listOf(16000, 22050, 24000, 32000, 44100, 48000)) pcm.rate else 44100
        val src = resample(pcm, rate).samples
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buf.clear()
                    val count = minOf(buf.capacity() / 2, src.size - pos)
                    if (count <= 0) {
                        codec.queueInputBuffer(i, 0, 0, pos * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        for (k in 0 until count) buf.putShort((src[pos + k].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
                        codec.queueInputBuffer(i, 0, count * 2, pos * 1_000_000L / rate, 0)
                        pos += count
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && trackIndex >= 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(trackIndex, buf, info)
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }
}

class FloatList {
    private var data = FloatArray(1 shl 16)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun add(v: FloatArray) {
        while (size + v.size > data.size) data = data.copyOf(data.size * 2)
        v.copyInto(data, size)
        size += v.size
    }

    fun toArray() = data.copyOf(size)
}
