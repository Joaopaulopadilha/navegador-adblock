package com.pessoal.navegador

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aviso de versão nova: consulta a última release no GitHub e, se for mais nova, oferece abrir a página de download.
 * Não baixa nem instala nada (instalar pacotes é bloqueado pelo Play Protect).
 */
object Updater {

    private const val REPO = "Joaopaulopadilha/navegador-adblock"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PAGE = "https://joaopaulopadilha.github.io/navegador/"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    class Release(val version: String, val notes: String)

    @Volatile
    private var busy = false

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Consulta a última release; devolve null se não houver nenhuma ou se falhar. Bloqueia: use fora da thread principal. */
    private fun fetchLatest(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isEmpty()) return null
            val body = json.optString("body")
            return Release(tag, body.replace(Regex("SHA-256:\\s*[0-9A-Fa-f]{64}"), "").trim())
        } finally {
            conn.disconnect()
        }
    }

    /** "0.10" > "0.9"; segmentos não numéricos contam como 0. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Verifica se há versão nova. [manual] = o usuário pediu: sempre responde (mesmo "já está atualizado" ou erro).
     * Automática: no máximo uma vez por dia e só fala se houver novidade.
     */
    fun check(activity: Activity, prefs: Prefs, manual: Boolean) {
        if (busy) return
        if (!manual) {
            if (!prefs.autoUpdateCheck) return
            if (System.currentTimeMillis() - prefs.lastUpdateCheck < CHECK_INTERVAL_MS) return
        }
        busy = true
        Thread {
            val rel = try {
                fetchLatest()
            } catch (_: Exception) {
                null
            }
            if (rel != null || manual) prefs.lastUpdateCheck = System.currentTimeMillis()
            activity.runOnUiThread {
                busy = false
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    rel == null -> if (manual) toast(activity, "Não foi possível verificar agora. Confira a internet.")
                    isNewer(rel.version, currentVersion(activity)) -> offer(activity, rel)
                    manual -> toast(activity, "Você já está na versão mais recente (${currentVersion(activity)}).")
                }
            }
        }.start()
    }

    private fun offer(activity: Activity, rel: Release) {
        val notes = if (rel.notes.isBlank()) "" else "\n\n" + rel.notes
        AlertDialog.Builder(activity)
            .setTitle("Nova versão ${rel.version} disponível")
            .setMessage("Você está na ${currentVersion(activity)}.$notes")
            .setPositiveButton("Abrir página de download") { _, _ -> openDownloadPage(activity) }
            .setNegativeButton("Agora não", null)
            .show()
    }

    /** Abre a página de download em outro navegador (este app não baixa arquivos). */
    private fun openDownloadPage(activity: Activity) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(PAGE))
        val chooser = Intent.createChooser(view, "Abrir com")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(activity, MainActivity::class.java)))
        try {
            activity.startActivity(chooser)
        } catch (_: Exception) {
            toast(activity, "Abra no navegador: $PAGE")
        }
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
