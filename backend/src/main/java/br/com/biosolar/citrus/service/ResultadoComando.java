package br.com.biosolar.citrus.service;

/** Resultado da validacao de um comando manual pelo motor de regras. */
public record ResultadoComando(boolean sucesso, String motivo, String mensagem) {

    public static final String COMANDO_EXECUTADO = "COMANDO_EXECUTADO";
    public static final String SEM_ALTERACAO = "SEM_ALTERACAO";
    public static final String BLOQUEIO_DE_EMERGENCIA = "BLOQUEIO_DE_EMERGENCIA";
    public static final String IRRIGACAO_CRITICA_EM_ANDAMENTO = "IRRIGACAO_CRITICA_EM_ANDAMENTO";
    public static final String SOLO_SATURADO = "SOLO_SATURADO";

    public static ResultadoComando executado(String mensagem) {
        return new ResultadoComando(true, COMANDO_EXECUTADO, mensagem);
    }

    public static ResultadoComando semAlteracao(String mensagem) {
        return new ResultadoComando(true, SEM_ALTERACAO, mensagem);
    }

    public static ResultadoComando recusado(String motivo, String mensagem) {
        return new ResultadoComando(false, motivo, mensagem);
    }
}
