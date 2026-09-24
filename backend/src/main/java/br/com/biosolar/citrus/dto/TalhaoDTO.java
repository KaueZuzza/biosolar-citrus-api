package br.com.biosolar.citrus.dto;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Instant;

import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;

public record TalhaoDTO(
        String id,
        String nome,
        String cultura,
        String culturaRotulo,
        String variedade,
        String solo,
        double areaHa,
        int plantas,
        int prioridade,
        double umidade,
        double limiteCritico,
        double limiteAtencao,
        double umidadeAlvo,
        StatusTalhao status,
        boolean aspersorLigado,
        ModoAcionamento modoAcionamento,
        boolean irrigacaoBloqueada,
        String bombaId,
        double vazaoBombaM3h,
        double potenciaBombaKw,
        double evapotranspiracaoPorHora,
        double variacaoPorHora,
        Double horasAteCritico,
        Instant ultimaAtualizacao,
        Instant ultimoAcionamento) {

    public static TalhaoDTO de(Talhao t, Fazenda f) {
        return new TalhaoDTO(t.getId(), t.getNome(), t.getCultura().name(), t.getCultura().getRotulo(),
                t.getVariedade(), t.getSolo(), t.getAreaHa(), t.getPlantas(), t.getPrioridade(),
                r1(t.getUmidade()), t.getLimiteCritico(), t.getLimiteAtencao(), t.getUmidadeAlvo(),
                t.classificarUmidade(), t.isAspersorLigado(), t.getModoAcionamento(), t.isIrrigacaoBloqueada(),
                t.getBombaId(), t.getVazaoBombaM3h(), t.getPotenciaBombaKw(),
                r1(f.evapotranspiracaoPorHora(t)), r1(f.variacaoUmidadePorHora(t)), r1(f.horasAteCritico(t)),
                t.getUltimaAtualizacao(), t.getUltimoAcionamento());
    }
}