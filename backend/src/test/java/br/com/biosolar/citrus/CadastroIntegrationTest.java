package br.com.biosolar.citrus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.biosolar.citrus.dto.ConfiguracaoDTO;
import br.com.biosolar.citrus.service.CadastroService;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.SimulacaoService;
import br.com.biosolar.citrus.simulation.SimuladorFazenda;

/** CRUD pela API: Frontend -> API -> banco, e o cadastro participando da automacao. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroIntegrationTest {

    private static final List<String> CODIGOS_DE_TESTE = List.of("E", "F", "G");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimulacaoService simulacao;

    @Autowired
    private SimuladorFazenda simulador;

    @Autowired
    private EstadoFazendaService estado;

    @Autowired
    private CadastroService cadastro;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void restaurarCenario() {
        simulacao.restaurar();
    }

    /** Devolve o banco ao cenario original para nao afetar os demais testes (mesmo contexto e mesmo H2). */
    @AfterEach
    void limpar() {
        for (String codigo : CODIGOS_DE_TESTE) {
            Boolean ativo = jdbc.query("select ativo from talhao where id = ?", rs -> rs.next() ? rs.getBoolean(1) : null,
                    codigo);
            if (ativo == null) {
                continue;
            }
            if (ativo) {
                cadastro.arquivarTalhao(codigo);
            }
            cadastro.excluirTalhaoDefinitivamente(codigo);
        }
        jdbc.update("update talhao set nome = 'Talhão A', umidade_alvo = 40 where id = 'A'");
        estado.sincronizarComBanco();
        cadastro.atualizarReservatorio(new ConfiguracaoDTO.ReservatorioRequest("Reservatório Central R-01", 800.0, 8.0, 67.0));
        simulacao.restaurar();
    }

    private static String talhao(String id, double umidadeInicial) {
        return """
                {"id":"%s","nome":"Talhão %s","cultura":"LIMAO","variedade":"Tahiti","solo":"Latossolo Amarelo",
                 "areaHa":5.5,"plantas":2200,"prioridade":1,"umidadeInicial":%s,"limiteAtencao":30,"umidadeAlvo":40,
                 "taxaEvapotranspiracao":1.5,"ganhoIrrigacao":7,"vazaoBombaM3h":18,"potenciaBombaKw":5.5}
                """.formatted(id, id, umidadeInicial);
    }

    @Test
    void talhaoCadastradoEntraNaAutomacao() throws Exception {
        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(talhao("e", 45)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("E")))
                .andExpect(jsonPath("$.bombaId", is("MB-E")))
                .andExpect(jsonPath("$.ativo", is(true)));

        // Gravado no banco
        assertThat(jdbc.queryForObject("select count(*) from talhao where id = 'E' and ativo", Integer.class)).isEqualTo(1);

        // Participa da telemetria e da automacao: umidade critica liga o aspersor (P2)
        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes", hasSize(5)))
                .andExpect(jsonPath("$.bombas[*].id", hasItem("MB-E")));
        mvc.perform(post("/simulacao/umidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talhaoId\":\"E\",\"delta\":-25}"))
                .andExpect(status().isOk());
        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes[4].id", is("E")))
                .andExpect(jsonPath("$.talhoes[4].status", is("CRITICO")))
                .andExpect(jsonPath("$.talhoes[4].aspersorLigado", is(true)))
                .andExpect(jsonPath("$.talhoes[4].modoAcionamento", is("AUTOMATICO")));

        // O estado operacional tambem e gravado no banco
        assertThat(jdbc.queryForObject("select aspersor_ligado from talhao where id = 'E'", Boolean.class)).isTrue();
    }

    @Test
    void dadosInvalidosEConflitosSaoRecusados() throws Exception {
        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(talhao("A", 40)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo", is("CONFLITO")));

        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(talhao("TALHAO NORTE", 40)))
                .andExpect(status().isBadRequest());

        String alvoAbaixoDaAtencao = talhao("F", 40).replace("\"umidadeAlvo\":40", "\"umidadeAlvo\":28");
        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(alvoAbaixoDaAtencao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]", org.hamcrest.Matchers.containsString("umidadeAlvo")));

        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"F\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo", is("REQUISICAO_INVALIDA")));

        mvc.perform(get("/talhoes/Z")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from talhao where id = 'F'", Integer.class)).isZero();
    }

    @Test
    void edicaoValeNaAutomacaoENoBanco() throws Exception {
        String edicao = talhao("A", 62).replace("\"umidadeAlvo\":40", "\"umidadeAlvo\":45")
                .replace("Talhão A", "Talhão A - Norte");
        mvc.perform(put("/talhoes/A").contentType(MediaType.APPLICATION_JSON).content(edicao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umidadeAlvo", is(45.0)))
                .andExpect(jsonPath("$.limiteCritico", is(25.0)));

        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes[0].nome", is("Talhão A - Norte")))
                .andExpect(jsonPath("$.talhoes[0].umidadeAlvo", is(45.0)));
        assertThat(jdbc.queryForObject("select umidade_alvo from talhao where id = 'A'", Double.class)).isEqualTo(45.0);
    }

    @Test
    void exclusaoArquivaEReativacaoDevolveAOperacao() throws Exception {
        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(talhao("F", 50)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/talhoes/F")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo", is(false)));
        mvc.perform(get("/telemetria")).andExpect(jsonPath("$.talhoes[*].id", not(hasItem("F"))));
        mvc.perform(get("/talhoes")).andExpect(jsonPath("$[?(@.id == 'F')].ativo", hasItem(false)));

        mvc.perform(post("/talhoes/F/reativar")).andExpect(status().isOk());
        mvc.perform(get("/telemetria")).andExpect(jsonPath("$.talhoes[*].id", hasItem("F")));

        // Exclusao definitiva so para arquivados
        mvc.perform(delete("/talhoes/F/definitivo")).andExpect(status().isConflict());
        mvc.perform(delete("/talhoes/F")).andExpect(status().isOk());
        mvc.perform(delete("/talhoes/F/definitivo")).andExpect(status().isNoContent());
        mvc.perform(get("/talhoes/F")).andExpect(status().isNotFound());
    }

    @Test
    void ultimoTalhaoAtivoNaoPodeSerExcluido() throws Exception {
        mvc.perform(delete("/talhoes/A")).andExpect(status().isOk());
        mvc.perform(delete("/talhoes/B")).andExpect(status().isOk());
        mvc.perform(delete("/talhoes/C")).andExpect(status().isOk());
        mvc.perform(delete("/talhoes/D")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("pelo menos um talhão")));

        for (String id : List.of("A", "B", "C")) {
            mvc.perform(post("/talhoes/" + id + "/reativar")).andExpect(status().isOk());
        }
        mvc.perform(get("/telemetria")).andExpect(jsonPath("$.talhoes", hasSize(4)));
    }

    @Test
    void alteracaoFeitaDiretamenteNoBancoChegaAAutomacao() throws Exception {
        // Simula uma edicao pelo pgAdmin
        jdbc.update("update talhao set nome = 'Talhão A (pgAdmin)', umidade_alvo = 42 where id = 'A'");
        assertThat(estado.sincronizarComBanco()).isEqualTo(1);

        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.talhoes[0].nome", is("Talhão A (pgAdmin)")))
                .andExpect(jsonPath("$.talhoes[0].umidadeAlvo", is(42.0)));

        // O ciclo da automacao grava so o estado operacional: a edicao feita no banco nao e sobrescrita
        simulador.executarCiclo();
        assertThat(jdbc.queryForObject("select nome from talhao where id = 'A'", String.class))
                .isEqualTo("Talhão A (pgAdmin)");
        assertThat(estado.sincronizarComBanco()).isZero();
    }

    @Test
    void restaurarCenarioUsaOsNiveisCadastrados() throws Exception {
        mvc.perform(post("/talhoes").contentType(MediaType.APPLICATION_JSON).content(talhao("G", 55)))
                .andExpect(status().isCreated());
        mvc.perform(put("/configuracao/reservatorio").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Reservatório Central R-01\",\"capacidadeM3\":800,\"vazaoRecargaM3h\":8,\"nivelInicial\":72}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservatorio.nivelInicial", is(72.0)));
        mvc.perform(post("/simulacao/umidade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"talhaoId\":\"G\",\"delta\":-20}"));

        mvc.perform(post("/simulacao/restaurar")).andExpect(status().isOk());

        mvc.perform(get("/telemetria"))
                .andExpect(jsonPath("$.reservatorio.nivel", is(72.0)))
                .andExpect(jsonPath("$.talhoes", hasSize(5)))
                .andExpect(jsonPath("$.talhoes[4].id", is("G")))
                .andExpect(jsonPath("$.talhoes[4].umidade", is(55.0)))
                .andExpect(jsonPath("$.talhoes[0].umidade", is(62.0)));
    }

    @Test
    void configuracaoValidaOsDados() throws Exception {
        mvc.perform(get("/configuracao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservatorio.limiteCritico", is(15.0)))
                .andExpect(jsonPath("$.regras.maximoTalhoes", is(8)))
                .andExpect(jsonPath("$.bancoDisponivel", is(true)));
        mvc.perform(put("/configuracao/usina").contentType(MediaType.APPLICATION_JSON).content("{\"potenciaSolarPicoKw\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/configuracao/usina").contentType(MediaType.APPLICATION_JSON).content("{\"potenciaSolarPicoKw\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usina.potenciaSolarPicoKw", is(45.0)));
    }
}
