package br.com.biosolar.citrus.service.mapa;

import static br.com.biosolar.citrus.util.Formatador.horas;
import static br.com.biosolar.citrus.util.Formatador.num;
import static br.com.biosolar.citrus.util.Formatador.pct;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.config.MapaProperties;
import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.Item;
import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.Pergunta;
import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.Resposta;
import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.Tema;
import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.TipoInformacao;
import br.com.biosolar.citrus.dto.MapaDTO;
import br.com.biosolar.citrus.dto.MapaDTO.Clima;
import br.com.biosolar.citrus.dto.MapaDTO.Municipio;
import br.com.biosolar.citrus.dto.MapaDTO.PropriedadesTalhao;
import br.com.biosolar.citrus.dto.ReservatorioDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.StatusReservatorio;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.service.EstadoFazendaService;

/**
 * Agente agricola do Mapa da Fazenda: analisa os dados do BioSolar e de fontes publicas e explica, em linguagem
 * simples, a condicao dos talhoes, a umidade, a irrigacao, o solo, o clima, os cuidados e a relacao com o cultivo
 * de citros.
 * <p>
 * E um agente <b>baseado em regras e em dados</b> (sem modelo de linguagem): cada frase e montada a partir de um
 * valor real ou de uma regra agronomica conhecida, entao ele nao inventa numeros. Toda informacao sai marcada:
 * DADO (BioSolar), PUBLICO (IBGE/Open-Meteo), ESTIMATIVA (calculo do agente, com a base informada) ou
 * ORIENTACAO (conhecimento tecnico geral). Sem internet, os itens publicos dizem que estao indisponiveis.
 */
@Service
public class AgenteAgricolaService {

    /** Coeficiente de cultura (Kc) de citros adultos com ~70% de cobertura do solo, fase intermediaria (FAO-56). */
    static final double KC_CITROS = 0.65;

    static final String FONTE_TELEMETRIA = "BioSolar · telemetria ao vivo (sensores e automação)";
    static final String FONTE_CADASTRO = "BioSolar · cadastro do talhão (PostgreSQL)";
    static final String FONTE_MAPA = "BioSolar · Mapa da Fazenda (tabela talhao_area)";
    static final String FONTE_TENDENCIA = "Cálculo do agente com a variação atual medida pelo simulador (tempo da fazenda)";
    static final String FONTE_AGUA = "Cálculo do agente: parâmetros da bomba e do aspersor cadastrados + nível do reservatório";
    static final String FONTE_FAO = "Cálculo do agente: FAO-56 (Allen et al., 1998), ETc = Kc × ET0, Kc ≈ 0,65 para citros adultos; ET0 do Open-Meteo";
    static final String FONTE_SOLOS = "Embrapa · Sistema Brasileiro de Classificação de Solos (características gerais da classe)";
    static final String FONTE_CITROS = "Orientação técnica geral de citricultura";
    static final String FONTE_OPEN_METEO = "Open-Meteo · modelos meteorológicos (coordenadas da fazenda)";
    static final String FONTE_PAM = "IBGE · Produção Agrícola Municipal (PAM), tabela 1613";
    static final String FONTE_IBGE = "IBGE · Localidades e Malhas territoriais";

    public static final String NAO_IDENTIFICADO = "Não identifiquei um tema específico na pergunta, então segue a análise "
            + "geral. Você pode perguntar sobre umidade, irrigação, solo, clima, cuidados, citros ou a região.";

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TALHAO_CITADO = Pattern.compile(
            "\\b(?:talhao|talhoes|lote|setor|quadra)\\s+(?:(?:do|da|o|numero|n)\\s+)?([a-z0-9]{1,10})\\b");

    /** Palavras-chave de um tema. As regras sao avaliadas em ordem: a primeira que casar define o tema. */
    private record Regra(Tema tema, List<String> palavras) {
    }

    private static final List<Regra> REGRAS = List.of(
            new Regra(Tema.REGIAO, List.of("capitao poco", "municipio", "regiao", "cidade", "ibge", "estado do para",
                    "produtor", "producao da", "nordeste paraense")),
            new Regra(Tema.CLIMA, List.of("clima", "chuva", "chover", "chove", "choveu", "temperatura", "calor",
                    "previsao", "evapotranspiracao", "et0", "estiagem", "periodo seco", "meteorolog", "tempo hoje",
                    "tempo agora", "como esta o tempo", "vento")),
            // Verbos de irrigacao antes de "devo"/"cuidado": "devo irrigar o talhao B?" e sobre irrigacao
            new Regra(Tema.IRRIGACAO, List.of("irrig", "aspersor", "bomba", "regar", "rega ", "molhar")),
            new Regra(Tema.CUIDADOS, List.of("cuidado", "cuidar", "recomend", "o que fazer", "o que devo", "devo",
                    "dica", "manejo", "prevenir", "doenca", "praga", "gomose", "problema", "risco", "sugest")),
            // "umidade do solo" e sobre umidade, nao sobre o tipo de solo
            new Regra(Tema.UMIDADE, List.of("umidade", "umido", "seco", "secando", "sensor", "tendencia")),
            new Regra(Tema.SOLO, List.of("solo", "terra", "latossolo", "argissolo", "neossolo", "areia", "arenoso",
                    "argila", "nutriente", "aduba", "calagem", "fertilidade")),
            new Regra(Tema.IRRIGACAO, List.of("agua", "reservatorio")),
            new Regra(Tema.CITROS, List.of("citros", "citrus", "laranja", "limao", "pomar", "fruto", "fruta", "flor",
                    "colheita", "produtividade", "variedade", "cultivo", "cultura", "planta")),
            new Regra(Tema.GERAL, List.of("resumo", "geral", "situacao", "como esta", "status", "analise", "visao",
                    "acontecendo", "fazenda")));

    private final EstadoFazendaService estado;
    private final MapaService mapa;
    private final FontesPublicasService fontes;
    private final MapaProperties props;
    private final Clock clock;

    public AgenteAgricolaService(EstadoFazendaService estado, MapaService mapa, FontesPublicasService fontes,
                                 MapaProperties props, Clock clock) {
        this.estado = estado;
        this.mapa = mapa;
        this.fontes = fontes;
        this.props = props;
        this.clock = clock;
    }

    /** Retrato consistente da automacao (memoria do servidor: responde mesmo com o banco fora do ar). */
    record Retrato(List<TalhaoDTO> talhoes, Map<String, Double> ganhoPorHora, ReservatorioDTO reservatorio,
                   LocalDateTime relogio) {

        Optional<TalhaoDTO> talhao(String id) {
            return talhoes.stream().filter(t -> t.id().equalsIgnoreCase(id)).findFirst();
        }
    }

