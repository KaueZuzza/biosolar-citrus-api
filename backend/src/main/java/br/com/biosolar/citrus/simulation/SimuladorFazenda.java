package br.com.biosolar.citrus.simulation;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.HistoricoService;
import br.com.biosolar.citrus.service.IndiceHidroEnergeticoCalculator;
import br.com.biosolar.citrus.service.MotorRegras;

/**
 * Loop de controle do servidor. A cada ciclo real (1 s), avanca o tempo da fazenda em passos de
 * 1 minuto simulado e, apos cada passo, executa o motor de regras, como a varredura de um CLP.
 * Assim a protecao hidrica reage no mesmo minuto simulado, mesmo com a simulacao acelerada.
 */
@Component
public class SimuladorFazenda {

    private final EstadoFazendaService estado;
    private final ModeloFisico modeloFisico;
    private final MotorRegras motorRegras;
    private final IndiceHidroEnergeticoCalculator indiceCalculator;
    private final HistoricoService historicoService;
    private final BioSolarProperties properties;
    private final Clock clock;

    private final AtomicLong ciclosExecutados = new AtomicLong();
    private volatile Instant ultimoCiclo;

    public SimuladorFazenda(EstadoFazendaService estado, ModeloFisico modeloFisico, MotorRegras motorRegras,
                            IndiceHidroEnergeticoCalculator indiceCalculator, HistoricoService historicoService,
                            BioSolarProperties properties, Clock clock) {
        this.estado = estado;
        this.modeloFisico = modeloFisico;
        this.motorRegras = motorRegras;
        this.indiceCalculator = indiceCalculator;
        this.historicoService = historicoService;
        this.properties = properties;
        this.clock = clock;
    }

    public void executarCiclo() {
        Instant agora = clock.instant();
        long[] geracao = new long[1];

        AmostraTelemetria amostra = estado.executar(fazenda -> {
            geracao[0] = estado.getGeracao();
            EstadoSimulacao sim = fazenda.getSimulacao();
            if (sim.isPausada()) {
                // Mesmo pausada, as regras de seguranca continuam sendo aplicadas
                motorRegras.avaliar(fazenda, agora);
                return null;
            }
            double minutos = properties.simulacao().minutosPorTick() * sim.getFatorVelocidade();
            int passos = Math.max(1, (int) Math.ceil(minutos));
            double passo = minutos / passos;
            for (int i = 0; i < passos; i++) {
                modeloFisico.avancar(fazenda, passo, agora);
                motorRegras.avaliar(fazenda, agora);
            }
            sim.registrarCiclo();

            int intervalo = Math.max(1, properties.simulacao().registrarLeituraACadaTicks());
            return sim.getTicks() % intervalo == 0
                    ? AmostraTelemetria.de(fazenda, indiceCalculator.calcular(fazenda).valor(), agora)
                    : null;
        });

        if (amostra != null) {
            // Descartada se o cenario for restaurado entre a captura e a gravacao
            estado.registrarLeitura(geracao[0], () -> historicoService.registrar(amostra));
        }
        ultimoCiclo = agora;
        ciclosExecutados.incrementAndGet();
    }

    public Instant getUltimoCiclo() {
        return ultimoCiclo;
    }

    public long getCiclosExecutados() {
        return ciclosExecutados.get();
    }
}
