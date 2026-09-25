/* Cartões de indicadores (reservatório, umidade média, aspersores, índice, energia). */
BS.componentes.indicadores = (function () {
  var f = BS.fmt;

  function kpi(id, valor, pctMeter, classe, sub) {
    var raiz = document.getElementById(id);
    BS.dom.texto(raiz.querySelector('[data-v]'), valor);
    BS.dom.texto(raiz.querySelector('[data-s]'), sub);
    var meter = raiz.querySelector('[data-meter]');
    var base = meter.classList.contains('solar') ? 'meter solar' : 'meter';
    meter.className = classe ? base + ' ' + classe : base;
    meter.firstElementChild.style.width = Math.max(0, Math.min(100, pctMeter)) + '%';
  }

  function atualizar(tel) {
    var r = tel.reservatorio;
    var stRes = BS.status.reservatorio[r.bloqueioEmergencia ? 'EMERGENCIA' : r.status];
    kpi('kpi-reservatorio', f.pct(r.nivel), r.nivel, stRes.cls,
      stRes.icone + ' ' + stRes.rotulo + ' · ' + f.num(r.volumeM3, 0) + ' de ' + f.num(r.capacidadeM3, 0) + ' m³');

    var talhoes = tel.talhoes;
    if (talhoes.length) {
      var media = talhoes.reduce(function (s, x) { return s + x.umidade; }, 0) / talhoes.length;
      var menor = talhoes.slice().sort(function (a, b) { return a.umidade - b.umidade; })[0];
      var stMenor = BS.status.talhao[menor.status];
      kpi('kpi-umidade', f.pct(media), media, stMenor.cls,
        'Menor: ' + menor.nome + ' ' + f.pct(menor.umidade) + ' ' + stMenor.icone);
    } else {
      kpi('kpi-umidade', '—', 0, '', 'Nenhum talhão ativo');
    }

    var ligados = talhoes.filter(function (x) { return x.aspersorLigado; }).length;
    kpi('kpi-aspersores', ligados + ' / ' + talhoes.length, talhoes.length ? ligados / talhoes.length * 100 : 0,
      r.bloqueioEmergencia ? 'crit' : '',
      r.bloqueioEmergencia ? '🔒 Bloqueados pela proteção hídrica'
        : ligados ? 'Consumo ' + f.num(r.consumoM3h, 0) + ' m³/h de água' : 'Todas as bombas desligadas');

    var ind = tel.indice;
    kpi('kpi-indice', String(ind.valor), ind.valor, BS.status.indice[ind.classificacao].cls,
      BS.status.indice[ind.classificacao].icone + ' ' + ind.rotulo);

    // Barra = cobertura solar do consumo; com as bombas paradas não há consumo, então fica vazia
    var e = tel.energia;
    kpi('kpi-energia', f.num(e.consumoKw, 1) + ' kW', e.consumoKw > 0 ? e.coberturaSolar : 0, '',
      e.consumoKw > 0
        ? '☀️ ' + f.num(e.coberturaSolar, 0) + '% solar (geração ' + f.num(e.geracaoSolarKw, 1) + ' kW)'
        : 'Bombas paradas · geração solar ' + f.num(e.geracaoSolarKw, 1) + ' kW');
  }

  return { atualizar: atualizar };
})();
