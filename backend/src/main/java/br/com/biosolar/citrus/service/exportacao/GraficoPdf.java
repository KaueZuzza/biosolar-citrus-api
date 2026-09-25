package br.com.biosolar.citrus.service.exportacao;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Grafico de linhas vetorial para o PDF (escala fixa 0-100%), no mesmo padrao do dashboard:
 * linhas de 1,4 pt, grade discreta, faixas de risco, linhas de referencia tracejadas com rotulo
 * e legenda quando ha mais de uma serie.
 */
final class GraficoPdf {

    record Serie(String nome, Color cor, double[] valores) {
    }

    record Referencia(double valor, String rotulo, Color cor) {
    }

    record Faixa(double de, double ate, Color cor) {
    }

    private static final Color FUNDO = new Color(0xFB, 0xFC, 0xFB);
    private static final Color GRADE = new Color(0xE1, 0xE6, 0xE2);
    private static final Color EIXO = new Color(0x9A, 0xA4, 0x9C);
    private static final Color ROTULO = new Color(0x6B, 0x75, 0x6E);
    private static final Color TEXTO = new Color(0x1B, 0x24, 0x1E);

    private GraficoPdf() {
    }

    static Image linhas(PdfWriter writer, float largura, float altura, List<String> rotulosX, List<Serie> series,
                        List<Referencia> referencias, List<Faixa> faixas, boolean area) {
        try {
            BaseFont normal = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont negrito = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            PdfTemplate t = writer.getDirectContent().createTemplate(largura, altura);

            float legenda = series.size() > 1 ? 16 : 0;
            float ml = 34;
            float mr = 12;
            float mb = 20;
            float mt = 10 + legenda;
            float iw = largura - ml - mr;
            float ih = altura - mt - mb;
            int n = rotulosX.size();

            t.setColorFill(FUNDO);
            t.roundRectangle(0, 0, largura, altura, 5);
            t.fill();

            for (Faixa f : faixas) {
                t.saveState();
                PdfGState transparencia = new PdfGState();
                transparencia.setFillOpacity(0.13f);
                t.setGState(transparencia);
                t.setColorFill(f.cor());
                t.rectangle(ml, y(f.de(), mb, ih), iw, y(f.ate(), mb, ih) - y(f.de(), mb, ih));
                t.fill();
                t.restoreState();
            }

            t.setLineWidth(0.4f);
            t.setColorStroke(GRADE);
            for (int v = 0; v <= 100; v += 25) {
                t.moveTo(ml, y(v, mb, ih));
                t.lineTo(ml + iw, y(v, mb, ih));
            }
            t.stroke();
            t.setLineWidth(0.7f);
            t.setColorStroke(EIXO);
            t.moveTo(ml, mb);
            t.lineTo(ml + iw, mb);
            t.stroke();

            t.beginText();
            t.setFontAndSize(normal, 6.5f);
            t.setColorFill(ROTULO);
            for (int v = 0; v <= 100; v += 25) {
                t.showTextAligned(PdfContentByte.ALIGN_RIGHT, v + "%", ml - 5, y(v, mb, ih) - 2.2f, 0);
            }
            int marcas = Math.min(5, n);
            for (int k = 0; k < marcas && n > 1; k++) {
                int i = Math.round(k * (n - 1) / (float) (marcas - 1));
                int alinhamento = k == 0 ? PdfContentByte.ALIGN_LEFT
                        : k == marcas - 1 ? PdfContentByte.ALIGN_RIGHT : PdfContentByte.ALIGN_CENTER;
                t.showTextAligned(alinhamento, rotulosX.get(i), x(i, n, ml, iw), 7, 0);
            }
            t.endText();

            for (Referencia r : referencias) {
                float yy = y(r.valor(), mb, ih);
                t.saveState();
                t.setLineDash(3f, 2.5f, 0f);
                t.setLineWidth(0.9f);
                t.setColorStroke(r.cor());
                t.moveTo(ml, yy);
                t.lineTo(ml + iw, yy);
                t.stroke();
                t.restoreState();
            }

            for (Serie s : series) {
                if (area) {
                    desenharArea(t, s, n, ml, iw, mb, ih);
                }
                t.setLineWidth(1.4f);
                t.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
                t.setLineCap(PdfContentByte.LINE_CAP_ROUND);
                t.setColorStroke(s.cor());
                boolean aberto = false;
                int ultimo = -1;
                for (int i = 0; i < n; i++) {
                    double v = s.valores()[i];
                    if (Double.isNaN(v)) {
                        aberto = false;
                        continue;
                    }
                    if (aberto) {
                        t.lineTo(x(i, n, ml, iw), y(v, mb, ih));
                    } else {
                        t.moveTo(x(i, n, ml, iw), y(v, mb, ih));
                        aberto = true;
                    }
                    ultimo = i;
                }
                t.stroke();
                if (ultimo >= 0) {
                    t.setColorFill(s.cor());
                    t.circle(x(ultimo, n, ml, iw), y(s.valores()[ultimo], mb, ih), 2.2f);
                    t.fill();
                }
            }

            // Rotulos das referencias por cima das linhas, com fundo, para nao serem cobertos pelas series
            for (Referencia r : referencias) {
                float yy = y(r.valor(), mb, ih);
                float larguraRotulo = negrito.getWidthPoint(r.rotulo(), 6.5f);
                t.setColorFill(FUNDO);
                t.roundRectangle(ml + 2, yy + 1.2f, larguraRotulo + 6, 8.4f, 2);
                t.fill();
                t.beginText();
                t.setFontAndSize(negrito, 6.5f);
                t.setColorFill(r.cor());
                t.showTextAligned(PdfContentByte.ALIGN_LEFT, r.rotulo(), ml + 5, yy + 3.2f, 0);
                t.endText();
            }

            if (legenda > 0) {
                float cx = ml;
                float cy = altura - 11;
                for (Serie s : series) {
                    t.setColorFill(s.cor());
                    t.roundRectangle(cx, cy, 9, 3.2f, 1.2f);
                    t.fill();
                    t.beginText();
                    t.setFontAndSize(normal, 7f);
                    t.setColorFill(TEXTO);
                    t.showTextAligned(PdfContentByte.ALIGN_LEFT, s.nome(), cx + 12, cy - 0.8f, 0);
                    t.endText();
                    cx += 12 + normal.getWidthPoint(s.nome(), 7f) + 14;
                }
            }
            return Image.getInstance(t);
        } catch (IOException | BadElementException e) {
            throw new IllegalStateException("Falha ao desenhar o gráfico do relatório", e);
        }
    }

