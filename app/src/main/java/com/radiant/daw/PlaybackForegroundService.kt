package com.radiant.daw

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackForegroundService : Service() {
    companion object {
        private const val channelId = "radiant-playback"
        private const val notificationId = 7301
        private const val actionStop = "com.radiant.daw.action.STOP_PLAYBACK"

        @Volatile
        var active: Boolean = false
            private set

        @Volatile
        var microphoneActive: Boolean = false
            private set

        @Volatile
        private var cleanSessionWhenStopped = false

        fun deferCleanSessionUntilAudioStops() {
            cleanSessionWhenStopped = true
        }

        fun cancelDeferredCleanSession() {
            cleanSessionWhenStopped = false
        }
    }

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var foregroundTypes = -1
    private val serviceHandler = Handler(Looper.getMainLooper())
    private val playbackMonitor = object : Runnable {
        override fun run() {
            val playing = NativeBridge.isTransportPlaying()
            val recording = NativeBridge.isAudioRecordingArmed()
            if (!playing && !recording) {
                stopSelf()
            } else {
                if (!playing) abandonAudioFocus()
                else if (focusRequest == null && !requestAudioFocus()) NativeBridge.stopPlayback()
                updateForegroundState()
                serviceHandler.postDelayed(this, 250)
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                NativeBridge.stopPlayback()
                abandonAudioFocus()
                if (NativeBridge.isAudioRecordingArmed()) updateForegroundState() else stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == actionStop) {
            NativeBridge.stopPlayback()
            NativeBridge.setAudioRecordingArmed(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!NativeBridge.isTransportPlaying() && !NativeBridge.isAudioRecordingArmed()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!updateForegroundState(force = true)) return START_NOT_STICKY

        if (NativeBridge.isTransportPlaying() && !requestAudioFocus()) {
            NativeBridge.stopPlayback()
            abandonAudioFocus()
            if (NativeBridge.isAudioRecordingArmed()) updateForegroundState() else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        active = true
        serviceHandler.removeCallbacks(playbackMonitor)
        serviceHandler.postDelayed(playbackMonitor, 250)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active = false
        microphoneActive = false
        foregroundTypes = -1
        serviceHandler.removeCallbacks(playbackMonitor)
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (cleanSessionWhenStopped) {
            cleanSessionWhenStopped = false
            NativeBridge.endAndroidSession()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestAudioFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
            .build()
            .also { focusRequest = it }

        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun updateForegroundState(force: Boolean = false): Boolean {
        val playing = NativeBridge.isTransportPlaying()
        val recording = NativeBridge.isAudioRecordingArmed()
        if (!playing && !recording) return false

        val types = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                (if (playing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0) or
                    (if (recording) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && playing -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        val notification = buildNotification(playing, recording)

        try {
            if (force || types != foregroundTypes) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types != 0)
                    startForeground(notificationId, notification, types)
                else
                    startForeground(notificationId, notification)
                foregroundTypes = types
            } else {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(notificationId, notification)
            }
        } catch (error: SecurityException) {
            android.util.Log.e("RadiantAudio", "Android rejected the requested audio foreground-service type", error)
            if (recording) NativeBridge.setAudioRecordingArmed(false)
            if (playing) NativeBridge.stopPlayback()
            stopSelf()
            return false
        }

        active = true
        microphoneActive = recording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(channelId, "Audio playback", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Radiant project playback controls"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(playing: Boolean, recording: Boolean) = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Radiant")
        .setContentText(
            when {
                playing && recording -> "Playing and recording audio"
                recording -> "Recording audio input"
                else -> "Playing project audio"
            },
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, RadiantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, PlaybackForegroundService::class.java).setAction(actionStop),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()
}
