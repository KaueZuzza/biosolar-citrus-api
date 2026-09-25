package br.com.biosolar.citrus.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.biosolar.citrus.service.EstadoFazendaService;

/** Dispara o ciclo do simulador periodicamente (desativado nos testes para mante-los deterministas). */
@Component
@ConditionalOnProperty(prefix = "biosolar.simulacao", name = "habilitada", havingValue = "true", matchIfMissing = true)
public class SimulacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoScheduler.class);

    private final SimuladorFazenda simulador;
    private final EstadoFazendaService estado;

    public SimulacaoScheduler(SimuladorFazenda simulador, EstadoFazendaService estado) {
        this.simulador = simulador;
        this.estado = estado;
    }

    @Scheduled(fixedRateString = "${biosolar.simulacao.intervalo-ms:1000}", initialDelay = 1000)
    public void ciclo() {
        try {
            simulador.executarCiclo();
        } catch (RuntimeException e) {
            log.error("Falha no ciclo de simulacao: {}", e.getMessage(), e);
        }
    }

    /** Traz para a automacao o cadastro alterado diretamente no banco (ex.: pelo pgAdmin). */
    @Scheduled(fixedDelayString = "${biosolar.simulacao.sincronizacao-cadastro-ms:5000}", initialDelay = 5000)
    public void sincronizarCadastro() {
        try {
            estado.sincronizarComBanco();
        } catch (RuntimeException e) {
            log.error("Falha ao sincronizar o cadastro com o banco: {}", e.getMessage(), e);
        }
    }
}