    private static void desenharArea(PdfTemplate t, Serie s, int n, float ml, float iw, float mb, float ih) {
        int primeiro = -1;
        int ultimo = -1;
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(s.valores()[i])) {
                if (primeiro < 0) {
                    primeiro = i;
                }
                ultimo = i;
            }
        }
        if (primeiro < 0 || primeiro == ultimo) {
            return;
        }
        t.saveState();
        PdfGState transparencia = new PdfGState();
        transparencia.setFillOpacity(0.14f);
        t.setGState(transparencia);
        t.setColorFill(s.cor());
        t.moveTo(x(primeiro, n, ml, iw), mb);
        for (int i = primeiro; i <= ultimo; i++) {
            double v = s.valores()[i];
            if (!Double.isNaN(v)) {
                t.lineTo(x(i, n, ml, iw), y(v, mb, ih));
            }
        }
        t.lineTo(x(ultimo, n, ml, iw), mb);
        t.closePath();
        t.fill();
        t.restoreState();
    }

    private static float x(int i, int n, float ml, float iw) {
        return n <= 1 ? ml : ml + i * iw / (n - 1);
    }

    private static float y(double valor, float mb, float ih) {
        return (float) (mb + Math.max(0, Math.min(100, valor)) / 100.0 * ih);
    }
}
