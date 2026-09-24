package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTelemetriaRepository;

/** Indicadores calculados: combina o estado atual (memoria) com os totais do historico (banco). */
@Service
public class IndicadoresService {

    private static final List<TipoEvento> TIPOS_ACIONAMENTO = List.of(
            TipoEvento.IRRIGACAO_CRITICA, TipoEvento.COMANDO_MANUAL, TipoEvento.COMANDO_RECUSADO);

    private final EstadoFazendaService estado;
    private final IndiceHidroEnergeticoCalculator indiceCalculator;
    private final EventoRepository eventoRepository;
    private final LeituraTelemetriaRepository leituraRepository;
    private final Clock clock;

    public IndicadoresService(EstadoFazendaService estado, IndiceHidroEnergeticoCalculator indiceCalculator,
                              EventoRepository eventoRepository, LeituraTelemetriaRepository leituraRepository,
                              Clock clock) {
        this.estado = estado;
        this.indiceCalculator = indiceCalculator;
        this.eventoRepository = eventoRepository;
        this.leituraRepository = leituraRepository;
        this.clock = clock;
    }

    record Instantaneo(IndiceDTO indice, double nivel, Double autonomia, double tendencia, double umidadeMedia,
                       int aspersores, int talhoes, int criticos, EnergiaDTO energia, double agua,
                       List<String> talhaoIds) {
    }

    Instantaneo instantaneo() {
        return estado.ler(f -> new Instantaneo(indiceCalculator.calcular(f), r1(f.getReservatorio().getNivel()),
                r1(f.autonomiaHoras()), r1(f.tendenciaReservatorioPorHora()), r1(f.umidadeMedia()),
                f.aspersoresLigados(), f.getTalhoes().size(), f.talhoesCriticos().size(), EnergiaDTO.de(f),
                r1(f.getSimulacao().getAguaConsumidaM3()),
                f.getTalhoes().stream().map(t -> t.getId()).toList()));
    }

    public IndicadoresDTO calcular() {
        Instantaneo agora = instantaneo();
        return new IndicadoresDTO(clock.instant(), agora.indice(), agora.nivel(), agora.autonomia(),
                agora.tendencia(), agora.umidadeMedia(), agora.aspersores(), agora.talhoes(), agora.criticos(),
                acionamentos(agora.talhaoIds()), agora.energia(), agora.agua(), estatisticasReservatorio());
    }

    IndicadoresDTO.Acionamentos acionamentos(List<String> talhaoIds) {
        Map<String, Map<TipoEvento, Long>> contagem = new HashMap<>();
        for (EventoRepository.ContagemPorTalhao c : eventoRepository.contarPorTalhaoETipo(TIPOS_ACIONAMENTO)) {
            contagem.computeIfAbsent(c.getTalhaoId(), k -> new EnumMap<>(TipoEvento.class)).put(c.getTipo(), c.getTotal());
        }
        List<IndicadoresDTO.PorTalhao> porTalhao = talhaoIds.stream().map(id -> {
            Map<TipoEvento, Long> m = contagem.getOrDefault(id, Map.of());
            return new IndicadoresDTO.PorTalhao(id, m.getOrDefault(TipoEvento.IRRIGACAO_CRITICA, 0L),
                    m.getOrDefault(TipoEvento.COMANDO_MANUAL, 0L), m.getOrDefault(TipoEvento.COMANDO_RECUSADO, 0L));
        }).toList();

        return new IndicadoresDTO.Acionamentos(
                eventoRepository.countByTipo(TipoEvento.IRRIGACAO_CRITICA),
                eventoRepository.countByTipo(TipoEvento.COMANDO_MANUAL),
                eventoRepository.countByTipo(TipoEvento.COMANDO_RECUSADO),
                eventoRepository.countByTipo(TipoEvento.BLOQUEIO_EMERGENCIA),
                porTalhao);
    }

    IndicadoresDTO.EstatisticasReservatorio estatisticasReservatorio() {
        LeituraTelemetriaRepository.Estatisticas e = leituraRepository.estatisticas();
        long total = e == null || e.getTotal() == null ? 0 : e.getTotal();
        if (total == 0) {
            return new IndicadoresDTO.EstatisticasReservatorio(null, null, null, null, 0);
        }
        return new IndicadoresDTO.EstatisticasReservatorio(r1(e.getMedio()), r1(e.getMinimo()), r1(e.getMaximo()),
                r1(e.getIndiceMedio()), total);
    }
}
