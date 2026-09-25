package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

/** Respostas de /exportacao/email. Nunca incluem usuario ou senha do SMTP. */
public final class EmailDTO {

    private EmailDTO() {
    }

    /** GET /exportacao/email: se o envio esta disponivel e de qual endereco ele sai. */
    public record Status(boolean configurado, String remetente, int limitePorHora, String orientacao) {
    }

    /** POST /exportacao/email: resultado do envio. */
    public record Resultado(boolean sucesso, String mensagem, List<String> destinatarios, List<String> anexos,
                            Instant enviadoEm) {
    }
}
