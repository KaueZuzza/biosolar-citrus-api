package br.com.biosolar.citrus.service.exportacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDataFormat;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.AlertaDTO;
import br.com.biosolar.citrus.dto.DecisaoDTO;
import br.com.biosolar.citrus.dto.EnergiaDTO;
import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.HistoricoDTO;
import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.RelatorioDTO;
import br.com.biosolar.citrus.dto.ReservatorioDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.util.Rotulos;

/**
 * Planilha .xlsx para analise: uma aba por assunto (Resumo, Talhoes, Reservatorio, Irrigacao, Eventos,
 * Historico), cada tabela com filtros e linhas zebradas, cabecalho congelado, larguras ajustadas e
 * valores numericos de verdade (datas, porcentagens e numeros formatados, nao texto).
 */
@Service
public class RelatorioExcelService {

    public static final List<String> ABAS = List.of("Resumo", "Talhões", "Reservatório", "Irrigação", "Eventos",
            "Histórico");

    private static final Set<TipoEvento> EVENTOS_IRRIGACAO = EnumSet.of(TipoEvento.IRRIGACAO_CRITICA,
            TipoEvento.IRRIGACAO_CONCLUIDA, TipoEvento.PROTECAO_SATURACAO, TipoEvento.COMANDO_MANUAL,
            TipoEvento.COMANDO_RECUSADO, TipoEvento.IRRIGACAO_BLOQUEADA, TipoEvento.BLOQUEIO_EMERGENCIA,
            TipoEvento.RECUPERACAO_SISTEMA);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String VERDE = "1F6B3A";
    private static final String VERDE_CLARO = "E7F2E9";
    private static final int LINHA_CABECALHO = 2;

    private final byte[] emblema = RelatorioPdfService.recurso("relatorio/logo-emblema.png");

    /** Estilos compartilhados (o Excel limita a quantidade de estilos por arquivo). */
    private static final class Estilos {
        final XSSFCellStyle titulo;
        final XSSFCellStyle subtitulo;
        final XSSFCellStyle cabecalho;
        final XSSFCellStyle secao;
        final XSSFCellStyle rotulo;
        final XSSFCellStyle texto;
        final XSSFCellStyle textoNegrito;
        final XSSFCellStyle textoQuebra;
        final XSSFCellStyle dataHora;
        final XSSFCellStyle diaHora;
        final XSSFCellStyle pct;
        final XSSFCellStyle pctNegrito;
        final XSSFCellStyle dec1;
        final XSSFCellStyle inteiro;
        final XSSFCellStyle link;

        Estilos(XSSFWorkbook wb) {
            XSSFDataFormat fmt = wb.createDataFormat();
            XSSFColor verde = new XSSFColor(hex(VERDE), null);

            XSSFFont fTitulo = wb.createFont();
            fTitulo.setBold(true);
            fTitulo.setFontHeightInPoints((short) 16);
            fTitulo.setColor(verde);
            titulo = wb.createCellStyle();
            titulo.setFont(fTitulo);

            XSSFFont fSub = wb.createFont();
            fSub.setItalic(true);
            fSub.setFontHeightInPoints((short) 10);
            fSub.setColor(new XSSFColor(hex("5F6B63"), null));
            subtitulo = wb.createCellStyle();
            subtitulo.setFont(fSub);

            XSSFFont fCab = wb.createFont();
            fCab.setBold(true);
            fCab.setColor(new XSSFColor(hex("FFFFFF"), null));
            cabecalho = wb.createCellStyle();
            cabecalho.setFont(fCab);
            cabecalho.setFillForegroundColor(verde);
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecalho.setWrapText(true);
            cabecalho.setVerticalAlignment(VerticalAlignment.CENTER);
            cabecalho.setAlignment(HorizontalAlignment.CENTER);
            cabecalho.setBorderBottom(BorderStyle.THIN);

            XSSFFont fSecao = wb.createFont();
            fSecao.setBold(true);
            fSecao.setFontHeightInPoints((short) 12);
            fSecao.setColor(verde);
            secao = wb.createCellStyle();
            secao.setFont(fSecao);
            secao.setFillForegroundColor(new XSSFColor(hex(VERDE_CLARO), null));
            secao.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            secao.setBorderBottom(BorderStyle.MEDIUM);
            secao.setBottomBorderColor(verde);

            XSSFFont fNegrito = wb.createFont();
            fNegrito.setBold(true);
            rotulo = wb.createCellStyle();
            rotulo.setFont(fNegrito);
            rotulo.setVerticalAlignment(VerticalAlignment.TOP);
            textoNegrito = wb.createCellStyle();
            textoNegrito.setFont(fNegrito);

            texto = wb.createCellStyle();
            texto.setVerticalAlignment(VerticalAlignment.TOP);
            textoQuebra = wb.createCellStyle();
            textoQuebra.setWrapText(true);
            textoQuebra.setVerticalAlignment(VerticalAlignment.TOP);

            dataHora = wb.createCellStyle();
            dataHora.setDataFormat(fmt.getFormat("dd/mm/yyyy hh:mm:ss"));
            dataHora.setAlignment(HorizontalAlignment.LEFT);
            diaHora = wb.createCellStyle();
            diaHora.setDataFormat(fmt.getFormat("dd/mm hh:mm"));
            diaHora.setAlignment(HorizontalAlignment.LEFT);
            pct = wb.createCellStyle();
            pct.setDataFormat(fmt.getFormat("0.0%"));
            pctNegrito = wb.createCellStyle();
            pctNegrito.setDataFormat(fmt.getFormat("0.0%"));
            pctNegrito.setFont(fNegrito);
            dec1 = wb.createCellStyle();
            dec1.setDataFormat(fmt.getFormat("#,##0.0"));
            inteiro = wb.createCellStyle();
            inteiro.setDataFormat(fmt.getFormat("#,##0"));

            XSSFFont fLink = wb.createFont();
            fLink.setUnderline(XSSFFont.U_SINGLE);
            fLink.setColor(new XSSFColor(hex("1971C2"), null));
            link = wb.createCellStyle();
            link.setFont(fLink);
        }
    }

