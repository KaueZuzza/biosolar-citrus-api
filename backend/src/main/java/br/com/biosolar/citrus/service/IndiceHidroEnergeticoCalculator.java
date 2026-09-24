package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.util.List;

import org.springframework.stereotype.Component;

import br.com.biosolar.citrus.dto.IndiceDTO;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Talhao;

/**
 * Indice Hidro-Energetico (IHE), de 0 a 100:
 *
 * <pre>
 *   IHE = 0,5 x H + 0,3 x S + 0,2 x E
 *
 *   H (hidrico)   = nivel do reservatorio (0-100%)
 *   S (solo)      = media dos talhoes de: 0 pts com umidade &lt;= 15%, 100 pts com umidade &gt;= umidade alvo (40%),
 *                   linear entre os dois
 *   E (energia)   = % do consumo das bombas atendido pela usina solar (100 se nao ha bombas ligadas)
 *
 *   Trava de seguranca: com o bloqueio de emergencia ativo o IHE e limitado a 25 (risco elevado).
 * </pre>
 *
 * Pesos: a agua disponivel e o recurso limitante (50%), a condicao do solo reflete a produtividade (30%)
 * e a origem da energia mede a eficiencia/custo da irrigacao (20%).
 */
@Component
public class IndiceHidroEnergeticoCalculator {

    public static final double PESO_HIDRICO = 0.5;
    public static final double PESO_SOLO = 0.3;
    public static final double PESO_ENERGIA = 0.2;
    public static final double UMIDADE_ZERO_PONTOS = 15.0;
    public static final double TETO_EMERGENCIA = 25.0;
    public static final String FORMULA = "IHE = 0,5 × H + 0,3 × S + 0,2 × E";

    public IndiceDTO calcular(Fazenda fazenda) {
        Reservatorio r = fazenda.getReservatorio();

        double h = limitar(r.getNivel());
        double s = fazenda.getTalhoes().stream().mapToDouble(IndiceHidroEnergeticoCalculator::adequacaoSolo)
                .average().orElse(0);
        double e = fazenda.coberturaSolar();

        double bruto = PESO_HIDRICO * h + PESO_SOLO * s + PESO_ENERGIA * e;
        boolean trava = r.isBloqueioEmergencia() && bruto > TETO_EMERGENCIA;
        int valor = (int) Math.round(trava ? TETO_EMERGENCIA : bruto);

        String classificacao;
        String rotulo;
        if (valor >= 70) {
            classificacao = "EQUILIBRADO";
            rotulo = "Operação equilibrada";
        } else if (valor >= 40) {
            classificacao = "ATENCAO";
            rotulo = "Atenção necessária";
        } else {
            classificacao = "RISCO";
            rotulo = "Risco hídrico elevado";
        }

        List<IndiceDTO.Fator> fatores = List.of(
                new IndiceDTO.Fator("H", "Hídrico: nível do reservatório", r1(h), PESO_HIDRICO,
                        r1(PESO_HIDRICO * h), "Nível atual do reservatório central (0 a 100%)."),
                new IndiceDTO.Fator("S", "Solo: adequação da umidade", r1(s), PESO_SOLO, r1(PESO_SOLO * s),
                        "Média dos talhões: 0 ponto com umidade até 15%, 100 pontos a partir da umidade alvo (40%), "
                                + "proporcional entre esses valores."),
                new IndiceDTO.Fator("E", "Energia: cobertura solar", r1(e), PESO_ENERGIA, r1(PESO_ENERGIA * e),
                        "Parcela do consumo das bombas atendida pela usina solar (100 quando nenhuma bomba está ligada)."));

        return new IndiceDTO(valor, classificacao, rotulo, FORMULA, r1(bruto), trava, fatores);
    }

    static double adequacaoSolo(Talhao t) {
        double faixa = t.getUmidadeAlvo() - UMIDADE_ZERO_PONTOS;
        return limitar((t.getUmidade() - UMIDADE_ZERO_PONTOS) / faixa * 100);
    }

    private static double limitar(double valor) {
        return Math.max(0, Math.min(100, valor));
    }
}
