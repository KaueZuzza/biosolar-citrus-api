/* Motor de Decisão: exibe a decisão calculada pelo servidor (o navegador não decide nada). */
BS.componentes.motorDecisao = (function () {
  var t = BS.dom.texto;
  var ultimoTitulo = null;

  function atualizar(tel) {
    var d = tel.decisao;
    BS.dom.attr('decisao', 'data-nivel', d.nivel);
    t('decisao-regra', d.regra + ' · ' + d.regraNome);
    t('decisao-titulo', (BS.status.severidade[d.nivel] || {}).icone + ' ' + d.titulo);
    t('decisao-oque', d.oQue);
    t('decisao-onde', d.onde);
    t('decisao-porque', d.porque);
    t('decisao-acao', d.acaoAutomatica);
    t('decisao-risco', d.risco);
    t('decisao-operador', d.acaoOperador);
    t('decisao-impacto', d.impacto);

    BS.dom.$$('.regra-chip').forEach(function (chip) {
      var ativa = d.regrasAtivas.indexOf(chip.getAttribute('data-regra')) >= 0;
      chip.classList.toggle('ativa', ativa);
      BS.dom.attr(chip, 'aria-current', ativa ? 'true' : null);
    });

    if (ultimoTitulo !== null && ultimoTitulo !== d.titulo) {
      BS.dom.anunciar('Decisão do sistema: ' + d.titulo + '. ' + d.acaoAutomatica, d.nivel === 'EMERGENCIA');
    }
    ultimoTitulo = d.titulo;
  }

  return { atualizar: atualizar };
})();
