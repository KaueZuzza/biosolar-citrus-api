package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.horas;
import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.StatusReservatorio;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TipoEvento;

/**
 * Motor de regras autonomo do servidor. Hierarquia (a regra de numero menor sempre prevalece):
 * <ol>
 *   <li><b>P1 - Bloqueio de emergencia</b>: reservatorio &lt; 15% desliga TODAS as bombas e impede
 *       qualquer acionamento ate o nivel voltar a 20% (histerese de rearme).</li>
 *   <li><b>P2 - Irrigacao critica</b>: umidade &lt; 25% liga o aspersor do talhao, desde que P1 nao esteja ativa.</li>
 *   <li><b>P3 - Operacao normal</b>: encerra a irrigacao automatica na umidade alvo (40%) e protege contra
 *       encharcamento (85%).</li>
 *   <li><b>P4 - Controle manual</b>: comandos do operador, aceitos somente se nao violarem P1 ou P2.</li>
 * </ol>
 * A classe nao depende de banco nem de HTTP: recebe o agregado {@link Fazenda}, altera o estado e registra eventos.
 */
@Component
public class MotorRegras {

    public static final String P1 = "P1";
    public static final String P2 = "P2";
    public static final String P3 = "P3";
    public static final String P4 = "P4";

    /** Acima deste valor o solo e considerado saturado: nenhum aspersor deve continuar ligado. */
    public static final double LIMITE_SATURACAO = 85.0;

    // =============================================================================================
    // Avaliacao automatica (executada a cada minuto simulado e apos qualquer alteracao de estado)
    // =============================================================================================

    public void avaliar(Fazenda fazenda, Instant agora) {
        Reservatorio reservatorio = fazenda.getReservatorio();

        // ---- PRIORIDADE 1: protecao hidrica ----------------------------------------------------
        if (!reservatorio.isBloqueioEmergencia() && reservatorio.isAbaixoDoLimiteCritico()) {
            ativarBloqueio(fazenda, agora);
        } else if (reservatorio.isBloqueioEmergencia()
                && reservatorio.getNivel() >= reservatorio.getLimiteRearme()) {
            liberarBloqueio(fazenda, agora);
        }

        if (reservatorio.isBloqueioEmergencia()) {
            manterBombasDesligadas(fazenda, agora);
        } else {
            // ---- PRIORIDADE 2: irrigacao critica ------------------------------------------------
            aplicarIrrigacaoCritica(fazenda, agora);
            // ---- PRIORIDADE 3: operacao normal --------------------------------------------------
            aplicarOperacaoNormal(fazenda, agora);
        }

        atualizarStatus(fazenda, agora);
    }

    private void ativarBloqueio(Fazenda fazenda, Instant agora) {
        Reservatorio r = fazenda.getReservatorio();
        r.setBloqueioEmergencia(true);

        List<String> desligadas = new ArrayList<>();
        for (Talhao t : fazenda.getTalhoes()) {
            if (t.isAspersorLigado()) {
                t.desligarAspersor(agora);
                desligadas.add(t.getBombaId());
            }
        }
        String bombas = desligadas.isEmpty()
                ? "Nenhuma bomba estava ligada; todas permanecem desligadas."
                : desligadas.size() + " bomba(s) desligada(s) imediatamente: " + String.join(", ", desligadas) + ".";

        fazenda.registrarEvento(TipoEvento.BLOQUEIO_EMERGENCIA, Severidade.EMERGENCIA, OrigemEvento.AUTOMACAO, P1,
                null, "Bloqueio de emergência ativado",
                "Reservatório atingiu " + pct(r.getNivel()) + " (limite de segurança: " + pct(r.getLimiteCritico())
                        + "). " + bombas + " Irrigação suspensa até o nível voltar a " + pct(r.getLimiteRearme()) + ".",
                agora);
    }

    private void liberarBloqueio(Fazenda fazenda, Instant agora) {
        Reservatorio r = fazenda.getReservatorio();
        r.setBloqueioEmergencia(false);
        fazenda.registrarEvento(TipoEvento.RECUPERACAO_SISTEMA, Severidade.SUCESSO, OrigemEvento.AUTOMACAO, P1,
                null, "Sistema recuperado: bloqueio liberado",
                "Reservatório voltou a " + pct(r.getNivel()) + " (rearme em " + pct(r.getLimiteRearme())
                        + "). Bombas liberadas; a irrigação automática volta a atender os talhões críticos.",
                agora);
    }

