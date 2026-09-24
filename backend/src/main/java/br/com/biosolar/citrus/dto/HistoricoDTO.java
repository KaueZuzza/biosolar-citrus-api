package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Resposta de GET /historico: serie temporal para os graficos (ordem cronologica). */
public record HistoricoDTO(int total, List<Ponto> pontos) {

    public record Ponto(
            Instant instante,
            LocalDateTime horaSimulada,
            double reservatorio,
            double umidadeMedia,
            Map<String, Double> umidades,
            Map<String, Boolean> aspersores,
            int aspersoresLigados,
            double consumoKw,
            double geracaoSolarKw,
            int indice,
            boolean bloqueioEmergencia) {
    }
}