    public Resposta responder(Pergunta pergunta) {
        String texto = pergunta == null || pergunta.pergunta() == null ? "" : pergunta.pergunta().trim();
        String n = normalizar(texto);
        Retrato r = estado.ler(f -> {
            Map<String, Double> ganho = new HashMap<>();
            for (Talhao t : f.getTalhoes()) {
                ganho.put(t.getId(), t.getGanhoIrrigacao());
            }
            return new Retrato(f.getTalhoes().stream().map(t -> TalhaoDTO.de(t, f)).toList(), ganho,
                    ReservatorioDTO.de(f), f.getSimulacao().getRelogioSimulado());
        });

        List<String> avisos = new ArrayList<>();
        // O talhao citado na pergunta ("e o talhao C?") vale mais que o selecionado na tela
        TalhaoDTO talhao = identificarTalhao(n, r.talhoes()).flatMap(r::talhao).orElse(null);
        String pedido = pergunta == null ? null : pergunta.talhaoId();
        if (talhao == null && pedido != null && !pedido.isBlank()) {
            talhao = r.talhao(pedido.trim()).orElse(null);
            if (talhao == null) {
                avisos.add("Talhão " + pedido.trim().toUpperCase(Locale.ROOT) + " não está entre os talhões ativos; "
                        + "mostrando a fazenda inteira.");
            }
        }

        Tema tema = temaExplicito(pergunta);
        boolean identificado = true;
        if (tema == null) {
            tema = classificar(n);
            if (tema == null) {
                tema = Tema.GERAL;
                identificado = texto.isEmpty() || talhao != null;
                if (!identificado) {
                    avisos.add(NAO_IDENTIFICADO);
                }
            }
        }
        if ((tema == Tema.GERAL || tema == Tema.TALHAO) && talhao != null) {
            tema = Tema.TALHAO;
        } else if (tema == Tema.TALHAO) {
            tema = Tema.GERAL;
        }

        Map<String, PropriedadesTalhao> noMapa = new HashMap<>();
        MapaDTO.Talhoes talhoesMapa = mapa.talhoes();
        talhoesMapa.features().forEach(ft -> noMapa.put(ft.id(), ft.properties()));
        if (!talhoesMapa.bancoDisponivel()) {
            avisos.add("Banco de dados indisponível: as áreas desenhadas no mapa não puderam ser lidas agora.");
        }

        Contexto c = new Contexto(r, noMapa);
        List<Item> itens = switch (tema) {
            case TALHAO -> c.talhaoCompleto(talhao);
            case UMIDADE -> c.umidade(talhao);
            case IRRIGACAO -> c.irrigacao(talhao);
            case SOLO -> c.solo(talhao);
            case CLIMA -> c.clima(talhao);
            case CUIDADOS -> c.cuidados(talhao);
            case CITROS -> c.citros(talhao);
            case REGIAO -> c.regiao();
            case GERAL -> c.fazenda();
        };
        String titulo = titulo(tema, talhao);
        return new Resposta(tema, talhao == null ? null : talhao.id(), titulo, c.resumo(tema, talhao), itens,
                texto.isEmpty() ? null : texto, identificado, avisos, clock.instant());
    }

    // =============================================================================================
    // Analises
    // =============================================================================================

    /** Estado de uma resposta: dados da automacao, do mapa e (sob demanda, em cache) das fontes publicas. */
    private final class Contexto {
        private final Retrato r;
        private final Map<String, PropriedadesTalhao> noMapa;
        private Clima clima;
        private Municipio municipio;

        Contexto(Retrato r, Map<String, PropriedadesTalhao> noMapa) {
            this.r = r;
            this.noMapa = noMapa;
        }

        Clima clima() {
            if (clima == null) {
                clima = fontes.clima();
            }
            return clima;
        }

        Municipio municipio() {
            if (municipio == null) {
                municipio = fontes.municipio();
            }
            return municipio;
        }

        // ---- Fazenda inteira ----------------------------------------------------------------------

        List<Item> fazenda() {
            List<Item> itens = new ArrayList<>();
            itens.add(situacaoFazenda());
            TalhaoDTO maisSeco = r.talhoes().stream().min(Comparator.comparingDouble(TalhaoDTO::umidade)).orElse(null);
            if (maisSeco != null) {
                itens.add(item(TipoInformacao.DADO, nivel(maisSeco.status()), "Talhão que pede mais atenção",
                        maisSeco.nome() + " (" + maisSeco.culturaRotulo() + " " + maisSeco.variedade() + "): "
                                + pct(maisSeco.umidade()) + " de umidade, " + rotulo(maisSeco.status()) + ", "
                                + variacaoTexto(maisSeco) + ".", FONTE_TELEMETRIA));
            }
            proximoAcionamento().ifPresent(itens::add);
            ReservatorioDTO res = r.reservatorio();
            if (res.autonomiaHoras() != null && !res.bloqueioEmergencia()) {
                itens.add(item(TipoInformacao.ESTIMATIVA, res.autonomiaHoras() < 6 ? "atencao" : "info",
                        "Autonomia do reservatório",
                        "Com o consumo e a recarga de agora, o reservatório chegaria ao limite de "
                                + pct(res.limiteCritico()) + " em ~" + horas(res.autonomiaHoras())
                                + " (relógio simulado da fazenda). É uma projeção: muda assim que bombas ligam ou desligam.",
                        FONTE_TENDENCIA));
            }
            itens.addAll(climaResumo(null));
            regiaoResumo().ifPresent(itens::add);
            long desenhados = noMapa.values().stream()
                    .filter(p -> MapaDTO.ORIGEM_DESENHADA.equals(p.origemGeometria())).count();
            itens.add(item(TipoInformacao.DADO, desenhados == noMapa.size() ? "ok" : "info", "Talhões no mapa",
                    desenhados + " de " + noMapa.size() + " talhão(ões) com a área real desenhada no mapa. "
                            + (desenhados < noMapa.size()
                            ? "Os demais aparecem em posição ilustrativa até você desenhar a área."
                            : "Todos têm localização registrada."), FONTE_MAPA));
            List<Item> cuidados = cuidadosFazenda();
            itens.addAll(cuidados.subList(0, Math.min(2, cuidados.size())));
            return itens;
        }

