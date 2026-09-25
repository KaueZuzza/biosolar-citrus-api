/* Assistente "Citrus": o navegador só transcreve a fala; interpretação e dados vêm do servidor. */
BS.assistenteService = {
  comando: function (texto) { return BS.api.post('/assistente/comando', { texto: texto }, { timeout: 30000 }); },
  exemplos: function () { return BS.api.get('/assistente/exemplos'); }
};