    /** Enquanto P1 estiver ativa: nenhuma bomba ligada e talhoes criticos sinalizados como bloqueados. */
    private void manterBombasDesligadas(Fazenda fazenda, Instant agora) {
        Reservatorio r = fazenda.getReservatorio();
        for (Talhao t : fazenda.getTalhoes()) {
            if (t.isAspersorLigado()) {
                t.desligarAspersor(agora);
                fazenda.registrarEvento(TipoEvento.BLOQUEIO_EMERGENCIA, Severidade.EMERGENCIA, OrigemEvento.AUTOMACAO,
                        P1, t.getId(), "Bomba " + t.getBombaId() + " desligada pela proteção hídrica",
                        "Bloqueio de emergência ativo: nenhuma bomba pode operar com o reservatório em "
                                + pct(r.getNivel()) + ".",
                        agora);
            }
            if (t.isCritico() && !t.isIrrigacaoBloqueada()) {
                t.setIrrigacaoBloqueada(true);
                fazenda.registrarEvento(TipoEvento.IRRIGACAO_BLOQUEADA, Severidade.CRITICO, OrigemEvento.AUTOMACAO, P1,
                        t.getId(), "Irrigação crítica impedida: " + t.getNome(),
                        t.getRotulo() + " está com " + pct(t.getUmidade()) + " de umidade (limite "
                                + pct(t.getLimiteCritico()) + "), mas o reservatório está em " + pct(r.getNivel())
                                + ". A proteção hídrica (P1) prevalece: bomba " + t.getBombaId() + " mantida desligada.",
                        agora);
            } else if (!t.isCritico() && t.isIrrigacaoBloqueada()) {
                t.setIrrigacaoBloqueada(false);
            }
        }
    }

    private void aplicarIrrigacaoCritica(Fazenda fazenda, Instant agora) {
        Reservatorio r = fazenda.getReservatorio();
        List<Talhao> ordenados = fazenda.getTalhoes().stream()
                .sorted(Comparator.comparingInt(Talhao::getPrioridade).thenComparingDouble(Talhao::getUmidade))
                .toList();

        for (Talhao t : ordenados) {
            t.setIrrigacaoBloqueada(false);
            if (t.isCritico() && !t.isAspersorLigado()) {
                t.ligarAspersor(ModoAcionamento.AUTOMATICO, agora);
                fazenda.registrarEvento(TipoEvento.IRRIGACAO_CRITICA, Severidade.CRITICO, OrigemEvento.AUTOMACAO, P2,
                        t.getId(), "Irrigação crítica: " + t.getNome(),
                        t.getRotulo() + " atingiu " + pct(t.getUmidade()) + " de umidade (limite crítico "
                                + pct(t.getLimiteCritico()) + "). Aspersor acionado automaticamente; reservatório seguro em "
                                + pct(r.getNivel()) + ". Desligamento automático ao atingir " + pct(t.getUmidadeAlvo()) + ".",
                        agora);
            }
        }
    }

    private void aplicarOperacaoNormal(Fazenda fazenda, Instant agora) {
        for (Talhao t : fazenda.getTalhoes()) {
            if (!t.isAspersorLigado()) {
                continue;
            }
            if (t.getModoAcionamento() == ModoAcionamento.AUTOMATICO && t.getUmidade() >= t.getUmidadeAlvo()) {
                t.desligarAspersor(agora);
                fazenda.registrarEvento(TipoEvento.IRRIGACAO_CONCLUIDA, Severidade.SUCESSO, OrigemEvento.AUTOMACAO, P3,
                        t.getId(), "Irrigação concluída: " + t.getNome(),
                        "Umidade recuperada para " + pct(t.getUmidade()) + " (alvo " + pct(t.getUmidadeAlvo())
                                + "). Aspersor desligado automaticamente para economizar água e energia.",
                        agora);
            } else if (t.getUmidade() >= LIMITE_SATURACAO) {
                t.desligarAspersor(agora);
                fazenda.registrarEvento(TipoEvento.PROTECAO_SATURACAO, Severidade.ATENCAO, OrigemEvento.AUTOMACAO, P3,
                        t.getId(), "Proteção contra encharcamento: " + t.getNome(),
                        "Umidade chegou a " + pct(t.getUmidade()) + " (máximo " + pct(LIMITE_SATURACAO)
                                + "). Aspersor desligado para evitar encharcamento e desperdício de água.",
                        agora);
            }
        }
    }

