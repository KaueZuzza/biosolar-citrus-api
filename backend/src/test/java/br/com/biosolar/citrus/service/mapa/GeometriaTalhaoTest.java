package br.com.biosolar.citrus.service.mapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Area geodesica, validacao do poligono e layout ilustrativo dos talhoes. */
class GeometriaTalhaoTest {

    /** Quadrado de ~lado metros com o canto sudoeste em (lat, lng). */
    private static List<List<Double>> quadrado(double lat, double lng, double lado) {
        double dLat = lado / 111320.0;
        double dLng = lado / (111320.0 * Math.cos(Math.toRadians(lat)));
        return List.of(List.of(lng, lat), List.of(lng + dLng, lat), List.of(lng + dLng, lat + dLat),
                List.of(lng, lat + dLat), List.of(lng, lat));
    }

    @Test
    void areaGeodesicaDeUmHectareEmCapitaoPoco() {
        List<double[]> anel = GeometriaTalhao.normalizar(quadrado(-1.757, -47.126, 100));
        assertThat(anel).hasSize(4);  // ponto de fechamento removido
        assertThat(GeometriaTalhao.areaHectares(anel)).isCloseTo(1.0, within(0.01));
        double[] centro = GeometriaTalhao.centro(anel);
        assertThat(centro[1]).isCloseTo(-1.757 + 50 / 111320.0, within(1e-6));
    }

    @Test
    void recusaPoligonosInvalidos() {
        assertThat(GeometriaTalhao.validar(GeometriaTalhao.normalizar(List.of(List.of(-47.1, -1.7), List.of(-47.0, -1.7)))))
                .singleElement().asString().contains("pelo menos 3 pontos");
        // "Gravata": lados se cruzam
        List<double[]> gravata = GeometriaTalhao.normalizar(List.of(List.of(-47.10, -1.70), List.of(-47.09, -1.71),
                List.of(-47.09, -1.70), List.of(-47.10, -1.71)));
        assertThat(GeometriaTalhao.validar(gravata)).singleElement().asString().contains("se cruzam");
        assertThat(GeometriaTalhao.validar(GeometriaTalhao.normalizar(List.of(List.of(-47.1, -95.0),
                List.of(-47.0, -1.7), List.of(-47.0, -1.8))))).singleElement().asString().contains("latitude");
        assertThat(GeometriaTalhao.validar(GeometriaTalhao.normalizar(quadrado(-1.75, -47.12, 5))))
                .singleElement().asString().contains("muito pequena");
        assertThat(GeometriaTalhao.validar(GeometriaTalhao.normalizar(Arrays.asList(Arrays.asList(-47.1, null),
                List.of(-47.0, -1.7), List.of(-47.0, -1.8))))).singleElement().asString().contains("dois números");
        assertThat(GeometriaTalhao.validar(GeometriaTalhao.normalizar(quadrado(-1.75, -47.12, 300)))).isEmpty();
    }

    @Test
    void layoutIlustrativoRespeitaAreaCadastradaSemSobreposicao() {
        List<GeometriaTalhao.Lote> lotes = List.of(new GeometriaTalhao.Lote("A", 12.5), new GeometriaTalhao.Lote("B", 10.8),
                new GeometriaTalhao.Lote("C", 8.2), new GeometriaTalhao.Lote("D", 7.6));
        Map<String, List<double[]>> layout = GeometriaTalhao.layoutIlustrativo(lotes, -1.75685, -47.12646);
        assertThat(layout.keySet()).containsExactly("A", "B", "C", "D");
        for (GeometriaTalhao.Lote lote : lotes) {
            assertThat(GeometriaTalhao.areaHectares(layout.get(lote.id()))).isCloseTo(lote.areaHa(), within(0.1));
            assertThat(GeometriaTalhao.validar(layout.get(lote.id()))).isEmpty();
        }
        // Caixas nao se sobrepoem (carreadores de 30 m entre os talhoes)
        List<double[]> caixas = layout.values().stream().map(GeometriaTalhaoTest::caixa).toList();
        for (int i = 0; i < caixas.size(); i++) {
            for (int j = i + 1; j < caixas.size(); j++) {
                double[] a = caixas.get(i);
                double[] b = caixas.get(j);
                boolean separadas = a[2] < b[0] || b[2] < a[0] || a[3] < b[1] || b[3] < a[1];
                assertThat(separadas).as("talhões %d e %d separados", i, j).isTrue();
            }
        }
        // Conjunto centralizado no ponto de referencia (a menos de 150 m)
        double[] todos = caixa(layout.values().stream().flatMap(List::stream).toList());
        double lat = (todos[1] + todos[3]) / 2;
        double lng = (todos[0] + todos[2]) / 2;
        assertThat(GeometriaTalhao.distanciaKm(lat, lng, -1.75685, -47.12646)).isLessThan(0.15);
    }

    @Test
    void distanciaEntreAFazendaEASede() {
        assertThat(GeometriaTalhao.distanciaKm(-1.7447162, -47.0638495, -1.75685, -47.12646)).isCloseTo(7.1, within(0.1));
    }

    private static double[] caixa(List<double[]> anel) {
        double[] c = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : anel) {
            c[0] = Math.min(c[0], p[0]);
            c[1] = Math.min(c[1], p[1]);
            c[2] = Math.max(c[2], p[0]);
            c[3] = Math.max(c[3], p[1]);
        }
        return c;
    }
}