        private Item situacaoFazenda() {
            ReservatorioDTO res = r.reservatorio();
            long criticos = r.talhoes().stream().filter(t -> t.status() == StatusTalhao.CRITICO).count();
            long atencao = r.talhoes().stream().filter(t -> t.status() == StatusTalhao.ATENCAO).count();
            long ligados = r.talhoes().stream().filter(TalhaoDTO::aspersorLigado).count();
            String nivel = res.bloqueioEmergencia() || criticos > 0 ? "critico"
                    : atencao > 0 || res.status() != StatusReservatorio.NORMAL ? "atencao" : "ok";
            return item(TipoInformacao.DADO, nivel, "Situação agora",
                    r.talhoes().size() + " talhão(ões) ativo(s): " + criticos + " crítico(s), " + atencao
                            + " em atenção. Reservatório em " + pct(res.nivel()) + " (" + num(res.volumeM3()) + " de "
                            + num(res.capacidadeM3()) + " m³)" + (res.bloqueioEmergencia()
                            ? ", com o BLOQUEIO DE EMERGÊNCIA ativo: nenhuma bomba pode ligar"
                            : "") + ". " + ligados + " aspersor(es) ligado(s). Hora na fazenda (relógio simulado): "
                            + r.relogio().format(HORA) + ".", FONTE_TELEMETRIA);
        }

        private Optional<Item> proximoAcionamento() {
            return r.talhoes().stream()
                    .filter(t -> t.status() != StatusTalhao.CRITICO && !t.aspersorLigado() && t.horasAteCritico() != null)
                    .min(Comparator.comparingDouble(TalhaoDTO::horasAteCritico))
                    .map(t -> item(TipoInformacao.ESTIMATIVA, t.horasAteCritico() < 2 ? "atencao" : "info",
                            "Próxima irrigação automática",
                            t.nome() + " deve chegar a " + pct(t.limiteCritico()) + " em ~" + horas(t.horasAteCritico())
                                    + " (relógio simulado da fazenda), se o ritmo de secagem atual se mantiver. Nesse momento a "
                                    + "irrigação crítica liga sozinha" + (r.reservatorio().bloqueioEmergencia()
                                    ? ", mas hoje ela seria impedida pelo bloqueio de emergência." : "."),
                            FONTE_TENDENCIA));
        }

        // ---- Um talhao ----------------------------------------------------------------------------

        List<Item> talhaoCompleto(TalhaoDTO t) {
            List<Item> itens = new ArrayList<>();
            itens.add(umidadeAgora(t));
            itens.add(tendencia(t));
            itens.add(irrigacaoAgora(t));
            aguaAteAlvo(t).ifPresent(itens::add);
            itens.addAll(soloItens(t));
            itens.addAll(climaResumo(t));
            itens.add(culturaItem(t));
            itens.add(areaNoMapa(t));
            List<Item> cuidados = cuidadosTalhao(t);
            itens.addAll(cuidados.subList(0, Math.min(3, cuidados.size())));
            return itens;
        }

        List<Item> umidade(TalhaoDTO t) {
            if (t != null) {
                return List.of(umidadeAgora(t), tendencia(t));
            }
            List<Item> itens = new ArrayList<>();
            r.talhoes().forEach(x -> itens.add(item(TipoInformacao.DADO, nivel(x.status()), x.nome(),
                    pct(x.umidade()) + " (" + rotulo(x.status()) + "), " + variacaoTexto(x) + ".", FONTE_TELEMETRIA)));
            proximoAcionamento().ifPresent(itens::add);
            itens.add(item(TipoInformacao.ORIENTACAO, "info", "Como ler a umidade",
                    "Acima de 30% o solo está bem suprido; entre 25% e 30% é atenção; abaixo de 25% a irrigação crítica "
                            + "liga automaticamente. A irrigação automática para em 40% (alvo) para não desperdiçar água.",
                    "Regras de automação do BioSolar"));
            return itens;
        }

        List<Item> irrigacao(TalhaoDTO t) {
            if (t != null) {
                List<Item> itens = new ArrayList<>(List.of(irrigacaoAgora(t)));
                aguaAteAlvo(t).ifPresent(itens::add);
                itens.addAll(climaDemanda(t));
                return itens;
            }
            List<Item> itens = new ArrayList<>();
            ReservatorioDTO res = r.reservatorio();
            long ligados = r.talhoes().stream().filter(TalhaoDTO::aspersorLigado).count();
            itens.add(item(TipoInformacao.DADO, res.bloqueioEmergencia() ? "critico" : ligados > 0 ? "info" : "ok",
                    "Irrigação agora", ligados + " de " + r.talhoes().size() + " aspersor(es) ligado(s), consumindo "
                            + num(res.consumoM3h()) + " m³/h do reservatório (recarga do poço: " + num(res.recargaM3h())
                            + " m³/h). Reservatório em " + pct(res.nivel()) + (res.bloqueioEmergencia()
                            ? ": bloqueio de emergência ativo, nenhuma bomba pode ligar." : "."), FONTE_TELEMETRIA));
            for (TalhaoDTO x : r.talhoes()) {
                if (x.aspersorLigado() || x.status() != StatusTalhao.NORMAL) {
                    itens.add(irrigacaoAgora(x));
                }
            }
            proximoAcionamento().ifPresent(itens::add);
            itens.addAll(climaDemanda(null));
            return itens;
        }

        List<Item> solo(TalhaoDTO t) {
            if (t != null) {
                return soloItens(t);
            }
            List<Item> itens = new ArrayList<>();
            Map<String, List<String>> porSolo = new LinkedHashMap<>();
            r.talhoes().forEach(x -> porSolo.computeIfAbsent(x.solo() == null ? "Não informado" : x.solo(),
                    k -> new ArrayList<>()).add(x.nome()));
            porSolo.forEach((solo, nomes) -> {
                String[] kb = conhecimentoSolo(solo);
                itens.add(item(TipoInformacao.DADO, "info", solo, "Cadastrado em: " + String.join(", ", nomes) + ".",
                        FONTE_CADASTRO));
                itens.add(item(TipoInformacao.ORIENTACAO, "info", kb[0], kb[1], FONTE_SOLOS));
            });
            return itens;
        }

        List<Item> clima(TalhaoDTO t) {
            List<Item> itens = new ArrayList<>(climaResumo(t));
            if (clima().disponivel()) {
                itens.add(previsaoDias());
            }
            itens.addAll(climaDemanda(t));
            return itens;
        }

        List<Item> cuidados(TalhaoDTO t) {
            return t != null ? cuidadosTalhao(t) : cuidadosFazenda();
        }

        List<Item> citros(TalhaoDTO t) {
            List<Item> itens = new ArrayList<>();
            if (t != null) {
                itens.add(culturaItem(t));
                variedadeItem(t).ifPresent(itens::add);
            } else {
                Map<String, TalhaoDTO> porVariedade = new LinkedHashMap<>();
                r.talhoes().forEach(x -> porVariedade.putIfAbsent(x.cultura() + "|" + x.variedade(), x));
                Map<String, Boolean> culturas = new LinkedHashMap<>();
                porVariedade.values().forEach(x -> {
                    if (culturas.putIfAbsent(x.cultura(), true) == null) {
                        itens.add(culturaItem(x));
                    }
                });
                porVariedade.values().forEach(x -> variedadeItem(x).ifPresent(itens::add));
            }
            regiaoResumo().ifPresent(itens::add);
            return itens;
        }

