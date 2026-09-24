package br.com.biosolar.citrus.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.biosolar.citrus.model.LeituraTalhao;

public interface LeituraTalhaoRepository extends JpaRepository<LeituraTalhao, Long> {

    List<LeituraTalhao> findByLeituraIdIn(Collection<Long> leituraIds);

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