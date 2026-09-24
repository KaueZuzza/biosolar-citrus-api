package br.com.biosolar.citrus.dto;

import java.util.List;

import br.com.biosolar.citrus.model.Severidade;

/**
 * Decisao mais relevante do momento, respondendo: o que, onde, por que, qual acao automatica,
 * qual o risco, o que o operador deve fazer e qual o impacto.
 */
public record DecisaoDTO(
        Severidade nivel,
        String regra,
        String regraNome,
        String titulo,
        String oQue,
        String onde,
        String porque,
        String acaoAutomatica,
        String risco,
        String acaoOperador,
        String impacto,
        List<String> talhoes,
        List<String> regrasAtivas) {
}