/* Mapa operacional: os talhões cadastrados ao redor do reservatório central. Clique abre os detalhes.
 * Os cartões acompanham o cadastro: talhões incluídos ou excluídos aparecem/somem sem recarregar a página. */
BS.componentes.mapaTalhoes = (function () {
  var f = BS.fmt;
  var ultimo = {};
  var abertoId = null;

  function criarCartao(x) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'talhao';
    b.setAttribute('data-id', x.id);
    // Somente conteúdo "phrasing" (span/i) dentro do <button>: HTML válido. Os filhos do botão são
    // itens flex (coluna), portanto se comportam como blocos sem precisar de <div>.
    b.innerHTML =
      '<span class="talhao-top"><span class="talhao-nome" data-nome></span><span data-pill></span></span>' +
      '<span class="talhao-cultura" data-cultura></span>' +
      '<span class="talhao-umid"><span class="v num" data-umid>--</span><span class="t" data-tend></span></span>' +
      '<span class="umid-bar" aria-hidden="true"><i data-bar></i>' +
      '<span class="lim" data-lim-critico title="Limite crítico"></span>' +
      '<span class="lim" data-lim-alvo style="opacity:.25" title="Umidade alvo"></span></span>' +
      '<span class="talhao-estado" data-estado></span>';
    b.addEventListener('click', function () { abrir(x.id); });
    return b;
  }

  /** Mantém um cartão por talhão ativo, na ordem do cadastro. */
  function sincronizarCartoes(talhoes) {
    var mapa = document.getElementById('mapa');
    var ids = talhoes.map(function (x) { return x.id; });
    BS.dom.$$('#mapa .talhao').forEach(function (el) {
      if (ids.indexOf(el.getAttribute('data-id')) < 0) el.remove();
    });
    var ordemAtual = BS.dom.$$('#mapa .talhao').map(function (el) { return el.getAttribute('data-id'); });
    // Só mexe no DOM quando o conjunto/ordem muda: mover um cartão tira o foco do teclado
    if (ordemAtual.join('|') !== ids.join('|')) {
      talhoes.forEach(function (x) {
        mapa.appendChild(mapa.querySelector('.talhao[data-id="' + CSS.escape(x.id) + '"]') || criarCartao(x));
      });
    }
    document.getElementById('mapa-vazio').hidden = talhoes.length > 0;
  }

  function atualizar(tel) {
    sincronizarCartoes(tel.talhoes);
    var bloqueio = tel.reservatorio.bloqueioEmergencia;
    ultimo = {};
    tel.talhoes.forEach(function (x) {
      ultimo[x.id] = x;
      var el = document.querySelector('#mapa .talhao[data-id="' + CSS.escape(x.id) + '"]');
      if (!el) return;
      var st = BS.status.talhao[x.status];
      el.className = 'talhao st-' + x.status + (x.aspersorLigado ? ' irrigando' : '') + (x.irrigacaoBloqueada ? ' bloqueado' : '');
      BS.dom.texto(el.querySelector('[data-nome]'), x.nome);
      BS.dom.texto(el.querySelector('[data-cultura]'), (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + x.culturaRotulo + ' ' + x.variedade);
      BS.dom.html(el.querySelector('[data-pill]'), '<span class="pill ' + st.cls + '">' + st.icone + ' ' + st.rotulo.toUpperCase() + '</span>');
      BS.dom.texto(el.querySelector('[data-umid]'), '💧 ' + f.pct(x.umidade));
      BS.dom.texto(el.querySelector('[data-tend]'), (x.variacaoPorHora >= 0 ? '↑ ' : '↓ ') + f.num(Math.abs(x.variacaoPorHora)) + '%/h');
      el.querySelector('[data-bar]').style.width = x.umidade + '%';
      el.querySelector('[data-lim-critico]').style.left = x.limiteCritico + '%';
      el.querySelector('[data-lim-alvo]').style.left = x.umidadeAlvo + '%';

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

    var dlg = document.getElementById('dlg-talhao');
    if (abertoId && dlg.open) {
      if (ultimo[abertoId]) preencherDetalhes(abertoId);
      else dlg.close();   // o talhão foi excluído enquanto os detalhes estavam abertos
    }
  }

  function preencherDetalhes(id) {
    var x = ultimo[id];
    if (!x) return;
    var st = BS.status.talhao[x.status];
    BS.dom.texto('dlg-talhao-titulo', (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + x.nome + ': ' + x.culturaRotulo + ' ' + x.variedade);
    // [rótulo, valor, chave da ajuda "?"]
    var linhas = [
      ['Umidade atual', f.pct(x.umidade), 'umidade'],
      ['Status', st.icone + ' ' + st.rotulo, 'status-talhao'],
      ['Limite crítico', f.pct(x.limiteCritico, 0), 'limite-critico'],
      ['Atenção abaixo de', f.pct(x.limiteAtencao, 0), 'limite-atencao'],
      ['Umidade alvo (fim da irrigação)', f.pct(x.umidadeAlvo, 0), 'umidade-alvo'],
      ['Variação atual', f.sinal(x.variacaoPorHora) + ' %/h', 'tendencia'],
      ['Evapotranspiração', f.num(x.evapotranspiracaoPorHora) + ' %/h', 'evapotranspiracao'],
      ['Previsão até crítico', x.status === 'CRITICO' ? 'já crítico' : (x.horasAteCritico === null ? 'estável/subindo' : '~' + f.horas(x.horasAteCritico)), null],
      ['Aspersor', x.aspersorLigado ? '🟢 Ligado (' + BS.status.modo[x.modoAcionamento] + ')' : (x.irrigacaoBloqueada ? '⛔ Bloqueado' : '⚪ Desligado'), 'modo'],
      ['Bomba', x.bombaId + ' · ' + f.num(x.potenciaBombaKw) + ' kW · ' + f.num(x.vazaoBombaM3h, 0) + ' m³/h', 'vazao'],
      ['Prioridade', x.prioridade + (x.prioridade === 1 ? ' (alta)' : x.prioridade === 2 ? ' (média)' : ' (baixa)'), 'prioridade'],
      ['Solo', x.solo, 'solo'],
      ['Área / plantas', f.num(x.areaHa) + ' ha · ' + x.plantas.toLocaleString('pt-BR') + ' plantas', 'area'],
      ['Última atualização', f.hora(x.ultimaAtualizacao), null]
    ];
    BS.dom.html('dlg-talhao-corpo', '<dl class="detalhe-grid">' + linhas.map(function (l) {
      return '<div><dt>' + f.esc(l[0]) + (l[2] ? ' ' + BS.ajuda.botao(l[2]) : '') + '</dt><dd>' + f.esc(l[1]) + '</dd></div>';
    }).join('') + '</dl>');

    var acoes = document.getElementById('dlg-talhao-acoes');
    var ligar = !x.aspersorLigado;
    var chave = id + ':' + ligar;
    if (acoes.__chave !== chave) {
      acoes.__chave = chave;
      acoes.innerHTML = '<button class="btn" type="button" data-fechar>Fechar</button>' +
        '<a class="btn" href="#gestao" data-editar>✏️ Editar cadastro</a>' +
        '<button class="btn ' + (ligar ? 'btn-primary' : '') + '" type="button" id="dlg-talhao-acionar">' +
        (ligar ? '💦 Ligar aspersor' : '⏹ Desligar aspersor') + '</button>';
      document.getElementById('dlg-talhao-acionar').addEventListener('click', function (e) {
        BS.componentes.controleAspersores.acionar(abertoId, ligar, e.currentTarget);
      });
      acoes.querySelector('[data-editar]').addEventListener('click', function (e) {
        e.preventDefault();
        document.getElementById('dlg-talhao').close();
        BS.componentes.gestao.editar(abertoId);
      });
    }
  }

  function abrir(id) {
    abertoId = id;
    document.getElementById('dlg-talhao-acoes').__chave = null;
    preencherDetalhes(id);
    BS.dom.abrirDialogo(document.getElementById('dlg-talhao'));
  }

  return { atualizar: atualizar, abrir: abrir };
})();
