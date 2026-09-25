/* Cliente HTTP da API REST. Único ponto de acesso ao servidor. */
(function () {
  var ouvintes = [];

  function notificar(estado) {
    ouvintes.forEach(function (fn) { try { fn(estado); } catch (e) { console.error(e); } });
  }

  async function requisitar(metodo, caminho, corpo) {
    var inicio = performance.now();
    var controle = new AbortController();
    var timer = setTimeout(function () { controle.abort(); }, 8000);
    try {
      var resposta = await fetch(BS.config.apiBase + caminho, {
        method: metodo,
        headers: corpo ? { 'Content-Type': 'application/json', 'Accept': 'application/json' } : { 'Accept': 'application/json' },
        body: corpo ? JSON.stringify(corpo) : undefined,
        signal: controle.signal,
        cache: 'no-store'
      });
      var dados = null;
      var tipo = resposta.headers.get('content-type') || '';
      if (tipo.indexOf('json') >= 0) dados = await resposta.json();
      notificar({ online: true, latencia: Math.round(performance.now() - inicio) });
      return { ok: resposta.ok, status: resposta.status, dados: dados };
    } catch (erro) {
      notificar({ online: false, erro: erro });
      throw erro;
    } finally {
      clearTimeout(timer);
    }
  }

  BS.api = {
    get: function (caminho) { return requisitar('GET', caminho); },
    post: function (caminho, corpo) { return requisitar('POST', caminho, corpo || {}); },
    put: function (caminho, corpo) { return requisitar('PUT', caminho, corpo || {}); },
    del: function (caminho) { return requisitar('DELETE', caminho); },
    url: function (caminho) { return BS.config.apiBase + caminho; },
    aoMudarConexao: function (fn) { ouvintes.push(fn); }
  };
})();
