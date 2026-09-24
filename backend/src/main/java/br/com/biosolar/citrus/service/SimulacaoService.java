package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.dto.SimulacaoRequests;
import br.com.biosolar.citrus.exception.OperacaoNaoPermitidaException;
import br.com.biosolar.citrus.exception.RecursoNaoEncontradoException;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TipoEvento;

/**
 * Painel de simulacao (modo demonstracao). Estes comandos NAO executam regras: apenas alteram as
 * condicoes fisicas no servidor. Em seguida o mesmo motor de regras do ciclo automatico e reavaliado,
 * exatamente como aconteceria se o sensor tivesse medido esses valores.
 */
@Service
public class SimulacaoService {

    /** Nivel usado pelo botao "Simular emergencia" (abaixo do limite de 15%). */
    public static final double NIVEL_EMERGENCIA_SIMULADA = 12.0;

    private final EstadoFazendaService estado;
    private final MotorRegras motorRegras;
    private final BioSolarProperties properties;
    private final Clock clock;

    public SimulacaoService(EstadoFazendaService estado, MotorRegras motorRegras, BioSolarProperties properties,
                            Clock clock) {
        this.estado = estado;
        this.motorRegras = motorRegras;
        this.properties = properties;
        this.clock = clock;
    }

    public SimulacaoRequests.Resposta alterarVelocidade(int fator) {
        verificarHabilitado();
        Instant agora = clock.instant();
        return estado.executar(f -> {
            int anterior = f.getSimulacao().getFatorVelocidade();
            f.getSimulacao().setFatorVelocidade(fator);
            double minutos = properties.simulacao().minutosPorTick() * fator;
            String mensagem = "Velocidade alterada de " + anterior + "× para " + fator + "× (1 s real = "
                    + num(minutos) + " min na fazenda).";
            f.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, OrigemEvento.SIMULACAO, null, null,
                    "Simulação: velocidade " + fator + "×", mensagem, agora);
            return new SimulacaoRequests.Resposta(true, mensagem);
        });
    }

    public SimulacaoRequests.Resposta pausar(boolean pausada) {
        verificarHabilitado();
        Instant agora = clock.instant();
        return estado.executar(f -> {
            f.getSimulacao().setPausada(pausada);
            String mensagem = pausada
                    ? "Tempo da fazenda pausado. As regras de segurança continuam ativas."
                    : "Tempo da fazenda retomado.";
            f.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, OrigemEvento.SIMULACAO, null, null,
                    pausada ? "Simulação pausada" : "Simulação retomada", mensagem, agora);
            return new SimulacaoRequests.Resposta(true, mensagem);
        });
    }

    public SimulacaoRequests.Resposta ajustarUmidade(String talhaoId, double delta) {
        verificarHabilitado();
        Instant agora = clock.instant();
        String id = talhaoId.trim().toUpperCase(Locale.ROOT);
        return estado.executar(f -> {
            Talhao t = f.buscarTalhao(id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Talhão '" + id + "' não encontrado."));
            double antes = t.getUmidade();
            t.setUmidade(antes + delta);
            String mensagem = "Umidade do " + t.getNome() + ": " + pct(antes) + " → " + pct(t.getUmidade()) + " ("
                    + (delta >= 0 ? "+" : "") + num(delta) + " p.p.). Regras reavaliadas pelo servidor.";
            f.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, OrigemEvento.SIMULACAO, null, t.getId(),
                    "Simulação: umidade do " + t.getNome() + " alterada", mensagem, agora);
            motorRegras.avaliar(f, agora);
            return new SimulacaoRequests.Resposta(true, mensagem);
        });
    }

    public SimulacaoRequests.Resposta ajustarReservatorio(double delta) {
        verificarHabilitado();
        Instant agora = clock.instant();
        return estado.executar(f -> {
            Reservatorio r = f.getReservatorio();
            double antes = r.getNivel();
            r.setNivel(antes + delta);
            String mensagem = "Reservatório: " + pct(antes) + " → " + pct(r.getNivel()) + " ("
                    + (delta >= 0 ? "+" : "") + num(delta) + " p.p.). Regras reavaliadas pelo servidor.";
            f.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, OrigemEvento.SIMULACAO, null, null,
                    "Simulação: nível do reservatório alterado", mensagem, agora);
            motorRegras.avaliar(f, agora);
            return new SimulacaoRequests.Resposta(true, mensagem);
        });
    }

    public SimulacaoRequests.Resposta simularEmergencia() {
        verificarHabilitado();
        Instant agora = clock.instant();
        return estado.executar(f -> {
            Reservatorio r = f.getReservatorio();
            double antes = r.getNivel();
            r.setNivel(Math.min(antes, NIVEL_EMERGENCIA_SIMULADA));
            String mensagem = "Queda brusca simulada: reservatório " + pct(antes) + " → " + pct(r.getNivel())
                    + " (vazamento/falha na captação). Proteção hídrica avaliada pelo servidor.";
            f.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, OrigemEvento.SIMULACAO, null, null,
                    "Simulação: emergência hídrica", mensagem, agora);
            motorRegras.avaliar(f, agora);
            return new SimulacaoRequests.Resposta(true, mensagem);
        });
    }

    public SimulacaoRequests.Resposta restaurar() {
        verificarHabilitado();
        estado.restaurarCenario(OrigemEvento.SIMULACAO);
        return new SimulacaoRequests.Resposta(true, "Cenário de demonstração restaurado.");
    }

    private void verificarHabilitado() {
        if (!properties.simulacao().controlesDemonstracao()) {
            throw new OperacaoNaoPermitidaException(
                    "Controles de simulação desabilitados neste ambiente (BIOSOLAR_CONTROLES_DEMO=false).");
        }
    }
}
