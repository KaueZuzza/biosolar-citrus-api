/* Formatação pt-BR e mapeamentos de status → ícone + rótulo + classe (nunca só cor). */
(function () {
  var cache = {};
  function nf(casas) {
    if (!cache[casas]) {
      cache[casas] = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
    }
    return cache[casas];
  }

  BS.fmt = {
    num: function (v, casas) {
      if (v === null || v === undefined || isNaN(v)) return '—';
      return nf(casas === undefined ? 1 : casas).format(v);
    },
    pct: function (v, casas) {
      if (v === null || v === undefined || isNaN(v)) return '—';
      return nf(casas === undefined ? 1 : casas).format(v) + '%';
    },
    sinal: function (v, casas) {
      if (v === null || v === undefined || isNaN(v)) return '—';
      return (v > 0 ? '+' : v < 0 ? '−' : '') + nf(casas === undefined ? 1 : casas).format(Math.abs(v));
    },
    horas: function (h) {
      if (h === null || h === undefined) return '—';
      if (h < 1) return Math.round(h * 60) + ' min';
      return nf(h < 10 ? 1 : 0).format(h) + ' h';
    },
    hora: function (iso) {
      if (!iso) return '--:--:--';
      return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    },
    dataHora: function (iso) {
      if (!iso) return '--';
      return new Date(iso).toLocaleString('pt-BR');
    },
    /** LocalDateTime do servidor ("2026-09-24T10:24:00") → "10:24" */
    horaSimulada: function (local) {
      return local ? String(local).slice(11, 16) : '--:--';
    },
    esc: function (s) {
      return String(s === null || s === undefined ? '' : s).replace(/[&<>"']/g, function (c) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
      });
    }
  };

  BS.status = {
    talhao: {
      NORMAL: { icone: '🟢', rotulo: 'Normal', cls: 'ok' },
      ATENCAO: { icone: '🟡', rotulo: 'Atenção', cls: 'warn' },
      CRITICO: { icone: '🔴', rotulo: 'Crítico', cls: 'crit' }
    },
    reservatorio: {
      NORMAL: { icone: '🟢', rotulo: 'Normal', cls: 'ok' },
      ATENCAO: { icone: '🟡', rotulo: 'Atenção', cls: 'warn' },
      CRITICO: { icone: '🔴', rotulo: 'Crítico', cls: 'crit' },
      EMERGENCIA: { icone: '🚨', rotulo: 'Emergência', cls: 'emerg' }
    },
    severidade: {
      INFO: { icone: 'ℹ️', rotulo: 'Informação', cls: 'info' },
      SUCESSO: { icone: '✅', rotulo: 'Sucesso', cls: 'ok' },
      ATENCAO: { icone: '🟡', rotulo: 'Atenção', cls: 'warn' },
      CRITICO: { icone: '🔴', rotulo: 'Crítico', cls: 'crit' },
      EMERGENCIA: { icone: '🚨', rotulo: 'Emergência', cls: 'emerg' }
    },
    indice: {
      EQUILIBRADO: { icone: '🟢', cls: 'ok' },
      ATENCAO: { icone: '🟡', cls: 'warn' },
      RISCO: { icone: '🔴', cls: 'crit' }
    },
    tipoEvento: {
      IRRIGACAO_CRITICA: '🚿',
      IRRIGACAO_CONCLUIDA: '✅',
      PROTECAO_SATURACAO: '💧',
      COMANDO_MANUAL: '👤',
      COMANDO_RECUSADO: '🚫',
      BLOQUEIO_EMERGENCIA: '🚨',
      IRRIGACAO_BLOQUEADA: '⛔',
      RECUPERACAO_SISTEMA: '🔓',
      ALERTA_UMIDADE: '🌱',
      ALERTA_RESERVATORIO: '💧',
      RESERVATORIO_ATUALIZADO: '📉',
      SIMULACAO: '🎬',
      SISTEMA: '⚙️'
    },
    modo: {
      AUTOMATICO: 'automático',
      MANUAL: 'manual',
      DESLIGADO: 'desligado'
    }
  };
})();
