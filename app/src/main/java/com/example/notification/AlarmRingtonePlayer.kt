package com.example.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Singleton player to manage alarm ringtone playback and vibration
 * across TaskAlarmReceiver, TaskActionReceiver, and ReminderAlarmActivity.
 */
object AlarmRingtonePlayer {
    private const val TAG = "AlarmRingtonePlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun startRingtone(context: Context, customUri: Uri? = null) {
        stopRingtone()

        val soundUri: Uri = customUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Started alarm ringtone audio loop.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone media player: ${e.message}", e)
        }

        // Start vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    @Synchronized
    fun stopRingtone() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}")
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibrator: ${e.message}")
        } finally {
            vibrator = null
        }
    }
}
