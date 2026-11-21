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
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                sendBroadcast(Intent(ACTION_PAUSE_PAGE).setPackage(packageName))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                sendBroadcast(Intent(ACTION_TOGGLE_PAGE).setPackage(packageName))
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Reproduzindo em segundo plano" }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        acquireLocks()
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reprodução em segundo plano", NotificationManager.IMPORTANCE_LOW)
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Navegador · segundo plano")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(icon, "Pausar / Tocar", toggle).build())
            .addAction(Notification.Action.Builder(icon, "Parar", stop).build())
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

    override fun onDestroy() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.pessoal.navegador.TOGGLE"
        const val ACTION_STOP = "com.pessoal.navegador.STOP"

        /** Mensagens enviadas ao MainActivity (broadcast restrito ao próprio app). */
        const val ACTION_TOGGLE_PAGE = "com.pessoal.navegador.TOGGLE_PAGE"
        const val ACTION_PAUSE_PAGE = "com.pessoal.navegador.PAUSE_PAGE"

        const val EXTRA_TITLE = "title"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LOCK_MS = 4 * 60 * 60 * 1000L // segurança: solta sozinho após 4 h
    }
}
