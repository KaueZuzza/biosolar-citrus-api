package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.pct;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EstadoSimulacaoRepository;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTalhaoRepository;
import br.com.biosolar.citrus.repository.LeituraTelemetriaRepository;
import br.com.biosolar.citrus.repository.ReservatorioRepository;
import br.com.biosolar.citrus.repository.TalhaoRepository;
import br.com.biosolar.citrus.simulation.CenarioInicial;
import jakarta.annotation.PostConstruct;

/**
 * Guardiao do estado operacional da fazenda (fonte unica da verdade).
 * <p>
 * Todo acesso passa por um lock justo: o ciclo do simulador, os comandos manuais e os controles de
 * demonstracao nunca alteram o estado ao mesmo tempo. Cada operacao de escrita e persistida no banco
 * (talhoes, reservatorio, simulador e eventos) na mesma transacao. Ao reiniciar, a API retoma o estado salvo.
 */
@Service
public class EstadoFazendaService {

    private static final Logger log = LoggerFactory.getLogger(EstadoFazendaService.class);

    private final ReentrantLock lock = new ReentrantLock(true);

    private final TalhaoRepository talhaoRepository;
    private final ReservatorioRepository reservatorioRepository;
    private final EstadoSimulacaoRepository estadoRepository;
    private final EventoRepository eventoRepository;
    private final LeituraTelemetriaRepository leituraRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final TransactionTemplate transacao;
    private final MotorRegras motorRegras;
    private final BioSolarProperties properties;
    private final Clock clock;

    /**
     * Apos uma falha de gravacao, novas tentativas sao adiadas por este intervalo. Sem isso, com o banco
     * fora do ar cada ciclo seguraria o lock ate o timeout de conexao e o dashboard ficaria sem resposta.
     */
    static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(10);

    private Fazenda fazenda;

    /** Incrementada a cada restauracao do cenario: leituras capturadas antes dela sao descartadas. */
    private volatile long geracao;

    private volatile Instant proximaTentativaPersistencia;

    public EstadoFazendaService(TalhaoRepository talhaoRepository, ReservatorioRepository reservatorioRepository,
                                EstadoSimulacaoRepository estadoRepository, EventoRepository eventoRepository,
                                LeituraTelemetriaRepository leituraRepository,
                                LeituraTalhaoRepository leituraTalhaoRepository,
                                PlatformTransactionManager transactionManager, MotorRegras motorRegras,
                                BioSolarProperties properties, Clock clock) {
        this.talhaoRepository = talhaoRepository;
        this.reservatorioRepository = reservatorioRepository;
        this.estadoRepository = estadoRepository;
        this.eventoRepository = eventoRepository;
        this.leituraRepository = leituraRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.transacao = new TransactionTemplate(transactionManager);
        this.motorRegras = motorRegras;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void inicializar() {
        lock.lock();
        try {
            Instant agora = clock.instant();
            List<Talhao> talhoes = talhaoRepository.findAll(Sort.by("id"));
            Optional<Reservatorio> reservatorio = reservatorioRepository.findById(Reservatorio.ID_CENTRAL);
            Optional<EstadoSimulacao> simulacao = estadoRepository.findById(EstadoSimulacao.ID_UNICO);

            if (talhoes.isEmpty() || reservatorio.isEmpty() || simulacao.isEmpty()) {
                fazenda = CenarioInicial.criar(agora, relogioInicial());
                fazenda.registrarEvento(TipoEvento.SISTEMA, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                        "Sistema iniciado",
                        "Cenário inicial criado no banco de dados: " + fazenda.getTalhoes().size()
                                + " talhões e reservatório central em " + pct(fazenda.getReservatorio().getNivel())
                                + ". Monitoramento automático ativo.",
                        agora);
                log.info("Banco vazio: cenario inicial criado.");
            } else {
                fazenda = new Fazenda(talhoes, reservatorio.get(), simulacao.get());
                fazenda.registrarEvento(TipoEvento.SISTEMA, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                        "API reiniciada: estado restaurado",
                        "Estado operacional recuperado do banco de dados (reservatório em "
                                + pct(fazenda.getReservatorio().getNivel()) + ", " + fazenda.aspersoresLigados()
                                + " aspersor(es) ligado(s)). Regras de segurança reavaliadas.",
                        agora);
                log.info("Estado da fazenda restaurado do banco de dados.");
            }
            fazenda.getReservatorio().setNivelUltimaAvaliacao(fazenda.getReservatorio().getNivel());
            motorRegras.avaliar(fazenda, agora);
            persistir();
        } finally {
            lock.unlock();
        }
    }

    /** Leitura consistente do estado (sem persistencia). */
    public <T> T ler(Function<Fazenda, T> leitura) {
        lock.lock();
        try {
            return leitura.apply(fazenda);
        } finally {
            lock.unlock();
        }
    }

    /** Alteracao do estado seguida de persistencia no banco. */
    public <T> T executar(Function<Fazenda, T> operacao) {
        lock.lock();
        try {
            T resultado = operacao.apply(fazenda);
            persistir();
            return resultado;
        } finally {
            lock.unlock();
        }
    }

    /** Volta ao cenario de demonstracao e limpa historico/eventos. */
    public void restaurarCenario(OrigemEvento origem) {
        lock.lock();
        try {
            Instant agora = clock.instant();
            transacao.executeWithoutResult(status -> {
                leituraTalhaoRepository.deleteAllInBatch();
                leituraRepository.deleteAllInBatch();
                eventoRepository.deleteAllInBatch();
            });
            fazenda = CenarioInicial.criar(agora, relogioInicial());
            geracao++;
            fazenda.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, origem, null, null,
                    "Cenário de demonstração restaurado",
                    "Reservatório em " + pct(fazenda.getReservatorio().getNivel())
                            + ", aspersores desligados e histórico reiniciado. Relógio da fazenda às "
                            + properties.simulacao().horaInicial() + "h.",
                    agora);
            fazenda.getReservatorio().setNivelUltimaAvaliacao(fazenda.getReservatorio().getNivel());
            motorRegras.avaliar(fazenda, agora);
            persistir();
        } finally {
            lock.unlock();
        }
    }

