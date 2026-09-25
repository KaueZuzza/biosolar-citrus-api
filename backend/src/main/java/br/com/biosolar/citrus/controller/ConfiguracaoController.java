package br.com.biosolar.citrus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.ConfiguracaoDTO;
import br.com.biosolar.citrus.service.CadastroService;
import jakarta.validation.Valid;

/** Cadastro do reservatorio central e da usina solar. */
@RestController
@RequestMapping("/configuracao")
public class ConfiguracaoController {

    private final CadastroService cadastro;

    public ConfiguracaoController(CadastroService cadastro) {
        this.cadastro = cadastro;
    }

    @GetMapping
    public ConfiguracaoDTO obter() {
        return cadastro.obterConfiguracao();
    }

    @PutMapping("/reservatorio")
    public ConfiguracaoDTO reservatorio(@Valid @RequestBody ConfiguracaoDTO.ReservatorioRequest requisicao) {
        return cadastro.atualizarReservatorio(requisicao);
    }

    @PutMapping("/usina")
    public ConfiguracaoDTO usina(@Valid @RequestBody ConfiguracaoDTO.UsinaRequest requisicao) {
        return cadastro.atualizarUsina(requisicao);
    }
}
