package br.com.biosolar.citrus.service.mapa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculos geograficos do Mapa da Fazenda (WGS 84, coordenadas em graus, pontos como {longitude, latitude},
 * na ordem do GeoJSON). Sem dependencias externas: os talhoes tem poucos hectares, entao formulas esfericas
 * sao precisas o bastante (erro bem abaixo de 1%).
 */
public final class GeometriaTalhao {

    /** Raio equatorial do WGS 84 (m), o mesmo usado por Leaflet/Turf para area geodesica. */
    static final double RAIO_TERRA_M = 6378137.0;
    static final int MAXIMO_VERTICES = 200;
    static final double AREA_MINIMA_HA = 0.01;
    static final double AREA_MAXIMA_HA = 10000;
    /** Estradas internas entre os talhoes no layout ilustrativo (m). */
    private static final double CARREADOR_M = 30;
    private static final double METROS_POR_GRAU = 111320.0;

    /** Talhao para o layout ilustrativo: codigo e area cadastrada. */
    public record Lote(String id, double areaHa) {
    }

    private GeometriaTalhao() {
    }

    /**
     * Remove o ponto de fechamento repetido e pontos consecutivos iguais, arredondando para 7 casas
     * (~1 cm). Cada ponto deve ter exatamente [longitude, latitude].
     */
    public static List<double[]> normalizar(List<List<Double>> coordenadas) {
        List<double[]> anel = new ArrayList<>();
        for (List<Double> p : coordenadas) {
            if (p == null || p.size() != 2 || p.get(0) == null || p.get(1) == null) {
                anel.add(new double[] {Double.NaN, Double.NaN});
                continue;
            }
            double[] ponto = {arredondar(p.get(0), 7), arredondar(p.get(1), 7)};
            if (anel.isEmpty() || !igual(anel.get(anel.size() - 1), ponto)) {
                anel.add(ponto);
            }
        }
        if (anel.size() > 1 && igual(anel.get(0), anel.get(anel.size() - 1))) {
            anel.remove(anel.size() - 1);
        }
        return anel;
    }

    /** Problemas que impedem gravar o poligono (lista vazia = valido). */
    public static List<String> validar(List<double[]> anel) {
        List<String> erros = new ArrayList<>();
        for (double[] p : anel) {
            if (Double.isNaN(p[0]) || Double.isNaN(p[1])) {
                erros.add("coordenadas: cada ponto deve ser [longitude, latitude] com dois números");
                return erros;
            }
            if (p[0] < -180 || p[0] > 180 || p[1] < -90 || p[1] > 90) {
                erros.add("coordenadas: longitude entre -180 e 180 e latitude entre -90 e 90");
                return erros;
            }
        }
        if (anel.size() < 3) {
            erros.add("coordenadas: marque pelo menos 3 pontos diferentes (cantos do talhão)");
            return erros;
        }
        if (anel.size() > MAXIMO_VERTICES) {
            erros.add("coordenadas: no máximo " + MAXIMO_VERTICES + " pontos");
            return erros;
        }
        if (seCruza(anel)) {
            erros.add("coordenadas: os lados do polígono se cruzam; marque os cantos em sequência, contornando o talhão");
            return erros;
        }
        double area = areaHectares(anel);
        if (area < AREA_MINIMA_HA) {
            erros.add("coordenadas: área muito pequena (" + String.format(java.util.Locale.ROOT, "%.4f", area)
                    + " ha); o mínimo é " + AREA_MINIMA_HA + " ha");
        } else if (area > AREA_MAXIMA_HA) {
            erros.add("coordenadas: área muito grande (" + Math.round(area) + " ha); o máximo é "
                    + (int) AREA_MAXIMA_HA + " ha");
        }
        return erros;
    }

    /** Area geodesica (esferica) do poligono, em hectares. Mesma formula do Leaflet.GeometryUtil e do Turf. */
    public static double areaHectares(List<double[]> anel) {
        int n = anel.size();
        if (n < 3) {
            return 0;
        }
        double soma = 0;
        for (int i = 0; i < n; i++) {
            double[] p1 = anel.get(i);
            double[] p2 = anel.get((i + 1) % n);
            soma += Math.toRadians(p2[0] - p1[0])
                    * (2 + Math.sin(Math.toRadians(p1[1])) + Math.sin(Math.toRadians(p2[1])));
        }
        return Math.abs(soma * RAIO_TERRA_M * RAIO_TERRA_M / 2.0) / 10000.0;
    }

