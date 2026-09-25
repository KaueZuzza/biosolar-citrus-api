/* Notificações úteis (a partir dos eventos do servidor) + toasts. Mantidas apenas em memória. */
(function () {
  var f = BS.fmt;

  /** Toast acessível: emergências usam anúncio assertivo; o restante é educado (polite). */
  BS.toast = function (opcoes) {
    var sev = BS.status.severidade[opcoes.severidade] || BS.status.severidade.INFO;
    var el = document.createElement('div');
    el.className = 'toast ' + sev.cls;
    el.setAttribute('role', opcoes.severidade === 'EMERGENCIA' ? 'alert' : 'status');
    el.innerHTML = '<span aria-hidden="true">' + (opcoes.icone || sev.icone) + '</span>' +
      '<div><b>' + f.esc(opcoes.titulo) + '</b><span class="desc">' + f.esc(opcoes.descricao || '') + '</span></div>' +
      '<button type="button" aria-label="Fechar notificação">✕</button>';
    el.querySelector('button').addEventListener('click', function () { el.remove(); });
    var caixa = document.getElementById('toasts');
    caixa.appendChild(el);
    while (caixa.children.length > 3) caixa.firstElementChild.remove();
    setTimeout(function () { el.remove(); }, opcoes.severidade === 'EMERGENCIA' ? 12000 : 7000);
  };
})();

BS.componentes.notificacoes = (function () {
  var f = BS.fmt;
  // Tipos que merecem notificação (evolução do nível e ações de simulação ficam só no histórico)
  var RELEVANTES = ['BLOQUEIO_EMERGENCIA', 'IRRIGACAO_CRITICA', 'IRRIGACAO_BLOQUEADA', 'RECUPERACAO_SISTEMA',
    'ALERTA_UMIDADE', 'ALERTA_RESERVATORIO', 'COMANDO_MANUAL', 'COMANDO_RECUSADO', 'IRRIGACAO_CONCLUIDA', 'PROTECAO_SATURACAO', 'CADASTRO'];
  var itens = [];
  var ultimoId = null;

  function render() {
    var naoLidas = itens.filter(function (i) { return !i.lida; }).length;
    var badge = document.getElementById('notif-count');
    badge.hidden = naoLidas === 0;
    BS.dom.texto(badge, naoLidas > 99 ? '99+' : String(naoLidas));
    BS.dom.attr('btn-notif', 'aria-label', 'Notificações: ' + naoLidas + ' não lida(s)');
    BS.dom.html('notif-lista', itens.length ? itens.slice(0, 40).map(function (i) {
      var sev = BS.status.severidade[i.e.severidade] || BS.status.severidade.INFO;
      return '<li class="' + sev.cls + (i.lida ? ' lida' : '') + '"><b>' + (BS.status.tipoEvento[i.e.tipo] || '') + ' ' + f.esc(i.e.titulo) +
        '</b><br>' + f.esc(i.e.descricao) + '<br><span class="muted">' + f.hora(i.e.instante) + '</span></li>';
    }).join('') : '<li>Nenhuma notificação ainda.</li>');
  }

  /** Recebe os eventos recentes da telemetria (mais novos primeiro). */
  function processar(eventos) {
    if (!eventos || !eventos.length) return;
    var maior = eventos[0].id;
    if (ultimoId === null) { ultimoId = maior; return; }  // primeira carga: não notifica o passado
    var novos = eventos.filter(function (e) { return e.id > ultimoId; }).reverse();
    ultimoId = Math.max(ultimoId, maior);
    var mudou = false;
    novos.forEach(function (e) {
      if (RELEVANTES.indexOf(e.tipo) < 0) return;
      itens.unshift({ e: e, lida: false });
      mudou = true;
      BS.toast({ severidade: e.severidade, icone: BS.status.tipoEvento[e.tipo], titulo: e.titulo, descricao: ' ' + e.descricao });
      if (e.severidade === 'EMERGENCIA') BS.dom.anunciar(e.titulo + '. ' + e.descricao, true);
    });
    if (mudou) { itens = itens.slice(0, 100); render(); }
  }

  function iniciar() {
    var botao = document.getElementById('btn-notif');
    var painel = document.getElementById('painel-notif');
    function fechar() { painel.hidden = true; botao.setAttribute('aria-expanded', 'false'); }
    botao.addEventListener('click', function () {
      var abrir = painel.hidden;
      painel.hidden = !abrir;
      botao.setAttribute('aria-expanded', String(abrir));
    });
    document.getElementById('notif-lidas').addEventListener('click', function () {
      itens.forEach(function (i) { i.lida = true; });
      render();
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !painel.hidden) { fechar(); botao.focus(); } });
    document.addEventListener('click', function (e) { if (!painel.hidden && !e.target.closest('.notif-wrap')) fechar(); });
    render();
  }

  return { iniciar: iniciar, processar: processar };
})();
