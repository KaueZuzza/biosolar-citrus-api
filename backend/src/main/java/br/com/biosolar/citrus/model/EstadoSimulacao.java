package br.com.biosolar.citrus.model;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Relogio da fazenda, velocidade da simulacao e acumuladores de energia e agua. */
@Entity
@Table(name = "estado_simulacao")
public class EstadoSimulacao {

    public static final int ID_UNICO = 1;

    @Id
    private Integer id;

    @Column(name = "relogio_simulado", nullable = false)
    private LocalDateTime relogioSimulado;

    @Column(name = "fator_velocidade", nullable = false)
    private int fatorVelocidade = 1;

    @Column(nullable = false)
    private boolean pausada;

    @Column(nullable = false)
    private long ticks;

    @Column(name = "potencia_solar_pico_kw", nullable = false)
    private double potenciaSolarPicoKw;

    @Column(name = "energia_consumida_kwh", nullable = false)
    private double energiaConsumidaKwh;

    /** Parte do consumo das bombas atendida pela usina solar. */
    @Column(name = "energia_solar_kwh", nullable = false)
    private double energiaSolarKwh;

    @Column(name = "agua_consumida_m3", nullable = false)
    private double aguaConsumidaM3;

    @Column(name = "iniciada_em", nullable = false)
    private Instant iniciadaEm;

    protected EstadoSimulacao() {
    }

    public EstadoSimulacao(LocalDateTime relogioSimulado, double potenciaSolarPicoKw, Instant agora) {
        this.id = ID_UNICO;
        this.relogioSimulado = relogioSimulado;
        this.potenciaSolarPicoKw = potenciaSolarPicoKw;
        this.iniciadaEm = agora;
    }

    /** Reinicia relogio, velocidade e acumuladores (restauracao do cenario). A usina cadastrada e mantida. */
    public void reiniciar(LocalDateTime relogioSimulado, Instant agora) {
        this.relogioSimulado = relogioSimulado;
        this.fatorVelocidade = 1;
        this.pausada = false;
        this.ticks = 0;
        this.energiaConsumidaKwh = 0;
        this.energiaSolarKwh = 0;
        this.aguaConsumidaM3 = 0;
        this.iniciadaEm = agora;
    }

    public void setPotenciaSolarPicoKw(double potenciaSolarPicoKw) {
        this.potenciaSolarPicoKw = potenciaSolarPicoKw;
    }

    public void acumular(double energiaKwh, double solarKwh, double aguaM3) {
        this.energiaConsumidaKwh += energiaKwh;
        this.energiaSolarKwh += solarKwh;
        this.aguaConsumidaM3 += aguaM3;
    }

    public void avancarRelogio(double minutos) {
        this.relogioSimulado = relogioSimulado.plusSeconds(Math.round(minutos * 60));
    }

    /** Conta um ciclo do simulador (1 ciclo = 1 intervalo real, independentemente da velocidade). */
    public void registrarCiclo() {
        this.ticks++;
    }

    public Integer getId() {
        return id;
    }

    public LocalDateTime getRelogioSimulado() {
        return relogioSimulado;
    }

    public int getFatorVelocidade() {
        return fatorVelocidade;
    }

    public void setFatorVelocidade(int fatorVelocidade) {
        this.fatorVelocidade = fatorVelocidade;
    }

    public boolean isPausada() {
        return pausada;
    }

    public void setPausada(boolean pausada) {
        this.pausada = pausada;
    }

    public long getTicks() {
        return ticks;
    }

    public double getPotenciaSolarPicoKw() {
        return potenciaSolarPicoKw;
    }

    public double getEnergiaConsumidaKwh() {
        return energiaConsumidaKwh;
    }

    public double getEnergiaSolarKwh() {
        return energiaSolarKwh;
    }

    public double getAguaConsumidaM3() {
        return aguaConsumidaM3;
    }

    public Instant getIniciadaEm() {
        return iniciadaEm;
    }
}
