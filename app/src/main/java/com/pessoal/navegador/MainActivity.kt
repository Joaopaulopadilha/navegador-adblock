package com.pessoal.navegador

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var ui: View
    private lateinit var topBar: LinearLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var btnHome: TextView
    private lateinit var btnReload: TextView
    private lateinit var btnMenu: TextView
    private lateinit var btnShield: TextView
    private lateinit var btnBg: TextView
    private lateinit var btnTabs: TextView
    private lateinit var btnTabsBox: View
    private lateinit var fullscreen: FrameLayout
    private lateinit var webHolder: FrameLayout
    private lateinit var startContainer: FrameLayout
    private lateinit var suggestionsBox: FrameLayout
    private lateinit var tabSwitcherBox: FrameLayout
    private lateinit var startPage: StartPageView
    private lateinit var tabSwitcher: TabSwitcherView
    private lateinit var urlSuggestions: SuggestionsView

    private lateinit var settings: Prefs
    private lateinit var adBlocker: AdBlocker
    private lateinit var youtubeScript: String

    /** As abas ficam em TabStore para sobreviverem à Activity ser recriada (ex.: troca de tema). */
    private val tabs get() = TabStore.tabs
    private lateinit var active: Tab

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    // Agente de usuário: o do celular (padrão do WebView) e um de computador com a mesma versão do Chrome.
    private val mobileUa: String by lazy { WebSettings.getDefaultUserAgent(this) }
    private val desktopUa: String by lazy {
        val version = Regex("Chrome/([\\d.]+)").find(mobileUa)?.groupValues?.get(1) ?: "124.0.0.0"
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var counterPosted = false

    // Tema aplicado nesta tela (para recriar se mudar nas configurações)
    private var appliedTheme = ThemeUtil.SYSTEM

    // Último endereço mostrado na barra (para distinguir "texto digitado" de "endereço da página")
    private var lastShownUrl = ""

    // Segundo plano (refere-se à aba ativa)
    private var bgEnabled = false
    private var mediaPlaying = false
    private var quitting = false
    private val stopServiceRunnable = Runnable { stopBackgroundService() }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* o serviço roda mesmo sem a notificação visível */ }

    /** Recebe os botões "Pausar/Tocar" e "Parar" da notificação. */
    private val pageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PlaybackService.ACTION_TOGGLE_PAGE -> active.web?.evaluateJavascript(TOGGLE_JS, null)
                PlaybackService.ACTION_SEEK_PAGE -> {
                    val sec = intent.getIntExtra(PlaybackService.EXTRA_SECONDS, 0)
                    active.web?.evaluateJavascript(
                        "(function(){var v=document.querySelector('video');if(v){v.currentTime=Math.max(0,v.currentTime+($sec));}})();",
                        null
                    )
                }
                PlaybackService.ACTION_QUIT_APP -> quitApp()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeUtil.wrap(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        ThemeUtil.applySystemBars(this)

        root = findViewById(R.id.root)
        ui = findViewById(R.id.browser_ui)
        topBar = findViewById(R.id.top_bar)
        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnHome = findViewById(R.id.btn_home)
        btnReload = findViewById(R.id.btn_reload)
        btnMenu = findViewById(R.id.btn_menu)
        btnShield = findViewById(R.id.btn_shield)
        btnBg = findViewById(R.id.btn_bg)
        btnTabs = findViewById(R.id.btn_tabs)
        btnTabsBox = findViewById(R.id.btn_tabs_box)
        fullscreen = findViewById(R.id.fullscreen)
        webHolder = findViewById(R.id.web_holder)
        startContainer = findViewById(R.id.start_container)
        suggestionsBox = findViewById(R.id.suggestions_box)
        tabSwitcherBox = findViewById(R.id.tab_switcher_box)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            ui.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        settings = Prefs(this)
        appliedTheme = settings.themeMode
        adBlocker = AdBlocker(this)
        adBlocker.enabled = settings.adblockDefault
        youtubeScript = assets.open("youtube_adblock.js").bufferedReader().use { it.readText() }
        bgEnabled = settings.backgroundPlay

        startPage = StartPageView(
            this, settings,
            onOpen = { navigate(it) },
            onSearch = { submitSearch(it) }
        )
        startContainer.addView(
            startPage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        urlSuggestions = SuggestionsView(this, settings, onPick = { submitSearch(it) })
        suggestionsBox.addView(
            urlSuggestions,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        tabSwitcher = TabSwitcherView(
            this,
            onSelect = { activate(it) },
            onClose = {
                closeTab(it)
                showTabSwitcher() // continua na lista, como no Chrome
            },
            onNew = { incognito -> openNewTab(incognito) }
        )
        tabSwitcherBox.addView(
            tabSwitcher,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        ContextCompat.registerReceiver(
            this,
            pageReceiver,
            IntentFilter().apply {
                addAction(PlaybackService.ACTION_TOGGLE_PAGE)
                addAction(PlaybackService.ACTION_SEEK_PAGE)
                addAction(PlaybackService.ACTION_QUIT_APP)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setupControls()

        if (savedInstanceState == null) Updater.check(this, settings, manual = false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    tabSwitcherBox.visibility == View.VISIBLE -> hideTabSwitcher()
                    suggestionsBox.visibility == View.VISIBLE -> hideUrlSuggestions(clearFocus = true)
                    customView != null -> exitFullscreen()
                    !active.showStart && canGoBackReal(active) -> active.web?.goBack()
                    // Aba aberta a partir de outra (link em nova janela): voltar fecha a aba e volta para a de origem.
                    closeToParent() -> Unit
                    // Tocando em segundo plano: o voltar só manda o app para trás, sem encerrar o som.
                    mediaPlaying && bgEnabled -> moveTaskToBack(true)
                    !active.showStart && settings.homeUrl() == null -> showStart()
                    // Sem mais para onde voltar: o app vai para segundo plano, mas nunca é fechado pelo voltar.
                    else -> moveTaskToBack(true)
                }
            }
        })

        restoreOrCreateTabs()
        val linkFromOutside = intent?.data?.toString()
        if (linkFromOutside != null) openNewTab(false, linkFromOutside) else activate(active)

        updateBgButton()
        if (bgEnabled) askNotificationPermissionOnce()
    }

    // ---------- Abas ----------

    private fun restoreOrCreateTabs() {
        if (tabs.isEmpty()) {
            val snaps = settings.loadTabs()
            if (snaps.isNotEmpty()) {
                snaps.forEach { s ->
                    tabs.add(Tab(TabStore.newId(), false).apply {
                        url = s.url
                        title = s.title
                        showStart = s.showStart || s.url.isBlank()
                    })
                }
                TabStore.activeId = tabs[settings.activeTabIndex.coerceIn(0, tabs.lastIndex)].id
            } else {
                val t = Tab(TabStore.newId(), false)
                settings.homeUrl()?.let {
                    t.url = it
                    t.showStart = false
                }
                tabs.add(t)
                TabStore.activeId = t.id
            }
            wipeIncognito() // limpa sobras de uma sessão anônima anterior
        }
        active = tabs.firstOrNull { it.id == TabStore.activeId } ?: tabs.first()
    }

    /** Abre uma aba nova (página de início ou o endereço dado) e passa para ela. */
    private fun openNewTab(incognito: Boolean, url: String? = null, parent: Tab? = null): Tab? {
        if (incognito && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Toast.makeText(this, "Este WebView não suporta a navegação anônima", Toast.LENGTH_LONG).show()
            return null
        }
        val t = Tab(TabStore.newId(), incognito, parent?.id)
        if (url != null) {
            t.url = url
            t.showStart = false
        }
        val at = if (parent != null) tabs.indexOf(parent) + 1 else tabs.size
        tabs.add(at.coerceIn(0, tabs.size), t)
        activate(t)
        return t
    }

    private fun activate(tab: Tab) {
        hideTabSwitcher()
        hideUrlSuggestions(clearFocus = true)
        // O som de uma aba não segue para a outra.
        mediaPlaying = false
        main.removeCallbacks(stopServiceRunnable)
        stopBackgroundService()

        active = tab
        TabStore.activeId = tab.id
        for (t in tabs) if (t !== tab) t.web?.visibility = View.GONE

        if (tab.showStart) {
            showStartUi(tab)
        } else {
            startContainer.visibility = View.GONE
            ensureWeb(tab).visibility = View.VISIBLE
            val shown = tab.web?.url?.takeIf { it != BLANK } ?: tab.url
            lastShownUrl = shown
            urlBar.setText(shown)
        }
        applyIncognitoUi()
        progress.visibility = View.GONE
        updateShield()
        updateNav()
        updateTabCount()
    }

    // ---------- Site para computador ----------

    private fun hostOf(url: String?): String? = url?.let { Uri.parse(it).host }

    private fun isDesktop(tab: Tab, host: String): Boolean = tab.desktopOverride ?: settings.isDesktopSite(host)

    /** Ajusta o agente de usuário (e o zoom) do WebView conforme o site que vai ser aberto. */
    private fun applyUserAgent(w: WebView, tab: Tab, url: String?) {
        val host = hostOf(url)
        val desktop = host != null && isDesktop(tab, host)
        val ua = if (desktop) desktopUa else mobileUa
        if (w.settings.userAgentString == ua) return
        w.settings.apply {
            userAgentString = ua
            // O site para computador usa uma tela larga (como o Chrome faz) e deixa dar zoom com os dedos.
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
            setSupportZoom(desktop)
            builtInZoomControls = desktop
            displayZoomControls = false
        }
    }

    private fun toggleDesktop() {
        val tab = active
        val w = tab.web ?: return
        val host = hostOf(w.url?.takeIf { it != BLANK } ?: tab.url) ?: return
        val now = !isDesktop(tab, host)
        if (tab.incognito) tab.desktopOverride = now else settings.setDesktopSite(host, now)
        applyUserAgent(w, tab, w.url)
        w.reload()
        Toast.makeText(
            this,
            if (now) "Site para computador: ligado" else "Site para computador: desligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showMenu() {
        val tab = active
        val host = if (tab.showStart) null else hostOf(tab.web?.url?.takeIf { it != BLANK } ?: tab.url)
        val desktop = host != null && isDesktop(tab, host)
        val popup = PopupMenu(this, btnMenu)
        popup.menu.add(0, MENU_DESKTOP, 0, if (desktop) "✓ Site para computador" else "Site para computador")
            .isEnabled = host != null
        popup.menu.add(0, MENU_NEW_TAB, 1, "Nova aba")
        popup.menu.add(0, MENU_NEW_INCOGNITO, 2, "Nova aba anônima")
        popup.menu.add(0, MENU_SETTINGS, 3, "Configurações")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_DESKTOP -> toggleDesktop()
                MENU_NEW_TAB -> openNewTab(false)
                MENU_NEW_INCOGNITO -> openNewTab(true)
                MENU_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun ensureWeb(tab: Tab): BackgroundWebView {
        tab.web?.let { return it }
        val w = createWeb(tab)
        applyUserAgent(w, tab, tab.url)
        val saved = tab.savedState
        tab.savedState = null
        if (saved != null && w.restoreState(saved) != null) {
            // A tela foi recriada (ex.: troca de tema): recarrega para a página já sair no tema novo.
            w.post { w.reload() }
            return w
        }
        if (tab.url.isNotBlank()) w.loadUrl(tab.url)
        return w
    }

    private fun createWeb(tab: Tab): BackgroundWebView {
        val w = BackgroundWebView(this)
        // A navegação anônima usa um perfil separado do WebView: cookies, armazenamento e cache isolados.
        if (tab.incognito) {
            try {
                WebViewCompat.setProfile(w, INCOGNITO_PROFILE)
            } catch (_: Exception) {
            }
        }
        w.visibility = View.GONE
        webHolder.addView(
            w,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        configure(w, tab)
        tab.web = w
        return w
    }

    private fun closeTab(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        val wasActive = tab === active
        tab.web?.let {
            webHolder.removeView(it)
            it.destroy()
        }
        tab.web = null
        tabs.removeAt(idx)
        if (tab.incognito && tabs.none { it.incognito }) wipeIncognito()

        if (tabs.isEmpty()) {
            val t = Tab(TabStore.newId(), false)
            tabs.add(t)
            activate(t)
            return
        }
        if (wasActive) {
            val next = tabs.firstOrNull { it.id == tab.parentId } ?: tabs[minOf(idx, tabs.lastIndex)]
            activate(next)
        } else {
            updateTabCount()
        }
    }

    /** Volta fechando a aba atual e passando para a que a abriu. Retorna falso se não há aba de origem. */
    private fun closeToParent(): Boolean {
        val parentId = active.parentId ?: return false
        if (tabs.none { it.id == parentId }) return false
        closeTab(active)
        return true
    }

    /** Apaga cookies, armazenamento e cache da navegação anônima (só quando não restam abas anônimas). */
    private fun wipeIncognito() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE)
            }
        } catch (_: Exception) {
            // O perfil pode estar em uso por um WebView ainda sendo destruído; é apagado na próxima abertura.
        }
    }

    private fun saveTabs() {
        val normal = tabs.filter { !it.incognito }
        settings.saveTabs(
            normal.map {
                val url = it.web?.url?.takeIf { u -> u != BLANK } ?: it.url
                Prefs.TabSnapshot(if (it.showStart) "" else url, it.title, it.showStart)
            }
        )
        settings.activeTabIndex = normal.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
    }

    private fun updateTabCount() {
        btnTabs.text = if (tabs.size > 99) "99+" else tabs.size.toString()
    }

    private fun showTabSwitcher() {
        hideUrlSuggestions(clearFocus = true)
        tabSwitcher.render(tabs, active.id)
        tabSwitcherBox.visibility = View.VISIBLE
    }

    private fun hideTabSwitcher() {
        tabSwitcherBox.visibility = View.GONE
    }

    /** Barra de cima roxa e captura de tela bloqueada enquanto a aba ativa é anônima. */
    private fun applyIncognitoUi() {
        val inc = active.incognito
        topBar.setBackgroundColor(ContextCompat.getColor(this, if (inc) R.color.incognito_bar else R.color.card))
        urlBar.hint = if (inc) "Anônima: pesquisar ou digitar endereço" else "Pesquisar ou digitar endereço"
        urlSuggestions.disabled = inc
        if (inc) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ---------- WebView de cada aba ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(w: BackgroundWebView, tab: Tab) {
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Links que abrem em nova janela viram uma nova aba (só com toque do usuário; pop-ups automáticos são barrados).
            setSupportMultipleWindows(true)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }

        // Canal de mensagens da página do YouTube para o app (só aceita origens do YouTube).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                w, "NvBridge", setOf("https://*.youtube.com", "https://youtube.com")
            ) { view, message, _, _, _ ->
                if (view !== active.web) return@addWebMessageListener // só a aba ativa controla o segundo plano
                val data = message.data.orEmpty()
                when {
                    data == "playing" -> onMedia(true)
                    data == "paused" -> onMedia(false)
                    data.startsWith("diag:") -> android.util.Log.i("NvDiag", data.removePrefix("diag:"))
                }
            }
        }

        // O ideal é rodar o script antes de qualquer código da página (necessário para limpar o player).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                w, youtubeScript, setOf("https://*.youtube.com", "https://youtube.com")
            )
        }

        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!request.isForMainFrame && adBlocker.shouldBlock(request.url)) {
                    tab.blocked.incrementAndGet()
                    if (tab === active) scheduleCounter()
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                // Só navega por http/https; esquemas como intent: e market: são ignorados.
                if (scheme != "http" && scheme != "https") return true
                // Clicou num link: o site de destino pode ter a sua própria escolha (celular ou computador).
                if (request.isForMainFrame) applyUserAgent(view, tab, request.url.toString())
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                newPage(tab, url)
                showUrl(tab, url)
                // Plano B se o navegador não suportar script de início de documento.
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                    (Uri.parse(url).host ?: "").endsWith("youtube.com")
                ) {
                    view.evaluateJavascript(youtubeScript, null)
                }
                if (tab === active) updateNav()
            }

            /** O YouTube troca de vídeo sem recarregar a página; essa chamada pega esse caso. */
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                newPage(tab, url)
                showUrl(tab, url)
                if (tab === active) updateNav()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (tab.pendingClearHistory && url == BLANK) {
                    tab.pendingClearHistory = false
                    view.clearHistory()
                }
                showUrl(tab, url)
                if (tab === active) updateNav()
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (tab !== active) return
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title.orEmpty()
                if (tab === active && mediaPlaying) PlaybackService.update(mediaTitle(), null)
            }

            /**
             * Players com DRM (Spotify, Netflix…) pedem a "mídia protegida". Só essa permissão é liberada;
             * câmera, microfone e o resto continuam negados.
             */
            override fun onPermissionRequest(request: PermissionRequest) {
                val drm = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                if (drm.isNotEmpty()) request.grant(drm.toTypedArray()) else request.deny()
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (tab === active) enterFullscreen(view, callback) else callback.onCustomViewHidden()
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false // pop-up automático: barrado
                val child = Tab(TabStore.newId(), tab.incognito, tab.id).apply { showStart = false }
                tabs.add((tabs.indexOf(tab) + 1).coerceIn(0, tabs.size), child)
                val childWeb = createWeb(child)
                (resultMsg.obj as WebView.WebViewTransport).webView = childWeb
                resultMsg.sendToTarget()
                activate(child)
                return true
            }

            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.web === window }?.let { closeTab(it) }
            }
        }
    }

    private fun setupControls() {
        btnBack.setOnClickListener {
            when {
                !active.showStart && canGoBackReal(active) -> active.web?.goBack()
                closeToParent() -> Unit
                !active.showStart && settings.homeUrl() == null -> showStart()
            }
        }
        btnForward.setOnClickListener {
            if (!active.showStart) active.web?.takeIf { it.canGoForward() }?.goForward()
        }
        btnHome.setOnClickListener { goHome() }
        btnTabsBox.setOnClickListener {
            if (tabSwitcherBox.visibility == View.VISIBLE) hideTabSwitcher() else showTabSwitcher()
        }
        btnReload.setOnClickListener {
            if (active.showStart) startPage.refresh() else active.web?.reload()
        }
        btnMenu.setOnClickListener { showMenu() }
        btnShield.setOnClickListener {
            adBlocker.enabled = !adBlocker.enabled
            updateShield()
            if (!active.showStart) active.web?.reload()
        }
        btnBg.setOnClickListener { toggleBackground() }

        urlBar.setSelectAllOnFocus(true)
        urlBar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val text = v.text.toString()
                hideUrlSuggestions(clearFocus = true)
                if (text.isNotBlank()) submitSearch(text)
                true
            } else false
        }
        // Pesquisas recentes sob a barra de endereço: aparecem ao tocar nela e filtram conforme você digita.
        urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) refreshUrlSuggestions() else hideUrlSuggestions(clearFocus = false)
        }
        urlBar.doAfterTextChanged { if (urlBar.hasFocus()) refreshUrlSuggestions() }
    }

    // ---------- Pesquisas recentes ----------

    private fun refreshUrlSuggestions() {
        val typed = urlBar.text.toString()
        // Com o endereço da página na barra (recém-focada), mostra todas as recentes em vez de filtrar por ele.
        val filter = if (typed == lastShownUrl) "" else typed
        suggestionsBox.visibility = if (urlSuggestions.update(filter)) View.VISIBLE else View.GONE
    }

    private fun hideUrlSuggestions(clearFocus: Boolean) {
        suggestionsBox.visibility = View.GONE
        if (clearFocus) {
            urlBar.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBar.windowToken, 0)
        }
    }

    /** Pesquisa ou abre o que foi digitado; pesquisas (não endereços) entram no histórico, menos na aba anônima. */
    private fun submitSearch(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        hideUrlSuggestions(clearFocus = true)
        val isSearch = t.contains(' ') || !t.contains('.')
        if (isSearch && settings.saveSearchHistory && !active.incognito) settings.addSearch(t)
        navigate(toUrl(t))
    }

    // ---------- Navegação e página de início ----------

    private fun navigate(url: String) = navigateTab(active, url)

    private fun navigateTab(tab: Tab, url: String) {
        val existed = tab.web != null
        tab.url = url
        tab.showStart = false
        tab.savedState = null
        val w = ensureWeb(tab) // se acabou de criar, já carregou tab.url
        if (existed) {
            applyUserAgent(w, tab, url)
            w.loadUrl(url)
        }
        if (tab === active) {
            startContainer.visibility = View.GONE
            w.visibility = View.VISIBLE
            updateNav()
        }
    }

    private fun showStart() {
        val t = active
        t.showStart = true
        t.url = ""
        t.title = ""
        // Descarrega a página (para qualquer som) e mostra a página de início.
        t.web?.let {
            t.pendingClearHistory = true
            it.loadUrl(BLANK)
            it.visibility = View.GONE
        }
        main.removeCallbacks(stopServiceRunnable)
        mediaPlaying = false
        stopBackgroundService()
        showStartUi(t)
        updateNav()
    }

    private fun showStartUi(t: Tab) {
        startContainer.visibility = View.VISIBLE
        startPage.setIncognito(t.incognito)
        lastShownUrl = ""
        urlBar.setText("")
        urlBar.clearFocus()
    }

    private fun goHome() {
        val home = settings.homeUrl()
        if (home == null) showStart() else navigate(home)
    }

    private fun showUrl(tab: Tab, url: String) {
        if (url == BLANK) return
        tab.url = url
        if (tab === active && !tab.showStart && !urlBar.hasFocus()) {
            lastShownUrl = url
            urlBar.setText(url)
        }
    }

    private fun toUrl(input: String): String {
        val t = input.trim()
        if (t.contains(' ') || !t.contains('.')) return settings.searchUrl(t)
        return Prefs.normalizeUrl(t)
    }

    /**
     * O histórico do WebView começa com a página em branco usada para parar o som ao abrir a página de início.
     * Voltar para ela não é voltar de verdade: nesse ponto o certo é mostrar a página de início (ou sair).
     */
    private fun canGoBackReal(tab: Tab): Boolean {
        val w = tab.web ?: return false
        if (!w.canGoBack()) return false
        val list = w.copyBackForwardList()
        val i = list.currentIndex
        return i > 0 && list.getItemAtIndex(i - 1).url != BLANK
    }

    private fun updateNav() {
        val canBack = !active.showStart &&
            (canGoBackReal(active) || active.parentId != null || settings.homeUrl() == null)
        btnBack.alpha = if (canBack) 1f else 0.35f
        val canForward = !active.showStart && active.web?.canGoForward() == true
        btnForward.alpha = if (canForward) 1f else 0.35f
    }

    // ---------- Contador (por página, da aba ativa) ----------

    /** Zera o contador da aba quando a página (ou o "vídeo" dentro do YouTube) muda. */
    private fun newPage(tab: Tab, url: String) {
        if (url == tab.pageUrl) return
        tab.pageUrl = url
        tab.blocked.set(0)
        if (tab === active) updateShield()
    }

    private fun updateShield() {
        btnShield.text = if (adBlocker.enabled) "🛡 ${active.blocked.get()}" else "🛡 off"
        btnShield.alpha = if (adBlocker.enabled) 1f else 0.5f
    }

    /** O contador sobe em threads de rede; atualiza a tela no máximo a cada 400 ms. */
    private fun scheduleCounter() {
        if (counterPosted) return
        counterPosted = true
        main.postDelayed({
            counterPosted = false
            updateShield()
        }, 400)
    }

    // ---------- Segundo plano ----------

    private fun updateBgButton() {
        btnBg.alpha = if (bgEnabled) 1f else 0.4f
    }

    private fun toggleBackground() {
        bgEnabled = !bgEnabled
        settings.backgroundPlay = bgEnabled
        updateBgButton()
        if (bgEnabled) {
            askNotificationPermissionOnce()
            if (mediaPlaying) startBackgroundService()
        } else {
            main.removeCallbacks(stopServiceRunnable)
            stopBackgroundService()
        }
        Toast.makeText(
            this,
            if (bgEnabled) "Segundo plano: ligado" else "Segundo plano: desligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !settings.askedNotifications) {
            settings.askedNotifications = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Chamado quando um vídeo da aba ativa começa ou para de tocar. */
    private fun onMedia(playing: Boolean) {
        mediaPlaying = playing
        main.removeCallbacks(stopServiceRunnable)
        if (playing) {
            startBackgroundService()
        } else {
            // Pausado: a notificação continua (para dar play de novo) e só some depois de um tempo parado.
            PlaybackService.update(mediaTitle(), false)
            main.postDelayed(stopServiceRunnable, PAUSED_KEEP_MS)
        }
    }

    private fun mediaTitle(): String =
        active.web?.title.orEmpty().removeSuffix(" - YouTube").ifBlank { active.title.removeSuffix(" - YouTube") }

    /** Botão "Encerrar" da notificação: fecha o app e o tira da lista de recentes. */
    private fun quitApp() {
        quitting = true
        saveTabs()
        main.removeCallbacks(stopServiceRunnable)
        stopBackgroundService()
        finishAndRemoveTask()
    }

    private fun startBackgroundService() {
        if (!bgEnabled) return
        val title = mediaTitle().ifBlank { "Reproduzindo" }
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_TITLE, title)
            )
        } catch (_: Exception) {
            // O Android só deixa iniciar o serviço com o app visível; se não deu, o som segue enquanto o processo viver.
        }
    }

    private fun stopBackgroundService() {
        stopService(Intent(this, PlaybackService::class.java))
    }

    // ---------- Tela cheia ----------

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        ui.visibility = View.GONE
        fullscreen.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fullscreen.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        fullscreen.removeView(view)
        fullscreen.visibility = View.GONE
        ui.visibility = View.VISIBLE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------- Ciclo de vida ----------

    override fun onPause() {
        super.onPause()
        saveTabs()
        // Avisa a página que o app saiu da tela, para ela não deixar o vídeo ser pausado por isso.
        if (bgEnabled) active.web?.evaluateJavascript(BG_ON_JS, null)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Seguindo o tema do sistema: se o sistema trocou entre claro e escuro, redesenha.
        if (settings.themeMode == ThemeUtil.SYSTEM) recreate()
    }

    override fun onResume() {
        super.onResume()
        // Voltando das configurações com outro tema: recria a tela já com as novas cores.
        if (settings.themeMode != appliedTheme) {
            recreate()
            return
        }
        active.web?.evaluateJavascript(BG_OFF_JS, null)
        // Voltando das configurações: relê o que pode ter mudado.
        bgEnabled = settings.backgroundPlay
        updateBgButton()
        if (active.showStart) startPage.refresh()
        updateNav()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openNewTab(false, it.toString()) }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        stopBackgroundService()
        try {
            unregisterReceiver(pageReceiver)
        } catch (_: Exception) {
        }
        // Os WebViews pertencem a esta Activity: guarda o histórico de cada aba e os destrói.
        // As abas (endereço, título, histórico) continuam em TabStore e são reabertas pela nova Activity.
        for (t in tabs) {
            t.web?.let { w ->
                val state = Bundle()
                w.saveState(state)
                t.savedState = state
                (w.parent as? ViewGroup)?.removeView(w)
                w.destroy()
            }
            t.web = null
        }
        // "Encerrar": as abas (inclusive as anônimas) não ficam na memória para a próxima abertura.
        if (quitting) {
            tabs.clear()
            TabStore.activeId = -1
        }
        super.onDestroy()
        // Encerrar de verdade: sem processo sobrando em segundo plano.
        if (quitting) android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        private const val BLANK = "about:blank"
        private const val INCOGNITO_PROFILE = "anonima"
        private const val MENU_DESKTOP = 1
        private const val MENU_NEW_TAB = 2
        private const val MENU_NEW_INCOGNITO = 3
        private const val MENU_SETTINGS = 4
        private const val PAUSED_KEEP_MS = 10 * 60_000L

        // Os botões da notificação são pedidos do usuário: liberam a pausa por um instante.
        private const val TOGGLE_JS =
            "(function(){var v=document.querySelector('video');if(!v)return;window.__nvAllowPause=true;" +
                "if(v.paused){v.play();}else{v.pause();}setTimeout(function(){window.__nvAllowPause=false;},800);})();"
        private const val BG_ON_JS = "window.__nvBg=true;window.__nvBgSince=Date.now();window.__nvResumes=0;"
        private const val BG_OFF_JS = "window.__nvBg=false;"
    }
}
