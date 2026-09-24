package br.com.biosolar.citrus.model;

/** Status geral exibido no topo do dashboard. */
public enum StatusSistema {
    NORMAL("Operação normal", "🟢"),
    ATENCAO("Atenção", "🟡"),
    RISCO_HIDRICO("Risco hídrico", "🔴"),
    EMERGENCIA("Emergência", "🚨");

    private final String rotulo;
    private final String emoji;

    StatusSistema(String rotulo, String emoji) {
        this.rotulo = rotulo;
        this.emoji = emoji;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getEmoji() {
        return emoji;
    }
}