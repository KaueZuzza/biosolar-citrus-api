package br.com.biosolar.citrus.exception;

import java.time.Instant;
import java.util.List;

/** Corpo padrao de erro, no mesmo formato das respostas de comando ({sucesso, motivo, mensagem}). */
public record ErroResponse(
        boolean sucesso,
        String motivo,
        String mensagem,
        int status,
        Instant instante,
        List<String> detalhes) {

    public static ErroResponse de(int status, String motivo, String mensagem, List<String> detalhes) {
        return new ErroResponse(false, motivo, mensagem, status, Instant.now(), detalhes);
    }
}