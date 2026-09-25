package br.com.biosolar.citrus.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.Reservatorio;
import br.com.biosolar.citrus.model.StatusReservatorio;

public interface ReservatorioRepository extends JpaRepository<Reservatorio, Integer> {

    /** Grava apenas o estado operacional; o cadastro (nome, capacidade, limites) nao e tocado. */
    @Modifying
    @Query("""
            update Reservatorio r set r.nivel = :nivel, r.status = :status,
                r.bloqueioEmergencia = :bloqueio, r.ultimaAtualizacao = :ultimaAtualizacao
            where r.id = :id
            """)
    int atualizarOperacao(@Param("id") Integer id, @Param("nivel") double nivel,
                          @Param("status") StatusReservatorio status, @Param("bloqueio") boolean bloqueio,
                          @Param("ultimaAtualizacao") Instant ultimaAtualizacao);

    default int atualizarOperacao(Reservatorio r) {
        return atualizarOperacao(r.getId(), r.getNivel(), r.getStatus(), r.isBloqueioEmergencia(),
                r.getUltimaAtualizacao());
    }
}
