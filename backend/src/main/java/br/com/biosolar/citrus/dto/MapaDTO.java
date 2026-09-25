package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.StatusTalhao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mapa da Fazenda: talhoes em GeoJSON, dados publicos do municipio (IBGE) e clima (Open-Meteo). */
public final class MapaDTO {

    /** Talhao sem area desenhada: aparece em posicao ilustrativa, calculada (nunca gravada). */
    public static final String ORIGEM_ILUSTRATIVA = "ILUSTRATIVA";
    /** Area desenhada pelo operador e gravada na tabela talhao_area. */
    public static final String ORIGEM_DESENHADA = "DESENHADA";

    private MapaDTO() {
    }

    public record Ponto(double lat, double lng) {
    }

    /** Geometria GeoJSON (Polygon): coordinates = [anel externo fechado de [longitude, latitude]]. */
    public record Geometria(String type, List<List<double[]>> coordinates) {
        public static Geometria poligono(List<double[]> anelFechado) {
            return new Geometria("Polygon", List.of(anelFechado));
        }
    }

    public record PropriedadesTalhao(
            String id,
            String nome,
            String cultura,
            String culturaRotulo,
            String variedade,
            String solo,
            double areaCadastroHa,
            Double areaMapaHa,
            int plantas,
            double umidade,
            StatusTalhao status,
            double limiteCritico,
            double limiteAtencao,
            double umidadeAlvo,
            double variacaoPorHora,
            Double horasAteCritico,
            boolean aspersorLigado,
            ModoAcionamento modoAcionamento,
            boolean irrigacaoBloqueada,
            String bombaId,
            String origemGeometria,
            Instant areaAtualizadaEm,
            Ponto centro) {
    }

    /** Feature GeoJSON de um talhao. */
    public record TalhaoMapa(String type, String id, Geometria geometry, PropriedadesTalhao properties) {
    }

    /** FeatureCollection GeoJSON com os talhoes ativos e o contexto do mapa. */
    public record Talhoes(
            String type,
            List<TalhaoMapa> features,
            Ponto referenciaFazenda,
            String municipio,
            String uf,
            Ponto sedeMunicipio,
            boolean bancoDisponivel,
            List<String> avisos,
            Instant geradoEm) {
    }

    /** Corpo do PUT /mapa/talhoes/{id}/area: cantos do talhao em sequencia, como [longitude, latitude]. */
    public record AreaRequest(
            @NotNull(message = "informe os pontos do polígono")
            @Size(min = 3, max = 201, message = "marque de 3 a 200 pontos")
            List<List<Double>> coordenadas) {
    }

    // ---- Fontes publicas --------------------------------------------------------------------------

    public record Fonte(String nome, String descricao, String url) {
    }

    /** Producao de um produto da PAM/IBGE no municipio. {@code semProducao}: o IBGE registra "-" (zero). */
    public record Cultivo(String produto, Double areaColhidaHa, Double producaoT, Double rendimentoKgHa,
                          Double valorMilReais, boolean semProducao) {
    }

    public record RankingEstado(String produto, int posicao, int municipiosProdutores, double participacaoPct,
                                double producaoEstadoT) {
    }

    public record ProducaoCitros(String ano, Cultivo laranja, Cultivo limao, RankingEstado rankingLaranja) {
    }

    public record Municipio(
            boolean disponivel,
            String codigoIbge,
            String nome,
            String uf,
            String microrregiao,
            String mesorregiao,
            String regiaoImediata,
            Double areaKm2,
            Ponto sede,
            JsonNode contorno,
            ProducaoCitros producao,
            List<Fonte> fontes,
            Instant consultadoEm,
            String aviso) {
    }

    public record DiaClima(String data, Double chuvaMm, Integer probabilidadeChuva, Double et0Mm, Double temperaturaMax,
                           Double temperaturaMin, boolean previsao) {
    }

    public record Clima(
            boolean disponivel,
            String horarioLocal,
            Double temperatura,
            Double umidadeAr,
            Double precipitacao,
            Double ventoKmh,
            Integer codigoTempo,
            String descricaoTempo,
            Double chuvaUltimos7DiasMm,
            Double et0Media7DiasMm,
            Double chuvaPrevista3DiasMm,
            Double et0HojeMm,
            List<DiaClima> dias,
            Fonte fonte,
            Instant consultadoEm,
            String aviso) {
    }
}
