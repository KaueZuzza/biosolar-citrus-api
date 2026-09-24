package br.com.biosolar.citrus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /bombas/acionar. Ex.: {"talhaoId": "A", "ligado": true} */
public record AcionamentoRequest(
        @NotBlank(message = "informe o talhaoId (ex.: \"A\")")
        @Size(max = 10, message = "talhaoId deve ter no maximo 10 caracteres")
        String talhaoId,

        @NotNull(message = "informe ligado: true para ligar ou false para desligar")
        Boolean ligado) {
}