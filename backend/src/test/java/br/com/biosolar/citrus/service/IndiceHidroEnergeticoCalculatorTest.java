package br.com.biosolar.citrus.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.simulation.CenarioInicial;

class IndiceHidroEnergeticoCalculatorTest {

    private static final Instant AGORA = Instant.parse("2026-09-24T15:00:00Z");

    private final IndiceHidroEnergeticoCalculator calculadora = new IndiceHidroEnergeticoCalculator();
    private final MotorRegras regras = new MotorRegras();

    private Fazenda fazenda() {
        return CenarioInicial.criar(AGORA, LocalDateTime.of(2026, 9, 24, 12, 0));
    }

    @Test
    void cenarioInicialEhEquilibrado() {
        IndiceDTO indice = calculadora.calcular(fazenda());

        // H = 67; S = media(100, 100, 64, 100) = 91; E = 100 (sem bombas ligadas)
        assertThat(indice.valor()).isEqualTo(81);
        assertThat(indice.classificacao()).isEqualTo("EQUILIBRADO");
        assertThat(indice.fatores()).extracting(IndiceDTO.Fator::peso).containsExactly(0.5, 0.3, 0.2);
    }

    @Test
    void emergenciaLimitaIndiceEmRisco() {
        Fazenda f = fazenda();
        f.getReservatorio().setNivel(10);
        regras.avaliar(f, AGORA);

        IndiceDTO indice = calculadora.calcular(f);

        assertThat(indice.travaEmergencia()).isTrue();
        assertThat(indice.valor()).isLessThanOrEqualTo(25);
        assertThat(indice.classificacao()).isEqualTo("RISCO");
    }
}
