package br.com.biosolar.citrus.dto;

import java.time.LocalDateTime;

public record SimulacaoDTO(
        int fatorVelocidade,
        boolean pausada,
        double minutosPorCiclo,
        long ciclos,
        LocalDateTime horaSimulada,
        boolean controlesHabilitados) {
}