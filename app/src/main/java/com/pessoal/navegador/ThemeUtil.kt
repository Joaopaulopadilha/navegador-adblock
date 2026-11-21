package com.pessoal.navegador

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Tema claro/escuro escolhido pelo usuário, independente do tema do sistema.
 * A cor de cada tema vem dos recursos (values = claro, values-night = escuro); aqui só dizemos
 * ao Android qual dos dois usar, trocando o "uiMode" da configuração do app.
 * O WebView também segue essa configuração para decidir o prefers-color-scheme das páginas.
 */
object ThemeUtil {

    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Usado em attachBaseContext das activities. */
    fun wrap(base: Context): Context {
        val mode = Prefs(base).themeMode
        if (mode == SYSTEM) return base
        val cfg = Configuration(base.resources.configuration)
        val night = if (mode == DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(cfg)
    }

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Ícones da barra de status e de navegação em escuro no tema claro e claros no escuro. */
    fun applySystemBars(activity: Activity) {
        val light = !isDark(activity)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