    public long getGeracao() {
        return geracao;
    }

    /**
     * Grava uma leitura do historico capturada na {@code geracaoDaLeitura}. E descartada se o cenario foi
     * restaurado depois da captura (evita um ponto antigo no historico recem-limpo) ou se o banco estiver
     * em espera apos uma falha (o historico e secundario; o estado operacional segue em memoria).
     */
    public void registrarLeitura(long geracaoDaLeitura, Runnable gravacao) {
        lock.lock();
        try {
            if (geracaoDaLeitura != geracao || emEsperaAposFalha()) {
                return;
            }
            try {
                gravacao.run();
            } catch (DataAccessException | TransactionException e) {
                registrarFalha(e);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Banco em espera apos uma falha recente: consultas nao essenciais podem ser puladas. */
    public boolean isBancoEmEspera() {
        return emEsperaAposFalha();
    }

    private boolean emEsperaAposFalha() {
        return proximaTentativaPersistencia != null && clock.instant().isBefore(proximaTentativaPersistencia);
    }

    private void registrarFalha(RuntimeException e) {
        if (proximaTentativaPersistencia == null) {
            log.error("Falha ao gravar no banco (a automacao segue ativa em memoria; nova tentativa em {} s): {}",
                    ESPERA_APOS_FALHA.toSeconds(), e.getMessage());
        }
        proximaTentativaPersistencia = clock.instant().plus(ESPERA_APOS_FALHA);
    }

    private LocalDateTime relogioInicial() {
        return LocalDate.now(clock).atTime(properties.simulacao().horaInicial(), 0);
    }

    /**
     * Grava o estado atual. Se o banco estiver indisponivel, a automacao continua operando em memoria
     * (as regras de seguranca nunca dependem do banco) e os eventos sao mantidos para a proxima gravacao.
     * Apos uma falha, as tentativas ficam suspensas por {@link #ESPERA_APOS_FALHA}; como o estado completo e
     * regravado a cada vez, nada se perde alem das leituras do historico desse intervalo.
     */
    private void persistir() {
        if (emEsperaAposFalha()) {
            return;
        }
        List<Evento> eventos = fazenda.drenarEventos();
        try {
            transacao.executeWithoutResult(status -> {
                talhaoRepository.saveAll(fazenda.getTalhoes());
                reservatorioRepository.save(fazenda.getReservatorio());
                estadoRepository.save(fazenda.getSimulacao());
                eventoRepository.saveAll(eventos);
            });
            if (proximaTentativaPersistencia != null) {
                proximaTentativaPersistencia = null;
                log.info("Conexao com o banco restabelecida: estado e eventos pendentes gravados.");
            }
            eventos.forEach(e -> log.info("[{}] {} | {}", e.getSeveridade(), e.getTitulo(), e.getDescricao()));
        } catch (DataAccessException | TransactionException e) {
            // Copias sem id: o original pode ter recebido o id do banco antes do rollback
            eventos.forEach(ev -> fazenda.registrarEventoPendente(ev.copiaParaNovaTentativa()));
            registrarFalha(e);
        }
    }
}
