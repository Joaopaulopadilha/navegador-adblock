package com.pessoal.navegador

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * WebView que não avisa o Chromium quando a janela fica invisível (app em segundo plano ou tela apagada).
 * Sem isso, o Chromium considera a página oculta e pausa a mídia por dentro, mesmo com o serviço rodando.
 */
class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Quando falso, o comportamento volta ao normal do Android. */
    var keepAlive: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepAlive) View.VISIBLE else visibility)
    }
}