    /** Detecta mudancas de faixa (talhoes e reservatorio) e registra apenas os alertas uteis. */
    private void atualizarStatus(Fazenda fazenda, Instant agora) {
        for (Talhao t : fazenda.getTalhoes()) {
            StatusTalhao novo = t.classificarUmidade();
            if (novo == t.getStatus()) {
                continue;
            }
            // Queda para ATENCAO avisa com antecedencia; CRITICO ja e coberto por IRRIGACAO_CRITICA/BLOQUEADA.
            if (novo == StatusTalhao.ATENCAO && t.getStatus() == StatusTalhao.NORMAL) {
                Double eta = fazenda.horasAteCritico(t);
                fazenda.registrarEvento(TipoEvento.ALERTA_UMIDADE, Severidade.ATENCAO, OrigemEvento.AUTOMACAO, null,
                        t.getId(), "Umidade em atenção: " + t.getNome(),
                        "Umidade caiu para " + pct(t.getUmidade()) + " (atenção abaixo de " + pct(t.getLimiteAtencao())
                                + "). A irrigação crítica será acionada automaticamente em " + pct(t.getLimiteCritico())
                                + (eta != null ? " (previsão: ~" + horas(eta) + " no tempo da fazenda)." : "."),
                        agora);
            }
            t.setStatus(novo);
        }

        Reservatorio r = fazenda.getReservatorio();
        StatusReservatorio anterior = r.getStatus();
        StatusReservatorio novo = r.classificarNivel();
        boolean alertou = false;
        if (novo != anterior) {
            if (novo == StatusReservatorio.ATENCAO && anterior == StatusReservatorio.NORMAL) {
                fazenda.registrarEvento(TipoEvento.ALERTA_RESERVATORIO, Severidade.ATENCAO, OrigemEvento.AUTOMACAO, null,
                        null, "Reservatório em atenção",
                        "Nível caiu para " + pct(r.getNivel()) + " (faixa de atenção: 15% a 30%). "
                                + "Abaixo de 15% o bloqueio de emergência desligará todas as bombas.",
                        agora);
                alertou = true;
            } else if (novo == StatusReservatorio.ATENCAO && r.isBloqueioEmergencia()) {
                fazenda.registrarEvento(TipoEvento.ALERTA_RESERVATORIO, Severidade.INFO, OrigemEvento.AUTOMACAO, P1,
                        null, "Reservatório acima de 15%: bloqueio mantido",
                        "Nível em " + pct(r.getNivel()) + ". O bloqueio só é liberado em " + pct(r.getLimiteRearme())
                                + " (histerese de rearme), evitando liga/desliga repetido das bombas.",
                        agora);
                alertou = true;
            } else if (novo == StatusReservatorio.NORMAL) {
                fazenda.registrarEvento(TipoEvento.ALERTA_RESERVATORIO, Severidade.SUCESSO, OrigemEvento.AUTOMACAO, null,
                        null, "Reservatório normalizado",
                        "Nível recuperado para " + pct(r.getNivel()) + " (acima de " + pct(r.getLimiteAtencao()) + ").",
                        agora);
                alertou = true;
            }
            r.setStatus(novo);
        }

        // Evolucao do nivel no historico a cada faixa de 10 pontos percentuais
        Double nivelAnterior = r.getNivelUltimaAvaliacao();
        if (!alertou && nivelAnterior != null
                && (int) Math.floor(nivelAnterior / 10) != (int) Math.floor(r.getNivel() / 10)) {
            double tendencia = fazenda.tendenciaReservatorioPorHora();
            fazenda.registrarEvento(TipoEvento.RESERVATORIO_ATUALIZADO, Severidade.INFO, OrigemEvento.AUTOMACAO, null,
                    null, "Reservatório atualizado",
                    pct(nivelAnterior) + " → " + pct(r.getNivel()) + " (tendência "
                            + (tendencia >= 0 ? "+" : "") + num(tendencia) + " p.p./h).",
                    agora);
        }
        r.setNivelUltimaAvaliacao(r.getNivel());
    }

    // =============================================================================================
    // PRIORIDADE 4: controle manual (validado contra as regras de seguranca)
    // =============================================================================================

