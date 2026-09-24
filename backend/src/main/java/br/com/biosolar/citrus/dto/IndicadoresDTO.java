package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

/** Resposta de GET /indicadores. */
public record IndicadoresDTO(
        Instant geradoEm,
        IndiceDTO indice,
        double nivelReservatorio,
        Double autonomiaHoras,
        double tendenciaReservatorioPorHora,
        double umidadeMedia,
        int aspersoresAtivos,
        int totalTalhoes,
        int talhoesCriticos,
        Acionamentos acionamentos,
        EnergiaDTO energia,
        double aguaConsumidaM3,
        EstatisticasReservatorio estatisticasReservatorio) {

    public record Acionamentos(
            long irrigacoesAutomaticas,
            long comandosManuais,
            long comandosRecusados,
            long bloqueiosEmergencia,
            List<PorTalhao> porTalhao) {
    }

    public record PorTalhao(String talhaoId, long automaticos, long manuais, long recusados) {
    }

    public record EstatisticasReservatorio(Double medio, Double minimo, Double maximo, Double indiceMedio,
                                           long leituras) {
    }
}