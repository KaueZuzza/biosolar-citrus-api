package br.com.biosolar.citrus.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.biosolar.citrus.model.EstadoSimulacao;

public interface EstadoSimulacaoRepository extends JpaRepository<EstadoSimulacao, Integer> {
}