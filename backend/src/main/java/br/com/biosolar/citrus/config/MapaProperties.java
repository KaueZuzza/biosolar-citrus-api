package br.com.biosolar.citrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Mapa da Fazenda (prefixo {@code biosolar.mapa}).
 *
 * @param municipioIbge        codigo IBGE do municipio (Capitao Poco - PA = 1502301)
 * @param municipioNome        nome exibido no mapa
 * @param uf                   sigla do estado
 * @param estadoIbge           codigo IBGE do estado (Para = 15), usado no ranking da producao de laranja
 * @param sedeLatitude         sede urbana do municipio (OpenStreetMap/Nominatim)
 * @param sedeLongitude        sede urbana do municipio (OpenStreetMap/Nominatim)
 * @param fazendaLatitude      ponto de referencia da fazenda: onde os talhoes aparecem enquanto a area real nao
 *                             for desenhada no mapa (posicao ILUSTRATIVA, area de pomares a oeste da sede)
 * @param fazendaLongitude     idem
 * @param fontesPublicas       consulta IBGE e Open-Meteo (false nos testes e em maquinas sem internet)
 * @param timeoutSegundos      tempo maximo de cada consulta externa
 * @param cacheClimaMinutos    validade do clima em cache (Open-Meteo)
 * @param cacheIbgeHoras       validade dos dados do IBGE em cache (mudam no maximo uma vez por ano)
 */
@ConfigurationProperties(prefix = "biosolar.mapa")
public record MapaProperties(
        @DefaultValue("1502301") String municipioIbge,
        @DefaultValue("Capitão Poço") String municipioNome,
        @DefaultValue("PA") String uf,
        @DefaultValue("15") String estadoIbge,
        @DefaultValue("-1.7447162") double sedeLatitude,
        @DefaultValue("-47.0638495") double sedeLongitude,
        @DefaultValue("-1.75685") double fazendaLatitude,
        @DefaultValue("-47.12646") double fazendaLongitude,
        @DefaultValue("true") boolean fontesPublicas,
        @DefaultValue("8") int timeoutSegundos,
        @DefaultValue("30") int cacheClimaMinutos,
        @DefaultValue("24") int cacheIbgeHoras) {
}
