package br.com.biosolar.citrus.exception;

/** Recurso inexistente (ex.: talhao desconhecido) -> HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}