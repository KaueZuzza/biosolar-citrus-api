/* Cadastro gravado no PostgreSQL: talhões (com bomba, aspersor e sensor), reservatório e usina solar. */
BS.cadastroService = {
  listarTalhoes: function () { return BS.api.get('/talhoes'); },
  criarTalhao: function (dados) { return BS.api.post('/talhoes', dados); },
  atualizarTalhao: function (id, dados) { return BS.api.put('/talhoes/' + encodeURIComponent(id), dados); },
  excluirTalhao: function (id) { return BS.api.del('/talhoes/' + encodeURIComponent(id)); },
  reativarTalhao: function (id) { return BS.api.post('/talhoes/' + encodeURIComponent(id) + '/reativar'); },
  excluirDefinitivamente: function (id) { return BS.api.del('/talhoes/' + encodeURIComponent(id) + '/definitivo'); },
  configuracao: function () { return BS.api.get('/configuracao'); },
  salvarReservatorio: function (dados) { return BS.api.put('/configuracao/reservatorio', dados); },
  salvarUsina: function (dados) { return BS.api.put('/configuracao/usina', dados); }
};
