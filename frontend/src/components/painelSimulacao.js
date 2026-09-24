/* Painel de Simulação (modo demonstração): envia ao servidor mudanças nas condições físicas. */
BS.componentes.painelSimulacao = (function () {
  var pausada = false;

  async function executar(promessa, botao) {
    if (botao) botao.setAttribute('aria-busy', 'true');
    try {
      var r = await promessa;
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
      if (b) executar(s.velocidade(Number(b.getAttribute('data-fator'))), b);
    });
    document.getElementById('sim-pausa').addEventListener('click', function (e) {
      executar(s.pausa(!pausada), e.currentTarget);
    });
    BS.dom.$$('[data-umidade]').forEach(function (b) {
      b.addEventListener('click', function () {
        executar(s.umidade(document.getElementById('sim-talhao').value, Number(b.getAttribute('data-umidade'))), b);
      });
    });
    BS.dom.$$('[data-reservatorio]').forEach(function (b) {
      b.addEventListener('click', function () { executar(s.reservatorio(Number(b.getAttribute('data-reservatorio'))), b); });
    });
    document.getElementById('sim-emergencia').addEventListener('click', function (e) { executar(s.emergencia(), e.currentTarget); });
    document.getElementById('sim-restaurar').addEventListener('click', function (e) {
      if (window.confirm('Restaurar o cenário de demonstração? O histórico de eventos e leituras será reiniciado no servidor.')) {
        executar(s.restaurar(), e.currentTarget);
      }
    });

    var toggle = document.getElementById('sim-toggle');
    toggle.addEventListener('click', function () {
      var corpo = document.getElementById('sim-body');
      var abrir = corpo.hidden;
      corpo.hidden = !abrir;
      toggle.setAttribute('aria-expanded', String(abrir));
      toggle.textContent = abrir ? 'Ocultar' : 'Mostrar';
    });
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
    document.querySelector('.sim-panel').hidden = !sim.controlesHabilitados;
  }

  return { iniciar: iniciar, atualizar: atualizar };
})();
