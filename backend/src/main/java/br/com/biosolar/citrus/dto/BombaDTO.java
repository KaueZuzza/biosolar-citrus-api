package br.com.biosolar.citrus.dto;

import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.Talhao;

/** Estado de uma motobomba (cada talhao possui uma bomba dedicada ao seu aspersor). */
public record BombaDTO(
        String id,
        String talhaoId,
        boolean ligada,
        ModoAcionamento modo,
        double potenciaKw,
        double vazaoM3h,
        boolean bloqueada) {

    public static BombaDTO de(Talhao t, Fazenda f) {
        return new BombaDTO(t.getBombaId(), t.getId(), t.isAspersorLigado(), t.getModoAcionamento(),
                t.getPotenciaBombaKw(), t.getVazaoBombaM3h(), f.getReservatorio().isBloqueioEmergencia());
    }
}