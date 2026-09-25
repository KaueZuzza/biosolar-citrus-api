/* Tela de carregamento: verifica a API, o banco (GET /saude) e a primeira telemetria.
 * Some sozinha quando tudo está pronto. Com o banco fora do ar o painel abre mesmo assim,
 * pois a automação continua em memória. */
BS.componentes = BS.componentes || {};

BS.componentes.carregamento = (function () {
  var TEMPO_MINIMO_MS = 700;      // evita um "piscar" da tela quando tudo responde na hora
  var AJUDA_APOS_MS = 10000;      // tempo sem API até mostrar a orientação
  var inicio = Date.now();
  var apiOk = false, telemetriaOk = false, concluido = false;
  var tela = document.getElementById('carregamento');

  var ICONES = { aguardando: '⏳', ok: '✅', aviso: '⚠️', erro: '⛔' };

  function marcar(passo, estado, texto) {
    var li = tela.querySelector('[data-passo="' + passo + '"]');
    if (!li) return;
    li.setAttribute('data-estado', estado);
    li.querySelector('.ic').textContent = ICONES[estado];
    if (texto) li.querySelector('[data-texto]').textContent = texto;
  }

  function mostrarAjuda() {
    if (apiOk || concluido) return;
    var url = BS.config.apiBase || location.origin;
    BS.dom.texto('carregamento-ajuda-texto', 'A API não respondeu em ' + url + '. Verifique se o servidor está rodando '
      + '(no VS Code: F5 em "BioSolar Citrus", ou scripts\\iniciar.ps1). Continuamos tentando automaticamente.');
    document.getElementById('carregamento-ajuda').hidden = false;
  }

  async function verificarSaude() {
    if (concluido) return;
    try {
      var r = await BS.telemetriaService.saude();
      if (!r.ok || !r.dados) throw new Error('HTTP ' + r.status);
      apiOk = true;
      marcar('api', 'ok', 'API conectada · versão ' + r.dados.versao);
      var banco = r.dados.banco || {};
      if (banco.status === 'UP') {
        marcar('banco', 'ok', 'Banco conectado · ' + String(banco.detalhe || 'PostgreSQL').split(' ').slice(0, 2).join(' '));
      } else {
        marcar('banco', 'aviso', 'Banco indisponível: a automação segue em memória e grava quando ele voltar');
      }
      document.getElementById('carregamento-ajuda').hidden = true;
      tentarConcluir();
    } catch (e) {
      marcar('api', 'erro', 'Aguardando a API do servidor…');
      setTimeout(verificarSaude, 1500);
    }
  }

  function tentarConcluir() {
    if (!apiOk || !telemetriaOk || concluido) return;
    setTimeout(fechar, Math.max(0, TEMPO_MINIMO_MS - (Date.now() - inicio)));
  }

  function fechar() {
    if (concluido) return;
    concluido = true;
    BS.dom.texto('carregamento-titulo', 'Tudo pronto.');
    tela.classList.add('saindo');
    document.body.classList.remove('carregando');
    document.getElementById('conteudo').removeAttribute('aria-busy');
    setTimeout(function () { tela.hidden = true; }, 450);
  }

  function iniciar() {
    document.getElementById('conteudo').setAttribute('aria-busy', 'true');
    document.getElementById('carregamento-continuar').addEventListener('click', fechar);
    setTimeout(mostrarAjuda, AJUDA_APOS_MS);
    verificarSaude();
  }

  return {
    iniciar: iniciar,
    /** Chamado pelo app a cada telemetria recebida. */
    telemetriaRecebida: function (tel) {
      if (telemetriaOk) return;
      telemetriaOk = true;
      marcar('dados', 'ok', 'Telemetria recebida · ' + tel.talhoes.length + ' talhão(ões) monitorado(s)');
      tentarConcluir();
    }
  };
})();