        List<Item> regiao() {
            List<Item> itens = new ArrayList<>();
            Municipio m = municipio();
            if (!m.disponivel()) {
                itens.add(item(TipoInformacao.PUBLICO, "info", "Dados do IBGE indisponíveis", m.aviso(), FONTE_IBGE));
                return itens;
            }
            StringBuilder sb = new StringBuilder(m.nome() + " – " + m.uf());
            if (m.mesorregiao() != null) {
                sb.append(", mesorregião ").append(m.mesorregiao());
            }
            if (m.microrregiao() != null) {
                sb.append(", microrregião do ").append(m.microrregiao());
            }
            if (m.areaKm2() != null) {
                sb.append(". Área territorial: ").append(inteiro(m.areaKm2())).append(" km²");
            }
            sb.append(" (código IBGE ").append(m.codigoIbge()).append(").");
            itens.add(item(TipoInformacao.PUBLICO, "info", "Município", sb.toString(), FONTE_IBGE));
            regiaoResumo().ifPresent(itens::add);
            if (m.producao() != null && m.producao().limao() != null) {
                MapaDTO.Cultivo l = m.producao().limao();
                itens.add(item(TipoInformacao.PUBLICO, "info", "Limão no município (PAM " + m.producao().ano() + ")",
                        l.semProducao() ? "O IBGE não registra produção de limão em " + m.nome() + " em "
                                + m.producao().ano() + " (valor \"-\", ou seja, zero)."
                                : "Área colhida: " + inteiro(l.areaColhidaHa()) + " ha; produção: " + inteiro(l.producaoT())
                                + " t.", FONTE_PAM));
            }
            itens.add(item(TipoInformacao.DADO, "info", "Localização da fazenda no mapa",
                    "O ponto de referência da fazenda está a ~" + num(GeometriaTalhao.distanciaKm(m.sede().lat(),
                            m.sede().lng(), referencia().lat(), referencia().lng())) + " km da sede de " + m.nome()
                            + ", em uma área agrícola da região. Enquanto as áreas não forem desenhadas, "
                            + "a posição dos talhões é ilustrativa.", FONTE_MAPA));
            if (m.aviso() != null) {
                itens.add(item(TipoInformacao.PUBLICO, "info", "Observação", m.aviso(), FONTE_IBGE));
            }
            return itens;
        }

        // ---- Blocos reutilizaveis -----------------------------------------------------------------

        private Item umidadeAgora(TalhaoDTO t) {
            String faixa = switch (t.status()) {
                case CRITICO -> "abaixo do limite crítico de " + pct(t.limiteCritico());
                case ATENCAO -> "na faixa de atenção (entre " + pct(t.limiteCritico()) + " e " + pct(t.limiteAtencao()) + ")";
                default -> t.umidade() >= 75 ? "bem suprida de água (próxima do máximo seguro de 85%)"
                        : "em faixa adequada (acima de " + pct(t.limiteAtencao()) + ")";
            };
            return item(TipoInformacao.DADO, nivel(t.status()), "Umidade do solo agora",
                    t.nome() + " (" + t.culturaRotulo() + " " + t.variedade() + ", " + num(t.areaHa()) + " ha) está com "
                            + pct(t.umidade()) + " de umidade, " + faixa + ". A irrigação automática leva o solo até "
                            + pct(t.umidadeAlvo()) + ".", FONTE_TELEMETRIA);
        }

        private Item tendencia(TalhaoDTO t) {
            double v = t.variacaoPorHora();
            String texto;
            String nivel = "info";
            if (t.aspersorLigado() && v > 0.05) {
                double h = (t.umidadeAlvo() - t.umidade()) / v;
                texto = "Com o aspersor ligado a umidade sobe ~" + num(v) + " ponto(s) percentual(is) por hora; deve "
                        + "chegar ao alvo de " + pct(t.umidadeAlvo()) + " em ~" + horas(Math.max(h, 0))
                        + " (relógio simulado da fazenda), se o ritmo se mantiver.";
            } else if (v < -0.05) {
                texto = "O solo está perdendo ~" + num(-v) + " ponto(s) percentual(is) por hora (evaporação e consumo "
                        + "das plantas, que acompanham o sol).";
                if (t.horasAteCritico() != null && t.status() != StatusTalhao.CRITICO) {
                    texto += " Nesse ritmo, chega a " + pct(t.limiteCritico()) + " em ~" + horas(t.horasAteCritico())
                            + " e a irrigação crítica liga sozinha.";
                    nivel = t.horasAteCritico() < 2 ? "atencao" : "info";
                }
            } else {
                texto = "A umidade está praticamente estável agora (" + (v >= 0 ? "+" : "") + num(v) + " p.p./h): "
                        + "à noite e no início da manhã a perda de água do solo é pequena.";
            }
            return item(TipoInformacao.ESTIMATIVA, nivel, "Tendência", texto
                    + " Relógio simulado da fazenda: " + r.relogio().format(HORA) + " (1 s real = 1 min à velocidade 1×).", FONTE_TENDENCIA);
        }

        private Item irrigacaoAgora(TalhaoDTO t) {
            String texto;
            String nivel;
            if (t.irrigacaoBloqueada()) {
                texto = t.nome() + " precisa de água, mas a irrigação está IMPEDIDA pela proteção hídrica: "
                        + motivoBloqueio() + ". A bomba " + t.bombaId() + " fica desligada até lá.";
                nivel = "critico";
            } else if (t.aspersorLigado()) {
                texto = "Aspersor do " + t.nome() + " LIGADO (" + (t.modoAcionamento() == ModoAcionamento.AUTOMATICO
                        ? "automático, pela irrigação crítica ou até o alvo" : "manual, por um operador")
                        + "). Bomba " + t.bombaId() + " puxando " + num(t.vazaoBombaM3h()) + " m³/h e usando "
                        + num(t.potenciaBombaKw()) + " kW.";
                nivel = "info";
            } else {
                texto = "Aspersor do " + t.nome() + " desligado. " + (t.status() == StatusTalhao.NORMAL
                        ? "Não há necessidade de irrigar agora."
                        : "A automação liga a bomba " + t.bombaId() + " sozinha se a umidade cair abaixo de "
                                + pct(t.limiteCritico()) + ".");
                nivel = t.status() == StatusTalhao.NORMAL ? "ok" : "atencao";
            }
            return item(TipoInformacao.DADO, nivel, "Irrigação · " + t.nome(), texto, FONTE_TELEMETRIA);
        }

