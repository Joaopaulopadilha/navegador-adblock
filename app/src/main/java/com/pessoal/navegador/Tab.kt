package com.pessoal.navegador

import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/** Uma aba. O WebView só existe depois que a aba é aberta pela primeira vez (carregamento preguiçoso). */
class Tab(val id: Int, val incognito: Boolean, var parentId: Int? = null) {
    var web: BackgroundWebView? = null

    /** Último endereço conhecido; usado para recarregar a aba quando ela ainda não tem WebView. */
    var url: String = ""
    var title: String = ""

    /** Verdadeiro enquanto a aba mostra a página de início (ainda sem nenhuma página aberta). */
    var showStart: Boolean = true

    /** Histórico de navegação guardado quando a tela é recriada (ex.: troca de tema). */
    var savedState: Bundle? = null

    /**
     * "Site para computador" desta aba. Nas abas normais fica nulo e vale o que foi lembrado para o site;
     * nas anônimas vale só para a aba, sem gravar nada.
     */
    var desktopOverride: Boolean? = null

    /** Pré-visualização da página, mostrada na lista de abas (só em memória). */
    var thumb: android.graphics.Bitmap? = null

    /** Bloqueios da página atual desta aba. */
    val blocked = AtomicInteger(0)
    var pageUrl: String? = null

    /** Ao voltar para a página de início, o histórico da página em branco usada para parar o som é apagado. */
    var pendingClearHistory = false
}

/**
 * Guarda as abas fora da Activity, para que sobrevivam a ela ser recriada (troca de tema, rotação etc.).
 * Os WebViews pertencem à Activity e são recriados; o que fica aqui é endereço, título e histórico.
 */
object TabStore {
    val tabs = mutableListOf<Tab>()
    var activeId = -1
    private var next = 1

    fun newId(): Int = next++
}
