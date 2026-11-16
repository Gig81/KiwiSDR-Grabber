package com.grabber.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.grabber.app.AppLogger

/**
 * Regole di convivenza con il sistema audio durante l'ascolto:
 * - audio focus: silenzio durante chiamate e perdite temporanee, volume ridotto (ducking)
 *   quando un'altra app lo chiede, silenzio se un'altra app prende il focus in modo permanente;
 * - cuffie scollegate (`ACTION_AUDIO_BECOMING_NOISY`): mute, per non diffondere l'audio dall'altoparlante.
 */
class AudioSessionPolicy(
    private val context: Context,
    private val output: AudioTrackOutput,
    private val onHeadphonesDisconnected: () -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> output.setFocusGain(1f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> output.setFocusGain(DUCK_GAIN)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> output.setFocusGain(0f)
        }
        AppLogger.debug(TAG, "Audio focus cambiato: $change")
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AppLogger.info(TAG, "Cuffie scollegate: audio silenziato")
                onHeadphonesDisconnected()
            }
        }
    }

    @Synchronized
    fun activate() {
        if (focusRequest == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            output.setFocusGain(if (granted) 1f else 0f)
            if (!granted) AppLogger.warn(TAG, "Audio focus non concesso (chiamata in corso?)")
        }
        if (!noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            noisyReceiverRegistered = true
        }
    }

    @Synchronized
    fun deactivate() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        output.setFocusGain(1f)
        if (noisyReceiverRegistered) {
            context.unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
    }

    private companion object {
        const val TAG = "AudioSessionPolicy"
        const val DUCK_GAIN = 0.25f
    }
}
