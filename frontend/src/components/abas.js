/* Seções do sistema (Visão geral, Talhões, Irrigação, Monitoramento, Relatórios, Gestão).
 * Navegação pelo endereço (#talhoes): funciona com links, voltar/avançar do navegador e favoritos.
 * Todas as seções continuam sendo atualizadas em segundo plano; só a exibição muda. */
BS.componentes.abas = (function () {
  var PADRAO = 'visao-geral';
  var atual = null;
  var ouvintes = [];

  function existentes() {
    return BS.dom.$$('.aba').map(function (s) { return s.getAttribute('data-aba'); });
  }

  function mostrar(nome, focar) {
    if (existentes().indexOf(nome) < 0) nome = PADRAO;
    if (nome === atual) return;
    atual = nome;
    BS.dom.$$('.aba').forEach(function (s) { s.hidden = s.getAttribute('data-aba') !== nome; });
    BS.dom.$$('.abas [data-aba]').forEach(function (a) {
      BS.dom.attr(a, 'aria-current', a.getAttribute('data-aba') === nome ? 'page' : null);
    });
    if (focar) {
      var titulo = document.getElementById('titulo-' + nome);
      if (titulo) titulo.focus({ preventScroll: true });
      window.scrollTo({ top: 0 });
    }
    ouvintes.forEach(function (fn) { try { fn(nome); } catch (e) { console.error(e); } });
  }

  function lerEndereco(focar) {
    var nome = (location.hash || '').replace('#', '');
    // Âncoras que não são seções (ex.: "Pular para o conteúdo") mantêm a seção atual
    if (nome && existentes().indexOf(nome) < 0 && atual !== null) return;
    mostrar(nome || PADRAO, focar);
  }

  /** Contador de alertas na aba Monitoramento (crítico em vermelho). */
  function atualizarAlertas(tel) {
    var alertas = (tel.alertas || []).filter(function (a) { return a.nivel !== 'INFO'; });
    var badge = document.getElementById('aba-alertas');
    badge.hidden = alertas.length === 0;
    BS.dom.texto(badge, String(alertas.length));
    var grave = alertas.some(function (a) { return a.nivel === 'CRITICO' || a.nivel === 'EMERGENCIA'; });
    badge.className = 'aba-badge' + (grave ? ' crit' : '');
    BS.dom.attr(badge, 'aria-label', alertas.length + ' alerta(s) ativo(s)');
  }

  function iniciar() {
    window.addEventListener('hashchange', function () { lerEndereco(true); });
    lerEndereco(false);
  }

  return {
    iniciar: iniciar,
    atualizar: atualizarAlertas,
    atual: function () { return atual; },
    ir: function (nome) { location.hash = nome; },
    aoMudar: function (fn) { ouvintes.push(fn); }
  };
})();
