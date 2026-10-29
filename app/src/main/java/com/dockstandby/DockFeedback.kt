package com.dockstandby

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.dockstandby.data.Prefs
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A subtle "you're docked" cue: one light haptic tap and a soft two-note chime.
 * Nothing plays in Do Not Disturb or at night (22:00–07:00); the chime also respects silent /
 * vibrate ringer modes.
 */
object DockFeedback {

    fun play(context: Context) {
        val s = Prefs.get(context).settings.value
        val nm = context.getSystemService(NotificationManager::class.java)
        val hour = LocalTime.now().hour
        if (hour >= 22 || hour < 7) return
        if (nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return

        val am = context.getSystemService(AudioManager::class.java)
        if (s.dockHaptic && am?.ringerMode != AudioManager.RINGER_MODE_SILENT) tap(context)
        if (s.dockChime && am?.ringerMode == AudioManager.RINGER_MODE_NORMAL) chime()
    }

    private fun tap(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        } ?: return
        if (Build.VERSION.SDK_INT >= 29) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        else vibrator.vibrate(VibrationEffect.createOneShot(20, 120))
    }

    /** Two soft sine notes (A5 then E6) with a bell-like decay, synthesised so no audio file ships. */
    private fun chime() {
        val rate = 44_100
        val notes = listOf(880.0 to 0.0, 1318.5 to 0.12) // frequency, start (s)
        val length = 0.9
        val samples = ShortArray((rate * length).toInt())
        for (i in samples.indices) {
            val t = i.toDouble() / rate
            var v = 0.0
            for ((f, start) in notes) {
                val dt = t - start
                if (dt >= 0) {
                    val attack = (dt / 0.008).coerceAtMost(1.0)
                    v += sin(2 * PI * f * dt) * exp(-dt * 5.5) * attack
                }
            }
            samples[i] = (v * 0.18 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size - 1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }
}
