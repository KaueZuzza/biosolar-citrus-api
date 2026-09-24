package br.com.biosolar.citrus.dto;

import br.com.biosolar.citrus.model.StatusSistema;

public record StatusSistemaDTO(StatusSistema codigo, String rotulo, String emoji, String descricao) {

    public static StatusSistemaDTO de(StatusSistema status, String descricao) {
        return new StatusSistemaDTO(status, status.getRotulo(), status.getEmoji(), descricao);
    }
}