package br.com.biosolar.citrus.service.mapa;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.biosolar.citrus.config.MapaProperties;
import br.com.biosolar.citrus.dto.MapaDTO;
import br.com.biosolar.citrus.dto.MapaDTO.Clima;
import br.com.biosolar.citrus.dto.MapaDTO.Cultivo;
import br.com.biosolar.citrus.dto.MapaDTO.DiaClima;
import br.com.biosolar.citrus.dto.MapaDTO.Fonte;
import br.com.biosolar.citrus.dto.MapaDTO.Municipio;
import br.com.biosolar.citrus.dto.MapaDTO.ProducaoCitros;
import br.com.biosolar.citrus.dto.MapaDTO.RankingEstado;

/**
 * Dados reais de fontes publicas, consultados pelo servidor (o navegador nunca fala com elas diretamente):
 * <ul>
 *   <li><b>IBGE - Localidades</b>: nome, microrregiao, mesorregiao e regiao imediata do municipio;</li>
 *   <li><b>IBGE - Malhas territoriais</b>: contorno oficial e area territorial (km²);</li>
 *   <li><b>IBGE - SIDRA, PAM tabela 1613</b>: area colhida, producao, rendimento e valor da laranja e do limao;</li>
 *   <li><b>Open-Meteo</b>: tempo atual, chuva e evapotranspiracao de referencia (ET0, FAO-56) dos ultimos 7 dias
 *       e previsao para 3 dias.</li>
 * </ul>
 * Sem internet (ou com {@code biosolar.mapa.fontes-publicas=false}) a resposta sai "indisponivel", com o motivo:
 * nada e inventado. Os resultados ficam em cache (IBGE 24 h, clima 30 min) e, se uma consulta falhar, a
 * ultima resposta valida continua sendo usada, sinalizada.
 */
@Service
public class FontesPublicasService {

    private static final Logger log = LoggerFactory.getLogger(FontesPublicasService.class);

    private static final String IBGE = "https://servicodados.ibge.gov.br/api";
    private static final String OPEN_METEO = "https://api.open-meteo.com/v1/forecast";
    private static final String USER_AGENT = "BioSolarCitrus/1.0 (+https://github.com/KaueZuzza/biosolar-citrus-api)";
    /** PAM: codigos dos produtos na classificacao 82 (Produto das lavouras permanentes). */
    static final String LARANJA = "2733";
    static final String LIMAO = "2734";
    private static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(20);

    static final Fonte FONTE_LOCALIDADES = new Fonte("IBGE - Localidades",
            "Divisão territorial oficial (município, microrregião, mesorregião)",
            "https://servicodados.ibge.gov.br/api/docs/localidades");
    static final Fonte FONTE_MALHAS = new Fonte("IBGE - Malhas territoriais",
            "Contorno oficial e área territorial do município", "https://servicodados.ibge.gov.br/api/docs/malhas");
    static final Fonte FONTE_PAM = new Fonte("IBGE - Produção Agrícola Municipal (PAM), tabela 1613",
            "Lavouras permanentes: área colhida, produção, rendimento e valor", "https://sidra.ibge.gov.br/tabela/1613");
    static final Fonte FONTE_CLIMA = new Fonte("Open-Meteo",
            "Modelos meteorológicos: tempo atual, chuva e evapotranspiração de referência (ET0, FAO-56)",
            "https://open-meteo.com/");

    private final MapaProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http;
    private final Cache<Municipio> cacheMunicipio = new Cache<>();
    private final Cache<Clima> cacheClima = new Cache<>();

    public FontesPublicasService(MapaProperties props, ObjectMapper mapper, Clock clock) {
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, props.timeoutSegundos())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // =============================================================================================
    // Municipio (IBGE)
    // =============================================================================================

    public Municipio municipio() {
        if (!props.fontesPublicas()) {
            return municipioIndisponivel("Consulta às fontes públicas desativada nesta instalação "
                    + "(biosolar.mapa.fontes-publicas=false).");
        }
        return cacheMunicipio.obter(clock, this::buscarMunicipio,
                m -> Duration.ofHours(m.producao() == null || m.contorno() == null ? 0 : props.cacheIbgeHoras())
                        .plusMinutes(m.producao() == null || m.contorno() == null ? 10 : 0),
                this::municipioIndisponivel,
                (m, aviso) -> comAviso(m, aviso));
    }

