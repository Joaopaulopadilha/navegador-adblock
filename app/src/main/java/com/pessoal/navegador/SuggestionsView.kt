package com.pessoal.navegador

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Lista de pesquisas recentes, cada uma com um ✕ para apagar só aquela.
 * Usada na página de início e sob a barra de endereço.
 */
class SuggestionsView(
    context: Context,
    private val prefs: Prefs,
    private val onPick: (String) -> Unit,
    private val maxRows: Int = 6
) : LinearLayout(context) {

    private var filter = ""

    /** Na navegação anônima as pesquisas não são mostradas. */
    var disabled = false

    init {
        orientation = VERTICAL
        visibility = GONE
    }

    /** Mostra as pesquisas que combinam com o texto digitado. Retorna se há algo para mostrar. */
    fun update(typed: String): Boolean {
        filter = typed.trim()
        removeAllViews()
        if (disabled || !prefs.saveSearchHistory) {
            visibility = GONE
            return false
        }
        val all = prefs.searches()
        val rows = all.filter { filter.isEmpty() || it.contains(filter, ignoreCase = true) }
            .filter { !it.equals(filter, ignoreCase = true) }
            .take(maxRows)
        if (rows.isEmpty()) {
            visibility = GONE
            return false
        }

        val text = ContextCompat.getColor(context, R.color.text)
        val muted = ContextCompat.getColor(context, R.color.muted)
        val danger = ContextCompat.getColor(context, R.color.danger)

        for (term in rows) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, 0, 0)
                isClickable = true
                setOnClickListener { onPick(term) }
            }
            row.addView(TextView(context).apply {
                this.text = "🕒"
                textSize = 14f
                setPadding(0, 0, dp(12), 0)
            })
            row.addView(TextView(context).apply {
                this.text = term
                textSize = 16f
                setTextColor(text)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(context).apply {
                this.text = "✕"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(danger)
                gravity = Gravity.CENTER
                isClickable = true
                // Apaga só esta pesquisa
                setOnClickListener {
                    prefs.removeSearch(term)
                    update(filter)
                }
            }, LayoutParams(dp(52), dp(48)))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }

        if (filter.isEmpty() && all.size > 1) {
            addView(TextView(context).apply {
                this.text = "Limpar todas as pesquisas"
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    prefs.clearSearches()
                    update(filter)
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        }
        visibility = VISIBLE
        return true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
