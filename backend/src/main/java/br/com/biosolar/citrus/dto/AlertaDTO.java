package br.com.biosolar.citrus.dto;

import br.com.biosolar.citrus.model.Severidade;

/** Alerta ativo no momento (calculado a partir do estado atual, diferente do historico de eventos). */
public record AlertaDTO(Severidade nivel, String titulo, String mensagem, String talhaoId) {
}