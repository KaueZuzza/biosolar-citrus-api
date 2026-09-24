package br.com.biosolar.citrus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import br.com.biosolar.citrus.simulation.ModeloSolar;
import br.com.biosolar.citrus.util.Formatador;

/**
 * Agregado em memoria com o estado operacional da fazenda (fonte da verdade do servidor).
 * O acesso concorrente e serializado por {@code EstadoFazendaService}, que tambem o persiste.
 * Os calculos derivados (vazoes, energia, autonomia) ficam aqui para que simulador,
 * motor de regras, indicadores e dashboard usem exatamente os mesmos numeros.
 */
public class Fazenda {

    /** Parcela da evapotranspiracao que ocorre mesmo sem sol (noite). */
    private static final double ET_BASE_NOTURNA = 0.2;

    private final List<Talhao> talhoes;
    private final Reservatorio reservatorio;
    private final EstadoSimulacao simulacao;
    private final List<Evento> eventosPendentes = new ArrayList<>();

    public Fazenda(List<Talhao> talhoes, Reservatorio reservatorio, EstadoSimulacao simulacao) {
        this.talhoes = new ArrayList<>(talhoes);
        this.talhoes.sort(Comparator.comparing(Talhao::getId));
        this.reservatorio = reservatorio;
        this.simulacao = simulacao;
    }

    // ---- Eventos --------------------------------------------------------------------------------

    public void registrarEvento(TipoEvento tipo, Severidade severidade, OrigemEvento origem, String regra,
                                String talhaoId, String titulo, String descricao, Instant agora) {
        eventosPendentes.add(new Evento(agora, simulacao.getRelogioSimulado(), tipo, severidade, origem,
                regra, talhaoId, titulo, descricao, Formatador.r1(reservatorio.getNivel())));
    }

    /** Devolve um evento a fila (usado quando a gravacao no banco falha). */
    public void registrarEventoPendente(Evento evento) {
        eventosPendentes.add(evento);
    }

    /** Retorna e limpa os eventos ainda nao persistidos. */
    public List<Evento> drenarEventos() {
        List<Evento> eventos = List.copyOf(eventosPendentes);
        eventosPendentes.clear();
        return eventos;
    }

    public List<Evento> getEventosPendentes() {
        return List.copyOf(eventosPendentes);
    }

    // ---- Consultas ------------------------------------------------------------------------------

    public Optional<Talhao> buscarTalhao(String id) {
        return talhoes.stream().filter(t -> t.getId().equalsIgnoreCase(id)).findFirst();
    }

    public List<Talhao> talhoesCriticos() {
        return talhoes.stream().filter(Talhao::isCritico).toList();
    }

    public int aspersoresLigados() {
        return (int) talhoes.stream().filter(Talhao::isAspersorLigado).count();
    }

    public double umidadeMedia() {
        return talhoes.stream().mapToDouble(Talhao::getUmidade).average().orElse(0);
    }

    // ---- Balanco hidrico e energetico -----------------------------------------------------------

    /** Fator de irradiancia solar (0 a 1) no horario simulado atual. */
    public double fatorSolar() {
        return ModeloSolar.fatorSolar(simulacao.getRelogioSimulado());
    }

    public double consumoAguaM3h() {
        return talhoes.stream().filter(Talhao::isAspersorLigado).mapToDouble(Talhao::getVazaoBombaM3h).sum();
    }

    public double recargaM3h() {
        return reservatorio.getVazaoRecargaM3h() * fatorSolar();
    }

    public double consumoEnergiaKw() {
        return talhoes.stream().filter(Talhao::isAspersorLigado).mapToDouble(Talhao::getPotenciaBombaKw).sum();
    }

    public double geracaoSolarKw() {
        return simulacao.getPotenciaSolarPicoKw() * fatorSolar();
    }

    /** Percentual (0-100) do consumo das bombas coberto pela usina solar; 100 quando nao ha consumo. */
    public double coberturaSolar() {
        double consumo = consumoEnergiaKw();
        return consumo <= 0 ? 100 : Math.min(100, geracaoSolarKw() / consumo * 100);
    }

    /** Perda de umidade por evapotranspiracao (% por hora), maior nas horas de sol forte. */
    public double evapotranspiracaoPorHora(Talhao t) {
        return t.getTaxaEvapotranspiracao() * (ET_BASE_NOTURNA + (1 - ET_BASE_NOTURNA) * fatorSolar());
    }

    /** Variacao liquida da umidade do talhao (% por hora). */
    public double variacaoUmidadePorHora(Talhao t) {
        return (t.isAspersorLigado() ? t.getGanhoIrrigacao() : 0) - evapotranspiracaoPorHora(t);
    }

    /** Horas (simuladas) ate o talhao atingir o limite critico; null se a umidade nao estiver caindo. */
    public Double horasAteCritico(Talhao t) {
        double variacao = variacaoUmidadePorHora(t);
        if (t.isCritico()) {
            return 0.0;
        }
        if (variacao >= 0) {
            return null;
        }
        return (t.getUmidade() - t.getLimiteCritico()) / -variacao;
    }

    /** Variacao do nivel do reservatorio (pontos percentuais por hora). */
    public double tendenciaReservatorioPorHora() {
        return (recargaM3h() - consumoAguaM3h()) / reservatorio.getCapacidadeM3() * 100;
    }

    /** Horas (simuladas) ate o reservatorio atingir o limite critico; null se o nivel estiver estavel/subindo. */
    public Double autonomiaHoras() {
        double saidaLiquida = consumoAguaM3h() - recargaM3h();
        if (saidaLiquida <= 0) {
            return null;
        }
        double volumeUtil = reservatorio.getVolumeM3()
                - reservatorio.getCapacidadeM3() * reservatorio.getLimiteCritico() / 100;
        return Math.max(0, volumeUtil / saidaLiquida);
    }

    // ---- Getters --------------------------------------------------------------------------------

    public List<Talhao> getTalhoes() {
        return talhoes;
    }

    public Reservatorio getReservatorio() {
        return reservatorio;
    }

    public EstadoSimulacao getSimulacao() {
        return simulacao;
    }
}