    private Municipio buscarMunicipio() {
        String cod = props.municipioIbge();
        List<String> falhas = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Fonte> fontes = new ArrayList<>();

        // As 5 consultas sao independentes: em paralelo, o pior caso e um unico tempo limite
        var fLoc = assincrono(() -> get(IBGE + "/v1/localidades/municipios/" + cod), "localidades", falhas);
        var fMeta = assincrono(() -> get(IBGE + "/v3/malhas/municipios/" + cod + "/metadados"), "metadados", falhas);
        var fMalha = assincrono(() -> get(IBGE + "/v3/malhas/municipios/" + cod
                + "?formato=application/vnd.geo%2Bjson&qualidade=intermediaria"), "malha", falhas);
        var fPam = assincrono(() -> get(IBGE + "/v3/agregados/1613/periodos/-1/variaveis/216%7C214%7C112%7C215"
                + "?localidades=N6%5B" + cod + "%5D&classificacao=82%5B" + LARANJA + "," + LIMAO + "%5D"), "PAM", falhas);
        var fRanking = assincrono(() -> get(IBGE + "/v3/agregados/1613/periodos/-1/variaveis/214"
                + "?localidades=N6%5BN3%5B" + props.estadoIbge() + "%5D%5D&classificacao=82%5B" + LARANJA + "%5D"),
                "PAM ranking", falhas);

        String nome = props.municipioNome();
        String uf = props.uf();
        String micro = null;
        String meso = null;
        String imediata = null;
        JsonNode loc = fLoc.join();
        if (loc != null && loc.hasNonNull("nome")) {
            nome = loc.path("nome").asText(nome);
            micro = texto(loc.path("microrregiao").path("nome"));
            meso = texto(loc.path("microrregiao").path("mesorregiao").path("nome"));
            String sigla = texto(loc.path("microrregiao").path("mesorregiao").path("UF").path("sigla"));
            uf = sigla != null ? sigla : uf;
            imediata = texto(loc.path("regiao-imediata").path("nome"));
            fontes.add(FONTE_LOCALIDADES);
        }

        Double areaKm2 = null;
        JsonNode meta = fMeta.join();
        if (meta != null && meta.isArray() && !meta.isEmpty()) {
            areaKm2 = numero(meta.get(0).path("area").path("dimensao").asText(null));
        }
        JsonNode malha = fMalha.join();
        JsonNode contorno = null;
        if (malha != null && malha.path("features").isArray() && !malha.path("features").isEmpty()) {
            contorno = malha.path("features").get(0).path("geometry");
            if (contorno.isMissingNode()) {
                contorno = null;
            }
        }
        if (areaKm2 != null || contorno != null) {
            fontes.add(FONTE_MALHAS);
        }

        ProducaoCitros producao = null;
        JsonNode pam = fPam.join();
        JsonNode ranking = fRanking.join();
        if (pam != null) {
            producao = lerProducao(pam, ranking, cod);
            if (producao != null) {
                fontes.add(FONTE_PAM);
            }
        }

        boolean algum = loc != null || meta != null || contorno != null || producao != null;
        if (!algum) {
            throw new IllegalStateException(String.join("; ", falhas));
        }
        String aviso = falhas.isEmpty() ? null
                : "Parte dos dados públicos não pôde ser consultada agora (" + String.join(", ", falhas) + ").";
        return new Municipio(true, cod, nome, uf, micro, meso, imediata, areaKm2,
                new MapaDTO.Ponto(props.sedeLatitude(), props.sedeLongitude()), contorno, producao, fontes,
                clock.instant(), aviso);
    }

    private Municipio municipioIndisponivel(String motivo) {
        return new Municipio(false, props.municipioIbge(), props.municipioNome(), props.uf(), null, null, null, null,
                new MapaDTO.Ponto(props.sedeLatitude(), props.sedeLongitude()), null, null, List.of(), clock.instant(),
                motivo);
    }