        private Optional<Item> aguaAteAlvo(TalhaoDTO t) {
            if (t.umidade() >= t.umidadeAlvo()) {
                return Optional.of(item(TipoInformacao.ESTIMATIVA, "ok", "Água necessária",
                        t.nome() + " já está no alvo de " + pct(t.umidadeAlvo()) + " ou acima: não precisa de irrigação "
                                + "agora.", FONTE_AGUA));
            }
            double ganho = t.aspersorLigado() ? t.variacaoPorHora()
                    : r.ganhoPorHora().getOrDefault(t.id(), 0.0) - t.evapotranspiracaoPorHora();
            if (ganho <= 0.05) {
                return Optional.empty();
            }
            double h = (t.umidadeAlvo() - t.umidade()) / ganho;
            double volume = h * t.vazaoBombaM3h();
            double energia = h * t.potenciaBombaKw();
            ReservatorioDTO res = r.reservatorio();
            double disponivel = Math.max(0, (res.nivel() - res.limiteCritico()) / 100 * res.capacidadeM3());
            String nivel = volume > disponivel ? "atencao" : "info";
            return Optional.of(item(TipoInformacao.ESTIMATIVA, nivel, "Água para chegar ao alvo",
                    "Para levar o " + t.nome() + " de " + pct(t.umidade()) + " a " + pct(t.umidadeAlvo())
                            + " seriam ~" + horas(h) + " de bomba, ~" + inteiro(volume) + " m³ de água e ~"
                            + num(energia) + " kWh. O reservatório tem ~" + inteiro(disponivel) + " m³ acima do limite de "
                            + pct(res.limiteCritico()) + (volume > disponivel
                            ? ": não seria suficiente para completar a irrigação." : ".")
                            + " Base: ganho do aspersor e evaporação atual do modelo da fazenda.", FONTE_AGUA));
        }

        private List<Item> soloItens(TalhaoDTO t) {
            String solo = t.solo() == null || t.solo().isBlank() ? "Não informado" : t.solo();
            String[] kb = conhecimentoSolo(solo);
            return List.of(
                    item(TipoInformacao.DADO, "info", "Solo do " + t.nome(), "Tipo cadastrado: " + solo + ". Perda de "
                            + "umidade agora (evaporação + consumo das plantas): "
                            + num(Math.max(t.evapotranspiracaoPorHora(), 0)) + " p.p./h.", FONTE_CADASTRO),
                    item(TipoInformacao.ORIENTACAO, "info", kb[0], kb[1], FONTE_SOLOS));
        }

        private List<Item> climaResumo(TalhaoDTO t) {
            Clima c = clima();
            if (!c.disponivel()) {
                return List.of(item(TipoInformacao.PUBLICO, "info", "Clima indisponível",
                        c.aviso() + " O agente não estima o clima sem dados reais.", FONTE_OPEN_METEO));
            }
            List<Item> itens = new ArrayList<>();
            StringBuilder sb = new StringBuilder("Agora na região");
            if (c.horarioLocal() != null && c.horarioLocal().length() >= 16) {
                sb.append(" (").append(c.horarioLocal().substring(11, 16)).append(", horário de Belém)");
            }
            sb.append(": ");
            if (c.temperatura() != null) {
                sb.append(num(c.temperatura())).append(" °C");
            }
            if (c.umidadeAr() != null) {
                sb.append(", umidade do ar ").append(inteiro(c.umidadeAr())).append("%");
            }
            if (c.descricaoTempo() != null) {
                sb.append(", ").append(c.descricaoTempo().toLowerCase(PT_BR));
            }
            sb.append(".");
            if (c.chuvaUltimos7DiasMm() != null) {
                sb.append(" Chuva nos últimos 7 dias: ").append(num(c.chuvaUltimos7DiasMm())).append(" mm.");
            }
            if (c.chuvaPrevista3DiasMm() != null) {
                sb.append(" Prevista para hoje e os próximos 2 dias: ").append(num(c.chuvaPrevista3DiasMm())).append(" mm.");
            }
            if (c.et0HojeMm() != null) {
                sb.append(" Evapotranspiração de referência (ET0) hoje: ").append(num(c.et0HojeMm())).append(" mm.");
            }
            itens.add(item(TipoInformacao.PUBLICO, "info", "Clima (Open-Meteo)", sb.toString(), FONTE_OPEN_METEO));
            if (periodoSeco(c)) {
                itens.add(item(TipoInformacao.ESTIMATIVA, "atencao", "Período seco",
                        "Nos últimos 7 dias choveu " + num(c.chuvaUltimos7DiasMm()) + " mm, contra uma demanda de "
                                + "referência de ~" + inteiro(c.et0Media7DiasMm() * 7) + " mm (7 × ET0 média de "
                                + num(c.et0Media7DiasMm()) + " mm/dia). A chuva quase não repõe a água do solo: "
                                + "a irrigação é a principal fonte de água do pomar agora.", FONTE_FAO));
            }
            if (t != null) {
                itens.addAll(climaDemanda(t));
            }
            return itens;
        }

        private Item previsaoDias() {
            StringBuilder sb = new StringBuilder();
            for (MapaDTO.DiaClima d : clima().dias()) {
                if (!d.previsao()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append(" · ");
                }
                sb.append(d.data().substring(8, 10)).append("/").append(d.data().substring(5, 7)).append(": ");
                sb.append(d.chuvaMm() == null ? "chuva s/d" : num(d.chuvaMm()) + " mm");
                if (d.probabilidadeChuva() != null) {
                    sb.append(" (").append(d.probabilidadeChuva()).append("% de chance)");
                }
                if (d.temperaturaMax() != null) {
                    sb.append(", máx. ").append(inteiro(d.temperaturaMax())).append(" °C");
                }
            }
            return item(TipoInformacao.PUBLICO, "info", "Previsão (3 dias)", sb + ".", FONTE_OPEN_METEO);
        }

