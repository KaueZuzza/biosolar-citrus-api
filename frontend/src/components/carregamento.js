/* Tela de carregamento: verifica a API, o banco (GET /saude) e a primeira telemetria.
 * As etapas são mostradas UMA DE CADA VEZ, com um tempo mínimo em cada uma, para que dê para
 * acompanhar o que está sendo carregado (mesmo quando o servidor responde na hora).
 * Com o banco fora do ar o painel abre mesmo assim, pois a automação continua em memória. */
BS.componentes = BS.componentes || {};

BS.componentes.carregamento = (function () {
  var TEMPO_POR_ETAPA_MS = 1100;  // tempo mínimo que cada etapa fica "em andamento" na tela
  var TEMPO_PRONTO_MS = 900;      // "Tudo pronto" visível antes de a tela sumir
  var AJUDA_APOS_MS = 10000;      // tempo sem API até mostrar a orientação
  var ORDEM = ['api', 'banco', 'dados'];
  var EM_ANDAMENTO = {
    api: 'Conectando à API do servidor…',
    banco: 'Verificando o banco de dados PostgreSQL…',
    dados: 'Carregando a telemetria da fazenda…'
  };

  var resultados = {};            // passo -> { estado, texto } (chega na hora; é exibido no ritmo da fila)
  var etapa = 0, inicioEtapa = 0, concluido = false, apiOk = false, timer = null;
  var tela = document.getElementById('carregamento');

  var ICONES = { aguardando: '⏳', andamento: '⚙️', ok: '✅', aviso: '⚠️', erro: '⛔' };

  function marcar(passo, estado, texto) {
    var li = tela.querySelector('[data-passo="' + passo + '"]');
    if (!li) return;
    li.setAttribute('data-estado', estado);
    li.querySelector('.ic').textContent = ICONES[estado];
    if (texto) li.querySelector('[data-texto]').textContent = texto;
  }

  function progresso(pct) {
    var barra = document.getElementById('carregamento-barra');
    barra.classList.add('determinada');
    barra.style.setProperty('--progresso', pct + '%');
    BS.dom.texto('carregamento-pct', Math.round(pct) + '%');
  }

  function comecarEtapa() {
    inicioEtapa = Date.now();
    marcar(ORDEM[etapa], 'andamento', EM_ANDAMENTO[ORDEM[etapa]]);
    // meio caminho da etapa atual na barra, para ela nunca parecer parada
    progresso((etapa + 0.4) / ORDEM.length * 100);
    agendar();
  }

  /** Avança a fila: confirma a etapa atual quando o resultado chegou E o tempo mínimo passou. */
  function avancar() {
    timer = null;
    if (concluido || etapa >= ORDEM.length) return;
    var passo = ORDEM[etapa];
    var r = resultados[passo];
    var restante = TEMPO_POR_ETAPA_MS - (Date.now() - inicioEtapa);
    if (!r) return;                                   // ainda esperando o servidor
    if (restante > 0) { agendar(restante); return; }
    marcar(passo, r.estado, r.texto);
    etapa++;
    progresso(etapa / ORDEM.length * 100);
    if (etapa < ORDEM.length) setTimeout(comecarEtapa, 250);
    else {
      BS.dom.texto('carregamento-titulo', 'Tudo pronto! Abrindo o centro de operação…');
      setTimeout(fechar, TEMPO_PRONTO_MS);
    }
  }

  function agendar(ms) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(avancar, ms || 0);
  }

  function registrar(passo, estado, texto) {
    resultados[passo] = { estado: estado, texto: texto };
    agendar();
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
      registrar('api', 'ok', 'API conectada · versão ' + r.dados.versao);
      var banco = r.dados.banco || {};
      if (banco.status === 'UP') {
        registrar('banco', 'ok', 'Banco conectado · ' + String(banco.detalhe || 'PostgreSQL').split(' ').slice(0, 2).join(' '));
      } else {
        registrar('banco', 'aviso', 'Banco indisponível: a automação segue em memória e grava quando ele voltar');
      }
      document.getElementById('carregamento-ajuda').hidden = true;
    } catch (e) {
      // Falha é mostrada na hora (é informação ao vivo), e a verificação continua
      if (etapa === 0) marcar('api', 'erro', 'Aguardando a API do servidor…');
      setTimeout(verificarSaude, 1500);
    }
  }

  function fechar() {
    if (concluido) return;
    concluido = true;
    if (timer) clearTimeout(timer);
    tela.classList.add('saindo');
    document.body.classList.remove('carregando');
    document.getElementById('conteudo').removeAttribute('aria-busy');
    setTimeout(function () { tela.hidden = true; }, 450);
  }

  function iniciar() {
    document.getElementById('conteudo').setAttribute('aria-busy', 'true');
    document.getElementById('carregamento-continuar').addEventListener('click', fechar);
    setTimeout(mostrarAjuda, AJUDA_APOS_MS);
    progresso(0);
    comecarEtapa();
    verificarSaude();
  }

  return {
    iniciar: iniciar,
    /** Chamado pelo app a cada telemetria recebida. */
    telemetriaRecebida: function (tel) {
      if (resultados.dados) return;
      registrar('dados', 'ok', 'Telemetria recebida · ' + tel.talhoes.length + ' talhão(ões) monitorado(s)');
    }
  };
})();
