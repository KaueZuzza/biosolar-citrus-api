package br.com.biosolar.citrus.exception;

/**
 * O banco de dados esta fora do ar. Alteracoes de cadastro precisam ser gravadas para valer, entao sao
 * recusadas; a automacao continua operando em memoria -> HTTP 503.
 */
public class BancoIndisponivelException extends RuntimeException {

    public BancoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