    public byte[] gerar(DadosExportacao d) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            wb.getProperties().getCoreProperties().setTitle("BioSolar Citrus - Relatório Operacional");
            wb.getProperties().getCoreProperties().setCreator("BioSolar Citrus");
            wb.getProperties().getCoreProperties().setDescription("Exportação gerada pelo servidor a partir do PostgreSQL");

            resumo(wb, e, d);
            talhoes(wb, e, d);
            reservatorio(wb, e, d);
            irrigacao(wb, e, d);
            eventos(wb, e, d);
            historico(wb, e, d);

            wb.setActiveSheet(0);
            wb.write(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gerar a planilha", ex);
        }
    }

    // ---- Resumo -------------------------------------------------------------------------------------

    private void resumo(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        XSSFSheet sh = wb.createSheet(ABAS.get(0));
        sh.setDisplayGridlines(false);
        sh.setColumnWidth(0, 12 * 256);
        sh.setColumnWidth(1, 36 * 256);
        sh.setColumnWidth(2, 18 * 256);
        sh.setColumnWidth(3, 70 * 256);
        for (int r = 0; r < 4; r++) {
            linha(sh, r).setHeightInPoints(19.5f);
        }

        int img = wb.addPicture(emblema, Workbook.PICTURE_TYPE_PNG);
        XSSFDrawing desenho = sh.createDrawingPatriarch();
        // Logo de 80 x 80 px na coluna A (linhas 1-4 com 26 px cada); coordenadas em EMU (1 px = 9525)
        int px = 9525;
        XSSFClientAnchor ancora = new XSSFClientAnchor(4 * px, 4 * px, 84 * px, 6 * px, 0, 0, 0, 3);
        ancora.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        desenho.createPicture(ancora, img);

        RelatorioDTO r = d.relatorio();
        TelemetriaDTO tel = d.telemetria();
        texto(sh, 0, 1, "BioSolar Citrus · Relatório Operacional", e.titulo);
        texto(sh, 1, 1, "Centro Inteligente de Automação Hidro-Energética", e.subtitulo);
        texto(sh, 2, 1, "Período: " + DATA_HORA.format(d.local(r.periodo().inicio())) + " a "
                + DATA_HORA.format(d.local(r.periodo().fim())) + " (" + r.periodo().duracaoMinutos() + " min)", e.subtitulo);
        texto(sh, 3, 1, "Gerado em " + DATA_HORA.format(d.local(r.geradoEm())) + " a partir do PostgreSQL e do estado atual da automação",
                e.subtitulo);

        int l = 5;
        secao(sh, e, l++, "Situação atual: " + tel.statusSistema().rotulo());
        texto(sh, l++, 1, tel.statusSistema().descricao(), e.texto);

        l++;
        Row cab = linha(sh, l++);
        celula(cab, 1, "Indicador", e.cabecalho);
        celula(cab, 2, "Valor", e.cabecalho);
        celula(cab, 3, "Detalhe", e.cabecalho);

        ReservatorioDTO res = tel.reservatorio();
        IndicadoresDTO ind = d.indicadores();
        EnergiaDTO en = tel.energia();
        double solar = en.energiaConsumidaKwh() > 0 ? en.energiaSolarKwh() / en.energiaConsumidaKwh() : 1.0;
        l = kpiPct(sh, e, l, "Reservatório (nível atual)", res.nivel(),
                Rotulos.statusReservatorio(res.status()) + " · " + Math.round(res.volumeM3()) + " de "
                        + Math.round(res.capacidadeM3()) + " m³");
        l = kpiPct(sh, e, l, "Reservatório (médio no período)", r.reservatorio().medio(),
                "mínimo " + pctTexto(r.reservatorio().minimo()) + " · máximo " + pctTexto(r.reservatorio().maximo()));
        l = kpiPct(sh, e, l, "Umidade média do solo", ind.umidadeMedia(), ind.totalTalhoes() + " talhão(ões) ativo(s)");
        l = kpiNum(sh, e, l, "Aspersores ligados agora", ind.aspersoresAtivos(), e.inteiro,
                "de " + ind.totalTalhoes() + " talhões");
        l = kpiNum(sh, e, l, "Índice Hidro-Energético", tel.indice().valor(), e.inteiro,
                tel.indice().rotulo() + " (0 a 100)");
        l = kpiNum(sh, e, l, "Irrigações automáticas", r.contagens().irrigacoesAutomaticas(), e.inteiro, "no período");
        l = kpiNum(sh, e, l, "Comandos manuais", r.contagens().comandosManuais(), e.inteiro,
                r.contagens().comandosRecusados() + " recusado(s) pelas regras de segurança");
        l = kpiNum(sh, e, l, "Bloqueios de emergência", r.contagens().bloqueiosEmergencia(), e.inteiro, "no período");
        l = kpiNum(sh, e, l, "Água consumida (m³)", r.aguaConsumidaM3(), e.dec1, "no período");
        l = kpiNum(sh, e, l, "Energia das bombas (kWh)", en.energiaConsumidaKwh(), e.dec1,
                "rede: " + String.format(java.util.Locale.of("pt", "BR"), "%.1f", en.energiaRedeKwh()) + " kWh");
        l = kpiPct(sh, e, l, "Energia solar utilizada", solar * 100, "parcela do consumo das bombas atendida pela usina");
        l = kpiNum(sh, e, l, "Leituras gravadas no período", d.totalLeituras(), e.inteiro,
                d.passoAmostragem() > 1 ? "abas de histórico com 1 leitura a cada " + d.passoAmostragem() : "todas nas abas de histórico");

        l++;
        secao(sh, e, l++, "Alertas ativos");
        List<AlertaDTO> alertas = tel.alertas();
        if (alertas.isEmpty()) {
            texto(sh, l++, 1, "Nenhum alerta ativo no momento da geração.", e.texto);
        }
        for (AlertaDTO a : alertas) {
            Row row = linha(sh, l++);
            celula(row, 1, Rotulos.severidade(a.nivel()) + ": " + a.titulo(), e.textoNegrito);
            celula(row, 3, a.mensagem(), e.texto);
        }

        DecisaoDTO dec = tel.decisao();
        if (dec != null) {
            l++;
            secao(sh, e, l++, "Decisão atual do sistema (" + dec.regra() + " · " + dec.regraNome() + ")");
            l = chaveValor(sh, e, l, "Decisão", dec.titulo());
            l = chaveValor(sh, e, l, "O que está acontecendo", dec.oQue());
            l = chaveValor(sh, e, l, "Ação automática", dec.acaoAutomatica());
            l = chaveValor(sh, e, l, "Recomendação ao operador", dec.acaoOperador());
        }

        l++;
        secao(sh, e, l++, "Conteúdo desta planilha");
        String[][] abas = {
            {"Talhões", "Cadastro, umidade atual, mínima e média do período, status e acionamentos de cada talhão"},
            {"Reservatório", "Série do nível do reservatório e da operação (bombas, energia, índice)"},
            {"Irrigação", "Resumo por talhão e todos os eventos de irrigação e comandos"},
            {"Eventos", "Histórico completo de eventos com tipo, severidade, origem e regra aplicada"},
            {"Histórico", "Umidade de cada talhão e estado dos aspersores ao longo do período"}
        };
        for (String[] aba : abas) {
            Row row = linha(sh, l++);
            Cell c = celula(row, 1, aba[0], e.link);
            XSSFHyperlink link = wb.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
            link.setAddress("'" + aba[0] + "'!A1");
            c.setHyperlink(link);
            celula(row, 3, aba[1], e.texto);
        }
        l++;
        texto(sh, l, 1, "Dica: use as setas dos cabeçalhos para filtrar e ordenar. Porcentagens, datas e números são "
                + "valores reais (podem ser usados em fórmulas e gráficos).", e.subtitulo);

        sh.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sh.setFitToPage(true);
        sh.getPrintSetup().setFitWidth((short) 1);
        sh.getPrintSetup().setFitHeight((short) 0);
    }

    // ---- Talhoes ------------------------------------------------------------------------------------

    private void talhoes(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        XSSFSheet sh = aba(wb, e, ABAS.get(1), "Talhões ativos: cadastro, umidade e acionamentos",
                "Umidade mínima e média calculadas sobre todas as leituras do período. Tempo irrigando = parcela das leituras com o aspersor ligado.");
        String[] cab = {"Código", "Nome", "Cultura", "Variedade", "Solo", "Área (ha)", "Plantas", "Prioridade",
            "Umidade atual", "Umidade mínima", "Umidade média", "Status", "Limite crítico", "Atenção abaixo de",
            "Alvo de irrigação", "Aspersor", "Modo", "Bomba", "Irrigações automáticas", "Comandos manuais",
            "Comandos recusados", "Tempo irrigando"};
        int[] larg = {9, 16, 11, 16, 34, 10, 10, 10, 11, 11, 11, 11, 10, 11, 11, 11, 12, 10, 12, 11, 11, 11};
        cabecalho(sh, e, cab, larg);

        Map<String, RelatorioDTO.Talhao> periodo = d.relatorio().talhoes().stream()
                .collect(Collectors.toMap(RelatorioDTO.Talhao::id, Function.identity(), (a, b) -> a));
        Map<String, IndicadoresDTO.PorTalhao> acion = d.indicadores().acionamentos().porTalhao().stream()
                .collect(Collectors.toMap(IndicadoresDTO.PorTalhao::talhaoId, Function.identity(), (a, b) -> a));
        int l = LINHA_CABECALHO + 1;
        for (TalhaoDTO t : d.telemetria().talhoes()) {
            RelatorioDTO.Talhao p = periodo.get(t.id());
            IndicadoresDTO.PorTalhao a = acion.get(t.id());
            Row row = linha(sh, l++);
            int c = 0;
            celula(row, c++, t.id(), e.textoNegrito);
            celula(row, c++, t.nome(), e.texto);
            celula(row, c++, t.culturaRotulo(), e.texto);
            celula(row, c++, t.variedade(), e.texto);
            celula(row, c++, t.solo(), e.texto);
            numero(row, c++, t.areaHa(), e.dec1);
            numero(row, c++, t.plantas(), e.inteiro);
            numero(row, c++, t.prioridade(), e.inteiro);
            fracao(row, c++, t.umidade(), e.pct);
            fracao(row, c++, p == null ? null : p.umidadeMinima(), e.pct);
            fracao(row, c++, p == null ? null : p.umidadeMedia(), e.pct);
            celula(row, c++, Rotulos.statusTalhao(t.status()), e.texto);
            fracao(row, c++, t.limiteCritico(), e.pct);
            fracao(row, c++, t.limiteAtencao(), e.pct);
            fracao(row, c++, t.umidadeAlvo(), e.pct);
            celula(row, c++, t.aspersorLigado() ? "Ligado" : t.irrigacaoBloqueada() ? "Bloqueado" : "Desligado", e.texto);
            celula(row, c++, modo(t), e.texto);
            celula(row, c++, t.bombaId(), e.texto);
            numero(row, c++, a == null ? 0 : a.automaticos(), e.inteiro);
            numero(row, c++, a == null ? 0 : a.manuais(), e.inteiro);
            numero(row, c++, a == null ? 0 : a.recusados(), e.inteiro);
            fracao(row, c, d.tempoIrrigandoPct().get(t.id()), e.pct);
        }
        l = tabela(sh, "tbTalhoes", l, cab.length);
        coresStatus(sh, "L" + (LINHA_CABECALHO + 2) + ":L" + l);
    }

    // ---- Reservatorio ---------------------------------------------------------------------------------

    private void reservatorio(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        ReservatorioDTO res = d.telemetria().reservatorio();
        XSSFSheet sh = aba(wb, e, ABAS.get(2), "Reservatório e operação ao longo do período", amostragem(d));
        String[] cab = {"Data/hora", "Hora na fazenda", "Nível do reservatório", "Umidade média", "Aspersores ligados",
            "Consumo das bombas (kW)", "Geração solar (kW)", "Índice Hidro-Energético", "Bloqueio de emergência"};
        int[] larg = {20, 14, 13, 12, 12, 14, 12, 14, 13};
        cabecalho(sh, e, cab, larg);
        int l = LINHA_CABECALHO + 1;
        for (HistoricoDTO.Ponto p : d.serie()) {
            Row row = linha(sh, l++);
            data(row, 0, d.local(p.instante()), e.dataHora);
            data(row, 1, p.horaSimulada(), e.diaHora);
            fracao(row, 2, p.reservatorio(), e.pct);
            fracao(row, 3, p.umidadeMedia(), e.pct);
            numero(row, 4, p.aspersoresLigados(), e.inteiro);
            numero(row, 5, p.consumoKw(), e.dec1);
            numero(row, 6, p.geracaoSolarKw(), e.dec1);
            numero(row, 7, p.indice(), e.inteiro);
            celula(row, 8, p.bloqueioEmergencia() ? "Sim" : "Não", e.texto);
        }
        l = tabela(sh, "tbReservatorio", l, cab.length);

        // Quadro com a situacao atual, ao lado da tabela (fica visivel junto com o cabecalho congelado)
        int col = cab.length + 1;
        sh.setColumnWidth(col, 26 * 256);
        sh.setColumnWidth(col + 1, 30 * 256);
        Row cabQuadro = linha(sh, LINHA_CABECALHO);
        celula(cabQuadro, col, "Situação atual", e.cabecalho);
        vazia(cabQuadro, col + 1, e.cabecalho);
        RelatorioDTO.Reservatorio est = d.relatorio().reservatorio();
        Object[][] quadro = {
            {"Reservatório", res.nome()},
            {"Nível atual", res.nivel()},
            {"Status", Rotulos.statusReservatorio(res.status())},
            {"Volume (m³)", res.volumeM3()},
            {"Capacidade (m³)", res.capacidadeM3()},
            {"Nível médio no período", est.medio()},
            {"Nível mínimo no período", est.minimo()},
            {"Nível máximo no período", est.maximo()},
            {"Consumo agora (m³/h)", res.consumoM3h()},
            {"Recarga (m³/h)", res.recargaM3h()},
            {"Tendência (p.p./h)", res.tendenciaPorHora()},
            {"Autonomia (h)", res.autonomiaHoras() == null ? "estável" : res.autonomiaHoras()},
            {"Atenção abaixo de", res.limiteAtencao()},
            {"Bloqueio abaixo de", res.limiteCritico()},
            {"Rearme a partir de", res.limiteRearme()},
            {"Proteção", res.bloqueioEmergencia() ? "BLOQUEIO ATIVO" : "Liberada"}
        };
        Set<String> porcentagens = Set.of("Nível atual", "Nível médio no período", "Nível mínimo no período",
                "Nível máximo no período", "Atenção abaixo de", "Bloqueio abaixo de", "Rearme a partir de");
        int q = LINHA_CABECALHO + 1;
        for (Object[] item : quadro) {
            Row row = linha(sh, q++);
            celula(row, col, (String) item[0], e.rotulo);
            Object v = item[1];
            if (v instanceof Number n && porcentagens.contains(item[0])) {
                fracao(row, col + 1, n.doubleValue(), e.pctNegrito);
            } else if (v instanceof Number n) {
                numero(row, col + 1, n.doubleValue(), e.dec1);
            } else {
                celula(row, col + 1, v == null ? "--" : v.toString(), e.textoNegrito);
            }
        }
    }

    // ---- Irrigacao ------------------------------------------------------------------------------------

    private void irrigacao(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        XSSFSheet sh = aba(wb, e, ABAS.get(3), "Irrigação: eventos e resumo por talhão",
                "À esquerda, cada irrigação/comando registrado; à direita, o total por talhão.");
        String[] cab = {"Data/hora", "Hora na fazenda", "Talhão", "Evento", "Origem", "Regra", "Descrição"};
        int[] larg = {20, 14, 9, 24, 12, 8, 70};
        cabecalho(sh, e, cab, larg);
        int l = LINHA_CABECALHO + 1;
        for (EventoDTO ev : d.eventos()) {
            if (!EVENTOS_IRRIGACAO.contains(ev.tipo())) {
                continue;
            }
            Row row = linha(sh, l++);
            data(row, 0, d.local(ev.instante()), e.dataHora);
            data(row, 1, ev.horaSimulada(), e.diaHora);
            celula(row, 2, ev.talhaoId() == null ? "" : ev.talhaoId(), e.texto);
            celula(row, 3, Rotulos.tipo(ev.tipo()), e.texto);
            celula(row, 4, Rotulos.origem(ev.origem()), e.texto);
            celula(row, 5, ev.regra() == null ? "" : ev.regra(), e.texto);
            celula(row, 6, ev.titulo() + ". " + ev.descricao(), e.texto);
        }
        tabela(sh, "tbIrrigacaoEventos", l, cab.length);

        int col = cab.length + 1;
        String[] cab2 = {"Talhão", "Nome", "Irrigações automáticas", "Comandos manuais", "Comandos recusados",
            "Tempo irrigando", "Aspersor agora"};
        int[] larg2 = {9, 16, 13, 12, 12, 12, 13};
        Row cabRow = linha(sh, LINHA_CABECALHO);
        for (int i = 0; i < cab2.length; i++) {
            celula(cabRow, col + i, cab2[i], e.cabecalho);
            sh.setColumnWidth(col + i, larg2[i] * 256);
        }
        Map<String, TalhaoDTO> atuais = d.telemetria().talhoes().stream()
                .collect(Collectors.toMap(TalhaoDTO::id, Function.identity(), (a, b) -> a));
        int q = LINHA_CABECALHO + 1;
        for (IndicadoresDTO.PorTalhao p : d.indicadores().acionamentos().porTalhao()) {
            Row row = linha(sh, q++);
            TalhaoDTO t = atuais.get(p.talhaoId());
            celula(row, col, p.talhaoId(), e.textoNegrito);
            celula(row, col + 1, d.nomeTalhao(p.talhaoId()), e.texto);
            numero(row, col + 2, p.automaticos(), e.inteiro);
            numero(row, col + 3, p.manuais(), e.inteiro);
            numero(row, col + 4, p.recusados(), e.inteiro);
            fracao(row, col + 5, d.tempoIrrigandoPct().get(p.talhaoId()), e.pct);
            celula(row, col + 6, t == null ? "" : t.aspersorLigado() ? "Ligado (" + modo(t).toLowerCase() + ")" : "Desligado", e.texto);
        }
        if (q > LINHA_CABECALHO + 1) {
            criarTabela(sh, "tbIrrigacaoTalhoes", LINHA_CABECALHO, col, q - 1, col + cab2.length - 1);
        }
    }

    // ---- Eventos ----------------------------------------------------------------------------------------

    private void eventos(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        String nota = d.eventosOmitidos() > 0
                ? "Os " + d.eventos().size() + " eventos mais recentes (" + d.eventosOmitidos() + " mais antigos omitidos)."
                : d.eventos().size() + " evento(s) no período, em ordem cronológica.";
        XSSFSheet sh = aba(wb, e, ABAS.get(4), "Eventos registrados", nota);
        String[] cab = {"Data/hora", "Hora na fazenda", "Tipo", "Severidade", "Origem", "Regra", "Talhão", "Título",
            "Descrição", "Reservatório no momento"};
        int[] larg = {20, 14, 24, 12, 12, 8, 9, 34, 70, 14};
        cabecalho(sh, e, cab, larg);
        int l = LINHA_CABECALHO + 1;
        for (EventoDTO ev : d.eventos()) {
            Row row = linha(sh, l++);
            data(row, 0, d.local(ev.instante()), e.dataHora);
            data(row, 1, ev.horaSimulada(), e.diaHora);
            celula(row, 2, Rotulos.tipo(ev.tipo()), e.texto);
            celula(row, 3, Rotulos.severidade(ev.severidade()), e.texto);
            celula(row, 4, Rotulos.origem(ev.origem()), e.texto);
            celula(row, 5, ev.regra() == null ? "" : ev.regra(), e.texto);
            celula(row, 6, ev.talhaoId() == null ? "" : ev.talhaoId(), e.texto);
            celula(row, 7, ev.titulo(), e.texto);
            celula(row, 8, ev.descricao(), e.texto);
            fracao(row, 9, ev.nivelReservatorio(), e.pct);
        }
        l = tabela(sh, "tbEventos", l, cab.length);
        coresStatus(sh, "D" + (LINHA_CABECALHO + 2) + ":D" + l);
    }

    // ---- Historico ------------------------------------------------------------------------------------

    private void historico(XSSFWorkbook wb, Estilos e, DadosExportacao d) {
        XSSFSheet sh = aba(wb, e, ABAS.get(5), "Histórico de umidade por talhão", amostragem(d));
        List<String> ids = d.talhoesSerie();
        List<String> cab = new ArrayList<>(List.of("Data/hora", "Hora na fazenda"));
        List<Integer> larg = new ArrayList<>(List.of(20, 14));
        for (String id : ids) {
            cab.add("Umidade " + id);
            larg.add(11);
        }
        cab.add("Umidade média");
        larg.add(11);
        cab.add("Reservatório");
        larg.add(12);
        for (String id : ids) {
            cab.add("Aspersor " + id);
            larg.add(10);
        }
        cabecalho(sh, e, cab.toArray(String[]::new), larg.stream().mapToInt(Integer::intValue).toArray());
        int l = LINHA_CABECALHO + 1;
        for (HistoricoDTO.Ponto p : d.serie()) {
            Row row = linha(sh, l++);
            int c = 0;
            data(row, c++, d.local(p.instante()), e.dataHora);
            data(row, c++, p.horaSimulada(), e.diaHora);
            for (String id : ids) {
                fracao(row, c++, p.umidades().get(id), e.pct);
            }
            fracao(row, c++, p.umidadeMedia(), e.pct);
            fracao(row, c++, p.reservatorio(), e.pct);
            for (String id : ids) {
                Boolean ligado = p.aspersores().get(id);
                celula(row, c++, ligado == null ? "" : ligado ? "Ligado" : "Desligado", e.texto);
            }
        }
        tabela(sh, "tbHistorico", l, cab.size());
    }

    // ---- Estrutura comum ---------------------------------------------------------------------------------

    /** Cria a aba com titulo (linha 1), nota (linha 2) e deixa o cabecalho da tabela na linha 3, congelada. */
    private XSSFSheet aba(XSSFWorkbook wb, Estilos e, String nome, String titulo, String nota) {
        XSSFSheet sh = wb.createSheet(nome);
        texto(sh, 0, 0, titulo, e.titulo);
        linha(sh, 0).setHeightInPoints(24);
        if (nota != null) {
            texto(sh, 1, 0, nota, e.subtitulo);
        }
        sh.createFreezePane(0, LINHA_CABECALHO + 1);
        sh.setRepeatingRows(new CellRangeAddress(LINHA_CABECALHO, LINHA_CABECALHO, -1, -1));
        PrintSetup ps = sh.getPrintSetup();
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        ps.setLandscape(true);
        sh.setFitToPage(true);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
        return sh;
    }

    private void cabecalho(XSSFSheet sh, Estilos e, String[] titulos, int[] larguras) {
        Row row = linha(sh, LINHA_CABECALHO);
        row.setHeightInPoints(32);
        for (int i = 0; i < titulos.length; i++) {
            celula(row, i, titulos[i], e.cabecalho);
            sh.setColumnWidth(i, larguras[i] * 256);
        }
    }

    /**
     * Transforma o intervalo em uma Tabela do Excel (filtro em cada coluna e linhas zebradas). Sem dados, grava
     * uma linha "Sem registros" (uma tabela precisa de ao menos uma linha). Retorna a ultima linha (base 1).
     */
    private int tabela(XSSFSheet sh, String nome, int proximaLinha, int colunas) {
        if (proximaLinha == LINHA_CABECALHO + 1) {
            Row row = linha(sh, proximaLinha++);
            row.createCell(0).setCellValue("Sem registros no período");
        }
        criarTabela(sh, nome, LINHA_CABECALHO, 0, proximaLinha - 1, colunas - 1);
        return proximaLinha;
    }

    private void criarTabela(XSSFSheet sh, String nome, int linha1, int col1, int linha2, int col2) {
        AreaReference area = new AreaReference(new CellReference(linha1, col1), new CellReference(linha2, col2),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable t = sh.createTable(area);
        t.setName(nome);
        t.setDisplayName(nome);
        CTTable ct = t.getCTTable();
        ct.addNewAutoFilter().setRef(area.formatAsString());
        CTTableStyleInfo estilo = ct.isSetTableStyleInfo() ? ct.getTableStyleInfo() : ct.addNewTableStyleInfo();
        estilo.setName("TableStyleLight9");
        estilo.setShowRowStripes(true);
        estilo.setShowColumnStripes(false);
        t.updateHeaders();
    }

    /** Destaca Critico/Emergencia em vermelho e Atencao em amarelo (formatacao condicional). */
    private static void coresStatus(XSSFSheet sh, String intervalo) {
        SheetConditionalFormatting scf = sh.getSheetConditionalFormatting();
        ConditionalFormattingRule critico = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"Crítico\"");
        critico.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        ConditionalFormattingRule emergencia = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"Emergência\"");
        emergencia.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        ConditionalFormattingRule atencao = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"Atenção\"");
        atencao.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        CellRangeAddress[] faixa = {CellRangeAddress.valueOf(intervalo)};
        scf.addConditionalFormatting(faixa, critico, emergencia);
        scf.addConditionalFormatting(faixa, atencao);
    }

    private void secao(XSSFSheet sh, Estilos e, int linha, String titulo) {
        Row row = linha(sh, linha);
        row.setHeightInPoints(20);
        celula(row, 1, titulo, e.secao);
        for (int c = 2; c <= 3; c++) {
            vazia(row, c, e.secao);  // celula sem valor: o titulo longo pode "transbordar" sobre ela
        }
    }

    private int chaveValor(XSSFSheet sh, Estilos e, int l, String chave, String valor) {
        Row row = linha(sh, l);
        celula(row, 1, chave, e.rotulo);
        Cell c = celula(row, 2, valor == null ? "" : valor, e.textoQuebra);
        sh.addMergedRegion(new CellRangeAddress(l, l, 2, 3));
        int linhas = Math.max(1, (int) Math.ceil((valor == null ? 0 : valor.length()) / 95.0));
        row.setHeightInPoints(15 * linhas + 2);
        c.setCellStyle(e.textoQuebra);
        return l + 1;
    }

    private int kpiPct(XSSFSheet sh, Estilos e, int l, String rotulo, Double valor, String detalhe) {
        Row row = linha(sh, l);
        celula(row, 1, rotulo, e.texto);
        fracao(row, 2, valor, e.pctNegrito);
        celula(row, 3, detalhe, e.subtitulo);
        return l + 1;
    }

    private int kpiNum(XSSFSheet sh, Estilos e, int l, String rotulo, double valor, XSSFCellStyle estilo, String detalhe) {
        Row row = linha(sh, l);
        celula(row, 1, rotulo, e.texto);
        numero(row, 2, valor, estilo);
        celula(row, 3, detalhe, e.subtitulo);
        return l + 1;
    }

    private static String amostragem(DadosExportacao d) {
        return d.passoAmostragem() > 1
                ? d.serie().size() + " leituras (amostra uniforme: 1 a cada " + d.passoAmostragem() + " das "
                        + d.totalLeituras() + " gravadas no PostgreSQL)."
                : d.serie().size() + " leitura(s) gravada(s) no PostgreSQL, em ordem cronológica.";
    }

    private static String modo(TalhaoDTO t) {
        return switch (t.modoAcionamento()) {
            case AUTOMATICO -> "Automático";
            case MANUAL -> "Manual";
            case DESLIGADO -> "--";
        };
    }

    private static String pctTexto(Double v) {
        return v == null ? "--" : String.format(java.util.Locale.of("pt", "BR"), "%.1f%%", v);
    }

    private static Row linha(XSSFSheet sh, int indice) {
        Row r = sh.getRow(indice);
        return r != null ? r : sh.createRow(indice);
    }

    private static void texto(XSSFSheet sh, int linha, int coluna, String valor, XSSFCellStyle estilo) {
        celula(linha(sh, linha), coluna, valor, estilo);
    }

    private static Cell celula(Row row, int coluna, String valor, XSSFCellStyle estilo) {
        Cell c = row.createCell(coluna);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
        return c;
    }

    private static void vazia(Row row, int coluna, XSSFCellStyle estilo) {
        row.createCell(coluna).setCellStyle(estilo);
    }

    private static void numero(Row row, int coluna, double valor, XSSFCellStyle estilo) {
        Cell c = row.createCell(coluna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    /** Porcentagem gravada como fracao (67,3% -> 0,673) com formato 0,0%: o Excel entende como numero. */
    private static void fracao(Row row, int coluna, Double valorPct, XSSFCellStyle estilo) {
        Cell c = row.createCell(coluna);
        if (valorPct != null) {
            c.setCellValue(valorPct / 100.0);
        }
        c.setCellStyle(estilo);
    }

    private static void data(Row row, int coluna, LocalDateTime valor, XSSFCellStyle estilo) {
        Cell c = row.createCell(coluna);
        if (valor != null) {
            c.setCellValue(valor);
        }
        c.setCellStyle(estilo);
    }

    private static byte[] hex(String rgb) {
        return new byte[] {(byte) Integer.parseInt(rgb.substring(0, 2), 16),
            (byte) Integer.parseInt(rgb.substring(2, 4), 16), (byte) Integer.parseInt(rgb.substring(4, 6), 16)};
    }
}
