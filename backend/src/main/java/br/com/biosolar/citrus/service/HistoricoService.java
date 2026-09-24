package br.com.biosolar.citrus.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final LeituraTelemetriaRepository leituraRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final EventoRepository eventoRepository;

    public HistoricoService(LeituraTelemetriaRepository leituraRepository,
                            LeituraTalhaoRepository leituraTalhaoRepository, EventoRepository eventoRepository) {
        this.leituraRepository = leituraRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.eventoRepository = eventoRepository;
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
