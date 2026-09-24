package br.com.biosolar.citrus.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.EventoDTO;
import br.com.biosolar.citrus.dto.HistoricoDTO;
import br.com.biosolar.citrus.service.HistoricoService;

@RestController
public class HistoricoController {

    private final HistoricoService historicoService;

    public HistoricoController(HistoricoService historicoService) {
        this.historicoService = historicoService;
    }

    /** Historico de eventos (mais recentes primeiro). Use desdeId para buscar apenas os novos. */
    @GetMapping("/eventos")
    public List<EventoDTO> eventos(@RequestParam(defaultValue = "50") int limite,
                                   @RequestParam(required = false) Long desdeId) {
        return historicoService.eventos(limite, desdeId);
    }

    /** Serie temporal da telemetria para os graficos (ordem cronologica). */
    @GetMapping("/historico")
    public HistoricoDTO historico(@RequestParam(defaultValue = "240") int limite) {
        return historicoService.consultar(limite);
    }
}