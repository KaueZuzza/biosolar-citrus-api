package br.com.biosolar.citrus.service.exportacao;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

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
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusSistema;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.util.Formatador;
import br.com.biosolar.citrus.util.Rotulos;

/**
 * Relatorio operacional em PDF (A4): capa com a logo, resumo com indicadores, reservatorio, talhoes,
 * irrigacao e energia, alertas e eventos importantes, com graficos vetoriais, tabelas que nao quebram
 * no meio de uma linha e "Pagina X de Y" no rodape.
 */
@Service
public class RelatorioPdfService {

    static final Color VERDE = new Color(0x1F, 0x6B, 0x3A);
    static final Color LARANJA = new Color(0xE8, 0x59, 0x0C);
    static final Color AZUL = new Color(0x19, 0x71, 0xC2);
    static final Color TEXTO = new Color(0x1B, 0x24, 0x1E);
    static final Color TEXTO_2 = new Color(0x44, 0x4F, 0x47);
    static final Color MUTED = new Color(0x6B, 0x75, 0x6E);
    static final Color BORDA = new Color(0xD5, 0xDC, 0xD6);
    static final Color ZEBRA = new Color(0xF5, 0xF8, 0xF5);
    static final Color CARTAO = new Color(0xFA, 0xFC, 0xFA);
    static final Color OK = new Color(0x2B, 0x8A, 0x3E);
    static final Color OK_BG = new Color(0xE7, 0xF5, 0xE9);
    static final Color ATENCAO = new Color(0xA8, 0x62, 0x00);
    static final Color ATENCAO_BG = new Color(0xFF, 0xF4, 0xD6);
    static final Color ATENCAO_LINHA = new Color(0xE0, 0x9A, 0x00);
    static final Color CRITICO = new Color(0xC0, 0x2B, 0x2B);
    static final Color CRITICO_BG = new Color(0xFD, 0xE8, 0xE8);
    /** Mesmas 8 cores categoricas do dashboard (tema claro), na ordem dos talhoes. */
    static final Color[] SERIES = {new Color(0x2A, 0x78, 0xD6), new Color(0xEB, 0x68, 0x34),
        new Color(0x1B, 0xAF, 0x7A), new Color(0xED, 0xA1, 0x00), new Color(0xE8, 0x7B, 0xA4),
        new Color(0x00, 0x83, 0x00), new Color(0x4A, 0x3A, 0xA7), new Color(0xE3, 0x49, 0x48)};

    private static final int MAX_EVENTOS_PDF = 15;
    private static final int MAX_PONTOS_GRAFICO = 300;
    private static final Set<TipoEvento> EVENTOS_IMPORTANTES = EnumSet.of(TipoEvento.IRRIGACAO_CRITICA,
            TipoEvento.IRRIGACAO_CONCLUIDA, TipoEvento.PROTECAO_SATURACAO, TipoEvento.COMANDO_MANUAL,
            TipoEvento.COMANDO_RECUSADO, TipoEvento.BLOQUEIO_EMERGENCIA, TipoEvento.IRRIGACAO_BLOQUEADA,
            TipoEvento.RECUPERACAO_SISTEMA, TipoEvento.ALERTA_UMIDADE, TipoEvento.ALERTA_RESERVATORIO);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_HORA_SEG = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private final byte[] logo;
    private final byte[] emblema;

    public RelatorioPdfService() {
        this.logo = recurso("relatorio/logo-biosolar.png");
        this.emblema = recurso("relatorio/logo-emblema.png");
    }

    static byte[] recurso(String caminho) {
        try (InputStream in = new ClassPathResource(caminho).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Recurso " + caminho + " não encontrado", e);
        }
    }

