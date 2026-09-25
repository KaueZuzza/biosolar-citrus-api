package br.com.biosolar.citrus.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Area de um talhao no Mapa da Fazenda: poligono desenhado pelo operador sobre a imagem de satelite.
 * Independente do cadastro do talhao (tabela propria, criada pela migracao V4).
 */
@Entity
@Table(name = "talhao_area")
public class TalhaoArea {

    @Id
    @Column(name = "talhao_id", length = 10)
    private String talhaoId;

    /** Anel externo em GeoJSON: [[longitude, latitude], ...], sem repetir o primeiro ponto. */
    @Column(nullable = false, length = 12000)
    private String coordenadas;

    @Column(nullable = false)
    private int vertices;

    @Column(name = "area_ha", nullable = false)
    private double areaHa;

    @Column(name = "centro_lat", nullable = false)
    private double centroLat;

    @Column(name = "centro_lng", nullable = false)
    private double centroLng;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected TalhaoArea() {
        // JPA
    }

    public TalhaoArea(String talhaoId) {
        this.talhaoId = talhaoId;
    }

    public void definir(String coordenadas, int vertices, double areaHa, double centroLat, double centroLng,
                        Instant atualizadoEm) {
        this.coordenadas = coordenadas;
        this.vertices = vertices;
        this.areaHa = areaHa;
        this.centroLat = centroLat;
        this.centroLng = centroLng;
        this.atualizadoEm = atualizadoEm;
    }

    public String getTalhaoId() {
        return talhaoId;
    }

    public String getCoordenadas() {
        return coordenadas;
    }

    public int getVertices() {
        return vertices;
    }

    public double getAreaHa() {
        return areaHa;
    }

    public double getCentroLat() {
        return centroLat;
    }

    public double getCentroLng() {
        return centroLng;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
