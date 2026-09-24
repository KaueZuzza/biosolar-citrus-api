package br.com.biosolar.citrus.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.biosolar.citrus.model.Evento;
import br.com.biosolar.citrus.model.TipoEvento;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findAllByOrderByIdDesc(Pageable pagina);

    List<Evento> findByIdGreaterThanOrderByIdDesc(Long id, Pageable pagina);

    List<Evento> findByTipoNotInOrderByIdDesc(Collection<TipoEvento> tipos, Pageable pagina);

    List<Evento> findAllByOrderByIdAsc();

    long countByTipo(TipoEvento tipo);

    @Query("""
            select e.talhaoId as talhaoId, e.tipo as tipo, count(e) as total
            from Evento e
            where e.talhaoId is not null and e.tipo in :tipos
            group by e.talhaoId, e.tipo
            """)
    List<ContagemPorTalhao> contarPorTalhaoETipo(@Param("tipos") Collection<TipoEvento> tipos);

    @Query("""
            select distinct e.talhaoId from Evento e
            where e.talhaoId is not null and e.tipo in :tipos
            order by e.talhaoId
            """)
    List<String> talhoesComEventos(@Param("tipos") Collection<TipoEvento> tipos);

    interface ContagemPorTalhao {
        String getTalhaoId();

        TipoEvento getTipo();

        Long getTotal();
    }
}