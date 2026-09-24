package br.com.biosolar.citrus.model;

public enum Cultura {
    LARANJA("Laranja"),
    LIMAO("Limão");

    private final String rotulo;

    Cultura(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}