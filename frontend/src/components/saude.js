/* Saúde do sistema: API, banco de dados, simulador e dispositivos simulados (GET /saude). */
BS.componentes.saude = (function () {
  var f = BS.fmt;
  var ROTULO = { UP: '🟢 OK', DOWN: '🔴 Fora do ar', DEGRADADO: '🟡 Degradado', ATRASADO: '🟡 Atrasado', INICIANDO: '⚙️ Iniciando', DESABILITADO: '⚪ Desabilitado' };

  function atualizar(s) {
    var pill = document.getElementById('saude-status');
    pill.className = 'pill ' + (s.status === 'UP' ? 'ok' : 'warn');
    BS.dom.texto(pill, s.status === 'UP' ? '🟢 Operacional' : '🟡 Degradado');
    var online = s.sensores.filter(function (x) { return x.status === 'ONLINE'; }).length;
    var linhas = [
      ['API REST', (ROTULO[s.api.status] || s.api.status) + ' · v' + s.versao],
      ['Banco de dados', (ROTULO[s.banco.status] || s.banco.status) + (s.banco.latenciaMs !== null ? ' · ' + s.banco.latenciaMs + ' ms' : '')],
      ['SGBD', String(s.banco.detalhe || '').split(' ').slice(0, 2).join(' ')],
      ['Simulador', (ROTULO[s.simulador.status] || s.simulador.status) + (s.simulador.latenciaMs !== null ? ' · ciclo há ' + s.simulador.latenciaMs + ' ms' : '')],
      ['Sensores', online + '/' + s.sensores.length + ' online'],
      ['Atuadores', s.atuadores.map(function (a) { return a.id + ' ' + (a.status === 'LIGADO' ? '🟢' : a.status === 'BLOQUEADO' ? '🔒' : '⚪'); }).join(' ')],
      ['Em operação há', f.horas(s.uptimeSegundos / 3600)]
    ];
    BS.dom.html('saude-lista', linhas.map(function (l) {
      return '<div><dt>' + f.esc(l[0]) + '</dt><dd>' + f.esc(l[1]) + '</dd></div>';
    }).join(''));
  }

  return { atualizar: atualizar };
})();
