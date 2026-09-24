/* BioSolar Citrus: configuração do dashboard.
 * O frontend NÃO guarda dados operacionais: consulta, exibe e envia comandos ao servidor. */
window.BS = window.BS || {};

BS.config = {
  /**
   * Endereço da API:
   *  - servido pelo próprio Spring Boot (http://localhost:8080) usa a mesma origem;
   *  - aberto via Live Server/arquivo usa http://localhost:8080;
   *  - pode ser sobrescrito com ?api=http://servidor:porta
   */
  apiBase: (function () {
    var param = new URLSearchParams(location.search).get('api');
    if (param) return param.replace(/\/$/, '');
    var portasDev = ['5500', '5501', '5173', '3000', '8000', '8081'];
    if (location.protocol === 'file:' || portasDev.indexOf(location.port) >= 0) return 'http://localhost:8080';
    return '';
  })(),

  /** Intervalos de atualização (ms). Polling simples e robusto. */
  intervalos: {
    telemetria: 1500,
    eventos: 3000,
    historico: 5000,
    indicadores: 5000,
    saude: 10000
  },

  pontosHistorico: 240,
  eventosTimeline: 80
};
