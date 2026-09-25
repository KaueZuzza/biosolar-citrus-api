package br.com.biosolar.citrus;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

import br.com.biosolar.citrus.service.SimulacaoService;
import br.com.biosolar.citrus.service.assistente.AssistenteService;

/** Assistente "Citrus": respostas com os dados reais da fazenda e acoes sob as regras de seguranca. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistenteIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimulacaoService simulacao;

    @BeforeEach
    void restaurarCenario() {
        simulacao.restaurar();
    }

    private ResultActions perguntar(String texto) throws Exception {
        return mvc.perform(post("/assistente/comando").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"" + texto + "\"}"));
    }

    @Test
    void statusUsaOsDadosReais() throws Exception {
        perguntar("Citrus, quero o status.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entendido", is(true)))
                .andExpect(jsonPath("$.intencao", is("STATUS")))
                .andExpect(jsonPath("$.comando", is("quero o status")))
                .andExpect(jsonPath("$.resposta", startsWith("O reservatório está em 67%. Existem 4 talhões monitorados, 0 aspersores ativos e ")))
                .andExpect(jsonPath("$.fala", containsString("67 por cento")));
    }

    @Test
    void perguntasSobreCadaAssunto() throws Exception {
        perguntar("Como está o reservatório?")
                .andExpect(jsonPath("$.intencao", is("RESERVATORIO")))
                .andExpect(jsonPath("$.resposta", startsWith("O reservatório está em 67% (normal), com 536 de 800 m³.")));
        perguntar("Quais talhões estão críticos?")
                .andExpect(jsonPath("$.intencao", is("TALHOES_CRITICOS")))
                .andExpect(jsonPath("$.resposta", startsWith("Nenhum talhão está crítico agora.")))
                .andExpect(jsonPath("$.acao.alvo", is("talhoes")));
        perguntar("Tem algum alerta?").andExpect(jsonPath("$.intencao", is("ALERTAS")));
        perguntar("Como está a irrigação?")
                .andExpect(jsonPath("$.intencao", is("IRRIGACAO")))
                .andExpect(jsonPath("$.resposta", startsWith("Nenhum aspersor está ligado agora.")));
        perguntar("O que está acontecendo?").andExpect(jsonPath("$.intencao", is("O_QUE_ACONTECE")));
        perguntar("Gere um relatório.")
                .andExpect(jsonPath("$.intencao", is("RELATORIO")))
                .andExpect(jsonPath("$.acao.tipo", is("ABRIR_EXPORTACAO")))
                .andExpect(jsonPath("$.resposta", containsString("Abri a área de exportação")));
        perguntar("Relatório de hoje.").andExpect(jsonPath("$.intencao", is("RELATORIO")));
    }

    @Test
    void talhaoCriticoAparecenaResposta() throws Exception {
        mvc.perform(post("/simulacao/umidade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"talhaoId\":\"C\",\"delta\":-10}")).andExpect(status().isOk());
        perguntar("Quais talhões estão críticos?")
                .andExpect(jsonPath("$.resposta", startsWith("1 talhão crítico: Talhão C com 21%")));
    }

    @Test
    void mostraOTalhaoPedido() throws Exception {
        perguntar("Mostre o talhão C.")
                .andExpect(jsonPath("$.intencao", is("TALHAO")))
                .andExpect(jsonPath("$.acao.tipo", is("ABRIR_TALHAO")))
                .andExpect(jsonPath("$.acao.talhaoId", is("C")))
                .andExpect(jsonPath("$.resposta", startsWith("Talhão C (Limão Tahiti): umidade 31%")));
        // Como o reconhecimento de voz costuma transcrever a letra falada
        perguntar("mostre o talhão cê").andExpect(jsonPath("$.acao.talhaoId", is("C")));
        perguntar("Como está o talhão B agora?").andExpect(jsonPath("$.acao.talhaoId", is("B")));
        perguntar("mostre o talhão Z")
                .andExpect(jsonPath("$.intencao", is("TALHAO")))
                .andExpect(jsonPath("$.resposta", startsWith("Qual talhão? Os talhões ativos são A, B, C e D.")));
    }

    @Test
    void ligaEDesligaRespeitandoAsRegrasDeSeguranca() throws Exception {
        perguntar("Citrus, ligar o aspersor do talhão B")
                .andExpect(jsonPath("$.intencao", is("LIGAR_ASPERSOR")))
                .andExpect(jsonPath("$.executouAcao", is(true)))
                .andExpect(jsonPath("$.resposta", startsWith("Pronto.")));
        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes[1].aspersorLigado", is(true)))
                .andExpect(jsonPath("$.talhoes[1].modoAcionamento", is("MANUAL")));

        perguntar("desligue a bomba do talhão B")
                .andExpect(jsonPath("$.intencao", is("DESLIGAR_ASPERSOR")))
                .andExpect(jsonPath("$.executouAcao", is(true)));
        mvc.perform(get("/telemetria")).andExpect(jsonPath("$.talhoes[1].aspersorLigado", is(false)));

        // Com o bloqueio de emergencia, o assistente NAO consegue ligar nada (mesma regra do painel)
        mvc.perform(post("/simulacao/emergencia")).andExpect(status().isOk());
        perguntar("ligar aspersor do talhão A")
                .andExpect(jsonPath("$.executouAcao", is(false)))
                .andExpect(jsonPath("$.resposta", startsWith("Não posso fazer isso agora:")));
        mvc.perform(get("/telemetria")).andExpect(jsonPath("$.talhoes[0].aspersorLigado", is(false)));

        perguntar("ligar o aspersor")
                .andExpect(jsonPath("$.executouAcao", is(false)))
                .andExpect(jsonPath("$.resposta", startsWith("De qual talhão?")));
    }

    @Test
    void comandoDesconhecidoEEntradaInvalida() throws Exception {
        perguntar("qual a cotação do dólar hoje")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entendido", is(false)))
                .andExpect(jsonPath("$.intencao", is("DESCONHECIDO")))
                .andExpect(jsonPath("$.resposta", is(AssistenteService.NAO_ENTENDI)));
        perguntar("   ").andExpect(status().isBadRequest());
        mvc.perform(get("/assistente/exemplos"))
                .andExpect(jsonPath("$.perguntas[0]", is("Citrus, quero o status")));
    }
}
