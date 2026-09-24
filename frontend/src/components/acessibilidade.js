/* Central de Acessibilidade. localStorage guarda SOMENTE preferências visuais (permitido pelo regulamento). */
BS.preferencias = (function () {
  var CHAVE = 'biosolar.preferencias';
  var PADRAO = { tema: 'sistema', fonte: 1, contraste: false, cinza: false, movimento: false, vozAuto: false, libras: false };
  var prefs = carregar();

  function carregar() {
    try { return Object.assign({}, PADRAO, JSON.parse(localStorage.getItem(CHAVE) || '{}')); }
    catch (e) { return Object.assign({}, PADRAO); }
  }
  function salvar() {
    try { localStorage.setItem(CHAVE, JSON.stringify(prefs)); } catch (e) { /* modo privado: segue sem salvar */ }
  }
  function aplicar() {
    var r = document.documentElement;
    if (prefs.tema === 'light' || prefs.tema === 'dark') r.setAttribute('data-theme', prefs.tema);
    else r.removeAttribute('data-theme');
    r.style.setProperty('--fs', prefs.fonte);
    BS.dom.attr(r, 'data-contrast', prefs.contraste ? 'alto' : null);
    BS.dom.attr(r, 'data-grayscale', prefs.cinza ? 'true' : null);
    BS.dom.attr(r, 'data-motion', prefs.movimento ? 'reduzido' : null);
  }

  return {
    obter: function () { return prefs; },
    definir: function (chave, valor) { prefs[chave] = valor; salvar(); aplicar(); },
    restaurar: function () { prefs = Object.assign({}, PADRAO); salvar(); aplicar(); },
    temaEfetivo: function () {
      if (prefs.tema === 'light' || prefs.tema === 'dark') return prefs.tema;
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
  };
})();

BS.componentes.acessibilidade = (function () {
  var P = BS.preferencias;
  var ESCALAS = [0.875, 1, 1.125, 1.25, 1.375, 1.5];
  var librasCarregado = false;

  function sincronizar() {
    var p = P.obter();
    BS.dom.texto('fonte-valor', Math.round(p.fonte * 100) + '%');
    BS.dom.$$('[data-tema]').forEach(function (b) { b.setAttribute('aria-pressed', String(b.getAttribute('data-tema') === p.tema)); });
    document.getElementById('pref-contraste').checked = p.contraste;
    document.getElementById('pref-cinza').checked = p.cinza;
    document.getElementById('pref-movimento').checked = p.movimento;
    document.getElementById('pref-voz-auto').checked = p.vozAuto;
    document.getElementById('pref-libras').checked = p.libras;
    document.querySelector('meta[name="theme-color"]').setAttribute('content', P.temaEfetivo() === 'dark' ? '#0f1512' : '#ffffff');
  }

  function mudarFonte(passo) {
    var atual = ESCALAS.indexOf(P.obter().fonte);
    if (atual < 0) atual = 1;
    var novo = passo === 0 ? 1 : ESCALAS[Math.max(0, Math.min(ESCALAS.length - 1, atual + passo))];
    P.definir('fonte', novo);
    sincronizar();
    BS.dom.anunciar('Tamanho da fonte: ' + Math.round(novo * 100) + '%');
  }

  function libras(ativar) {
    var area = document.getElementById('vlibras');
    var status = document.getElementById('libras-status');
    area.hidden = !ativar;
    if (!ativar || librasCarregado) return;
    status.textContent = 'Carregando VLibras…';
    var s = document.createElement('script');
    s.src = 'https://vlibras.gov.br/app/vlibras-plugin.js';
    s.onload = function () {
      try {
        new window.VLibras.Widget('https://vlibras.gov.br/app');
        if (document.readyState === 'complete' && typeof window.onload === 'function') window.onload();
        librasCarregado = true;
        status.textContent = 'VLibras ativo: use o botão azul na lateral da tela.';
      } catch (e) {
        status.textContent = 'Não foi possível iniciar o VLibras.';
      }
    };
    s.onerror = function () { status.textContent = 'VLibras indisponível (sem conexão com a internet). O restante do sistema segue funcionando.'; };
    document.body.appendChild(s);
  }

  function iniciar() {
    var dlg = document.getElementById('dlg-a11y');
    document.getElementById('btn-a11y').addEventListener('click', function () { sincronizar(); BS.dom.abrirDialogo(dlg); });
    document.getElementById('fonte-menos').addEventListener('click', function () { mudarFonte(-1); });
    document.getElementById('fonte-mais').addEventListener('click', function () { mudarFonte(1); });
    document.getElementById('fonte-padrao').addEventListener('click', function () { mudarFonte(0); });
    BS.dom.$$('[data-tema]').forEach(function (b) {
      b.addEventListener('click', function () { P.definir('tema', b.getAttribute('data-tema')); sincronizar(); });
    });
    [['pref-contraste', 'contraste'], ['pref-cinza', 'cinza'], ['pref-movimento', 'movimento'], ['pref-voz-auto', 'vozAuto']].forEach(function (par) {
      document.getElementById(par[0]).addEventListener('change', function (e) { P.definir(par[1], e.target.checked); });
    });
    document.getElementById('pref-libras').addEventListener('change', function (e) { P.definir('libras', e.target.checked); libras(e.target.checked); });
    document.getElementById('a11y-ouvir').addEventListener('click', function () { BS.app.falarStatus(); });
    document.getElementById('a11y-reset').addEventListener('click', function () { P.restaurar(); sincronizar(); libras(false); });

    document.getElementById('btn-tema').addEventListener('click', function () {
      var novo = P.temaEfetivo() === 'dark' ? 'light' : 'dark';
      P.definir('tema', novo);
      sincronizar();
      BS.dom.anunciar('Tema ' + (novo === 'dark' ? 'escuro' : 'claro') + ' ativado');
    });

    sincronizar();
    if (P.obter().libras) libras(true);
  }

  return { iniciar: iniciar };
})();
