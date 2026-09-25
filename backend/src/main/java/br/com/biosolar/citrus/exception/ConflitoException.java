package br.com.biosolar.citrus.exception;

/** Operacao incompativel com o estado atual (ex.: codigo de talhao ja usado) -> HTTP 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
