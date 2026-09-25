package br.com.biosolar.citrus.service.mapa;

import static br.com.biosolar.citrus.util.Formatador.num;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.biosolar.citrus.config.MapaProperties;
import br.com.biosolar.citrus.dto.MapaDTO;
import br.com.biosolar.citrus.dto.MapaDTO.AreaRequest;
import br.com.biosolar.citrus.dto.MapaDTO.Geometria;
import br.com.biosolar.citrus.dto.MapaDTO.Ponto;
import br.com.biosolar.citrus.dto.MapaDTO.PropriedadesTalhao;
import br.com.biosolar.citrus.dto.MapaDTO.TalhaoMapa;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.exception.ConflitoException;
import br.com.biosolar.citrus.exception.DadosInvalidosException;
import br.com.biosolar.citrus.exception.RecursoNaoEncontradoException;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.Talhao;
import br.com.biosolar.citrus.model.TalhaoArea;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.TalhaoAreaRepository;
import br.com.biosolar.citrus.repository.TalhaoRepository;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.EstadoFazendaService.AlteracaoCadastro;

/**
 * Talhoes no Mapa da Fazenda. Os dados de cada talhao (cadastro, umidade, aspersor, status) vem do mesmo estado
 * da automacao que alimenta o painel; a area desenhada vem da tabela {@code talhao_area} do PostgreSQL.
 * Talhao sem area desenhada aparece em posicao ILUSTRATIVA, calculada aqui e nunca gravada.
 * <p>
 * Com o banco fora do ar o mapa continua respondendo (estado ao vivo + posicoes ilustrativas, com aviso).
 */
@Service
public class MapaService {

    private static final Logger log = LoggerFactory.getLogger(MapaService.class);
    private static final TypeReference<List<double[]>> ANEL = new TypeReference<>() {
    };

