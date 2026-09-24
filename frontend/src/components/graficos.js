/* Gráficos: umidade dos talhões, nível do reservatório e acionamentos por talhão.
 * Cores: slots categóricos 1-4 na ordem fixa (a cor segue o talhão, nunca a posição). */
BS.componentes.graficos = (function () {
  var f = BS.fmt;
  var CORES = { A: 'var(--series-1)', B: 'var(--series-2)', C: 'var(--series-3)', D: 'var(--series-4)' };

  function legenda(id, itens, quadrado) {
    BS.dom.html(id, itens.map(function (i) {
      return '<span><i class="key' + (quadrado ? ' sq' : '') + '" style="background:' + i.cor + '"></i>' + f.esc(i.nome) + '</span>';
    }).join(''));
  }

  function tabela(id, cabecalho, linhas) {
    BS.dom.html(id, '<table class="dados"><thead><tr>' + cabecalho.map(function (c) { return '<th scope="col">' + f.esc(c) + '</th>'; }).join('') +
      '</tr></thead><tbody>' + linhas.map(function (l) {
        return '<tr>' + l.map(function (c, i) { return i === 0 ? '<th scope="row">' + f.esc(c) + '</th>' : '<td>' + f.esc(c) + '</td>'; }).join('') + '</tr>';
      }).join('') + '</tbody></table>');
  }

  function atualizarHistorico(hist) {
    var pontos = hist.pontos || [];
    var ids = pontos.length ? Object.keys(pontos[pontos.length - 1].umidades) : ['A', 'B', 'C', 'D'];
    var rotulos = pontos.map(function (p) { return f.hora(p.instante).slice(0, 5); });
    var titulos = pontos.map(function (p) { return f.hora(p.instante) + ' · fazenda ' + f.horaSimulada(p.horaSimulada); });

    var series = ids.map(function (id) {
      return { nome: 'Talhão ' + id, cor: CORES[id] || 'var(--series-1)', valores: pontos.map(function (p) { return p.umidades[id]; }) };
    });
    legenda('g-umid-legenda', series);
    BS.charts.linhas(document.getElementById('g-umidade'), {
      rotulos: rotulos, titulosTooltip: titulos, series: series, unidade: '%', altura: 260,
      referencias: [{ valor: 25, rotulo: 'Limite crítico 25%', cor: 'var(--crit)' }]
    });

    BS.charts.linhas(document.getElementById('g-reservatorio'), {
      rotulos: rotulos, titulosTooltip: titulos, unidade: '%', area: true, altura: 260,
      series: [{ nome: 'Reservatório', cor: 'var(--series-1)', valores: pontos.map(function (p) { return p.reservatorio; }) }],
      faixas: [
        { de: 0, ate: 15, cor: 'var(--crit)', opacidade: 0.10 },
        { de: 15, ate: 30, cor: 'var(--warn)', opacidade: 0.10 }
      ],
      referencias: [
        { valor: 30, rotulo: 'Atenção 30%', cor: 'var(--warn)' },
        { valor: 15, rotulo: 'Bloqueio 15%', cor: 'var(--crit)' }
      ]
    });

    var ultimos = pontos.slice(-20).reverse();
    tabela('g-umidade-tabela', ['Horário'].concat(ids.map(function (id) { return 'Talhão ' + id + ' (%)'; })),
      ultimos.map(function (p) { return [f.hora(p.instante)].concat(ids.map(function (id) { return f.num(p.umidades[id]); })); }));
    tabela('g-reservatorio-tabela', ['Horário', 'Hora na fazenda', 'Reservatório (%)', 'Aspersores ligados', 'Bloqueio'],
      ultimos.map(function (p) {
        return [f.hora(p.instante), f.horaSimulada(p.horaSimulada), f.num(p.reservatorio), String(p.aspersoresLigados), p.bloqueioEmergencia ? 'sim' : 'não'];
      }));
  }

  function atualizarIndicadores(ind) {
    var por = ind.acionamentos.porTalhao;
    var series = [
      { nome: 'Irrigações automáticas', cor: 'var(--series-1)', valores: por.map(function (p) { return p.automaticos; }) },
      { nome: 'Comandos manuais', cor: 'var(--series-2)', valores: por.map(function (p) { return p.manuais; }) }
    ];
    legenda('g-irr-legenda', series, true);
    BS.charts.colunas(document.getElementById('g-irrigacoes'), {
      categorias: por.map(function (p) { return 'Talhão ' + p.talhaoId; }),
      series: series, altura: 230
    });
    tabela('g-irrigacoes-tabela', ['Talhão', 'Automáticas', 'Manuais', 'Recusados'],
      por.map(function (p) { return ['Talhão ' + p.talhaoId, String(p.automaticos), String(p.manuais), String(p.recusados)]; }));
  }

  return { atualizarHistorico: atualizarHistorico, atualizarIndicadores: atualizarIndicadores };
})();
