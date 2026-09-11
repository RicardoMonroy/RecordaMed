package com.example.recordamed.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object BuiltInSoundManager {

    const val SOUND_BELLS = "BELLS"
    const val SOUND_HARP = "HARP"
    const val SOUND_BOWL = "BOWL"

    data class SoundOption(
        val id: String,
        val title: String,
        val description: String,
        val icon: String
    )

    val SOUND_OPTIONS = listOf(
        SoundOption(
            id = SOUND_BELLS,
            title = "Campanas Suaves",
            description = "Tonos claros y relajantes (Recomendado para pastillas)",
            icon = "🔔"
        ),
        SoundOption(
            id = SOUND_HARP,
            title = "Arpa Serena",
            description = "Arpegio suave y fluido (Recomendado para gotas)",
            icon = "🌊"
        ),
        SoundOption(
            id = SOUND_BOWL,
            title = "Cuenco Tibetano",
            description = "Resonancia profunda y calmada (Recomendado para inyecciones)",
            icon = "🧘"
        )
    )

    fun getSoundFilePath(context: Context, soundId: String): String {
        val soundDir = File(context.filesDir, "builtin_sounds").apply { if (!exists()) mkdirs() }
        val soundFile = File(soundDir, "sound_${soundId.lowercase()}.wav")
        if (!soundFile.exists() || soundFile.length() < 1000) {
            generateWavFile(soundFile, soundId)
        }
        return soundFile.absolutePath
    }

    private fun generateWavFile(file: File, soundId: String) {
        val sampleRate = 44100
        val durationSeconds = when (soundId) {
            SOUND_HARP -> 4.5
            SOUND_BOWL -> 5.5
            else -> 4.0 // BELLS
        }
        val numSamples = (sampleRate * durationSeconds).toInt()
        val pcmData = ShortArray(numSamples)

        when (soundId) {
            SOUND_BELLS -> generateBellsAudio(pcmData, sampleRate)
            SOUND_HARP -> generateHarpAudio(pcmData, sampleRate)
            SOUND_BOWL -> generateBowlAudio(pcmData, sampleRate)
            else -> generateBellsAudio(pcmData, sampleRate)
        }

        writeWavFile(file, pcmData, sampleRate)
    }

    private fun generateBellsAudio(data: ShortArray, sampleRate: Int) {
        val chords = listOf(
            523.25 to 0.0,   // C5
            659.25 to 0.5,   // E5
            783.99 to 1.0,   // G5
            1046.50 to 1.5   // C6
        )
        for (i in data.indices) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for ((freq, startTime) in chords) {
                if (t >= startTime) {
                    val dt = t - startTime
                    val envelope = exp(-2.2 * dt) // decaimiento suave
                    val tone = sin(2 * PI * freq * dt) + 0.3 * sin(2 * PI * (freq * 2) * dt)
                    sample += tone * envelope * 0.35
                }
            }
            data[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun generateHarpAudio(data: ShortArray, sampleRate: Int) {
        val notes = listOf(
            261.63 to 0.0, // C4
            329.63 to 0.35, // E4
            392.00 to 0.70, // G4
            493.88 to 1.05, // B4
            523.25 to 1.40, // C5
            659.25 to 1.75  // E5
        )
        for (i in data.indices) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for ((freq, startTime) in notes) {
                if (t >= startTime) {
                    val dt = t - startTime
                    val envelope = exp(-1.8 * dt)
                    val tone = sin(2 * PI * freq * dt) + 0.25 * sin(2 * PI * freq * 1.5 * dt)
                    sample += tone * envelope * 0.28
                }
            }
            data[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun generateBowlAudio(data: ShortArray, sampleRate: Int) {
        val fundamental = 216.0 // Frecuencia de meditación relajante
        for (i in data.indices) {
            val t = i.toDouble() / sampleRate
            val tremolo = 1.0 + 0.15 * sin(2 * PI * 3.5 * t) // pulsación lenta
            val envelope = exp(-0.6 * t) // decaimiento muy largo y resonante
            val tone = sin(2 * PI * fundamental * t) +
                    0.4 * sin(2 * PI * (fundamental * 2.02) * t) +
                    0.2 * sin(2 * PI * (fundamental * 3.01) * t)
            val sample = tone * envelope * tremolo * 0.45
            data[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int) {
        try {
            val totalAudioLen = pcmData.size * 2
            val totalDataLen = totalAudioLen + 36
            val channels = 1
            val byteRate = sampleRate * channels * 2

            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray())
            buffer.putInt(totalDataLen)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16) // Subchunk1Size (16 for PCM)
            buffer.putShort(1) // AudioFormat (1 for PCM)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort((channels * 2).toShort()) // BlockAlign
            buffer.putShort(16) // BitsPerSample
            buffer.put("data".toByteArray())
            buffer.putInt(totalAudioLen)

            FileOutputStream(file).use { fos ->
                fos.write(header)
                val byteBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in pcmData) {
                    byteBuffer.putShort(s)
                }
                fos.write(byteBuffer.array())
            }
        } catch (e: Exception) {
            Log.e("BuiltInSoundManager", "Error escribiendo archivo WAV: ${e.message}", e)
        }
    }
}
