package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.biosolar.citrus.dto.ConfiguracaoDTO;
import br.com.biosolar.citrus.dto.TalhaoCadastroDTO;
import br.com.biosolar.citrus.dto.TalhaoRequest;
import br.com.biosolar.citrus.exception.BancoIndisponivelException;
import br.com.biosolar.citrus.exception.ConflitoException;
import br.com.biosolar.citrus.exception.DadosInvalidosException;
import br.com.biosolar.citrus.exception.RecursoNaoEncontradoException;
import br.com.biosolar.citrus.model.CadastroReservatorio;
import br.com.biosolar.citrus.model.CadastroTalhao;
import br.com.biosolar.citrus.model.EstadoSimulacao;
import br.com.biosolar.citrus.model.Fazenda;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EstadoSimulacaoRepository;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.repository.LeituraTalhaoRepository;
import br.com.biosolar.citrus.repository.ReservatorioRepository;
import br.com.biosolar.citrus.repository.TalhaoRepository;
import br.com.biosolar.citrus.service.EstadoFazendaService.AlteracaoCadastro;

/**
 * Cadastro pela interface: talhoes (com sua motobomba, aspersor e sensor), reservatorio e usina solar.
 * Tudo e gravado no PostgreSQL antes de valer na automacao (ver {@link EstadoFazendaService#alterarCadastro}).
 * <p>
 * Excluir um talhao o <b>arquiva</b>: ele sai da automacao e do painel, mas o historico e preservado e ele pode
 * ser reativado. A exclusao definitiva so e permitida para talhoes arquivados.
 */
@Service
public class CadastroService {

