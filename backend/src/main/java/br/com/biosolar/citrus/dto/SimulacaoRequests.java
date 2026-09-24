package br.com.biosolar.citrus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Corpos das requisicoes do painel de simulacao (modo demonstracao). */
public final class SimulacaoRequests {

    private SimulacaoRequests() {
    }

    public record Velocidade(
            @NotNull(message = "informe o fator de velocidade")
            @Min(value = 1, message = "fator minimo: 1") @Max(value = 60, message = "fator maximo: 60")
            Integer fator) {
    }

    public record Pausa(@NotNull(message = "informe pausada: true ou false") Boolean pausada) {
    }

    public record AjusteUmidade(
            @NotBlank(message = "informe o talhaoId") String talhaoId,
            @NotNull(message = "informe o delta em pontos percentuais")
            @DecimalMin(value = "-50", message = "delta minimo: -50") @DecimalMax(value = "50", message = "delta maximo: 50")
            Double delta) {
    }

    public record AjusteReservatorio(
            @NotNull(message = "informe o delta em pontos percentuais")
            @DecimalMin(value = "-60", message = "delta minimo: -60") @DecimalMax(value = "60", message = "delta maximo: 60")
            Double delta) {
    }

    public record Resposta(boolean sucesso, String mensagem) {
    }
}