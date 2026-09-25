package br.com.biosolar.citrus.controller;

import java.time.Clock;
import java.time.format.DateTimeFormatter;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.EmailDTO;
import br.com.biosolar.citrus.dto.EmailRequest;
import br.com.biosolar.citrus.service.exportacao.ColetaExportacaoService;
import br.com.biosolar.citrus.service.exportacao.CompartilhamentoService;
import br.com.biosolar.citrus.service.exportacao.EmailService;
import br.com.biosolar.citrus.service.exportacao.RelatorioExcelService;
import br.com.biosolar.citrus.service.exportacao.RelatorioPdfService;
import jakarta.validation.Valid;

/** Exportar (PDF, Excel) e compartilhar (WhatsApp, e-mail) o relatorio operacional. */
@RestController
@RequestMapping("/exportacao")
public class ExportacaoController {

    private static final DateTimeFormatter ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ColetaExportacaoService coleta;
    private final RelatorioPdfService pdf;
    private final RelatorioExcelService excel;
    private final CompartilhamentoService compartilhamento;
    private final EmailService email;
    private final Clock clock;

    public ExportacaoController(ColetaExportacaoService coleta, RelatorioPdfService pdf, RelatorioExcelService excel,
                                CompartilhamentoService compartilhamento, EmailService email, Clock clock) {
        this.coleta = coleta;
        this.pdf = pdf;
        this.excel = excel;
        this.compartilhamento = compartilhamento;
        this.email = email;
        this.clock = clock;
    }

    /** PDF do relatorio. Por padrao abre no navegador; {@code ?download=true} forca o download. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "false") boolean download) {
        byte[] conteudo = pdf.gerar(coleta.coletar());
        return arquivo(conteudo, MediaType.APPLICATION_PDF, nome("pdf"), download);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel() {
        byte[] conteudo = excel.gerar(coleta.coletar());
        return arquivo(conteudo, XLSX, nome("xlsx"), true);
    }

    /** Mensagem curta de status para o WhatsApp (texto + link wa.me pronto). */
    @GetMapping("/whatsapp")
    public CompartilhamentoService.Mensagem whatsapp() {
        return compartilhamento.whatsapp();
    }

    @GetMapping("/email")
    public EmailDTO.Status statusEmail() {
        return email.status();
    }

    @PostMapping("/email")
    public EmailDTO.Resultado enviarEmail(@Valid @RequestBody EmailRequest requisicao) {
        return email.enviar(requisicao);
    }

    private String nome(String extensao) {
        return "biosolar-relatorio-" + ARQUIVO.format(clock.instant().atZone(clock.getZone())) + "." + extensao;
    }

    private static ResponseEntity<byte[]> arquivo(byte[] conteudo, MediaType tipo, String nome, boolean download) {
        ContentDisposition disposicao = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(nome).build();
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(conteudo);
    }
}
