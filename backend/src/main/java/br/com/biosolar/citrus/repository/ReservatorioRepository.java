package br.com.biosolar.citrus.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.biosolar.citrus.model.Reservatorio;

public interface ReservatorioRepository extends JpaRepository<Reservatorio, Integer> {
}