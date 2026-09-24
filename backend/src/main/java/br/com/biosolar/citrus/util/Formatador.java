package br.com.biosolar.citrus.util;

import java.util.Locale;

/** Formatacao numerica em pt-BR para mensagens exibidas ao operador. */
public final class Formatador {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private Formatador() {
    }

    /** Ex.: 21.47 -> "21,4%" (truncado: 24,97% aparece como 24,9%, coerente com a regra "&lt; 25%"). */
    public static String pct(double valor) {
        return String.format(PT_BR, "%.1f%%", r1(valor));
    }

    /** Ex.: 5.5 -> "5,5". */
    public static String num(double valor) {
        return String.format(PT_BR, "%.1f", valor);
    }

    /** Ex.: 2.25 -> "2,3 h"; valores longos em horas inteiras. */
    public static String horas(double horas) {
        if (horas < 1) {
            return String.format(PT_BR, "%.0f min", horas * 60);
        }
        return String.format(PT_BR, horas < 10 ? "%.1f h" : "%.0f h", horas);
    }

    /**
     * Trunca para 1 casa decimal (em direcao a zero). O truncamento garante que o valor exibido fique sempre
     * do mesmo lado de um limite que o valor real: 24,97% vira 24,9% (critico) e nunca "25,0%".
     */
    public static double r1(double valor) {
        double t = (valor >= 0 ? Math.floor(valor * 10 + 1e-6) : Math.ceil(valor * 10 - 1e-6)) / 10.0;
        return t == 0 ? 0.0 : t;
    }

    public static Double r1(Double valor) {
        return valor == null ? null : r1(valor.doubleValue());
    }
}
