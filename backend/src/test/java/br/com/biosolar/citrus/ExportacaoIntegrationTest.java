package br.com.biosolar.citrus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.service.SimulacaoService;
import br.com.biosolar.citrus.service.exportacao.RelatorioExcelService;
import br.com.biosolar.citrus.simulation.SimuladorFazenda;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/**
 * Exportacoes geradas pelo servidor: PDF, Excel, mensagem do WhatsApp e envio por e-mail (SMTP em memoria
 * do GreenMail, sem credenciais reais).
 */
@SpringBootTest(properties = {
    "biosolar.email.host=localhost",
    "biosolar.email.porta=3025",
    "biosolar.email.starttls=false",
    "biosolar.email.ssl=false",
    // Isola o teste do .env da maquina: sem isso, um SMTP real configurado la mandaria usuario/senha ao GreenMail
    "biosolar.email.usuario=",
    "biosolar.email.senha=",
    "biosolar.email.nome-remetente=BioSolar Citrus",
    "biosolar.email.remetente=relatorios@biosolar.test",
    "biosolar.email.limite-por-hora=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportacaoIntegrationTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SimulacaoService simulacao;

    @Autowired
    private SimuladorFazenda simulador;

    @Autowired
    private EventoRepository eventoRepository;

    @BeforeEach
    void cenarioComHistorico() throws Exception {
        simulacao.restaurar();
        // Um talhao critico gera irrigacao automatica, eventos e leituras para os graficos e planilhas
        mvc.perform(post("/simulacao/umidade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"talhaoId\":\"C\",\"delta\":-10}")).andExpect(status().isOk());
        for (int i = 0; i < 12; i++) {
            simulador.executarCiclo();
        }
    }

    @Test
    void pdfTemCapaSecoesEPaginacao() throws Exception {
        byte[] pdf = mvc.perform(get("/exportacao/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", startsWith("inline; filename=\"biosolar-relatorio-")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader leitor = new PdfReader(pdf);
        int paginas = leitor.getNumberOfPages();
        assertThat(paginas).isBetween(2, 5);
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        StringBuilder texto = new StringBuilder();
        for (int p = 1; p <= paginas; p++) {
            String pagina = extrator.getTextFromPage(p);
            assertThat(pagina).contains("Página " + p + " de " + paginas);
            texto.append(pagina).append('\n');
        }
        assertThat(texto.toString())
                .contains("RELATÓRIO OPERACIONAL", "BioSolar Citrus", "Resumo dos principais indicadores",
                        "Situação do reservatório", "Situação dos talhões", "Irrigação e acionamentos",
                        "Alertas ativos", "Eventos importantes", "Talhão C");

        mvc.perform(get("/exportacao/pdf").param("download", "true"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));
    }

    @Test
    void excelTemAbasOrganizadasComFiltrosEFormatos() throws Exception {
        byte[] xlsx = mvc.perform(get("/exportacao/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            List<String> abas = new ArrayList<>();
            wb.forEach(s -> abas.add(s.getSheetName()));
            assertThat(abas).containsExactlyElementsOf(RelatorioExcelService.ABAS);

            for (String nome : List.of("Talhões", "Reservatório", "Irrigação", "Eventos", "Histórico")) {
                XSSFSheet aba = wb.getSheet(nome);
                assertThat(aba.getTables()).as("tabela com filtro em %s", nome).isNotEmpty();
                assertThat(aba.getTables().get(0).getCTTable().getAutoFilter()).isNotNull();
                assertThat(aba.getPaneInformation().isFreezePane()).as("cabeçalho congelado em %s", nome).isTrue();
                assertThat(aba.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 3);
            }

            XSSFSheet talhoes = wb.getSheet("Talhões");
            XSSFTable tabela = talhoes.getTables().get(0);
            assertThat(tabela.getEndCellReference().getRow() - tabela.getStartCellReference().getRow()).isEqualTo(4);
            Row cabecalho = talhoes.getRow(2);
            assertThat(cabecalho.getCell(8).getStringCellValue()).isEqualTo("Umidade atual");
            Row a = talhoes.getRow(3);
            assertThat(a.getCell(0).getStringCellValue()).isEqualTo("A");
            Cell umidade = a.getCell(8);
            assertThat(umidade.getNumericCellValue()).isBetween(0.0, 1.0);
            assertThat(umidade.getCellStyle().getDataFormatString()).isEqualTo("0.0%");

            XSSFSheet eventos = wb.getSheet("Eventos");
            Cell data = eventos.getRow(3).getCell(0);
            assertThat(DateUtil.isCellDateFormatted(data)).isTrue();

            XSSFSheet historico = wb.getSheet("Histórico");
            assertThat(historico.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Umidade A");
            assertThat(historico.getLastRowNum()).isGreaterThanOrEqualTo(4);
        }
    }

    @Test
    void mensagemDoWhatsappEhResumida() throws Exception {
        mvc.perform(get("/exportacao/whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto", startsWith("*BIOSOLAR CITRUS — STATUS*\n📅 *Data:*")))
                .andExpect(jsonPath("$.texto", containsString("💧 *Reservatório:*")))
                .andExpect(jsonPath("$.texto", containsString("🌱 *Talhões:* 4 monitorados · ")))
                .andExpect(jsonPath("$.texto", containsString("🚿 *Irrigação:*")))
                .andExpect(jsonPath("$.texto", containsString("⚠️ *Alertas:*")))
                .andExpect(jsonPath("$.texto", containsString("⚙️ *Sistema:*")))
                .andExpect(jsonPath("$.link", startsWith("https://wa.me/?text=")));
    }

    @Test
    void envioDeEmailPeloServidorComAnexosEValidacao() throws Exception {
        mvc.perform(get("/exportacao/email"))
                .andExpect(jsonPath("$.configurado", is(true)))
                .andExpect(jsonPath("$.remetente", is("relatorios@biosolar.test")))
                .andExpect(jsonPath("$.senha").doesNotExist());

        mvc.perform(post("/exportacao/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatarios\":\"nao-e-email\",\"anexo\":\"PDF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo", is("REQUISICAO_INVALIDA")));
        mvc.perform(post("/exportacao/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatarios\":\"gestor@fazenda.test\"}"))
                .andExpect(status().isBadRequest());

        long eventosAntes = eventoRepository.count();
        mvc.perform(post("/exportacao/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatarios\":\"gestor@fazenda.test\",\"assunto\":\"Relatório da semana\","
                                + "\"mensagem\":\"Segue o relatório.\",\"anexo\":\"AMBOS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso", is(true)))
                .andExpect(jsonPath("$.anexos.length()", is(2)));

        MimeMessage[] recebidas = smtp.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        MimeMessage msg = recebidas[0];
        assertThat(msg.getSubject()).isEqualTo("Relatório da semana");
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("gestor@fazenda.test");
        List<String> anexos = new ArrayList<>();
        coletarAnexos(msg, anexos);
        assertThat(anexos).hasSize(2);
        assertThat(anexos).anyMatch(n -> n.endsWith(".pdf")).anyMatch(n -> n.endsWith(".xlsx"));

        // O envio fica registrado no historico, com o e-mail mascarado
        simulador.executarCiclo();
        assertThat(eventoRepository.count()).isGreaterThan(eventosAntes);
        assertThat(eventoRepository.findAllByOrderByIdAsc()).anyMatch(e -> "Relatório enviado por e-mail".equals(e.getTitulo())
                && e.getDescricao().contains("g***@fazenda.test"));

        // Limite de envios por hora (protecao do SMTP)
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/exportacao/email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"destinatarios\":\"gestor@fazenda.test\",\"anexo\":\"PDF\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/exportacao/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatarios\":\"gestor@fazenda.test\",\"anexo\":\"PDF\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.motivo", is("LIMITE_DE_ENVIOS")));
    }

    private static void coletarAnexos(Part parte, List<String> nomes) throws Exception {
        if (Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition()) && parte.getFileName() != null) {
            nomes.add(parte.getFileName());
        }
        if (parte.getContent() instanceof Multipart multi) {
            for (int i = 0; i < multi.getCount(); i++) {
                BodyPart p = multi.getBodyPart(i);
                coletarAnexos(p, nomes);
            }
        }
    }
}
