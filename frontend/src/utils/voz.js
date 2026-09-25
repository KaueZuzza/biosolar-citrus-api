/* Leitura em voz (Web Speech API). Recurso opcional: a interface funciona sem ele. */
(function () {
  function suportado() {
    return 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;
  }

  function vozPtBr() {
    var vozes = window.speechSynthesis.getVoices() || [];
    return vozes.find(function (v) { return /pt[-_]BR/i.test(v.lang); }) ||
      vozes.find(function (v) { return /^pt/i.test(v.lang); }) || null;
  }

  BS.voz = {
    suportado: suportado,
    /** @param aoTerminar chamada quando a leitura acaba (ou falha), opcional */
    falar: function (texto, aoTerminar) {
      if (!suportado()) {
        BS.toast && BS.toast({ severidade: 'ATENCAO', titulo: 'Leitura em voz indisponível', descricao: 'Este navegador não suporta a Web Speech API.' });
        if (aoTerminar) aoTerminar();
        return;
      }
      window.speechSynthesis.cancel();
      var fala = new SpeechSynthesisUtterance(texto);
      fala.lang = 'pt-BR';
      fala.rate = 1.02;
      var voz = vozPtBr();
      if (voz) fala.voice = voz;
      if (aoTerminar) {
        var chamado = false;
        var fim = function () { if (!chamado) { chamado = true; aoTerminar(); } };
        fala.onend = fim;
        fala.onerror = fim;
      }
      window.speechSynthesis.speak(fala);
    },
    parar: function () { if (suportado()) window.speechSynthesis.cancel(); }
  };

  if (suportado()) window.speechSynthesis.onvoiceschanged = function () { /* carrega a lista de vozes */ };
})();
