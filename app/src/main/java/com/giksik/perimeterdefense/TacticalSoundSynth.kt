package com.giksik.perimeterdefense

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.giksik.perimeterdefense.domain.AmmoColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small procedural PCM synthesizer. Audio is presentation-only and never blocks
 * the Compose/game thread. Keep the individual effects short for mobile playback.
 */
internal object TacticalSoundSynth {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var soundEnabled: Boolean = true

    private fun play(samples: ShortArray) {
        if (!soundEnabled || samples.isEmpty()) return
        scope.launch {
            var track: AudioTrack? = null
            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(samples.size * 2, minBuffer))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                val written = track.write(samples, 0, samples.size)
                if (written > 0) {
                    track.play()
                    delay(samples.size * 1000L / SAMPLE_RATE + 40L)
                }
            } catch (_: Throwable) {
                // Unsupported audio devices must never interrupt gameplay.
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    fun playArmorClick() {
        val count = (SAMPLE_RATE * 0.04).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / count
            phase += 2.0 * PI * (1150.0 - 750.0 * t) / SAMPLE_RATE
            val wave = sin(phase) * 0.72 + (Random.nextDouble() - 0.5) * 0.28
            data[i] = (wave * exp(-t * 9.0) * 13500).toInt().toShort()
        }
        play(data)
    }

    fun playTurretFire(color: AmmoColor) {
        if (color == AmmoColor.NONE) return
        val duration = when (color) {
            AmmoColor.RED -> 0.20
            AmmoColor.BLUE -> 0.18
            AmmoColor.YELLOW -> 0.16
            AmmoColor.GREEN_HEAL -> 0.22
            AmmoColor.NONE -> return
        }
        val count = (SAMPLE_RATE * duration).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        var lowNoise = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / count
            val value = when (color) {
                AmmoColor.RED -> {
                    phase += 2.0 * PI * (340.0 * exp(-t * 2.2)) / SAMPLE_RATE
                    val raw = (Random.nextDouble() - 0.5) * 2.0
                    lowNoise = lowNoise * 0.78 + raw * 0.22
                    (sin(phase) * 0.65 + lowNoise * 0.55) * (1.0 - t) * (1.0 - t)
                }
                AmmoColor.BLUE -> {
                    val vibrato = sin(2.0 * PI * 55.0 * t) * 90.0
                    phase += 2.0 * PI * (1350.0 - 750.0 * t + vibrato) / SAMPLE_RATE
                    sin(phase) * (1.0 - t)
                }
                AmmoColor.YELLOW -> {
                    phase += 2.0 * PI * (620.0 + sin(t * 90.0) * 280.0) / SAMPLE_RATE
                    val square = if (sin(phase) >= 0.0) 0.6 else -0.6
                    (square + (Random.nextDouble() - 0.5) * 0.9) * exp(-t * 3.2)
                }
                AmmoColor.GREEN_HEAL -> {
                    val frequency = if (t < 0.45) 523.25 else 783.99
                    phase += 2.0 * PI * frequency / SAMPLE_RATE
                    sin(phase) * (1.0 - t * 0.7)
                }
                AmmoColor.NONE -> 0.0
            }
            val amplitude = when (color) {
                AmmoColor.RED -> 22500.0
                AmmoColor.BLUE -> 17500.0
                AmmoColor.YELLOW -> 18000.0
                AmmoColor.GREEN_HEAL -> 15000.0
                AmmoColor.NONE -> 0.0
            }
            data[i] = (value * amplitude).toInt().coerceIn(-32767, 32767).toShort()
        }
        play(data)
    }

    fun playMineCatapult() {
        val count = (SAMPLE_RATE * 0.34).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        var lowNoise = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / count
            val wave = if (t < 0.35) {
                phase += 2.0 * PI * (260.0 + 680.0 * (t / 0.35)) / SAMPLE_RATE
                sin(phase) * 0.58
            } else {
                val localT = (t - 0.35) / 0.65
                phase += 2.0 * PI * (130.0 * exp(-localT * 2.8)) / SAMPLE_RATE
                val raw = (Random.nextDouble() - 0.5) * 2.0
                lowNoise = lowNoise * 0.84 + raw * 0.16
                (sin(phase) * 0.75 + lowNoise * 0.65) * (1.0 - localT) * (1.0 - localT)
            }
            data[i] = (wave * 26000).toInt().coerceIn(-32767, 32767).toShort()
        }
        play(data)
    }

    fun playHeavyExplosion() {
        val count = (SAMPLE_RATE * 0.42).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        var lowNoise = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / count
            phase += 2.0 * PI * (110.0 * exp(-t * 3.0) + 28.0) / SAMPLE_RATE
            val raw = (Random.nextDouble() - 0.5) * 2.0
            lowNoise = lowNoise * 0.88 + raw * 0.12
            val sample = (sin(phase) * 0.8 + lowNoise * 0.7) * exp(-t * 4.2)
            data[i] = (sample * 28500).toInt().coerceIn(-32767, 32767).toShort()
        }
        play(data)
    }

    fun playErrorBuzz() {
        val count = (SAMPLE_RATE * 0.14).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / count
            val frequency = if (t < 0.5) 165.0 else 130.0
            phase += 2.0 * PI * frequency / SAMPLE_RATE
            val saw = (phase / PI) % 2.0 - 1.0
            data[i] = (saw * (1.0 - t) * 13000).toInt().toShort()
        }
        play(data)
    }
}
