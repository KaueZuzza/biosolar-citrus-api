/* Exportar (PDF, Excel) e compartilhar (WhatsApp, e-mail). Os arquivos são gerados pelo servidor;
 * credenciais de e-mail nunca passam pelo navegador. */
BS.exportacaoService = (function () {
  /** Baixa um arquivo gerado pelo servidor. Resolve {blob, nome}; rejeita com a mensagem de erro da API. */
  async function arquivo(caminho) {
    var controle = new AbortController();
    var timer = setTimeout(function () { controle.abort(); }, 60000);
    try {
      var r = await fetch(BS.api.url(caminho), { signal: controle.signal, cache: 'no-store' });
      if (!r.ok) {
        var msg = 'O servidor não conseguiu gerar o arquivo (HTTP ' + r.status + ').';
        try { var erro = await r.json(); if (erro && erro.mensagem) msg = erro.mensagem; } catch (e) { /* corpo não JSON */ }
        throw new Error(msg);
      }
      var disp = r.headers.get('content-disposition') || '';
      var m = /filename="?([^";]+)"?/i.exec(disp);
      return { blob: await r.blob(), nome: m ? m[1] : 'biosolar-relatorio' };
    } catch (e) {
      if (e.name === 'AbortError') throw new Error('O servidor demorou demais para gerar o arquivo. Tente de novo.');
      if (e instanceof TypeError) throw new Error('Sem conexão com o servidor. Verifique se a API está rodando.');
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    pdf: function () { return arquivo('/exportacao/pdf'); },
    excel: function () { return arquivo('/exportacao/excel'); },
    urlCsv: function () { return BS.api.url('/relatorio/csv'); },
    whatsapp: function () { return BS.api.get('/exportacao/whatsapp'); },
    statusEmail: function () { return BS.api.get('/exportacao/email'); },
    enviarEmail: function (dados) { return BS.api.post('/exportacao/email', dados, { timeout: 60000 }); }
  };
})();
