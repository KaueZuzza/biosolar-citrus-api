/* Alertas ativos calculados pelo servidor (telemetria.alertas): somem sozinhos quando o problema é resolvido. */
BS.componentes.alertas = (function () {
  var f = BS.fmt;

  function atualizar(tel) {
    var alertas = tel.alertas || [];
    var graves = alertas.filter(function (a) { return a.nivel === 'CRITICO' || a.nivel === 'EMERGENCIA'; }).length;
    var total = document.getElementById('alertas-total');
    total.className = 'pill ' + (graves ? 'crit' : alertas.length ? 'warn' : 'ok');
    BS.dom.texto(total, alertas.length ? alertas.length + (alertas.length === 1 ? ' alerta' : ' alertas') : '✅ Nenhum');
    BS.dom.html('alertas', alertas.length ? alertas.map(function (a) {
      var sev = BS.status.severidade[a.nivel] || BS.status.severidade.INFO;
      return '<li class="' + sev.cls + '"><b>' + sev.icone + ' ' + f.esc(a.titulo) + '</b>' + f.esc(a.mensagem) + '</li>';
    }).join('') : '<li class="vazio-lista">Nenhum alerta no momento: a fazenda está operando normalmente.</li>');
  }

  return { atualizar: atualizar };
})();
