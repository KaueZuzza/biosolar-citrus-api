package br.com.biosolar.citrus.repository;

import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.EstadoSimulacao;

public interface EstadoSimulacaoRepository extends JpaRepository<EstadoSimulacao, Integer> {

    /** Grava relogio, velocidade e acumuladores; a potencia da usina (cadastro) nao e tocada. */
    @Modifying
    @Query("""
            update EstadoSimulacao s set s.relogioSimulado = :relogio, s.fatorVelocidade = :fator,
                s.pausada = :pausada, s.ticks = :ticks, s.energiaConsumidaKwh = :energia,
                s.energiaSolarKwh = :solar, s.aguaConsumidaM3 = :agua, s.iniciadaEm = :iniciadaEm
            where s.id = :id
            """)
    int atualizarOperacao(@Param("id") Integer id, @Param("relogio") LocalDateTime relogio,
                          @Param("fator") int fator, @Param("pausada") boolean pausada, @Param("ticks") long ticks,
                          @Param("energia") double energia, @Param("solar") double solar, @Param("agua") double agua,
                          @Param("iniciadaEm") Instant iniciadaEm);

    default int atualizarOperacao(EstadoSimulacao s) {
        return atualizarOperacao(s.getId(), s.getRelogioSimulado(), s.getFatorVelocidade(), s.isPausada(),
                s.getTicks(), s.getEnergiaConsumidaKwh(), s.getEnergiaSolarKwh(), s.getAguaConsumidaM3(),
                s.getIniciadaEm());
    }
}
