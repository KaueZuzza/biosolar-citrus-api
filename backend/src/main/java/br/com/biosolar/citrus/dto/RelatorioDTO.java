package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Resposta de GET /relatorio. */
public record RelatorioDTO(
        Instant geradoEm,
        Periodo periodo,
        StatusSistemaDTO statusAtual,
        IndiceDTO indice,
        Double indiceMedio,
        Reservatorio reservatorio,
        List<Talhao> talhoes,
        Contagens contagens,
        EnergiaDTO energia,
        double aguaConsumidaM3,
        List<String> talhoesCriticos,
        List<EventoDTO> eventosRelevantes,
        String resumoTexto) {

    public record Periodo(Instant inicio, Instant fim, long duracaoMinutos, LocalDateTime horaSimuladaAtual) {
    }

    public record Reservatorio(double atual, Double medio, Double minimo, Double maximo, String status) {
    }

    public record Talhao(String id, String cultura, double umidadeAtual, Double umidadeMinima,
                         Double umidadeMedia, String status, long irrigacoesAutomaticas, long comandosManuais) {
    }

    public record Contagens(long irrigacoesAutomaticas, long comandosManuais, long comandosRecusados,
                            long bloqueiosEmergencia, long totalEventos) {
    }
}