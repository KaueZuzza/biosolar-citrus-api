package br.com.biosolar.citrus.dto;

import static br.com.biosolar.citrus.util.Formatador.r1;

import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Fazenda;

public record EnergiaDTO(
        double consumoKw,
        double geracaoSolarKw,
        double potenciaSolarPicoKw,
        double fatorSolar,
        double coberturaSolar,
        double energiaConsumidaKwh,
        double energiaSolarKwh,
        double energiaRedeKwh) {

    public static EnergiaDTO de(Fazenda f) {
        EstadoSimulacao s = f.getSimulacao();
        return new EnergiaDTO(r1(f.consumoEnergiaKw()), r1(f.geracaoSolarKw()), s.getPotenciaSolarPicoKw(),
                Math.round(f.fatorSolar() * 100) / 100.0, r1(f.coberturaSolar()), r1(s.getEnergiaConsumidaKwh()),
                r1(s.getEnergiaSolarKwh()), r1(s.getEnergiaConsumidaKwh() - s.getEnergiaSolarKwh()));
    }
}