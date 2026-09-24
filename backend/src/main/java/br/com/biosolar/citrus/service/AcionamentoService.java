package br.com.biosolar.citrus.service;

import static br.com.biosolar.citrus.util.Formatador.r1;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.biosolar.citrus.dto.AcionamentoRequest;
import br.com.biosolar.citrus.dto.AcionamentoResponse;
import br.com.biosolar.citrus.dto.BombaDTO;
import br.com.biosolar.citrus.dto.TalhaoDTO;
import br.com.biosolar.citrus.exception.RecursoNaoEncontradoException;
import br.com.biosolar.citrus.model.Talhao;

/** Comandos manuais do operador sobre as bombas/aspersores (Prioridade 4). */
@Service
public class AcionamentoService {

    private final EstadoFazendaService estado;
    private final MotorRegras motorRegras;
    private final Clock clock;

    public AcionamentoService(EstadoFazendaService estado, MotorRegras motorRegras, Clock clock) {
        this.estado = estado;
        this.motorRegras = motorRegras;
        this.clock = clock;
    }

    public AcionamentoResponse acionar(AcionamentoRequest requisicao) {
        String talhaoId = requisicao.talhaoId().trim().toUpperCase(Locale.ROOT);
        Instant agora = clock.instant();

        return estado.executar(fazenda -> {
            Talhao talhao = fazenda.buscarTalhao(talhaoId).orElseThrow(() -> new RecursoNaoEncontradoException(
                    "Talhão '" + talhaoId + "' não encontrado. Talhões disponíveis: "
                            + fazenda.getTalhoes().stream().map(Talhao::getId).collect(Collectors.joining(", ")) + "."));

            ResultadoComando resultado = motorRegras.avaliarComandoManual(fazenda, talhao, requisicao.ligado(), agora);

            return new AcionamentoResponse(resultado.sucesso(), resultado.motivo(), resultado.mensagem(), agora,
                    TalhaoDTO.de(talhao, fazenda), BombaDTO.de(talhao, fazenda),
                    r1(fazenda.getReservatorio().getNivel()), fazenda.getReservatorio().isBloqueioEmergencia());
        });
    }

    public List<BombaDTO> listarBombas() {
        return estado.ler(fazenda -> fazenda.getTalhoes().stream().map(t -> BombaDTO.de(t, fazenda)).toList());
    }
}
