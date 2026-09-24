package br.com.biosolar.citrus.model;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Amostra periodica da telemetria geral (base dos graficos e do relatorio). */
@Entity
@Table(name = "leitura_telemetria")
public class LeituraTelemetria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant instante;

    @Column(name = "hora_simulada", nullable = false)
    private LocalDateTime horaSimulada;

    @Column(name = "nivel_reservatorio", nullable = false)
    private double nivelReservatorio;

    @Column(name = "umidade_media", nullable = false)
    private double umidadeMedia;

    @Column(name = "aspersores_ligados", nullable = false)
    private int aspersoresLigados;

    @Column(name = "consumo_kw", nullable = false)
    private double consumoKw;

    @Column(name = "geracao_solar_kw", nullable = false)
    private double geracaoSolarKw;

    @Column(nullable = false)
    private int indice;

    @Column(name = "bloqueio_emergencia", nullable = false)
    private boolean bloqueioEmergencia;

    protected LeituraTelemetria() {
    }

    public LeituraTelemetria(Instant instante, LocalDateTime horaSimulada, double nivelReservatorio,
                             double umidadeMedia, int aspersoresLigados, double consumoKw,
                             double geracaoSolarKw, int indice, boolean bloqueioEmergencia) {
        this.instante = instante;
        this.horaSimulada = horaSimulada;
        this.nivelReservatorio = nivelReservatorio;
        this.umidadeMedia = umidadeMedia;
        this.aspersoresLigados = aspersoresLigados;
        this.consumoKw = consumoKw;
        this.geracaoSolarKw = geracaoSolarKw;
        this.indice = indice;
        this.bloqueioEmergencia = bloqueioEmergencia;
    }

    public Long getId() {
        return id;
    }

    public Instant getInstante() {
        return instante;
    }

    public LocalDateTime getHoraSimulada() {
        return horaSimulada;
    }

    public double getNivelReservatorio() {
        return nivelReservatorio;
    }

    public double getUmidadeMedia() {
        return umidadeMedia;
    }

    public int getAspersoresLigados() {
        return aspersoresLigados;
    }

    public double getConsumoKw() {
        return consumoKw;
    }

    public double getGeracaoSolarKw() {
        return geracaoSolarKw;
    }

    public int getIndice() {
        return indice;
    }

    public boolean isBloqueioEmergencia() {
        return bloqueioEmergencia;
    }
}
