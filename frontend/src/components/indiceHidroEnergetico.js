/* Índice Hidro-Energético: anel + fatores (H, S, E) calculados pelo servidor. */
BS.componentes.indice = (function () {
  var f = BS.fmt;
  var CIRCUNFERENCIA = 2 * Math.PI * 50;
  var COR = { EQUILIBRADO: 'var(--ok)', ATENCAO: 'var(--warn)', RISCO: 'var(--crit)' };

  function atualizar(tel) {
    var ind = tel.indice;
    var st = BS.status.indice[ind.classificacao];
    var pill = document.getElementById('ind-class');
    pill.className = 'pill ' + st.cls;
    BS.dom.texto(pill, st.icone + ' ' + ind.rotulo);
    BS.dom.texto('ind-valor', String(ind.valor));
    BS.dom.attr('ind-ring', 'aria-valuenow', ind.valor);
    BS.dom.attr('ind-ring', 'aria-valuetext', ind.valor + ' de 100, ' + ind.rotulo);
    var arco = document.getElementById('ind-arco');
    arco.setAttribute('stroke-dashoffset', (CIRCUNFERENCIA * (1 - ind.valor / 100)).toFixed(1));
    arco.style.stroke = COR[ind.classificacao];

    var partes = ind.fatores.map(function (x) { return f.num(x.peso, 1) + ' × ' + f.num(x.valor, 0); });
    BS.dom.texto('ind-formula', ind.formula + '  =  ' + partes.join(' + ') + '  =  ' + f.num(ind.valorSemTrava, 1) +
      (ind.travaEmergencia ? '  →  trava de emergência: ' + ind.valor : ''));

    BS.dom.html('ind-fatores', ind.fatores.map(function (x) {
      return '<div class="fator-linha"><span><b>' + x.codigo + '</b> · ' + f.esc(x.nome) + '</span>' +
        '<span class="num">' + f.num(x.valor, 0) + ' pts × ' + f.num(x.peso * 100, 0) + '% = <b>' + f.num(x.contribuicao, 1) + '</b></span>' +
        '<div class="meter ' + (x.valor >= 70 ? 'ok' : x.valor >= 40 ? 'warn' : 'crit') + '"><i style="width:' + x.valor + '%"></i></div></div>';
    }).join(''));
  }

  return { atualizar: atualizar };
})();
