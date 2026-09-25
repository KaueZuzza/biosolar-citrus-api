package br.com.biosolar.citrus.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Talhao do pomar com seu sensor de umidade do solo e o conjunto motobomba + aspersor.
 * Cada talhao possui uma bomba dedicada (id {@code MB-<talhao>}): ligar a bomba liga o aspersor.
 */
@Entity
@Table(name = "talhao")
public class Talhao {

    @Id
    private String id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cultura cultura;

    @Column(nullable = false)
    private String variedade;

    @Column(nullable = false)
    private String solo;

    @Column(name = "area_ha", nullable = false)
    private double areaHa;

    @Column(nullable = false)
    private int plantas;

    /** 1 = alta (maior sensibilidade ao deficit hidrico) ... 3 = baixa. */
    @Column(nullable = false)
    private int prioridade;

    /** Umidade volumetrica do solo (%). */
    @Column(nullable = false)
    private double umidade;

    @Column(name = "limite_critico", nullable = false)
    private double limiteCritico;

    @Column(name = "limite_atencao", nullable = false)
    private double limiteAtencao;

    /** Umidade em que a irrigacao automatica e encerrada. */
    @Column(name = "umidade_alvo", nullable = false)
    private double umidadeAlvo;

    /** Perda de umidade no pico de radiacao solar (% por hora). */
    @Column(name = "taxa_evapotranspiracao", nullable = false)
    private double taxaEvapotranspiracao;

    /** Ganho de umidade com o aspersor ligado (% por hora). */
    @Column(name = "ganho_irrigacao", nullable = false)
    private double ganhoIrrigacao;

    @Column(name = "vazao_bomba_m3h", nullable = false)
    private double vazaoBombaM3h;

    @Column(name = "potencia_bomba_kw", nullable = false)
    private double potenciaBombaKw;

    @Column(name = "aspersor_ligado", nullable = false)
    private boolean aspersorLigado;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_acionamento", nullable = false)
    private ModoAcionamento modoAcionamento = ModoAcionamento.DESLIGADO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusTalhao status = StatusTalhao.NORMAL;

    /** Talhao critico impedido de irrigar pelo bloqueio de emergencia. */
    @Column(name = "irrigacao_bloqueada", nullable = false)
    private boolean irrigacaoBloqueada;

    @Column(name = "ultima_atualizacao", nullable = false)
    private Instant ultimaAtualizacao;

    @Column(name = "ultimo_acionamento")
    private Instant ultimoAcionamento;

    /** Umidade usada ao restaurar o cenario de demonstracao. */
    @Column(name = "umidade_inicial", nullable = false)
    private double umidadeInicial;

    /** Talhao arquivado (excluido da operacao) continua no banco para preservar o historico. */
    @Column(nullable = false)
    private boolean ativo = true;

    protected Talhao() {
    }

    /** Novo talhao cadastrado pela interface: comeca com a umidade inicial e o aspersor desligado. */
    public Talhao(String id, CadastroTalhao cadastro, Instant agora) {
        this.id = id;
        aplicarCadastro(cadastro);
        this.umidade = cadastro.umidadeInicial();
        this.ultimaAtualizacao = agora;
        this.status = classificarUmidade();
    }

    public Talhao(String id, Cultura cultura, String variedade, String solo, double areaHa, int plantas,
                  int prioridade, double umidade, double taxaEvapotranspiracao, Instant agora) {
        this.id = id;
        this.nome = "Talhão " + id;
        this.cultura = cultura;
        this.variedade = variedade;
        this.solo = solo;
        this.areaHa = areaHa;
        this.plantas = plantas;
        this.prioridade = prioridade;
        this.umidade = umidade;
        this.limiteCritico = 25;
        this.limiteAtencao = 30;
        this.umidadeAlvo = 40;
        this.taxaEvapotranspiracao = taxaEvapotranspiracao;
        this.ganhoIrrigacao = 7.0;
        this.vazaoBombaM3h = 18.0;
        this.potenciaBombaKw = 5.5;
        this.umidadeInicial = umidade;
        this.ultimaAtualizacao = agora;
        this.status = classificarUmidade();
    }

    // ---- Cadastro -------------------------------------------------------------------------------

    public CadastroTalhao getCadastro() {
        return new CadastroTalhao(nome, cultura, variedade, solo, areaHa, plantas, prioridade, umidadeInicial,
                limiteCritico, limiteAtencao, umidadeAlvo, taxaEvapotranspiracao, ganhoIrrigacao, vazaoBombaM3h,
                potenciaBombaKw);
    }

