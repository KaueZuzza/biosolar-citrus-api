/* Área "Exportar e compartilhar": PDF e Excel gerados pelo servidor, mensagem curta para o WhatsApp e
 * envio por e-mail feito pelo backend (SMTP configurado no .env do servidor). Qualquer botão com
 * [data-exportar] na página aciona estas funções (card da aba Relatórios e rodapé do resumo). */
BS.componentes.exportacao = (function () {
  var f = BS.fmt;
  var mensagem = null;       // última mensagem do WhatsApp recebida do servidor
  var emailConfigurado = null;

  function ocupado(botao, sim, texto) {
    if (!botao) return;
    if (sim) {
      botao.__texto = botao.innerHTML;
      botao.setAttribute('aria-busy', 'true');
      botao.disabled = true;
      if (texto) botao.textContent = texto;
    } else {
      botao.removeAttribute('aria-busy');
      botao.disabled = false;
      if (botao.__texto !== undefined) botao.innerHTML = botao.__texto;
    }
  }

  function erro(titulo, e) {
    BS.toast({ severidade: 'CRITICO', titulo: titulo, descricao: ' ' + (e && e.message ? e.message : 'Tente novamente.') });
  }

  function salvar(arquivo) {
    var url = URL.createObjectURL(arquivo.blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = arquivo.nome;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 30000);
  }

  // ---- PDF e Excel ---------------------------------------------------------------------------------

  async function baixar(tipo, botao) {
    ocupado(botao, true, 'Gerando…');
    try {
      var arquivo = await (tipo === 'excel' ? BS.exportacaoService.excel() : BS.exportacaoService.pdf());
      salvar(arquivo);
      BS.toast({ severidade: 'SUCESSO', icone: tipo === 'excel' ? '📊' : '📄', titulo: (tipo === 'excel' ? 'Planilha' : 'PDF') + ' pronto',
        descricao: ' ' + arquivo.nome + ' foi salvo na pasta de downloads.' });
    } catch (e) {
      erro('Não foi possível gerar o ' + (tipo === 'excel' ? 'Excel' : 'PDF'), e);
    } finally {
      ocupado(botao, false);
    }
  }

  async function abrirPdf(botao) {
    // A aba é aberta já no clique (senão o navegador bloqueia como pop-up) e recebe o PDF quando ficar pronto
    var janela = window.open('', '_blank');
    if (janela) {
      janela.document.title = 'Gerando relatório…';
      janela.document.body.style.cssText = 'font-family:system-ui,sans-serif;display:grid;place-items:center;height:90vh;color:#444';
      janela.document.body.textContent = 'Gerando o relatório em PDF no servidor…';
    }
    ocupado(botao, true, 'Gerando…');
    try {
      var arquivo = await BS.exportacaoService.pdf();
      var url = URL.createObjectURL(arquivo.blob);
      if (janela && !janela.closed) janela.location.href = url;
      else salvar(arquivo);
      setTimeout(function () { URL.revokeObjectURL(url); }, 120000);
    } catch (e) {
      if (janela && !janela.closed) janela.close();
      erro('Não foi possível gerar o PDF', e);
    } finally {
      ocupado(botao, false);
    }
  }

  // ---- WhatsApp -------------------------------------------------------------------------------------

  /** Mostra o texto com a mesma formatação que o WhatsApp aplica (*negrito*, _itálico_). */
  function previa(texto) {
    return f.esc(texto).replace(/\*([^*\n]+)\*/g, '<b>$1</b>').replace(/_([^_\n]+)_/g, '<i>$1</i>');
  }

  function numeroWhatsapp() {
    var digitos = (document.getElementById('wpp-numero').value || '').replace(/\D/g, '');
    if (!digitos) return '';
    if (digitos.length === 10 || digitos.length === 11) digitos = '55' + digitos;  // DDD + número, Brasil
    return digitos;
  }

  async function abrirWhatsapp() {
    var dlg = document.getElementById('dlg-whatsapp');
    mensagem = null;
    BS.dom.html('wpp-previa', '<p class="muted">Consultando o servidor…</p>');
    document.getElementById('wpp-sistema').hidden = !navigator.share;
    document.getElementById('wpp-pdf').hidden = true;
    BS.dom.abrirDialogo(dlg);
    try {
      var r = await BS.exportacaoService.whatsapp();
      if (!r.ok) throw new Error((r.dados && r.dados.mensagem) || 'HTTP ' + r.status);
      mensagem = r.dados.texto;
      BS.dom.html('wpp-previa', '<div class="wpp-balao">' + previa(mensagem) + '</div>');
      // Celulares que compartilham arquivos podem mandar o PDF junto com a mensagem
      try {
        var teste = new File([new Blob(['%PDF'])], 'teste.pdf', { type: 'application/pdf' });
        document.getElementById('wpp-pdf').hidden = !(navigator.canShare && navigator.canShare({ files: [teste] }));
      } catch (e) { /* navegador sem suporte a File */ }
    } catch (e) {
      BS.dom.html('wpp-previa', '<p class="email-aviso erro">Não foi possível montar a mensagem: ' + f.esc(e.message) + '</p>');
    }
  }

  function enviarWhatsapp() {
    if (!mensagem) return;
    var numero = numeroWhatsapp();
    window.open('https://wa.me/' + numero + '?text=' + encodeURIComponent(mensagem), '_blank', 'noopener');
  }

  async function compartilharSistema() {
    if (!mensagem || !navigator.share) return;
    try { await navigator.share({ title: 'BioSolar Citrus — Status', text: mensagem }); }
    catch (e) { /* o usuário cancelou */ }
  }

  async function compartilharComPdf(botao) {
    if (!mensagem) return;
    ocupado(botao, true, 'Gerando PDF…');
    try {
      var arquivo = await BS.exportacaoService.pdf();
      var pdf = new File([arquivo.blob], arquivo.nome, { type: 'application/pdf' });
      await navigator.share({ title: 'BioSolar Citrus — Relatório', text: mensagem, files: [pdf] });
    } catch (e) {
      if (e && e.name !== 'AbortError') erro('Não foi possível compartilhar o PDF', e);
    } finally {
      ocupado(botao, false);
    }
  }

  async function copiar(texto) {
    try {
      await navigator.clipboard.writeText(texto);
      BS.toast({ severidade: 'SUCESSO', titulo: 'Mensagem copiada', descricao: ' Cole no WhatsApp ou em outro aplicativo.' });
    } catch (e) {
      BS.toast({ severidade: 'ATENCAO', titulo: 'Não foi possível copiar', descricao: ' Selecione o texto da prévia e copie manualmente.' });
    }
  }

  // ---- E-mail ---------------------------------------------------------------------------------------

  async function verificarEmail() {
    try {
      var r = await BS.exportacaoService.statusEmail();
      if (!r.ok) return null;
      emailConfigurado = r.dados.configurado;
      BS.dom.texto('exp-email-sub', r.dados.configurado ? 'PDF ou Excel enviado pelo servidor.' : 'Requer configurar o SMTP no servidor (.env).');
      return r.dados;
    } catch (e) {
      return null;
    }
  }

  function mostrarErrosEmail(mensagem, detalhes) {
    var caixa = document.getElementById('email-erros');
    caixa.hidden = false;
    caixa.innerHTML = '<b>' + f.esc(mensagem) + '</b>' + (detalhes && detalhes.length
      ? '<ul>' + detalhes.map(function (d) { return '<li>' + f.esc(d.replace(/^[a-z]+: /, '')) + '</li>'; }).join('') + '</ul>' : '');
  }

  async function abrirEmail() {
    var dlg = document.getElementById('dlg-email');
    var form = document.getElementById('form-email');
    document.getElementById('email-erros').hidden = true;
    var hoje = new Date();
    document.getElementById('email-assunto').value = 'BioSolar Citrus — Relatório operacional ' +
      hoje.toLocaleDateString('pt-BR') + ' ' + hoje.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    var aviso = document.getElementById('email-aviso');
    aviso.hidden = true;
    BS.dom.abrirDialogo(dlg);
    var status = await verificarEmail();
    var enviar = document.getElementById('email-enviar');
    if (!status) {
      aviso.hidden = false;
      aviso.className = 'email-aviso erro';
      aviso.textContent = 'Sem conexão com o servidor. Verifique se a API está rodando.';
      enviar.disabled = true;
    } else if (!status.configurado) {
      aviso.hidden = false;
      aviso.className = 'email-aviso atencao';
      aviso.innerHTML = '<b>O envio de e-mail ainda não está configurado no servidor.</b> ' + f.esc(status.orientacao) +
        ' Veja o passo a passo no README (seção "Envio por e-mail").';
      enviar.disabled = true;
    } else {
      aviso.hidden = false;
      aviso.className = 'email-aviso ok';
      aviso.textContent = 'Será enviado pelo servidor a partir de ' + status.remetente + '.';
      enviar.disabled = false;
    }
    setTimeout(function () { document.getElementById('email-para').focus(); }, 50);
    form.__pronto = true;
  }

  async function enviarEmail(evento) {
    evento.preventDefault();
    var form = evento.target;
    var para = document.getElementById('email-para');
    document.getElementById('email-erros').hidden = true;
    if (!para.value.trim() || !para.checkValidity()) {
      mostrarErrosEmail('Informe um e-mail válido no campo "Para".', ['Para vários destinatários, separe por vírgula (máximo 5).']);
      para.focus();
      return;
    }
    var botao = document.getElementById('email-enviar');
    ocupado(botao, true, 'Enviando…');
    try {
      var r = await BS.exportacaoService.enviarEmail({
        destinatarios: para.value.trim(),
        assunto: form.assunto.value.trim(),
        mensagem: form.mensagem.value.trim(),
        anexo: form.anexo.value
      });
      if (!r.ok) {
        mostrarErrosEmail((r.dados && r.dados.mensagem) || 'Falha no envio (HTTP ' + r.status + ').', r.dados && r.dados.detalhes);
        return;
      }
      document.getElementById('dlg-email').close();
      form.mensagem.value = '';
      BS.toast({ severidade: 'SUCESSO', icone: '✉️', titulo: 'E-mail enviado', descricao: ' ' + r.dados.mensagem });
      BS.app.atualizarAgora();
    } catch (e) {
      mostrarErrosEmail(e.name === 'AbortError' ? 'O servidor de e-mail demorou demais para responder.' : 'Sem conexão com o servidor.');
    } finally {
      ocupado(botao, false);
    }
  }

  // ---- Ligações --------------------------------------------------------------------------------------

  function acionar(acao, botao) {
    if (acao === 'pdf') return baixar('pdf', botao);
    if (acao === 'pdf-abrir') return abrirPdf(botao);
    if (acao === 'excel') return baixar('excel', botao);
    if (acao === 'whatsapp') return abrirWhatsapp();
    if (acao === 'email') return abrirEmail();
  }

  function iniciar() {
    document.addEventListener('click', function (e) {
      var botao = e.target.closest('[data-exportar]');
      if (!botao || botao.getAttribute('aria-busy') === 'true') return;
      acionar(botao.getAttribute('data-exportar'), botao);
    });
    document.getElementById('wpp-abrir').addEventListener('click', enviarWhatsapp);
    document.getElementById('wpp-sistema').addEventListener('click', compartilharSistema);
    document.getElementById('wpp-pdf').addEventListener('click', function (e) { compartilharComPdf(e.currentTarget); });
    document.getElementById('wpp-copiar').addEventListener('click', function () { if (mensagem) copiar(mensagem); });
    document.getElementById('form-email').addEventListener('submit', enviarEmail);
    verificarEmail();
  }

  /** Leva o operador até a área de exportação (usado pelo Citrus: "gere um relatório"). */
  function destacar() {
    BS.componentes.abas.ir('relatorios');
    setTimeout(function () {
      var card = document.getElementById('exportacao');
      card.scrollIntoView({ block: 'center', behavior: 'smooth' });
      card.classList.remove('destaque');
      void card.offsetWidth;
      card.classList.add('destaque');
    }, 120);
  }

  return { iniciar: iniciar, destacar: destacar, abrirWhatsapp: abrirWhatsapp, abrirEmail: abrirEmail };
})();
