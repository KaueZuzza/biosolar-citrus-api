package br.com.biosolar.citrus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** GET /configuracao: cadastro do reservatorio e da usina, e os limites fixos das regras de seguranca. */
public record ConfiguracaoDTO(
        Reservatorio reservatorio,
        Usina usina,
        Regras regras,
        boolean bancoDisponivel) {

    public record Reservatorio(String nome, double capacidadeM3, double vazaoRecargaM3h, double nivelInicial,
                               double limiteAtencao, double limiteCritico, double limiteRearme) {
    }

    public record Usina(double potenciaSolarPicoKw) {
    }

    /** Limites definidos pelo regulamento (exibidos, nao editaveis pela interface). */
    public record Regras(double limiteCriticoTalhao, double limiteSaturacao, int maximoTalhoes) {
    }

    /** PUT /configuracao/reservatorio. */
    public record ReservatorioRequest(
            @NotBlank(message = "informe o nome") @Size(max = 60, message = "nome com no maximo 60 caracteres")
            String nome,
            @NotNull(message = "informe a capacidade")
            @DecimalMin(value = "10", message = "capacidade minima: 10 m3") @DecimalMax(value = "1000000", message = "capacidade maxima: 1.000.000 m3")
            Double capacidadeM3,
            @NotNull(message = "informe a vazao de recarga")
            @DecimalMin(value = "0", message = "recarga minima: 0 m3/h") @DecimalMax(value = "1000", message = "recarga maxima: 1000 m3/h")
            Double vazaoRecargaM3h,
            @NotNull(message = "informe o nivel inicial")
            @DecimalMin(value = "0", message = "nivel inicial de 0 a 100%") @DecimalMax(value = "100", message = "nivel inicial de 0 a 100%")
            Double nivelInicial) {
    }

    /** PUT /configuracao/usina. */
    public record UsinaRequest(
            @NotNull(message = "informe a potencia de pico")
            @DecimalMin(value = "0", message = "potencia minima: 0 kWp") @DecimalMax(value = "10000", message = "potencia maxima: 10.000 kWp")
            Double potenciaSolarPicoKw) {
    }
}
