package br.com.biosolar.citrus.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.biosolar.citrus.model.Talhao;

public interface TalhaoRepository extends JpaRepository<Talhao, String> {
}