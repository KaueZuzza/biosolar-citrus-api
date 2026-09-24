/* Alertas ativos: calculados pelo servidor a partir do estado atual (GET /telemetria → alertas). */
BS.componentes.alertas = (function () {
  var f = BS.fmt;

  function atualizar(tel) {
    var lista = tel.alertas || [];
    var graves = lista.some(function (a) { return a.nivel === 'EMERGENCIA' || a.nivel === 'CRITICO'; });
    var atencao = lista.some(function (a) { return a.nivel === 'ATENCAO'; });
    var pill = document.getElementById('alertas-total');
    pill.className = 'pill ' + (graves ? 'crit' : atencao ? 'warn' : 'neutral');
    BS.dom.texto(pill, String(lista.length));
    BS.dom.html('alertas', lista.length ? lista.map(function (a) {
      var sev = BS.status.severidade[a.nivel] || BS.status.severidade.INFO;
      return '<li class="' + sev.cls + '"><b>' + sev.icone + ' ' + f.esc(a.titulo) + '</b>' + f.esc(a.mensagem) + '</li>';
    }).join('') : '<li>✅ Nenhum alerta ativo.</li>');
  }

  return { atualizar: atualizar };
})();
