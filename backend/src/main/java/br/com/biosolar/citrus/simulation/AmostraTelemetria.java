package br.com.biosolar.citrus.simulation;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import br.com.biosolar.citrus.model.Fazenda;

/** Fotografia imutavel da telemetria, capturada sob lock e gravada no historico fora dele. */
public record AmostraTelemetria(
        Instant instante,
        LocalDateTime horaSimulada,
        double nivelReservatorio,
        double umidadeMedia,
        int aspersoresLigados,
        double consumoKw,
        double geracaoSolarKw,
        int indice,
        boolean bloqueioEmergencia,
        List<Talhao> talhoes) {

    public record Talhao(String talhaoId, double umidade, boolean aspersorLigado) {
    }

    public static AmostraTelemetria de(Fazenda f, int indice, Instant agora) {
        return new AmostraTelemetria(agora, f.getSimulacao().getRelogioSimulado(), r1(f.getReservatorio().getNivel()),
                r1(f.umidadeMedia()), f.aspersoresLigados(), r1(f.consumoEnergiaKw()), r1(f.geracaoSolarKw()), indice,
                f.getReservatorio().isBloqueioEmergencia(),
                f.getTalhoes().stream().map(t -> new Talhao(t.getId(), r1(t.getUmidade()), t.isAspersorLigado()))
                        .toList());
    }
}
