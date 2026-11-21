package com.eleicao.auth.service;

import com.eleicao.auth.api.UsuarioApi;
import com.eleicao.auth.domain.Usuario;
import com.eleicao.auth.repository.UsuarioRepository;
import com.eleicao.shared.domain.Perfil;
import com.eleicao.shared.exception.BusinessRuleException;
import com.eleicao.shared.exception.ConflictException;
import com.eleicao.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação real da UsuarioApi: é por aqui que o módulo cadastro cria, altera e remove
 * o login de um eleitor. A senha chega em texto puro e sai daqui já como hash BCrypt;
 * nenhum outro módulo vê ou grava hash.
 *
 * Os métodos entram na transação de quem chamou (ex.: EleitorService.criar), então
 * se o eleitor falhar ao ser gravado, o usuário também é desfeito.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService implements UsuarioApi {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Usuario criar(String nome, String login, String senhaPlana, Perfil perfil) {
        if (senhaPlana == null || senhaPlana.isBlank()) {
            throw new BusinessRuleException("A senha é obrigatória");
        }
        if (perfil == null) {
            throw new BusinessRuleException("O perfil é obrigatório");
        }
        if (usuarioRepository.existsByLogin(login)) {
            throw new ConflictException("Já existe um usuário com este login: " + login);
        }

        return usuarioRepository.save(Usuario.builder()
                .nome(nome)
                .login(login)
                .senhaHash(passwordEncoder.encode(senhaPlana))
                .perfil(perfil)
                .build());
    }

    /** Altera nome e login. Senha e perfil não mudam por aqui. */
    @Override
    @Transactional
    public Usuario atualizar(Long usuarioId, String nome, String login) {
        Usuario usuario = buscar(usuarioId);
        if (usuarioRepository.existsByLoginAndIdNot(login, usuarioId)) {
            throw new ConflictException("Já existe um usuário com este login: " + login);
        }
        // Entidade gerenciada: o JPA grava a alteração no fim da transação, sem save().
        usuario.setNome(nome);
        usuario.setLogin(login);
        return usuario;
    }

    /** Remover um usuário que não existe não é erro (mesmo comportamento do stub que existia). */
    @Override
    @Transactional
    public void remover(Long usuarioId) {
        usuarioRepository.findById(usuarioId).ifPresent(usuarioRepository::delete);
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> NotFoundException.de("Usuário", usuarioId));
    }
}
