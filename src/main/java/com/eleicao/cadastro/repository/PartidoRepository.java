package com.eleicao.cadastro.repository;

import com.eleicao.cadastro.domain.Partido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Os partidos entram pela migration V3 (seed). Não há CRUD de partido na API, só consulta. */
public interface PartidoRepository extends JpaRepository<Partido, Long> {

    /** Lista na ordem do número da legenda (10, 11, 12, 13...). */
    List<Partido> findAllByOrderByNumeroAsc();
}
