package br.com.biosolar.citrus.service.mapa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.biosolar.citrus.dto.AgenteAgricolaDTO.Tema;
import br.com.biosolar.citrus.dto.TalhaoDTO;

/** Interpretacao das perguntas do agente agricola (sem Spring). */
class AgenteAgricolaInterpretacaoTest {

    private static Tema tema(String pergunta) {
        return AgenteAgricolaService.classificar(AgenteAgricolaService.normalizar(pergunta));
    }

    @Test
    void identificaOTemaDaPergunta() {
        assertThat(tema("Vai chover nos próximos dias?")).isEqualTo(Tema.CLIMA);
        assertThat(tema("Qual a umidade do solo do talhão C?")).isEqualTo(Tema.UMIDADE);
        assertThat(tema("Qual o tipo de solo do talhão C?")).isEqualTo(Tema.SOLO);
        assertThat(tema("Devo irrigar o talhão B agora?")).isEqualTo(Tema.IRRIGACAO);
        assertThat(tema("Que cuidados devo ter com o pomar?")).isEqualTo(Tema.CUIDADOS);
        assertThat(tema("Quanta água tem no reservatório?")).isEqualTo(Tema.IRRIGACAO);
        assertThat(tema("Como a laranja reage à seca?")).isEqualTo(Tema.CITROS);
        assertThat(tema("O solo do talhão A está seco?")).isEqualTo(Tema.UMIDADE);
        assertThat(tema("Qual a melhor variedade de laranja?")).isEqualTo(Tema.CITROS);
        assertThat(tema("Quanto Capitão Poço produz de laranja?")).isEqualTo(Tema.REGIAO);
        assertThat(tema("Como está a fazenda?")).isEqualTo(Tema.GERAL);
        assertThat(tema("xyz")).isNull();
        assertThat(tema("")).isNull();
    }

    @Test
    void identificaOTalhaoCitado() {
        List<TalhaoDTO> talhoes = List.of(talhao("A", "Talhão A"), talhao("B", "Talhão B"), talhao("NORTE1", "Lote Norte"));
        assertThat(AgenteAgricolaService.identificarTalhao(AgenteAgricolaService.normalizar("Como está o talhão B?"), talhoes))
                .contains("B");
        assertThat(AgenteAgricolaService.identificarTalhao(AgenteAgricolaService.normalizar("solo do talhao norte1"), talhoes))
                .contains("NORTE1");
        assertThat(AgenteAgricolaService.identificarTalhao(AgenteAgricolaService.normalizar("E o lote norte?"), talhoes))
                .contains("NORTE1");
        assertThat(AgenteAgricolaService.identificarTalhao(AgenteAgricolaService.normalizar("talhão Z"), talhoes)).isEmpty();
    }

    @Test
    void conhecimentoDeSolosPorClasse() {
        assertThat(AgenteAgricolaService.conhecimentoSolo("Neossolo Quartzarênico (arenoso)")[1]).contains("baixa capacidade");
        assertThat(AgenteAgricolaService.conhecimentoSolo("Latossolo Amarelo")[0]).isEqualTo("Latossolo");
        assertThat(AgenteAgricolaService.conhecimentoSolo("Argissolo Vermelho-Amarelo")[0]).isEqualTo("Argissolo");
        assertThat(AgenteAgricolaService.conhecimentoSolo("Terra preta")[0]).isEqualTo("Sobre este solo");
    }

    private static TalhaoDTO talhao(String id, String nome) {
        return new TalhaoDTO(id, nome, "LARANJA", "Laranja", "Pera Rio", "Latossolo Amarelo", 10, 1000, 1, 40, 25, 30,
                40, null, false, null, false, "MB-" + id, 18, 5.5, 1, -1, null, null, null);
    }
}
