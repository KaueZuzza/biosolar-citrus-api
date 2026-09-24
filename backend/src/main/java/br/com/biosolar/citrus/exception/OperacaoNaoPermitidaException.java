package br.com.biosolar.citrus.exception;

/** Operacao desabilitada por configuracao (ex.: controles de simulacao) -> HTTP 403. */
public class OperacaoNaoPermitidaException extends RuntimeException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}