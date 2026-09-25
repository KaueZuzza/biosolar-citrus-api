package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.AssistenteDTO;
import br.com.biosolar.citrus.service.assistente.AssistenteService;
import jakarta.validation.Valid;

/**
 * Assistente operacional "Citrus". O navegador transcreve a fala (Web Speech API) e envia o texto; a
 * interpretacao e a consulta aos dados acontecem aqui no servidor.
 */
@RestController
@RequestMapping("/assistente")
public class AssistenteController {

    private final AssistenteService assistente;

    public AssistenteController(AssistenteService assistente) {
        this.assistente = assistente;
    }

    /** Ex.: {"texto": "Citrus, quero o status"} */
    @PostMapping("/comando")
    public AssistenteDTO.Resposta comando(@Valid @RequestBody AssistenteDTO.Requisicao requisicao) {
        return assistente.interpretar(requisicao.texto());
    }

    @GetMapping("/exemplos")
    public AssistenteDTO.Exemplos exemplos() {
        return assistente.exemplos();
    }
}
