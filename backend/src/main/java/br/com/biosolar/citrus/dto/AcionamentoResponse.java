package br.com.biosolar.citrus.dto;

import java.time.Instant;

/** Resposta de POST /bombas/acionar (sucesso=false quando uma regra de seguranca recusa o comando). */
public record AcionamentoResponse(
        boolean sucesso,
        String motivo,
        String mensagem,
        Instant instante,
        TalhaoDTO talhao,
        BombaDTO bomba,
        double nivelReservatorio,
        boolean bloqueioEmergencia) {
}