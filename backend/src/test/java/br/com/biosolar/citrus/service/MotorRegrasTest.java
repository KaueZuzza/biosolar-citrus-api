package br.com.biosolar.citrus.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.simulation.CenarioInicial;
import br.com.biosolar.citrus.simulation.ModeloFisico;

/** Regras de seguranca hidrica e hierarquia P1 > P2 > P3 > P4 (sem Spring, sem banco). */
class MotorRegrasTest {

    private static final Instant AGORA = Instant.parse("2026-09-24T15:00:00Z");

    private final MotorRegras regras = new MotorRegras();
    private final ModeloFisico fisica = new ModeloFisico();
    private Fazenda fazenda;

    @BeforeEach
    void criarFazenda() {
        fazenda = CenarioInicial.criar(AGORA, LocalDateTime.of(2026, 9, 24, 12, 0));
        fazenda.getReservatorio().setNivelUltimaAvaliacao(fazenda.getReservatorio().getNivel());
    }

    private Talhao talhao(String id) {
        return fazenda.buscarTalhao(id).orElseThrow();
    }

    private boolean houveEvento(TipoEvento tipo) {
        return fazenda.getEventosPendentes().stream().map(Evento::getTipo).anyMatch(tipo::equals);
    }

    @Test
    @DisplayName("Teste 1: reservatório acima de 15%, comando manual funciona")
    void comandoManualComReservatorioSeguro() {
        assertThat(fazenda.getReservatorio().getNivel()).isGreaterThan(15);

        ResultadoComando ligar = regras.avaliarComandoManual(fazenda, talhao("A"), true, AGORA);
        assertThat(ligar.sucesso()).isTrue();
        assertThat(ligar.motivo()).isEqualTo(ResultadoComando.COMANDO_EXECUTADO);
        assertThat(talhao("A").isAspersorLigado()).isTrue();
        assertThat(talhao("A").getModoAcionamento()).isEqualTo(ModoAcionamento.MANUAL);

        ResultadoComando desligar = regras.avaliarComandoManual(fazenda, talhao("A"), false, AGORA);
        assertThat(desligar.sucesso()).isTrue();
        assertThat(talhao("A").isAspersorLigado()).isFalse();
        assertThat(houveEvento(TipoEvento.COMANDO_MANUAL)).isTrue();
    }

    @Test
    @DisplayName("Teste 2: reservatório abaixo de 15%, todas as bombas desligam")
    void reservatorioCriticoDesligaTodasAsBombas() {
        fazenda.getTalhoes().forEach(t -> regras.avaliarComandoManual(fazenda, t, true, AGORA));
        assertThat(fazenda.aspersoresLigados()).isEqualTo(4);

        fazenda.getReservatorio().setNivel(14.0);
        regras.avaliar(fazenda, AGORA);

        assertThat(fazenda.aspersoresLigados()).isZero();
        assertThat(fazenda.getReservatorio().isBloqueioEmergencia()).isTrue();
        assertThat(houveEvento(TipoEvento.BLOQUEIO_EMERGENCIA)).isTrue();
    }

    @Test
    @DisplayName("Teste 3: umidade < 25% e reservatório > 15%, aspersor liga automaticamente")
    void umidadeCriticaComReservatorioSeguroLigaAspersor() {
        talhao("C").setUmidade(24.0);

        regras.avaliar(fazenda, AGORA);

        assertThat(talhao("C").isAspersorLigado()).isTrue();
        assertThat(talhao("C").getModoAcionamento()).isEqualTo(ModoAcionamento.AUTOMATICO);
        assertThat(houveEvento(TipoEvento.IRRIGACAO_CRITICA)).isTrue();
        // os demais talhoes (umidade normal) permanecem desligados
        assertThat(fazenda.aspersoresLigados()).isEqualTo(1);
    }

    @Test
    @DisplayName("Teste 4: umidade < 25% e reservatório < 15%, aspersor NÃO liga")
    void umidadeCriticaComReservatorioCriticoNaoLiga() {
        talhao("A").setUmidade(12.0);
        fazenda.getReservatorio().setNivel(10.0);

        regras.avaliar(fazenda, AGORA);

        assertThat(talhao("A").isAspersorLigado()).isFalse();
        assertThat(talhao("A").isIrrigacaoBloqueada()).isTrue();
        assertThat(fazenda.getReservatorio().isBloqueioEmergencia()).isTrue();
        assertThat(houveEvento(TipoEvento.IRRIGACAO_BLOQUEADA)).isTrue();

        // o operador tambem nao consegue forcar o acionamento
        ResultadoComando manual = regras.avaliarComandoManual(fazenda, talhao("A"), true, AGORA);
        assertThat(manual.sucesso()).isFalse();
        assertThat(manual.motivo()).isEqualTo(ResultadoComando.BLOQUEIO_DE_EMERGENCIA);
        assertThat(talhao("A").isAspersorLigado()).isFalse();
    }

