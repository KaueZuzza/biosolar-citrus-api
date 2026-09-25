package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.pct;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.biosolar.citrus.config.BioSolarProperties;
import br.com.biosolar.citrus.exception.BancoIndisponivelException;
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
 * Guardiao do estado operacional da fazenda (fonte unica da verdade da automacao).
 * <p>
 * Todo acesso passa por um lock justo: o ciclo do simulador, os comandos manuais, os controles de
 * demonstracao e as alteracoes de cadastro nunca alteram o estado ao mesmo tempo.
 * <ul>
 *   <li><b>Estado operacional</b> (umidade, aspersores, nivel, relogio): calculado em memoria e gravado no banco
 *       a cada alteracao, somente nas colunas operacionais.</li>
 *   <li><b>Cadastro</b> (talhoes, reservatorio, usina): o banco e a referencia. Alteracoes pela API sao gravadas
 *       antes de valer na memoria; alteracoes feitas diretamente no banco (ex.: pgAdmin) sao trazidas para a
 *       automacao por {@link #sincronizarComBanco()}.</li>
 * </ul>
 * Ao reiniciar, a API retoma o estado salvo. Com o banco fora do ar, a automacao segue em memoria.
 */
@Service
public class EstadoFazendaService {

    private static final Logger log = LoggerFactory.getLogger(EstadoFazendaService.class);

    static final String MENSAGEM_BANCO_INDISPONIVEL = "Banco de dados indisponível no momento: a alteração não foi "
            + "gravada. A automação continua funcionando; tente novamente em alguns segundos.";

    /**
     * Apos uma falha de gravacao, novas tentativas sao adiadas por este intervalo. Sem isso, com o banco
     * fora do ar cada ciclo seguraria o lock ate o timeout de conexao e o dashboard ficaria sem resposta.
     */
    static final Duration ESPERA_APOS_FALHA = Duration.ofSeconds(10);

    private final ReentrantLock lock = new ReentrantLock(true);

    private final TalhaoRepository talhaoRepository;
    private final ReservatorioRepository reservatorioRepository;
    private final EstadoSimulacaoRepository estadoRepository;
    private final EventoRepository eventoRepository;
    private final LeituraTelemetriaRepository leituraRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final TransactionTemplate transacao;
    private final TransactionTemplate transacaoLeitura;
    private final MotorRegras motorRegras;
    private final BioSolarProperties properties;
    private final Clock clock;

    private Fazenda fazenda;

    /** Incrementada a cada restauracao do cenario: leituras capturadas antes dela sao descartadas. */
    private volatile long geracao;

    /** Incrementada a cada alteracao de cadastro: uma sincronizacao lida antes dela e descartada. */
    private volatile long versaoCadastro;

    private volatile Instant proximaTentativaPersistencia;

    /** Alteracao de cadastro em duas fases: o banco e gravado primeiro; so depois a memoria e alterada. */
    public record AlteracaoCadastro<T>(Runnable aplicarNaMemoria, Supplier<T> resultado) {
    }

    private record RetratoBanco(List<Talhao> talhoes, Optional<Reservatorio> reservatorio,
                                Optional<EstadoSimulacao> simulacao) {
    }

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
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
        this.motorRegras = motorRegras;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void inicializar() {
        lock.lock();
        try {
            Instant agora = clock.instant();
            RetratoBanco banco = lerBanco();
            boolean bancoVazio = banco.talhoes().isEmpty() && banco.reservatorio().isEmpty()
                    && banco.simulacao().isEmpty();

            // Primeira execucao (ou linha removida manualmente): completa com o cenario de referencia
            Fazenda cenario = CenarioInicial.criar(agora, relogioInicial());
            List<Talhao> ativos = banco.talhoes().isEmpty()
                    ? cenario.getTalhoes()
                    : banco.talhoes().stream().filter(Talhao::isAtivo).toList();
            Reservatorio reservatorio = banco.reservatorio().orElse(cenario.getReservatorio());
            EstadoSimulacao simulacao = banco.simulacao().orElse(cenario.getSimulacao());
            transacao.executeWithoutResult(status -> {
                if (banco.talhoes().isEmpty()) {
                    talhaoRepository.saveAll(ativos);
                }
                if (banco.reservatorio().isEmpty()) {
                    reservatorioRepository.save(reservatorio);
                }
                if (banco.simulacao().isEmpty()) {
                    estadoRepository.save(simulacao);
                }
            });

            fazenda = new Fazenda(ativos, reservatorio, simulacao);
            if (bancoVazio) {
                fazenda.registrarEvento(TipoEvento.SISTEMA, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                        "Sistema iniciado",
                        "Cenário inicial criado no banco de dados: " + fazenda.getTalhoes().size()
                                + " talhões e reservatório central em " + pct(reservatorio.getNivel())
                                + ". Monitoramento automático ativo.",
                        agora);
                log.info("Banco vazio: cenario inicial criado.");
            } else {
                fazenda.registrarEvento(TipoEvento.SISTEMA, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                        "API reiniciada: estado restaurado",
                        "Estado operacional recuperado do banco de dados (" + fazenda.getTalhoes().size()
                                + " talhões ativos, reservatório em " + pct(reservatorio.getNivel()) + ", "
                                + fazenda.aspersoresLigados() + " aspersor(es) ligado(s)). Regras de segurança reavaliadas.",
                        agora);
                log.info("Estado da fazenda restaurado do banco de dados ({} talhoes ativos).", ativos.size());
            }
            if (ativos.isEmpty()) {
                log.warn("Nenhum talhao ativo no banco: cadastre ou reative um talhao pela aba Gestao.");
            }
            reservatorio.setNivelUltimaAvaliacao(reservatorio.getNivel());
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

    /** Alteracao do estado operacional seguida de persistencia no banco. */
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

    /**
     * Alteracao de cadastro (talhoes, reservatorio, usina). {@code preparar} roda dentro de uma transacao: valida,
     * grava no banco e devolve o que deve mudar na memoria. A memoria so e alterada depois do commit, entao uma
     * falha no banco nunca deixa a automacao com um cadastro que nao foi gravado.
     */
    public <T> T alterarCadastro(Function<Fazenda, AlteracaoCadastro<T>> preparar) {
        lock.lock();
        try {
            if (emEsperaAposFalha()) {
                throw new BancoIndisponivelException(MENSAGEM_BANCO_INDISPONIVEL);
            }
            AlteracaoCadastro<T> alteracao;
            try {
                alteracao = transacao.execute(status -> preparar.apply(fazenda));
            } catch (DataIntegrityViolationException e) {
                throw e;
            } catch (DataAccessException | TransactionException e) {
                registrarFalha(e);
                throw new BancoIndisponivelException(MENSAGEM_BANCO_INDISPONIVEL);
            }
            Instant agora = clock.instant();
            alteracao.aplicarNaMemoria().run();
            versaoCadastro++;
            motorRegras.avaliar(fazenda, agora);
            persistir();
            return alteracao.resultado().get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Traz para a automacao as alteracoes de cadastro feitas diretamente no banco (ex.: pgAdmin): talhoes
     * incluidos, editados, arquivados ou removidos, reservatorio e usina. O estado operacional nao e lido: ele
     * e calculado pela automacao e regravado a cada ciclo.
     *
     * @return quantidade de alteracoes aplicadas
     */
    public int sincronizarComBanco() {
        if (emEsperaAposFalha()) {
            return 0;
        }
        long versaoLida = versaoCadastro;
        RetratoBanco banco;
        try {
            banco = transacaoLeitura.execute(status -> lerBanco());
        } catch (DataAccessException | TransactionException e) {
            registrarFalha(e);
            return 0;
        }

        lock.lock();
        try {
            if (versaoLida != versaoCadastro) {
                return 0; // a API alterou o cadastro durante a leitura: a proxima sincronizacao le de novo
            }
            Instant agora = clock.instant();
            List<String> mudancas = new ArrayList<>();

            Set<String> ativosNoBanco = banco.talhoes().stream().filter(Talhao::isAtivo).map(Talhao::getId)
                    .collect(Collectors.toSet());
            for (Talhao doBanco : banco.talhoes()) {
                if (!doBanco.isAtivo()) {
                    continue;
                }
                Optional<Talhao> naMemoria = talhaoNaMemoria(doBanco.getId());
                if (naMemoria.isPresent()) {
                    if (!naMemoria.get().getCadastro().equals(doBanco.getCadastro())) {
                        naMemoria.get().aplicarCadastro(doBanco.getCadastro());
                        mudancas.add("dados do " + doBanco.getNome() + " atualizados");
                    }
                } else {
                    doBanco.prepararParaOperacao(agora);
                    fazenda.adicionarTalhao(doBanco);
                    mudancas.add(doBanco.getNome() + " incluído na operação");
                }
            }
            for (Talhao t : List.copyOf(fazenda.getTalhoes())) {
                if (!ativosNoBanco.contains(t.getId())) {
                    fazenda.removerTalhao(t.getId(), agora);
                    mudancas.add(t.getNome() + " retirado da operação");
                }
            }
            banco.reservatorio().ifPresent(r -> {
                if (!fazenda.getReservatorio().getCadastro().equals(r.getCadastro())) {
                    fazenda.getReservatorio().aplicarCadastro(r.getCadastro());
                    mudancas.add("reservatório atualizado");
                }
            });
            banco.simulacao().ifPresent(s -> {
                if (fazenda.getSimulacao().getPotenciaSolarPicoKw() != s.getPotenciaSolarPicoKw()) {
                    fazenda.getSimulacao().setPotenciaSolarPicoKw(s.getPotenciaSolarPicoKw());
                    mudancas.add("usina solar atualizada");
                }
            });

            if (!mudancas.isEmpty()) {
                versaoCadastro++;
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                        "Cadastro atualizado pelo banco de dados",
                        "Alterações feitas diretamente no PostgreSQL aplicadas à automação: "
                                + String.join("; ", mudancas) + ".",
                        agora);
                motorRegras.avaliar(fazenda, agora);
                persistir();
                log.info("Sincronizacao com o banco: {}", mudancas);
            }
            return mudancas.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Restaura o cenario de demonstracao: volta aos niveis iniciais CADASTRADOS (umidade inicial de cada talhao
     * ativo e nivel inicial do reservatorio), desliga os aspersores e reinicia o relogio e o historico.
     * O cadastro (talhoes, reservatorio, usina) e mantido.
     */
    public void restaurarCenario(OrigemEvento origem) {
        lock.lock();
        try {
            if (emEsperaAposFalha()) {
                throw new BancoIndisponivelException(MENSAGEM_BANCO_INDISPONIVEL);
            }
            Instant agora = clock.instant();
            try {
                transacao.executeWithoutResult(status -> {
                    leituraTalhaoRepository.deleteAllInBatch();
                    leituraRepository.deleteAllInBatch();
                    eventoRepository.deleteAllInBatch();
                });
            } catch (DataAccessException | TransactionException e) {
                registrarFalha(e);
                throw new BancoIndisponivelException(MENSAGEM_BANCO_INDISPONIVEL);
            }
            geracao++;
            fazenda.descartarEventosPendentes();
            fazenda.getTalhoes().forEach(t -> t.reiniciarOperacao(agora));
            fazenda.getReservatorio().reiniciarOperacao(agora);
            fazenda.getSimulacao().reiniciar(relogioInicial(), agora);
            fazenda.registrarEvento(TipoEvento.SIMULACAO, Severidade.INFO, origem, null, null,
                    "Cenário de demonstração restaurado",
                    "Níveis iniciais cadastrados: reservatório em " + pct(fazenda.getReservatorio().getNivel()) + " e "
                            + fazenda.getTalhoes().size() + " talhões com a umidade inicial. Aspersores desligados e "
                            + "histórico reiniciado. Relógio da fazenda às " + properties.simulacao().horaInicial() + "h.",
                    agora);
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
            } catch (DataIntegrityViolationException e) {
                // Ex.: talhao removido do banco entre a captura e a gravacao; a proxima leitura ja nao o inclui
                log.warn("Leitura do historico descartada: {}", e.getMostSpecificCause().getMessage());
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

    private RetratoBanco lerBanco() {
        return new RetratoBanco(talhaoRepository.findAllByOrderByIdAsc(),
                reservatorioRepository.findById(Reservatorio.ID_CENTRAL),
                estadoRepository.findById(EstadoSimulacao.ID_UNICO));
    }

    private Optional<Talhao> talhaoNaMemoria(String id) {
        return fazenda.getTalhoes().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    private LocalDateTime relogioInicial() {
        return LocalDate.now(clock).atTime(properties.simulacao().horaInicial(), 0);
    }

    /**
     * Grava o estado operacional atual e os eventos pendentes. Somente as colunas operacionais sao atualizadas,
     * para nao sobrescrever edicoes de cadastro feitas diretamente no banco. Se o banco estiver indisponivel, a
     * automacao continua operando em memoria (as regras de seguranca nunca dependem do banco) e os eventos sao
     * mantidos para a proxima gravacao. Apos uma falha, as tentativas ficam suspensas por
     * {@link #ESPERA_APOS_FALHA}; como o estado completo e regravado a cada vez, nada se perde alem das leituras
     * do historico desse intervalo.
     */
    private void persistir() {
        if (emEsperaAposFalha()) {
            return;
        }
        List<Evento> eventos = fazenda.drenarEventos();
        try {
            transacao.executeWithoutResult(status -> {
                fazenda.getTalhoes().forEach(talhaoRepository::atualizarOperacao);
                if (reservatorioRepository.atualizarOperacao(fazenda.getReservatorio()) == 0) {
                    reservatorioRepository.save(fazenda.getReservatorio()); // linha removida manualmente
                }
                if (estadoRepository.atualizarOperacao(fazenda.getSimulacao()) == 0) {
                    estadoRepository.save(fazenda.getSimulacao());
                }
                eventoRepository.saveAll(eventos);
            });
            if (proximaTentativaPersistencia != null) {
                proximaTentativaPersistencia = null;
                log.info("Conexao com o banco restabelecida: estado e eventos pendentes gravados.");
            }
            eventos.forEach(e -> log.info("[{}] {} | {}", e.getSeveridade(), e.getTitulo(), e.getDescricao()));
        } catch (DataIntegrityViolationException e) {
            // Nao e queda do banco: um evento aponta para um talhao removido diretamente no banco. Os eventos
            // voltam para a fila sem o vinculo com o talhao (o texto continua identificando-o).
            log.warn("Eventos regravados sem vinculo de talhao: {}", e.getMostSpecificCause().getMessage());
            eventos.forEach(ev -> fazenda.registrarEventoPendente(ev.copiaParaNovaTentativa(false)));
        } catch (DataAccessException | TransactionException e) {
            // Copias sem id: o original pode ter recebido o id do banco antes do rollback
            eventos.forEach(ev -> fazenda.registrarEventoPendente(ev.copiaParaNovaTentativa(true)));
            registrarFalha(e);
        }
    }
}
