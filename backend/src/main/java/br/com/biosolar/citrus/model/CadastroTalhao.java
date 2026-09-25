package br.com.biosolar.citrus.model;

/**
 * Dados cadastrais de um talhao (editaveis pela interface ou diretamente no banco). Nao inclui o estado
 * operacional (umidade atual, aspersor, status), que e controlado pela automacao.
 *
 * @param umidadeInicial umidade usada ao restaurar o cenario de demonstracao
 * @param limiteCritico  abaixo dele a irrigacao critica (P2) e acionada; fixo em 25% pelo regulamento na interface
 */
public record CadastroTalhao(
        String nome,
        Cultura cultura,
        String variedade,
        String solo,
        double areaHa,
        int plantas,
        int prioridade,
        double umidadeInicial,
        double limiteCritico,
        double limiteAtencao,
        double umidadeAlvo,
        double taxaEvapotranspiracao,
        double ganhoIrrigacao,
        double vazaoBombaM3h,
        double potenciaBombaKw) {
}
