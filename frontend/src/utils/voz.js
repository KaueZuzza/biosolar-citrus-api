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

  /** Trechos de até ~220 caracteres, cortados no fim das frases: falas longas numa só leitura podem parar no meio
   *  em alguns navegadores. "212.400" e "65,2" não são cortados (o corte exige espaço depois da pontuação). */
  function trechos(texto) {
    var frases = String(texto || '').replace(/([.!?;:])\s+/g, '$1\u0000').split('\u0000');
    var saida = [];
    var atual = '';
    frases.forEach(function (frase) {
      frase = frase.trim();
      if (!frase) return;
      if (atual && (atual + ' ' + frase).length > 220) { saida.push(atual); atual = frase; }
      else atual = atual ? atual + ' ' + frase : frase;
    });
    if (atual) saida.push(atual);
    return saida;
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
      var partes = trechos(texto);
      var chamado = false;
      var fim = function () { if (!chamado) { chamado = true; if (aoTerminar) aoTerminar(); } };
      if (!partes.length) { fim(); return; }
      var voz = vozPtBr();
      partes.forEach(function (parte, i) {
        var fala = new SpeechSynthesisUtterance(parte);
        fala.lang = 'pt-BR';
        fala.rate = 1.02;
        if (voz) fala.voice = voz;
        // Erro em qualquer trecho (inclusive cancel) encerra; o fim normal é o do último trecho
        fala.onerror = fim;
        if (i === partes.length - 1) fala.onend = fim;
        window.speechSynthesis.speak(fala);
      });
    },
    parar: function () { if (suportado()) window.speechSynthesis.cancel(); }
  };

  if (suportado()) window.speechSynthesis.onvoiceschanged = function () { /* carrega a lista de vozes */ };
})();
