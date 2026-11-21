package com.pessoal.navegador

import android.content.Context
import android.net.Uri
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bloqueio de requisições: lista de domínios de anúncios + alguns padrões de URL do YouTube.
 * Roda em threads de rede do WebView (shouldInterceptRequest), então só usa estruturas imutáveis/atômicas.
 */
class AdBlocker(context: Context) {

    @Volatile
    var enabled = true

    val blocked = AtomicInteger(0)

    private val hosts: Set<String> = context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
        lines.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toHashSet()
    }

    fun shouldBlock(uri: Uri): Boolean {
        if (!enabled) return false
        val host = uri.host?.lowercase() ?: return false

        // Serviços de música/vídeo que quebram se alguma requisição for barrada.
        if (NEVER_BLOCK.any { host == it || host.endsWith(".$it") }) return false

        if (matchesHost(host)) return true

        // Padrões de anúncio em URLs; nunca em googlevideo.com (é onde ficam os vídeos de verdade).
        if (host.endsWith("googlevideo.com")) return false
        val url = uri.toString()
        return PATTERNS.any { url.contains(it) }
    }

    /** "a.b.example.com" casa com "a.b.example.com", "b.example.com" e "example.com", mas nunca com "com". */
    private fun matchesHost(host: String): Boolean {
        var h = host
        while (true) {
            if (hosts.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false
        }
    }

    companion object {
        private val NEVER_BLOCK = listOf("spotify.com", "scdn.co", "spotifycdn.com", "spotifycdn.net")

        private val PATTERNS = listOf(
            "/pagead/",
            "/api/stats/ads",
            "/ptracking",
            "/get_midroll_",
            "doubleclick.net/"
        )
    }
}
