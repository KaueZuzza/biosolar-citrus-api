package br.com.biosolar.citrus.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.biosolar.citrus.model.LeituraTelemetria;

public interface LeituraTelemetriaRepository extends JpaRepository<LeituraTelemetria, Long> {

    List<LeituraTelemetria> findAllByOrderByIdDesc(Pageable pagina);

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