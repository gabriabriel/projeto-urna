package com.eleicao.apuracao.repository;

import com.eleicao.apuracao.domain.Apuracao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Só acesso a dados, sem regra de negócio. */
public interface ApuracaoRepository extends JpaRepository<Apuracao, Long> {

    Optional<Apuracao> findByEleicaoId(Long eleicaoId);

    boolean existsByEleicaoId(Long eleicaoId);
}
