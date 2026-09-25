package br.com.biosolar.citrus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /exportacao/email.
 * Ex.: {"destinatarios": "gestor@fazenda.com", "assunto": "Relatório", "mensagem": "Segue.", "anexo": "PDF"}
 */
public record EmailRequest(
        @NotBlank(message = "informe o e-mail do destinatário")
        @Size(max = 500, message = "lista de destinatários muito longa")
        String destinatarios,
        @Size(max = 150, message = "o assunto deve ter no máximo 150 caracteres")
        String assunto,
        @Size(max = 2000, message = "a mensagem deve ter no máximo 2000 caracteres")
        String mensagem,
        @NotNull(message = "escolha o anexo: PDF, EXCEL ou AMBOS")
        Anexo anexo) {

    public enum Anexo {
        PDF,
        EXCEL,
        AMBOS
    }
}
