package br.com.biosolar.citrus.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.LeituraTelemetria;

public interface LeituraTelemetriaRepository extends JpaRepository<LeituraTelemetria, Long> {

    List<LeituraTelemetria> findAllByOrderByIdDesc(Pageable pagina);

    /** Remove leituras antigas (as linhas de leitura_talhao sao removidas em cascata pelo banco). */
    @Modifying
    @Query("delete from LeituraTelemetria l where l.instante < :limite")
    int excluirAnterioresA(@Param("limite") Instant limite);

    /** Amostragem uniforme do historico completo (1 leitura a cada {@code passo}) para planilhas e graficos. */
    @Query("select l from LeituraTelemetria l where mod(l.id, :passo) = 0 order by l.id")
    List<LeituraTelemetria> amostrar(@Param("passo") long passo);

    @Query("""
            select avg(l.nivelReservatorio) as medio, min(l.nivelReservatorio) as minimo,
                   max(l.nivelReservatorio) as maximo, avg(l.indice) as indiceMedio,
                   avg(l.umidadeMedia) as umidadeMedia, count(l) as total
            from LeituraTelemetria l
            """)
    Estatisticas estatisticas();

    interface Estatisticas {
        Double getMedio();

        Double getMinimo();

        Double getMaximo();

        Double getIndiceMedio();

        Double getUmidadeMedia();

        Long getTotal();
    }
}