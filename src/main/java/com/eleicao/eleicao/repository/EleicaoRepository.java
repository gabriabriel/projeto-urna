package com.eleicao.eleicao.repository;

import com.eleicao.eleicao.domain.Eleicao;
import com.eleicao.shared.domain.StatusEleicao;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Só acesso a dados, sem regra de negócio.
 */
public interface EleicaoRepository extends JpaRepository<Eleicao, Long> {

    List<Eleicao> findAllByOrderByDataDescTurnoAsc();

    List<Eleicao> findByStatusOrderByDataDescTurnoAsc(StatusEleicao status);

    boolean existsByDataAndTurno(LocalDate data, Integer turno);

    boolean existsByDataAndTurnoAndIdNot(LocalDate data, Integer turno, Long id);

    /**
     * Busca travando a linha (SELECT ... FOR UPDATE) até o fim da transação. Usado nas mudanças de
     * estado (alterar, excluir, abrir, encerrar) para que duas requisições simultâneas do Chefe não
     * passem pela mesma checagem de status ao mesmo tempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Eleicao e where e.id = :id")
    Optional<Eleicao> buscarParaAlterar(@Param("id") Long id);
}
