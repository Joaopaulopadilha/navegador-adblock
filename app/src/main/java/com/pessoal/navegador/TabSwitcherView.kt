package com.pessoal.navegador

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Lista de abas: toque abre, ✕ fecha, e dois botões criam aba normal ou anônima. */
class TabSwitcherView(
    context: Context,
    private val onSelect: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit,
    private val onNew: (incognito: Boolean) -> Unit
) : ScrollView(context) {

    private val cBg = color(R.color.bg)
    private val cCard = color(R.color.card)
    private val cField = color(R.color.field)
    private val cText = color(R.color.text)
    private val cMuted = color(R.color.muted)
    private val cAccent = color(R.color.accent)
    private val cDanger = color(R.color.danger)
    private val cIncognito = color(R.color.incognito_bar)

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(24))
    }

    init {
        setBackgroundColor(cBg)
        isFillViewport = true
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(tabs: List<Tab>, activeId: Int) {
        column.removeAllViews()

        // Botões de nova aba
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            actionButton("+ Nova aba", cAccent, Color_ON_ACCENT) { onNew(false) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) }
        )
        actions.addView(
            actionButton("🕶 Anônima", cIncognito, 0xFFFFFFFF.toInt()) { onNew(true) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) }
        )
        column.addView(actions)

        val normal = tabs.filter { !it.incognito }
        val priv = tabs.filter { it.incognito }
        if (normal.isNotEmpty()) {
            section("Abas (${normal.size})")
            normal.forEach { column.addView(card(it, it.id == activeId)) }
        }
        if (priv.isNotEmpty()) {
            section("Anônimas (${priv.size})")
            priv.forEach { column.addView(card(it, it.id == activeId)) }
        }
    }

    private fun section(text: String) {
        column.addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cMuted)
            setPadding(dp(4), dp(20), 0, dp(8))
        })
    }

    private fun actionButton(label: String, bg: Int, fg: Int, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(fg)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(12).toFloat()
        }
        isClickable = true
        setOnClickListener { click() }
    }

    private fun card(tab: Tab, isActive: Boolean): View {
        val bg = GradientDrawable().apply {
            setColor(if (tab.incognito) cIncognito else cCard)
            cornerRadius = dp(14).toFloat()
            if (isActive) setStroke(dp(2), cAccent)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(12), dp(10), dp(4), dp(10))
            isClickable = true
            setOnClickListener { onSelect(tab) }
        }
        val onDark = if (tab.incognito) 0xFFFFFFFF.toInt() else cText
        val subtle = if (tab.incognito) 0xFFC9C2E0.toInt() else cMuted

        val host = hostOf(tab)
        val letter = when {
            tab.incognito && tab.showStart -> "🕶"
            tab.showStart -> "+"
            else -> host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
        row.addView(TextView(context).apply {
            text = letter
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (tab.incognito) 0xFFFFFFFF.toInt() else cAccent)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (tab.incognito) 0x33FFFFFF else cField)
            }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = titleOf(tab, host)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onDark)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(context).apply {
            text = if (tab.showStart) (if (tab.incognito) "Navegação anônima" else "Página de início") else host
            textSize = 12f
            setTextColor(subtle)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(12)
        })

        row.addView(TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(if (tab.incognito) 0xFFFFFFFF.toInt() else cDanger)
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClose(tab) }
        }, LinearLayout.LayoutParams(dp(52), dp(44)))

        val wrap = FrameLayout(context)
        wrap.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        wrap.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        return wrap
    }

    private fun hostOf(tab: Tab): String =
        Uri.parse(tab.web?.url?.takeIf { it != "about:blank" } ?: tab.url).host.orEmpty().removePrefix("www.")

    private fun titleOf(tab: Tab, host: String): String = when {
        tab.showStart -> if (tab.incognito) "Aba anônima" else "Nova aba"
        tab.title.isNotBlank() -> tab.title
        host.isNotBlank() -> host
        else -> "Sem título"
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val Color_ON_ACCENT = 0xFF1A1300.toInt()
    }
}
