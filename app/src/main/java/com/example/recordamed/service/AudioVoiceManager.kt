package com.example.recordamed.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

class AudioVoiceManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    fun startRecording(): Boolean {
        return try {
            val dir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Error al iniciar grabación: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            currentRecordingFile?.absolutePath
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Error al detener grabación: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            null
        }
    }

    fun playAudioPreview(path: String, onCompletion: () -> Unit = {}) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    onCompletion()
                    stopPlayback()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Error reproduciendo preview: ${e.message}", e)
        }
    }

    fun playBuiltInSoundPreview(soundId: String, onCompletion: () -> Unit = {}) {
        val soundPath = BuiltInSoundManager.getSoundFilePath(context, soundId)
        playAudioPreview(soundPath, onCompletion)
    }

    fun playAlarmSound(customAudioPath: String?, soundType: String = BuiltInSoundManager.SOUND_BELLS, onLoop: Boolean = true) {
        stopPlayback()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                if (!customAudioPath.isNullOrBlank() && File(customAudioPath).exists()) {
                    setDataSource(customAudioPath)
                } else {
                    val soundPath = BuiltInSoundManager.getSoundFilePath(context, soundType)
                    if (File(soundPath).exists()) {
                        setDataSource(soundPath)
                    } else {
                        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        setDataSource(context, defaultUri)
                    }
                }
                isLooping = onLoop
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Error reproduciendo alarma: ${e.message}", e)
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Error al detener reproductor: ${e.message}", e)
            mediaPlayer = null
        }
    }

    fun release() {
        stopPlayback()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
