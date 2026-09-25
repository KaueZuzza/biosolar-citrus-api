/* Relatórios: indicadores acumulados desde a última restauração do cenário (GET /indicadores). */
BS.componentes.periodo = (function () {
  var f = BS.fmt;

  function atualizar(ind) {
    var e = ind.energia, est = ind.estatisticasReservatorio, a = ind.acionamentos;
    var solar = e.energiaConsumidaKwh > 0 ? e.energiaSolarKwh / e.energiaConsumidaKwh * 100 : 100;
    var linhas = [
      ['Água consumida', f.num(ind.aguaConsumidaM3, 1) + ' m³'],
      ['Energia das bombas', f.num(e.energiaConsumidaKwh, 1) + ' kWh (' + f.num(solar, 0) + '% solar)'],
      ['Nível médio do reservatório', est.leituras ? f.pct(est.medio) : '—'],
      ['Menor nível registrado', est.leituras ? f.pct(est.minimo) : '—'],
      ['Índice médio', est.leituras ? f.num(est.indiceMedio, 0) + ' / 100' : '—'],
      ['Irrigações automáticas', String(a.irrigacoesAutomaticas)],
      ['Comandos manuais / recusados', a.comandosManuais + ' / ' + a.comandosRecusados],
      ['Bloqueios de emergência', String(a.bloqueiosEmergencia)]
    ];
    BS.dom.html('periodo-lista', linhas.map(function (l) {
      return '<div><dt>' + f.esc(l[0]) + '</dt><dd class="num">' + f.esc(l[1]) + '</dd></div>';
    }).join(''));
  }

  return { atualizar: atualizar };
})();
