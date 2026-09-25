/* Visão geral: situação de cada talhão em uma linha (umidade, status e aspersor). Clique abre os detalhes.
 * Os itens são atualizados no lugar (sem recriar o HTML) para não tirar o foco de quem navega pelo teclado. */
BS.componentes.resumoTalhoes = (function () {
  var f = BS.fmt;

  function criarItem(id) {
    var li = document.createElement('li');
    li.setAttribute('data-id', id);
    li.innerHTML = '<button type="button"><span aria-hidden="true" data-icone></span><span class="rt-nome" data-nome></span>' +
      '<span class="rt-umid" data-umid></span><span class="rt-estado" data-estado></span></button>';
    li.querySelector('button').addEventListener('click', function () { BS.componentes.mapaTalhoes.abrir(id); });
    return li;
  }

  function atualizar(tel) {
    var lista = document.getElementById('resumo-talhoes');
    var ids = tel.talhoes.map(function (x) { return x.id; });
    var vazio = lista.querySelector('.vazio-lista');
    if (!ids.length) {
      if (!vazio) lista.innerHTML = '<li class="vazio-lista">Nenhum talhão ativo. Cadastre um talhão na aba <a href="#gestao">Gestão</a>.</li>';
      return;
    }
    if (vazio) vazio.remove();
    BS.dom.$$('li[data-id]', lista).forEach(function (li) { if (ids.indexOf(li.getAttribute('data-id')) < 0) li.remove(); });
    var ordemAtual = BS.dom.$$('li[data-id]', lista).map(function (li) { return li.getAttribute('data-id'); });
    if (ordemAtual.join('|') !== ids.join('|')) {
      ids.forEach(function (id) { lista.appendChild(lista.querySelector('li[data-id="' + CSS.escape(id) + '"]') || criarItem(id)); });
    }

    var bloqueio = tel.reservatorio.bloqueioEmergencia;
    tel.talhoes.forEach(function (x) {
      var li = lista.querySelector('li[data-id="' + CSS.escape(x.id) + '"]');
      var st = BS.status.talhao[x.status];
      var estado = x.irrigacaoBloqueada ? '⛔ irrigação bloqueada'
        : x.aspersorLigado ? '🚿 irrigando (' + BS.status.modo[x.modoAcionamento] + ')'
          : bloqueio ? '🔒 bomba bloqueada' : 'aspersor desligado';
      li.className = 'st-' + x.status;
      BS.dom.texto(li.querySelector('[data-icone]'), st.icone);
      BS.dom.texto(li.querySelector('[data-nome]'), x.nome);
      BS.dom.texto(li.querySelector('[data-umid]'), f.pct(x.umidade));
      BS.dom.texto(li.querySelector('[data-estado]'), (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + x.culturaRotulo + ' · ' + estado);
      BS.dom.attr(li.querySelector('button'), 'aria-label',
        x.nome + ': umidade ' + f.pct(x.umidade) + ', ' + st.rotulo + ', ' + estado + '. Abrir detalhes.');
    });
  }

  return { atualizar: atualizar };
})();
