package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.horas;
import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.DecisaoDTO;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusReservatorio;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;

/**
 * "Motor de Decisao": escolhe a situacao mais relevante do momento (seguindo a hierarquia P1 &gt; P2 &gt; P3 &gt; P4)
 * e a explica em linguagem de operador.
 */
@Service
public class MotorDecisaoService {

    public DecisaoDTO decidir(Fazenda f) {
        Reservatorio r = f.getReservatorio();
        List<Talhao> automaticos = f.getTalhoes().stream()
                .filter(t -> t.isAspersorLigado() && t.getModoAcionamento() == ModoAcionamento.AUTOMATICO)
                .sorted(Comparator.comparingDouble(Talhao::getUmidade))
                .toList();
        List<Talhao> manuais = f.getTalhoes().stream()
                .filter(t -> t.isAspersorLigado() && t.getModoAcionamento() == ModoAcionamento.MANUAL)
                .toList();

        List<String> regrasAtivas = new ArrayList<>();
        if (r.isBloqueioEmergencia()) {
            regrasAtivas.add(MotorRegras.P1);
        } else if (!automaticos.isEmpty()) {
            regrasAtivas.add(MotorRegras.P2);
        } else {
            regrasAtivas.add(MotorRegras.P3);
        }
        if (!manuais.isEmpty()) {
            regrasAtivas.add(MotorRegras.P4);
        }

        if (r.isBloqueioEmergencia()) {
            return bloqueio(f, regrasAtivas);
        }
        if (!automaticos.isEmpty()) {
            return irrigacaoAutomatica(f, automaticos, regrasAtivas);
        }
        if (r.classificarNivel() == StatusReservatorio.ATENCAO) {
            return reservatorioAtencao(f, regrasAtivas);
        }
        List<Talhao> emAtencao = f.getTalhoes().stream()
                .filter(t -> t.classificarUmidade() == StatusTalhao.ATENCAO)
                .sorted(Comparator.comparingDouble(Talhao::getUmidade))
                .toList();
        if (!emAtencao.isEmpty()) {
            return talhaoAtencao(f, emAtencao.get(0), regrasAtivas);
        }
        if (!manuais.isEmpty()) {
            return irrigacaoManual(f, manuais, regrasAtivas);
        }
        return operacaoNormal(f, regrasAtivas);
    }

    private DecisaoDTO bloqueio(Fazenda f, List<String> regrasAtivas) {
        Reservatorio r = f.getReservatorio();
        List<Talhao> impedidos = f.talhoesCriticos();

        String oQue = r.isAbaixoDoLimiteCritico()
                ? "Reservatório em " + pct(r.getNivel()) + ", abaixo do limite de segurança de "
                        + pct(r.getLimiteCritico()) + "."
                : "Reservatório em " + pct(r.getNivel()) + ": acima de " + pct(r.getLimiteCritico())
                        + ", mas ainda abaixo do nível de rearme (" + pct(r.getLimiteRearme()) + ").";
        String onde = "Reservatório central e as " + f.getTalhoes().size() + " motobombas"
                + (impedidos.isEmpty() ? "" : ". Talhões sem irrigação: " + listar(impedidos));
        String risco = impedidos.isEmpty()
                ? "Alto: reserva hídrica comprometida."
                : "Muito alto: " + impedidos.size() + " talhão(ões) com umidade crítica sem poder irrigar.";

        double recarga = f.recargaM3h();
        String impacto;
        if (recarga > 0.05) {
            double horasRearme = (r.getLimiteRearme() - r.getNivel()) / 100 * r.getCapacidadeM3() / recarga;
            impacto = "Consumo de água zerado. Recarga solar de +" + num(recarga) + " m³/h: rearme previsto em ~"
                    + horas(Math.max(0, horasRearme)) + " (tempo da fazenda).";
        } else {
            impacto = "Consumo de água zerado. Sem recarga solar no momento (período noturno): nível estável.";
        }

        return new DecisaoDTO(Severidade.EMERGENCIA, MotorRegras.P1, "Bloqueio de emergência",
                "Bloqueio de emergência ativo", oQue, onde,
                "Regra P1 (proteção hídrica): abaixo de " + pct(r.getLimiteCritico())
                        + " a reserva é preservada e nenhuma bomba pode operar, nem mesmo para irrigação crítica.",
                "Todas as bombas foram desligadas pelo servidor. Novos acionamentos, automáticos ou manuais, estão bloqueados.",
                risco,
                "Verificar a captação e a recarga do reservatório. O rearme é automático quando o nível atingir "
                        + pct(r.getLimiteRearme()) + ".",
                impacto, ids(impedidos), regrasAtivas);
    }

