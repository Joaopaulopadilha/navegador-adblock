package com.pessoal.navegador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Serviço em primeiro plano que mantém o app vivo enquanto um vídeo toca com a tela apagada ou em outro app.
 * Ele não toca nada: só segura o processo, a CPU e o Wi-Fi. O som continua vindo do próprio WebView.
 * A notificação mostra o título da página e tem os botões: voltar 10 s, tocar/pausar, avançar 10 s e encerrar o app.
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var title = ""
    private var playing = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUIT -> {
                sendBroadcast(Intent(ACTION_QUIT_APP).setPackage(packageName))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                sendBroadcast(Intent(ACTION_TOGGLE_PAGE).setPackage(packageName))
                return START_NOT_STICKY
            }
            ACTION_BACK -> {
                sendBroadcast(Intent(ACTION_SEEK_PAGE).setPackage(packageName).putExtra(EXTRA_SECONDS, -SEEK_SECONDS))
                return START_NOT_STICKY
            }
            ACTION_FORWARD -> {
                sendBroadcast(Intent(ACTION_SEEK_PAGE).setPackage(packageName).putExtra(EXTRA_SECONDS, SEEK_SECONDS))
                return START_NOT_STICKY
            }
        }

        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        playing = true
        instance = this
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        acquireLocks()
        return START_NOT_STICKY
    }

    /** Chamado pela tela principal quando o título da página ou o estado (tocando/pausado) muda. */
    private fun refresh(newTitle: String?, nowPlaying: Boolean?) {
        if (newTitle != null) title = newTitle
        if (nowPlaying != null && nowPlaying != playing) {
            playing = nowPlaying
            if (playing) acquireLocks() else releaseLocks()
        }
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

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Reproduzindo em segundo plano" })
            .setContentText(if (playing) "Navegador · tocando" else "Navegador · pausado")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(1, R.drawable.ic_rewind, "Voltar 10 s", ACTION_BACK))
            .addAction(
                if (playing) action(2, R.drawable.ic_pause, "Pausar", ACTION_TOGGLE)
                else action(2, R.drawable.ic_play, "Tocar", ACTION_TOGGLE)
            )
            .addAction(action(3, R.drawable.ic_forward, "Avançar 10 s", ACTION_FORWARD))
            .addAction(action(4, R.drawable.ic_close, "Encerrar", ACTION_QUIT))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
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
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.pessoal.navegador.TOGGLE"
        const val ACTION_BACK = "com.pessoal.navegador.BACK10"
        const val ACTION_FORWARD = "com.pessoal.navegador.FORWARD10"
        const val ACTION_QUIT = "com.pessoal.navegador.QUIT"

        /** Mensagens enviadas ao MainActivity (broadcast restrito ao próprio app). */
        const val ACTION_TOGGLE_PAGE = "com.pessoal.navegador.TOGGLE_PAGE"
        const val ACTION_SEEK_PAGE = "com.pessoal.navegador.SEEK_PAGE"
        const val ACTION_QUIT_APP = "com.pessoal.navegador.QUIT_APP"

        const val EXTRA_TITLE = "title"
        const val EXTRA_SECONDS = "seconds"

        private const val SEEK_SECONDS = 10
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOCK_MS = 4 * 60 * 60 * 1000L // segurança: solta sozinho após 4 h

        @Volatile
        private var instance: PlaybackService? = null

        /** Atualiza a notificação se o serviço estiver rodando (mesmo processo, sem passar por Intent). */
        fun update(title: String?, playing: Boolean?) {
            instance?.refresh(title, playing)
        }
    }
}
