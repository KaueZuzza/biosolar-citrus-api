package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.StatusDTO;
import br.com.biosolar.citrus.dto.TelemetriaDTO;
import br.com.biosolar.citrus.service.TelemetriaService;

@RestController
public class TelemetriaController {

    private final TelemetriaService telemetriaService;

    public TelemetriaController(TelemetriaService telemetriaService) {
        this.telemetriaService = telemetriaService;
    }

    /** Leitura completa: reservatorio, talhoes, bombas, energia, indice, decisao, alertas e eventos recentes. */
    @GetMapping("/telemetria")
    public TelemetriaDTO telemetria() {
        return telemetriaService.obterTelemetria();
    }

    /** Resumo operacional (inclui texto para leitura em voz). */
    @GetMapping("/status")
    public StatusDTO status() {
        return telemetriaService.obterStatus();
    }
}