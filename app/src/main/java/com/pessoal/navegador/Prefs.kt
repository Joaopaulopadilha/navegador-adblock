package com.pessoal.navegador

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Shortcut(val id: String, val name: String, val url: String)

/** Configurações do navegador, guardadas no aparelho. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("navegador", Context.MODE_PRIVATE)

    /** "start" (página de atalhos), "youtube", "google" ou "custom". */
    var homeMode: String
        get() = sp.getString("home_mode", HOME_START) ?: HOME_START
        set(v) = sp.edit().putString("home_mode", v).apply()

    var homeCustom: String
        get() = sp.getString("home_custom", "") ?: ""
        set(v) = sp.edit().putString("home_custom", v).apply()

    var searchEngine: String
        get() = sp.getString("search_engine", "google")?.takeIf { it in ENGINES } ?: "google"
        set(v) = sp.edit().putString("search_engine", v).apply()

    var backgroundPlay: Boolean
        get() = sp.getBoolean("background_play", false)
        set(v) = sp.edit().putBoolean("background_play", v).apply()

    var adblockDefault: Boolean
        get() = sp.getBoolean("adblock_default", true)
        set(v) = sp.edit().putBoolean("adblock_default", v).apply()

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) = sp.edit().putBoolean("asked_notifications", v).apply()

    /** "system", "light" ou "dark". */
    var themeMode: String
        get() = sp.getString("theme_mode", ThemeUtil.SYSTEM)?.takeIf { it in listOf(ThemeUtil.SYSTEM, ThemeUtil.LIGHT, ThemeUtil.DARK) }
            ?: ThemeUtil.SYSTEM
        set(v) = sp.edit().putString("theme_mode", v).apply()

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("auto_update_check", true)
        set(v) = sp.edit().putBoolean("auto_update_check", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    // ---------- Site para computador (lembrado por site) ----------

    fun isDesktopSite(host: String): Boolean =
        (sp.getStringSet("desktop_sites", emptySet()) ?: emptySet()).contains(siteKey(host))

    fun setDesktopSite(host: String, on: Boolean) {
        val set = (sp.getStringSet("desktop_sites", emptySet()) ?: emptySet()).toMutableSet()
        if (on) set.add(siteKey(host)) else set.remove(siteKey(host))
        sp.edit().putStringSet("desktop_sites", set).apply()
    }

    // ---------- Abas (só as normais; as anônimas nunca são salvas) ----------

    data class TabSnapshot(val url: String, val title: String, val showStart: Boolean)

    var activeTabIndex: Int
        get() = sp.getInt("active_tab", 0)
        set(v) = sp.edit().putInt("active_tab", v).apply()

    fun loadTabs(): List<TabSnapshot> {
        val raw = sp.getString("tabs", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TabSnapshot(o.optString("url"), o.optString("title"), o.optBoolean("start", false))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTabs(list: List<TabSnapshot>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("start", it.showStart)) }
        sp.edit().putString("tabs", arr.toString()).apply()
    }

    // ---------- Pesquisas recentes ----------

    var saveSearchHistory: Boolean
        get() = sp.getBoolean("save_search_history", true)
        set(v) = sp.edit().putBoolean("save_search_history", v).apply()

    /** Mais recentes primeiro. */
    fun searches(): List<String> {
        val raw = sp.getString("searches", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSearches(list: List<String>) {
        sp.edit().putString("searches", JSONArray(list).toString()).apply()
    }

    /** Guarda a pesquisa no topo da lista, sem repetir e sem passar de [MAX_SEARCHES]. */
    fun addSearch(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val list = searches().filter { !it.equals(t, ignoreCase = true) }.toMutableList()
        list.add(0, t)
        saveSearches(list.take(MAX_SEARCHES))
    }

    /** Apaga uma pesquisa específica. */
    fun removeSearch(term: String) = saveSearches(searches().filter { it != term })

    fun clearSearches() = saveSearches(emptyList())

    /** Endereço da página inicial, ou null quando a inicial é a página de atalhos. */
    fun homeUrl(): String? = when (homeMode) {
        HOME_YOUTUBE -> "https://m.youtube.com/"
        HOME_GOOGLE -> "https://www.google.com/"
        HOME_CUSTOM -> homeCustom.trim().takeIf { it.isNotEmpty() }?.let { normalizeUrl(it) }
        else -> null
    }

    fun engineName(): String = ENGINES.getValue(searchEngine).first

    fun searchUrl(query: String): String = ENGINES.getValue(searchEngine).second + Uri.encode(query)

    // ---------- Atalhos ----------

    fun shortcuts(): List<Shortcut> {
        val raw = sp.getString("shortcuts", null) ?: return defaultShortcuts().also { saveShortcuts(it) }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Shortcut(o.getString("id"), o.getString("name"), o.getString("url"))
            }
        } catch (_: Exception) {
            defaultShortcuts()
        }
    }

    fun saveShortcuts(list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
        }
        sp.edit().putString("shortcuts", arr.toString()).apply()
    }

    fun resetShortcuts() = saveShortcuts(defaultShortcuts())

    private fun defaultShortcuts() = listOf(
        Shortcut(newId(), "YouTube", "https://m.youtube.com/"),
        Shortcut(newId(), "Google", "https://www.google.com/"),
        Shortcut(newId(), "Wikipédia", "https://pt.wikipedia.org/"),
        Shortcut(newId(), "GitHub", "https://github.com/")
    )

    companion object {
        const val HOME_START = "start"
        const val HOME_YOUTUBE = "youtube"
        const val HOME_GOOGLE = "google"
        const val HOME_CUSTOM = "custom"
        const val MAX_SEARCHES = 50

        /** chave -> (nome, prefixo da URL de busca) */
        val ENGINES: LinkedHashMap<String, Pair<String, String>> = linkedMapOf(
            "google" to ("Google" to "https://www.google.com/search?q="),
            "duckduckgo" to ("DuckDuckGo" to "https://duckduckgo.com/?q="),
            "bing" to ("Bing" to "https://www.bing.com/search?q="),
            "brave" to ("Brave Search" to "https://search.brave.com/search?q=")
        )

        fun newId(): String = UUID.randomUUID().toString()

        /** "open.spotify.com" -> "spotify.com"; "www.uol.com.br" -> "uol.com.br" (aproximação do domínio do site). */
        fun siteKey(host: String): String {
            val labels = host.lowercase().split('.')
            if (labels.size <= 2) return host.lowercase()
            val second = labels[labels.size - 2]
            val twoPartTld = labels.last().length == 2 && second in setOf("com", "co", "org", "net", "gov", "edu")
            return labels.takeLast(if (twoPartTld) 3 else 2).joinToString(".")
        }

        /** Acrescenta https:// quando faltar; http vira https. */
        fun normalizeUrl(input: String): String {
            val t = input.trim()
            return when {
                t.startsWith("https://") -> t
                t.startsWith("http://") -> "https://" + t.removePrefix("http://")
                else -> "https://$t"
            }
        }
    }
}
