package com.codeunlock.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.sin

/**
 * AmbientSoundManager — science-backed ambient soundscape engine inspired by Regain.
 *
 * Uses real-time procedural audio synthesis via Android AudioTrack:
 * - 0 KB download size
 * - 100% offline
 * - Seamless infinite generation with zero audio loop seams or clicks
 * - Low-latency and ultra-low CPU footprint
 */
enum class AmbientSoundType(val displayName: String, val emoji: String) {
    NONE("Off", "🔇"),
    BROWN_NOISE("Brown Noise", "🌊"),
    ALPHA_FLOW("Alpha Flow (10Hz)", "🧠"),
    WHITE_NOISE("White Noise", "💨"),
    RAIN_SOUND("Gentle Rain", "🌧️")
}

object AmbientSoundManager {

    private const val TAG = "CodeUnlock.Audio"
    private const val SAMPLE_RATE = 44100

    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private var synthesisThread: Thread? = null

    private val _currentSound = MutableStateFlow(AmbientSoundType.NONE)
    val currentSound: StateFlow<AmbientSoundType> = _currentSound.asStateFlow()

    private val _volume = MutableStateFlow(50) // 0 to 100
    val volume: StateFlow<Int> = _volume.asStateFlow()

    fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        _volume.value = clamped
        val gain = clamped / 100f
        audioTrack?.setVolume(gain)
    }

    @Synchronized
    fun startSound(type: AmbientSoundType, volumePercent: Int = _volume.value) {
        if (type == AmbientSoundType.NONE) {
            stopSound()
            return
        }

        stopSound()
        _currentSound.value = type
        setVolume(volumePercent)
        isPlaying = true

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.setVolume(_volume.value / 100f)
        track.play()

        synthesisThread = Thread {
            generateAudioStream(track, type, bufferSize)
        }.apply {
            name = "CodeUnlock-AudioSynth"
            priority = Thread.NORM_PRIORITY
            start()
        }

        Log.i(TAG, "Started ambient sound: ${type.displayName}")
    }

    @Synchronized
    fun stopSound() {
        isPlaying = false
        _currentSound.value = AmbientSoundType.NONE

        runCatching {
            synthesisThread?.interrupt()
            synthesisThread = null
        }

        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }

        Log.i(TAG, "Stopped ambient sound")
    }

    private fun generateAudioStream(track: AudioTrack, type: AmbientSoundType, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val random = Random()

        // Synthesis state variables
        var brownLast = 0.0
        var phase = 0.0
        val carrierFreq = 220.0 // 220 Hz gentle harmonic
        val modFreq = 10.0      // 10 Hz Alpha wave oscillation
        val twoPi = 2.0 * Math.PI
        var rainFilter = 0.0

        while (isPlaying) {
            for (i in buffer.indices) {
                val sample: Double = when (type) {
                    AmbientSoundType.BROWN_NOISE -> {
                        // Brown noise: integrated random walk with gentle decay
                        val white = (random.nextDouble() * 2.0 - 1.0)
                        brownLast = (brownLast + (0.04 * white)) / 1.02
                        brownLast.coerceIn(-1.0, 1.0) * 0.4
                    }
                    AmbientSoundType.ALPHA_FLOW -> {
                        // 10Hz amplitude-modulated gentle sine wave for alpha flow
                        phase += (carrierFreq / SAMPLE_RATE) * twoPi
                        if (phase > twoPi) phase -= twoPi
                        val carrier = sin(phase)
                        val mod = 0.6 + 0.4 * sin(phase * (modFreq / carrierFreq))
                        carrier * mod * 0.25
                    }
                    AmbientSoundType.WHITE_NOISE -> {
                        // Soft white noise
                        (random.nextDouble() * 2.0 - 1.0) * 0.15
                    }
                    AmbientSoundType.RAIN_SOUND -> {
                        // Rain: pink/brown noise with random droplet bursts
                        val white = random.nextDouble() * 2.0 - 1.0
                        rainFilter = 0.95 * rainFilter + 0.05 * white
                        val droplet = if (random.nextDouble() < 0.002) random.nextDouble() * 0.5 else 0.0
                        (rainFilter + droplet).coerceIn(-1.0, 1.0) * 0.35
                    }
                    AmbientSoundType.NONE -> 0.0
                }

                buffer[i] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }

            val written = track.write(buffer, 0, buffer.size)
            if (written <= 0) break
        }
    }
}
