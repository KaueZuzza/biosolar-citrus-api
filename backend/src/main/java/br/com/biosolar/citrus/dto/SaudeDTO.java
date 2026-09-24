package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.util.List;

/** Resposta de GET /saude: status da API, do banco, do simulador e dos dispositivos simulados. */
public record SaudeDTO(
        String status,
        String versao,
        Instant instante,
        Instant iniciadaEm,
        long uptimeSegundos,
        Componente api,
        Componente banco,
        Componente simulador,
        List<Dispositivo> sensores,
        List<Dispositivo> atuadores) {

    public record Componente(String status, String detalhe, Long latenciaMs) {
    }

    public record Dispositivo(String id, String tipo, String local, String status, String leitura) {
    }
}