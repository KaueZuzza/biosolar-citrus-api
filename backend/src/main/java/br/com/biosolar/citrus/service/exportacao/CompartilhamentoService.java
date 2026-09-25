package br.com.biosolar.citrus.service.exportacao;

import static br.com.biosolar.citrus.util.Formatador.pct;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.AlertaDTO;
import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.ReservatorioDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.TelemetriaService;
import br.com.biosolar.citrus.util.Rotulos;

/**
 * Mensagem curta de status para WhatsApp (e corpo do e-mail): uma linha por assunto, com os dados reais
 * do momento. Funciona mesmo com o banco fora do ar (so omite a contagem do periodo, que vem do banco).
 */
@Service
public class CompartilhamentoService {

    private static final Logger log = LoggerFactory.getLogger(CompartilhamentoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final int MAX_ALERTAS_LISTADOS = 3;

    public record Mensagem(String texto, String link) {
    }

    private final TelemetriaService telemetriaService;
    private final EstadoFazendaService estado;
    private final EventoRepository eventoRepository;
    private final Clock clock;

    public CompartilhamentoService(TelemetriaService telemetriaService, EstadoFazendaService estado,
                                   EventoRepository eventoRepository, Clock clock) {
        this.telemetriaService = telemetriaService;
        this.estado = estado;
        this.eventoRepository = eventoRepository;
        this.clock = clock;
    }

    public Mensagem whatsapp() {
        String texto = mensagemStatus();
        return new Mensagem(texto, "https://wa.me/?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                .replace("+", "%20"));
    }

    public String mensagemStatus() {
        TelemetriaDTO t = telemetriaService.obterTelemetria();
        ReservatorioDTO r = t.reservatorio();
        List<TalhaoDTO> talhoes = t.talhoes();
        long criticos = talhoes.stream().filter(x -> x.status() == StatusTalhao.CRITICO).count();
        long atencao = talhoes.stream().filter(x -> x.status() == StatusTalhao.ATENCAO).count();
        double umidadeMedia = talhoes.stream().mapToDouble(TalhaoDTO::umidade).average().orElse(0);
        List<String> ligados = talhoes.stream().filter(TalhaoDTO::aspersorLigado).map(TalhaoDTO::id).toList();
        List<AlertaDTO> alertas = t.alertas().stream().filter(a -> a.nivel() != Severidade.INFO).toList();
        EnergiaDTO e = t.energia();

        StringBuilder sb = new StringBuilder();
        sb.append("*BIOSOLAR CITRUS — STATUS*\n");
        sb.append("📅 *Data:* ").append(DATA.format(t.horarioLeitura().atZone(zona()))).append('\n');

        sb.append("💧 *Reservatório:* ").append(pct(r.nivel())).append(" (").append(Rotulos.statusReservatorio(r.status()))
                .append(") · ").append(Math.round(r.volumeM3())).append(" de ").append(Math.round(r.capacidadeM3()))
                .append(" m³");
        if (r.bloqueioEmergencia()) {
            sb.append(" · BLOQUEIO DE EMERGÊNCIA");
        }
        sb.append('\n');

        sb.append("🌱 *Talhões:* ").append(talhoes.size()).append(" monitorado").append(talhoes.size() == 1 ? "" : "s")
                .append(" · ").append(criticos == 0 ? "nenhum crítico" : criticos + (criticos == 1 ? " crítico" : " críticos"))
                .append(" · ").append(atencao).append(" em atenção · umidade média ").append(pct(umidadeMedia)).append('\n');

        sb.append("🚿 *Irrigação:* ");
        if (r.bloqueioEmergencia()) {
            sb.append("bombas bloqueadas pela proteção do reservatório");
        } else if (ligados.isEmpty()) {
            sb.append("nenhum aspersor ligado");
        } else {
            sb.append(ligados.size()).append(ligados.size() == 1 ? " aspersor ligado (" : " aspersores ligados (")
                    .append(String.join(", ", ligados)).append(')');
        }
        Long irrigacoes = irrigacoesNoPeriodo();
        if (irrigacoes != null) {
            sb.append(" · ").append(irrigacoes).append(irrigacoes == 1 ? " irrigação automática" : " irrigações automáticas")
                    .append(" no período");
        }
        sb.append('\n');

        sb.append("⚠️ *Alertas:* ");
        if (alertas.isEmpty()) {
            sb.append("nenhum alerta ativo");
        } else {
            sb.append(alertas.size()).append(alertas.size() == 1 ? " ativo — " : " ativos — ");
            sb.append(String.join("; ", alertas.stream().limit(MAX_ALERTAS_LISTADOS).map(AlertaDTO::titulo).toList()));
            if (alertas.size() > MAX_ALERTAS_LISTADOS) {
                sb.append(" (+").append(alertas.size() - MAX_ALERTAS_LISTADOS).append(')');
            }
        }
        sb.append('\n');

        sb.append("⚙️ *Sistema:* ").append(t.statusSistema().rotulo()).append(" · Índice Hidro-Energético ")
                .append(t.indice().valor()).append("/100");
        if (e.consumoKw() > 0) {
            sb.append(" · bombas com ").append(Math.round(e.coberturaSolar())).append("% de energia solar");
        }
        sb.append("\n\n_Enviado pelo BioSolar Citrus_");
        return sb.toString();
    }

    private Long irrigacoesNoPeriodo() {
        if (estado.isBancoEmEspera()) {
            return null;
        }
        try {
            return eventoRepository.countByTipo(TipoEvento.IRRIGACAO_CRITICA);
        } catch (RuntimeException ex) {
            log.warn("Contagem de irrigações indisponível para o resumo: {}", ex.getMessage());
            return null;
        }
    }

    private ZoneId zona() {
        return clock.getZone();
    }
}