    /** Aplica os dados cadastrais; o estado operacional (umidade atual, aspersor) nao e alterado. */
    public void aplicarCadastro(CadastroTalhao c) {
        this.nome = c.nome();
        this.cultura = c.cultura();
        this.variedade = c.variedade();
        this.solo = c.solo();
        this.areaHa = c.areaHa();
        this.plantas = c.plantas();
        this.prioridade = c.prioridade();
        this.umidadeInicial = c.umidadeInicial();
        this.limiteCritico = c.limiteCritico();
        this.limiteAtencao = c.limiteAtencao();
        this.umidadeAlvo = c.umidadeAlvo();
        this.taxaEvapotranspiracao = c.taxaEvapotranspiracao();
        this.ganhoIrrigacao = c.ganhoIrrigacao();
        this.vazaoBombaM3h = c.vazaoBombaM3h();
        this.potenciaBombaKw = c.potenciaBombaKw();
    }

    /** Volta a umidade inicial cadastrada, com o aspersor desligado (restauracao do cenario). */
    public void reiniciarOperacao(Instant agora) {
        this.umidade = umidadeInicial;
        this.aspersorLigado = false;
        this.modoAcionamento = ModoAcionamento.DESLIGADO;
        this.irrigacaoBloqueada = false;
        this.ultimoAcionamento = null;
        this.ultimaAtualizacao = agora;
        this.status = classificarUmidade();
    }

    /** Talhao que entra (ou volta) na operacao: aspersor desligado e status recalculado. */
    public void prepararParaOperacao(Instant agora) {
        if (aspersorLigado) {
            desligarAspersor(agora);
        }
        this.irrigacaoBloqueada = false;
        this.ultimaAtualizacao = agora;
        this.status = classificarUmidade();
    }

    // ---- Regras do proprio talhao ------------------------------------------------------------

    public boolean isCritico() {
        return umidade < limiteCritico;
    }

    public StatusTalhao classificarUmidade() {
        if (umidade < limiteCritico) {
            return StatusTalhao.CRITICO;
        }
        if (umidade < limiteAtencao) {
            return StatusTalhao.ATENCAO;
        }
        return StatusTalhao.NORMAL;
    }

    public void ligarAspersor(ModoAcionamento modo, Instant agora) {
        this.aspersorLigado = true;
        this.modoAcionamento = modo;
        this.ultimoAcionamento = agora;
        this.irrigacaoBloqueada = false;
    }

    public void desligarAspersor(Instant agora) {
        this.aspersorLigado = false;
        this.modoAcionamento = ModoAcionamento.DESLIGADO;
        this.ultimoAcionamento = agora;
    }

    public String getBombaId() {
        return "MB-" + id;
    }

    public String getRotulo() {
        return nome + " (" + cultura.getRotulo() + ")";
    }

    // ---- Getters / setters ---------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Cultura getCultura() {
        return cultura;
    }

    public String getVariedade() {
        return variedade;
    }

    public String getSolo() {
        return solo;
    }

    public double getAreaHa() {
        return areaHa;
    }

    public int getPlantas() {
        return plantas;
    }

    public int getPrioridade() {
        return prioridade;
    }

    public double getUmidade() {
        return umidade;
    }

    public void setUmidade(double umidade) {
        this.umidade = Math.max(0, Math.min(100, umidade));
    }

    public double getLimiteCritico() {
        return limiteCritico;
    }

    public double getLimiteAtencao() {
        return limiteAtencao;
    }

    public double getUmidadeAlvo() {
        return umidadeAlvo;
    }

    public double getTaxaEvapotranspiracao() {
        return taxaEvapotranspiracao;
    }

    public double getGanhoIrrigacao() {
        return ganhoIrrigacao;
    }

    public double getVazaoBombaM3h() {
        return vazaoBombaM3h;
    }

    public double getPotenciaBombaKw() {
        return potenciaBombaKw;
    }

    public boolean isAspersorLigado() {
        return aspersorLigado;
    }

    public ModoAcionamento getModoAcionamento() {
        return modoAcionamento;
    }

    public StatusTalhao getStatus() {
        return status;
    }

    public void setStatus(StatusTalhao status) {
        this.status = status;
    }

    public boolean isIrrigacaoBloqueada() {
        return irrigacaoBloqueada;
    }

    public void setIrrigacaoBloqueada(boolean irrigacaoBloqueada) {
        this.irrigacaoBloqueada = irrigacaoBloqueada;
    }

    public Instant getUltimaAtualizacao() {
        return ultimaAtualizacao;
    }

    public void setUltimaAtualizacao(Instant ultimaAtualizacao) {
        this.ultimaAtualizacao = ultimaAtualizacao;
    }

    public Instant getUltimoAcionamento() {
        return ultimoAcionamento;
    }

    public String getSensorId() {
        return "SU-" + id;
    }

    public double getUmidadeInicial() {
        return umidadeInicial;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
