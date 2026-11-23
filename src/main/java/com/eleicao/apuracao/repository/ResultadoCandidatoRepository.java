package com.eleicao.apuracao.repository;

import com.eleicao.apuracao.domain.ResultadoCandidato;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Só acesso a dados, sem regra de negócio. */
public interface ResultadoCandidatoRepository extends JpaRepository<ResultadoCandidato, Long> {

    List<ResultadoCandidato> findByApuracaoId(Long apuracaoId);
}
