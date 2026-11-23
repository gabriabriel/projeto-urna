package com.eleicao.votacao.repository;

import com.eleicao.shared.domain.Cargo;
import com.eleicao.votacao.domain.Participacao;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Só acesso a dados. Responde "o eleitor já votou neste cargo?" sem saber em quem votou.
 */
public interface ParticipacaoRepository extends JpaRepository<Participacao, Long> {

    boolean existsByEleicaoIdAndEleitorIdAndCargo(Long eleicaoId, Long eleitorId, Cargo cargo);
}