    private static Municipio comAviso(Municipio m, String aviso) {
        return new Municipio(m.disponivel(), m.codigoIbge(), m.nome(), m.uf(), m.microrregiao(), m.mesorregiao(),
                m.regiaoImediata(), m.areaKm2(), m.sede(), m.contorno(), m.producao(), m.fontes(), m.consultadoEm(), aviso);
    }

    /** PAM (SIDRA v3): producao de laranja e limao do municipio e posicao do municipio no estado (laranja). */
    static ProducaoCitros lerProducao(JsonNode pam, JsonNode ranking, String codigoMunicipio) {
        String ano = null;
        Map<String, Double[]> valores = new java.util.HashMap<>();  // produto -> [area, producao, rendimento, valor]
        Map<String, Boolean> traco = new java.util.HashMap<>();
        for (JsonNode variavel : pam) {
            int indice = switch (variavel.path("id").asText()) {
                case "216" -> 0;
                case "214" -> 1;
                case "112" -> 2;
                case "215" -> 3;
                default -> -1;
            };
            if (indice < 0) {
                continue;
            }
            for (JsonNode resultado : variavel.path("resultados")) {
                String produto = codigoCategoria(resultado);
                for (JsonNode serie : resultado.path("series")) {
                    for (Map.Entry<String, JsonNode> e : serie.path("serie").properties()) {
                        ano = e.getKey();
                        String bruto = e.getValue().asText("");
                        valores.computeIfAbsent(produto, k -> new Double[4])[indice] = valorSidra(bruto);
                        if (indice == 1) {
                            traco.put(produto, "-".equals(bruto.trim()));
                        }
                    }
                }
            }
        }
        if (ano == null) {
            return null;
        }
        return new ProducaoCitros(ano, cultivo("Laranja", valores.get(LARANJA), traco.get(LARANJA)),
                cultivo("Limão", valores.get(LIMAO), traco.get(LIMAO)), lerRanking(ranking, codigoMunicipio, "Laranja"));
    }