    public byte[] gerar(DadosExportacao d) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 62, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, saida);
            String geradoEm = DATA_HORA.format(d.local(d.relatorio().geradoEm()));
            writer.setPageEvent(new Paginacao(Image.getInstance(emblema), geradoEm, periodoCurto(d)));
            doc.addTitle("BioSolar Citrus - Relatório Operacional");
            doc.addSubject("Relatório operacional do período " + periodoCurto(d));
            doc.addAuthor("BioSolar Citrus");
            doc.addCreator("BioSolar Citrus - Centro Inteligente de Automação Hidro-Energética");
            doc.open();

            capa(doc, d, geradoEm);
            resumo(doc, writer, d);
            reservatorio(doc, writer, d);
            talhoes(doc, writer, d);
            irrigacaoEnergia(doc, writer, d);
            alertasEEventos(doc, writer, d);
            observacoes(doc, d);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o PDF do relatório", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return saida.toByteArray();
    }

    // ---- Capa e resumo -------------------------------------------------------------------------

    private void capa(Document doc, DadosExportacao d, String geradoEm) throws DocumentException, IOException {
        RelatorioDTO.Periodo p = d.relatorio().periodo();
        PdfPTable t = new PdfPTable(new float[] {1.05f, 3f});
        t.setWidthPercentage(100);

        Image img = Image.getInstance(logo);
        img.scaleToFit(104, 104);
        PdfPCell celLogo = new PdfPCell(img, false);
        celLogo.setBorder(Rectangle.NO_BORDER);
        celLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celLogo.setPaddingRight(10);
        t.addCell(celLogo);

        PdfPCell texto = new PdfPCell();
        texto.setBorder(Rectangle.NO_BORDER);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        texto.addElement(par("RELATÓRIO OPERACIONAL", fonte(9, Font.BOLD, LARANJA), 0));
        texto.addElement(par("BioSolar Citrus", fonte(24, Font.BOLD, VERDE), 2));
        texto.addElement(par("Centro Inteligente de Automação Hidro-Energética", fonte(10, Font.NORMAL, TEXTO_2), 8));
        texto.addElement(linhaRotulo("Período: ", DATA_HORA.format(d.local(p.inicio())) + " a "
                + DATA_HORA.format(d.local(p.fim())) + " (" + duracao(p.duracaoMinutos()) + ")"));
        texto.addElement(linhaRotulo("Hora na fazenda (simulada): ",
                p.horaSimuladaAtual() == null ? "--" : DIA_HORA.format(p.horaSimuladaAtual())));
        texto.addElement(linhaRotulo("Gerado em: ", geradoEm));
        t.addCell(texto);
        doc.add(t);

        // Faixa de status geral
        TelemetriaDTO tel = d.telemetria();
        StatusSistema codigo = tel.statusSistema().codigo();
        Color cor = corStatus(codigo);
        PdfPTable faixa = new PdfPTable(1);
        faixa.setWidthPercentage(100);
        faixa.setSpacingBefore(14);
        Phrase frase = new Phrase();
        frase.add(new Chunk("STATUS: " + limpar(tel.statusSistema().rotulo()).toUpperCase(PT_BR) + "   ",
                fonte(10.5f, Font.BOLD, cor)));
        frase.add(new Chunk(limpar(tel.statusSistema().descricao()), fonte(9.5f, Font.NORMAL, TEXTO)));
        PdfPCell c = new PdfPCell(frase);
        c.setBackgroundColor(fundoStatus(codigo));
        c.setBorder(Rectangle.LEFT);
        c.setBorderColorLeft(cor);
        c.setBorderWidthLeft(5);
        c.setPadding(9);
        c.setPaddingLeft(12);
        faixa.addCell(c);
        doc.add(faixa);
    }

    private void resumo(Document doc, PdfWriter w, DadosExportacao d) throws DocumentException {
        secao(doc, w, "1", "Resumo dos principais indicadores", null);
        TelemetriaDTO tel = d.telemetria();
        IndicadoresDTO ind = d.indicadores();
        RelatorioDTO r = d.relatorio();
        ReservatorioDTO res = tel.reservatorio();
        EnergiaDTO e = tel.energia();

        TalhaoDTO menor = tel.talhoes().stream().min(Comparator.comparingDouble(TalhaoDTO::umidade)).orElse(null);
        List<String> ligados = tel.talhoes().stream().filter(TalhaoDTO::aspersorLigado).map(TalhaoDTO::id).toList();
        double solarPct = e.energiaConsumidaKwh() > 0 ? e.energiaSolarKwh() / e.energiaConsumidaKwh() * 100 : 100;

        PdfPTable k = new PdfPTable(4);
        k.setWidthPercentage(100);
        k.setKeepTogether(true);
        k.addCell(kpi("Reservatório", pct(res.nivel()), Rotulos.statusReservatorio(res.status()) + " · "
                + inteiro(res.volumeM3()) + " de " + inteiro(res.capacidadeM3()) + " m³"));
        k.addCell(kpi("Umidade média do solo", pct(ind.umidadeMedia()),
                menor == null ? "sem talhões ativos" : "Menor: " + menor.nome() + " (" + pct(menor.umidade()) + ")"));
        k.addCell(kpi("Aspersores ligados", ind.aspersoresAtivos() + " de " + ind.totalTalhoes(),
                ligados.isEmpty() ? "todas as bombas desligadas" : "Talhões " + String.join(", ", ligados)));
        k.addCell(kpi("Índice Hidro-Energético", tel.indice().valor() + "/100", tel.indice().rotulo()));
        k.addCell(kpi("Irrigações automáticas", String.valueOf(r.contagens().irrigacoesAutomaticas()), "no período"));
        k.addCell(kpi("Comandos manuais", String.valueOf(r.contagens().comandosManuais()),
                "recusados pela segurança: " + r.contagens().comandosRecusados()));
        k.addCell(kpi("Água consumida", decimal(r.aguaConsumidaM3()) + " m³", "no período"));
        k.addCell(kpi("Energia solar", Math.round(solarPct) + "%",
                "de " + decimal(e.energiaConsumidaKwh()) + " kWh consumidos"));
        doc.add(k);

        DecisaoDTO dec = tel.decisao();
        if (dec != null) {
            Color cor = corSeveridade(dec.nivel());
            PdfPTable caixa = new PdfPTable(1);
            caixa.setWidthPercentage(100);
            caixa.setSpacingBefore(10);
            caixa.setKeepTogether(true);
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.LEFT | Rectangle.TOP | Rectangle.RIGHT | Rectangle.BOTTOM);
            c.setBorderColor(BORDA);
            c.setBorderWidth(0.6f);
            c.setBorderColorLeft(cor);
            c.setBorderWidthLeft(4);
            c.setPadding(9);
            c.setPaddingLeft(12);
            c.addElement(par("DECISÃO ATUAL DO SISTEMA · " + nulo(dec.regra()) + " · " + nulo(dec.regraNome()),
                    fonte(7.5f, Font.BOLD, MUTED), 2));
            c.addElement(par(dec.titulo(), fonte(11, Font.BOLD, TEXTO), 3));
            c.addElement(linhaRotulo("O que está acontecendo: ", dec.oQue()));
            c.addElement(linhaRotulo("Ação automática: ", dec.acaoAutomatica()));
            c.addElement(linhaRotulo("Recomendação ao operador: ", dec.acaoOperador()));
            caixa.addCell(c);
            doc.add(caixa);
        }
    }

    // ---- Reservatorio ------------------------------------------------------------------------------

    private void reservatorio(Document doc, PdfWriter w, DadosExportacao d) throws DocumentException {
        ReservatorioDTO res = d.telemetria().reservatorio();
        RelatorioDTO.Reservatorio est = d.relatorio().reservatorio();
        secao(doc, w, "2", "Situação do reservatório", res.nome() + " · capacidade " + inteiro(res.capacidadeM3()) + " m³");

        PdfPTable t = new PdfPTable(new float[] {1.25f, 1.6f, 1.25f, 1.6f});
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        par(t, "Nível atual", pct(res.nivel()) + " (" + Rotulos.statusReservatorio(res.status()) + ")",
                "Volume", inteiro(res.volumeM3()) + " de " + inteiro(res.capacidadeM3()) + " m³");
        par(t, "Nível médio no período", pctNulo(est.medio()),
                "Mínimo / máximo", pctNulo(est.minimo()) + " / " + pctNulo(est.maximo()));
        par(t, "Consumo agora", res.consumoM3h() > 0 ? decimal(res.consumoM3h()) + " m³/h" : "zero",
                "Recarga", "+" + decimal(res.recargaM3h()) + " m³/h");
        par(t, "Tendência", sinal(res.tendenciaPorHora()) + " p.p./h",
                "Autonomia", res.autonomiaHoras() == null ? "estável" : Formatador.horas(res.autonomiaHoras()));
        par(t, "Limites", "atenção " + inteiro(res.limiteAtencao()) + "% · bloqueio " + inteiro(res.limiteCritico())
                        + "% · rearme " + inteiro(res.limiteRearme()) + "%",
                "Proteção", res.bloqueioEmergencia() ? "BLOQUEIO DE EMERGÊNCIA ATIVO" : "Liberada");
        doc.add(t);

        List<HistoricoDTO.Ponto> pontos = reduzir(d.serie());
        if (pontos.size() >= 2) {
            double[] niveis = pontos.stream().mapToDouble(HistoricoDTO.Ponto::reservatorio).toArray();
            Image g = GraficoPdf.linhas(w, larguraUtil(doc), 150, rotulosTempo(d, pontos),
                    List.of(new GraficoPdf.Serie("Reservatório", AZUL, niveis)),
                    List.of(new GraficoPdf.Referencia(res.limiteAtencao(), "Atenção " + inteiro(res.limiteAtencao()) + "%", ATENCAO_LINHA),
                            new GraficoPdf.Referencia(res.limiteCritico(), "Bloqueio " + inteiro(res.limiteCritico()) + "%", CRITICO)),
                    List.of(new GraficoPdf.Faixa(0, res.limiteCritico(), CRITICO),
                            new GraficoPdf.Faixa(res.limiteCritico(), res.limiteAtencao(), ATENCAO_LINHA)),
                    true);
            grafico(doc, "Nível do reservatório no período (%)", g);
        }
    }

    // ---- Talhoes -------------------------------------------------------------------------------------

    private void talhoes(Document doc, PdfWriter w, DadosExportacao d) throws DocumentException {
        List<TalhaoDTO> talhoes = d.telemetria().talhoes();
        secao(doc, w, "3", "Situação dos talhões", talhoes.size() + " talhão(ões) ativo(s) monitorado(s)");
        Map<String, RelatorioDTO.Talhao> periodo = d.relatorio().talhoes().stream()
                .collect(Collectors.toMap(RelatorioDTO.Talhao::id, Function.identity(), (a, b) -> a));

        PdfPTable t = tabela(new float[] {1.5f, 1.6f, 0.95f, 0.85f, 0.85f, 0.95f, 1.35f},
                "Talhão", "Cultura", "Umidade atual", "Mínima", "Média", "Status", "Aspersor");
        int i = 0;
        for (TalhaoDTO x : talhoes) {
            RelatorioDTO.Talhao p = periodo.get(x.id());
            Color fundo = i++ % 2 == 1 ? ZEBRA : null;
            t.addCell(celula(x.nome(), fonte(8.5f, Font.BOLD, TEXTO), fundo, Element.ALIGN_LEFT));
            t.addCell(celula(x.culturaRotulo() + " " + x.variedade(), corpo(), fundo, Element.ALIGN_LEFT));
            t.addCell(celula(pct(x.umidade()), fonte(8.5f, Font.BOLD, TEXTO), fundo, Element.ALIGN_RIGHT));
            t.addCell(celula(p == null ? "--" : pctNulo(p.umidadeMinima()), corpo(), fundo, Element.ALIGN_RIGHT));
            t.addCell(celula(p == null ? "--" : pctNulo(p.umidadeMedia()), corpo(), fundo, Element.ALIGN_RIGHT));
            Color corStatus = switch (x.status()) {
                case CRITICO -> CRITICO;
                case ATENCAO -> ATENCAO;
                default -> OK;
            };
            t.addCell(celula(Rotulos.statusTalhao(x.status()), fonte(8.5f, Font.BOLD, corStatus), fundo, Element.ALIGN_CENTER));
            String aspersor = x.aspersorLigado()
                    ? "Ligado (" + (x.modoAcionamento().name().equals("MANUAL") ? "manual" : "automático") + ")"
                    : x.irrigacaoBloqueada() ? "Bloqueado" : "Desligado";
            t.addCell(celula(aspersor, corpo(), fundo, Element.ALIGN_LEFT));
        }
        if (talhoes.isEmpty()) {
            PdfPCell vazio = celula("Nenhum talhão ativo.", corpo(), null, Element.ALIGN_CENTER);
            vazio.setColspan(7);
            t.addCell(vazio);
        }
        doc.add(t);

        List<String> criticos = d.relatorio().talhoesCriticos();
        doc.add(par("Talhões que atingiram o nível crítico (25%) no período: "
                + (criticos.isEmpty() ? "nenhum." : String.join(", ", criticos) + "."), fonte(8.5f, Font.NORMAL, TEXTO_2), 0, 5));

        List<HistoricoDTO.Ponto> pontos = reduzir(d.serie());
        List<String> ids = talhoes.stream().map(TalhaoDTO::id).toList();
        if (pontos.size() >= 2 && !ids.isEmpty()) {
            List<GraficoPdf.Serie> series = new ArrayList<>();
            for (int s = 0; s < ids.size(); s++) {
                String id = ids.get(s);
                double[] valores = pontos.stream().mapToDouble(p -> {
                    Double v = p.umidades().get(id);
                    return v == null ? Double.NaN : v;
                }).toArray();
                series.add(new GraficoPdf.Serie(d.nomeTalhao(id), SERIES[s % SERIES.length], valores));
            }
            Image g = GraficoPdf.linhas(w, larguraUtil(doc), 170, rotulosTempo(d, pontos), series,
                    List.of(new GraficoPdf.Referencia(25, "Limite crítico 25%", CRITICO)), List.of(), false);
            grafico(doc, "Umidade do solo por talhão no período (%)", g);
        }
    }

    // ---- Irrigacao e energia ---------------------------------------------------------------------------

    private void irrigacaoEnergia(Document doc, PdfWriter w, DadosExportacao d) throws DocumentException {
        secao(doc, w, "4", "Irrigação e acionamentos", "Contagens desde o início do período; tempo irrigando = parcela das leituras com o aspersor ligado");
        IndicadoresDTO.Acionamentos ac = d.indicadores().acionamentos();
        PdfPTable t = tabela(new float[] {1.6f, 1.1f, 1.1f, 1.1f, 1.1f},
                "Talhão", "Irrigações automáticas", "Comandos manuais", "Comandos recusados", "Tempo irrigando");
        int i = 0;
        for (IndicadoresDTO.PorTalhao p : ac.porTalhao()) {
            Color fundo = i++ % 2 == 1 ? ZEBRA : null;
            t.addCell(celula(d.nomeTalhao(p.talhaoId()), fonte(8.5f, Font.BOLD, TEXTO), fundo, Element.ALIGN_LEFT));
            t.addCell(celula(String.valueOf(p.automaticos()), corpo(), fundo, Element.ALIGN_RIGHT));
            t.addCell(celula(String.valueOf(p.manuais()), corpo(), fundo, Element.ALIGN_RIGHT));
            t.addCell(celula(String.valueOf(p.recusados()), corpo(), fundo, Element.ALIGN_RIGHT));
            Double tempo = d.tempoIrrigandoPct().get(p.talhaoId());
            t.addCell(celula(tempo == null ? "--" : pct(tempo), corpo(), fundo, Element.ALIGN_RIGHT));
        }
        Color total = new Color(0xEC, 0xF1, 0xEC);
        t.addCell(celula("Total", fonte(8.5f, Font.BOLD, TEXTO), total, Element.ALIGN_LEFT));
        t.addCell(celula(String.valueOf(ac.irrigacoesAutomaticas()), fonte(8.5f, Font.BOLD, TEXTO), total, Element.ALIGN_RIGHT));
        t.addCell(celula(String.valueOf(ac.comandosManuais()), fonte(8.5f, Font.BOLD, TEXTO), total, Element.ALIGN_RIGHT));
        t.addCell(celula(String.valueOf(ac.comandosRecusados()), fonte(8.5f, Font.BOLD, TEXTO), total, Element.ALIGN_RIGHT));
        t.addCell(celula("", corpo(), total, Element.ALIGN_RIGHT));
        doc.add(t);

        EnergiaDTO e = d.telemetria().energia();
        double solarPct = e.energiaConsumidaKwh() > 0 ? e.energiaSolarKwh() / e.energiaConsumidaKwh() * 100 : 100;
        PdfPTable en = new PdfPTable(new float[] {1.25f, 1.6f, 1.25f, 1.6f});
        en.setWidthPercentage(100);
        en.setSpacingBefore(8);
        en.setKeepTogether(true);
        par(en, "Energia das bombas", decimal(e.energiaConsumidaKwh()) + " kWh",
                "Energia solar usada", decimal(e.energiaSolarKwh()) + " kWh (" + Math.round(solarPct) + "%)");
        par(en, "Energia da rede", decimal(e.energiaRedeKwh()) + " kWh",
                "Usina solar", decimal(e.potenciaSolarPicoKw()) + " kWp · gerando " + decimal(e.geracaoSolarKw()) + " kW");
        par(en, "Água consumida", decimal(d.relatorio().aguaConsumidaM3()) + " m³",
                "Bloqueios de emergência", String.valueOf(ac.bloqueiosEmergencia()));
        doc.add(en);
    }

    // ---- Alertas e eventos ------------------------------------------------------------------------------

    private void alertasEEventos(Document doc, PdfWriter w, DadosExportacao d) throws DocumentException {
        secao(doc, w, "5", "Alertas ativos", null);
        List<AlertaDTO> alertas = d.telemetria().alertas();
        if (alertas.isEmpty()) {
            doc.add(par("Nenhum alerta ativo no momento da geração do relatório.", fonte(9, Font.NORMAL, OK), 0, 0));
        } else {
            PdfPTable t = tabela(new float[] {0.9f, 2f, 3.4f}, "Nível", "Alerta", "Detalhe");
            int i = 0;
            for (AlertaDTO a : alertas) {
                Color fundo = i++ % 2 == 1 ? ZEBRA : null;
                t.addCell(celula(Rotulos.severidade(a.nivel()), fonte(8.5f, Font.BOLD, corSeveridade(a.nivel())), fundo, Element.ALIGN_LEFT));
                t.addCell(celula(a.titulo(), fonte(8.5f, Font.BOLD, TEXTO), fundo, Element.ALIGN_LEFT));
                t.addCell(celula(a.mensagem(), corpo(), fundo, Element.ALIGN_LEFT));
            }
            doc.add(t);
        }

        List<EventoDTO> importantes = new ArrayList<>(d.eventos().stream()
                .filter(e -> EVENTOS_IMPORTANTES.contains(e.tipo())).toList());
        java.util.Collections.reverse(importantes);
        List<EventoDTO> exibidos = importantes.stream().limit(MAX_EVENTOS_PDF).toList();
        secao(doc, w, "6", "Eventos importantes",
                importantes.size() > MAX_EVENTOS_PDF
                        ? "Os " + MAX_EVENTOS_PDF + " mais recentes de " + importantes.size()
                                + ". O histórico completo, com filtros, está na planilha Excel (aba Eventos)."
                        : importantes.isEmpty() ? null : "Mais recentes primeiro.");
        if (exibidos.isEmpty()) {
            doc.add(par("Nenhum evento importante registrado no período.", corpo(), 0, 0));
            return;
        }
        PdfPTable t = tabela(new float[] {1.05f, 1.35f, 0.55f, 4.2f}, "Data/hora", "Tipo", "Talhão", "Descrição");
        int i = 0;
        for (EventoDTO e : exibidos) {
            Color fundo = i++ % 2 == 1 ? ZEBRA : null;
            t.addCell(celula(DATA_HORA_SEG.format(d.local(e.instante())), corpo(), fundo, Element.ALIGN_LEFT));
            t.addCell(celula(Rotulos.tipo(e.tipo()), fonte(8.5f, Font.BOLD, corSeveridade(e.severidade())), fundo, Element.ALIGN_LEFT));
            t.addCell(celula(e.talhaoId() == null ? "--" : e.talhaoId(), corpo(), fundo, Element.ALIGN_CENTER));
            // Tipo e talhao ja estao nas colunas: a descricao basta (linhas mais curtas, menos paginas)
            t.addCell(celula(e.descricao(), fonte(8.2f, Font.NORMAL, TEXTO), fundo, Element.ALIGN_LEFT));
        }
        doc.add(t);
    }

    private void observacoes(Document doc, DadosExportacao d) throws DocumentException {
        String amostra = d.serie().size() > MAX_PONTOS_GRAFICO
                ? "Os gráficos resumem as " + d.totalLeituras() + " leituras gravadas no período em até "
                        + MAX_PONTOS_GRAFICO + " pontos por linha, distribuídos uniformemente. "
                : "Os gráficos usam as " + d.totalLeituras() + " leituras gravadas no período. ";
        Paragraph p = par("Sobre este relatório: dados lidos do PostgreSQL e do estado atual da automação no momento da "
                + "geração. O período começa na última restauração do cenário (ou no início da simulação). " + amostra
                + "Para análises detalhadas, use a exportação em Excel.", fonte(7.5f, Font.ITALIC, MUTED), 16, 0);
        doc.add(p);
    }

    // ---- Blocos reutilizaveis ---------------------------------------------------------------------------

    private void secao(Document doc, PdfWriter w, String numero, String titulo, String subtitulo) throws DocumentException {
        // Evita titulo "orfao" no pe da pagina: sem espaco para o titulo e o inicio do conteudo, pula a pagina
        if (w.getVerticalPosition(true) - doc.bottom() < 130) {
            doc.newPage();
        }
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(16);
        t.setSpacingAfter(subtitulo == null ? 8 : 6);
        t.setKeepTogether(true);
        Phrase frase = new Phrase();
        frase.add(new Chunk(numero + "   ", fonte(12, Font.BOLD, LARANJA)));
        frase.add(new Chunk(limpar(titulo), fonte(13, Font.BOLD, VERDE)));
        PdfPCell c = new PdfPCell(frase);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(VERDE);
        c.setBorderWidthBottom(1.2f);
        c.setPaddingLeft(0);
        c.setPaddingBottom(5);
        t.addCell(c);
        if (subtitulo != null) {
            PdfPCell s = new PdfPCell(new Phrase(limpar(subtitulo), fonte(8, Font.NORMAL, MUTED)));
            s.setBorder(Rectangle.NO_BORDER);
            s.setPaddingLeft(0);
            s.setPaddingTop(4);
            t.addCell(s);
        }
        doc.add(t);
    }

    private void grafico(Document doc, String titulo, Image imagem) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setKeepTogether(true);
        PdfPCell rot = new PdfPCell(new Phrase(limpar(titulo), fonte(8.5f, Font.BOLD, TEXTO_2)));
        rot.setBorder(Rectangle.NO_BORDER);
        rot.setPaddingLeft(0);
        rot.setPaddingBottom(4);
        t.addCell(rot);
        PdfPCell img = new PdfPCell(imagem, false);
        img.setBorder(Rectangle.NO_BORDER);
        img.setPadding(0);
        t.addCell(img);
        doc.add(t);
    }

    private PdfPCell kpi(String rotulo, String valor, String detalhe) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDA);
        c.setBorderWidth(0.6f);
        c.setBackgroundColor(CARTAO);
        c.setPadding(8);
        c.setPaddingBottom(10);
        c.addElement(par(rotulo.toUpperCase(PT_BR), fonte(6.8f, Font.BOLD, MUTED), 2));
        c.addElement(par(valor, fonte(16, Font.BOLD, TEXTO), 2));
        c.addElement(par(detalhe, fonte(7.3f, Font.NORMAL, TEXTO_2), 0));
        return c;
    }

    private PdfPTable tabela(float[] larguras, String... cabecalhos) {
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSplitRows(false);
        for (String cab : cabecalhos) {
            PdfPCell c = new PdfPCell(new Phrase(limpar(cab), fonte(8, Font.BOLD, Color.WHITE)));
            c.setBackgroundColor(VERDE);
            c.setBorderColor(VERDE);
            c.setPadding(5);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(c);
        }
        return t;
    }

    /** Linha de 4 colunas: rotulo, valor, rotulo, valor. */
    private void par(PdfPTable t, String r1, String v1, String r2, String v2) {
        t.addCell(celulaRotulo(r1));
        t.addCell(celula(v1, fonte(8.8f, Font.BOLD, TEXTO), null, Element.ALIGN_LEFT));
        t.addCell(celulaRotulo(r2));
        t.addCell(celula(v2, fonte(8.8f, Font.BOLD, TEXTO), null, Element.ALIGN_LEFT));
    }

    private PdfPCell celulaRotulo(String texto) {
        return celula(texto, fonte(8.3f, Font.NORMAL, MUTED), ZEBRA, Element.ALIGN_LEFT);
    }

    private PdfPCell celula(String texto, Font fonte, Color fundo, int alinhamento) {
        PdfPCell c = new PdfPCell(new Phrase(limpar(texto), fonte));
        estilizar(c, fundo, alinhamento);
        return c;
    }

    private static void estilizar(PdfPCell c, Color fundo, int alinhamento) {
        c.setBorderColor(BORDA);
        c.setBorderWidth(0.5f);
        c.setPadding(4.5f);
        c.setPaddingBottom(5.5f);
        c.setHorizontalAlignment(alinhamento);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (fundo != null) {
            c.setBackgroundColor(fundo);
        }
    }

    private Paragraph linhaRotulo(String rotulo, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(limpar(rotulo) + " ", fonte(9, Font.BOLD, TEXTO_2)));
        p.add(new Chunk(limpar(nulo(valor)), fonte(9, Font.NORMAL, TEXTO)));
        p.setSpacingAfter(1.5f);
        return p;
    }

    private static Paragraph par(String texto, Font fonte, float depois) {
        return par(texto, fonte, 0, depois);
    }

    private static Paragraph par(String texto, Font fonte, float antes, float depois) {
        Paragraph p = new Paragraph(limpar(texto), fonte);
        p.setSpacingBefore(antes);
        p.setSpacingAfter(depois);
        p.setLeading(fonte.getSize() * 1.25f);
        return p;
    }

    private static Font corpo() {
        return fonte(8.5f, Font.NORMAL, TEXTO);
    }

    static Font fonte(float tamanho, int estilo, Color cor) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, tamanho, estilo, cor);
    }

    // ---- Dados auxiliares ---------------------------------------------------------------------------------

    private static List<HistoricoDTO.Ponto> reduzir(List<HistoricoDTO.Ponto> serie) {
        if (serie.size() <= MAX_PONTOS_GRAFICO) {
            return serie;
        }
        List<HistoricoDTO.Ponto> r = new ArrayList<>();
        double passo = (serie.size() - 1) / (double) (MAX_PONTOS_GRAFICO - 1);
        for (int i = 0; i < MAX_PONTOS_GRAFICO; i++) {
            r.add(serie.get((int) Math.round(i * passo)));
        }
        return r;
    }

    private static List<String> rotulosTempo(DadosExportacao d, List<HistoricoDTO.Ponto> pontos) {
        Instant ini = pontos.get(0).instante();
        Instant fim = pontos.get(pontos.size() - 1).instante();
        DateTimeFormatter f = Duration.between(ini, fim).toHours() >= 20 ? DIA_HORA : HORA;
        return pontos.stream().map(p -> f.format(d.local(p.instante()))).toList();
    }

    private static float larguraUtil(Document doc) {
        return doc.getPageSize().getWidth() - doc.leftMargin() - doc.rightMargin();
    }

    private static String periodoCurto(DadosExportacao d) {
        RelatorioDTO.Periodo p = d.relatorio().periodo();
        return DIA_HORA.format(d.local(p.inicio())) + " a " + DIA_HORA.format(d.local(p.fim()));
    }

    private static String duracao(long minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        long h = minutos / 60;
        long m = minutos % 60;
        return h + " h" + (m > 0 ? " " + m + " min" : "");
    }

    static Color corStatus(StatusSistema s) {
        return switch (s) {
            case NORMAL -> OK;
            case ATENCAO -> ATENCAO;
            case RISCO_HIDRICO, EMERGENCIA -> CRITICO;
        };
    }

    private static Color fundoStatus(StatusSistema s) {
        return switch (s) {
            case NORMAL -> OK_BG;
            case ATENCAO -> ATENCAO_BG;
            case RISCO_HIDRICO, EMERGENCIA -> CRITICO_BG;
        };
    }

    private static Color corSeveridade(Severidade s) {
        if (s == null) {
            return TEXTO_2;
        }
        return switch (s) {
            case INFO -> AZUL;
            case SUCESSO -> OK;
            case ATENCAO -> ATENCAO;
            case CRITICO, EMERGENCIA -> CRITICO;
        };
    }

    private static String pct(double v) {
        return Formatador.pct(v);
    }

    private static String pctNulo(Double v) {
        return v == null ? "--" : Formatador.pct(v);
    }

    private static String decimal(double v) {
        return Formatador.num(v);
    }

    private static String inteiro(double v) {
        return String.format(PT_BR, "%,.0f", v);
    }

    private static String sinal(double v) {
        return (v > 0 ? "+" : "") + Formatador.num(v);
    }

    private static String nulo(String s) {
        return s == null || s.isBlank() ? "--" : s;
    }

    /**
     * As fontes padrao do PDF usam a codificacao Windows-1252 (acentos do portugues funcionam); simbolos fora
     * dela (setas, emojis) sao trocados por equivalentes em texto ou removidos.
     */
    static String limpar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = texto.replace("→", "->").replace("≥", ">=").replace("≤", "<=").replace("✓", "")
                .replace(" ", " ");
        CharsetEncoder enc = CP1252.newEncoder();
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (enc.canEncode(ch)) {
                sb.append(ch);
            }
        });
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    /** Cabecalho (paginas 2+) e rodape com "Pagina X de Y" em todas as paginas. */
    private static final class Paginacao extends PdfPageEventHelper {

        private final Image emblema;
        private final String geradoEm;
        private final String periodo;
        private PdfTemplate total;
        private BaseFont normal;
        private BaseFont negrito;

        Paginacao(Image emblema, String geradoEm, String periodo) {
            this.emblema = emblema;
            this.geradoEm = geradoEm;
            this.periodo = periodo;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                negrito = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            total = writer.getDirectContent().createTemplate(24, 10);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float esq = document.left();
            float dir = document.right();
            float altura = document.getPageSize().getHeight();
            cb.saveState();
            if (writer.getPageNumber() > 1) {
                try {
                    emblema.scaleToFit(22, 22);
                    emblema.setAbsolutePosition(esq, altura - 42);
                    cb.addImage(emblema);
                } catch (DocumentException e) {
                    throw new IllegalStateException(e);
                }
                cb.beginText();
                cb.setColorFill(VERDE);
                cb.setFontAndSize(negrito, 9.5f);
                cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "BioSolar Citrus", esq + 28, altura - 34, 0);
                cb.setColorFill(MUTED);
                cb.setFontAndSize(normal, 8.5f);
                cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Relatório Operacional",
                        esq + 30 + negrito.getWidthPoint("BioSolar Citrus", 9.5f), altura - 34, 0);
                cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "Período: " + periodo, dir, altura - 34, 0);
                cb.endText();
                cb.setColorStroke(BORDA);
                cb.setLineWidth(0.6f);
                cb.moveTo(esq, altura - 48);
                cb.lineTo(dir, altura - 48);
                cb.stroke();
            }
            cb.setColorStroke(BORDA);
            cb.setLineWidth(0.6f);
            cb.moveTo(esq, 38);
            cb.lineTo(dir, 38);
            cb.stroke();
            String pagina = "Página " + writer.getPageNumber() + " de ";
            float largura = normal.getWidthPoint(pagina, 8);
            float x = dir - largura - 12;
            cb.beginText();
            cb.setColorFill(MUTED);
            cb.setFontAndSize(normal, 8);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Gerado em " + geradoEm
                    + " · BioSolar Citrus · Centro Inteligente de Automação Hidro-Energética", esq, 26, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, pagina, x, 26, 0);
            cb.endText();
            cb.addTemplate(total, x + largura, 26);
            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(normal, 8);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
