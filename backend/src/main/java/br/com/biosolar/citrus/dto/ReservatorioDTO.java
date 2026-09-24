package br.com.biosolar.citrus.dto;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Instant;

import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.StatusReservatorio;

public record ReservatorioDTO(
        String nome,
        double nivel,
        double capacidadeM3,
        double volumeM3,
        StatusReservatorio status,
        double limiteAtencao,
        double limiteCritico,
        double limiteRearme,
        boolean bloqueioEmergencia,
        double consumoM3h,
        double recargaM3h,
        double tendenciaPorHora,
        Double autonomiaHoras,
        Instant ultimaAtualizacao) {

    public static ReservatorioDTO de(Fazenda f) {
        Reservatorio r = f.getReservatorio();
        return new ReservatorioDTO(r.getNome(), r1(r.getNivel()), r.getCapacidadeM3(), r1(r.getVolumeM3()),
                r.classificarNivel(), r.getLimiteAtencao(), r.getLimiteCritico(), r.getLimiteRearme(),
                r.isBloqueioEmergencia(), r1(f.consumoAguaM3h()), r1(f.recargaM3h()),
                r1(f.tendenciaReservatorioPorHora()), r1(f.autonomiaHoras()), r.getUltimaAtualizacao());
    }
}