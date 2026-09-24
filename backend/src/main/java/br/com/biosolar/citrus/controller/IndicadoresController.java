package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.IndicadoresDTO;
import br.com.biosolar.citrus.dto.SaudeDTO;
import br.com.biosolar.citrus.service.IndicadoresService;
import br.com.biosolar.citrus.service.SaudeService;

@RestController
public class IndicadoresController {

    private final IndicadoresService indicadoresService;
    private final SaudeService saudeService;

    public IndicadoresController(IndicadoresService indicadoresService, SaudeService saudeService) {
        this.indicadoresService = indicadoresService;
        this.saudeService = saudeService;
    }

    @GetMapping("/indicadores")
    public IndicadoresDTO indicadores() {
        return indicadoresService.calcular();
    }

    @GetMapping("/saude")
    public SaudeDTO saude() {
        return saudeService.verificar();
    }
}