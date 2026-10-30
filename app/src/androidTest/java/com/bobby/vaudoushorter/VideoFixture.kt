package com.bobby.vaudoushorter

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File
import java.util.Arrays

/** Generate a small, moving local clip. No network or personal gallery is involved. */
internal fun createVideoFixture(directory: File): File {
    val file = File(directory, "vaudou-autoplay-${System.nanoTime()}.mp4")
    val width = 128
    val height = 192
    val frameCount = 48
    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 12)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var muxing = false
    var track = -1
    try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        val pixels = ByteArray(width * height * 3 / 2) { 128.toByte() }
        var input = 0
        var done = false
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!done) {
            check(SystemClock.elapsedRealtime() < deadline) { "Video fixture encoding timed out" }
            if (input <= frameCount) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val end = input == frameCount
                    if (!end) {
                        Arrays.fill(pixels, 0, width * height, (40 + input * 3).toByte())
                        codec.getInputBuffer(index)!!.apply { clear(); put(pixels) }
                    }
                    codec.queueInputBuffer(index, 0, if (end) 0 else pixels.size, input * 1_000_000L / 12,
                        if (end) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    input++
                }
            }
            when (val output = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }
                else -> if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(muxing)
                        muxer.writeSampleData(track, codec.getOutputBuffer(output)!!, info)
                    }
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
        }
    } finally {
        runCatching { codec.stop() }
        codec.release()
        if (muxing) muxer.stop()
        muxer.release()
    }
    return file
}
