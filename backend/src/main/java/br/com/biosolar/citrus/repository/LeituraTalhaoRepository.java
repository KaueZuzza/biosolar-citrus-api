package br.com.biosolar.citrus.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.LeituraTalhao;

public interface LeituraTalhaoRepository extends JpaRepository<LeituraTalhao, Long> {

    List<LeituraTalhao> findByLeituraIdIn(Collection<Long> leituraIds);

    long countByTalhaoId(String talhaoId);

    @Modifying
    @Query("delete from LeituraTalhao lt where lt.talhaoId = :talhaoId")
    int excluirPorTalhao(@Param("talhaoId") String talhaoId);

    @Query("""
            select lt.talhaoId as talhaoId, min(lt.umidade) as minima, avg(lt.umidade) as media
            from LeituraTalhao lt
            group by lt.talhaoId
            """)
    List<ResumoUmidade> resumoPorTalhao();

    interface ResumoUmidade {
        String getTalhaoId();

        Double getMinima();

        Double getMedia();
    }
}