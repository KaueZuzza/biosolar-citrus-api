package br.com.biosolar.citrus.simulation;

import java.time.LocalDateTime;

/**
 * Curva de irradiancia simplificada para a regiao amazonica (proxima ao Equador):
 * nascer do sol ~06h, por do sol ~18h e pico ao meio-dia (meia senoide).
 */
public final class ModeloSolar {

    private static final double NASCER = 6.0;
    private static final double POR = 18.0;

    private ModeloSolar() {
    }

    /** Fator de 0 (noite) a 1 (meio-dia). */
    public static double fatorSolar(LocalDateTime horario) {
        double hora = horario.getHour() + horario.getMinute() / 60.0 + horario.getSecond() / 3600.0;
        if (hora <= NASCER || hora >= POR) {
            return 0;
        }
        return Math.sin(Math.PI * (hora - NASCER) / (POR - NASCER));
    }
}
