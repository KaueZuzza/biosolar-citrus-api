package br.com.biosolar.citrus.dto;

import br.com.biosolar.citrus.model.Cultura;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /talhoes (cadastro) e PUT /talhoes/{id} (edicao; o codigo nao muda). O limite critico nao e
 * editavel: a irrigacao critica (P2) segue o limite do regulamento.
 */
public record TalhaoRequest(
        @Size(max = 10, message = "codigo com no maximo 10 caracteres")
        String id,

        @NotBlank(message = "informe o nome") @Size(max = 60, message = "nome com no maximo 60 caracteres")
        String nome,

        @NotNull(message = "informe a cultura (LARANJA ou LIMAO)")
        Cultura cultura,

        @NotBlank(message = "informe a variedade") @Size(max = 60, message = "variedade com no maximo 60 caracteres")
        String variedade,

        @NotBlank(message = "informe o tipo de solo") @Size(max = 80, message = "solo com no maximo 80 caracteres")
        String solo,

        @NotNull(message = "informe a area")
        @DecimalMin(value = "0.1", message = "area minima: 0,1 ha") @DecimalMax(value = "1000", message = "area maxima: 1000 ha")
        Double areaHa,

        @NotNull(message = "informe o numero de plantas")
        @Min(value = 0, message = "plantas: minimo 0") @Max(value = 1000000, message = "plantas: maximo 1.000.000")
        Integer plantas,

        @NotNull(message = "informe a prioridade")
        @Min(value = 1, message = "prioridade de 1 (alta) a 3 (baixa)") @Max(value = 3, message = "prioridade de 1 (alta) a 3 (baixa)")
        Integer prioridade,

        @NotNull(message = "informe a umidade inicial")
        @DecimalMin(value = "0", message = "umidade inicial de 0 a 100%") @DecimalMax(value = "100", message = "umidade inicial de 0 a 100%")
        Double umidadeInicial,

        @NotNull(message = "informe o limite de atencao")
        Double limiteAtencao,

        @NotNull(message = "informe a umidade alvo")
        Double umidadeAlvo,

        @NotNull(message = "informe a evapotranspiracao")
        @DecimalMin(value = "0", message = "evapotranspiracao minima: 0 %/h") @DecimalMax(value = "10", message = "evapotranspiracao maxima: 10 %/h")
        Double taxaEvapotranspiracao,

        @NotNull(message = "informe o ganho do aspersor")
        @DecimalMin(value = "0.5", message = "ganho minimo: 0,5 %/h") @DecimalMax(value = "30", message = "ganho maximo: 30 %/h")
        Double ganhoIrrigacao,

        @NotNull(message = "informe a vazao da bomba")
        @DecimalMin(value = "1", message = "vazao minima: 1 m3/h") @DecimalMax(value = "200", message = "vazao maxima: 200 m3/h")
        Double vazaoBombaM3h,

        @NotNull(message = "informe a potencia da bomba")
        @DecimalMin(value = "0.5", message = "potencia minima: 0,5 kW") @DecimalMax(value = "100", message = "potencia maxima: 100 kW")
        Double potenciaBombaKw) {
}
