package com.pessoal.navegador

import android.content.Context
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Lista de abas em grade, como no Chrome: cada aba mostra uma pré-visualização da página.
 * Toque abre, ✕ fecha, e dois botões criam aba normal ou anônima.
 */
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
        setPadding(dp(12), dp(12), dp(12), dp(24))
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
            actionButton("+ Nova aba", cAccent, COLOR_ON_ACCENT) { onNew(false) },
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
            grid(normal, activeId)
        }
        if (priv.isNotEmpty()) {
            section("Anônimas (${priv.size})")
            grid(priv, activeId)
        }
    }

    private fun section(text: String) {
        column.addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cMuted)
            setPadding(dp(4), dp(18), 0, dp(8))
        })
    }

    /** Duas abas por linha. */
    private fun grid(list: List<Tab>, activeId: Int) {
        list.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, tab ->
                row.addView(
                    card(tab, tab.id == activeId),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i == 0) rightMargin = dp(5) else leftMargin = dp(5)
                        bottomMargin = dp(10)
                    }
                )
            }
            if (pair.size == 1) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(5) })
            }
            column.addView(row)
        }
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
        val onDark = if (tab.incognito) 0xFFFFFFFF.toInt() else cText
        val subtle = if (tab.incognito) 0xFFC9C2E0.toInt() else cMuted
        val host = hostOf(tab)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(if (tab.incognito) cIncognito else cCard)
                cornerRadius = dp(14).toFloat()
                if (isActive) setStroke(dp(2), cAccent)
            }
            setPadding(dp(8), dp(6), dp(8), dp(8))
            isClickable = true
            setOnClickListener { onSelect(tab) }
        }

        // Cabeçalho: inicial do site, título e ✕
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(context).apply {
            text = letterOf(tab, host)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (tab.incognito) 0xFFFFFFFF.toInt() else cAccent)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (tab.incognito) 0x33FFFFFF else cField)
            }
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        head.addView(TextView(context).apply {
            text = titleOf(tab, host)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onDark)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(8)
        })
        head.addView(TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(if (tab.incognito) 0xFFFFFFFF.toInt() else cDanger)
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClose(tab) }
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        box.addView(head)

        // Pré-visualização
        val preview = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(cField)
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
        }
        val thumb = tab.thumb
        if (thumb != null && !thumb.isRecycled) {
            preview.addView(TopCropImageView(context).apply { setImageBitmap(thumb) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        } else {
            preview.addView(TextView(context).apply {
                text = if (tab.incognito) "🕶" else if (tab.showStart) "+" else letterOf(tab, host)
                textSize = 36f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(subtle)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        box.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(PREVIEW_HEIGHT_DP)).apply {
            topMargin = dp(4)
        })
        return box
    }

    private fun letterOf(tab: Tab, host: String): String = when {
        tab.incognito && tab.showStart -> "🕶"
        tab.showStart -> "+"
        else -> host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
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

    /** Mostra o topo da imagem, ajustada à largura (a parte de baixo que não couber é cortada). */
    private class TopCropImageView(context: Context) : ImageView(context) {
        init {
            scaleType = ScaleType.MATRIX
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val d = drawable ?: return
            if (d.intrinsicWidth <= 0 || width <= 0) return
            val scale = width.toFloat() / d.intrinsicWidth
            imageMatrix = Matrix().apply { setScale(scale, scale) }
        }
    }

    companion object {
        private const val COLOR_ON_ACCENT = 0xFF1A1300.toInt()
        private const val PREVIEW_HEIGHT_DP = 190
    }
}
