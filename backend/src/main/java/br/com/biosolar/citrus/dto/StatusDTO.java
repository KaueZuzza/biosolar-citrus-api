package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.time.LocalDateTime;

/** Resposta de GET /status: resumo operacional (inclui texto pronto para leitura em voz). */
public record StatusDTO(
        Instant instante,
        StatusSistemaDTO status,
        String resumo,
        int talhoesMonitorados,
        int reservatorios,
        int talhoesCriticos,
        int talhoesAtencao,
        int aspersoresLigados,
        double nivelReservatorio,
        boolean bloqueioEmergencia,
        int indiceHidroEnergetico,
        LocalDateTime horaSimulada,
        int fatorVelocidade) {
}