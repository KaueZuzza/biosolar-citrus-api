/* Controle manual dos aspersores (P4). Os botões nunca são desabilitados pela interface:
 * quem decide é o servidor, e a recusa (HTTP 409) é exibida com o motivo retornado pela API. */
BS.componentes.controleAspersores = (function () {
  var f = BS.fmt;
  var criado = false;

  var TITULOS_RECUSA = {
    BLOQUEIO_DE_EMERGENCIA: ['🚨 AÇÃO BLOQUEADA', 'O reservatório está abaixo do limite de segurança. O sistema de proteção hídrica impede o acionamento das bombas.'],
    IRRIGACAO_CRITICA_EM_ANDAMENTO: ['🔴 DESLIGAMENTO NÃO PERMITIDO', 'A irrigação crítica (P2) tem prioridade sobre o controle manual (P4).'],
    SOLO_SATURADO: ['💧 ACIONAMENTO RECUSADO', 'O solo já está saturado: irrigar agora desperdiçaria água e energia.']
  };

  /** Envia o comando ao servidor e mostra o resultado. Compartilhado com o mapa (detalhes do talhão). */
  async function acionar(talhaoId, ligar, botao) {
    if (botao && botao.getAttribute('aria-busy') === 'true') return;  // ignora duplo clique
    if (botao) { botao.setAttribute('aria-busy', 'true'); }
    try {
      var r = await BS.bombaService.acionar(talhaoId, ligar);
      var d = r.dados || {};
      if (r.ok && d.sucesso) {
        BS.toast({ severidade: 'SUCESSO', titulo: d.motivo === 'SEM_ALTERACAO' ? 'Sem alteração' : 'Comando executado', descricao: d.mensagem });
        BS.dom.anunciar(d.mensagem);
      } else if (r.status === 409) {
        mostrarRecusa(d);
      } else {
        BS.toast({ severidade: 'ATENCAO', titulo: 'Comando não aceito', descricao: d.mensagem || ('Erro HTTP ' + r.status) });
      }
    } catch (e) {
      BS.toast({ severidade: 'CRITICO', titulo: 'Servidor indisponível', descricao: 'O comando não foi enviado. Verifique a conexão com a API.' });
    } finally {
      if (botao) botao.removeAttribute('aria-busy');
      BS.app.atualizarAgora();
    }
  }

  function mostrarRecusa(d) {
    var info = TITULOS_RECUSA[d.motivo] || ['🚫 COMANDO RECUSADO', ''];
    var dlg = document.getElementById('dlg-bloqueio');
    BS.dom.texto('dlg-bloqueio-titulo', info[0]);
    BS.dom.texto('dlg-bloqueio-msg', d.mensagem);
    BS.dom.texto('dlg-bloqueio-detalhe', info[1]);
    BS.dom.texto('dlg-bloqueio-codigo', 'HTTP 409 · motivo: ' + d.motivo);
    BS.dom.abrirDialogo(dlg);
    BS.dom.anunciar(info[0] + '. ' + d.mensagem, true);
  }

  function criar(talhoes) {
    var lista = document.getElementById('controle-lista');
    lista.innerHTML = talhoes.map(function (x) {
      return '<li class="controle-item" data-id="' + x.id + '">' +
        '<div><div class="nome">' + (x.cultura === 'LIMAO' ? '🍋 ' : '🍊 ') + f.esc(x.nome) +
        ' <span class="muted small">· ' + f.esc(x.bombaId) + '</span></div>' +
        '<div class="estado" data-estado></div></div>' +
        '<button class="btn" type="button" data-acao></button></li>';
    }).join('');
    lista.addEventListener('click', function (e) {
      var botao = e.target.closest('[data-acao]');
      if (!botao || botao.getAttribute('aria-busy') === 'true') return;
      var id = botao.closest('[data-id]').getAttribute('data-id');
      acionar(id, botao.getAttribute('data-ligar') === 'true', botao);
    });
    criado = true;
  }

  function atualizar(tel) {
    if (!criado) criar(tel.talhoes);
    var bloqueio = tel.reservatorio.bloqueioEmergencia;
    document.getElementById('controle').classList.toggle('bloqueio-ativo', bloqueio);

    tel.talhoes.forEach(function (x) {
      var item = document.querySelector('#controle-lista [data-id="' + x.id + '"]');
      if (!item) return;
      var estado;
      if (x.aspersorLigado) estado = '<span class="pill ok">🟢 LIGADO</span> <span class="muted">' + BS.status.modo[x.modoAcionamento] + '</span>';
      else if (bloqueio) estado = '<span class="pill emerg">🔒 BLOQUEADO</span>';
      else estado = '<span class="pill neutral">⚪ DESLIGADO</span>';
      estado += ' <span class="muted num">· 💧 ' + f.pct(x.umidade) + '</span>';
      BS.dom.html(item.querySelector('[data-estado]'), estado);

      var botao = item.querySelector('[data-acao]');
      var ligar = !x.aspersorLigado;
      BS.dom.attr(botao, 'data-ligar', String(ligar));
      BS.dom.texto(botao, ligar ? (bloqueio ? '🔒 Ligar' : 'Ligar') : 'Desligar');
      botao.className = 'btn ' + (ligar ? 'btn-primary' : '');
      BS.dom.attr(botao, 'aria-label', (ligar ? 'Ligar' : 'Desligar') + ' aspersor do ' + x.nome);
    });
  }

  return { atualizar: atualizar, acionar: acionar };
})();
