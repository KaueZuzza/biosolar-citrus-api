package br.com.biosolar.citrus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.OrigemEvento;
import br.com.biosolar.citrus.model.Severidade;
import br.com.biosolar.citrus.model.TipoEvento;
import br.com.biosolar.citrus.repository.EventoRepository;
import br.com.biosolar.citrus.service.EstadoFazendaService;
import br.com.biosolar.citrus.service.SimulacaoService;

/** Robustez da gravacao no banco: nova tentativa apos rollback e leituras capturadas antes de restaurar. */
@SpringBootTest
@ActiveProfiles("test")
class PersistenciaIntegrationTest {

    @Autowired
    private EstadoFazendaService estado;

    @Autowired
    private SimulacaoService simulacao;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void restaurarCenario() {
        simulacao.restaurar();
    }

    private static Evento novoEvento() {
        return new Evento(Instant.now(), null, TipoEvento.SISTEMA, Severidade.INFO, OrigemEvento.SISTEMA, null, null,
                "Teste de persistencia", "Evento gravado em uma transacao desfeita.", 50.0);
    }

    @Test
    void eventoDeTransacaoDesfeitaEhRegravadoPorCopiaSemId() {
        Evento original = novoEvento();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            eventoRepository.save(original);
            status.setRollbackOnly();
        });

        // O rollback deixa o id gerado no objeto: regrava-lo tentaria atualizar uma linha inexistente
        assertThat(original.getId()).isNotNull();
        assertThat(eventoRepository.existsById(original.getId())).isFalse();
        assertThatThrownBy(() -> eventoRepository.save(original)).isInstanceOf(RuntimeException.class);

        Evento copia = original.copiaParaNovaTentativa();
        assertThat(copia.getId()).isNull();
        Evento gravado = eventoRepository.save(copia);
        assertThat(eventoRepository.findById(gravado.getId())).get()
                .satisfies(e -> {
                    assertThat(e.getTitulo()).isEqualTo(original.getTitulo());
                    assertThat(e.getDescricao()).isEqualTo(original.getDescricao());
                    assertThat(e.getNivelReservatorio()).isEqualTo(50.0);
                });
    }

    @Test
    void leituraCapturadaAntesDaRestauracaoEhDescartada() {
        long geracaoAntiga = estado.getGeracao();
        simulacao.restaurar();

        AtomicBoolean gravouAntiga = new AtomicBoolean();
        estado.registrarLeitura(geracaoAntiga, () -> gravouAntiga.set(true));
        assertThat(gravouAntiga).isFalse();

        AtomicBoolean gravouAtual = new AtomicBoolean();
        estado.registrarLeitura(estado.getGeracao(), () -> gravouAtual.set(true));
        assertThat(gravouAtual).isTrue();
    }
}
