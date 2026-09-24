package br.com.biosolar.citrus.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.AcionamentoRequest;
import br.com.biosolar.citrus.dto.AcionamentoResponse;
import br.com.biosolar.citrus.dto.BombaDTO;
import br.com.biosolar.citrus.service.AcionamentoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/bombas")
public class BombaController {

    private final AcionamentoService acionamentoService;

    public BombaController(AcionamentoService acionamentoService) {
        this.acionamentoService = acionamentoService;
    }

    @GetMapping
    public List<BombaDTO> listar() {
        return acionamentoService.listarBombas();
    }

    /**
     * Comando manual do operador. Retorna 200 quando executado e 409 (Conflict) quando uma regra de
     * seguranca recusa o comando, com o motivo (ex.: BLOQUEIO_DE_EMERGENCIA).
     */
    @PostMapping("/acionar")
    public ResponseEntity<AcionamentoResponse> acionar(@Valid @RequestBody AcionamentoRequest requisicao) {
        AcionamentoResponse resposta = acionamentoService.acionar(requisicao);
        return ResponseEntity.status(resposta.sucesso() ? HttpStatus.OK : HttpStatus.CONFLICT).body(resposta);
    }
}