        /** Demanda de agua do pomar pela FAO-56 (ETc = Kc x ET0), por talhao ou para a fazenda inteira. */
        private List<Item> climaDemanda(TalhaoDTO t) {
            Clima c = clima();
            if (!c.disponivel()) {
                return List.of();
            }
            Double et0 = c.et0HojeMm() != null ? c.et0HojeMm() : c.et0Media7DiasMm();
            if (et0 == null) {
                return List.of();
            }
            double etc = KC_CITROS * et0;
            double area = t != null ? t.areaHa() : r.talhoes().stream().mapToDouble(TalhaoDTO::areaHa).sum();
            double m3Dia = etc * area * 10;
            StringBuilder sb = new StringBuilder("Consumo estimado do pomar hoje: ~" + num(etc) + " mm/dia ("
                    + String.format(PT_BR, "%.2f", KC_CITROS) + " × ET0 de " + num(et0) + " mm), o que dá ~" + inteiro(m3Dia) + " m³/dia para "
                    + (t != null ? "os " + num(area) + " ha do " + t.nome() : "os " + num(area) + " ha da fazenda") + ".");
            String nivel = "info";
            if (c.chuvaPrevista3DiasMm() != null) {
                double demanda3 = etc * 3;
                double cobertura = demanda3 <= 0 ? 100 : Math.min(100, c.chuvaPrevista3DiasMm() / demanda3 * 100);
                sb.append(" A chuva prevista para 3 dias (").append(num(c.chuvaPrevista3DiasMm()))
                        .append(" mm) cobriria ~").append(inteiro(cobertura)).append("% dessa demanda.");
                nivel = cobertura < 50 ? "atencao" : "info";
            }
            sb.append(" Estimativa agronômica com clima real; não considera a eficiência da irrigação nem o "
                    + "estágio exato das plantas.");
            return List.of(item(TipoInformacao.ESTIMATIVA, nivel, "Demanda de água (FAO-56)", sb.toString(), FONTE_FAO));
        }

        private Item culturaItem(TalhaoDTO t) {
            boolean laranja = "LARANJA".equals(t.cultura());
            String texto = laranja
                    ? "Laranjeiras são mais sensíveis à falta de água na florada e no pegamento dos frutos: déficit "
                            + "hídrico nessas fases reduz o número de frutos. No crescimento dos frutos, a falta de água "
                            + "diminui o tamanho e o rendimento de suco. Já o excesso de água por muito tempo favorece "
                            + "doenças de raiz e colo, como a gomose (Phytophthora)."
                    : "Limoeiros e a lima ácida Tahiti respondem bem à irrigação, que ajuda a manter floradas e produção "
                            + "ao longo do ano. Também são sensíveis ao déficit hídrico na florada e ao encharcamento "
                            + "prolongado, que favorece a gomose (Phytophthora).";
            return item(TipoInformacao.ORIENTACAO, "info", "Água e " + (laranja ? "laranja" : "limão"),
                    texto + " Por isso o BioSolar mantém o solo entre " + pct(t.limiteCritico()) + " e "
                            + pct(t.umidadeAlvo()) + " e desliga os aspersores antes de 85%.", FONTE_CITROS);
        }

        private Optional<Item> variedadeItem(TalhaoDTO t) {
            String v = normalizar(t.variedade());
            String texto = null;
            if (v.contains("pera")) {
                texto = "Pera (Pera Rio): laranja de meia-estação, a variedade mais plantada no Brasil, usada para suco "
                        + "e para consumo in natura.";
            } else if (v.contains("valencia")) {
                texto = "Valência: laranja tardia (colhida mais para o fim da safra), muito usada pela indústria de suco.";
            } else if (v.contains("tahiti")) {
                texto = "Tahiti: é uma lima ácida (Citrus latifolia), conhecida no Brasil como limão Tahiti; é o \"limão\" "
                        + "mais cultivado no país.";
            } else if (v.contains("sicilian")) {
                texto = "Siciliano: limão verdadeiro (Citrus limon), mais adaptado a regiões de clima ameno do que ao "
                        + "clima quente e úmido da Amazônia.";
            }
            return Optional.ofNullable(texto).map(x -> item(TipoInformacao.ORIENTACAO, "info",
                    "Variedade · " + t.variedade(), x, FONTE_CITROS));
        }

        private Item areaNoMapa(TalhaoDTO t) {
            PropriedadesTalhao p = noMapa.get(t.id());
            if (p != null && MapaDTO.ORIGEM_DESENHADA.equals(p.origemGeometria()) && p.areaMapaHa() != null) {
                double dif = t.areaHa() > 0 ? (p.areaMapaHa() - t.areaHa()) / t.areaHa() * 100 : 0;
                return item(TipoInformacao.DADO, Math.abs(dif) > 15 ? "atencao" : "ok", "Área no mapa",
                        "Área desenhada no satélite: " + num(p.areaMapaHa()) + " ha (cadastro: " + num(t.areaHa())
                                + " ha" + (Math.abs(dif) > 15 ? "; diferença de " + inteiro(Math.abs(dif))
                                + "%, vale conferir o desenho ou o cadastro" : "") + ").", FONTE_MAPA);
            }
            return item(TipoInformacao.DADO, "info", "Área no mapa",
                    "O " + t.nome() + " aparece em posição ILUSTRATIVA (um quadrado com os " + num(t.areaHa())
                            + " ha cadastrados). Use \"Desenhar área\" para marcar a localização real.", FONTE_MAPA);
        }

        private List<Item> cuidadosTalhao(TalhaoDTO t) {
            List<Item> itens = new ArrayList<>();
            ReservatorioDTO res = r.reservatorio();
            if (t.irrigacaoBloqueada()) {
                itens.add(cuidado("critico", "Prioridade máxima", t.nome() + " está crítico e sem irrigação: "
                        + motivoBloqueio() + ". Reabastecer o reservatório é a única forma de liberar as bombas; "
                        + "quando liberar, os talhões críticos são atendidos primeiro."));
            } else if (t.status() == StatusTalhao.CRITICO && t.aspersorLigado()) {
                itens.add(cuidado("atencao", "Acompanhe a recuperação", "A irrigação crítica está em andamento. Confira "
                        + "no campo se o aspersor molha a área toda (entupimentos, vazamentos ou pressão baixa)."));
            } else if (t.status() == StatusTalhao.ATENCAO && t.variacaoPorHora() < 0) {
                itens.add(cuidado("atencao", "Antecipar ou esperar", "O solo está secando. A automação irrigará ao "
                        + "cruzar " + pct(t.limiteCritico()) + "; se preferir antecipar, irrigue no fim da tarde ou à "
                        + "noite, quando menos água se perde por evaporação e vento."));
            }
            if (t.umidade() >= 75) {
                itens.add(cuidado("atencao", "Evite irrigar", "Solo muito úmido: novas irrigações agora só aumentam o "
                        + "risco de encharcamento e de doenças de raiz."));
            }
            String solo = normalizar(t.solo());
            if (solo.contains("quartzarenico") || solo.contains("arenos")) {
                itens.add(cuidado("info", "Solo arenoso", "Prefira irrigações mais curtas e frequentes e adubação "
                        + "parcelada: o solo arenoso segura pouca água e perde nutrientes com facilidade."));
            } else if (solo.contains("argissolo")) {
                itens.add(cuidado("info", "Solo com argila no subsolo", "Evite lâminas grandes de uma vez: a água "
                        + "infiltra mais devagar em profundidade e pode escorrer em áreas inclinadas."));
            }
            if (clima().disponivel() && periodoSeco(clima())) {
                itens.add(cuidado("info", "Tempo seco", "Cobertura morta (palhada) na linha de plantio reduz a perda de "
                        + "água do solo e a temperatura na superfície durante a estiagem."));
            }
            if (res.status() != StatusReservatorio.NORMAL && !t.irrigacaoBloqueada()) {
                itens.add(cuidado("atencao", "Economize água", "Reservatório em " + pct(res.nivel()) + ": priorize os "
                        + "talhões críticos e evite irrigações manuais que não sejam necessárias."));
            }
            itens.add(cuidado("info", "Confirme no campo", "Observe sinais de estresse hídrico nas plantas (folhas "
                    + "enroladas ou murchas nas horas quentes) e compare com a leitura do sensor " + sensor(t) + "."));
            return itens;
        }

