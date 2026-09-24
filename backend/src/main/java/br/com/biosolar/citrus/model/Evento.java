package br.com.biosolar.citrus.model;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro do historico operacional: o que aconteceu, onde, por que e por qual regra. */
@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant instante;

    @Column(name = "hora_simulada")
    private LocalDateTime horaSimulada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severidade severidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemEvento origem;

    /** Regra da hierarquia que originou o evento (P1..P4), quando aplicavel. */
    private String regra;

    @Column(name = "talhao_id")
    private String talhaoId;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "nivel_reservatorio")
    private Double nivelReservatorio;

    protected Evento() {
    }

    public Evento(Instant instante, LocalDateTime horaSimulada, TipoEvento tipo, Severidade severidade,
                  OrigemEvento origem, String regra, String talhaoId, String titulo, String descricao,
                  Double nivelReservatorio) {
        this.instante = instante;
        this.horaSimulada = horaSimulada;
        this.tipo = tipo;
        this.severidade = severidade;
        this.origem = origem;
        this.regra = regra;
        this.talhaoId = talhaoId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.nivelReservatorio = nivelReservatorio;
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

    public TipoEvento getTipo() {
        return tipo;
    }

    public Severidade getSeveridade() {
        return severidade;
    }

    public OrigemEvento getOrigem() {
        return origem;
    }

    public String getRegra() {
        return regra;
    }

    public String getTalhaoId() {
        return talhaoId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Double getNivelReservatorio() {
        return nivelReservatorio;
    }
}
