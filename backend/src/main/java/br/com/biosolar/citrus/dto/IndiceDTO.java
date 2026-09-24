package br.com.biosolar.citrus.dto;

import java.util.List;

/**
 * Indice Hidro-Energetico (0-100) com os fatores que o compoem, para exibicao em "Como e calculado?".
 */
public record IndiceDTO(
        int valor,
        String classificacao,
        String rotulo,
        String formula,
        double valorSemTrava,
        boolean travaEmergencia,
        List<Fator> fatores) {

    public record Fator(String codigo, String nome, double valor, double peso, double contribuicao,
                        String descricao) {
    }
}