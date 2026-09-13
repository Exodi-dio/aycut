package com.exodi.aycut.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import java.io.File

/**
 * Decodes the video track of one imported file with MediaCodec straight to an
 * output [Surface] (from a SurfaceTexture owned by the GL thread). Plays a
 * window of the file: starts at a seeked source position and stops feeding
 * once past the clip's source end.
 *
 * Android-only, self-contained, and rebuilt on every seek so scrubbing is
 * always sync-boundary accurate at the cost of a codec restart.
 */
class ClipDecoder(
    private val file: File,
    private val mime: String,
) {
    private var extractor: MediaExtractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private var endSourceUs: Long = Long.MAX_VALUE
    private var eosQueued = false
    private var eosSeen = false

    fun start(sourceStartUs: Long, sourceEndUs: Long, surface: Surface) {
        releaseIfNeeded()
        extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                track = i
                break
            }
        }
        require(track >= 0) { "$file has no video track" }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        endSourceUs = sourceEndUs
        eosQueued = false
        eosSeen = false

        val decoder = MediaCodec.createDecoderByType(mime)
        codec = decoder
        decoder.configure(format, surface, null, 0)
        extractor.seekTo(sourceStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        decoder.start()
    }

    /**
     * Feeds decodable input and renders ready output. States: false when the
     * decoder has delivered end-of-stream (clip preview is finished).
     */
    fun pulse(): Boolean {
        val codec = codec ?: return false
        if (!eosQueued) pumpInput(codec)
        if (eosQueued && eosSeen) return false
        pumpOutput(codec)
        return !eosSeen
    }

    fun stop() {
        releaseIfNeeded()
    }

    private fun pumpInput(codec: MediaCodec) {
        while (true) {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex < 0) break
            if (extractor.sampleTime < 0 || extractor.sampleTime >= endSourceUs) {
                codec.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                eosQueued = true
                break
            }
            val buffer = codec.getInputBuffer(inIndex) ?: break
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                eosQueued = true
                break
            }
            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun pumpOutput(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    val render = info.size > 0
                    codec.releaseOutputBuffer(outIndex, render)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosSeen = true
                        return
                    }
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
            }
        }
    }

    private fun releaseIfNeeded() {
        codec?.stop()
        codec?.release()
        codec = null
        try {
            extractor.release()
        } catch (_: Exception) {
            // extractor already released
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L

        /** True when the given file has a decodable video track. */
        fun videoMimeOf(file: File): String? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("video/") == true) return mime
                }
                null
            } finally {
                extractor.release()
            }
        }
    }
}