    private DecisaoDTO irrigacaoAutomatica(Fazenda f, List<Talhao> irrigando, List<String> regrasAtivas) {
        Reservatorio r = f.getReservatorio();
        Talhao p = irrigando.get(0);
        boolean critico = p.isCritico();

        String oQue = critico
                ? p.getRotulo() + " apresenta umidade de " + pct(p.getUmidade()) + ", abaixo do limite crítico de "
                        + pct(p.getLimiteCritico()) + "."
                : p.getRotulo() + " em recuperação: umidade " + pct(p.getUmidade()) + ", subindo até o alvo de "
                        + pct(p.getUmidadeAlvo()) + ".";
        if (irrigando.size() > 1) {
            oQue += " Também irrigando: " + listar(irrigando.subList(1, irrigando.size())) + ".";
        }

        double variacao = f.variacaoUmidadePorHora(p);
        String previsao = variacao > 0
                ? " Previsão de conclusão: ~" + horas((p.getUmidadeAlvo() - p.getUmidade()) / variacao) + "."
                : "";

        boolean reservatorioNormal = r.classificarNivel() == StatusReservatorio.NORMAL;
        return new DecisaoDTO(critico ? Severidade.CRITICO : Severidade.INFO, MotorRegras.P2, "Irrigação crítica",
                critico ? "Irrigação crítica em andamento" : "Irrigação automática em recuperação",
                oQue,
                irrigando.stream().map(t -> t.getNome() + " (" + t.getCultura().getRotulo() + " " + t.getVariedade() + ")")
                        .collect(Collectors.joining(", ")),
                "Regra P2: umidade abaixo de " + pct(p.getLimiteCritico()) + " com reservatório seguro ("
                        + pct(r.getNivel()) + ", acima de " + pct(r.getLimiteCritico()) + ").",
                "Aspersor acionado automaticamente pelo servidor. Desligamento automático ao atingir "
                        + pct(p.getUmidadeAlvo()) + "." + previsao,
                reservatorioNormal
                        ? "Controlado: reservatório em nível normal."
                        : "Elevado: reservatório em atenção (" + pct(r.getNivel())
                                + "). A proteção hídrica atuará abaixo de " + pct(r.getLimiteCritico()) + ".",
                reservatorioNormal
                        ? "Nenhuma ação necessária. Acompanhe a recuperação da umidade."
                        : "Monitorar o reservatório e evitar acionamentos manuais não essenciais.",
                impactoConsumo(f), ids(irrigando), regrasAtivas);
    }

    private DecisaoDTO reservatorioAtencao(Fazenda f, List<String> regrasAtivas) {
        Reservatorio r = f.getReservatorio();
        double tendencia = f.tendenciaReservatorioPorHora();
        return new DecisaoDTO(Severidade.ATENCAO, MotorRegras.P3, "Operação normal",
                "Reservatório em atenção",
                "Reservatório em " + pct(r.getNivel()) + " (faixa de atenção: 15% a 30%).",
                "Reservatório central",
                "Nível abaixo de " + pct(r.getLimiteAtencao()) + ". Se cair abaixo de " + pct(r.getLimiteCritico())
                        + ", o bloqueio de emergência (P1) desligará todas as bombas.",
                "Monitoramento reforçado. Irrigações críticas continuam permitidas enquanto o nível estiver acima de "
                        + pct(r.getLimiteCritico()) + ".",
                "Moderado: margem de " + num(r.getNivel() - r.getLimiteCritico()) + " p.p. até o bloqueio.",
                "Evitar acionamentos manuais não essenciais e verificar a recarga do reservatório.",
                "Tendência do nível: " + (tendencia >= 0 ? "+" : "") + num(tendencia) + " p.p./h. " + impactoConsumo(f),
                List.of(), regrasAtivas);
    }

