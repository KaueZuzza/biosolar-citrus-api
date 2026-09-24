package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.SimulacaoRequests;
import br.com.biosolar.citrus.service.SimulacaoService;
import jakarta.validation.Valid;

/**
 * Painel de simulacao para a demonstracao. Altera apenas as condicoes fisicas no servidor;
 * as decisoes continuam sendo tomadas pelo motor de regras do backend.
 */
@RestController
@RequestMapping("/simulacao")
public class SimulacaoController {

    private final SimulacaoService simulacaoService;

    public SimulacaoController(SimulacaoService simulacaoService) {
        this.simulacaoService = simulacaoService;
    }

    @PostMapping("/velocidade")
    public SimulacaoRequests.Resposta velocidade(@Valid @RequestBody SimulacaoRequests.Velocidade requisicao) {
        return simulacaoService.alterarVelocidade(requisicao.fator());
    }

    @PostMapping("/pausa")
    public SimulacaoRequests.Resposta pausa(@Valid @RequestBody SimulacaoRequests.Pausa requisicao) {
        return simulacaoService.pausar(requisicao.pausada());
    }

    @PostMapping("/umidade")
    public SimulacaoRequests.Resposta umidade(@Valid @RequestBody SimulacaoRequests.AjusteUmidade requisicao) {
        return simulacaoService.ajustarUmidade(requisicao.talhaoId(), requisicao.delta());
    }

    @PostMapping("/reservatorio")
    public SimulacaoRequests.Resposta reservatorio(
            @Valid @RequestBody SimulacaoRequests.AjusteReservatorio requisicao) {
        return simulacaoService.ajustarReservatorio(requisicao.delta());
    }

    @PostMapping("/emergencia")
    public SimulacaoRequests.Resposta emergencia() {
        return simulacaoService.simularEmergencia();
    }

    @PostMapping("/restaurar")
    public SimulacaoRequests.Resposta restaurar() {
        return simulacaoService.restaurar();
    }
}