package br.com.biosolar.citrus.exception;

import java.util.List;

/** Dados coerentes campo a campo, mas invalidos em conjunto (ex.: alvo abaixo da atencao) -> HTTP 400. */
public class DadosInvalidosException extends RuntimeException {

    private final List<String> detalhes;

    public DadosInvalidosException(String mensagem, List<String> detalhes) {
        super(mensagem);
        this.detalhes = List.copyOf(detalhes);
    }

    public List<String> getDetalhes() {
        return detalhes;
    }
}
