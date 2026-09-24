package br.com.biosolar.citrus.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.TipoEvento;

public record EventoDTO(
        Long id,
        Instant instante,
        LocalDateTime horaSimulada,
        TipoEvento tipo,
        Severidade severidade,
        OrigemEvento origem,
        String regra,
        String talhaoId,
        String titulo,
        String descricao,
        Double nivelReservatorio) {

    public static EventoDTO de(Evento e) {
        return new EventoDTO(e.getId(), e.getInstante(), e.getHoraSimulada(), e.getTipo(), e.getSeveridade(),
                e.getOrigem(), e.getRegra(), e.getTalhaoId(), e.getTitulo(), e.getDescricao(),
                e.getNivelReservatorio());
    }
}