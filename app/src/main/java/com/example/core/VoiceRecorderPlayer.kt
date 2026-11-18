package com.example.core

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): File {
        stopRecordingCleanup()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(16000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        startTimeMs = System.currentTimeMillis()
        return file
    }

    fun stopRecording(): Pair<File?, Long> {
        val file = outputFile
        val durationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(500L)
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Short recording might throw RuntimeException on stop
        }
        stopRecordingCleanup()
        return if (file != null && file.exists() && file.length() > 0L) {
            file to durationMs
        } else {
            null to 0L
        }
    }

    private fun stopRecordingCleanup() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}

class VoicePlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null

    fun playOrToggle(
        sourceUrl: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (currentUrl == sourceUrl && mediaPlayer?.isPlaying == true) {
            stop()
            return false
        }
        stop()
        return try {
            val player = MediaPlayer().apply {
                setDataSource(sourceUrl)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    this@VoicePlayerManager.stop()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    this@VoicePlayerManager.stop()
                    onError("Playback error ($what/$extra)")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            currentUrl = sourceUrl
            true
        } catch (e: Exception) {
            stop()
            onError(e.localizedMessage ?: "Unable to play audio")
            false
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        currentUrl = null
    }
}
