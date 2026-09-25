package br.com.biosolar.citrus.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.ModoAcionamento;
import br.com.biosolar.citrus.model.StatusTalhao;
import br.com.biosolar.citrus.model.Talhao;

public interface TalhaoRepository extends JpaRepository<Talhao, String> {

    /** Todos os talhoes, inclusive os arquivados. */
    List<Talhao> findAllByOrderByIdAsc();

    long countByAtivoTrue();

    /**
     * Grava apenas o estado operacional (umidade, aspersor, status). Os dados cadastrais nao sao tocados, para
     * que uma edicao feita diretamente no banco (ex.: pgAdmin) nao seja sobrescrita pelo ciclo da automacao.
     */
    @Modifying
    @Query("""
            update Talhao t set t.umidade = :umidade, t.aspersorLigado = :aspersorLigado,
                t.modoAcionamento = :modo, t.status = :status, t.irrigacaoBloqueada = :irrigacaoBloqueada,
                t.ultimaAtualizacao = :ultimaAtualizacao, t.ultimoAcionamento = :ultimoAcionamento
            where t.id = :id
            """)
    int atualizarOperacao(@Param("id") String id, @Param("umidade") double umidade,
                          @Param("aspersorLigado") boolean aspersorLigado, @Param("modo") ModoAcionamento modo,
                          @Param("status") StatusTalhao status, @Param("irrigacaoBloqueada") boolean irrigacaoBloqueada,
                          @Param("ultimaAtualizacao") Instant ultimaAtualizacao,
                          @Param("ultimoAcionamento") Instant ultimoAcionamento);

    default int atualizarOperacao(Talhao t) {
        return atualizarOperacao(t.getId(), t.getUmidade(), t.isAspersorLigado(), t.getModoAcionamento(),
                t.getStatus(), t.isIrrigacaoBloqueada(), t.getUltimaAtualizacao(), t.getUltimoAcionamento());
    }
}
