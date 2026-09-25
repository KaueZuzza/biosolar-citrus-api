package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contrato do assistente operacional "Citrus" (POST /assistente/comando). */
public final class AssistenteDTO {

    private AssistenteDTO() {
    }

    /** O que o operador falou (transcrito pelo navegador) ou digitou. */
    public record Requisicao(
            @NotBlank(message = "diga ou digite um comando")
            @Size(max = 300, message = "comando muito longo (máximo 300 caracteres)")
            String texto) {
    }

    public enum Intencao {
        STATUS,
        RESERVATORIO,
        TALHOES_CRITICOS,
        ALERTAS,
        IRRIGACAO,
        TALHAO,
        LIGAR_ASPERSOR,
        DESLIGAR_ASPERSOR,
        RELATORIO,
        O_QUE_ACONTECE,
        ENERGIA,
        INDICE,
        AJUDA,
        DESCONHECIDO
    }

    /**
     * Acao que o painel executa junto com a resposta.
     *
     * @param tipo    NAVEGAR (abre uma secao), ABRIR_TALHAO (detalhes), ABRIR_EXPORTACAO, ATUALIZAR
     * @param alvo    secao do painel (visao-geral, talhoes, irrigacao, monitoramento, relatorios, gestao)
     * @param talhaoId talhao envolvido, quando houver
     * @param rotulo  texto do botao exibido na conversa
     */
    public record Acao(String tipo, String alvo, String talhaoId, String rotulo) {
    }

    /**
     * @param entendido  false quando o comando nao foi reconhecido
     * @param comando    o texto recebido, sem a palavra de ativacao "Citrus"
     * @param resposta   resposta para exibir (com numeros e unidades)
     * @param fala       a mesma resposta adaptada para leitura em voz
     * @param executouAcao true quando o comando alterou o estado da fazenda (ex.: ligou um aspersor)
     */
    public record Resposta(boolean entendido, Intencao intencao, String comando, String resposta, String fala,
                           Acao acao, boolean executouAcao, Instant instante) {
    }

    /** Exemplos exibidos como sugestoes no painel do Citrus. */
    public record Exemplos(List<String> perguntas, List<String> acoes) {
    }
}
