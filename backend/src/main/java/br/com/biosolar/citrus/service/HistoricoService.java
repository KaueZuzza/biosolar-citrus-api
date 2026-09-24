package br.com.biosolar.citrus.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.HistoricoDTO;
import br.com.biosolar.citrus.model.LeituraTalhao;
import br.com.biosolar.citrus.model.LeituraTelemetria;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTalhaoRepository;
import br.com.biosolar.citrus.repository.LeituraTelemetriaRepository;
import br.com.biosolar.citrus.simulation.AmostraTelemetria;

/** Gravacao e consulta do historico de telemetria e de eventos. */
@Service
public class HistoricoService {

    public static final int LIMITE_MAXIMO_PONTOS = 720;
    public static final int LIMITE_MAXIMO_EVENTOS = 500;

    private static final Logger log = LoggerFactory.getLogger(HistoricoService.class);

    private final LeituraTelemetriaRepository leituraRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final EventoRepository eventoRepository;
    private final BioSolarProperties properties;
    private final Clock clock;

    public HistoricoService(LeituraTelemetriaRepository leituraRepository,
                            LeituraTalhaoRepository leituraTalhaoRepository, EventoRepository eventoRepository,
                            BioSolarProperties properties, Clock clock) {
        this.leituraRepository = leituraRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.eventoRepository = eventoRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Retencao simples: remove leituras de telemetria (amostras dos graficos) mais antigas que o limite
     * configurado. O historico de EVENTOS nunca e apagado por esta rotina.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    @Transactional
    public int aplicarRetencao() {
        Instant limite = clock.instant().minus(Duration.ofHours(properties.simulacao().retencaoLeiturasHoras()));
        int removidas = leituraRepository.excluirAnterioresA(limite);
        if (removidas > 0) {
            log.info("Retencao: {} leitura(s) de telemetria anteriores a {} removida(s).", removidas, limite);
        }
        return removidas;
    }

    @Transactional
    public void registrar(AmostraTelemetria amostra) {
        LeituraTelemetria leitura = leituraRepository.save(new LeituraTelemetria(amostra.instante(),
                amostra.horaSimulada(), amostra.nivelReservatorio(), amostra.umidadeMedia(),
                amostra.aspersoresLigados(), amostra.consumoKw(), amostra.geracaoSolarKw(), amostra.indice(),
                amostra.bloqueioEmergencia()));
        leituraTalhaoRepository.saveAll(amostra.talhoes().stream()
                .map(t -> new LeituraTalhao(leitura.getId(), t.talhaoId(), t.umidade(), t.aspersorLigado()))
                .toList());
    }

    @Transactional(readOnly = true)
    public HistoricoDTO consultar(int limite) {
        int quantidade = Math.max(1, Math.min(limite, LIMITE_MAXIMO_PONTOS));
        List<LeituraTelemetria> leituras = new ArrayList<>(
                leituraRepository.findAllByOrderByIdDesc(PageRequest.of(0, quantidade)));
        Collections.reverse(leituras);
        if (leituras.isEmpty()) {
            return new HistoricoDTO(0, List.of());
        }

        Map<Long, List<LeituraTalhao>> porLeitura = leituraTalhaoRepository
                .findByLeituraIdIn(leituras.stream().map(LeituraTelemetria::getId).toList()).stream()
                .collect(Collectors.groupingBy(LeituraTalhao::getLeituraId));

        List<HistoricoDTO.Ponto> pontos = leituras.stream().map(l -> {
            Map<String, Double> umidades = new TreeMap<>();
            Map<String, Boolean> aspersores = new TreeMap<>();
            for (LeituraTalhao lt : porLeitura.getOrDefault(l.getId(), List.of())) {
                umidades.put(lt.getTalhaoId(), lt.getUmidade());
                aspersores.put(lt.getTalhaoId(), lt.isAspersorLigado());
            }
            return new HistoricoDTO.Ponto(l.getInstante(), l.getHoraSimulada(), l.getNivelReservatorio(),
                    l.getUmidadeMedia(), umidades, aspersores, l.getAspersoresLigados(), l.getConsumoKw(),
                    l.getGeracaoSolarKw(), l.getIndice(), l.isBloqueioEmergencia());
        }).toList();

        return new HistoricoDTO(pontos.size(), pontos);
    }

    @Transactional(readOnly = true)
    public List<EventoDTO> eventos(int limite, Long desdeId) {
        PageRequest pagina = PageRequest.of(0, Math.max(1, Math.min(limite, LIMITE_MAXIMO_EVENTOS)));
        return (desdeId == null
                ? eventoRepository.findAllByOrderByIdDesc(pagina)
                : eventoRepository.findByIdGreaterThanOrderByIdDesc(desdeId, pagina))
                .stream().map(EventoDTO::de).toList();
    }
}
