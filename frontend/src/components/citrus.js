/* Citrus: assistente operacional por voz. O navegador transcreve a fala (Web Speech API, pt-BR) e envia o
 * texto ao servidor, que interpreta o comando, consulta os dados reais (automação + PostgreSQL) e responde.
 * A resposta aparece em texto e, se a opção estiver marcada, também é lida em voz. Sem reconhecimento de voz
 * no navegador (ex.: Firefox), o mesmo painel aceita o comando digitado. */
BS.componentes.citrus = (function () {
  var f = BS.fmt;
  var Reconhecimento = window.SpeechRecognition || window.webkitSpeechRecognition;
  var reconhecedor = null;
  var ouvindo = false;
  var consultando = false;
  var ultimoParcial = '';
  var MAX_MENSAGENS = 12;

  var ESTADOS = {
    pronto: 'Toque no microfone e fale. Ex.: "Citrus, quero o status".',
    ouvindo: '🎙️ Ouvindo…',
    consultando: '🔎 Consultando os dados…',
    falando: '🔊 Respondendo…',
    semVoz: 'Seu navegador não reconhece voz (use Chrome ou Edge). Digite o comando abaixo.'
  };

  function el(id) { return document.getElementById(id); }

  function estado(nome, texto) {
    el('citrus-estado').setAttribute('data-estado', nome);
    BS.dom.texto('citrus-estado-texto', texto || ESTADOS[nome] || '');
    el('btn-citrus').classList.toggle('ativo', nome === 'ouvindo' || nome === 'falando');
    var mic = el('citrus-mic');
    mic.setAttribute('aria-pressed', String(nome === 'ouvindo'));
    BS.dom.attr(mic, 'aria-label', nome === 'ouvindo' ? 'Parar de ouvir' : 'Falar com o Citrus');
  }

  // ---- Painel -----------------------------------------------------------------------------------------

  function aberto() { return !el('painel-citrus').hidden; }

  /** Painel fixo logo abaixo do botão (no celular, o CSS o transforma em folha na parte de baixo da tela). */
  function posicionar() {
    var painel = el('painel-citrus');
    if (window.innerWidth <= 560) { painel.style.top = ''; painel.style.right = ''; return; }
    var r = el('btn-citrus').getBoundingClientRect();
    painel.style.top = Math.round(r.bottom + 8) + 'px';
    painel.style.right = Math.max(8, Math.round(window.innerWidth - r.right)) + 'px';
  }

  function abrir(escutar) {
    var painel = el('painel-citrus');
    posicionar();
    painel.hidden = false;
    el('btn-citrus').setAttribute('aria-expanded', 'true');
    if (escutar && Reconhecimento) ouvir();
    else setTimeout(function () { el('citrus-texto').focus(); }, 30);
  }

  function fechar(devolverFoco) {
    pararDeOuvir();
    if (window.speechSynthesis) window.speechSynthesis.cancel();
    el('painel-citrus').hidden = true;
    el('btn-citrus').setAttribute('aria-expanded', 'false');
    if (!consultando) estado(Reconhecimento ? 'pronto' : 'semVoz');
    if (devolverFoco) el('btn-citrus').focus();
  }

  // ---- Conversa ---------------------------------------------------------------------------------------

  function adicionar(quem, html) {
    var lista = el('citrus-conversa');
    var li = document.createElement('li');
    li.className = 'citrus-msg ' + quem;
    li.innerHTML = html;
    lista.appendChild(li);
    while (lista.children.length > MAX_MENSAGENS) lista.firstElementChild.remove();
    lista.scrollTop = lista.scrollHeight;
    return li;
  }

  function responder(r) {
    var li = adicionar('citrus' + (r.entendido ? '' : ' nao-entendi'), '<p>' + f.esc(r.resposta) + '</p>');
    if (r.acao && r.acao.rotulo) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'btn btn-sm citrus-acao';
      b.textContent = r.acao.rotulo + ' →';
      b.addEventListener('click', function () { executar(r.acao, true); });
      li.appendChild(b);
    }
    BS.dom.anunciar('Citrus: ' + r.resposta);
  }

  // ---- Envio do comando -------------------------------------------------------------------------------

  async function enviar(texto) {
    texto = (texto || '').trim();
    if (!texto || consultando) return;
    consultando = true;
    adicionar('usuario', '<p>' + f.esc(texto) + '</p>');
    estado('consultando');
    try {
      var r = await BS.assistenteService.comando(texto);
      if (!r.ok) throw new Error((r.dados && r.dados.mensagem) || 'HTTP ' + r.status);
      responder(r.dados);
      if (r.dados.acao) executar(r.dados.acao, false);
      if (el('citrus-falar').checked && r.dados.fala) {
        estado('falando');
        BS.voz.falar(r.dados.fala, function () { if (!ouvindo && !consultando) estado('pronto', 'Toque no microfone para perguntar de novo.'); });
      } else {
        estado('pronto', 'Toque no microfone para perguntar de novo.');
      }
    } catch (e) {
      var msg = e instanceof TypeError || e.name === 'AbortError'
        ? 'Não consegui falar com o servidor da fazenda. Verifique se a API está rodando.'
        : 'Não consegui consultar os dados agora (' + e.message + ').';
      adicionar('citrus nao-entendi', '<p>' + f.esc(msg) + '</p>');
      estado('pronto', 'Tente de novo em instantes.');
    } finally {
      consultando = false;
    }
  }

  /** Executa no painel a ação que o servidor indicou (navegar, abrir um talhão, exportar, atualizar). */
  function executar(acao, manual) {
    var abas = BS.componentes.abas;
    if (acao.tipo === 'ABRIR_TALHAO' && acao.talhaoId) {
      abas.ir('talhoes');
      setTimeout(function () { BS.componentes.mapaTalhoes.abrir(acao.talhaoId); }, 150);
    } else if (acao.tipo === 'ABRIR_EXPORTACAO') {
      BS.componentes.exportacao.destacar();
    } else if (acao.tipo === 'ATUALIZAR') {
      BS.app.atualizarAgora();
      if (manual && acao.alvo) abas.ir(acao.alvo);
    } else if (acao.tipo === 'NAVEGAR' && acao.alvo) {
      abas.ir(acao.alvo);
    }
  }

  // ---- Reconhecimento de voz -------------------------------------------------------------------------

  function criarReconhecedor() {
    var r = new Reconhecimento();
    r.lang = 'pt-BR';
    r.interimResults = true;
    r.continuous = false;
    r.maxAlternatives = 1;
    r.onresult = function (evento) {
      var final = '';
      var parcial = '';
      for (var i = evento.resultIndex; i < evento.results.length; i++) {
        var trecho = evento.results[i][0].transcript;
        if (evento.results[i].isFinal) final += trecho; else parcial += trecho;
      }
      if (parcial) {
        ultimoParcial = parcial;
        estado('ouvindo', '🎙️ Ouvindo… "' + parcial.trim() + '"');
      }
      if (final) {
        ultimoParcial = '';
        ouvindo = false;
        enviar(final);
      }
    };
    r.onerror = function (evento) {
      ouvindo = false;
      var mensagens = {
        'not-allowed': 'Permita o uso do microfone (ícone de cadeado na barra de endereço) e tente de novo.',
        'service-not-allowed': 'O navegador bloqueou o reconhecimento de voz. Digite o comando abaixo.',
        'no-speech': 'Não ouvi nada. Toque no microfone e fale de novo.',
        'audio-capture': 'Nenhum microfone encontrado. Conecte um microfone ou digite o comando.',
        'network': 'O reconhecimento de voz do navegador precisa de internet. Digite o comando abaixo.',
        'aborted': ESTADOS.pronto
      };
      estado('pronto', mensagens[evento.error] || 'Não consegui ouvir (' + evento.error + '). Tente de novo ou digite.');
    };
    r.onend = function () {
      // Terminou sem resultado final: usa o último trecho ouvido, se houver
      if (ouvindo) {
        ouvindo = false;
        if (ultimoParcial) { var t = ultimoParcial; ultimoParcial = ''; enviar(t); }
        else if (!consultando) estado('pronto');
      }
    };
    return r;
  }

  function ouvir() {
    if (!Reconhecimento || ouvindo || consultando) return;
    if (window.speechSynthesis) window.speechSynthesis.cancel();  // não ouvir a própria voz
    reconhecedor = reconhecedor || criarReconhecedor();
    ultimoParcial = '';
    try {
      reconhecedor.start();
      ouvindo = true;
      estado('ouvindo');
    } catch (e) {
      estado('pronto', 'O microfone está ocupado. Tente de novo em instantes.');
    }
  }

  function pararDeOuvir() {
    if (reconhecedor && ouvindo) {
      try { reconhecedor.stop(); } catch (e) { /* já parado */ }
    }
  }

  // ---- Início ---------------------------------------------------------------------------------------------

  async function carregarSugestoes() {
    try {
      var r = await BS.assistenteService.exemplos();
      if (!r.ok) return;
      var caixa = el('citrus-sugestoes');
      caixa.innerHTML = '';
      r.dados.perguntas.slice(0, 6).forEach(function (texto) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'citrus-sugestao';
        b.textContent = texto;
        b.addEventListener('click', function () { enviar(texto); });
        caixa.appendChild(b);
      });
    } catch (e) { /* sem sugestões: o painel funciona igual */ }
  }

  function iniciar() {
    // O cabeçalho usa backdrop-filter, que prende elementos 'fixed' dentro dele: o painel vai para o <body>
    document.body.appendChild(el('painel-citrus'));
    window.addEventListener('resize', function () { if (aberto()) posicionar(); });
    var prefs = BS.preferencias.obter();
    el('citrus-falar').checked = prefs.citrusVoz !== false && BS.voz.suportado();
    el('citrus-falar').disabled = !BS.voz.suportado();
    el('citrus-falar').addEventListener('change', function (e) {
      BS.preferencias.definir('citrusVoz', e.target.checked);
      if (!e.target.checked && window.speechSynthesis) window.speechSynthesis.cancel();
    });

    if (!Reconhecimento) {
      el('citrus-mic').disabled = true;
      el('citrus-mic').title = 'Reconhecimento de voz indisponível neste navegador';
      estado('semVoz');
    } else {
      estado('pronto');
    }

    el('btn-citrus').addEventListener('click', function () {
      if (!aberto()) abrir(true);
      else if (ouvindo) pararDeOuvir();
      else ouvir();
    });
    el('citrus-mic').addEventListener('click', function () { if (ouvindo) pararDeOuvir(); else ouvir(); });
    el('citrus-fechar').addEventListener('click', function () { fechar(true); });
    el('citrus-form').addEventListener('submit', function (e) {
      e.preventDefault();
      var campo = el('citrus-texto');
      var texto = campo.value;
      campo.value = '';
      pararDeOuvir();
      enviar(texto);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && aberto() && !document.querySelector('dialog[open]')) fechar(true);
    });
    document.addEventListener('click', function (e) {
      // Fecha ao clicar fora (mas não quando o clique foi em uma janela aberta pelo próprio Citrus)
      if (aberto() && !e.target.closest('.citrus-wrap') && !e.target.closest('#painel-citrus') && !e.target.closest('dialog') && !ouvindo && !consultando) fechar(false);
    });
    carregarSugestoes();
  }

  return { iniciar: iniciar, abrir: abrir, enviar: enviar };
})();
