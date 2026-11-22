package com.eleicao.cadastro.repository;

import com.eleicao.cadastro.domain.Candidato;
import com.eleicao.shared.domain.Cargo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Só acesso a dados, sem regra de negócio.
 * Os métodos que devolvem candidatos usam @EntityGraph para trazer eleitor, usuário, partido e
 * eleição na mesma consulta (nome e sigla são necessários nas respostas e nos outros módulos).
 */
public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    Optional<Candidato> findDetalhadoById(Long id);

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    Optional<Candidato> findByEleitorIdAndEleicaoId(Long eleitorId, Long eleicaoId);

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    List<Candidato> findByEleicaoIdAndCargoOrderByNumeroAsc(Long eleicaoId, Cargo cargo);

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    List<Candidato> findByEleicaoIdOrderByCargoAscNumeroAsc(Long eleicaoId);

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    List<Candidato> findByCargoOrderByEleicaoIdAscNumeroAsc(Cargo cargo);

    @EntityGraph(attributePaths = {"eleitor.usuario", "partido", "eleicao"})
    List<Candidato> findAllByOrderByEleicaoIdAscCargoAscNumeroAsc();

    Optional<Candidato> findByEleicaoIdAndCargoAndNumero(Long eleicaoId, Cargo cargo, Integer numero);

    boolean existsByEleitorId(Long eleitorId);

    boolean existsByEleitorIdAndEleicaoId(Long eleitorId, Long eleicaoId);
}
