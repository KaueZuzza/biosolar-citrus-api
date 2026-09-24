/* Gráficos SVG leves (sem dependências, funcionam offline).
 * Especificação: linhas de 2px, área a 10%, grade de 1px discreta, marcadores >= 8px com anel na cor da
 * superfície, colunas <= 24px com topo arredondado de 4px e 2px de espaço entre barras, linha de referência + tooltip
 * ao passar o mouse e navegação por teclado (← →). Cores sempre por variável CSS (tema claro/escuro). */
(function () {
  var esc = function (s) { return BS.fmt.esc(s); };

  function ticksLimpos(max) {
    var passo = max <= 5 ? 1 : max <= 10 ? 2 : max <= 25 ? 5 : max <= 50 ? 10 : 20;
    var topo = Math.max(passo * 2, Math.ceil(max / passo) * passo);
    var ticks = [];
    for (var v = 0; v <= topo; v += passo) ticks.push(v);
    return { topo: topo, ticks: ticks };
  }

  function prepararContainer(container, render) {
    if (container.__preparado) return;
    container.__preparado = true;
    container.__render = render;
    if ('ResizeObserver' in window) {
      var largura = 0;
      new ResizeObserver(function () {
        var w = Math.round(container.clientWidth);
        if (w !== largura && container.__cfg) { largura = w; container.__render(container, container.__cfg); }
      }).observe(container);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Linhas (uma ou mais séries sobre o mesmo eixo)
  // ---------------------------------------------------------------------------------------------
  function linhas(container, cfg) {
    prepararContainer(container, linhas);
    container.__cfg = cfg;
    var st = container.__st || (container.__st = { idx: null });

    var W = Math.max(280, Math.round(container.clientWidth || 600));
    var H = cfg.altura || 240;
    var multi = cfg.series.length > 1;
    var m = { t: 14, r: multi ? 58 : 18, b: 28, l: 40 };
    var iw = W - m.l - m.r;
    var ih = H - m.t - m.b;
    var n = cfg.rotulos.length;
    var yMin = cfg.yMin || 0;
    var yMax = cfg.yMax || 100;

    if (n < 2) {
      container.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + H + '" aria-hidden="true"><rect class="chart-bg" width="' + W + '" height="' + H + '" rx="8"/>' +
        '<text class="vazio" x="' + W / 2 + '" y="' + H / 2 + '" text-anchor="middle">Aguardando leituras do servidor…</text></svg>';
      return;
    }

    var x = function (i) { return m.l + i * iw / (n - 1); };
    var y = function (v) { return m.t + ih - (Math.max(yMin, Math.min(yMax, v)) - yMin) / (yMax - yMin) * ih; };
    st.geo = { x: x, y: y, n: n, m: m, W: W, H: H, iw: iw };

    var p = [];
    p.push('<svg viewBox="0 0 ' + W + ' ' + H + '" width="' + W + '" height="' + H + '" aria-hidden="true" focusable="false">');
    p.push('<rect class="chart-bg" width="' + W + '" height="' + H + '" rx="8"/>');

    (cfg.faixas || []).forEach(function (f) {
      p.push('<rect x="' + m.l + '" width="' + iw + '" y="' + y(f.ate) + '" height="' + (y(f.de) - y(f.ate)) +
        '" style="fill:' + f.cor + ';fill-opacity:' + (f.opacidade || 0.08) + '"/>');
    });

    (cfg.ticks || [0, 25, 50, 75, 100]).forEach(function (t) {
      p.push('<line class="grid-line" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(t) + '" y2="' + y(t) + '"/>');
      p.push('<text class="axis-label" x="' + (m.l - 8) + '" y="' + (y(t) + 4) + '" text-anchor="end">' + t + (cfg.unidade || '') + '</text>');
    });
    p.push('<line class="base-line" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(yMin) + '" y2="' + y(yMin) + '"/>');

    var qtdRotulos = Math.max(2, Math.min(6, Math.floor(iw / 90)));
    for (var k = 0; k < qtdRotulos; k++) {
      var i = Math.round(k * (n - 1) / (qtdRotulos - 1));
      var ancora = k === 0 ? 'start' : k === qtdRotulos - 1 ? 'end' : 'middle';
      p.push('<text class="axis-label" x="' + x(i) + '" y="' + (H - 8) + '" text-anchor="' + ancora + '">' + esc(cfg.rotulos[i]) + '</text>');
    }

    (cfg.referencias || []).forEach(function (r) {
      p.push('<line class="ref-line" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(r.valor) + '" y2="' + y(r.valor) + '" style="stroke:' + r.cor + '"/>');
      p.push('<text class="ref-label" x="' + (m.l + 6) + '" y="' + (y(r.valor) - 5) + '">' + esc(r.rotulo) + '</text>');
    });

    cfg.series.forEach(function (s) {
      var d = '';
      s.valores.forEach(function (v, i) {
        if (v === null || v === undefined) return;
        d += (d ? 'L' : 'M') + x(i).toFixed(1) + ',' + y(v).toFixed(1);
      });
      if (!d) return;
      if (cfg.area) {
        p.push('<path d="' + d + 'L' + x(n - 1).toFixed(1) + ',' + y(yMin) + 'L' + x(0).toFixed(1) + ',' + y(yMin) + 'Z" style="fill:' + s.cor + ';fill-opacity:.10"/>');
      }
      p.push('<path d="' + d + '" fill="none" style="stroke:' + s.cor + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>');
    });

    // Rótulos finais: só quando não colidem (a legenda e o tooltip cobrem o restante)
    var finais = cfg.series.map(function (s) {
      var v = s.valores[n - 1];
      return { s: s, v: v, yy: v === null || v === undefined ? null : y(v) };
    }).filter(function (f) { return f.yy !== null; }).sort(function (a, b) { return a.yy - b.yy; });
    finais.forEach(function (f, idx) {
      p.push('<circle cx="' + x(n - 1) + '" cy="' + f.yy + '" r="4" style="fill:' + f.s.cor + ';stroke:var(--chart-surface)" stroke-width="2"/>');
      var colide = finais.some(function (o, j) { return j !== idx && Math.abs(o.yy - f.yy) < 13 && j < idx; });
      if (multi && !colide) {
        p.push('<text class="end-label" x="' + (x(n - 1) + 8) + '" y="' + (f.yy + 4) + '">' + esc(f.s.nome) + ' ' + BS.fmt.num(f.v, 0) + '</text>');
      }
    });

    p.push('<g class="hover" style="display:none"><line class="crosshair" y1="' + m.t + '" y2="' + (m.t + ih) + '"/>');
    cfg.series.forEach(function () {
      p.push('<circle r="4.5" stroke-width="2" style="stroke:var(--chart-surface)"/>');
    });
    p.push('</g></svg><div class="tooltip" aria-hidden="true"></div>');

    container.innerHTML = p.join('');
    ligarInteracao(container, mostrarLinha);
    if (st.idx !== null) mostrarLinha(container, Math.min(st.idx, n - 1));
  }

  function mostrarLinha(container, idx) {
    var st = container.__st, cfg = container.__cfg, g = st.geo;
    if (!g || idx === null) return;
    st.idx = idx;
    var hover = container.querySelector('.hover');
    var tip = container.querySelector('.tooltip');
    if (!hover || !tip) return;
    hover.style.display = '';
    var cx = g.x(idx);
    var linha = hover.querySelector('line');
    linha.setAttribute('x1', cx); linha.setAttribute('x2', cx);
    var circulos = hover.querySelectorAll('circle');
    var linhasTip = '';
    cfg.series.forEach(function (s, i) {
      var v = s.valores[idx];
      var c = circulos[i];
      if (v === null || v === undefined) { c.style.display = 'none'; return; }
      c.style.display = '';
      c.setAttribute('cx', cx); c.setAttribute('cy', g.y(v));
      c.style.fill = s.cor;
      linhasTip += '<div class="tt-row"><span><i class="key" style="background:' + s.cor + '"></i>' + esc(s.nome) + '</span><b>' +
        BS.fmt.num(v, 1) + (cfg.unidade || '') + '</b></div>';
    });
    tip.innerHTML = '<div class="tt-title">' + esc(cfg.titulosTooltip ? cfg.titulosTooltip[idx] : cfg.rotulos[idx]) + '</div>' + linhasTip;
    posicionarTooltip(container, tip, cx, g);
  }

  // ---------------------------------------------------------------------------------------------
  // Colunas agrupadas (categorias x séries)
  // ---------------------------------------------------------------------------------------------
  function colunas(container, cfg) {
    prepararContainer(container, colunas);
    container.__cfg = cfg;
    var st = container.__st || (container.__st = { idx: null });

    var W = Math.max(280, Math.round(container.clientWidth || 500));
    var H = cfg.altura || 220;
    var m = { t: 18, r: 12, b: 28, l: 34 };
    var iw = W - m.l - m.r, ih = H - m.t - m.b;
    var nc = cfg.categorias.length, ns = cfg.series.length;
    var max = 0;
    cfg.series.forEach(function (s) { s.valores.forEach(function (v) { max = Math.max(max, v || 0); }); });
    var escala = ticksLimpos(max);
    var y = function (v) { return m.t + ih - v / escala.topo * ih; };
    var banda = iw / nc;
    var larg = Math.min(24, (banda * 0.7 - (ns - 1) * 2) / ns);
    var grupo = ns * larg + (ns - 1) * 2;
    var xBarra = function (c, s) { return m.l + c * banda + (banda - grupo) / 2 + s * (larg + 2); };
    st.geo = { m: m, W: W, H: H, banda: banda, n: nc, x: function (c) { return m.l + c * banda + banda / 2; } };

    var p = [];
    p.push('<svg viewBox="0 0 ' + W + ' ' + H + '" width="' + W + '" height="' + H + '" aria-hidden="true" focusable="false">');
    p.push('<rect class="chart-bg" width="' + W + '" height="' + H + '" rx="8"/>');
    escala.ticks.forEach(function (t) {
      p.push('<line class="grid-line" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(t) + '" y2="' + y(t) + '"/>');
      p.push('<text class="axis-label" x="' + (m.l - 8) + '" y="' + (y(t) + 4) + '" text-anchor="end">' + t + '</text>');
    });
    p.push('<rect class="hl" x="0" y="' + m.t + '" width="0" height="' + ih + '" style="fill:var(--ink);fill-opacity:.05"/>');
    cfg.categorias.forEach(function (cat, c) {
      cfg.series.forEach(function (s, si) {
        var v = s.valores[c] || 0;
        var bx = xBarra(c, si), h = y(0) - y(v);
        if (v > 0) {
          var r = Math.min(4, larg / 2, h);
          var y0 = y(0), topo = y0 - h;
          p.push('<path d="M' + bx + ',' + y0 + 'V' + (topo + r) + 'Q' + bx + ',' + topo + ' ' + (bx + r) + ',' + topo +
            'H' + (bx + larg - r) + 'Q' + (bx + larg) + ',' + topo + ' ' + (bx + larg) + ',' + (topo + r) + 'V' + y0 + 'Z" style="fill:' + s.cor + '"/>');
          p.push('<text class="axis-label" x="' + (bx + larg / 2) + '" y="' + (topo - 5) + '" text-anchor="middle" style="fill:var(--ink-2);font-weight:700">' + v + '</text>');
        }
      });
      p.push('<text class="axis-label" x="' + (m.l + c * banda + banda / 2) + '" y="' + (H - 8) + '" text-anchor="middle">' + esc(cat) + '</text>');
    });
    p.push('<line class="base-line" x1="' + m.l + '" x2="' + (W - m.r) + '" y1="' + y(0) + '" y2="' + y(0) + '"/>');
    p.push('</svg><div class="tooltip" aria-hidden="true"></div>');
    container.innerHTML = p.join('');
    ligarInteracao(container, mostrarColuna);
    if (st.idx !== null) mostrarColuna(container, Math.min(st.idx, nc - 1));
  }

  function mostrarColuna(container, idx) {
    var st = container.__st, cfg = container.__cfg, g = st.geo;
    if (!g || idx === null) return;
    st.idx = idx;
    var hl = container.querySelector('.hl');
    var tip = container.querySelector('.tooltip');
    hl.setAttribute('x', g.m.l + idx * g.banda);
    hl.setAttribute('width', g.banda);
    var linhasTip = cfg.series.map(function (s) {
      return '<div class="tt-row"><span><i class="key sq" style="background:' + s.cor + '"></i>' + esc(s.nome) + '</span><b>' + (s.valores[idx] || 0) + '</b></div>';
    }).join('');
    tip.innerHTML = '<div class="tt-title">' + esc(cfg.titulos ? cfg.titulos[idx] : cfg.categorias[idx]) + '</div>' + linhasTip;
    posicionarTooltip(container, tip, g.x(idx), g);
  }

  // ---------------------------------------------------------------------------------------------
  // Interação comum: mouse/toque + teclado
  // ---------------------------------------------------------------------------------------------
  function posicionarTooltip(container, tip, cx, g) {
    var escalaX = container.clientWidth / g.W;
    var px = cx * escalaX;
    tip.classList.add('show');
    var largura = tip.offsetWidth || 160;
    var esquerda = px + 14;
    if (esquerda + largura > container.clientWidth) esquerda = px - largura - 14;
    tip.style.left = Math.max(0, esquerda) + 'px';
    tip.style.top = '8px';
  }

  function ocultar(container) {
    container.__st.idx = null;
    var hover = container.querySelector('.hover');
    if (hover) hover.style.display = 'none';
    var hl = container.querySelector('.hl');
    if (hl) hl.setAttribute('width', 0);
    var tip = container.querySelector('.tooltip');
    if (tip) tip.classList.remove('show');
  }

  function ligarInteracao(container, mostrar) {
    if (container.__interacao) return;
    container.__interacao = true;

    function indicePorPonteiro(evento) {
      var g = container.__st.geo;
      if (!g) return null;
      var ret = container.getBoundingClientRect();
      var px = (evento.clientX - ret.left) * (g.W / ret.width);
      if (g.banda) return Math.max(0, Math.min(g.n - 1, Math.floor((px - g.m.l) / g.banda)));
      return Math.max(0, Math.min(g.n - 1, Math.round((px - g.m.l) / g.iw * (g.n - 1))));
    }

    container.addEventListener('pointermove', function (e) {
      var i = indicePorPonteiro(e);
      if (i !== null) container.__mostrar(container, i);
    });
    container.addEventListener('pointerleave', function () { ocultar(container); });
    container.addEventListener('focus', function () {
      var g = container.__st.geo;
      if (g) container.__mostrar(container, g.n - 1);
    });
    container.addEventListener('blur', function () { ocultar(container); });
    container.addEventListener('keydown', function (e) {
      var g = container.__st.geo;
      if (!g) return;
      var atual = container.__st.idx === null ? g.n - 1 : container.__st.idx;
      if (e.key === 'ArrowLeft') { container.__mostrar(container, Math.max(0, atual - 1)); e.preventDefault(); }
      else if (e.key === 'ArrowRight') { container.__mostrar(container, Math.min(g.n - 1, atual + 1)); e.preventDefault(); }
      else if (e.key === 'Home') { container.__mostrar(container, 0); e.preventDefault(); }
      else if (e.key === 'End') { container.__mostrar(container, g.n - 1); e.preventDefault(); }
      else if (e.key === 'Escape') { ocultar(container); }
    });
  }

  function comMostrar(fn, mostrar) {
    return function (container, cfg) {
      container.__mostrar = mostrar;
      fn(container, cfg);
    };
  }

  BS.charts = {
    linhas: comMostrar(linhas, mostrarLinha),
    colunas: comMostrar(colunas, mostrarColuna)
  };
})();
