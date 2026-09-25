package br.com.biosolar.citrus.service.mapa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.biosolar.citrus.dto.MapaDTO;

/**
 * Leitura das respostas do IBGE (SIDRA/PAM) e do Open-Meteo. Os JSON abaixo reproduzem o formato real das APIs
 * (valores reduzidos), sem acessar a internet durante os testes.
 */
class FontesPublicasLeituraTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto.replace('\'', '"'));
    }

    private static String variavel(String id, String laranja, String limao) {
        return "{'id':'" + id + "','variavel':'v" + id + "','unidade':'u','resultados':["
                + "{'classificacoes':[{'id':'82','nome':'Produto','categoria':{'2733':'Laranja'}}],"
                + "'series':[{'localidade':{'id':'1502301','nome':'Capitão Poço - PA'},'serie':{'2025':'" + laranja + "'}}]},"
                + "{'classificacoes':[{'id':'82','nome':'Produto','categoria':{'2734':'Limão'}}],"
                + "'series':[{'localidade':{'id':'1502301','nome':'Capitão Poço - PA'},'serie':{'2025':'" + limao + "'}}]}]}";
    }

    @Test
    void producaoDeCitrosEPosicaoNoEstado() throws Exception {
        JsonNode pam = json("[" + variavel("216", "12000", "-") + "," + variavel("214", "212400", "-") + ","
                + variavel("112", "17700", "-") + "," + variavel("215", "424800", "-") + "]");
        JsonNode ranking = json("[{'id':'214','resultados':[{'classificacoes':[],'series':["
                + "{'localidade':{'id':'1502301'},'serie':{'2025':'212400'}},"
                + "{'localidade':{'id':'1503002'},'serie':{'2025':'29400'}},"
                + "{'localidade':{'id':'1505007'},'serie':{'2025':'68569'}},"
                + "{'localidade':{'id':'1500107'},'serie':{'2025':'-'}},"
                + "{'localidade':{'id':'1500206'},'serie':{'2025':'...'}}]}]}]");

        MapaDTO.ProducaoCitros p = FontesPublicasService.lerProducao(pam, ranking, "1502301");

        assertThat(p.ano()).isEqualTo("2025");
        assertThat(p.laranja().areaColhidaHa()).isEqualTo(12000.0);
        assertThat(p.laranja().producaoT()).isEqualTo(212400.0);
        assertThat(p.laranja().rendimentoKgHa()).isEqualTo(17700.0);
        assertThat(p.laranja().valorMilReais()).isEqualTo(424800.0);
        assertThat(p.laranja().semProducao()).isFalse();
        // "-" no SIDRA e zero absoluto: sem producao de limao registrada
        assertThat(p.limao().semProducao()).isTrue();
        assertThat(p.limao().producaoT()).isZero();
        // Ranking: so municipios com producao > 0 contam; "..." (nao disponivel) fica de fora
        assertThat(p.rankingLaranja().posicao()).isEqualTo(1);
        assertThat(p.rankingLaranja().municipiosProdutores()).isEqualTo(3);
        assertThat(p.rankingLaranja().participacaoPct()).isCloseTo(68.4, within(0.05));
    }

    @Test
    void valoresEspeciaisDoSidra() {
        assertThat(FontesPublicasService.valorSidra("-")).isZero();
        assertThat(FontesPublicasService.valorSidra("...")).isNull();
        assertThat(FontesPublicasService.valorSidra("X")).isNull();
        assertThat(FontesPublicasService.valorSidra(" 17700 ")).isEqualTo(17700.0);
    }

    @Test
    void climaComChuvaPassadaEPrevista() throws Exception {
        JsonNode r = json("{'current':{'time':'2026-09-25T12:15','temperature_2m':33.2,'relative_humidity_2m':50,"
                + "'precipitation':0.0,'wind_speed_10m':11.0,'weather_code':2},"
                + "'daily':{'time':['2026-09-23','2026-09-24','2026-09-25','2026-09-26'],"
                + "'precipitation_sum':[0.9,0.0,2.2,0.2],'precipitation_probability_max':[76,8,86,75],"
                + "'et0_fao_evapotranspiration':[5.03,6.32,4.70,5.89],'temperature_2m_max':[34.3,35.6,34.3,35.5],"
                + "'temperature_2m_min':[23.1,22.8,23.4,23.0]}}");

        MapaDTO.Clima c = FontesPublicasService.lerClima(r, Instant.parse("2026-09-25T15:20:00Z"));

        assertThat(c.disponivel()).isTrue();
        assertThat(c.temperatura()).isEqualTo(33.2);
        assertThat(c.descricaoTempo()).isEqualTo("Parcialmente nublado");
        assertThat(c.chuvaUltimos7DiasMm()).isEqualTo(0.9);       // 23 e 24/09 (antes de hoje)
        assertThat(c.et0Media7DiasMm()).isCloseTo(5.7, within(0.05));
        assertThat(c.chuvaPrevista3DiasMm()).isEqualTo(2.4);     // hoje + amanha
        assertThat(c.et0HojeMm()).isEqualTo(4.70);
        assertThat(c.dias()).hasSize(4);
        assertThat(c.dias().get(1).previsao()).isFalse();
        assertThat(c.dias().get(2).previsao()).isTrue();
    }
}
