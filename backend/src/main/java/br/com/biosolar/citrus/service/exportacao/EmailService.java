package br.com.biosolar.citrus.service.exportacao;

import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import br.com.biosolar.citrus.config.EmailProperties;
import br.com.biosolar.citrus.dto.EmailDTO;
import br.com.biosolar.citrus.dto.EmailRequest;
import br.com.biosolar.citrus.exception.DadosInvalidosException;
import br.com.biosolar.citrus.exception.ExportacaoException;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Envia o relatorio (PDF, Excel ou ambos) por e-mail pelo servidor SMTP configurado no .env.
 * As credenciais ficam so no servidor; o navegador informa apenas destinatario, assunto e mensagem.
 * Cada envio vira um evento RELATORIO no historico (com o e-mail mascarado).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_DESTINATARIOS = 5;
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    static final String TIPO_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final EmailProperties props;
    private final ColetaExportacaoService coleta;
    private final RelatorioPdfService pdf;
    private final RelatorioExcelService excel;
    private final CompartilhamentoService compartilhamento;
    private final EstadoFazendaService estado;
    private final Clock clock;
    private final Deque<Instant> envios = new ArrayDeque<>();
    private final byte[] emblema = RelatorioPdfService.recurso("relatorio/logo-emblema.png");

    public EmailService(EmailProperties props, ColetaExportacaoService coleta, RelatorioPdfService pdf,
                        RelatorioExcelService excel, CompartilhamentoService compartilhamento,
                        EstadoFazendaService estado, Clock clock) {
        this.props = props;
        this.coleta = coleta;
        this.pdf = pdf;
        this.excel = excel;
        this.compartilhamento = compartilhamento;
        this.estado = estado;
        this.clock = clock;
    }

    public EmailDTO.Status status() {
        if (!props.configurado()) {
            return new EmailDTO.Status(false, null, props.limitePorHora(),
                    "Envio de e-mail desativado: defina BIOSOLAR_SMTP_HOST, BIOSOLAR_SMTP_USUARIO e BIOSOLAR_SMTP_SENHA "
                            + "no arquivo .env do servidor e reinicie a API.");
        }
        return new EmailDTO.Status(true, props.remetenteEfetivo(), props.limitePorHora(),
                "O e-mail é enviado pelo servidor, a partir de " + props.remetenteEfetivo() + ".");
    }

    public EmailDTO.Resultado enviar(EmailRequest req) {
        if (!props.configurado()) {
            throw new ExportacaoException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NAO_CONFIGURADO",
                    "O envio de e-mail ainda não foi configurado no servidor. Defina BIOSOLAR_SMTP_HOST, "
                            + "BIOSOLAR_SMTP_USUARIO e BIOSOLAR_SMTP_SENHA no arquivo .env e reinicie a API.");
        }
        List<String> destinatarios = validarDestinatarios(req.destinatarios());
        verificarLimite();

        Instant agora = clock.instant();
        String carimbo = ARQUIVO.format(agora.atZone(clock.getZone()));
        DadosExportacao dados = coleta.coletar();
        boolean comPdf = req.anexo() != EmailRequest.Anexo.EXCEL;
        boolean comExcel = req.anexo() != EmailRequest.Anexo.PDF;
        List<String> anexos = new ArrayList<>();

        String assunto = limparLinha(req.assunto());
        if (assunto.isBlank()) {
            assunto = "BioSolar Citrus — Relatório operacional " + DATA.format(agora.atZone(clock.getZone()));
        }
        String resumo = compartilhamento.mensagemStatus();

        JavaMailSenderImpl remetente = criarRemetente();
        try {
            MimeMessage msg = remetente.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(props.remetenteEfetivo(), props.nomeRemetente());
            h.setTo(destinatarios.toArray(String[]::new));
            h.setSubject(assunto);
            h.setText(textoSimples(req.mensagem(), resumo, comPdf, comExcel),
                    html(req.mensagem(), resumo, comPdf, comExcel, DATA.format(agora.atZone(clock.getZone()))));
            h.addInline("logo", new ByteArrayResource(emblema), "image/png");
            if (comPdf) {
                String nome = "biosolar-relatorio-" + carimbo + ".pdf";
                h.addAttachment(nome, new ByteArrayResource(pdf.gerar(dados)), "application/pdf");
                anexos.add(nome);
            }
            if (comExcel) {
                String nome = "biosolar-relatorio-" + carimbo + ".xlsx";
                h.addAttachment(nome, new ByteArrayResource(excel.gerar(dados)), TIPO_EXCEL);
                anexos.add(nome);
            }
            remetente.send(msg);
        } catch (MailAuthenticationException e) {
            log.warn("SMTP recusou a autenticação: {}", e.getMessage());
            throw new ExportacaoException(HttpStatus.BAD_GATEWAY, "EMAIL_AUTENTICACAO",
                    "O servidor de e-mail recusou o usuário ou a senha configurados no .env (BIOSOLAR_SMTP_USUARIO / "
                            + "BIOSOLAR_SMTP_SENHA). No Gmail e no Outlook, use uma \"senha de app\".");
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("Falha ao enviar e-mail: {}", e.getMessage());
            throw new ExportacaoException(HttpStatus.BAD_GATEWAY, "EMAIL_FALHOU",
                    "Não foi possível enviar o e-mail agora (" + causa(e) + "). Verifique a conexão e o servidor SMTP.");
        }
        registrarEnvio(agora);

        String descricao = String.join(" e ", anexos.stream().map(a -> a.endsWith(".pdf") ? "PDF" : "Excel").toList())
                + " enviado para " + String.join(", ", destinatarios.stream().map(EmailService::mascarar).toList()) + ".";
        estado.executar(f -> {
            f.registrarEvento(TipoEvento.RELATORIO, Severidade.SUCESSO, OrigemEvento.OPERADOR, null, null,
                    "Relatório enviado por e-mail", descricao, agora);
            return null;
        });
        return new EmailDTO.Resultado(true, "Relatório enviado para " + String.join(", ", destinatarios) + ".",
                destinatarios, anexos, agora);
    }

    // ---- Validacao e protecao ---------------------------------------------------------------------------

    private static List<String> validarDestinatarios(String lista) {
        Set<String> enderecos = new LinkedHashSet<>();
        List<String> invalidos = new ArrayList<>();
        for (String bruto : Arrays.asList(lista.split("[,;\\s]+"))) {
            String email = bruto.trim();
            if (email.isEmpty()) {
                continue;
            }
            if (!FORMATO_EMAIL.matcher(email).matches() || !valido(email)) {
                invalidos.add(email);
            } else {
                enderecos.add(email.toLowerCase(Locale.ROOT));
            }
        }
        if (!invalidos.isEmpty()) {
            throw new DadosInvalidosException("E-mail de destinatário inválido.",
                    invalidos.stream().map(i -> "destinatarios: \"" + i + "\" não é um e-mail válido").toList());
        }
        if (enderecos.isEmpty()) {
            throw new DadosInvalidosException("Informe ao menos um destinatário.", List.of("destinatarios: vazio"));
        }
        if (enderecos.size() > MAX_DESTINATARIOS) {
            throw new DadosInvalidosException("Envie para no máximo " + MAX_DESTINATARIOS + " destinatários por vez.",
                    List.of("destinatarios: " + enderecos.size() + " informados"));
        }
        return List.copyOf(enderecos);
    }

    private static boolean valido(String email) {
        try {
            new InternetAddress(email, true).validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    /** Protecao contra uso abusivo do SMTP (a API nao tem login): no maximo N envios por hora. */
    private synchronized void verificarLimite() {
        Instant limite = clock.instant().minus(Duration.ofHours(1));
        while (!envios.isEmpty() && envios.peekFirst().isBefore(limite)) {
            envios.pollFirst();
        }
        if (envios.size() >= props.limitePorHora()) {
            throw new ExportacaoException(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_DE_ENVIOS",
                    "Limite de " + props.limitePorHora() + " e-mails por hora atingido. Tente novamente mais tarde.");
        }
    }

    private synchronized void registrarEnvio(Instant quando) {
        envios.addLast(quando);
    }

    private JavaMailSenderImpl criarRemetente() {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(props.host().trim());
        s.setPort(props.porta());
        s.setDefaultEncoding("UTF-8");
        boolean autenticar = props.usuario() != null && !props.usuario().isBlank();
        if (autenticar) {
            s.setUsername(props.usuario().trim());
            s.setPassword(props.senha());
        }
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(autenticar));
        p.put("mail.smtp.starttls.enable", String.valueOf(props.starttls()));
        p.put("mail.smtp.ssl.enable", String.valueOf(props.ssl()));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "20000");
        p.put("mail.smtp.writetimeout", "20000");
        return s;
    }

    // ---- Conteudo ------------------------------------------------------------------------------------------

    private static String textoSimples(String mensagem, String resumo, boolean comPdf, boolean comExcel) {
        StringBuilder sb = new StringBuilder();
        if (mensagem != null && !mensagem.isBlank()) {
            sb.append(mensagem.trim()).append("\n\n");
        }
        sb.append(resumo.replaceAll("\\*(.+?)\\*", "$1").replaceAll("_(.+?)_", "$1")).append("\n\n");
        sb.append("Em anexo: ").append(descricaoAnexos(comPdf, comExcel)).append(".\n");
        return sb.toString();
    }

    private static String html(String mensagem, String resumo, boolean comPdf, boolean comExcel, String data) {
        StringBuilder linhas = new StringBuilder();
        for (String linha : resumo.split("\n")) {
            if (linha.isBlank()) {
                continue;
            }
            String l = HtmlUtils.htmlEscape(linha).replaceAll("\\*(.+?)\\*", "<b>$1</b>").replaceAll("_(.+?)_", "<i>$1</i>");
            linhas.append("<div style=\"padding:3px 0\">").append(l).append("</div>");
        }
        String msg = mensagem == null || mensagem.isBlank() ? ""
                : "<p style=\"font-size:15px;line-height:1.5;margin:18px 0\">"
                        + HtmlUtils.htmlEscape(mensagem.trim()).replace("\n", "<br>") + "</p>";
        return """
                <div style="font-family:'Segoe UI',Arial,sans-serif;color:#1b241e;max-width:640px">
                  <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                    <td><img src="cid:logo" width="56" height="56" alt="BioSolar Citrus"></td>
                    <td style="padding-left:12px">
                      <div style="font-size:20px;font-weight:700;color:#1f6b3a">BioSolar Citrus</div>
                      <div style="font-size:13px;color:#6b756e">Relatório operacional · %s</div>
                    </td>
                  </tr></table>
                  %s
                  <div style="background:#f5f8f5;border-left:4px solid #1f6b3a;border-radius:6px;padding:12px 16px;margin:16px 0;font-size:14px">
                    %s
                  </div>
                  <p style="font-size:14px">Em anexo: <b>%s</b>.</p>
                  <p style="font-size:12px;color:#6b756e;border-top:1px solid #d5dcd6;padding-top:10px;margin-top:20px">
                    Enviado pelo servidor do BioSolar Citrus · Centro Inteligente de Automação Hidro-Energética.
                    Dados lidos do PostgreSQL e do estado atual da automação no momento do envio.
                  </p>
                </div>
                """.formatted(data, msg, linhas, descricaoAnexos(comPdf, comExcel));
    }

    private static String descricaoAnexos(boolean comPdf, boolean comExcel) {
        if (comPdf && comExcel) {
            return "relatório em PDF e planilha Excel";
        }
        return comPdf ? "relatório em PDF" : "planilha Excel";
    }

    private static String limparLinha(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    /** "gestor@fazenda.com" -> "g***@fazenda.com" (o historico nao guarda o endereco completo). */
    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }

    private static String causa(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return m.length() > 140 ? m.substring(0, 140) + "…" : m;
    }
}
