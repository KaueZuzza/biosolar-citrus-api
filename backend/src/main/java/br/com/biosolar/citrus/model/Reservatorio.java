package br.com.biosolar.citrus.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Reservatorio central que abastece as quatro motobombas.
 * <ul>
 *   <li>NORMAL: nivel acima de 30%</li>
 *   <li>ATENCAO: nivel entre 15% e 30%</li>
 *   <li>CRITICO: nivel abaixo de 15% (dispara o bloqueio de emergencia)</li>
 * </ul>
 * O bloqueio so e liberado quando o nivel volta a {@code limiteRearme} (histerese),
 * evitando que as bombas liguem e desliguem repetidamente em torno de 15%.
 */
@Entity
@Table(name = "reservatorio")
public class Reservatorio {

    public static final int ID_CENTRAL = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "capacidade_m3", nullable = false)
    private double capacidadeM3;

    /** Nivel atual (% da capacidade). */
    @Column(nullable = false)
    private double nivel;

    @Column(name = "limite_atencao", nullable = false)
    private double limiteAtencao;

    @Column(name = "limite_critico", nullable = false)
    private double limiteCritico;

    @Column(name = "limite_rearme", nullable = false)
    private double limiteRearme;

    /** Vazao maxima de recarga do poco tubular (bomba solar), em m3/h ao meio-dia. */
    @Column(name = "vazao_recarga_m3h", nullable = false)
    private double vazaoRecargaM3h;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusReservatorio status = StatusReservatorio.NORMAL;

    @Column(name = "bloqueio_emergencia", nullable = false)
    private boolean bloqueioEmergencia;

    @Column(name = "ultima_atualizacao", nullable = false)
    private Instant ultimaAtualizacao;

    /** Nivel na ultima avaliacao de regras (usado para registrar a evolucao no historico). */
    @Transient
    private Double nivelUltimaAvaliacao;

    protected Reservatorio() {
    }

    public Reservatorio(String nome, double capacidadeM3, double nivel, double vazaoRecargaM3h, Instant agora) {
        this.id = ID_CENTRAL;
        this.nome = nome;
        this.capacidadeM3 = capacidadeM3;
        this.nivel = nivel;
        this.limiteAtencao = 30;
        this.limiteCritico = 15;
        this.limiteRearme = 20;
        this.vazaoRecargaM3h = vazaoRecargaM3h;
        this.ultimaAtualizacao = agora;
        this.status = classificarNivel();
    }

    public StatusReservatorio classificarNivel() {
        if (nivel < limiteCritico) {
            return StatusReservatorio.CRITICO;
        }
        if (nivel <= limiteAtencao) {
            return StatusReservatorio.ATENCAO;
        }
        return StatusReservatorio.NORMAL;
    }

    /** Nivel abaixo do limite de seguranca: nenhuma bomba pode operar. */
    public boolean isAbaixoDoLimiteCritico() {
        return nivel < limiteCritico;
    }

    public double getVolumeM3() {
        return capacidadeM3 * nivel / 100.0;
    }

    public void ajustarVolume(double deltaM3) {
        setNivel(nivel + deltaM3 / capacidadeM3 * 100.0);
    }

    public Integer getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public double getCapacidadeM3() {
        return capacidadeM3;
    }

    public double getNivel() {
        return nivel;
    }

    public void setNivel(double nivel) {
        this.nivel = Math.max(0, Math.min(100, nivel));
    }

    public double getLimiteAtencao() {
        return limiteAtencao;
    }

    public double getLimiteCritico() {
        return limiteCritico;
    }

    public double getLimiteRearme() {
        return limiteRearme;
    }

    public double getVazaoRecargaM3h() {
        return vazaoRecargaM3h;
    }

    public StatusReservatorio getStatus() {
        return status;
    }

    public void setStatus(StatusReservatorio status) {
        this.status = status;
    }

    public boolean isBloqueioEmergencia() {
        return bloqueioEmergencia;
    }

    public void setBloqueioEmergencia(boolean bloqueioEmergencia) {
        this.bloqueioEmergencia = bloqueioEmergencia;
    }

    public Instant getUltimaAtualizacao() {
        return ultimaAtualizacao;
    }

    public void setUltimaAtualizacao(Instant ultimaAtualizacao) {
        this.ultimaAtualizacao = ultimaAtualizacao;
    }

    public Double getNivelUltimaAvaliacao() {
        return nivelUltimaAvaliacao;
    }

    public void setNivelUltimaAvaliacao(Double nivelUltimaAvaliacao) {
        this.nivelUltimaAvaliacao = nivelUltimaAvaliacao;
    }
}
