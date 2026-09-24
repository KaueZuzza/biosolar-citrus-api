package br.com.biosolar.citrus.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara o ciclo do simulador periodicamente (desativado nos testes para mante-los deterministas). */
@Component
@ConditionalOnProperty(prefix = "biosolar.simulacao", name = "habilitada", havingValue = "true", matchIfMissing = true)
public class SimulacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoScheduler.class);

    private final SimuladorFazenda simulador;

    public SimulacaoScheduler(SimuladorFazenda simulador) {
        this.simulador = simulador;
    }

    @Scheduled(fixedRateString = "${biosolar.simulacao.intervalo-ms:1000}", initialDelay = 1000)
    public void ciclo() {
        try {
            simulador.executarCiclo();
        } catch (RuntimeException e) {
            log.error("Falha no ciclo de simulacao: {}", e.getMessage(), e);
        }
    }
}
