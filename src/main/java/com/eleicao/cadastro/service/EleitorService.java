package com.eleicao.cadastro.service;

import com.eleicao.auth.api.UsuarioApi;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.cadastro.domain.Eleitor;
import com.eleicao.cadastro.dto.EleitorRequest;
import com.eleicao.cadastro.dto.EleitorResponse;
import com.eleicao.cadastro.dto.EleitorUpdateRequest;
import com.eleicao.cadastro.repository.CandidatoRepository;
import com.eleicao.cadastro.repository.EleitorRepository;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do Chefe da Seção sobre eleitores. Toda a regra de negócio fica aqui.
 * O login do eleitor pertence ao módulo auth e só é acessado pela interface UsuarioApi.
 */
@Service
@RequiredArgsConstructor
public class EleitorService {

    private final EleitorRepository eleitorRepository;
    private final CandidatoRepository candidatoRepository;
    private final UsuarioApi usuarioApi;

    /** Cria o login e o eleitor na MESMA transação: se um falhar, nenhum é gravado. */
    @Transactional
    public EleitorResponse criar(EleitorRequest request) {
        exigirTituloECpfLivres(request.tituloEleitor(), request.cpf(), null);

        Usuario usuario = usuarioApi.criar(request.nome(), request.login(), request.senha(), Perfil.ELEITOR);

        Eleitor eleitor = eleitorRepository.save(Eleitor.builder()
                .usuario(usuario)
                .tituloEleitor(request.tituloEleitor())
                .cpf(request.cpf())
                .build());

        return EleitorResponse.from(eleitor);
    }

    @Transactional(readOnly = true)
    public List<EleitorResponse> listar() {
        return eleitorRepository.findAllByOrderByUsuarioNomeAsc().stream()
                .map(EleitorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EleitorResponse buscar(Long id) {
        return EleitorResponse.from(buscarEntidade(id));
    }

    @Transactional
    public EleitorResponse atualizar(Long id, EleitorUpdateRequest request) {
        Eleitor eleitor = buscarEntidade(id);
        exigirTituloECpfLivres(request.tituloEleitor(), request.cpf(), id);

        // O usuário é a mesma instância gerenciada: o que o módulo auth alterar já aparece na resposta.
        usuarioApi.atualizar(eleitor.getUsuario().getId(), request.nome(), request.login());

        eleitor.setTituloEleitor(request.tituloEleitor());
        eleitor.setCpf(request.cpf());

        return EleitorResponse.from(eleitor);
    }

    @Transactional
    public void excluir(Long id) {
        Eleitor eleitor = buscarEntidade(id);

        // A candidatura é do nosso módulo, então checamos aqui com mensagem clara.
        if (candidatoRepository.existsByEleitorId(id)) {
            throw new ConflictException("Eleitor é candidato e não pode ser excluído. Exclua a candidatura antes");
        }

        Long usuarioId = eleitor.getUsuario().getId();
        try {
            eleitorRepository.delete(eleitor);
            // flush força o DELETE agora. A participação em votação é do módulo votação (não podemos
            // consultá-la), então quem barra é a chave estrangeira do banco: se o eleitor já votou, estoura aqui.
            eleitorRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Eleitor já participou de uma votação e não pode ser excluído");
        }

        // O eleitor precisa sair antes do usuário (FK eleitor.usuario_id -> usuario.id).
        usuarioApi.remover(usuarioId);
    }

    // ------------------------------------------------------------------ privados

    private Eleitor buscarEntidade(Long id) {
        return eleitorRepository.findDetalhadoById(id)
                .orElseThrow(() -> NotFoundException.de("Eleitor", id));
    }

    /** idAtual é o eleitor que está sendo alterado (ignorado na checagem) ou null ao criar. */
    private void exigirTituloECpfLivres(String titulo, String cpf, Long idAtual) {
        eleitorRepository.findByTituloEleitor(titulo)
                .filter(outro -> !outro.getId().equals(idAtual))
                .ifPresent(outro -> {
                    throw new ConflictException("Já existe um eleitor com este título: " + titulo);
                });

        eleitorRepository.findByCpf(cpf)
                .filter(outro -> !outro.getId().equals(idAtual))
                .ifPresent(outro -> {
                    throw new ConflictException("Já existe um eleitor com este CPF");
                });
    }
}
