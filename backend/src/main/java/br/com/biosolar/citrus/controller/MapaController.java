package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.AgenteAgricolaDTO;
import br.com.biosolar.citrus.dto.MapaDTO;
import br.com.biosolar.citrus.service.mapa.AgenteAgricolaService;
import br.com.biosolar.citrus.service.mapa.FontesPublicasService;
import br.com.biosolar.citrus.service.mapa.MapaService;
import jakarta.validation.Valid;

/**
 * Mapa da Fazenda: talhoes sobre a imagem de satelite (GeoJSON), area desenhada de cada talhao, dados publicos
 * de Capitao Poco - PA (IBGE, Open-Meteo) e o agente agricola. Independente das demais rotas da API.
 */
@RestController
@RequestMapping("/mapa")
public class MapaController {

    private final MapaService mapa;
    private final FontesPublicasService fontes;
    private final AgenteAgricolaService agente;

    public MapaController(MapaService mapa, FontesPublicasService fontes, AgenteAgricolaService agente) {
        this.mapa = mapa;
        this.fontes = fontes;
        this.agente = agente;
    }

    /** Talhoes ativos em GeoJSON (FeatureCollection), com umidade, status e a origem da geometria. */
    @GetMapping("/talhoes")
    public MapaDTO.Talhoes talhoes() {
        return mapa.talhoes();
    }

    /** Grava a area desenhada: {"coordenadas": [[longitude, latitude], ...]} (cantos em sequencia). */
    @PutMapping("/talhoes/{id}/area")
    public MapaDTO.TalhaoMapa salvarArea(@PathVariable String id, @Valid @RequestBody MapaDTO.AreaRequest req) {
        return mapa.salvarArea(id, req);
    }

    /** Apaga a area desenhada: o talhao volta a posicao ilustrativa. */
    @DeleteMapping("/talhoes/{id}/area")
    public MapaDTO.TalhaoMapa removerArea(@PathVariable String id) {
        return mapa.removerArea(id);
    }

    /** Capitao Poco - PA: divisao territorial, contorno oficial, area e producao de citros (IBGE). */
    @GetMapping("/municipio")
    public MapaDTO.Municipio municipio() {
        return fontes.municipio();
    }

    /** Tempo atual, chuva e evapotranspiracao (Open-Meteo) nas coordenadas da fazenda. */
    @GetMapping("/clima")
    public MapaDTO.Clima clima() {
        return fontes.clima();
    }

    /** Ex.: {"pergunta": "Preciso irrigar o talhão C?"} ou {"talhaoId": "C", "tema": "SOLO"}. */
    @PostMapping("/agente")
    public AgenteAgricolaDTO.Resposta agente(@Valid @RequestBody AgenteAgricolaDTO.Pergunta pergunta) {
        return agente.responder(pergunta);
    }
}
