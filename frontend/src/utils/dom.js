/* Utilitários de DOM: atualizações "no lugar" para não perder o foco do teclado a cada leitura. */
(function () {
  BS.dom = {
    $: function (sel, raiz) { return (raiz || document).querySelector(sel); },
    $$: function (sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); },

    /** Altera o texto apenas se mudou (evita reflow e releitura por leitores de tela). */
    texto: function (el, valor) {
      if (typeof el === 'string') el = document.getElementById(el);
      if (el && el.textContent !== String(valor)) el.textContent = valor;
    },

    attr: function (el, nome, valor) {
      if (typeof el === 'string') el = document.getElementById(el);
      if (!el) return;
      if (valor === null || valor === undefined || valor === false) el.removeAttribute(nome);
      else if (el.getAttribute(nome) !== String(valor)) el.setAttribute(nome, valor);
    },

    html: function (el, valor) {
      if (typeof el === 'string') el = document.getElementById(el);
      if (el && el.__html !== valor) { el.innerHTML = valor; el.__html = valor; }
    },

    /** Anuncia uma mensagem a leitores de tela (polite) ou com urgência (assertive). */
    anunciar: function (mensagem, urgente) {
      var el = document.getElementById(urgente ? 'anuncio-critico' : 'anuncio-status');
      if (!el) return;
      el.textContent = '';
      setTimeout(function () { el.textContent = mensagem; }, 60);
    },

    /** Abre um <dialog> modal devolvendo o foco ao elemento de origem ao fechar. */
    abrirDialogo: function (dlg) {
      var origem = document.activeElement;
      if (!dlg.open) dlg.showModal();
      dlg.addEventListener('close', function volta() {
        dlg.removeEventListener('close', volta);
        if (origem && origem.focus) origem.focus();
      });
    }
  };

  // Botões [data-fechar] fecham o diálogo que os contém; clique no fundo também fecha.
  document.addEventListener('click', function (e) {
    var fechar = e.target.closest('[data-fechar]');
    if (fechar) {
      var dlg = fechar.closest('dialog');
      if (dlg) dlg.close();
      return;
    }
    if (e.target.tagName === 'DIALOG') e.target.close();
  });
})();
