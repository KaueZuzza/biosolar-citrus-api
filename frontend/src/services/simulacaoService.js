/* Painel de simulação: altera condições físicas no servidor (não executa regras no navegador). */
BS.simulacaoService = {
  velocidade: function (fator) { return BS.api.post('/simulacao/velocidade', { fator: fator }); },
  pausa: function (pausada) { return BS.api.post('/simulacao/pausa', { pausada: pausada }); },
  umidade: function (talhaoId, delta) { return BS.api.post('/simulacao/umidade', { talhaoId: talhaoId, delta: delta }); },
  reservatorio: function (delta) { return BS.api.post('/simulacao/reservatorio', { delta: delta }); },
  emergencia: function () { return BS.api.post('/simulacao/emergencia'); },
  restaurar: function () { return BS.api.post('/simulacao/restaurar'); }
};