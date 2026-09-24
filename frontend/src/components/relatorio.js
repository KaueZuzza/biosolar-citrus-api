/* Relatório operacional: gerado pelo servidor; aqui só exibimos, imprimimos (PDF) e compartilhamos. */
BS.componentes.relatorio = (function () {
  var f = BS.fmt;
  var atual = null;

  function kpi(rotulo, valor) {
    return '<div><dt>' + f.esc(rotulo) + '</dt><dd class="num">' + f.esc(valor) + '</dd></div>';
  }

  function render(r) {
    var c = r.contagens, e = r.energia, res = r.reservatorio;
    var solarPct = e.energiaConsumidaKwh > 0 ? e.energiaSolarKwh / e.energiaConsumidaKwh * 100 : 100;
    var st = r.statusAtual;
    var html = '';
    html += '<p class="small muted">Gerado em ' + f.dataHora(r.geradoEm) + ' · Período: ' + f.dataHora(r.periodo.inicio) +
      ' → ' + f.dataHora(r.periodo.fim) + ' (' + r.periodo.duracaoMinutos + ' min) · Hora na fazenda: ' + f.horaSimulada(r.periodo.horaSimuladaAtual) + '</p>';
    html += '<p><span class="pill ' + (BS.status.reservatorio[st.codigo === 'RISCO_HIDRICO' ? 'CRITICO' : st.codigo] || BS.status.reservatorio.NORMAL).cls + '">' +
      f.esc(st.emoji + ' ' + st.rotulo) + '</span> ' + f.esc(st.descricao) + '</p>';
    html += '<dl class="rel-kpis">' +
      kpi('Reservatório atual', f.pct(res.atual)) +
      kpi('Nível médio', f.pct(res.medio)) +
      kpi('Menor nível registrado', f.pct(res.minimo)) +
      kpi('Índice Hidro-Energético', r.indice.valor + '/100') +
      kpi('Índice médio', f.num(r.indiceMedio, 0)) +
      kpi('Irrigações automáticas', String(c.irrigacoesAutomaticas)) +
      kpi('Comandos manuais', String(c.comandosManuais)) +
      kpi('Comandos recusados', String(c.comandosRecusados)) +
      kpi('Bloqueios de emergência', String(c.bloqueiosEmergencia)) +
      kpi('Água consumida', f.num(r.aguaConsumidaM3, 1) + ' m³') +
      kpi('Energia das bombas', f.num(e.energiaConsumidaKwh, 1) + ' kWh') +
      kpi('Energia solar', f.num(solarPct, 0) + '%') + '</dl>';
    html += '<h3 style="font-size:1rem">Talhões</h3><div class="tabela-scroll" style="max-height:none"><table class="dados"><thead><tr>' +
      '<th scope="col">Talhão</th><th scope="col">Cultura</th><th scope="col">Atual</th><th scope="col">Mínima</th><th scope="col">Média</th>' +
      '<th scope="col">Status</th><th scope="col">Irrig. auto</th><th scope="col">Manuais</th></tr></thead><tbody>' +
      r.talhoes.map(function (t) {
        var s = BS.status.talhao[t.status];
        return '<tr><th scope="row">' + f.esc(t.id) + '</th><td>' + f.esc(t.cultura) + '</td><td>' + f.pct(t.umidadeAtual) + '</td><td>' +
          f.pct(t.umidadeMinima) + '</td><td>' + f.pct(t.umidadeMedia) + '</td><td>' + s.icone + ' ' + s.rotulo + '</td><td>' +
          t.irrigacoesAutomaticas + '</td><td>' + t.comandosManuais + '</td></tr>';
      }).join('') + '</tbody></table></div>';
    html += '<p><b>Talhões que atingiram nível crítico:</b> ' + (r.talhoesCriticos.length ? f.esc(r.talhoesCriticos.join(', ')) : 'nenhum') + '</p>';
    html += '<h3 style="font-size:1rem">Eventos relevantes</h3><ol class="rel-eventos">' + r.eventosRelevantes.slice(0, 15).map(function (ev) {
      return '<li><b>' + f.hora(ev.instante) + '</b> ' + (BS.status.tipoEvento[ev.tipo] || '') + ' ' + f.esc(ev.titulo) + ': ' + f.esc(ev.descricao) + '</li>';
    }).join('') + '</ol>';
    BS.dom.html('relatorio-corpo', html);
  }

  async function abrir() {
    var dlg = document.getElementById('dlg-relatorio');
    BS.dom.html('relatorio-corpo', '<p>Gerando relatório no servidor…</p>');
    BS.dom.abrirDialogo(dlg);
    try {
      var r = await BS.relatorioService.obter();
      if (!r.ok) throw new Error('HTTP ' + r.status);
      atual = r.dados;
      render(atual);
    } catch (e) {
      BS.dom.html('relatorio-corpo', '<p>Não foi possível gerar o relatório: servidor indisponível.</p>');
    }
  }

  function imprimir() {
    if (!atual) return;
    var area = document.getElementById('area-impressao') || document.body.appendChild(Object.assign(document.createElement('div'), { id: 'area-impressao' }));
    area.innerHTML = '<h1>🍊☀️ BioSolar Citrus: Relatório Operacional</h1>' + document.getElementById('relatorio-corpo').innerHTML;
    document.body.classList.add('imprimindo');
    window.addEventListener('afterprint', function limpar() {
      window.removeEventListener('afterprint', limpar);
      document.body.classList.remove('imprimindo');
    });
    window.print();
  }

  function iniciar() {
    document.getElementById('btn-relatorio').addEventListener('click', abrir);
    document.getElementById('rel-imprimir').addEventListener('click', imprimir);
    document.getElementById('rel-csv').setAttribute('href', BS.relatorioService.urlCsv());
    document.getElementById('rel-whatsapp').addEventListener('click', function () {
      if (atual) window.open('https://wa.me/?text=' + encodeURIComponent(atual.resumoTexto), '_blank', 'noopener');
    });
    document.getElementById('rel-copiar').addEventListener('click', async function () {
      if (!atual) return;
      try {
        await navigator.clipboard.writeText(atual.resumoTexto);
        BS.toast({ severidade: 'SUCESSO', titulo: 'Resumo copiado', descricao: ' Cole em qualquer aplicativo de mensagens.' });
      } catch (e) {
        BS.toast({ severidade: 'ATENCAO', titulo: 'Não foi possível copiar', descricao: ' Use o botão do WhatsApp ou o CSV.' });
      }
    });
  }

  return { iniciar: iniciar };
})();
