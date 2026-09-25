/* Medidor visual do reservatório central (tanque) e nó central do mapa. */
BS.componentes.reservatorio = (function () {
  var f = BS.fmt, t = BS.dom.texto;

  function atualizar(tel) {
    var r = tel.reservatorio;
    var chave = r.bloqueioEmergencia ? 'EMERGENCIA' : r.status;
    var st = BS.status.reservatorio[chave];

    t('res-nome', r.nome + ' · capacidade ' + f.num(r.capacidadeM3, 0) + ' m³');
    var pill = document.getElementById('res-status');
    pill.className = 'pill ' + st.cls;
    t(pill, st.icone + ' ' + st.rotulo);

    var tanque = document.getElementById('tanque');
    // "consumindo": ondas mais rápidas enquanto alguma bomba puxa água do reservatório
    tanque.className = 'tanque ' + st.cls + (r.consumoM3h > 0 ? ' consumindo' : '');
    BS.dom.attr(tanque, 'aria-valuenow', r.nivel);
    BS.dom.attr(tanque, 'aria-valuetext', f.pct(r.nivel) + ', ' + st.rotulo);
    document.getElementById('tanque-agua').style.height = r.nivel + '%';
    var pct = document.getElementById('tanque-pct');
    var textoPct = f.pct(r.nivel);
    if (pct.textContent !== textoPct && pct.textContent !== '--') {
      pct.classList.remove('mudou');
      void pct.offsetWidth; // reinicia a animação de destaque do número
      pct.classList.add('mudou');
    }
    t(pct, textoPct);

    t('res-volume', f.num(r.volumeM3, 0) + ' m³');
    t('res-consumo', r.consumoM3h > 0 ? f.num(r.consumoM3h, 0) + ' m³/h' : 'zero');
    t('res-recarga', '+' + f.num(r.recargaM3h, 1) + ' m³/h');
    t('res-tendencia', f.sinal(r.tendenciaPorHora) + ' p.p./h');
    t('res-autonomia', r.autonomiaHoras === null ? 'estável' : f.horas(r.autonomiaHoras));
    t('res-bloqueio', r.bloqueioEmergencia ? '🔒 BLOQUEIO ATIVO (rearme em ' + f.pct(r.limiteRearme, 0) + ')' : '✅ Liberada');

    // Nó central do mapa operacional
    var hub = document.getElementById('hub');
    var ligadas = tel.bombas.filter(function (b) { return b.ligada; }).length;
    hub.className = 'hub-box ' + (r.bloqueioEmergencia ? 'emerg' : st.cls) + (ligadas > 0 && !r.bloqueioEmergencia ? ' fluindo' : '');
    t('hub-nivel', f.pct(r.nivel, 0));
    t('hub-bombas', r.bloqueioEmergencia ? '🔒 bloqueio' : ligadas + '/' + tel.bombas.length + ' bombas');
  }

  return { atualizar: atualizar };
})();
