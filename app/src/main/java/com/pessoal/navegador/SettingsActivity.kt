package com.pessoal.navegador

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var column: LinearLayout

    private val cBg by lazy { color(R.color.bg) }
    private val cText by lazy { color(R.color.text) }
    private val cMuted by lazy { color(R.color.muted) }
    private val cAccent by lazy { color(R.color.accent) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeUtil.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeUtil.applySystemBars(this)
        prefs = Prefs(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(cBg) }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(40))
        }
        scroll.addView(column)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)

        // Cabeçalho
        column.addView(TextView(this).apply {
            text = "‹  Configurações"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cText)
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { finish() }
        })

        // Aparência
        section("Aparência")
        radios(
            options = listOf(
                ThemeUtil.SYSTEM to "Seguir o sistema",
                ThemeUtil.LIGHT to "Claro",
                ThemeUtil.DARK to "Escuro"
            ),
            current = prefs.themeMode
        ) {
            if (it != prefs.themeMode) {
                prefs.themeMode = it
                recreate() // redesenha esta tela já no tema escolhido; a tela principal faz o mesmo ao voltar
            }
        }

        // Página inicial
        section("Página inicial")
        val customUrl = EditText(this).apply {
            hint = "Endereço (ex.: site.com.br)"
            setHintTextColor(cMuted)
            setTextColor(cText)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(prefs.homeCustom)
            doAfterTextChanged { prefs.homeCustom = it?.toString().orEmpty() }
        }
        radios(
            options = listOf(
                Prefs.HOME_START to "Página de início (busca e atalhos)",
                Prefs.HOME_YOUTUBE to "YouTube",
                Prefs.HOME_GOOGLE to "Google",
                Prefs.HOME_CUSTOM to "Outra página"
            ),
            current = prefs.homeMode
        ) {
            prefs.homeMode = it
            customUrl.visibility = if (it == Prefs.HOME_CUSTOM) View.VISIBLE else View.GONE
        }
        customUrl.visibility = if (prefs.homeMode == Prefs.HOME_CUSTOM) View.VISIBLE else View.GONE
        column.addView(customUrl)

        // Busca
        section("Mecanismo de busca")
        radios(
            options = Prefs.ENGINES.map { it.key to it.value.first },
            current = prefs.searchEngine
        ) { prefs.searchEngine = it }

        // Pesquisas recentes
        section("Pesquisas recentes")
        toggle("Salvar pesquisas recentes", "Aparecem ao tocar na caixa de busca, cada uma com um ✕ para apagar", prefs.saveSearchHistory) {
            prefs.saveSearchHistory = it
        }
        column.addView(Button(this).apply {
            text = "Apagar todas as pesquisas"
            setOnClickListener {
                val n = prefs.searches().size
                prefs.clearSearches()
                Toast.makeText(
                    context,
                    if (n == 0) "Não havia pesquisas salvas" else "$n pesquisas apagadas",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // Reprodução
        section("Reprodução")
        toggle("Tocar em segundo plano", "Mantém o som com a tela apagada ou em outro app", prefs.backgroundPlay) {
            prefs.backgroundPlay = it
        }

        // Bloqueio
        section("Bloqueio de anúncios")
        toggle("Ligado ao abrir o app", "O botão do escudo na barra liga e desliga só nesta sessão", prefs.adblockDefault) {
            prefs.adblockDefault = it
        }

        // Atalhos
        section("Atalhos da página de início")
        column.addView(Button(this).apply {
            text = "Restaurar atalhos padrão"
            setOnClickListener {
                prefs.resetShortcuts()
                Toast.makeText(context, "Atalhos restaurados", Toast.LENGTH_SHORT).show()
            }
        })

        // Atualização
        section("Atualização")
        toggle("Verificar atualizações sozinho", "Consulta o GitHub no máximo uma vez por dia ao abrir o app", prefs.autoUpdateCheck) {
            prefs.autoUpdateCheck = it
        }
        column.addView(Button(this).apply {
            text = "Verificar atualização (versão ${Updater.currentVersion(context)})"
            setOnClickListener { Updater.check(this@SettingsActivity, prefs, manual = true) }
        })

        column.addView(TextView(this).apply {
            text = "As mudanças valem na hora, exceto o bloqueio ligado ao abrir (vale na próxima abertura)."
            textSize = 12f
            setTextColor(cMuted)
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun section(title: String) {
        column.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cAccent)
            setPadding(0, dp(24), 0, dp(6))
        })
    }

    private fun radios(options: List<Pair<String, String>>, current: String, onChange: (String) -> Unit) {
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        for ((key, label) in options) {
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                tag = key
                textSize = 16f
                setTextColor(cText)
                setPadding(dp(4), dp(10), 0, dp(10))
                isChecked = key == current
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val key = g.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            onChange(key)
        }
        column.addView(group)
    }

    @Suppress("DEPRECATION")
    private fun toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        column.addView(Switch(this).apply {
            text = title
            textSize = 16f
            setTextColor(cText)
            isChecked = checked
            setPadding(0, dp(6), 0, dp(2))
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
        column.addView(TextView(this).apply {
            text = hint
            textSize = 12f
            setTextColor(cMuted)
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