    @Test
    @DisplayName("Teste 5: após a emergência, todas as bombas permanecem desligadas")
    void aposEmergenciaBombasPermanecemDesligadas() {
        talhao("C").setUmidade(20.0);
        talhao("D").setUmidade(22.0);
        fazenda.getReservatorio().setNivel(14.0);
        regras.avaliar(fazenda, AGORA);
        assertThat(fazenda.getReservatorio().isBloqueioEmergencia()).isTrue();

        // 3 horas simuladas: a recarga solar eleva o nivel acima de 15%, mas abaixo do rearme (20%)
        for (int minuto = 0; minuto < 180; minuto++) {
            fisica.avancar(fazenda, 1, AGORA);
            regras.avaliar(fazenda, AGORA);
            assertThat(fazenda.aspersoresLigados()).as("minuto %d", minuto).isZero();
        }
        assertThat(fazenda.getReservatorio().getNivel()).isBetween(15.0, 20.0);
        assertThat(fazenda.getReservatorio().isBloqueioEmergencia()).isTrue();

        ResultadoComando manual = regras.avaliarComandoManual(fazenda, talhao("B"), true, AGORA);
        assertThat(manual.sucesso()).isFalse();
        assertThat(fazenda.aspersoresLigados()).isZero();
    }

    @Test
    @DisplayName("Rearme: ao atingir 20% o bloqueio é liberado e a irrigação crítica é retomada")
    void rearmeLiberaIrrigacaoCritica() {
        talhao("C").setUmidade(20.0);
        fazenda.getReservatorio().setNivel(12.0);
        regras.avaliar(fazenda, AGORA);
        assertThat(talhao("C").isAspersorLigado()).isFalse();

        fazenda.getReservatorio().setNivel(20.5);
        regras.avaliar(fazenda, AGORA);

        assertThat(fazenda.getReservatorio().isBloqueioEmergencia()).isFalse();
        assertThat(houveEvento(TipoEvento.RECUPERACAO_SISTEMA)).isTrue();
        assertThat(talhao("C").isAspersorLigado()).isTrue();
        assertThat(talhao("C").isIrrigacaoBloqueada()).isFalse();
    }

    @Test
    @DisplayName("P2 > P4: operador não pode desligar uma irrigação crítica")
    void manualNaoDesligaIrrigacaoCritica() {
        talhao("C").setUmidade(22.0);
        regras.avaliar(fazenda, AGORA);

        ResultadoComando r = regras.avaliarComandoManual(fazenda, talhao("C"), false, AGORA);

        assertThat(r.sucesso()).isFalse();
        assertThat(r.motivo()).isEqualTo(ResultadoComando.IRRIGACAO_CRITICA_EM_ANDAMENTO);
        assertThat(talhao("C").isAspersorLigado()).isTrue();
    }

    @Test
    @DisplayName("P3: irrigação automática é encerrada ao atingir a umidade alvo (40%)")
    void irrigacaoAutomaticaEncerraNoAlvo() {
        talhao("C").setUmidade(24.0);
        regras.avaliar(fazenda, AGORA);
        assertThat(talhao("C").isAspersorLigado()).isTrue();

        // a irrigacao eleva a umidade ate o alvo em algumas horas simuladas
        for (int minuto = 0; minuto < 600 && talhao("C").isAspersorLigado(); minuto++) {
            fisica.avancar(fazenda, 1, AGORA);
            regras.avaliar(fazenda, AGORA);
        }

        assertThat(talhao("C").isAspersorLigado()).isFalse();
        assertThat(talhao("C").getUmidade()).isGreaterThanOrEqualTo(40.0);
        assertThat(houveEvento(TipoEvento.IRRIGACAO_CONCLUIDA)).isTrue();
    }

    @Test
    @DisplayName("Simulação: sem irrigação a umidade cai e o reservatório consome água com bombas ligadas")
    void balancoHidrico() {
        double umidadeAntes = talhao("B").getUmidade();
        fisica.avancar(fazenda, 60, AGORA);
        assertThat(talhao("B").getUmidade()).isLessThan(umidadeAntes);

        regras.avaliarComandoManual(fazenda, talhao("A"), true, AGORA);
        regras.avaliarComandoManual(fazenda, talhao("B"), true, AGORA);
        double nivelAntes = fazenda.getReservatorio().getNivel();
        fisica.avancar(fazenda, 60, AGORA);
        assertThat(fazenda.getReservatorio().getNivel()).isLessThan(nivelAntes);
        assertThat(fazenda.getSimulacao().getEnergiaConsumidaKwh()).isPositive();
    }
}
