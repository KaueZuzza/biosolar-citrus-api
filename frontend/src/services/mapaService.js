/* Mapa da Fazenda: talhões em GeoJSON (API + PostgreSQL), dados públicos (IBGE, Open-Meteo) e agente agrícola. */
BS.mapaService = {
  talhoes: function () { return BS.api.get('/mapa/talhoes'); },
  /** coordenadas: [[longitude, latitude], ...] na ordem em que os cantos foram marcados. */
  salvarArea: function (id, coordenadas) {
    return BS.api.put('/mapa/talhoes/' + encodeURIComponent(id) + '/area', { coordenadas: coordenadas });
  },
  removerArea: function (id) { return BS.api.del('/mapa/talhoes/' + encodeURIComponent(id) + '/area'); },
  // IBGE e Open-Meteo são consultados pelo servidor (com cache): a primeira consulta pode levar alguns segundos
  municipio: function () { return BS.api.get('/mapa/municipio', { timeout: 30000 }); },
  clima: function () { return BS.api.get('/mapa/clima', { timeout: 30000 }); },
  /** { pergunta?, talhaoId?, tema? }: fontes públicas podem levar alguns segundos na primeira consulta. */
  agente: function (dados) { return BS.api.post('/mapa/agente', dados, { timeout: 30000 }); }
};
