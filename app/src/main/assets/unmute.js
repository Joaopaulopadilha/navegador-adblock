(function () {
  'use strict';
  if (window.__nvUnmuteInit) return;
  window.__nvUnmuteInit = true;

  // Tira do mudo o que estiver tocando nesta página (o app chama isso quando o botão de volume + é apertado).
  function unmute() {
    try {
      var p = document.getElementById('movie_player'); // player do YouTube
      if (p) {
        try { if (p.isMuted && p.isMuted() && p.unMute) p.unMute(); } catch (e) {}
        try { if (p.getVolume && p.getVolume() === 0 && p.setVolume) p.setVolume(100); } catch (e) {}
      }
      var els = document.querySelectorAll('video,audio');
      for (var i = 0; i < els.length; i++) {
        try {
          if (els[i].muted) els[i].muted = false;
          if (els[i].volume === 0) els[i].volume = 1;
        } catch (e) {}
      }
      var b = document.querySelector('.ytp-unmute'); // aviso "toque para ativar o som"
      if (b && b.offsetParent !== null) { try { b.click(); } catch (e) {} }
    } catch (e) {}
  }
  window.__nvUnmute = unmute;

  // Canal com o app, para alcançar também os vídeos dentro de iframes (cada frame se apresenta uma vez).
  try {
    if (window.NvAudio) {
      window.NvAudio.onmessage = function (e) { if (e.data === 'unmute') unmute(); };
      window.NvAudio.postMessage('hi');
    }
  } catch (e) {}
})();
