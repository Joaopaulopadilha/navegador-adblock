package com.pessoal.navegador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

/**
 * Aviso fixo na barra de notificações enquanto há aba anônima aberta.
 * Tocar em qualquer lugar dele fecha todas as abas anônimas e apaga os dados delas.
 */
object IncognitoNotice {

    /** Mensagem interna (só dentro do app) para a tela principal fechar as abas anônimas. */
    const val ACTION_CLOSE = "com.pessoal.navegador.CLOSE_INCOGNITO"

    private const val CHANNEL_ID = "incognito"
    private const val NOTIFICATION_ID = 2

    fun show(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Abas anônimas abertas", NotificationManager.IMPORTANCE_LOW)
        )
        val tap = PendingIntent.getBroadcast(
            context, 3, Intent(context, CloseIncognitoReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fechar abas anônimas abertas")
            .setContentText("Toque aqui para fechar tudo e apagar os dados da navegação anônima")
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_SECRET) // não aparece na tela de bloqueio
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

/** Recebe o toque na notificação das abas anônimas. */
class CloseIncognitoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        IncognitoNotice.cancel(context)
        if (MainActivity.alive) {
            // A tela principal fecha as abas (e destrói os WebViews delas).
            context.sendBroadcast(Intent(IncognitoNotice.ACTION_CLOSE).setPackage(context.packageName))
        } else {
            // Sem tela aberta: tira as abas anônimas da memória e apaga os dados delas.
            TabStore.tabs.removeAll { it.incognito }
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().deleteProfile(MainActivity.INCOGNITO_PROFILE)
                }
            } catch (_: Exception) {
            }
        }
    }
}
