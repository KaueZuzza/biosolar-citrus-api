package br.com.biosolar.citrus;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.biosolar.citrus.service.SimulacaoService;
import br.com.biosolar.citrus.simulation.SimuladorFazenda;

/** Testes ponta a ponta da API (H2 em memoria; ciclo automatico desligado para determinismo). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BioSolarApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimulacaoService simulacao;

    @Autowired
    private SimuladorFazenda simulador;

    @BeforeEach
    void restaurarCenario() {
        simulacao.restaurar();
    }

    private static String acionar(String talhao, boolean ligado) {
        return "{\"talhaoId\":\"" + talhao + "\",\"ligado\":" + ligado + "}";
    }

    @Test
    void telemetriaRetornaEstadoCompleto() throws Exception {
        mvc.perform(get("/telemetria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservatorio.nivel", is(67.0)))
                .andExpect(jsonPath("$.reservatorio.status", is("NORMAL")))
                .andExpect(jsonPath("$.talhoes", hasSize(4)))
                .andExpect(jsonPath("$.bombas", hasSize(4)))
                .andExpect(jsonPath("$.statusSistema.codigo", is("NORMAL")))
                .andExpect(jsonPath("$.decisao.titulo").exists())
                .andExpect(jsonPath("$.indice.valor").isNumber())
                .andExpect(jsonPath("$.eventosRecentes[0].titulo").exists());
    }

    @Test
    void comandoManualComReservatorioSeguroEhExecutado() throws Exception {
        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content(acionar("a", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso", is(true)))
                .andExpect(jsonPath("$.motivo", is("COMANDO_EXECUTADO")))
                .andExpect(jsonPath("$.talhao.aspersorLigado", is(true)))
                .andExpect(jsonPath("$.talhao.modoAcionamento", is("MANUAL")));
    }

    @Test
    void emergenciaDesligaBombasERecusaAcionamento() throws Exception {
        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content(acionar("B", true)))
                .andExpect(status().isOk());

        mvc.perform(post("/simulacao/emergencia")).andExpect(status().isOk());

        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.reservatorio.bloqueioEmergencia", is(true)))
                .andExpect(jsonPath("$.statusSistema.codigo", is("EMERGENCIA")))
                .andExpect(jsonPath("$.bombas[*].ligada", everyItem(is(false))))
                .andExpect(jsonPath("$.decisao.regra", is("P1")));

        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content(acionar("A", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sucesso", is(false)))
                .andExpect(jsonPath("$.motivo", is("BLOQUEIO_DE_EMERGENCIA")))
                .andExpect(jsonPath("$.mensagem", containsString("Acionamento bloqueado")))
                .andExpect(jsonPath("$.talhao.aspersorLigado", is(false)));
    }

    @Test
    void umidadeCriticaAcionaIrrigacaoNoServidor() throws Exception {
        mvc.perform(post("/simulacao/umidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talhaoId\":\"C\",\"delta\":-7}"))
                .andExpect(status().isOk());

        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes[2].id", is("C")))
                .andExpect(jsonPath("$.talhoes[2].status", is("CRITICO")))
                .andExpect(jsonPath("$.talhoes[2].aspersorLigado", is(true)))
                .andExpect(jsonPath("$.talhoes[2].modoAcionamento", is("AUTOMATICO")))
                .andExpect(jsonPath("$.decisao.regra", is("P2")));

        simulador.executarCiclo();
        simulador.executarCiclo();

        mvc.perform(get("/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pontos[0].umidades.C").isNumber());
    }

    @Test
    void requisicoesInvalidasRetornamErrosClaros() throws Exception {
        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo", is("REQUISICAO_INVALIDA")))
                .andExpect(jsonPath("$.detalhes", hasSize(2)));

        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content("{talhao"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo", is("JSON_INVALIDO")));

        mvc.perform(post("/bombas/acionar").contentType(MediaType.APPLICATION_JSON).content(acionar("Z", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.motivo", is("NAO_ENCONTRADO")));
    }

    @Test
    void endpointsAdicionaisRespondem() throws Exception {
        mvc.perform(get("/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.talhoesMonitorados", is(4)))
                .andExpect(jsonPath("$.resumo", containsString("Reservatório em 67 por cento")));
        mvc.perform(get("/eventos?limite=5")).andExpect(status().isOk());
        mvc.perform(get("/indicadores")).andExpect(status().isOk())
                .andExpect(jsonPath("$.acionamentos.porTalhao", hasSize(4)));
        mvc.perform(get("/saude")).andExpect(status().isOk())
                .andExpect(jsonPath("$.banco.status", is("UP")));
        mvc.perform(get("/relatorio")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoTexto", containsString("BioSolar Citrus")));
        mvc.perform(get("/relatorio/csv")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }
}
