/* Relatório operacional (JSON) e exportação CSV gerada pelo servidor. */
BS.relatorioService = {
  obter: function () { return BS.api.get('/relatorio'); },
  urlCsv: function () { return BS.api.url('/relatorio/csv'); }
};