        private List<Item> cuidadosFazenda() {
            List<Item> itens = new ArrayList<>();
            ReservatorioDTO res = r.reservatorio();
            if (res.bloqueioEmergencia()) {
                itens.add(cuidado("critico", "Reabasteça o reservatório", "Bloqueio de emergência ativo: nenhuma bomba "
                        + "liga até o nível voltar a " + pct(res.limiteRearme()) + ". Os talhões críticos são atendidos "
                        + "primeiro quando liberar."));
            } else if (res.status() != StatusReservatorio.NORMAL) {
                itens.add(cuidado("atencao", "Economize água", "Reservatório em " + pct(res.nivel()) + ": evite "
                        + "irrigações manuais desnecessárias e acompanhe a autonomia."));
            }
            r.talhoes().stream().filter(t -> t.status() != StatusTalhao.NORMAL)
                    .sorted(Comparator.comparingDouble(TalhaoDTO::umidade)).limit(2)
                    .forEach(t -> itens.add(cuidado(nivel(t.status()), t.nome(), pct(t.umidade()) + " de umidade: "
                            + (t.irrigacaoBloqueada() ? "precisa de água, mas está sem irrigação até o bloqueio de "
                            + "emergência ser liberado."
                            : t.aspersorLigado() ? "irrigação em andamento, acompanhe até " + pct(t.umidadeAlvo()) + "."
                            : "a automação liga a irrigação abaixo de " + pct(t.limiteCritico()) + "."))));
            if (clima().disponivel() && periodoSeco(clima())) {
                itens.add(cuidado("info", "Tempo seco", "Irrigue preferencialmente no fim da tarde ou à noite e mantenha "
                        + "cobertura morta na linha de plantio para reduzir a evaporação."));
            }
            itens.add(cuidado("info", "Manutenção", "Verifique periodicamente filtros, aspersores e vazamentos: perdas na "
                    + "irrigação gastam água do reservatório e energia das bombas."));
            return itens;
        }

        /** Producao de laranja do municipio no IBGE (PAM) e a posicao no estado, quando disponivel. */
        private Optional<Item> regiaoResumo() {
            Municipio m = municipio();
            if (!m.disponivel() || m.producao() == null || m.producao().laranja() == null) {
                return Optional.of(item(TipoInformacao.PUBLICO, "info", "Produção de citros na região",
                        m.disponivel() ? "O IBGE não retornou a produção de laranja do município agora."
                                : "Dados do IBGE indisponíveis agora: " + m.aviso(), FONTE_PAM));
            }
            MapaDTO.Cultivo l = m.producao().laranja();
            if (l.semProducao() || l.producaoT() == null) {
                return Optional.of(item(TipoInformacao.PUBLICO, "info", "Laranja no município",
                        "O IBGE não registra produção de laranja em " + m.nome() + " em " + m.producao().ano() + ".",
                        FONTE_PAM));
            }
            StringBuilder sb = new StringBuilder();
            MapaDTO.RankingEstado rk = m.producao().rankingLaranja();
            if (rk != null) {
                sb.append(m.nome()).append(rk.posicao() == 1 ? " é o maior produtor de laranja do " + nomeEstado(m.uf())
                        : " é o " + rk.posicao() + "º produtor de laranja do " + nomeEstado(m.uf()))
                        .append(": ").append(inteiro(l.producaoT())).append(" t em ").append(m.producao().ano())
                        .append(" (").append(num(rk.participacaoPct())).append("% da produção do estado, entre ")
                        .append(rk.municipiosProdutores()).append(" municípios produtores).");
            } else {
                sb.append(m.nome()).append(" produziu ").append(inteiro(l.producaoT())).append(" t de laranja em ")
                        .append(m.producao().ano()).append(".");
            }
            if (l.areaColhidaHa() != null) {
                sb.append(" Área colhida: ").append(inteiro(l.areaColhidaHa())).append(" ha");
                if (l.rendimentoKgHa() != null) {
                    sb.append(", rendimento médio de ").append(num(l.rendimentoKgHa() / 1000)).append(" t/ha");
                }
                sb.append(".");
            }
            if (l.valorMilReais() != null) {
                sb.append(" Valor da produção: R$ ").append(num(l.valorMilReais() / 1000)).append(" milhões.");
            }
            return Optional.of(item(TipoInformacao.PUBLICO, "info", "Laranja em " + m.nome() + " (IBGE)", sb.toString(),
                    FONTE_PAM));
        }

        String resumo(Tema tema, TalhaoDTO t) {
            if (t != null) {
                return t.nome() + ": " + pct(t.umidade()) + " de umidade (" + rotulo(t.status()) + "), aspersor "
                        + (t.aspersorLigado() ? "ligado" : "desligado") + (t.irrigacaoBloqueada()
                        ? ", irrigação bloqueada pela proteção hídrica" : "") + ".";
            }
            ReservatorioDTO res = r.reservatorio();
            long criticos = r.talhoes().stream().filter(x -> x.status() == StatusTalhao.CRITICO).count();
            long atencao = r.talhoes().stream().filter(x -> x.status() == StatusTalhao.ATENCAO).count();
            return "Fazenda com " + r.talhoes().size() + " talhões (" + criticos + " crítico(s), " + atencao
                    + " em atenção) e reservatório em " + pct(res.nivel()) + (res.bloqueioEmergencia()
                    ? ", com bloqueio de emergência." : ".");
        }

        /** Abaixo de 15% ou, depois de uma emergencia, ainda sem o nivel de rearme (20%). */
        private String motivoBloqueio() {
            ReservatorioDTO res = r.reservatorio();
            return res.nivel() < res.limiteCritico()
                    ? "o reservatório está em " + pct(res.nivel()) + ", abaixo do limite de " + pct(res.limiteCritico())
                            + ", e as bombas só voltam a operar quando ele chegar a " + pct(res.limiteRearme())
                    : "o bloqueio de emergência continua ativo: o reservatório está em " + pct(res.nivel())
                            + " e só libera as bombas ao voltar a " + pct(res.limiteRearme()) + " (rearme)";
        }