    public ResultadoComando avaliarComandoManual(Fazenda fazenda, Talhao t, boolean ligar, Instant agora) {
        Reservatorio r = fazenda.getReservatorio();

        if (ligar) {
            if (r.isBloqueioEmergencia() || r.isAbaixoDoLimiteCritico()) {
                fazenda.registrarEvento(TipoEvento.COMANDO_RECUSADO, Severidade.EMERGENCIA, OrigemEvento.OPERADOR, P1,
                        t.getId(), "Acionamento bloqueado: " + t.getNome(),
                        "Operador tentou ligar o aspersor do " + t.getNome() + ". Comando recusado pelo servidor: "
                                + "reservatório em " + pct(r.getNivel()) + ", proteção hídrica ativa (P1).",
                        agora);
                return ResultadoComando.recusado(ResultadoComando.BLOQUEIO_DE_EMERGENCIA,
                        "Acionamento bloqueado devido ao nível crítico do reservatório (" + pct(r.getNivel())
                                + "). O sistema de proteção hídrica impede o acionamento das bombas até o nível voltar a "
                                + pct(r.getLimiteRearme()) + ".");
            }
            if (t.getUmidade() >= LIMITE_SATURACAO) {
                fazenda.registrarEvento(TipoEvento.COMANDO_RECUSADO, Severidade.ATENCAO, OrigemEvento.OPERADOR, P3,
                        t.getId(), "Acionamento recusado: " + t.getNome(),
                        "Solo saturado (" + pct(t.getUmidade()) + "). Irrigar agora desperdiçaria água e energia.",
                        agora);
                return ResultadoComando.recusado(ResultadoComando.SOLO_SATURADO,
                        "O solo do " + t.getNome() + " já está saturado (" + pct(t.getUmidade())
                                + "). O acionamento foi recusado para evitar encharcamento e desperdício.");
            }
            if (t.isAspersorLigado()) {
                return ResultadoComando.semAlteracao("O aspersor do " + t.getNome() + " já está ligado ("
                        + (t.getModoAcionamento() == ModoAcionamento.AUTOMATICO ? "modo automático" : "modo manual") + ").");
            }
            t.ligarAspersor(ModoAcionamento.MANUAL, agora);
            fazenda.registrarEvento(TipoEvento.COMANDO_MANUAL, Severidade.INFO, OrigemEvento.OPERADOR, P4, t.getId(),
                    "Controle manual: " + t.getNome() + " ligado",
                    "Operador ligou o aspersor do " + t.getNome() + " (umidade " + pct(t.getUmidade())
                            + "). Reservatório em " + pct(r.getNivel()) + ": comando validado pelas regras de segurança.",
                    agora);
            return ResultadoComando.executado("Aspersor do " + t.getNome() + " ligado manualmente.");
        }

        if (!t.isAspersorLigado()) {
            return ResultadoComando.semAlteracao("O aspersor do " + t.getNome() + " já está desligado.");
        }
        if (t.isCritico()) {
            fazenda.registrarEvento(TipoEvento.COMANDO_RECUSADO, Severidade.CRITICO, OrigemEvento.OPERADOR, P2,
                    t.getId(), "Desligamento recusado: " + t.getNome(),
                    "Operador tentou desligar o aspersor do " + t.getNome() + ", mas a umidade está em "
                            + pct(t.getUmidade()) + " (abaixo de " + pct(t.getLimiteCritico())
                            + "). A irrigação crítica (P2) tem prioridade sobre o controle manual.",
                    agora);
            return ResultadoComando.recusado(ResultadoComando.IRRIGACAO_CRITICA_EM_ANDAMENTO,
                    "O " + t.getNome() + " está com umidade crítica (" + pct(t.getUmidade())
                            + "). A irrigação crítica tem prioridade sobre o controle manual e só pode ser interrompida "
                            + "depois que a umidade superar " + pct(t.getLimiteCritico()) + ".");
        }
        t.desligarAspersor(agora);
        fazenda.registrarEvento(TipoEvento.COMANDO_MANUAL, Severidade.INFO, OrigemEvento.OPERADOR, P4, t.getId(),
                "Controle manual: " + t.getNome() + " desligado",
                "Operador desligou o aspersor do " + t.getNome() + " (umidade " + pct(t.getUmidade()) + ").",
                agora);
        return ResultadoComando.executado("Aspersor do " + t.getNome() + " desligado manualmente.");
    }
}
