package br.com.biosolar.citrus.dto;

import br.com.biosolar.citrus.model.Talhao;

/** Cadastro de um talhao (GET /talhoes), inclusive arquivados. */
public record TalhaoCadastroDTO(
        String id,
        String nome,
        String cultura,
        String culturaRotulo,
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
        double potenciaBombaKw,
        String bombaId,
        String sensorId,
        boolean ativo,
        long registrosHistorico) {

    public static TalhaoCadastroDTO de(Talhao t, long registrosHistorico) {
        return new TalhaoCadastroDTO(t.getId(), t.getNome(), t.getCultura().name(), t.getCultura().getRotulo(),
                t.getVariedade(), t.getSolo(), t.getAreaHa(), t.getPlantas(), t.getPrioridade(), t.getUmidadeInicial(),
                t.getLimiteCritico(), t.getLimiteAtencao(), t.getUmidadeAlvo(), t.getTaxaEvapotranspiracao(),
                t.getGanhoIrrigacao(), t.getVazaoBombaM3h(), t.getPotenciaBombaKw(), t.getBombaId(), t.getSensorId(),
                t.isAtivo(), registrosHistorico);
    }
}