        private MapaDTO.Ponto referencia() {
            return new MapaDTO.Ponto(props.fazendaLatitude(), props.fazendaLongitude());
        }
    }

    // =============================================================================================
    // Base de conhecimento e interpretacao
    // =============================================================================================

    /** [titulo, texto] com as caracteristicas gerais da classe de solo (SiBCS/Embrapa). */
    static String[] conhecimentoSolo(String solo) {
        String s = normalizar(solo);
        if (s.contains("quartzarenico") || (s.contains("neossolo") && s.contains("arenos")) || s.contains("arenoso")) {
            return new String[] {"Neossolo Quartzarênico (arenoso)", "Solo arenoso e profundo, com baixa capacidade de "
                    + "reter água e nutrientes: seca mais rápido que os demais e perde adubo por lixiviação com chuva ou "
                    + "irrigação em excesso. Costumam funcionar melhor irrigações mais frequentes com lâminas menores, "
                    + "adubação parcelada e cobertura morta na linha de plantio."};
        }
        if (s.contains("latossolo")) {
            return new String[] {"Latossolo", "Solos profundos, porosos e bem drenados, muito comuns no nordeste "
                    + "paraense. Em geral são ácidos e de baixa fertilidade natural, por isso dependem de calagem e "
                    + "adubação conforme a análise de solo. A água armazenada varia com a textura: os de textura média "
                    + "a argilosa retêm mais água que os arenosos."};
        }
        if (s.contains("argissolo")) {
            return new String[] {"Argissolo", "Têm mais argila no subsolo do que na superfície (horizonte B textural). "
                    + "A água infiltra bem no topo e mais devagar em profundidade, o que aumenta o risco de "
                    + "escorrimento e erosão em áreas inclinadas. Lâminas moderadas de irrigação e solo coberto ajudam."};
        }
        if (s.contains("gleissolo")) {
            return new String[] {"Gleissolo", "Solos mal drenados, sujeitos a encharcamento. Os citros sofrem com raízes "
                    + "sem oxigênio: exigem drenagem e cuidado redobrado para não irrigar em excesso."};
        }
        if (s.contains("plintossolo")) {
            return new String[] {"Plintossolo", "Solos com drenagem limitada em profundidade, que podem encharcar na "
                    + "época chuvosa e ressecar na seca. Pedem atenção à drenagem e ao volume de cada irrigação."};
        }
        if (s.contains("nitossolo")) {
            return new String[] {"Nitossolo", "Solos argilosos e bem estruturados, com boa retenção de água e drenagem "
                    + "adequada."};
        }
        if (s.contains("cambissolo")) {
            return new String[] {"Cambissolo", "Solos pouco desenvolvidos, de profundidade e fertilidade variáveis. A "
                    + "análise de solo e a profundidade efetiva das raízes orientam o manejo."};
        }
        return new String[] {"Sobre este solo", "Tipo de solo sem orientação específica na base do agente. A análise de "
                + "solo da área é a melhor referência para calagem, adubação e irrigação."};
    }

    /** Tema de uma pergunta ja normalizada (minusculas, sem acentos) ou null. Usado tambem pelo assistente Citrus. */
    public static Tema classificar(String n) {
        if (n.isEmpty()) {
            return null;
        }
        String t = " " + n + " ";
        for (Regra regra : REGRAS) {
            for (String palavra : regra.palavras()) {
                if (t.contains(palavra)) {
                    return regra.tema();
                }
            }
        }
        return null;
    }

    static Optional<String> identificarTalhao(String n, List<TalhaoDTO> talhoes) {
        Matcher m = TALHAO_CITADO.matcher(n);
        while (m.find()) {
            String codigo = m.group(1);
            Optional<String> id = talhoes.stream().map(TalhaoDTO::id).filter(x -> x.equalsIgnoreCase(codigo)).findFirst();
            if (id.isPresent()) {
                return id;
            }
        }
        return talhoes.stream()
                .filter(t -> {
                    String nome = normalizar(t.nome());
                    return nome.length() >= 3 && (" " + n + " ").contains(" " + nome + " ");
                })
                .map(TalhaoDTO::id).findFirst();
    }

    static String normalizar(String texto) {
        String n = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Tema temaExplicito(Pergunta p) {
        if (p == null || p.tema() == null || p.tema().isBlank()) {
            return null;
        }
        try {
            return Tema.valueOf(p.tema().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String titulo(Tema tema, TalhaoDTO t) {
        String alvo = t != null ? t.nome() : "Fazenda";
        return switch (tema) {
            case TALHAO -> "Análise do " + alvo;
            case GERAL -> "Análise geral da fazenda";
            case UMIDADE -> "Umidade · " + alvo;
            case IRRIGACAO -> "Irrigação · " + alvo;
            case SOLO -> "Solo · " + alvo;
            case CLIMA -> "Clima e demanda de água · " + alvo;
            case CUIDADOS -> "Cuidados recomendados · " + alvo;
            case CITROS -> "Citros e cultivo · " + alvo;
            case REGIAO -> "Região de Capitão Poço";
        };
    }

    private static boolean periodoSeco(Clima c) {
        return c.chuvaUltimos7DiasMm() != null && c.et0Media7DiasMm() != null
                && c.chuvaUltimos7DiasMm() < 0.3 * c.et0Media7DiasMm() * 7;
    }

    private static Item item(TipoInformacao tipo, String nivel, String titulo, String texto, String fonte) {
        return new Item(tipo, nivel, titulo, texto, fonte);
    }

    private static Item cuidado(String nivel, String titulo, String texto) {
        return item(TipoInformacao.ORIENTACAO, nivel, titulo, texto, FONTE_CITROS);
    }

    private static String nivel(StatusTalhao s) {
        return switch (s) {
            case CRITICO -> "critico";
            case ATENCAO -> "atencao";
            default -> "ok";
        };
    }

    private static String rotulo(StatusTalhao s) {
        return switch (s) {
            case CRITICO -> "crítico";
            case ATENCAO -> "em atenção";
            default -> "normal";
        };
    }

    private static String variacaoTexto(TalhaoDTO t) {
        double v = t.variacaoPorHora();
        if (Math.abs(v) < 0.05) {
            return "umidade estável agora";
        }
        return (v > 0 ? "subindo " : "caindo ") + num(Math.abs(v)) + " p.p./h";
    }

    private static String sensor(TalhaoDTO t) {
        return "SU-" + t.id();
    }

    private static String inteiro(double v) {
        return String.format(PT_BR, "%,.0f", v);
    }

    private static String nomeEstado(String uf) {
        return "PA".equalsIgnoreCase(uf) ? "Pará" : uf;
    }
}