    static RankingEstado lerRanking(JsonNode ranking, String codigoMunicipio, String produto) {
        if (ranking == null || !ranking.isArray() || ranking.isEmpty()) {
            return null;
        }
        record Linha(String id, double t) {
        }
        List<Linha> linhas = new ArrayList<>();
        for (JsonNode resultado : ranking.get(0).path("resultados")) {
            for (JsonNode serie : resultado.path("series")) {
                String id = serie.path("localidade").path("id").asText();
                Iterator<JsonNode> v = serie.path("serie").elements();
                Double t = v.hasNext() ? valorSidra(v.next().asText("")) : null;
                if (t != null && t > 0) {
                    linhas.add(new Linha(id, t));
                }
            }
        }
        linhas.sort(Comparator.comparingDouble(Linha::t).reversed());
        double total = linhas.stream().mapToDouble(Linha::t).sum();
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).id().equals(codigoMunicipio)) {
                return new RankingEstado(produto, i + 1, linhas.size(),
                        GeometriaTalhao.arredondar(linhas.get(i).t() / total * 100, 1), total);
            }
        }
        return null;
    }

    private static Cultivo cultivo(String nome, Double[] v, Boolean semProducao) {
        if (v == null) {
            return null;
        }
        return new Cultivo(nome, v[0], v[1], v[2], v[3], Boolean.TRUE.equals(semProducao));
    }

    private static String codigoCategoria(JsonNode resultado) {
        for (JsonNode c : resultado.path("classificacoes")) {
            Iterator<String> nomes = c.path("categoria").fieldNames();
            if (nomes.hasNext()) {
                return nomes.next();
            }
        }
        return "";
    }

    /** Valores do SIDRA: numero; "-" = zero absoluto; "...", "..", "X" (sigilo) = nao disponivel. */
    static Double valorSidra(String bruto) {
        String v = bruto == null ? "" : bruto.trim();
        if ("-".equals(v)) {
            return 0.0;
        }
        return numero(v);
    }

    // =============================================================================================
    // Clima (Open-Meteo)
    // =============================================================================================

    public Clima clima() {
        if (!props.fontesPublicas()) {
            return climaIndisponivel("Consulta às fontes públicas desativada nesta instalação "
                    + "(biosolar.mapa.fontes-publicas=false).");
        }
        return cacheClima.obter(clock, this::buscarClima, c -> Duration.ofMinutes(props.cacheClimaMinutos()),
                this::climaIndisponivel, FontesPublicasService::comAviso);
    }

    private Clima buscarClima() {
        String url = OPEN_METEO + "?latitude=" + props.fazendaLatitude() + "&longitude=" + props.fazendaLongitude()
                + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code"
                + "&daily=precipitation_sum,precipitation_probability_max,et0_fao_evapotranspiration,"
                + "temperature_2m_max,temperature_2m_min&past_days=7&forecast_days=3&timezone=America%2FBelem";
        try {
            return lerClima(get(url), clock.instant());
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static Clima lerClima(JsonNode r, Instant agora) {
        JsonNode atual = r.path("current");
        String horario = texto(atual.path("time"));
        String hoje = horario != null && horario.length() >= 10 ? horario.substring(0, 10) : null;
        JsonNode d = r.path("daily");
        JsonNode datas = d.path("time");
        List<DiaClima> dias = new ArrayList<>();
        double chuvaPassada = 0;
        double et0Passada = 0;
        int diasEt0 = 0;
        int diasChuva = 0;
        double chuvaPrevista = 0;
        int diasPrevistos = 0;
        Double et0Hoje = null;
        for (int i = 0; i < datas.size(); i++) {
            String data = datas.get(i).asText();
            boolean previsao = hoje == null || data.compareTo(hoje) >= 0;
            Double chuva = numeroNo(d.path("precipitation_sum").path(i));
            Double et0 = numeroNo(d.path("et0_fao_evapotranspiration").path(i));
            Double pMax = numeroNo(d.path("precipitation_probability_max").path(i));
            dias.add(new DiaClima(data, chuva, pMax == null ? null : (int) Math.round(pMax), et0,
                    numeroNo(d.path("temperature_2m_max").path(i)), numeroNo(d.path("temperature_2m_min").path(i)),
                    previsao));
            if (!previsao) {
                if (chuva != null) {
                    chuvaPassada += chuva;
                    diasChuva++;
                }
                if (et0 != null) {
                    et0Passada += et0;
                    diasEt0++;
                }
            } else {
                if (chuva != null) {
                    chuvaPrevista += chuva;
                    diasPrevistos++;
                }
                if (data.equals(hoje)) {
                    et0Hoje = et0;
                }
            }
        }
        Integer codigo = atual.hasNonNull("weather_code") ? atual.path("weather_code").asInt() : null;
        return new Clima(true, horario, numeroNo(atual.path("temperature_2m")),
                numeroNo(atual.path("relative_humidity_2m")), numeroNo(atual.path("precipitation")),
                numeroNo(atual.path("wind_speed_10m")), codigo, codigo == null ? null : descricaoTempo(codigo),
                diasChuva > 0 ? GeometriaTalhao.arredondar(chuvaPassada, 1) : null,
                diasEt0 > 0 ? GeometriaTalhao.arredondar(et0Passada / diasEt0, 1) : null,
                diasPrevistos > 0 ? GeometriaTalhao.arredondar(chuvaPrevista, 1) : null, et0Hoje, dias, FONTE_CLIMA,
                agora, null);
    }

    private Clima climaIndisponivel(String motivo) {
        return new Clima(false, null, null, null, null, null, null, null, null, null, null, null, List.of(), FONTE_CLIMA,
                clock.instant(), motivo);
    }

    private static Clima comAviso(Clima c, String aviso) {
        return new Clima(c.disponivel(), c.horarioLocal(), c.temperatura(), c.umidadeAr(), c.precipitacao(),
                c.ventoKmh(), c.codigoTempo(), c.descricaoTempo(), c.chuvaUltimos7DiasMm(), c.et0Media7DiasMm(),
                c.chuvaPrevista3DiasMm(), c.et0HojeMm(), c.dias(), c.fonte(), c.consultadoEm(), aviso);
    }

    /** Codigos de tempo da OMM (WMO) usados pelo Open-Meteo. */
    static String descricaoTempo(int codigo) {
        return switch (codigo) {
            case 0 -> "Céu limpo";
            case 1 -> "Predominantemente limpo";
            case 2 -> "Parcialmente nublado";
            case 3 -> "Encoberto";
            case 45, 48 -> "Nevoeiro";
            case 51, 53, 55, 56, 57 -> "Garoa";
            case 61 -> "Chuva fraca";
            case 63 -> "Chuva moderada";
            case 65, 66, 67 -> "Chuva forte";
            case 80 -> "Pancadas de chuva fracas";
            case 81 -> "Pancadas de chuva";
            case 82 -> "Pancadas de chuva fortes";
            case 95 -> "Trovoada";
            case 96, 99 -> "Trovoada com granizo";
            default -> codigo >= 71 && codigo <= 86 ? "Neve" : "Condição não identificada";
        };
    }

    // =============================================================================================
    // HTTP, cache e conversoes
    // =============================================================================================

    private JsonNode get(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(2, props.timeoutSegundos())))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        try {
            HttpResponse<byte[]> resp;
            try {
                resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
                // Falha momentanea de conexao (comum logo apos a rede acordar): uma nova tentativa
                resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            }
            if (resp.statusCode() != 200) {
                throw new IOException("HTTP " + resp.statusCode() + " em " + req.uri().getHost());
            }
            return mapper.readTree(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("consulta interrompida", e);
        }
    }

    @FunctionalInterface
    private interface Consulta {
        JsonNode executar() throws IOException;
    }

    private static java.util.concurrent.CompletableFuture<JsonNode> assincrono(Consulta consulta, String nome,
                                                                               List<String> falhas) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> tentar(consulta, nome, falhas));
    }

    private static JsonNode tentar(Consulta consulta, String nome, List<String> falhas) {
        try {
            return consulta.executar();
        } catch (IOException | RuntimeException e) {
            log.warn("Fonte pública indisponível ({}): {}", nome, e.getMessage());
            falhas.add(nome);
            return null;
        }
    }

    private static String texto(JsonNode no) {
        return no == null || no.isMissingNode() || no.isNull() ? null : no.asText();
    }

    private static Double numeroNo(JsonNode no) {
        return no == null || no.isMissingNode() || no.isNull() || !no.isNumber() ? null : no.asDouble();
    }

    private static Double numero(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Cache simples com validade, reaproveitamento da ultima resposta boa e pausa apos falha. */
    private static final class Cache<T> {
        private T valor;
        private Instant validoAte = Instant.MIN;
        private Instant pausaAte = Instant.MIN;

        synchronized T obter(Clock clock, Supplier<T> buscar, Function<T, Duration> validade,
                             Function<String, T> indisponivel, java.util.function.BiFunction<T, String, T> comAviso) {
            Instant agora = clock.instant();
            if (valor != null && agora.isBefore(validoAte)) {
                return valor;
            }
            if (agora.isBefore(pausaAte)) {
                return reserva(indisponivel, comAviso, "Sem resposta da fonte pública há pouco; nova tentativa em instantes.");
            }
            try {
                T novo = buscar.get();
                valor = novo;
                validoAte = agora.plus(validade.apply(novo));
                return novo;
            } catch (RuntimeException e) {
                log.warn("Fonte pública indisponível: {}", e.getMessage());
                pausaAte = agora.plus(ESPERA_APOS_FALHA);
                return reserva(indisponivel, comAviso, "Fonte pública indisponível agora (sem internet ou serviço fora do ar).");
            }
        }

        private T reserva(Function<String, T> indisponivel, java.util.function.BiFunction<T, String, T> comAviso,
                          String motivo) {
            return valor != null ? comAviso.apply(valor, motivo + " Exibindo a última consulta bem-sucedida.")
                    : indisponivel.apply(motivo);
        }
    }
}
