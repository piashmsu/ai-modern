package com.piash.priya.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.piash.priya.util.DebugLog
import java.io.ByteArrayOutputStream

/**
 * Push-to-talk PCM recorder.
 *
 * Captures 16-bit mono 16 kHz PCM into memory while [isRecording] is true,
 * then exposes the buffer as a WAV byte array via [stopAndWav]. Sized for
 * short utterances (≤ 30 s) — anything longer should stream to disk.
 */
class AudioRecorderPcm(private val context: Context) {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var capturing = false
    private val output = ByteArrayOutputStream()
    private var recorderThread: Thread? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val isRecording: Boolean get() = capturing

    @Synchronized
    fun start(): Boolean {
        if (capturing) return true
        if (!hasPermission()) {
            DebugLog.e("AudioRec", "RECORD_AUDIO not granted")
            return false
        }
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(4096)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, channelConfig, audioFormat,
                minBuf * 2,
            )
        } catch (t: Throwable) {
            DebugLog.e("AudioRec", "AudioRecord ctor failed", t)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            DebugLog.e("AudioRec", "AudioRecord not initialized")
            try { rec.release() } catch (_: Throwable) {}
            return false
        }
        recorder = rec
        output.reset()
        capturing = true
        rec.startRecording()
        recorderThread = Thread({
            val buf = ByteArray(minBuf)
            while (capturing) {
                val n = try { rec.read(buf, 0, buf.size) } catch (_: Throwable) { break }
                if (n > 0) output.write(buf, 0, n)
                if (n < 0) break
            }
        }, "AudioRecorderPcm").also { it.start() }
        DebugLog.i("AudioRec", "start sampleRate=$sampleRate buf=$minBuf")
        return true
    }

    @Synchronized
    fun stopAndWav(): ByteArray {
        if (!capturing) return ByteArray(0)
        capturing = false
        try { recorderThread?.join(800) } catch (_: InterruptedException) {}
        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        recorderThread = null
        val pcm = output.toByteArray()
        DebugLog.i("AudioRec", "stop pcmBytes=${pcm.size}")
        return wrapAsWav(pcm, sampleRate, channels = 1, bitsPerSample = 16)
    }

    @Synchronized
    fun cancel() {
        capturing = false
        try { recorderThread?.join(400) } catch (_: InterruptedException) {}
        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        recorderThread = null
        output.reset()
    }

    private fun wrapAsWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcm.size + 36
        val out = ByteArrayOutputStream(pcm.size + 44)
        // RIFF header
        out.write("RIFF".toByteArray())
        out.writeInt32LE(totalDataLen)
        out.write("WAVE".toByteArray())
        // fmt chunk
        out.write("fmt ".toByteArray())
        out.writeInt32LE(16) // PCM chunk size
        out.writeInt16LE(1)  // audio format = PCM
        out.writeInt16LE(channels)
        out.writeInt32LE(sampleRate)
        out.writeInt32LE(byteRate)
        out.writeInt16LE(blockAlign)
        out.writeInt16LE(bitsPerSample)
        // data chunk
        out.write("data".toByteArray())
        out.writeInt32LE(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt32LE(v: Int) {
        write(v and 0xff)
        write((v shr 8) and 0xff)
        write((v shr 16) and 0xff)
        write((v shr 24) and 0xff)
    }

    private fun ByteArrayOutputStream.writeInt16LE(v: Int) {
        write(v and 0xff)
        write((v shr 8) and 0xff)
    }
}
