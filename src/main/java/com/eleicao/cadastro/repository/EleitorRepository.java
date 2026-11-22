package com.eleicao.cadastro.repository;

import com.eleicao.cadastro.domain.Eleitor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Só acesso a dados, sem regra de negócio.
 * O @EntityGraph traz o usuário junto na mesma consulta: como o open-in-view está desligado,
 * a entidade devolvida para outros módulos não pode depender de carregamento preguiçoso.
 */
public interface EleitorRepository extends JpaRepository<Eleitor, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<Eleitor> findDetalhadoById(Long id);

    @EntityGraph(attributePaths = "usuario")
    Optional<Eleitor> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = "usuario")
    List<Eleitor> findAllByOrderByUsuarioNomeAsc();

    Optional<Eleitor> findByTituloEleitor(String tituloEleitor);

    Optional<Eleitor> findByCpf(String cpf);
}
