package com.github.mytv.myearthquakealert.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Sound, vibration and speech of an alert (design §4 声音与振动):
 * - REMIND: the system notification sound once and one short vibration, following the
 *   ringer mode and Do Not Disturb.
 * - STRONG: a looping alarm on the alarm stream ([AudioAttributes.USAGE_ALARM]), a
 *   500/250 ms vibration loop with alarm attributes, spoken countdown, and one beep per
 *   second near the end when speech is unavailable. Whether this may sound during Do Not
 *   Disturb is decided by the caller ([dndActive]).
 *
 * [stopAll] silences everything at once (静音 / 我已安全). Main thread only.
 */
internal class AlertEffects(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var alarmTone: ToneGenerator? = null
    private var beeper: ToneGenerator? = null
    private var vibrating = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: (() -> String)? = null
    private var alarmVolume = 1f

    /** Do Not Disturb is on (any filter other than "all"). */
    fun dndActive(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /**
     * REMIND: notification sound once plus a short vibration, as the ringer mode allows.
     * [withSound] is false when the notification channel already plays the sound.
     */
    fun chime(withSound: Boolean) {
        if (dndActive()) return
        val audio = context.getSystemService(AudioManager::class.java)
        val mode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        if (withSound && mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            } catch (e: Exception) {
                Log.w(TAG, "chime failed: ${e.message}")
            }
        }
        vibrate(longArrayOf(0, 300), repeat = -1, usage = AudioAttributes.USAGE_NOTIFICATION_EVENT)
    }

    /** STRONG: starts the looping alarm sound and vibration. */
    fun startAlarm() {
        if (player == null && alarmTone == null) {
            player = createAlarmPlayer()
            if (player == null) {
                // No playable alarm ringtone (e.g. media storage not unlocked yet): synthesized tone.
                alarmTone = try {
                    ToneGenerator(AudioManager.STREAM_ALARM, 100).apply { startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK) }
                } catch (e: Exception) {
                    Log.w(TAG, "alarm tone unavailable: ${e.message}")
                    null
                }
            }
        }
        if (!vibrating) {
            vibrate(longArrayOf(0, 500, 250), repeat = 0, usage = AudioAttributes.USAGE_ALARM)
            vibrating = true
        }
    }

    /** Stops the alarm sound and vibration (speech continues). */
    fun stopAlarm() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        alarmTone?.let {
            it.stopTone()
            it.release()
        }
        alarmTone = null
        stopVibration()
    }

    val alarmPlaying: Boolean get() = player != null || alarmTone != null

    /** Lowers the alarm while speech is playing. */
    fun duck(ducked: Boolean) {
        alarmVolume = if (ducked) 0.25f else 1f
        try {
            player?.setVolume(alarmVolume, alarmVolume)
        } catch (_: Exception) {
        }
    }

    /** One short beep on the alarm stream (last 10 s without speech). */
    fun beep() {
        try {
            val tone = beeper ?: ToneGenerator(AudioManager.STREAM_ALARM, 90).also { beeper = it }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.w(TAG, "beep failed: ${e.message}")
        }
    }

    /**
     * Speaks [text] (evaluated when the engine is ready, so a countdown is never stale).
     * [flush] drops what is still queued.
     */
    fun speak(flush: Boolean = true, text: () -> String) {
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            tts = TextToSpeech(context.applicationContext) { status ->
                main.post { onTtsInit(status) }
            }
            return
        }
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        doSpeak(engine, text(), flush)
    }

    val speechAvailable: Boolean get() = ttsReady

    fun stopSpeech() {
        pendingSpeech = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        duck(false)
    }

    /** Silences sound, vibration and speech immediately. */
    fun stopAll() {
        stopSpeech()
        stopAlarm()
        beeper?.release()
        beeper = null
    }

    fun release() {
        stopAll()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
    }

    private fun onTtsInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS unavailable ($status)")
            ttsReady = false
            pendingSpeech = null
            return
        }
        val result = try {
            engine.setLanguage(Locale.getDefault())
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language ${Locale.getDefault()} unsupported")
            ttsReady = false
            pendingSpeech = null
            return
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { duck(true) }
            }

            override fun onDone(utteranceId: String?) {
                main.post { duck(false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { duck(false) }
            }
        })
        ttsReady = true
        pendingSpeech?.let { doSpeak(engine, it(), true) }
        pendingSpeech = null
    }

    private fun doSpeak(engine: TextToSpeech, text: String, flush: Boolean) {
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "eew-${System.nanoTime()}")
    }

    private fun createAlarmPlayer(): MediaPlayer? {
        val candidates = listOfNotNull(
            safeUri { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) },
            safeUri { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) },
            safeUri { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) },
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(context, uri)
                mp.isLooping = true
                mp.prepare()
                mp.setVolume(alarmVolume, alarmVolume)
                mp.start()
                return mp
            } catch (e: Exception) {
                Log.w(TAG, "alarm sound $uri failed: ${e.message}")
                mp.release()
            }
        }
        return null
    }

    private inline fun safeUri(block: () -> android.net.Uri?): android.net.Uri? = try {
        block()
    } catch (e: Exception) {
        null
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray, repeat: Int, usage: Int) {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val attributes = AudioAttributes.Builder().setUsage(usage).build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat), attributes)
            } else {
                vibrator.vibrate(pattern, repeat, attributes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "vibrate failed: ${e.message}")
        }
    }

    private fun stopVibration() {
        if (!vibrating) return
        vibrating = false
        try {
            vibrator()?.cancel()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "AlertEffects"
    }
}
