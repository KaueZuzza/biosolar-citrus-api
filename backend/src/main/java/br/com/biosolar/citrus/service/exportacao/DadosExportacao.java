package br.com.biosolar.citrus.service.exportacao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.HistoricoDTO;
import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.RelatorioDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;

/**
 * Tudo o que o PDF, a planilha e o e-mail precisam, coletado de uma vez: o relatorio do periodo
 * (banco), o estado ao vivo (automacao) e o historico amostrado (banco).
 *
 * @param relatorio          resumo do periodo (mesmo conteudo de GET /relatorio)
 * @param telemetria         estado atual: reservatorio, talhoes, alertas, decisao, energia
 * @param indicadores        acionamentos por talhao (automaticos, manuais, recusados) e estatisticas
 * @param serie              leituras em ordem cronologica (amostradas quando o historico e grande)
 * @param totalLeituras      leituras gravadas no banco no periodo
 * @param passoAmostragem    1 = todas as leituras; N = uma a cada N
 * @param eventos            eventos em ordem cronologica (os mais recentes, ate o limite)
 * @param eventosOmitidos    eventos antigos que ficaram de fora por causa do limite
 * @param tempoIrrigandoPct  % das leituras com o aspersor ligado, por talhao
 * @param talhoesSerie       talhoes que aparecem no historico (inclui arquivados)
 */
public record DadosExportacao(
        RelatorioDTO relatorio,
        TelemetriaDTO telemetria,
        IndicadoresDTO indicadores,
        List<HistoricoDTO.Ponto> serie,
        long totalLeituras,
        long passoAmostragem,
        List<EventoDTO> eventos,
        long eventosOmitidos,
        Map<String, Double> tempoIrrigandoPct,
        List<String> talhoesSerie,
        ZoneId zona) {

    public LocalDateTime local(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, zona);
    }

    /** Nome cadastrado do talhao (ou "Talhão X" para talhoes que so existem no historico). */
    public String nomeTalhao(String id) {
        return telemetria.talhoes().stream().filter(t -> t.id().equals(id)).map(TalhaoDTO::nome).findFirst()
                .orElse("Talhão " + id);
    }
}
