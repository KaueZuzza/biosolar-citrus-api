package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Size;

/** Agente agricola do Mapa da Fazenda: pergunta e resposta com a origem de cada informacao. */
public final class AgenteAgricolaDTO {

    private AgenteAgricolaDTO() {
    }

    public record Pergunta(
            @Size(max = 300, message = "a pergunta pode ter no máximo 300 caracteres") String pergunta,
            @Size(max = 10) String talhaoId,
            @Size(max = 20) String tema) {
    }

    public enum Tema {
        GERAL, TALHAO, UMIDADE, IRRIGACAO, SOLO, CLIMA, CUIDADOS, CITROS, REGIAO
    }

    /**
     * De onde vem cada informacao, para o usuario nunca confundir medicao com estimativa:
     * DADO = registrado/medido pelo BioSolar; PUBLICO = fonte publica oficial (IBGE, Open-Meteo);
     * ESTIMATIVA = calculo/projecao feito pelo agente (a base do calculo e sempre informada);
     * ORIENTACAO = conhecimento agronomico geral, nao especifico desta fazenda.
     */
    public enum TipoInformacao {
        DADO, PUBLICO, ESTIMATIVA, ORIENTACAO
    }

    /** ok, atencao, critico ou info: cor do item na tela. */
    public record Item(TipoInformacao tipo, String nivel, String titulo, String texto, String fonte) {
    }

    public record Resposta(
            Tema tema,
            String talhaoId,
            String titulo,
            String resumo,
            List<Item> itens,
            String perguntaRecebida,
            boolean temaIdentificado,
            List<String> avisos,
            Instant geradoEm) {
    }
}
