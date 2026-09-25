/* Mapa da Fazenda: imagem de satélite real (Esri World Imagery) de Capitão Poço – PA, talhões vindos da API/
 * PostgreSQL (GeoJSON), desenho da área real de cada talhão, dados públicos (IBGE, Open-Meteo) e o agente agrícola.
 *
 * Independente das demais seções: o Leaflet (frontend/vendor/leaflet, local) só é carregado quando a seção é
 * aberta, e a atualização roda apenas enquanto ela está visível. Nenhum dado de talhão fica no JavaScript. */
BS.componentes.mapaFazenda = (function () {
  var f = BS.fmt;
  var INTERVALO_MS = 5000;
  var INTERVALO_PUBLICO_MS = 10 * 60 * 1000;
  var ESRI_IMAGEM = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
  var ESRI_ROTULOS = 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}';
  var OSM = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
  var TIPOS = {
    DADO: '📊 Dado do sistema',
    PUBLICO: '🌐 Fonte pública',
    ESTIMATIVA: '≈ Estimativa',
    ORIENTACAO: '📘 Orientação geral'
  };

  var L = null;
  var carregandoLeaflet = null;
  var mapa = null;
  var camadaTalhoes = null;
  var camadaMunicipio = null;
  var contorno = null;
  var sede = null;
  var poligonos = {};
  var itensLista = {};
  var dados = null;
  var selecionado = null;
  var desenho = null;
  var timer = null;
  var timerPublico = null;
  var enquadrado = false;
  var avisoTiles = false;

  // ---------------------------------------------------------------------------------------------
  // Carregamento sob demanda
  // ---------------------------------------------------------------------------------------------

  function carregarLeaflet() {
    if (window.L) return Promise.resolve(window.L);
    if (carregandoLeaflet) return carregandoLeaflet;
    carregandoLeaflet = new Promise(function (ok, falha) {
      var css = document.createElement('link');
      css.rel = 'stylesheet';
      css.href = 'vendor/leaflet/leaflet.css';
      // Antes do mapa.css, para que os ajustes de tema do BioSolar prevaleçam
      var nosso = document.querySelector('link[href="src/styles/mapa.css"]');
      document.head.insertBefore(css, nosso);
      var s = document.createElement('script');
      s.src = 'vendor/leaflet/leaflet.js';
      s.onload = function () { ok(window.L); };
      s.onerror = function () { carregandoLeaflet = null; falha(new Error('Leaflet indisponível')); };
      document.head.appendChild(s);
    });
    return carregandoLeaflet;
  }

  function reduzido() {
    return document.documentElement.getAttribute('data-motion') === 'reduzido';
  }

  function estado(texto) {
    var el = document.getElementById('mf-estado');
    el.hidden = !texto;
    BS.dom.texto(el, texto || '');
  }

  // ---------------------------------------------------------------------------------------------
  // Mapa
  // ---------------------------------------------------------------------------------------------

  function criarMapa(Lf) {
    L = Lf;
    mapa = L.map('mf-mapa', { zoomSnap: 0.5, worldCopyJump: false, minZoom: 5, maxZoom: 19 });
    mapa.attributionControl.setPrefix('<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>');

    var satelite = L.tileLayer(ESRI_IMAGEM, {
      maxZoom: 19, maxNativeZoom: 17,
      attribution: 'Imagens © <a href="https://www.esri.com" target="_blank" rel="noopener">Esri</a>, Maxar, Earthstar Geographics'
    });
    var rotulos = L.tileLayer(ESRI_ROTULOS, { maxZoom: 19, maxNativeZoom: 17, attribution: 'Nomes e limites © Esri' });
    var ruas = L.tileLayer(OSM, {
      maxZoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
    });
    satelite.addTo(mapa);
    rotulos.addTo(mapa);
    camadaMunicipio = L.layerGroup().addTo(mapa);
    camadaTalhoes = L.featureGroup().addTo(mapa);

    L.control.layers(
      { '🛰️ Satélite': satelite, '🗺️ Ruas (OpenStreetMap)': ruas },
      { 'Nomes e limites': rotulos, 'Talhões': camadaTalhoes, 'Município (IBGE)': camadaMunicipio },
      { collapsed: true }
    ).addTo(mapa);
    L.control.scale({ imperial: false, maxWidth: 140 }).addTo(mapa);

    [satelite, ruas].forEach(function (camada) {
      camada.on('tileerror', function () {
        if (avisoTiles) return;
        avisoTiles = true;
        estado('As imagens do mapa não carregaram (sem internet?). Talhões, dados e o agente continuam funcionando.');
      });
      camada.on('tileload', function () {
        if (avisoTiles) { avisoTiles = false; estado(null); }
      });
    });

    mapa.on('click', function (e) { if (desenho) adicionarPonto(e.latlng); });
    mapa.setView([-1.7447, -47.0638], 12);
  }

  function enquadrarFazenda() {
    if (!mapa || !camadaTalhoes.getLayers().length) return;
    var b = camadaTalhoes.getBounds();
    var opcoes = { padding: [40, 40], maxZoom: 17 };
    if (reduzido()) mapa.fitBounds(b, opcoes); else mapa.flyToBounds(b, Object.assign({ duration: 0.8 }, opcoes));
  }

  function enquadrarMunicipio() {
    if (!mapa) return;
    if (contorno) {
      var opcoes = { padding: [20, 20] };
      if (reduzido()) mapa.fitBounds(contorno.getBounds(), opcoes);
      else mapa.flyToBounds(contorno.getBounds(), Object.assign({ duration: 0.8 }, opcoes));
    } else if (sede) {
      mapa.setView(sede.getLatLng(), 12);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Talhões (GET /mapa/talhoes)
  // ---------------------------------------------------------------------------------------------

  async function atualizar() {
    var r;
    try { r = await BS.mapaService.talhoes(); } catch (e) {
      estado('Sem conexão com a API: exibindo a última leitura recebida.');
      return;
    }
    if (!r.ok || !r.dados) { estado('A API não retornou os talhões agora. Tentando de novo…'); return; }
    dados = r.dados;
    if (!avisoTiles) estado(null);
    desenharTalhoes();
    atualizarLista();
    atualizarSeletorAgente();
    marcarSede();
    var aviso = document.getElementById('mf-aviso');
    aviso.hidden = !(dados.avisos && dados.avisos.length);
    BS.dom.texto(aviso, (dados.avisos || []).join(' '));
    if (!enquadrado && dados.features.length) { enquadrado = true; enquadrarFazenda(); }
  }

  function anel(feature) {
    var pontos = feature.geometry.coordinates[0].map(function (c) { return [c[1], c[0]]; });
    pontos.pop();  // GeoJSON repete o primeiro ponto no fim
    return pontos;
  }

  function classe(p) {
    return 'mf-poligono st-' + p.status +
      (p.origemGeometria === 'ILUSTRATIVA' ? ' ilustrativa' : '') +
      (p.aspersorLigado ? ' irrigando' : '') +
      (p.irrigacaoBloqueada ? ' bloqueado' : '') +
      (selecionado === p.id ? ' selecionado' : '') +
      (desenho && desenho.id === p.id ? ' editando' : '');
  }

  function rotulo(p) {
    return '<b>' + f.esc(p.id) + '</b> ' + f.pct(p.umidade) + (p.aspersorLigado ? ' 💧' : '') +
      (p.irrigacaoBloqueada ? ' ⛔' : '');
  }

  function desenharTalhoes() {
    var presentes = {};
    dados.features.forEach(function (ft) {
      var p = ft.properties;
      presentes[ft.id] = true;
      var pontos = anel(ft);
      var chave = JSON.stringify(pontos);
      var poly = poligonos[ft.id];
      if (!poly) {
        poly = L.polygon(pontos, { className: classe(p), weight: 2 });
        poly.bindTooltip(rotulo(p), { permanent: true, direction: 'center', className: 'mf-rotulo', interactive: false });
        poly.on('click', function (e) {
          if (desenho) return;  // o clique segue para o mapa e vira um ponto do desenho
          L.DomEvent.stopPropagation(e);
          selecionar(ft.id, false);
          abrirPopup(ft.id, e.latlng);
        });
        poly.addTo(camadaTalhoes);
        poly.__chave = chave;
        poligonos[ft.id] = poly;
      } else {
        if (poly.__chave !== chave) { poly.setLatLngs(pontos); poly.__chave = chave; }
        poly.setTooltipContent(rotulo(p));
      }
      poly.__props = p;
      var el = poly.getElement && poly.getElement();
      if (el) el.setAttribute('class', classe(p) + ' leaflet-interactive');
    });
    Object.keys(poligonos).forEach(function (id) {
      if (!presentes[id]) { camadaTalhoes.removeLayer(poligonos[id]); delete poligonos[id]; }
    });
    if (selecionado && !presentes[selecionado]) selecionado = null;
  }

  function areaTexto(p) {
    return p.areaMapaHa != null
      ? f.num(p.areaMapaHa, 1) + ' ha desenhados (cadastro: ' + f.num(p.areaCadastroHa, 1) + ' ha)'
      : f.num(p.areaCadastroHa, 1) + ' ha (cadastro)';
  }

  function variacaoTexto(p) {
    if (Math.abs(p.variacaoPorHora) < 0.05) return 'estável';
    return (p.variacaoPorHora > 0 ? 'subindo ' : 'caindo ') + f.num(Math.abs(p.variacaoPorHora), 1) + ' p.p./h';
  }

  function abrirPopup(id, latlng) {
    var poly = poligonos[id];
    if (!poly) return;
    var p = poly.__props;
    var st = BS.status.talhao[p.status] || BS.status.talhao.NORMAL;
    var html = '<div class="mf-popup">' +
      '<p class="mf-popup-titulo"><b>' + f.esc(p.nome) + '</b> <span class="pill ' + st.cls + '">' + st.icone + ' ' + st.rotulo + '</span></p>' +
      '<p class="mf-popup-sub">' + (p.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + f.esc(p.culturaRotulo + ' ' + p.variedade) + ' · ' + f.esc(p.solo || '') + '</p>' +
      '<dl>' +
      '<div><dt>Umidade</dt><dd>' + f.pct(p.umidade) + ' · ' + variacaoTexto(p) + '</dd></div>' +
      '<div><dt>Aspersor</dt><dd>' + (p.irrigacaoBloqueada ? '⛔ bloqueado pela proteção hídrica'
        : p.aspersorLigado ? '💧 ligado (' + (BS.status.modo[p.modoAcionamento] || '') + ')' : 'desligado') + ' · ' + f.esc(p.bombaId) + '</dd></div>' +
      '<div><dt>Área</dt><dd>' + areaTexto(p) + '</dd></div>' +
      '<div><dt>No mapa</dt><dd>' + (p.origemGeometria === 'ILUSTRATIVA' ? 'posição ilustrativa' : 'área desenhada' +
        (p.areaAtualizadaEm ? ' em ' + f.dataHora(p.areaAtualizadaEm) : '')) + '</dd></div>' +
      '</dl>' +
      '<div class="mf-popup-acoes">' +
      '<button class="btn btn-sm btn-primary" type="button" data-acao="analisar">🤖 Analisar</button>' +
      '<button class="btn btn-sm" type="button" data-acao="desenhar">✏️ ' + (p.origemGeometria === 'ILUSTRATIVA' ? 'Desenhar área' : 'Redesenhar') + '</button>' +
      '<button class="btn btn-sm btn-ghost" type="button" data-acao="detalhes">Detalhes</button>' +
      '</div></div>';
    var popup = L.popup({ maxWidth: 320, className: 'mf-popup-caixa', autoPanPadding: [30, 30] })
      .setLatLng(latlng || poly.getBounds().getCenter()).setContent(html).openOn(mapa);
    var el = popup.getElement();
    if (el) el.querySelectorAll('[data-acao]').forEach(function (b) {
      b.addEventListener('click', function () { mapa.closePopup(); acao(b.getAttribute('data-acao'), id); });
    });
  }

  function acao(tipo, id) {
    if (tipo === 'analisar') analisarTalhao(id);
    else if (tipo === 'desenhar') iniciarDesenho(id);
    else if (tipo === 'remover') removerArea(id);
    else if (tipo === 'detalhes') BS.componentes.mapaTalhoes.abrir(id);
  }

  function selecionar(id, voar) {
    selecionado = id;
    Object.keys(poligonos).forEach(function (k) {
      var el = poligonos[k].getElement && poligonos[k].getElement();
      if (el) el.setAttribute('class', classe(poligonos[k].__props) + ' leaflet-interactive');
    });
    Object.keys(itensLista).forEach(function (k) {
      itensLista[k].li.classList.toggle('selecionado', k === id);
      itensLista[k].botao.setAttribute('aria-pressed', k === id ? 'true' : 'false');
      itensLista[k].acoes.hidden = k !== id;
    });
    var alvo = document.getElementById('ag-alvo');
    if (alvo && id && alvo.querySelector('option[value="' + id + '"]')) alvo.value = id;
    if (voar && poligonos[id]) {
      var opcoes = { padding: [60, 60], maxZoom: 17 };
      if (reduzido()) mapa.fitBounds(poligonos[id].getBounds(), opcoes);
      else mapa.flyToBounds(poligonos[id].getBounds(), Object.assign({ duration: 0.7 }, opcoes));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Lista lateral (alternativa acessível ao mapa): atualizada no lugar para não perder o foco
  // ---------------------------------------------------------------------------------------------

  function criarItem(id) {
    var li = document.createElement('li');
    li.className = 'mf-item';
    li.innerHTML =
      '<button type="button" class="mf-item-principal" aria-pressed="false">' +
      '<span class="mf-cor" aria-hidden="true"></span><span class="mf-item-nome" data-nome></span>' +
      '<span class="pill" data-pill></span></button>' +
      '<p class="mf-item-sub" data-sub></p>' +
      '<div class="mf-item-acoes" hidden>' +
      '<button class="btn btn-sm" type="button" data-acao="desenhar"></button>' +
      '<button class="btn btn-sm btn-ghost" type="button" data-acao="remover" hidden>↺ Voltar à posição ilustrativa</button>' +
      '<button class="btn btn-sm btn-primary" type="button" data-acao="analisar">🤖 Analisar</button>' +
      '</div>';
    var botao = li.querySelector('.mf-item-principal');
    botao.addEventListener('click', function () { selecionar(id, true); });
    li.querySelectorAll('[data-acao]').forEach(function (b) {
      b.addEventListener('click', function () { acao(b.getAttribute('data-acao'), id); });
    });
    var item = { li: li, botao: botao, acoes: li.querySelector('.mf-item-acoes') };
    itensLista[id] = item;
    return item;
  }

  function atualizarLista() {
    var ul = document.getElementById('mf-lista');
    var ids = dados.features.map(function (ft) { return ft.id; });
    Object.keys(itensLista).forEach(function (id) {
      if (ids.indexOf(id) < 0) { itensLista[id].li.remove(); delete itensLista[id]; }
    });
    if (!ids.length) {
      BS.dom.html(ul, '<li class="vazio-lista">Nenhum talhão ativo. Cadastre um talhão na aba <a href="#gestao">Gestão</a>.</li>');
      return;
    }
    var vazio = ul.querySelector('.vazio-lista');
    if (vazio) { vazio.remove(); ul.__html = null; }
    dados.features.forEach(function (ft) {
      var p = ft.properties;
      var item = itensLista[ft.id] || criarItem(ft.id);
      if (item.li.parentNode !== ul) ul.appendChild(item.li);
      var st = BS.status.talhao[p.status] || BS.status.talhao.NORMAL;
      item.li.className = 'mf-item st-' + p.status + (selecionado === ft.id ? ' selecionado' : '');
      item.li.querySelector('.mf-cor').style.background = BS.cores.talhao(ft.id);
      BS.dom.texto(item.li.querySelector('[data-nome]'), p.nome);
      var pill = item.li.querySelector('[data-pill]');
      pill.className = 'pill ' + st.cls;
      BS.dom.texto(pill, st.icone + ' ' + f.pct(p.umidade) + (p.aspersorLigado ? ' 💧' : ''));
      var desenhada = p.origemGeometria !== 'ILUSTRATIVA';
      BS.dom.texto(item.li.querySelector('[data-sub]'), p.culturaRotulo + ' ' + p.variedade + ' · ' +
        areaTexto(p) + ' · ' + (desenhada ? '✔ área desenhada' : '▭ posição ilustrativa'));
      BS.dom.texto(item.li.querySelector('[data-acao="desenhar"]'), desenhada ? '✏️ Redesenhar área' : '✏️ Desenhar área');
      item.li.querySelector('[data-acao="remover"]').hidden = !desenhada;
      item.acoes.hidden = selecionado !== ft.id;
      item.botao.setAttribute('aria-label', p.nome + ', ' + st.rotulo + ', umidade ' + f.pct(p.umidade) +
        (desenhada ? ', área desenhada' : ', posição ilustrativa'));
    });
  }

  function marcarSede() {
    if (sede || !dados || !dados.sedeMunicipio) return;
    sede = L.circleMarker([dados.sedeMunicipio.lat, dados.sedeMunicipio.lng], { radius: 7, className: 'mf-sede' })
      .bindTooltip(f.esc(dados.municipio) + ' (sede do município)', { direction: 'top', offset: [0, -6] })
      .addTo(camadaMunicipio);
  }

  // ---------------------------------------------------------------------------------------------
  // Desenho da área (PUT /mapa/talhoes/{id}/area)
  // ---------------------------------------------------------------------------------------------

  function areaHa(pontos) {
    if (pontos.length < 3) return 0;
    var R = 6378137;
    var s = 0;
    for (var i = 0; i < pontos.length; i++) {
      var a = pontos[i];
      var b = pontos[(i + 1) % pontos.length];
      s += (b.lng - a.lng) * Math.PI / 180 * (2 + Math.sin(a.lat * Math.PI / 180) + Math.sin(b.lat * Math.PI / 180));
    }
    return Math.abs(s * R * R / 2) / 10000;
  }

  function iniciarDesenho(id) {
    if (!mapa || !poligonos[id]) return;
    if (desenho) cancelarDesenho();
    mapa.closePopup();
    selecionar(id, true);
    desenho = {
      id: id,
      pontos: [],
      linha: L.polyline([], { className: 'mf-desenho-linha', interactive: false }).addTo(mapa),
      area: L.polygon([], { className: 'mf-desenho-area', interactive: false }).addTo(mapa),
      vertices: L.layerGroup().addTo(mapa)
    };
    mapa.doubleClickZoom.disable();
    document.getElementById('mf-mapa').classList.add('desenhando');
    document.getElementById('mf-desenho').hidden = false;
    BS.dom.texto('mf-desenho-titulo', '✏️ ' + poligonos[id].__props.nome + ':');
    atualizarDesenho();
    desenharTalhoes();
    BS.dom.anunciar('Modo de desenho: clique no mapa nos cantos do ' + poligonos[id].__props.nome + ', em sequência.');
  }

  function adicionarPonto(latlng) {
    if (!desenho) return;
    desenho.pontos.push(latlng);
    atualizarDesenho();
  }

  function atualizarDesenho() {
    var pts = desenho.pontos;
    desenho.linha.setLatLngs(pts);
    desenho.area.setLatLngs(pts.length >= 3 ? pts : []);
    desenho.vertices.clearLayers();
    pts.forEach(function (p, i) {
      L.circleMarker(p, { radius: i === 0 ? 6 : 5, className: 'mf-vertice' + (i === 0 ? ' primeiro' : ''), interactive: false })
        .addTo(desenho.vertices);
    });
    var props = poligonos[desenho.id].__props;
    var info = pts.length < 3
      ? 'Clique no mapa nos cantos do talhão, em sequência (' + pts.length + ' de pelo menos 3 pontos).'
      : pts.length + ' pontos · prévia ≈ ' + f.num(areaHa(pts), 2) + ' ha (cadastro: ' + f.num(props.areaCadastroHa, 1) +
        ' ha). A área oficial é calculada pelo servidor.';
    BS.dom.texto('mf-desenho-info', info);
    document.getElementById('mf-desenho-desfazer').disabled = pts.length === 0;
    document.getElementById('mf-desenho-salvar').disabled = pts.length < 3;
  }

  function desfazerPonto() {
    if (!desenho || !desenho.pontos.length) return;
    desenho.pontos.pop();
    atualizarDesenho();
  }

  function encerrarDesenho() {
    if (!desenho) return;
    [desenho.linha, desenho.area, desenho.vertices].forEach(function (c) { mapa.removeLayer(c); });
    desenho = null;
    mapa.doubleClickZoom.enable();
    document.getElementById('mf-mapa').classList.remove('desenhando');
    document.getElementById('mf-desenho').hidden = true;
    if (dados) desenharTalhoes();
  }

  function cancelarDesenho() {
    encerrarDesenho();
  }

  async function salvarDesenho() {
    if (!desenho || desenho.pontos.length < 3) return;
    var id = desenho.id;
    var nome = poligonos[id].__props.nome;
    var coords = desenho.pontos.map(function (p) { return [p.lng, p.lat]; });
    var botao = document.getElementById('mf-desenho-salvar');
    botao.disabled = true;
    try {
      var r = await BS.mapaService.salvarArea(id, coords);
      if (r.ok) {
        encerrarDesenho();
        // O aviso visual vem da notificação do evento gravado no histórico ("Área marcada no mapa")
        BS.dom.anunciar('Área do ' + nome + ' salva: ' + f.num(r.dados.properties.areaMapaHa, 2) + ' hectares.');
        await atualizar();
        selecionar(id, false);
      } else {
        var d = r.dados || {};
        BS.toast({ severidade: 'CRITICO', titulo: 'Área não salva', descricao: ' ' +
          ((d.detalhes && d.detalhes.length ? d.detalhes.join(' ') : d.mensagem) || 'Erro ' + r.status) });
        botao.disabled = false;
      }
    } catch (e) {
      BS.toast({ severidade: 'CRITICO', titulo: 'Sem conexão', descricao: ' A área não foi salva. Tente de novo.' });
      botao.disabled = false;
    }
  }

  async function removerArea(id) {
    var p = poligonos[id] && poligonos[id].__props;
    if (!p || !window.confirm('Apagar a área desenhada do ' + p.nome + '? Ele volta à posição ilustrativa. ' +
      'O cadastro e o histórico não mudam.')) return;
    try {
      var r = await BS.mapaService.removerArea(id);
      if (r.ok) {
        BS.dom.anunciar(p.nome + ' voltou à posição ilustrativa.');
        await atualizar();
      } else {
        BS.toast({ severidade: 'CRITICO', titulo: 'Não foi possível remover', descricao: ' ' + ((r.dados && r.dados.mensagem) || '') });
      }
    } catch (e) {
      BS.toast({ severidade: 'CRITICO', titulo: 'Sem conexão', descricao: ' Tente de novo em instantes.' });
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Capitão Poço: IBGE e Open-Meteo (consultados pelo servidor)
  // ---------------------------------------------------------------------------------------------

  function inteiro(v) { return v == null ? '--' : Math.round(v).toLocaleString('pt-BR'); }

  async function carregarPublicos() {
    var fontes = [];
    try {
      var m = await BS.mapaService.municipio();
      if (m.ok) {
        renderizarMunicipio(m.dados);
        (m.dados.fontes || []).forEach(function (x) { fontes.push(x); });
      }
    } catch (e) { BS.dom.html('mf-regiao', '<p class="muted small">Sem conexão com a API.</p>'); }
    try {
      var c = await BS.mapaService.clima();
      if (c.ok) {
        renderizarClima(c.dados);
        if (c.dados.disponivel && c.dados.fonte) fontes.push(c.dados.fonte);
      }
    } catch (e) { BS.dom.html('mf-clima', ''); }
    BS.dom.html('mf-fontes', fontes.length ? 'Fontes: ' + fontes.map(function (x) {
      return '<a href="' + f.esc(x.url) + '" target="_blank" rel="noopener" title="' + f.esc(x.descricao) + '">' + f.esc(x.nome) + '</a>';
    }).join(' · ') : '');
  }

  function renderizarMunicipio(m) {
    if (!m.disponivel) {
      BS.dom.html('mf-regiao', '<p class="mf-indisponivel">🌐 Dados do IBGE indisponíveis agora. ' + f.esc(m.aviso || '') +
        ' Nenhum valor é estimado no lugar.</p>');
      return;
    }
    var linhas = [];
    if (m.mesorregiao) linhas.push(['Mesorregião', m.mesorregiao]);
    if (m.microrregiao) linhas.push(['Microrregião', m.microrregiao]);
    if (m.areaKm2 != null) linhas.push(['Área territorial', inteiro(m.areaKm2) + ' km²']);
    var p = m.producao;
    if (p && p.laranja && !p.laranja.semProducao) {
      linhas.push(['🍊 Laranja (PAM ' + p.ano + ')', inteiro(p.laranja.producaoT) + ' t · ' + inteiro(p.laranja.areaColhidaHa) + ' ha colhidos']);
      if (p.rankingLaranja) linhas.push(['No Pará', p.rankingLaranja.posicao + 'º produtor · ' +
        f.num(p.rankingLaranja.participacaoPct, 1) + '% do estado']);
    }
    if (p && p.limao) linhas.push(['🍋 Limão (PAM ' + p.ano + ')', p.limao.semProducao ? 'sem produção registrada' : inteiro(p.limao.producaoT) + ' t']);
    BS.dom.html('mf-regiao', '<dl class="mf-dados">' + linhas.map(function (l) {
      return '<div><dt>' + f.esc(l[0]) + '</dt><dd>' + f.esc(l[1]) + '</dd></div>';
    }).join('') + '</dl>' + (m.aviso ? '<p class="muted small">' + f.esc(m.aviso) + '</p>' : ''));

    if (m.contorno && !contorno && mapa) {
      contorno = L.geoJSON(m.contorno, { style: function () { return { className: 'mf-municipio' }; }, interactive: false });
      contorno.addTo(camadaMunicipio);
    }
  }

  function renderizarClima(c) {
    if (!c.disponivel) {
      BS.dom.html('mf-clima', '<p class="mf-indisponivel">🌦️ Clima indisponível agora. ' + f.esc(c.aviso || '') + '</p>');
      return;
    }
    var hora = c.horarioLocal && c.horarioLocal.length >= 16 ? c.horarioLocal.substring(11, 16) : '';
    BS.dom.html('mf-clima',
      '<p class="mf-clima-agora"><span class="mf-clima-temp">' + f.num(c.temperatura, 0) + ' °C</span> ' +
      f.esc(c.descricaoTempo || '') + '<br><small>Umidade do ar ' + inteiro(c.umidadeAr) + '% · vento ' +
      f.num(c.ventoKmh, 0) + ' km/h' + (hora ? ' · ' + hora + ' (Belém)' : '') + '</small></p>' +
      '<dl class="mf-dados">' +
      '<div><dt>Chuva (7 dias)</dt><dd>' + f.num(c.chuvaUltimos7DiasMm, 1) + ' mm</dd></div>' +
      '<div><dt>Prevista (3 dias)</dt><dd>' + f.num(c.chuvaPrevista3DiasMm, 1) + ' mm</dd></div>' +
      '<div><dt>ET0 hoje</dt><dd>' + f.num(c.et0HojeMm, 1) + ' mm</dd></div>' +
      '</dl>' + (c.aviso ? '<p class="muted small">' + f.esc(c.aviso) + '</p>' : ''));
  }

  // ---------------------------------------------------------------------------------------------
  // Agente agrícola (POST /mapa/agente)
  // ---------------------------------------------------------------------------------------------

  function atualizarSeletorAgente() {
    var sel = document.getElementById('ag-alvo');
    var ids = dados.features.map(function (ft) { return ft.id; }).join(',');
    if (sel.__ids === ids) return;
    sel.__ids = ids;
    var atual = sel.value;
    sel.innerHTML = '<option value="">Fazenda inteira</option>' + dados.features.map(function (ft) {
      return '<option value="' + f.esc(ft.id) + '">' + f.esc(ft.properties.nome) + '</option>';
    }).join('');
    if (atual && sel.querySelector('option[value="' + atual + '"]')) sel.value = atual;
  }

  function analisarTalhao(id) {
    var sel = document.getElementById('ag-alvo');
    if (sel.querySelector('option[value="' + id + '"]')) sel.value = id;
    perguntar({ talhaoId: id, tema: 'TALHAO' });
    var card = document.getElementById('agente');
    card.scrollIntoView({ behavior: reduzido() ? 'auto' : 'smooth', block: 'start' });
  }

  /** Chamado pelo Citrus ("Ver análise completa"): a mesma análise, completa, no card do agente. */
  function consultar(tema, talhaoId) {
    var sel = document.getElementById('ag-alvo');
    if (!talhaoId) sel.value = '';
    else if (sel.querySelector('option[value="' + talhaoId + '"]')) sel.value = talhaoId;
    perguntar({ tema: tema || null, talhaoId: talhaoId || null });
    document.getElementById('agente').scrollIntoView({ behavior: reduzido() ? 'auto' : 'smooth', block: 'start' });
  }

  async function perguntar(corpo) {
    var caixa = document.getElementById('ag-resposta');
    caixa.setAttribute('aria-busy', 'true');
    BS.dom.html(caixa, '<p class="ag-carregando"><span aria-hidden="true">🤖</span> Analisando os dados da fazenda e das fontes públicas…</p>');
    try {
      var r = await BS.mapaService.agente(corpo);
      if (!r.ok) {
        BS.dom.html(caixa, '<p class="ag-aviso">⚠️ ' + f.esc((r.dados && (r.dados.detalhes || []).concat([r.dados.mensagem]).filter(Boolean).join(' ')) ||
          'O agente não conseguiu responder agora.') + '</p>');
        return;
      }
      renderizarResposta(r.dados);
    } catch (e) {
      BS.dom.html(caixa, '<p class="ag-aviso">⚠️ Sem conexão com a API. Tente novamente em instantes.</p>');
    } finally {
      caixa.removeAttribute('aria-busy');
    }
  }

  function renderizarResposta(r) {
    var html = '<header class="ag-cab"><h4>' + f.esc(r.titulo) + '</h4><p>' + f.esc(r.resumo) + '</p></header>';
    (r.avisos || []).forEach(function (a) { html += '<p class="ag-aviso">⚠️ ' + f.esc(a) + '</p>'; });
    html += '<ul class="ag-itens">' + r.itens.map(function (it) {
      return '<li class="ag-item nivel-' + f.esc(it.nivel) + '">' +
        '<div class="ag-item-cab"><span class="ag-tipo ' + f.esc(it.tipo) + '">' + (TIPOS[it.tipo] || f.esc(it.tipo)) + '</span>' +
        '<b>' + f.esc(it.titulo) + '</b></div>' +
        '<p>' + f.esc(it.texto) + '</p>' +
        '<small>Fonte: ' + f.esc(it.fonte) + '</small></li>';
    }).join('') + '</ul>';
    html += '<p class="ag-rodape">Análise gerada às ' + f.hora(r.geradoEm) +
      (r.perguntaRecebida ? ' · pergunta: “' + f.esc(r.perguntaRecebida) + '”' : '') +
      ' · agente baseado em regras e dados reais (sem modelo de linguagem).</p>';
    BS.dom.html('ag-resposta', html);
  }

  function ligarAgente() {
    BS.dom.$$('.ag-temas [data-tema]').forEach(function (b) {
      b.addEventListener('click', function () {
        var alvo = document.getElementById('ag-alvo').value;
        perguntar({ tema: b.getAttribute('data-tema'), talhaoId: alvo || null });
      });
    });
    document.getElementById('ag-form').addEventListener('submit', function (e) {
      e.preventDefault();
      var input = document.getElementById('ag-pergunta');
      var texto = input.value.trim();
      if (!texto) { input.focus(); return; }
      perguntar({ pergunta: texto, talhaoId: document.getElementById('ag-alvo').value || null });
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Ciclo de vida da seção
  // ---------------------------------------------------------------------------------------------

  async function abrir() {
    try {
      var Lf = await carregarLeaflet();
      if (!mapa) criarMapa(Lf);
    } catch (e) {
      estado('Não foi possível carregar a biblioteca do mapa (vendor/leaflet). Os dados e o agente continuam disponíveis abaixo.');
    }
    if (mapa) setTimeout(function () { mapa.invalidateSize(); }, 50);
    if (mapa) await atualizar();
    if (!timer) timer = setInterval(function () { if (!document.hidden && mapa) atualizar(); }, INTERVALO_MS);
    if (!timerPublico) {
      carregarPublicos();
      timerPublico = setInterval(carregarPublicos, INTERVALO_PUBLICO_MS);
    }
  }

  function pausar() {
    if (desenho) cancelarDesenho();
    if (timer) { clearInterval(timer); timer = null; }
    if (timerPublico) { clearInterval(timerPublico); timerPublico = null; }
  }

  function teclado(e) {
    if (!desenho) return;
    var digitando = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target && e.target.tagName) || '');
    if (e.key === 'Escape') { e.preventDefault(); cancelarDesenho(); }
    else if (!digitando && (e.key === 'Backspace' || (e.key === 'z' && (e.ctrlKey || e.metaKey)))) { e.preventDefault(); desfazerPonto(); }
    else if (!digitando && e.key === 'Enter' && desenho.pontos.length >= 3) { e.preventDefault(); salvarDesenho(); }
  }

  function iniciar() {
    ligarAgente();
    document.getElementById('mf-ir-fazenda').addEventListener('click', enquadrarFazenda);
    document.getElementById('mf-ir-municipio').addEventListener('click', enquadrarMunicipio);
    document.getElementById('mf-desenho-desfazer').addEventListener('click', desfazerPonto);
    document.getElementById('mf-desenho-salvar').addEventListener('click', salvarDesenho);
    document.getElementById('mf-desenho-cancelar').addEventListener('click', cancelarDesenho);
    document.addEventListener('keydown', teclado);
    document.getElementById('ag-alvo').addEventListener('change', function (e) {
      if (e.target.value && poligonos[e.target.value]) selecionar(e.target.value, true);
    });
    BS.componentes.abas.aoMudar(function (nome) { if (nome === 'mapa') abrir(); else pausar(); });
    if (BS.componentes.abas.atual() === 'mapa') abrir();
  }

  return { iniciar: iniciar, analisar: analisarTalhao, desenhar: iniciarDesenho, consultar: consultar };
})();
