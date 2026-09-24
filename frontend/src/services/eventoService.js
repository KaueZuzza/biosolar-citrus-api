/* Histórico de eventos gravado no banco de dados. */
BS.eventoService = {
  listar: function (limite) { return BS.api.get('/eventos?limite=' + (limite || 50)); },
  desde: function (id, limite) { return BS.api.get('/eventos?limite=' + (limite || 50) + '&desdeId=' + id); }
};