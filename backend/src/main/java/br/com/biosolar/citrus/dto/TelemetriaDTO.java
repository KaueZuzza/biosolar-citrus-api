package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Resposta de GET /telemetria: retrato completo e consistente da fazenda em um instante. */
public record TelemetriaDTO(
        Instant horarioLeitura,
        LocalDateTime horaSimulada,
        StatusSistemaDTO statusSistema,
        SimulacaoDTO simulacao,
        ReservatorioDTO reservatorio,
        List<TalhaoDTO> talhoes,
        List<BombaDTO> bombas,
        EnergiaDTO energia,
        IndiceDTO indice,
        DecisaoDTO decisao,
        List<AlertaDTO> alertas,
        List<EventoDTO> eventosRecentes) {

    public TelemetriaDTO comEventos(List<EventoDTO> eventos) {
        return new TelemetriaDTO(horarioLeitura, horaSimulada, statusSistema, simulacao, reservatorio, talhoes,
                bombas, energia, indice, decisao, alertas, eventos);
    }
}