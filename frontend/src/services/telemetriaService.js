/* Telemetria, status, histórico, indicadores e saúde (somente leitura). */
BS.telemetriaService = {
  obter: function () { return BS.api.get('/telemetria'); },
  status: function () { return BS.api.get('/status'); },
  historico: function (limite) { return BS.api.get('/historico?limite=' + (limite || BS.config.pontosHistorico)); },
  indicadores: function () { return BS.api.get('/indicadores'); },
  saude: function () { return BS.api.get('/saude'); }
};