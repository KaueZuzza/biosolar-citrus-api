/* Comandos manuais das bombas/aspersores. O servidor valida e pode recusar (HTTP 409). */
BS.bombaService = {
  acionar: function (talhaoId, ligado) {
    return BS.api.post('/bombas/acionar', { talhaoId: talhaoId, ligado: ligado });
  },
  listar: function () { return BS.api.get('/bombas'); }
};