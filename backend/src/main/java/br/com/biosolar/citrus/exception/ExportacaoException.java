package br.com.biosolar.citrus.exception;

import org.springframework.http.HttpStatus;

/**
 * Falha em uma exportacao ou compartilhamento, com o status HTTP e o motivo que o frontend exibe.
 * Ex.: e-mail nao configurado (503), limite de envios atingido (429), servidor SMTP recusou (502).
 */
public class ExportacaoException extends RuntimeException {

    private final HttpStatus status;
    private final String motivo;

    public ExportacaoException(HttpStatus status, String motivo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.motivo = motivo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMotivo() {
        return motivo;
    }
}
