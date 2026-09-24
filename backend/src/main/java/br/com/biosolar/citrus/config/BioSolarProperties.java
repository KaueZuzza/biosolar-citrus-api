package br.com.biosolar.citrus.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracoes da aplicacao (prefixo {@code biosolar} no application.yml).
 */
@ConfigurationProperties(prefix = "biosolar")
public record BioSolarProperties(
        @DefaultValue Cors cors,
        @DefaultValue Simulacao simulacao) {

    public record Cors(@DefaultValue("*") List<String> origensPermitidas) {
    }

    /**
     * @param habilitada                 liga o ciclo automatico do simulador (desligado nos testes)
     * @param intervaloMs                intervalo real entre ciclos
     * @param minutosPorTick             minutos simulados por ciclo com fator de velocidade 1x
     * @param registrarLeituraACadaTicks frequencia de gravacao do historico de telemetria
     * @param controlesDemonstracao      habilita os endpoints do painel de simulacao
     * @param horaInicial                hora do relogio simulado ao iniciar/restaurar o cenario
     */
    public record Simulacao(
            @DefaultValue("true") boolean habilitada,
            @DefaultValue("1000") long intervaloMs,
            @DefaultValue("1") double minutosPorTick,
            @DefaultValue("2") int registrarLeituraACadaTicks,
            @DefaultValue("true") boolean controlesDemonstracao,
            @DefaultValue("9") int horaInicial) {
    }
}