    /** Limite de talhoes ativos: capacidade do reservatorio e 8 cores distinguiveis nos graficos. */
    public static final int MAXIMO_TALHOES = 8;
    /** Limite da irrigacao critica (P2) definido pelo regulamento para novos talhoes. */
    public static final double LIMITE_CRITICO_PADRAO = 25.0;

    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9]{1,10}");
    private static final String MENSAGEM_LEITURA_INDISPONIVEL = "Banco de dados indisponível no momento: o cadastro não "
            + "pode ser consultado agora. A automação continua funcionando; tente novamente em alguns segundos.";

    private final EstadoFazendaService estado;
    private final TalhaoRepository talhaoRepository;
    private final ReservatorioRepository reservatorioRepository;
    private final EstadoSimulacaoRepository estadoRepository;
    private final EventoRepository eventoRepository;
    private final LeituraTalhaoRepository leituraTalhaoRepository;
    private final TransactionTemplate transacaoLeitura;
    private final Clock clock;

    public CadastroService(EstadoFazendaService estado, TalhaoRepository talhaoRepository,
                           ReservatorioRepository reservatorioRepository, EstadoSimulacaoRepository estadoRepository,
                           EventoRepository eventoRepository, LeituraTalhaoRepository leituraTalhaoRepository,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.estado = estado;
        this.talhaoRepository = talhaoRepository;
        this.reservatorioRepository = reservatorioRepository;
        this.estadoRepository = estadoRepository;
        this.eventoRepository = eventoRepository;
        this.leituraTalhaoRepository = leituraTalhaoRepository;
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
        this.clock = clock;
    }

    // =============================================================================================
    // Talhoes
    // =============================================================================================

    /** Todos os talhoes cadastrados no banco, inclusive os arquivados. */
    public List<TalhaoCadastroDTO> listarTalhoes() {
        return lerDoBanco(() -> talhaoRepository.findAllByOrderByIdAsc().stream()
                .map(t -> TalhaoCadastroDTO.de(t, registrosHistorico(t.getId()))).toList());
    }

    public TalhaoCadastroDTO buscarTalhao(String id) {
        String codigo = normalizar(id);
        return lerDoBanco(() -> talhaoRepository.findById(codigo)
                .map(t -> TalhaoCadastroDTO.de(t, registrosHistorico(codigo)))
                .orElseThrow(() -> naoEncontrado(codigo)));
    }

    public TalhaoCadastroDTO criarTalhao(TalhaoRequest req) {
        String codigo = normalizar(req.id());
        if (!CODIGO.matcher(codigo).matches()) {
            throw new DadosInvalidosException("Código do talhão inválido.",
                    List.of("id: use de 1 a 10 letras ou números, sem espaços (ex.: E, T5, NORTE1)"));
        }
        CadastroTalhao cadastro = montarCadastro(req, LIMITE_CRITICO_PADRAO);
        Instant agora = clock.instant();

        return estado.alterarCadastro(fazenda -> {
            talhaoRepository.findById(codigo).ifPresent(existente -> {
                throw new ConflitoException("Já existe um talhão com o código " + codigo
                        + (existente.isAtivo() ? "." : " (arquivado). Reative-o na lista de talhões ou use outro código."));
            });
            verificarLimite(fazenda);
            Talhao novo = new Talhao(codigo, cadastro, agora);
            talhaoRepository.saveAndFlush(novo);
            return new AlteracaoCadastro<>(() -> {
                fazenda.adicionarTalhao(novo);
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.OPERADOR, null, null,
                        "Talhão cadastrado: " + novo.getNome(),
                        novo.getRotulo() + " (" + novo.getVariedade() + ", " + num(novo.getAreaHa()) + " ha) incluído na "
                                + "automação com umidade inicial de " + pct(novo.getUmidade()) + ". Bomba " + novo.getBombaId()
                                + " e sensor " + novo.getSensorId() + " em operação.",
                        agora);
            }, () -> TalhaoCadastroDTO.de(novo, 0));
        });
    }

    public TalhaoCadastroDTO atualizarTalhao(String id, TalhaoRequest req) {
        String codigo = normalizar(id);
        Instant agora = clock.instant();
        return estado.alterarCadastro(fazenda -> {
            Talhao doBanco = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            // O limite critico nao muda pela interface (regra P2 do regulamento)
            CadastroTalhao cadastro = montarCadastro(req, doBanco.getLimiteCritico());
            doBanco.aplicarCadastro(cadastro);
            talhaoRepository.saveAndFlush(doBanco);
            long historico = registrosHistorico(codigo);
            return new AlteracaoCadastro<>(() -> {
                fazenda.getTalhoes().stream().filter(t -> t.getId().equals(codigo)).findFirst()
                        .ifPresent(t -> t.aplicarCadastro(cadastro));
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.OPERADOR, null, null,
                        "Cadastro atualizado: " + cadastro.nome(),
                        "Dados do talhão " + codigo + " alterados pelo operador (atenção abaixo de "
                                + pct(cadastro.limiteAtencao()) + ", irrigação até " + pct(cadastro.umidadeAlvo())
                                + ", bomba de " + num(cadastro.vazaoBombaM3h()) + " m³/h). Regras reavaliadas.",
                        agora);
            }, () -> TalhaoCadastroDTO.de(doBanco, historico));
        });
    }

    /** "Excluir" pela interface: retira o talhao da automacao e preserva o historico. */
    public TalhaoCadastroDTO arquivarTalhao(String id) {
        String codigo = normalizar(id);
        Instant agora = clock.instant();
        return estado.alterarCadastro(fazenda -> {
            Talhao doBanco = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            if (!doBanco.isAtivo()) {
                throw new ConflitoException("O " + doBanco.getNome() + " já está arquivado.");
            }
            if (fazenda.getTalhoes().size() <= 1) {
                throw new ConflitoException("A fazenda precisa de pelo menos um talhão ativo. Cadastre outro antes de "
                        + "excluir este.");
            }
            doBanco.setAtivo(false);
            doBanco.prepararParaOperacao(agora);
            talhaoRepository.saveAndFlush(doBanco);
            long historico = registrosHistorico(codigo);
            return new AlteracaoCadastro<>(() -> {
                boolean estavaIrrigando = fazenda.getTalhoes().stream()
                        .anyMatch(t -> t.getId().equals(codigo) && t.isAspersorLigado());
                fazenda.removerTalhao(codigo, agora);
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.ATENCAO, OrigemEvento.OPERADOR, null, null,
                        "Talhão excluído da operação: " + doBanco.getNome(),
                        doBanco.getNome() + " arquivado pelo operador" + (estavaIrrigando ? " (bomba " + doBanco.getBombaId()
                                + " desligada)" : "") + ". O histórico foi preservado e o talhão pode ser reativado.",
                        agora);
            }, () -> TalhaoCadastroDTO.de(doBanco, historico));
        });
    }

    public TalhaoCadastroDTO reativarTalhao(String id) {
        String codigo = normalizar(id);
        Instant agora = clock.instant();
        return estado.alterarCadastro(fazenda -> {
            Talhao doBanco = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            if (doBanco.isAtivo()) {
                throw new ConflitoException("O " + doBanco.getNome() + " já está ativo.");
            }
            verificarLimite(fazenda);
            doBanco.setAtivo(true);
            doBanco.prepararParaOperacao(agora);
            talhaoRepository.saveAndFlush(doBanco);
            long historico = registrosHistorico(codigo);
            return new AlteracaoCadastro<>(() -> {
                fazenda.adicionarTalhao(doBanco);
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.OPERADOR, null, null,
                        "Talhão reativado: " + doBanco.getNome(),
                        doBanco.getNome() + " voltou à automação com umidade de " + pct(doBanco.getUmidade()) + ".",
                        agora);
            }, () -> TalhaoCadastroDTO.de(doBanco, historico));
        });
    }

    /**
     * Remove o talhao do banco. Somente para arquivados: as leituras de umidade dele sao apagadas e os eventos
     * continuam no historico, sem o vinculo com o talhao (o texto de cada evento ainda o identifica).
     */
    public void excluirTalhaoDefinitivamente(String id) {
        String codigo = normalizar(id);
        Instant agora = clock.instant();
        estado.alterarCadastro(fazenda -> {
            Talhao doBanco = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            if (doBanco.isAtivo()) {
                throw new ConflitoException("Exclua (arquive) o " + doBanco.getNome()
                        + " antes de removê-lo definitivamente.");
            }
            int leituras = leituraTalhaoRepository.excluirPorTalhao(codigo);
            int eventos = eventoRepository.desvincularTalhao(codigo);
            talhaoRepository.delete(doBanco);
            talhaoRepository.flush();
            return new AlteracaoCadastro<Void>(() -> fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.ATENCAO,
                    OrigemEvento.OPERADOR, null, null, "Talhão removido definitivamente: " + doBanco.getNome(),
                    "Cadastro do talhão " + codigo + " apagado do banco. " + leituras + " leitura(s) de umidade removida(s); "
                            + eventos + " evento(s) mantido(s) no histórico.",
                    agora), () -> null);
        });
    }

    // =============================================================================================
    // Reservatorio e usina
    // =============================================================================================

    public ConfiguracaoDTO obterConfiguracao() {
        boolean bancoDisponivel = !estado.isBancoEmEspera();
        return estado.ler(f -> {
            Reservatorio r = f.getReservatorio();
            return new ConfiguracaoDTO(
                    new ConfiguracaoDTO.Reservatorio(r.getNome(), r.getCapacidadeM3(), r.getVazaoRecargaM3h(),
                            r.getNivelInicial(), r.getLimiteAtencao(), r.getLimiteCritico(), r.getLimiteRearme()),
                    new ConfiguracaoDTO.Usina(f.getSimulacao().getPotenciaSolarPicoKw()),
                    new ConfiguracaoDTO.Regras(LIMITE_CRITICO_PADRAO, MotorRegras.LIMITE_SATURACAO, MAXIMO_TALHOES),
                    bancoDisponivel);
        });
    }

    public ConfiguracaoDTO atualizarReservatorio(ConfiguracaoDTO.ReservatorioRequest req) {
        Instant agora = clock.instant();
        estado.alterarCadastro(fazenda -> {
            Reservatorio doBanco = reservatorioRepository.findById(Reservatorio.ID_CENTRAL)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Reservatório central não encontrado."));
            CadastroReservatorio atual = doBanco.getCadastro();
            CadastroReservatorio novo = new CadastroReservatorio(req.nome().trim(), req.capacidadeM3(),
                    req.vazaoRecargaM3h(), req.nivelInicial(), atual.limiteAtencao(), atual.limiteCritico(),
                    atual.limiteRearme());
            doBanco.aplicarCadastro(novo);
            reservatorioRepository.saveAndFlush(doBanco);
            return new AlteracaoCadastro<Void>(() -> {
                fazenda.getReservatorio().aplicarCadastro(novo);
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.OPERADOR, null, null,
                        "Reservatório atualizado",
                        novo.nome() + ": capacidade de " + num(novo.capacidadeM3()) + " m³, recarga de até "
                                + num(novo.vazaoRecargaM3h()) + " m³/h e nível inicial de " + pct(novo.nivelInicial()) + ".",
                        agora);
            }, () -> null);
        });
        return obterConfiguracao();
    }

    public ConfiguracaoDTO atualizarUsina(ConfiguracaoDTO.UsinaRequest req) {
        Instant agora = clock.instant();
        estado.alterarCadastro(fazenda -> {
            EstadoSimulacao doBanco = estadoRepository.findById(EstadoSimulacao.ID_UNICO)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Estado da simulação não encontrado."));
            doBanco.setPotenciaSolarPicoKw(req.potenciaSolarPicoKw());
            estadoRepository.saveAndFlush(doBanco);
            return new AlteracaoCadastro<Void>(() -> {
                fazenda.getSimulacao().setPotenciaSolarPicoKw(req.potenciaSolarPicoKw());
                fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO, OrigemEvento.OPERADOR, null, null,
                        "Usina solar atualizada", "Potência de pico da usina fotovoltaica: "
                                + num(req.potenciaSolarPicoKw()) + " kWp.",
                        agora);
            }, () -> null);
        });
        return obterConfiguracao();
    }

    // =============================================================================================
    // Apoio
    // =============================================================================================

    private CadastroTalhao montarCadastro(TalhaoRequest req, double limiteCritico) {
        List<String> erros = new ArrayList<>();
        if (req.limiteAtencao() <= limiteCritico || req.limiteAtencao() >= MotorRegras.LIMITE_SATURACAO) {
            erros.add("limiteAtencao: deve ficar entre " + pct(limiteCritico) + " (crítico) e "
                    + pct(MotorRegras.LIMITE_SATURACAO) + " (saturação)");
        }
        if (req.umidadeAlvo() < req.limiteAtencao() || req.umidadeAlvo() >= MotorRegras.LIMITE_SATURACAO) {
            erros.add("umidadeAlvo: deve ser maior ou igual ao limite de atenção e menor que "
                    + pct(MotorRegras.LIMITE_SATURACAO) + " (saturação)");
        }
        if (req.ganhoIrrigacao() <= req.taxaEvapotranspiracao()) {
            erros.add("ganhoIrrigacao: deve ser maior que a evapotranspiração, senão a irrigação nunca recupera o solo");
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException("Dados do talhão inconsistentes.", erros);
        }
        return new CadastroTalhao(req.nome().trim(), req.cultura(), req.variedade().trim(), req.solo().trim(),
                req.areaHa(), req.plantas(), req.prioridade(), req.umidadeInicial(), limiteCritico,
                req.limiteAtencao(), req.umidadeAlvo(), req.taxaEvapotranspiracao(), req.ganhoIrrigacao(),
                req.vazaoBombaM3h(), req.potenciaBombaKw());
    }

    private void verificarLimite(Fazenda fazenda) {
        if (fazenda.getTalhoes().size() >= MAXIMO_TALHOES) {
            throw new ConflitoException("Limite de " + MAXIMO_TALHOES + " talhões ativos atingido (capacidade do "
                    + "reservatório e do painel). Exclua um talhão antes de cadastrar outro.");
        }
    }

    private long registrosHistorico(String codigo) {
        return eventoRepository.countByTalhaoId(codigo) + leituraTalhaoRepository.countByTalhaoId(codigo);
    }

    private <T> T lerDoBanco(java.util.function.Supplier<T> consulta) {
        if (estado.isBancoEmEspera()) {
            throw new BancoIndisponivelException(MENSAGEM_LEITURA_INDISPONIVEL);
        }
        try {
            return transacaoLeitura.execute(status -> consulta.get());
        } catch (DataAccessException | TransactionException e) {
            throw new BancoIndisponivelException(MENSAGEM_LEITURA_INDISPONIVEL);
        }
    }

    private static String normalizar(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    private static RecursoNaoEncontradoException naoEncontrado(String codigo) {
        return new RecursoNaoEncontradoException("Talhão '" + codigo + "' não encontrado.");
    }
}
