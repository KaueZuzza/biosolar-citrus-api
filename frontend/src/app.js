/* Orquestração do dashboard: polling simples e robusto. O servidor é a fonte da verdade;
 * o navegador mantém apenas a última leitura em memória para exibição (nada é persistido). */
BS.app = (function () {
  var C = BS.componentes;
  var ultimaTelemetria = null;
  var online = null;

  function laco(funcao, intervalo) {
    async function ciclo() {
      try { await funcao(); } catch (e) { /* status de conexão tratado em BS.api */ }
      setTimeout(ciclo, intervalo);
    }
    ciclo();
  }

  function seguro(componente, dados) {
    try { componente.atualizar(dados); } catch (e) { console.error(e); }
  }

  async function carregarTelemetria() {
    var r = await BS.telemetriaService.obter();
    if (!r.ok) return;
    var t = r.dados;
    ultimaTelemetria = t;
    [C.statusGeral, C.indicadores, C.motorDecisao, C.reservatorio, C.mapaTalhoes, C.controleAspersores, C.indice, C.painelSimulacao]
      .forEach(function (c) { seguro(c, t); });
    C.notificacoes.processar(t.eventosRecentes);
  }

  async function carregarEventos() {
    var r = await BS.eventoService.listar(BS.config.eventosTimeline);
    if (r.ok) C.historico.atualizar(r.dados);
  }

  async function carregarHistorico() {
    var r = await BS.telemetriaService.historico();
    if (r.ok) C.graficos.atualizarHistorico(r.dados);
  }

  async function carregarIndicadores() {
    var r = await BS.telemetriaService.indicadores();
    if (r.ok) C.graficos.atualizarIndicadores(r.dados);
  }

  async function carregarSaude() {
    var r = await BS.telemetriaService.saude();
    if (r.ok) C.saude.atualizar(r.dados);
  }

  function atualizarAgora() {
    carregarTelemetria().catch(function () {});
    carregarEventos().catch(function () {});
    setTimeout(function () { carregarIndicadores().catch(function () {}); }, 300);
  }

  async function falarStatus() {
    try {
      var r = await BS.telemetriaService.status();
      if (!r.ok) throw new Error();
      var texto = r.dados.resumo;
      if (ultimaTelemetria) texto += ' Decisão do sistema: ' + ultimaTelemetria.decisao.titulo + '.';
      BS.voz.falar(texto);
    } catch (e) {
      BS.voz.falar('Sem conexão com o servidor da fazenda.');
    }
  }

  BS.api.aoMudarConexao(function (s) {
    var dot = document.getElementById('api-dot');
    dot.className = 'dot ' + (s.online ? 'on' : 'off');
    BS.dom.texto('api-texto', s.online ? 'API online · ' + s.latencia + ' ms' : 'API offline');
    document.body.classList.toggle('offline', !s.online);
    if (!s.online) BS.dom.texto('offline-hora', ultimaTelemetria ? BS.fmt.hora(ultimaTelemetria.horarioLeitura) : '--');
    if (online !== null && online !== s.online) {
      BS.toast(s.online
        ? { severidade: 'SUCESSO', titulo: 'Conexão restabelecida', descricao: ' Telemetria voltou a ser recebida do servidor.' }
        : { severidade: 'CRITICO', titulo: 'Servidor indisponível', descricao: ' Tentando reconectar automaticamente…' });
    }
    online = s.online;
  });

  function iniciar() {
    C.historico.iniciar();
    C.notificacoes.iniciar();
    C.painelSimulacao.iniciar();
    C.relatorio.iniciar();
    C.acessibilidade.iniciar();
    document.getElementById('btn-voz').addEventListener('click', falarStatus);

    var i = BS.config.intervalos;
    laco(carregarTelemetria, i.telemetria);
    laco(carregarEventos, i.eventos);
    laco(carregarHistorico, i.historico);
    laco(carregarIndicadores, i.indicadores);
    laco(carregarSaude, i.saude);
  }

  iniciar();
  return { atualizarAgora: atualizarAgora, falarStatus: falarStatus };
})();
