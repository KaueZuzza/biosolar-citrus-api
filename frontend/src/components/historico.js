/* Linha do tempo dos eventos (gravados no banco pelo servidor). */
BS.componentes.historico = (function () {
  var f = BS.fmt;
  var eventos = [];
  var filtro = 'todos';
  var maiorIdVisto = null;

  function passaFiltro(e) {
    if (filtro === 'todos') return true;
    if (filtro === 'criticos') return e.severidade === 'CRITICO' || e.severidade === 'EMERGENCIA';
    return e.origem === filtro;
  }

  function render() {
    var lista = eventos.filter(passaFiltro);
    var el = document.getElementById('timeline');
    if (!lista.length) {
      BS.dom.html(el, '<li class="vazio-lista" style="display:block">Nenhum evento para este filtro.</li>');
      return;
    }
    var limiteNovo = el.__maiorId || 0;
    BS.dom.html(el, lista.map(function (e) {
      var sev = BS.status.severidade[e.severidade] || BS.status.severidade.INFO;
      var tags = '';
      if (e.regra) tags += '<span class="pill rule">' + f.esc(e.regra) + '</span>';
      if (e.talhaoId) tags += '<span class="pill neutral">Talhão ' + f.esc(e.talhaoId) + '</span>';
      tags += '<span class="pill ' + sev.cls + '">' + sev.icone + ' ' + sev.rotulo + '</span>';
      if (e.nivelReservatorio !== null) tags += '<span class="pill neutral">💧 ' + f.pct(e.nivelReservatorio) + '</span>';
      return '<li' + (limiteNovo && e.id > limiteNovo ? ' class="novo"' : '') + '>' +
        '<div class="tl-hora">' + f.hora(e.instante) + '<small>fazenda ' + f.horaSimulada(e.horaSimulada) + '</small></div>' +
        '<span class="tl-icone ' + sev.cls + '" aria-hidden="true">' + (BS.status.tipoEvento[e.tipo] || '•') + '</span>' +
        '<div class="tl-corpo"><b>' + f.esc(e.titulo) + '</b><p>' + f.esc(e.descricao) + '</p><div class="tl-tags">' + tags + '</div></div></li>';
    }).join(''));
    el.__maiorId = eventos.length ? eventos[0].id : 0;
  }

  function atualizar(lista) {
    var topo = lista.length ? lista[0].id : null;
    if (topo === maiorIdVisto && lista.length === eventos.length) return;
    maiorIdVisto = topo;
    eventos = lista;
    render();
  }

  function iniciar() {
    document.getElementById('hist-filtros').addEventListener('click', function (e) {
      var b = e.target.closest('[data-filtro]');
      if (!b) return;
      filtro = b.getAttribute('data-filtro');
      BS.dom.$$('#hist-filtros [data-filtro]').forEach(function (x) { x.setAttribute('aria-pressed', String(x === b)); });
      document.getElementById('timeline').__html = null;
      render();
    });
  }

  return { iniciar: iniciar, atualizar: atualizar };
})();
