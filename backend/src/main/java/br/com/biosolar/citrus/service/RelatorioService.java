package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;
import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.dto.RelatorioDTO;
import br.com.biosolar.citrus.dto.StatusSistemaDTO;
import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTalhaoRepository;

/** Relatorio operacional do periodo desde o inicio (ou ultima restauracao) da simulacao. */
@Service
public class RelatorioService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final List<TipoEvento> TIPOS_IRRELEVANTES = List.of(TipoEvento.RESERVATORIO_ATUALIZADO);

    private final EstadoFazendaService estado;
    private final IndicadoresService indicadoresService;
    private final TelemetriaService telemetriaService;
    private final EventoRepository eventoRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final Clock clock;

    public RelatorioService(EstadoFazendaService estado, IndicadoresService indicadoresService,
                            TelemetriaService telemetriaService, EventoRepository eventoRepository,
                            LeituraTalhaoRepository leituraTalhaoRepository, Clock clock) {
        this.estado = estado;
        this.indicadoresService = indicadoresService;
        this.telemetriaService = telemetriaService;
        this.eventoRepository = eventoRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.clock = clock;
    }

    private record TalhaoAtual(String id, String cultura, double umidade, String status) {
    }

    private record Retrato(StatusSistemaDTO status, Instant inicio, LocalDateTime horaSimulada, String statusReservatorio,
                           List<TalhaoAtual> talhoes) {
    }

    @Transactional(readOnly = true)
    public RelatorioDTO gerar() {
        Instant agora = clock.instant();
        IndicadoresService.Instantaneo inst = indicadoresService.instantaneo();
        Retrato retrato = estado.ler(f -> new Retrato(telemetriaService.statusSistema(f),
                f.getSimulacao().getIniciadaEm(), f.getSimulacao().getRelogioSimulado(),
                f.getReservatorio().classificarNivel().name(),
                f.getTalhoes().stream().map(t -> new TalhaoAtual(t.getId(), t.getCultura().getRotulo(),
                        r1(t.getUmidade()), t.classificarUmidade().name())).toList()));

        IndicadoresDTO.Acionamentos acionamentos = indicadoresService.acionamentos(inst.talhaoIds());
        IndicadoresDTO.EstatisticasReservatorio est = indicadoresService.estatisticasReservatorio();
        Map<String, LeituraTalhaoRepository.ResumoUmidade> umidades = leituraTalhaoRepository.resumoPorTalhao()
                .stream().collect(Collectors.toMap(LeituraTalhaoRepository.ResumoUmidade::getTalhaoId,
                        Function.identity()));
        Map<String, IndicadoresDTO.PorTalhao> porTalhao = acionamentos.porTalhao().stream()
                .collect(Collectors.toMap(IndicadoresDTO.PorTalhao::talhaoId, Function.identity()));

        List<RelatorioDTO.Talhao> talhoes = retrato.talhoes().stream().map(t -> {
            LeituraTalhaoRepository.ResumoUmidade u = umidades.get(t.id());
            IndicadoresDTO.PorTalhao c = porTalhao.get(t.id());
            return new RelatorioDTO.Talhao(t.id(), t.cultura(), t.umidade(),
                    u == null ? null : r1(u.getMinima()), u == null ? null : r1(u.getMedia()), t.status(),
                    c == null ? 0 : c.automaticos(), c == null ? 0 : c.manuais());
        }).toList();

        RelatorioDTO.Contagens contagens = new RelatorioDTO.Contagens(acionamentos.irrigacoesAutomaticas(),
                acionamentos.comandosManuais(), acionamentos.comandosRecusados(), acionamentos.bloqueiosEmergencia(),
                eventoRepository.count());

        List<String> criticos = eventoRepository.talhoesComEventos(
                List.of(TipoEvento.IRRIGACAO_CRITICA, TipoEvento.IRRIGACAO_BLOQUEADA));
        List<EventoDTO> relevantes = eventoRepository
                .findByTipoNotInOrderByIdDesc(TIPOS_IRRELEVANTES, PageRequest.of(0, 30)).stream()
                .map(EventoDTO::de).toList();

        RelatorioDTO.Periodo periodo = new RelatorioDTO.Periodo(retrato.inicio(), agora,
                Duration.between(retrato.inicio(), agora).toMinutes(), retrato.horaSimulada());
        RelatorioDTO.Reservatorio reservatorio = new RelatorioDTO.Reservatorio(inst.nivel(), est.medio(), est.minimo(),
                est.maximo(), retrato.statusReservatorio());

        String resumo = resumoTexto(agora, periodo, retrato.status(), reservatorio, inst.umidadeMedia(), contagens,
                inst.indice(), inst.energia(), criticos);

        return new RelatorioDTO(agora, periodo, retrato.status(), inst.indice(), est.indiceMedio(), reservatorio, talhoes,
                contagens, inst.energia(), inst.agua(), criticos, relevantes, resumo);
    }

    /** Resumo em texto (compartilhamento via WhatsApp e cabecalho do relatorio impresso). */
    private String resumoTexto(Instant agora, RelatorioDTO.Periodo periodo, StatusSistemaDTO status,
                               RelatorioDTO.Reservatorio reservatorio, double umidadeMedia,
                               RelatorioDTO.Contagens contagens, IndiceDTO indice, EnergiaDTO energia,
                               List<String> criticos) {
        ZoneId zona = clock.getZone();
        double solarPct = energia.energiaConsumidaKwh() > 0
                ? energia.energiaSolarKwh() / energia.energiaConsumidaKwh() * 100 : 100;
        StringBuilder sb = new StringBuilder();
        sb.append("🍊☀️ *BioSolar Citrus: Relatório Operacional*\n");
        sb.append("📅 ").append(DATA_HORA.format(agora.atZone(zona))).append(" (período de ")
                .append(periodo.duracaoMinutos()).append(" min)\n");
        sb.append(status.emoji()).append(" Status: ").append(status.rotulo()).append("\n");
        sb.append("💧 Reservatório: ").append(pct(reservatorio.atual()));
        if (reservatorio.minimo() != null) {
            sb.append(" (médio ").append(pct(reservatorio.medio())).append(", mínimo ")
                    .append(pct(reservatorio.minimo())).append(")");
        }
        sb.append("\n🌱 Umidade média: ").append(pct(umidadeMedia)).append("\n");
        sb.append("🚿 Irrigações automáticas: ").append(contagens.irrigacoesAutomaticas())
                .append(" | Comandos manuais: ").append(contagens.comandosManuais())
                .append(" | Recusados: ").append(contagens.comandosRecusados()).append("\n");
        sb.append("🚨 Bloqueios de emergência: ").append(contagens.bloqueiosEmergencia()).append("\n");
        sb.append("⚡ Índice Hidro-Energético: ").append(indice.valor()).append("/100 (").append(indice.rotulo())
                .append(")\n");
        sb.append("☀️ Energia: ").append(num(energia.energiaConsumidaKwh())).append(" kWh, ")
                .append(Math.round(solarPct)).append("% solar\n");
        sb.append("⚠️ Talhões que atingiram nível crítico: ")
                .append(criticos.isEmpty() ? "nenhum" : String.join(", ", criticos));
        return sb.toString();
    }

    /** Relatorio em CSV (separador ";" e BOM UTF-8 para abrir corretamente no Excel pt-BR). */
    @Transactional(readOnly = true)
    public String gerarCsv() {
        RelatorioDTO r = gerar();
        ZoneId zona = clock.getZone();
        StringBuilder sb = new StringBuilder("﻿");
        linha(sb, "BioSolar Citrus - Relatório Operacional");
        linha(sb, "Gerado em", DATA_HORA.format(r.geradoEm().atZone(zona)));
        linha(sb, "Início do período", DATA_HORA.format(r.periodo().inicio().atZone(zona)));
        linha(sb, "Duração (min)", String.valueOf(r.periodo().duracaoMinutos()));
        linha(sb, "Status atual", r.statusAtual().rotulo());
        sb.append('\n');

        linha(sb, "Indicador", "Valor");
        linha(sb, "Nível atual do reservatório (%)", dec(r.reservatorio().atual()));
        linha(sb, "Nível médio do reservatório (%)", dec(r.reservatorio().medio()));
        linha(sb, "Menor nível registrado (%)", dec(r.reservatorio().minimo()));
        linha(sb, "Maior nível registrado (%)", dec(r.reservatorio().maximo()));
        linha(sb, "Índice Hidro-Energético atual", String.valueOf(r.indice().valor()));
        linha(sb, "Índice Hidro-Energético médio", dec(r.indiceMedio()));
        linha(sb, "Irrigações automáticas", String.valueOf(r.contagens().irrigacoesAutomaticas()));
        linha(sb, "Comandos manuais", String.valueOf(r.contagens().comandosManuais()));
        linha(sb, "Comandos recusados", String.valueOf(r.contagens().comandosRecusados()));
        linha(sb, "Bloqueios de emergência", String.valueOf(r.contagens().bloqueiosEmergencia()));
        linha(sb, "Água consumida (m³)", dec(r.aguaConsumidaM3()));
        linha(sb, "Energia consumida (kWh)", dec(r.energia().energiaConsumidaKwh()));
        linha(sb, "Energia solar utilizada (kWh)", dec(r.energia().energiaSolarKwh()));
        linha(sb, "Talhões que atingiram nível crítico",
                r.talhoesCriticos().isEmpty() ? "nenhum" : String.join(", ", r.talhoesCriticos()));
        sb.append('\n');

        linha(sb, "Talhão", "Cultura", "Umidade atual (%)", "Umidade mínima (%)", "Umidade média (%)", "Status",
                "Irrigações automáticas", "Comandos manuais");
        for (RelatorioDTO.Talhao t : r.talhoes()) {
            linha(sb, t.id(), t.cultura(), dec(t.umidadeAtual()), dec(t.umidadeMinima()), dec(t.umidadeMedia()),
                    t.status(), String.valueOf(t.irrigacoesAutomaticas()), String.valueOf(t.comandosManuais()));
        }
        sb.append('\n');

        linha(sb, "Eventos (ordem cronológica)");
        linha(sb, "Data/hora", "Hora simulada", "Tipo", "Severidade", "Origem", "Regra", "Talhão", "Título",
                "Descrição", "Reservatório (%)");
        for (Evento e : eventoRepository.findAllByOrderByIdAsc()) {
            linha(sb, DATA_HORA.format(e.getInstante().atZone(zona)),
                    e.getHoraSimulada() == null ? "" : HORA.format(e.getHoraSimulada()),
                    e.getTipo().name(), e.getSeveridade().name(), e.getOrigem().name(),
                    e.getRegra() == null ? "" : e.getRegra(), e.getTalhaoId() == null ? "" : e.getTalhaoId(),
                    e.getTitulo(), e.getDescricao(), dec(e.getNivelReservatorio()));
        }
        return sb.toString();
    }

    private static String dec(Double valor) {
        return valor == null ? "" : String.format(Locale.of("pt", "BR"), "%.1f", valor);
    }

    private static void linha(StringBuilder sb, String... campos) {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            String c = campos[i] == null ? "" : campos[i];
            if (c.contains(";") || c.contains("\"") || c.contains("\n")) {
                c = "\"" + c.replace("\"", "\"\"") + "\"";
            }
            sb.append(c);
        }
        sb.append('\n');
    }
}
