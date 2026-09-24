/* Status geral (topo) e relógio da fazenda. */
BS.componentes = BS.componentes || {};

BS.componentes.statusGeral = (function () {
  var t = BS.dom.texto;
  var ultimoStatus = null;

  function atualizar(tel) {
    var s = tel.statusSistema;
    BS.dom.attr('status-hero', 'data-status', s.codigo);
    document.body.setAttribute('data-status', s.codigo);
    t('status-emoji', s.emoji);
    t('status-rotulo', s.rotulo);
    t('status-desc', s.descricao);
    t('status-leitura', BS.fmt.hora(tel.horarioLeitura));

    var sim = tel.simulacao;
    t('status-simulacao', sim.pausada ? '⏸ Simulação pausada' : '⚙️ Simulação em execução · ' + sim.fatorVelocidade + '×');
    t('relogio-hora', BS.fmt.horaSimulada(tel.horaSimulada));
    t('relogio-velocidade', sim.pausada ? 'pausado' : sim.fatorVelocidade + '×');
    t('relogio-icone', tel.energia.fatorSolar > 0 ? '☀️' : '🌙');

    if (ultimoStatus !== null && ultimoStatus !== s.codigo) {
      var emergencia = s.codigo === 'EMERGENCIA';
      BS.dom.anunciar('Status do sistema: ' + s.rotulo + '. ' + s.descricao, emergencia);
      if (emergencia && BS.preferencias && BS.preferencias.obter().vozAuto) {
        BS.voz.falar('Atenção. Emergência hídrica. ' + s.descricao);
      }
    }
    ultimoStatus = s.codigo;
  }

  return { atualizar: atualizar };
})();
