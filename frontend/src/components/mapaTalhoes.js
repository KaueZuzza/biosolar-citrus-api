/* Mapa operacional: os 4 talhões ao redor do reservatório central. Clique abre os detalhes. */
BS.componentes.mapaTalhoes = (function () {
  var f = BS.fmt;
  var criado = false;
  var ultimo = {};
  var abertoId = null;

  function criar(talhoes) {
    var mapa = document.getElementById('mapa');
    talhoes.forEach(function (x) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'talhao';
      b.setAttribute('data-pos', x.id);
      b.setAttribute('data-id', x.id);
      // Somente conteúdo "phrasing" (span/i) dentro do <button>: HTML válido. Os filhos do botão são
      // itens flex (coluna), portanto se comportam como blocos sem precisar de <div>.
      b.innerHTML =
        '<span class="talhao-top"><span class="talhao-nome">' + f.esc(x.nome) + '</span><span data-pill></span></span>' +
        '<span class="talhao-cultura">' + (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + f.esc(x.culturaRotulo + ' ' + x.variedade) + '</span>' +
        '<span class="talhao-umid"><span class="v num" data-umid>--</span><span class="t" data-tend></span></span>' +
        '<span class="umid-bar" aria-hidden="true"><i data-bar></i>' +
        '<span class="lim" style="left:' + x.limiteCritico + '%" title="Limite crítico"></span>' +
        '<span class="lim" style="left:' + x.umidadeAlvo + '%;opacity:.25" title="Umidade alvo"></span></span>' +
        '<span class="talhao-estado" data-estado></span>';
      b.addEventListener('click', function () { abrirDetalhes(x.id); });
      mapa.appendChild(b);
    });
    criado = true;
  }

  function atualizar(tel) {
    if (!criado) criar(tel.talhoes);
    var bloqueio = tel.reservatorio.bloqueioEmergencia;
    tel.talhoes.forEach(function (x) {
      ultimo[x.id] = x;
      var el = document.querySelector('#mapa .talhao[data-id="' + x.id + '"]');
      if (!el) return;
      var st = BS.status.talhao[x.status];
      el.className = 'talhao st-' + x.status + (x.aspersorLigado ? ' irrigando' : '') + (x.irrigacaoBloqueada ? ' bloqueado' : '');
      BS.dom.html(el.querySelector('[data-pill]'), '<span class="pill ' + st.cls + '">' + st.icone + ' ' + st.rotulo.toUpperCase() + '</span>');
      BS.dom.texto(el.querySelector('[data-umid]'), '💧 ' + f.pct(x.umidade));
      BS.dom.texto(el.querySelector('[data-tend]'), (x.variacaoPorHora >= 0 ? '↑ ' : '↓ ') + f.num(Math.abs(x.variacaoPorHora)) + '%/h');
      el.querySelector('[data-bar]').style.width = x.umidade + '%';

      var estado;
      if (x.irrigacaoBloqueada) estado = '⛔ Irrigação bloqueada (proteção hídrica)';
      else if (x.aspersorLigado) estado = '<span class="gotas" aria-hidden="true"><i></i><i></i><i></i></span> Irrigando · ' + BS.status.modo[x.modoAcionamento];
      else if (bloqueio) estado = '🔒 Bomba bloqueada';
      else if (x.horasAteCritico !== null && x.status !== 'CRITICO') estado = '⏱️ Crítico em ~' + f.horas(x.horasAteCritico);
      else estado = 'Aspersor desligado';
      BS.dom.html(el.querySelector('[data-estado]'), estado);

      BS.dom.attr(el, 'aria-label', x.nome + ', ' + x.culturaRotulo + ' ' + x.variedade + '. Umidade ' + f.pct(x.umidade) +
        ', status ' + st.rotulo + '. ' + (x.aspersorLigado ? 'Aspersor ligado, modo ' + BS.status.modo[x.modoAcionamento] : 'Aspersor desligado') +
        (x.irrigacaoBloqueada ? ', irrigação bloqueada pela proteção hídrica' : '') + '. Abrir detalhes.');
    });
    if (abertoId && document.getElementById('dlg-talhao').open) preencherDetalhes(abertoId);
  }

  function preencherDetalhes(id) {
    var x = ultimo[id];
    if (!x) return;
    var st = BS.status.talhao[x.status];
    BS.dom.texto('dlg-talhao-titulo', (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + x.nome + ': ' + x.culturaRotulo + ' ' + x.variedade);
    var linhas = [
      ['Umidade atual', f.pct(x.umidade)],
      ['Status', st.icone + ' ' + st.rotulo],
      ['Limite crítico', f.pct(x.limiteCritico, 0)],
      ['Atenção abaixo de', f.pct(x.limiteAtencao, 0)],
      ['Umidade alvo (fim da irrigação)', f.pct(x.umidadeAlvo, 0)],
      ['Variação atual', f.sinal(x.variacaoPorHora) + ' %/h'],
      ['Evapotranspiração', f.num(x.evapotranspiracaoPorHora) + ' %/h'],
      ['Previsão até crítico', x.status === 'CRITICO' ? 'já crítico' : (x.horasAteCritico === null ? 'estável/subindo' : '~' + f.horas(x.horasAteCritico))],
      ['Aspersor', x.aspersorLigado ? '🟢 Ligado (' + BS.status.modo[x.modoAcionamento] + ')' : (x.irrigacaoBloqueada ? '⛔ Bloqueado' : '⚪ Desligado')],
      ['Bomba', x.bombaId + ' · ' + f.num(x.potenciaBombaKw) + ' kW · ' + f.num(x.vazaoBombaM3h, 0) + ' m³/h'],
      ['Prioridade', x.prioridade + (x.prioridade === 1 ? ' (alta)' : ' (média)')],
      ['Solo', x.solo],
      ['Área / plantas', f.num(x.areaHa) + ' ha · ' + x.plantas.toLocaleString('pt-BR') + ' plantas'],
      ['Última atualização', f.hora(x.ultimaAtualizacao)]
    ];
    BS.dom.html('dlg-talhao-corpo', '<dl class="detalhe-grid">' + linhas.map(function (l) {
      return '<div><dt>' + f.esc(l[0]) + '</dt><dd>' + f.esc(l[1]) + '</dd></div>';
    }).join('') + '</dl>');

    var acoes = document.getElementById('dlg-talhao-acoes');
    var ligar = !x.aspersorLigado;
    var chave = ligar + '';
    if (acoes.__chave !== chave) {
      acoes.__chave = chave;
      acoes.innerHTML = '<button class="btn" type="button" data-fechar>Fechar</button>' +
        '<button class="btn ' + (ligar ? 'btn-primary' : '') + '" type="button" id="dlg-talhao-acionar">' +
        (ligar ? '💦 Ligar aspersor' : '⏹ Desligar aspersor') + '</button>';
      document.getElementById('dlg-talhao-acionar').addEventListener('click', function (e) {
        BS.componentes.controleAspersores.acionar(abertoId, ligar, e.currentTarget);
      });
    }
  }

  function abrirDetalhes(id) {
    abertoId = id;
    document.getElementById('dlg-talhao-acoes').__chave = null;
    preencherDetalhes(id);
    BS.dom.abrirDialogo(document.getElementById('dlg-talhao'));
  }

  return { atualizar: atualizar };
})();