    private final EstadoFazendaService estado;
    private final TalhaoRepository talhaoRepository;
    private final TalhaoAreaRepository areaRepository;
    private final MapaProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    public MapaService(EstadoFazendaService estado, TalhaoRepository talhaoRepository,
                       TalhaoAreaRepository areaRepository, MapaProperties props, ObjectMapper mapper, Clock clock) {
        this.estado = estado;
        this.talhaoRepository = talhaoRepository;
        this.areaRepository = areaRepository;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** FeatureCollection GeoJSON com os talhoes ativos, na ordem do cadastro. */
    public MapaDTO.Talhoes talhoes() {
        List<TalhaoDTO> talhoes = estado.ler(f -> f.getTalhoes().stream().map(t -> TalhaoDTO.de(t, f)).toList());
        List<String> avisos = new ArrayList<>();
        Map<String, TalhaoArea> areas = new HashMap<>();
        boolean banco = !estado.isBancoEmEspera();
        if (banco) {
            try {
                areaRepository.findAllById(talhoes.stream().map(TalhaoDTO::id).toList())
                        .forEach(a -> areas.put(a.getTalhaoId(), a));
            } catch (DataAccessException e) {
                log.warn("Áreas dos talhões indisponíveis: {}", e.getMessage());
                banco = false;
            }
        }
        if (!banco) {
            avisos.add("Banco de dados indisponível no momento: as áreas desenhadas não puderam ser lidas e todos os "
                    + "talhões aparecem em posição ilustrativa. Os dados de umidade e irrigação continuam ao vivo.");
        }

        // Layout calculado sobre TODOS os talhoes: a posicao ilustrativa de cada um nao muda quando outro e desenhado
        Map<String, List<double[]>> ilustrativo = GeometriaTalhao.layoutIlustrativo(
                talhoes.stream().map(t -> new GeometriaTalhao.Lote(t.id(), t.areaHa())).toList(),
                props.fazendaLatitude(), props.fazendaLongitude());

        List<TalhaoMapa> features = new ArrayList<>();
        int ilustrativos = 0;
        for (TalhaoDTO t : talhoes) {
            TalhaoArea area = areas.get(t.id());
            List<double[]> anel = area != null ? ler(area) : null;
            if (anel == null) {
                anel = ilustrativo.get(t.id());
                area = null;
                ilustrativos++;
            }
            features.add(feature(t, anel, area));
        }
        if (ilustrativos > 0 && banco) {
            avisos.add(ilustrativos == talhoes.size()
                    ? "Os talhões ainda não têm a área real desenhada: aparecem em posição ilustrativa (quadrados com a "
                            + "área cadastrada). Selecione um talhão e use \"Desenhar área\" para marcar o local real."
                    : ilustrativos + " talhão(ões) ainda em posição ilustrativa: desenhe a área real no mapa.");
        }
        return new MapaDTO.Talhoes("FeatureCollection", features,
                new Ponto(props.fazendaLatitude(), props.fazendaLongitude()), props.municipioNome(), props.uf(),
                new Ponto(props.sedeLatitude(), props.sedeLongitude()), banco, avisos, clock.instant());
    }

    /** Grava (ou substitui) a area desenhada de um talhao ativo. */
    public TalhaoMapa salvarArea(String id, AreaRequest req) {
        String codigo = codigo(id);
        List<double[]> anel = GeometriaTalhao.normalizar(req.coordenadas());
        List<String> erros = GeometriaTalhao.validar(anel);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException("Área do talhão inválida.", erros);
        }
        double areaHa = GeometriaTalhao.arredondar(GeometriaTalhao.areaHectares(anel), 2);
        double[] centro = GeometriaTalhao.centro(anel);
        String json = escrever(anel);
        Instant agora = clock.instant();

        estado.alterarCadastro(fazenda -> {
            Talhao talhao = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            if (!talhao.isAtivo()) {
                throw new ConflitoException("O " + talhao.getNome() + " está arquivado: reative-o na aba Gestão para "
                        + "marcar a área no mapa.");
            }
            TalhaoArea area = areaRepository.findById(codigo).orElseGet(() -> new TalhaoArea(codigo));
            boolean redesenho = area.getCoordenadas() != null;
            area.definir(json, anel.size(), areaHa, centro[1], centro[0], agora);
            areaRepository.saveAndFlush(area);
            String diferenca = comparar(areaHa, talhao.getAreaHa());
            return new AlteracaoCadastro<Void>(() -> fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO,
                    OrigemEvento.OPERADOR, null, codigo,
                    (redesenho ? "Área redesenhada no mapa: " : "Área marcada no mapa: ") + talhao.getNome(),
                    "Polígono com " + anel.size() + " pontos e " + num(areaHa) + " ha desenhado sobre a imagem de "
                            + "satélite (cadastro: " + num(talhao.getAreaHa()) + " ha" + diferenca + ").",
                    agora), () -> null);
        });
        return buscar(codigo);
    }

    /** Apaga a area desenhada: o talhao volta a aparecer na posicao ilustrativa. */
    public TalhaoMapa removerArea(String id) {
        String codigo = codigo(id);
        Instant agora = clock.instant();
        estado.alterarCadastro(fazenda -> {
            Talhao talhao = talhaoRepository.findById(codigo).orElseThrow(() -> naoEncontrado(codigo));
            TalhaoArea area = areaRepository.findById(codigo).orElseThrow(() -> new RecursoNaoEncontradoException(
                    "O " + talhao.getNome() + " não tem área desenhada no mapa."));
            areaRepository.delete(area);
            areaRepository.flush();
            return new AlteracaoCadastro<Void>(() -> fazenda.registrarEvento(TipoEvento.CADASTRO, Severidade.INFO,
                    OrigemEvento.OPERADOR, null, codigo, "Área removida do mapa: " + talhao.getNome(),
                    "O desenho da área foi apagado; o talhão voltou à posição ilustrativa. Cadastro e histórico "
                            + "não foram alterados.", agora), () -> null);
        });
        return buscar(codigo);
    }

    public TalhaoMapa buscar(String id) {
        String codigo = codigo(id);
        return talhoes().features().stream().filter(f -> f.id().equals(codigo)).findFirst()
                .orElseThrow(() -> naoEncontrado(codigo));
    }

    // ---------------------------------------------------------------------------------------------

    private TalhaoMapa feature(TalhaoDTO t, List<double[]> anel, TalhaoArea area) {
        double[] c = area != null ? new double[] {area.getCentroLng(), area.getCentroLat()} : GeometriaTalhao.centro(anel);
        PropriedadesTalhao p = new PropriedadesTalhao(t.id(), t.nome(), t.cultura(), t.culturaRotulo(), t.variedade(),
                t.solo(), t.areaHa(), area != null ? area.getAreaHa() : null, t.plantas(), t.umidade(), t.status(),
                t.limiteCritico(), t.limiteAtencao(), t.umidadeAlvo(), t.variacaoPorHora(), t.horasAteCritico(),
                t.aspersorLigado(), t.modoAcionamento(), t.irrigacaoBloqueada(), t.bombaId(),
                area != null ? MapaDTO.ORIGEM_DESENHADA : MapaDTO.ORIGEM_ILUSTRATIVA,
                area != null ? area.getAtualizadoEm() : null, new Ponto(c[1], c[0]));
        return new TalhaoMapa("Feature", t.id(), Geometria.poligono(GeometriaTalhao.fechado(anel)), p);
    }

    private List<double[]> ler(TalhaoArea area) {
        try {
            List<double[]> anel = mapper.readValue(area.getCoordenadas(), ANEL);
            return anel.size() >= 3 ? anel : null;
        } catch (JsonProcessingException e) {
            log.warn("Área do talhão {} ilegível no banco: {}", area.getTalhaoId(), e.getMessage());
            return null;
        }
    }

    private String escrever(List<double[]> anel) {
        try {
            return mapper.writeValueAsString(anel);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao converter o polígono", e);
        }
    }

    private static String comparar(double desenhada, double cadastro) {
        if (cadastro <= 0) {
            return "";
        }
        double dif = (desenhada - cadastro) / cadastro * 100;
        if (Math.abs(dif) < 5) {
            return "; compatível";
        }
        return "; " + (dif > 0 ? "+" : "") + Math.round(dif) + "% em relação ao cadastro";
    }

    private static String codigo(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    private static RecursoNaoEncontradoException naoEncontrado(String codigo) {
        return new RecursoNaoEncontradoException("Talhão " + codigo + " não encontrado entre os talhões ativos.");
    }
}
