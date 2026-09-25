package br.com.biosolar.citrus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.TalhaoAreaRepository;
import br.com.biosolar.citrus.service.SimulacaoService;
import br.com.biosolar.citrus.service.mapa.AgenteAgricolaService;

/**
 * Mapa da Fazenda: talhoes em GeoJSON vindos do banco, area desenhada gravada no PostgreSQL (H2 nos testes),
 * fontes publicas desligadas (sem internet nos testes) e o agente agricola.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MapaIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimulacaoService simulacao;

    @Autowired
    private TalhaoAreaRepository areaRepository;

    @Autowired
    private EventoRepository eventoRepository;

    @BeforeEach
    void restaurarCenario() {
        areaRepository.deleteAll();
        simulacao.restaurar();
    }

    @AfterEach
    void limparAreas() {
        areaRepository.deleteAll();
    }

    /** Quadrado de ~lado metros perto da fazenda, como o navegador envia: [[longitude, latitude], ...]. */
    private static String quadrado(double lado) {
        double lat = -1.7568;
        double lng = -47.1264;
        double dLat = lado / 111320.0;
        double dLng = lado / (111320.0 * Math.cos(Math.toRadians(lat)));
        return String.format(Locale.ROOT, "{\"coordenadas\":[[%f,%f],[%f,%f],[%f,%f],[%f,%f]]}",
                lng, lat, lng + dLng, lat, lng + dLng, lat + dLat, lng, lat + dLat);
    }

    private ResultActions perguntar(String json) throws Exception {
        return mvc.perform(post("/mapa/agente").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void talhoesDoBancoEmGeoJsonComPosicaoIlustrativa() throws Exception {
        mvc.perform(get("/mapa/talhoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", hasSize(4)))
                .andExpect(jsonPath("$.features[*].id", is(java.util.List.of("A", "B", "C", "D"))))
                .andExpect(jsonPath("$.features[0].type", is("Feature")))
                .andExpect(jsonPath("$.features[0].geometry.type", is("Polygon")))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", hasSize(5)))  // anel fechado
                .andExpect(jsonPath("$.features[*].properties.origemGeometria", everyItem(is("ILUSTRATIVA"))))
                .andExpect(jsonPath("$.features[2].properties.nome", is("Talhão C")))
                .andExpect(jsonPath("$.features[2].properties.areaCadastroHa", is(8.2)))
                .andExpect(jsonPath("$.features[2].properties.areaMapaHa", nullValue()))
                .andExpect(jsonPath("$.features[2].properties.umidade", is(31.0)))
                .andExpect(jsonPath("$.features[2].properties.solo", containsString("Neossolo")))
                .andExpect(jsonPath("$.municipio", is("Capitão Poço")))
                .andExpect(jsonPath("$.bancoDisponivel", is(true)))
                .andExpect(jsonPath("$.avisos[0]", containsString("posição ilustrativa")));
    }

    @Test
    void desenhaERemoveAAreaDoTalhao() throws Exception {
        String ilustrativoAntes = mvc.perform(get("/mapa/talhoes")).andReturn().getResponse().getContentAsString();

        mvc.perform(put("/mapa/talhoes/c/area").contentType(MediaType.APPLICATION_JSON).content(quadrado(286)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("C")))
                .andExpect(jsonPath("$.properties.origemGeometria", is("DESENHADA")))
                .andExpect(jsonPath("$.properties.areaMapaHa", closeTo(8.18, 0.1)))
                .andExpect(jsonPath("$.properties.areaAtualizadaEm", notNullValue()))
                .andExpect(jsonPath("$.geometry.coordinates[0]", hasSize(5)));

        assertThat(areaRepository.findById("C")).get().satisfies(a -> {
            assertThat(a.getVertices()).isEqualTo(4);
            assertThat(a.getAreaHa()).isBetween(8.0, 8.4);
        });
        assertThat(eventoRepository.findAllByOrderByIdDesc(PageRequest.of(0, 5)).stream().map(Evento::getTitulo))
                .contains("Área marcada no mapa: Talhão C");

        // Os outros talhoes continuam ilustrativos e na mesma posicao
        mvc.perform(get("/mapa/talhoes"))
                .andExpect(jsonPath("$.features[2].properties.origemGeometria", is("DESENHADA")))
                .andExpect(jsonPath("$.features[0].properties.origemGeometria", is("ILUSTRATIVA")))
                .andExpect(jsonPath("$.avisos[0]", startsWith("3 talhão(ões) ainda em posição ilustrativa")));
        String depois = mvc.perform(get("/mapa/talhoes")).andReturn().getResponse().getContentAsString();
        assertThat(trecho(depois, "\"id\":\"A\"")).isEqualTo(trecho(ilustrativoAntes, "\"id\":\"A\""));

        // Redesenhar substitui a area
        mvc.perform(put("/mapa/talhoes/C/area").contentType(MediaType.APPLICATION_JSON).content(quadrado(300)))
                .andExpect(jsonPath("$.properties.areaMapaHa", closeTo(9.0, 0.1)));
        assertThat(areaRepository.count()).isEqualTo(1);

        mvc.perform(delete("/mapa/talhoes/C/area"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties.origemGeometria", is("ILUSTRATIVA")));
        assertThat(areaRepository.findById("C")).isEmpty();
        mvc.perform(delete("/mapa/talhoes/C/area")).andExpect(status().isNotFound());
    }

    @Test
    void recusaAreaInvalidaOuTalhaoInexistente() throws Exception {
        mvc.perform(put("/mapa/talhoes/C/area").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordenadas\":[[-47.12,-1.75],[-47.11,-1.75]]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/mapa/talhoes/C/area").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordenadas\":[[-47.13,-1.75],[-47.12,-1.76],[-47.12,-1.75],[-47.13,-1.76]]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]", containsString("se cruzam")));
        mvc.perform(put("/mapa/talhoes/Z/area").contentType(MediaType.APPLICATION_JSON).content(quadrado(200)))
                .andExpect(status().isNotFound());
        assertThat(areaRepository.count()).isZero();
    }

    @Test
    void semInternetAsFontesPublicasDizemQueEstaoIndisponiveis() throws Exception {
        mvc.perform(get("/mapa/municipio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(false)))
                .andExpect(jsonPath("$.codigoIbge", is("1502301")))
                .andExpect(jsonPath("$.producao", nullValue()))
                .andExpect(jsonPath("$.contorno", nullValue()))
                .andExpect(jsonPath("$.aviso", containsString("desativada")));
        mvc.perform(get("/mapa/clima"))
                .andExpect(jsonPath("$.disponivel", is(false)))
                .andExpect(jsonPath("$.temperatura", nullValue()));
    }

    @Test
    void agenteAnalisaAFazendaSemInventarDados() throws Exception {
        perguntar("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema", is("GERAL")))
                .andExpect(jsonPath("$.titulo", is("Análise geral da fazenda")))
                .andExpect(jsonPath("$.resumo", containsString("reservatório em 67,0%")))
                .andExpect(jsonPath("$.itens[0].tipo", is("DADO")))
                .andExpect(jsonPath("$.itens[*].fonte", everyItem(notNullValue())))
                // Sem internet nos testes: nada de clima estimado, o agente diz que esta indisponivel
                .andExpect(jsonPath("$.itens[*].titulo", hasItem("Clima indisponível")))
                .andExpect(jsonPath("$.itens[*].titulo", not(hasItem("Demanda de água (FAO-56)"))))
                .andExpect(jsonPath("$.itens[*].titulo", hasItem("Talhões no mapa")));
    }

    @Test
    void agenteRespondePerguntasSobreUmTalhao() throws Exception {
        perguntar("{\"pergunta\":\"Qual o tipo de solo do talhão C?\"}")
                .andExpect(jsonPath("$.tema", is("SOLO")))
                .andExpect(jsonPath("$.talhaoId", is("C")))
                .andExpect(jsonPath("$.itens[1].tipo", is("ORIENTACAO")))
                .andExpect(jsonPath("$.itens[1].titulo", is("Neossolo Quartzarênico (arenoso)")));
        perguntar("{\"pergunta\":\"Preciso irrigar o talhão C?\"}")
                .andExpect(jsonPath("$.tema", is("IRRIGACAO")))
                .andExpect(jsonPath("$.talhaoId", is("C")))
                .andExpect(jsonPath("$.itens[0].tipo", is("DADO")))
                .andExpect(jsonPath("$.itens[1].tipo", is("ESTIMATIVA")))
                .andExpect(jsonPath("$.itens[1].titulo", is("Água para chegar ao alvo")))
                .andExpect(jsonPath("$.itens[1].texto", containsString("Base:")));
        // Talhao B (41%) ja esta acima do alvo de 40%: o agente diz que nao precisa de agua
        perguntar("{\"pergunta\":\"Preciso irrigar o talhão B?\"}")
                .andExpect(jsonPath("$.itens[1].titulo", is("Água necessária")))
                .andExpect(jsonPath("$.itens[1].texto", containsString("não precisa de irrigação")));
        perguntar("{\"talhaoId\":\"a\"}")
                .andExpect(jsonPath("$.tema", is("TALHAO")))
                .andExpect(jsonPath("$.titulo", is("Análise do Talhão A")))
                .andExpect(jsonPath("$.itens[*].titulo", hasItem("Área no mapa")))
                .andExpect(jsonPath("$.itens[*].tipo", hasItem("ESTIMATIVA")));
        perguntar("{\"talhaoId\":\"C\",\"tema\":\"CITROS\"}")
                .andExpect(jsonPath("$.tema", is("CITROS")))
                .andExpect(jsonPath("$.itens[*].titulo", hasItem("Variedade · Tahiti")));
    }

    @Test
    void perguntaNaoEntendidaOuTalhaoInexistente() throws Exception {
        perguntar("{\"pergunta\":\"xyz qwerty\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temaIdentificado", is(false)))
                .andExpect(jsonPath("$.tema", is("GERAL")))
                .andExpect(jsonPath("$.avisos", hasItem(AgenteAgricolaService.NAO_IDENTIFICADO)));
        perguntar("{\"talhaoId\":\"Z\"}")
                .andExpect(jsonPath("$.tema", is("GERAL")))
                .andExpect(jsonPath("$.avisos[0]", startsWith("Talhão Z não está entre os talhões ativos")));
        perguntar("{\"pergunta\":\"" + "a".repeat(301) + "\"}").andExpect(status().isBadRequest());
    }

    private static String trecho(String json, String inicio) {
        int i = json.indexOf(inicio);
        return json.substring(i, json.indexOf("\"properties\"", i));
    }
}
