package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.horas;
import static br.com.biosolar.citrus.util.Formatador.pct;
import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.dto.AlertaDTO;
import br.com.biosolar.citrus.dto.BombaDTO;
import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.dto.ReservatorioDTO;
import br.com.biosolar.citrus.dto.SimulacaoDTO;
import br.com.biosolar.citrus.dto.StatusDTO;
import br.com.biosolar.citrus.dto.StatusSistemaDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusReservatorio;
import br.com.biosolar.citrus.model.StatusSistema;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.repository.EventoRepository;

/** Monta a telemetria, o status geral e os alertas ativos a partir do estado do servidor. */
@Service
public class TelemetriaService {

    private static final Logger log = LoggerFactory.getLogger(TelemetriaService.class);
    private static final int EVENTOS_RECENTES = 15;

    private final EstadoFazendaService estado;
    private final IndiceHidroEnergeticoCalculator indiceCalculator;
    private final MotorDecisaoService motorDecisao;
    private final EventoRepository eventoRepository;
    private final BioSolarProperties properties;
    private final Clock clock;

    public TelemetriaService(EstadoFazendaService estado, IndiceHidroEnergeticoCalculator indiceCalculator,
                             MotorDecisaoService motorDecisao, EventoRepository eventoRepository,
                             BioSolarProperties properties, Clock clock) {
        this.estado = estado;
        this.indiceCalculator = indiceCalculator;
        this.motorDecisao = motorDecisao;
        this.eventoRepository = eventoRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public TelemetriaDTO obterTelemetria() {
        TelemetriaDTO telemetria = estado.ler(this::montar);
        return telemetria.comEventos(eventosRecentes());
    }

    public StatusDTO obterStatus() {
        return estado.ler(f -> {
            IndiceDTO indice = indiceCalculator.calcular(f);
            Reservatorio r = f.getReservatorio();
            return new StatusDTO(clock.instant(), statusSistema(f), resumoFalado(f, indice), f.getTalhoes().size(), 1,
                    f.talhoesCriticos().size(), contarAtencao(f), f.aspersoresLigados(), r1(r.getNivel()),
                    r.isBloqueioEmergencia(), indice.valor(), f.getSimulacao().getRelogioSimulado(),
                    f.getSimulacao().getFatorVelocidade());
        });
    }

    private TelemetriaDTO montar(Fazenda f) {
        EstadoSimulacao sim = f.getSimulacao();
        List<TalhaoDTO> talhoes = f.getTalhoes().stream().map(t -> TalhaoDTO.de(t, f)).toList();
        List<BombaDTO> bombas = f.getTalhoes().stream().map(t -> BombaDTO.de(t, f)).toList();
        SimulacaoDTO simulacao = new SimulacaoDTO(sim.getFatorVelocidade(), sim.isPausada(),
                properties.simulacao().minutosPorTick() * sim.getFatorVelocidade(), sim.getTicks(),
                sim.getRelogioSimulado(), properties.simulacao().controlesDemonstracao());

        return new TelemetriaDTO(clock.instant(), sim.getRelogioSimulado(), statusSistema(f), simulacao,
                ReservatorioDTO.de(f), talhoes, bombas, EnergiaDTO.de(f), indiceCalculator.calcular(f),
                motorDecisao.decidir(f), alertas(f), List.of());
    }

    private List<EventoDTO> eventosRecentes() {
        try {
            return eventoRepository.findAllByOrderByIdDesc(PageRequest.of(0, EVENTOS_RECENTES)).stream()
                    .map(EventoDTO::de).toList();
        } catch (RuntimeException e) {
            // Telemetria ao vivo continua disponivel mesmo se o banco oscilar
            log.warn("Eventos recentes indisponiveis: {}", e.getMessage());
            return List.of();
        }
    }

    StatusSistemaDTO statusSistema(Fazenda f) {
        Reservatorio r = f.getReservatorio();
        List<Talhao> criticos = f.talhoesCriticos();
        int total = f.getTalhoes().size();

        if (r.isBloqueioEmergencia()) {
            return StatusSistemaDTO.de(StatusSistema.EMERGENCIA, "Reservatório em " + pct(r.getNivel())
                    + ": bloqueio de emergência, todas as bombas desligadas.");
        }
        if (!criticos.isEmpty()) {
            Talhao t = criticos.get(0);
            return StatusSistemaDTO.de(StatusSistema.RISCO_HIDRICO, t.getNome() + " com umidade crítica ("
                    + pct(t.getUmidade()) + "): irrigação automática em andamento"
                    + (criticos.size() > 1 ? " (+" + (criticos.size() - 1) + " talhão)." : "."));
        }
        if (r.classificarNivel() == StatusReservatorio.ATENCAO) {
            return StatusSistemaDTO.de(StatusSistema.ATENCAO, "Reservatório em atenção (" + pct(r.getNivel())
                    + "): monitoramento reforçado de " + total + " talhões.");
        }
        int atencao = contarAtencao(f);
        if (atencao > 0) {
            return StatusSistemaDTO.de(StatusSistema.ATENCAO, atencao + " talhão(ões) próximo(s) do limite crítico. "
                    + "Sistema monitorando " + total + " talhões e 1 reservatório central.");
        }
        return StatusSistemaDTO.de(StatusSistema.NORMAL,
                "Sistema monitorando " + total + " talhões e 1 reservatório central.");
    }

    List<AlertaDTO> alertas(Fazenda f) {
        Reservatorio r = f.getReservatorio();
        List<AlertaDTO> alertas = new ArrayList<>();
        if (r.isBloqueioEmergencia()) {
            alertas.add(new AlertaDTO(Severidade.EMERGENCIA, "Bloqueio de emergência ativo",
                    "Reservatório em " + pct(r.getNivel()) + ". Todas as bombas desligadas até " + pct(r.getLimiteRearme()) + ".",
                    null));
        } else if (r.classificarNivel() == StatusReservatorio.ATENCAO) {
            alertas.add(new AlertaDTO(Severidade.ATENCAO, "Reservatório em atenção",
                    "Nível em " + pct(r.getNivel()) + " (faixa de 15% a 30%).", null));
        }
        for (Talhao t : f.getTalhoes()) {
            if (t.isCritico()) {
                alertas.add(new AlertaDTO(Severidade.CRITICO,
                        t.isIrrigacaoBloqueada() ? t.getNome() + " crítico sem irrigação" : t.getNome() + " em irrigação crítica",
                        "Umidade " + pct(t.getUmidade()) + (t.isIrrigacaoBloqueada()
                                ? ": irrigação impedida pela proteção hídrica."
                                : ": aspersor acionado automaticamente."),
                        t.getId()));
            } else if (t.classificarUmidade() == StatusTalhao.ATENCAO && !t.isAspersorLigado()) {
                Double eta = f.horasAteCritico(t);
                alertas.add(new AlertaDTO(Severidade.ATENCAO, t.getNome() + " em atenção",
                        "Umidade " + pct(t.getUmidade()) + (eta != null ? ": crítico em ~" + horas(eta) + "." : "."),
                        t.getId()));
            }
        }
        if (f.consumoEnergiaKw() > 0 && f.coberturaSolar() < 100) {
            alertas.add(new AlertaDTO(Severidade.INFO, "Bombas usando energia da rede",
                    "A geração solar cobre " + Math.round(f.coberturaSolar()) + "% do consumo atual.", null));
        }
        return alertas;
    }

    /** Texto para leitura em voz (Web Speech API): sem simbolos, numeros inteiros. */
    String resumoFalado(Fazenda f, IndiceDTO indice) {
        Reservatorio r = f.getReservatorio();
        StatusSistema status = statusSistema(f).codigo();
        int criticos = f.talhoesCriticos().size();
        int atencao = contarAtencao(f);
        StringBuilder sb = new StringBuilder();
        sb.append("Status da fazenda: ").append(status.getRotulo().toLowerCase()).append(". ");
        sb.append("Reservatório em ").append(Math.round(r.getNivel())).append(" por cento");
        if (r.isBloqueioEmergencia()) {
            sb.append(", bloqueio de emergência ativo, todas as bombas desligadas");
        }
        sb.append(". ");
        sb.append("Umidade média dos talhões: ").append(Math.round(f.umidadeMedia())).append(" por cento. ");
        sb.append(f.aspersoresLigados()).append(" de ").append(f.getTalhoes().size()).append(" aspersores ligados. ");
        if (criticos == 0 && atencao == 0) {
            sb.append("Nenhum talhão em atenção. ");
        } else {
            if (criticos > 0) {
                sb.append(criticos).append(criticos == 1 ? " talhão crítico. " : " talhões críticos. ");
            }
            if (atencao > 0) {
                sb.append(atencao).append(atencao == 1 ? " talhão em atenção. " : " talhões em atenção. ");
            }
        }
        sb.append("Índice hidro-energético: ").append(indice.valor()).append(" de 100, ")
                .append(indice.rotulo().toLowerCase()).append(".");
        return sb.toString();
    }

    private static int contarAtencao(Fazenda f) {
        return (int) f.getTalhoes().stream().filter(t -> t.classificarUmidade() == StatusTalhao.ATENCAO).count();
    }
}