    /** Centroide do poligono ({longitude, latitude}); para poligonos degenerados, a media dos vertices. */
    public static double[] centro(List<double[]> anel) {
        double a = 0;
        double cx = 0;
        double cy = 0;
        int n = anel.size();
        for (int i = 0; i < n; i++) {
            double[] p1 = anel.get(i);
            double[] p2 = anel.get((i + 1) % n);
            double f = p1[0] * p2[1] - p2[0] * p1[1];
            a += f;
            cx += (p1[0] + p2[0]) * f;
            cy += (p1[1] + p2[1]) * f;
        }
        if (Math.abs(a) < 1e-15) {
            double sx = 0;
            double sy = 0;
            for (double[] p : anel) {
                sx += p[0];
                sy += p[1];
            }
            return new double[] {arredondar(sx / n, 7), arredondar(sy / n, 7)};
        }
        return new double[] {arredondar(cx / (3 * a), 7), arredondar(cy / (3 * a), 7)};
    }

    /** Distancia em km entre dois pontos (haversine). */
    public static double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371.0 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /**
     * Posicao ILUSTRATIVA dos talhoes que ainda nao tem area desenhada: quadrados com a area cadastrada, em
     * grade (ordem do cadastro) separados por carreadores de 30 m, centralizados no ponto de referencia.
     * Nunca e gravada no banco: serve apenas para o talhao aparecer no mapa ate a area real ser desenhada.
     */
    public static Map<String, List<double[]>> layoutIlustrativo(List<Lote> lotes, double latRef, double lngRef) {
        Map<String, List<double[]>> resultado = new LinkedHashMap<>();
        int n = lotes.size();
        if (n == 0) {
            return resultado;
        }
        int colunas = (int) Math.ceil(Math.sqrt(n));
        int linhas = (int) Math.ceil(n / (double) colunas);
        double[] larguraColuna = new double[colunas];
        double[] alturaLinha = new double[linhas];
        double[] lado = new double[n];
        for (int i = 0; i < n; i++) {
            lado[i] = Math.sqrt(Math.max(lotes.get(i).areaHa(), 0.01) * 10000);
            larguraColuna[i % colunas] = Math.max(larguraColuna[i % colunas], lado[i]);
            alturaLinha[i / colunas] = Math.max(alturaLinha[i / colunas], lado[i]);
        }
        double larguraTotal = soma(larguraColuna) + CARREADOR_M * (colunas - 1);
        double alturaTotal = soma(alturaLinha) + CARREADOR_M * (linhas - 1);
        double grausPorMetroLng = 1 / (METROS_POR_GRAU * Math.cos(Math.toRadians(latRef)));
        double grausPorMetroLat = 1 / METROS_POR_GRAU;

        for (int i = 0; i < n; i++) {
            int c = i % colunas;
            int l = i / colunas;
            // Canto da celula (x para leste, y para o sul a partir do canto noroeste do conjunto)
            double x0 = -larguraTotal / 2 + soma(larguraColuna, c) + CARREADOR_M * c + (larguraColuna[c] - lado[i]) / 2;
            double y0 = -alturaTotal / 2 + soma(alturaLinha, l) + CARREADOR_M * l + (alturaLinha[l] - lado[i]) / 2;
            double oeste = lngRef + x0 * grausPorMetroLng;
            double leste = lngRef + (x0 + lado[i]) * grausPorMetroLng;
            double norte = latRef - y0 * grausPorMetroLat;
            double sul = latRef - (y0 + lado[i]) * grausPorMetroLat;
            resultado.put(lotes.get(i).id(), List.of(
                    new double[] {arredondar(oeste, 6), arredondar(norte, 6)},
                    new double[] {arredondar(leste, 6), arredondar(norte, 6)},
                    new double[] {arredondar(leste, 6), arredondar(sul, 6)},
                    new double[] {arredondar(oeste, 6), arredondar(sul, 6)}));
        }
        return resultado;
    }

    /** Anel fechado (primeiro ponto repetido no fim), como exige o GeoJSON. */
    public static List<double[]> fechado(List<double[]> anel) {
        List<double[]> r = new ArrayList<>(anel);
        if (!anel.isEmpty()) {
            r.add(anel.get(0));
        }
        return r;
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean seCruza(List<double[]> anel) {
        int n = anel.size();
        for (int i = 0; i < n; i++) {
            double[] a1 = anel.get(i);
            double[] a2 = anel.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                // Lados vizinhos compartilham um vertice: nao contam como cruzamento
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) {
                    continue;
                }
                if (segmentosSeCruzam(a1, a2, anel.get(j), anel.get((j + 1) % n))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentosSeCruzam(double[] p1, double[] p2, double[] p3, double[] p4) {
        double d1 = orientacao(p3, p4, p1);
        double d2 = orientacao(p3, p4, p2);
        double d3 = orientacao(p1, p2, p3);
        double d4 = orientacao(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orientacao(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean igual(double[] a, double[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    private static double soma(double[] valores) {
        return soma(valores, valores.length);
    }

    private static double soma(double[] valores, int ate) {
        double s = 0;
        for (int i = 0; i < ate; i++) {
            s += valores[i];
        }
        return s;
    }

    static double arredondar(double v, int casas) {
        double f = Math.pow(10, casas);
        return Math.round(v * f) / f;
    }
}
