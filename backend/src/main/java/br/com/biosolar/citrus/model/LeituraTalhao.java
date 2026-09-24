package br.com.biosolar.citrus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Umidade e estado do aspersor de um talhao em uma {@link LeituraTelemetria}. */
@Entity
@Table(name = "leitura_talhao")
public class LeituraTalhao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leitura_id", nullable = false)
    private Long leituraId;

    @Column(name = "talhao_id", nullable = false)
    private String talhaoId;

    @Column(nullable = false)
    private double umidade;

    @Column(name = "aspersor_ligado", nullable = false)
    private boolean aspersorLigado;

    protected LeituraTalhao() {
    }

    public LeituraTalhao(Long leituraId, String talhaoId, double umidade, boolean aspersorLigado) {
        this.leituraId = leituraId;
        this.talhaoId = talhaoId;
        this.umidade = umidade;
        this.aspersorLigado = aspersorLigado;
    }

    public Long getLeituraId() {
        return leituraId;
    }

    public String getTalhaoId() {
        return talhaoId;
    }

    public double getUmidade() {
        return umidade;
    }

    public boolean isAspersorLigado() {
        return aspersorLigado;
    }
}
