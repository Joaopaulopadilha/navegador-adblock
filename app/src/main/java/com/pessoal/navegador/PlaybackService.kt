package com.pessoal.navegador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Serviço em primeiro plano que mantém o app vivo enquanto um vídeo toca com a tela apagada ou em outro app.
 * Ele não toca nada: só segura o processo, a CPU e o Wi-Fi. O som continua vindo do próprio WebView.
 * A notificação é de mídia: título da página, barra de tempo (arrastável) e os botões faixa anterior,
 * tocar/pausar, próxima faixa e encerrar o app.
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var session: MediaSession? = null

    private var title = ""
    private var artist = ""
    private var playing = true
    private var positionMs = 0L
    private var durationMs = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUIT -> {
                quit()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                toPage(ACTION_TOGGLE_PAGE)
                return START_NOT_STICKY
            }
            ACTION_PREV -> {
                toPage(ACTION_PREV_PAGE)
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                toPage(ACTION_NEXT_PAGE)
                return START_NOT_STICKY
            }
        }

        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
        playing = true
        instance = this
        ensureSession()
        syncSession()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        acquireLocks()
        return START_NOT_STICKY
    }

    private fun toPage(action: String, extra: (Intent.() -> Unit)? = null) {
        sendBroadcast(Intent(action).setPackage(packageName).apply { extra?.invoke(this) })
    }

    private fun quit() {
        toPage(ACTION_QUIT_APP)
        stopSelf()
    }

    private fun ensureSession() {
        if (session != null) return
        session = MediaSession(this, "navegador").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!playing) toPage(ACTION_TOGGLE_PAGE)
                }

                override fun onPause() {
                    if (playing) toPage(ACTION_TOGGLE_PAGE)
                }

                override fun onSkipToNext() = toPage(ACTION_NEXT_PAGE)

                override fun onSkipToPrevious() = toPage(ACTION_PREV_PAGE)

                override fun onSeekTo(pos: Long) {
                    positionMs = pos
                    syncSession()
                    toPage(ACTION_SEEK_PAGE) { putExtra(EXTRA_POSITION_MS, pos) }
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == CUSTOM_QUIT) quit()
                }
            })
            isActive = true
        }
    }

    /** Passa título, duração e posição para a sessão de mídia (é dela que vem a barra de tempo). */
    private fun syncSession() {
        val s = session ?: return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { "Reproduzindo em segundo plano" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist.ifBlank { "Navegador" })
                .apply { if (durationMs > 0) putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs) }
                .build()
        )
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (durationMs > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0),
                    if (playing) 1f else 0f
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(CUSTOM_QUIT, "Encerrar", R.drawable.ic_close).build()
                )
                .build()
        )
    }

    /** Chamado pela tela principal quando o título, o estado ou a posição muda. */
    private fun refresh(newTitle: String?, nowPlaying: Boolean?, pos: Long?, dur: Long?, newArtist: String? = null) {
        if (newTitle != null) title = newTitle
        if (newArtist != null) artist = newArtist
        if (pos != null) positionMs = pos
        if (dur != null) durationMs = dur
        if (nowPlaying != null && nowPlaying != playing) {
            playing = nowPlaying
            if (playing) acquireLocks() else releaseLocks()
        }
        syncSession()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun action(requestCode: Int, iconRes: Int, label: String, action: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, iconRes), label,
            PendingIntent.getService(
                this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reprodução em segundo plano", NotificationManager.IMPORTANCE_LOW)
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        session?.let { style.setMediaSession(it.sessionToken) }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Reproduzindo em segundo plano" })
            .setContentText(artist.ifBlank { if (playing) "Navegador · tocando" else "Navegador · pausado" })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(1, R.drawable.ic_prev, "Faixa anterior", ACTION_PREV))
            .addAction(
                if (playing) action(2, R.drawable.ic_pause, "Pausar", ACTION_TOGGLE)
                else action(2, R.drawable.ic_play, "Tocar", ACTION_TOGGLE)
            )
            .addAction(action(3, R.drawable.ic_next, "Próxima faixa", ACTION_NEXT))
            .addAction(action(4, R.drawable.ic_close, "Encerrar", ACTION_QUIT))
            .setStyle(style)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "navegador:playback")
                    .apply {
                        setReferenceCounted(false)
                        acquire(MAX_LOCK_MS)
                    }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "navegador:wifi")
                    .apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
        } catch (_: Exception) {
            // Sem o lock o som pode falhar com a tela apagada, mas o app não deve cair por isso.
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        releaseLocks()
        session?.release()
        session = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.pessoal.navegador.TOGGLE"
        const val ACTION_PREV = "com.pessoal.navegador.PREV"
        const val ACTION_NEXT = "com.pessoal.navegador.NEXT"
        const val ACTION_QUIT = "com.pessoal.navegador.QUIT"

        /** Mensagens enviadas ao MainActivity (broadcast restrito ao próprio app). */
        const val ACTION_TOGGLE_PAGE = "com.pessoal.navegador.TOGGLE_PAGE"
        const val ACTION_PREV_PAGE = "com.pessoal.navegador.PREV_PAGE"
        const val ACTION_NEXT_PAGE = "com.pessoal.navegador.NEXT_PAGE"
        const val ACTION_SEEK_PAGE = "com.pessoal.navegador.SEEK_PAGE"
        const val ACTION_QUIT_APP = "com.pessoal.navegador.QUIT_APP"

        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_POSITION_MS = "position_ms"

        private const val CUSTOM_QUIT = "quit"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOCK_MS = 4 * 60 * 60 * 1000L // segurança: solta sozinho após 4 h

        @Volatile
        private var instance: PlaybackService? = null

        /** Atualiza a notificação se o serviço estiver rodando (mesmo processo, sem passar por Intent). */
        fun update(title: String?, playing: Boolean?) {
            instance?.refresh(title, playing, null, null)
        }

        /** Título e artista da faixa, vindos da página (MediaSession). */
        fun updateMeta(title: String, artist: String) {
            instance?.refresh(title, null, null, null, artist)
        }

        fun updateProgress(positionMs: Long, durationMs: Long, playing: Boolean) {
            instance?.refresh(null, playing, positionMs, durationMs)
        }
    }
}
