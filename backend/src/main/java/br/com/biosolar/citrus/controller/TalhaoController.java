package br.com.biosolar.citrus.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biosolar.citrus.dto.TalhaoCadastroDTO;
import br.com.biosolar.citrus.dto.TalhaoRequest;
import br.com.biosolar.citrus.service.CadastroService;
import jakarta.validation.Valid;

/**
 * Cadastro de talhoes (cada talhao inclui sua motobomba + aspersor e seu sensor de umidade).
 * As alteracoes sao gravadas no PostgreSQL e passam a valer na automacao imediatamente.
 */
@RestController
@RequestMapping("/talhoes")
public class TalhaoController {

    private final CadastroService cadastro;

    public TalhaoController(CadastroService cadastro) {
        this.cadastro = cadastro;
    }

    /** Todos os talhoes cadastrados, inclusive os arquivados (ativo = false). */
    @GetMapping
    public List<TalhaoCadastroDTO> listar() {
        return cadastro.listarTalhoes();
    }

    @GetMapping("/{id}")
    public TalhaoCadastroDTO buscar(@PathVariable String id) {
        return cadastro.buscarTalhao(id);
    }

    @PostMapping
    public ResponseEntity<TalhaoCadastroDTO> criar(@Valid @RequestBody TalhaoRequest requisicao) {
        TalhaoCadastroDTO criado = cadastro.criarTalhao(requisicao);
        return ResponseEntity.created(URI.create("/talhoes/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public TalhaoCadastroDTO atualizar(@PathVariable String id, @Valid @RequestBody TalhaoRequest requisicao) {
        return cadastro.atualizarTalhao(id, requisicao);
    }

    /** Exclui o talhao da operacao (arquiva): sai da automacao, o historico e preservado. */
    @DeleteMapping("/{id}")
    public TalhaoCadastroDTO excluir(@PathVariable String id) {
        return cadastro.arquivarTalhao(id);
    }

    @PostMapping("/{id}/reativar")
    public TalhaoCadastroDTO reativar(@PathVariable String id) {
        return cadastro.reativarTalhao(id);
    }

    /** Remove do banco um talhao ja arquivado. */
    @DeleteMapping("/{id}/definitivo")
    public ResponseEntity<Void> excluirDefinitivamente(@PathVariable String id) {
        cadastro.excluirTalhaoDefinitivamente(id);
        return ResponseEntity.noContent().build();
    }
}
