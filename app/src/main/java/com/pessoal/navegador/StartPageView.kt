package com.pessoal.navegador

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.LruCache
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Página inicial: busca no mecanismo escolhido, pesquisas recentes e ícones de acesso rápido.
 * Toque abre o atalho; segurar e arrastar reordena; segurar e soltar sem mexer edita ou remove; "+" adiciona.
 */
class StartPageView(
    context: Context,
    private val prefs: Prefs,
    private val onOpen: (String) -> Unit,
    private val onSearch: (String) -> Unit
) : ScrollView(context) {

    // Cores do tema atual (claro ou escuro)
    private val cBg = color(R.color.bg)
    private val cField = color(R.color.field)
    private val cText = color(R.color.text)
    private val cMuted = color(R.color.muted)
    private val cAccent = color(R.color.accent)
    private val cBorder = color(R.color.border)
    private val cIconBg = color(R.color.iconbg)

    private val titleView = TextView(context)
    private val subtitle = TextView(context)
    private val gridHint = TextView(context)
    private var incognito = false
    private val search = EditText(context)
    private val grid = GridLayout(context)
    private val suggestions = SuggestionsView(context, prefs, onPick = { term -> submit(term) })

    // Estado do arrastar
    private var draggedId: String? = null
    private var moved = false
    private var lastSwap = 0L

    init {
        isFillViewport = true
        setBackgroundColor(cBg)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        titleView.apply {
            text = "Navegador"
            textSize = 36f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cText)
            gravity = Gravity.CENTER
        }
        column.addView(titleView)

        subtitle.apply {
            textSize = 13f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(24))
        }
        column.addView(subtitle)

        // Caixa de busca
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(color(R.color.searchbox))
                setStroke(dp(1), cBorder)
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(18), 0, dp(18), 0)
        }
        box.addView(TextView(context).apply {
            text = "🔍"
            textSize = 18f
            setPadding(0, 0, dp(10), 0)
        })
        search.apply {
            hint = "Pesquisar ou digitar endereço"
            setHintTextColor(color(R.color.searchbox_hint))
            setTextColor(color(R.color.searchbox_text))
            textSize = 16f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submit(v.text.toString())
                    true
                } else false
            }
            // As pesquisas recentes aparecem quando a caixa ganha o foco e filtram conforme você digita.
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) suggestions.update(text.toString()) else suggestions.visibility = View.GONE
            }
            doAfterTextChanged { if (hasFocus()) suggestions.update(it?.toString().orEmpty()) }
        }
        box.addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        column.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

        column.addView(
            suggestions,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )

        // Atalhos
        grid.columnCount = COLUMNS
        column.addView(
            grid,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(36) }
        )
        grid.setOnDragListener { _, e -> onGridDrag(e) }

        gridHint.apply {
            text = "Segure e arraste para reorganizar. Segure e solte sem mexer para editar ou remover."
            textSize = 11f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), 0)
        }
        column.addView(gridHint)

        refresh()
    }

    /** Na aba anônima a página explica o modo e não mostra atalhos nem pesquisas recentes. */
    fun setIncognito(on: Boolean) {
        incognito = on
        suggestions.disabled = on
        refresh()
    }

    private fun submit(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        search.setText("")
        search.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        suggestions.visibility = View.GONE
        onSearch(t)
    }

    /** Relê as configurações (mecanismo de busca e atalhos) e redesenha. */
    fun refresh() {
        if (incognito) {
            titleView.text = "🕶 Anônima"
            subtitle.text = "As pesquisas e páginas desta aba não entram nas pesquisas recentes. " +
                "Cookies e dados dos sites são apagados quando você fecha todas as abas anônimas."
            grid.visibility = View.GONE
            gridHint.visibility = View.GONE
            suggestions.visibility = View.GONE
            return
        }
        titleView.text = "Navegador"
        grid.visibility = View.VISIBLE
        gridHint.visibility = View.VISIBLE
        subtitle.text = "Pesquisando com ${prefs.engineName()}"
        grid.removeAllViews()
        for (s in prefs.shortcuts()) {
            grid.addView(
                cell(s.id, s.name, s.url, firstLetter(s.name),
                    onClick = { onOpen(s.url) },
                    onLongClick = { v -> startDrag(v, s) })
            )
        }
        grid.addView(cell(ADD_TAG, "Adicionar", null, "+", onClick = { showEditor(null) }, onLongClick = null))
        if (search.hasFocus()) suggestions.update(search.text.toString()) else suggestions.visibility = View.GONE
    }

    // ---------- Arrastar para reordenar ----------

    private fun startDrag(view: View, s: Shortcut) {
        draggedId = s.id
        moved = false
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val data = ClipData.newPlainText("nv-shortcut", s.id)
        if (view.startDragAndDrop(data, View.DragShadowBuilder(view), null, 0)) {
            view.alpha = 0.3f // o "buraco" que mostra onde o atalho vai cair
        } else {
            draggedId = null
        }
    }

    private fun onGridDrag(e: DragEvent): Boolean = when (e.action) {
        DragEvent.ACTION_DRAG_STARTED ->
            draggedId != null && e.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        DragEvent.ACTION_DRAG_LOCATION -> {
            swapUnder(e.x, e.y)
            true
        }
        DragEvent.ACTION_DRAG_ENDED -> {
            finishDrag()
            true
        }
        else -> true
    }

    private fun cellWithTag(tag: String): View? =
        (0 until grid.childCount).map { grid.getChildAt(it) }.firstOrNull { it.tag == tag }

    /** Move o atalho arrastado para a posição do atalho que está sob o dedo. */
    private fun swapUnder(x: Float, y: Float) {
        val dragged = draggedId?.let { cellWithTag(it) } ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSwap < SWAP_DELAY_MS) return // dá tempo da grade se reajustar antes da próxima troca
        for (i in 0 until grid.childCount) {
            val c = grid.getChildAt(i)
            if (c === dragged || c.tag == ADD_TAG) continue
            if (x >= c.left && x < c.right && y >= c.top && y < c.bottom) {
                grid.removeView(dragged)
                grid.addView(dragged, i)
                moved = true
                lastSwap = now
                return
            }
        }
    }

    private fun finishDrag() {
        val id = draggedId ?: return
        draggedId = null
        cellWithTag(id)?.alpha = 1f

        val newOrder = (0 until grid.childCount).mapNotNull { grid.getChildAt(it).tag as? String }
            .filter { it != ADD_TAG }
        val current = prefs.shortcuts()
        if (newOrder != current.map { it.id }) {
            val byId = current.associateBy { it.id }
            prefs.saveShortcuts(newOrder.mapNotNull { byId[it] })
        } else if (!moved) {
            // Segurou e soltou sem mexer: abre o menu de editar/remover.
            current.firstOrNull { it.id == id }?.let { showActions(it) }
        }
    }

    private fun cell(
        tag: String,
        label: String,
        faviconFor: String?,
        letter: String,
        onClick: () -> Unit,
        onLongClick: ((View) -> Unit)?
    ): View {
        val item = LinearLayout(context).apply {
            this.tag = tag
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            isClickable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { v -> onLongClick(v); true }
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
        }

        val circle = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cField)
            }
        }
        val letterView = TextView(context).apply {
            text = letter
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cAccent)
            gravity = Gravity.CENTER
        }
        val iconView = ImageView(context).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        circle.addView(letterView, FrameLayout.LayoutParams(dp(60), dp(60)))
        circle.addView(iconView, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        item.addView(circle, LinearLayout.LayoutParams(dp(60), dp(60)))

        item.addView(TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(cText)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT))

        if (faviconFor != null) {
            FaviconLoader.load(faviconFor) { bmp ->
                // Fundo claro: ícones escuros (como o do GitHub) somem num círculo escuro.
                circle.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(cIconBg)
                    setStroke(dp(1), cBorder)
                }
                iconView.setImageBitmap(bmp)
                iconView.visibility = View.VISIBLE
                letterView.visibility = View.GONE
            }
        }
        return item
    }

    private fun showActions(s: Shortcut) {
        AlertDialog.Builder(context)
            .setTitle(s.name)
            .setItems(arrayOf("Editar", "Remover")) { _, which ->
                if (which == 0) showEditor(s) else remove(s)
            }
            .show()
    }

    private fun remove(s: Shortcut) {
        prefs.saveShortcuts(prefs.shortcuts().filter { it.id != s.id })
        refresh()
    }

    private fun showEditor(existing: Shortcut?) {
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val name = EditText(context).apply {
            hint = "Nome"
            setText(existing?.name.orEmpty())
            setSingleLine(true)
        }
        val url = EditText(context).apply {
            hint = "Endereço (ex.: site.com.br)"
            setText(existing?.url.orEmpty())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        form.addView(name)
        form.addView(url)

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) "Novo atalho" else "Editar atalho")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val address = url.text.toString().trim()
                if (address.isEmpty()) return@setPositiveButton
                val fixed = Prefs.normalizeUrl(address)
                val title = name.text.toString().trim().ifEmpty { Uri.parse(fixed).host.orEmpty().removePrefix("www.") }
                val list = prefs.shortcuts().toMutableList()
                if (existing == null) {
                    list.add(Shortcut(Prefs.newId(), title, fixed))
                } else {
                    val i = list.indexOfFirst { it.id == existing.id }
                    if (i >= 0) list[i] = existing.copy(name = title, url = fixed)
                }
                prefs.saveShortcuts(list)
                refresh()
            }
            .show()
    }

    private fun firstLetter(s: String): String = s.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLUMNS = 4
        private const val ADD_TAG = "__add__"
        private const val SWAP_DELAY_MS = 140L
    }
}

/** Baixa o ícone direto do próprio site (https://site/favicon.ico), sem passar por serviços de terceiros. */
object FaviconLoader {
    private val cache = LruCache<String, Bitmap>(64)
    private val failed = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(pageUrl: String, onResult: (Bitmap) -> Unit) {
        val host = Uri.parse(pageUrl).host ?: return
        cache.get(host)?.let {
            onResult(it)
            return
        }
        if (synchronized(failed) { failed.contains(host) }) return
        pool.execute {
            val bmp = fetch(host)
            if (bmp != null) {
                cache.put(host, bmp)
                main.post { onResult(bmp) }
            } else {
                synchronized(failed) { failed.add(host) }
            }
        }
    }

    private fun fetch(host: String): Bitmap? {
        for (path in listOf("/favicon.ico", "/apple-touch-icon.png")) {
            try {
                val c = URL("https://$host$path").openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Navegador")
                if (c.responseCode == 200) {
                    c.inputStream.use { stream ->
                        val b = BitmapFactory.decodeStream(stream)
                        if (b != null && b.width >= 16) return b
                    }
                }
            } catch (_: Exception) {
                // tenta o próximo caminho; sem ícone, o atalho mostra a letra
            }
        }
        return null
    }
}
