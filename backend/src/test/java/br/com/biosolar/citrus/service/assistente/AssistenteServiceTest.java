package br.com.biosolar.citrus.service.assistente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.StatusTalhao;

/** Interpretacao do texto falado: palavra de ativacao, letras soletradas e versao para voz. */
class AssistenteServiceTest {

    private static TalhaoDTO talhao(String id, String nome) {
        return new TalhaoDTO(id, nome, "LARANJA", "Laranja", "Pera", "Latossolo", 10, 1000, 2, 50, 25, 35, 40,
                StatusTalhao.NORMAL, false, ModoAcionamento.DESLIGADO, false, "MB-" + id, 18, 5.5, 1, -1, null, null, null);
    }

    private static final List<TalhaoDTO> TALHOES = List.of(talhao("A", "Talhão A"), talhao("B", "Talhão B"),
            talhao("E", "Talhão E"), talhao("N1", "Talhão Norte"));

    @Test
    void removeAPalavraDeAtivacaoEAcentos() {
        assertThat(AssistenteService.normalizar("Citrus, quero o STATUS!")).isEqualTo("quero o status");
        assertThat(AssistenteService.normalizar("Ok Citrus: como está o reservatório?")).isEqualTo("como esta o reservatorio");
        assertThat(AssistenteService.normalizar("sitrus quais talhões estão críticos")).isEqualTo("quais talhoes estao criticos");
    }

    @Test
    void identificaOTalhaoPorCodigoLetraFaladaOuNome() {
        assertThat(id("mostre o talhao b")).contains("B");
        assertThat(id("mostre o talhao be")).contains("B");
        assertThat(id("como esta o talhao a agora")).contains("A");
        assertThat(id("ligar o aspersor do talhao do e")).contains("E");
        assertThat(id("abrir talhao n1")).contains("N1");
        assertThat(id("como esta o talhao norte")).contains("N1");
        assertThat(id("quais talhoes estao criticos")).isEmpty();
        assertThat(id("mostre o talhao z")).isEmpty();
    }

    @Test
    void versaoFaladaSemSimbolos() {
        assertThat(AssistenteService.paraFala("Reservatório em 67%, com 536 de 800 m³; autonomia de 5,2 h."))
                .isEqualTo("Reservatório em 67 por cento, com 536 de 800 metros cúbicos; autonomia de 5,2 horas.");
        assertThat(AssistenteService.paraFala("Consumo de 36 m³/h e 11 kW.")).isEqualTo(
                "Consumo de 36 metros cúbicos por hora e 11 quilowatts.");
        // Unidades das respostas do agente agricola
        assertThat(AssistenteService.paraFala("34,6 °C · 6,8 mm em 7 dias; ~3,0 mm/dia (0,65 × ET0) para 8,2 ha."))
                .isEqualTo("34,6 graus, 6,8 milímetros em 7 dias; cerca de 3,0 milímetros por dia (0,65 vezes "
                        + "evapotranspiração de referência) para 8,2 hectares.");
        assertThat(AssistenteService.paraFala("4 talhão(ões) ativo(s), perdendo 1,2 ponto(s) percentual(is) por hora; 212.400 t."))
                .isEqualTo("4 talhões ativos, perdendo 1,2 pontos percentuais por hora; 212.400 toneladas.");
        assertThat(AssistenteService.paraFala("Atenção: 1 talhão(ões) próximo(s) do limite. 1 de 4 aspersor(es) ligado(s)."))
                .isEqualTo("Atenção: 1 talhão próximo do limite. 1 de 4 aspersores ligados.");
        assertThat(AssistenteService.paraFala("Área de 2.901 km², 17,7 t/ha e R$ 424,8 milhões. Evapotranspiração de referência (ET0) hoje."))
                .isEqualTo("Área de 2.901 quilômetros quadrados, 17,7 toneladas por hectare e 424,8 milhões de reais. "
                        + "Evapotranspiração de referência hoje.");
    }

    private static Optional<String> id(String comando) {
        return AssistenteService.identificarTalhao(comando, TALHOES);
    }
}
