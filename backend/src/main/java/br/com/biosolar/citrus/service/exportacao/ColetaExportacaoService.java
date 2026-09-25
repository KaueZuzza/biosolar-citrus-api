package br.com.biosolar.citrus.service.exportacao;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.HistoricoDTO;
import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.RelatorioDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.exception.BancoIndisponivelException;
import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.LeituraTalhao;
import br.com.biosolar.citrus.model.LeituraTelemetria;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTalhaoRepository;
import br.com.biosolar.citrus.repository.LeituraTelemetriaRepository;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.IndicadoresService;
import br.com.biosolar.citrus.service.RelatorioService;
import br.com.biosolar.citrus.service.TelemetriaService;

/** Reune os dados das exportacoes (PDF, Excel, e-mail) a partir do banco e do estado ao vivo. */
@Service
public class ColetaExportacaoService {

    /** Linhas maximas das abas de historico: acima disso, as leituras sao amostradas uniformemente. */
    static final int MAX_LEITURAS = 3000;
    /** Eventos maximos na planilha (os mais recentes). */
    static final int MAX_EVENTOS = 20000;
    private static final int LOTE_CONSULTA = 1000;

    private final RelatorioService relatorioService;
    private final TelemetriaService telemetriaService;
    private final IndicadoresService indicadoresService;
    private final EstadoFazendaService estado;
    private final LeituraTelemetriaRepository leituraRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final EventoRepository eventoRepository;
    private final Clock clock;

    public ColetaExportacaoService(RelatorioService relatorioService, TelemetriaService telemetriaService,
                                   IndicadoresService indicadoresService,
                                   EstadoFazendaService estado, LeituraTelemetriaRepository leituraRepository,
                                   LeituraTalhaoRepository leituraTalhaoRepository, EventoRepository eventoRepository,
                                   Clock clock) {
        this.relatorioService = relatorioService;
        this.telemetriaService = telemetriaService;
        this.indicadoresService = indicadoresService;
        this.estado = estado;
        this.leituraRepository = leituraRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.eventoRepository = eventoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DadosExportacao coletar() {
        if (estado.isBancoEmEspera()) {
            // Sem banco nao ha historico: responde na hora em vez de esperar o timeout de conexao
            throw new BancoIndisponivelException("O banco de dados está temporariamente indisponível, e o relatório "
                    + "depende do histórico gravado nele. A automação continua funcionando; tente de novo em instantes.");
        }
        RelatorioDTO relatorio = relatorioService.gerar();
        TelemetriaDTO telemetria = telemetriaService.obterTelemetria();
        IndicadoresDTO indicadores = indicadoresService.calcular();

        long total = leituraRepository.count();
        long passo = Math.max(1, (total + MAX_LEITURAS - 1) / MAX_LEITURAS);
        List<LeituraTelemetria> leituras = total == 0 ? List.of() : leituraRepository.amostrar(passo);
        List<HistoricoDTO.Ponto> serie = montarSerie(leituras);
        TreeSet<String> talhoesSerie = new TreeSet<>();
        serie.forEach(p -> talhoesSerie.addAll(p.umidades().keySet()));

        Page<Evento> pagina = eventoRepository.findAll(PageRequest.of(0, MAX_EVENTOS, Sort.by(Sort.Direction.DESC, "id")));
        List<EventoDTO> eventos = new ArrayList<>(pagina.getContent().stream().map(EventoDTO::de).toList());
        Collections.reverse(eventos);
        long omitidos = Math.max(0, pagina.getTotalElements() - eventos.size());

        Map<String, Double> tempoIrrigando = new HashMap<>();
        for (LeituraTalhaoRepository.TempoIrrigacao t : leituraTalhaoRepository.tempoIrrigando()) {
            long totalLeituras = t.getTotal() == null ? 0 : t.getTotal();
            long ligadas = t.getLigadas() == null ? 0 : t.getLigadas();
            tempoIrrigando.put(t.getTalhaoId(), totalLeituras == 0 ? 0.0 : ligadas * 100.0 / totalLeituras);
        }

        return new DadosExportacao(relatorio, telemetria, indicadores, serie, total, passo, eventos, omitidos, tempoIrrigando,
                List.copyOf(talhoesSerie), clock.getZone());
    }

    private List<HistoricoDTO.Ponto> montarSerie(List<LeituraTelemetria> leituras) {
        Map<Long, List<LeituraTalhao>> porLeitura = new HashMap<>();
        List<Long> ids = leituras.stream().map(LeituraTelemetria::getId).toList();
        for (int i = 0; i < ids.size(); i += LOTE_CONSULTA) {
            List<Long> lote = ids.subList(i, Math.min(ids.size(), i + LOTE_CONSULTA));
            porLeitura.putAll(leituraTalhaoRepository.findByLeituraIdIn(lote).stream()
                    .collect(Collectors.groupingBy(LeituraTalhao::getLeituraId)));
        }
        return leituras.stream().map(l -> {
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
    }
}
