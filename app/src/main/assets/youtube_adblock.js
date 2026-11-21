(function () {
  'use strict';
  if (window.__nvAdblock) return;
  window.__nvAdblock = true;

  // ---- 0) Segundo plano: faz a página achar que está sempre visível, para o YouTube não pausar o vídeo ----
  function notify(msg) {
    try { if (window.NvBridge) window.NvBridge.postMessage(msg); } catch (e) {}
  }
  // Diagnóstico: o app grava essas mensagens no logcat (tag NvDiag).
  function diag(msg) { notify('diag:' + String(msg).slice(0, 400)); }

  // Estado REAL de visibilidade da página (o que o navegador diz, antes de eu esconder).
  function realHidden() {
    try { return Object.getOwnPropertyDescriptor(Document.prototype, 'hidden').get.call(document); } catch (e) { return 'n/a'; }
  }

  try {
    var always = function (name, value) {
      try { Object.defineProperty(document, name, { configurable: true, get: function () { return value; } }); } catch (e) {}
    };
    always('hidden', false);
    always('visibilityState', 'visible');
    always('webkitHidden', false);
    always('webkitVisibilityState', 'visible');

    // Registrado primeiro e na fase de captura da window: roda antes de qualquer ouvinte do YouTube.
    ['visibilitychange', 'webkitvisibilitychange', 'freeze'].forEach(function (type) {
      window.addEventListener(type, function (e) {
        if (window.__nvBg) diag('evento ' + type + ' (bloqueado) real_hidden=' + realHidden());
        e.stopImmediatePropagation();
      }, true);
    });
  } catch (e) {}

  // Quando o app vai para segundo plano, o YouTube (ou o navegador) pode pausar o vídeo na hora.
  // Nos primeiros segundos depois disso, pausas que não vieram do botão da notificação são desfeitas.
  // O app avisa a página por window.__nvBg / __nvBgSince (veja MainActivity.onPause).
  function guarding() {
    return !!window.__nvBg && !window.__nvAllowPause && (Date.now() - (window.__nvBgSince || 0) < 5000);
  }

  try {
    var origPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function () {
      var g = guarding();
      if (window.__nvBg) {
        diag('pause() guard=' + g + ' real_hidden=' + realHidden() + ' | ' + String(new Error().stack || '').replace(/\s+/g, ' ').slice(0, 260));
      }
      if (g) return;
      return origPause.apply(this, arguments);
    };
  } catch (e) {}

  // Posição e duração para a barra de tempo da notificação: "time:<posição ms>:<duração ms ou -1>:<1 tocando/0 parado>"
  function reportTime(v, playing) {
    try {
      var d = v.duration;
      notify('time:' + Math.round((v.currentTime || 0) * 1000) + ':' + (isFinite(d) ? Math.round(d * 1000) : -1) + ':' + (playing ? 1 : 0));
    } catch (e) {}
  }
  ['seeked', 'durationchange', 'loadedmetadata'].forEach(function (type) {
    document.addEventListener(type, function (e) {
      if (e.target && e.target.tagName === 'VIDEO') reportTime(e.target, !e.target.paused);
    }, true);
  });
  setInterval(function () {
    var v = document.querySelector('video');
    if (v && !v.paused) reportTime(v, true);
  }, 4000);

  document.addEventListener('playing', function (e) { if (e.target && e.target.tagName === 'VIDEO') { notify('playing'); reportTime(e.target, true); } }, true);
  document.addEventListener('pause', function (e) {
    var v = e.target;
    if (!v || v.tagName !== 'VIDEO') return;
    if (window.__nvBg) {
      diag('evento pause guard=' + guarding() + ' real_hidden=' + realHidden() + ' ended=' + v.ended + ' t=' + Number(v.currentTime).toFixed(1));
    }
    if (guarding() && !v.ended && ((window.__nvResumes = (window.__nvResumes || 0) + 1) <= 3)) {
      setTimeout(function () {
        try {
          var p = v.play();
          if (p && p.catch) p.catch(function (err) { diag('play() falhou: ' + err); });
        } catch (err) { diag('play() erro: ' + err); }
      }, 0);
      return;
    }
    notify('paused');
    reportTime(v, false);
  }, true);
  document.addEventListener('ended', function (e) { if (e.target && e.target.tagName === 'VIDEO') { notify('paused'); reportTime(e.target, false); } }, true);
  // ---- 1) Remove os campos de anúncio das respostas do player (a causa dos anúncios) ----
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];

  function prune(o) {
    try {
      if (o && typeof o === 'object') {
        for (var i = 0; i < AD_KEYS.length; i++) {
          if (AD_KEYS[i] in o) delete o[AD_KEYS[i]];
        }
        if (o.playerResponse && typeof o.playerResponse === 'object') prune(o.playerResponse);
        if (o.response && typeof o.response === 'object' && o.response.playerResponse) prune(o.response.playerResponse);
      }
    } catch (e) {}
    return o;
  }

  // JSON.parse (usado pelo YouTube em várias respostas)
  var origParse = JSON.parse;
  JSON.parse = function () {
    var r = origParse.apply(this, arguments);
    return prune(r);
  };

  // fetch(...).json()
  try {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.apply(this, arguments).then(prune);
    };
  } catch (e) {}

  // Resposta inicial do player embutida na página
  try {
    var initial;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return initial; },
      set: function (v) { initial = prune(v); }
    });
  } catch (e) {}

  // ---- 2) Esconde o que sobrar de anúncio na tela ----
  var CSS = [
    '#player-ads', '.video-ads', '.ytp-ad-module', '.ytp-ad-overlay-container', '.ytp-ad-image-overlay',
    '#masthead-ad', 'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-banner-promo-renderer',
    'ytd-statement-banner-renderer', 'ytd-promoted-sparkles-web-renderer', 'ytd-display-ad-renderer',
    'ytd-companion-slot-renderer', 'ytd-player-legacy-desktop-watch-ads-renderer',
    'ytm-promoted-sparkles-web-renderer', 'ytm-companion-slot', 'ytm-promoted-video-renderer',
    'ytm-ad-slot-renderer', 'ytm-statement-banner-renderer', 'ytm-companion-ad-renderer',
    '.ad-container', '.ad-div',
    // convites para instalar o app do YouTube
    'ytm-open-app-banner', 'ytm-open-app-promo-renderer', 'ytm-app-promo-renderer', '.mweb-open-app-banner'
  ].join(',') + '{display:none !important}';

  function addCss() {
    var parent = document.head || document.documentElement;
    if (!parent) { setTimeout(addCss, 20); return; }
    var s = document.createElement('style');
    s.textContent = CSS;
    parent.appendChild(s);
  }
  addCss();

  // Botão "Abrir app" / "Open app": o nome das classes muda, então localiza pelo texto.
  var OPEN_APP = /^(abrir app|abrir o app|usar o app|open app|open in app|get app)$/i;
  var lastScan = 0;
  function hideOpenAppButtons() {
    var now = Date.now();
    if (now - lastScan < 1000) return;
    lastScan = now;
    var els = document.querySelectorAll('a, button, ytm-button-renderer, [role="button"]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.dataset && el.dataset.nvHidden) continue;
      var t = (el.textContent || '').trim();
      if (t.length < 20 && OPEN_APP.test(t)) {
        el.style.setProperty('display', 'none', 'important');
        if (el.dataset) el.dataset.nvHidden = '1';
      }
    }
  }

  // ---- 3) Plano B: se um anúncio ainda tocar, pula rápido ----
  var touched = false;
  setInterval(function () {
    var player = document.querySelector('#movie_player, .html5-video-player');
    var video = document.querySelector('video');
    var showing = !!(player && player.classList.contains('ad-showing'));

    if (showing && video) {
      touched = true;
      try {
        video.muted = true;
        video.playbackRate = 16;
        if (isFinite(video.duration) && video.duration > 0) video.currentTime = video.duration;
      } catch (e) {}
    } else if (touched && video) {
      touched = false;
      try { video.muted = false; video.playbackRate = 1; } catch (e) {}
    }

    hideOpenAppButtons();

    var skips = document.querySelectorAll(
      '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button'
    );
    for (var i = 0; i < skips.length; i++) { try { skips[i].click(); } catch (e) {} }
  }, 250);
})();
