package br.com.biosolar.citrus.model;

/**
 * Dados cadastrais do reservatorio central. Os limites de protecao hidrica (critico, rearme e atencao)
 * seguem o regulamento e sao exibidos, mas nao editados, pela interface.
 *
 * @param nivelInicial nivel usado ao restaurar o cenario de demonstracao (%)
 */
public record CadastroReservatorio(
        String nome,
        double capacidadeM3,
        double vazaoRecargaM3h,
        double nivelInicial,
        double limiteAtencao,
        double limiteCritico,
        double limiteRearme) {
}