    private DecisaoDTO talhaoAtencao(Fazenda f, Talhao t, List<String> regrasAtivas) {
        Reservatorio r = f.getReservatorio();
        Double eta = f.horasAteCritico(t);
        return new DecisaoDTO(Severidade.ATENCAO, MotorRegras.P3, "Operação normal",
                t.getNome() + " se aproximando do limite crítico",
                t.getRotulo() + " com " + pct(t.getUmidade()) + " de umidade (atenção abaixo de "
                        + pct(t.getLimiteAtencao()) + ").",
                t.getNome() + " (" + t.getCultura().getRotulo() + " " + t.getVariedade() + ", " + t.getSolo() + ")",
                "Evapotranspiração de " + num(f.evapotranspiracaoPorHora(t)) + "%/h no horário atual; limite crítico em "
                        + pct(t.getLimiteCritico()) + ".",
                "Nenhuma por enquanto. A irrigação crítica (P2) será acionada automaticamente ao atingir "
                        + pct(t.getLimiteCritico()) + (eta != null ? " (previsão: ~" + horas(eta) + ")." : "."),
                "Baixo: reservatório em " + pct(r.getNivel()) + " garante a irrigação.",
                "Nenhuma ação necessária. Opcional: irrigar manualmente para antecipar.",
                "Irrigação prevista: " + num(t.getVazaoBombaM3h()) + " m³/h de água e " + num(t.getPotenciaBombaKw())
                        + " kW por bomba.",
                List.of(t.getId()), regrasAtivas);
    }

    private DecisaoDTO irrigacaoManual(Fazenda f, List<Talhao> manuais, List<String> regrasAtivas) {
        Talhao t = manuais.get(0);
        boolean acimaDoAlvo = manuais.stream().anyMatch(m -> m.getUmidade() > m.getUmidadeAlvo());
        return new DecisaoDTO(Severidade.INFO, MotorRegras.P4, "Controle manual",
                "Irrigação manual em andamento",
                "Operador mantém o(s) aspersor(es) de " + listar(manuais) + " ligado(s).",
                manuais.stream().map(Talhao::getNome).collect(Collectors.joining(", ")),
                "Regra P4: controle manual permitido, pois o reservatório está seguro e nenhuma regra de segurança é violada.",
                "Proteções ativas: desligamento automático se o reservatório cair abaixo de 15% ou se a umidade atingir 85%.",
                acimaDoAlvo ? "Atenção: talhão acima da umidade alvo, com possível desperdício de água." : "Baixo.",
                acimaDoAlvo
                        ? "Considere desligar: o solo do " + t.getNome() + " já está acima da umidade alvo ("
                                + pct(t.getUmidadeAlvo()) + ")."
                        : "Nenhuma ação necessária.",
                impactoConsumo(f), ids(manuais), regrasAtivas);
    }

    private DecisaoDTO operacaoNormal(Fazenda f, List<String> regrasAtivas) {
        Reservatorio r = f.getReservatorio();
        Talhao proximo = null;
        Double menorEta = null;
        for (Talhao t : f.getTalhoes()) {
            Double eta = f.horasAteCritico(t);
            if (eta != null && (menorEta == null || eta < menorEta)) {
                menorEta = eta;
                proximo = t;
            }
        }
        String previsao = proximo != null
                ? " Próximo talhão a exigir irrigação: " + proximo.getNome() + " em ~" + horas(menorEta) + "."
                : "";
        return new DecisaoDTO(Severidade.SUCESSO, MotorRegras.P3, "Operação normal",
                "Operação equilibrada",
                "Todos os " + f.getTalhoes().size() + " talhões com umidade adequada (média " + pct(f.umidadeMedia())
                        + ") e reservatório em " + pct(r.getNivel()) + ".",
                "Fazenda inteira: " + f.getTalhoes().size() + " talhões e reservatório central",
                "Nenhum limite atingido: umidade acima de 30% e reservatório acima de 30%.",
                "Nenhuma intervenção necessária. Monitoramento contínuo a cada ciclo." + previsao,
                "Baixo.",
                "Nenhuma ação necessária.",
                f.consumoAguaM3h() > 0 ? impactoConsumo(f)
                        : "Bombas desligadas: consumo de água e energia zerados. Recarga solar do reservatório: +"
                                + num(f.recargaM3h()) + " m³/h.",
                List.of(), regrasAtivas);
    }

    private String impactoConsumo(Fazenda f) {
        Double autonomia = f.autonomiaHoras();
        return "Consumo de " + num(f.consumoAguaM3h()) + " m³/h de água e " + num(f.consumoEnergiaKw()) + " kW ("
                + Math.round(f.coberturaSolar()) + "% solar)."
                + (autonomia != null ? " Autonomia do reservatório até o limite de segurança: ~" + horas(autonomia) + "."
                        : " Recarga supera o consumo: nível estável.");
    }

    private static String listar(List<Talhao> talhoes) {
        return talhoes.stream().map(t -> t.getNome() + " (" + pct(t.getUmidade()) + ")")
                .collect(Collectors.joining(", "));
    }

    private static List<String> ids(List<Talhao> talhoes) {
        return talhoes.stream().map(Talhao::getId).toList();
    }
}
