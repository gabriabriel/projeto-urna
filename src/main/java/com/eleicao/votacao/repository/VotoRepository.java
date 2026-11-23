package com.eleicao.votacao.repository;

import com.eleicao.shared.domain.Cargo;
import com.eleicao.votacao.domain.Voto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Só acesso a dados. A consulta de contagem devolve linhas [candidatoId, quantidade].
 * Candidatos sem nenhum voto não aparecem aqui: quem monta a contagem completa é o ContagemVotosService.
 */
public interface VotoRepository extends JpaRepository<Voto, String> {

    @Query("""
            select v.candidato.id, count(v)
            from Voto v
            where v.candidato.eleicao.id = :eleicaoId and v.candidato.cargo = :cargo
            group by v.candidato.id
            """)
    List<Object[]> contarPorCandidato(@Param("eleicaoId") Long eleicaoId, @Param("cargo") Cargo cargo);
}
