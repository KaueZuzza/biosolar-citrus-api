/* Painel de Simulação (modo demonstração): envia ao servidor mudanças nas condições físicas. */
BS.componentes.painelSimulacao = (function () {
  var pausada = false;

  /** Recebe uma função que cria a requisição: com o botão ocupado, cliques repetidos são ignorados
   *  (um duplo clique em "Reduzir 10%" não pode aplicar −20%). */
  async function executar(requisicao, botao) {
    if (botao && botao.getAttribute('aria-busy') === 'true') return;
    if (botao) botao.setAttribute('aria-busy', 'true');
    try {
      var r = await requisicao();
      var d = r.dados || {};
      if (r.ok) BS.toast({ severidade: 'INFO', icone: '🎬', titulo: 'Simulação', descricao: ' ' + (d.mensagem || 'Comando aplicado.') });
      else BS.toast({ severidade: 'ATENCAO', titulo: 'Simulação recusada', descricao: ' ' + (d.mensagem || ('HTTP ' + r.status)) });
    } catch (e) {
      BS.toast({ severidade: 'CRITICO', titulo: 'Servidor indisponível', descricao: ' Não foi possível alterar a simulação.' });
    } finally {
      if (botao) botao.removeAttribute('aria-busy');
      BS.app.atualizarAgora();
    }
  }

  function iniciar() {
    var s = BS.simulacaoService;
    document.getElementById('sim-velocidades').addEventListener('click', function (e) {
      var b = e.target.closest('[data-fator]');
      if (b) executar(function () { return s.velocidade(Number(b.getAttribute('data-fator'))); }, b);
    });
    document.getElementById('sim-pausa').addEventListener('click', function (e) {
      executar(function () { return s.pausa(!pausada); }, e.currentTarget);
    });
    BS.dom.$$('[data-umidade]').forEach(function (b) {
      b.addEventListener('click', function () {
        executar(function () {
          return s.umidade(document.getElementById('sim-talhao').value, Number(b.getAttribute('data-umidade')));
        }, b);
      });
    });
    BS.dom.$$('[data-reservatorio]').forEach(function (b) {
      b.addEventListener('click', function () {
        executar(function () { return s.reservatorio(Number(b.getAttribute('data-reservatorio'))); }, b);
      });
    });
    document.getElementById('sim-emergencia').addEventListener('click', function (e) {
      executar(function () { return s.emergencia(); }, e.currentTarget);
    });
    document.getElementById('sim-restaurar').addEventListener('click', function (e) {
      var botao = e.currentTarget;
      if (botao.getAttribute('aria-busy') === 'true') return;
      if (window.confirm('Restaurar o cenário de demonstração?\n\nA fazenda volta aos níveis iniciais cadastrados ' +
          '(umidade de cada talhão e nível do reservatório), os aspersores desligam e o histórico de eventos e ' +
          'leituras é reiniciado. O cadastro dos talhões é mantido.')) {
        executar(function () { return s.restaurar(); }, botao);
      }
    });

    // Painel lateral não modal: o dashboard continua visível enquanto a demonstração acontece
    var botaoDemo = document.getElementById('btn-demo');
    var painel = document.getElementById('painel-demo');
    function abrir(sim) {
      painel.hidden = !sim;
      botaoDemo.setAttribute('aria-expanded', String(sim));
      if (sim) document.getElementById('sim-fechar').focus();
      else botaoDemo.focus();
    }
    botaoDemo.addEventListener('click', function () { abrir(painel.hidden); });
    document.getElementById('sim-fechar').addEventListener('click', function () { abrir(false); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !painel.hidden && !document.querySelector('dialog[open]')) abrir(false);
    });
  }

  /** Opções do seletor de talhão acompanham o cadastro (mantendo a seleção atual). */
  function sincronizarTalhoes(talhoes) {
    var select = document.getElementById('sim-talhao');
    var chave = talhoes.map(function (x) { return x.id + '=' + x.nome; }).join('|');
    if (select.__chave === chave) return;
    select.__chave = chave;
    var anterior = select.value;
    select.innerHTML = talhoes.map(function (x) {
      return '<option value="' + BS.fmt.esc(x.id) + '">' + BS.fmt.esc(x.nome) + '</option>';
    }).join('');
    var ids = talhoes.map(function (x) { return x.id; });
    if (ids.indexOf(anterior) >= 0) select.value = anterior;
    else if (ids.indexOf('C') >= 0) select.value = 'C';   // roteiro da banca começa pelo talhão arenoso
  }

  function atualizar(tel) {
    var sim = tel.simulacao;
    pausada = sim.pausada;
    BS.dom.$$('#sim-velocidades [data-fator]').forEach(function (b) {
      BS.dom.attr(b, 'aria-pressed', String(Number(b.getAttribute('data-fator')) === sim.fatorVelocidade));
    });
    var pausa = document.getElementById('sim-pausa');
    BS.dom.attr(pausa, 'aria-pressed', String(sim.pausada));
    BS.dom.texto(pausa, sim.pausada ? '▶ Retomar' : '⏸ Pausar');
    sincronizarTalhoes(tel.talhoes);
    document.getElementById('btn-demo').hidden = !sim.controlesHabilitados;
    if (!sim.controlesHabilitados) document.getElementById('painel-demo').hidden = true;
  }

  return { iniciar: iniciar, atualizar: atualizar };
})();
